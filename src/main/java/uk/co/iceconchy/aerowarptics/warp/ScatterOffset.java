package uk.co.iceconchy.aerowarptics.warp;

import org.joml.Vector3d;

/**
 * How far a scattered exit throws the hull from its intended target.
 *
 * <p>Deliberately free of Minecraft, like {@link WarpCost} and {@link FissureDrain}: it takes plain
 * numbers and returns a vector, so every boundary condition and scaling rule can be exercised in
 * tests without a server.
 *
 * <p>The Singularity's {@code instability} config value is the probability that any particular warp
 * scatters its exit. On a hit, the offset is a random horizontal distance within a radius that
 * scales with the warp distance — a short hop scatters very little, a long jump up to the cap.
 * The arrival search still proves the scattered spot clear, so a hull can never materialise inside
 * terrain.
 */
public final class ScatterOffset {

    /**
     * Maximum horizontal offset when an exit is scattered, in blocks.
     *
     * <p>A Singularity at its maximum range lands up to this far from the anchor. Enough to be
     * visible on a map and potentially in different terrain, but still recognisably near the
     * intended destination.
     */
    public static final double MAX_SCATTER = 256.0D;

    /**
     * Warp distance at which scatter reaches its maximum, in blocks.
     *
     * <p>Below this the offset scales linearly with distance; above it the cap applies. Chosen so
     * a typical Singularity jump — roughly five hundred thousand blocks — sits near the top of the
     * scale without always hitting the cap.
     */
    public static final double FULL_SCATTER_DISTANCE = 500_000.0D;

    private ScatterOffset() {
    }

    /**
     * Whether this warp's exit is scattered.
     *
     * @param instability the tier's instability value, in {@code [0, 1]}
     * @param roll        uniform random in {@code [0, 1)}
     * @return {@code true} when the roll triggers scatter
     */
    public static boolean isScattered(double instability, double roll) {
        return instability > 0.0D && roll < instability;
    }

    /**
     * The radius within which the scattered offset falls.
     *
     * <p>Scales linearly from zero at zero distance up to {@link #MAX_SCATTER} at
     * {@link #FULL_SCATTER_DISTANCE}, and stays there beyond it. A short hop scatters a few blocks;
     * a cross-world jump scatters up to the cap.
     *
     * @param distance warp distance in blocks, non-negative
     */
    public static double scatterRadius(double distance) {
        double factor = Math.min(1.0D, Math.max(0.0D, distance) / FULL_SCATTER_DISTANCE);
        return MAX_SCATTER * factor;
    }

    /**
     * A horizontal offset vector for a scattered exit.
     *
     * <p>The Y component is always zero — scatter displaces the hull laterally, not vertically. The
     * magnitude is {@code radius × magnitude} where {@code radius} comes from
     * {@link #scatterRadius(double)} and {@code magnitude} is a random fraction in {@code [0, 1]}.
     *
     * @param distance warp distance in blocks
     * @param angle    random angle in radians, in {@code [0, 2π)}
     * @param magnitude random fraction in {@code [0, 1)} controlling how far within the radius
     * @return the offset vector, horizontal only
     */
    public static Vector3d offset(double distance, double angle, double magnitude) {
        double radius = scatterRadius(distance);
        double r = radius * Math.max(0.0D, Math.min(1.0D, magnitude));
        double x = r * Math.cos(angle);
        double z = r * Math.sin(angle);
        return new Vector3d(x, 0.0D, z);
    }
}
