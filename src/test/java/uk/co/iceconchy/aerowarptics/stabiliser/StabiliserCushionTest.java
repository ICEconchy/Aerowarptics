package uk.co.iceconchy.aerowarptics.stabiliser;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spatial Stabiliser's arithmetic, against a toy hull.
 *
 * <p>Every failure here is a silent one in game. A cushion that pushes the wrong way launches the ship;
 * one that never lets go of a moored hull keeps its rigid body awake forever; one that smooths a
 * teleport drags a warp's arrival back towards where it left. None of those throw, and all of them look
 * like "physics" from the deck.
 *
 * <p>The toy hull is the case the block was asked for: something - a hot-air balloon with the burners
 * lit - adds the same velocity every tick, and nothing else acts.
 */
class StabiliserCushionTest {

    private static final double EPSILON = 1.0e-9D;

    /** The shipped defaults. */
    private static final StabiliserCushion.Settings DEFAULTS =
            new StabiliserCushion.Settings(0.75D, 0.02D, 0.25D, 0.05D);

    /** Cushioning with nothing bled away, so the pure effect of the cushion can be measured. */
    private static final StabiliserCushion.Settings CUSHION_ONLY =
            new StabiliserCushion.Settings(0.75D, 0.0D, 0.25D, 0.05D);

    private static final Vector3dc ZERO = new Vector3d();

    /** A hull that Sable pushes and the stabiliser corrects, tick by tick. */
    private static final class ToyHull {
        final Vector3d velocity = new Vector3d();
        final Vector3d spin = new Vector3d();
        final Vector3d position = new Vector3d(0.0D, 100.0D, 0.0D);

        /** One physics step: the push lands in the velocity, and the hull moves at the new speed. */
        void physics(Vector3dc push, Vector3dc torque) {
            velocity.add(push);
            spin.add(torque);
            position.add(velocity);
        }

        StabiliserCushion.Correction stabilise(StabiliserCushion cushion, StabiliserCushion.Settings settings) {
            StabiliserCushion.Correction correction = cushion.step(velocity, spin, position, settings);
            velocity.add(correction.linear());
            spin.add(correction.angular());
            return correction;
        }
    }

    private static ToyHull rise(StabiliserCushion.Settings settings, double lift, int ticks) {
        ToyHull hull = new ToyHull();
        StabiliserCushion cushion = new StabiliserCushion();
        Vector3d push = new Vector3d(0.0D, lift, 0.0D);
        for (int tick = 0; tick < ticks; tick++) {
            hull.physics(push, ZERO);
            if (settings != null) {
                hull.stabilise(cushion, settings);
            }
        }
        return hull;
    }

    // ------------------------------------------------------------------ the point of it

    @Test
    void aSteadyLiftIsFeltAtTheShareTheCushionLetsThrough() {
        double lift = 0.02D;
        int ticks = 40;
        ToyHull bare = rise(null, lift, ticks);
        ToyHull cushioned = rise(CUSHION_ONLY, lift, ticks);

        assertEquals(lift * ticks, bare.velocity.y, EPSILON);
        // The first tick only primes, so it is felt in full; every one after it at a quarter.
        double expected = lift + (ticks - 1) * lift * 0.25D;
        assertEquals(expected, cushioned.velocity.y, EPSILON,
                "a hull cushioned at 0.75 should respond to its lift as though four times heavier");
        assertTrue(cushioned.position.y - 100.0D < 0.35D * (bare.position.y - 100.0D),
                "the height gained, not just the final speed, should be about a third of the bare climb");
    }

    @Test
    void settlingBringsAPushThatNeverStopsToAGentlerSpeed() {
        double lift = 0.02D;
        ToyHull hull = rise(DEFAULTS, lift, 3_000);

        // v = (v + (1 - cushion) * lift) * (1 - settle) settles where the two sides balance.
        double terminal = (1.0D - DEFAULTS.cushion()) * lift * (1.0D - DEFAULTS.settle()) / DEFAULTS.settle();
        assertEquals(terminal, hull.velocity.y, 1.0e-6D, "a steady lift should settle, not keep climbing");
        assertTrue(hull.velocity.y > 0.0D, "settling must never turn a climb into a descent");
        assertTrue(hull.velocity.y < lift * 3_000 / 100.0D, "and the settled speed is a small fraction of the bare one");
    }

