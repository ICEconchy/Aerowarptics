package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * The numbers behind how a flotilla looks and sounds: what each drive hums at, how a hum slides onto
 * the flagship's, how ragged a tether is, which way the station HUD points.
 *
 * <p>Free of Minecraft, like {@code RiftLightning} and {@code ThemeLook}, so that the one rule the whole
 * sequence rests on - a harmonised fleet is heard as one note before it is seen as a row of green - is
 * checked rather than trusted to an ear.
 */
public final class FlotillaVisuals {

    private FlotillaVisuals() {
    }

    /**
     * What a drive of each tier hums at before it has harmonised: Mk I, Mk II, Mk III, Singularity,
     * Creative. Deliberately not a chord with each other, so a fleet that has not found its note sounds
     * unsettled, and deliberately spread, so it is heard which ship has not found it yet.
     */
    private static final float[] HUM_PITCH = {0.80F, 0.93F, 1.07F, 0.71F, 1.19F};

    /** The chord a harmonised fleet resolves into, as multiples of its root: root, major third, fifth. */
    private static final float[] CHORD = {1.0F, 1.2599F, 1.4983F};

    public static float humPitch(int tier) {
        return HUM_PITCH[Math.max(0, Math.min(HUM_PITCH.length - 1, tier))];
    }

    /**
     * An escort's hum, sliding onto the flagship's as it harmonises. Exactly the flagship's once it has;
     * at no harmony at all, its own tier's note with a slow wobble on it, so an unharmonised fleet sounds
     * like machines that have not agreed on anything.
     *
     * @param harmony 0 to 1
     * @param ticks   for the wobble
     */
    public static float slide(int ownTier, int flagshipTier, float harmony, float ticks) {
        float h = clamp(harmony);
        float own = humPitch(ownTier);
        float target = humPitch(flagshipTier);
        float wobble = (1.0F - h) * 0.03F * (float) Math.sin(ticks * 0.21F + ownTier);
        return own + (target - own) * ease(h) + wobble;
    }

    /** The resolving chord's pitches, from the flagship's own note. */
    public static float[] chord(int flagshipTier) {
        float root = humPitch(flagshipTier);
        float[] out = new float[CHORD.length];
        for (int i = 0; i < CHORD.length; i++) {
            out[i] = Math.min(2.0F, root * CHORD[i]);
        }
        return out;
    }

    /**
     * How bright a tether is drawn this frame, 0 to 1: flickering while its drive is still finding the
     * note, steady once it has. Never out altogether, so a tether does not blink out of existence and look
     * like a ship that has dropped out of the muster.
     *
     * @param seed anything stable per tether, so a fleet's tethers do not flicker in step
     */
    public static float tetherAlpha(float harmony, float ticks, int seed) {
        float h = clamp(harmony);
        double noise = Math.sin(ticks * 1.7D + seed * 0.37D) * Math.sin(ticks * 0.53D + seed * 1.3D);
        float flicker = (float) (0.5D + 0.5D * noise);
        return 0.35F + 0.65F * (h + (1.0F - h) * flicker * flicker);
    }

    /** How far a tether's middle is thrown off the straight, in blocks: ragged until it has harmonised. */
    public static float tetherSag(float harmony) {
        return (1.0F - clamp(harmony)) * 0.9F;
    }

    /**
     * Which way the station HUD's arrow points, in radians clockwise from straight up the screen.
     *
     * <p>From where the ship is to where its slot is: the negative of the drift, in the formation's own
     * frame (starboard, ahead), turned into the player's by the difference between which way they are
     * looking and which way the flagship's bow is. So "straight up" is always "go the way you are facing".
     *
     * @param dRight   the ship's drift to starboard of its slot
     * @param dAhead   the ship's drift ahead of its slot
     * @param lookYaw  the player's yaw, in degrees, as Minecraft gives it
     * @param bowYaw   the flagship's bow, as a yaw in the same convention
     */
    public static double arrow(double dRight, double dAhead, double lookYaw, double bowYaw) {
        double toSlot = Math.atan2(-dRight, -dAhead);
        return wrap(toSlot + Math.toRadians(bowYaw - lookYaw));
    }

    /** The yaw, in Minecraft's convention, that looks along a horizontal direction. */
    public static double yawOf(double x, double z) {
        return Math.toDegrees(Math.atan2(-x, z));
    }

    /**
     * How much camera shake the countdown adds each tick at a point through it. Builds with the square of
     * the progress, so the last second is where it is felt; a scale of 0 is still.
     */
    public static float countdownShake(float progress, float scale) {
        float p = clamp(progress);
        return Math.max(0.0F, scale) * 0.07F * p * p;
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }

    private static double wrap(double radians) {
        double full = Math.PI * 2.0D;
        double r = radians % full;
        if (r > Math.PI) {
            r -= full;
        } else if (r < -Math.PI) {
            r += full;
        }
        return r;
    }
}
