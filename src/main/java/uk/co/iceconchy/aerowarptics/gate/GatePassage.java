package uk.co.iceconchy.aerowarptics.gate;

/**
 * A passage through a Rift Gate as a set piece: how much of each part of it there is, at every moment.
 * The client's overlay draws it; this says when.
 *
 * <ol>
 *   <li><b>The plunge.</b> The pane's own light bursts over the view and clears from the middle outwards,
 *   and what it clears to is already the conduit, picking up speed.</li>
 *   <li><b>The conduit.</b> As long as the server says the passage lasts. Nothing of the far side is seen:
 *   the traveller is already standing there, held, and the veil is what keeps that from being three
 *   seconds of staring at a gate they cannot walk away from.</li>
 *   <li><b>The flare.</b> The far end of the conduit swells until it is the whole view, timed to be whole
 *   as the passage ends.</li>
 *   <li><b>Emerging.</b> The light thins and the far side is behind it.</li>
 * </ol>
 *
 * <h2>One function of the clock, and of one thing the server says</h2>
 * Not a state machine. Every curve here is a function of how long ago the passage began, how long the
 * server said it would last, and when - if yet - the server said it was over. The forecast is only a
 * forecast: a server running slow ends the passage late, and a client running slow hears of the end
 * early. So the flare is aimed at the forecast and then waits, white, for the word; and word that comes
 * before the flare has begun starts it there and then. In both cases every curve is the same either side
 * of the moment the word arrives, which is what {@code GatePassageTest} holds: a curve that jumped there
 * would be a tunnel that flickered every time the server hiccuped.
 *
 * <p>Ages are in ticks and may be fractional. {@code arrived} is the age at which the client heard the
 * passage had ended, or negative while it has not. Minecraft-free.
 */
public final class GatePassage {

    /** Ticks the pane's light takes to clear, and the conduit to come up to speed. */
    public static final int PLUNGE_TICKS = 10;

    /** Ticks the far end takes to swell from a point of light into the whole view. */
    public static final int FLARE_TICKS = 12;

    /**
     * Ticks the light takes to thin once the traveller is through.
     *
     * <p>The server's too: it goes on protecting a traveller for this long after they arrive, because for
     * this long they still cannot see what is in front of them.
     */
    public static final int EMERGE_TICKS = 14;

    /**
     * Ticks past the forecast a client waits for word that the passage is over before it gives up and
     * clears the view by itself. A lost packet must not leave a player looking at white for ever.
     */
    public static final int LOST_TICKS = 60;

    /** How much wider the view stands as the far side appears, easing back as the light thins. */
    public static final double FOV_WIDEN = 0.28D;

    /** How far down the conduit a traveller goes each tick at full speed, in the conduit's own lengths. */
    public static final double PACE = 0.34D;

    /** The share of full speed the conduit already has as the pane's light clears. */
    private static final double STANDING_START = 0.25D;

    /** How far off the middle of the view the conduit's far end swings, in half-heights of the screen. */
    private static final double SWAY = 0.24D;

    private GatePassage() {
    }

    // ------------------------------------------------------------- the moments

    /** How long the flare takes on a passage this long: all of {@link #FLARE_TICKS}, or half a short one. */
    private static double flareTicks(double forecast) {
        return Math.min(FLARE_TICKS, Math.max(1.0D, forecast * 0.5D));
    }

    /** How long the plunge takes on a passage this long. */
    private static double plungeTicks(double forecast) {
        return Math.min(PLUNGE_TICKS, Math.max(1.0D, forecast * 0.5D));
    }

    /**
     * The age the flare begins to swell at.
     *
     * <p>Aimed so that it is whole at the forecast - unless word of the end came before that, in which
     * case it begins on the word.
     */
    static double flareBegins(double forecast, double arrived) {
        double aimed = Math.max(0.0D, forecast - flareTicks(forecast));
        return arrived >= 0.0D ? Math.min(arrived, aimed) : aimed;
    }

    /** The age the flare is whole at: the view is nothing but light. */
    static double flareWhole(double forecast, double arrived) {
        return flareBegins(forecast, arrived) + flareTicks(forecast);
    }

    /**
     * The age the far side begins to show at.
     *
     * <p>Never before the flare is whole and never before the server has said the passage is over - bar
     * the one case where it never says so, which is what {@link #LOST_TICKS} is for.
     */
    public static double emergesAt(double forecast, double arrived) {
        double lost = Math.max(0.0D, forecast) + LOST_TICKS;
        if (arrived < 0.0D) {
            return lost;
        }
        return Math.min(lost, Math.max(arrived, flareWhole(forecast, arrived)));
    }

    // ---------------------------------------------------------------- the curves

