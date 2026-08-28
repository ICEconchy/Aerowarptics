package uk.co.iceconchy.aerowarptics;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.warp.ScatterOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scatter-offset formula for unstable exits.
 *
 * Two quite different things are tested here. The probability gate is a coin flip -- instability
 * is a chance, not a magnitude -- and the offset geometry is a bounded vector. Both must behave
 * correctly for the Singularity's unstable exit to feel deliberate rather than random.
 */
class ScatterOffsetTest {

    private static final double EPSILON = 1.0e-9D;

    // --------------------------------------------------------- probability gate

    @Test
    void zeroInstabilityNeverScatters() {
        for (double roll = 0.0D; roll < 1.0D; roll += 0.01D) {
            assertFalse(ScatterOffset.isScattered(0.0D, roll),
                    "zero instability must never scatter, even at roll=0");
        }
        assertFalse(ScatterOffset.isScattered(0.0D, 0.0D));
        assertFalse(ScatterOffset.isScattered(0.0D, 0.99D));
    }

    @Test
    void fullInstabilityAlwaysScatters() {
        for (double roll = 0.0D; roll < 1.0D; roll += 0.01D) {
            assertTrue(ScatterOffset.isScattered(1.0D, roll),
                    "instability=1 must scatter for every roll below 1");
        }
    }

    @Test
    void instabilityIsAProbability() {
        int trials = 10_000;
        int scattered = 0;
        for (int i = 0; i < trials; i++) {
            double roll = i / (double) trials;
            if (ScatterOffset.isScattered(0.5D, roll)) {
                scattered++;
            }
        }
        double fraction = scattered / (double) trials;
        assertTrue(fraction > 0.47D && fraction < 0.53D,
                "instability=0.5 should scatter ~50%, got " + (fraction * 100) + "%");
    }

    @Test
    void aRollAtTheBoundaryIsNotScattered() {
        assertFalse(ScatterOffset.isScattered(0.5D, 0.5D));
        assertFalse(ScatterOffset.isScattered(0.15D, 0.15D));
    }

    @Test
    void aSlightlyUnderRollIsScattered() {
        assertTrue(ScatterOffset.isScattered(0.5D, 0.49D));
        assertTrue(ScatterOffset.isScattered(0.15D, 0.14D));
    }

    // ----------------------------------------------------------- scatter radius

    @Test
    void scatterRadiusIsZeroAtZeroDistance() {
        assertEquals(0.0D, ScatterOffset.scatterRadius(0.0D), EPSILON);
    }

    @Test
    void scatterRadiusScalesLinearlyWithDistance() {
        double half = ScatterOffset.scatterRadius(ScatterOffset.FULL_SCATTER_DISTANCE * 0.5D);
        double full = ScatterOffset.scatterRadius(ScatterOffset.FULL_SCATTER_DISTANCE);
        assertEquals(full * 0.5D, half, EPSILON,
                "radius should be exactly half at half the reference distance");
    }

    @Test
    void scatterRadiusReachesMaxAtFullScatterDistance() {
        assertEquals(ScatterOffset.MAX_SCATTER,
                ScatterOffset.scatterRadius(ScatterOffset.FULL_SCATTER_DISTANCE), EPSILON);
    }

    @Test
    void scatterRadiusCapsBeyondFullScatterDistance() {
        assertEquals(ScatterOffset.MAX_SCATTER,
                ScatterOffset.scatterRadius(ScatterOffset.FULL_SCATTER_DISTANCE * 2.0D), EPSILON);
        assertEquals(ScatterOffset.MAX_SCATTER,
                ScatterOffset.scatterRadius(2_000_000.0D), EPSILON,
                "a two-million-block jump must not exceed the cap");
    }

    @Test
    void scatterRadiusIsMonotonicallyIncreasing() {
        double previous = 0.0D;
        for (double distance = 100.0D; distance <= 1_000_000.0D; distance += 100.0D) {
            double current = ScatterOffset.scatterRadius(distance);
            assertTrue(current >= previous,
                    "radius must not decrease as distance grows at " + distance);
            previous = current;
        }
    }

    @Test
    void scatterRadiusIsNeverNegative() {
        assertEquals(0.0D, ScatterOffset.scatterRadius(-1000.0D), EPSILON,
                "a negative distance must not produce a negative radius");
    }

