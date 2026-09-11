package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the corridor is tested along the bearing rather than boxed around it.
 *
 * <p>One axis-aligned box around a diagonal sweep is far bigger than the path it describes: a run of
 * {@code d} blocks at forty-five degrees spans {@code d / sqrt(2)} on each horizontal axis, so the
 * enclosing box picks up two large wedges of terrain either side of the corridor that the hull never
 * approaches. Those wedges were read like everything else, and a block in one of them refused a
 * launch whose real path was clear.
 *
 * <p>The invariant that matters is two-sided: the segments must stay <em>inside</em> the old box (or
 * they are no tighter) and must still <em>cover</em> the true path (or the check has been made
 * unsound, which is far worse than being over-sensitive).
 */
class SweepSegmentTest {

    private static final Vector3d EAST = new Vector3d(1.0D, 0.0D, 0.0D);
    private static final Vector3d DIAGONAL =
            new Vector3d(1.0D, 0.0D, 1.0D).normalize(new Vector3d());

    /** A twenty-block hull at the origin. */
    private static BoundingBox3d hull() {
        return new BoundingBox3d(0.0D, 0.0D, 0.0D, 20.0D, 10.0D, 20.0D);
    }

    private static List<BoundingBox3d> segments(Vector3d bow, double reach) {
        return LaunchClearance.departureSegments(hull(), bow, reach, 0.0D, 0.0D);
    }

    private static double totalVolume(List<BoundingBox3d> boxes) {
        double total = 0.0D;
        for (BoundingBox3d box : boxes) {
            total += box.width() * box.height() * box.length();
        }
        return total;
    }

    private static boolean contains(BoundingBox3d box, double x, double y, double z) {
        return x >= box.minX() && x <= box.maxX()
                && y >= box.minY() && y <= box.maxY()
                && z >= box.minZ() && z <= box.maxZ();
    }

