package uk.co.iceconchy.aerowarptics.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Sketch rift is left as: a screwed-up ball of paper, as flat faces in its own unit space.
 *
 * <p>A sphere with its corners pushed in at random, which is all a crumpled ball is - the shape reads
 * entirely from its facets catching the light at different angles, so the faces are flat-shaded and
 * none of them is allowed to be pushed in so far that it ends up facing inward. {@code PaperBallTest}
 * is what holds that last part, because a face lit from inside is simply a dark patch and nothing
 * about it throws.
 *
 * <p>Free of Minecraft, like {@link PencilModel}, and seeded so every viewer screws the same drawing
 * up the same way.
 */
public final class PaperBall {

    /** Faces round the ball, and rings of them from top to bottom. */
    private static final int SIDES = 10;
    private static final int RINGS = 6;

    /**
     * How far a corner may be pushed in, as a fraction of the radius.
     *
     * <p>Deep enough to be creased, shallow enough that every facet still faces outward.
     */
    private static final float CRUSH = 0.24F;

    /** How much darker the deepest crease is drawn than the paper it is folded from. */
    private static final float SHADE = 0.35F;

    /** One flat facet: four corners, the way it faces, and how shaded its crease is. */
    public record Face(float[] corners, float nx, float ny, float nz, float tone) {
    }

    private PaperBall() {
    }

    /** The ball a rift's own seed crumples into. Its radius never exceeds one. */
    public static List<Face> faces(int seed) {
        float[][] grid = new float[RINGS + 1][];
        for (int ring = 0; ring <= RINGS; ring++) {
            grid[ring] = new float[SIDES * 3];
            double pitch = Math.PI * ring / RINGS;
            for (int side = 0; side < SIDES; side++) {
                double turn = Math.PI * 2.0D * side / SIDES;
                // One crush per corner, not per face, so neighbouring facets share their creases.
                float crush = 1.0F - CRUSH * RiftShatter.noise(seed, 22_000 + ring * SIDES + side);
                grid[ring][side * 3] = (float) (Math.sin(pitch) * Math.cos(turn)) * crush;
                grid[ring][side * 3 + 1] = (float) Math.cos(pitch) * crush;
                grid[ring][side * 3 + 2] = (float) (Math.sin(pitch) * Math.sin(turn)) * crush;
            }
        }

        List<Face> faces = new ArrayList<>(RINGS * SIDES);
        for (int ring = 0; ring < RINGS; ring++) {
            for (int side = 0; side < SIDES; side++) {
                int next = (side + 1) % SIDES;
                float[] a = corner(grid, ring, side);
                float[] b = corner(grid, ring, next);
                float[] c = corner(grid, ring + 1, next);
                float[] d = corner(grid, ring + 1, side);
                faces.add(facet(seed, ring * SIDES + side, a, b, c, d));
            }
        }
        return List.copyOf(faces);
    }

    private static float[] corner(float[][] grid, int ring, int side) {
        return new float[]{grid[ring][side * 3], grid[ring][side * 3 + 1], grid[ring][side * 3 + 2]};
    }

    /** One facet, facing away from the middle, shaded by how far in its own crease goes. */
    private static Face facet(int seed, int index, float[] a, float[] b, float[] c, float[] d) {
        double[] across = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        double[] along = {d[0] - b[0], d[1] - b[1], d[2] - b[2]};
        double nx = across[1] * along[2] - across[2] * along[1];
        double ny = across[2] * along[0] - across[0] * along[2];
        double nz = across[0] * along[1] - across[1] * along[0];
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-9D) {
            // The two rows meet at a pole, so the facet is a sliver: face it straight out from there.
            nx = a[0];
            ny = a[1];
            nz = a[2];
            length = Math.max(1.0e-6D, Math.sqrt(nx * nx + ny * ny + nz * nz));
        }
        nx /= length;
        ny /= length;
        nz /= length;
        double midX = (a[0] + b[0] + c[0] + d[0]) * 0.25D;
        double midY = (a[1] + b[1] + c[1] + d[1]) * 0.25D;
        double midZ = (a[2] + b[2] + c[2] + d[2]) * 0.25D;
        if (nx * midX + ny * midY + nz * midZ < 0.0D) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float tone = 1.0F - SHADE * RiftShatter.noise(seed, 22_500 + index);
        return new Face(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]},
                (float) nx, (float) ny, (float) nz, tone);
    }
}
