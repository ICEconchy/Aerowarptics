package uk.co.iceconchy.aerowarptics.client.fx;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Vector3d;

/**
 * Where a hull stops the storm's rain: how far down a column of it gets before it meets a ship.
 *
 * <p>The level's heightmap is the ground's, and a ship is not in it - its blocks stand in a plot a long way
 * off and are only drawn where the ship is. Sable teaches vanilla's rain about that by wrapping the heightmap
 * reads inside {@code LevelRenderer.renderSnowAndRain} and {@code tickRain}, and only those. The storm's rain
 * is this mod's own drawing and asks the level itself, so while vanilla's rain stopped on a deck the storm's
 * fell straight through it, into the cabins and out of the keel. This asks Sable's question for ours: start
 * at the top of the hull's box over the column, come down through the plot a block at a time, and stop at the
 * first block that would stop rain.
 *
 * <p>It differs from Sable's answer in one way. Vanilla's rain can only end on a whole block, so Sable's
 * answer is one - and a ship rides at any height, so vanilla's rain ends anything up to a block over a deck
 * or under it, and its splashes land there. The storm's rain can end anywhere, so once the walk has found
 * the block it closes in on that block's surface.
 *
 * <p>No Minecraft in here, so it can be tested: the plot is whatever answers {@link Plot}.
 */
public final class RainCover {

    /** No ship over this column. Below any height there is, so it loses every {@code Math.max}. */
    public static final double NONE = Double.NEGATIVE_INFINITY;

    /** Halvings of the walk's last block: the surface is found to a thirty-second of a block. */
    private static final int REFINEMENTS = 5;

    private RainCover() {
    }

    /** A ship's blocks, asked in the plot's own coordinates: does the block here stop rain? */
    @FunctionalInterface
    public interface Plot {
        boolean stops(int x, int y, int z);
    }

    /**
     * The lowest height rain falling down the middle of a column reaches before a hull stops it, or
     * {@link #NONE} if this hull is not under it.
     *
     * @param pose   where the hull is - as drawn, for rain that is drawn
     * @param bounds the hull's box in the world, which is all of the column worth walking
     * @param x      the column, in the world
     * @param z      the column, in the world
     */
    public static double height(Pose3dc pose, BoundingBox3dc bounds, int x, int z, Plot plot) {
        if (x + 1 <= bounds.minX() || x >= bounds.maxX() || z + 1 <= bounds.minZ() || z >= bounds.maxZ()) {
            // Outside the box nothing here is this hull's - and far enough outside it, the plot the pose
            // maps to is somebody else's.
            return NONE;
        }
        double depth = bounds.maxY() - bounds.minY();
        // The column's centre line through the box, in the plot. Both ends go through the pose as places
        // rather than the line as a direction, so a hull at any attitude is walked the way the rain falls.
        Vector3d top = pose.transformPositionInverse(new Vector3d(x + 0.5D, bounds.maxY(), z + 0.5D));
        Vector3d bottom = pose.transformPositionInverse(new Vector3d(x + 0.5D, bounds.minY(), z + 0.5D));

        // Half a block down to begin with. A level hull's box ends on the top face of its top block, so a
        // walk in whole blocks from there reads the face between two blocks every time, and which of them
        // that is belongs to the rounding: a deck one block thick is found or missed by it.
        for (double fallen = 0.5D; fallen < depth; fallen += 1.0D) {
            if (!stops(top, bottom, fallen / depth, plot)) {
                continue;
            }
            double open = Math.max(fallen - 1.0D, 0.0D);
            double blocked = fallen;
            for (int halving = 0; halving < REFINEMENTS; halving++) {
                double middle = (open + blocked) * 0.5D;
                if (stops(top, bottom, middle / depth, plot)) {
                    blocked = middle;
                } else {
                    open = middle;
                }
            }
            // The open side of the surface, so a splash put here is on the deck and not just inside it.
            return bounds.maxY() - open;
        }
        return NONE;
    }

    private static boolean stops(Vector3d top, Vector3d bottom, double share, Plot plot) {
        return plot.stops(
                floor(top.x + (bottom.x - top.x) * share),
                floor(top.y + (bottom.y - top.y) * share),
                floor(top.z + (bottom.z - top.z) * share));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
