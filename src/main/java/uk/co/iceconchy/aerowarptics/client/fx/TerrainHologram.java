package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure;
import uk.co.iceconchy.aerowarptics.astrolabe.TerrainPalette;

/**
 * The relief map an Astrolabe Cartography Table projects above itself.
 *
 * <p>Entirely client-side. The ground being shown is the ground the player is standing over, which
 * means their own client already has every chunk of it - so there is nothing to ask the server for and
 * nothing to send. (The chart's <em>destination</em> preview is the opposite case, and is surveyed
 * server-side for exactly that reason.)
 *
 * <p>One sample per block, shaded off the same rules as a vanilla map, and then given height. Coarser
 * sampling was the whole problem with the first version of this: at three blocks a sample the
 * projection was a field of unrelated colours that happened to be at different heights, and a landscape
 * you cannot recognise is not a map of anywhere.
 *
 * <p>Sampling is cached and rebuilt only when the ship has actually gone somewhere. A table on a
 * moving hull would otherwise re-read thousands of heightmap columns every frame, which is a lot of
 * work to produce a picture that has not changed.
 */
@OnlyIn(Dist.CLIENT)
public final class TerrainHologram {

    /** Samples across a full-sized projection, one per block. Smaller tables chart proportionally less. */
    private static final int FULL_CELLS = 80;

    /** Blocks between samples. One: this is a map, not a mosaic. */
    private static final int STEP = 1;

    /** How wide a full-sized projection is drawn, in blocks. A little inside the table it sits on. */
    private static final float FULL_SPAN = 2.6F;

    /** Vertical range of terrain the projection can express, in blocks either side of the ship. */
    private static final float RELIEF_RANGE = 48.0F;

    /**
     * How tall that range is drawn.
     *
     * <p>Rather more than it used to be. Against the horizontal scale this is still a landscape
     * flattened to about three-fifths of true, but it is enough that a ridge reads as a ridge instead
     * of as a change of colour, and it keeps the whole projection under a block tall so a player can
     * still see across the table.
     *
     * <p>Note that the relief is drawn <em>upwards</em> from the projection's origin rather than
     * either side of it - see {@link #elevation}. A ship is usually flying above the ground it is over,
     * so a range centred on the ship's own altitude would hang almost entirely downwards, and the
     * bottom of it would be inside the table.
     */
    private static final float FULL_RELIEF_HEIGHT = 0.85F;

    /** Blocks of terrain a step has to drop before it is drawn as a cliff face rather than a slope. */
    private static final int CLIFF = 2;

    /** Ticks between rebuilds when the ship is sitting still. */
    private static final int REFRESH_TICKS = 40;

    /** How far the ship has to move before the picture is worth resampling, in blocks. */
    private static final double REBUILD_DISTANCE = 8.0D;

    /**
     * Ticks a rebuild has to wait however far the ship has gone.
     *
     * <p>Distance alone is not a floor. A hull in a warp corridor covers nine blocks a tick, which
     * without this would mean resampling every column every single frame of the one part of
     * the journey where nobody can see the table anyway.
     */
    private static final int MIN_REBUILD_TICKS = 10;

    /**
     * Furthest the samples may be drawn from the middle of the table, in blocks.
     *
     * <p>{@link #REBUILD_DISTANCE} blocks of travel works out at about a quarter of this, so in
     * ordinary flight the limit is never reached. It is here for the case the rebuild floor cannot
     * cover - a hull in a warp corridor moves nine blocks a tick, and without a stop the chart would
     * slide clean off its table on the one part of the journey where it cannot be rebuilt fast
     * enough. Past the limit the terrain goes back to being dragged along, which is wrong but is at
     * least still on the table.
     */
    private static final float FULL_MAX_SHIFT = 0.3F;

    /** Rows either side of the scan line that are lifted as it passes. */
    private static final int BAND = 3;

    /** The tint the whole projection is pulled towards, so it reads as light rather than as terrain. */
    private static final int TINT = 0x49D9C4;

