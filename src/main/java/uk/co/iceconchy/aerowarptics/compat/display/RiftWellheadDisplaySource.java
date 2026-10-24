package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity;
import uk.co.iceconchy.aerowarptics.wellhead.WellheadReactor;

/**
 * A Rift Wellhead on a Display Link, read off its collar: how it is doing, how strained it is and where that is
 * going, and what is in its tank and its gullet.
 *
 * <p>The state is the reactor's band while the collar holds a frame, and the reason it holds none while it does
 * not - the same words the goggles use. The heading is the one worth putting on a board: a calm well whose
 * heading reads past ninety is going to run away, and says so here minutes before the comparator does.
 */
public class RiftWellheadDisplaySource extends AWDisplaySource<RiftCollarBlockEntity> {

    public static final String SOURCE_ID = "rift_wellhead";

    static final int MODE_STATE = 0;
    static final int MODE_STRAIN = 1;
    static final int MODE_HEADING = 2;
    static final int MODE_ESSENCE = 3;
    static final int MODE_GULLET = 4;

    RiftWellheadDisplaySource() {
        super(SOURCE_ID, RiftCollarBlockEntity.class, () -> AWBlockEntities.RIFT_COLLAR.get(),
                "state", "strain", "heading", "essence", "gullet");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode != MODE_STATE;
    }

    @Override
    protected MutableComponent line(RiftCollarBlockEntity collar, int mode) {
        return switch (mode) {
            case MODE_STATE -> AWLang.translate(collar.isFormed()
                    ? WellheadReactor.Band.of(collar.strain()).translationKey()
                    : collar.failure().translationKey()).component();
            case MODE_STRAIN -> percent((float) collar.strain());
            case MODE_HEADING -> percent((float) collar.heading());
            case MODE_ESSENCE -> number(collar.tank().getFluidAmount());
            case MODE_GULLET -> number((int) Math.round(collar.matter()));
            default -> EMPTY_LINE;
        };
    }
}
