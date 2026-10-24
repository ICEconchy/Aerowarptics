package uk.co.iceconchy.aerowarptics.drive;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import uk.co.iceconchy.aerowarptics.AWConfig;

/**
 * Progression tiers for the Rift Drive.
 *
 * <p>The enum carries only identity and the values a fresh config file should start with; every
 * number the warp system actually reads comes from {@link AWConfig#TIERS} at runtime. Adding a tier
 * means adding a constant here plus its block/item/recipe resources - no part of the warp sequence,
 * state machine or networking needs to change.
 */
public enum RiftDriveTier implements StringRepresentable {

    /** Short reach and slow to charge, but light enough to share an engine with the propellers. */
    MK_I("mk_i", 0,
            new Defaults(32, 64, 8.0D, 4_000.0D, 2_400, 60, 180, 100, 40, 2_400, 1.0D, 0.0D, 30.0D)),

    /** The workhorse: further, cheaper, and noticeably quicker to spin up. */
    MK_II("mk_ii", 1,
            new Defaults(64, 128, 12.0D, 24_000.0D, 1_800, 50, 150, 90, 35, 1_800, 1.6D, 0.0D, 45.0D)),

    /** Long haul. Expensive to build, efficient to run. */
    MK_III("mk_iii", 2,
            new Defaults(128, 256, 12.0D, 120_000.0D, 1_400, 40, 120, 80, 30, 1_400, 2.4D, 0.0D, 60.0D)),

    /** Effectively unlimited reach, at the price of a long recovery and an unstable exit. */
    SINGULARITY("singularity", 3,
            new Defaults(128, 256, 24.0D, 2_000_000.0D, 1_200, 30, 90, 70, 30, 6_000, 3.2D, 0.15D, 90.0D)),

    /**
     * A drive with the costs taken out. Creative only - nothing makes one.
     *
     * <p>Almost all of "no penalties" falls out of its numbers rather than out of special cases: one
     * RPM to run, one tick to charge, one tick of spin, no stress, no cooldown and no instability. The
     * flight itself is left alone at roughly the timings of a Mk III, because the journey through the
     * rift is the thing being tested, not a tax on it.
     *
     * <p>What it cannot express as numbers is handled by {@link #creative()}: it never stalls for want of
     * rotation, its range is not clamped by the server-wide maximum, and it pays nothing. That last was
     * once left to the numbers too, as an efficiency of a hundred, and a hundredth of a cost is still a
     * cost - it ran out of charge two and a half thousand kilometres into a cruise.
     */
    CREATIVE("creative", 4,
            new Defaults(1, 1, 0.0D, 1.0e8D, 1, 1, 1, 60, 25, 0, 100.0D, 0.0D, 90.0D));

    /**
     * Starting values written into a fresh config file.
     *
     * <p>The RPM and stress figures are sized to what an airship actually carries, which is Simulated's
     * Portable Engine: 2,048 su at 32 RPM, or 4,096 su at 64 RPM superheated. Create charges stress per
     * RPM on both sides, so a drive's real draw is its impact <em>times</em> the speed it is turned at,
     * and gearing a shaft up raises that draw without adding a single unit of capacity. The first
     * defaults forgot the multiplication - 16 per RPM looked light - and asked for a whole engine for a
     * Mk I and six for a Singularity, on top of whatever flew the ship. Now a Mk I at full rate takes a
     * quarter of one engine, a Mk II three quarters, a Mk III one superheated engine and a Singularity
     * two, or a steam engine. Every optimal speed is 32 RPM doubled, because cogs only double or halve:
     * the old 96 and 192 could not be reached from an engine without a speed controller, so in practice
     * they were run at 256 and cost more than their own figures said.
     *
     * @param minimumRpm     rotational speed below which the drive cannot charge
     * @param optimalRpm     rotational speed at which the drive charges at full rate
     * @param stressImpact   Create stress units consumed per RPM
     * @param maximumRange   furthest reachable anchor, in blocks
     * @param chargeTicks    ticks from empty to full at {@code optimalRpm}
     * @param stabilizeTicks spin needed for a jump next door, in ticks at full rate
     * @param stabilizeTicksFar spin needed for a jump at the limit of this drive's range
     * @param warpTicks      ticks spent in the corridor before the airship moves
     * @param arriveTicks    ticks spent settling after the airship moves
     * @param cooldownTicks  ticks of cooldown after a completed warp
     * @param costEfficiency cost divisor; higher is cheaper
     * @param instability    chance the exit point is scattered
     * @param cruiseSpeed    top speed of a free cruise, in blocks per second. The ship really flies
     *                       it, so these stay near what a server can load terrain at - roughly elytra
     *                       speed for a Mk I, three times it for a Singularity - rather than scaling with
     *                       range the way everything else here does
     */
    public record Defaults(int minimumRpm,
                           int optimalRpm,
                           double stressImpact,
                           double maximumRange,
                           int chargeTicks,
                           int stabilizeTicks,
                           int stabilizeTicksFar,
                           int warpTicks,
                           int arriveTicks,
                           int cooldownTicks,
                           double costEfficiency,
                           double instability,
                           double cruiseSpeed) {
    }

