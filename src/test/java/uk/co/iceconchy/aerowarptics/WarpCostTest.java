package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
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

    // ------------------------------------------------------------------ reach
    //
    // The reported bug behind these: a heavy ship refused an anchor "beyond drive range" that every screen
    // showed inside it. The range is only reachable while the jump fits in a full charge, and the hull's
    // mass spends part of that charge before it has gone a block.

    @Test
    void aLightHullReachesTheWholeRange() {
        WarpCost.Formula mkI = formula(1.0D, 4_000.0D);
        assertEquals(4_000.0D, mkI.reach(0.0D), 1.0e-9D);
        assertEquals(4_000.0D, mkI.reach(100_000.0D), 1.0e-9D, "a 100k hull still has charge to spare at 4000");
    }

    @Test
    void aHeavyHullRunsOutOfChargeBeforeItRunsOutOfRange() {
        WarpCost.Formula mkI = formula(1.0D, 4_000.0D);
        // 1.0 - 0.20 base - 0.75 for 200k of mass leaves 0.05 of a charge, at 0.00004 a block.
        assertEquals(1_250.0D, mkI.reach(200_000.0D), 1.0e-6D);
        assertTrue(mkI.exceedsFullCharge(1_300.0D, 200_000.0D),
                "just past the reach, the rules refuse it as too far - inside the tier's range");
        assertFalse(mkI.exceedsFullCharge(1_200.0D, 200_000.0D));
    }

    @Test
    void aHullTooHeavyToJumpAtAllHasNoReach() {
        WarpCost.Formula mkI = formula(1.0D, 4_000.0D);
        assertEquals(-1.0D, mkI.reach(213_334.0D), "the mass alone costs more than a full charge");
        // 125 blocks of charge left, under the 128-block minimum: nothing is both allowed and affordable.
        assertEquals(-1.0D, mkI.reach(212_000.0D), "what is left will not reach the minimum distance");
    }

    @Test
    void theReachAgreesWithTheRulesItSummarises() {
        WarpCost.Formula mkII = formula(1.6D, 24_000.0D);
        for (double mass = 0.0D; mass <= 400_000.0D; mass += 12_500.0D) {
            double reach = mkII.reach(mass);
            if (reach < 0.0D) {
                assertTrue(mkII.exceedsFullCharge(mkII.minimumDistance(), mass),
                        mass + ": no reach, so even the shortest allowed jump must cost more than a charge");
                continue;
            }
            assertFalse(mkII.exceedsFullCharge(reach - 1.0e-6D, mass), mass + ": the reach itself must be affordable");
            assertTrue(reach >= mkII.maximumDistance() - 1.0e-6D || mkII.exceedsFullCharge(reach + 1.0D, mass),
                    mass + ": a block past the reach must be unaffordable, unless the reach is the range");
        }
    }

    @Test
    void theUnclampedCostShowsHowFarOverACharge() {
        WarpCost.Formula mkI = formula(1.0D, 4_000.0D);
        assertEquals(1.0D, mkI.cost(4_000.0D, 400_000.0D), 1.0e-9D, "the clamped figure hides it");
        assertEquals(0.20D + 0.16D + 1.50D, mkI.unclampedCost(4_000.0D, 400_000.0D), 1.0e-9D);
        assertEquals(0.16D, mkI.distanceTerm(4_000.0D), 1.0e-9D);
        assertEquals(1.50D, mkI.massTerm(400_000.0D), 1.0e-9D);
    }

    @Test
    void aFreeDriveReachesItsWholeRangeWhateverItCarries() {
        WarpCost.Formula free = formula(1.0D, 4_000.0D).free();
        assertEquals(4_000.0D, free.reach(1.0e9D), 1.0e-9D);
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

    @Test
    void onlyTheCreativeDriveIsExcusedTheMinimumDistance() {
        assertEquals(0.0D, WarpCost.minimumDistance(RiftDriveTier.CREATIVE, 128.0D),
                "a creative drive must be able to hop a hull across the room");
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            if (tier != RiftDriveTier.CREATIVE) {
                assertEquals(128.0D, WarpCost.minimumDistance(tier, 128.0D),
                        tier + " must still honour the server's minimum");
            }
        }
    }

    @Test
    void aDriveWithNoMinimumWillJumpAcrossTheRoom() {
        // What a creative drive is handed. The floor exists so a pilot cannot spend a whole charge
        // and a cooldown on a hop they could have walked; a drive with neither to spend has nothing
        // for it to protect, and refusing a short hop is exactly the wrong answer for a testing tool.
        WarpCost.Formula creative = new WarpCost.Formula(0.20D, 0.00004D, 0.15D, 40_000.0D, 1.0D,
                0.0D, 100_000_000.0D);
        assertEquals(WarpFailure.NONE, creative.checkRange(0.0D));
        assertEquals(WarpFailure.NONE, creative.checkRange(1.0D));
        assertEquals(WarpFailure.NONE, creative.checkRange(100_000_000.0D));
    }

    @Test
    void aFreeDriveNeverSpendsItsCharge() {
        // The report was a creative drive reading "Flown 3,198 m of 2,495 km": priced like any other drive
        // and only divided by a large efficiency, it ran out of charge there, and refused any jump further.
        WarpCost.Formula priced = formula(100.0D, Double.POSITIVE_INFINITY);
        WarpCost.Formula free = priced.free();
        assertTrue(priced.exceedsFullCharge(3_000_000.0D, 40_000.0D),
                "the old creative pricing did run out - this is the bug");
        for (double distance : new double[]{0.0D, 2_495_000.0D, 1.0e8D, WarpCost.WORLD_SPAN}) {
            assertEquals(0.0D, free.cost(distance, 1.0e9D), "a free drive paid for " + distance + " blocks");
            assertFalse(free.exceedsFullCharge(distance, 1.0e9D), distance + " blocks was out of a free drive's reach");
        }
        assertEquals(priced.minimumDistance(), free.minimumDistance());
        assertEquals(priced.maximumDistance(), free.maximumDistance(), "being free does not change the range");
    }

    @Test
    void aReachLongerThanAnyWorldIsUnlimited() {
        assertTrue(WarpCost.unlimited(Double.POSITIVE_INFINITY));
        assertTrue(WarpCost.unlimited(RiftDriveTier.CREATIVE.defaults().maximumRange()),
                "a creative drive's shipped range has to read as infinite, or the screens print 100,000 km");
        assertTrue(WarpCost.unlimited(WarpCost.WORLD_SPAN));
        // Corner to corner of the widest world border, with the full build height on top, is still inside it.
        double corners = Math.hypot(Math.hypot(59_999_968.0D, 59_999_968.0D), 4_064.0D);
        assertTrue(corners <= WarpCost.WORLD_SPAN, "two places in one world can be further apart than the span");
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            if (!tier.creative()) {
                assertFalse(WarpCost.unlimited(tier.defaults().maximumRange()),
                        tier + " has a real range and must print it");
            }
        }
        assertFalse(WarpCost.unlimited(Double.NaN), "a reach nobody could work out is not an infinite one");
    }
}
