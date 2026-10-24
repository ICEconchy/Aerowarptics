package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;

/**
 * Reports a Rift Ward to a Display Link: whether its field stands, what it has banked, how wide the field is set,
 * and how many rifts it has turned aside. The charge on a board by the gate is the reading a base wants: a ward
 * runs down quietly, and the first anybody otherwise knows of it is a stranger's ship in the courtyard.
 */
public class RiftWardDisplaySource extends AWDisplaySource<RiftWardBlockEntity> {

    /** Registry path, {@code aerowarptics:rift_ward} in full. */
    public static final String SOURCE_ID = "rift_ward";

    static final int MODE_STATE = 0;
    static final int MODE_CHARGE = 1;
    static final int MODE_RADIUS = 2;
    static final int MODE_TURNED = 3;

    RiftWardDisplaySource() {
        super(SOURCE_ID, RiftWardBlockEntity.class, () -> AWBlockEntities.RIFT_WARD.get(),
                "state", "charge", "radius", "turned");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode != MODE_STATE;
    }

    /**
     * A link on any block of the machine reads the machine: the master is walled in by the rest of it, so a link
     * that could only read the master could never be put anywhere.
     */
    @Override
    public void registerExtras() {
        com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK.add(
                uk.co.iceconchy.aerowarptics.registry.AWBlocks.RIFT_WARD_PART.get(), this);
        com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK.add(
                uk.co.iceconchy.aerowarptics.registry.AWBlocks.RIFT_WARD_BASE.get(), this);
    }

    @Override
    protected net.minecraft.world.level.block.entity.BlockEntity source(
            com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context) {
        net.minecraft.core.BlockPos master = uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock.masterOf(
                context.level(), context.getSourcePos());
        return master == null ? super.source(context) : context.level().getBlockEntity(master);
    }

    @Override
    protected MutableComponent line(RiftWardBlockEntity ward, int mode) {
        return switch (mode) {
            case MODE_STATE -> AWLang.translate("display_source." + ward.stateKey()).component();
            case MODE_CHARGE -> number((int) Math.round(ward.charge()));
            case MODE_RADIUS -> number(ward.radius());
            case MODE_TURNED -> number(ward.turned());
            default -> EMPTY_LINE;
        };
    }
}
