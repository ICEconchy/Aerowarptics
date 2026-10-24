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
 * <p>A Create kinetic machine: it takes a shaft on any face, draws stress, and needs a minimum RPM
 * before it will charge. One block class serves every tier; the tier is baked in at
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

    /**
     * A shaft may drive it from any face.
     *
     * <p>It used to be only the two ends of its own axis, which suited a lone drive but meant a drive
     * built into a frame of Drive Extenders had to sit exactly where one of those ends faced outwards.
     * Create links two kinetic blocks on any shared face both claim a shaft on, without asking about
     * either one's axis, and a drive is a consumer that cares how fast it is turned rather than which
     * way - so there was nothing but the restriction itself in the way. The facing still sets which way
     * the drive is drawn and which way its bow points.
     */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return true;
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        // Create draws a speed requirement on the goggles from this. A creative drive has none, and
        // showing one would be telling the player to fix something that is not broken.
        return tier.creative() ? IRotate.SpeedLevel.NONE : IRotate.SpeedLevel.MEDIUM;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        if (preferred == null || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            preferred = context.getNearestLookingDirection().getOpposite();
        }
        return defaultBlockState().setValue(FACING, preferred);
    }

    // -------------------------------------------------------------- extenders

    /**
     * Builds the drive into whatever frame of Drive Extenders is already waiting for it. Only on a real
     * arrival: which way a drive faces has no bearing on the frame it can form, so a wrench turning it
     * has nothing to re-form.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            DriveAssembly.tryForm(level, pos);
        }
    }

    /** Lets go of the drive's extenders when it goes, and offers them to any other drive that wants them. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        java.util.List<BlockPos> freed = java.util.List.of();
        if (!level.isClientSide && !newState.is(this)
                && level.getBlockEntity(pos) instanceof RiftDriveBlockEntity drive) {
            freed = DriveAssembly.unform(level, drive);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!freed.isEmpty()) {
            DriveAssembly.offerTo(level, freed);
        }
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

    // --------------------------------------------------------------- redstone

    /** A comparator reads what the drive is doing - see {@link DriveSignal} for the scale. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        RiftDriveBlockEntity drive = getBlockEntity(level, pos);
        return drive == null ? DriveSignal.IDLE : drive.comparatorSignal();
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        RiftDriveBlockEntity drive = getBlockEntity(level, pos);
        // Holding extenders means building a frame round this drive, which starts by laying them against it.
        if (drive == null || DriveExtenderBlock.isBuilding(player)) {
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
