package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.Locale;

/**
 * Reports a Rift Chute to a Display Link.
 *
 * <p>A chute's most useful reading by far is why nothing is moving, which is exactly what its own
 * screen puts in a pill along the bottom: unpaired, far end not loaded, nowhere to put it, out of
 * essence, waiting for a warp. {@code STATE} is that same sentence, through the same translation
 * keys, so a board by the sorting room and a pair of goggles never disagree.
 *
 * <p>{@code HELD} is whatever has arrived and not been collected. It clears when the tray is empty
 * rather than writing "None" &mdash; a chute in normal service is empty most of the time, and a row
 * of "None" down a Display Board tells nobody anything.
 */
public class RiftChuteDisplaySource extends AWDisplaySource<RiftChuteBlockEntity> {

    /** Registry path, {@code aerowarptics:rift_chute} in full. */
    public static final String SOURCE_ID = "rift_chute";

    static final int MODE_STATE = 0;
    static final int MODE_LINK = 1;
    static final int MODE_ESSENCE = 2;
    static final int MODE_FILL = 3;
    static final int MODE_HELD = 4;

    RiftChuteDisplaySource() {
        super(SOURCE_ID, RiftChuteBlockEntity.class, () -> AWBlockEntities.RIFT_CHUTE.get(),
                "state", "link", "essence", "fill", "held");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode == MODE_ESSENCE || mode == MODE_FILL;
    }

    @Override
    protected MutableComponent line(RiftChuteBlockEntity chute, int mode) {
        return switch (mode) {
            // The chute's own screen builds this key the same way, from the reason's name.
            case MODE_STATE -> AWLang.translate("gui.rift_chute.reason."
                    + chute.lastReason().name().toLowerCase(Locale.ROOT)).component();
            case MODE_LINK -> AWLang.translate(chute.isRiftOpen()
                    ? "gui.rift_chute.rift_open"
                    : "gui.rift_chute.rift_collapsed").component();
            case MODE_ESSENCE -> number(chute.contents().getAmount());
            case MODE_FILL -> percent(chute.contents().getAmount()
                    / (float) RiftChuteBlockEntity.CAPACITY);
            case MODE_HELD -> held(chute);
            // Unreachable: the mode is clamped to the list this source declares.
            default -> EMPTY_LINE;
        };
    }

    /** What is sitting in the tray, or nothing at all. */
    private static MutableComponent held(RiftChuteBlockEntity chute) {
        ItemStack stack = chute.held();
        if (stack.isEmpty()) {
            return EMPTY_LINE;
        }
        if (stack.getCount() == 1) {
            return stack.getHoverName().copy();
        }
        // The name is passed through as a component rather than a string: resolving it here would
        // resolve it on the server, where a dedicated server has no mod language files loaded and
        // an item would come out as a raw translation key.
        return AWLang.translate("display_source.rift_chute.held",
                stack.getCount(), stack.getHoverName()).component();
    }
}
