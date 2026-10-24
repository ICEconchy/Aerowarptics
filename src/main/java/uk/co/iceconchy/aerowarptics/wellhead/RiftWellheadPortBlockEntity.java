package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * A Rift Wellhead's port: nothing but somewhere for a pipe or a funnel to find - see
 * {@link RiftWellheadPortBlock}. It saves nothing and ticks nothing; the collar is the machine.
 */
public class RiftWellheadPortBlockEntity extends BlockEntity {

    public RiftWellheadPortBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_WELLHEAD_PORT.get(), pos, state);
    }

    /** The collar this port serves, or {@code null} if the frame has come apart. */
    @Nullable
    public RiftCollarBlockEntity collar() {
        return level == null ? null : WellheadShellBlock.collar(level, worldPosition, getBlockState());
    }

    public boolean isHatch() {
        return getBlockState().getValue(RiftWellheadPortBlock.HATCH);
    }
}
