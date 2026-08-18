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

    private WarpCost() {
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
            double safeDistance = Math.max(0.0D, distance);
            double safeMass = Math.max(0.0D, mass);
            double raw = baseCost
                    + safeDistance * distanceMultiplier
                    + (safeMass / Math.max(1.0D, massReference)) * sizeMultiplier;
            double scaled = raw / Math.max(0.01D, efficiency);
            return Math.min(1.0D, Math.max(0.0D, scaled));
        }

        /** True when the raw cost exceeds a full charge, i.e. the jump is impossible for this tier. */
        public boolean exceedsFullCharge(double distance, double mass) {
            double raw = baseCost
                    + Math.max(0.0D, distance) * distanceMultiplier
                    + (Math.max(0.0D, mass) / Math.max(1.0D, massReference)) * sizeMultiplier;
            return raw / Math.max(0.01D, efficiency) > 1.0D;
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
    }

    /** Reads the live server config for a tier. */
    public static Formula fromConfig(RiftDriveTier tier) {
        return new Formula(
                AWConfig.BASE_WARP_COST.get(),
                AWConfig.DISTANCE_MULTIPLIER.get(),
                AWConfig.SIZE_MULTIPLIER.get(),
                AWConfig.MASS_REFERENCE.get(),
                tier.costEfficiency(),
                AWConfig.MINIMUM_WARP_DISTANCE.get(),
                tier.maximumRange());
    }
}
