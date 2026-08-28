package uk.co.iceconchy.aerowarptics.gate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/**
 * The pane that stands in a Rift Gate's opening: a hole in space you can see, and walk through.
 *
 * <p>A block rather than a drawn effect, and built along the lines of the Nether portal, because the
 * two are the same idea. A doorway is a thing that <em>is</em> somewhere - it should be lit by the
 * game's own light engine, occlude and be occluded like anything else, hold its own shape when a ring
 * is L-shaped, and be there for anybody who walks up to it without a client-side effect having to be
 * renewed at them. The previous aperture was a billboard fitted to an ellipse and bent to the mask;
 * this is simply the cells of the mask, one block each.
 *
 * <h2>It does not move anybody</h2>
 * Deliberately, and this is the important part. The Nether portal teleports from
 * {@code entityInside}, which is exactly the thing this mod's gates must not do: a crossing here is
 * <em>a change of side between two sightings the gate took itself</em>, never something inferred from
 * where an entity happens to be when a block asks. That rule is what makes a fast vehicle come out of
 * the far gate instead of skipping through the pane between ticks, and it lives in
 * {@link RiftGateBlockEntity} where the gate can see both sides. This block is scenery over the top
 * of it - which also means a pane that somehow outlives its gate is inert rather than a trap.
 *
 * <h2>Who puts it there</h2>
 * The gate does, from {@link RiftGateShape#cells()}, and takes it away again when it closes. The
 * random tick here is the backstop for the case the gate cannot cover: a ring broken, or a controller
 * gone, while its chunks were not loaded. The pane checks that it is still ringed - walk out from it
 * in the four directions in its own plane, and every walk has to end on frame - and removes itself if
 * it is not. That is the same question the Nether portal asks on its random tick, for the same reason:
 * an unbreakable block with nothing holding it up is a permanent scar.
 */
public class RiftPortalBlock extends Block {

    public static final MapCodec<RiftPortalBlock> CODEC = simpleCodec(RiftPortalBlock::new);

    /**
     * The horizontal axis the pane spans; it is thin along the other one.
     *
     * <p>The same convention as the Nether portal's own {@code AXIS}, and the same as
     * {@link RiftGateShape#span()}, so the gate can hand its span straight to the blockstate.
     */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** What the pane is doing - forming, standing, or falling apart. See {@link RiftPortalStage}. */
    public static final EnumProperty<RiftPortalStage> STAGE =
            EnumProperty.create("stage", RiftPortalStage.class);

    /** Four pixels thick, in the middle of the block, exactly as a Nether portal is. */
    private static final VoxelShape ALONG_X = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape ALONG_Z = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    /**
     * How far the ring may be from any cell of the opening.
     *
     * <p>A guard rather than a measurement. No cell can be further from the frame than the opening
     * has cells, and the flood fill stops at {@link RiftGateStructure#MAX_AREA} of them - so a walk
     * that gets that far has left the world this block belongs to, and the answer is no.
     */
    private static final int MAX_REACH = RiftGateStructure.MAX_AREA;

    public RiftPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(STAGE, RiftPortalStage.OPENING));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, STAGE);
    }

    /**
     * How brightly a pane burns, from its stage.
     *
     * <p>Dimmer while it is opening or closing, because it is not fully there yet - the same idea as
     * the shimmer's own alpha climbing with brightness, just carried into the block's own light rather
     * than left for the eye to read off a translucent texture alone.
     */
    public static int stageLight(BlockState state) {
        return switch (state.getValue(STAGE)) {
            case OPENING, CLOSING -> 8;
            case OPEN -> 11;
        };
    }

    // ----------------------------------------------------------------- appearance

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    /**
     * A pane made of many blocks is drawn as one sheet.
     *
     * <p>Without this, every cell draws its own edges against its neighbours, and a five-by-five
     * opening comes out as a grid of twenty-five little windows rather than one surface. The two
     * faces a player actually looks at are along the axis the pane is <em>thin</em> on, where the
     * neighbour is never another pane, so those are never skipped.
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> state.setValue(AXIS,
                    state.getValue(AXIS) == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        // A pane is symmetrical about both of its own axes, so there is nothing for a mirror to do.
        return state;
    }

    /**
     * Sparks drifting off the surface, and a hole in space being quietly audible.
     *
     * <p>Sparse on purpose. A gate can be thirty blocks across, so a rate that reads well on one
     * block is a wall of particles on a big one; this is tuned so that the whole pane gives off about
     * as much as a Nether portal of ordinary size.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(28) != 0) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        // Drifting along the pane rather than out of it: the shimmer is a surface, not a fountain.
        double drift = (random.nextDouble() - 0.5D) * 0.06D;
        level.addParticle(AWParticles.RIFT_SPARK.get(), x, y, z,
                state.getValue(AXIS) == Direction.Axis.X ? drift : 0.0D,
                (random.nextDouble() - 0.3D) * 0.05D,
                state.getValue(AXIS) == Direction.Axis.Z ? drift : 0.0D);
    }

    // ------------------------------------------------------------------ crossing

    /**
     * Nothing. See the note on this class: a crossing belongs to the gate, not to the pane.
     *
     * <p>Overridden rather than left to the default so that the omission is visible. Somebody adding
     * a teleport here would be re-introducing the exact bug the gate's two-sighting rule exists to
     * prevent, and finding an empty override with this comment on it is the only warning they will
     * get.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    }

    // ------------------------------------------------------------------ upkeep

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!stillRinged(level, pos, state.getValue(AXIS))) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * Whether this cell is still inside a ring of gate frame.
     *
     * <p>Walks out in the four directions the pane lies in, through any other pane it meets, and
     * asks what it lands on. Every one of them has to land on frame: a hole in a wall is only a hole
     * in a wall while there is a wall.
     */
    private static boolean stillRinged(LevelReader level, BlockPos pos, Direction.Axis span) {
        Direction across = span == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (Direction direction : new Direction[] {across, across.getOpposite(), Direction.UP, Direction.DOWN}) {
            if (!reachesFrame(level, pos, direction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean reachesFrame(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int step = 0; step < MAX_REACH; step++) {
            cursor.move(direction);
            BlockState state = level.getBlockState(cursor);
            if (state.is(AWBlocks.RIFT_PORTAL.get())) {
                continue;
            }
            return state.is(AWBlocks.RIFT_GATE_FRAME.get()) || state.is(AWBlocks.RIFT_GATE.get());
        }
        return false;
    }
}
