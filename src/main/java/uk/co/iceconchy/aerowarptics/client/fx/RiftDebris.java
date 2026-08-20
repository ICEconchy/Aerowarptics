package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;

/**
 * What is loose inside a rift's throat.
 *
 * <p>A bore with nothing in it is a lit pipe. What makes it a place is having things in it to pass -
 * broken glass still turning over from the aperture that tore open to let the ship in, heavier
 * fragments of somewhere else, and the occasional streak of light running the other way.
 *
 * <h2>Why almost none of it moves</h2>
 * The hull covers the length of the bore in about five seconds, which is the one motion cue in the
 * whole corridor that is real rather than painted. Debris moving fast <em>as well</em> would cross the
 * view inside a frame and simply not be seen - that is exactly why the corridor was a screen effect
 * before it was a place. So the field is fixed in the bore and the ship supplies the speed. A slow
 * drift and a tumble keep it from being a sculpture; a handful of pieces are given real speed and
 * drawn as streaks, because a few things tearing past is energy and everything tearing past is a blur.
 *
 * <p>Positions wrap, so a bore stays populated for as long as it is open without anything being
 * spawned or thrown away. Deterministic from the rift's own seed, for the same reason
 * {@link RiftShatter} is: nothing about it needs to be sent.
 */
public final class RiftDebris {

    /** Pieces loose in a bore. */
    public static final int MOTES = 96;

    /**
     * Closest to the axis a piece may sit, as a fraction of the bore's radius.
     *
     * <p>The hull flies down the middle. Debris in there would be inside the ship rather than passing
     * it, which reads as a rendering fault rather than as scenery.
     */
    public static final float INNER = 0.34F;

    /** Furthest out a piece may sit. Short of the wall, so nothing is embedded in it. */
    public static final float OUTER = 0.94F;

    /** Fraction of the field given real speed and drawn as streaks. */
    private static final float STREAK_SHARE = 0.16F;

    /** Fraction of the bore's length a drifting piece covers per tick. */
    private static final float DRIFT = 0.0008F;

    /** The same, for one of the fast ones. Against the ship, so they close rather than dawdle. */
    private static final float STREAK_DRIFT = 0.030F;

    private RiftDebris() {
    }

    /**
     * One piece of debris, in the bore's own coordinates.
     *
     * @param angle  where it sits around the bore
     * @param radius how far out, as a fraction of the bore's radius
     * @param along  where it starts down the bore, as a fraction of its depth
     * @param size   how big, as a fraction of the bore's radius
     * @param spin   radians per tick it tumbles through; ignored for a streak
     * @param axis   which way its tumble axis lies
     * @param drift  fraction of the bore's depth it covers per tick, signed
     * @param glass  whether it is a shard off the aperture rather than heavier wreckage
     * @param streak whether it is moving fast enough to be drawn as a line rather than a piece
     */
    public record Mote(float angle, float radius, float along, float size,
                       float spin, float axis, float drift, boolean glass, boolean streak) {
    }

    /** The whole field for a bore. */
    public static Mote[] field(int seed) {
        Mote[] motes = new Mote[MOTES];
        for (int index = 0; index < MOTES; index++) {
            int n = 20_000 + index * 11;
            boolean streak = RiftShatter.noise(seed, n) < STREAK_SHARE;
            boolean glass = !streak && RiftShatter.noise(seed, n + 1) < 0.55F;

            float radius = INNER + (OUTER - INNER) * RiftShatter.noise(seed, n + 2);
            float roll = RiftShatter.noise(seed, n + 3);
            // Glass comes off a pane and is small; wreckage is whatever else the fold has in it.
            float size = streak ? 0.008F + 0.010F * roll
                    : glass ? 0.014F + 0.026F * roll
                    : 0.020F + 0.040F * roll;
            float drift = streak
                    // Against the ship, so a streak closes with it rather than dawdling alongside.
                    ? -STREAK_DRIFT * (0.6F + 0.8F * RiftShatter.noise(seed, n + 4))
                    : DRIFT * (RiftShatter.noise(seed, n + 4) - 0.5F) * 2.0F;

            motes[index] = new Mote(
                    RiftShatter.noise(seed, n + 5) * (float) (Math.PI * 2.0D),
                    radius,
                    RiftShatter.noise(seed, n + 6),
                    size,
                    (RiftShatter.noise(seed, n + 7) - 0.5F) * 0.16F,
                    RiftShatter.noise(seed, n + 8) * (float) (Math.PI * 2.0D),
                    drift,
                    glass,
                    streak);
        }
        return motes;
    }

    /**
     * Where a piece is down the bore now, as a fraction of its depth.
     *
     * <p>Wrapped rather than clamped. A piece that ran off the end and stopped would leave the bore
     * emptying out behind the ship over a long corridor, which is the opposite of what a corridor
     * should do as you get further into it.
     */
    public static float along(Mote mote, float time) {
        float raw = mote.along() + mote.drift() * time;
        float wrapped = raw - Mth.floor(raw);
        // Mth.floor of a negative gets this back into range, but guard the boundary anyway: a value
        // of exactly one would put a piece inside the cone where the bore has already closed.
        return wrapped >= 1.0F ? 0.0F : wrapped;
    }

    /**
     * How long a streak is drawn, as a fraction of the bore's depth.
     *
     * <p>Proportional to how fast it is going, which is the only honest way to draw one: a line is a
     * record of where something was, so a faster piece leaves a longer one.
     */
    public static float streakLength(Mote mote) {
        return Math.min(0.22F, Math.abs(mote.drift()) * 4.5F);
    }
}
