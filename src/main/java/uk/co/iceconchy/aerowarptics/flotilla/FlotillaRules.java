package uk.co.iceconchy.aerowarptics.flotilla;

import uk.co.iceconchy.aerowarptics.warp.WarpCost;

/**
 * The numbers behind a flotilla: how many escorts a flagship can lead, and what a fold costs each drive.
 *
 * <p>Free of Minecraft, like {@link WarpCost}, which this leans on rather than repeats. The live config
 * is read by the callers and handed in.
 */
public final class FlotillaRules {

    private FlotillaRules() {
    }

    /**
     * Escorts a flagship may lead, by drive tier index: Mk I, Mk II, Mk III, Singularity, Creative. Minus
     * one is unlimited. These are the config's defaults; the server's own values are read through
     * {@code AWConfig.TIERS}.
     */
    static final int[] DEFAULT_ESCORTS = {1, 2, 4, 8, -1};

    /** The default escort cap for a tier, by its index. Out of range reads as the last tier's. */
    public static int defaultEscorts(int tierIndex) {
        return DEFAULT_ESCORTS[Math.max(0, Math.min(DEFAULT_ESCORTS.length - 1, tierIndex))];
    }

    /** A configured cap as a count: a negative setting is unlimited. */
    public static int cap(int configured) {
        return configured < 0 ? Integer.MAX_VALUE : configured;
    }

    /** Whether a flagship already leading {@code escorts} may take one more. */
    public static boolean hasRoom(int escorts, int configured) {
        return escorts < cap(configured);
    }

    // -------------------------------------------------------------------- cost

    /**
     * The mass a drive is charged for when it folds as part of a flotilla.
     *
     * <p>A drive in a fold pays for its own hull, as it always would, multiplied by how much heavier the
     * whole fleet is than its heaviest ship, raised to {@code exponent - 1}:
     *
     * <pre>
     *   charged = own * (total / heaviest) ^ (exponent - 1)
     * </pre>
     *
     * <ul>
     *   <li>A ship on its own is its own heaviest and its own total, so the factor is one and it pays
     *       exactly what it always did. So does every ship at an exponent of one - which is what makes
     *       the exponent the only thing a server has to reason about.</li>
     *   <li>Above one, every drive in a fleet pays more than it would alone, and more the more there is
     *       of the fleet: two equal ships at the default 1.5 pay 1.41 times each, four pay twice, eight
     *       pay 2.83 times. The same tonnage as one great hull is always cheaper - which is the rule a
     *       fleet is meant to feel: moving it takes better drives than moving that much ship on its own.</li>
     *   <li>Measured against the heaviest rather than the flagship, so a fleet led by a skiff is not
     *       priced as though its cruiser were an extravagance of the skiff's.</li>
     * </ul>
     *
     * @param own      this drive's own hull mass
     * @param total    every hull in the fold, this one included
     * @param heaviest the heaviest single hull in the fold
     * @param exponent the server's {@code flotillaMassExponent}; below one is treated as one
     */
    public static double chargedMass(double own, double total, double heaviest, double exponent) {
        double safeOwn = finite(own);
        double safeHeaviest = Math.max(safeOwn, finite(heaviest));
        double safeTotal = Math.max(safeHeaviest, finite(total));
        if (safeHeaviest <= 0.0D) {
            return safeOwn;
        }
        double power = Math.max(0.0D, (Double.isFinite(exponent) ? exponent : 1.0D) - 1.0D);
        return safeOwn * Math.pow(safeTotal / safeHeaviest, power);
    }

    /** What a fold costs one drive, as a fraction of its full charge. See {@link #chargedMass}. */
    public static double foldCost(WarpCost.Formula formula, double distance, double own, double total,
                                  double heaviest, double exponent) {
        return formula.cost(distance, chargedMass(own, total, heaviest, exponent));
    }

    /** Whether even a full charge would not pay for one drive's share of the fold. */
    public static boolean exceedsFullCharge(WarpCost.Formula formula, double distance, double own,
                                            double total, double heaviest, double exponent) {
        return formula.exceedsFullCharge(distance, chargedMass(own, total, heaviest, exponent));
    }

    // ----------------------------------------------------------------- essence

    /**
     * Rift Essence a flagship's coupler needs in hand to begin a fold: one charge per escort for every
     * upkeep interval the fold can last. Asked for up front, and then spent a little at a time, so a
     * coupler can never run dry with its escorts half way down the corridor.
     */
    public static int essenceForFold(int escorts, int costPerEscort, int foldTicks, int interval) {
        if (escorts <= 0 || costPerEscort <= 0) {
            return 0;
        }
        int charges = (int) Math.ceil(Math.max(1, foldTicks) / (double) Math.max(1, interval));
        long total = (long) escorts * costPerEscort * charges;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }
}
