package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.GateTraversal;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coming out of the far gate the right way round.
 *
 * <p>Almost everything here is about handedness. A portal that reflects rather than rotates puts a
 * vehicle out with its left on its right, and that is not something anybody notices by driving through
 * one gate - it looks like a turn. It shows up as a car that steers backwards afterwards.
 */
class GateTraversalTest {

    private static final double EPSILON = 1.0e-9D;

    /** A five by five gate lying along X, so you pass through it along Z. */
    private static RiftGateShape acrossX(int x, int y, int z) {
        return new RiftGateShape(Direction.Axis.X, x, y, z, x + 4, y + 4, z);
    }

    /** A five by five gate lying along Z, so you pass through it along X. */
    private static RiftGateShape acrossZ(int x, int y, int z) {
        return new RiftGateShape(Direction.Axis.Z, x, y, z, x, y + 4, z + 4);
    }

    // ------------------------------------------------------------------ yaw

    @Test
    void aGateFacesTheWayYouPassThroughIt() {
        assertEquals(0.0F, GateTraversal.yawOf(acrossX(0, 64, 0)), "a gate across X should face south");
        assertEquals(-90.0F, GateTraversal.yawOf(acrossZ(0, 64, 0)), "a gate across Z should face east");
    }

    @Test
    void twoGatesTheSameWayRoundTurnNobody() {
        assertEquals(0.0F, GateTraversal.yawDelta(acrossX(0, 64, 0), acrossX(90, 70, 40)), EPSILON);
        assertEquals(0.0F, GateTraversal.yawDelta(acrossZ(0, 64, 0), acrossZ(90, 70, 40)), EPSILON);
    }

    @Test
    void twoGatesAtRightAnglesTurnAQuarter() {
        assertEquals(-90.0F, GateTraversal.yawDelta(acrossX(0, 64, 0), acrossZ(90, 70, 40)), EPSILON);
        assertEquals(90.0F, GateTraversal.yawDelta(acrossZ(0, 64, 0), acrossX(90, 70, 40)), EPSILON);
    }

    /**
     * There is no half turn, and there should not be.
     *
     * <p>A gate's plane has two faces, and which one you leave by is decided by which way you were
     * going - not by the yaw. Turning somebody a hundred and eighty degrees as well would send them
     * back the way they came.
     */
    @Test
    void thereIsNoHalfTurn() {
        RiftGateShape[] gates = {acrossX(0, 64, 0), acrossZ(0, 64, 0), acrossX(9, 9, 9), acrossZ(9, 9, 9)};
        for (RiftGateShape from : gates) {
            for (RiftGateShape to : gates) {
                float delta = Math.abs(GateTraversal.yawDelta(from, to));
                assertTrue(delta == 0.0F || delta == 90.0F, "unexpected turn of " + delta);
            }
        }
    }

    // -------------------------------------------------------------- rotation

    @Test
    void aHeadingTurnsTheWayMinecraftMeasuresIt() {
        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 east = GateTraversal.rotateYaw(south, -90.0F);
        assertEquals(1.0D, east.x, 1.0e-6D);
        assertEquals(0.0D, east.z, 1.0e-6D);

        Vec3 north = GateTraversal.rotateYaw(south, 180.0F);
        assertEquals(0.0D, north.x, 1.0e-6D);
        assertEquals(-1.0D, north.z, 1.0e-6D);
    }

    @Test
    void heightIsNeverTurned() {
        Vec3 turned = GateTraversal.rotateYaw(new Vec3(1.0D, 7.0D, -3.0D), 90.0F);
        assertEquals(7.0D, turned.y, EPSILON, "a gate turned somebody upside down");
    }

