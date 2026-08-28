package uk.co.iceconchy.aerowarptics.fissure;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * A Rift Fissure: a tear in space that was already there.
 *
 * <p>Everything else in this mod tears space on purpose and closes it afterwards. A fissure is the
 * opposite - somewhere the fabric went, on its own, a long time ago, and stayed gone. It sits in the
 * ruins that generate around it leaking essence into a room nobody has walked into for a very long
 * time.
 *
 * <h2>Being invisible</h2>
 * There is no model. The block renders nothing at all, and the tear a player sees through
 * {@link RiftGogglesItem} is drawn by the client's own rift effects, which is why it looks like every
 * other rift in the mod rather than like a special case.
 *
 * <p>The subtle half is the hit box. A block with a shape is one you can put your crosshair on, and
 * an outline drawn around thin air would give the whole thing away to a player with no goggles on -
 * so the shape is handed out per observer. Minecraft asks for it with the looking entity in the
 * collision context, which is exactly the question "who wants to know", and the answer here depends
 * on what they are wearing. Without goggles there is nothing to target, nothing to break, and no
 * outline; with them, all three.
 */
public class RiftFissureBlock extends Block implements IBE<RiftFissureBlockEntity> {

    public static final MapCodec<RiftFissureBlock> CODEC = simpleCodec(RiftFissureBlock::new);

    /** Most of the block, but not all of it: an outline flush with the grid reads as a box. */
    private static final VoxelShape SHAPE = Block.box(1.0D, 1.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public RiftFissureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Nothing is drawn from a model. What a fissure looks like is the client's rift effects. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        boolean seen = context instanceof EntityCollisionContext entity
                && entity.getEntity() instanceof Player player
                && RiftGogglesItem.isWorn(player);
        return seen ? SHAPE : Shapes.empty();
    }

    /**
     * Nothing collides with a fissure, whoever is looking.
     *
     * <p>Deliberately not tied to the goggles. A player who puts them on should start seeing the
     * thing, not start walking into it - and a wall that exists for some players and not others is
     * the kind of asymmetry that desynchronises a server.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    /**
     * The sparks around a tear, for the one player who can see it.
     *
     * <p>Ambient particles are normally drawn for everybody in range, which would put a cloud of
     * purple over an invisible block and undo the point of it. This one asks first.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        AWClientHooks.animateFissure(level, pos, random);
    }

    @Override
    public Class<RiftFissureBlockEntity> getBlockEntityClass() {
        return RiftFissureBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftFissureBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_FISSURE.get();
    }
}
