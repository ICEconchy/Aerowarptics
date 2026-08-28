package uk.co.iceconchy.aerowarptics.compat.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.List;

/**
 * The eight-mode readout a Rift Drive offers a Display Link, factored out of {@link RiftDriveDisplaySource}
 * so that {@link RiftModulatorDisplaySource} - which reads the same drive at one remove, through
 * whichever Modulator is bolted beside it - describes it exactly the same way rather than keeping a
 * second copy of the same eight-case switch to drift out of step with the first.
 *
 * <p>Package-private: this is wiring between the two sources, not a third thing to register anywhere.
 */
final class DriveReadout {

    static final int MODE_TIER = 0;
    static final int MODE_STATE = 1;
    static final int MODE_CHARGE = 2;
    static final int MODE_PROGRESS = 3;
    static final int MODE_COOLDOWN = 4;
    static final int MODE_COURSE = 5;
    static final int MODE_HEADING = 6;
    static final int MODE_FAULT = 7;

    /** Mode names in scroll order - shared verbatim so the two sources' dropdowns read identically. */
    static final List<String> MODES =
            List.of("tier", "state", "charge", "progress", "cooldown", "course", "heading", "fault");

    private DriveReadout() {
    }

    static boolean numeric(int mode) {
        return mode == MODE_CHARGE || mode == MODE_PROGRESS || mode == MODE_COOLDOWN;
    }

    static MutableComponent line(RiftDriveBlockEntity drive, int mode) {
        return switch (mode) {
            case MODE_TIER -> AWLang.translate(drive.tier().translationKey()).component();
            case MODE_STATE -> AWLang.translate(drive.state().translationKey()).component();
            case MODE_CHARGE -> AWDisplaySource.percent(drive.charge());
            case MODE_PROGRESS -> AWDisplaySource.percent(drive.sequenceProgress());
            case MODE_COOLDOWN -> AWDisplaySource.number(AWDisplaySource.seconds(drive.cooldownTicks()));
            case MODE_COURSE -> course(drive);
            case MODE_HEADING -> AWLang.translate(drive.heading().translationKey()).component();
            case MODE_FAULT -> fault(drive);
            // Unreachable: the mode is clamped to the list this source declares.
            default -> DisplaySource.EMPTY_LINE;
        };
    }

    /** The destination the drive is committed to, or nothing at all. */
    private static MutableComponent course(RiftDriveBlockEntity drive) {
        String label = drive.destinationLabel();
        return label == null ? DisplaySource.EMPTY_LINE : AWDisplaySource.text(label);
    }

    /**
     * The last refusal, or nothing.
     *
     * <p>{@code WarpFailure.NONE} translates to an empty string already, but returning
     * {@code EMPTY_LINE} is the difference between a cleared sign and a blank one that Create still
     * considers written.
     */
    private static MutableComponent fault(RiftDriveBlockEntity drive) {
        WarpFailure failure = drive.lastFailure();
        return failure.isFailure()
                ? AWLang.translate(failure.translationKey()).component()
                : DisplaySource.EMPTY_LINE;
    }
}
