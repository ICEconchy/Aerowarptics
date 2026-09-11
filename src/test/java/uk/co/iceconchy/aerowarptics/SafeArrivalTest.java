package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That an arrival proves the aperture and throat clear, not just the hull's box.
 *
 * <p>The hull comes out of a rift that is wider than it is, and flies out through the throat behind
 * that aperture. A block that clears the hull can still foul the opening it flies through, so the
 * volume each arrival candidate has to prove clear is widened to the aperture's radius. This pins down
 * that widening and that the departure and arrival sides are treated congruently for a symmetric
 * course.
 */
class SafeArrivalTest {

    private static final double EPSILON = 1.0e-9D;

    private static BoundingBox3d hull() {
        return new BoundingBox3d(0.0D, 0.0D, 0.0D, 10.0D, 10.0D, 10.0D);
    }

    /** The aperture margin is the aperture's reach past the hull, and never negative. */
    @Test
    void theApertureMarginIsHowFarTheApertureReachesPastTheHull() {
        assertEquals(1.75D, SafeArrival.apertureMargin(6.75D, 5.0D), EPSILON);
        assertEquals(0.0D, SafeArrival.apertureMargin(3.0D, 5.0D), EPSILON,
                "an aperture is never narrower than its hull");
        assertEquals(0.0D, SafeArrival.apertureMargin(5.0D, 5.0D), EPSILON);
    }

    /** The candidate volume is the hull swept back along the run-out, widened by clearance and margin. */
    @Test
    void theCandidateVolumeIsTheRunOutWidenedToTheAperture() {
        Vector3d approach = new Vector3d(1.0D, 0.0D, 0.0D);
        BoundingBox3d volume = SafeArrival.candidateVolume(hull(), approach, 50.0D, 2.0D, 3.0D);
        // 10-block hull + 50-block run-out = 60 along the bearing, plus 2*(clearance+margin) each side.
        assertEquals(60.0D + 2.0D * 5.0D, volume.maxX() - volume.minX(), EPSILON);
        assertEquals(10.0D + 2.0D * 5.0D, volume.maxY() - volume.minY(), EPSILON);
    }

    /**
     * A block on the exit plane, just outside the hull but inside the aperture, is inside the volume.
     *
     * <p>The failure the plain hull sweep missed: the block never touches the hull, so a hull-only
     * test would clear the candidate and the ship would fly out of an aperture with a block in its
     * mouth.
     */
    @Test
    void aBlockOnTheExitApertureFallsInsideTheTestedVolume() {
        Vector3d approach = new Vector3d(1.0D, 0.0D, 0.0D);
        double margin = 4.0D;
        BoundingBox3d volume = SafeArrival.candidateVolume(hull(), approach, 50.0D, 0.0D, margin);
        // The hull reaches z=10; the aperture reaches z=14. A block at z=13 is outside the hull and
        // inside the aperture, and must be covered by the tested volume.
        assertTrue(volume.maxZ() >= 13.0D,
                "the aperture ring at the exit plane was left out of the tested volume");
        assertTrue(volume.minZ() <= -3.0D, "the aperture ring is symmetric about the hull");
    }

    /**
     * The entry and exit clearance volumes are congruent for a symmetric course.
     *
     * <p>Same hull, same reach, same margins, opposite bearings: the departure volume proved by
     * {@link LaunchClearance} and the arrival volume proved by {@link SafeArrival} have identical
     * extent. This is the "same checks on both apertures" the plan calls for, made concrete.
     */
    @Test
    void theEntryAndExitVolumesAreCongruent() {
        Vector3d bow = new Vector3d(1.0D, 0.0D, 0.0D);
        Vector3d approach = new Vector3d(-1.0D, 0.0D, 0.0D); // the exit is entered from the far side
        BoundingBox3d departure = LaunchClearance.departureVolume(hull(), bow, 80.0D, 2.0D, 3.0D);
        BoundingBox3d arrival = SafeArrival.candidateVolume(hull(), approach, 80.0D, 2.0D, 3.0D);
        assertEquals(departure.width(), arrival.width(), EPSILON);
        assertEquals(departure.height(), arrival.height(), EPSILON);
        assertEquals(departure.length(), arrival.length(), EPSILON);
    }
}
