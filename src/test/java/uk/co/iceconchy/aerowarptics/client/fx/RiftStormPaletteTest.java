package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colours of a storm sky.
 *
 * <p>None of this can throw and all of it can be quietly wrong: a sky that goes grey rather than violet,
 * a night that stays black so the storm is invisible after dark, a flash that dims instead of lighting,
 * a channel pushed past one that wraps to a dark colour in a byte buffer somewhere downstream. The
 * inputs are vanilla's own typical figures - its clear noon sky, its night sky, its white clouds.
 */
class RiftStormPaletteTest {

    private static final float[] NOON_SKY = {0.47F, 0.65F, 1.0F};
    private static final float[] NIGHT_SKY = {0.0F, 0.0F, 0.0F};
    private static final float[] NOON_CLOUD = {1.0F, 1.0F, 1.0F};
    private static final float EPSILON = 1.0e-6F;

    private static float luma(float[] c) {
        return RiftStormPalette.luma(c[0], c[1], c[2]);
    }

    private static float[] sky(float[] c, float storm, float flash) {
        return RiftStormPalette.sky(c[0], c[1], c[2], storm, flash);
    }

    private static void assertColour(float[] expected, float[] actual, String message) {
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(expected[channel], actual[channel], EPSILON, message + " (channel " + channel + ")");
        }
    }

    @Test
    void noStormChangesNothing() {
        assertColour(NOON_SKY, sky(NOON_SKY, 0.0F, 0.0F), "sky");
        assertColour(NOON_CLOUD, RiftStormPalette.clouds(1.0F, 1.0F, 1.0F, 0.0F, 0.0F), "clouds");
        assertColour(NOON_SKY, RiftStormPalette.fog(0.47F, 0.65F, 1.0F, 0.0F, 0.0F), "fog");
        assertEquals(0.8F, RiftStormPalette.skyLight(0.8F, 0.0F, 0.0F), EPSILON);
        assertEquals(0.4F, RiftStormPalette.shroud(0.4F, 0.0F), EPSILON);
        assertEquals(0.4F, RiftStormPalette.vanillaRain(0.4F, 0.0F), EPSILON);
    }

    @Test
    void aStormDarkensADaySky() {
        float[] storm = sky(NOON_SKY, 1.0F, 0.0F);
        assertTrue(luma(storm) < luma(NOON_SKY) * 0.6F,
                "a full storm should take away well over a third of a noon sky's brightness, got "
                        + luma(storm) + " from " + luma(NOON_SKY));
    }

    @Test
    void theDarkeningDeepensWithTheStorm() {
        float previous = Float.MAX_VALUE;
        for (float storm = 0.0F; storm <= 1.0F; storm += 0.1F) {
            float now = luma(sky(NOON_SKY, storm, 0.0F));
            assertTrue(now <= previous + EPSILON, "a stronger storm made the sky brighter at " + storm);
            previous = now;
        }
    }

    @Test
    void theStormIsVioletNotGrey() {
        for (float[] input : new float[][] {NOON_SKY, NIGHT_SKY, {0.9F, 0.5F, 0.2F}}) {
            float[] storm = sky(input, 1.0F, 0.0F);
            assertTrue(storm[2] > storm[0] && storm[0] > storm[1],
                    "expected blue > red > green, got " + storm[0] + ", " + storm[1] + ", " + storm[2]);
        }
        float[] clouds = RiftStormPalette.clouds(1.0F, 1.0F, 1.0F, 1.0F, 0.0F);
        assertTrue(clouds[2] > clouds[0] && clouds[0] > clouds[1], "storm clouds should be violet too");
    }

    @Test
    void aNightStormGlowsFaintlyRatherThanStayingBlack() {
        float[] storm = sky(NIGHT_SKY, 1.0F, 0.0F);
        assertTrue(luma(storm) > 0.01F, "a storm over a black sky must still show");
        assertTrue(luma(storm) < 0.1F, "but only faintly - it is a night sky, got " + luma(storm));
    }

    @Test
    void cloudsGoFurtherThanTheSkyAndFogLeastFar() {
        assertTrue(RiftStormPalette.CLOUD_REACH > RiftStormPalette.SKY_REACH,
                "storm cloud should read heavier than the sky behind it");
        assertTrue(RiftStormPalette.FOG_REACH < RiftStormPalette.SKY_REACH,
                "fog touches the blocks, so it is the lightest-handed of the three");
    }

    @Test
    void aFlashLightsTheSky() {
        float[] dark = sky(NOON_SKY, 1.0F, 0.0F);
        float[] lit = sky(NOON_SKY, 1.0F, 1.0F);
        assertTrue(luma(lit) > luma(dark), "a flash must brighten a storm sky");
        assertTrue(lit[2] >= lit[1], "and light it violet, not white or green");
        assertTrue(RiftStormPalette.skyLight(0.5F, 1.0F, 1.0F) > RiftStormPalette.skyLight(0.5F, 1.0F, 0.0F),
                "a flash must light the ground as well");
    }

    @Test
    void everyChannelStaysInsideTheColourCube() {
        float[][] inputs = {NOON_SKY, NIGHT_SKY, NOON_CLOUD, {1.0F, 0.0F, 0.0F}, {0.0F, 1.0F, 1.0F}};
        for (float[] input : inputs) {
            for (float storm = -0.5F; storm <= 1.5F; storm += 0.25F) {
                for (float flash = -0.5F; flash <= 1.5F; flash += 0.5F) {
                    for (float[] out : new float[][] {
                            sky(input, storm, flash),
                            RiftStormPalette.clouds(input[0], input[1], input[2], storm, flash),
                            RiftStormPalette.fog(input[0], input[1], input[2], storm, flash)}) {
                        for (float channel : out) {
                            assertTrue(channel >= 0.0F && channel <= 1.0F,
                                    "channel " + channel + " escaped at storm " + storm + ", flash " + flash);
                        }
                    }
                    float light = RiftStormPalette.skyLight(input[0], storm, flash);
                    assertTrue(light >= 0.0F && light <= 1.0F, "sky light " + light + " escaped");
                }
            }
        }
    }

    @Test
    void aStormDimsTheDaylight() {
        assertTrue(RiftStormPalette.skyLight(1.0F, 1.0F, 0.0F) < 0.7F);
        assertTrue(RiftStormPalette.skyLight(1.0F, 1.0F, 0.0F) > 0.5F,
                "dimmer than day, not as dark as night - mobs are the server's business, not the sky's");
    }

    @Test
    void theSunIsVeiledButNotErased() {
        float shroud = RiftStormPalette.shroud(0.0F, 1.0F);
        assertTrue(shroud > 0.5F && shroud < 1.0F, "a smudge of sun should show through, got " + shroud);
        assertEquals(1.0F, RiftStormPalette.shroud(1.0F, 1.0F), EPSILON,
                "rain already hiding the sun must not be undone by a storm");
    }

    @Test
    void vanillaRainCrossFadesOutAsTheStormFadesIn() {
        assertEquals(1.0F, RiftStormPalette.vanillaRain(1.0F, 0.0F), EPSILON);
        assertEquals(0.5F, RiftStormPalette.vanillaRain(1.0F, 0.5F), EPSILON);
        assertEquals(0.0F, RiftStormPalette.vanillaRain(1.0F, 1.0F), EPSILON,
                "at full strength none of vanilla's rain may still be falling alongside the storm's");
    }
}