    // ----------------------------------------------------------------- offset

    @Test
    void offsetIsAlwaysHorizontal() {
        for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += 0.3D) {
            for (double magnitude = 0.0D; magnitude <= 1.0D; magnitude += 0.2D) {
                Vector3d v = ScatterOffset.offset(100_000.0D, angle, magnitude);
                assertEquals(0.0D, v.y, EPSILON, "scatter must never displace vertically");
            }
        }
    }

    @Test
    void offsetMagnitudeRespectsScatterRadius() {
        double distance = 250_000.0D;
        double maxRadius = ScatterOffset.scatterRadius(distance);
        for (double magnitude = 0.0D; magnitude <= 1.5D; magnitude += 0.1D) {
            for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += 0.5D) {
                Vector3d v = ScatterOffset.offset(distance, angle, magnitude);
                double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
                double effectiveMagnitude = Math.max(0.0D, Math.min(1.0D, magnitude));
                double expectedMax = maxRadius * effectiveMagnitude;
                assertTrue(horizontal <= expectedMax + EPSILON,
                        "offset magnitude " + horizontal + " exceeds radius " + expectedMax);
            }
        }
    }

    @Test
    void offsetAtKnownAnglePointsTheRightWay() {
        Vector3d east = ScatterOffset.offset(500_000.0D, 0.0D, 1.0D);
        assertTrue(east.x > 0.0D, "angle 0 should point east");
        assertEquals(0.0D, east.z, 1.0e-6D, "angle 0 should have no Z component");

        Vector3d south = ScatterOffset.offset(500_000.0D, Math.PI / 2.0D, 1.0D);
        assertTrue(south.z > 0.0D, "angle pi/2 should point south");
        assertEquals(0.0D, south.x, 1.0e-6D, "angle pi/2 should have no X component");
    }

    @Test
    void offsetAtFullMagnitudeReachesTheRadius() {
        double distance = 100_000.0D;
        double expectedRadius = ScatterOffset.scatterRadius(distance);
        Vector3d v = ScatterOffset.offset(distance, 0.0D, 1.0D);
        assertEquals(expectedRadius, v.x, EPSILON,
                "magnitude 1.0 should place the offset exactly on the radius");
    }

    @Test
    void offsetAtZeroMagnitudeIsZero() {
        Vector3d v = ScatterOffset.offset(500_000.0D, 1.23D, 0.0D);
        assertEquals(0.0D, v.x, EPSILON);
        assertEquals(0.0D, v.z, EPSILON);
        assertEquals(0.0D, v.y, EPSILON);
    }

    @Test
    void magnitudeIsClampedToUnitInterval() {
        Vector3d atOne = ScatterOffset.offset(500_000.0D, 0.0D, 1.0D);
        Vector3d atTwo = ScatterOffset.offset(500_000.0D, 0.0D, 2.0D);
        assertEquals(atOne.x, atTwo.x, EPSILON, "magnitude 2.0 must be clamped to 1.0");

        Vector3d atNeg = ScatterOffset.offset(500_000.0D, 0.0D, -0.5D);
        assertEquals(0.0D, atNeg.x, EPSILON);
        assertEquals(0.0D, atNeg.z, EPSILON);
    }

    // ---------------------------------------------------------- integration

    @Test
    void singularityAtTypicalRangeScattersUpTo256Blocks() {
        double radius = ScatterOffset.scatterRadius(500_000.0D);
        assertEquals(ScatterOffset.MAX_SCATTER, radius, EPSILON);

        double shortRadius = ScatterOffset.scatterRadius(100_000.0D);
        assertEquals(ScatterOffset.MAX_SCATTER * 0.2D, shortRadius, EPSILON);
    }

    @Test
    void creativeDriveDefaultsToZeroInstability() {
        assertFalse(ScatterOffset.isScattered(RiftDriveTier.CREATIVE.defaults().instability(), 0.0D));
        assertFalse(ScatterOffset.isScattered(RiftDriveTier.CREATIVE.defaults().instability(), 0.99D));
    }

    @Test
    void mkIDefaultsToZeroInstability() {
        assertFalse(ScatterOffset.isScattered(RiftDriveTier.MK_I.defaults().instability(), 0.0D));
    }
}
