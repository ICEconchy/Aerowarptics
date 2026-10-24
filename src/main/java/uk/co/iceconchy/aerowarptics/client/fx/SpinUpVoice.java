package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * What a Rift Drive winding up sounds like, as numbers: how high and how loud for how far wound.
 *
 * <p>The spin-up was the one stretch of a warp with nothing to hear after its first clunk. A drive
 * reaching across the map can wind for half a minute, and for all of it the only sign anything was
 * happening was a bar on a screen nobody had open. So it has a voice that climbs as the spin builds - the
 * sound of a machine being turned harder - which also says, without a screen, how nearly ready it is and
 * how hard it is being driven: a starved shaft climbs slowly.
 *
 * <p>Free of Minecraft so the curve can be held to the range a sound engine will actually play.
 */
public final class SpinUpVoice {

    /** The lowest and highest pitch the game's sound engine plays; anything past them is clamped there. */
    public static final float PITCH_FLOOR = 0.5F;
    public static final float PITCH_CEILING = 2.0F;

    /** Where the wind-up starts and where it tops out. Short of the ceiling, to leave the shimmer room. */
    private static final float PITCH_START = 0.55F;
    private static final float PITCH_FULL = 1.85F;

    /** How far the pitch wavers at full spin, as a drive holds there waiting for its rift. */
    private static final float SHIMMER = 0.04F;

    private static final float VOLUME_START = 0.3F;
    private static final float VOLUME_FULL = 1.0F;

    /** Share of the way to its target a voice moves each tick, coming in and going out. */
    public static final float EASE = 0.2F;

    private SpinUpVoice() {
    }

    /**
     * The pitch for a drive this far wound.
     *
     * <p>Squared, so the climb is slow at first and steepens: the first half of a wind-up is a machine
     * getting going, and the second is the one that should make a crew look up.
     *
     * @param progress 0..1 through the spin-up
     * @param ticks    a clock, for the waver a fully wound drive holds with
     */
    public static float pitch(float progress, float ticks) {
        float wound = clamp01(progress);
        float base = PITCH_START + (PITCH_FULL - PITCH_START) * wound * wound;
        // Only once it is fully wound: a drive held at full spin - an escort waiting on its flagship, a
        // landing still loading - should sound held, not stuck.
        float waver = wound >= 1.0F ? SHIMMER * (float) Math.sin(ticks * 0.6F) : 0.0F;
        return Math.max(PITCH_FLOOR, Math.min(PITCH_CEILING, base + waver));
    }

    /** How loud, before the player's own volume setting: it swells as it winds. */
    public static float volume(float progress) {
        return VOLUME_START + (VOLUME_FULL - VOLUME_START) * clamp01(progress);
    }

    private static float clamp01(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }
}
