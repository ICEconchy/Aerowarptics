package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;

/**
 * The shape of a torn aperture.
 *
 * <p>Split out from the renderer because one property of it is load-bearing rather than decorative,
 * and a property that matters deserves a test. The face of a rift is what hides an airship as it goes
 * through, and it is only wide enough to do that because the tear is defined as a wander
 * <em>outwards</em> from a circle sized to cover the hull. A rim that dipped inside that circle would
 * cut a window in the very surface doing the hiding, and the ship would show through the hole it is
 * supposed to be disappearing into.
 *
 * <p>Plain arithmetic on purpose: no state, no randomness, no client-only types. Every viewer works
 * the same aperture out from the same angle and tick, so a rift looks the same to everyone watching it
 * without a byte being sent about its shape.
 */
public final class RiftTear {

    /** How far past the covering circle the tear may reach, as a fraction of it. */
    public static final float RAG = 0.22F;
    /** How far the flames beyond the rim may reach, as a fraction of the rim. */
    public static final float FLAME = 0.35F;

    private RiftTear() {
    }

    /**
     * How far the torn rim reaches at an angle, as a multiple of the covering circle.
     *
     * @return a value in {@code [1, 1 + RAG]} - never less than one
     */
    public static float rim(double angle, float time) {
        float wander = 0.55F
                + 0.25F * Mth.sin((float) (angle * 3.0D) + time * 0.7F)
                + 0.15F * Mth.sin((float) (angle * 7.0D) - time * 1.1F)
                + 0.08F * Mth.sin((float) (angle * 13.0D) + time * 1.9F);
        return 1.0F + RAG * Mth.clamp(wander, 0.0F, 1.0F);
    }

    /**
     * How far the flame licks past the rim at an angle, 0 to 1.
     *
     * <p>Wanders faster than the rim does, so the edge looks alight rather than merely wobbly. It is
     * purely additive fire and hides nothing, so it is free to be as ragged as it likes.
     */
    public static float lick(double angle, float time) {
        return Mth.clamp(0.5F
                + 0.35F * Mth.sin((float) (angle * 5.0D) + time * 2.3F)
                + 0.25F * Mth.sin((float) (angle * 11.0D) - time * 3.7F), 0.0F, 1.0F);
    }
}
