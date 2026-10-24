package uk.co.iceconchy.aerowarptics.rifting;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Brazier: a bowl of Rift Essence that holds a small rift open above it while it has a signal.
 *
 * <p>The second road to rifting (see {@link RiftingType}). A fissure is free and has to be found; this is
 * anywhere, once you have essence, and it costs by the second - {@code crafting.brazierBurnRate}, two
 * millibuckets by default - whether a fan is blowing through it or not.
 *
 * <p>{@link #POWERED} is the redstone; {@link #LIT} is whether its rift fire is actually burning, which the brazier's
 * own block entity decides from the signal and whether there is essence to burn. The fan reads only
 * {@code LIT}. They are two properties rather than one because a powered brazier that has run dry is a
 * different thing from a switched-off one, and the goggles say which.
 *
 * <p><b>Part height, on purpose.</b> Create's air current stops at any block whose collision shape covers
 * the middle of its face, the way a campfire's does not. The bowl stands nine pixels high, so a fan's air
 * passes over it and the rift above it catches the stream - a full block here would be a brazier no fan
 * could ever blow through.
 */
public class RiftBrazierBlock extends Block implements IBE<RiftBrazierBlockEntity> {

    public static final MapCodec<RiftBrazierBlock> CODEC = simpleCodec(RiftBrazierBlock::new);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 9.0D, 15.0D);

    public RiftBrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED,
                context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    /** A bucket pours in or scoops out, as it would at any tank; pipes and spouts use the capability. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<RiftBrazierBlockEntity> getBlockEntityClass() {
        return RiftBrazierBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftBrazierBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_BRAZIER.get();
    }
}
