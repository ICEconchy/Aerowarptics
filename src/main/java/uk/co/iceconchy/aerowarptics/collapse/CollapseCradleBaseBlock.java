package uk.co.iceconchy.aerowarptics.collapse;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * The eight blocks round a Collapse Cradle's master on its floor: where a shaft turns it, at the middle of
 * each side, and where pipes
 * and funnels join it - see {@link RelicBaseBlock}.
 */
public class CollapseCradleBaseBlock extends RelicBaseBlock {

    public static final MapCodec<CollapseCradleBaseBlock> CODEC = simpleCodec(CollapseCradleBaseBlock::new);

    public CollapseCradleBaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean isMaster(BlockState state) {
        return state.getBlock() instanceof CollapseCradleBlock;
    }

    @Override
    protected ItemStack machine() {
        return new ItemStack(AWItems.COLLAPSE_CRADLE.get());
    }

    @Override
    protected void signal(Level level, BlockPos master) {
        CollapseCradleBlock.readSignal(level, master);
    }
}
