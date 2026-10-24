package uk.co.iceconchy.aerowarptics.warp;

import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/**
 * The warp cost and range formulas.
 *
 * <p>Deliberately free of Minecraft types: {@link Formula} is a plain value object so the numbers can
 * be reasoned about and unit tested without a server. {@link #fromConfig(RiftDriveTier)} is the only
 * bridge to the live config.
 */
public final class WarpCost {

    /** What a fresh config file starts with, here so the pure classes and their tests can read them. */
    public static final double DEFAULT_BASE_COST = 0.20D;
    public static final double DEFAULT_DISTANCE_MULTIPLIER = 0.00004D;
    public static final double DEFAULT_SIZE_MULTIPLIER = 0.15D;
    public static final double DEFAULT_MASS_REFERENCE = 40_000.0D;
    public static final double DEFAULT_MINIMUM_DISTANCE = 128.0D;
    public static final double DEFAULT_MAXIMUM_DISTANCE = 2_000_000.0D;

    private WarpCost() {
    }

    /**
     * A tier's formula on a fresh config file, with no server to ask.
     *
     * <p>For the tests that hold the crafting layer's numbers - a fold measured against a tier's reach is
     * only checkable once there is a reach to measure - and for nothing that runs in a game, which reads
     * {@link #fromConfig}.
     */
    public static Formula defaults(RiftDriveTier tier) {
        RiftDriveTier.Defaults d = tier.defaults();
        double range = tier.creative() ? d.maximumRange() : Math.min(d.maximumRange(), DEFAULT_MAXIMUM_DISTANCE);
        Formula formula = new Formula(DEFAULT_BASE_COST, DEFAULT_DISTANCE_MULTIPLIER, DEFAULT_SIZE_MULTIPLIER,
                DEFAULT_MASS_REFERENCE, d.costEfficiency(), minimumDistance(tier, DEFAULT_MINIMUM_DISTANCE),
                unlimited(range) ? Double.POSITIVE_INFINITY : range);
        return tier.creative() ? formula.free() : formula;
    }

    /**
     * A frozen snapshot of the cost settings for one drive tier.
     *
     * @param baseCost           flat cost of opening a rift, as a fraction of a full charge
     * @param distanceMultiplier extra cost per block travelled
     * @param sizeMultiplier     extra cost per {@code massReference} units of airship mass
     * @param massReference      mass counting as one unit of {@code sizeMultiplier}
     * @param efficiency         tier cost divisor; higher is cheaper
     * @param minimumDistance    shortest warp the drive will attempt
     * @param maximumDistance    furthest the drive can reach
     */
    public record Formula(double baseCost,
                          double distanceMultiplier,
                          double sizeMultiplier,
                          double massReference,
                          double efficiency,
                          double minimumDistance,
                          double maximumDistance) {

        /**
         * Fraction of a full drive charge this warp consumes.
         *
         * @param distance blocks between the airship and its destination
         * @param mass     airship mass reported by Sable, or a volume proxy when mass is unavailable
         * @return a value in {@code [0, 1]}; a result of exactly {@code 1} means the jump needs a
         * completely full drive, and anything that would exceed {@code 1} is clamped so the caller
         * compares against charge rather than against an unbounded number
         */
        public double cost(double distance, double mass) {
            return Math.min(1.0D, Math.max(0.0D, unclampedCost(distance, mass)));
        }

        /** True when the raw cost exceeds a full charge, i.e. the jump is impossible for this tier. */
        public boolean exceedsFullCharge(double distance, double mass) {
            return unclampedCost(distance, mass) > 1.0D;
        }

        /**
         * The cost before it is clamped into a charge: above {@code 1} for a jump no charge will pay for.
         *
         * <p>What {@link #cost} hides, and what a refusal has to show - a pilot told "beyond drive range"
         * for an anchor well inside the tier's range is looking at a cost of more than a full charge, and
         * the only way to see that is this figure.
         */
        public double unclampedCost(double distance, double mass) {
            return (baseCost + distanceTerm(distance) + massTerm(mass)) / Math.max(0.01D, efficiency);
        }

        /** The distance part of the raw cost, before the tier's efficiency divides it. */
        public double distanceTerm(double distance) {
            return Math.max(0.0D, distance) * distanceMultiplier;
        }

        /** The mass part of the raw cost, before the tier's efficiency divides it. */
        public double massTerm(double mass) {
            return (Math.max(0.0D, mass) / Math.max(1.0D, massReference)) * sizeMultiplier;
        }

        /**
         * The furthest a hull of this mass can jump on a full charge.
         *
         * <p>Not the same thing as {@link #maximumDistance}, and the difference is a whole class of bug
         * report. The range is only reachable while the cost of reaching it fits in one charge; a heavy
         * hull spends so much of that charge on its own mass that it runs out of charge long before it runs
         * out of range, and every anchor between the two is refused as {@link WarpFailure#DESTINATION_TOO_FAR}.
         * With the shipped settings the long-haul tiers never reach their range at all, whatever their mass.
         *
         * @return the reach in blocks, never beyond {@link #maximumDistance}; or {@code -1} when no jump at
         * all is possible - the mass alone costs a full charge, or what is left will not reach
         * {@link #minimumDistance}
         */
        public double reach(double mass) {
            double spare = Math.max(0.01D, efficiency) - baseCost - massTerm(mass);
            if (spare < 0.0D) {
                return -1.0D;
            }
            double byCharge = distanceMultiplier > 0.0D ? spare / distanceMultiplier : Double.POSITIVE_INFINITY;
            double reach = Math.min(maximumDistance, byCharge);
            return reach < minimumDistance ? -1.0D : reach;
        }

        /** Range check, independent of charge. */
        public WarpFailure checkRange(double distance) {
            if (distance < minimumDistance) {
                return WarpFailure.DESTINATION_TOO_CLOSE;
            }
            if (distance > maximumDistance) {
                return WarpFailure.DESTINATION_TOO_FAR;
            }
            return WarpFailure.NONE;
        }

        /**
         * The same drive with nothing to pay: every jump and every cruise costs none of the charge.
         *
         * <p>What a creative drive is priced with. Its tier numbers alone could only make it cheap - the
         * efficiency divides a cost the server sets, and is capped - which left it refusing a jump past a
         * couple of thousand kilometres and dropping out of a cruise there, both for want of a charge that
         * refills in a tick. With nothing drawn, the charge is never spent, and a cruise is held only to the
         * range: {@link FreeCruise#limit} already answers the range when there is no cost per block.
         */
        public Formula free() {
            return new Formula(0.0D, 0.0D, 0.0D, massReference, efficiency, minimumDistance, maximumDistance);
        }
    }

