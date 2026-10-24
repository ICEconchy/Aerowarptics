package uk.co.iceconchy.aerowarptics.client.screen;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plotting table's model of the ground, and the pointer on it: a column for every known block and no wall
 * where there is no step, the block under the pointer is the one the ray reaches first, and what the camera
 * draws where is what the pointer picks there.
 */
class HologramTest {

    private static final int U = HologramMesh.UNKNOWN;

    // ------------------------------------------------------------------ the mesh

    @Test
    void flatGroundIsTopsAndASkirtAndNothingElse() {
        int size = 5;
        int[] heights = new int[size * size];
        Arrays.fill(heights, 64);
        int[] colours = new int[size * size];
        Arrays.fill(colours, 0xFF7FB238);
        HologramMesh mesh = HologramMesh.build(size, heights, colours, 64);
        assertEquals(25, mesh.tops());
        // Walls only round the edge, one per edge cell per outward side: 4 sides x 5 cells.
        assertEquals(25 + 20, mesh.quads());
    }

    @Test
    void aStepHasOneWallAndAHoleHasItsEdgesWalledDownToTheSkirt() {
        int size = 3;
        int[] heights = {64, 64, 64, 64, 70, 64, 64, 64, 64};
        HologramMesh pillar = HologramMesh.build(size, heights, new int[9], 64);
        // Nine tops, the pillar's four sides, and the twelve skirt walls round the outside.
        assertEquals(9 + 4 + 12, pillar.quads());

        int[] holed = {64, 64, 64, 64, U, 64, 64, 64, 64};
        HologramMesh hole = HologramMesh.build(size, holed, new int[9], 64);
        // Eight tops, the four walls facing into the hole, and the twelve round the outside.
        assertEquals(8, hole.tops());
        assertEquals(8 + 4 + 12, hole.quads());
    }

    @Test
    void theTargetsGroundIsHeightNothing() {
        int[] heights = {60, 61, 62, 63, 64, 65, 66, 67, 68};
        HologramMesh mesh = HologramMesh.build(3, heights, new int[9], 64);
        float[] positions = mesh.positions();
        boolean found = false;
        for (int quad = 0; quad < mesh.quads(); quad++) {
            int at = quad * HologramMesh.FLOATS_PER_QUAD;
            // The middle column's top is the square 0..1 at height 0.
            if (positions[at] == 0.0F && positions[at + 1] == 0.0F && positions[at + 2] == 1.0F
                    && positions[at + 4] == 0.0F && positions[at + 7] == 0.0F) {
                found = true;
            }
        }
        assertTrue(found, "no top at height 0 over the middle column");
        assertEquals(60 - 64 - HologramMesh.SKIRT, mesh.floor(), 1.0e-6F);
    }

    @Test
    void facesAreShadedAsTheGameShadesABlock() {
        assertEquals(0xFF_646464, HologramMesh.shade(0xFF_7D7D7D, HologramMesh.NORTH_SOUTH));
        assertEquals(0x80_000000 | 0x10_20_30, HologramMesh.shade(0x80_102030, 1.0F), "alpha kept");
    }

    // ------------------------------------------------------------------ the pointer

    /** A column heights, one per cell, as the mesh measures them. */
    private static int top(int[] tops, int size, int x, int z) {
        return tops[z * size + x];
    }

    @Test
    void aRayStraightDownPicksTheColumnUnderIt() {
        int size = 5;
        int[] tops = new int[size * size];
        HologramPick.Hit hit = HologramPick.pick(new double[]{1.5D, 50.0D, -0.5D}, new double[]{0.0D, -1.0D, 0.0D}, size,
                (x, z) -> top(tops, size, x, z));
        assertNotNull(hit);
        // Model x 1.5 is cell 1 + radius 2 = 3; model z -0.5 is cell -1 + 2 = 1.
        assertEquals(3, hit.x());
        assertEquals(1, hit.z());
        assertEquals(0.0D, hit.py(), 1.0e-9D);
    }

    /** A wall in the way is hit, not the ground behind it - the block a player aiming past a ridge did not mean. */
    @Test
    void aWallInTheWayIsHitAndNotTheGroundBehindIt() {
        int size = 9;
        int[] tops = new int[size * size];
        // A ridge ten high along cell x = 5.
        for (int z = 0; z < size; z++) {
            tops[z * size + 5] = 10;
        }
        HologramPick.Hit hit = HologramPick.pick(new double[]{-10.0D, 4.0D, 0.5D}, new double[]{1.0D, -0.02D, 0.0D}, size,
                (x, z) -> top(tops, size, x, z));
        assertNotNull(hit);
        assertEquals(5, hit.x(), "hit the ridge's face");
    }

    @Test
    void aRayThatMissesTheModelPicksNothing() {
        int size = 5;
        int[] tops = new int[size * size];
        assertNull(HologramPick.pick(new double[]{0.0D, 50.0D, 0.0D}, new double[]{0.0D, 1.0D, 0.0D}, size,
                (x, z) -> top(tops, size, x, z)), "looking up");
        assertNull(HologramPick.pick(new double[]{40.0D, 50.0D, 40.0D}, new double[]{0.0D, -1.0D, 0.0D}, size,
                (x, z) -> top(tops, size, x, z)), "off the edge");
        int[] unknown = new int[size * size];
        Arrays.fill(unknown, U);
        assertNull(HologramPick.pick(new double[]{0.0D, 50.0D, 0.0D}, new double[]{0.0D, -1.0D, 0.0D}, size,
                (x, z) -> top(unknown, size, x, z)), "nothing known");
    }

