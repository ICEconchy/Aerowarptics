package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;
import uk.co.iceconchy.aerowarptics.warp.ObstructionScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final Vector3d DIAGONAL = new Vector3d(1.0D, 0.0D, 1.0D).normalize();

    /**
     * The box the sweep used to start from: the same ten-block hull, unioned with a propeller bearing's
     * contraption box and a sub-level moored alongside.
     *
     * <p>Create sizes a bearing contraption's box to enclose the blades' <em>full rotation</em>, so a
     * propeller off the port side reaches six blocks out whatever the blades are doing at the time.
     */
    private static BoundingBox3d assemblyWithPropellers() {
        return new BoundingBox3d(0.0D, 0.0D, -6.0D, 10.0D, 14.0D, 16.0D);
    }

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
     * the proven volume runs to x=110, so its last block is x=109; a wall across the path at any of
     * those x is an obstruction.
     */
    @Test
    void aThinWallIsCaughtAnywhereAlongTheCorridor() {
        for (int wall : new int[]{10 /* entry plane */, 60 /* mid-corridor */, 109 /* far end */}) {
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
     * A block only a propeller's swept disc reaches no longer refuses the launch.
     *
     * <p>The complaint this change answers. Unioning the bearing contraptions and the connected
     * sub-levels into the footprint widened the corridor by the span of the disc on every axis, for
     * the whole length of the run - so a block the hull never approaches, sitting out in that ring,
     * read as terrain in the ship's path. Sweeping the hull alone is the difference between the two
     * assertions below.
     */
    @Test
    void aBlockInAPropellerSweepNoLongerRefusesTheLaunch() {
        // The hull spans z in [0,10]; z = -3 is outside it and inside the old assembly box.
        int besideTheHull = -3;
        assertEquals(ObstructionScan.Verdict.OBSTRUCTED,
                LaunchClearance.scanAhead(assemblyWithPropellers(), BOW, 100.0D, 0.0D, 0.0D, PLENTY,
                        (x, y, z) -> z == besideTheHull),
                "the assembly footprint is what used to read this block as an obstruction");
        assertEquals(ObstructionScan.Verdict.CLEAR,
                LaunchClearance.scanAhead(hull(), BOW, 100.0D, 0.0D, 0.0D, PLENTY,
                        (x, y, z) -> z == besideTheHull),
                "the hull footprint must not: the hull itself never goes near it");
    }

    /**
     * Narrowing the footprint to the hull can only ever shrink the proven volume, never move it.
     *
     * <p>The failure that would not throw is a "tighter" corridor that is tighter in one place and
     * reaches somewhere new in another - it would wave through a wall the old check caught while
     * still refusing on open sky. Every segment of the hull sweep stays inside the volume the
     * assembly sweep proved, on an axis-aligned bearing and on a diagonal alike.
     */
    @Test
    void theHullSweepNeverReachesOutsideTheAssemblySweep() {
        for (Vector3d bow : List.of(BOW, DIAGONAL)) {
            BoundingBox3d whole =
                    LaunchClearance.departureVolume(assemblyWithPropellers(), bow, 120.0D, 1.0D, 3.0D);
            for (BoundingBox3d segment
                    : LaunchClearance.departureSegments(hull(), bow, 120.0D, 1.0D, 3.0D)) {
                assertTrue(segment.minX() >= whole.minX() - 1.0e-6D
                                && segment.maxX() <= whole.maxX() + 1.0e-6D
                                && segment.minY() >= whole.minY() - 1.0e-6D
                                && segment.maxY() <= whole.maxY() + 1.0e-6D
                                && segment.minZ() >= whole.minZ() - 1.0e-6D
                                && segment.maxZ() <= whole.maxZ() + 1.0e-6D,
                        "a hull segment escaped the old assembly volume on bow " + bow);
            }
        }
    }

    /**
     * A block in the aperture ring, clear of the hull, no longer refuses the launch.
     *
     * <p>The rift is wider than the hull, and the corridor used to be padded out to it - the teal box in
     * the clearance overlay - so terrain the hull never goes near read as an obstruction. The padded
     * volume does still reach the block; the segments actually tested must not.
     */
    @Test
    void aBlockInTheApertureRingIsNotTested() {
        // Hull spans z in [0,10]; an aperture margin of 4 plus a clearance of 2 reaches to z=16. A block
        // at z=13 is outside the hull but inside the padding.
        int marginBlock = 13;
        assertTrue(LaunchClearance.departureSegments(hull(), BOW, 100.0D, 2.0D, 4.0D).stream()
                        .anyMatch(s -> s.minZ() <= marginBlock && s.maxZ() > marginBlock),
                "the padded volume is supposed to reach this block, or the test proves nothing");
        for (BoundingBox3d segment : LaunchClearance.testedSegments(hull(), BOW, 100.0D)) {
            assertTrue(segment.maxZ() <= marginBlock,
                    "a tested segment reached past the hull into the aperture ring: " + segment);
        }
    }

    /**
     * The ground a moored hull rests on, and the blocks against its sides, do not refuse the launch.
     *
     * <p>The report from the overlay: the whole layer of stone under the deck marked red down the
     * length of the corridor, along with the column beside the far face. Neither is inside the amber
     * box. The hull here has settled a few hundredths into the ground, as a real one does, and every
     * block that only touches the sweep - below, above, either side, past the far end - must read
     * clear, while a block one layer further in must still be caught.
     */
    @Test
    void blocksTouchingTheSweepDoNotRefuseTheLaunch() {
        BoundingBox3d settled = new BoundingBox3d(0.0D, 63.97D, 0.0D, 10.0D, 74.0D, 10.0D);
        assertEquals(ObstructionScan.Verdict.CLEAR,
                LaunchClearance.scanAhead(settled, BOW, 100.0D, 0.0D, 0.0D, PLENTY,
                        (x, y, z) -> y <= 63 || y >= 74 || z <= -1 || z >= 10 || x <= -1 || x >= 110),
                "a block touching the hull sweep's faces was read as inside it");
        for (int[] inside : new int[][]{{0, 64, 0}, {109, 73, 9}, {50, 64, 5}}) {
            assertEquals(ObstructionScan.Verdict.OBSTRUCTED,
                    LaunchClearance.scanAhead(settled, BOW, 100.0D, 0.0D, 0.0D, PLENTY,
                            (x, y, z) -> x == inside[0] && y == inside[1] && z == inside[2]),
                    "a block inside the sweep at " + java.util.Arrays.toString(inside) + " was missed");
        }
    }

    /**
     * Only a block reaching past the contact tolerance counts, on the near side and the far side
     * alike.
     *
     * <p>Pinned at the boundary, because the failure that would not throw is the tolerance quietly
     * applied the wrong way round on one side - which would widen the sweep there by a block rather
     * than trimming it.
     */
    @Test
    void theBlockSpanCountsOnlyBlocksReachingIntoTheBox() {
        ObstructionScan.BlockSpan exact = ObstructionScan.BlockSpan.inside(0.0D, 0.0D, 0.0D, 10.0D, 10.0D, 10.0D);
        assertEquals(new ObstructionScan.BlockSpan(0, 0, 0, 9, 9, 9), exact);
        // Within the tolerance of a whole block: the neighbouring block is touching, not inside.
        ObstructionScan.BlockSpan settled = ObstructionScan.BlockSpan.inside(-0.1D, -0.1D, -0.1D, 10.1D, 10.1D, 10.1D);
        assertEquals(exact, settled);
        // Past it: the neighbouring block really is inside.
        ObstructionScan.BlockSpan reaching = ObstructionScan.BlockSpan.inside(-0.2D, -0.2D, -0.2D, 10.2D, 10.2D, 10.2D);
        assertEquals(new ObstructionScan.BlockSpan(-1, -1, -1, 10, 10, 10), reaching);
    }

    /**
     * The tested segments are exactly the bare hull sweep - the amber box, and never the teal.
     *
     * <p>Pinned because the gate, the dry-run report and the overlay's red collisions all read
     * {@link LaunchClearance#testedSegments}; a padding quietly reintroduced there would bring back
     * every false refusal at once while the overlay went on drawing the amber as if nothing had changed.
     */
    @Test
    void theTestedSegmentsAreTheBareHullSweep() {
        for (Vector3d bow : List.of(BOW, DIAGONAL)) {
            List<BoundingBox3d> tested = LaunchClearance.testedSegments(hull(), bow, 120.0D);
            List<BoundingBox3d> bare = LaunchClearance.departureSegments(hull(), bow, 120.0D, 0.0D, 0.0D);
            assertEquals(bare.size(), tested.size());
            for (int i = 0; i < bare.size(); i++) {
                BoundingBox3d a = bare.get(i);
                BoundingBox3d b = tested.get(i);
                assertTrue(a.minX() == b.minX() && a.minY() == b.minY() && a.minZ() == b.minZ()
                                && a.maxX() == b.maxX() && a.maxY() == b.maxY() && a.maxZ() == b.maxZ(),
                        "tested segment " + i + " differs from the bare sweep on bow " + bow);
            }
        }
    }
}
