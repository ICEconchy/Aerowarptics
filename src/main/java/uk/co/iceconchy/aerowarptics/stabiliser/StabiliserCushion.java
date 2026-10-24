package uk.co.iceconchy.aerowarptics.stabiliser;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * What a Spatial Stabiliser does to a hull's motion, one tick at a time.
 *
 * <p>Free of Minecraft and of Sable on purpose, the same way {@code AWLayout} is: a stabiliser that
 * quietly throws a ship across the map, or quietly does nothing at all, both look like "physics" in
 * game, and the only place either can be caught is a test that drives this with numbers.
 *
 * <h2>What "cushioning" means</h2>
 * Every tick Sable integrates whatever is acting on the hull - lift from a hot-air balloon, thrust
 * from a propeller, gravity, drag - into a new velocity. The stabiliser compares that velocity with the
 * one it left the hull at a tick ago, and hands back a fraction of the difference. The ship still goes
 * where its machinery is pushing it; it just gets there as though it were several times heavier. Then
 * a small share of whatever motion is left is bled away, so a push that never lets up settles at a
 * gentler speed instead of merely reaching the same one later.
 *
 * <p>Two things are deliberately <em>not</em> cushioned, and both are recognised rather than
 * configured away:
 * <ul>
 *   <li><b>Impacts.</b> A hull that hits a hillside changes speed in one tick by far more than any
 *       balloon can manage. Handing a fraction of that back would push the ship into the hillside
 *       again on the next tick, and the solver would spend the landing fighting the stabiliser. A
 *       change above the jolt limit passes through untouched.</li>
 *   <li><b>Teleports.</b> A warp or a Rift Gate moves a hull with {@code relocate}, and may hand it a
 *       new velocity as it does. The hull turning up somewhere its velocity could not have carried it
 *       is the tell, and that tick is taken as a fresh start rather than as a change to be smoothed.</li>
 * </ul>
 *
 * <p>Units are whatever the caller measures in, provided it is consistent from one tick to the next.
 * The block entity feeds it blocks per tick and radians per tick, which is what the jolt limits in the
 * config are written in.
 */
public final class StabiliserCushion {

    /**
     * How far, in blocks, a hull may stray from where its own velocity would have carried it before
     * the tick is read as a teleport.
     *
     * <p>Not a tunable. Honest flight misses the prediction by a fraction of a block - the pose's
     * origin is not quite the centre of mass, and a tick's acceleration is averaged - while the
     * shortest real relocation in the mod is a Rift Gate's refusal, which pushes a vehicle back by
     * {@code GateTraversal.CLEARANCE}. A false alarm costs one uncushioned tick, which nobody can see.
     */
    public static final double TELEPORT_MARGIN = 1.0D;

    /**
     * Corrections smaller than these are dropped.
     *
     * <p>Every nudge wakes the rigid body, and a moored ship that is woken every tick never goes back
     * to sleep - so a hull sitting still must not be touched at all, not touched by nothing. The cost
     * is that settling stops bleeding a drift once its share of the drift is this small, which leaves
     * the last few thousandths of a block a tick to the solver, whose job putting a nearly still body to sleep is.
     */
    public static final double LINEAR_EPSILON = 1.0e-4D;
    public static final double ANGULAR_EPSILON = 1.0e-5D;

    /** The most a cushion may absorb. At 1.0 a hull would never respond to anything again. */
    public static final double MAX_CUSHION = 0.95D;

    /** The most a settle may bleed away per tick. More than this is a brake, not a stabiliser. */
    public static final double MAX_SETTLE = 0.5D;

    /**
     * How hard the stabiliser works, as one bundle so a tick reads the config once.
     *
     * @param cushion       share of each tick's change in motion absorbed, 0 to {@link #MAX_CUSHION}
     * @param settle        share of the remaining motion bled away each tick, 0 to {@link #MAX_SETTLE}
     * @param joltLimit     a change in linear velocity bigger than this in one tick is an impact
     * @param spinJoltLimit a change in angular velocity bigger than this in one tick is an impact
     */
    public record Settings(double cushion, double settle, double joltLimit, double spinJoltLimit) {
        public Settings {
            cushion = clamp(cushion, 0.0D, MAX_CUSHION);
            settle = clamp(settle, 0.0D, MAX_SETTLE);
            joltLimit = Math.max(0.0D, finiteOr(joltLimit, 0.0D));
            spinJoltLimit = Math.max(0.0D, finiteOr(spinJoltLimit, 0.0D));
        }

        private static double clamp(double value, double least, double most) {
            return Math.max(least, Math.min(most, finiteOr(value, least)));
        }

        private static double finiteOr(double value, double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }
    }

