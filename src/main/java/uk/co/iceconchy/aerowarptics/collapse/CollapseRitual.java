package uk.co.iceconchy.aerowarptics.collapse;

/**
 * One collapse, in progress: how far through it is and how unstable it has become.
 *
 * <p>Free of Minecraft, so a test can drive a ritual tick by tick: the cradle feeds it the conditions each
 * tick and does what it says. The rules that matter most here are about <em>not</em> moving:
 * <ul>
 *   <li><b>A pause never resolves.</b> A ritual whose chunk unloads simply stops ticking, and one whose
 *       cradle is turned below the minimum is held - neither advances nor winds - so a player logging out, or a
 *       shaft snapping mid-ritual, can never spend a heart on an outcome nobody was there for.</li>
 *   <li><b>Resuming does not spike.</b> Create reports no speed for the first ticks after a reload, and a
 *       level with no players stops ticking block entities, so pauses will be routine. For a grace window
 *       after one ends the ritual holds whatever the speed says, rather than reading a reloading shaft as a
 *       cradle turned at nothing and winding instability up for it.</li>
 * </ul>
 */
public final class CollapseRitual {

    /** What a tick of the ritual is told. */
    public record Conditions(double speed, double minimumSpeed, double optimalSpeed, boolean storm,
                             double shortfall, int stabilisers, double imbalance) {
    }

    public enum Step {
        /** Nothing moved: a grace window, or turned too slowly to go on. */
        HELD,
        ADVANCED,
        /** The last tick: the ritual is over and its instability final. */
        COMPLETE
    }

    private final int length;
    private int elapsed;
    private double instability;
    private int sinceResume;

    /** A ritual starting now, running for {@code length} ticks. */
    public CollapseRitual(int length) {
        this(length, 0, 0.0D);
    }

    /** A ritual read back from disk. It resumes, so it starts in its grace window. */
    public CollapseRitual(int length, int elapsed, double instability) {
        this.length = Math.max(1, length);
        this.elapsed = Math.max(0, Math.min(elapsed, this.length));
        this.instability = CollapseInstability.clamp01(instability);
        this.sinceResume = elapsed > 0 ? 0 : Integer.MAX_VALUE;
    }

    /** Marks a resume, as after a reload: the next {@code grace} ticks hold. */
    public void resume() {
        sinceResume = 0;
    }

    public Step tick(Conditions conditions, int grace, CollapseInstability.Settings settings) {
        if (elapsed >= length) {
            return Step.COMPLETE;
        }
        if (sinceResume < grace) {
            sinceResume++;
            return Step.HELD;
        }
        if (Math.abs(conditions.speed()) < conditions.minimumSpeed()) {
            return Step.HELD;
        }
        double before = progress();
        elapsed++;
        if (CollapseRules.phase(before) == CollapseRules.Phase.WINDING) {
            double pressure = CollapseInstability.pressure(conditions.storm(), conditions.speed(),
                    conditions.optimalSpeed(), conditions.shortfall(), settings);
            double damping = CollapseInstability.damping(conditions.stabilisers(), conditions.imbalance(), settings);
            double winding = length * (CollapseRules.WINDING_END - CollapseRules.OPENING_END);
            instability = CollapseInstability.step(instability, CollapseInstability.rate(pressure, damping),
                    1.0D / Math.max(1.0D, winding));
        }
        return elapsed >= length ? Step.COMPLETE : Step.ADVANCED;
    }

    /**
     * Where the instability is heading: what it is now, plus what the rest of the winding would add if it went
     * on as these conditions find it. It is what a running collapse shows - a lopsided cage wobbles from the
     * moment the rift opens, not only once the damage is done - and, for conditions that never change, it is
     * exactly where the ritual ends.
     */
    public double heading(Conditions conditions, CollapseInstability.Settings settings) {
        double pressure = CollapseInstability.pressure(conditions.storm(), conditions.speed(),
                conditions.optimalSpeed(), conditions.shortfall(), settings);
        double damping = CollapseInstability.damping(conditions.stabilisers(), conditions.imbalance(), settings);
        double left = CollapseInstability.clamp01((CollapseRules.WINDING_END - Math.max(progress(), CollapseRules.OPENING_END))
                / (CollapseRules.WINDING_END - CollapseRules.OPENING_END));
        return CollapseInstability.clamp01(instability + CollapseInstability.rate(pressure, damping) * left);
    }

    public double progress() {
        return elapsed / (double) length;
    }

    public CollapseRules.Phase phase() {
        return CollapseRules.phase(progress());
    }

    public double instability() {
        return instability;
    }

    public int length() {
        return length;
    }

    public int elapsed() {
        return elapsed;
    }
}
