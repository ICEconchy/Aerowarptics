package uk.co.iceconchy.aerowarptics.drive;

import net.minecraft.util.StringRepresentable;

/**
 * How a Rift Drive flies: to a place somebody chose, or along a line until somebody stops it.
 *
 * <p>Both go through the same rift and the same corridor, and a redstone signal fires both. What
 * differs is where the far end is decided. A {@link #WAYPOINT} is planned in full before the aperture
 * opens, from a course set at an Astrolabe or a Rift Probe. A {@link #FREE_CRUISE} has no far end
 * until the pilot asks for one: the bearing locks as the rift opens, the hull holds inside the fold,
 * and letting go works out how far the ship has come and puts it down there.
 *
 * <p>A setting on the machine rather than a kind of course, because a cruise has nowhere to be set
 * <em>to</em> - there is no chart for it, and nothing about it needs anyone's permission but the
 * drive's.
 */
public enum DriveMode implements StringRepresentable {

    /** Fly the standing course: the drive's behaviour before cruising existed. */
    WAYPOINT("waypoint"),
    /** Lock the bow's bearing and cruise the fold until disengaged. */
    FREE_CRUISE("free_cruise");

    private final String name;

    DriveMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "drive.mode." + name;
    }

    public DriveMode next() {
        DriveMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Anything unknown reads as a waypoint, which is what every drive saved before cruising was. */
    public static DriveMode byIndex(int index) {
        DriveMode[] values = values();
        return index >= 0 && index < values.length ? values[index] : WAYPOINT;
    }
}
