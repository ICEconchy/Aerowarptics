package uk.co.iceconchy.aerowarptics.client.screen;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Berth's sidebar, as rectangles: the ward's strip with a third tab, and sliders whose arithmetic the
 * screen must not have to get right for itself.
 */
class BerthLayoutTest {

    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}};

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    @Test
    void everyPieceIsOnTheStripAndClearOfEveryOther() {
        for (int[] screen : SCREENS) {
            WardLayout.Sidebar side = BerthLayout.sidebar(screen[0], screen[1]);
            List<Rect> pieces = side.pieces();
            for (int i = 0; i < pieces.size(); i++) {
                Rect piece = pieces.get(i);
                assertTrue(piece.x() >= side.frame().x() && piece.right() <= side.frame().right()
                        && piece.y() >= 0 && piece.bottom() <= screen[1], "piece " + i + " hangs off the strip at "
                        + screen[0] + "x" + screen[1] + ": " + piece);
                for (int j = i + 1; j < pieces.size(); j++) {
                    assertFalse(overlap(piece, pieces.get(j)), "pieces " + i + " and " + j + " overlap at "
                            + screen[0] + "x" + screen[1]);
                }
            }
        }
    }

    @Test
    void thereAreThreeTabsAndTheyFillTheStripsWidth() {
        WardLayout.Sidebar side = BerthLayout.sidebar(320, 240);
        assertEquals(3, side.tabs().size());
        assertEquals(BerthLayout.ROWS, side.rows().size());
        assertEquals(side.rows().get(0).x(), side.tabs().get(0).x());
        assertEquals(side.rows().get(0).right(), side.tabs().get(2).right());
        // And the ward's own strip is still two: the overload did not move it.
        assertEquals(WardLayout.TABS, WardLayout.sidebar(320, 240).tabs().size());
    }

    /** A name's box sits inside its row, so two of them on one tab never touch. */
    @Test
    void aTextBoxSitsInsideItsRow() {
        WardLayout.Sidebar side = BerthLayout.sidebar(320, 240);
        for (Rect row : side.rows()) {
            Rect box = BerthLayout.textBox(row);
            assertTrue(box.y() >= row.y() && box.bottom() <= row.bottom() && box.width() == row.width());
        }
    }

    /** A slider reaches both its ends, stays in its range off either, and lands only on its steps. */
    @Test
    void aSliderReachesItsEndsAndKeepsToItsSteps() {
        Rect row = BerthLayout.sidebar(320, 240).rows().get(3);
        Rect track = WardLayout.sliderBar(row);
        assertEquals(0, BerthLayout.sliderValue(row, track.x(), 0, 355, 5));
        assertEquals(355, BerthLayout.sliderValue(row, track.right(), 0, 355, 5));
        assertEquals(0, BerthLayout.sliderValue(row, track.x() - 500, 0, 355, 5));
        assertEquals(355, BerthLayout.sliderValue(row, track.right() + 500, 0, 355, 5));
        for (int x = track.x(); x <= track.right(); x++) {
            assertEquals(0, BerthLayout.sliderValue(row, x, 0, 355, 5) % 5, "off a step at pixel " + x);
            int offset = BerthLayout.sliderValue(row, x, -96, 96, 1);
            assertTrue(offset >= -96 && offset <= 96);
        }
        assertEquals(-96, BerthLayout.sliderValue(row, track.x(), -96, 96, 1));
        assertEquals(0.5F, BerthLayout.sliderFraction(0, -96, 96), 1.0e-6F);
        assertEquals(0.0F, BerthLayout.sliderFraction(3, 3, 3), 1.0e-6F, "a slider with no range divided by nought");
    }

    /** The camera stands further off for a longer slip, and never further than the camera goes. */
    @Test
    void theCameraDrawsBackForALongerSlip() {
        assertTrue(BerthLayout.cameraDistance(24, 13) < BerthLayout.cameraDistance(120, 13));
        assertTrue(BerthLayout.cameraDistance(512, 512) <= OrbitCamera.MAX_DISTANCE);
        assertTrue(BerthLayout.cameraDistance(3, 3) >= 18.0F);
    }
}
