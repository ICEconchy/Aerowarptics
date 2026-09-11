package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the departure corridor is the length of the ship, not a multiple of it.
 *
 * <p>The corridor run used to be flown at passage speed for the whole of the drive tier's corridor
 * phase, so the distance that had to be proven clear came out as
 * {@code (hullLength + margin) x (1 + corridorTicks / transitTicks)} - with the shipped figures, two
 * and a half hull lengths of real world space. None of it was observable: the hull is inside the
 * entry throat for the entire phase. What it did do was scale every cost in a warp with the size of
 * the ship - blocks to prove clear, chunks to force-generate and hold resident, and throat geometry
 * to draw - until a large enough hull could not launch at all, refused for a corridor that had
 * nothing in it.
 *
 * <p>These are arithmetic tests on the shape of the reach rather than on a running world, in the
 * same spirit as {@link ObstructionScanTest}: the formula is the thing that was wrong.
 */
class CorridorReachTest {

    /** Blocks of overshoot on a passage, mirroring {@code WarpFlight.TRANSIT_MARGIN}. */
    private static final double TRANSIT_MARGIN = 4.0D;

    /**
     * The reach the old formula produced, kept here as the thing being moved away from.
     *
     * @param corridorTicks the tier's corridor phase, 70-100 on the shipped tiers
     * @param transitTicks  {@code riftTransitTicks}, 60 by default
     */
    private static double legacyReach(double hullLength, int corridorTicks, int transitTicks) {
        double transitRun = hullLength + TRANSIT_MARGIN;
        double transitSpeed = transitRun / transitTicks;
        return transitRun + corridorTicks * transitSpeed;
    }

    /** What the reach is now: the passage, plus whatever drift is configured (zero by default). */
    private static double reach(double hullLength, double drift) {
        return hullLength + TRANSIT_MARGIN + drift;
    }

    /**
     * With no drift, the corridor is exactly long enough to swallow the hull.
     *
     * <p>Which is the whole requirement: the aperture has to take the ship bow to stern, and nothing
     * beyond that is hidden any better for having been flown through.
     */
    @Test
    void withoutDriftTheCorridorIsJustTheHullPlusItsMargin() {
        for (double hullLength : new double[]{20.0D, 60.0D, 100.0D, 200.0D}) {
            assertEquals(hullLength + TRANSIT_MARGIN, reach(hullLength, 0.0D), 1.0e-9D,
                    "a hull of " + hullLength + " should need only its own length proven clear");
        }
    }

    /** Drift is added as a flat distance, not multiplied by anything the hull controls. */
    @Test
    void driftIsAFlatDistanceOnTop() {
        assertEquals(104.0D + 32.0D, reach(100.0D, 32.0D), 1.0e-9D);
        assertEquals(204.0D + 32.0D, reach(200.0D, 32.0D), 1.0e-9D);
    }

    /**
     * The reach no longer grows faster than the ship does.
     *
     * <p>The old formula multiplied the hull's length by a constant above two, so doubling a ship
     * more than doubled the volume to prove. Now the excess over the hull is a fixed amount - the
     * margin plus any drift - whatever the ship's size, which is what stops large vessels being
     * priced out of warping.
     */
    @Test
    void reachGrowsNoFasterThanTheHull() {
        double small = reach(50.0D, 0.0D);
        double large = reach(200.0D, 0.0D);
        double hullRatio = 200.0D / 50.0D;
        assertTrue(large / small <= hullRatio + 1.0e-9D,
                "reach grew " + (large / small) + "x for a " + hullRatio + "x hull");
    }

    /** And it is a large, unambiguous cut against what it replaced, at every tier. */
    @Test
    void theNewReachIsFarShorterThanTheOldOne() {
        int transitTicks = 60; // riftTransitTicks default
        for (int corridorTicks : new int[]{70, 80, 90, 100}) { // singularity .. mk i
            for (double hullLength : new double[]{60.0D, 100.0D, 200.0D}) {
                double before = legacyReach(hullLength, corridorTicks, transitTicks);
                double after = reach(hullLength, 0.0D);
                assertTrue(after < before / 2.0D,
                        "hull " + hullLength + " at " + corridorTicks + " corridor ticks: "
                                + before + " -> " + after + ", expected at least a halving");
            }
        }
    }

    /**
     * The throat is sized from the reach, so it shrinks with it rather than needing its own fix.
     *
     * <p>The throat exists to hide whatever the corridor flies through; a corridor that goes nowhere
     * needs no more depth than the passage itself.
     */
    @Test
    void theThroatFollowsTheReachDown() {
        double before = WarpFlight.throatFor(legacyReach(200.0D, 100, 60));
        double after = WarpFlight.throatFor(reach(200.0D, 0.0D));
        assertTrue(after < before / 2.0D, "throat " + before + " -> " + after);
    }
}
