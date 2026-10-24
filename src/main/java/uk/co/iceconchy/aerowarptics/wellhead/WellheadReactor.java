package uk.co.iceconchy.aerowarptics.wellhead;

/**
 * A Rift Wellhead's working: what it digests, how strained its fissure is, and how sound its frame.
 *
 * <p>Shaped like {@code CollapseRitual} and for the same reason free of Minecraft: a well can be run here, in a
 * test, for an hour of its life in a millisecond. It is stepped once a second.
 *
 * <h2>Why it behaves like a reactor</h2>
 * Fed matter is digested into essence, and digesting strains the tear. Two things rise with strain: how fast the
 * well digests (so a strained well pays better), and how much each millibucket strains it (so a strained well
 * strains itself). Pressure therefore climbs with the <em>square</em> of strain, while what holds it down -
 * rotation, a cage of stabilisers, a sound frame - only pushes back in proportion to it. A line and a parabola
 * meet twice or not at all:
 * <ul>
 *   <li>the lower meeting is where a well <b>settles</b>: nudge it and it comes back;</li>
 *   <li>the upper is the <b>cliff</b>: past it pressure outruns damping for good, and only cutting the feed or a
 *       scram brings it back;</li>
 *   <li>with too little damping they never meet, and the well runs away from the first block fed.</li>
 * </ul>
 * So weakening the cage does not simply make a well worse. It moves the settling point up - more yield - and the
 * cliff down towards it, until they touch and there is nowhere to stand. Running hot is choosing how close.
 *
 * <p>Nothing here reads config: every number arrives in {@link Settings} and {@link Conditions}.
 */
public final class WellheadReactor {

    /** Below this a well is calm, and its frame mends. */
    public static final double STRAINING = 0.35D;
    /** From this the tear is being torn: the frame wears and the fissure bleeds. */
    public static final double TEARING = 0.65D;
    /** From this the alarm sounds. Still recoverable. */
    public static final double CRITICAL = 0.90D;
    /** At this it is not. */
    public static final double MELTDOWN = 1.0D;

    /** How a well is doing, by its strain alone. */
    public enum Band implements net.minecraft.util.StringRepresentable {
        CALM, STRAINING, TEARING, CRITICAL, MELTDOWN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public String translationKey() {
            return "wellhead.band." + getSerializedName();
        }

        public static Band of(double strain) {
            if (strain >= WellheadReactor.MELTDOWN) {
                return MELTDOWN;
            }
            if (strain >= WellheadReactor.CRITICAL) {
                return CRITICAL;
            }
            if (strain >= WellheadReactor.TEARING) {
                return TEARING;
            }
            return strain >= WellheadReactor.STRAINING ? STRAINING : CALM;
        }
    }

    /**
     * A tier's tuning.
     *
     * @param digestion      millibuckets a second a calm well digests
     * @param yieldGain      how much faster a fully strained one does: digestion is multiplied by 1 + this x strain
     * @param pressure       strain a second a calm well digesting feed of strain 1 puts on its fissure
     * @param feedback       how much harder each millibucket strains a strained tear: 1 + this x strain
     * @param damping        strain a second, per unit of strain, a perfectly kept well takes off
     * @param cageFloor      the share of that damping rotation gives alone, with no stabilisers at all
     * @param lopsided       how much of a cage's work a wholly one-sided one loses
     * @param stormFactor    what a raging Rift Storm multiplies pressure and digestion by
     * @param overflow       strain a second from a tank that can take none of what is digested
     * @param leak           strain a second an untended, unfed tear loses of its own accord, so none is ever stuck
     * @param wear           integrity a second a tearing well loses
     * @param mend           integrity a second a calm one gets back
     * @param integrityFloor the least a frame's damping ever falls to through wear
     * @param bleed          millibuckets a second a tearing fissure loses from its reservoir
     */
    public record Settings(double digestion, double yieldGain, double pressure, double feedback, double damping,
                           double cageFloor, double lopsided, double stormFactor, double overflow, double leak,
                           double wear, double mend, double integrityFloor, double bleed) {
    }

    /**
     * What a well is standing in this second.
     *
     * @param speed       how fast the collar is turning, unsigned
     * @param stabilisers switched-on Spatial Stabilisers in reach
     * @param needed      how many this tier wants for a full cage
     * @param imbalance   how lopsided the cage is, 0 to 1 - {@code CollapseInstability.imbalance}
     * @param tolerance   what the fissure's own size divides pressure by: a large tear takes more
     * @param tankRoom    millibuckets the tank can still take
     * @param breached    the frame is open: nothing damps but the tear's own leak
     * @param grace       just loaded, when Create reports no speed for a few ticks: nothing moves
     */
    public record Conditions(double speed, double minimumSpeed, double optimalSpeed, int stabilisers, int needed,
                             double imbalance, boolean storm, double tolerance, int tankRoom, boolean breached,
                             boolean grace) {
    }

    /**
     * What a second's work came to.
     *
     * @param essence millibuckets for the tank
     * @param spilt   millibuckets digested with nowhere to go, lost back into the tear
     * @param bleed   millibuckets the fissure lost from its reservoir
     */
    public record Step(int essence, int spilt, int bleed, Band band) {
    }

    /** Essence still to be had from what is in the gullet, in millibuckets. */
    private double matter;
    /** How straining that matter is, per millibucket, averaged over the lot. */
    private double feedStrain;
    private double strain;
    private double integrity = 1.0D;
    /** The parts of a millibucket not yet handed on, so a slow well still fills its tank. */
    private double essenceCarry;
    private double bleedCarry;

    public WellheadReactor() {
    }

    public WellheadReactor(double matter, double feedStrain, double strain, double integrity) {
        this.matter = Math.max(0.0D, matter);
        this.feedStrain = Math.max(0.0D, feedStrain);
        this.strain = clamp01(strain);
        this.integrity = clamp01(integrity);
    }