    /**
     * Further apart than any two places in one world can be: corner to corner of the widest world border
     * the game allows, which is thirty million blocks each way from the middle. A reach at least this long
     * cannot refuse anything, so it is infinite in every way a player could find out.
     */
    public static final double WORLD_SPAN = Math.hypot(60_000_000.0D, 60_000_000.0D);

    /**
     * Whether a reach is infinite in practice - at least {@link #WORLD_SPAN}, or infinite outright.
     *
     * <p>A creative drive's range is a config value, a hundred thousand kilometres to start with, rather than
     * infinity itself, and the screens used to print it as that number. This is the test for printing
     * "infinite" instead; one configured shorter than a world still shows the number, because then it is one.
     */
    public static boolean unlimited(double reach) {
        return reach >= WORLD_SPAN;
    }

    /**
     * Reads the live server config for a tier.
     *
     * <p>A creative drive is {@link Formula#free}, and a reach no world is wide enough to test is made
     * infinite outright, so that a cruise - which counts every block flown, and could in principle go round
     * in circles past any finite number - is not held to one either.
     */
    public static Formula fromConfig(RiftDriveTier tier) {
        double range = tier.maximumRange();
        Formula formula = new Formula(
                AWConfig.BASE_WARP_COST.get(),
                AWConfig.DISTANCE_MULTIPLIER.get(),
                AWConfig.SIZE_MULTIPLIER.get(),
                AWConfig.MASS_REFERENCE.get(),
                tier.costEfficiency(),
                minimumDistance(tier, AWConfig.MINIMUM_WARP_DISTANCE.get()),
                unlimited(range) ? Double.POSITIVE_INFINITY : range);
        return tier.creative() ? formula.free() : formula;
    }

    /**
     * The shortest jump a tier will accept.
     *
     * <p>Zero for a creative drive. The floor exists so a pilot cannot spend a whole charge and a
     * cooldown on a hop they could have walked, and a creative drive has neither to spend - so all
     * the floor does there is refuse the commonest thing anybody wants a creative drive for, which
     * is moving a hull a few blocks to see what happens. The maximum is waived for the same tier for
     * the same sort of reason, over in {@code RiftDriveTier.maximumRange}.
     *
     * @param configured the server's {@code minimumWarpDistance}
     */
    public static double minimumDistance(RiftDriveTier tier, double configured) {
        return tier.creative() ? 0.0D : configured;
    }
}
