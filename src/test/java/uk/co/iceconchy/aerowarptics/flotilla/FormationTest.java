package uk.co.iceconchy.aerowarptics.flotilla;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry a formation is built on: the flagship's frame, spacing, and the order ships go in.
 *
 * <p>Every one of these, got wrong, still draws and still folds - with starboard on the port side, or
 * two escorts put down in the same piece of sky - which is why they are checked here rather than in the
 * air.
 */
class FormationTest {

    private static final int[][] BOWS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final double EPSILON = 1.0e-9D;

    @Test
    void aSlotSurvivesTheRoundTripThroughShipSpaceForEveryBow() {
        FormationSlot slot = new FormationSlot(12.5D, -3.0D, 40.0D);
        for (int[] bow : BOWS) {
            double[] step = Formation.toShip(slot, bow[0], bow[1]);
            FormationSlot back = Formation.toSlot(step[0], step[1], step[2], bow[0], bow[1]);
            assertEquals(slot.right(), back.right(), EPSILON, "starboard, bow " + bow[0] + "," + bow[1]);
            assertEquals(slot.up(), back.up(), EPSILON);
            assertEquals(slot.ahead(), back.ahead(), EPSILON, "ahead, bow " + bow[0] + "," + bow[1]);
        }
    }

    @Test
    void starboardIsTheBowTurnedClockwiseFromAbove() {
        // Bow to the north: ahead is -z and starboard is east, +x.
        double[] ahead = Formation.toShip(new FormationSlot(0, 0, 10), 0, -1);
        assertEquals(0.0D, ahead[0], EPSILON);
        assertEquals(-10.0D, ahead[2], EPSILON);
        double[] starboard = Formation.toShip(new FormationSlot(10, 0, 0), 0, -1);
        assertEquals(10.0D, starboard[0], EPSILON, "a ship off the starboard beam of a north-bound flagship is east of it");
        assertEquals(0.0D, starboard[2], EPSILON);
        // Bow to the east: starboard is south, +z.
        double[] east = Formation.toShip(new FormationSlot(10, 0, 0), 1, 0);
        assertEquals(0.0D, east[0], EPSILON);
        assertEquals(10.0D, east[2], EPSILON);
    }

    @Test
    void driftIsTheStraightLineFromTheSlot() {
        assertEquals(5.0D, Formation.drift(new FormationSlot(3, 0, 4), new FormationSlot(0, 0, 0)), EPSILON);
        assertTrue(Formation.inStation(6.0D, 6.0D), "exactly at the tolerance is still in station");
        assertFalse(Formation.inStation(6.0001D, 6.0D));
    }

    @Test
    void shipsClashOnlyWhenTheyShareSkyInBothDirections() {
        Formation.Footprint small = new Formation.Footprint(5, 3);
        FormationSlot here = new FormationSlot(0, 0, 0);
        assertTrue(Formation.clashes(here, small, new FormationSlot(15, 0, 0), small, 6.0D), "10 apart plus 6 is 16");
        assertFalse(Formation.clashes(here, small, new FormationSlot(17, 0, 0), small, 6.0D));
        assertFalse(Formation.clashes(here, small, new FormationSlot(0, 13, 0), small, 6.0D),
                "stacked high enough above, they never touch");
        assertTrue(Formation.clashes(here, small, new FormationSlot(0, 11, 0), small, 6.0D));
    }

    @Test
    void theFirstClashNamesWhichShipIsInTheWay() {
        Formation.Footprint hull = new Formation.Footprint(4, 2);
        List<FormationSlot> others = List.of(FormationSlot.FLAGSHIP, new FormationSlot(40, 0, 0));
        List<Formation.Footprint> footprints = List.of(new Formation.Footprint(10, 5), hull);
        assertEquals(0, Formation.firstClash(new FormationSlot(10, 0, 0), hull, others, footprints, 4));
        assertEquals(1, Formation.firstClash(new FormationSlot(35, 0, 0), hull, others, footprints, 4));
        assertEquals(-1, Formation.firstClash(new FormationSlot(0, 0, 40), hull, others, footprints, 4));
    }

    @Test
    void aFootprintIsWideEnoughForTheHullAtAnyHeading() {
        Formation.Footprint footprint = Formation.Footprint.ofBox(6, 10, 8);
        assertEquals(5.0D, footprint.radius(), EPSILON, "half the diagonal of a six by eight deck");
        assertEquals(5.0D, footprint.halfHeight(), EPSILON);
    }

    @Test
    void snappingAndClampingKeepASlotSensible() {
        FormationSlot snapped = Formation.snap(new FormationSlot(3.4, 0.6, -7.5), 1.0D);
        assertEquals(new FormationSlot(3, 1, -7), snapped);
        FormationSlot clamped = Formation.clampReach(new FormationSlot(300, 0, 400), 100.0D);
        assertEquals(100.0D, clamped.reach(), 1.0e-6D);
        assertEquals(0.6D, clamped.right() / 100.0D, 1.0e-6D, "pulled in along its own line");
        assertEquals(new FormationSlot(1, 2, 3), Formation.clampReach(new FormationSlot(1, 2, 3), 100.0D));
    }

    @Test
    void theFleetGoesFrontToBackAndOutFromTheMiddle() {
        List<FormationSlot> slots = List.of(
                new FormationSlot(-20, 0, 0),
                new FormationSlot(0, 0, -30),
                new FormationSlot(10, 0, 0),
                new FormationSlot(0, 0, 30),
                new FormationSlot(20, 0, 0));
        assertEquals(List.of(3, 2, 0, 4, 1), Formation.rippleOrder(slots));
    }

    @Test
    void theExtentTakesInEveryShipsFootprint() {
        Formation.Extent extent = Formation.extent(
                List.of(FormationSlot.FLAGSHIP, new FormationSlot(30, 5, -20)),
                List.of(new Formation.Footprint(10, 4), new Formation.Footprint(6, 3)));
        assertEquals(-10.0D, extent.minRight(), EPSILON);
        assertEquals(36.0D, extent.maxRight(), EPSILON);
        assertEquals(46.0D, extent.width(), EPSILON);
        assertEquals(12.0D, extent.height(), EPSILON, "from 4 below the flagship to 8 above it");
        assertEquals(36.0D, extent.length(), EPSILON);
    }

    @Test
    void aSlotOffDiskIsNeverAtInfinity() {
        FormationSlot slot = new FormationSlot(Double.NaN, Double.POSITIVE_INFINITY, 4);
        assertEquals(new FormationSlot(0, 0, 4), slot);
        assertEquals(slot, FormationSlot.load(slot.save()));
    }
}
