package uk.co.iceconchy.aerowarptics.wellhead;

import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;

/**
 * A meltdown's scene: where everything is and when, as curves of how far through it is.
 *
 * <p>Pure, and read by both sides, as {@code CollapseChoreography} is and for its reason: the server takes the
 * ground by the same curve the client draws the dark swallowing it by, so what is seen to go is what goes. A
 * curve that jumps at a beat is a piece that teleports on screen - {@code MeltdownChoreographyTest} samples
 * every one of these a hundred times a tick. The one thing here that is meant to jump is the heartbeat, and it
 * is a count rather than a curve.
 *
 * <h2>The five beats</h2>
 * <ol>
 *   <li><b>Breach</b>: the frame fails, all of it, in a couple of seconds, and the tear burns white.</li>
 *   <li><b>Swell</b>: the long one. The tear grows into a black ball with a funnel climbing out of it into the
 *       sky, the ground strips off towards it from the surface down, everything loose is drawn in, and a
 *       heartbeat runs through all of it, quickening.</li>
 *   <li><b>Hush</b>: it stops. No sound, the debris hanging where it was, the tear drawn down to a point.</li>
 *   <li><b>Detonation</b>: the point lets go. A ball of dark runs out from it and the ground is gone behind
 *       it; a ring of air runs out ahead and arrives where it arrives when it arrives.</li>
 *   <li><b>Fallout</b>: the dark draws back off what it left, what was thrown comes down, and the sky returns.</li>
 * </ol>
 *
 * <p>The first version of this was a third the size and half as long, and almost all of it was additive light,
 * which at noon is nearly nothing (seen in game, 2026-10-09). What matters most here is therefore the two
 * things that are <em>dark</em> - {@link #tear} and {@link #front} - because dark is what reads by day.
 */
public final class MeltdownChoreography {

    public static final double BREACH_END = 0.12D;
    public static final double SWELL_END = 0.58D;
    public static final double HUSH_END = 0.64D;
    public static final double BLAST_END = 0.76D;

    /** How wide the tear swells, as a share of the crater's radius. */
    public static final double TEAR_SHARE = 0.4D;

    /** How far out the ground strips before the detonation, as a share of the crater's radius. */
    public static final double PEEL_SHARE = 0.8D;

    /** How far the ring of air runs, in crater radii, and how far past the detonation it takes to. */
    public static final double RING_REACH = 2.0D;
    private static final double RING_END = BLAST_END + 0.08D;

    /** How long the flash takes to come. Short, but never a step. */
    private static final double FLASH_RISE = 0.004D;

    /** How long the dark takes to draw back off the crater once the ground has gone. */
    private static final double FRONT_FALL = 0.035D;

    /** Ticks between heartbeats as the swell begins, and as it ends. */
    public static final double HEARTBEAT_SLOW = 60.0D;
    public static final double HEARTBEAT_FAST = 8.0D;

    private MeltdownChoreography() {
    }

    /** The beat a meltdown is in, for the things that happen once as it is entered. */
    public enum Beat {
        BREACH, SWELL, HUSH, DETONATION, FALLOUT, OVER;

        public static Beat at(double progress) {
            if (progress >= 1.0D) {
                return OVER;
            }
            if (progress >= BLAST_END) {
                return FALLOUT;
            }
            if (progress >= HUSH_END) {
                return DETONATION;
            }
            if (progress >= SWELL_END) {
                return HUSH;
            }
            return progress >= BREACH_END ? SWELL : BREACH;
        }
    }

    /**
     * The tear's radius, in blocks: a little wider than it hung as the frame fails, swelling through the swell,
     * drawn down to nothing in the hush.
     */
    public static double tear(double progress, double crater) {
        double wide = Math.max(3.0D, crater * TEAR_SHARE);
        if (progress < BREACH_END) {
            return CollapseChoreography.lerp(CollapseChoreography.smooth(span(progress, 0.0D, BREACH_END)), 1.0D, 2.0D);
        }
        if (progress < SWELL_END) {
            return CollapseChoreography.lerp(CollapseChoreography.smooth(span(progress, BREACH_END, SWELL_END)), 2.0D, wide);
        }
        return CollapseChoreography.lerp(CollapseChoreography.smooth(span(progress, SWELL_END, HUSH_END)), wide, 0.0D);
    }

    /** How far out the ground is stripping, as a share of the crater's radius: nothing until the swell. */
    public static double peel(double progress) {
        return PEEL_SHARE * CollapseChoreography.smooth(span(progress, BREACH_END, SWELL_END));
    }

    /** How hard the tear draws things in, 0 to 1: rising through the swell and let go as the hush begins. */
    public static double pull(double progress) {
        return CollapseChoreography.smooth(span(progress, BREACH_END, SWELL_END))
                * (1.0D - CollapseChoreography.smooth(span(progress, SWELL_END, SWELL_END + 0.015D)));
    }

