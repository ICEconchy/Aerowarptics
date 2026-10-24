package uk.co.iceconchy.aerowarptics.cooling;

/**
 * How much of a Rift Drive's cooldown is left once what stands round it has done its work, as arithmetic.
 *
 * <p>Two things cool a drive. A Cooling Vent is the machine for it: turned by a shaft, it carries the heat
 * of a warp out as steam, and each one halves the time the drive takes to recover. A block of ice is the
 * same idea with nothing to turn - a passive cooler, the way an unfuelled Blaze Burner is a passive heater -
 * and takes a tenth off.
 *
 * <p>A drive only has so much heat to give up, so the two share one limit: at most {@link Settings#most}
 * coolers count, vents and ice together. Vents are counted first, because a vent is the better of the two
 * and a pilot who bolts one on should never find it did nothing for standing next to the ice it replaced.
 * Ice fills whatever room the vents leave.
 *
 * <p>Kept free of Minecraft and of any config lookup, like {@code RiftStormRules}, so the rules take plain
 * numbers and can be tested for what they are. The drive counts what is round it and hands the numbers here.
 */
public final class DriveCooling {

    /**
     * What a fresh config file starts with: four coolers at most, a vent leaves a half, ice nine tenths.
     * Here rather than in {@code AWConfig} so the tests read the numbers a player gets, not a copy of them.
     */
    public static final Settings DEFAULTS = new Settings(4, 0.5D, 0.9D);

    /**
     * Stress a vent draws per RPM, by default. Half a Mk I's, the lightest drive's: a vent backed onto its
     * drive turns at the drive's speed, so it always costs less than the machine it is cooling.
     */
    public static final double DEFAULT_VENT_STRESS = 4.0D;

    /** The speed a vent has to be turned at, by default: a Portable Engine's own, so it needs no gearing. */
    public static final int DEFAULT_VENT_MINIMUM_RPM = 32;

    private DriveCooling() {
    }

    /**
     * How cooling is tuned. Read from the config by whoever asks.
     *
     * @param most      the most coolers one drive takes any good from, vents and ice together
     * @param ventShare fraction of the cooldown one turning vent leaves, in {@code [0, 1]}
     * @param iceShare  fraction of the cooldown one block of ice leaves, in {@code [0, 1]}
     */
    public record Settings(int most, double ventShare, double iceShare) {

        public Settings {
            most = Math.max(0, most);
            ventShare = clamp01(ventShare);
            iceShare = clamp01(iceShare);
        }
    }

    /**
     * The coolers a drive takes any good from, out of those round it.
     *
     * @param vents turning Cooling Vents that count
     * @param ice   blocks of ice that count
     */
    public record Fit(int vents, int ice) {

        /** Nothing cooling the drive: its cooldown is its own. */
        public static final Fit NONE = new Fit(0, 0);

        public Fit {
            vents = Math.max(0, vents);
            ice = Math.max(0, ice);
        }

        public int total() {
            return vents + ice;
        }

        public boolean any() {
            return total() > 0;
        }
    }

    /**
     * Which of the coolers round a drive count: vents first, then ice in whatever room they leave.
     *
     * @param vents every turning vent that answers to the drive
     * @param ice   every block of ice against the drive
     * @param most  the most that count, vents and ice together
     */
    public static Fit fit(int vents, int ice, int most) {
        int room = Math.max(0, most);
        int countedVents = Math.min(Math.max(0, vents), room);
        int countedIce = Math.min(Math.max(0, ice), room - countedVents);
        return new Fit(countedVents, countedIce);
    }

    /**
     * Whether the vent at this place in the drive's own order is one of those that count.
     *
     * @param index where the vent comes among the drive's turning vents, from nought
     */
    public static boolean ventCounts(int index, int most) {
        return index >= 0 && index < most;
    }

    /**
     * The fraction of a cooldown these coolers leave: one, with nothing fitted, falling towards nought.
     *
     * <p>Each cooler takes its share of what the ones before it left, so two vents leave a quarter and not
     * nothing - there is always some cooldown left to halve.
     */
    public static double share(Fit fit, Settings settings) {
        Fit counted = fit(fit.vents(), fit.ice(), settings.most());
        return Math.pow(settings.ventShare(), counted.vents()) * Math.pow(settings.iceShare(), counted.ice());
    }

    /**
     * A completed warp's cooldown, cooled.
     *
     * @param base the cooldown the warp earned before anything cooled it, in ticks
     */
    public static int cooldownTicks(int base, Fit fit, Settings settings) {
        if (base <= 0) {
            return 0;
        }
        return (int) Math.round(base * share(fit, settings));
    }

    private static double clamp01(double value) {
        // NaN compares false both ways and would otherwise pass straight through to a cooldown of nothing.
        return Double.isNaN(value) ? 1.0D : Math.max(0.0D, Math.min(1.0D, value));
    }
}
