package uk.co.iceconchy.aerowarptics.berth;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A slip's geometry, and whether a ship fits one - the failures here would all draw and run. */
class BerthBoxTest {

    private static final double EPS = 1.0e-9D;

    private static BerthBox slip(double yaw) {
        return new BerthBox(100.5D, 64.0D, -40.5D, 20.0D, 8.0D, 10.0D, yaw);
    }

    /** Minecraft's yaw: nought is south, ninety is west. Got backwards, every berth faces its mirror image. */
    @Test
    void theFacingIsMinecraftsYaw() {
        assertVec(0, 0, 1, slip(0).facing());
        assertVec(-1, 0, 0, slip(90).facing());
        assertVec(0, 0, -1, slip(180).facing());
        assertVec(1, 0, 0, slip(270).facing());
    }

    @Test
    void aPointGoesIntoTheSlipsTermsAndBackTheSameWayRound() {
        for (int yaw = 0; yaw < 360; yaw += 5) {
            BerthBox box = slip(yaw);
            Vector3d world = box.world(3.0D, 2.5D, -7.0D);
            Vector3d local = box.local(world.x, world.y, world.z);
            assertEquals(3.0D, local.x, EPS, "across, at yaw " + yaw);
            assertEquals(2.5D, local.y, EPS);
            assertEquals(-7.0D, local.z, EPS, "along, at yaw " + yaw);
            // And "along" really is along the facing: a mirror would pass the round trip.
            Vector3d ahead = box.world(0.0D, 0.0D, 1.0D).sub(box.centreX(), box.floorY(), box.centreZ());
            assertEquals(1.0D, ahead.dot(box.facing()), EPS);
        }
    }

    @Test
    void theSlipHoldsWhatIsInsideItAtAnyTurn() {
        for (int yaw = 0; yaw < 360; yaw += 15) {
            BerthBox box = slip(yaw);
            Vector3d bow = box.world(0.0D, 5.0D, 9.9D);
            Vector3d beyond = box.world(0.0D, 5.0D, 10.1D);
            Vector3d beside = box.world(4.1D, 5.0D, 0.0D);
            assertTrue(box.contains(bow.x, bow.y, bow.z), "yaw " + yaw);
            assertFalse(box.contains(beyond.x, beyond.y, beyond.z), "yaw " + yaw);
            assertFalse(box.contains(beside.x, beside.y, beside.z), "yaw " + yaw);
            assertFalse(box.contains(box.centreX(), 63.9D, box.centreZ()));
            assertFalse(box.contains(box.centreX(), 74.1D, box.centreZ()));
        }
    }

    /** A ship rests on the floor's clearance, not hung in the middle of a tall slip. */
    @Test
    void aShipRestsOverTheFloor() {
        Vector3d mark = slip(35).mark(4.0D, 1.0D);
        assertEquals(100.5D, mark.x, EPS);
        assertEquals(64.0D + 1.0D + 2.0D, mark.y, EPS);
        assertEquals(-40.5D, mark.z, EPS);
    }

    /** The lane lies behind the way a hull comes in, and swaps ends when it comes in stern first. */
    @Test
    void theLaneIsBehindTheWayIn() {
        BerthBox box = slip(0);
        BerthBox lane = box.lane(30.0D, false);
        assertEquals(-40.5D - 10.0D - 15.0D, lane.centreZ(), EPS, "bow first into a south-facing slip comes from the north");
        assertEquals(30.0D, lane.length(), EPS);
        assertEquals(box.width(), lane.width(), EPS);
        BerthBox astern = box.lane(30.0D, true);
        assertEquals(-40.5D + 10.0D + 15.0D, astern.centreZ(), EPS);
        // The lane starts where the slip ends: no gap a girder could hide in, no overlap counted twice.
        assertEquals(box.centreZ() - box.length() * 0.5D, lane.centreZ() + lane.length() * 0.5D, EPS);
    }

    @Test
    void aHullThatAppearsInsideTheSlipNeedsNoLane() {
        BerthBox box = slip(0);
        assertEquals(0.0D, box.laneReach(4.0D, 6.0D), EPS);
        assertEquals(40.0D + 6.0D - 10.0D, box.laneReach(40.0D, 12.0D), EPS);
    }

