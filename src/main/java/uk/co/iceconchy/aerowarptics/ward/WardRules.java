package uk.co.iceconchy.aerowarptics.ward;

/**
 * The Rift Ward's limits and defaults, as arithmetic. Free of Minecraft and of the config.
 */
public final class WardRules {

    /** The narrowest a field may be set, in blocks: narrower and the machine is most of what it covers. */
    public static final int MIN_RADIUS = 16;

    /** What a ward just set down holds its field at. */
    public static final int DEFAULT_RADIUS = 32;

    /** How far a ship's hull is kept clear of a field's rim, besides its own half-length. */
    public static final double RIM_MARGIN = 4.0D;

    /** How far round the rim each step of the walk for a clear arrival goes, and how many steps each way. */
    public static final double RIM_STEP_DEGREES = 20.0D;
    public static final int RIM_STEPS = 4;

    /** The most networks one ward may name: enough for a server's factions, few enough to read on its panel. */
    public static final int MOST_NETWORKS = 8;

    /** Ticks the ward gives a redstone signal for, having turned something aside. */
    public static final int PULSE_TICKS = 20;

    private WardRules() {
    }

    /**
     * How far into a field a drive of this tier pushes before it is turned, as a share of the field's radius -
     * what a fresh config is written with. A Mk I is turned at the rim; a Singularity lands where it aimed
     * anywhere in the outer half; a Creative drive is not a ward's to stop.
     */
    public static double defaultPenetration(int tierIndex) {
        return switch (tierIndex) {
            case 0 -> 0.0D;
            case 1 -> 0.15D;
            case 2 -> 0.3D;
            case 3 -> 0.5D;
            default -> 1.0D;
        };
    }

    /** A radius as a ward will keep it: within what any ward may hold, and what this server allows. */
    public static int clampRadius(int wanted, int most) {
        return Math.max(MIN_RADIUS, Math.min(Math.max(MIN_RADIUS, most), wanted));
    }

    /** What a comparator reads off a ward: how full its charge is, in fifteenths, and never nought while it holds any. */
    public static int comparator(double charge, double capacity) {
        double fraction = WardCharge.fraction(charge, capacity);
        if (fraction <= 0.0D) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(fraction * 15.0D));
    }

    /**
     * A height to show in place of the real one, for a column a stranger's scan may not read: noise, but the same
     * noise every time the same column is asked for, so a second scan gives nothing the first did not.
     *
     * @param low  the lowest height the scan could have read there
     * @param span how many heights above that it could have read
     */
    public static int interference(int x, int z, long seed, int low, int span) {
        if (span <= 1) {
            return low;
        }
        long mixed = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        mixed ^= mixed >>> 31;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 29;
        return low + (int) Math.floorMod(mixed, (long) span);
    }
}
