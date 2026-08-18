package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The warp cost and range formulas. */
class WarpCostTest {

    /** Roughly the shipped Mk II numbers, pinned so the tests do not move with the config defaults. */
    private static WarpCost.Formula formula(double efficiency, double maxRange) {
        return new WarpCost.Formula(
                0.20D,      // base
                0.00004D,   // per block
                0.15D,      // per mass unit
                40_000.0D,  // mass reference
                efficiency,
                128.0D,     // minimum distance
                maxRange);
    }

    @Test
    void baseCostAppliesToAZeroLengthWarp() {
        WarpCost.Formula f = formula(1.0D, 100_000.0D);
        assertEquals(0.20D, f.cost(0.0D, 0.0D), 1.0e-9D);
    }

    @Test
    void distanceAndMassBothIncreaseCost() {
        WarpCost.Formula f = formula(1.0D, 1_000_000.0D);
        double near = f.cost(1_000.0D, 0.0D);
        double far = f.cost(10_000.0D, 0.0D);
        double heavy = f.cost(1_000.0D, 40_000.0D);

        assertTrue(far > near, "a longer warp must cost more");
        assertTrue(heavy > near, "a heavier airship must cost more");
        assertEquals(0.20D + 0.04D, near, 1.0e-9D);
        assertEquals(0.20D + 0.04D + 0.15D, heavy, 1.0e-9D);
    }

    @Test
    void efficiencyDividesTheCost() {
        WarpCost.Formula cheap = formula(2.0D, 1_000_000.0D);
        WarpCost.Formula dear = formula(1.0D, 1_000_000.0D);
        assertEquals(dear.cost(5_000.0D, 20_000.0D) / 2.0D, cheap.cost(5_000.0D, 20_000.0D), 1.0e-9D);
    }

    @Test
    void costIsClampedIntoTheChargeRange() {
        WarpCost.Formula f = formula(1.0D, 1.0e9D);
        assertEquals(1.0D, f.cost(1.0e9D, 1.0e9D), 1.0e-9D);
        assertEquals(0.20D, f.cost(-500.0D, -500.0D), 1.0e-9D, "negative inputs are treated as zero");
    }

    @Test
    void exceedsFullChargeDetectsImpossibleJumpsEvenThoughCostIsClamped() {
        WarpCost.Formula f = formula(1.0D, 1.0e9D);
        assertFalse(f.exceedsFullCharge(1_000.0D, 0.0D));
        assertTrue(f.exceedsFullCharge(1.0e7D, 0.0D),
                "a jump whose raw cost is over 100% must be reported as out of reach");
    }

    @Test
    void rangeCheckRejectsBothEnds() {
        WarpCost.Formula f = formula(1.0D, 24_000.0D);
        assertEquals(WarpFailure.DESTINATION_TOO_CLOSE, f.checkRange(10.0D));
        assertEquals(WarpFailure.DESTINATION_TOO_CLOSE, f.checkRange(127.99D));
        assertEquals(WarpFailure.NONE, f.checkRange(128.0D));
        assertEquals(WarpFailure.NONE, f.checkRange(24_000.0D));
        assertEquals(WarpFailure.DESTINATION_TOO_FAR, f.checkRange(24_000.01D));
    }
}