    /**
     * Two slips side by side at forty-five degrees do not share air, though the upright boxes round them do.
     * Answered from those boxes, a ship in one fouls the other.
     */
    @Test
    void overlapIsTheSlipsOwnShapeNotItsEnclosingBox() {
        BerthBox box = new BerthBox(0.0D, 64.0D, 0.0D, 40.0D, 6.0D, 10.0D, 45.0D);
        double[] bounds = box.bounds();
        // A small box in the corner of the enclosing box, well clear of the slip itself.
        double cornerX = bounds[3] - 2.0D;
        double cornerZ = bounds[5] - 2.0D;
        assertFalse(box.overlaps(cornerX - 1, 66, cornerZ - 1, cornerX + 1, 68, cornerZ + 1));
        assertTrue(box.overlaps(-1, 66, -1, 1, 68, 1));
        assertFalse(box.overlaps(-1, 74, -1, 1, 80, 1), "above the slip");
        assertFalse(box.overlaps(-1, 60, -1, 1, 64, 1), "under its floor");
        // Along its length it reaches as far as it is long.
        Vector3d bow = box.world(0.0D, 0.0D, 19.0D);
        assertTrue(box.overlaps(bow.x - 0.5, 66, bow.z - 0.5, bow.x + 0.5, 68, bow.z + 0.5));
        Vector3d past = box.world(0.0D, 0.0D, 22.0D);
        assertFalse(box.overlaps(past.x - 0.5, 66, past.z - 0.5, past.x + 0.5, 68, past.z + 0.5));
    }

    // ------------------------------------------------------------------ fit

    /**
     * Whether a ship fits is the same whichever way the slip, and so the ship in it, faces. Measured off a
     * world box it is not: a long ship fits its slip north-south and is refused it at forty-five degrees.
     */
    @Test
    void aShipThatFitsFitsAtEveryHeading() {
        for (int yaw = 0; yaw < 360; yaw += 5) {
            BerthBox box = slip(yaw);
            // The ship's own +z is its bow; turned to lie along the facing.
            Quaterniond attitude = BerthMooring.attitude(new Quaterniond(), new Vector3d(0, 0, 1), box);
            assertTrue(BerthFit.fits(6.0D, 8.0D, 18.0D, attitude, box, 1.0D), "refused at yaw " + yaw);
            assertFalse(BerthFit.fits(6.0D, 8.0D, 18.5D, attitude, box, 1.0D), "too long, taken at yaw " + yaw);
            assertFalse(BerthFit.fits(6.5D, 8.0D, 18.0D, attitude, box, 1.0D), "too wide, taken at yaw " + yaw);
            assertFalse(BerthFit.fits(6.0D, 8.5D, 18.0D, attitude, box, 1.0D), "too tall, taken at yaw " + yaw);
        }
    }

    /** The fit is of the ship as it will sit: one whose bow is its own +x is measured that way round. */
    @Test
    void aShipIsMeasuredTheWayItsBowLies() {
        BerthBox box = slip(0);
        Quaterniond attitude = BerthMooring.attitude(new Quaterniond(), new Vector3d(1, 0, 0), box);
        assertTrue(BerthFit.fits(18.0D, 8.0D, 6.0D, attitude, box, 1.0D));
        assertFalse(BerthFit.fits(6.0D, 8.0D, 18.0D, attitude, box, 1.0D), "long across the slip, and taken");
    }

    /** A ship already turned in the world ends bow along the facing, not turned by the facing again. */
    @Test
    void theAttitudeTurnsTheBowOntoTheFacingFromWhereverItPoints() {
        for (int heading = 0; heading < 360; heading += 35) {
            Quaterniond current = new Quaterniond().rotateY(Math.toRadians(heading));
            Vector3d bow = current.transform(new Vector3d(0, 0, 1));
            for (int yaw = 0; yaw < 360; yaw += 25) {
                BerthBox box = slip(yaw);
                Quaterniond attitude = BerthMooring.attitude(current, bow, box);
                Vector3d moored = attitude.transform(new Vector3d(0, 0, 1));
                assertEquals(1.0D, moored.dot(box.facing()), 1.0e-9D, "heading " + heading + " into yaw " + yaw);
                assertEquals(1.0D, attitude.transform(new Vector3d(0, 1, 0)).y, 1.0e-9D, "a level ship was tipped");
            }
        }
    }

