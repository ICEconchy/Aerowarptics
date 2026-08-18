package uk.co.iceconchy.aerowarptics.anchor;

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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * Block form of a warp destination.
 *
 * <p>Placing it registers a {@link WarpAnchor} with the server-wide registry; breaking it withdraws
 * the record. Right-clicking opens the anchor's configuration screen for anyone allowed to edit it.
 */
public class WarpAnchorBlock extends HorizontalDirectionalBlock implements IBE<WarpAnchorBlockEntity>, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final com.mojang.serialization.MapCodec<WarpAnchorBlock> CODEC = simpleCodec(WarpAnchorBlock::new);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    public WarpAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                                     BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The visible model is the GeckoLib one drawn by WarpAnchorRenderer.
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        withBlockEntityDo(level, pos, be -> be.onPlaced(placer instanceof Player player ? player : null, null));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        WarpAnchorBlockEntity anchor = getBlockEntity(level, pos);
        if (anchor == null) {
            return InteractionResult.PASS;
        }
        if (!anchor.mayEdit(player)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            AWClientHooks.openWarpAnchorScreen(anchor);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<WarpAnchorBlockEntity> getBlockEntityClass() {
        return WarpAnchorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WarpAnchorBlockEntity> getBlockEntityType() {
        return AWBlockEntities.WARP_ANCHOR.get();
    }
}
