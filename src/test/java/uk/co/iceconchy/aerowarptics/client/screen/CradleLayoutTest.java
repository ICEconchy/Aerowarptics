package uk.co.iceconchy.aerowarptics.client.screen;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Collapse Cradle's panel, held to fitting the 320 pixels Auto GUI scale promises and to keeping its parts
 * apart: a slot drawn over the dial, or a button under the inventory, is nothing a compiler would notice.
 */
class CradleLayoutTest {

    private static final Rect WINDOW = new Rect(0, 0, CradleLayout.WIDTH, CradleLayout.HEIGHT);

    @Test
    void itFitsTheSmallestWindowAutoScalePromises() {
        assertTrue(CradleLayout.WIDTH <= 320, "the panel is wider than Auto scale guarantees");
        assertTrue(CradleLayout.HEIGHT <= 240, "the panel is taller than Auto scale guarantees");
    }

    @Test
    void everyRegionIsInsideTheWindowAndApartFromTheRest() {
        CradleLayout.Panel panel = CradleLayout.panel();
        List<Rect> regions = panel.regions();
        for (int i = 0; i < regions.size(); i++) {
            assertInside(WINDOW, regions.get(i), "region " + i);
            for (int j = i + 1; j < regions.size(); j++) {
                assertFalse(regions.get(i).overlaps(regions.get(j)), "regions " + i + " and " + j + " overlap");
            }
        }
    }

    /** The altar holds the three slots and the offerings round them, none of them on top of another. */
    @Test
    void theAltarHoldsItsSlotsAndOfferingsApart() {
        CradleLayout.Panel panel = CradleLayout.panel();
        List<Rect> things = new ArrayList<>(List.of(
                CradleLayout.well(CradleLayout.ARMS_X, CradleLayout.ARMS_Y),
                CradleLayout.well(CradleLayout.CUP_X, CradleLayout.CUP_Y),
                CradleLayout.well(CradleLayout.OUT_X, CradleLayout.OUT_Y)));
        things.addAll(panel.offerings());
        assertTrue(panel.offerings().size() >= 8, "fewer places for offerings than a cradle draws on");
        for (int i = 0; i < things.size(); i++) {
            assertInside(panel.altar(), things.get(i), "altar piece " + i);
            for (int j = i + 1; j < things.size(); j++) {
                assertFalse(things.get(i).overlaps(things.get(j)), "altar pieces " + i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void theReadoutsHoldTheirGauges() {
        CradleLayout.Panel panel = CradleLayout.panel();
        List<Rect> gauges = List.of(panel.essence(), panel.speed(), panel.lean(), panel.figures());
        for (int i = 0; i < gauges.size(); i++) {
            assertInside(panel.readouts(), gauges.get(i), "gauge " + i);
            for (int j = i + 1; j < gauges.size(); j++) {
                assertFalse(gauges.get(i).overlaps(gauges.get(j)), "gauges " + i + " and " + j + " overlap");
            }
        }
    }

    /** Every one of the player's slots is inside the inventory region, which is what keeps the button off them. */
    @Test
    void thePlayersSlotsAreInTheInventory() {
        Rect inventory = CradleLayout.panel().inventory();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                assertInside(inventory, CradleLayout.well(CradleLayout.INVENTORY_X + column * CradleLayout.SLOT,
                        CradleLayout.INVENTORY_Y + row * CradleLayout.SLOT), "inventory " + row + "," + column);
            }
        }
        for (int column = 0; column < 9; column++) {
            assertInside(inventory, CradleLayout.well(CradleLayout.INVENTORY_X + column * CradleLayout.SLOT,
                    CradleLayout.HOTBAR_Y), "hotbar " + column);
        }
        assertTrue(CradleLayout.HOTBAR_Y >= CradleLayout.INVENTORY_Y + 3 * CradleLayout.SLOT,
                "the hotbar is on top of the inventory");
    }

    private static void assertInside(Rect outer, Rect inner, String what) {
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom(), what + " " + inner + " is not inside " + outer);
    }
}
