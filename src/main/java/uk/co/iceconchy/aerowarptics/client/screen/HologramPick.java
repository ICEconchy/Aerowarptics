package uk.co.iceconchy.aerowarptics.client.screen;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntBinaryOperator;

/**
 * Which block of the model the pointer is over: a ray from the camera through the pointer, walked across the
 * columns until it goes into one.
 *
 * <p>Walked cell by cell, the way a ray is walked through a voxel grid - every column the ray passes over is visited
 * once, in order, and the first one whose top it has come down through, or whose wall it has run into, is the one it
 * hit. Stepping along the ray a fixed distance at a time missed thin ridges seen edge-on and picked the block behind a
 * wall, which on a model a player is aiming a weapon into is the block they did not mean.
 *
 * <p>In the mesh's own coordinates - see {@link HologramMesh}: cell {@code (x, z)} is the square from
 * {@code x - radius} to {@code x - radius + 1}, and heights are from the target's ground. Pure, for the tests.
 */
public final class HologramPick {

    /** A hit: the cell, and the point on it the ray met, in the mesh's coordinates. */
    public record Hit(int x, int z, double px, double py, double pz) {
    }

    private HologramPick() {
    }

    /**
     * @param origin    the ray's start
     * @param direction the ray's direction; need not be a unit vector
     * @param size      cells along an edge
     * @param top       a cell's top in the mesh's heights, or {@link HologramMesh#UNKNOWN} where it is not known
     * @return the first cell the ray goes into, or {@code null} for a ray that misses the model
     */
    @Nullable
    public static Hit pick(double[] origin, double[] direction, int size, IntBinaryOperator top) {
        int radius = size / 2;
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
        if (length < 1.0e-12D) {
            return null;
        }
        double dx = direction[0] / length;
        double dy = direction[1] / length;
        double dz = direction[2] / length;
        // Into grid space, where cell (x, z) is [x, x + 1): the mesh's coordinates shifted by the radius.
        double ox = origin[0] + radius;
        double oy = origin[1];
        double oz = origin[2] + radius;

        // Clip the ray to the grid's square first, so a camera outside the model starts walking at its edge.
        double[] span = clip(ox, oz, dx, dz, size);
        if (span == null) {
            return null;
        }
        double t = Math.max(0.0D, span[0]) + 1.0e-9D;
        double end = span[1];
        int cx = (int) Math.floor(ox + dx * t);
        int cz = (int) Math.floor(oz + dz * t);
        int stepX = dx > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double nextX = dx == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? cx + 1 : cx) - ox) / dx;
        double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? cz + 1 : cz) - oz) / dz;
        double deltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double deltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double enter = t;
        for (int guard = 0; guard < size * 4 + 8 && enter <= end; guard++) {
            if (cx < 0 || cz < 0 || cx >= size || cz >= size) {
                return null;
            }
            double leave = Math.min(Math.min(nextX, nextZ), end);
            int height = top.applyAsInt(cx, cz);
            if (height != HologramMesh.UNKNOWN) {
                double yEnter = oy + dy * enter;
                double yLeave = oy + dy * leave;
                if (yEnter <= height) {
                    // Ran into the column's wall as it came over the edge of the cell.
                    return new Hit(cx, cz, ox + dx * enter - radius, yEnter, oz + dz * enter - radius);
                }
                if (yLeave <= height && dy < 0) {
                    // Came down through its top inside the cell.
                    double tTop = (height - oy) / dy;
                    return new Hit(cx, cz, ox + dx * tTop - radius, height, oz + dz * tTop - radius);
                }
            }
            if (nextX < nextZ) {
                enter = nextX;
                nextX += deltaX;
                cx += stepX;
            } else {
                enter = nextZ;
                nextZ += deltaZ;
                cz += stepZ;
            }
        }
        return null;
    }

    /** Where a ray enters and leaves the grid's square, seen from above, or {@code null} if it never crosses it. */
    @Nullable
    private static double[] clip(double ox, double oz, double dx, double dz, int size) {
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        double[][] axes = {{ox, dx}, {oz, dz}};
        for (double[] axis : axes) {
            if (Math.abs(axis[1]) < 1.0e-12D) {
                if (axis[0] < 0.0D || axis[0] >= size) {
                    return null;
                }
                continue;
            }
            double a = (0.0D - axis[0]) / axis[1];
            double b = (size - axis[0]) / axis[1];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
        }
        if (far < Math.max(near, 0.0D)) {
            return null;
        }
        return new double[]{near, far};
    }
}