    /**
     * How much of the crater has been taken, as a share of its radius: all of it inside the detonation, and
     * easing out - fast through the small shells at the middle and slow through the great ones at the rim,
     * which is both how a blast looks and what keeps the largest crater's worst tick affordable.
     */
    public static double carve(double progress) {
        double t = span(progress, HUSH_END, BLAST_END);
        return 1.0D - (1.0D - t) * (1.0D - t);
    }

    /**
     * The dark that runs out from the point, as a share of the crater's radius: it is the edge of the ground's
     * going through the detonation, and then draws back in off what is left, from the rim inwards.
     */
    public static double front(double progress) {
        return carve(progress) * (1.0D - CollapseChoreography.smooth(span(progress, BLAST_END, BLAST_END + FRONT_FALL)));
    }

    /** How dark the sky is, 0 to 1: gathering through the breach and the swell, lifting through the fallout. */
    public static double darkness(double progress) {
        return CollapseChoreography.smooth(span(progress, 0.0D, BREACH_END + 0.1D))
                * (1.0D - CollapseChoreography.smooth(span(progress, BLAST_END, 1.0D)));
    }

    /** How far the tear's light has gone from the rift's violet to white, 0 to 1. */
    public static double whiteness(double progress) {
        return CollapseChoreography.smooth(span(progress, 0.0D, BREACH_END));
    }

    /** The flash in the world, 0 to 1: nothing, then all of it at the detonation, and fading through it. */
    public static double flash(double progress) {
        return span(progress, HUSH_END, HUSH_END + FLASH_RISE) * (1.0D - span(progress, HUSH_END + FLASH_RISE, BLAST_END));
    }

    /** The white over a near watcher's whole view, 0 to 1: the flash's first second and no more. */
    public static double whiteout(double progress) {
        return span(progress, HUSH_END, HUSH_END + FLASH_RISE)
                * (1.0D - CollapseChoreography.smooth(span(progress, HUSH_END + FLASH_RISE, HUSH_END + 0.035D)));
    }

    /**
     * How hard a camera standing at the tear shakes while it lasts: a tremble through the breach, a climb through
     * the swell, and nothing at all from the hush on. The jolt at the detonation is not here - it is one kick,
     * given once, and a curve that held it for the length of the flash shook the view for five seconds.
     */
    public static double shake(double progress) {
        double tremble = 0.3D * CollapseChoreography.smooth(span(progress, 0.0D, 0.03D))
                * (1.0D - span(progress, SWELL_END, SWELL_END + 0.015D));
        return tremble + 0.7D * pull(progress);
    }

    /** How tall the funnel over the tear stands, 0 to 1: climbing early in the swell, gone with the tear. */
    public static double funnel(double progress) {
        double swell = SWELL_END - BREACH_END;
        return CollapseChoreography.smooth(span(progress, BREACH_END, BREACH_END + swell * 0.4D))
                * (1.0D - CollapseChoreography.smooth(span(progress, SWELL_END, HUSH_END)));
    }

    /** How far the ring of air has run, in crater radii. */
    public static double ring(double progress) {
        double out = span(progress, HUSH_END, RING_END);
        return RING_REACH * (1.0D - (1.0D - out) * (1.0D - out));
    }

    /** How much of the ring of air is left, 0 to 1. */
    public static double ringStrength(double progress) {
        return span(progress, HUSH_END, HUSH_END + FLASH_RISE) * (1.0D - span(progress, HUSH_END + FLASH_RISE, RING_END));
    }

    /** Whether loose debris hangs where it is: the hush. */
    public static boolean hangs(double progress) {
        return progress >= SWELL_END && progress < HUSH_END;
    }

    // ------------------------------------------------------------------ the heartbeat

    /** Ticks between two heartbeats at this point of the swell: slow as it begins, fast as it ends. */
    public static double heartbeatInterval(double progress) {
        return CollapseChoreography.lerp(span(progress, BREACH_END, SWELL_END), HEARTBEAT_SLOW, HEARTBEAT_FAST);
    }

    /**
     * How many heartbeats there have been by now, as a real number: its whole part is the count, and each time
     * that moves a beat has fallen. None before the swell and no more after it. The count is the same on every
     * client and the server because it is worked out from how far through the scene is, not kept.
     *
     * @param length the scene's length in ticks
     */
    public static double heartbeats(double progress, int length) {
        double swellTicks = (SWELL_END - BREACH_END) * length;
        double interval = heartbeatInterval(Math.min(progress, SWELL_END));
        // The integral of one over an interval that shortens in a straight line.
        return swellTicks * Math.log(HEARTBEAT_SLOW / interval) / (HEARTBEAT_SLOW - HEARTBEAT_FAST);
    }

    /** How much a viewer this far off is in it, 0 to 1: all of it close by, none past the range. */
    public static double felt(double distance, double range) {
        return 1.0D - CollapseChoreography.smooth(span(distance, range * 0.25D, range));
    }

    static double span(double value, double from, double to) {
        return CollapseChoreography.span(value, from, to);
    }
}
