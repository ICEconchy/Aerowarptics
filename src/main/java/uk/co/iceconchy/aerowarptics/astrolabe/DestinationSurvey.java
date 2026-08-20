package uk.co.iceconchy.aerowarptics.astrolabe;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;

/**
 * A top-down look at somewhere the ship is not.
 *
 * <p>The chart's preview of a destination has to come from the server: the client has never had those
 * chunks and cannot invent them. So the ground is sampled here into a grid, and the screen does nothing
 * but draw the pixels it is handed.
 *
 * <p>One sample per block, shaded the way vanilla shades a filled map - see {@link TerrainPalette}.
 * That resolution is the point of the thing: a chart sampled every three blocks is a mosaic of
 * unrelated colours, and a coastline needs to look like a coastline before anybody can read a valley
 * off it.
 *
 * <p>Colours travel one byte to a cell, packed exactly as vanilla packs a map: six bits of palette
 * index and two of brightness. A whole survey is then about the size of a filled map's contents, which
 * is what makes a grid this fine affordable to send at all. Height travels alongside as a second byte,
 * measured against the anchor, and is what the contour lines on the chart are drawn from.
 *
 * <p>Only already-loaded chunks are read. A destination in the middle of unexplored terrain comes back
 * mostly blank, which is honest - nobody has been there - and, more importantly, means opening a chart
 * can never drag a few hundred chunks off disk on the server's tick thread.
 *
 * @param radius  blocks from the centre the survey reaches, in each direction
 * @param step    blocks between samples; the grid is {@code (2 * radius / step) + 1} on a side
 * @param centre  the anchor the survey is centred on
 * @param groundY surface height at the anchor itself, or {@link #NO_GROUND} where it is unknown
 * @param colours one packed {@link MapColor} per cell, row-major from north-west, {@code 0} where
 *                unknown
 * @param relief  height of each cell above the anchor's ground, clamped to a signed byte
 */