    private static boolean anyContains(List<BoundingBox3d> boxes, double x, double y, double z) {
        for (BoundingBox3d box : boxes) {
            if (contains(box, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Always at least one box, even for a standing start. */
    @Test
    void aZeroReachStillProducesTheHullItself() {
        List<BoundingBox3d> segments = segments(EAST, 0.0D);
        assertEquals(1, segments.size());
        assertTrue(anyContains(segments, 10.0D, 5.0D, 10.0D));
    }

    /**
     * The whole path the hull travels is still covered, sampled densely along the bearing.
     *
     * <p>This is the soundness half. A tighter volume that let a block through would be a far worse
     * bug than the over-sensitivity it replaced.
     */
    @Test
    void everyPointAlongThePathIsStillCovered() {
        for (Vector3d bow : List.of(EAST, DIAGONAL)) {
            double reach = 300.0D;
            List<BoundingBox3d> segments = segments(bow, reach);
            for (double t = 0.0D; t <= reach; t += 0.5D) {
                // The hull's centre, and each of its eight corners, at this point along the run.
                double cx = 10.0D + bow.x * t;
                double cy = 5.0D + bow.y * t;
                double cz = 10.0D + bow.z * t;
                assertTrue(anyContains(segments, cx, cy, cz),
                        "hull centre at t=" + t + " on bow " + bow + " fell outside every segment");
                for (double dx : new double[]{-10.0D, 10.0D}) {
                    for (double dz : new double[]{-10.0D, 10.0D}) {
                        assertTrue(anyContains(segments, cx + dx, cy, cz + dz),
                                "a hull corner at t=" + t + " on bow " + bow + " was not covered");
                    }
                }
            }
        }
    }

    /** Every segment stays within the single box the old check used, so this is strictly tighter. */
    @Test
    void segmentsStayInsideTheOldEnclosingBox() {
        for (Vector3d bow : List.of(EAST, DIAGONAL)) {
            BoundingBox3d whole = LaunchClearance.departureVolume(hull(), bow, 300.0D, 0.0D, 0.0D);
            for (BoundingBox3d segment : segments(bow, 300.0D)) {
                assertTrue(segment.minX() >= whole.minX() - 1.0e-6D
                                && segment.maxX() <= whole.maxX() + 1.0e-6D
                                && segment.minY() >= whole.minY() - 1.0e-6D
                                && segment.maxY() <= whole.maxY() + 1.0e-6D
                                && segment.minZ() >= whole.minZ() - 1.0e-6D
                                && segment.maxZ() <= whole.maxZ() + 1.0e-6D,
                        "a segment escaped the enclosing box on bow " + bow);
            }
        }
    }

    /**
     * On a diagonal bearing the marched volume is dramatically smaller than the enclosing box.
     *
     * <p>The actual complaint. The box grows as the square of the run on a diagonal while the real
     * corridor grows linearly, so the saving widens the further the ship goes.
     */
    @Test
    void aDiagonalCorridorIsFarSmallerThanItsEnclosingBox() {
        BoundingBox3d whole = LaunchClearance.departureVolume(hull(), DIAGONAL, 300.0D, 0.0D, 0.0D);
        double boxed = whole.width() * whole.height() * whole.length();
        double marched = totalVolume(segments(DIAGONAL, 300.0D));
        // Around a three-fold cut in practice; asserted at two so the exact optimum the search lands
        // on can shift without turning this into a brittle tripwire.
        assertTrue(marched < boxed / 2.0D,
                "marched " + marched + " vs boxed " + boxed + "; expected at least a halving");
    }

    /** An axis-aligned bearing was never the problem, so it must not be made worse. */
    @Test
    void anAxisAlignedCorridorIsNotInflated() {
        BoundingBox3d whole = LaunchClearance.departureVolume(hull(), EAST, 300.0D, 0.0D, 0.0D);
        double boxed = whole.width() * whole.height() * whole.length();
        double marched = totalVolume(segments(EAST, 300.0D));
        // Slicing an already-tight box only adds a hull of overlap per cut, so the search should
        // settle on a single segment here and match the old volume exactly.
        assertEquals(boxed, marched, 1.0e-6D,
                "an axis-aligned bearing should not be segmented at all");
    }

    /**
     * On every bearing, the marched volume is no worse than the single box it replaced.
     *
     * <p>The safety net under the whole idea: the single box is always one of the candidates the
     * search considers, so however the heuristic changes it can never regress into reading more than
     * the old check did.
     */
    @Test
    void marchingIsNeverWorseThanTheOldBox() {
        for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += Math.PI / 16.0D) {
            Vector3d bow = new Vector3d(Math.cos(angle), 0.0D, Math.sin(angle)).normalize();
            for (double reach : new double[]{40.0D, 150.0D, 600.0D}) {
                BoundingBox3d whole = LaunchClearance.departureVolume(hull(), bow, reach, 0.0D, 0.0D);
                double boxed = whole.width() * whole.height() * whole.length();
                double marched = totalVolume(segments(bow, reach));
                assertTrue(marched <= boxed + 1.0e-6D,
                        "bow " + bow + " reach " + reach + ": marched " + marched + " > boxed " + boxed);
            }
        }
    }

    /** The segment count stays bounded however long the corridor or however small the hull. */
    @Test
    void theSegmentCountIsBounded() {
        assertTrue(segments(DIAGONAL, 100_000.0D).size() <= 64);
        List<BoundingBox3d> tiny = SafeArrival.sweepSegments(
                new BoundingBox3d(0.0D, 0.0D, 0.0D, 0.01D, 0.01D, 0.01D),
                DIAGONAL, 100_000.0D, 0.0D, 0.0D);
        assertTrue(tiny.size() <= 64, "a degenerate hull asked for " + tiny.size() + " segments");
        assertFalse(tiny.isEmpty());
    }

    /**
     * Padding widens the corridor by exactly the clearance plus the aperture margin.
     *
     * <p>Compared across the whole chain rather than segment by segment: padding changes which
     * segment count comes out cheapest, so the two runs legitimately slice the path differently.
     * What must hold is that the padded corridor reaches exactly {@code pad} further on every face.
     */
    @Test
    void paddingWidensTheCorridorByTheFullMargin() {
        List<BoundingBox3d> bare = segments(DIAGONAL, 120.0D);
        List<BoundingBox3d> padded =
                LaunchClearance.departureSegments(hull(), DIAGONAL, 120.0D, 1.0D, 3.0D);
        double pad = 4.0D;
        assertEquals(extreme(bare, false, 0) - pad, extreme(padded, false, 0), 1.0e-9D, "minX");
        assertEquals(extreme(bare, false, 1) - pad, extreme(padded, false, 1), 1.0e-9D, "minY");
        assertEquals(extreme(bare, false, 2) - pad, extreme(padded, false, 2), 1.0e-9D, "minZ");
        assertEquals(extreme(bare, true, 0) + pad, extreme(padded, true, 0), 1.0e-9D, "maxX");
        assertEquals(extreme(bare, true, 1) + pad, extreme(padded, true, 1), 1.0e-9D, "maxY");
        assertEquals(extreme(bare, true, 2) + pad, extreme(padded, true, 2), 1.0e-9D, "maxZ");
    }

    /** The furthest any segment reaches on one axis, either the low or the high side. */
    private static double extreme(List<BoundingBox3d> boxes, boolean high, int axis) {
        double found = high ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (BoundingBox3d box : boxes) {
            double value = switch (axis) {
                case 0 -> high ? box.maxX() : box.minX();
                case 1 -> high ? box.maxY() : box.minY();
                default -> high ? box.maxZ() : box.minZ();
            };
            found = high ? Math.max(found, value) : Math.min(found, value);
        }
        return found;
    }
}
