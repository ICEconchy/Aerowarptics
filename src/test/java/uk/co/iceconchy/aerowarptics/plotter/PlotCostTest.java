package uk.co.iceconchy.aerowarptics.plotter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's price. There is no range limit - distance is paid for - so the thing that must hold is that
 * the price only ever goes up with distance and width, never overflows however far the plot, and a turning machine
 * draws at the configured pace.
 */
class PlotCostTest {

    private static final int BASE = 500;
    private static final int PER_KM = 250;
    private static final int PER_RADIUS = 40;

    @Test
    void aPlotCostsItsFloorItsDistanceAndItsWidth() {
        assertEquals(BASE, PlotCost.essence(0.0D, 0.0D, BASE, PER_KM, PER_RADIUS));
        assertEquals(BASE + PER_KM, PlotCost.essence(1000.0D, 0.0D, BASE, PER_KM, PER_RADIUS));
        assertEquals(BASE + PER_KM * 10 + PER_RADIUS * 6, PlotCost.essence(10_000.0D, 6.0D, BASE, PER_KM, PER_RADIUS));
    }

    @Test
    void furtherAndWiderAreNeverCheaper() {
        long last = -1L;
        for (double distance = 0.0D; distance < 2_000_000.0D; distance = distance * 1.7D + 13.0D) {
            long price = PlotCost.essence(distance, 4.0D, BASE, PER_KM, PER_RADIUS);
            assertTrue(price >= last, "a plot " + distance + " out is cheaper than a shorter one");
            last = price;
        }
        assertTrue(PlotCost.essence(500.0D, 12.0D, BASE, PER_KM, PER_RADIUS)
                > PlotCost.essence(500.0D, 3.0D, BASE, PER_KM, PER_RADIUS));
    }

    /** Across a thirty-million-block world and further: a large number, never a negative one. */
    @Test
    void theFarthestPlotIsExpensiveNotAnOverflow() {
        long acrossTheWorld = PlotCost.essence(60_000_000.0D, 16.0D, BASE, PER_KM, PER_RADIUS);
        assertEquals(BASE + 15_000_000L + 640L, acrossTheWorld);
        long absurd = PlotCost.essence(1.0e300D, 1.0e300D, 1_000_000, 1_000_000, 1_000_000);
        assertTrue(absurd > 0L, "an absurd plot went negative");
    }

    @Test
    void nonsenseCostsTheFloor() {
        assertEquals(BASE, PlotCost.essence(Double.NaN, Double.NaN, BASE, PER_KM, PER_RADIUS));
        assertEquals(BASE, PlotCost.essence(-500.0D, -3.0D, BASE, PER_KM, PER_RADIUS));
    }

    @Test
    void itDrawsNothingTooSlowTheRateAtItsMinimumAndNoMoreThanFourTimesThat() {
        assertEquals(0, PlotCost.drawRate(32.0D, 64.0D, 10));
        assertEquals(10, PlotCost.drawRate(64.0D, 64.0D, 10));
        assertEquals(10, PlotCost.drawRate(-64.0D, 64.0D, 10), "either way round");
        assertEquals(20, PlotCost.drawRate(128.0D, 64.0D, 10));
        assertEquals(40, PlotCost.drawRate(256.0D, 64.0D, 10));
        assertEquals(40, PlotCost.drawRate(4096.0D, 64.0D, 10), "capped");
    }

    @Test
    void progressIsAFraction() {
        assertEquals(0.0F, PlotCost.progress(0L, 1000L));
        assertEquals(0.5F, PlotCost.progress(500L, 1000L));
        assertEquals(1.0F, PlotCost.progress(5000L, 1000L));
        assertEquals(1.0F, PlotCost.progress(0L, 0L), "a free plot is paid for");
    }
}
