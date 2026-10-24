package uk.co.iceconchy.aerowarptics.drive;

/**
 * What a comparator reads off a Rift Drive.
 *
 * <p>A drive already says what it is doing on its console, its goggles and a Display Link, and none of
 * those is something a circuit can act on. This is the same answer as a redstone strength, so a ship can
 * light a lamp while its drive recovers, sound a horn as it winds up, or throw its own lever the moment
 * the charge is full.
 *
 * <pre>
 *    0  idle - not turning, or not turning fast enough to charge
 *    1  recovering - cooling down after a warp, or clearing a fault
 *    2  charging, in twelve steps
 *   ..
 *   13
 *   14  fully charged, waiting for a signal
 *   15  at work - locked on, spinning up, in the rift, or coming out of it
 * </pre>
 *
 * <p>Kept free of Minecraft, like {@link RiftDriveState}, so the table above is what a test reads.
 */
public final class DriveSignal {

    /** Not turning. The one reading that is also what a comparator shows with no drive there at all. */
    public static final int IDLE = 0;

    /** Cooling down, or clearing a fault: the drive cannot be asked for anything yet. */
    public static final int RECOVERING = 1;

    /** The weakest and strongest readings a charging drive gives, an empty one and one about to fill. */
    public static final int CHARGING_LEAST = 2;
    public static final int CHARGING_MOST = 13;

    /** Fully charged and idle - the only reading a drive will take a launch signal on. */
    public static final int CHARGED = 14;

    /** From the moment a course is locked until the hull has settled at the far end. */
    public static final int WORKING = 15;

    private DriveSignal() {
    }

    /**
     * The comparator strength for a drive in this state, holding this much charge.
     *
     * <p>Fifteen from lock-on to arrival rather than only while the rings align: a warp is one thing to a
     * circuit, and a signal that dropped as the rift opened would read as the warp having stopped. A
     * fault shares the cooldown's reading because it is the same condition to anything wired to it -
     * the drive will not go, and will be able to again shortly without being touched.
     *
     * @param charge 0..1; only read while the drive is charging
     */
    public static int of(RiftDriveState state, float charge) {
        return switch (state) {
            case IDLE -> IDLE;
            case COOLDOWN, ERROR -> RECOVERING;
            case CHARGING -> charging(charge);
            case CHARGED -> CHARGED;
            case DESTINATION_SELECTED, STABILIZING, WARPING, ARRIVING -> WORKING;
        };
    }

    /**
     * A charging drive's reading: the charge cut into twelve even steps.
     *
     * <p>Floored, so the top step is reached at eleven twelfths and held until the drive is actually full,
     * at which point it stops being a charging drive and reads {@link #CHARGED}. Rounding would have shown
     * the last step's worth of charge as already there.
     */
    static int charging(float charge) {
        int steps = CHARGING_MOST - CHARGING_LEAST + 1;
        float clamped = Float.isNaN(charge) ? 0.0F : Math.max(0.0F, Math.min(1.0F, charge));
        return CHARGING_LEAST + Math.min(steps - 1, (int) (clamped * steps));
    }
}
