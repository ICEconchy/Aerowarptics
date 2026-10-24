package uk.co.iceconchy.aerowarptics.sunderer;

import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.lerp;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.smooth;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.span;

/**
 * A sundering's scene: where everything is and when, as curves of the tick it is at (partial ticks allowed) and
 * whether the hand was rolled.
 *
 * <p>Pure, and read by both sides, as the meltdown's choreography is and for its reason: the server sets the
 * Sunderer's own light by {@link #lightStage} and pulls what stands near by {@link #pull}, and every client draws
 * from the same clock, so what is seen is what is done. {@code SundererChoreographyTest} samples every curve a
 * hundred times a tick, with and without the hand, because a curve that jumps is a piece that teleports on
 * screen. Where each block of the frame is, is {@link Unravelling}'s.
 *
 * <h2>The ring unravels (the owner's choice, 2026-10-17)</h2>
 * The first scene was a flat membrane with cracks drawn on it, and the owner sent it back as "just a 2d plane".
 * This one has no surface at all:
 * <ol>
 *   <li><b>Reaching</b>: a bright {@link #point} opens in the middle of the ring and {@link #search}es - starlines
 *       shoot out along the ring's axis for a far side, and die away - while a {@link #lens} starts to bend the
 *       world behind it. It finds nothing, and the point goes black: the {@link #seed}.</li>
 *   <li><b>Lifting</b>: the third of the frame that bursts cracks free and rises into orbit, a block at a time.</li>
 *   <li><b>Orbiting</b>: the blocks circle the growing seed in a tilted, precessing ring over a {@link #disc} of
 *       dust, faster and tighter, the lens bending the standing frame round them.</li>
 *   <li><b>Consuming</b>: they fall in one after another; the seed {@link #pulse}s with each.</li>
 *   <li><b>Breach</b>: silence, the lens snaps tight, the seed implodes to a point, a flash - and the fissure.</li>
 * </ol>
 * What must be found by day is solid - the seed is a black ball, the blocks are blocks - and the light is trim.
 */
public final class SundererChoreography {

    /** Where the straining's share ends for the lifting, and for the orbiting; the rest is the consuming. */
    public static final double LIFT_END = 0.24D;
    public static final double ORBIT_END = 0.8D;

    /** Ticks of the reaching: the point opening, the starlines searching and dying, the seed being born. */
    public static final double POINT_FROM = 10.0D;
    public static final double POINT_TO = 40.0D;
    public static final double SEARCH_FROM = 30.0D;
    public static final double SEARCH_TO = 90.0D;
    public static final double SEARCH_DIES = 100.0D;
    public static final double SEED_FROM = 120.0D;
    public static final double SEED_TO = 150.0D;

    /** The seed: how wide it is born, and how wide it grows, as a share of the opening's smaller half-size. */
    public static final double SEED_BORN = 0.15D;
    public static final double SEED_SHARE = 0.35D;
    /** How much a swallow swells it, as a share of the opening's smaller half-size. */
    public static final double PULSE_SHARE = 0.06D;

    /** How many turns the orbit makes from start to finish, as a steady and an accelerating part. */
    public static final double TURNS_STEADY = 1.2D;
    public static final double TURNS_GATHERING = 2.2D;
    /** How far the orbit drifts while the hand is out, in radians: barely. */
    public static final double HAND_DRIFT = 0.35D;

    /** How dark the sky goes at most, as a share {@code RiftStormSky.lend} takes. */
    public static final double DARKEST = 0.8D;
    /** The Sunderer's own light at the height of the straining. */
    public static final double BRIGHTEST = 12.0D;
    /** How far the ring of dust runs out along the ground after the flash, in blocks. */
    public static final double DUST_REACH = 12.0D;

    /** Shares of the breach: the implosion, the flash's rise and its fall. */
    public static final double IMPLODES_FROM = 0.2D;
    public static final double IMPLODES_TO = 0.7D;
    public static final double FLASH_PEAK = 0.75D;

    private SundererChoreography() {
    }

    // ---------------------------------------------------------------- where it is

    /**
     * How far the straining has got, 0 to 1, not counting the hand: it is held still while the hand is out. The
     * frame's lifting, orbiting and swallowing all run by this.
     */
    public static double straining(double tick, boolean hand) {
        double into = tick - SundererSequence.REACHING;
        if (into <= 0.0D) {
            return 0.0D;
        }
        if (hand && into > SundererSequence.HAND_AFTER) {
            into = SundererSequence.HAND_AFTER + Math.max(0.0D, into - SundererSequence.HAND_AFTER - SundererSequence.HAND);
        }
        return Math.min(1.0D, into / SundererSequence.STRAINING);
    }

