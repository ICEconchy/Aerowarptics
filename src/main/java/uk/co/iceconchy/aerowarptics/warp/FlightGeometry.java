package uk.co.iceconchy.aerowarptics.warp;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * How big a hull is for the purposes of flying it through a rift: its length along the bearing, its
 * width across it, and every distance a flight derives from those two.
 *
 * <p>The hull used to be measured off its <em>world</em> bounding box, and a world box knows nothing
 * about which way the ship is facing. The aperture's radius came from the box's X extent and its
 * height, so the same four-hundred-block ship got a forty-block rift flying north and a
 * two-hundred-and-seventy-block one flying east - and since every clearance margin and chunk claim is
 * sized off that radius, whether a large vessel could warp at all depended on the compass. A hull
 * sitting at forty-five degrees was worse again: its world box is a square as wide as it is long.
 *
 * <p>So the hull is measured the way it actually sits. Its plot-space block box is turned by the
 * ship's orientation, and the extent of that turned box is read along the bow, across it, and up.
 * Those are the same whatever the heading, which is the whole point.
 *
 * <p>Kept free of Minecraft and Sable, like {@link ObstructionScan} and {@link ArrivalSearch}, so the
 * geometry can be swept across sizes and headings in a test without a world. {@link WarpFlight} is the
 * bridge to a live airship and the live config, and both its planned flight and its departure shape
 * are built from one of these - which is what keeps them in lockstep, rather than a comment asking
 * two copies of the arithmetic to stay the same.
 *
 * @param hullSpan       the hull's extent along the bearing, which sets when the bow reaches a plane
 * @param crossSpan      its horizontal extent across the bearing
 * @param height         its vertical extent
 * @param hullRadius     the radius the apertures are scaled from: half the diagonal of the cross-section
 *                       the aperture has to pass, never less than four
 * @param riftRadius     the aperture's radius, {@code hullRadius x riftRadiusFactor}
 * @param transitRun     how far the hull travels for an aperture to swallow it whole, bow to stern
 * @param transitTicks   how long that passage takes
 * @param emergeDistance how far back from its resting place the hull reappears at the far end
 */
