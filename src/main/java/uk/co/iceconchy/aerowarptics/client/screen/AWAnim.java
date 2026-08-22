package uk.co.iceconchy.aerowarptics.client.screen;

/**
 * The small amount of motion these screens have.
 *
 * <p>Minecraft screens tick twenty times a second and render as fast as the machine can, so anything
 * that moves has to hold two values and be asked for the blend between them. {@link Eased} is that,
 * plus the one behaviour worth having: a value that chases its target instead of jumping to it, so a
 * bar that has just been told the charge is now eighty percent slides there.
 *
 * <p>Free of Minecraft, and therefore testable - which matters more than it sounds, because the
 * failure mode of an easing function is not a crash but a bar that never quite arrives, or one that
 * overshoots into a shape the eye reads as a glitch.
 */
public final class AWAnim {

    private AWAnim() {
    }

    /** Decelerating: fast at the start, settling at the end. The default for anything arriving. */
    public static float easeOut(float t) {
        float clamped = clamp(t);
        float remaining = 1.0F - clamped;
        return 1.0F - remaining * remaining * remaining;
    }

    /** Accelerating then decelerating. For something that travels rather than arrives. */
    public static float easeInOut(float t) {
        float clamped = clamp(t);
        return clamped < 0.5F
                ? 4.0F * clamped * clamped * clamped
                : 1.0F - (float) Math.pow(-2.0D * clamped + 2.0D, 3.0D) / 2.0F;
    }

    public static float clamp(float t) {
        return t < 0.0F ? 0.0F : Math.min(t, 1.0F);
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * A 0..1 sine over the given period, in ticks.
     *
     * <p>Used for anything that should read as "working" rather than "stuck": a charging bar, a
     * pending marker. Deliberately never reaches either end quickly, so it breathes rather than
     * blinks.
     */
    public static float pulse(float ticks, float period) {
        return (float) (0.5D + 0.5D * Math.sin(ticks / period * Math.PI * 2.0D));
    }

    /** A 0..1 ramp that repeats every {@code period} ticks. For sweeps and travelling highlights. */
    public static float sweep(float ticks, float period) {
        float phase = ticks % period;
        if (phase < 0.0F) {
            phase += period;
        }
        return phase / period;
    }

    /**
     * Blends two packed ARGB colours.
     *
     * <p>Channel by channel including alpha, so fading a colour to something transparent works as
     * well as fading between two solid ones.
     */
    public static int blend(int from, int to, float t) {
        float amount = clamp(t);
        int a = Math.round(lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, amount));
        int r = Math.round(lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, amount));
        int g = Math.round(lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, amount));
        int b = Math.round(lerp(from & 0xFF, to & 0xFF, amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** The same colour at a different opacity, as a fraction of its own. */
    public static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp(alpha));
        return (a << 24) | (argb & 0x00FF_FFFF);
    }

    /**
     * A value that chases a target.
     *
     * <p>Two samples are kept - where it was at the end of the last tick and where it is now - because
     * a screen renders between ticks and reading only the current value makes everything move in
     * twenty discrete steps.
     */
    public static final class Eased {

        /**
         * Fraction of the remaining distance covered each tick.
         *
         * <p>Exponential rather than linear, so a large change moves quickly and a small one settles.
         * A linear rate has to be tuned to the size of the change, and these values range from a
         * fraction to several thousand.
         */
        private final float rate;

        private float previous;
        private float current;
        private float target;

        public Eased(float rate, float initial) {
            this.rate = clamp(rate);
            this.previous = initial;
            this.current = initial;
            this.target = initial;
        }

        public Eased(float rate) {
            this(rate, 0.0F);
        }

        public void set(float value) {
            target = value;
        }

        /** Jumps straight there, for when a screen is handed a whole new subject to show. */
        public void snap(float value) {
            target = value;
            current = value;
            previous = value;
        }

        public void tick() {
            previous = current;
            current += (target - current) * rate;
            // Without this a chase never formally arrives, and a bar sits at 99.97% forever.
            if (Math.abs(target - current) < 1.0e-4F) {
                current = target;
            }
        }

        public float get(float partialTicks) {
            return lerp(previous, current, clamp(partialTicks));
        }

        public float target() {
            return target;
        }
    }
}