    /** What to add to the hull's velocity this tick. Never {@code null}; {@link #NONE} when nothing. */
    public record Correction(Vector3dc linear, Vector3dc angular) {
        public static final Correction NONE = new Correction(new Vector3d(), new Vector3d());

        public boolean isNone() {
            return linear.lengthSquared() == 0.0D && angular.lengthSquared() == 0.0D;
        }
    }

    /** Why a tick handed back nothing, for the log and for the tests. */
    public enum Outcome {
        /** Cushioned, or had nothing worth correcting. */
        CUSHIONED,
        /** No tick to compare with: the first one after switching on, or after a release. */
        PRIMED,
        /** The hull changed speed too sharply to be anything but a collision. */
        IMPACT,
        /** The hull turned up somewhere its velocity could not have taken it. */
        TELEPORT
    }

    private boolean primed;
    private final Vector3d leftLinear = new Vector3d();
    private final Vector3d leftAngular = new Vector3d();
    private final Vector3d lastPosition = new Vector3d();
    private Outcome lastOutcome = Outcome.PRIMED;

    /**
     * Forgets the tick being compared with.
     *
     * <p>Called whenever the stabiliser stops acting - switched off, unlinked, standing down for a
     * warp. Without it, switching back on a minute later would compare the hull against a velocity
     * from a minute ago and "cushion" everything that had happened since in a single tick.
     */
    public void release() {
        primed = false;
    }

    public boolean primed() {
        return primed;
    }

    public Outcome lastOutcome() {
        return lastOutcome;
    }

    /**
     * Works out this tick's correction and remembers what the hull is left at.
     *
     * @param linearNow   the hull's linear velocity this tick
     * @param angularNow  the hull's angular velocity this tick
     * @param positionNow where the hull is this tick, in the same frame as the velocity
     * @return what to add to the hull's velocity - apply it, or the next tick compares against a
     *         velocity the hull never actually had
     */
    public Correction step(Vector3dc linearNow, Vector3dc angularNow, Vector3dc positionNow, Settings settings) {
        if (!linearNow.isFinite() || !angularNow.isFinite() || !positionNow.isFinite()) {
            // A NaN compared against a limit is never "too big", so it would sail past both checks
            // below and be handed to the solver as a correction. Nothing is safer than doing nothing.
            primed = false;
            lastOutcome = Outcome.PRIMED;
            return Correction.NONE;
        }
        if (!primed) {
            return restart(linearNow, angularNow, positionNow, Outcome.PRIMED);
        }
        if (teleported(positionNow, linearNow)) {
            return restart(linearNow, angularNow, positionNow, Outcome.TELEPORT);
        }
        if (leftLinear.distance(linearNow) > settings.joltLimit()
                || leftAngular.distance(angularNow) > settings.spinJoltLimit()) {
            return restart(linearNow, angularNow, positionNow, Outcome.IMPACT);
        }

        Vector3d linear = correction(leftLinear, linearNow, settings);
        Vector3d angular = correction(leftAngular, angularNow, settings);
        if (linear.length() < LINEAR_EPSILON) {
            linear.zero();
        }
        if (angular.length() < ANGULAR_EPSILON) {
            angular.zero();
        }

        leftLinear.set(linearNow).add(linear);
        leftAngular.set(angularNow).add(angular);
        lastPosition.set(positionNow);
        lastOutcome = Outcome.CUSHIONED;
        return linear.lengthSquared() == 0.0D && angular.lengthSquared() == 0.0D
                ? Correction.NONE
                : new Correction(linear, angular);
    }

    /**
     * The velocity change that turns {@code now} into the cushioned one.
     *
     * <p>The target keeps the share of the tick's change the cushion lets through, and then gives up
     * the settle's share of the result - so a hull with nothing acting on it still coasts to a stop,
     * and one with something acting on it settles where the two balance.
     */
    static Vector3d correction(Vector3dc before, Vector3dc now, Settings settings) {
        Vector3d target = new Vector3d(now).sub(before).mul(1.0D - settings.cushion()).add(before)
                .mul(1.0D - settings.settle());
        return target.sub(now);
    }

    private boolean teleported(Vector3dc positionNow, Vector3dc linearNow) {
        // Where the hull should be: last tick's spot, carried along by the average of the velocity it
        // was left at and the one it has now. Both are per tick, so one tick of travel is just the sum.
        Vector3d expected = new Vector3d(leftLinear).add(linearNow).mul(0.5D).add(lastPosition);
        return expected.distance(positionNow) > TELEPORT_MARGIN;
    }

    private Correction restart(Vector3dc linearNow, Vector3dc angularNow, Vector3dc positionNow, Outcome why) {
        leftLinear.set(linearNow);
        leftAngular.set(angularNow);
        lastPosition.set(positionNow);
        primed = true;
        lastOutcome = why;
        return Correction.NONE;
    }
}
