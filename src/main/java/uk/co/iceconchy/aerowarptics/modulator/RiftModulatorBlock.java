package uk.co.iceconchy.aerowarptics.modulator;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Modulator block.
 *
 * <p>Not kinetic and not a machine in Create's sense - it draws no stress and turns no shaft. All it
 * does is sit beside a Rift Drive, hold a little Rift Essence, and remember a colour and a look. See
 * {@link RiftModulatorBlockEntity} for what those actually do.
 *
 * <p>Takes all six {@link DirectionalBlock#FACING} values, like the drive it dresses, but for a purely
 * cosmetic reason: a model standing on a plinth reads as "placed the wrong way up" if it cannot at
 * least be turned to face sensibly on a wall or a ceiling, the way a Rift Drive itself can be mounted
 * any which way aboard a hull.
 */
public class RiftModulatorBlock extends DirectionalBlock implements IBE<RiftModulatorBlockEntity> {

    public static final com.mojang.serialization.MapCodec<RiftModulatorBlock> CODEC =
            simpleCodec(RiftModulatorBlock::new);

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);

    public RiftModulatorBlock(Properties properties) {
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
     * Takes its facing from whichever Rift Drive it is placed beside, rather than from where the
     * player is looking.
     *
     * <p>A pilot places this module to dress a drive they have already built, not to aim a new
     * fixture of its own - so there is nothing to aim, and asking them to line it up by eye would only
     * invite it to be mounted a quarter turn off from the machine it belongs to. Falls back to the
     * ordinary "face the player" default when there is no drive to read yet, so the block still places
     * sensibly on its own.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction driveFacing = adjacentDriveFacing(context.getLevel(), context.getClickedPos());
        Direction facing = driveFacing != null ? driveFacing : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** The facing of the first Rift Drive found among the six neighbours, or {@code null}. */
    private static Direction adjacentDriveFacing(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            if (level.getBlockEntity(neighbourPos) instanceof RiftDriveBlockEntity) {
                // Create's DirectionalKineticBlock shares vanilla's own FACING property rather than
                // declaring a second one, which is what lets this be read without importing the
                // drive's block class at all.
                return level.getBlockState(neighbourPos).getValue(BlockStateProperties.FACING);
            }
        }
        return null;
    }

    // ------------------------------------------------------------- appearance

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // Drawn by the GeckoLib renderer rather than a baked model, same as the drive it dresses.
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            AWClientHooks.requestModulatorPanel(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------------------------------------------------------- block entity

    @Override
    public Class<RiftModulatorBlockEntity> getBlockEntityClass() {
        return RiftModulatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftModulatorBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_MODULATOR.get();
    }
}