    /**
     * A ship that floats a little over is moored upright, and fits the slip its own size fits. Measured as it
     * flew, a five-block beam at a seventh of a degree of roll is refused a nine-block slip.
     */
    @Test
    void aShipThatFloatsALittleOverIsMooredLevelAndStillFits() {
        BerthBox box = new BerthBox(220.5D, -60.0D, 12.5D, 16.0D, 9.0D, 9.0D, 180.0D);
        // Bow along its own +x, pointing east, rolled and pitched a fraction of a degree.
        Quaterniond current = new Quaterniond().rotateZ(Math.toRadians(0.15D)).rotateX(Math.toRadians(-0.2D));
        Vector3d bow = current.transform(new Vector3d(1, 0, 0));
        Quaterniond attitude = BerthMooring.attitude(current, bow, box);
        assertEquals(1.0D, attitude.transform(new Vector3d(0, 1, 0)).y, EPS, "moored leaning");
        assertEquals(1.0D, attitude.transform(new Vector3d(1, 0, 0)).dot(box.facing()), EPS);
        assertTrue(BerthFit.fits(9.0D, 5.0D, 5.0D, attitude, box, 2.0D));
        assertFalse(BerthFit.fits(9.0D, 5.0D, 5.0D, current, box, 2.0D), "the test's lean is too slight to matter");

        Quaterniond lined = BerthMooring.lineUp(current, 9.0D, 5.0D, 5.0D, box, 2.0D);
        assertEquals(1.0D, lined.transform(new Vector3d(0, 1, 0)).y, EPS, "a ship flown in was moored leaning");
    }

    /**
     * The bow as a drive hands it over: levelled in the world first ({@code WarpFlight.worldBow}). Brought back
     * into a hull tilted two ways at once, that is a hair off the ship's own axis, and was turned into the slip
     * that hair askew - a nine-block beam then measured 9.00001 across and was refused the thirteen-block slip it
     * fits exactly. Seen in game 2026-10-14, at the pitch and roll used here, which are that hull's own.
     */
    @Test
    void aBowLevelledInTheWorldStillMoorsSquare() {
        BerthBox box = new BerthBox(100045.5D, 100.0D, 100043.5D, 24.0D, 13.0D, 13.0D, 180.0D);
        Quaterniond current = new Quaterniond(0.005096646957099438D, 6.581694833585061E-8D, 7.009170803939924E-5D,
                0.999987006187439D).normalize();
        for (Vector3d end : new Vector3d[] {new Vector3d(0, 0, -1), new Vector3d(0, 0, 1), new Vector3d(1, 0, 0),
                new Vector3d(-1, 0, 0)}) {
            Vector3d bow = current.transform(new Vector3d(end));
            bow.y = 0.0D;
            bow.normalize();
            Quaterniond attitude = BerthMooring.attitude(current, bow, box);
            assertEquals(1.0D, attitude.transform(new Vector3d(end)).dot(box.facing()), 1.0e-12D, "askew, bow " + end);
            double along = end.x != 0.0D ? 9.0D : 7.0D;
            double across = end.x != 0.0D ? 7.0D : 9.0D;
            BerthFit.Sitting sitting = BerthFit.sitting(9.0D, 6.0D, 7.0D, attitude, box);
            assertEquals(along, sitting.along(), 1.0e-9D, "bow " + end);
            assertEquals(across, sitting.across(), 1.0e-9D, "bow " + end);
            assertTrue(BerthFit.fits(9.0D, 6.0D, 7.0D, attitude, box, 2.0D), "an exact fit refused, bow " + end);
        }
    }

    private static void assertVec(double x, double y, double z, Vector3d actual) {
        assertEquals(x, actual.x, EPS);
        assertEquals(y, actual.y, EPS);
        assertEquals(z, actual.z, EPS);
    }
}
