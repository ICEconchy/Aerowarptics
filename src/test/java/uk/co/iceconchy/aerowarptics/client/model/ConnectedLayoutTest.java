package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connected textures: which tile a face draws, and that the tiles shipped are in the order that assumes.
 *
 * <p>Nothing about a wrong tile throws. A table one entry out draws a seam where two blocks meet, a tile set drawn in
 * another order draws the wrong borders everywhere, and a quarter laid on the wrong corner draws a border in the
 * middle of a wall. All three are read off here - the last two off the shipped tiles and the vanilla UV layout.
 */
class ConnectedLayoutTest {

    private static final Path CTM = Path.of("src/main/resources/assets/aerowarptics/textures/block/ctm");

    /** Every way the eight neighbours can look different has exactly one tile, and every tile is one of them. */
    @Test
    void everyArrangementHasItsOwnTile() {
        Set<Integer> arrangements = new HashSet<>();
        Set<Integer> tiles = new HashSet<>();
        for (int neighbours = 0; neighbours < 256; neighbours++) {
            int tile = ConnectedLayout.full(neighbours);
            assertTrue(tile >= 0 && tile < ConnectedLayout.FULL_TILES, "no tile for " + neighbours);
            if (arrangements.add(ConnectedLayout.canonical(neighbours))) {
                assertTrue(tiles.add(tile), "two arrangements share tile " + tile);
            }
        }
        assertEquals(ConnectedLayout.FULL_TILES, arrangements.size());
        assertEquals(ConnectedLayout.FULL_TILES, tiles.size());
        assertEquals(0, ConnectedLayout.full(0), "a block on its own is not tile 0");
        assertEquals(26, ConnectedLayout.full(255), "a block in the middle of a wall is not tile 26");
    }

    /** A corner that cannot show - a side beside it not joined - changes nothing. */
    @Test
    void aCornerWithoutBothItsSidesIsIgnored() {
        assertEquals(ConnectedLayout.full(ConnectedLayout.UP), ConnectedLayout.full(ConnectedLayout.UP | ConnectedLayout.UP_LEFT));
        assertEquals(ConnectedLayout.full(0), ConnectedLayout.full(ConnectedLayout.UP_LEFT | ConnectedLayout.DOWN_RIGHT));
    }

    /**
     * The artist's 47 Rift Gate frame tiles are in the order {@link ConnectedLayout#TILES} says: each unjoined edge of
     * a tile is a brass border, and each unjoined corner between two joined edges a brass nub, so a tile's arrangement
     * can be read off its pixels.
     */
    @Test
    void theGateFrameTilesAreInTheirOrder() throws IOException {
        for (int tile = 0; tile < ConnectedLayout.FULL_TILES; tile++) {
            BufferedImage image = ImageIO.read(CTM.resolve("rift_gate_frame/" + tile + ".png").toFile());
            assertEquals(ConnectedLayout.mask(ConnectedLayout.TILES[tile]), arrangement(image), "tile " + tile);
        }
    }

    /** The compact sets: alone, joined all round, up and down, across, and the inside corner, in that order. */
    @Test
    void theCompactTilesAreInTheirOrder() throws IOException {
        int all = ConnectedLayout.UP | ConnectedLayout.DOWN | ConnectedLayout.LEFT | ConnectedLayout.RIGHT;
        int[] expected = {
                0,
                all | ConnectedLayout.UP_LEFT | ConnectedLayout.UP_RIGHT | ConnectedLayout.DOWN_LEFT | ConnectedLayout.DOWN_RIGHT,
                ConnectedLayout.UP | ConnectedLayout.DOWN,
                ConnectedLayout.LEFT | ConnectedLayout.RIGHT,
                all,
        };
        // Warp Glass is edged in violet, not brass, so only the brass-bordered sets can be read this way.
        for (String set : new String[]{"rift_casing", "rift_wellhead_part"}) {
            for (int tile = 0; tile < ConnectedLayout.COMPACT_TILES; tile++) {
                BufferedImage image = ImageIO.read(CTM.resolve(set + "/" + tile + ".png").toFile());
                assertEquals(expected[tile], arrangement(image), set + " tile " + tile);
            }
        }
        for (String set : new String[]{"rift_casing", "rift_wellhead_part", "warp_glass"}) {
            for (int tile = 0; tile < ConnectedLayout.COMPACT_TILES; tile++) {
                assertTrue(Files.exists(CTM.resolve(set + "/" + tile + ".png")), set + " has no tile " + tile);
            }
        }
    }