    @Test
    void theCushionPushesAgainstTheChangeAndNeverBendsIt() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.stabilise(cushion, CUSHION_ONLY);

        Vector3d push = new Vector3d(0.03D, 0.06D, -0.03D);
        hull.physics(push, ZERO);
        StabiliserCushion.Correction correction = hull.stabilise(cushion, CUSHION_ONLY);

        Vector3d expected = new Vector3d(push).mul(-0.75D);
        assertEquals(expected.x, correction.linear().x(), EPSILON);
        assertEquals(expected.y, correction.linear().y(), EPSILON);
        assertEquals(expected.z, correction.linear().z(), EPSILON);
    }

    @Test
    void spinIsCushionedTheSameWay() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.stabilise(cushion, CUSHION_ONLY);

        hull.physics(ZERO, new Vector3d(0.0D, 0.02D, 0.0D));
        StabiliserCushion.Correction correction = hull.stabilise(cushion, CUSHION_ONLY);

        assertEquals(-0.015D, correction.angular().y(), EPSILON);
        assertEquals(0.005D, hull.spin.y, EPSILON, "a quarter of a sudden turn should get through");
    }

    // --------------------------------------------------------------- leaving things alone

    @Test
    void theFirstTickOnlyPrimes() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.velocity.set(0.4D, 0.1D, 0.0D);

        assertSame(StabiliserCushion.Correction.NONE, hull.stabilise(cushion, DEFAULTS),
                "with nothing to compare against there is nothing to cushion");
        assertEquals(StabiliserCushion.Outcome.PRIMED, cushion.lastOutcome());
        assertTrue(cushion.primed());
    }

    @Test
    void aHullAtRestIsNeverTouched() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        for (int tick = 0; tick < 200; tick++) {
            hull.physics(ZERO, ZERO);
            StabiliserCushion.Correction correction = hull.stabilise(cushion, DEFAULTS);
            assertTrue(correction.isNone(), "every nudge wakes the body; a moored ship must be allowed to sleep");
        }
    }

    @Test
    void aSettledHullIsLeftToSleepOnceTheCorrectionIsTooSmallToMatter() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.velocity.set(0.1D, 0.0D, 0.0D);
        int quietFrom = -1;
        for (int tick = 0; tick < 2_000; tick++) {
            hull.physics(ZERO, ZERO);
            if (hull.stabilise(cushion, DEFAULTS).isNone() && tick > 0) {
                quietFrom = quietFrom < 0 ? tick : quietFrom;
            } else {
                quietFrom = -1;
            }
        }
        assertTrue(quietFrom > 0, "a coasting hull should eventually stop being nudged at all");
    }

    @Test
    void steadyCruisingWithNoSettleIsLeftAlone() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.velocity.set(0.5D, 0.0D, 0.2D);
        for (int tick = 0; tick < 100; tick++) {
            hull.physics(ZERO, ZERO);
            assertTrue(hull.stabilise(cushion, CUSHION_ONLY).isNone(),
                    "a cushion absorbs change; a ship already going somewhere steadily has none");
        }
        assertEquals(0.5D, hull.velocity.x, EPSILON);
    }

    @Test
    void anImpactPassesStraightThrough() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.velocity.set(0.0D, -0.6D, 0.0D);
        hull.stabilise(cushion, DEFAULTS);

        // The ground stops it dead in one tick.
        hull.velocity.zero();
        StabiliserCushion.Correction correction = hull.stabilise(cushion, DEFAULTS);

        assertSame(StabiliserCushion.Correction.NONE, correction,
                "handing back part of a crash pushes the hull into what it hit");
        assertEquals(StabiliserCushion.Outcome.IMPACT, cushion.lastOutcome());
        hull.physics(ZERO, ZERO);
        assertTrue(hull.stabilise(cushion, DEFAULTS).isNone(), "and it starts again from the stopped hull");
    }

    @Test
    void aTeleportIsAFreshStartNotAChangeToSmooth() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.velocity.set(0.1D, 0.0D, 0.0D);
        hull.physics(ZERO, ZERO);
        hull.stabilise(cushion, DEFAULTS);

        // A Rift Gate: somewhere else entirely, and turned to a new heading at a modest speed.
        hull.position.add(400.0D, 0.0D, -250.0D);
        hull.velocity.set(0.0D, 0.0D, 0.2D);
        StabiliserCushion.Correction correction = hull.stabilise(cushion, DEFAULTS);

        assertSame(StabiliserCushion.Correction.NONE, correction,
                "the momentum a gate hands over is the gate's decision, not a jolt");
        assertEquals(StabiliserCushion.Outcome.TELEPORT, cushion.lastOutcome());
        assertEquals(0.2D, hull.velocity.z, EPSILON);
    }

    @Test
    void honestFlightIsNeverMistakenForATeleport() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        // Fast, and accelerating as hard as the jolt limit allows.
        Vector3d push = new Vector3d(0.24D, 0.0D, 0.0D);
        for (int tick = 0; tick < 60; tick++) {
            hull.physics(push, ZERO);
            hull.stabilise(cushion, CUSHION_ONLY);
            if (tick > 0) {
                assertEquals(StabiliserCushion.Outcome.CUSHIONED, cushion.lastOutcome(), "tick " + tick);
            }
        }
    }

    @Test
    void releasingMakesTheNextTickAFreshStart() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.stabilise(cushion, DEFAULTS);
        cushion.release();
        assertFalse(cushion.primed());

        // A minute later, switched back on: whatever happened meanwhile is not cushioned in one tick.
        hull.velocity.set(0.0D, 0.2D, 0.0D);
        assertSame(StabiliserCushion.Correction.NONE, hull.stabilise(cushion, DEFAULTS));
        assertEquals(StabiliserCushion.Outcome.PRIMED, cushion.lastOutcome());
    }

    @Test
    void aNonFiniteReadingIsNeverHandedToTheSolver() {
        StabiliserCushion cushion = new StabiliserCushion();
        ToyHull hull = new ToyHull();
        hull.stabilise(cushion, DEFAULTS);

        hull.velocity.set(Double.NaN, 0.0D, 0.0D);
        assertSame(StabiliserCushion.Correction.NONE, hull.stabilise(cushion, DEFAULTS),
                "a NaN is never bigger than a limit, so it must be caught before the limits are asked");
        assertFalse(cushion.primed());
    }

    // ------------------------------------------------------------------------ settings

    @Test
    void settingsAreClampedSoAHullCanAlwaysStillMove() {
        StabiliserCushion.Settings wild = new StabiliserCushion.Settings(5.0D, 9.0D, -1.0D, Double.NaN);
        assertEquals(StabiliserCushion.MAX_CUSHION, wild.cushion(), EPSILON);
        assertEquals(StabiliserCushion.MAX_SETTLE, wild.settle(), EPSILON);
        assertEquals(0.0D, wild.joltLimit(), EPSILON);
        assertEquals(0.0D, wild.spinJoltLimit(), EPSILON);

        StabiliserCushion.Settings negative = new StabiliserCushion.Settings(-1.0D, -1.0D, 1.0D, 1.0D);
        assertEquals(0.0D, negative.cushion(), EPSILON, "a negative cushion would amplify every change");
        assertEquals(0.0D, negative.settle(), EPSILON, "a negative settle would accelerate every ship");
    }

    @Test
    void theHeaviestCushionStillLetsALiftThrough() {
        StabiliserCushion.Settings heaviest = new StabiliserCushion.Settings(1.0D, 0.0D, 0.25D, 0.05D);
        ToyHull hull = rise(heaviest, 0.02D, 100);
        assertTrue(hull.velocity.y > 0.02D, "at the cap a ship is slow to answer, never deaf");
    }
}
