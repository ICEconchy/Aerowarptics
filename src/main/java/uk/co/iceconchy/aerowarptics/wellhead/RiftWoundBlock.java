package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * A Rift Wound: what stands where a fissure was, after a Rift Wellhead's meltdown took it.
 *
 * <p>A landmark and nothing else. It gives no essence, rifts nothing, and no frame forms round it - the frame's
 * survey looks for a fissure, and this is not one. It is placed by a meltdown and by nothing else: no item, no
 * recipe, no loot. It is as hard as the fissure it replaces, nothing collides with it, and it refuses to move,
 * so the place a meltdown happened stays marked. An operator who wants one gone uses {@code /setblock}.
 *
 * <p>Unlike a fissure it is no secret: anybody can see and point at it. And unlike everything else in the mod it
 * is meant to look as if it should not be there - a crooked black crack that stutters from shape to shape,
 * reaches, bleeds red and hangs from a thread of dark. That is {@code WellheadSpectacle.drawWound}'s, and it is
 * deliberately not a held aperture, which is the tidy thing a wound must not look like. It gives no light.
 */
public class RiftWoundBlock extends Block implements EntityBlock {

    public static final MapCodec<RiftWoundBlock> CODEC = simpleCodec(RiftWoundBlock::new);

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public RiftWoundBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Nothing is drawn from a model: what a wound looks like is the client's rift effects, as a fissure's is. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftWoundBlockEntity(pos, state);
    }

    /** Only a client ticks one, and only to keep what it draws there alive. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? (ticked, pos, at, wound) -> AWClientHooks.tickWound(pos) : null;
    }
}
