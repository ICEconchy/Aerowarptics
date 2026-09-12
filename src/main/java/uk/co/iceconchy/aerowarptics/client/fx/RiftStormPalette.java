package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * The colours of a sky with a Rift Storm in it.
 *
 * <p>Deliberately free of Minecraft, like {@code AWAnim} and {@code AWDraw}: plain floats in, plain
 * floats out, so what the storm does to a colour can be tested without a game. {@link RiftStormSky}
 * hands it vanilla's own sky, cloud and fog colours, and hands back what this returns.
 *
 * <h2>The shape of it</h2>
 * Vanilla's thunderstorm darkens by blending each colour toward a grey worked out from how bright the
 * colour already was. This does the same, toward a <em>violet</em> worked out from how bright it was,
 * with a small floor under it. That floor is the difference between a dark sky and a black one: at
 * night vanilla's sky is black, and a storm sky with nothing added to black is indistinguishable from
 * no storm at all. The floor gives the night a faint bruised glow, which is the rift showing through.
 *
 * <p>Blue is weighted heaviest and green lightest in every target, so whatever the input the result
 * reads as violet rather than grey: {@code blue > red > green}.
 */
public final class RiftStormPalette {

    /** How far toward the storm colour each surface goes at full strength. The sky is left a trace of itself. */
    static final float SKY_REACH = 0.85F;
    static final float CLOUD_REACH = 0.90F;
    static final float FOG_REACH = 0.60F;

    /** The colour a flash lights things toward: rift-fire violet, not vanilla's pale blue. */
    static final float FLASH_R = 0.78F;
    static final float FLASH_G = 0.60F;
    static final float FLASH_B = 1.00F;

    /** How much of the world's sky light a full storm takes away - a little more than vanilla's rain does. */
    static final float LIGHT_LOSS = 0.35F;

    /** How much of the sun, moon and stars a full storm hides. Not all: a smudge of sun through a storm is the point. */
    static final float SHROUD = 0.85F;

    private RiftStormPalette() {
    }

    /** Vanilla's own luminance weights, from {@code ClientLevel.getSkyColor}. */
    static float luma(float r, float g, float b) {
        return r * 0.3F + g * 0.59F + b * 0.11F;
    }

    /**
     * The sky dome's colour.
     *
     * @param storm how much storm there is, {@code 0..1}
     * @param flash how lit the sky is by a bolt, {@code 0..1}
     */
    public static float[] sky(float r, float g, float b, float storm, float flash) {
        float l = luma(r, g, b);
        return blend(r, g, b,
                l * 0.28F + 0.05F, l * 0.18F + 0.02F, l * 0.46F + 0.10F,
                SKY_REACH * storm, flash * 0.5F);
    }

    /** The clouds: heavier toward the storm than the sky is, so they read as storm cloud rather than haze. */
    public static float[] clouds(float r, float g, float b, float storm, float flash) {
        float l = luma(r, g, b);
        return blend(r, g, b,
                l * 0.36F + 0.05F, l * 0.28F + 0.03F, l * 0.46F + 0.08F,
                CLOUD_REACH * storm, flash * 0.6F);
    }

    /**
     * The fog, which is also what the sky fades into at the horizon.
     *
     * <p>Lighter-handed than the sky, because the fog colour is derived from the sky colour already -
     * so it has had some of the storm once - and because fog is the one of these that touches the
     * blocks. Only its colour is changed, never its distance: a storm that pulled the fog in would be
     * a storm that shortened everybody's view, which nobody would thank it for.
     */
    public static float[] fog(float r, float g, float b, float storm, float flash) {
        float l = luma(r, g, b);
        return blend(r, g, b,
                l * 0.30F + 0.04F, l * 0.22F + 0.02F, l * 0.40F + 0.07F,
                FOG_REACH * storm, flash * 0.3F);
    }

    /**
     * How bright the sky light is, as {@code ClientLevel.getSkyDarken} reports it: {@code 0} is dark,
     * {@code 1} is noon. A storm takes some away; a flash gives it back for an instant, which is what
     * makes a strike light up the ground rather than only the sky.
     */
    public static float skyLight(float light, float storm, float flash) {
        float dimmed = light * (1.0F - LIGHT_LOSS * clamp01(storm));
        return clamp01(dimmed + (1.0F - dimmed) * clamp01(flash) * 0.6F);
    }

    /**
     * The rain level the sky is drawn as having, for the sun, moon and stars only: vanilla fades them
     * out by that figure, so passing it a storm's strength fades them out by the storm too.
     */
    public static float shroud(float rainLevel, float storm) {
        return Math.max(rainLevel, SHROUD * clamp01(storm));
    }

    /**
     * How much of vanilla's own rain and snow is still drawn under a storm.
     *
     * <p>The storm's rain replaces it rather than falling alongside it - pale blue and violet drops in
     * the same air read as two weathers at once. Faded out as the storm's own rain fades in, so the
     * change is a cross-fade rather than a switch.
     */
    public static float vanillaRain(float rainLevel, float storm) {
        return rainLevel * (1.0F - clamp01(storm));
    }

    private static float[] blend(float r, float g, float b, float tr, float tg, float tb, float reach, float lit) {
        float k = clamp01(reach);
        float nr = r + (tr - r) * k;
        float ng = g + (tg - g) * k;
        float nb = b + (tb - b) * k;
        float f = clamp01(lit);
        return new float[] {
                clamp01(nr + (FLASH_R - nr) * f),
                clamp01(ng + (FLASH_G - ng) * f),
                clamp01(nb + (FLASH_B - nb) * f)};
    }

    static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }
}