    /** Puts one item's worth into the gullet. */
    public void feed(double essence, double strainPerMillibucket) {
        if (essence <= 0.0D) {
            return;
        }
        double total = matter + essence;
        feedStrain = (feedStrain * matter + Math.max(0.0D, strainPerMillibucket) * essence) / total;
        matter = total;
    }

    /** Throws the gullet away: a scram. The strain already there is left to be damped. */
    public void dump() {
        matter = 0.0D;
        feedStrain = 0.0D;
    }

    /**
     * The frame is broken open with matter still in it: all of it goes into the tear at once.
     *
     * @param capacity what a full gullet holds, in millibuckets - a full one is a meltdown outright
     */
    public void breach(double capacity) {
        if (capacity > 0.0D) {
            strain = clamp01(strain + matter * feedStrain / capacity);
        }
        dump();
    }

    /** One second. */
    public Step step(Conditions conditions, Settings settings) {
        if (conditions.grace() || strain >= MELTDOWN) {
            return new Step(0, 0, 0, Band.of(strain));
        }
        double storm = conditions.storm() ? settings.stormFactor() : 1.0D;
        double want = settings.digestion() * (1.0D + settings.yieldGain() * strain) * storm;
        double digested = Math.min(want, matter);
        // The share of a calm second's digestion that happened: a well running out of matter strains less.
        double worked = settings.digestion() <= 0.0D ? 0.0D : digested / settings.digestion();
        matter -= digested;

        essenceCarry += digested;
        int whole = (int) Math.floor(essenceCarry);
        essenceCarry -= whole;
        int essence = Math.min(whole, Math.max(0, conditions.tankRoom()));
        int spilt = whole - essence;

        double pressure = settings.pressure() * worked * feedStrain * (1.0D + settings.feedback() * strain)
                / Math.max(0.25D, conditions.tolerance());
        if (whole > 0 && spilt > 0) {
            pressure += settings.overflow() * spilt / whole;
        }
        double relief = settings.leak() + damping(conditions, settings) * strain;
        strain = clamp01(strain + pressure - relief);

        Band band = Band.of(strain);
        int bleed = 0;
        if (band.ordinal() >= Band.TEARING.ordinal()) {
            integrity = Math.max(settings.integrityFloor(), integrity - settings.wear());
            bleedCarry += settings.bleed();
            bleed = (int) Math.floor(bleedCarry);
            bleedCarry -= bleed;
        } else if (band == Band.CALM) {
            integrity = Math.min(1.0D, integrity + settings.mend());
        }
        if (matter <= 0.0D) {
            matter = 0.0D;
            feedStrain = 0.0D;
        }
        return new Step(essence, spilt, bleed, band);
    }

    /** Strain a second, per unit of strain, that these conditions take off this well. */
    public double damping(Conditions conditions, Settings settings) {
        if (conditions.breached()) {
            return 0.0D;
        }
        return settings.damping() * speedShare(conditions) * cage(conditions, settings) * integrity;
    }

    /** What rotation gives: nothing below the minimum, half at it, all of it at the optimal speed. */
    public static double speedShare(Conditions conditions) {
        double speed = Math.abs(conditions.speed());
        if (speed < conditions.minimumSpeed() || speed <= 0.0D) {
            return 0.0D;
        }
        if (conditions.optimalSpeed() <= conditions.minimumSpeed()) {
            return 1.0D;
        }
        return 0.5D + 0.5D * clamp01((speed - conditions.minimumSpeed())
                / (conditions.optimalSpeed() - conditions.minimumSpeed()));
    }

    /** What the cage gives: the floor with no stabilisers, all of it with a full and balanced ring. */
    public static double cage(Conditions conditions, Settings settings) {
        double share = conditions.needed() <= 0 ? 1.0D
                : clamp01(Math.max(0, conditions.stabilisers()) / (double) conditions.needed());
        share *= 1.0D - settings.lopsided() * clamp01(conditions.imbalance());
        return settings.cageFloor() + (1.0D - settings.cageFloor()) * share;
    }

    /**
     * Where the strain is going: what it comes to if these conditions hold and the gullet is kept full. What the
     * panel shows beside the gauge, so a well that is going to run away says so while it is still calm.
     */
    public double heading(Conditions conditions, Settings settings, int seconds) {
        WellheadReactor ahead = new WellheadReactor(Math.max(matter, 1.0D), matter > 0.0D ? feedStrain : 1.0D,
                strain, integrity);
        Conditions roomy = new Conditions(conditions.speed(), conditions.minimumSpeed(), conditions.optimalSpeed(),
                conditions.stabilisers(), conditions.needed(), conditions.imbalance(), conditions.storm(),
                conditions.tolerance(), conditions.tankRoom(), conditions.breached(), false);
        for (int second = 0; second < seconds && ahead.strain < MELTDOWN; second++) {
            ahead.matter = Math.max(ahead.matter, settings.digestion() * 4.0D);
            ahead.step(roomy, settings);
        }
        return ahead.strain;
    }

    /** Millibuckets a second this well digests at its present strain, with matter to digest. */
    public double rate(Settings settings, boolean storm) {
        return settings.digestion() * (1.0D + settings.yieldGain() * strain) * (storm ? settings.stormFactor() : 1.0D);
    }

    public double matter() {
        return matter;
    }

    public double feedStrain() {
        return feedStrain;
    }

    public double strain() {
        return strain;
    }

    public double integrity() {
        return integrity;
    }

    public Band band() {
        return Band.of(strain);
    }

    /** For an operator's command, and nothing else: a well is never told its strain in play. */
    public void forceStrain(double value) {
        strain = clamp01(value);
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