    // ------------------------------------------------------------------ the camera

    /** A point drawn on the view and the ray cast back through where it was drawn agree. */
    @Test
    void theRayThroughAPointOnTheViewPassesThroughThePoint() {
        OrbitCamera camera = new OrbitCamera(0.7F, 0.6F, 120.0F);
        camera.focus(3.0F, 1.0F, -2.0F);
        for (int i = 0; i < 40; i++) {
            camera.tick();
        }
        Matrix4f view = camera.view(1.0F);
        Matrix4f projection = OrbitCamera.projection(1.35F, 800.0F);
        float[][] points = {{0.0F, 0.0F, 0.0F}, {20.0F, 5.0F, -13.0F}, {-30.0F, -4.0F, 22.0F}};
        for (float[] point : points) {
            Vector4f clip = new Vector4f(point[0], point[1], point[2], 1.0F).mul(new Matrix4f(projection).mul(view));
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            double[] ray = OrbitCamera.ray(view, projection, ndcX, ndcY);
            // The point lies on the ray: the offset from the ray's origin is parallel to its direction.
            double ox = point[0] - ray[0];
            double oy = point[1] - ray[1];
            double oz = point[2] - ray[2];
            double cx = oy * ray[5] - oz * ray[4];
            double cy = oz * ray[3] - ox * ray[5];
            double cz = ox * ray[4] - oy * ray[3];
            double lengths = Math.sqrt(ox * ox + oy * oy + oz * oz)
                    * Math.sqrt(ray[3] * ray[3] + ray[4] * ray[4] + ray[5] * ray[5]);
            assertTrue(Math.sqrt(cx * cx + cy * cy + cz * cz) / lengths < 1.0e-3D, "the ray misses its own point");
        }
    }

    @Test
    void theCameraStaysAboveTheGroundAndWithinReach() {
        OrbitCamera camera = new OrbitCamera(0.0F, 0.5F, 100.0F);
        camera.turn(0.0F, -10.0F);
        camera.zoom(0.0001F);
        for (int i = 0; i < 60; i++) {
            camera.tick();
        }
        assertEquals(OrbitCamera.MIN_PITCH, camera.pitch(1.0F), 1.0e-4F);
        assertEquals(OrbitCamera.MIN_DISTANCE, camera.distance(1.0F), 1.0e-3F);
        assertTrue(camera.eye(1.0F).y > 0.0F, "the eye went under the ground");
    }

    // ------------------------------------------------------------------ the layout

    @Test
    void theTableFitsItsWindowAndNothingOverlaps() {
        PlotterLayout.Panel panel = PlotterLayout.panel();
        AWLayout.Rect window = new AWLayout.Rect(0, 0, PlotterLayout.WIDTH, PlotterLayout.HEIGHT);
        for (AWLayout.Rect piece : panel.pieces()) {
            assertTrue(piece.x() >= 4 && piece.y() >= 4 && piece.right() <= window.right() - 4
                    && piece.bottom() <= window.bottom() - 4, piece + " is on the frame or off the slab");
        }
        for (int i = 0; i < panel.pieces().size(); i++) {
            for (int j = i + 1; j < panel.pieces().size(); j++) {
                assertTrue(!panel.pieces().get(i).overlaps(panel.pieces().get(j)),
                        panel.pieces().get(i) + " overlaps " + panel.pieces().get(j));
            }
        }
        for (AWLayout.Rect tab : panel.tabButtons()) {
            assertTrue(tab.x() >= panel.tabs().x() && tab.right() <= panel.tabs().right(), tab + " is off its strip");
        }
        assertTrue(AWLayout.fitScale(320, 240, PlotterLayout.WIDTH, PlotterLayout.HEIGHT) > 0.75F,
                "on the smallest screen the table is too small to aim into");
    }

    /** The sidebar the table folds into fits the smallest screen the game promises, with nothing on top of anything. */
    @Test
    void theSidebarFitsEveryScreenAndNothingOverlaps() {
        for (int[] screen : new int[][]{{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            PlotterLayout.Sidebar side = PlotterLayout.sidebar(screen[0], screen[1]);
            for (AWLayout.Rect piece : side.pieces()) {
                assertTrue(piece.x() >= side.frame().x() && piece.right() <= screen[0] && piece.y() >= 0
                        && piece.bottom() <= screen[1], piece + " is off the sidebar on " + screen[0] + "x" + screen[1]);
            }
            for (int i = 0; i < side.pieces().size(); i++) {
                for (int j = i + 1; j < side.pieces().size(); j++) {
                    assertTrue(!side.pieces().get(i).overlaps(side.pieces().get(j)), side.pieces().get(i) + " overlaps "
                            + side.pieces().get(j) + " on " + screen[0] + "x" + screen[1]);
                }
            }
            assertTrue(!side.back().overlaps(side.header()), "the back button covers the title");
            assertTrue(side.frame().x() >= screen[0] / 2, "the sidebar takes more than half the view");
        }
    }
}
