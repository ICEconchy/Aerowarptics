package uk.co.iceconchy.aerowarptics.drive;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Drive block.
 *
 * <p>A Create kinetic machine: it takes a shaft on the axis it faces, draws stress, and needs a
 * minimum RPM before it will charge. One block class serves every tier; the tier is baked in at
 * registration so a new tier needs no new logic.
 */
public class RiftDriveBlock extends DirectionalKineticBlock implements IBE<RiftDriveBlockEntity> {

    public static final com.mojang.serialization.MapCodec<RiftDriveBlock> CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                            propertiesCodec(),
                            net.minecraft.util.StringRepresentable.fromEnum(RiftDriveTier::values)
                                    .fieldOf("tier").forGetter(RiftDriveBlock::tier))
                    .apply(instance, RiftDriveBlock::new));

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    private final RiftDriveTier tier;

    public RiftDriveBlock(Properties properties, RiftDriveTier tier) {
        super(properties);
        this.tier = tier;
    }

    public RiftDriveTier tier() {
        return tier;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalKineticBlock> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------ create hooks

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // Power enters along the drive's own axis, from either end.
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

    // ------------------------------------------------------------- appearance

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Drawn by the GeckoLib renderer rather than a baked model.
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        RiftDriveBlockEntity drive = getBlockEntity(level, pos);
        if (drive == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            AWClientHooks.requestRiftNavigation(drive);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------------------------------------------------------- block entity

    @Override
    public Class<RiftDriveBlockEntity> getBlockEntityClass() {
        return RiftDriveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftDriveBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_DRIVE.get();
    }
}