    /** How far towards that tint. Light: the map has to survive being tinted. */
    private static final float TINT_STRENGTH = 0.16F;

    /**
     * How much of a full-sized table this one is: 1/3, 2/3 or 1.
     *
     * <p>Everything about the picture scales by it together - how far it is thrown, how much ground
     * it covers, and how tall its relief stands. Scaling the span without the sample count would
     * give a small table the same landscape drawn smaller, which is a worse map rather than a
     * smaller one; scaling both keeps a block of ground the same size on every table, so the only
     * thing a bigger table buys is more of it.
     */
    private final float scale;

    private final int cells;
    private final float span;
    private final float reliefHeight;
    private final float maxShift;

    private final int[] colours;
    private final float[] heights;
    private final int[] surface;
    private final byte[] palette;
    private final int[] depth;

    /**
     * @param size blocks along a side of the table drawing this, 1 to
     *             {@link uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure#MAX_SIZE}
     */
    public TerrainHologram(int size) {
        this.scale = Mth.clamp(size, 1, AstrolabeStructure.MAX_SIZE)
                / (float) AstrolabeStructure.MAX_SIZE;
        this.cells = Math.max(8, Math.round(FULL_CELLS * scale));
        this.span = FULL_SPAN * scale;
        this.reliefHeight = FULL_RELIEF_HEIGHT * scale;
        this.maxShift = FULL_MAX_SHIFT * scale;
        this.colours = new int[cells * cells];
        this.heights = new float[cells * cells];
        this.surface = new int[cells * cells];
        this.palette = new byte[cells * cells];
        this.depth = new int[cells * cells];
    }

    private boolean built;
    private long builtAt = Long.MIN_VALUE;
    private Vec3 builtAround = Vec3.ZERO;

    /**
     * World column the first sample was taken from.
     *
     * <p>Kept because the grid is anchored to whole blocks but the table it is drawn over is not, and
     * on a moving ship the two drift apart between rebuilds. See {@link #render}.
     */
    private int originX;
    private int originZ;

    /**
     * Rebuilds the sample grid if it has gone stale.
     *
     * @param centre where in the world the table currently is
     */
    public void refresh(ClientLevel level, Vec3 centre, long gameTime) {
        boolean moved = centre.distanceToSqr(builtAround) > REBUILD_DISTANCE * REBUILD_DISTANCE;
        long since = gameTime - builtAt;
        if (built && (since < MIN_REBUILD_TICKS || (!moved && since < REFRESH_TICKS))) {
            return;
        }
        builtAt = gameTime;
        builtAround = centre;
        built = true;

        originX = Mth.floor(centre.x) - cells * STEP / 2;
        originZ = Mth.floor(centre.z) - cells * STEP / 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Chunk presence is checked once per chunk rather than once per sample.
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean chunkLoaded = false;

        // Heights first, colours second. Shading a cell needs its northern neighbour's height, and on
        // the row where one chunk meets the next that neighbour has not been read yet.
        for (int row = 0; row < cells; row++) {
            int worldZ = originZ + row * STEP;
            for (int column = 0; column < cells; column++) {
                int index = row * cells + column;
                int worldX = originX + column * STEP;
                int chunkX = worldX >> 4;
                int chunkZ = worldZ >> 4;
                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                    chunkLoaded = level.hasChunk(chunkX, chunkZ);
                }
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

        for (int row = 0; row < cells; row++) {
            for (int column = 0; column < cells; column++) {
                int index = row * cells + column;
                if (surface[index] == TerrainPalette.UNKNOWN_SURFACE) {
                    colours[index] = 0;
                    heights[index] = 0.0F;
                    continue;
                }
                MapColor colour = MapColor.byId(palette[index] & 0xFF);
                int north = row > 0 ? surface[index - cells] : TerrainPalette.UNKNOWN_SURFACE;
                MapColor.Brightness brightness = colour == MapColor.WATER
                        ? TerrainPalette.depth(depth[index], column, row)
                        : TerrainPalette.slope(surface[index], north, STEP, column, row);
                colours[index] = tint(TerrainPalette.argb(colour, brightness) & 0xFFFFFF);
                heights[index] = Mth.clamp((surface[index] - (float) centre.y) / RELIEF_RANGE, -1.0F, 1.0F);
            }
        }
    }

    /** Pulls a map colour towards the projection's own light so the whole thing reads as one object. */
    private static int tint(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r * (1.0F - TINT_STRENGTH) + ((TINT >> 16) & 0xFF) * TINT_STRENGTH);
        g = Math.round(g * (1.0F - TINT_STRENGTH) + ((TINT >> 8) & 0xFF) * TINT_STRENGTH);
        b = Math.round(b * (1.0F - TINT_STRENGTH) + (TINT & 0xFF) * TINT_STRENGTH);
        // A colour of exactly zero is the "nothing here" marker, so nudge a genuinely black cell.
        int packed = (r << 16) | (g << 8) | b;
        return packed == 0 ? 0x010101 : packed;
    }

