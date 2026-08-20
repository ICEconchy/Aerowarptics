package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.util.Mth;

/**
 * How long a Rift Drive takes to spin up before it can tear a rift.
 *
 * <p>Two things decide it, and they are deliberately separate. <em>Distance</em> sets how much spin
 * the jump needs - reaching further is more work, so a drive winding up for a hop across the valley
 * and one winding up for the far side of the world should not sound alike. <em>Rotational speed</em>
 * sets how fast that work gets done, so feeding the machine harder visibly and audibly pays off rather
 * than being a threshold you either clear or do not.
 *
 * <p>The distance curve is logarithmic because the ranges involved are not remotely linear: a Mk I
 * reaches four thousand blocks and a Singularity two million. On a straight line every warp anyone
 * actually makes would sit squashed against the bottom of the scale. Normalising against the drive's
 * <em>own</em> maximum means "as far as this machine can reach" always costs a full spin, whichever
 * machine it is.
 */
public final class SpinUp {

    /**
     * Distance at which the curve starts to flatten, in blocks.
     *
     * <p>Below this the extra spin per block is steep, which is what makes short hops feel cheap and
     * a serious crossing feel like a commitment.
     */
    private static final double KNEE = 256.0D;

    private SpinUp() {
    }

    /**
     * Spin a jump needs, in ticks-at-full-rate.
     *
     * @param distance     how far the destination is, in blocks
     * @param maximumRange the furthest this drive can reach
     * @param near         spin needed for a hop next door
     * @param far          spin needed for a jump at the very limit of the drive's range
     */
    public static double required(double distance, double maximumRange, int near, int far) {
        return Mth.lerp(reach(distance, maximumRange), near, far);
    }

    /**
     * How far along its range a jump sits, 0 to 1, on a log curve.
     *
     * @param maximumRange a drive with no stated range cannot scale anything, so it reads as nearby
     */
    public static double reach(double distance, double maximumRange) {
        if (!(maximumRange > 0.0D) || !(distance > 0.0D)) {
            return 0.0D;
        }
        double span = Math.log1p(maximumRange / KNEE);
        if (span <= 0.0D) {
            return 0.0D;
        }
        return Mth.clamp(Math.log1p(distance / KNEE) / span, 0.0D, 1.0D);
    }

    /**
     * Spin gained per tick at a given rotational speed, as a fraction of full rate.
     *
     * <p>At the drive's optimal speed it winds up at its rated pace; at its minimum it crawls. Over-
     * speeding buys nothing, matching how charging already behaves - there has to be a point past
     * which more shaft is simply more shaft.
     *
     * @param minRate the fraction of full rate a drive manages at its minimum speed
     */
    public static double rate(double rpm, int minimumRpm, int optimalRpm, double minRate) {
        if (rpm < minimumRpm) {
            return 0.0D;
        }
        double band = optimalRpm - minimumRpm;
        double through = band <= 0.0D ? 1.0D : Mth.clamp((rpm - minimumRpm) / band, 0.0D, 1.0D);
        return Mth.clamp(minRate + (1.0D - minRate) * through, 0.0D, 1.0D);
    }
}
