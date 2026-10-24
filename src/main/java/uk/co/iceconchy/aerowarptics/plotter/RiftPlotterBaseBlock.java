package uk.co.iceconchy.aerowarptics.plotter;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * The eight blocks round a Rift Plotter's master on its floor: where a shaft turns it, at the middle of each side, and where pipes join
 * its tank - see {@link RelicBaseBlock}.
 */
public class RiftPlotterBaseBlock extends RelicBaseBlock {

    public static final MapCodec<RiftPlotterBaseBlock> CODEC = simpleCodec(RiftPlotterBaseBlock::new);

    public RiftPlotterBaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean isMaster(BlockState state) {
        return state.getBlock() instanceof RiftPlotterBlock;
    }

    @Override
    protected ItemStack machine() {
        return new ItemStack(AWItems.RIFT_PLOTTER.get());
    }

    @Override
    protected void signal(Level level, BlockPos master) {
        RiftPlotterBlock.readSignal(level, master);
    }
}
