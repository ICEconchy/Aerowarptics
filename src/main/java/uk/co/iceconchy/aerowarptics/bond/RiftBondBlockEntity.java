package uk.co.iceconchy.aerowarptics.bond;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

import java.util.UUID;

/**
 * A Rift Bond: the bond it is tuned to, and nothing else.
 *
 * <p>It does not tick and keeps nothing on the client. All it has to do is be findable by the bond it
 * carries, so it reports to {@link RiftBonds} whenever it starts or stops standing, and whenever that bond
 * changes - a load from disk included, because Sable and Dimensional Sable both rebuild a ship's block
 * entities from their saved data.
 */
public class RiftBondBlockEntity extends BlockEntity {

    private static final String BOND = "Bond";

    @Nullable
    private UUID bond;

    public RiftBondBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_BOND.get(), pos, state);
    }

    /** The bond this block is tuned to, or {@code null} for one that has not been given one yet. */
    @Nullable
    public UUID bond() {
        return bond;
    }

    /** Tunes this block to a bond, leaving whichever it was in. */
    public void tune(UUID to) {
        if (to.equals(bond)) {
            return;
        }
        if (standing() && bond != null) {
            RiftBonds.leave(bond, this);
        }
        bond = to;
        if (standing()) {
            RiftBonds.join(to, this);
        }
        setChanged();
    }

    private boolean standing() {
        return level != null && !level.isClientSide && !isRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (standing() && bond != null) {
            RiftBonds.join(bond, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && bond != null) {
            RiftBonds.leave(bond, this);
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (standing() && bond != null) {
            RiftBonds.join(bond, this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bond != null) {
            tag.putUUID(BOND, bond);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID read = tag.hasUUID(BOND) ? tag.getUUID(BOND) : null;
        if (read != null) {
            // Through tune(), so a block already standing when its data is rewritten moves bonds properly.
            tune(read);
        }
    }
}
