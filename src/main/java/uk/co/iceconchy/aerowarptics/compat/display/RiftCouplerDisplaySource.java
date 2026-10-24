package uk.co.iceconchy.aerowarptics.compat.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerReadings;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Reports a Rift Coupler to a Display Link, one value per link like every other source here.
 *
 * <p>What it is, whose flotilla it sails in, how many of its escorts are ready - as words for a sign, or
 * as a bare number for a nixie tube - and how it stands. The roster, one ship to a line, is a separate
 * source: {@link RiftCouplerRosterDisplaySource}.
 */
public class RiftCouplerDisplaySource extends AWDisplaySource<RiftCouplerBlockEntity> {

    /** The registry path. Baked into every configured link's save data; do not rename. */
    public static final String SOURCE_ID = "rift_coupler";

    RiftCouplerDisplaySource() {
        super(SOURCE_ID, RiftCouplerBlockEntity.class, () -> AWBlockEntities.RIFT_COUPLER.get(),
                "role", "flotilla", "ready", "ready_count", "status");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode == 3;
    }

    @Override
    protected MutableComponent line(RiftCouplerBlockEntity coupler, int mode) {
        if (!(coupler.getLevel() instanceof ServerLevel level)) {
            return DisplaySource.EMPTY_LINE;
        }
        CouplerReadings readings = CouplerReadings.of(level, coupler);
        return switch (mode) {
            case 0 -> AWLang.translate(readings.role().translationKey()).component();
            case 1 -> readings.flotilla() == null
                    ? AWLang.translate("display_source.rift_coupler.no_flotilla").component()
                    : readings.flotilla().isBlank()
                    ? AWLang.translate("gui.rift_navigation.unnamed_ship").component()
                    : text(readings.flotilla());
            case 2 -> AWLang.translate("display_source.rift_coupler.ready", readings.ready(), readings.escorts())
                    .component();
            case 3 -> number(readings.ready());
            default -> AWLang.translate(readings.status()).component();
        };
    }
}
