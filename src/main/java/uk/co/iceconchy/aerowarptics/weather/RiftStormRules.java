package uk.co.iceconchy.aerowarptics.weather;

import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/**
 * What a Rift Storm does to a drive, as arithmetic.
 *
 * <p>Kept apart from {@link RiftStorm} and free of any config lookup, so the rules take plain numbers
 * and can be tested directly. The drive asks {@link RiftStorm} with its own tier; that reads the live
 * config and the storm, and hands the numbers here.
 *
 * <p>A storm is space already coming apart, which cuts both ways. A drive tearing it open has less to
 * recover from afterwards, so its cooldown is shortened. But every drive's exit becomes as unreliable as
 * a Singularity's: the Singularity's own {@code instability} is added on top of the drive's.
 */
public final class RiftStormRules {

    private RiftStormRules() {
    }

    /**
     * A completed warp's cooldown.
     *
     * @param base       the tier's configured cooldown, in ticks
     * @param raging     whether a storm is raging over the drive
     * @param multiplier fraction of the cooldown left during a storm, in {@code [0, 1]}
     */
    public static int cooldownTicks(int base, boolean raging, double multiplier) {
        if (!raging || base <= 0) {
            return Math.max(0, base);
        }
        double fraction = Math.max(0.0D, Math.min(1.0D, multiplier));
        return (int) Math.round(base * fraction);
    }

    /**
     * The chance this warp scatters its exit.
     *
     * <p>The Singularity is left as it is: it is the drive the storm is borrowing from, and it already
     * carries that instability - adding its own figure to itself would make a storm punish most the one
     * drive whose exit was already unsure. A creative drive is left alone for the reason it is left
     * alone everywhere else: it is defined by having no penalties.
     *
     * @param tier                   the drive's tier
     * @param ownInstability         that tier's configured instability
     * @param singularityInstability the Singularity's configured instability
     * @param raging                 whether a storm is raging over the drive
     */
    public static double instability(RiftDriveTier tier, double ownInstability,
                                     double singularityInstability, boolean raging) {
        double own = clamp01(ownInstability);
        if (!raging || tier.creative() || tier == RiftDriveTier.SINGULARITY) {
            return own;
        }
        return clamp01(own + singularityInstability);
    }

    /**
     * A length between two configured bounds, inclusive, from a uniform fraction.
     *
     * <p>Bounds given the wrong way round are read the right way round: a config file with the least
     * above the most describes a range, just carelessly, and it is not worth refusing to load over.
     *
     * @param fraction uniform in {@code [0, 1)}
     */
    public static int roll(int least, int most, double fraction) {
        int low = Math.min(least, most);
        int high = Math.max(least, most);
        double f = Math.max(0.0D, Math.min(1.0D, fraction));
        long span = (long) high - low + 1L;
        return (int) Math.min(high, low + (long) Math.floor(f * span));
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
