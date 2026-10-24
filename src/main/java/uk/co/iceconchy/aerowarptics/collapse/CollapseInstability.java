package uk.co.iceconchy.aerowarptics.collapse;

import java.util.List;

/**
 * How unstable a collapse is getting, as arithmetic.
 *
 * <p>Shaped like {@code StabiliserCushion} and for the same reason free of Minecraft: a ritual can be flown
 * through it in a test, and every lesson it teaches is an assertion rather than a hope.
 *
 * <p>Instability builds while the ritual winds, at a rate set by what is pushing it and what is holding it:
 * <ul>
 *   <li><b>Pressure</b> rises with a raging Rift Storm, with turning the cradle below its optimal speed, and
 *       with a tank that cannot keep up with what the ritual is burning.</li>
 *   <li><b>Damping</b> comes from switched-on Spatial Stabilisers around the cradle - their second job, the
 *       same verb they do aboard ship. More of them damp more, down to a floor; but a cage that is lopsided
 *       damps less, and one standing entirely to one side barely at all. Balance is the length of the sum of
 *       their offsets from the cradle over their count: a ring of opposites cancels to nothing. It is the
 *       stabiliser's own lesson again - symmetry, not quantity.</li>
 * </ul>
 * Held constant for the whole winding, the final instability is simply pressure times damping.
 */
public final class CollapseInstability {

    /**
     * The shape of the thing, in one place.
     *
     * @param pressure        what winds a collapse up with nothing else going on
     * @param stormFactor     how much a raging storm multiplies that by
     * @param speedWeight     added at no speed at all, scaled by how far below optimal the cradle turns
     * @param shortfallWeight added when the tank has paid nothing, scaled by how much it has fallen short
     * @param perStabiliser   how much each balanced stabiliser takes off the damping factor
     * @param floor           the least the damping factor can fall to, however many there are
     */
    public record Settings(double pressure, double stormFactor, double speedWeight, double shortfallWeight,
                           double perStabiliser, double floor) {

        public static final Settings DEFAULT = new Settings(0.6D, 1.5D, 0.4D, 0.6D, 0.2D, 0.25D);
    }

    private CollapseInstability() {
    }

    /**
     * How lopsided a cage of stabilisers is, 0 (perfectly balanced) to 1 (all to one side, as far away as
     * they may stand).
     *
     * @param offsets each active stabiliser's offset from the cradle, {x, y, z}, in blocks
     * @param reach   how far a stabiliser may stand and still count; normalises the answer
     */
    public static double imbalance(List<double[]> offsets, double reach) {
        if (offsets.isEmpty() || reach <= 0.0D) {
            return 1.0D;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (double[] offset : offsets) {
            x += offset[0];
            y += offset[1];
            z += offset[2];
        }
        double mean = Math.sqrt(x * x + y * y + z * z) / offsets.size();
        return clamp01(mean / reach);
    }

    /** The factor the pressure is multiplied by: 1 with no stabilisers, down towards the floor with many. */
    public static double damping(int stabilisers, double imbalance, Settings settings) {
        double base = Math.max(settings.floor(), 1.0D - Math.max(0, stabilisers) * settings.perStabiliser());
        return base + (1.0D - base) * clamp01(imbalance);
    }

    /**
     * What is winding the collapse up.
     *
     * @param speed     the cradle's speed, RPM
     * @param optimal   the recipe's drive tier's optimal speed
     * @param shortfall 0..1 how much of the essence due so far the tank has failed to pay
     */
    public static double pressure(boolean storm, double speed, double optimal, double shortfall, Settings settings) {
        double pressure = settings.pressure() * (storm ? settings.stormFactor() : 1.0D);
        if (optimal > 0.0D) {
            pressure += settings.speedWeight() * clamp01(1.0D - Math.abs(speed) / optimal);
        }
        return pressure + settings.shortfallWeight() * clamp01(shortfall);
    }

    /** Instability per whole winding, from what is pushing and what is holding. */
    public static double rate(double pressure, double damping) {
        return Math.max(0.0D, pressure) * Math.max(0.0D, damping);
    }

    /** Moves instability on by a share of the winding, never out of 0..1. */
    public static double step(double instability, double rate, double share) {
        return clamp01(instability + rate * Math.max(0.0D, share));
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
