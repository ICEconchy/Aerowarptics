package uk.co.iceconchy.aerowarptics.ward;

/**
 * A deflection, as a scene: every curve the server times its cues by and every client draws from.
 *
 * <p>The flight is planned to the field's rim before the ship leaves its mooring, so nothing here decides
 * anything. It is the telling of it, laid over the ship's own time in the corridor so that the last beat meets the
 * hull coming out:
 *
 * <ol>
 *   <li><b>Reach</b> - over the place aimed at, a tear begins to open.</li>
 *   <li><b>Catch</b> - it stops half open. The ward's weight swings towards it and the chain goes taut; the
 *       light bends, the day dims, and the ward tolls once.</li>
 *   <li><b>Skid</b> - the tear is dragged across the sky to the rim.</li>
 *   <li><b>Break</b> - at the rim it bursts open, and the hull comes out of it.</li>
 *   <li><b>Settle</b> - the weight, let go, swings itself still.</li>
 * </ol>
 *
 * <p>A ward that cannot pay plays the first two and then <em>gives way</em>: the chain slackens and the tear
 * opens where it was aimed.
 *
 * <p>Every curve is one function of the scene's age and its length, continuous across each beat - a curve that
 * jumps at a beat is a piece that teleports on screen, and {@code WardChoreographyTest} walks every join. Free of
 * Minecraft.
 */
public final class WardChoreography {

    /** The shortest run-up to the break a scene is given, however short the ship's corridor. */
    public static final int SHORTEST = 40;

    /** Ticks after the break the scene goes on for: the weight swinging itself still. */
    public static final int SETTLE_TICKS = 240;

    /** Ticks the tear takes to burst from half open to wide. */
    public static final int BURST_TICKS = 8;

    /** Ticks the burst tear stands before the hull's own exit rift has taken its place. */
    public static final int GHOST_TICKS = 24;

    /** Ticks the ring of pushed air takes to run out. */
    public static final int RING_TICKS = 40;

    /** Ticks the dimming takes to lift once the tear has broken. */
    public static final int LIFT_TICKS = 80;

    /** The share of the run-up the tear spends reaching, and the share by which it has been caught. */
    private static final double REACH_END = 0.25D;
    private static final double CATCH_END = 0.40D;

    /** Ticks one swing of the freed weight takes, and how long the swinging takes to die to about a third. */
    private static final double SWING_PERIOD = 38.0D;
    private static final double SWING_DECAY = 70.0D;

    private WardChoreography() {
    }

    /** How long the scene runs up to its break: the ship's time in the corridor, but never too short to read. */
    public static int runUp(int corridorTicks) {
        return Math.max(SHORTEST, corridorTicks);
    }

    /** The tick the ward tolls on: as the tear is caught. */
    public static int tollAt(int total) {
        return (int) Math.round(total * REACH_END);
    }

    /** Whether the scene has run its whole length. */
    public static boolean over(double age, int total) {
        return age >= total + SETTLE_TICKS;
    }

    // ------------------------------------------------------------------ the beats, each 0..1

    /** The tear beginning to open over the place aimed at. */
    public static double reach(double age, int total) {
        return smooth(age / (total * REACH_END));
    }

    /** The tear being caught and held. */
    public static double caught(double age, int total) {
        return smooth((age - total * REACH_END) / (total * (CATCH_END - REACH_END)));
    }

    /** How far along its track from the place aimed at to the rim the tear has been dragged. */
    public static double skid(double age, int total) {
        return smooth((age - total * CATCH_END) / (total * (1.0D - CATCH_END)));
    }

    /** The tear bursting open at the rim. */
    public static double burst(double age, int total) {
        return smooth((age - total) / BURST_TICKS);
    }

    // ------------------------------------------------------------------ what is drawn from them

    /** How far open the tear is: half while it is held, wide once it breaks. */
    public static double tear(double age, int total) {
        return 0.5D * reach(age, total) + 0.5D * burst(age, total);
    }

    /** How much of the tear is still there to draw: all of it, until the hull's own rift has taken its place. */
    public static double ghost(double age, int total) {
        return 1.0D - smooth((age - total - BURST_TICKS) / GHOST_TICKS);
    }

    /** How hard the held tear strains against the field: the sparks it sheds, the light it bends. */
    public static double strain(double age, int total) {
        return caught(age, total) * (1.0D - burst(age, total));
    }

    /** How far the day is dimmed. */
    public static double dim(double age, int total) {
        return caught(age, total) * (1.0D - smooth((age - total) / LIFT_TICKS));
    }

    /**
     * How far the weight leans towards the tear, as a share of its furthest: pulled over as the tear is caught,
     * held there through the skid, and - let go at the break - swinging through its rest and back until it is still.
     */
    public static double lean(double age, int total) {
        if (age <= total) {
            double p = caught(age, total);
            // Out fast and then slowing against the chain: a weight snatched, not a weight pushed.
            return 1.0D - (1.0D - p) * (1.0D - p);
        }
        double since = age - total;
        return Math.cos(since * Math.PI * 2.0D / SWING_PERIOD) * Math.exp(-since / SWING_DECAY);
    }

    /** How far out the ring of pushed air has run, and how much of it is left. */
    public static double ring(double age, int total) {
        return clamp((age - total) / RING_TICKS);
    }

    public static double ringFade(double age, int total) {
        return age < total ? 0.0D : 1.0D - ring(age, total);
    }

    // ------------------------------------------------------------------ aboard

    /** How far the corridor is bent sideways for the crew: with the catch, until the break lets go of it. */
    public static double bend(double age, int total) {
        return strain(age, total);
    }

    /** How hard the deck shakes: once as the tear is caught, and once, harder, as it breaks. */
    public static double shake(double age, int total) {
        return Math.max(0.6D * pulse(age - total * REACH_END, 16.0D), pulse(age - total, 22.0D));
    }

    // ------------------------------------------------------------------ giving way

    /** A ward that cannot pay: the weight starts for the tear and is let drop. */
    public static double slackLean(double age, int total) {
        double p = caught(age, total);
        return 0.5D * Math.sin(Math.PI * p);
    }

    /** How slack the chain has gone, on a ward giving way. */
    public static double sag(double age, int total) {
        return smooth((age - total * (REACH_END + CATCH_END) * 0.5D) / (total * (CATCH_END - REACH_END) * 0.5D));
    }

    // ------------------------------------------------------------------ helpers

    /** A value along a track. */
    public static double along(double from, double to, double share) {
        return from + (to - from) * share;
    }

    private static double pulse(double since, double length) {
        if (since <= 0.0D) {
            return 0.0D;
        }
        // Up over two ticks rather than at once, so nothing on screen jumps on the beat.
        return since < 2.0D ? since / 2.0D : Math.max(0.0D, 1.0D - (since - 2.0D) / length);
    }

    private static double smooth(double share) {
        double t = clamp(share);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
