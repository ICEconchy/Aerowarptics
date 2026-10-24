package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * A Rift Wound's block entity: there so a client has something to tick, and for nothing else - see
 * {@link RiftWoundBlock}. It holds nothing and saves nothing.
 */
public class RiftWoundBlockEntity extends BlockEntity {

    public RiftWoundBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_WOUND.get(), pos, state);
    }
}
