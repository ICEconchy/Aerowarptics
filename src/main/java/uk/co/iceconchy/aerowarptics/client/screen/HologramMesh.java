package uk.co.iceconchy.aerowarptics.client.screen;

/**
 * The ground round a plotter's target as a solid of columns: a top for every known block, and a wall wherever a
 * column stands over its neighbour or over the edge of what is known.
 *
 * <p>Built once per survey, as flat arrays, and handed to a vertex buffer by the table and the projection; nothing
 * here knows about a buffer or a game, so it can be counted in a test - every known column has exactly one top, a
 * wall is never drawn between two columns of one height, and the solid is closed round its edge.
 *
 * <p>Coordinates are blocks from the target: the target's own column is the square from 0 to 1 on x and z, and
 * height is measured from the top of the target's ground, so the point a plot is aimed at is (0.5, 0, 0.5). Faces
 * are shaded as the game shades a block - tops full, north and south a little darker, east and west darker again
 * - so the relief reads without any lighting.
 */
public final class HologramMesh {

    /** A column nobody could read. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /** How the game shades a block's faces, which this copies so the model reads as blocks. */
    public static final float TOP = 1.0F;
    public static final float NORTH_SOUTH = 0.8F;
    public static final float EAST_WEST = 0.62F;

    /** Blocks the model's skirt hangs below the lowest known ground, so the edge of it reads as a cut. */
    public static final int SKIRT = 3;

    /** Four corners a quad, three numbers a corner. */
    public static final int FLOATS_PER_QUAD = 12;

    private final float[] positions;
    private final int[] colours;
    private final int quads;
    private final int tops;
    private final float floor;

    private HologramMesh(float[] positions, int[] colours, int quads, int tops, float floor) {
        this.positions = positions;
        this.colours = colours;
        this.quads = quads;
        this.tops = tops;
        this.floor = floor;
    }

    /** Every quad's four corners, x y z each, wound anticlockwise seen from outside the solid. */
    public float[] positions() {
        return positions;
    }

    /** Every quad's colour, 0xAARRGGBB, already shaded for the way it faces. */
    public int[] colours() {
        return colours;
    }

    public int quads() {
        return quads;
    }

    /** How many of the quads are tops - one a known column. */
    public int tops() {
        return tops;
    }

    /** The bottom of the skirt, in the mesh's own heights. */
    public float floor() {
        return floor;
    }

    /**
     * Builds the solid.
     *
     * @param size    cells along one edge; the target's cell is the middle one
     * @param heights each cell's surface Y, row-major from the north-west, {@link #UNKNOWN} where unknown
     * @param colours each cell's top colour, 0xAARRGGBB
     * @param groundY the target's surface Y, which becomes height 0
     */
    public static HologramMesh build(int size, int[] heights, int[] colours, int groundY) {
        int radius = size / 2;
        int lowest = Integer.MAX_VALUE;
        for (int height : heights) {
            if (height != UNKNOWN) {
                lowest = Math.min(lowest, height);
            }
        }
        float floor = lowest == Integer.MAX_VALUE ? -SKIRT : lowest - groundY - SKIRT;
        Builder out = new Builder(Math.max(16, heights.length * 3));
        int tops = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int height = heights[z * size + x];
                if (height == UNKNOWN) {
                    continue;
                }
                float x0 = x - radius;
                float z0 = z - radius;
                float top = height - groundY;
                int colour = colours[z * size + x];
                out.quad(x0, top, z0 + 1, x0 + 1, top, z0 + 1, x0 + 1, top, z0, x0, top, z0, shade(colour, TOP));
                tops++;
                // North (-z), south (+z), west (-x), east (+x): a wall down to whichever is higher of the
                // neighbour's top and the skirt, and only where this column stands over it.
                float north = wallBottom(heights, size, x, z - 1, groundY, floor);
                if (north < top) {
                    out.quad(x0 + 1, north, z0, x0 + 1, top, z0, x0, top, z0, x0, north, z0, shade(colour, NORTH_SOUTH));
                }
                float south = wallBottom(heights, size, x, z + 1, groundY, floor);
                if (south < top) {
                    out.quad(x0, south, z0 + 1, x0, top, z0 + 1, x0 + 1, top, z0 + 1, x0 + 1, south, z0 + 1,
                            shade(colour, NORTH_SOUTH));
                }
                float west = wallBottom(heights, size, x - 1, z, groundY, floor);
                if (west < top) {
                    out.quad(x0, west, z0, x0, top, z0, x0, top, z0 + 1, x0, west, z0 + 1, shade(colour, EAST_WEST));
                }
                float east = wallBottom(heights, size, x + 1, z, groundY, floor);
                if (east < top) {
                    out.quad(x0 + 1, east, z0 + 1, x0 + 1, top, z0 + 1, x0 + 1, top, z0, x0 + 1, east, z0,
                            shade(colour, EAST_WEST));
                }
            }
        }
        return new HologramMesh(out.positions(), out.colours(), out.quads, tops, floor);
    }

    /** Where a wall facing this neighbour stops: at its top, or at the skirt where it is unknown or off the edge. */
    private static float wallBottom(int[] heights, int size, int x, int z, int groundY, float floor) {
        if (x < 0 || z < 0 || x >= size || z >= size) {
            return floor;
        }
        int height = heights[z * size + x];
        return height == UNKNOWN ? floor : height - groundY;
    }

    /** A colour darkened for a face, alpha kept. */
    public static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** A growing pair of arrays, so the mesh is built without a list of boxed floats. */
    private static final class Builder {

        private float[] positions;
        private int[] colours;
        private int quads;

        Builder(int expected) {
            positions = new float[expected * FLOATS_PER_QUAD];
            colours = new int[expected];
        }

        void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                  float dx, float dy, float dz, int colour) {
            if (quads == colours.length) {
                positions = java.util.Arrays.copyOf(positions, positions.length * 2);
                colours = java.util.Arrays.copyOf(colours, colours.length * 2);
            }
            int at = quads * FLOATS_PER_QUAD;
            positions[at] = ax;
            positions[at + 1] = ay;
            positions[at + 2] = az;
            positions[at + 3] = bx;
            positions[at + 4] = by;
            positions[at + 5] = bz;
            positions[at + 6] = cx;
            positions[at + 7] = cy;
            positions[at + 8] = cz;
            positions[at + 9] = dx;
            positions[at + 10] = dy;
            positions[at + 11] = dz;
            colours[quads] = colour;
            quads++;
        }

        float[] positions() {
            return java.util.Arrays.copyOf(positions, quads * FLOATS_PER_QUAD);
        }

        int[] colours() {
            return java.util.Arrays.copyOf(colours, quads);
        }
    }
}
