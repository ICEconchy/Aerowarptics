package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.warp.ScatterOffset;
import uk.co.iceconchy.aerowarptics.weather.RiftStormRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Rift Storm does to a drive.
 *
 * <p>Read against the tiers' shipped defaults rather than invented numbers, so the tests say what a
 * player on an untouched config actually gets.
 */
class RiftStormRulesTest {

    private static final double SINGULARITY = RiftDriveTier.SINGULARITY.defaults().instability();

    // ------------------------------------------------------------ cooldown

    @Test
    void calmWeatherLeavesTheCooldownAlone() {
        assertEquals(2_400, RiftStormRules.cooldownTicks(2_400, false, 0.5D));
    }

    @Test
    void aStormShortensTheCooldown() {
        assertEquals(1_200, RiftStormRules.cooldownTicks(2_400, true, 0.5D));
        assertEquals(1_800, RiftStormRules.cooldownTicks(2_400, true, 0.75D));
    }

    @Test
    void aStormNeverLengthensTheCooldownOrMakesItNegative() {
        assertEquals(2_400, RiftStormRules.cooldownTicks(2_400, true, 3.0D), "a multiplier past one is clamped");
        assertEquals(0, RiftStormRules.cooldownTicks(2_400, true, -1.0D), "a negative multiplier is clamped");
        assertEquals(0, RiftStormRules.cooldownTicks(0, true, 0.5D), "the creative drive has none to shorten");
    }

    @Test
    void theShortenedCooldownRoundsRatherThanTruncates() {
        assertEquals(2, RiftStormRules.cooldownTicks(3, true, 0.5D));
    }

    // --------------------------------------------------------- instability

    @Test
    void calmWeatherLeavesEveryTiersInstabilityAlone() {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            double own = tier.defaults().instability();
            assertEquals(own, RiftStormRules.instability(tier, own, SINGULARITY, false), tier.name());
        }
    }

    @Test
    void aStormLendsTheOrdinaryDrivesTheSingularitysInstability() {
        for (RiftDriveTier tier : new RiftDriveTier[] {RiftDriveTier.MK_I, RiftDriveTier.MK_II, RiftDriveTier.MK_III}) {
            double own = tier.defaults().instability();
            assertEquals(own + SINGULARITY, RiftStormRules.instability(tier, own, SINGULARITY, true), 1.0e-9D,
                    tier.name());
        }
    }

    @Test
    void theSingularityIsNotChargedItsOwnInstabilityTwice() {
        assertEquals(SINGULARITY,
                RiftStormRules.instability(RiftDriveTier.SINGULARITY, SINGULARITY, SINGULARITY, true));
    }

    @Test
    void aCreativeDriveStaysStableInAStorm() {
        assertEquals(0.0D, RiftStormRules.instability(RiftDriveTier.CREATIVE, 0.0D, SINGULARITY, true));
    }

    @Test
    void borrowedInstabilityIsAddedOnTopAndClampedToACertainty() {
        assertEquals(0.4D, RiftStormRules.instability(RiftDriveTier.MK_II, 0.25D, 0.15D, true), 1.0e-9D,
                "a server that has given a Mk II its own instability keeps it, with the Singularity's added");
        assertEquals(1.0D, RiftStormRules.instability(RiftDriveTier.MK_III, 0.8D, 0.6D, true),
                "a probability past one is clamped");
    }

    /**
     * The whole point, end to end: on a shipped config a Mk I never scatters in calm weather and does
     * in a storm, on exactly the rolls a Singularity would.
     */
    @Test
    void aMkIScattersInAStormOnTheRollsASingularityWould() {
        double own = RiftDriveTier.MK_I.defaults().instability();
        double calm = RiftStormRules.instability(RiftDriveTier.MK_I, own, SINGULARITY, false);
        double storm = RiftStormRules.instability(RiftDriveTier.MK_I, own, SINGULARITY, true);
        for (double roll = 0.0D; roll < 1.0D; roll += 0.01D) {
            assertFalse(ScatterOffset.isScattered(calm, roll), "calm roll " + roll);
            assertEquals(ScatterOffset.isScattered(SINGULARITY, roll), ScatterOffset.isScattered(storm, roll),
                    "storm roll " + roll);
        }
        assertTrue(ScatterOffset.isScattered(storm, 0.0D));
    }

    // ----------------------------------------------------------------- roll

    @Test
    void rollsCoverTheWholeRangeInclusively() {
        assertEquals(10, RiftStormRules.roll(10, 20, 0.0D));
        assertEquals(20, RiftStormRules.roll(10, 20, 0.999_999D));
        assertEquals(20, RiftStormRules.roll(10, 20, 1.0D), "a fraction of exactly one must not overshoot");
        assertEquals(15, RiftStormRules.roll(10, 20, 0.5D));
    }

    @Test
    void boundsGivenBackwardsAreReadForwards() {
        assertEquals(10, RiftStormRules.roll(20, 10, 0.0D));
        assertEquals(20, RiftStormRules.roll(20, 10, 0.999_999D));
    }

    @Test
    void equalBoundsAlwaysRollThatValue() {
        assertEquals(7, RiftStormRules.roll(7, 7, 0.0D));
        assertEquals(7, RiftStormRules.roll(7, 7, 0.99D));
    }

    @Test
    void theWidestConfigurableRangeDoesNotOverflow() {
        assertEquals(100_000_000, RiftStormRules.roll(1, 100_000_000, 0.999_999_999D));
    }
}
