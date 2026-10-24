package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of the volume an arrival proves clear.
 *
 * <p>It used to be widened to the aperture's radius on every side, on the reasoning that a block
 * clearing the hull could still foul the opening it flies out of. The launch side gave that up first -
 * the rift is a picture, not a solid - and the arrival followed once it turned out the widening, which
 * grows with the hull, pushed a large ship's tested volume tens of blocks into the ground under its own
 * keel. The padded box survives as a pure helper the two sides share and the overlay draws; what an
 * arrival actually tests is {@link SafeArrival#arrivalSegments}.
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
     * A block beside the hull, inside the aperture's ring but clear of the hull and its clearance, is
     * not in what an arrival tests - above, beside, or below.
     *
     * <p>Below is the one that mattered: that ring reached under the keel, and for a large hull it
     * reached further than the search could climb, so every landing over flat ground was refused.
     */
    @Test
    void theArrivalTestsTheHullAndItsClearanceAndNotTheApertureRing() {
        Vector3d approach = new Vector3d(1.0D, 0.0D, 0.0D);
        double clearance = 2.0D;
        for (BoundingBox3d segment : SafeArrival.arrivalSegments(hull(), approach, 50.0D, clearance)) {
            assertTrue(segment.maxZ() <= 10.0D + clearance + EPSILON, "reached " + segment.maxZ());
            assertTrue(segment.minZ() >= -clearance - EPSILON, "reached " + segment.minZ());
            assertTrue(segment.minY() >= -clearance - EPSILON, "reached under the keel to " + segment.minY());
            assertTrue(segment.maxY() <= 10.0D + clearance + EPSILON);
        }
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
