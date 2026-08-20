package uk.co.iceconchy.aerowarptics.astrolabe;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * How a column of the world becomes a pixel.
 *
 * <p>Both of the Astrolabe's maps - the relief the table projects and the top-down preview of a
 * destination - draw the same ground the same way, so the rules live here once rather than twice. They
 * are vanilla's rules: the same {@link MapColor} table a filled map uses, and the same north-facing
 * slope shading, because a chart that looks like a map a player has already made is a chart they can
 * read without being taught anything.
 *
 * <p>Nothing here is client-only. The destination survey runs on the server and the relief runs on the
 * client, and they have to agree.
 */
public final class TerrainPalette {

    private TerrainPalette() {
    }

    /** How far down from the top of a column to look for something with a colour. */
    private static final int SCAN_DEPTH = 24;

    /** A column of the world, reduced to the two facts a map needs. */
    public static final int UNKNOWN_SURFACE = Integer.MIN_VALUE;

    /**
     * Reads the top of a column: the height of its surface and the map colour of whatever is there.
     *
     * <p>The heightmap alone is not enough. It stops at the first thing that is not air, which on a
     * jungle floor is a leaf and on a lake is the water, and a surprising amount of the world's top
     * layer has no map colour at all - glass, torches, tall grass. So the scan walks down from the
     * heightmap until it finds something that does, which is what vanilla's own map does.
     *
     * @return the surface height, or {@link #UNKNOWN_SURFACE} when the column has no colour at all
     */
    public static int surfaceOf(LevelReader level, BlockPos.MutableBlockPos cursor, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int floor = Math.max(level.getMinBuildHeight(), top - SCAN_DEPTH);
        for (int y = top; y >= floor; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.getMapColor(level, cursor) != MapColor.NONE) {
                return y;
            }
        }
        return UNKNOWN_SURFACE;
    }

    /** The map colour at a surface found by {@link #surfaceOf}. */
    public static MapColor colourAt(LevelReader level, BlockPos.MutableBlockPos cursor, int x, int surface, int z) {
        cursor.set(x, surface, z);
        return level.getBlockState(cursor).getMapColor(level, cursor);
    }

    /**
     * How deep the water is at a column, or {@code 0} where there is none.
     *
     * <p>Open water is the one surface with no slope to shade, so without this an ocean is a single
     * flat blue rectangle and a coastline has no shape. Depth stands in for relief: shallows come out
     * bright, a trench comes out dark, and the shore reads as a shore.
     */
    public static int waterDepth(LevelReader level, int x, int z) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        return Math.max(0, surface - floor);
    }

    /**
     * Vanilla's slope shading: a cell is lit by how much it rises above the one to its north.
     *
     * <p>This single rule is most of what makes a map legible. Without it terrain is a wash of flat
     * colours with no shape to it; with it, ridges, valleys and cliffs all read at a glance, and they
     * read the same way here as they do on a map in a player's inventory.
     *
     * <p>The dither term is vanilla's too. Alternating the threshold on a checkerboard keeps large
     * flats from banding into hard steps, at the cost of a faint texture that ends up looking like
     * paper.
     *
     * @param here  surface height of this cell
     * @param north surface height of the cell one step north, or {@link #UNKNOWN_SURFACE}
     * @param step  blocks between samples, which sets how much a given rise is worth
     */
    public static MapColor.Brightness slope(int here, int north, int step, int x, int z) {
        if (north == UNKNOWN_SURFACE) {
            return MapColor.Brightness.NORMAL;
        }
        double rise = (here - north) * 4.0D / (step + 4.0D) + ((x + z & 1) - 0.5D) * 0.4D;
        if (rise > 0.6D) {
            return MapColor.Brightness.HIGH;
        }
        return rise < -0.6D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    /** Vanilla's water shading: shallow is bright, deep is dark. */
    public static MapColor.Brightness depth(int waterDepth, int x, int z) {
        double shade = waterDepth * 0.1D + (x + z & 1) * 0.2D;
        if (shade < 0.5D) {
            return MapColor.Brightness.HIGH;
        }
        return shade > 0.9D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    /**
     * A map colour as 0xAARRGGBB.
     *
     * <p>Deliberately not {@link MapColor#calculateRGBColor}. That method returns the byte order
     * vanilla's map <em>texture</em> wants, which is ABGR - red and blue the other way round from
     * every other colour in the game. Handing its result to something expecting ARGB turns grass blue
     * and water orange, which is exactly as confusing as it sounds. The channels are scaled here
     * instead, from the colour's own plain RGB.
     */
    public static int argb(MapColor colour, MapColor.Brightness brightness) {
        if (colour == MapColor.NONE) {
            return 0;
        }
        int modifier = brightness.modifier;
        int r = ((colour.col >> 16) & 0xFF) * modifier / 255;
        int g = ((colour.col >> 8) & 0xFF) * modifier / 255;
        int b = (colour.col & 0xFF) * modifier / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The same, from the single byte a survey travels as. */
    public static int argb(byte packed) {
        int id = packed & 0xFF;
        MapColor colour = MapColor.byId(id >> 2);
        return colour == MapColor.NONE ? 0 : argb(colour, MapColor.Brightness.byId(id & 3));
    }

    /** Scales every channel of an 0xAARRGGBB colour, keeping its alpha. */
    public static int scale(int argb, float factor) {
        int r = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
