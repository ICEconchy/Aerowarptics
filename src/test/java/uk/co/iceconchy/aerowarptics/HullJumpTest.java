package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.Pose3d;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.BorrowedMotion;
import uk.co.iceconchy.aerowarptics.airship.HullJump;
import uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundSettlePacket;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hull's jump, and everything that has to go through it with the hull.
 *
 * <p>The two reports this answers - an airship vanishing at a warp, and a player standing on its deck
 * being launched off it - were one fault: a client interpolating a teleport as movement, drawing the
 * hull part of the way across and carrying its crew the same distance in a tick. What is pinned here is
 * the arithmetic that replaces it. Every point that rode the hull lands on the same spot of the moved
 * hull; poses on either side of the jump are told apart; and once the near side has been carried
 * across, the per-tick change a passenger is carried by is the hull's own motion again, not the jump.
 */
class HullJumpTest {

    private static final double EPSILON = 1.0e-6D;

    /** Plot coordinates - the kind of numbers a hull's rotation point really has. */
    private static final Vector3d ROTATION_POINT = new Vector3d(20_480_016.5D, 70.0D, 20_480_008.5D);

    /** The warp that was reported: (60.84, 213.04, -338.49) -> (329.54, 117.39, -4955.81). */
    private static final Vector3d DEPARTURE = new Vector3d(60.84D, 213.04D, -338.49D);
    private static final Vector3d ARRIVAL = new Vector3d(329.54D, 117.39D, -4955.81D);

    private static Pose3d pose(Vector3d position, double yawDegrees) {
        Pose3d pose = new Pose3d();
        pose.position().set(position);
        pose.orientation().set(new Quaterniond().rotateY(Math.toRadians(yawDegrees)));
        pose.rotationPoint().set(ROTATION_POINT);
        return pose;
    }

    private static void assertNear(Vector3d expected, Vector3d actual, double tolerance, String message) {
        assertEquals(0.0D, expected.distance(actual), tolerance, message + ": expected " + expected + " got " + actual);
    }

    @Test
    void aPointOnTheDeckLandsOnTheSameSpotOfTheMovedHull() {
        Pose3d before = pose(DEPARTURE, 30.0D);
        Pose3d after = pose(ARRIVAL, 120.0D);
        HullJump jump = HullJump.between(before, after);

        // A plank near the stern and a player standing a block and a half above it.
        Vector3d plank = new Vector3d(ROTATION_POINT).add(-11.0D, 2.0D, 5.0D);
        Vector3d player = new Vector3d(plank).add(0.5D, 1.5D, 0.5D);
        for (Vector3d local : new Vector3d[]{plank, player}) {
            Vector3d world = before.transformPosition(local, new Vector3d());
            assertNear(after.transformPosition(local, new Vector3d()), jump.apply(world, new Vector3d()), EPSILON,
                    "whatever rode the hull must land where the same spot of the hull now is");
        }
    }

    @Test
    void aBufferedPoseMovesToWhatItWouldHaveBeenOnTheFarSide() {
        Pose3d before = pose(DEPARTURE, 30.0D);
        Pose3d after = pose(ARRIVAL, 120.0D);
        HullJump jump = HullJump.between(before, after);

        // A pose from three ticks before the jump, the hull drifting and turning a little.
        Pose3d earlier = pose(new Vector3d(DEPARTURE).add(-1.2D, 0.1D, 0.9D), 28.5D);
        Pose3d moved = jump.moved(earlier);

        // Measured with Sable's own pose maths, not the jump's: wherever a block of the earlier hull sat
        // relative to the hull's placement at the jump, it must sit the same way relative to the new one.
        Vector3d local = new Vector3d(ROTATION_POINT).add(4.0D, 3.0D, -7.0D);
        Vector3d world = earlier.transformPosition(local, new Vector3d());
        Vector3d expected = after.transformPosition(before.transformPositionInverse(world, new Vector3d()), new Vector3d());
        assertNear(expected, moved.transformPosition(local, new Vector3d()), EPSILON,
                "a carried pose must put every block where the jump puts that block");
        assertEquals(before.position().distance(earlier.position()), after.position().distance(moved.position()),
                EPSILON, "the hull's own motion before the jump is kept, only moved to the far side");
        assertNear(ROTATION_POINT, new Vector3d(moved.rotationPoint()), 0.0D,
                "a teleport does not move the rotation point within the plot");
    }

