package uk.co.iceconchy.aerowarptics.client.render;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.weather.RiftStormRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far a storm eats into a drive.
 *
 * <p>The layer this drives draws through a shader that keeps a texel wherever the texture's alpha is at least
 * the threshold handed it, so every failure here is silent and visible. A threshold of zero keeps every empty
 * texel of the sheet as well - the whole drive painted black. A threshold that is not one with no storm puts
 * void on a drive under a clear sky. A rim above its void has no edge showing. And a drive that looks
 * corrupted should be one the storm is actually changing.
 */
class StormCorruptionTest {

    /** The deepest texel either sheet holds; see tools/rift_drive_void.py. */
    private static final float DEEPEST_TEXEL = 254.0F / 255.0F;

    @Test
    void theLookFollowsTheRules() {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            // A tier the storm changes in any way - its cooldown or its exit - is one it visibly eats into.
            boolean changed = RiftStormRules.cooldownTicks(1_000, true, 0.5D) != 1_000 && !tier.creative()
                    || RiftStormRules.instability(tier, 0.1D, 0.2D, true) != 0.1D;
            assertEquals(changed, StormCorruption.affects(tier), tier.name());
        }
        assertFalse(StormCorruption.affects(RiftDriveTier.CREATIVE));
        assertTrue(StormCorruption.affects(RiftDriveTier.SINGULARITY), "the Singularity's cooldown still shortens");
    }

    @Test
    void noStormShowsNothing() {
        float growth = StormCorruption.growth(0.0F, 1.0F);
        assertEquals(0.0F, growth);
        assertTrue(StormCorruption.rimThreshold(growth) > DEEPEST_TEXEL, "the rim shows with no storm");
        assertTrue(StormCorruption.voidThreshold(growth) > DEEPEST_TEXEL, "the void shows with no storm");
        assertEquals(255, StormCorruption.colour(StormCorruption.rimThreshold(growth)) >>> 24);
    }

    @Test
    void theThresholdNeverLetsAnEmptyTexelThrough() {
        for (float growth = 0.0F; growth <= 1.0F; growth += 0.01F) {
            for (float threshold : new float[] {StormCorruption.rimThreshold(growth), StormCorruption.voidThreshold(growth)}) {
                assertTrue(threshold > 0.0F && threshold <= 1.0F, "threshold " + threshold + " at growth " + growth);
                assertTrue(StormCorruption.colour(threshold) >>> 24 >= 1, "a zero alpha passes every texel");
            }
        }
    }

    @Test
    void theRimAlwaysReachesFurtherThanTheVoid() {
        for (float growth = 0.0F; growth <= 1.0F; growth += 0.01F) {
            assertTrue(StormCorruption.rimThreshold(growth) <= StormCorruption.voidThreshold(growth),
                    "the rim is inside its void at growth " + growth);
        }
        float full = StormCorruption.growth(1.0F, 1.0F);
        assertTrue(StormCorruption.voidThreshold(full) - StormCorruption.rimThreshold(full) >= 0.2F,
                "at the height of a storm the rim should be a visible band, not a hairline");
    }

    @Test
    void moreStormGrowsThePatches() {
        float lastRim = Float.MAX_VALUE;
        float lastVoid = Float.MAX_VALUE;
        for (float storm = 0.0F; storm <= 1.0F; storm += 0.02F) {
            for (float breath = 0.0F; breath <= 1.0F; breath += 0.5F) {
                float growth = StormCorruption.growth(storm, breath);
                assertTrue(growth >= 0.0F && growth <= 1.0F);
            }
            float growth = StormCorruption.growth(storm, 0.5F);
            float rim = StormCorruption.rimThreshold(growth);
            float voidThreshold = StormCorruption.voidThreshold(growth);
            assertTrue(rim <= lastRim && voidThreshold <= lastVoid, "a stronger storm shrank the patches at " + storm);
            lastRim = rim;
            lastVoid = voidThreshold;
        }
        assertTrue(StormCorruption.voidThreshold(StormCorruption.growth(1.0F, 1.0F)) < 0.5F,
                "at the height of a storm the void should have opened well past its patches' middles");
    }

    @Test
    void breathingOnlyCreeps() {
        float least = StormCorruption.growth(1.0F, 0.0F);
        float most = StormCorruption.growth(1.0F, 1.0F);
        assertEquals(1.0F, most, 1.0e-6F);
        assertEquals(1.0F - StormCorruption.BREATH, least, 1.0e-6F);
        for (float time = 0.0F; time < 2_000.0F; time += 7.0F) {
            float breath = StormCorruption.breath(time, 1.0F);
            assertTrue(breath >= 0.0F && breath <= 1.0F);
        }
    }
}
