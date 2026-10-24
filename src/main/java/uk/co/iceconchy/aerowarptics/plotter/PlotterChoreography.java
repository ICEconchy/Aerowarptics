package uk.co.iceconchy.aerowarptics.plotter;

/**
 * The Rift Plotter's strike as a set piece: how much of each part of the spectacle round the machine there is, at
 * every moment of it. {@code PlotterSpectacle} draws it; this says when.
 *
 * <ol>
 *   <li><b>Charging.</b> A beam gathers from the armillary ball to the point where the entry will tear, thickening as
 *   the charge fills, wound about with strands of the field. A star of light swells at its end, rings of the field
 *   close in on it, faster the fuller it is, and arcs jump from the ball to the star.</li>
 *   <li><b>Reaching</b> - paid for, waiting on the far end. The beam at full, the star straining, and cracks running
 *   out across the disc the tear will fill.</li>
 *   <li><b>The tear.</b> The star bursts out to the rim, a flash, and a shockwave rolls out across the rift's plane
 *   as the aperture opens.</li>
 *   <li><b>Open.</b> Tethers of light from the ball hold the rim.</li>
 *   <li><b>Closing.</b> The tethers let go, and the rift heals.</li>
 * </ol>
 *
 * <p>Every curve is a function of the state, the ticks into it and the charge, and meets the next state's where they
 * join, so nothing snaps at a change of state - bar the tear itself, which is meant to. Minecraft-free, so
 * {@code PlotterChoreographyTest} can walk it.
 */
public final class PlotterChoreography {

    /** Ticks each piece takes to come in at the start of a charge, rather than snapping on. */
    public static final int RAMP_TICKS = 12;

    /** Ticks the star takes to burst out to the rim at the tear. */
    public static final int BURST_TICKS = 7;

    /** Ticks the flash at the tear lasts. */
    public static final int FLASH_TICKS = 9;

    /** Ticks the shockwave takes to roll out and fade. */
    public static final int SHOCK_TICKS = 28;

    /** How far the shockwave rolls, in rift radii. */
    public static final double SHOCK_REACH = 6.0D;

    /** Ticks the cracks take to reach the rim while the far end is waited on. */
    public static final int CRACK_TICKS = 30;

    /** Ticks after the tear the tethers take to take hold. */
    public static final int TETHER_TICKS = 18;

    /** How big the star is at full charge, in rift radii. */
    public static final double STAR = 0.3D;

    private PlotterChoreography() {
    }

    /** How strong the beam from the ball is, 0..1. It gives way to the tear as the star bursts. */
    public static double beam(PlotterState state, double charged, double ticks) {
        return switch (state) {
            case CHARGING -> ramp(ticks) * (0.2D + 0.8D * clamp(charged));
            case REACHING -> 1.0D;
            case OPEN -> 1.0D - PlotterWorks.smooth(ticks / BURST_TICKS);
            case IDLE, CLOSING -> 0.0D;
        };
    }

    /** How big the star at the tear point is, in rift radii. At the tear it bursts out past the rim. */
    public static double star(PlotterState state, double charged, double ticks) {
        return switch (state) {
            case CHARGING -> ramp(ticks) * STAR * (0.15D + 0.85D * Math.pow(clamp(charged), 1.5D));
            // Straining: it swells and shrinks a little either side of full.
            case REACHING -> STAR * (1.0D + 0.12D * Math.sin(ticks * 0.7D));
            case OPEN -> STAR + (1.25D - STAR) * easeOut(ticks / BURST_TICKS);
            case IDLE, CLOSING -> 0.0D;
        };
    }

    /** How bright the star is, 0..1. It fades as it bursts. */
    public static double starGlow(PlotterState state, double charged, double ticks) {
        return switch (state) {
            case CHARGING -> ramp(ticks) * (0.4D + 0.6D * clamp(charged));
            case REACHING -> 1.0D;
            case OPEN -> 1.0D - clamp(ticks / BURST_TICKS);
            case IDLE, CLOSING -> 0.0D;
        };
    }

    /** How strongly the rings of the field close in on the star, 0..1. */
    public static double gather(PlotterState state, double charged, double ticks) {
        return switch (state) {
            case CHARGING -> ramp(ticks) * (0.3D + 0.7D * clamp(charged));
            case REACHING -> 1.0D;
            case OPEN -> 1.0D - clamp(ticks / BURST_TICKS);
            case IDLE, CLOSING -> 0.0D;
        };
    }

    /** Ticks each ring takes to close from the outside in: slow at first, quick as the charge fills. */
    public static double gatherPeriod(double charged) {
        return 36.0D - 24.0D * clamp(charged);
    }

    /** How far the cracks have run towards the rim, 0..1. */
    public static double cracks(PlotterState state, double ticks) {
        return switch (state) {
            case REACHING -> PlotterWorks.smooth(ticks / CRACK_TICKS);
            case OPEN -> 1.0D;
            case IDLE, CHARGING, CLOSING -> 0.0D;
        };
    }

    /** How bright the cracks are, 0..1: they burn while the far end is waited on and are gone in the tear. */
    public static double crackGlow(PlotterState state, double ticks) {
        return switch (state) {
            case REACHING -> 0.5D + 0.5D * PlotterWorks.smooth(ticks / CRACK_TICKS);
            case OPEN -> 1.0D - clamp(ticks / BURST_TICKS);
            case IDLE, CHARGING, CLOSING -> 0.0D;
        };
    }

    /** How bright the flash at the tear is, 0..1. */
    public static double flash(PlotterState state, double ticks) {
        return state == PlotterState.OPEN ? Math.pow(1.0D - clamp(ticks / FLASH_TICKS), 2.0D) : 0.0D;
    }

    /** How far the shockwave has rolled, in rift radii, or 0 when there is none. */
    public static double shock(PlotterState state, double ticks) {
        return state == PlotterState.OPEN && ticks < SHOCK_TICKS ? 1.0D + (SHOCK_REACH - 1.0D) * easeOut(ticks / SHOCK_TICKS)
                : 0.0D;
    }

    /** How bright the shockwave is, 0..1. */
    public static double shockGlow(PlotterState state, double ticks) {
        return state == PlotterState.OPEN ? Math.pow(1.0D - clamp(ticks / SHOCK_TICKS), 1.5D) : 0.0D;
    }

    /** How firmly the tethers from the ball hold the rim, 0..1. */
    public static double tethers(PlotterState state, double ticks) {
        return switch (state) {
            case OPEN -> PlotterWorks.smooth((ticks - BURST_TICKS) / TETHER_TICKS);
            case CLOSING -> 1.0D - PlotterWorks.smooth(ticks / (PlotterWorks.CLOSE_TICKS * 0.5D));
            case IDLE, CHARGING, REACHING -> 0.0D;
        };
    }

    /** The chance an arc jumps from the ball to the star in any one tick. */
    public static double arcChance(PlotterState state, double charged) {
        return switch (state) {
            case CHARGING -> 0.04D + 0.3D * clamp(charged);
            case REACHING -> 0.45D;
            case IDLE, OPEN, CLOSING -> 0.0D;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static double ramp(double ticks) {
        return PlotterWorks.smooth(ticks / RAMP_TICKS);
    }

    private static double easeOut(double t) {
        double x = clamp(t);
        return 1.0D - (1.0D - x) * (1.0D - x) * (1.0D - x);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
