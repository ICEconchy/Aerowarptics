package uk.co.iceconchy.aerowarptics.gate;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The control block of a Rift Gate: one piece of the ring that thinks.
 *
 * <p>Part of the frame rather than a console standing beside it, which is what lets the shaft and the
 * pipe that feed a gate bolt visibly onto the thing they are feeding. It counts as frame for the
 * purpose of closing the ring, so a player can put it anywhere in the circle - including a corner,
 * which is where most people put it.
 *
 * <p>Takes rotation on its own axis, exactly like a Rift Drive. It draws nothing at all until it is
 * holding an aperture open.
 */
public class RiftGateBlock extends DirectionalKineticBlock implements IBE<RiftGateBlockEntity> {

    public static final MapCodec<RiftGateBlock> CODEC = simpleCodec(RiftGateBlock::new);

    public RiftGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalKineticBlock> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------ create hooks

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.MEDIUM;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        if (preferred == null || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            preferred = context.getNearestLookingDirection().getOpposite();
        }
        return defaultBlockState().setValue(FACING, preferred);
    }

    // -------------------------------------------------------------- assembly

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // The usual order is to build the ring and put the controller in last, so trying here means
        // the gate forms the instant it is finished rather than on the next sweep.
        if (!level.isClientSide && !oldState.is(this)
                && level.getBlockEntity(pos) instanceof RiftGateBlockEntity gate) {
            gate.tryForm();
        }
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        RiftGateBlockEntity gate = getBlockEntity(level, pos);
        if (gate == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            AWClientHooks.requestGateDial(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------------------------------------------------- block entity

    @Override
    public Class<RiftGateBlockEntity> getBlockEntityClass() {
        return RiftGateBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftGateBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_GATE.get();
    }
}
