package uk.co.iceconchy.aerowarptics.plotter;

/**
 * What a plot costs in Rift Essence, and how fast a turning plotter draws it.
 *
 * <p>There is no range limit. Distance is paid for instead: a floor for tearing anything at all, a price per
 * kilometre from the lens to the exit, and a price per block of radius - a wider tear is a bigger one. A shot at
 * the other side of the world is not refused; it is expensive, and the plotter simply goes on drawing essence
 * until it has been paid. That is why the plotter <em>charges</em> rather than checking its tank: the tank is a
 * buffer that pipes keep filling, and the charge is what has been drawn out of it so far. A plot longer than the
 * tank holds is a plot that takes a supply line, not one the machine turns down.
 *
 * <p>The charge is kept when a plot is stood down or refused at the far end - it has been drawn, not spent - and
 * the next plot starts from it. Only opening the rift spends it.
 *
 * <p>Pure, so the pricing can be tested without a config: the block entity hands in the configured numbers.
 */
public final class PlotCost {

    /** How much faster than at its minimum speed a plotter may draw, however fast it is turned. */
    public static final double MOST_DRAW = 4.0D;

    private PlotCost() {
    }

    /**
     * The whole price of a plot, in millibuckets.
     *
     * @param distance  blocks from the lens to the exit's middle
     * @param radius    the rift's radius, in blocks
     * @param base      the floor
     * @param perKm     millibuckets per thousand blocks of distance
     * @param perRadius millibuckets per block of radius
     */
    public static long essence(double distance, double radius, int base, int perKm, int perRadius) {
        double far = Double.isFinite(distance) ? Math.max(0.0D, distance) : 0.0D;
        double wide = Double.isFinite(radius) ? Math.max(0.0D, radius) : 0.0D;
        double cost = Math.max(0, base) + Math.max(0, perKm) * far / 1000.0D + Math.max(0, perRadius) * wide;
        // Far past any tank in the game, and still a number: a plot across a thirty-million-block world is
        // seven and a half million millibuckets at the default price, not an overflow.
        return (long) Math.ceil(Math.min(cost, (double) (Long.MAX_VALUE / 4)));
    }

    /**
     * Millibuckets a tick a plotter turning at {@code speed} draws: none below the minimum, the configured rate at
     * it, and more the faster it turns, up to {@link #MOST_DRAW} times as much.
     */
    public static int drawRate(double speed, double minimumSpeed, int rate) {
        double turning = Math.abs(speed);
        if (rate <= 0 || turning < Math.max(1.0e-6D, minimumSpeed)) {
            return 0;
        }
        double ratio = minimumSpeed <= 0.0D ? MOST_DRAW : Math.min(MOST_DRAW, turning / minimumSpeed);
        return Math.max(1, (int) Math.round(rate * ratio));
    }

    /**
     * What a lane costs a second: {@code percent} of what opening the same plot costs, never less than a millibucket -
     * so a lane across the world costs more to hold than one down the road, as it cost more to open.
     */
    public static long upkeep(long price, double percent) {
        double share = Double.isFinite(percent) ? Math.max(0.0D, percent) : 0.0D;
        return Math.max(1L, (long) Math.ceil(Math.max(0L, price) * share / 100.0D));
    }

    /** How far through its charge a plot is, 0..1. */
    public static float progress(long charge, long cost) {
        if (cost <= 0L) {
            return 1.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, charge / (double) cost));
    }
}
