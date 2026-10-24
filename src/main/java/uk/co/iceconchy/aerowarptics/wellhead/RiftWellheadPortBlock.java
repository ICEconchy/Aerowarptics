package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;

/**
 * Where a formed Rift Wellhead meets the world: the hatch in the middle of its top, which blocks are fed
 * through, and the tap in the middle of each face the collar is not in, which essence is drawn from.
 *
 * <p>A port is a part with a block entity, and has one for no other reason than that Create's pipes and funnels
 * will not join a block without - the relics' floor learned it the hard way, when a machine whose body was all
 * inert parts took no pipe anywhere. The block entity holds nothing: what a pipe or a funnel finds here is the
 * collar's own tank or gullet, handed over by {@code AWCapabilities}.
 */
public class RiftWellheadPortBlock extends WellheadShellBlock implements EntityBlock {

    public static final MapCodec<RiftWellheadPortBlock> CODEC = simpleCodec(RiftWellheadPortBlock::new);

    /** The port on top, which takes blocks in. Every other port is a tap. */
    public static final BooleanProperty HATCH = BooleanProperty.create("hatch");

    public RiftWellheadPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(X, 0).setValue(Y, 0).setValue(Z, 0).setValue(HATCH, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HATCH);
    }

    public BlockState at(Vec3i offset, boolean hatch) {
        return at(offset).setValue(HATCH, hatch);
    }

    /** A face is glass to its middle until the frame forms, so glass is what a port goes back to. */
    @Override
    public BlockState original(BlockState state) {
        return AWBlocks.WARP_GLASS.get().defaultBlockState();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftWellheadPortBlockEntity(pos, state);
    }
}
