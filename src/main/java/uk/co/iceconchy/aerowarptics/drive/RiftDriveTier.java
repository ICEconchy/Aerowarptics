package uk.co.iceconchy.aerowarptics.drive;

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

    /** Short reach, slow to charge, thirsty for rotational force. */
    MK_I("mk_i", 0,
            new Defaults(64, 128, 16.0D, 4_000.0D, 2_400, 60, 100, 40, 2_400, 1.0D, 0.0D)),

    /** The workhorse: further, cheaper, and noticeably quicker to spin up. */
    MK_II("mk_ii", 1,
            new Defaults(96, 192, 24.0D, 24_000.0D, 1_800, 50, 90, 35, 1_800, 1.6D, 0.0D)),

    /** Long haul. Expensive to build, efficient to run. */
    MK_III("mk_iii", 2,
            new Defaults(128, 256, 32.0D, 120_000.0D, 1_400, 40, 80, 30, 1_400, 2.4D, 0.0D)),

    /** Effectively unlimited reach, at the price of a long recovery and an unstable exit. */
    SINGULARITY("singularity", 3,
            new Defaults(192, 256, 48.0D, 2_000_000.0D, 1_200, 30, 70, 30, 6_000, 3.2D, 0.15D));

    /**
     * Starting values written into a fresh config file.
     *
     * @param minimumRpm     rotational speed below which the drive cannot charge
     * @param optimalRpm     rotational speed at which the drive charges at full rate
     * @param stressImpact   Create stress units consumed per RPM
     * @param maximumRange   furthest reachable anchor, in blocks
     * @param chargeTicks    ticks from empty to full at {@code optimalRpm}
     * @param stabilizeTicks ticks spent locking the destination
     * @param warpTicks      ticks spent in the corridor before the airship moves
     * @param arriveTicks    ticks spent settling after the airship moves
     * @param cooldownTicks  ticks of cooldown after a completed warp
     * @param costEfficiency cost divisor; higher is cheaper
     * @param instability    chance the exit point is scattered
     */
    public record Defaults(int minimumRpm,
                           int optimalRpm,
                           double stressImpact,
                           double maximumRange,
                           int chargeTicks,
                           int stabilizeTicks,
                           int warpTicks,
                           int arriveTicks,
                           int cooldownTicks,
                           double costEfficiency,
                           double instability) {
    }

    private final String name;
    private final int index;
    private final Defaults defaults;

    RiftDriveTier(String name, int index, Defaults defaults) {
        this.name = name;
        this.index = index;
        this.defaults = defaults;
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
        return Math.min(AWConfig.TIERS.get(this).maximumRange().get(), AWConfig.MAXIMUM_WARP_DISTANCE.get());
    }

    public int chargeTicks() {
        return AWConfig.TIERS.get(this).chargeTicks().get();
    }

    public int stabilizeTicks() {
        return AWConfig.TIERS.get(this).stabilizeTicks().get();
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
}
