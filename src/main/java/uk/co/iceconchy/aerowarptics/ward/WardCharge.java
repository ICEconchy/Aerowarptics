package uk.co.iceconchy.aerowarptics.ward;

/**
 * What a Rift Ward's field costs to hold and to use, as arithmetic.
 *
 * <p>A ward banks Rift Essence as a charge, drawn from its tank while it is turned. The charge is what the
 * field runs on: it pays the upkeep, a little all the time, and each thing it turns aside, a lot at once. A
 * ward that is not loaded draws nothing - nothing there is turning that anybody can vouch for - and goes on
 * paying upkeep out of what it had banked, counted up whenever something asks ({@link #after}). So no ward ever
 * has to keep its own ground loaded, and a base nobody is at is protected for as long as its charge lasts.
 *
 * <p>Free of Minecraft and of the config: the block entity and the registry hand the numbers in.
 */
public final class WardCharge {

    /** The radius the upkeep figure is quoted at; upkeep scales from here by area. */
    public static final double REFERENCE_RADIUS = 32.0D;

    private WardCharge() {
    }

    /**
     * Millibuckets a second a field of this radius costs to hold. By area, so twice as wide is four times as
     * dear - the price of a wide field is what keeps one from being thrown over a whole island.
     *
     * @param upkeepAtReference millibuckets a second at {@link #REFERENCE_RADIUS}
     */
    public static double upkeepPerSecond(double radius, double upkeepAtReference) {
        double scale = Math.max(0.0D, radius) / REFERENCE_RADIUS;
        return Math.max(0.0D, upkeepAtReference) * scale * scale;
    }

    /** A charge after some ticks of upkeep and nothing else: never below nought. */
    public static double after(double charge, long ticks, double upkeepPerSecond) {
        if (ticks <= 0L) {
            return Math.max(0.0D, charge);
        }
        return Math.max(0.0D, charge - upkeepPerSecond * ticks / 20.0D);
    }

    /**
     * What turning a vessel aside costs: a base, and more for a heavier one, in the unit a warp is priced in.
     *
     * @param massReference the mass that counts as one unit, the warp cost's own
     */
    public static long deflection(double base, double perMass, double mass, double massReference) {
        double units = massReference <= 0.0D ? 0.0D : Math.max(0.0D, mass) / massReference;
        return Math.round(Math.max(0.0D, base) + Math.max(0.0D, perMass) * units);
    }

    /** What turning a Rift Plotter's exit aside costs: a base, and more for a wider rift. */
    public static long plotted(double base, double perRadius, double radius) {
        return Math.round(Math.max(0.0D, base) + Math.max(0.0D, perRadius) * Math.max(0.0D, radius));
    }

    /**
     * Whether a field is standing.
     *
     * <p>A standing field stays up while it has any charge at all. One that has dropped stays down until the
     * charge is back above a share of what the ward can bank - without that gap a ward with a trickle coming in
     * would be up for a tick out of every few, and a rift would get through or not by which tick it was.
     *
     * @param wasStanding whether it stood a moment ago
     * @param enabled     whether its owner has it switched on
     * @param recover     the share of {@code capacity} a dropped field waits for, 0..1
     */
    public static boolean standing(boolean wasStanding, boolean enabled, double charge, double capacity, double recover) {
        if (!enabled || charge <= 0.0D) {
            return false;
        }
        return wasStanding || charge >= capacity * Math.max(0.0D, Math.min(1.0D, recover));
    }

    /** Whether a field can pay to turn something aside. */
    public static boolean affords(double charge, long cost) {
        return charge >= cost;
    }

    /** How full a ward is, 0..1. */
    public static double fraction(double charge, double capacity) {
        return capacity <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, charge / capacity));
    }
}
