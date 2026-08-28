package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;

/**
 * A spark of lightning discharging off a rift: a jagged line that flashes into existence and is gone,
 * over and over, at points scattered around the torn rim and down the length of the corridor behind
 * it.
 *
 * <p>Deterministic and seeded, for the same reason {@link RiftShatter} and {@link RiftDebris} are:
 * every viewer works the same flashes out from the same rift seed and the same clock, so two players
 * watching one aperture see the same lightning without a byte being sent about it.
 *
 * <h2>A fixed field of emitters, each on its own clock</h2>
 * Rather than rolling dice every tick - which would have one client's roll drift from another's the
 * moment a frame is dropped - each rift gets a small fixed number of emitters, generated once from its
 * seed exactly like {@link RiftDebris}'s motes. Each has its own period and phase, so "random sparking"
 * is really a handful of independent clocks landing on different beats: read the whole field at any
 * tick and some are dark and one or two are mid-flash, which is what makes it read as scattered rather
 * than as a synchronised pulse.
 *
 * <h2>The path itself</h2>
 * Not drawn here - {@link RiftEffectManager} places a bolt's points against the aperture's own plane
 * or the corridor's own bore, because the two are different shapes and only the renderer knows which
 * is which. What this hands back for each point is a single jittered value in {@code [-1, 1]}, worked
 * out fresh from the emitter, which flash it is and which point of the path - so a bolt is a different
 * shape every time it fires without needing to remember the last one, and the renderer is free to
 * shape that jitter however each kind of bolt needs to: tapering to nothing at a free end, or pinched
 * to nothing at both ends where a bolt is anchored on both sides.
 */
public final class RiftLightning {

    /** Bolts scattered around the torn rim, jumping outward from it. */
    public static final int APERTURE_BOLTS = 5;
    /** Bolts scattered down the length of the corridor, arcing across the bore. */
    public static final int CORRIDOR_BOLTS = 6;
    /**
     * Bolts that reach for the ground below the rift, if there is any within range.
     *
     * <p>Fewer than the other two fields, deliberately. A strike that actually finds the ground and
     * marks it is the rarest and most dramatic of the three kinds, and a field as dense as the rim's
     * own sparks would make every rift look like it was under artillery.
     */
    public static final int GROUND_BOLTS = 2;

    /** Points in a bolt's path, root to tip. Few enough to read as a discharge, not a scribble. */
    public static final int SEGMENTS = 5;

    /** Ticks a flash is visible once it fires. */
    public static final float FLASH_LIFE = 5.0F;

    private static final float PERIOD_MIN = 26.0F;
    private static final float PERIOD_MAX = 74.0F;

    private RiftLightning() {
    }

    /**
     * One emitter: where it sits, and the clock it fires on.
     *
     * @param anchor angle around the rim or the bore this emitter sits at
     * @param period ticks between the starts of two flashes
     * @param phase  offset into that period, so emitters spread across the same clock fire at
     *               different moments rather than all at once
     */
    public record Emitter(float anchor, float period, float phase) {
    }

    public static Emitter[] apertureField(int seed) {
        return field(seed, APERTURE_BOLTS, 40_000);
    }

    public static Emitter[] corridorField(int seed) {
        return field(seed, CORRIDOR_BOLTS, 50_000);
    }

    public static Emitter[] groundField(int seed) {
        return field(seed, GROUND_BOLTS, 60_000);
    }

    private static Emitter[] field(int seed, int count, int base) {
        Emitter[] emitters = new Emitter[count];
        for (int index = 0; index < count; index++) {
            int n = base + index * 9;
            float anchor = RiftShatter.noise(seed, n) * (float) (Math.PI * 2.0D);
            float period = PERIOD_MIN + (PERIOD_MAX - PERIOD_MIN) * RiftShatter.noise(seed, n + 1);
            float phase = RiftShatter.noise(seed, n + 2) * period;
            emitters[index] = new Emitter(anchor, period, phase);
        }
        return emitters;
    }

    /**
     * How far into a flash an emitter is at a given time, or a negative number while it is dark.
     *
     * @return {@code 0} at the instant it fires, approaching {@code 1} as it fades, negative while
     *         waiting for its next one
     */
    public static float flash(Emitter emitter, float time) {
        float since = Mth.positiveModulo(time - emitter.phase(), emitter.period());
        return since < FLASH_LIFE ? since / FLASH_LIFE : -1.0F;
    }

    /**
     * Which flash cycle a time falls in, for seeding that flash's own path.
     *
     * <p>Two calls landing in the same cycle draw the same bolt; the moment the cycle rolls over to
     * the next one, a fresh path is drawn from the same emitter - this is the entire mechanism behind
     * a bolt looking different every time it fires.
     */
    public static int cycle(Emitter emitter, float time) {
        return Mth.floor((time - emitter.phase()) / emitter.period());
    }

    /**
     * How bright a flash reads at its own progress, {@code 0..1}: a hard snap to full and a fast
     * fade, the way an actual discharge is bright for an instant rather than dimming gracefully.
     */
    public static float brightness(float progress) {
        if (progress < 0.0F) {
            return 0.0F;
        }
        float fade = 1.0F - progress;
        return fade * fade;
    }

    /**
     * A repeatable jitter in {@code [-1, 1]} for one point of one bolt's path on one flash.
     *
     * <p>The three identifiers are folded into the seed rather than passed to {@link RiftShatter#noise}
     * as separate indices, because that call only takes one - mixing them here is what lets one
     * emitter's fifth flash and its sixth draw entirely different paths from the same underlying hash.
     */
    public static float jitter(int seed, int emitterIndex, int cycleNumber, int pointIndex) {
        int n = seed ^ (emitterIndex * 0x1F3D5B79) ^ (cycleNumber * 0x2545F491);
        return (RiftShatter.noise(n, pointIndex) - 0.5F) * 2.0F;
    }
}
