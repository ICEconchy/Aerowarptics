package uk.co.iceconchy.aerowarptics.sunderer;

/**
 * One sundering, in progress: where it is in its timeline, and whether it has failed.
 *
 * <p>Free of Minecraft, as {@code CollapseRitual} is, so a test can drive one tick by tick: the Sunderer feeds it
 * the conditions each tick and does what it says.
 *
 * <h2>The beats</h2>
 * <ol>
 *   <li><b>Reaching</b>: a point opens in the ring and searches for a far side, and finds none. Not yet committed - a Sunderer
 *       that stops turning, or a ring that is broken, here <em>misfires</em>: the aperture closes, the machine
 *       survives and half its charge is handed back.</li>
 *   <li><b>Straining</b>: committed. The burst third of the frame lifts out, orbits the black seed the point became,
 *       and is swallowed. A ring broken now ruptures at once; a shaft
 *       that stops is waited on for {@link #STALL_LIMIT} ticks and then ruptures.</li>
 *   <li><b>The Hand</b>, when it was rolled: inside the straining, whose clock it holds still.</li>
 *   <li><b>Breach</b>: nothing can stop it now. The ring comes down either way.</li>
 * </ol>
 *
 * <p>The rules about <em>not</em> moving are the collapse's: a sundering whose chunk unloads is simply not
 * ticked, and for a grace window after a resume it holds whatever the speed says, because Create reports no
 * speed for the first ticks after a reload. A pause never resolves.
 */
public final class SundererSequence {

    /** Ticks the aperture spends reaching for a far side. */
    public static final int REACHING = 160;
    /** Ticks of straining - the frame lifting, orbiting and being swallowed - not counting the hand. */
    public static final int STRAINING = 500;
    /** How far into the straining the hand comes, when it comes: while the frame is orbiting. */
    public static final int HAND_AFTER = 260;
    /** Ticks the hand takes, from the first bulge to the slit sealing. */
    public static final int HAND = 160;
    /** Ticks from the cracks meeting to the fissure hanging in what is left of the ring. */
    public static final int BREACH = 60;

    /** How long a committed sundering waits on a stopped shaft before it ruptures. */
    public static final int STALL_LIMIT = 40;
    /** How long a sundering still reaching waits on a stopped shaft before it misfires. */
    public static final int REACH_STALL_LIMIT = 10;

    /** What a tick of the sequence is told. */
    public record Conditions(boolean turning, boolean ringWhole) {
    }

    public enum Step {
        /** Nothing moved: a grace window, or a committed sundering waiting on its shaft. */
        HELD,
        ADVANCED,
        /** Failed before committing: the machine survives and half the charge is handed back. */
        MISFIRED,
        /** Failed after committing: the ground goes and nothing is torn. */
        RUPTURED,
        /** The last tick: the breach is over and the fissure is to be set down. */
        TORN
    }

    public enum Beat {
        REACHING, STRAINING, HAND, BREACH, DONE;

        /** Whether a failure now is a rupture rather than a misfire. */
        public boolean committed() {
            return this != REACHING;
        }
    }

    private final boolean hand;
    private int elapsed;
    private int stalled;
    private int sinceResume;

    /** A sundering starting now. */
    public SundererSequence(boolean hand) {
        this(hand, 0, 0);
    }

    /** A sundering read back from disk. It resumes, so it starts in its grace window. */
    public SundererSequence(boolean hand, int elapsed, int stalled) {
        this.hand = hand;
        this.elapsed = Math.max(0, Math.min(elapsed, length(hand)));
        this.stalled = Math.max(0, stalled);
        this.sinceResume = elapsed > 0 ? 0 : Integer.MAX_VALUE;
    }

    /** Marks a resume, as after a reload: the next {@code grace} ticks hold. */
    public void resume() {
        sinceResume = 0;
    }

    public Step tick(Conditions conditions, int grace) {
        if (elapsed >= length(hand)) {
            return Step.TORN;
        }
        if (sinceResume < grace) {
            sinceResume++;
            return Step.HELD;
        }
        Beat beat = beat();
        if (beat == Beat.REACHING) {
            if (!conditions.ringWhole()) {
                return Step.MISFIRED;
            }
            // A shaft that drops out for a moment is not a stopped one: Create reports no speed for a tick whenever
            // the Sunderer's own light changes its state. Held a moment; only a real stop misfires.
            if (!conditions.turning()) {
                stalled++;
                return stalled > REACH_STALL_LIMIT ? Step.MISFIRED : Step.HELD;
            }
            stalled = 0;
        } else if (beat != Beat.BREACH) {
            if (!conditions.ringWhole()) {
                return Step.RUPTURED;
            }
            if (!conditions.turning()) {
                stalled++;
                return stalled > STALL_LIMIT ? Step.RUPTURED : Step.HELD;
            }
            stalled = 0;
        }
        elapsed++;
        return elapsed >= length(hand) ? Step.TORN : Step.ADVANCED;
    }

    /** What a misfire hands back of the charge it spent. */
    public static int refund(int charge) {
        return Math.max(0, charge) / 2;
    }

    public Beat beat() {
        return at(elapsed, hand);
    }

    public boolean hand() {
        return hand;
    }

    public int elapsed() {
        return elapsed;
    }

    public int stalled() {
        return stalled;
    }

    public int length() {
        return length(hand);
    }

    // ------------------------------------------------------------------ the timeline

    /** The whole sundering's length in ticks. */
    public static int length(boolean hand) {
        return REACHING + STRAINING + (hand ? HAND : 0) + BREACH;
    }

    /** The tick the hand's beat begins. */
    public static int handStart() {
        return REACHING + HAND_AFTER;
    }

    /** The tick the breach begins. */
    public static int breachStart(boolean hand) {
        return REACHING + STRAINING + (hand ? HAND : 0);
    }

    /** The beat a sundering is in at a given tick, partial ticks allowed. */
    public static Beat at(double tick, boolean hand) {
        if (tick >= length(hand)) {
            return Beat.DONE;
        }
        if (tick >= breachStart(hand)) {
            return Beat.BREACH;
        }
        if (tick < REACHING) {
            return Beat.REACHING;
        }
        if (hand && tick >= handStart() && tick < handStart() + HAND) {
            return Beat.HAND;
        }
        return Beat.STRAINING;
    }
}
