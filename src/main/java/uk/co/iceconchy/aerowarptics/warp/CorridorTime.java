package uk.co.iceconchy.aerowarptics.warp;

import uk.co.iceconchy.aerowarptics.AWConfig;

/**
 * How long a warp spends in its corridor, once distance is allowed a say.
 *
 * <p>Out of the box every warp of a tier lasts the same: the tier's {@code warpTicks}, whether the jump is
 * across the bay or across the map. That is deliberate - the corridor is a fold, not a distance - and it
 * is also the one thing about a warp a server might reasonably want otherwise, because a journey that
 * takes as long as a hop does not feel like a journey. So the length is the tier's figure multiplied by
 * something that grows with distance, and a server chooses whether it grows at all, how fast, and on what
 * curve.
 *
 * <p>Only the corridor stretches. The run at the rift, the passage through it and the run out are all
 * sized from the hull, and the spin-up before any of it already answers to distance on a curve of its
 * own ({@link SpinUp}).
 *
 * <p>Free of Minecraft, so the curves are tested as arithmetic; {@link #fromConfig} is the bridge.
 */
public final class CorridorTime {

    /** The distance one unit of the multiplier is measured over, in blocks: a kilometre. */
    public static final double UNIT = 1_000.0D;

    private CorridorTime() {
    }

    /** How a warp's length grows with how far it goes. */
    public enum Scaling {
        /** It does not: every warp of a tier lasts the tier's own figure. */
        NONE,
        /** By the same amount for every kilometre, without end - twice as far is twice the extra. */
        LINEAR,
        /** By the square root of the distance: four times as far is twice the extra. */
        SQUARE_ROOT,
        /** By the same amount each time the distance doubles, which flattens out long hauls hardest. */
        LOGARITHMIC
    }

    /**
     * How many times the tier's figure a warp of this length lasts, before any limit.
     *
     * <p>Every curve starts at one for a jump of no length and never goes below it, so switching scaling
     * on can only ever lengthen a warp - a server reaching for this to slow travel down does not find
     * that it has made the short hops quicker.
     *
     * @param distance   how far the jump reaches, in blocks
     * @param multiplier how much of the tier's figure each unit of the curve adds
     */
    public static double factor(double distance, Scaling scaling, double multiplier) {
        if (!(distance > 0.0D) || !(multiplier > 0.0D)) {
            return 1.0D;
        }
        double units = distance / UNIT;
        double grown = switch (scaling) {
            case NONE -> 0.0D;
            case LINEAR -> units;
            case SQUARE_ROOT -> Math.sqrt(units);
            // Base two, so "each doubling adds the multiplier" is literally true past the first unit.
            case LOGARITHMIC -> Math.log1p(units) / Math.log(2.0D);
        };
        return 1.0D + multiplier * grown;
    }

    /**
     * Ticks a warp holds its corridor.
     *
     * @param base     the tier's own corridor time, which is what a warp lasts with scaling off
     * @param distance how far the jump reaches, in blocks
     * @param most     the longest any warp may be made by scaling. A limit on the stretch, not on the
     *                 tier: a tier already longer than this keeps its own figure
     */
    public static int ticks(int base, double distance, Scaling scaling, double multiplier, int most) {
        int floor = Math.max(1, base);
        double scaled = floor * factor(distance, scaling, multiplier);
        // Compared as doubles before rounding: a far jump on a straight line overflows an int long
        // before it reaches any limit a server would set.
        double capped = Math.min(scaled, Math.max(floor, most));
        return (int) Math.max(floor, Math.round(capped));
    }

    /** The same, on the server's settings. */
    public static int fromConfig(int base, double distance) {
        return ticks(base, distance, AWConfig.WARP_TIME_SCALING.get(), AWConfig.WARP_TIME_MULTIPLIER.get(),
                AWConfig.WARP_TIME_LIMIT.get());
    }
}