    /** A quarter takes its tile from the two neighbours beside its corner and the one across it. */
    @Test
    void aQuarterReadsItsOwnCorner() {
        assertEquals(ConnectedLayout.ALONE, ConnectedLayout.compact(false, false, true));
        assertEquals(ConnectedLayout.ACROSS, ConnectedLayout.compact(true, false, true));
        assertEquals(ConnectedLayout.UP_AND_DOWN, ConnectedLayout.compact(false, true, true));
        assertEquals(ConnectedLayout.INSIDE_CORNER, ConnectedLayout.compact(true, true, false));
        assertEquals(ConnectedLayout.JOINED, ConnectedLayout.compact(true, true, true));
    }

    /**
     * Each quarter of a face takes the same quarter of its tile, on every face: the halves the model cuts the face into
     * along its right and up are the halves vanilla's UVs give a cube's face, so up-left on the face is up-left on the
     * texture.
     */
    @Test
    void eachQuarterOfAFaceIsTheSameQuarterOfItsTile() {
        float[][] expected = {{0, 0, 8, 8}, {8, 0, 16, 8}, {0, 8, 8, 16}, {8, 8, 16, 16}};
        for (Direction face : Direction.values()) {
            for (int part = 0; part < 4; part++) {
                float[] uvs = ConnectedGeometry.uvs(face, ConnectedGeometry.quarter(face, part));
                assertArrayEquals(expected[part], uvs, 1.0e-6F, face + " quarter " + part);
            }
            assertArrayEquals(new float[]{0, 0, 16, 16}, ConnectedGeometry.uvs(face, new float[][]{{0, 0, 0}, {16, 16, 16}}),
                    1.0e-6F, face + " whole");
        }
    }

    /** A tile's arrangement from its pixels: an edge without a brass border is joined, a corner without a nub too. */
    private static int arrangement(BufferedImage image) {
        int last = image.getWidth() - 1;
        boolean up = !border(image, true, 0);
        boolean down = !border(image, true, last);
        boolean left = !border(image, false, 0);
        boolean right = !border(image, false, last);
        int mask = (up ? ConnectedLayout.UP : 0) | (down ? ConnectedLayout.DOWN : 0)
                | (left ? ConnectedLayout.LEFT : 0) | (right ? ConnectedLayout.RIGHT : 0);
        mask |= up && left && !brass(image.getRGB(1, 1)) ? ConnectedLayout.UP_LEFT : 0;
        mask |= up && right && !brass(image.getRGB(last - 1, 1)) ? ConnectedLayout.UP_RIGHT : 0;
        mask |= down && left && !brass(image.getRGB(1, last - 1)) ? ConnectedLayout.DOWN_LEFT : 0;
        mask |= down && right && !brass(image.getRGB(last - 1, last - 1)) ? ConnectedLayout.DOWN_RIGHT : 0;
        return mask;
    }

    /** Whether the middle of a row (or column) along a tile's edge is brass. */
    private static boolean border(BufferedImage image, boolean row, int at) {
        int brass = 0;
        for (int i = 4; i < image.getWidth() - 4; i++) {
            if (brass(row ? image.getRGB(i, at) : image.getRGB(at, i))) {
                brass++;
            }
        }
        return brass > (image.getWidth() - 8) / 2;
    }

    /** Warm and bright enough to be the brass the borders are drawn in, rather than stone or rift light. */
    private static boolean brass(int argb) {
        int r = (argb >> 16) & 0xFF;
        int b = argb & 0xFF;
        return r > 90 && r > b + 30;
    }
}
