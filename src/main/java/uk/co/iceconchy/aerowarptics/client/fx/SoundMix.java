package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * How loud one of the mod's sounds is let out, whatever it was asked for.
 *
 * <p>A sound's volume is two things written as one number. Up to one it is loudness; past one it is no
 * louder - the engine clamps it - and only carries further, the distance it fades over multiplied by it.
 * So a sound asked for at three and merely scaled down is every bit as loud beside the machine and heard
 * a third as far, which is the opposite of what a player turning the mod down wants. The two are taken
 * apart here: {@link #gain} is the loudness, trimmed, and {@link #reach} hands the carry back as distance.
 *
 * <p>Free of Minecraft, so a test can hold the arithmetic; {@link TrimmedSound} is what applies it.
 */
public final class SoundMix {

    /** The mod's namespace, as a sound's location carries it. */
    private static final String NAMESPACE = "aerowarptics";

    /**
     * What every sound is turned down by before the player's own {@code effectVolume}. The mod's sounds
     * were written at or near full volume and several are played at once - a warp's exit is heard from the
     * drive, the rift and the corridor together - which players found too loud. A half here, with the
     * setting running to two, leaves the old loudness within a player's reach.
     */
    public static final float TRIM = 0.5F;

    private SoundMix() {
    }

    /** Whether a sound is one of the mod's own, and so this mix's to turn down. */
    public static boolean covers(String namespace) {
        return NAMESPACE.equals(namespace);
    }

    /** What a loop that sets its own volume each tick multiplies it by. */
    public static float level(double setting) {
        if (!(setting > 0.0D)) {
            return 0.0F;
        }
        return TRIM * (float) setting;
    }

    /**
     * How loud a sound asked for at {@code asked} is played.
     *
     * @param asked   the volume its caller gave it, which past one is reach and not loudness
     * @param setting the player's {@code effectVolume}
     */
    public static float gain(float asked, double setting) {
        if (!(asked > 0.0F)) {
            return 0.0F;
        }
        return Math.min(1.0F, Math.min(asked, 1.0F) * level(setting));
    }

    /**
     * The distance a sound asked for at {@code asked} fades over, now that its volume no longer says.
     *
     * @param attenuation the distance its definition gives it
     * @param asked       the volume its caller gave it
     */
    public static int reach(int attenuation, float asked) {
        if (!(asked > 1.0F)) {
            return attenuation;
        }
        return Math.round(attenuation * asked);
    }
}
