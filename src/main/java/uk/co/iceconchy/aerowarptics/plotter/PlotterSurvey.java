package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.MapColor;
import uk.co.iceconchy.aerowarptics.astrolabe.TerrainPalette;

/**
 * The ground round a plotter's target, in three dimensions: one column a block, how high it stands and what
 * colour its top is.
 *
 * <p>The Astrolabe's {@code DestinationSurvey} is the model and the palette - the same surface, the same map
 * colours, so a player reads the same kind of picture at both machines - but it is flat, a byte of relief against
 * the anchor, and fixed at four chunks. A plotter's picture is a model the player turns in their hands and aims
 * into, so heights travel whole, as the block the column's top is at, and the reach is the server's
 * {@code plotterSurveyChunks}.
 *
 * <p>Like the chart's, it is read only from chunks already loaded, on the server's thread; the plotter's
 * {@code PlotterSurveys} loads them first, through {@code Holds}, and only these.
 *
 * @param centreX  the target's column
 * @param centreZ  the target's column
 * @param radius   blocks the survey reaches out from it; the grid is {@code 2 * radius + 1} on a side
 * @param groundY  the surface at the target itself, or {@link #UNKNOWN} where it could not be read
 * @param colours  one packed {@link MapColor} a cell, row-major from the north-west, 0 where unknown
 * @param heights  the Y of each cell's surface block, {@link #UNKNOWN} where unknown
 */
public record PlotterSurvey(int centreX, int centreZ, int radius, int groundY, byte[] colours, short[] heights) {

    /** A column nobody could read. Short, because heights travel as shorts. */
    public static final short UNKNOWN = Short.MIN_VALUE;

    /** The widest survey a decoder will allocate for: eight chunks out, and half a chunk over. */
    public static final int MAX_RADIUS = 8 * 16 + 8;

    public static final StreamCodec<RegistryFriendlyByteBuf, PlotterSurvey> STREAM_CODEC =
            StreamCodec.of(PlotterSurvey::encode, PlotterSurvey::decode);

    public PlotterSurvey {
        int size = 2 * radius + 1;
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("survey radius " + radius + " is out of range");
        }
        if (colours.length != size * size || heights.length != size * size) {
            throw new IllegalArgumentException("a survey " + size + " across has " + colours.length
                    + " colours and " + heights.length + " heights");
        }
    }

    /** Blocks along one edge. */
    public int size() {
        return 2 * radius + 1;
    }

    /** Whether a cell, counted from the north-west corner, is inside the grid. */
    public boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < size() && z < size();
    }

    public boolean known(int x, int z) {
        return inside(x, z) && heights[z * size() + x] != UNKNOWN && colours[z * size() + x] != 0;
    }

    /** A cell's surface Y, or {@link #UNKNOWN}. */
    public int height(int x, int z) {
        return inside(x, z) ? heights[z * size() + x] : UNKNOWN;
    }

    /** A cell's top as 0xAARRGGBB, or 0 where unknown. */
    public int colour(int x, int z) {
        return inside(x, z) ? TerrainPalette.argb(colours[z * size() + x]) : 0;
    }

    /** The world column a cell stands for. */
    public int worldX(int x) {
        return centreX - radius + x;
    }

    public int worldZ(int z) {
        return centreZ - radius + z;
    }

    /** How much of the grid came back with ground in it, 0..1. */
    public float coverage() {
        int known = 0;
        for (short height : heights) {
            if (height != UNKNOWN) {
                known++;
            }
        }
        return heights.length == 0 ? 0.0F : known / (float) heights.length;
    }

    /** The lowest and highest known surface, {lowest, highest}; the target's ground twice over for an empty grid. */
    public int[] span() {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (short height : heights) {
            if (height != UNKNOWN) {
                low = Math.min(low, height);
                high = Math.max(high, height);
            }
        }
        if (low > high) {
            int ground = groundY == UNKNOWN ? 64 : groundY;
            return new int[]{ground, ground};
        }
        return new int[]{low, high};
    }

    // ------------------------------------------------------------------ reading the ground

    /**
     * Reads the ground round a target. Reads, never loads: a column in a chunk that is not there comes back
     * unknown, which the panel draws as the edge of what is known.
     */
    public static PlotterSurvey of(ServerLevel level, int centreX, int centreZ, int radius) {
        int reach = Math.max(0, Math.min(MAX_RADIUS, radius));
        int size = 2 * reach + 1;
        byte[] colours = new byte[size * size];
        short[] heights = new short[size * size];
        java.util.Arrays.fill(heights, UNKNOWN);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean loaded = false;
        for (int row = 0; row < size; row++) {
            int worldZ = centreZ - reach + row;
            for (int column = 0; column < size; column++) {
                int worldX = centreX - reach + column;
                if (worldX >> 4 != lastChunkX || worldZ >> 4 != lastChunkZ) {
                    lastChunkX = worldX >> 4;
                    lastChunkZ = worldZ >> 4;
                    loaded = level.hasChunk(lastChunkX, lastChunkZ);
                }
                if (!loaded) {
                    continue;
                }
                int top = TerrainPalette.surfaceOf(level, cursor, worldX, worldZ);
                if (top == TerrainPalette.UNKNOWN_SURFACE) {
                    continue;
                }
                MapColor colour = TerrainPalette.colourAt(level, cursor, worldX, top, worldZ);
                MapColor.Brightness brightness = colour == MapColor.WATER
                        ? TerrainPalette.depth(TerrainPalette.waterDepth(level, worldX, worldZ), column, row)
                        : MapColor.Brightness.HIGH;
                int index = row * size + column;
                colours[index] = colour.getPackedId(brightness);
                heights[index] = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, top));
            }
        }
        short middle = heights[reach * size + reach];
        return new PlotterSurvey(centreX, centreZ, reach, middle == UNKNOWN ? UNKNOWN : middle, colours, heights);
    }

    /** Whether every chunk a survey of this reach would read is loaded. Reads, never loads. */
    public static boolean ready(ServerLevel level, int centreX, int centreZ, int radius) {
        for (int x = (centreX - radius) >> 4; x <= (centreX + radius) >> 4; x++) {
            for (int z = (centreZ - radius) >> 4; z <= (centreZ + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ wire

    private static void encode(RegistryFriendlyByteBuf buf, PlotterSurvey survey) {
        buf.writeInt(survey.centreX);
        buf.writeInt(survey.centreZ);
        buf.writeVarInt(survey.radius);
        buf.writeInt(survey.groundY);
        buf.writeBytes(survey.colours);
        for (short height : survey.heights) {
            buf.writeShort(height);
        }
    }

    private static PlotterSurvey decode(RegistryFriendlyByteBuf buf) {
        int centreX = buf.readInt();
        int centreZ = buf.readInt();
        int radius = buf.readVarInt();
        int groundY = buf.readInt();
        // A broken or hostile sender must not be able to make the client allocate an arbitrary array.
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("plotter survey of radius " + radius + " is out of range");
        }
        int size = 2 * radius + 1;
        byte[] colours = new byte[size * size];
        buf.readBytes(colours);
        short[] heights = new short[size * size];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = buf.readShort();
        }
        return new PlotterSurvey(centreX, centreZ, radius, groundY, colours, heights);
    }
}
