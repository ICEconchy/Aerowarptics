package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * The shapes of the things a Rift Storm draws in its sky, as arithmetic.
 *
 * <p>Deliberately free of Minecraft, like {@link RiftStormPalette}: plain floats in, plain floats out, so how
 * a tear opens, how the vortex winds and how a flash stutters can be tested without a game. The classes that
 * draw them - {@link RiftStormTears}, {@link RiftStormVortex}, {@link RiftStormStreaks} and
 * {@link RiftStormCloudFlashes} - decide where and when; this decides how much.
 *
 * <p>Every function here is continuous and comes to rest at nothing at its ends. A shape that jumped would
 * pop on screen - a tear snapping shut, a streak's tail cut off square - and a shape that did not come to
 * nothing would leave something hanging in the sky after the storm had moved on. {@code RiftStormShapesTest}
 * samples each finely enough to catch either.
 */
public final class RiftStormShapes {

    /** Share of a tear's life spent as a bare seam before it starts to open. */
    static final float TEAR_SEAM = 0.12F;
    /** Share of its life by which a tear is fully open. */
    static final float TEAR_OPEN = 0.32F;
    /** Share of its life at which it starts to close again. */
    static final float TEAR_CLOSE = 0.76F;
    /** Share of its life by which it has healed back to a seam; the seam fades over what is left. */
    static final float TEAR_HEAL = 0.92F;
    /** Share of its life over which the seam first brightens. */
    static final float SEAM_IN = 0.08F;

    /** How many arms the vortex has, and how tightly they wind toward its eye. */
    static final float VORTEX_ARMS = 3.0F;
    static final float VORTEX_TWIST = 2.6F;
    /** Radii, as a share of the vortex's, of its clear eye and of where its cloud starts to thin. */
    static final float VORTEX_EYE = 0.06F;
    static final float VORTEX_BODY = 0.22F;
    static final float VORTEX_THIN = 0.55F;

    private RiftStormShapes() {
    }

    // ------------------------------------------------------------------ tears

    /**
     * How far open a tear is, {@code 0..1}, a share {@code progress} of the way through its life: a seam, then
     * a slow parting, held open, then a slow healing back to a seam.
     */
    public static float tearOpening(float progress) {
        if (progress <= TEAR_SEAM || progress >= TEAR_HEAL) {
            return 0.0F;
        }
        if (progress < TEAR_OPEN) {
            return smooth((progress - TEAR_SEAM) / (TEAR_OPEN - TEAR_SEAM));
        }
        if (progress > TEAR_CLOSE) {
            return smooth((TEAR_HEAL - progress) / (TEAR_HEAL - TEAR_CLOSE));
        }
        return 1.0F;
    }

    /**
     * How bright a tear's edges are, {@code 0..1}: brightening as the seam first appears, full while there is a
     * tear, fading once it has healed. Nothing at either end of its life.
     */
    public static float tearSeam(float progress) {
        if (progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        if (progress < SEAM_IN) {
            return smooth(progress / SEAM_IN);
        }
        if (progress > TEAR_HEAL) {
            return smooth((1.0F - progress) / (1.0F - TEAR_HEAL));
        }
        return 1.0F;
    }

    /**
     * How wide a tear is at a share {@code along} of its length, as a share of its widest: nothing at either
     * end, so it is a slit in the sky rather than a box cut out of it, and fullest a little either side of
     * the middle rather than in a point there.
     */
    public static float tearWidth(float along) {
        if (along <= 0.0F || along >= 1.0F) {
            return 0.0F;
        }
        return (float) Math.pow(Math.sin(Math.PI * along), 0.7D);
    }

    // ----------------------------------------------------------------- vortex

    /**
     * How thick the vortex's cloud is at a point, {@code 0..1}.
     *
     * @param radius from the eye, as a share of the vortex's radius: nothing at or beyond one
     * @param angle  round the eye, in radians
     * @param turn   how far the whole vortex has turned, in radians
     */
    public static float vortexShade(float radius, float angle, float turn) {
        if (radius < 0.0F || radius >= 1.0F) {
            return 0.0F;
        }
        // A logarithmic spiral: the arms wind tighter the nearer the eye they come, which is what makes a
        // ring of dark bands read as something turning inward rather than as a pinwheel.
        double phase = VORTEX_ARMS * (angle - turn) + VORTEX_TWIST * Math.log(radius + 0.05D);
        float arm = (float) (0.5D + 0.5D * Math.cos(phase));
        arm *= arm;
        float body = smooth((radius - VORTEX_EYE) / (VORTEX_BODY - VORTEX_EYE));
        float thinning = 1.0F - smooth((radius - VORTEX_THIN) / (1.0F - VORTEX_THIN));
        // A haze between the arms, so the vortex is a disc of cloud with darker arms in it, not bare arms.
        return clamp01((0.25F + 0.75F * arm) * body * thinning);
    }

    /** How bright the vortex's eye glows at a radius from its middle, as a share of the eye's own: nothing at the edge. */
    public static float vortexEye(float radius) {
        if (radius < 0.0F || radius >= 1.0F) {
            return 0.0F;
        }
        float fall = 1.0F - radius;
        return fall * fall;
    }

    // ---------------------------------------------------------------- streaks

    /** How bright a streak's trail is a share {@code behind} of the way from its head to its tail. */
    public static float streakTrail(float behind) {
        if (behind <= 0.0F) {
            return 1.0F;
        }
        if (behind >= 1.0F) {
            return 0.0F;
        }
        float left = 1.0F - behind;
        return left * left;
    }

    /** How bright a whole streak is through its life: a quick kindling, then burning out over its last third. */
    public static float streakLife(float progress) {
        if (progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        if (progress < 0.1F) {
            return smooth(progress / 0.1F);
        }
        if (progress > 0.66F) {
            return smooth((1.0F - progress) / 0.34F);
        }
        return 1.0F;
    }

    // ---------------------------------------------------------------- flashes

    /**
     * A cloud flash's brightness through its life: a few stuttering pulses, each weaker than the last, and
     * nothing at the start, between pulses, or at the end.
     *
     * @param pulses how many times it flickers, at least one
     */
    public static float sheetFlicker(float progress, int pulses) {
        if (progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        int count = Math.max(1, pulses);
        float wave = progress * count;
        float within = wave - (float) Math.floor(wave);
        float pulse = (float) Math.sin(Math.PI * within);
        return pulse * (0.35F + 0.65F * (1.0F - progress));
    }

    // ----------------------------------------------------------------- shared

    /** Smoothstep on a share clamped to {@code 0..1}: eases in and out, so nothing starts or stops with a jolt. */
    static float smooth(float t) {
        float x = clamp01(t);
        return x * x * (3.0F - 2.0F * x);
    }

    static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }
}