    /** How far through its beat the hand is, 0 to 1; 0 when it was not rolled. */
    public static double handBeat(double tick, boolean hand) {
        if (!hand) {
            return 0.0D;
        }
        double start = SundererSequence.handStart();
        return span(tick, start, start + SundererSequence.HAND);
    }

    /** Whether the hand is out at this tick. */
    public static boolean handOut(double tick, boolean hand) {
        double beat = handBeat(tick, hand);
        return hand && beat > 0.0D && beat < 1.0D;
    }

    /** How far through the breach it is, 0 to 1. */
    public static double breach(double tick, boolean hand) {
        double start = SundererSequence.breachStart(hand);
        return span(tick, start, start + SundererSequence.BREACH);
    }

    /** A rise and fall over the hand's beat, so what it quietens comes back after it. */
    static double handHush(double tick, boolean hand) {
        double beat = handBeat(tick, hand);
        return smooth(span(beat, 0.0D, 0.12D)) * (1.0D - smooth(span(beat, 0.9D, 1.0D)));
    }

    /** How far through the orbiting and the consuming together the frame is, 0 to 1. */
    public static double orbiting(double tick, boolean hand) {
        return span(straining(tick, hand), LIFT_END, 1.0D);
    }

    // ---------------------------------------------------------------- the reaching

    /** The bright point that opens in the ring, 0 to 1: it becomes the seed as the seed is born. */
    public static double point(double tick) {
        return smooth(span(tick, POINT_FROM, POINT_TO)) * (1.0D - smooth(span(tick, SEED_FROM, SEED_TO)));
    }

    /** How far the starlines reach for a far side, 0 to 1: out, and dying away when they find none. */
    public static double search(double tick) {
        return smooth(span(tick, SEARCH_FROM, SEARCH_TO)) * (1.0D - smooth(span(tick, SEARCH_DIES, SEED_FROM)));
    }

    /** How hard the world behind the ring is bent towards the seed, 0 to 1: snapping tight at the implosion. */
    public static double lens(double tick, boolean hand) {
        double b = breach(tick, hand);
        double held = 0.35D * smooth(span(tick, POINT_TO, SundererSequence.REACHING))
                + 0.5D * smooth(straining(tick, hand));
        return lerp(smooth(span(b, 0.0D, IMPLODES_TO)), held, 1.0D) * (1.0D - smooth(span(b, IMPLODES_TO, 0.85D)));
    }

    // ---------------------------------------------------------------- the seed

    /**
     * The seed's radius, in blocks: born small at the end of the reaching, growing as the frame orbits it, swelling
     * at each swallow, and drawn down to nothing in the breach.
     *
     * @param size  the opening's smaller half-size, in blocks
     * @param count how many blocks of the frame it swallows
     */
    public static double seed(double tick, boolean hand, double size, int count) {
        double born = SEED_BORN * smooth(span(tick, SEED_FROM, SEED_TO));
        double grown = (SEED_SHARE * size - SEED_BORN) * smooth(straining(tick, hand));
        double swell = PULSE_SHARE * size * pulse(tick, hand, count);
        return Math.max(0.0D, born + grown + swell) * (1.0D - implosion(tick, hand));
    }

    /** The largest a seed of this opening ever is, for keeping the orbit clear of it. */
    public static double largestSeed(double size) {
        return SEED_SHARE * size + PULSE_SHARE * size;
    }

    /**
     * How swollen the seed is by the swallows, 0 to 1: a rise and fall round each block's, peaking as the block is
     * gone, so the beat of them is seen in the seed - see {@link Unravelling#swallowed}.
     */
    public static double pulse(double tick, boolean hand, int count) {
        double s = straining(tick, hand);
        if (s <= ORBIT_END || count <= 0) {
            return 0.0D;
        }
        double through = span(s, ORBIT_END, 1.0D) * count;
        double phase = through - Math.floor(through);
        // Each beat rises and falls to nothing at its ends, the last one at the end of the straining, so there is
        // nothing to fade out.
        return Math.sin(Math.PI * phase) * Math.sin(Math.PI * phase);
    }

