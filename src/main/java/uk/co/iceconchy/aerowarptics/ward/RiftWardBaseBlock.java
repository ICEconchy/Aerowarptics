package uk.co.iceconchy.aerowarptics.ward;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * The eight blocks round a Rift Ward's master on its floor: where a shaft turns it, at the middle of each side, and where pipes join
 * its tank - see {@link RelicBaseBlock}.
 */
public class RiftWardBaseBlock extends RelicBaseBlock {

    public static final MapCodec<RiftWardBaseBlock> CODEC = simpleCodec(RiftWardBaseBlock::new);

    public RiftWardBaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean isMaster(BlockState state) {
        return state.getBlock() instanceof RiftWardBlock;
    }

    @Override
    protected ItemStack machine() {
        return new ItemStack(AWItems.RIFT_WARD.get());
    }

    @Override
    protected void signal(Level level, BlockPos master) {
        // A ward takes no orders from a circuit; it only gives it readings.
    }
    // ------------------------------------------------------------------ what it tells a circuit

    /** A pulse from any block of the machine when it turns something aside - see {@link RiftWardBlock#pulse}. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return stillValid(level, pos, state) ? RiftWardBlock.pulse(level, master(pos, state)) : 0;
    }

    /** A comparator against any block of the machine reads how full its charge is. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return stillValid(level, pos, state) ? RiftWardBlock.reading(level, master(pos, state)) : 0;
    }
}
