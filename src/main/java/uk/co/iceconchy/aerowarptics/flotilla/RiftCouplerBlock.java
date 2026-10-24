package uk.co.iceconchy.aerowarptics.flotilla;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.DriveAssembly;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Coupler block: a drive module that lets several ships fold as one.
 *
 * <p>Built the way the Rift Modulator is, because it is the same kind of thing - a fitting bolted to any
 * face of a Rift Drive, taking its facing from the drive, drawing no stress and turning no shaft. See
 * {@link RiftCouplerBlockEntity} for what it actually does.
 *
 * <p>Unlike the Modulator it remembers who placed it. Neither Sable nor Simulated knows who owns a ship,
 * and a flotilla needs somebody to answer "may this ship join", "may that one be cast off": the player
 * who fitted the coupler is the nearest thing to an owner the ship has, the same answer the Rift Gate
 * gives for who may rename it.
 */
public class RiftCouplerBlock extends DirectionalBlock implements IBE<RiftCouplerBlockEntity> {

    public static final com.mojang.serialization.MapCodec<RiftCouplerBlock> CODEC =
            simpleCodec(RiftCouplerBlock::new);

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    public RiftCouplerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Faces the way the drive beside it faces, for the Modulator's reason: this is a fitting for a
     * machine already built, not a fixture of its own to aim.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction driveFacing = adjacentDriveFacing(context.getLevel(), context.getClickedPos());
        Direction facing = driveFacing != null ? driveFacing : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Nullable
    private static Direction adjacentDriveFacing(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            RiftDriveBlockEntity drive = DriveAssembly.driveAt(level, pos.relative(direction));
            if (drive != null) {
                return drive.getBlockState().getValue(BlockStateProperties.FACING);
            }
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof RiftCouplerBlockEntity coupler) {
            coupler.setOwner(player.getUUID());
        }
    }

    // ------------------------------------------------------------- appearance

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            AWClientHooks.requestCouplerPanel(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------------------------------------------------------- block entity

    @Override
    public Class<RiftCouplerBlockEntity> getBlockEntityClass() {
        return RiftCouplerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftCouplerBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_COUPLER.get();
    }
}