    /**
     * By serialised name, for the recipes that name a tier - a fold measured against a tier's reach, a
     * collapse that makes a tier's core.
     */
    public static final Codec<RiftDriveTier> CODEC = StringRepresentable.fromEnum(RiftDriveTier::values);

    private final String name;
    private final int index;
    private final Defaults defaults;

    RiftDriveTier(String name, int index, Defaults defaults) {
        this.name = name;
        this.index = index;
        this.defaults = defaults;
    }

    /**
     * Whether this tier is a creative-mode drive, exempt from the rules the others live by.
     *
     * <p>Only what a config value cannot say reads this: "never refuse to run for lack of rotation",
     * "ignore the server-wide range cap", "no minimum hop", "pay nothing" ({@code WarpCost.Formula.free})
     * and "untouched by a Rift Storm". Everything else that makes a creative drive painless is simply its
     * own numbers, which is why there is one flag here rather than a fork through the drive.
     */
    public boolean creative() {
        return this == CREATIVE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Stable ordinal used on the wire and in NBT. */
    public int index() {
        return index;
    }

    public Defaults defaults() {
        return defaults;
    }

    public String blockName() {
        return "rift_drive_" + name;
    }

    public String translationKey() {
        return "tier." + name;
    }

    public static RiftDriveTier byIndex(int index) {
        for (RiftDriveTier tier : values()) {
            if (tier.index == index) {
                return tier;
            }
        }
        return MK_I;
    }

    // ------------------------------------------------------------- live values

    public int minimumRpm() {
        return AWConfig.TIERS.get(this).minimumRpm().get();
    }

    public int optimalRpm() {
        return AWConfig.TIERS.get(this).optimalRpm().get();
    }

    public double stressImpact() {
        return AWConfig.TIERS.get(this).stressImpact().get();
    }

    public double maximumRange() {
        double configured = AWConfig.TIERS.get(this).maximumRange().get();
        // The global cap is a server's statement about how far players may travel. A creative drive is
        // not a player's tool, so it is not what that setting is about.
        return creative() ? configured : Math.min(configured, AWConfig.MAXIMUM_WARP_DISTANCE.get());
    }

    public int chargeTicks() {
        return AWConfig.TIERS.get(this).chargeTicks().get();
    }

    public int stabilizeTicks() {
        return AWConfig.TIERS.get(this).stabilizeTicks().get();
    }

    /** Spin needed for a jump at the very edge of this drive's range. */
    public int stabilizeTicksFar() {
        return AWConfig.TIERS.get(this).stabilizeTicksFar().get();
    }

    public int warpTicks() {
        return AWConfig.TIERS.get(this).warpTicks().get();
    }

    public int arriveTicks() {
        return AWConfig.TIERS.get(this).arriveTicks().get();
    }

    public int cooldownTicks() {
        return AWConfig.TIERS.get(this).cooldownTicks().get();
    }

    public double costEfficiency() {
        return AWConfig.TIERS.get(this).costEfficiency().get();
    }

    public double instability() {
        return AWConfig.TIERS.get(this).instability().get();
    }

    /** Blocks per second this tier's free cruise covers. */
    public double cruiseSpeed() {
        return AWConfig.TIERS.get(this).cruiseSpeed().get();
    }

    /** Escorts a flagship with this drive may lead: the configured figure, where minus one is unlimited. */
    public int flotillaEscorts() {
        return AWConfig.TIERS.get(this).flotillaEscorts().get();
    }
}
