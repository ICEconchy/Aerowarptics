package uk.co.iceconchy.aerowarptics.stabiliser;

/**
 * What a Spatial Stabiliser is doing, as the goggles report it.
 *
 * <p>An enum with a translation key rather than a boolean, for the reason {@code WarpFailure} is one:
 * a stabiliser that is switched on and doing nothing looks exactly like one that is working, until the
 * ship shoots upward. Every way it can be idle is named, so a pilot can tell which one to fix.
 *
 * <p>Sent by ordinal, so a new one goes on the end.
 */
public enum StabiliserStatus {
    /** Powered, linked, aboard, and holding the hull. */
    CUSHIONING("cushioning"),
    /** No redstone signal. The switch is the only control it has. */
    OFF("off"),
    /** Not beside a Rift Drive, nor in a Collapse Cradle's ring, so there is nothing for it to steady. */
    NOT_LINKED("not_linked"),
    /** Beside a drive, but the two are not aboard an airship. */
    GROUNDED("grounded"),
    /** The drive is flying a warp, which commands the hull's velocity outright. */
    STANDING_DOWN("standing_down"),
    /** Another stabiliser aboard already has this hull. See {@link HullClaims}. */
    DEFERRING("deferring"),
    /** In a Collapse Cradle's ring, switched on, and counted by it: its second job, between collapses. */
    ATTENDING("attending"),
    /** In the ring of a cradle that is collapsing something now. */
    STEADYING("steadying");

    private final String name;

    StabiliserStatus(String name) {
        this.name = name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "gui.spatial_stabiliser.status." + name;
    }

    /** Whether the goggles should draw this line as a fault rather than as ordinary information. */
    public boolean isFault() {
        return this == NOT_LINKED || this == GROUNDED;
    }

    /** Whether the stabiliser is doing one of its two jobs, which the goggles draw as they draw a machine at work. */
    public boolean isWorking() {
        return this == CUSHIONING || this == ATTENDING || this == STEADYING;
    }

    public static StabiliserStatus byIndex(int index) {
        StabiliserStatus[] values = values();
        return index >= 0 && index < values.length ? values[index] : OFF;
    }
}
