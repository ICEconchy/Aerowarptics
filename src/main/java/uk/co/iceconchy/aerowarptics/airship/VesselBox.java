package uk.co.iceconchy.aerowarptics.airship;

import org.joml.Vector3d;

import java.util.function.UnaryOperator;

/**
 * Growing one box to take in another that sits in a different frame.
 *
 * <p>The arithmetic behind {@link Airship#vesselShipBounds()}, kept free of Sable so it can be tested
 * without a ship. A part of a vessel has its own plot and its own pose, so its box cannot simply be
 * unioned with the hull's: each of its eight corners is carried into the hull's frame first, and the hull's
 * box is grown round wherever they land. For a part turned against the hull that is a looser fit than the
 * part itself, and never a tighter one - which is the right way round for something a clearance is proved
 * from.
 */
public final class VesselBox {

    private VesselBox() {
    }

    /**
     * Grows {@code box} to hold another box, each of whose corners is first put through {@code intoFrame}.
     *
     * @param box       {@code minX, minY, minZ, maxX, maxY, maxZ}, grown in place
     * @param intoFrame carries a point from the other box's frame into {@code box}'s; a corner it cannot
     *                  place - a result that is not finite - is left out rather than allowed to make the
     *                  box the size of the world
     */
    public static void include(double[] box, double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ, UnaryOperator<Vector3d> intoFrame) {
        for (int corner = 0; corner < 8; corner++) {
            Vector3d placed = intoFrame.apply(new Vector3d(
                    (corner & 1) == 0 ? minX : maxX,
                    (corner & 2) == 0 ? minY : maxY,
                    (corner & 4) == 0 ? minZ : maxZ));
            if (placed == null || !placed.isFinite()) {
                continue;
            }
            box[0] = Math.min(box[0], placed.x);
            box[1] = Math.min(box[1], placed.y);
            box[2] = Math.min(box[2], placed.z);
            box[3] = Math.max(box[3], placed.x);
            box[4] = Math.max(box[4], placed.y);
            box[5] = Math.max(box[5], placed.z);
        }
    }
}
