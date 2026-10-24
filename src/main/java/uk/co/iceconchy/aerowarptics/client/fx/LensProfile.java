package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * How a Mostly Dangerous rift bends the view around it, and when.
 *
 * <p>Something massive arriving pulls the light passing close to it, so what is behind it is seen
 * pulled out and round it - the sky just past the cloud's edge is really the sky from nearer its middle,
 * stretched round in arcs. {@link GravityLens} draws that for real, by redrawing a disc round the rift from
 * a copy of the frame; this says where in that copy each point of the disc should look.
 *
 * <h2>The curve</h2>
 * A point a distance {@code rho} out from the middle of the lens - as a fraction of the lens - shows the
 * frame from {@link #sample} of that instead. Four things about it are load-bearing, and none of them
 * throws when it is wrong, which is why {@code LensProfileTest} holds each one:
 * <ul>
 *   <li><b>Nothing at the edge.</b> At the rim of the lens it looks exactly where it is, so the disc
 *       meets the undisturbed frame around it with no seam.</li>
 *   <li><b>Nothing folds.</b> Further out always looks further out. A curve that turned back on itself
 *       would draw the same piece of sky twice with a crease between, which reads as a smear on the
 *       glass, not as space bending.</li>
 *   <li><b>Only ever inwards.</b> Light bends towards a mass, so a point only ever shows what is nearer
 *       the middle than it is - and never through the middle and out the other side.</li>
 *   <li><b>Strongest just past the cloud.</b> Bending hidden behind the cloud is bending nobody sees.</li>
 * </ul>
 *
 * <h2>Briefly</h2>
 * The bending is a pulse, not a state: as the cloud arrives, again as a hull bores out of it, and once
 * more, hard and short, as it collapses. Between those the view is left alone - a permanent lens would
 * stop reading as something happening and start reading as a smudge - and it costs a copy of the frame
 * only while it is actually bending.
 *
 * <p>Free of Minecraft, for {@code LensProfileTest}.
 */
public final class LensProfile {

    /** How far the bending reaches from the middle, as a multiple of the rim: twice the cloud's reach. */
    public static final float REACH = Nebula.REACH * 2.0F;

    /** Where the bending is strongest, as a fraction of the lens: the cloud's own furthest edge. */
    public static final float CORE = Nebula.REACH / REACH;

    /**
     * How hard it pulls.
     *
     * <p>Kept below {@code CORE * CORE}, and that is the whole of the guarantee that it cannot fold: the
     * pull's steepest slope, which is at the middle, is this over that - so under one, further out
     * always stays further out.
     */
    private static final float PULL = 0.8F * CORE * CORE;

    /** Where the lens starts to ease back to looking straight, as a fraction of it. */
    private static final float EASE_FROM = 0.68F;

    /** How strongly the cloud arriving bends it, and how long it takes to let go afterwards. */
    private static final float ARRIVE = 0.8F;
    private static final float ARRIVE_FALL = 30.0F;
    /** A hull boring out: the strongest of the three. */
    private static final float EMERGE_RISE = 3.0F;
    private static final float EMERGE_FALL = 28.0F;
    /** The collapse: hard and short. */
    private static final float COLLAPSE = 0.9F;
    private static final float COLLAPSE_RISE = 6.0F;
    private static final float COLLAPSE_FALL = 12.0F;

    private LensProfile() {
    }

    /**
     * Where in the frame a point of the lens looks, as a fraction of the lens.
     *
     * @param rho      how far out the point is, as a fraction of the lens
     * @param strength how hard the lens is bending right now, 0 to 1
     */
    public static float sample(float rho, float strength) {
        if (rho <= 0.0F) {
            return 0.0F;
        }
        if (rho >= 1.0F) {
            return rho;
        }
        float s = clamp(strength);
        float ease = 1.0F - smoothstep(EASE_FROM, 1.0F, rho);
        return rho - s * PULL * rho / (rho * rho + CORE * CORE) * ease;
    }

    /**
     * How hard the lens is bending at a moment, 0 to 1.
     *
     * @param openTicks   how long the rift takes to open
     * @param transitFrom when a hull started through it, or negative if none has
     * @param closeFrom   when it starts to close - its planned end, until something brings that forward
     */
    public static float strength(float time, float openTicks, float transitFrom, float closeFrom) {
        float breaks = openTicks * RiftShatter.CRACK_PHASE;
        float arrive = ARRIVE * pulse(time, breaks, Math.max(1.0F, breaks), ARRIVE_FALL);
        float emerge = transitFrom < 0.0F ? 0.0F
                : pulse(time, transitFrom + EMERGE_RISE, EMERGE_RISE, EMERGE_FALL);
        float collapse = COLLAPSE * pulse(time, closeFrom + COLLAPSE_RISE, COLLAPSE_RISE, COLLAPSE_FALL);
        return Math.max(arrive, Math.max(emerge, collapse));
    }

    /** A rise to one at {@code peak} and a fall back to nothing, both smooth, and nothing either side. */
    private static float pulse(float time, float peak, float rise, float fall) {
        if (time <= peak - rise || time >= peak + fall) {
            return 0.0F;
        }
        if (time <= peak) {
            return smoothstep(peak - rise, peak, time);
        }
        return 1.0F - smoothstep(peak, peak + fall, time);
    }

    private static float smoothstep(float from, float to, float x) {
        float t = clamp((x - from) / (to - from));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