    /**
     * Draws the projection, centred on the origin of the current pose.
     *
     * @param sweep 0..1 position of the scan line that travels across the map
     * @param alpha overall opacity, so the projection can fade in rather than snap on
     */
    public void render(PoseStack poseStack, VertexConsumer buffer, float sweep, float alpha, Vec3 centre) {
        if (!built) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        float cell = span / cells;
        int scanRow = Mth.clamp((int) (sweep * cells), 0, cells - 1);

        // Where the samples belong relative to where the table is now.
        //
        // The grid is anchored to whole world columns, and it is only rebuilt every so often - but the
        // ship carrying the table moves continuously. Drawing the grid centred on the table regardless
        // dragged the whole landscape along with the hull and then snapped it back on the next rebuild,
        // which on a moving ship is most of what "the map jitters" was. Offsetting by the distance the
        // table has travelled since the samples were taken pins the terrain to the ground it was read
        // off, so the ship slides across its own chart and a rebuild changes nothing anybody can see.
        float shiftX = Mth.clamp((float) (originX + cells * STEP / 2.0D - centre.x) * cell / STEP,
                -maxShift, maxShift);
        float shiftZ = Mth.clamp((float) (originZ + cells * STEP / 2.0D - centre.z) * cell / STEP,
                -maxShift, maxShift);
        float half = span / 2.0F;

        for (int row = 0; row < cells; row++) {
            // The scan line brightens a band as it passes, which is the whole of what stops a static
            // relief from looking like a painted model. Worked out per row rather than per cell.
            int fromScan = Math.abs(row - scanRow);
            float lift = fromScan > BAND ? 0.0F : 0.4F * (1.0F - fromScan / (float) (BAND + 1));
            float faceAlpha = alpha * (0.78F + lift * 0.22F);

            for (int column = 0; column < cells; column++) {
                int index = row * cells + column;
                int colour = colours[index];
                if (colour == 0) {
                    continue;
                }
                float x0 = -half + column * cell + shiftX;
                float z0 = -half + row * cell + shiftZ;
                float y = elevation(heights[index]);

                quad(matrix, buffer, x0, y, z0, cell, withAlpha(brighten(colour, lift), faceAlpha));

                // A skirt down to whichever neighbour is lower, so a cliff is a cliff rather than two
                // tiles at different heights with a hole between them. Only for a real step: at one
                // sample a block the ordinary slope of a hillside is every cell, and filling those
                // in would triple the geometry to draw something the shading already says.
                if (column + 1 < cells && colours[index + 1] != 0
                        && surface[index] - surface[index + 1] >= CLIFF) {
                    skirtX(matrix, buffer, x0 + cell, y, elevation(heights[index + 1]), z0, cell,
                            withAlpha(colour, alpha * 0.5F));
                }
                if (row + 1 < cells && colours[index + cells] != 0
                        && surface[index] - surface[index + cells] >= CLIFF) {
                    skirtZ(matrix, buffer, x0, y, elevation(heights[index + cells]), z0 + cell, cell,
                            withAlpha(colour, alpha * 0.5F));
                }
            }
        }

        // Where the ship is: a bright mote at the middle, which is where the table always is. Sized in
        // cells rather than left at one, because one cell is now a single block and invisible.
        // Deliberately not shifted with the terrain: this one is the table, which is the origin.
        float mote = cell * 3.0F;
        int here = (cells / 2) * cells + cells / 2;
        quad(matrix, buffer, -mote * 0.5F, elevation(heights[here]) + reliefHeight * 0.03F,
                -mote * 0.5F, mote, withAlpha(0xFFFFFF, alpha));
    }

