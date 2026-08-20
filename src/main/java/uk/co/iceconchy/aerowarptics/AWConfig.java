package uk.co.iceconchy.aerowarptics;

import net.neoforged.neoforge.common.ModConfigSpec;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every tunable value the warp system uses.
 *
 * <p>Gameplay values live in the SERVER spec because the server is authoritative over charge, cost,
 * range and arrival; clients receive the resulting numbers over the network instead of computing
 * them locally. Only presentation settings live in the CLIENT spec.
 */
public final class AWConfig {

    private AWConfig() {
    }

    // ---------------------------------------------------------------- server

    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MINIMUM_WARP_DISTANCE;
    public static final ModConfigSpec.DoubleValue MAXIMUM_WARP_DISTANCE;
    public static final ModConfigSpec.DoubleValue BASE_WARP_COST;
    public static final ModConfigSpec.DoubleValue DISTANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SIZE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASS_REFERENCE;

    public static final ModConfigSpec.DoubleValue ARRIVAL_GROUND_BUFFER;
    public static final ModConfigSpec.IntValue SAFE_ARRIVAL_RADIUS;
    public static final ModConfigSpec.IntValue SAFE_ARRIVAL_VERTICAL_RADIUS;
    public static final ModConfigSpec.IntValue SAFE_ARRIVAL_STEP;
    public static final ModConfigSpec.DoubleValue ARRIVAL_CLEARANCE;
    public static final ModConfigSpec.IntValue MAX_SHIP_BLOCK_SAMPLES;

    public static final ModConfigSpec.DoubleValue RIFT_LEAD_DISTANCE;
    public static final ModConfigSpec.DoubleValue RIFT_RADIUS_FACTOR;
    public static final ModConfigSpec.DoubleValue APPROACH_SPEED;
    public static final ModConfigSpec.IntValue APPROACH_LIMIT_TICKS;
    public static final ModConfigSpec.IntValue RIFT_TRANSIT_TICKS;
    public static final ModConfigSpec.DoubleValue SPIN_MINIMUM_RATE;
    public static final ModConfigSpec.DoubleValue EMERGE_DISTANCE_FACTOR;

    public static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION_WARP;
    public static final ModConfigSpec.BooleanValue ALLOW_REDSTONE_INITIATION;
    public static final ModConfigSpec.BooleanValue ALLOW_PUBLIC_ANCHORS;
    public static final ModConfigSpec.BooleanValue ALLOW_PRIVATE_ANCHORS;
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_ABOARD;
    public static final ModConfigSpec.IntValue MAX_INTERACTION_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_ANCHORS_PER_PLAYER;

    public static final ModConfigSpec.DoubleValue FAILURE_CHARGE_PENALTY;
    public static final ModConfigSpec.IntValue FAILURE_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue DANGEROUS_FAILURES;
    public static final ModConfigSpec.BooleanValue TRACE_WARPS;
    public static final ModConfigSpec.DoubleValue DANGEROUS_FAILURE_IMPULSE;

    /** Per-tier settings, seeded from {@link RiftDriveTier#defaults()}. */
    public static final Map<RiftDriveTier, TierConfig> TIERS = new EnumMap<>(RiftDriveTier.class);

    public static final ModConfigSpec SERVER_SPEC;

    /** One tier's worth of server config values. */
    public record TierConfig(
            ModConfigSpec.IntValue minimumRpm,
            ModConfigSpec.IntValue optimalRpm,
            ModConfigSpec.DoubleValue stressImpact,
            ModConfigSpec.DoubleValue maximumRange,
            ModConfigSpec.IntValue chargeTicks,
            ModConfigSpec.IntValue stabilizeTicks,
            ModConfigSpec.IntValue stabilizeTicksFar,
            ModConfigSpec.IntValue warpTicks,
            ModConfigSpec.IntValue arriveTicks,
            ModConfigSpec.IntValue cooldownTicks,
            ModConfigSpec.DoubleValue costEfficiency,
            ModConfigSpec.DoubleValue instability) {
    }

