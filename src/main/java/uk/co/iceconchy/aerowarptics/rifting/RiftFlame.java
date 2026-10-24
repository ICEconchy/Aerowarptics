package uk.co.iceconchy.aerowarptics.rifting;

import java.util.List;

/**
 * The shape of a Rift Brazier's fire: rift fire, drawn the way a rift is.
 *
 * <p>A rift's face is dark in the middle, glows towards its edge, and burns at a torn rim that wanders as it
 * stands, with a haze of light outside it. The brazier's flames are that, in the shape of flame: each tongue
 * a void-dark core, a glow creeping in from its edge, a hot rim whose tear ripples upward as fire does, and a
 * violet haze round the outside. They are drawn as crossed upright panes, as vanilla draws fire, so they need
 * nothing turned to face a viewer and stand as well on a ship's deck as on the ground.
 *
 * <p>Positions are in blocks from the brazier's corner, heights from its floor; {@code s} runs 0 at a tongue's
 * foot to 1 at its tip, and {@code time} is in ticks. Free of Minecraft, for {@code RiftFlameTest}: a tongue
 * that spills out of its block, a rim that crosses itself or a flame that never goes out are none of them
 * things that throw.
 */
public final class RiftFlame {

    /** One tongue of fire: where its foot stands, how tall and wide it burns, and its own place in the flicker. */
    public record Tongue(double x, double z, double height, double halfWidth, double phase) {
    }

    /** Where the flames spring from: the essence in the brazier's basin, half a block up inside its open cage. */
    public static final double FLOOR = 8.0D / 16.0D;

    /** How far across a tongue its void core reaches, as a share of the half-width; the rim burns beyond it. */
    public static final double CORE = 0.78D;
    /** Where the glow starts creeping in over the core, as a share of the half-width. */
    public static final double GLOW_FROM = 0.3D;
    /** How far out the haze reaches, as a multiple of the half-width. */
    public static final double HAZE = 1.6D;

    /** How much the rim wanders in and out, at most, as a share of the half-width. */
    public static final double TEAR = 0.24D;
    /** How far a tongue's tip sways, at most, in blocks. */
    public static final double SWAY = 0.085D;
    /** How far a tongue's height flickers, at most, as a share of it. */
    public static final double FLICKER = 0.23D;

    /**
     * How far past its block the fire may reach, sideways and up, in blocks - the brazier's render box is built
     * from these, and {@code RiftFlameTest} holds every flame inside them.
     */
    public static final double REACH_OUT = 0.25D;
    public static final double REACH_UP = 1.5D;

    /** How fast the fire catches, and how fast it dies, as a share of full a tick. */
    public static final double CATCH = 0.12D;
    public static final double DIE = 0.08D;

    /**
     * Four tall tongues towards the corners of the cage, licking up past its rails, and four short ones on its
     * sides between them, all of them inside its rim. Each flickers on its own phase.
     */
    public static final List<Tongue> TONGUES = List.of(
            new Tongue(0.5D + 0.235D, 0.5D + 0.235D, 0.92D, 0.12D, 0.0D),
            new Tongue(0.5D - 0.235D, 0.5D + 0.235D, 0.78D, 0.11D, 1.9D),
            new Tongue(0.5D - 0.235D, 0.5D - 0.235D, 0.98D, 0.12D, 3.7D),
            new Tongue(0.5D + 0.235D, 0.5D - 0.235D, 0.84D, 0.11D, 5.3D),
            new Tongue(0.5D + 0.34D, 0.5D, 0.46D, 0.08D, 0.8D),
            new Tongue(0.5D, 0.5D + 0.34D, 0.40D, 0.075D, 2.6D),
            new Tongue(0.5D - 0.34D, 0.5D, 0.50D, 0.08D, 4.4D),
            new Tongue(0.5D, 0.5D - 0.34D, 0.44D, 0.075D, 6.1D));

    private RiftFlame() {
    }

    /**
     * A flame's outline up its length, 0..1 of its half-width: round at the foot, fullest a little under
     * halfway, and drawn to a point at the tip.
     */
    public static double shape(double s) {
        double t = clamp01(s);
        return Math.sqrt(Math.max(0.0D, Math.sin(Math.PI * (0.1D + 0.9D * t))));
    }

    /**
     * How far the rim has wandered from the outline, as a multiplier - the rift's torn rim, rippling upward,
     * as fire climbs. Never more than {@link #TEAR} either way, so the rim never folds in over the core.
     */
    public static double wander(double s, double time, double phase) {
        return 1.0D + (TEAR * 2.0D / 3.0D) * Math.sin(s * 9.0D - time * 0.45D + phase)
                + (TEAR / 3.0D) * Math.sin(s * 21.0D - time * 0.9D + phase * 2.0D);
    }

    /** How far a tongue has swayed sideways at this height, in blocks: nothing at its foot, most at its tip. */
    public static double sway(double s, double time, double phase) {
        double t = clamp01(s);
        return t * t * (SWAY * 0.7D * Math.sin(time * 0.12D + phase) + SWAY * 0.3D * Math.sin(time * 0.47D + phase * 2.0D));
    }

    /** How tall a tongue burns this tick, as a share of its height. */
    public static double flicker(double time, double phase) {
        return 1.0D + FLICKER * (0.52D * Math.sin(time * 0.31D + phase) + 0.3D * Math.sin(time * 0.83D + phase * 2.0D)
                + 0.18D * Math.sin(time * 1.9D + phase * 3.0D));
    }

    /** A tongue's height now, in blocks, at a fire this far caught (0..1). */
    public static double height(Tongue tongue, double time, double level) {
        return tongue.height() * flicker(time, tongue.phase()) * clamp01(level);
    }

    /** A tongue's half-width at a height, in blocks, rim included - its torn edge, not its haze. */
    public static double halfWidth(Tongue tongue, double s, double time, double level) {
        return tongue.halfWidth() * shape(s) * wander(s, time, tongue.phase()) * (0.5D + 0.5D * clamp01(level));
    }

    /**
     * How dark the core is at a height, 0..1: the tip is all fire, so the void thins out over the top quarter
     * and a tongue ends in light rather than in a black point.
     */
    public static double core(double s) {
        double t = clamp01((s - 0.72D) / 0.28D);
        return 1.0D - t * t * (3.0D - 2.0D * t);
    }

    /** How far caught the fire is a tick later: it catches quickly when lit and dies down when not. */
    public static double step(double level, boolean lit) {
        return lit ? Math.min(1.0D, level + CATCH) : Math.max(0.0D, level - DIE);
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
