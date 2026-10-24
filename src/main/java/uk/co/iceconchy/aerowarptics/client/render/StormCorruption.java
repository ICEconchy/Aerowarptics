package uk.co.iceconchy.aerowarptics.client.render;

import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/**
 * How far a Rift Storm has eaten into a drive, as arithmetic.
 *
 * <p>Kept free of anything that needs a game, so the thresholds {@link StormCorruptionLayer} draws with can be
 * tested directly. The layer draws the drive twice over itself with vanilla's dissolve shader - the one the
 * ender dragon's death uses - which keeps a texel only where the texture's alpha is at least the vertex
 * colour's alpha. The void sheets' alpha is the order the void takes each texel in, deepest first (see
 * {@code tools/rift_drive_void.py}), so one number - the threshold - says how far the patches have grown.
 *
 * <p>Two thresholds, one per sheet. The rim's is always the lower, so it shows a band further out than the
 * void does: a patch is edged in violet light, and at the very start of a storm there is only that light,
 * specks of it where the void is about to break through, before any dark opens inside them.
 */
public final class StormCorruption {

    /** The rim's threshold at the height of a storm: the thinnest edge of any patch it ever reaches. */
    static final float RIM_FLOOR = 0.04F;
    /** How much deeper the void starts than its rim, in the sheets' own depth. About a texel of edge. */
    static final float RIM_BAND = 0.26F;
    /** How much of its reach a patch gives back and takes again as it breathes. */
    static final float BREATH = 0.15F;
    /** Radians of breath a tick: a slow creep, about fifteen seconds each way. */
    static final float BREATH_RATE = 0.01F;

    private StormCorruption() {
    }

    /**
     * Whether a storm eats into this drive at all: every drive whose warps a storm changes, which is every
     * drive but the Creative one - defined, as in {@code RiftStormRules}, by having no penalties.
     */
    public static boolean affects(RiftDriveTier tier) {
        return !tier.creative();
    }

    /**
     * Where a drive's patches are in their breathing, {@code 0..1}. Each drive at its own phase, so a hangar of
     * drives does not pulse in step.
     */
    public static float breath(float time, float phase) {
        return 0.5F + 0.5F * (float) Math.sin(time * BREATH_RATE + phase);
    }

    /** How far the patches have grown, {@code 0..1}, from the storm's strength and the drive's breath. */
    public static float growth(float storm, float breath) {
        return clamp01(storm) * (1.0F - BREATH + BREATH * clamp01(breath));
    }

    /**
     * The rim's threshold: one with nothing grown, which the sheets never reach - their deepest texel is 254 -
     * falling to {@link #RIM_FLOOR} with everything grown. Never zero, or every empty texel would pass.
     */
    public static float rimThreshold(float growth) {
        return 1.0F - clamp01(growth) * (1.0F - RIM_FLOOR);
    }

    /** The void's threshold: a band deeper than the rim's, and no deeper than the sheets go. */
    public static float voidThreshold(float growth) {
        return Math.min(1.0F, rimThreshold(growth) + RIM_BAND);
    }

    /** A threshold as the colour the layer hands GeckoLib: white, with the threshold in its alpha. */
    public static int colour(float threshold) {
        int alpha = Math.max(1, Math.min(255, Math.round(threshold * 255.0F)));
        return alpha << 24 | 0xFFFFFF;
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }
}
