package uk.co.iceconchy.aerowarptics.plotter;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * The Rift Plotter: a relic that tears a rift in front of itself and opens the other end of it anywhere in the
 * world - round a target typed in as coordinates, at a distance, bearing and height the player sets on a model of
 * the ground there, facing whichever way they aim it. Whatever crosses the near rift while it stands - an airship,
 * a shell, a crate of TNT, a player - comes out of the far one.
 *
 * <p>The cradle's sibling, and built the same way: three blocks across and three high, placed whole from one item.
 * This is its master block, in the middle of the bottom layer - the block entity, the
 * renderer; the other twenty-six are {@link RiftPlotterPartBlock}s (see {@link PlotterStructure}). Essence goes in
 * at any of its blocks by pipe or bucket, a redstone rising edge at any of them replays the last plot, and an empty
 * hand opens the plotting table. See {@link RiftPlotterBlockEntity}.
 *
 * <p>It will not move: not by piston, contraption or ship. Its rifts are thrown from the ground it stands on, and a
 * machine of twenty-seven blocks carried by something that moves blocks one at a time arrives in pieces.
 */
public class RiftPlotterBlock extends KineticBlock implements IBE<RiftPlotterBlockEntity> {

    public static final MapCodec<RiftPlotterBlock> CODEC = simpleCodec(RiftPlotterBlock::new);

    public RiftPlotterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // One, on its underside: a line run in from below ends here, as one run into a side ends at that side's base.
        // The machine goes by the fastest of the five - see RelicFloor.
        return uk.co.iceconchy.aerowarptics.collapse.CradleStructure.shaftTowards(pos, pos, face);
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.MEDIUM;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide) {
            readSignal(level, pos);
        }
    }

    /** Whether any block of the machine has a signal at it, told to the master: a rising edge replays the plot. */
    static void readSignal(Level level, BlockPos master) {
        if (!(level.getBlockEntity(master) instanceof RiftPlotterBlockEntity plotter)) {
            return;
        }
        boolean powered = false;
        for (BlockPos cell : PlotterStructure.cells(master)) {
            if (level.hasNeighborSignal(cell)) {
                powered = true;
                break;
            }
        }
        plotter.signal(powered);
    }

    // ------------------------------------------------------------------ standing up

    /**
     * Where the player aims is the middle of the machine's floor; the rest of it needs to be clear, and on the
     * ground. Refused, the item says which - see {@link RiftPlotterItem}.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (aboard(level, pos) || !PlotterStructure.fits(pos, level.getMinBuildHeight(), level.getMaxBuildHeight(),
                cell -> level.getBlockState(cell).canBeReplaced())) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    /** Whether a position is on a ship: in a plot, which is where Sable keeps every hull's blocks. */
    static boolean aboard(Level level, BlockPos pos) {
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos) != null;
    }

    /**
     * The machine refuses to move, whoever asks - every block of it, as the cradle's does. Create's contraptions and
     * Simulated's assembler both ask this of each block they would take.
     */
    public static void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.simibubi.create.api.contraption.BlockMovementChecks.registerMovementAllowedCheck(
                (state, level, pos) -> state.getBlock() instanceof RiftPlotterBlock
                        || state.getBlock() instanceof RiftPlotterPartBlock
                        || state.getBlock() instanceof RiftPlotterBaseBlock
                        ? com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.FAIL
                        : com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS));
    }

    /** Whoever sets a plotter down owns the rifts it tears, which is what a Rift Ward asks of one. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof RiftPlotterBlockEntity plotter) {
            plotter.placedBy(player);
        }
    }

    /** Raises the parts a tick after the master is set down, and again whenever it loads - see the cradle's. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RiftPlotterPartBlock parts = AWBlocks.RIFT_PLOTTER_PART.get();
        RiftPlotterBaseBlock bases = AWBlocks.RIFT_PLOTTER_BASE.get();
        for (Vec3i offset : PlotterStructure.offsets()) {
            if (offset.equals(Vec3i.ZERO)) {
                continue;
            }
            BlockPos cell = pos.offset(offset);
            int[] place = PlotterStructure.placeOf(offset);
            // The floor is bases, which a pipe can join and, at the middle of each side, a shaft; the body above is inert parts.
            BlockState wanted = offset.getY() == 0 ? bases.at(place[0], place[2]) : parts.at(place[0], place[1], place[2]);
            BlockState there = level.getBlockState(cell);
            if (there == wanted) {
                continue;
            }
            // A machine set down before its floor was bases has inert parts there: those are its own, and are swapped.
            boolean ours = there.getBlock() instanceof RiftPlotterPartBlock && RiftPlotterPartBlock.master(cell, there).equals(pos);
            if (!ours && !there.canBeReplaced()) {
                Trace.log(Trace.PLOTTER, "plotter at {} has no room to stand: {} is at {}", Trace.pos(pos),
                        there.getBlock(), Trace.pos(cell));
                level.destroyBlock(pos, true);
                return;
            }
            level.setBlock(cell, wanted, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ hands

    /** A bucket fills or empties the tank; anything else opens the plotting table, as an empty hand does. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Opens the plotting table. The client asks for it, and the server answers with what the machine holds. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            AWClientHooks.requestPlotterPanel(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------ coming apart

    /** Going, the master stands its rifts down and takes its parts with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof RiftPlotterBlockEntity plotter) {
                plotter.broken();
            }
            if (!level.isClientSide) {
                for (BlockPos cell : PlotterStructure.cells(pos)) {
                    BlockState there = level.getBlockState(cell);
                    boolean part = there.getBlock() instanceof RiftPlotterPartBlock
                            && RiftPlotterPartBlock.master(cell, there).equals(pos);
                    boolean base = there.getBlock() instanceof RiftPlotterBaseBlock
                            && uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock.master(cell, there).equals(pos);
                    if (part || base) {
                        level.removeBlock(cell, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<RiftPlotterBlockEntity> getBlockEntityClass() {
        return RiftPlotterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftPlotterBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_PLOTTER.get();
    }
}