    /**
     * A part of the vessel - a tender docked alongside, a turret on a Swivel Bearing - goes through the hull's
     * own jump, and comes out exactly where it sat against the hull.
     *
     * <p>That is the whole of why a joint survives the warp. A joint is anchored at a point in each of the
     * two plots it joins; if both sub-levels move by one rigid transform, each anchor lands where the other
     * expects it and the joint has nothing to pull against. A part left where it was, which is what used to
     * happen, is pulled after the hull across the whole jump.
     */
    @Test
    void aPartMovedByTheHullsJumpKeepsItsPlaceAgainstTheHull() {
        Pose3d hullBefore = pose(DEPARTURE, 30.0D);
        Pose3d hullAfter = pose(ARRIVAL, 120.0D);
        HullJump jump = HullJump.between(hullBefore, hullAfter);

        // A tender docked off the port side, a little lower and turned on its own axis; its plot is
        // somewhere else in the grid entirely.
        Vector3d tenderPlot = new Vector3d(20_512_016.5D, 70.0D, 20_480_008.5D);
        Pose3d tender = new Pose3d();
        tender.position().set(new Vector3d(DEPARTURE).add(-14.0D, -3.0D, 6.0D));
        tender.orientation().set(new Quaterniond().rotateY(Math.toRadians(-65.0D)).rotateX(Math.toRadians(4.0D)));
        tender.rotationPoint().set(tenderPlot);

        Pose3d moved = jump.moved(tender);

        // The docking connector's tip on the tender, and the one on the hull it is locked to: at the same
        // world point before the jump, and so at the same world point after it.
        Vector3d tenderTip = new Vector3d(tenderPlot).add(2.0D, 1.0D, -3.5D);
        Vector3d world = tender.transformPosition(tenderTip, new Vector3d());
        Vector3d hullTip = hullBefore.transformPositionInverse(world, new Vector3d());
        assertNear(hullAfter.transformPosition(hullTip, new Vector3d()), moved.transformPosition(tenderTip, new Vector3d()),
                EPSILON, "the two halves of a joint must meet where they met before the jump");

        // And the tender's whole attitude against the hull's is what it was.
        Quaterniond relativeBefore = new Quaterniond(hullBefore.orientation()).conjugate().mul(tender.orientation());
        Quaterniond relativeAfter = new Quaterniond(hullAfter.orientation()).conjugate().mul(moved.orientation());
        assertEquals(1.0D, Math.abs(relativeBefore.dot(relativeAfter)), EPSILON,
                "a part must not come out turned against the hull it is fastened to");
        assertNear(tenderPlot, new Vector3d(moved.rotationPoint()), 0.0D, "its plot is its own and does not move");
    }

    @Test
    void nearSideAndFarSideAreToldApart() {
        HullJump jump = HullJump.between(pose(DEPARTURE, 0.0D), pose(ARRIVAL, 0.0D));
        // The last few ticks before the jump, and the first few after, at a brisk passage speed.
        for (int tick = 0; tick < 12; tick++) {
            Vector3d drift = new Vector3d(0.0D, 0.0D, -9.0D * tick);
            assertTrue(jump.behind(new Vector3d(DEPARTURE).sub(drift)), "tick -" + tick + " is on the near side");
            assertFalse(jump.behind(new Vector3d(ARRIVAL).add(drift)), "tick +" + tick + " is on the far side");
        }
    }

    /**
     * Idempotent, which is what lets the client look for stragglers every tick for a second without
     * carrying anything across twice.
     */
    @Test
    void aPoseCarriedAcrossIsNeverCarriedAgain() {
        HullJump jump = HullJump.between(pose(DEPARTURE, 10.0D), pose(ARRIVAL, 10.0D));
        Pose3d moved = jump.moved(pose(new Vector3d(DEPARTURE).add(0.0D, 0.0D, 3.0D), 10.0D));
        assertFalse(jump.behind(moved.position()));
    }

    /**
     * The failure itself, and that it is gone.
     *
     * <p>A player standing on a deck is carried each tick by the change between the hull's last pose and
     * its current one - and Sable keeps that change as the player's momentum. A client interpolating
     * across the jump puts the hull half-way (Sable's default delay is a tick and a half, so the
     * interpolation lands between snapshots): the change is thousands of blocks, the player is carried
     * that far, and keeps going. With the near side carried across first, the same interpolation is
     * between two far-side poses and the change is the hull's own motion.
     */
    @Test
    void interpolatingAcrossTheJumpWasTheThrowAndRealigningRemovesIt() {
        Pose3d lastBefore = pose(DEPARTURE, 45.0D);
        Pose3d firstAfter = pose(ARRIVAL, 45.0D);
        HullJump jump = HullJump.between(lastBefore, firstAfter);
        // The corridor holds the hull still before the jump; the far aperture starts moving it after.
        Pose3d secondAfter = pose(new Vector3d(ARRIVAL).add(0.0D, 0.0D, -1.6D), 45.0D);

        Vector3d feet = new Vector3d(ROTATION_POINT).add(2.0D, 3.0D, 2.0D);

        // Unaligned: the running pose goes from the last near-side snapshot to half-way.
        Pose3d halfWay = lastBefore.lerp(firstAfter, 0.5D, new Pose3d());
        double thrown = carried(lastBefore, halfWay, feet);
        assertTrue(BorrowedMotion.impossible(thrown),
                "half a warp in one tick is what the crew were carried by, was " + thrown);

        // Realigned: the same interpolation, between the carried-across near side and the far side.
        Pose3d realignedLast = jump.moved(lastBefore);
        Pose3d realignedHalf = realignedLast.lerp(secondAfter, 0.5D, new Pose3d());
        double carried = carried(realignedLast, realignedHalf, feet);
        assertTrue(carried < 2.0D, "after realignment a passenger moves only with the hull, was " + carried);
        assertFalse(BorrowedMotion.impossible(carried));
    }

