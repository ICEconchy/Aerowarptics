package uk.co.iceconchy.aerowarptics.berth;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;

/**
 * Whether a vessel fits a slip.
 *
 * <p>Measured the way {@link FlightGeometry} measures a hull for a rift, and for the same reason: the
 * vessel's own box, turned by the attitude it will sit at, read along the slip's length, across its width
 * and up. Never the vessel's world box - a world box knows nothing of which way a ship faces, so a long
 * ship lying across the compass would be refused a slip it fits lengthways, and one lying at forty-five
 * degrees would be refused everything.
 *
 * <p>The clearance is the air the arrival is proved against on every side. It is part of the fit so that
 * the two cannot disagree: a ship that fits is a ship whose proof reads nothing outside the slip.
 */
public final class BerthFit {

    private BerthFit() {
    }

    /**
     * How a vessel sits in a slip, measured in the slip's own terms.
     *
     * @param along  its extent along the facing
     * @param across across it
     * @param up     its height
     */
    public record Sitting(double along, double across, double up) {
    }

    /**
     * @param attitude the ship-to-world rotation the vessel will sit at
     */
    public static Sitting sitting(double sizeX, double sizeY, double sizeZ, Quaterniondc attitude, BerthBox box) {
        return new Sitting(
                FlightGeometry.extentAlong(sizeX, sizeY, sizeZ, attitude, box.facing()),
                FlightGeometry.extentAlong(sizeX, sizeY, sizeZ, attitude, box.across()),
                FlightGeometry.extentAlong(sizeX, sizeY, sizeZ, attitude, new Vector3d(0.0D, 1.0D, 0.0D)));
    }

    /** Whether a vessel sitting so fits the slip with the clearance to spare on every side. */
    public static boolean fits(Sitting sitting, BerthBox box, double clearance) {
        double spare = Math.max(0.0D, clearance) * 2.0D;
        return sitting.along() + spare <= box.length() + EPSILON
                && sitting.across() + spare <= box.width() + EPSILON
                && sitting.up() + spare <= box.height() + EPSILON;
    }

    public static boolean fits(double sizeX, double sizeY, double sizeZ, Quaterniondc attitude, BerthBox box,
                               double clearance) {
        return fits(sitting(sizeX, sizeY, sizeZ, attitude, box), box, clearance);
    }

    /** A turn about the upright leaves a hair of rounding in a shadow; a slip the exact size of its ship still takes it. */
    private static final double EPSILON = 1.0e-6D;
}
