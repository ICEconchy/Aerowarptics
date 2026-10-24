package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * What a Rift Storm does to the colour of a raindrop.
 *
 * <p>Deliberately free of Minecraft, like {@link RiftStormPalette}: {@link RiftRainTexture} hands it each
 * pixel of whatever rain texture the game is using and writes back what this returns, so the recolouring
 * can be tested without a game.
 *
 * <h2>Why a recolour, not a tint</h2>
 * Vanilla's rain is one colour - a mid blue, {@code (70, 103, 195)} - drawn at many transparencies: the
 * texture is really a mask, and the blue is only what shows through it. A vertex tint multiplies, and a
 * multiply can only take colour away, so there is no tint that turns that blue purple; the red simply is
 * not in it to bring out. The mask is kept exactly as it is, which keeps vanilla's drops - their shapes,
 * how many there are, how faint - and only the colour behind it is replaced.
 *
 * <p>It is replaced by brightness rather than by value, so a resource pack that draws its rain white, or
 * grey, or bright, gets violet rain just as bright as its own: a violet worked out from how light the pixel
 * was, deeper for a dark drop and paler toward lilac for a light one. Vanilla's own blue comes out a
 * muted violet no brighter than it went in - rain that happens to be purple, not rain that glows.
 */
public final class RiftRainTint {

    /** Vanilla's rain colour, and what a pixel's lightness is measured against. */
    static final int VANILLA_R = 70;
    static final int VANILLA_G = 103;
    static final int VANILLA_B = 195;

    /** The violet vanilla's own blue becomes. */
    static final float DEEP_R = 140.0F;
    static final float DEEP_G = 88.0F;
    static final float DEEP_B = 215.0F;
    /** The lilac a much lighter drop is drawn toward. */
    static final float PALE_R = 222.0F;
    static final float PALE_G = 200.0F;
    static final float PALE_B = 255.0F;

    private RiftRainTint() {
    }

    /** Vanilla's luminance weights, as {@link RiftStormPalette#luma} uses. */
    static float luma(float r, float g, float b) {
        return r * 0.3F + g * 0.59F + b * 0.11F;
    }

    /**
     * One pixel, recoloured.
     *
     * @return the new colour as {@code 0xAARRGGBB}, with the alpha handed in left exactly as it was
     */
    public static int recolour(int r, int g, int b, int a) {
        if (a == 0) {
            return 0;
        }
        // How light the pixel is against vanilla's own drop: one for vanilla, more for a lighter pack.
        float light = luma(r, g, b) / luma(VANILLA_R, VANILLA_G, VANILLA_B);
        float nr;
        float ng;
        float nb;
        if (light <= 1.0F) {
            nr = DEEP_R * light;
            ng = DEEP_G * light;
            nb = DEEP_B * light;
        } else {
            // Past vanilla's lightness the violet is paled rather than scaled, so it never clips to white.
            float t = Math.min(1.0F, (light - 1.0F) / 1.5F);
            nr = DEEP_R + (PALE_R - DEEP_R) * t;
            ng = DEEP_G + (PALE_G - DEEP_G) * t;
            nb = DEEP_B + (PALE_B - DEEP_B) * t;
        }
        return (a & 0xFF) << 24 | channel(nr) << 16 | channel(ng) << 8 | channel(nb);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
