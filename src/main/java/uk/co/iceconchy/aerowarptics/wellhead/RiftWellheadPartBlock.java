package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;

/**
 * The casing and the glass of a formed Rift Wellhead: every block of its shell that is neither the collar nor a
 * port. No block entity, no item, no loot - see {@link WellheadShellBlock} for what it does and does not do.
 *
 * <p>{@link #GLASS} says which of the two it stands for, so it lets the light through that the glass did and
 * goes back to the right block. It could be worked out from where the block stands, but only with the frame's
 * size and the collar's side to hand, and a block that has lost its collar has neither.
 */
public class RiftWellheadPartBlock extends WellheadShellBlock {

    public static final MapCodec<RiftWellheadPartBlock> CODEC = simpleCodec(RiftWellheadPartBlock::new);

    public static final BooleanProperty GLASS = BooleanProperty.create("glass");

    public RiftWellheadPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(X, 0).setValue(Y, 0).setValue(Z, 0).setValue(GLASS, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GLASS);
    }

    public BlockState at(Vec3i offset, boolean glass) {
        return at(offset).setValue(GLASS, glass);
    }

    @Override
    public BlockState original(BlockState state) {
        return state.getValue(GLASS) ? AWBlocks.WARP_GLASS.get().defaultBlockState()
                : AWBlocks.RIFT_CASING.get().defaultBlockState();
    }

    // Glass that stopped passing light when its frame formed would put the fissure in the dark.

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(GLASS);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(GLASS) ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction side) {
        return state.getValue(GLASS) && neighbour.getBlock() instanceof RiftWellheadPartBlock
                && neighbour.getValue(GLASS) || super.skipRendering(state, neighbour, side);
    }
}
