package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;
import uk.co.iceconchy.aerowarptics.warp.ObstructionScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * That the departure path is proven clear the same way the arrival always was.
 *
 * <p>For years the arrival end was searched exhaustively while the departure end - the entry aperture,
 * the run at it, and the corridor run flown behind it in real world space - was never checked at all.
 * A hull flown into a hillside is exactly what makes Sable's solver eject it, the launch-thousands-of-
 * blocks report. This sweeps a wall through every part of that path and proves it caught, the mirror
 * of {@link ObstructionScanTest} for the departure side.
 */
class LaunchClearanceTest {

    private static final long PLENTY = 10_000_000L;

    /** A ten-block hull at the origin, flying along +X. */
    private static BoundingBox3d hull() {
        return new BoundingBox3d(0.0D, 0.0D, 0.0D, 10.0D, 10.0D, 10.0D);
    }

    private static final Vector3d BOW = new Vector3d(1.0D, 0.0D, 0.0D);

    /** Nothing anywhere ahead is a clear launch. */
    @Test
    void clearAirAheadPasses() {
        assertEquals(ObstructionScan.Verdict.CLEAR,
                LaunchClearance.scanAhead(hull(), BOW, 100.0D, 0.0D, 0.0D, PLENTY, (x, y, z) -> false));
    }

    /**
     * A one-block wall is caught at the entry plane, mid-corridor and the far end alike.
     *
     * <p>The three places the old (absent) check ignored. With the hull at [0,10] and a reach of 100,
     * the proven volume runs to x=110; a wall across the path at any of those x is an obstruction.
     */
    @Test
    void aThinWallIsCaughtAnywhereAlongTheCorridor() {
        for (int wall : new int[]{10 /* entry plane */, 60 /* mid-corridor */, 110 /* far end */}) {
            int at = wall;
            assertEquals(ObstructionScan.Verdict.OBSTRUCTED,
                    LaunchClearance.scanAhead(hull(), BOW, 100.0D, 0.0D, 0.0D, PLENTY,
                            (x, y, z) -> x == at),
                    "a wall at x=" + at + " down the corridor was flown straight into");
        }
    }

    /** A wall beyond the proven reach is not this warp's problem. */
    @Test
    void aWallPastTheReachIsNotCaught() {
        assertEquals(ObstructionScan.Verdict.CLEAR,
                LaunchClearance.scanAhead(hull(), BOW, 100.0D, 0.0D, 0.0D, PLENTY, (x, y, z) -> x == 200));
    }

    /**
     * The swept volume is exactly the hull's span plus the reach, along the bearing.
     *
     * <p>Pinned so a later change to the sweep cannot silently shorten the proven path - the failure
     * that would not throw is a corridor proven one block shorter than it is flown.
     */
    @Test
    void theSweptLengthIsTheHullSpanPlusTheReach() {
        BoundingBox3d volume = LaunchClearance.departureVolume(hull(), BOW, 100.0D, 0.0D, 0.0D);
        assertEquals(110.0D, volume.maxX() - volume.minX(), 1.0e-9D,
                "10-block hull + 100-block reach = 110 along the bow");
    }

    /** Clearance and aperture margin widen the volume on every side. */
    @Test
    void theApertureMarginWidensTheProvenVolume() {
        BoundingBox3d bare = LaunchClearance.departureVolume(hull(), BOW, 100.0D, 0.0D, 0.0D);
        BoundingBox3d widened = LaunchClearance.departureVolume(hull(), BOW, 100.0D, 1.0D, 3.0D);
        // 2 * (clearance + apertureMargin) added to each dimension.
        assertEquals((bare.maxY() - bare.minY()) + 8.0D, widened.maxY() - widened.minY(), 1.0e-9D);
        assertNotEquals(bare.maxZ() - bare.minZ(), widened.maxZ() - widened.minZ());
    }

    /**
     * A block that clears the bare hull but fouls the aperture opening is still caught.
     *
     * <p>The rift is wider than the hull, so the opening it flies through reaches past the hull's own
     * box. A block sitting in that ring never touches the hull and would pass a hull-only check - and
     * fouls the aperture all the same.
     */
    @Test
    void aBlockInTheApertureRingIsCaught() {
        // Hull spans z in [0,10]; an aperture margin of 4 reaches to z=14. A block at z=13 is outside
        // the hull but inside the aperture.
        int marginBlock = 13;
        assertEquals(ObstructionScan.Verdict.OBSTRUCTED,
                LaunchClearance.scanAhead(hull(), BOW, 100.0D, 0.0D, 4.0D, PLENTY,
                        (x, y, z) -> z == marginBlock),
                "a block in the aperture ring but clear of the hull was missed");
        // With no aperture margin the same block is outside the proven volume.
        assertEquals(ObstructionScan.Verdict.CLEAR,
                LaunchClearance.scanAhead(hull(), BOW, 100.0D, 0.0D, 0.0D, PLENTY,
                        (x, y, z) -> z == marginBlock));
    }
}
