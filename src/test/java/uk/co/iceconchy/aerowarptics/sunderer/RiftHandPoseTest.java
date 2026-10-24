package uk.co.iceconchy.aerowarptics.sunderer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand through a straining Sunderer: nothing of it before the membrane splits, nothing of it after it has
 * gone, the elbow never out of the slit, every joint moving smoothly, and its fingers closing on the block it
 * came for wherever that block is in the ring.
 */
class RiftHandPoseTest {

    private static final RiftHandPose.Point VIEWER = new RiftHandPose.Point(2.0D, 0.5D, 5.0D);

    /** Blocks of frame it might reach for: up, down, sideways and diagonal, as near as the slit leaves them. */
    private static final List<RiftHandPose.Point> GRIPS = List.of(
            new RiftHandPose.Point(0.0D, 2.5D, 0.0D),
            new RiftHandPose.Point(0.0D, -2.5D, 0.0D),
            new RiftHandPose.Point(2.5D, 0.0D, 0.0D),
            new RiftHandPose.Point(-1.8D, 1.8D, 0.0D),
            new RiftHandPose.Point(1.0D, 0.5D, 0.0D));

    @Test
    void nothingComesThroughBeforeTheMembraneSplits() {
        for (RiftHandPose.Point grip : GRIPS) {
            for (double beat = 0.0D; beat <= RiftHandPose.BULGE_END; beat += 0.002D) {
                for (RiftHandPose.Point joint : RiftHandPose.at(beat, grip, VIEWER).joints()) {
                    assertTrue(joint.n() <= 1.0e-9D, "a joint was through at " + beat + ": " + joint);
                }
            }
        }
    }

    @Test
    void itIsAllGoneAtTheEnd() {
        for (RiftHandPose.Point grip : GRIPS) {
            RiftHandPose.Pose pose = RiftHandPose.at(1.0D, grip, VIEWER);
            for (RiftHandPose.Point joint : pose.joints()) {
                assertTrue(joint.n() <= 1.0e-9D, "something was left out of the plane: " + joint);
            }
            assertEquals(0.0D, pose.split(), 1.0e-9D, "the slit never sealed");
        }
    }

    @Test
    void theFingersComeFirst() {
        RiftHandPose.Pose pose = RiftHandPose.at((RiftHandPose.BULGE_END + RiftHandPose.FINGERS_END) / 2.0D,
                GRIPS.get(0), VIEWER);
        assertTrue(pose.tips().stream().anyMatch(tip -> tip.n() > 0.0D), "no fingertip was through");
        assertTrue(pose.wrist().n() < 0.0D, "the wrist came through with the fingers");
    }

    @Test
    void theElbowStaysInTheSlit() {
        for (RiftHandPose.Point grip : GRIPS) {
            for (double beat = 0.0D; beat <= 1.0D; beat += 0.002D) {
                RiftHandPose.Point elbow = RiftHandPose.at(beat, grip, VIEWER).elbow();
                assertTrue(elbow.n() <= 0.0D, "the elbow came out of the plane at " + beat);
                assertEquals(0.0D, Math.hypot(elbow.u(), elbow.v()), 1.0e-9D, "the elbow left the slit at " + beat);
            }
        }
    }

    /** A joint that jumps is a finger that teleports. Every joint, sampled finely across the whole beat. */
    @Test
    void noJointJumps() {
        for (RiftHandPose.Point grip : GRIPS) {
            for (RiftHandPose.Point viewer : new RiftHandPose.Point[]{VIEWER, null}) {
                List<RiftHandPose.Point> last = RiftHandPose.at(0.0D, grip, viewer).joints();
                int steps = 16_000;
                for (int step = 1; step <= steps; step++) {
                    double beat = step / (double) steps;
                    List<RiftHandPose.Point> now = RiftHandPose.at(beat, grip, viewer).joints();
                    for (int joint = 0; joint < now.size(); joint++) {
                        double moved = now.get(joint).distance(last.get(joint));
                        assertTrue(moved < 0.02D, "joint " + joint + " jumped " + moved + " at " + beat);
                    }
                    last = now;
                }
            }
        }
    }

    @Test
    void itClosesOnTheBlockItCameFor() {
        for (RiftHandPose.Point grip : GRIPS) {
            RiftHandPose.Pose pose = RiftHandPose.at(RiftHandPose.GRIP_END, grip, VIEWER);
            double distance = pose.carried().distance(grip);
            assertTrue(distance < 1.0D, "the hand closed " + distance + " from the block at " + grip);
        }
    }

    @Test
    void itTurnsToWhoeverIsThere() {
        double looking = (RiftHandPose.FEEL_END + RiftHandPose.NOTICE_END) / 2.0D;
        RiftHandPose.Pose seen = RiftHandPose.at(looking, GRIPS.get(0), VIEWER);
        RiftHandPose.Pose unseen = RiftHandPose.at(looking, GRIPS.get(0), null);
        RiftHandPose.Point toViewer = new RiftHandPose.Point(VIEWER.u() - seen.wrist().u(),
                VIEWER.v() - seen.wrist().v(), VIEWER.n() - seen.wrist().n());
        double facing = dot(seen.palmNormal(), toViewer) / toViewer.distance(new RiftHandPose.Point(0, 0, 0));
        double without = dot(unseen.palmNormal(), toViewer) / toViewer.distance(new RiftHandPose.Point(0, 0, 0));
        // The arm still points out of the plane while it looks, so the palm can only turn so far towards someone
        // standing off to one side - but it must turn markedly, and only when someone is there.
        assertTrue(facing > 0.4D, "it did not turn its palm to the viewer: " + facing);
        assertTrue(facing > without + 0.3D, "it faced the viewer about as much with nobody there");
    }

    @Test
    void theSlitLeavesTheBlockInReach() {
        double[] slit = RiftHandPose.slit(3.5D, 3.5D, 3.5D, 7.5D);
        assertEquals(RiftHandPose.SLIT_REACH, Math.hypot(3.5D - slit[0], 7.5D - slit[1]), 1.0e-9D);
        double[] near = RiftHandPose.slit(3.5D, 3.5D, 4.5D, 4.5D);
        assertEquals(3.5D, near[0], 1.0e-9D);
        assertEquals(3.5D, near[1], 1.0e-9D);
    }

    private static double dot(RiftHandPose.Point a, RiftHandPose.Point b) {
        return a.u() * b.u() + a.v() * b.v() + a.n() * b.n();
    }
}
