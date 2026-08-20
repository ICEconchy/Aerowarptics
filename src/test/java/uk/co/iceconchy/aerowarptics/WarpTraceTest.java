package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpTrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a hull sits relative to an aperture.
 *
 * <p>This is the arithmetic behind the original complaint - a ship that vanished on contact with a
 * rift - so the sign convention is worth pinning down. Everything is a distance still to go: positive
 * has not reached the plane, negative is through it.
 */
class WarpTraceTest {

    private static final double EPSILON = 1.0e-9D;

    @Test
    void theBowLeadsTheCentreAndTheSternTrailsIt() {
        WarpTrace.Crossing crossing = WarpTrace.crossing(50.0D, 60.0D);
        assertEquals(20.0D, crossing.bow(), EPSILON, "the bow is half a hull nearer the plane");
        assertEquals(50.0D, crossing.centre(), EPSILON);
        assertEquals(80.0D, crossing.stern(), EPSILON, "the stern is half a hull further from it");
    }

    @Test
    void nothingIsTouchingWhileTheApertureIsStillAhead() {
        WarpTrace.Crossing crossing = WarpTrace.crossing(50.0D, 60.0D);
        assertFalse(crossing.touching());
        assertFalse(crossing.fullyThrough());
    }

    @Test
    void theBowTouchesAtHalfAHullOut() {
        // This is exactly the moment the approach hands over to the passage.
        WarpTrace.Crossing crossing = WarpTrace.crossing(30.0D, 60.0D);
        assertEquals(0.0D, crossing.bow(), EPSILON);
        assertTrue(crossing.touching());
        assertFalse(crossing.fullyThrough(), "half a hull is still hanging out of the aperture");
    }

    @Test
    void aHullWhoseCentreIsOnThePlaneIsOnlyHalfIn() {
        // The old behaviour teleported here, which is why a ship looked like it vanished on contact.
        WarpTrace.Crossing crossing = WarpTrace.crossing(0.0D, 60.0D);
        assertTrue(crossing.touching());
        assertFalse(crossing.fullyThrough(), "the stern is still thirty blocks outside");
        assertEquals(30.0D, crossing.stern(), EPSILON);
    }

    @Test
    void aHullIsOnlyThroughOnceItsSternIsPastThePlane() {
        assertFalse(WarpTrace.crossing(-29.0D, 60.0D).fullyThrough());
        assertTrue(WarpTrace.crossing(-30.0D, 60.0D).fullyThrough(), "stern exactly on the plane counts");
        assertTrue(WarpTrace.crossing(-31.0D, 60.0D).fullyThrough());
    }

    @Test
    void aHullWithNoLengthCrossesAllAtOnce() {
        WarpTrace.Crossing crossing = WarpTrace.crossing(0.0D, 0.0D);
        assertTrue(crossing.touching());
        assertTrue(crossing.fullyThrough());
    }

    @Test
    void aNegativeSpanIsTreatedAsNoneRatherThanInvertingTheShip() {
        WarpTrace.Crossing crossing = WarpTrace.crossing(5.0D, -40.0D);
        assertEquals(5.0D, crossing.bow(), EPSILON);
        assertEquals(5.0D, crossing.stern(), EPSILON);
    }

    @Test
    void theSummaryNamesTheStateItIsIn() {
        assertTrue(WarpTrace.crossing(50.0D, 60.0D).toString().endsWith("stern=+80.00"),
                "an approach reports no state at all");
        assertTrue(WarpTrace.crossing(0.0D, 60.0D).toString().contains("CROSSING"));
        assertTrue(WarpTrace.crossing(-40.0D, 60.0D).toString().contains("THROUGH"));
    }
}
