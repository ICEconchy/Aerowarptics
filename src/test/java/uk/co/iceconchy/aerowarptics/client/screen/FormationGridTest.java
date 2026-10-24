package uk.co.iceconchy.aerowarptics.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Fleet Board's grid: a dragged marker lands on the slot it was dropped over. */
class FormationGridTest {

    @Test
    void aSlotSurvivesTheTripToThePanelAndBack() {
        FormationGrid grid = new FormationGrid(20, 40, 104, 108, 60);
        for (double right = -50; right <= 50; right += 12.5) {
            for (double ahead = -50; ahead <= 50; ahead += 12.5) {
                assertEquals(right, grid.right(grid.x(right)), 1.0e-9D);
                assertEquals(ahead, grid.ahead(grid.y(ahead)), 1.0e-9D);
            }
        }
    }

    @Test
    void aMarkerIsPickedUpAnywhereInItsBox() {
        double[] xs = {50};
        double[] ys = {60};
        assertEquals(0, FormationGrid.pick(xs, ys, 50, 60, 6));
        assertEquals(0, FormationGrid.pick(xs, ys, 56, 54, 6), "by its corner, not only its middle");
        assertEquals(-1, FormationGrid.pick(xs, ys, 57, 60, 6), "and not from outside it");
        assertEquals(-1, FormationGrid.pick(new double[0], new double[0], 50, 60, 6));
    }

    @Test
    void ofTwoMarkersDrawnOverEachOtherTheNearerMiddleWins() {
        // Two slots side by side, their boxes overlapping - as they were beside the flagship on the board.
        double[] xs = {40, 48};
        double[] ys = {60, 60};
        assertEquals(0, FormationGrid.pick(xs, ys, 42, 60, 6));
        assertEquals(1, FormationGrid.pick(xs, ys, 46, 60, 6));
        assertEquals(1, FormationGrid.pick(xs, ys, 48, 63, 6), "only one box holds this pixel");
    }

    @Test
    void theBowIsAtTheTopAndStarboardOnTheRight() {
        FormationGrid grid = new FormationGrid(0, 0, 100, 100, 40);
        assertTrue(grid.y(10) < grid.centreY(), "ahead is up the panel");
        assertTrue(grid.x(10) > grid.centreX(), "starboard is to the right");
    }

    @Test
    void theScaleHoldsStillWhileShipsWanderAFewBlocks() {
        assertEquals(new FormationGrid(0, 0, 100, 100, 33).scale(), new FormationGrid(0, 0, 100, 100, 40).scale());
        assertEquals(FormationGrid.MIN_REACH, FormationGrid.fit(2), "never zoomed in past the minimum");
        assertEquals(0.0D, FormationGrid.MIN_REACH % FormationGrid.STEP, 1.0e-9D,
                "the minimum is a whole number of steps, or fitting would move it");
        assertEquals(FormationGrid.MIN_REACH, FormationGrid.fit(Double.NaN));
    }

    @Test
    void theFurthestSlotStaysInsideThePanel() {
        FormationGrid grid = new FormationGrid(10, 10, 104, 108, 90);
        assertTrue(grid.contains(grid.x(90), grid.y(0)));
        assertTrue(grid.contains(grid.x(0), grid.y(-90)));
        assertEquals(10 + 104 - FormationGrid.MARGIN, grid.clampX(10_000), 1.0e-9D);
    }
}