    /**
     * A yaw and a direction are the same fact, either way round.
     *
     * <p>This is what a gate on a hull turns a traveller's facing with, since {@code yawDelta} only
     * means anything inside a gate's own local frame - see {@code worldYaw} on
     * {@code RiftGateBlockEntity}. If the round trip did not land exactly back where it started, a
     * gate on a level hull facing an arbitrary heading would turn a traveller's facing by a few
     * degrees every time they crossed, which is exactly the kind of drift nothing would notice until
     * a save file was months old.
     */
    @Test
    void aYawAndADirectionRoundTrip() {
        // Mth's sin and cos are a fast lookup table, not the exact function, so the round trip is
        // close rather than exact - loose enough to allow for that and still catch a real mistake,
        // such as the sign of yawOfDirection's x term being flipped.
        for (float yaw : new float[] {0.0F, 33.0F, 90.0F, 145.5F, -12.0F, -179.0F}) {
            Vec3 direction = GateTraversal.directionOfYaw(yaw);
            assertEquals(1.0D, direction.length(), 1.0e-2D, "not a unit vector");
            assertEquals(yaw, GateTraversal.yawOfDirection(direction), 1.0e-1F);
        }
    }

    /** Zero is south, and it grows the same way {@link #aHeadingTurnsTheWayMinecraftMeasuresIt} pins. */
    @Test
    void directionOfYawAgreesWithRotateYaw() {
        Vec3 south = new Vec3(0.0D, 0.0D, 1.0D);
        assertEquals(south.x, GateTraversal.directionOfYaw(0.0F).x, 1.0e-6D);
        assertEquals(south.z, GateTraversal.directionOfYaw(0.0F).z, 1.0e-6D);

        for (float degrees : new float[] {33.0F, -90.0F, 180.0F}) {
            Vec3 expected = GateTraversal.rotateYaw(south, degrees);
            Vec3 actual = GateTraversal.directionOfYaw(degrees);
            assertEquals(expected.x, actual.x, 1.0e-6D);
            assertEquals(expected.z, actual.z, 1.0e-6D);
        }
    }

    // ------------------------------------------------------------------ side

    @Test
    void eitherSideOfThePlaneIsADifferentSide() {
        // The whole of crossing detection rests on this: two points either side of the opening have
        // to disagree, or a gate can never tell that anything has gone through it.
        RiftGateShape gate = acrossX(0, 64, 20);
        assertFalse(gate.side(new Vec3(2.5D, 66.0D, 19.0D)));
        assertTrue(gate.side(new Vec3(2.5D, 66.0D, 22.0D)));
        // Being far away on one side is still that side, which is what lets something walk in.
        assertFalse(gate.side(new Vec3(2.5D, 66.0D, -400.0D)));
    }

    @Test
    void movingAlongTheOpeningIsNeverAChangeOfSide() {
        RiftGateShape gate = acrossX(0, 64, 20);
        boolean walking = gate.side(new Vec3(0.5D, 66.0D, 19.0D));
        assertEquals(walking, gate.side(new Vec3(4.5D, 66.0D, 19.0D)));
        assertEquals(walking, gate.side(new Vec3(2.5D, 69.0D, 19.5D)));
    }

    // --------------------------------------------------------------- arrival

    @Test
    void youComeOutTheSideYouWereHeading() {
        RiftGateShape from = acrossX(0, 64, 20);
        RiftGateShape to = acrossX(100, 64, 60);

        Vec3 forwards = new Vec3(2.5D, 66.5D, 22.0D);
        GateTraversal.Arrival ahead = GateTraversal.map(from, to, forwards, new Vec3(0.0D, 0.0D, 0.5D));
        assertTrue(to.distanceToPlane(ahead.position()) > 0.0D, "arrived behind the far gate");

        Vec3 backwards = new Vec3(2.5D, 66.5D, 18.0D);
        GateTraversal.Arrival behind = GateTraversal.map(from, to, backwards, new Vec3(0.0D, 0.0D, -0.5D));
        assertTrue(to.distanceToPlane(behind.position()) < 0.0D, "arrived in front of the far gate");
    }

