package uk.co.iceconchy.aerowarptics.gate;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block of Rift Gate frame.
 *
 * <p>Deliberately inert. It has no block entity, no state and nothing to say: the frame of a gate is a
 * wall, and a wall of forty blocks does not need forty machines in it. Everything a gate knows lives
 * on its controller, which finds the frame by looking rather than by being told.
 *
 * <p>That also means a player can build the ring in any order, out of as many pieces as they like,
 * and take it apart the same way. The controller notices within a second either way.
 */
public class RiftGateFrameBlock extends Block {

    public static final MapCodec<RiftGateFrameBlock> CODEC = simpleCodec(RiftGateFrameBlock::new);

    public RiftGateFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }
}
