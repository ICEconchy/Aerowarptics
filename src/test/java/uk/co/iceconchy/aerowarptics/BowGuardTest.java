package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the departure gate cannot become a size limit on ships.
 *
 * <p>Proving the whole corridor clear was a gate whose cost grew with the hull, and past a certain
 * size it refused every attempt - over open water, with nothing anywhere near the ship. A check that
 * says no to every large vessel protects none of them.
 *
 * <p>What gates a launch now is the bow guard: the bare hull swept forward to the aperture, no
 * padding, no corridor. The property that matters is that its cost is the hull's own footprint plus
 * a fixed lead, so it is the same test for a flying city as for a skiff. These assert that shape
 * directly, since it is the thing that regressed twice.
 */
class BowGuardTest {

    private static final Vector3d EAST = new Vector3d(1.0D, 0.0D, 0.0D);
    private static final Vector3d DIAGONAL =
            new Vector3d(1.0D, 0.0D, 1.0D).normalize(new Vector3d());

    /** The lead distance the guard covers, matching {@code riftLeadDistance}'s default. */
    private static final double LEAD = 24.0D;

    private static BoundingBox3d hull(double size) {
        return new BoundingBox3d(0.0D, 0.0D, 0.0D, size, size * 0.5D, size);
    }

    private static List<BoundingBox3d> guard(BoundingBox3dc hull, Vector3d bow) {
        return LaunchClearance.departureSegments(hull, bow, LEAD, 0.0D, 0.0D);
    }

    private static double volume(List<BoundingBox3d> boxes) {
        double total = 0.0D;
        for (BoundingBox3d box : boxes) {
            total += box.width() * box.height() * box.length();
        }
        return total;
    }

    /** The hull's own volume, for comparison against what the guard reads. */
    private static double hullVolume(BoundingBox3dc hull) {
        return hull.width() * hull.height() * hull.length();
    }

    /**
     * The guard reads a bounded multiple of the ship's own footprint, at any size.
     *
     * <p>The corridor proof failed exactly here: its volume grew faster than the hull, so the ratio
     * climbed without limit and eventually exhausted every budget. The guard's excess over the hull
     * is a fixed lead, so the ratio falls as ships get bigger rather than rising.
     */
    @Test
    void theGuardStaysProportionalToTheHullAtEverySize() {
        double previousRatio = Double.MAX_VALUE;
        for (double size : new double[]{20.0D, 50.0D, 100.0D, 200.0D, 400.0D}) {
            BoundingBox3d hull = hull(size);
            double ratio = volume(guard(hull, EAST)) / hullVolume(hull);
            assertTrue(ratio < 4.0D,
                    "a hull of " + size + " reads " + ratio + "x its own volume at the bow");
            assertTrue(ratio <= previousRatio,
                    "the guard got relatively more expensive as the ship grew: " + ratio);
            previousRatio = ratio;
        }
    }

    /** Doubling the ship does not more than double what the guard reads. */
    @Test
    void theGuardGrowsNoFasterThanTheHull() {
        double small = volume(guard(hull(100.0D), EAST));
        double large = volume(guard(hull(200.0D), EAST));
        double hullRatio = hullVolume(hull(200.0D)) / hullVolume(hull(100.0D));
        assertTrue(large / small <= hullRatio + 1.0e-9D,
                "guard grew " + (large / small) + "x for a " + hullRatio + "x hull");
    }

    /** The guard reaches exactly the lead distance ahead, and no further. */
    @Test
    void theGuardReachesTheApertureAndNoFurther() {
        BoundingBox3d hull = hull(40.0D);
        double furthest = Double.NEGATIVE_INFINITY;
        for (BoundingBox3d segment : guard(hull, EAST)) {
            furthest = Math.max(furthest, segment.maxX());
        }
        assertEquals(hull.maxX() + LEAD, furthest, 1.0e-9D);
    }

    /**
     * The guard is a small fraction of the corridor proof it replaced as the gate.
     *
     * <p>With the shipped tiers the corridor reaches the hull's length again on top of the lead, so
     * on a large ship the guard is the cheaper test by a wide margin - and, unlike the corridor, it
     * does not grow at all when the drive tier holds the corridor open for longer.
     */
    @Test
    void theGuardIsFarCheaperThanTheCorridorItReplaced() {
        BoundingBox3d hull = hull(200.0D);
        double guard = volume(guard(hull, DIAGONAL));
        // corridorReach = hull + margin, launchReach adds the lead on top.
        double corridor = volume(
                LaunchClearance.departureSegments(hull, DIAGONAL, 200.0D + 4.0D + LEAD, 2.0D, 19.0D));
        assertTrue(guard < corridor,
                "guard " + guard + " should be cheaper than the corridor " + corridor);
    }

    /** It is bounded on every bearing, not just the convenient ones. */
    @Test
    void theGuardIsBoundedOnEveryBearing() {
        BoundingBox3d hull = hull(150.0D);
        for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += Math.PI / 16.0D) {
            Vector3d bow = new Vector3d(Math.cos(angle), 0.0D, Math.sin(angle)).normalize();
            double ratio = volume(guard(hull, bow)) / hullVolume(hull);
            assertTrue(ratio < 4.0D, "bearing " + bow + " reads " + ratio + "x the hull");
        }
    }
}
