package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one thing a throat must never do: taper across something it is hiding.
 *
 * <p>A rift's bore is full width for most of its length and then closes to a point, so the far end
 * reads as a tunnel going somewhere rather than a bag. The closing part is a hole in the occluder as
 * far as anything inside it is concerned, so the hull has to be well clear of it.
 *
 * <p>This is not hypothetical. The depth used to be sized by scaling only the passage while the
 * corridor run - flown down the same bore - grew independently of it, so on a long corridor the hull's
 * bow finished about a block inside the closing cone. Invisible in a screenshot, and exactly the sort
 * of thing that is only ever noticed as "the effect looks wrong sometimes".
 */
class ThroatCoverageTest {

    @Test
    void theTaperNeverReachesTheHull() {
        // Everything from a skiff nipping across a valley to a dreadnought on a long corridor run.
        for (double reach = 1.0D; reach <= 400.0D; reach += 0.5D) {
            double fullWidth = WarpFlight.throatFor(reach) * WarpFlight.THROAT_FULL_WIDTH;
            assertTrue(fullWidth > reach, String.format(
                    "a hull reaching %.1f sits inside a bore that is only full width to %.1f",
                    reach, fullWidth));
        }
    }

    @Test
    void thereIsRealMarginRatherThanARoundingWin() {
        // Passing by a hundredth of a block would be passing by luck.
        for (double reach : new double[]{8.0D, 23.0D, 50.0D, 120.0D, 300.0D}) {
            double fullWidth = WarpFlight.throatFor(reach) * WarpFlight.THROAT_FULL_WIDTH;
            assertTrue(fullWidth >= reach * 1.03D,
                    "only " + (fullWidth - reach) + " blocks of margin at reach " + reach);
        }
    }

    @Test
    void aDeeperReachAlwaysAsksForADeeperBore() {
        double previous = 0.0D;
        for (double reach = 1.0D; reach <= 200.0D; reach += 1.0D) {
            double depth = WarpFlight.throatFor(reach);
            assertTrue(depth > previous, "the bore stopped growing with the hull at " + reach);
            previous = depth;
        }
    }
}