public record DestinationSurvey(int radius, int step, BlockPos centre, int groundY,
                                byte[] colours, byte[] relief) {

    /** How far out the preview looks. Four chunks in every direction. */
    public static final int RADIUS = 64;

    /** Sampling interval. One block: this is a map, not a mosaic. */
    public static final int STEP = 1;

    /** Blocks between contour lines on the chart. */
    public static final int CONTOUR_INTERVAL = 8;

    /** The anchor's own column could not be read. */
    public static final int NO_GROUND = Integer.MIN_VALUE;

    /** Largest grid any survey may claim to be, so a decoder can bound its allocation. */
    private static final int MAX_SIZE = 2 * RADIUS / STEP + 1;

    public static final StreamCodec<RegistryFriendlyByteBuf, DestinationSurvey> STREAM_CODEC =
            StreamCodec.of(DestinationSurvey::encode, DestinationSurvey::decode);

    public DestinationSurvey {
        // The two grids are read by the same index and travel under one length on the wire, so a
        // survey where they disagree would decode as garbage rather than fail.
        if (colours.length != relief.length) {
            throw new IllegalArgumentException("survey has " + colours.length + " colours but "
                    + relief.length + " heights");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("survey step must be positive");
        }
    }

    /** Number of samples along one edge of the grid. */
    public int size() {
        return 2 * radius / step + 1;
    }

    /** The colour of one cell as 0xAARRGGBB, or fully transparent where the ground is unknown. */
    public int colour(int x, int z) {
        int index = index(x, z);
        return index < 0 ? 0 : TerrainPalette.argb(colours[index]);
    }

    /** How far above the anchor's ground a cell stands, in blocks. */
    public int relief(int x, int z) {
        int index = index(x, z);
        return index < 0 ? 0 : relief[index];
    }

    public boolean known(int x, int z) {
        int index = index(x, z);
        return index >= 0 && colours[index] != 0;
    }

    private int index(int x, int z) {
        int size = size();
        if (x < 0 || z < 0 || x >= size || z >= size) {
            return -1;
        }
        int index = z * size + x;
        return index < colours.length ? index : -1;
    }

    /** How much of the survey came back with ground in it, 0..1. Blank means unexplored, not flat. */
    public float coverage() {
        if (colours.length == 0) {
            return 0.0F;
        }
        int known = 0;
        for (byte colour : colours) {
            if (colour != 0) {
                known++;
            }
        }
        return known / (float) colours.length;
    }

    // ----------------------------------------------------------------- survey

    /**
     * Samples the ground around an anchor.
     *
     * <p>Two passes, because slope shading needs a cell's northern neighbour and that neighbour may
     * live in a different chunk. The first pass reads heights and colours; the second turns them into
     * pixels. Doing it the other way round would mean either re-reading columns or shading the seam
     * between chunks differently from everywhere else.
     */
    public static DestinationSurvey of(ServerLevel level, BlockPos centre) {
        int size = MAX_SIZE;
        int count = size * size;
        int[] surface = new int[count];
        byte[] palette = new byte[count];
        int[] depth = new int[count];
        byte[] colours = new byte[count];
        byte[] relief = new byte[count];

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int originX = centre.getX() - RADIUS;
        int originZ = centre.getZ() - RADIUS;

        // Chunk presence is checked once per chunk rather than once per sample. At one sample a block
        // that is the difference between 256 checks and 16,641 of them.
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean chunkLoaded = false;

        for (int row = 0; row < size; row++) {
            int worldZ = originZ + row * STEP;
            for (int column = 0; column < size; column++) {
                int worldX = originX + column * STEP;
                int chunkX = worldX >> 4;
                int chunkZ = worldZ >> 4;
                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                    chunkLoaded = level.hasChunk(chunkX, chunkZ);
                }
                int index = row * size + column;
                if (!chunkLoaded) {
                    surface[index] = TerrainPalette.UNKNOWN_SURFACE;
                    continue;
                }
                int top = TerrainPalette.surfaceOf(level, cursor, worldX, worldZ);
                surface[index] = top;
                if (top == TerrainPalette.UNKNOWN_SURFACE) {
                    continue;
                }
                MapColor colour = TerrainPalette.colourAt(level, cursor, worldX, top, worldZ);
                palette[index] = (byte) colour.id;
                depth[index] = colour == MapColor.WATER ? TerrainPalette.waterDepth(level, worldX, worldZ) : 0;
            }
        }

        int anchorIndex = (size / 2) * size + size / 2;
        int groundY = surface[anchorIndex] == TerrainPalette.UNKNOWN_SURFACE
                ? NO_GROUND : surface[anchorIndex];
        int reliefBase = groundY == NO_GROUND ? centre.getY() : groundY;

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int index = row * size + column;
                if (surface[index] == TerrainPalette.UNKNOWN_SURFACE) {
                    continue;
                }
                MapColor colour = MapColor.byId(palette[index] & 0xFF);
                int north = row > 0 ? surface[index - size] : TerrainPalette.UNKNOWN_SURFACE;
                MapColor.Brightness brightness = colour == MapColor.WATER
                        ? TerrainPalette.depth(depth[index], column, row)
                        : TerrainPalette.slope(surface[index], north, STEP, column, row);
                colours[index] = colour.getPackedId(brightness);
                relief[index] = (byte) Mth.clamp(surface[index] - reliefBase, -127, 127);
            }
        }
        return new DestinationSurvey(RADIUS, STEP, centre.immutable(), groundY, colours, relief);
    }

    // -------------------------------------------------------------------- wire

    private static void encode(RegistryFriendlyByteBuf buf, DestinationSurvey survey) {
        buf.writeVarInt(survey.radius);
        buf.writeVarInt(survey.step);
        buf.writeBlockPos(survey.centre);
        buf.writeInt(survey.groundY);
        buf.writeVarInt(survey.colours.length);
        buf.writeBytes(survey.colours);
        buf.writeBytes(survey.relief);
    }

    private static DestinationSurvey decode(RegistryFriendlyByteBuf buf) {
        int radius = buf.readVarInt();
        int step = buf.readVarInt();
        BlockPos centre = buf.readBlockPos();
        int groundY = buf.readInt();
        int length = buf.readVarInt();
        // A hostile or broken sender must not be able to make the client allocate an arbitrary array.
        if (length < 0 || length > MAX_SIZE * MAX_SIZE) {
            throw new IllegalArgumentException("destination survey of " + length + " cells is out of range");
        }
        byte[] colours = new byte[length];
        byte[] relief = new byte[length];
        buf.readBytes(colours);
        buf.readBytes(relief);
        return new DestinationSurvey(radius, step, centre, groundY, colours, relief);
    }
}
