package uk.co.iceconchy.aerowarptics.client.screen;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Ward's sidebar, as rectangles. A piece four pixels over another, or a foot hung off the bottom of the
 * smallest screen the game promises, draws and runs and is simply wrong.
 */
class WardLayoutTest {

    /** The smallest screen Auto GUI scale promises, and some a player might really have. */
    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}};

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static boolean inside(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.right() <= outer.right() && inner.y() >= outer.y()
                && inner.bottom() <= outer.bottom();
    }

    @Test
    void everyPieceIsOnTheStripAndClearOfEveryOther() {
        for (int[] screen : SCREENS) {
            WardLayout.Sidebar side = WardLayout.sidebar(screen[0], screen[1]);
            List<Rect> pieces = side.pieces();
            for (int i = 0; i < pieces.size(); i++) {
                assertTrue(inside(side.frame(), pieces.get(i)), "piece " + i + " hangs off the strip at "
                        + screen[0] + "x" + screen[1] + ": " + pieces.get(i));
                for (int j = i + 1; j < pieces.size(); j++) {
                    assertFalse(overlap(pieces.get(i), pieces.get(j)), "pieces " + i + " and " + j + " overlap at "
                            + screen[0] + "x" + screen[1] + ": " + pieces.get(i) + " " + pieces.get(j));
                }
            }
        }
    }

    /** The strip is on the right, runs the screen's height, and leaves the world most of a small screen. */
    @Test
    void theStripLeavesTheWorldInView() {
        for (int[] screen : SCREENS) {
            WardLayout.Sidebar side = WardLayout.sidebar(screen[0], screen[1]);
            assertEquals(screen[0], side.frame().right());
            assertEquals(0, side.frame().y());
            assertEquals(screen[1], side.frame().height());
            assertTrue(side.frame().x() >= screen[0] / 2, "the strip covers more than half of a " + screen[0] + " screen");
        }
    }

    @Test
    void thereAreTwoTabsAndSixRowsAndTheyFillTheStripsWidth() {
        WardLayout.Sidebar side = WardLayout.sidebar(320, 240);
        assertEquals(WardLayout.TABS, side.tabs().size());
        assertEquals(WardLayout.ROWS, side.rows().size());
        assertEquals(side.rows().get(0).x(), side.tabs().get(0).x());
        assertEquals(side.rows().get(0).right(), side.tabs().get(WardLayout.TABS - 1).right());
        for (Rect row : side.rows()) {
            assertEquals(WardLayout.ROW_HEIGHT, row.height());
        }
    }

    /** The Who tab's own pieces: the box and its button share a row; the names fill three rows and no more. */
    @Test
    void theNamesAndTheBoxStandInTheirRows() {
        WardLayout.Sidebar side = WardLayout.sidebar(320, 240);
        assertTrue(inside(side.rows().get(1), side.nameBox()));
        assertTrue(inside(side.rows().get(1), side.add()));
        assertFalse(overlap(side.nameBox(), side.add()));
        Rect names = side.names();
        assertEquals(side.rows().get(2).y(), names.y());
        assertEquals(side.rows().get(4).bottom(), names.bottom());
        assertFalse(overlap(names, side.rows().get(1)));
        assertFalse(overlap(names, side.rows().get(5)));
        for (int shown = 0; shown < WardLayout.NAMES_SHOWN; shown++) {
            assertTrue(inside(names, side.name(shown)), "name " + shown + " hangs out of the list");
            assertTrue(inside(side.name(shown), side.strike(shown)));
            if (shown > 0) {
                assertFalse(overlap(side.name(shown - 1), side.name(shown)));
            }
        }
    }

    @Test
    void theSlidersBarSitsInItsRowUnderItsCaption() {
        Rect row = WardLayout.sidebar(320, 240).rows().get(3);
        Rect bar = WardLayout.sliderBar(row);
        assertTrue(inside(row, bar));
        assertTrue(bar.y() >= row.y() + 10, "the bar is drawn over its caption");
        // And the knob, two pixels proud of the bar either way, stays in the row.
        assertTrue(bar.y() - 2 >= row.y() && bar.bottom() + 2 <= row.bottom());
    }

    @Test
    void theListOfNamesScrollsOnlyAsFarAsItHasNames() {
        assertEquals(0, WardLayout.clampScroll(5, 3));
        assertEquals(0, WardLayout.clampScroll(-2, 8));
        assertEquals(8 - WardLayout.NAMES_SHOWN, WardLayout.clampScroll(99, 8));
        assertEquals(1, WardLayout.clampScroll(1, 8));
    }

    /** The camera stands off far enough to take in the field, and never further than it can go. */
    @Test
    void theCameraDrawsBackWithTheRadius() {
        assertTrue(WardLayout.cameraDistance(16.0D) < WardLayout.cameraDistance(96.0D));
        assertTrue(WardLayout.cameraDistance(96.0D) > 96.0D * 2.0D, "a field's far rim is behind the camera");
        assertEquals(OrbitCamera.MAX_DISTANCE, WardLayout.cameraDistance(100_000.0D));
    }
}