    static {
        SERVER_BUILDER.comment("Global limits on where a Rift Drive may take an airship.").push("range");
        MINIMUM_WARP_DISTANCE = SERVER_BUILDER
                .comment("Warps shorter than this, in blocks, are rejected. A rift is not worth opening for a short hop.")
                .defineInRange("minimumWarpDistance", 128.0D, 0.0D, 1.0e7D);
        MAXIMUM_WARP_DISTANCE = SERVER_BUILDER
                .comment("Hard upper bound on warp distance regardless of drive tier.")
                .defineInRange("maximumWarpDistance", 2_000_000.0D, 1.0D, 1.0e9D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Warp cost is a fraction of a full drive charge.",
                        "cost = baseWarpCost + distance * distanceMultiplier + (mass / massReference) * sizeMultiplier",
                        "then divided by the tier's costEfficiency.")
                .push("cost");
        BASE_WARP_COST = SERVER_BUILDER
                .comment("Flat cost of opening a rift at all.")
                .defineInRange("baseWarpCost", 0.20D, 0.0D, 1.0D);
        DISTANCE_MULTIPLIER = SERVER_BUILDER
                .comment("Additional cost per block of travel.")
                .defineInRange("distanceMultiplier", 0.00004D, 0.0D, 1.0D);
        SIZE_MULTIPLIER = SERVER_BUILDER
                .comment("Additional cost per massReference units of airship mass.")
                .defineInRange("sizeMultiplier", 0.15D, 0.0D, 10.0D);
        MASS_REFERENCE = SERVER_BUILDER
                .comment("Airship mass counting as one unit of sizeMultiplier. Sable reports a physical mass per sub-level.")
                .defineInRange("massReference", 40_000.0D, 1.0D, 1.0e9D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("How the exit point is chosen once the rift opens.").push("arrival");
        ARRIVAL_GROUND_BUFFER = SERVER_BUILDER
                .comment("Blocks of daylight left between the anchor and the airship's underside.",
                        "The search starts here and climbs, so an airship never materialises sitting on its anchor.")
                .defineInRange("arrivalGroundBuffer", 6.0D, 0.0D, 256.0D);
        SAFE_ARRIVAL_RADIUS = SERVER_BUILDER
                .comment("Horizontal radius, in blocks, searched around an anchor for a clear arrival volume.")
                .defineInRange("safeArrivalRadius", 48, 0, 512);
        SAFE_ARRIVAL_VERTICAL_RADIUS = SERVER_BUILDER
                .comment("Vertical radius, in blocks, searched around an anchor for a clear arrival volume.")
                .defineInRange("safeArrivalVerticalRadius", 32, 0, 512);
        SAFE_ARRIVAL_STEP = SERVER_BUILDER
                .comment("Spacing between candidate arrival positions. Larger values search faster but coarser.")
                .defineInRange("safeArrivalStep", 4, 1, 32);
        ARRIVAL_CLEARANCE = SERVER_BUILDER
                .comment("Extra clearance, in blocks, required around the airship on arrival.")
                .defineInRange("arrivalClearance", 2.0D, 0.0D, 32.0D);
        MAX_SHIP_BLOCK_SAMPLES = SERVER_BUILDER
                .comment("Upper bound on blocks tested per candidate arrival position.",
                        "Keeps the obstruction check cheap for very large airships.")
                .defineInRange("maxShipBlockSamples", 4096, 64, 200_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The flight through the rift: the run at the entry aperture, the passage",
                        "through it, and the run out of the far one. The corridor is flown inside the",
                        "entry aperture rather than anywhere else, so it needs no settings of its own -",
                        "how long it lasts comes from the drive's tier.")
                .push("flight");
        RIFT_LEAD_DISTANCE = SERVER_BUILDER
                .comment("Blocks between the bow and the entry rift, on top of half the hull's length.")
                .defineInRange("riftLeadDistance", 24.0D, 4.0D, 512.0D);
        RIFT_RADIUS_FACTOR = SERVER_BUILDER
                .comment("Rift aperture radius as a multiple of the hull's own radius.")
                .defineInRange("riftRadiusFactor", 1.35D, 1.0D, 8.0D);
        APPROACH_SPEED = SERVER_BUILDER
                .comment("Blocks per tick the airship flies at the entry rift.")
                .defineInRange("approachSpeed", 1.6D, 0.1D, 32.0D);
        APPROACH_LIMIT_TICKS = SERVER_BUILDER
                .comment("Ticks the run at the entry rift may take before the airship is pulled through anyway.")
                .defineInRange("approachLimitTicks", 120, 20, 1200);
        SPIN_MINIMUM_RATE = SERVER_BUILDER
                .comment("How fast a drive spins up when turning at exactly its minimum speed, as a",
                        "fraction of its rate at optimal speed. Lower makes the shaft you feed it",
                        "matter more; 1.0 makes rotational speed irrelevant to spin-up.")
                .defineInRange("spinMinimumRate", 0.35D, 0.05D, 1.0D);
        RIFT_TRANSIT_TICKS = SERVER_BUILDER
                .comment("Ticks the hull spends being drawn through an aperture, bow to stern.",
                        "The same figure is used at both ends, so a departure and an arrival mirror",
                        "each other. Speed is worked out from the hull's own length, so a large ship",
                        "does not take any longer to pass through than a small one.")
                .defineInRange("riftTransitTicks", 60, 5, 600);
        EMERGE_DISTANCE_FACTOR = SERVER_BUILDER
                .comment("How far back from its resting place the airship appears, as a multiple of hull length.",
                        "The whole run-out is checked for obstructions before the warp is allowed to start.")
                .defineInRange("emergeDistanceFactor", 1.25D, 0.0D, 8.0D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Who may use drives and anchors.").push("permissions");
        ALLOW_REDSTONE_INITIATION = SERVER_BUILDER
                .comment("Allow a redstone signal into a Rift Drive to start a warp.",
                        "The signal can only fire a course a player already set on an Astrolabe",
                        "Cartography Table, so wiring a drive up grants no access the person who set",
                        "the course did not already have. Turn this off to require a hand on a control.")
                .define("allowRedstoneInitiation", true);
        ALLOW_CROSS_DIMENSION_WARP = SERVER_BUILDER
                .comment("Allow warping to anchors in other dimensions.",
                        "Sable sub-levels are bound to the plot grid of a single ServerLevel and expose no API for",
                        "moving one between dimensions, so this stays false unless an extension registers a handler",
                        "with CrossDimensionWarp.setHandler.")
                .define("allowCrossDimensionWarp", false);
        ALLOW_PUBLIC_ANCHORS = SERVER_BUILDER
                .comment("Allow anchors that anybody may warp to.")
                .define("allowPublicAnchors", true);
        ALLOW_PRIVATE_ANCHORS = SERVER_BUILDER
                .comment("Allow anchors restricted to their owner.")
                .define("allowPrivateAnchors", true);
        REQUIRE_PLAYER_ABOARD = SERVER_BUILDER
                .comment("Require the initiating player to be standing on the airship that is about to warp.")
                .define("requirePlayerAboard", true);
        MAX_INTERACTION_DISTANCE = SERVER_BUILDER
                .comment("Maximum distance, in blocks, between the player and the Rift Drive they are commanding.")
                .defineInRange("maxInteractionDistance", 12, 1, 64);
        MAX_ANCHORS_PER_PLAYER = SERVER_BUILDER
                .comment("Maximum number of registered Warp Anchors a single player may own. 0 disables the limit.")
                .defineInRange("maxAnchorsPerPlayer", 0, 0, 10_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("What happens when a warp cannot complete.").push("failure");
        FAILURE_CHARGE_PENALTY = SERVER_BUILDER
                .comment("Fraction of the drive's charge lost when a warp aborts.")
                .defineInRange("failureChargePenalty", 0.35D, 0.0D, 1.0D);
        FAILURE_COOLDOWN_TICKS = SERVER_BUILDER
                .comment("Ticks the drive spends in its error state after an aborted warp.")
                .defineInRange("failureCooldownTicks", 200, 0, 72_000);
        DANGEROUS_FAILURES = SERVER_BUILDER
                .comment("When enabled, an aborted warp also shoves the airship around.",
                        "Off by default: the addon never damages or deletes an airship as a failure consequence.")
                .define("dangerousFailures", false);
        DANGEROUS_FAILURE_IMPULSE = SERVER_BUILDER
                .comment("Impulse magnitude applied by a dangerous failure, relative to airship mass.")
                .defineInRange("dangerousFailureImpulse", 1.5D, 0.0D, 100.0D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Diagnostics. Off by default and of no interest during normal play.")
                .push("debug");
        TRACE_WARPS = SERVER_BUILDER
                .comment("Log every warp in detail: the plan, each stage change, both teleports and any",
                        "abort, with the numbers behind them - where the hull is relative to each",
                        "aperture, how far its bow and stern are from the plane, and how long each stage",
                        "actually took. Written at INFO so it lands in latest.log without touching the",
                        "logging configuration. One line per stage change, so a warp costs about ten lines.",
                        "Turn this on when reporting that a warp looked wrong; leave it off otherwise.")
                .define("traceWarps", false);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Per-tier Rift Drive settings. New tiers only need an enum entry plus these values.")
                .push("drives");
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            RiftDriveTier.Defaults d = tier.defaults();
            SERVER_BUILDER.push(tier.getSerializedName());
            TIERS.put(tier, new TierConfig(
                    SERVER_BUILDER.comment("Rotational speed below which the drive cannot charge at all.")
                            .defineInRange("minimumRpm", d.minimumRpm(), 1, 4096),
                    SERVER_BUILDER.comment("Rotational speed at which the drive charges at its full rate.")
                            .defineInRange("optimalRpm", d.optimalRpm(), 1, 4096),
                    SERVER_BUILDER.comment("Create stress impact of this drive, in stress units per RPM.")
                            .defineInRange("stressImpact", d.stressImpact(), 0.0D, 8192.0D),
                    SERVER_BUILDER.comment("Maximum warp distance this tier can reach, in blocks.")
                            .defineInRange("maximumRange", d.maximumRange(), 1.0D, 1.0e9D),
                    SERVER_BUILDER.comment("Ticks needed to charge from empty to full at optimalRpm.")
                            .defineInRange("chargeTicks", d.chargeTicks(), 1, 432_000),
                    SERVER_BUILDER.comment("Spin needed to open a rift to somewhere close by, in ticks at",
                                    "full rate. A drive turning below its optimal speed takes longer.")
                            .defineInRange("stabilizeTicks", d.stabilizeTicks(), 1, 12_000),
                    SERVER_BUILDER.comment("Spin needed to open a rift at the very limit of this drive's range.",
                                    "Everything between here and stabilizeTicks is interpolated on a log",
                                    "curve, so a short hop is cheap and a long haul is a commitment.")
                            .defineInRange("stabilizeTicksFar", d.stabilizeTicksFar(), 1, 48_000),
                    SERVER_BUILDER.comment("Ticks the airship spends in the warp corridor.")
                            .defineInRange("warpTicks", d.warpTicks(), 5, 12_000),
                    SERVER_BUILDER.comment("Ticks the airship takes to coast out of the exit rift and settle.")
                            .defineInRange("arriveTicks", d.arriveTicks(), 5, 12_000),
                    SERVER_BUILDER.comment("Ticks of cooldown after a completed warp.")
                            .defineInRange("cooldownTicks", d.cooldownTicks(), 0, 432_000),
                    SERVER_BUILDER.comment("Cost divisor. Higher is more efficient.")
                            .defineInRange("costEfficiency", d.costEfficiency(), 0.01D, 100.0D),
                    SERVER_BUILDER.comment("Chance per warp that the drive scatters its arrival point.")
                            .defineInRange("instability", d.instability(), 0.0D, 1.0D)));
            SERVER_BUILDER.pop();
        }
        SERVER_BUILDER.pop();

        SERVER_SPEC = SERVER_BUILDER.build();
    }

    // ---------------------------------------------------------------- client

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue RIFT_DISTORTION;
    public static final ModConfigSpec.BooleanValue WARP_CORRIDOR;
    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ModConfigSpec.DoubleValue EFFECT_VOLUME;

    public static final ModConfigSpec CLIENT_SPEC;

    static {
        CLIENT_BUILDER.comment("Presentation of the warp sequence. None of this affects gameplay.").push("visualEffects");
        PARTICLE_DENSITY = CLIENT_BUILDER
                .comment("Scales every particle count the warp effects emit. 0 disables warp particles.")
                .defineInRange("particleDensity", 1.0D, 0.0D, 4.0D);
        RIFT_DISTORTION = CLIENT_BUILDER
                .comment("Draw the spatial distortion disc when a rift forms.")
                .define("riftDistortion", true);
        WARP_CORRIDOR = CLIENT_BUILDER
                .comment("Draw the corridor streaks and wake while the airship is in warp.")
                .define("warpCorridor", true);
        SCREEN_SHAKE = CLIENT_BUILDER
                .comment("Shake the camera during rift formation and exit.")
                .define("screenShake", true);
        EFFECT_VOLUME = CLIENT_BUILDER
                .comment("Volume multiplier for warp sounds emitted by this mod.")
                .defineInRange("effectVolume", 1.0D, 0.0D, 2.0D);
        CLIENT_BUILDER.pop();

        CLIENT_SPEC = CLIENT_BUILDER.build();
    }
}