public record FlightGeometry(double hullSpan, double crossSpan, double height, double hullRadius,
                             double riftRadius, double transitRun, int transitTicks,
                             double emergeDistance) {

    /** Blocks of overshoot on a passage, so the stern ends up inside the aperture rather than in it. */
    public static final double TRANSIT_MARGIN = 4.0D;

    /** The smallest hull radius an aperture is ever scaled from, so a single block still gets a rift. */
    public static final double MINIMUM_HULL_RADIUS = 4.0D;

    /**
     * Measures a hull and works out the distances a flight through a rift needs.
     *
     * @param sizeX                the hull's plot-space extent along its own X axis, in blocks
     * @param sizeY                along its own Y axis
     * @param sizeZ                along its own Z axis
     * @param orientation          the ship-to-world rotation
     * @param bow                  world-space bearing the hull will fly along; normalised horizontally here
     * @param riftRadiusFactor     {@code flight.riftRadiusFactor}
     * @param transitTicks         {@code flight.riftTransitTicks}; floored at five as the flight does
     * @param emergeTicks          the drive tier's arrive ticks
     * @param emergeDistanceFactor {@code flight.emergeDistanceFactor}
     */
    public static FlightGeometry of(double sizeX, double sizeY, double sizeZ, Quaterniondc orientation,
                                    Vector3dc bow, double riftRadiusFactor, int transitTicks,
                                    int emergeTicks, double emergeDistanceFactor) {
        Vector3d along = horizontal(bow);
        // A quarter turn of the bow about the vertical: the horizontal direction the aperture's width
        // has to span.
        Vector3d across = new Vector3d(-along.z, 0.0D, along.x);
        Vector3d up = new Vector3d(0.0D, 1.0D, 0.0D);

        double hullSpan = extentAlong(sizeX, sizeY, sizeZ, orientation, along);
        double crossSpan = extentAlong(sizeX, sizeY, sizeZ, orientation, across);
        double height = extentAlong(sizeX, sizeY, sizeZ, orientation, up);

        double hullRadius = Math.max(MINIMUM_HULL_RADIUS, Math.hypot(crossSpan, height) * 0.5D);
        double riftRadius = hullRadius * riftRadiusFactor;

        double transitRun = hullSpan + TRANSIT_MARGIN;
        int ticks = transitTicks(transitTicks);

        return new FlightGeometry(hullSpan, crossSpan, height, hullRadius, riftRadius, transitRun,
                ticks, emergeDistance(hullSpan, transitRun, ticks, emergeTicks, emergeDistanceFactor));
    }

    /** The passage's length in ticks, floored as the flight floors it. */
    public static int transitTicks(int configured) {
        return Math.max(5, configured);
    }

    /**
     * How far back from its resting place a hull of this length reappears.
     *
     * <p>The run out has to fly the hull clear of the aperture and then bleed that same speed away
     * to nothing. Sizing it from the passage speed is what lets the ship leave the throat and
     * come to rest without ever changing pace: a shorter run would need a sudden brake, a longer
     * one a sudden shove. The configured multiple is a floor under that, not the whole story.
     *
     * <p>Public because a flotilla going through one rift is sized by the same rule with the whole
     * formation's length in place of one hull's - see {@link FleetPassage}.
     *
     * @param transitTicks already floored - see {@link #transitTicks}
     */
    public static double emergeDistance(double hullSpan, double transitRun, int transitTicks, int emergeTicks,
                                        double emergeDistanceFactor) {
        double passage = transitRun / Math.max(1, transitTicks);
        double settle = passage * Math.max(1, emergeTicks) * 0.5D;
        return Math.max(8.0D, Math.max(hullSpan * emergeDistanceFactor, transitRun + settle));
    }

    /**
     * This hull flown on a formation's run rather than its own: the same hull and the same aperture, but
     * the passage, its timing and the run out of the whole fleet it is going through one rift with.
     */
    public FlightGeometry flownWith(double transitRun, int transitTicks, double emergeDistance) {
        return new FlightGeometry(hullSpan, crossSpan, height, hullRadius, riftRadius, transitRun, transitTicks,
                emergeDistance);
    }

    /**
     * How far a box, turned by {@code orientation}, reaches along a unit direction.
     *
     * <p>Each of the box's own axes contributes its length times how much of that axis points along
     * the direction, and the contributions add. For an unturned box read along a world axis that is
     * just the matching side; for a turned one it is the exact shadow the box casts on the line.
     *
     * @param direction a unit vector; the result is meaningless for anything else
     */
    public static double extentAlong(double sizeX, double sizeY, double sizeZ, Quaterniondc orientation,
                                     Vector3dc direction) {
        Vector3d axisX = orientation.transform(new Vector3d(1.0D, 0.0D, 0.0D));
        Vector3d axisY = orientation.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        Vector3d axisZ = orientation.transform(new Vector3d(0.0D, 0.0D, 1.0D));
        return Math.abs(sizeX) * Math.abs(axisX.dot(direction))
                + Math.abs(sizeY) * Math.abs(axisY.dot(direction))
                + Math.abs(sizeZ) * Math.abs(axisZ.dot(direction));
    }

    /** How far past the hull's own footprint the apertures reach on every side. Never negative. */
    public double apertureMargin() {
        return Math.max(0.0D, riftRadius - hullRadius);
    }

    /** Blocks per tick the hull travels from the moment it reaches an aperture until it is clear. */
    public double passageSpeed() {
        return transitRun / transitTicks;
    }

    /**
     * The bearing flattened onto the horizontal and made unit length.
     *
     * <p>{@link WarpFlight#worldBow} already hands over a horizontal unit vector; this only guards the
     * degenerate case, where the bow points straight up, with an arbitrary but bounded north.
     */
    static Vector3d horizontal(Vector3dc bow) {
        Vector3d flat = new Vector3d(bow.x(), 0.0D, bow.z());
        if (flat.lengthSquared() < 1.0e-12D) {
            return new Vector3d(0.0D, 0.0D, -1.0D);
        }
        return flat.normalize();
    }
}
