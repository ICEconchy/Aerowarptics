package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla's rain, recoloured for a Rift Storm.
 *
 * <p>The failures here all draw, and draw wrongly: rain that stays blue because the recolour only scaled it,
 * rain that has gone glaring lilac - which is the very thing this recolour was written to stop - or a mask
 * whose alpha was disturbed, which puts drops where vanilla had none or takes away the ones it had. The input
 * is vanilla's own drop colour, read off its {@code rain.png}, at the transparencies that file uses.
 */
class RiftRainTintTest {

    private static final int[] VANILLA_ALPHAS = {14, 32, 47, 80, 115, 152, 255};

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private static int vanilla(int alpha) {
        return RiftRainTint.recolour(RiftRainTint.VANILLA_R, RiftRainTint.VANILLA_G, RiftRainTint.VANILLA_B, alpha);
    }

    @Test
    void vanillasDropComesOutPurple() {
        int colour = vanilla(255);
        assertTrue(blue(colour) > green(colour) && red(colour) > green(colour),
                "violet has more red and blue in it than green: " + Integer.toHexString(colour));
        assertTrue(red(colour) >= blue(colour) * 0.55F,
                "enough red that it reads as purple, not as the blue it started: " + Integer.toHexString(colour));
    }

    @Test
    void theMaskIsLeftExactlyAsItWas() {
        for (int alpha : VANILLA_ALPHAS) {
            assertEquals(alpha, vanilla(alpha) >>> 24, "alpha " + alpha + " was changed");
        }
    }

    @Test
    void anEmptyPixelStaysEmpty() {
        assertEquals(0, RiftRainTint.recolour(RiftRainTint.VANILLA_R, RiftRainTint.VANILLA_G, RiftRainTint.VANILLA_B, 0));
        assertEquals(0, RiftRainTint.recolour(0, 0, 0, 0));
    }

    @Test
    void itIsNoBrighterThanVanillasRainToWithinReason() {
        int colour = vanilla(255);
        float before = RiftRainTint.luma(RiftRainTint.VANILLA_R, RiftRainTint.VANILLA_G, RiftRainTint.VANILLA_B);
        float after = RiftRainTint.luma(red(colour), green(colour), blue(colour));
        assertTrue(after > before * 0.6F && after < before * 1.4F,
                "the storm's rain should be about as bright as vanilla's, not glowing: " + after + " from " + before);
    }

    @Test
    void aLighterPacksRainStaysLighterAndNeverClips() {
        int vanilla = vanilla(255);
        int white = RiftRainTint.recolour(255, 255, 255, 255);
        assertTrue(RiftRainTint.luma(red(white), green(white), blue(white))
                        > RiftRainTint.luma(red(vanilla), green(vanilla), blue(vanilla)),
                "a resource pack's white rain should come out lighter than vanilla's blue");
        assertTrue(green(white) < 255 && blue(white) > green(white),
                "white rain should be paled toward lilac, not washed out to white: " + Integer.toHexString(white));
    }

    @Test
    void theColourGrowsSmoothlyWithLightness() {
        float previous = -1.0F;
        for (int grey = 0; grey <= 255; grey++) {
            int colour = RiftRainTint.recolour(grey, grey, grey, 255);
            float now = RiftRainTint.luma(red(colour), green(colour), blue(colour));
            assertTrue(now >= previous - 1.0F, "a lighter pixel came out darker at grey " + grey);
            assertTrue(previous < 0.0F || now - previous < 4.0F, "a jump in the ramp at grey " + grey);
            previous = now;
        }
    }
}
