package uk.co.iceconchy.aerowarptics.client.fx;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Where the starlines are: the geometry of a cruise's hyperspace streaks, with nothing about drawing.
 *
 * <p>The look is the jump to lightspeed - stars pulled out into long white lines rushing past - with one
 * change the original never needed. Those streaks converge on the point dead ahead, which is exactly the
 * point a cruising pilot has to see. So the field is a tube of lines around the ship with a
 * <em>window</em> down its middle: every line is faded out before any part of it comes within
 * {@link #CLEAR_DEGREES} of the heading, and the view forward stays open however fast the ship goes.
 * That promise is the one thing about this effect that is load-bearing rather than taste, so it is kept
 * here, free of Minecraft, where {@code HyperspaceFieldTest} can hold every line to it.
 *
 * <p>Positions are relative to the camera. The lines do not travel with the world; they travel past the
 * viewer, at a rate the caller scrolls by, so a ship turning under the camera swings the whole tube
 * round with it the way a pilot's view would.
 */
public final class HyperspaceField {

    private HyperspaceField() {
    }

    /** How many lines make up the field. */
    public static final int STREAKS = 220;

    /** Closest a line runs to the axis of travel, in blocks. Inside a small hull's deck, mostly. */
    public static final double INNER = 5.0D;

    /** Furthest a line runs from the axis. */
    public static final double OUTER = 44.0D;

    /** How far ahead a line appears, and how far behind it is gone, in blocks. */
    public static final double AHEAD = 90.0D;
    public static final double BEHIND = 30.0D;

    /** Half-angle of the window kept clear down the heading, in degrees. */
    public static final double CLEAR_DEGREES = 16.0D;

    /** Degrees past the window over which a line fades in, so the window's edge is soft rather than cut. */
    public static final double FADE_DEGREES = 12.0D;

    /** Longest a line gets, in blocks, at full speed and full stretch. */
    public static final double LONGEST = 34.0D;

    /**
     * One line, from its leading end to its trailing end.
     *
     * @param head  the end nearest the direction the lines travel in - behind the viewer, eventually
     * @param tail  the end still further ahead
     * @param alpha how visible it is, 0 to 1
     */
    public record Streak(Vector3d head, Vector3d tail, float alpha) {
    }

    /**
     * Where line {@code index} is.
     *
     * @param forward the heading, unit length
     * @param scroll  how far the field has travelled, in blocks - the caller's running total of speed
     * @param speed   0 to 1, how fast the ship is going as a fraction of its top speed
     * @param stretch 0 to 1, how drawn out the lines are: rising as the jump engages, falling as it drops
     */
    public static Streak streak(int index, Vector3dc forward, double scroll, double speed, double stretch) {
        Vector3d f = new Vector3d(forward);
        if (f.lengthSquared() < 1.0e-9D) {
            f.set(0.0D, 0.0D, 1.0D);
        }
        f.normalize();
        Vector3d right = new Vector3d(f).cross(0.0D, 1.0D, 0.0D);
        if (right.lengthSquared() < 1.0e-9D) {
            right.set(1.0D, 0.0D, 0.0D);
        }
        right.normalize();
        Vector3d up = new Vector3d(right).cross(f).normalize();

        double angle = noise(index, 1) * Math.PI * 2.0D;
        // Square root spreads the lines evenly over the tube's cross-section rather than bunching them
        // at the middle, which is also where the window is.
        double radius = INNER + (OUTER - INNER) * Math.sqrt(noise(index, 2));
        double pace = 0.7D + 0.6D * noise(index, 3);
        double span = AHEAD + BEHIND;
        double travelled = (noise(index, 4) * span + scroll * pace) % span;
        double along = AHEAD - travelled;
        double length = LONGEST * clamp01(stretch) * clamp01(speed) * (0.45D + 0.55D * noise(index, 5));

        double ox = right.x * Math.cos(angle) * radius + up.x * Math.sin(angle) * radius;
        double oy = right.y * Math.cos(angle) * radius + up.y * Math.sin(angle) * radius;
        double oz = right.z * Math.cos(angle) * radius + up.z * Math.sin(angle) * radius;
        Vector3d head = new Vector3d(f).mul(along).add(ox, oy, oz);
        Vector3d tail = new Vector3d(f).mul(along + length).add(ox, oy, oz);

        // In from nothing at the far end, out to nothing behind, so none of them pops.
        double ends = Math.min(smooth(0.0D, 18.0D, AHEAD - along), smooth(0.0D, 10.0D, along + BEHIND));
        double brightness = 0.55D + 0.45D * noise(index, 6);
        double alpha = brightness * ends * window(radius, along + length) * clamp01(stretch) * clamp01(speed * 1.6D);
        return new Streak(head, tail, (float) Math.max(0.0D, Math.min(1.0D, alpha)));
    }

    /**
     * How much of a line survives the window, from the point on it nearest the heading.
     *
     * <p>A line runs parallel to the heading at a fixed distance from it, so its angle from the heading
     * shrinks the further ahead a point on it lies - the nearest approach is always its far end. Fading
     * by that end fades the whole line.
     *
     * @param radius how far the line runs from the axis
     * @param along  how far ahead its far end is
     */
    public static double window(double radius, double along) {
        double degrees = Math.toDegrees(Math.atan2(radius, along));
        return smooth(CLEAR_DEGREES, CLEAR_DEGREES + FADE_DEGREES, degrees);
    }

    /** Degrees between a camera-relative point and the heading. */
    public static double degreesFrom(Vector3dc forward, Vector3dc point) {
        double length = point.length() * forward.length();
        if (length < 1.0e-9D) {
            return 180.0D;
        }
        double cos = Math.max(-1.0D, Math.min(1.0D, point.dot(forward) / length));
        return Math.toDegrees(Math.acos(cos));
    }

    private static double smooth(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    /** A stable 0..1 number per line and property, so the field is the same shape frame to frame. */
    static double noise(int index, int salt) {
        long h = index * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }
}
