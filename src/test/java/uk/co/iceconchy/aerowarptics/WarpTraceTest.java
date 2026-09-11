package uk.co.iceconchy.aerowarptics;

import org.joml.Vector3d;
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

    // ------------------------------------------------------- new diagnostic lines
    //
    // These format without a running game - the whole point of keeping the formatting Minecraft-free -
    // so the failure they guard is a trace line that only crashes once diagnostics are turned on.

    @Test
    void aHealthyTickReportsNoDivergence() {
        String line = WarpTrace.velocitySummary(new Vector3d(0.0D, 0.0D, 3.0D),
                new Vector3d(0.0D, 0.0D, 2.9D), 48.0D);
        assertTrue(line.contains("|c|=3.00"));
        assertTrue(line.contains("|r|=2.90"));
        assertFalse(line.contains("DIVERGED"), "a tick tracking its command is not a divergence");
        assertFalse(line.contains("OVER-CEILING"));
    }

    @Test
    void aYeetShowsAsReportedVelocityDivergingFromCommanded() {
        // Commanded a crawl, doing thousands: the signature of a penetration ejection.
        String line = WarpTrace.velocitySummary(new Vector3d(0.0D, 0.0D, 1.0D),
                new Vector3d(0.0D, 0.0D, 9000.0D), 48.0D);
        assertTrue(line.contains("OVER-CEILING"), "a hull past the ceiling must be flagged");
    }

    @Test
    void aCommandAtTheCeilingIsFlaggedAsClamped() {
        String line = WarpTrace.velocitySummary(new Vector3d(48.0D, 0.0D, 0.0D),
                new Vector3d(48.0D, 0.0D, 0.0D), 48.0D);
        assertTrue(line.contains("CLAMPED"));
    }

    @Test
    void aCrossingIsSizedAgainstTheJumpThreshold() {
        assertTrue(WarpTrace.crossingSummary(4626.0D, 128.0D, 3).contains("JUMP"));
        assertTrue(WarpTrace.crossingSummary(4626.0D, 128.0D, 3).contains("notice->3 players"));
        assertTrue(WarpTrace.crossingSummary(40.0D, 128.0D, 0).contains("under-threshold"),
                "a move under the threshold would not fire the collapse, and nobody was told");
    }

    @Test
    void anArrivalThatWillUnloadIsFlagged() {
        assertTrue(WarpTrace.arrivalSummary(true, true, false).contains("WILL-UNLOAD"));
        assertFalse(WarpTrace.arrivalSummary(true, true, true).contains("WILL-UNLOAD"),
                "a durable landing is not flagged");
    }
}