    /**
     * A sampled height, 0..1 from the bottom of the projection.
     *
     * <p>The whole relief therefore sits between the projection's origin and one {@code reliefHeight}
     * above it, which is what keeps the low ground out of the tabletop it is floating over.
     */
    private float elevation(float normalised) {
        return (normalised * 0.5F + 0.5F) * reliefHeight;
    }

    private static void quad(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z,
                             float size, int argb) {
        vertex(matrix, buffer, x, y, z, argb);
        vertex(matrix, buffer, x, y, z + size, argb);
        vertex(matrix, buffer, x + size, y, z + size, argb);
        vertex(matrix, buffer, x + size, y, z, argb);
    }

    private static void skirtX(Matrix4f matrix, VertexConsumer buffer, float x, float top, float bottom,
                               float z, float size, int argb) {
        vertex(matrix, buffer, x, top, z, argb);
        vertex(matrix, buffer, x, bottom, z, argb);
        vertex(matrix, buffer, x, bottom, z + size, argb);
        vertex(matrix, buffer, x, top, z + size, argb);
    }

    private static void skirtZ(Matrix4f matrix, VertexConsumer buffer, float x, float top, float bottom,
                               float z, float size, int argb) {
        vertex(matrix, buffer, x, top, z, argb);
        vertex(matrix, buffer, x, bottom, z, argb);
        vertex(matrix, buffer, x + size, bottom, z, argb);
        vertex(matrix, buffer, x + size, top, z, argb);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z, int argb) {
        buffer.addVertex(matrix, x, y, z).setColor(argb);
    }

    private static int brighten(int rgb, float amount) {
        int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) + 255 * amount), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) + 255 * amount), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) + 255 * amount), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Ship-to-world transform for the hull a block is riding, or {@code null} for one on the ground.
     *
     * <p>A table bolted to an airship has a block position inside that ship's plot - a reserved
     * corner of the world nowhere near where the ship appears to be - so its own coordinates are the
     * wrong thing to map. Sable's pose converts them back into somewhere that means something.
     */
    public static Pose3dc poseOf(ClientLevel level, BlockPos pos, float partialTick) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel instanceof ClientSubLevel client) {
            // The pose Sable draws the hull itself at, interpolated between network snapshots. Its
            // logical pose only moves once a tick, so anything positioned from that stutters against
            // the very blocks it is supposed to be sitting on.
            return client.renderPose(partialTick);
        }
        return subLevel == null ? null : subLevel.logicalPose();
    }

    /**
     * Where a table's middle is in the world.
     *
     * <p>The offset is applied before the pose rather than after, because it is a distance measured
     * across the table - and on a hull that is pitched or turned, "half a block that way" means
     * something different in the ship's frame than it does in the world's.
     *
     * @param offset blocks from the anchoring cell to the middle of the table, along both axes
     */
    public static Vec3 centreOf(BlockPos pos, double offset, Pose3dc pose) {
        Vec3 local = Vec3.atCenterOf(pos).add(offset, 0.0D, offset);
        return pose == null ? local : pose.transformPosition(local);
    }
}