    /** How far the seed has fallen in on itself, 0 to 1. */
    public static double implosion(double tick, boolean hand) {
        return smooth(span(breach(tick, hand), IMPLODES_FROM, IMPLODES_TO));
    }

    /** The dust disc turning in the orbit's plane, 0 to 1: forming as the frame swings in, eaten with it. */
    public static double disc(double tick, boolean hand) {
        double s = straining(tick, hand);
        return smooth(span(s, LIFT_END, LIFT_END + 0.16D)) * (1.0D - smooth(span(s, 0.86D, 1.0D)));
    }

    /**
     * How far round the orbit has turned, in radians: steady at first and gathering pace, held all but still while
     * the hand is out.
     */
    public static double orbitAngle(double tick, boolean hand) {
        double o = orbiting(tick, hand);
        return Math.PI * 2.0D * (TURNS_STEADY * o + TURNS_GATHERING * o * o) + HAND_DRIFT * handBeat(tick, hand);
    }

    // ---------------------------------------------------------------- the room

    /**
     * How dark the sky and fog go, 0 to {@link #DARKEST}: a little as it reaches, more as it strains, darker
     * while the hand is out, darkest as it implodes, and given back after the flash.
     */
    public static double darkness(double tick, boolean hand) {
        double held = 0.15D * smooth(span(tick, 0.0D, SundererSequence.REACHING))
                + 0.45D * smooth(straining(tick, hand))
                + 0.15D * handHush(tick, hand);
        double b = breach(tick, hand);
        return lerp(smooth(span(b, 0.0D, IMPLODES_TO)), held, DARKEST) * (1.0D - smooth(span(b, IMPLODES_TO, 1.0D)));
    }

    /**
     * The Sunderer's own light, 0 to {@link #BRIGHTEST}: it lights the room as it strains, so the ring is what the
     * room is lit by, dims while the hand is out, and dies in the breach.
     */
    public static double light(double tick, boolean hand) {
        double reaching = lerp(smooth(span(tick, 0.0D, SundererSequence.REACHING)), 4.0D, 8.0D);
        double held = reaching + 4.0D * smooth(straining(tick, hand));
        held = lerp(handHush(tick, hand), held, 6.0D);
        return held * (1.0D - smooth(span(breach(tick, hand), 0.0D, 0.5D)));
    }

    /** {@link #light} as the block's {@code CHARGE} stage, 0 to 3: set a handful of times, not every tick. */
    public static int lightStage(double tick, boolean hand) {
        return (int) Math.max(0L, Math.min(3L, Math.round(light(tick, hand) / 4.0D)));
    }

    /** How hard what stands near is drawn towards the opening, 0 to 1. */
    public static double pull(double tick, boolean hand) {
        return smooth(straining(tick, hand)) * (1.0D - 0.7D * handHush(tick, hand))
                * (1.0D - smooth(span(breach(tick, hand), 0.0D, IMPLODES_FROM)));
    }

    // ---------------------------------------------------------------- the sound

    /** The groan's pitch: falling as the search finds nothing, rising as the frame is drawn round. */
    public static double humPitch(double tick, boolean hand) {
        double falling = lerp(smooth(span(tick, SEARCH_TO, SEED_TO)), 1.0D, 0.6D);
        return lerp(smooth(straining(tick, hand)), falling, 1.5D);
    }

    /** The groan's loudness, 0 to 1: it ducks for the hand and goes silent as the breach begins. */
    public static double humVolume(double tick, boolean hand) {
        return smooth(span(tick, 0.0D, 20.0D))
                * (1.0D - 0.8D * handHush(tick, hand))
                * (1.0D - smooth(span(breach(tick, hand), 0.0D, 0.15D)));
    }

    // ---------------------------------------------------------------- the breach

    /** The flash as the point lets go, 0 to 1: up in three ticks, gone over the rest of the breach. */
    public static double flash(double tick, boolean hand) {
        double b = breach(tick, hand);
        return smooth(span(b, IMPLODES_TO, FLASH_PEAK)) * (1.0D - smooth(span(b, FLASH_PEAK, 1.0D)));
    }

    /** How far the ring of dust has run out along the ground, in blocks. */
    public static double dust(double tick, boolean hand) {
        return DUST_REACH * smooth(span(breach(tick, hand), IMPLODES_TO, 1.0D));
    }

    /** Whether the breach has reached the moment the ring comes down and the fissure is set. */
    public static boolean torn(double tick, boolean hand) {
        return breach(tick, hand) >= IMPLODES_TO;
    }
}
