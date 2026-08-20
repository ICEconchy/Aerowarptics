package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The speed profile of a warp, end to end.
 *
 * <p>A warp is meant to be one continuous motion: the run at the aperture eases down to the speed the
 * hull passes through at, the passage and the corridor hold that speed, and the run out bleeds it away
 * to nothing. Every one of those joins used to be a step change, and a step change in a ship's motion
 * is something a passenger feels. These pin the joins.
 */
class WarpSpeedTest {

    /** A representative hull: 19 blocks long, three seconds to pass through an aperture. */
    private static final double TRANSIT_RUN = 22.97D;
    private static final int TRANSIT_TICKS = 60;
    private static final double PASSAGE = TRANSIT_RUN / TRANSIT_TICKS;
    private static final double CRUISE = 1.6D;
    private static final int EMERGE_TICKS = 30;

    // ---------------------------------------------------------------- run in

    @Test
    void theRunAtTheApertureArrivesAtExactlyPassageSpeed() {
        // The join that used to be a 4x drop the instant the bow touched the plane.
        assertEquals(PASSAGE, WarpFlight.approachSpeed(0.0D, CRUISE, PASSAGE), 1.0e-9D);
    }

    @Test
    void theRunCruisesUntilItHasToSlowDown() {
        assertEquals(CRUISE, WarpFlight.approachSpeed(40.0D, CRUISE, PASSAGE), 1.0e-9D);
        assertEquals(CRUISE, WarpFlight.approachSpeed(12.0D, CRUISE, PASSAGE), 1.0e-9D);
    }

    @Test
    void theRunOnlyEverSlowsDownOnItsWayIn() {
        double previous = WarpFlight.approachSpeed(60.0D, CRUISE, PASSAGE);
        for (double toPlane = 60.0D; toPlane >= 0.0D; toPlane -= 0.25D) {
            double speed = WarpFlight.approachSpeed(toPlane, CRUISE, PASSAGE);
            assertTrue(speed <= previous + 1.0e-9D, "the run sped up again at " + toPlane);
            assertTrue(speed >= PASSAGE - 1.0e-9D, "the run fell below passage speed at " + toPlane);
            previous = speed;
        }
    }

    @Test
    void aDriveConfiguredSlowerThanItsPassageIsNotSpedUp() {
        // Nothing stops someone setting an approach speed below the passage speed. It should simply
        // be the whole run, not produce a hull that accelerates into the aperture.
        double slow = PASSAGE * 0.5D;
        assertEquals(slow, WarpFlight.approachSpeed(0.0D, slow, slow), 1.0e-9D);
        assertTrue(WarpFlight.approachSpeed(40.0D, slow, slow) <= PASSAGE);
    }

    // --------------------------------------------------------------- run out

    @Test
    void theRunOutLandsExactlyOnTheMark() {
        double remaining = PASSAGE * EMERGE_TICKS * 0.5D;
        for (int tick = 0; tick < EMERGE_TICKS; tick++) {
            remaining -= remaining * WarpFlight.emergeFraction(EMERGE_TICKS - tick);
        }
        assertEquals(0.0D, remaining, 1.0e-9D, "the hull should have nothing left to travel");
    }

    @Test
    void theRunOutSlowsDownTheWholeWayRatherThanStoppingDead() {
        double remaining = PASSAGE * EMERGE_TICKS * 0.5D;
        double previousStep = Double.MAX_VALUE;
        for (int tick = 0; tick < EMERGE_TICKS; tick++) {
            double step = remaining * WarpFlight.emergeFraction(EMERGE_TICKS - tick);
            assertTrue(step <= previousStep + 1.0e-9D, "the hull sped up again on tick " + tick);
            previousStep = step;
            remaining -= step;
        }
        assertTrue(previousStep < PASSAGE * 0.1D,
                "the last step should be a settle, not a stop: " + previousStep);
    }

    @Test
    void theRunOutLeavesTheApertureAtPassageSpeed() {
        // This is why plan() sizes the run out at passage x ticks / 2 rather than picking a distance:
        // it is what makes the first step out of the aperture match the speed inside it.
        double remaining = PASSAGE * EMERGE_TICKS * 0.5D;
        double firstStep = remaining * WarpFlight.emergeFraction(EMERGE_TICKS);
        assertEquals(PASSAGE, firstStep, PASSAGE * 0.05D,
                "the hull should leave the aperture doing what it was doing inside it");
    }

    @Test
    void theRunOutIsSafeOnItsLastTickAndBeyond() {
        assertEquals(1.0D, WarpFlight.emergeFraction(1), 1.0e-9D, "the last tick takes what is left");
        assertEquals(1.0D, WarpFlight.emergeFraction(0), 1.0e-9D);
        assertEquals(1.0D, WarpFlight.emergeFraction(-5), 1.0e-9D);
    }
}