    @Test
    void momentumIsCarriedThroughTheTurn() {
        RiftGateShape from = acrossX(0, 64, 20);
        RiftGateShape to = acrossZ(100, 64, 60);
        Vec3 driving = new Vec3(0.0D, 0.0D, 0.9D);
        GateTraversal.Arrival arrival = GateTraversal.map(from, to, new Vec3(2.5D, 66.5D, 22.0D), driving);
        // Same speed, turned onto the far gate's axis rather than left pointing the old way.
        assertEquals(driving.length(), arrival.motion().length(), 1.0e-6D, "the gate changed the speed");
        assertEquals(0.9D, arrival.motion().x, 1.0e-6D, "the gate did not turn the momentum");
        assertEquals(0.0D, arrival.motion().z, 1.0e-6D);
    }

    /**
     * The property that catches a mirror.
     *
     * <p>Going through and coming straight back has to put a traveller where they started - same place
     * across the opening, same height, same heading. A reflection passes every other test in this file
     * and fails this one, because reflecting twice does not get you back to a rotation.
     */
    @Test
    void goingThroughAndComingBackReturnsYouToWhereYouStarted() {
        RiftGateShape[] gates = {
                acrossX(0, 64, 20), acrossZ(100, 64, 60), acrossX(-40, 70, -12), acrossZ(8, 12, 300)};

        for (RiftGateShape from : gates) {
            for (RiftGateShape to : gates) {
                if (from.equals(to)) {
                    continue;
                }
                // Somewhere deliberately off-centre, so a mirror has something to get wrong.
                Vec3 entry = from.pointAt(-0.6D, 0.4D, 1.5D);
                double across = from.acrossFraction(entry);
                double up = from.upFraction(entry);
                Vec3 motion = new Vec3(0.31D, 0.0D, -0.22D);

                GateTraversal.Arrival out = GateTraversal.map(from, to, entry, motion);
                // Turn round and drive back in from where you were put down.
                GateTraversal.Arrival back = GateTraversal.map(to, from, out.position(), out.motion());

                assertEquals(across, from.acrossFraction(back.position()), 1.0e-6D,
                        "came back to a different part of the opening");
                assertEquals(up, from.upFraction(back.position()), 1.0e-6D,
                        "came back at a different height");
                assertEquals(motion.x, back.motion().x, 1.0e-6D, "momentum did not survive the round trip");
                assertEquals(motion.z, back.motion().z, 1.0e-6D, "momentum did not survive the round trip");
                assertEquals(0.0F, GateTraversal.yawDelta(from, to) + GateTraversal.yawDelta(to, from),
                        1.0e-4F, "the two turns did not cancel");
            }
        }
    }

    @Test
    void aNarrowGateNeverPutsYouInItsOwnFrame() {
        // Entering a wide gate at its edge must land inside a narrow one, not past its side.
        RiftGateShape wide = new RiftGateShape(Direction.Axis.X, 0, 64, 20, 14, 78, 20);
        RiftGateShape narrow = acrossZ(100, 64, 60);
        Vec3 edge = wide.pointAt(-1.0D, 1.0D, 2.0D);
        GateTraversal.Arrival arrival = GateTraversal.map(wide, narrow, edge, Vec3.ZERO);
        assertTrue(Math.abs(narrow.acrossFraction(arrival.position())) <= 1.0D + 1.0e-9D,
                "arrived outside the far gate's width");
        assertTrue(Math.abs(narrow.upFraction(arrival.position())) <= 1.0D + 1.0e-9D,
                "arrived outside the far gate's height");
    }

    @Test
    void whatFitsThroughIsWhatTheOpeningIs() {
        RiftGateShape gate = acrossX(0, 64, 20);
        assertTrue(gate.admits(4.0D, 4.0D), "a small vehicle was refused by a five-wide gate");
        assertTrue(gate.admits(5.0D, 5.0D), "a vehicle exactly the size of the opening was refused");
        assertFalse(gate.admits(6.0D, 3.0D), "something too wide was let through");
        assertFalse(gate.admits(3.0D, 6.0D), "something too tall was let through");
    }
}
