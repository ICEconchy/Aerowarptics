package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Reports a Rift Brazier to a Display Link: whether its rift is open, and how much it has left to burn - the
 * reading a rifting line wants on a wall before the brazier goes out in the middle of a batch.
 */
public class RiftBrazierDisplaySource extends AWDisplaySource<RiftBrazierBlockEntity> {

    /** Registry path, {@code aerowarptics:rift_brazier} in full. */
    public static final String SOURCE_ID = "rift_brazier";

    static final int MODE_STATE = 0;
    static final int MODE_ESSENCE = 1;
    static final int MODE_FILL = 2;

    RiftBrazierDisplaySource() {
        super(SOURCE_ID, RiftBrazierBlockEntity.class, () -> AWBlockEntities.RIFT_BRAZIER.get(),
                "state", "essence", "fill");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode != MODE_STATE;
    }

    @Override
    protected MutableComponent line(RiftBrazierBlockEntity brazier, int mode) {
        return switch (mode) {
            case MODE_STATE -> AWLang.translate(brazier.isLit()
                    ? "display_source.rift_brazier.lit"
                    : brazier.isPowered() ? "display_source.rift_brazier.dry" : "display_source.rift_brazier.unlit")
                    .component();
            case MODE_ESSENCE -> number(brazier.contents().getAmount());
            case MODE_FILL -> percent(brazier.fillLevel());
            default -> EMPTY_LINE;
        };
    }
}