    /**
     * How much of the world is hidden, 0 to 1.
     *
     * <p>Whole from the first tick: there is no fading in. The traveller has just walked into a pane of
     * light, and the first thing drawn over them is that light - see {@link #splash}.
     */
    public static double veil(double age, double forecast, double arrived) {
        if (age < 0.0D) {
            return 0.0D;
        }
        return 1.0D - smooth((age - emergesAt(forecast, arrived)) / EMERGE_TICKS);
    }

    /**
     * How much of what is drawn is the light at the far end, 0 to 1.
     *
     * <p>It only ever grows. Once it is whole the view is white, and it is the {@link #veil} thinning
     * that lets the far side through it - if this fell instead, the conduit would come back for a moment
     * behind the light, on the way out of it.
     */
    public static double flare(double age, double forecast, double arrived) {
        return smooth((age - flareBegins(forecast, arrived)) / flareTicks(forecast));
    }

    /** How much of the pane's own light is still over the view, 1 as the passage begins and soon 0. */
    public static double splash(double age, double forecast) {
        return 1.0D - smooth(age / plungeTicks(forecast));
    }

    /** How fast the conduit is going by, as a share of its full speed, {@link #STANDING_START} to 1. */
    public static double rush(double age, double forecast) {
        return STANDING_START + (1.0D - STANDING_START) * smooth(age / (2.0D * plungeTicks(forecast)));
    }

    /**
     * How far down the conduit the traveller has gone, in its own lengths.
     *
     * <p>The integral of {@link #rush}, worked out rather than added up a frame at a time, so that a
     * dropped frame is a dropped frame and not a lurch in the walls.
     */
    public static double depth(double age, double forecast) {
        if (age <= 0.0D) {
            return 0.0D;
        }
        double ramp = 2.0D * plungeTicks(forecast);
        double u = Math.min(1.0D, age / ramp);
        // The integral of 3u^2 - 2u^3 is u^3 - u^4 / 2: a half by the time the ramp is over, and a
        // straight line after it.
        double eased = ramp * (u * u * u - 0.5D * u * u * u * u) + Math.max(0.0D, age - ramp);
        return PACE * (STANDING_START * age + (1.0D - STANDING_START) * eased);
    }

    /**
     * How far across the view the far end of the conduit has swung.
     *
     * <p>Nothing as the passage begins, because the traveller has just gone straight in, and nothing
     * again by the time the flare is whole, because the light they come out through is dead ahead.
     *
     * @param seed this passage's own, so that no two bend the same way
     */
    public static double swayX(double age, double forecast, double arrived, double seed) {
        return swing(age, forecast, arrived) * Math.sin(age * 0.11D + seed);
    }

    /** {@link #swayX}, up the view rather than across it, on a rhythm of its own. */
    public static double swayY(double age, double forecast, double arrived, double seed) {
        return swing(age, forecast, arrived) * 0.7D * Math.sin(age * 0.073D + 1.3D + seed * 1.7D);
    }

    private static double swing(double age, double forecast, double arrived) {
        return SWAY * smooth(age / (2.0D * plungeTicks(forecast))) * (1.0D - flare(age, forecast, arrived));
    }

    /** How far round the conduit has turned, in radians: a slow corkscrew with a lurch in it. */
    public static double roll(double age, double seed) {
        return age * 0.021D + 0.55D * Math.sin(age * 0.05D + seed);
    }

    /** How far apart the colours are pulled, 0 to 1: more the faster it goes, and never still. */
    public static double chroma(double age, double forecast) {
        return rush(age, forecast) * (0.6D + 0.4D * Math.sin(age * 0.19D));
    }

    /**
     * How much wider the view stands, as a fraction of itself.
     *
     * <p>Wide for the whole of the conduit, where nobody can see it, so that it is already wide as the
     * far side appears and can ease back as the light thins: arriving reads as slowing down.
     */
    public static double fov(double age, double forecast, double arrived) {
        if (age < 0.0D) {
            return 0.0D;
        }
        return FOV_WIDEN * (1.0D - smooth((age - emergesAt(forecast, arrived)) / EMERGE_TICKS));
    }

    /** Whether the traveller is still held: until the far side begins to show, they have nothing to steer by. */
    public static boolean held(double age, double forecast, double arrived) {
        return age >= 0.0D && age < emergesAt(forecast, arrived);
    }

    /** Whether there is nothing left to draw. */
    public static boolean over(double age, double forecast, double arrived) {
        return age >= emergesAt(forecast, arrived) + EMERGE_TICKS;
    }

    /** Smoothstep on 0 to 1, clamped either side, and never a NaN. */
    static double smooth(double t) {
        if (!(t > 0.0D)) {
            return 0.0D;
        }
        if (t >= 1.0D) {
            return 1.0D;
        }
        return t * t * (3.0D - 2.0D * t);
    }
}
