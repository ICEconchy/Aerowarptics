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
    public static final ModConfigSpec.IntValue MAX_ARRIVAL_BLOCK_CHECKS;

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

    public static final ModConfigSpec.IntValue GATE_DIAL_COST;
    public static final ModConfigSpec.IntValue GATE_DIAL_COST_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue GATE_STRESS;
    public static final ModConfigSpec.DoubleValue GATE_STRESS_PER_BLOCK;
    public static final ModConfigSpec.IntValue GATE_MINIMUM_RPM;
    public static final ModConfigSpec.IntValue GATE_DIAL_TICKS;
    public static final ModConfigSpec.IntValue GATE_IDLE_TICKS;
    public static final ModConfigSpec.IntValue MAX_GATES_PER_PLAYER;

    public static final ModConfigSpec.IntValue CHUTE_COST_PER_ITEM;
    public static final ModConfigSpec.IntValue CHUTE_BATCH_SIZE;
    public static final ModConfigSpec.IntValue PROBE_COST;
    public static final ModConfigSpec.DoubleValue PROBE_COST_PER_BLOCK;
    public static final ModConfigSpec.IntValue PROBE_MINIMUM_RANGE;
    public static final ModConfigSpec.IntValue PROBE_MAXIMUM_RANGE;
    public static final ModConfigSpec.IntValue PROBE_REACH_TICKS;
    public static final ModConfigSpec.IntValue PROBE_TIMEOUT_TICKS;

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
        MAX_ARRIVAL_BLOCK_CHECKS = SERVER_BUILDER
                .comment("Most blocks the arrival check may read while proving one candidate clear.",
                        "",
                        "A safety valve, not a sampling rate. The check reads every block in the",
                        "volume a hull would occupy - it has to, because a check that skipped any of",
                        "them could clear a ship to arrive inside a one-block floor. Whole chunk",
                        "sections of open air are dismissed without being read, so in practice only",
                        "the parts of the volume with anything in them cost anything.",
                        "",
                        "Running out means the candidate could not be PROVED clear, and an unproven",
                        "volume is refused rather than accepted. Lowering this does not make arrivals",
                        "cheaper, it makes them fail.")
                .defineInRange("maxArrivalBlockChecks", 4_000_000, 4_096, 64_000_000);
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

        SERVER_BUILDER.comment("Rift Gates: the standing doorways vehicles and people drive through.")
                .push("gates");
        GATE_DIAL_COST = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, spent striking a connection, before size.")
                .defineInRange("gateDialCost", 250, 0, 1_000_000);
        GATE_DIAL_COST_PER_BLOCK = SERVER_BUILDER
                .comment("Extra millibuckets per block of opening. A bigger doorway is a bigger tear,",
                        "which is what stops the largest gate being the obvious one to build everywhere.")
                .defineInRange("gateDialCostPerBlock", 12, 0, 10_000);
        GATE_STRESS = SERVER_BUILDER
                .comment("Stress the gate draws while it is holding an aperture open, before size.",
                        "A gate standing dark costs nothing: essence opens a connection, rotation holds it.")
                .defineInRange("gateStress", 4.0D, 0.0D, 1_024.0D);
        GATE_STRESS_PER_BLOCK = SERVER_BUILDER
                .comment("Extra stress per block of opening while held open.")
                .defineInRange("gateStressPerBlock", 0.25D, 0.0D, 64.0D);
        GATE_MINIMUM_RPM = SERVER_BUILDER
                .comment("Rotation speed needed to hold a gate open. Falling below it drops the",
                        "connection - which is the whole of what makes rotation the holding cost.")
                .defineInRange("gateMinimumRpm", 32, 1, 256);
        GATE_DIAL_TICKS = SERVER_BUILDER
                .comment("Ticks between striking a connection and the aperture being safe to cross.")
                .defineInRange("gateDialTicks", 40, 5, 600);
        GATE_IDLE_TICKS = SERVER_BUILDER
                .comment("Ticks a connection is held with nothing crossing before it lets go.",
                        "0 holds it open indefinitely, which costs stress for as long as it stands.")
                .defineInRange("gateIdleTicks", 1_200, 0, 72_000);
        MAX_GATES_PER_PLAYER = SERVER_BUILDER
                .comment("Maximum number of Rift Gates a single player may own. 0 disables the limit.")
                .defineInRange("maxGatesPerPlayer", 0, 0, 10_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Probes: soundings thrown at ground nobody has stood on.")
                .push("probe");
        PROBE_COST = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, spent throwing a sounding, before distance.")
                .defineInRange("probeCost", 400, 0, 1_000_000);
        PROBE_COST_PER_BLOCK = SERVER_BUILDER
                .comment("Extra millibuckets per block of range. Reaching further costs more, which is",
                        "what keeps a probe a decision rather than something to spam at the horizon.")
                .defineInRange("probeCostPerBlock", 0.35D, 0.0D, 100.0D);
        PROBE_MINIMUM_RANGE = SERVER_BUILDER
                .comment("Closest a sounding may be thrown, in blocks. Below this a probe is only",
                        "surveying ground the ship could already see.")
                .defineInRange("probeMinimumRange", 512, 16, 1_000_000);
        PROBE_MAXIMUM_RANGE = SERVER_BUILDER
                .comment("Furthest a sounding may be thrown, in blocks. A probe is still bounded by the",
                        "drive's own range when the course is actually flown.")
                .defineInRange("probeMaximumRange", 24_000, 32, 10_000_000);
        PROBE_REACH_TICKS = SERVER_BUILDER
                .comment("Shortest a sounding takes. The wait is the server bringing that ground into",
                        "being, so this is a floor on it rather than a delay for its own sake.")
                .defineInRange("probeReachTicks", 60, 0, 12_000);
        PROBE_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("Ticks after which a sounding reads whatever has arrived and stops waiting.",
                        "A thin reading is reported as thin rather than being waited on forever.")
                .defineInRange("probeTimeoutTicks", 400, 20, 72_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Chutes: belt-fed holes in space that hand items to another chute.")
                .push("chute");
        CHUTE_COST_PER_ITEM = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, spent for each item that crosses.",
                        "Charged per item rather than per transfer, so moving a stack costs a stack's",
                        "worth - otherwise one expensive rift becomes free bulk logistics.",
                        "Set to 0 to make chutes run on nothing.")
                .defineInRange("chuteCostPerItem", 2, 0, 10_000);
        CHUTE_BATCH_SIZE = SERVER_BUILDER
                .comment("Most items one chute may send in a single transfer.",
                        "Caps the burst rather than the throughput: a chute still transfers several",
                        "times a second, but it cannot move an entire double chest in one tick.")
                .defineInRange("chuteBatchSize", 16, 1, 64);
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
