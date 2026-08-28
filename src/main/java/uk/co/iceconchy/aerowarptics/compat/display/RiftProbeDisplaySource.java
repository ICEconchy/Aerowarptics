package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Reports a Rift Probe to a Display Link.
 *
 * <p>The probe is the machine a player most wants reported remotely, because a sounding is a wait:
 * you set a bearing and a range, pay for it, and then have nothing to look at until it comes back.
 * {@code STATE} and {@code PROGRESS} together are a scanning console, and {@code COST} against
 * {@code ESSENCE} says whether the next one can be afforded before walking over to find out.
 *
 * <p>The bearing is the compass point the probe is set to, worded as the probe's own panel words it
 * &mdash; "North-east", not a vector.
 */
public class RiftProbeDisplaySource extends AWDisplaySource<RiftProbeBlockEntity> {

    /** Registry path, {@code aerowarptics:rift_probe} in full. */
    public static final String SOURCE_ID = "rift_probe";

    static final int MODE_STATE = 0;
    static final int MODE_BEARING = 1;
    static final int MODE_RANGE = 2;
    static final int MODE_PROGRESS = 3;
    static final int MODE_ESSENCE = 4;
    static final int MODE_FILL = 5;
    static final int MODE_COST = 6;

    RiftProbeDisplaySource() {
        super(SOURCE_ID, RiftProbeBlockEntity.class, () -> AWBlockEntities.RIFT_PROBE.get(),
                "state", "bearing", "range", "progress", "essence", "fill", "cost");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode == MODE_RANGE || mode == MODE_PROGRESS
                || mode == MODE_ESSENCE || mode == MODE_FILL || mode == MODE_COST;
    }

    @Override
    protected MutableComponent line(RiftProbeBlockEntity probe, int mode) {
        return switch (mode) {
            case MODE_STATE -> AWLang.translate(probe.state().translationKey()).component();
            case MODE_BEARING -> AWLang.translate(probe.bearing().translationKey()).component();
            case MODE_RANGE -> number(probe.range());
            case MODE_PROGRESS -> percent(probe.reachProgress());
            case MODE_ESSENCE -> number(probe.essence());
            case MODE_FILL -> percent(probe.fillLevel());
            case MODE_COST -> number(probe.cost());
            // Unreachable: the mode is clamped to the list this source declares.
            default -> EMPTY_LINE;
        };
    }
}
