package uk.co.iceconchy.aerowarptics.probe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Probe block.
 *
 * <p>Right-clicking opens its panel, in the same way the Astrolabe's chart opens: the client asks the
 * server for the current state and the server decides what to send back. Nothing about a sounding is
 * worked out on the client, because a sounding is a request to generate terrain and that is not a
 * decision a client gets to make.
 */
public class RiftProbeBlock extends Block implements IBE<RiftProbeBlockEntity> {

    public static final MapCodec<RiftProbeBlock> CODEC = simpleCodec(RiftProbeBlock::new);

    /** A squat instrument: a heavy plinth under a dish, so it reads as something that looks outward. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 6.0D, 15.0D),
            Block.box(4.0D, 6.0D, 4.0D, 12.0D, 11.0D, 12.0D),
            Block.box(2.0D, 11.0D, 2.0D, 14.0D, 14.0D, 14.0D));

    public RiftProbeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            AWClientHooks.requestProbePanel(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<RiftProbeBlockEntity> getBlockEntityClass() {
        return RiftProbeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftProbeBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_PROBE.get();
    }
}
