package uk.co.iceconchy.aerowarptics.berth;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A simulated hull drawn in to its mark, and one held there against its own weight. */
class BerthMooringTest {

    private static final BerthBox SLIP = new BerthBox(0.5D, 64.0D, 0.5D, 24.0D, 10.0D, 10.0D, 90.0D);

    /** One tick of a hull doing as it is told: moved by the velocity commanded, turned by the turn. */
    private static void fly(Vector3d centre, Quaterniond attitude, BerthMooring.Pull pull) {
        centre.add(pull.linear());
        double angle = pull.angular().length();
        if (angle > 0.0D) {
            Vector3d axis = new Vector3d(pull.angular()).normalize();
            attitude.premul(new Quaterniond().rotateAxis(angle, axis.x, axis.y, axis.z)).normalize();
        }
    }

    @Test
    void aShipFlownInIsDrawnToItsMarkAndTurnedToTheFacing() {
        Vector3d mark = SLIP.mark(4.0D, 1.0D);
        Vector3d centre = new Vector3d(7.0D, 70.0D, 3.5D);
        Quaterniond attitude = new Quaterniond().rotateY(Math.toRadians(140));
        Quaterniond wanted = BerthMooring.lineUp(attitude, 6.0D, 4.0D, 18.0D, SLIP, 1.0D);
        assertNotNull(wanted);

        int ticks = 0;
        double last = Double.MAX_VALUE;
        while (ticks < 400) {
            BerthMooring.Pull pull = BerthMooring.draw(centre, mark, attitude, wanted);
            if (pull.moored()) {
                break;
            }
            assertTrue(pull.linear().length() <= BerthMooring.MAX_DRAW + 1.0e-9D, "yanked");
            assertTrue(pull.angular().length() <= BerthMooring.MAX_TURN + 1.0e-9D, "spun");
            assertTrue(pull.distance() <= last + 1.0e-9D, "drawn away from its mark at tick " + ticks);
            last = pull.distance();
            fly(centre, attitude, pull);
            ticks++;
        }
        assertTrue(ticks < 400, "never moored");
        assertTrue(ticks > 20, "snapped in rather than glided");
        // One of its own level ends lies along the facing.
        double along = Math.abs(attitude.transform(new Vector3d(0, 0, 1)).dot(SLIP.facing()));
        assertEquals(1.0D, along, 0.01D);
    }

    /** A ship is taken the way it came, by whichever end is nearest the facing - not turned end for end. */
    @Test
    void aShipIsLinedUpByTheEndThatNeedsLeastTurning() {
        // Bow twenty degrees off the facing: stays bow along it. Twenty off the reverse: stays stern along it.
        Quaterniond nearlyAhead = new Quaterniond().rotateY(Math.toRadians(-90 + 20));
        Quaterniond turned = BerthMooring.lineUp(nearlyAhead, 6.0D, 4.0D, 18.0D, SLIP, 1.0D);
        assertEquals(1.0D, turned.transform(new Vector3d(0, 0, 1)).dot(SLIP.facing()), 1.0e-9D);

        Quaterniond nearlyAstern = new Quaterniond().rotateY(Math.toRadians(90 + 20));
        Quaterniond backed = BerthMooring.lineUp(nearlyAstern, 6.0D, 4.0D, 18.0D, SLIP, 1.0D);
        assertEquals(-1.0D, backed.transform(new Vector3d(0, 0, 1)).dot(SLIP.facing()), 1.0e-9D);
    }

    /** Unless only another way round fits: a ship lying across a slip it fits lengthways is turned lengthways. */
    @Test
    void aShipThatOnlyFitsOneWayIsTurnedThatWay() {
        Quaterniond across = new Quaterniond().rotateY(Math.toRadians(5));
        Quaterniond turned = BerthMooring.lineUp(across, 6.0D, 4.0D, 18.0D, SLIP, 1.0D);
        assertNotNull(turned);
        assertTrue(BerthFit.fits(6.0D, 4.0D, 18.0D, turned, SLIP, 1.0D));
        assertEquals(1.0D, Math.abs(turned.transform(new Vector3d(0, 0, 1)).dot(SLIP.facing())), 1.0e-9D);
        assertNull(BerthMooring.lineUp(across, 12.0D, 4.0D, 30.0D, SLIP, 1.0D), "a ship that fits no way was caught");
    }

    /**
     * A hull whose balloons no longer carry it: every tick it falls, and every tick the hold puts back half of
     * what is left. It settles a hair under its mark and stays moored.
     */
    @Test
    void aHeldShipDoesNotSinkOutOfItsBerth() {
        Vector3d mark = SLIP.mark(4.0D, 1.0D);
        Vector3d centre = new Vector3d(mark);
        Quaterniond attitude = BerthMooring.attitude(new Quaterniond(), new Vector3d(0, 0, 1), SLIP);
        double fall = 0.04D; // blocks a tick: a stone dropped for a twentieth of a second
        for (int tick = 0; tick < 600; tick++) {
            BerthMooring.Pull pull = BerthMooring.hold(centre, mark, attitude, attitude);
            assertTrue(pull.moored(), "let go at tick " + tick + ", " + pull.distance() + " from its mark");
            fly(centre, attitude, pull);
            centre.y -= fall;
        }
        assertTrue(mark.y - centre.y < 0.1D, "sagged " + (mark.y - centre.y));
    }

    @Test
    void onlyASlowShipInsideTheSlipThatFitsIsCaught() {
        Vector3d inside = SLIP.mark(4.0D, 1.0D);
        Vector3d outside = SLIP.world(0.0D, 4.0D, 14.0D);
        assertTrue(BerthMooring.catches(SLIP, inside, 0.05D, 0.15D, true));
        assertFalse(BerthMooring.catches(SLIP, inside, 0.30D, 0.15D, true), "a ship flying through was caught");
        assertFalse(BerthMooring.catches(SLIP, inside, 0.05D, 0.15D, false));
        assertFalse(BerthMooring.catches(SLIP, outside, 0.05D, 0.15D, true));
        // Once caught, it is kept a little past the slip's edge, so one at the edge is not caught and dropped by turns.
        assertTrue(BerthMooring.keeps(SLIP, outside));
        assertFalse(BerthMooring.keeps(SLIP, SLIP.world(0.0D, 4.0D, 12.0D + BerthMooring.KEEP_SLACK + 0.5D)));
    }
}
