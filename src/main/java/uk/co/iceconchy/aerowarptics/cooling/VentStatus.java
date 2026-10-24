package uk.co.iceconchy.aerowarptics.cooling;

/**
 * What a Cooling Vent is doing, as the goggles report it.
 *
 * <p>An enum with a translation key rather than a boolean, for the Spatial Stabiliser's reason: a vent that
 * is doing nothing looks exactly like one that is working until the drive takes its full time to cool.
 * Every way it can be idle is named, so a pilot can tell which one to fix.
 */
public enum VentStatus {
    /** Turning, beside a drive, and counted: the drive's next cooldown is shorter for it. */
    READY("ready"),
    /** The drive is cooling down from a warp and this vent is carrying the heat out. */
    VENTING("venting"),
    /** Not beside a Rift Drive, so there is no drive for it to cool. */
    NOT_LINKED("not_linked"),
    /** Not turning, or not fast enough. Unlike ice, a vent does nothing without a shaft. */
    STOPPED("stopped"),
    /** The drive already has as many coolers as it takes any good from. */
    SURPLUS("surplus");

    private final String name;

    VentStatus(String name) {
        this.name = name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "gui.cooling_vent.status." + name;
    }

    /** Whether this vent is shortening its drive's cooldown. */
    public boolean isWorking() {
        return this == READY || this == VENTING;
    }

    /** Whether the goggles should draw this line as a fault rather than as ordinary information. */
    public boolean isFault() {
        return this == NOT_LINKED || this == STOPPED;
    }

    public static VentStatus byIndex(int index) {
        VentStatus[] values = values();
        return index >= 0 && index < values.length ? values[index] : NOT_LINKED;
    }
}