    /** How far a point standing on the hull is carried by one tick's change of pose - Sable's rule. */
    private static double carried(Pose3d last, Pose3d current, Vector3d local) {
        Vector3d then = last.transformPosition(local, new Vector3d());
        Vector3d now = current.transformPosition(local, new Vector3d());
        return then.distance(now);
    }

    // ------------------------------------------------------------------ who goes with the hull

    /** A hull whose blocks run from (0, 60, 0) to (9, 64, 19) in its plot. */
    private static boolean inside(double x, double y, double z) {
        return Airship.withinHull(0, 60, 0, 9, 64, 19, x, y, z);
    }

    @Test
    void somebodyInTheAirAboveTheDeckGoesWithIt() {
        assertTrue(inside(4.5D, 65.0D, 10.0D), "standing on the top deck");
        assertTrue(inside(4.5D, 66.25D, 10.0D), "at the top of a jump from it");
        assertTrue(inside(4.5D, 61.0D, 10.0D), "in the hold");
        assertTrue(inside(-0.3D, 65.0D, 10.0D), "at the very rail, feet overhanging the last plank");
    }

    @Test
    void somebodyOutsideItDoesNot() {
        assertFalse(inside(-3.0D, 65.0D, 10.0D), "beside the hull, clear of the rail");
        assertFalse(inside(4.5D, 59.0D, 10.0D), "under the keel");
        assertFalse(inside(4.5D, 70.0D, 10.0D), "well above anything a jump reaches");
        assertFalse(inside(4.5D, 65.0D, 23.0D), "past the bow");
    }

    // ------------------------------------------------------------------ borrowed motion

    @Test
    void onlyATeleportCanLendAnImpossibleSpeed() {
        for (double flight : new double[]{0.0D, 1.6D, 9.0D, 48.0D}) {
            assertFalse(BorrowedMotion.impossible(flight), flight + " blocks a tick is something a hull does");
        }
        for (double jump : new double[]{128.0D, 2313.0D, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertTrue(BorrowedMotion.impossible(jump), jump + " blocks a tick is not flight");
        }
        assertEquals(0.0D, BorrowedMotion.sane(new Vector3d(0.0D, 0.0D, -2313.0D)).length(), 0.0D,
                "a fling is never passed on");
        assertEquals(1.6D, BorrowedMotion.sane(new Vector3d(0.0D, 0.0D, -1.6D)).length(), EPSILON,
                "a hull's real speed is");
    }

    // ------------------------------------------------------------------ the wire

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    @Test
    void theJumpSurvivesTheWire() {
        HullJump jump = HullJump.between(pose(DEPARTURE, 30.0D), pose(ARRIVAL, 120.0D));
        UUID ship = UUID.randomUUID();
        RegistryFriendlyByteBuf buf = buffer();
        ClientboundFoldCrossedPacket.STREAM_CODEC.encode(buf, ClientboundFoldCrossedPacket.of(ship, jump, true));
        ClientboundFoldCrossedPacket read = ClientboundFoldCrossedPacket.STREAM_CODEC.decode(buf);

        assertEquals(ship, read.shipId());
        assertTrue(read.carry());
        Vector3d somewhere = new Vector3d(70.0D, 215.0D, -330.0D);
        assertNear(jump.apply(somewhere, new Vector3d()), read.jump().apply(somewhere, new Vector3d()), EPSILON,
                "the client must carry things exactly as far as the server did");
    }

    @Test
    void aCrossingThatNamesNowhereIsRefused() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeUUID(UUID.randomUUID());
        buf.writeDouble(Double.NaN);
        buf.writeDouble(0.0D);
        buf.writeDouble(0.0D);
        assertThrows(IllegalArgumentException.class, () -> ClientboundFoldCrossedPacket.STREAM_CODEC.decode(buf));
    }

    @Test
    void aSettleCannotHandOnAFling() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeDouble(0.0D);
        buf.writeDouble(0.0D);
        buf.writeDouble(-5000.0D);
        ClientboundSettlePacket read = ClientboundSettlePacket.STREAM_CODEC.decode(buf);
        assertEquals(0.0D, read.hullVelocity().length(), 0.0D);
    }
}
