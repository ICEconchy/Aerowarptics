package uk.co.iceconchy.aerowarptics.detector;

/**
 * How far an arrival is from a Spatial Wave Detector, and what that is worth as a signal.
 *
 * <p>Kept free of Minecraft so the grading can be tested directly. An arrival is measured as a box
 * rather than a point, because a ship is not a point: a hundred-block hull whose bow comes out of the
 * rift beside a detector has arrived beside it, whatever the distance to its middle.
 */
public final class WaveReach {

    /** The strongest signal redstone carries. */
    public static final int FULL = 15;

    private WaveReach() {
    }

    /**
     * Distance from a point to the nearest part of a box, zero if the point is inside it.
     *
     * <p>Per axis, the gap is how far the point sits outside the box's span on that axis, or nothing
     * when it is within it - the three gaps are then an ordinary vector.
     */
    public static double distanceToBox(double x, double y, double z,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        double dx = Math.max(0.0D, Math.max(minX - x, x - maxX));
        double dy = Math.max(0.0D, Math.max(minY - y, y - maxY));
        double dz = Math.max(0.0D, Math.max(minZ - z, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The signal an arrival at {@code distance} is worth to a detector listening out to {@code range}.
     *
     * <p>Graded the way a sculk sensor grades a vibration: full strength on top of the detector, one
     * at the very edge of its range, and nothing beyond it. That makes a single detector a rangefinder
     * as well as an alarm - a comparator chain can tell a ship landing in the yard from one arriving
     * over the hill - without the edge ever reading as silence.
     *
     * @return 1..15 within range, 0 outside it or for a range that is not positive
     */
    public static int strength(double distance, int range) {
        if (range <= 0 || !(distance >= 0.0D) || distance > range) {
            return 0;
        }
        int strength = FULL - (int) Math.floor((FULL - 1) * distance / range);
        return Math.max(1, Math.min(FULL, strength));
    }
}
