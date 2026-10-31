package uk.co.iceconchy.aerowarptics;

import net.neoforged.neoforge.common.ModConfigSpec;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.cooling.DriveCooling;
import uk.co.iceconchy.aerowarptics.stabiliser.StabiliserCushion;

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
    public static final ModConfigSpec.DoubleValue CROSS_DIMENSION_DISTANCE;
    public static final ModConfigSpec.DoubleValue BASE_WARP_COST;
    public static final ModConfigSpec.DoubleValue DISTANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SIZE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASS_REFERENCE;

    public static final ModConfigSpec.DoubleValue ARRIVAL_GROUND_BUFFER;
    public static final ModConfigSpec.IntValue MAX_ARRIVAL_HEIGHT;
    public static final ModConfigSpec.IntValue SAFE_ARRIVAL_RADIUS;
    public static final ModConfigSpec.IntValue SAFE_ARRIVAL_VERTICAL_RADIUS;
    public static final ModConfigSpec.IntValue SAFE_ARRIVAL_STEP;
    public static final ModConfigSpec.DoubleValue ARRIVAL_CLEARANCE;
    public static final ModConfigSpec.IntValue MAX_ARRIVAL_BLOCK_CHECKS;
    public static final ModConfigSpec.DoubleValue ARRIVAL_BLOCK_CHECK_SCALE;
    public static final ModConfigSpec.IntValue MAX_ARRIVAL_CHUNKS;
    public static final ModConfigSpec.IntValue ARRIVAL_PRELOAD_TIMEOUT_TICKS;
    public static final ModConfigSpec.BooleanValue KEEP_AIRSHIPS_LOADED;
    public static final ModConfigSpec.BooleanValue REQUIRE_LOADED_ARRIVAL;

    public static final ModConfigSpec.IntValue RESIDENCY_ROAM_LIMIT;
    public static final ModConfigSpec.BooleanValue ASTROLABE_LOADS_PREVIEWS;
    public static final ModConfigSpec.BooleanValue BEACON_REACHES_SHIPS;
    public static final ModConfigSpec.IntValue SUMMON_REACH_TIMEOUT_TICKS;
    public static final ModConfigSpec.BooleanValue CHUTES_HOLD_PARTNERS;

    public static final ModConfigSpec.BooleanValue REQUIRE_CLEAR_LAUNCH;
    public static final ModConfigSpec.DoubleValue CORRIDOR_DRIFT;
    public static final ModConfigSpec.DoubleValue RIFT_LEAD_DISTANCE;
    public static final ModConfigSpec.DoubleValue RIFT_RADIUS_FACTOR;
    public static final ModConfigSpec.DoubleValue APPROACH_SPEED;
    public static final ModConfigSpec.IntValue APPROACH_LIMIT_TICKS;
    public static final ModConfigSpec.IntValue RIFT_TRANSIT_TICKS;
    public static final ModConfigSpec.DoubleValue SPIN_MINIMUM_RATE;
    public static final ModConfigSpec.DoubleValue EMERGE_DISTANCE_FACTOR;
    public static final ModConfigSpec.DoubleValue MAX_COMMANDED_SPEED;
    public static final ModConfigSpec.BooleanValue HOLD_ATTITUDE;
    public static final ModConfigSpec.EnumValue<uk.co.iceconchy.aerowarptics.warp.CorridorTime.Scaling> WARP_TIME_SCALING;
    public static final ModConfigSpec.DoubleValue WARP_TIME_MULTIPLIER;
    public static final ModConfigSpec.IntValue WARP_TIME_LIMIT;

    public static final ModConfigSpec.IntValue CRUISE_ACCELERATION_TICKS;
    public static final ModConfigSpec.IntValue CRUISE_BRAKING_TICKS;
    public static final ModConfigSpec.IntValue CRUISE_SENSOR_BUDGET;

    public static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION_WARP;
    public static final ModConfigSpec.BooleanValue ALLOW_REDSTONE_INITIATION;
    public static final ModConfigSpec.BooleanValue ALLOW_PUBLIC_ANCHORS;
    public static final ModConfigSpec.BooleanValue ALLOW_PRIVATE_ANCHORS;
    public static final ModConfigSpec.BooleanValue ALLOW_ANCHOR_NETWORKS;
    public static final ModConfigSpec.IntValue MAX_NETWORKS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_NETWORK_MEMBERS;
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_ABOARD;
    public static final ModConfigSpec.IntValue MAX_INTERACTION_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_ANCHORS_PER_PLAYER;

    public static final ModConfigSpec.IntValue GATE_DIAL_COST;
    public static final ModConfigSpec.IntValue GATE_DIAL_COST_PER_BLOCK;
    public static final ModConfigSpec.IntValue GATE_UPKEEP_COST;
    public static final ModConfigSpec.IntValue GATE_UPKEEP_COST_PER_BLOCK;
    public static final ModConfigSpec.IntValue GATE_UPKEEP_INTERVAL;
    public static final ModConfigSpec.DoubleValue GATE_STRESS;
    public static final ModConfigSpec.DoubleValue GATE_STRESS_PER_BLOCK;
    public static final ModConfigSpec.IntValue GATE_MINIMUM_RPM;
    public static final ModConfigSpec.IntValue FISSURE_RESERVOIR_LEAST;
    public static final ModConfigSpec.IntValue FISSURE_RESERVOIR_MOST;
    public static final ModConfigSpec.IntValue FISSURE_DRAIN_RATE;
    public static final ModConfigSpec.IntValue FISSURE_SIPHON_RADIUS;

    public static final ModConfigSpec.IntValue GATE_DIAL_TICKS;
    public static final ModConfigSpec.IntValue GATE_PASSAGE_TICKS;
    public static final ModConfigSpec.IntValue MAX_GATES_PER_PLAYER;

    public static final ModConfigSpec.BooleanValue RAIL_GATES;
    public static final ModConfigSpec.BooleanValue RAIL_CROSS_DIMENSION;
    public static final ModConfigSpec.IntValue RAIL_CORRIDOR_LENGTH;
    public static final ModConfigSpec.IntValue RAIL_LINGER_TICKS;
    public static final ModConfigSpec.IntValue RAIL_RETRY_TICKS;
    public static final ModConfigSpec.IntValue RAIL_MAX_LANES;
    public static final ModConfigSpec.IntValue RAIL_APRON_LENGTH;
    public static final ModConfigSpec.IntValue RAIL_RESCUE_GRACE_TICKS;
    public static final ModConfigSpec.IntValue RAIL_PRE_DIAL_DISTANCE;
    public static final ModConfigSpec.IntValue RAIL_VISIT_TICKS;

    public static final ModConfigSpec.IntValue CHUTE_COST_PER_ITEM;
    public static final ModConfigSpec.IntValue CHUTE_BATCH_SIZE;

    public static final ModConfigSpec.IntValue MODULATOR_UPKEEP_COST;
    public static final ModConfigSpec.IntValue MODULATOR_UPKEEP_INTERVAL;

    public static final ModConfigSpec.DoubleValue STATION_TOLERANCE;
    public static final ModConfigSpec.IntValue TAKE_STATION_RANGE;
    public static final ModConfigSpec.IntValue FORMATION_REACH;
    public static final ModConfigSpec.IntValue HARMONISE_TICKS;
    public static final ModConfigSpec.IntValue FOLD_COUNTDOWN_TICKS;
    public static final ModConfigSpec.IntValue RIPPLE_TICKS;
    public static final ModConfigSpec.DoubleValue FLOTILLA_MASS_EXPONENT;
    public static final ModConfigSpec.IntValue COUPLER_UPKEEP_COST;
    public static final ModConfigSpec.IntValue COUPLER_UPKEEP_INTERVAL;
    public static final ModConfigSpec.IntValue WAKE_ECHO_DELAY_TICKS;
    public static final ModConfigSpec.IntValue WAKE_ECHO_MIN_OFFSET;
    public static final ModConfigSpec.IntValue WAKE_ECHO_MAX_OFFSET;
    public static final ModConfigSpec.IntValue SLOT_SURVEY_INTERVAL;

    public static final ModConfigSpec.DoubleValue STABILISER_CUSHION;
    public static final ModConfigSpec.DoubleValue STABILISER_SETTLE;
    public static final ModConfigSpec.DoubleValue STABILISER_JOLT_LIMIT;
    public static final ModConfigSpec.DoubleValue STABILISER_SPIN_JOLT_LIMIT;

    public static final ModConfigSpec.DoubleValue COOLING_VENT_STRESS;
    public static final ModConfigSpec.IntValue COOLING_VENT_MINIMUM_RPM;
    public static final ModConfigSpec.DoubleValue COOLING_VENT_SHARE;
    public static final ModConfigSpec.DoubleValue COOLING_ICE_SHARE;
    public static final ModConfigSpec.IntValue COOLING_MOST;

    public static final ModConfigSpec.DoubleValue BOND_REACH;
    public static final ModConfigSpec.BooleanValue BERTH_ENABLED;
    public static final ModConfigSpec.IntValue BERTH_LINK_REACH;
    public static final ModConfigSpec.IntValue BERTH_MAX_PER_ANCHOR;
    public static final ModConfigSpec.IntValue BERTH_MAX_LENGTH;
    public static final ModConfigSpec.IntValue BERTH_MAX_WIDTH;
    public static final ModConfigSpec.IntValue BERTH_MAX_HEIGHT;
    public static final ModConfigSpec.DoubleValue BERTH_CAPTURE_SPEED;
    public static final ModConfigSpec.BooleanValue BERTH_KEEP_MOORED_LOADED;
    public static final ModConfigSpec.IntValue BERTH_RESERVE_MARGIN;

    public static final ModConfigSpec.IntValue DETECTOR_MAX_RANGE;
    public static final ModConfigSpec.IntValue DETECTOR_PULSE_TICKS;

    public static final ModConfigSpec.IntValue BEACON_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue BEACON_BEAM_TICKS;
    public static final ModConfigSpec.IntValue BEACON_AIM_RANGE;
    public static final ModConfigSpec.IntValue PROBE_COST;
    public static final ModConfigSpec.DoubleValue PROBE_COST_PER_BLOCK;
    public static final ModConfigSpec.IntValue PROBE_ESSENCE_BUFFER;
    public static final ModConfigSpec.IntValue PROBE_MINIMUM_RANGE;
    public static final ModConfigSpec.IntValue PROBE_MAXIMUM_RANGE;
    public static final ModConfigSpec.IntValue PROBE_REACH_TICKS;
    public static final ModConfigSpec.IntValue PROBE_TIMEOUT_TICKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> PROBE_DIMENSIONS;

    public static final ModConfigSpec.DoubleValue FAILURE_CHARGE_PENALTY;
    public static final ModConfigSpec.IntValue FAILURE_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue DANGEROUS_FAILURES;
    public static final ModConfigSpec.BooleanValue TRACE;
    public static final ModConfigSpec.DoubleValue DANGEROUS_FAILURE_IMPULSE;
    public static final ModConfigSpec.IntValue MANIFEST_GRACE_TICKS;

    public static final ModConfigSpec.BooleanValue NATURAL_RIFT_STORMS;
    public static final ModConfigSpec.IntValue RIFT_STORM_CALM_LEAST;
    public static final ModConfigSpec.IntValue RIFT_STORM_CALM_MOST;
    public static final ModConfigSpec.IntValue RIFT_STORM_LENGTH_LEAST;
    public static final ModConfigSpec.IntValue RIFT_STORM_LENGTH_MOST;
    public static final ModConfigSpec.DoubleValue RIFT_STORM_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue RIFT_STORM_CALLABLE;
    public static final ModConfigSpec.IntValue RIFT_STORM_CALL_MINIMUM_CALM;
    public static final ModConfigSpec.IntValue STORMGLASS_SET_TICKS;
    public static final ModConfigSpec.IntValue RIFT_RAIN_RATE;
    public static final ModConfigSpec.IntValue RIFT_RAIN_CLOUD_HEIGHT;
    public static final ModConfigSpec.DoubleValue RIFT_RAIN_CLOUD_MULTIPLIER;

    public static final ModConfigSpec.IntValue BRAZIER_BURN_RATE;
    public static final ModConfigSpec.BooleanValue RIFTING_HOPS;
    public static final ModConfigSpec.BooleanValue RIFT_PEARL_HOPS;
    public static final ModConfigSpec.IntValue RIFTING_HOP_RANGE;
    public static final ModConfigSpec.IntValue COLLAPSE_DEPOT_REACH;
    public static final ModConfigSpec.IntValue COLLAPSE_STABILISER_REACH;
    public static final ModConfigSpec.DoubleValue COLLAPSE_SETTLE_BELOW;
    public static final ModConfigSpec.DoubleValue COLLAPSE_SPIT_ABOVE;
    public static final ModConfigSpec.IntValue COLLAPSE_FLING_RANGE;
    public static final ModConfigSpec.IntValue COLLAPSE_PULL_RANGE;
    public static final ModConfigSpec.IntValue COLLAPSE_BLAST_RANGE;
    public static final ModConfigSpec.DoubleValue COLLAPSE_BLAST_DAMAGE;
    public static final ModConfigSpec.IntValue COLLAPSE_RESUME_GRACE;
    public static final ModConfigSpec.DoubleValue COLLAPSE_FOLD_MINIMUM_SPEED;

    public static final ModConfigSpec.BooleanValue PLOTTER_ENABLED;
    public static final ModConfigSpec.IntValue PLOTTER_BASE_COST;
    public static final ModConfigSpec.IntValue PLOTTER_COST_PER_KM;
    public static final ModConfigSpec.IntValue PLOTTER_COST_PER_RADIUS;
    public static final ModConfigSpec.IntValue PLOTTER_DRAW_RATE;
    public static final ModConfigSpec.DoubleValue PLOTTER_MINIMUM_SPEED;
    public static final ModConfigSpec.DoubleValue PLOTTER_STRESS;
    public static final ModConfigSpec.IntValue PLOTTER_MAX_OPEN_SECONDS;
    public static final ModConfigSpec.DoubleValue PLOTTER_MAX_EXIT_SPEED;
    public static final ModConfigSpec.DoubleValue PLOTTER_LANE_UPKEEP;
    public static final ModConfigSpec.IntValue PLOTTER_DOCK_REACH;
    public static final ModConfigSpec.IntValue PLOTTER_MAX_DOCK_DEPTH;
    public static final ModConfigSpec.IntValue PLOTTER_MAX_DOCK_RADIUS;
    public static final ModConfigSpec.DoubleValue PLOTTER_DOCK_CAPTURE_SPEED;
    public static final ModConfigSpec.DoubleValue PLOTTER_LAUNCH_SPEED;
    public static final ModConfigSpec.DoubleValue PLOTTER_MAX_RADIUS;
    public static final ModConfigSpec.IntValue PLOTTER_SURVEY_CHUNKS;
    public static final ModConfigSpec.BooleanValue PLOTTER_CARRIES_ENTITIES;
    public static final ModConfigSpec.IntValue PLOTTER_CATCHMENT;
    public static final ModConfigSpec.IntValue PLOTTER_ARRIVAL_HOLD_SECONDS;
    public static final ModConfigSpec.IntValue PLOTTER_REACH_TIMEOUT;

    public static final ModConfigSpec.BooleanValue WARD_ENABLED;
    public static final ModConfigSpec.IntValue WARD_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue WARD_MINIMUM_SPEED;
    public static final ModConfigSpec.DoubleValue WARD_STRESS;
    public static final ModConfigSpec.IntValue WARD_CAPACITY;
    public static final ModConfigSpec.IntValue WARD_DRAW_RATE;
    public static final ModConfigSpec.DoubleValue WARD_UPKEEP;
    public static final ModConfigSpec.IntValue WARD_DEFLECT_BASE;
    public static final ModConfigSpec.DoubleValue WARD_DEFLECT_PER_MASS;
    public static final ModConfigSpec.IntValue WARD_PLOTTER_BASE;
    public static final ModConfigSpec.IntValue WARD_PLOTTER_PER_RADIUS;
    public static final ModConfigSpec.IntValue WARD_SCAN_COST;
    public static final ModConfigSpec.DoubleValue WARD_RECOVER_FRACTION;
    public static final ModConfigSpec.DoubleValue WARD_STORM_THINNING;
    public static final ModConfigSpec.BooleanValue WARD_DEFLECTS_PLOTTERS;
    public static final ModConfigSpec.BooleanValue WARD_SCRAMBLES_SCANS;
    public static final ModConfigSpec.IntValue WARD_SPECTACLE_RANGE;
    public static final ModConfigSpec.IntValue WARD_TOLL_RANGE;
    /** How far into a field each drive tier pushes before it is turned aside, as a share of the field's radius. */
    public static final java.util.Map<RiftDriveTier, ModConfigSpec.DoubleValue> WARD_PENETRATION =
            new java.util.EnumMap<>(RiftDriveTier.class);

    /** Per-tier settings, seeded from {@link RiftDriveTier#defaults()}. */
    public static final Map<RiftDriveTier, TierConfig> TIERS = new EnumMap<>(RiftDriveTier.class);

    /** The Rift Wellhead - see {@code wellhead/WellheadReactor} for what each figure does. */
    public static final ModConfigSpec.BooleanValue WELLHEAD_ENABLED;
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.DoubleValue> WELLHEAD_DIGESTION =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.IntValue> WELLHEAD_GULLET =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.IntValue> WELLHEAD_TANK =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.IntValue> WELLHEAD_STABILISERS =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.DoubleValue> WELLHEAD_MINIMUM_SPEED =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.DoubleValue> WELLHEAD_STRESS =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final java.util.Map<uk.co.iceconchy.aerowarptics.wellhead.WellheadTier, ModConfigSpec.IntValue> WELLHEAD_CRATER =
            new java.util.EnumMap<>(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.class);
    public static final ModConfigSpec.DoubleValue WELLHEAD_YIELD_GAIN;
    public static final ModConfigSpec.DoubleValue WELLHEAD_PRESSURE;
    public static final ModConfigSpec.DoubleValue WELLHEAD_FEEDBACK;
    public static final ModConfigSpec.DoubleValue WELLHEAD_DAMPING;
    public static final ModConfigSpec.DoubleValue WELLHEAD_CAGE_FLOOR;
    public static final ModConfigSpec.DoubleValue WELLHEAD_STORM_FACTOR;
    public static final ModConfigSpec.IntValue WELLHEAD_BLEED;
    public static final ModConfigSpec.IntValue WELLHEAD_STABILISER_REACH;
    public static final ModConfigSpec.IntValue WELLHEAD_RESUME_GRACE;
    public static final ModConfigSpec.IntValue WELLHEAD_SCRAM_LOCKOUT;
    public static final ModConfigSpec.DoubleValue WELLHEAD_HARM;
    public static final ModConfigSpec.BooleanValue WELLHEAD_MELTDOWN_BREAKS_BLOCKS;
    public static final ModConfigSpec.IntValue WELLHEAD_MELTDOWN_TICKS;
    public static final ModConfigSpec.IntValue WELLHEAD_CARVE_BUDGET;
    public static final ModConfigSpec.IntValue WELLHEAD_DEBRIS;
    public static final ModConfigSpec.IntValue WELLHEAD_SPECTACLE_RANGE;
    public static final ModConfigSpec.IntValue WELLHEAD_ZONE_MINUTES;
    public static final ModConfigSpec.DoubleValue WELLHEAD_ZONE_HARM;

    public static final ModConfigSpec.BooleanValue SUNDERER_ENABLED;
    public static final ModConfigSpec.IntValue SUNDERER_CHARGE;
    public static final ModConfigSpec.DoubleValue SUNDERER_MINIMUM_SPEED;
    public static final ModConfigSpec.DoubleValue SUNDERER_STRESS;
    public static final ModConfigSpec.IntValue SUNDERER_LEAST_OPENING;
    public static final ModConfigSpec.IntValue SUNDERER_MOST_OPENING;
    public static final ModConfigSpec.DoubleValue SUNDERER_SHATTER;
    public static final ModConfigSpec.DoubleValue SUNDERER_HAND_CHANCE;
    public static final ModConfigSpec.IntValue SUNDERER_SPACING;
    public static final ModConfigSpec.IntValue SUNDERER_ANCHOR_SPACING;
    public static final ModConfigSpec.IntValue SUNDERER_FISSURE_ESSENCE;
    public static final ModConfigSpec.IntValue SUNDERER_UNSETTLED_MINUTES;
    public static final ModConfigSpec.IntValue SUNDERER_RUPTURE_CRATER;
    public static final ModConfigSpec.IntValue SUNDERER_RUPTURE_ZONE_MINUTES;
    public static final ModConfigSpec.BooleanValue SUNDERER_RUPTURE_BREAKS_BLOCKS;
    public static final ModConfigSpec.IntValue SUNDERER_SPECTACLE_RANGE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SUNDERER_FORBIDDEN_DIMENSIONS;

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
            ModConfigSpec.DoubleValue instability,
            ModConfigSpec.DoubleValue cruiseSpeed,
            ModConfigSpec.IntValue flotillaEscorts) {
    }

    static {
        SERVER_BUILDER.comment("Global limits on where a Rift Drive may take an airship.").push("range");
        MINIMUM_WARP_DISTANCE = SERVER_BUILDER
                .comment("Warps shorter than this, in blocks, are rejected. A rift is not worth opening for a short hop.")
                .defineInRange("minimumWarpDistance", uk.co.iceconchy.aerowarptics.warp.WarpCost.DEFAULT_MINIMUM_DISTANCE, 0.0D, 1.0e7D);
        MAXIMUM_WARP_DISTANCE = SERVER_BUILDER
                .comment("Hard upper bound on warp distance regardless of drive tier.")
                .defineInRange("maximumWarpDistance", uk.co.iceconchy.aerowarptics.warp.WarpCost.DEFAULT_MAXIMUM_DISTANCE, 1.0D, 1.0e9D);
        CROSS_DIMENSION_DISTANCE = SERVER_BUILDER
                .comment("How far a warp into another dimension counts as, in blocks, whatever the real positions.",
                        "It is priced, range-checked and spun up for exactly like a jump of this length, so a drive",
                        "whose tier cannot reach this far cannot leave its dimension either. Only used when",
                        "permissions.allowCrossDimensionWarp is on and Dimensional Sable is installed.")
                .defineInRange("crossDimensionDistance", 1_000.0D, 0.0D, 1.0e6D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Warp cost is a fraction of a full drive charge.",
                        "cost = baseWarpCost + distance * distanceMultiplier + (mass / massReference) * sizeMultiplier",
                        "then divided by the tier's costEfficiency.")
                .push("cost");
        BASE_WARP_COST = SERVER_BUILDER
                .comment("Flat cost of opening a rift at all.")
                .defineInRange("baseWarpCost", uk.co.iceconchy.aerowarptics.warp.WarpCost.DEFAULT_BASE_COST, 0.0D, 1.0D);
        DISTANCE_MULTIPLIER = SERVER_BUILDER
                .comment("Additional cost per block of travel.")
                .defineInRange("distanceMultiplier", uk.co.iceconchy.aerowarptics.warp.WarpCost.DEFAULT_DISTANCE_MULTIPLIER, 0.0D, 1.0D);
        SIZE_MULTIPLIER = SERVER_BUILDER
                .comment("Additional cost per massReference units of airship mass.")
                .defineInRange("sizeMultiplier", uk.co.iceconchy.aerowarptics.warp.WarpCost.DEFAULT_SIZE_MULTIPLIER, 0.0D, 10.0D);
        MASS_REFERENCE = SERVER_BUILDER
                .comment("Airship mass counting as one unit of sizeMultiplier. Sable reports a physical mass per sub-level.")
                .defineInRange("massReference", uk.co.iceconchy.aerowarptics.warp.WarpCost.DEFAULT_MASS_REFERENCE, 1.0D, 1.0e9D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("How the exit point is chosen once the rift opens.").push("arrival");
        ARRIVAL_GROUND_BUFFER = SERVER_BUILDER
                .comment("Blocks of daylight left between the anchor and the airship's underside, unless the",
                        "anchor or Rift Probe says otherwise. Each of those carries its own arrival height,",
                        "which a player can adjust; this is the height a new one starts at, and the height",
                        "used by anything saved before arrival heights were adjustable and by Rift Beacons.",
                        "The search starts here and climbs, so an airship never materialises sitting on its anchor.")
                .defineInRange("arrivalGroundBuffer", 6.0D, 0.0D, 256.0D);
        MAX_ARRIVAL_HEIGHT = SERVER_BUILDER
                .comment("Highest arrival height a player may set on a Warp Anchor or a Rift Probe, in blocks.",
                        "Heights already set above this are lowered to it when a warp is planned.")
                .defineInRange("maxArrivalHeight", 128, 0, 1024);
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
        ARRIVAL_BLOCK_CHECK_SCALE = SERVER_BUILDER
                .comment("Block-check allowance per block of hull volume, which is what ties the budget",
                        "to the ship rather than to a flat number.",
                        "",
                        "A large hull legitimately has to prove a large volume clear, and a fixed budget",
                        "quietly turned that into a size limit: past a certain hull the check ran out",
                        "before it finished, and an unproven volume is refused. Scaling the allowance",
                        "with the hull means a big ship gets a big allowance, while a skiff still cannot",
                        "run away with the server. maxArrivalBlockChecks remains the hard ceiling.",
                        "",
                        "Only sections that could actually hold something solid are ever read, so this",
                        "is rarely approached: water, kelp and grass are dismissed a whole section at a",
                        "time without reading a block.")
                .defineInRange("arrivalBlockCheckScale", 8.0D, 0.25D, 256.0D);
        MAX_ARRIVAL_CHUNKS = SERVER_BUILDER
                .comment("Most chunk columns one warp may load or hold for any single purpose: the ground",
                        "an arrival is checked against, the landing held for the flight, the corridor",
                        "held behind it, and the landing loaded ahead of time while the drive spins up.",
                        "",
                        "This used to be a flat 1024 hidden in the code, and a hull past roughly four",
                        "hundred blocks could not prove any landing clear at all - every candidate asked",
                        "for more ground than that, every one was refused, and the warp reported nowhere",
                        "to arrive. An arrival that needs more than this is now refused straight away with",
                        "its own reason rather than after trying four thousand places.",
                        "",
                        "Chunks are loaded in the background during spin-up, so raising this mostly costs",
                        "memory while a large ship is in flight, not a stalled server.")
                .defineInRange("maxArrivalChunks", 4096, 256, 65_536);
        ARRIVAL_PRELOAD_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("How long a fully spun-up drive waits for its landing to finish loading before it",
                        "opens the rift anyway, in ticks.",
                        "",
                        "The landing is claimed the moment a drive starts spinning up, so the ground the",
                        "arrival check reads loads in the background. Normally it is ready long before the",
                        "drive is. Ground nobody has ever visited has to be generated first, and a large",
                        "hull can need hundreds of chunks of it: the drive holds at full spin until they",
                        "are ready rather than making the server generate them all in one tick. After",
                        "this long it stops waiting and loads whatever is left the old way. 0 never waits.")
                .defineInRange("arrivalPreloadTimeoutTicks", 600, 0, 12_000);
        KEEP_AIRSHIPS_LOADED = SERVER_BUILDER
                .comment("Keep an assembled airship carrying a Rift Drive loaded wherever it is left, so a ship",
                        "can jump into unloaded wilderness and simply stay there. The drive holds its ship with",
                        "Sable's own force-load ticket, which Sable saves with the world: an unattended ship",
                        "parked in the wild is reloaded and still there after a server restart. The hold is",
                        "renewed by the drive and lapses by itself if the drive is gone, so it cannot outlive",
                        "the ship. An unmanned ship that wanders more than loading.residencyRoamLimit from",
                        "where it was left is let go rather than followed.",
                        "",
                        "This is separate from the hold every warp takes on its ship for the length of the",
                        "warp, which is always on: that is what stops a ship landing on ground nothing holds",
                        "and being unloaded out from under its own arrival.",
                        "",
                        "Turn this off to let drive-bearing ships unload like any other build once a warp is",
                        "over - in which case consider turning requireLoadedArrival back on.")
                .define("keepAirshipsLoaded", true);
        REQUIRE_LOADED_ARRIVAL = SERVER_BUILDER
                .comment("Refuse a warp whose landing zone will not stay loaded on its own once the arrival",
                        "ticket lapses - i.e. no player stands within view and no force-loaded region",
                        "reaches it. This is the old, strict behaviour: it refuses the jump at the mooring",
                        "rather than flying into wilderness. It is off by default because keepAirshipsLoaded",
                        "now holds such a landing resident through the drive itself, so the ship is not lost.",
                        "Only turn this on if you have also turned keepAirshipsLoaded off and want warps to",
                        "unattended anchors forbidden rather than kept loaded.")
                .define("requireLoadedArrival", false);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("What the mod loads, and when. Nothing here is an always-on chunk loader: each",
                        "setting loads ground only while something is being done with it - a pilot looking",
                        "at a map, a beacon calling a ship, items flowing through a chute - and lets it go",
                        "by itself a few seconds after that stops. See /aerowarptics holds list.")
                .push("loading");
        RESIDENCY_ROAM_LIMIT = SERVER_BUILDER
                .comment("How far, in blocks, an unmanned ship held by keepAirshipsLoaded may drift from where",
                        "it was left before it is let go. A hold that followed a ship anywhere would load and",
                        "generate country the whole length of an unmanned course. A ship let go is saved",
                        "wherever it stops, not lost, and is held again once it comes to rest.")
                .defineInRange("residencyRoamLimit", 256, 16, 100_000);
        ASTROLABE_LOADS_PREVIEWS = SERVER_BUILDER
                .comment("Load the ground round a Warp Anchor while a player is looking at it on an Astrolabe,",
                        "so the chart can show a map of somewhere nobody is standing. Held for about thirty",
                        "seconds, one anchor per player at a time. Off, an anchor in unloaded ground shows no",
                        "map until someone goes there.")
                .define("astrolabeLoadsPreviews", true);
        BEACON_REACHES_SHIPS = SERVER_BUILDER
                .comment("Let a Rift Beacon call a ship that is not loaded. The ground the ship was last seen",
                        "over is loaded until it comes back, and the summon goes ahead as soon as it does;",
                        "from then on the warp holds the ship itself. Only ships whose drive has run since",
                        "this version was installed can be found this way.")
                .define("beaconReachesShips", true);
        SUMMON_REACH_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("How long a beacon waits for an unloaded ship to come back before giving up, in ticks.")
                .defineInRange("summonReachTimeoutTicks", 600, 100, 6000);
        CHUTES_HOLD_PARTNERS = SERVER_BUILDER
                .comment("Let a Rift Chute load its partner while items are flowing into it. The partner's",
                        "chunk - or the ship it is aboard - is held for as long as items keep coming, and lets",
                        "go by itself about fifteen seconds after they stop. Off, a chute whose partner is not",
                        "loaded refuses items until somebody goes there.")
                .define("chutesHoldPartners", true);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The flight through the rift: the run at the entry aperture, the passage",
                        "through it, and the run out of the far one. The corridor is flown inside the",
                        "entry aperture rather than anywhere else, so it needs no settings of its own -",
                        "how long it lasts comes from the drive's tier, and from how far the jump goes",
                        "only if warpTimeScaling says so.")
                .push("flight");
        REQUIRE_CLEAR_LAUNCH = SERVER_BUILDER
                .comment("Prove the WHOLE departure corridor clear before opening the rift, and refuse the",
                        "warp at the mooring if anything stands anywhere in it.",
                        "",
                        "Off by default, because as a gate it scaled badly: the volume to prove grows with",
                        "the hull, and past a certain size a ship could not be cleared at all - refused on",
                        "every attempt, over open water, with no block anywhere near it. A check that says",
                        "no to every large vessel is not protecting them from anything.",
                        "",
                        "What still runs unconditionally is the bow guard: the hull's own path through open",
                        "air on its way to the aperture, tested at the bare hull with no padding. That is",
                        "the case that actually ejects a ship - driving into a hillside directly ahead -",
                        "and it costs the same for a flying city as for a skiff. Beyond the bow, the pilot",
                        "is flying the ship.",
                        "",
                        "Turn this on for the old strict behaviour. It refuses more, including warps that",
                        "would have been fine, and the refusals get more likely the larger the ship.",
                        "/aerowarptics warp clearance draws what it tests either way.")
                .define("requireClearLaunch", false);
        CORRIDOR_DRIFT = SERVER_BUILDER
                .comment("Blocks the hull travels during the corridor run, on top of the passage that",
                        "swallowed it. Zero holds the ship still inside the throat for the duration.",
                        "",
                        "The corridor is a fold in space, not a distance: once the aperture has taken the",
                        "hull bow to stern, every further block flown is real world space that has to be",
                        "proven clear, kept chunk-resident, and hidden behind a correspondingly deeper",
                        "throat. It used to run for the whole corridor phase at passage speed, which on a",
                        "long hull meant flying two and a half ship-lengths through terrain nobody can",
                        "see, purely so the ship's motion never changed. That is the single largest cost",
                        "in a warp and it buys nothing: the hull is inside the throat throughout.",
                        "",
                        "Raise it if you want the crew to see movement out of the throat. The speed is",
                        "this distance spread over the tier's corridor ticks, so the run stays smooth",
                        "however long the tier holds the corridor open, and the cost no longer grows with",
                        "the size of the ship.")
                .defineInRange("corridorDrift", 0.0D, 0.0D, 512.0D);
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
        MAX_COMMANDED_SPEED = SERVER_BUILDER
                .comment("Hard ceiling, in blocks per tick, on any velocity the drive commands or the",
                        "hull is allowed to report while a warp is being flown. A backstop, not a speed",
                        "setting: it sits far above the fastest legitimate passage a top-tier drive",
                        "flies at and far below the thousands-of-blocks-a-tick a physics ejection",
                        "produces, so a solver glitch or a unit slip that would fling the hull across",
                        "the world is clamped at source and, if the hull is somehow already moving that",
                        "fast, the warp is aborted and the ship put back rather than launched. Raise it",
                        "only if a legitimate passage is ever clamped, which would show in the warp",
                        "trace; the default clears every shipped tier with room to spare.")
                .defineInRange("maxCommandedSpeed", 48.0D, 4.0D, 4096.0D);
        HOLD_ATTITUDE = SERVER_BUILDER
                .comment("Hold the hull at the attitude it launched at for the whole of a warp.",
                        "Sable's sails and other lift blocks push on a hull in proportion to its speed,",
                        "and a warp flies it far faster than it ever flies itself, so a hull whose lift",
                        "blocks are not balanced about its centre of mass is twisted out of true on the",
                        "way through and comes out of the far rift tilted. Turn off only to see what the",
                        "physics would do on its own.")
                .define("holdAttitude", true);
        WARP_TIME_SCALING = SERVER_BUILDER
                .comment("Whether a warp lasts longer the further it goes, and on what curve.",
                        "",
                        "NONE: it does not. Every warp lasts its drive's own drives.<tier>.warpTicks in the",
                        "corridor, however far it reaches. This is how the mod has always behaved.",
                        "LINEAR: the same extra for every kilometre, without end.",
                        "SQUARE_ROOT: four times as far adds twice the extra.",
                        "LOGARITHMIC: the same extra each time the distance doubles - the gentlest on long hauls.",
                        "",
                        "Only the time in the corridor stretches; the run at the rift and the run out are",
                        "sized from the ship. A warp into another dimension counts as range.crossDimensionDistance",
                        "long. Every ship in a fold through one rift takes the flagship's time.")
                .defineEnum("warpTimeScaling", uk.co.iceconchy.aerowarptics.warp.CorridorTime.Scaling.NONE);
        WARP_TIME_MULTIPLIER = SERVER_BUILDER
                .comment("How much longer distance makes a warp, when warpTimeScaling is not NONE: the share",
                        "of the drive's warpTicks that one kilometre adds. At 0.25, a jump of one kilometre",
                        "lasts a quarter longer than the drive's own time on any curve. On LINEAR, four",
                        "kilometres doubles it and forty makes it eleven times as long; on SQUARE_ROOT forty",
                        "kilometres is about two and a half times; on LOGARITHMIC, about two and a third.",
                        "0 is the same as NONE.")
                .defineInRange("warpTimeMultiplier", 0.25D, 0.0D, 1_000.0D);
        WARP_TIME_LIMIT = SERVER_BUILDER
                .comment("The longest, in ticks, scaling may make any warp's time in the corridor. 1200 is a",
                        "minute. A drive whose own warpTicks is already longer than this keeps its own.",
                        "The ship is held loaded and its landing kept ready for as long as the warp lasts,",
                        "and a Rift Modulator or Rift Coupler is charged its upkeep for all of it.")
                .defineInRange("warpTimeLimitTicks", 1_200, 5, 72_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Free cruise: a drive set to Free Cruise flies the ship through the world along its",
                        "bow, at a speed set by the strength of its redstone signal. Top speed is per tier,",
                        "under drives.<tier>.cruiseSpeed.")
                .push("cruise");
        CRUISE_ACCELERATION_TICKS = SERVER_BUILDER
                .comment("Ticks a cruise takes to build from a standstill to full speed.")
                .defineInRange("accelerationTicks", 120, 1, 12_000);
        CRUISE_BRAKING_TICKS = SERVER_BUILDER
                .comment("Ticks a cruise takes to brake from full speed to a standstill. This is also how",
                        "hard a cruise brakes for terrain ahead, so the ship keeps whatever distance this",
                        "needs between itself and anything in front of it: lower means it can run closer",
                        "to hills and ground that has not loaded yet before slowing down.")
                .defineInRange("brakingTicks", 40, 1, 12_000);
        CRUISE_SENSOR_BUDGET = SERVER_BUILDER
                .comment("Most blocks a cruising ship may read on one look ahead, every four ticks. A ship",
                        "that runs out treats the rest as unseen and slows to match, so a very large hull",
                        "skimming dense terrain cruises more slowly rather than costing the server more.",
                        "Open sky costs almost nothing: whole empty chunk sections are skipped unread.")
                .defineInRange("sensorBudget", 200_000, 1_000, 10_000_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Who may use drives and anchors.").push("permissions");
        ALLOW_REDSTONE_INITIATION = SERVER_BUILDER
                .comment("Allow a redstone signal into a Rift Drive to start a warp.",
                        "The signal can only fire a course a player already set on an Astrolabe",
                        "Cartography Table, so wiring a drive up grants no access the person who set",
                        "the course did not already have. Turn this off to require a hand on a control.")
                .define("allowRedstoneInitiation", true);
        ALLOW_CROSS_DIMENSION_WARP = SERVER_BUILDER
                .comment("Allow warping to anchors, and to a Rift Probe's scans, in other dimensions. Off by default,",
                        "and experimental.",
                        "Needs the Dimensional Sable mod installed on the server as well; without it this does nothing.",
                        "Sable keeps a ship in one dimension, so Dimensional Sable rebuilds the whole vessel in the",
                        "other one, at the moment it passes through the fold. That takes a noticeable tick on a very",
                        "large ship. Propellers and other contraptions on bearings are taken apart for the crossing,",
                        "and bearings that were running put them back together on arrival; pistons, pulleys and",
                        "gantries are left as blocks. Rift Gates stay in their own dimension; which dimensions a",
                        "Rift Probe may scan is probe.probeDimensions.")
                .define("allowCrossDimensionWarp", false);
        ALLOW_PUBLIC_ANCHORS = SERVER_BUILDER
                .comment("Allow anchors that anybody may warp to.")
                .define("allowPublicAnchors", true);
        ALLOW_PRIVATE_ANCHORS = SERVER_BUILDER
                .comment("Allow anchors restricted to their owner.")
                .define("allowPrivateAnchors", true);
        ALLOW_ANCHOR_NETWORKS = SERVER_BUILDER
                .comment("Allow networks: named groups of players who share Warp Anchors and Rift Gates set to",
                        "Network access. Off, no machine can be put on a network, and one already set to Network",
                        "access is open to its owner alone.")
                .define("allowAnchorNetworks", true);
        MAX_NETWORKS_PER_PLAYER = SERVER_BUILDER
                .comment("Most networks a single player may own. 0 disables the limit. Operators are not held to it.")
                .defineInRange("maxNetworksPerPlayer", 16, 0, 10_000);
        MAX_NETWORK_MEMBERS = SERVER_BUILDER
                .comment("Most players one network may hold, its owner included.")
                .defineInRange("maxNetworkMembers", 64, 1, 256);
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
        GATE_UPKEEP_COST = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, spent every upkeep interval to hold a",
                        "connection open, before size. A doorway is a tear somebody is holding apart,",
                        "and holding it costs for as long as it is held. Set to 0, along with the",
                        "per-block figure, to go back to essence being a one-off price for dialling.")
                .defineInRange("gateUpkeepCost", 10, 0, 1_000_000);
        GATE_UPKEEP_COST_PER_BLOCK = SERVER_BUILDER
                .comment("Extra millibuckets per block of opening, every upkeep interval.",
                        "A wider doorway is heavier to hold as well as dearer to strike.")
                .defineInRange("gateUpkeepCostPerBlock", 1, 0, 10_000);
        GATE_UPKEEP_INTERVAL = SERVER_BUILDER
                .comment("Ticks between upkeep charges. Twenty is once a second, which is what the",
                        "goggle and Display Link readings are worded for.")
                .defineInRange("gateUpkeepInterval", 20, 1, 1_200);
        GATE_STRESS = SERVER_BUILDER
                .comment("Stress the gate draws while it is holding an aperture open, before size.",
                        "A gate standing dark costs nothing: essence strikes a connection and keeps it",
                        "standing, and rotation holds the machinery that does the keeping.")
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
        GATE_PASSAGE_TICKS = SERVER_BUILDER
                .comment("Ticks a player spends in passage between walking into a gate and coming out of",
                        "the far one. They are put at the far gate at once, and for this long cannot be",
                        "hurt, targeted, knocked about or seen, and cannot move, while their own client",
                        "draws the passage over their view - which is also what hides the far side",
                        "loading in. A vehicle with a crew aboard is held still at the far gate for as",
                        "long. Mobs, items and empty vehicles are never held. 0 makes every crossing",
                        "instant, as gates were before passages.")
                .defineInRange("gatePassageTicks", 60, 0, 200);
        MAX_GATES_PER_PLAYER = SERVER_BUILDER
                .comment("Maximum number of Rift Gates a single player may own. 0 disables the limit.")
                .defineInRange("maxGatesPerPlayer", 0, 0, 10_000);

        SERVER_BUILDER.comment("Rail gates: Create track run into a Rift Gate comes out of the gate it is routed to,",
                        "through a short corridor in the Rift Corridor dimension. A train that reaches a closed gate",
                        "waits there and the gate dials for it, paying as a dial does.")
                .push("rail");
        RAIL_GATES = SERVER_BUILDER
                .comment("Whether track run into a Rift Gate becomes a rail lane at all.")
                .define("enabled", true);
        RAIL_CROSS_DIMENSION = SERVER_BUILDER
                .comment("Whether a gate may be routed to a gate in another dimension. Trains go through such a",
                        "route; people and vehicles walking up to the gate still cannot.")
                .define("allowCrossDimension", true);
        RAIL_CORRIDOR_LENGTH = SERVER_BUILDER
                .comment("Length of each lane's corridor, in blocks. 0 works it out from Create's longest train",
                        "(maxAssemblyLength) plus 32, which is the least that lets every carriage Create can build",
                        "leave one gate before it reaches the other. A larger figure is a longer ride in the void.",
                        "Only new lanes are laid at a new length.")
                .defineInRange("corridorLength", 0, 0, 1_024);
        RAIL_LINGER_TICKS = SERVER_BUILDER
                .comment("Ticks a gate that opened for a train stays open after the last one is through, so a",
                        "second train close behind does not pay to open it again.")
                .defineInRange("lingerTicks", 60, 0, 1_200);
        RAIL_RETRY_TICKS = SERVER_BUILDER
                .comment("Ticks between a waiting train's attempts to have a gate dial for it, when the gate",
                        "refused - not enough essence or rotation, or the far gate busy.")
                .defineInRange("retryTicks", 40, 5, 1_200);
        RAIL_MAX_LANES = SERVER_BUILDER
                .comment("Most rail lanes one gate may have running into it.")
                .defineInRange("maxLanesPerGate", 8, 1, 64);
        RAIL_APRON_LENGTH = SERVER_BUILDER
                .comment("Straight track laid on from the stub that comes out of the far gate, for players to",
                        "build from. A curve drawn straight off the stub is lost if the gate is routed elsewhere;",
                        "one drawn off the apron is not.")
                .defineInRange("apronLength", 2, 0, 16);
        RAIL_RESCUE_GRACE_TICKS = SERVER_BUILDER
                .comment("Ticks anything may stand still in the Rift Corridor before the corridor puts it right: a",
                        "player off a train is put back at the gate they came through, and a train with nothing",
                        "driving it is sent on out the way it was going. Create lifts a rider off and puts them",
                        "back as a carriage crosses, so this is not zero.")
                .defineInRange("rescueGraceTicks", 60, 10, 6_000);
        RAIL_VISIT_TICKS = SERVER_BUILDER
                .comment("Ticks a player who steps into the Rift Corridor through a gate's own opening may stay",
                        "there before the corridor puts them back where they came in. Right-clicking a gate that",
                        "has track run into it is the way in - a door for getting at a train that has stopped in a",
                        "corridor - and right-clicking the mouth at either end is the way out again. Their time",
                        "left is counted down on their own view, and standing on a train does not spend it.",
                        "Default is three minutes.")
                .defineInRange("visitTicks", 3_600, 100, 72_000);
        RAIL_PRE_DIAL_DISTANCE = SERVER_BUILDER
                .comment("How far ahead, in blocks, a scheduled train whose path runs through a gate asks it to",
                        "dial, so that the gate is open when it gets there instead of stopping it dead. 0 waits",
                        "until the train is at the gate.")
                .defineInRange("preDialDistance", 96, 0, 1_024);
        SERVER_BUILDER.pop();
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
        PROBE_ESSENCE_BUFFER = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, a Rift Probe holds. 0 leaves it unlimited: a probe",
                        "keeps whatever is piped in. A probe always has room for its longest sounding, so a",
                        "figure below that cost is raised to it rather than leaving a range nobody can pay for.",
                        "Lowering this spills nothing; a probe holding more just takes no more until it is used.")
                .defineInRange("probeEssenceBuffer", 0, 0, Integer.MAX_VALUE);
        PROBE_MINIMUM_RANGE = SERVER_BUILDER
                .comment("Closest a sounding may be thrown, in blocks. Below this a probe is only",
                        "surveying ground the ship could already see.")
                .defineInRange("probeMinimumRange", 512, 16, 1_000_000);
        PROBE_MAXIMUM_RANGE = SERVER_BUILDER
                .comment("Furthest a sounding may be thrown, in blocks. A probe is still bounded by the",
                        "drive's own range when the course is actually flown.")
                .defineInRange("probeMaximumRange", 100_000, 32, 10_000_000);
        PROBE_REACH_TICKS = SERVER_BUILDER
                .comment("Shortest a sounding takes. The wait is the server bringing that ground into",
                        "being, so this is a floor on it rather than a delay for its own sake.")
                .defineInRange("probeReachTicks", 60, 0, 12_000);
        PROBE_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("Ticks after which a sounding reads whatever has arrived and stops waiting.",
                        "A thin reading is reported as thin rather than being waited on forever.")
                .defineInRange("probeTimeoutTicks", 400, 20, 72_000);
        PROBE_DIMENSIONS = SERVER_BUILDER
                .comment("Dimensions a Rift Probe may scan besides the one it is in, chosen on its panel.",
                        "Only used when permissions.allowCrossDimensionWarp is on and Dimensional Sable is",
                        "installed, since a scan of somewhere no ship can be sent is essence spent on nothing.",
                        "A probe reaches a dimension nobody has had to walk into first, so this lists the",
                        "vanilla three rather than everything: add a mod's dimension by its id, or \"*\" for",
                        "every dimension the server has - which includes the ones mods keep for themselves.",
                        "Empty keeps every probe to its own dimension.")
                .defineListAllowEmpty("probeDimensions",
                        () -> java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                        () -> "minecraft:overworld",
                        entry -> entry instanceof String);
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

        SERVER_BUILDER.comment("Rift Modulators: cosmetic modules that let a Rift Drive's pilot",
                        "choose the rift's colour and look. Essence is spent only while the linked",
                        "drive is actually running a warp - an idle drive costs a Modulator nothing.")
                .push("modulator");
        MODULATOR_UPKEEP_COST = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, spent every upkeep interval while the",
                        "Modulator is dressing a warp in progress. Set to 0 to make it run on nothing.")
                .defineInRange("modulatorUpkeepCost", 5, 0, 10_000);
        MODULATOR_UPKEEP_INTERVAL = SERVER_BUILDER
                .comment("Ticks between upkeep charges. Twenty is once a second.")
                .defineInRange("modulatorUpkeepInterval", 20, 1, 1_200);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Couplers and flotillas: several ships folding together behind one",
                        "flagship. How many escorts a flagship may lead is per drive tier, under drives.")
                .push("flotilla");
        STATION_TOLERANCE = SERVER_BUILDER
                .comment("How far an escort may stray from its slot, in blocks, and still count as in station.",
                        "Slots are kept at least twice this apart, beyond the ships' own size, so two escorts",
                        "each keeping station can never touch.")
                .defineInRange("stationTolerance", 6.0D, 1.0D, 64.0D);
        TAKE_STATION_RANGE = SERVER_BUILDER
                .comment("How close a flagship has to be, in blocks, to be offered to an escort taking station.")
                .defineInRange("takeStationRange", 256, 16, 4_096);
        FORMATION_REACH = SERVER_BUILDER
                .comment("How far from its flagship an escort's slot may be, in blocks.")
                .defineInRange("formationReach", 192, 16, 2_048);
        HARMONISE_TICKS = SERVER_BUILDER
                .comment("Ticks an escort must hold station, charged, after stations are sounded before its",
                        "drive has matched the flagship's. Any slip starts it again.")
                .defineInRange("harmoniseTicks", 60, 1, 1_200);
        FOLD_COUNTDOWN_TICKS = SERVER_BUILDER
                .comment("Ticks of countdown between the flagship's drive reaching full spin and the fold.",
                        "An escort that drifts out of station during it is shaken loose.")
                .defineInRange("foldCountdownTicks", 100, 20, 1_200);
        RIPPLE_TICKS = SERVER_BUILDER
                .comment("Ticks between one escort's rift and the next, front to back, as the fleet goes.")
                .defineInRange("rippleTicks", 4, 0, 40);
        FLOTILLA_MASS_EXPONENT = SERVER_BUILDER
                .comment("How much dearer a fold is than flying alone. Each drive pays for its own mass times",
                        "(fleet mass / heaviest ship) ^ (this - 1). 1 is no dearer than flying alone; at the",
                        "default 1.5 two equal ships each pay 1.41 times, four pay twice and eight 2.83 times.")
                .defineInRange("flotillaMassExponent", 1.5D, 1.0D, 3.0D);
        COUPLER_UPKEEP_COST = SERVER_BUILDER
                .comment("Rift Essence, in millibuckets, a flagship's coupler spends per escort every upkeep",
                        "interval of a fold. The whole fold's worth must be in the tank before it can start.",
                        "0 runs on nothing.")
                .defineInRange("couplerUpkeepCost", 4, 0, 10_000);
        COUPLER_UPKEEP_INTERVAL = SERVER_BUILDER
                .comment("Ticks between the coupler's upkeep charges. Twenty is once a second.")
                .defineInRange("couplerUpkeepInterval", 20, 1, 1_200);
        WAKE_ECHO_DELAY_TICKS = SERVER_BUILDER
                .comment("Ticks a shaken-loose escort arrives after the rest of the fleet.")
                .defineInRange("wakeEchoDelayTicks", 60, 0, 1_200);
        WAKE_ECHO_MIN_OFFSET = SERVER_BUILDER
                .comment("Nearest a shaken-loose escort is thrown from its slot, in blocks, beyond its own size.")
                .defineInRange("wakeEchoMinOffset", 12, 0, 512);
        WAKE_ECHO_MAX_OFFSET = SERVER_BUILDER
                .comment("Furthest a shaken-loose escort is thrown from its slot, in blocks, beyond its own size.",
                        "If nowhere in that ring is clear it comes out in its own slot.")
                .defineInRange("wakeEchoMaxOffset", 48, 0, 1_024);
        SLOT_SURVEY_INTERVAL = SERVER_BUILDER
                .comment("Ticks between looks at the escorts' slots at the far end while stations are sounded.",
                        "Only ground already loaded is read; sounding stations starts it loading.")
                .defineInRange("slotSurveyInterval", 40, 10, 1_200);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Spatial Stabilisers: Rift Drive modules that cushion an airship against sharp",
                        "changes in its motion while they have a redstone signal. They stand down while",
                        "the drive is flying a warp, and one stabiliser per ship does the work - more",
                        "aboard do not stack.")
                .push("stabiliser");
        STABILISER_CUSHION = SERVER_BUILDER
                .comment("Share of each tick's change in the ship's motion the stabiliser absorbs.",
                        "0.75 lets a quarter through, so a ship responds to its balloons and propellers as",
                        "though it were four times heavier. Capped below 1, where it would never move again.")
                .defineInRange("stabiliserCushion", 0.75D, 0.0D, StabiliserCushion.MAX_CUSHION);
        STABILISER_SETTLE = SERVER_BUILDER
                .comment("Share of the ship's remaining motion bled away each tick while cushioning.",
                        "This is what makes a steady push - burners left on - settle at a gentler speed",
                        "instead of merely reaching the same one later. 0 cushions without slowing.")
                .defineInRange("stabiliserSettle", 0.02D, 0.0D, StabiliserCushion.MAX_SETTLE);
        STABILISER_JOLT_LIMIT = SERVER_BUILDER
                .comment("A change in speed bigger than this, in blocks per tick, within a single tick is",
                        "read as a collision and left alone. Cushioning an impact would push the ship",
                        "back into whatever it hit. Balloons and propellers never come close.")
                .defineInRange("stabiliserJoltLimit", 0.25D, 0.001D, 48.0D);
        STABILISER_SPIN_JOLT_LIMIT = SERVER_BUILDER
                .comment("The same for turning, in radians per tick: a sharper change in spin than this",
                        "within one tick is read as a collision and left alone.")
                .defineInRange("stabiliserSpinJoltLimit", 0.05D, 0.0001D, 6.3D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Cooling: what shortens a Rift Drive's cooldown after a warp. A Cooling Vent set",
                        "against a drive and turned by a shaft carries the heat out as steam; a block of ice",
                        "against it does a little of the same with nothing to turn. What counts is fixed as",
                        "the warp completes, and applies on top of a Rift Storm's own share.")
                .push("cooling");
        COOLING_VENT_STRESS = SERVER_BUILDER
                .comment("Create stress units a Cooling Vent consumes per RPM. A vent backed straight onto",
                        "its drive turns at the drive's speed, so this is multiplied by that; fed from a",
                        "shaft of its own it can be run as slowly as ventMinimumRpm.")
                .defineInRange("ventStressImpact", DriveCooling.DEFAULT_VENT_STRESS, 0.0D, 4_096.0D);
        COOLING_VENT_MINIMUM_RPM = SERVER_BUILDER
                .comment("Rotational speed below which a Cooling Vent carries nothing out.")
                .defineInRange("ventMinimumRpm", DriveCooling.DEFAULT_VENT_MINIMUM_RPM, 1, 256);
        COOLING_VENT_SHARE = SERVER_BUILDER
                .comment("Fraction of a drive's cooldown one turning Cooling Vent leaves. 0.5 halves it,",
                        "and each further vent takes the same share of what the last one left; 1 makes",
                        "vents do nothing.")
                .defineInRange("ventCooldownMultiplier", DriveCooling.DEFAULTS.ventShare(), 0.0D, 1.0D);
        COOLING_ICE_SHARE = SERVER_BUILDER
                .comment("Fraction of a drive's cooldown one block of ice against it leaves. 0.9 takes a",
                        "tenth off. Any block in the aerowarptics:drive_coolant tag counts, which is every",
                        "kind of ice unless a data pack says otherwise; it is not used up.")
                .defineInRange("iceCooldownMultiplier", DriveCooling.DEFAULTS.iceShare(), 0.0D, 1.0D);
        COOLING_MOST = SERVER_BUILDER
                .comment("The most coolers one drive takes any good from, vents and ice together. Vents are",
                        "counted first and ice fills what room they leave, so four vents leave a sixteenth",
                        "of a cooldown and four blocks of ice about two thirds. 0 switches cooling off.")
                .defineInRange("mostCoolers", DriveCooling.DEFAULTS.most(), 0, 16);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Bonds: blocks that mark the loose sections of one ship, so that a warp takes",
                        "all of them. A bond is not a joint and holds nothing together in ordinary flight.")
                .push("bonds");
        BOND_REACH = SERVER_BUILDER
                .comment("Furthest apart, in blocks, two Rift Bonds tuned alike may be and still count as one",
                        "ship. Measured from block to block. Past this a section is left behind, which is",
                        "what stops a bond forgotten on a distant wreck from having it dragged along.")
                .defineInRange("bondReach", 256.0D, 8.0D, 4_096.0D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Berths: blocks that mark out a slip at a Warp Anchor's station. A ship warping to",
                        "that anchor is given a free berth it fits, turned to it, and held there.")
                .push("berth");
        BERTH_ENABLED = SERVER_BUILDER
                .comment("Whether berths do anything. Off, every anchor is an ordinary anchor again, no ship is",
                        "held or drawn in, and ships already moored are let go.")
                .define("berthEnabled", true);
        BERTH_LINK_REACH = SERVER_BUILDER
                .comment("Furthest, in blocks, a berth may be from the Warp Anchor it belongs to.")
                .defineInRange("linkReach", 128, 8, 1_024);
        BERTH_MAX_PER_ANCHOR = SERVER_BUILDER
                .comment("The most berths one anchor's station may have.")
                .defineInRange("maxPerAnchor", 16, 1, 128);
        BERTH_MAX_LENGTH = SERVER_BUILDER
                .comment("The longest a slip may be set, in blocks.")
                .defineInRange("maxLength", 256, 3, 512);
        BERTH_MAX_WIDTH = SERVER_BUILDER
                .comment("The widest a slip may be set, in blocks.")
                .defineInRange("maxWidth", 128, 3, 512);
        BERTH_MAX_HEIGHT = SERVER_BUILDER
                .comment("The tallest a slip may be set, in blocks.")
                .defineInRange("maxHeight", 128, 3, 512);
        BERTH_CAPTURE_SPEED = SERVER_BUILDER
                .comment("The fastest a ship flown into a slip by hand may be going and still be caught, in",
                        "blocks a second. A ship flying through faster than this is not parking.")
                .defineInRange("captureSpeed", 3.0D, 0.1D, 40.0D);
        BERTH_KEEP_MOORED_LOADED = SERVER_BUILDER
                .comment("Whether a berth keeps the ship it holds loaded with nobody near. Off, a moored ship",
                        "is filed away by Sable like any other left alone, and comes back when somebody does.")
                .define("keepMooredLoaded", true);
        BERTH_RESERVE_MARGIN = SERVER_BUILDER
                .comment("Ticks a berth stays reserved past the moment its ship is due, before a warp that",
                        "never arrived stops holding the slip. Twenty is one second.")
                .defineInRange("reserveMarginTicks", 600, 20, 12_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Spatial Wave Detectors: redstone sources that pulse when a ship finishes a warp",
                        "or something steps out of a Rift Gate nearby. Each detector's own range is set on",
                        "the block with Create's value box; these bound it and time the pulse.")
                .push("detector");
        DETECTOR_MAX_RANGE = SERVER_BUILDER
                .comment("Furthest any detector may listen, in blocks. A detector set further than this",
                        "is pulled back to it. Range costs nothing to run - arrivals are announced, not",
                        "searched for - so this is a gameplay limit on spying across the map, not a",
                        "performance one.")
                .defineInRange("detectorMaxRange", 128, 1, 256);
        DETECTOR_PULSE_TICKS = SERVER_BUILDER
                .comment("Ticks a detector's signal lasts after an arrival. Twenty is one second.",
                        "Another arrival during a pulse starts it again rather than stacking.")
                .defineInRange("detectorPulseTicks", 20, 2, 1_200);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Beacons: hand-held summons for a ship you are not standing on.")
                .push("beacon");
        BEACON_COOLDOWN_TICKS = SERVER_BUILDER
                .comment("Ticks before a beacon may be used again, successful or not.",
                        "Applied to refusals too. A drive that has just said no will keep saying no,",
                        "and without this a held right-click asks it sixty times a second.")
                .defineInRange("beaconCooldownTicks", 100, 0, 72_000);
        BEACON_BEAM_TICKS = SERVER_BUILDER
                .comment("Ticks the marker beam stands at a summon site.",
                        "Purely cosmetic, and deliberately independent of the warp it marks: the beam",
                        "is lit by the client that saw the summon, so it cannot be told to stop by a",
                        "warp that failed on a server the viewer is no longer near.")
                .defineInRange("beaconBeamTicks", 200, 20, 12_000);
        BEACON_AIM_RANGE = SERVER_BUILDER
                .comment("How far a beacon's aim reaches, in blocks.",
                        "This is the ray from the holder's eye, not the drive's range - the ship still",
                        "has to be able to reach wherever the ray lands. Deliberately well past normal",
                        "interaction range: pointing at a clearing across a valley is the whole gesture.")
                .defineInRange("beaconAimRange", 128, 8, 512);
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
        MANIFEST_GRACE_TICKS = SERVER_BUILDER
                .comment("How long a crew manifest outlives the last refresh, in ticks.",
                        "A warp that dies at the crossing leaves the manifest running so that",
                        "passengers who came off the hull are still recovered while the ship",
                        "coasts to a stop. Larger values cover larger hulls that take longer",
                        "to settle; shorter values mean a failed warp stops tracking people",
                        "sooner. The default of 600 (30 seconds) covers any hull the game can",
                        "generate.")
                .defineInRange("manifestGraceTicks", 600, 100, 12_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Fissures: the tears that generate in the world rather than being",
                        "opened by a machine. Only a player wearing Rift Infused Goggles can see one,",
                        "and only a Spatial Siphon standing near it can empty it.")
                .push("fissure");
        FISSURE_RESERVOIR_LEAST = SERVER_BUILDER
                .comment("Fewest millibuckets of Rift Essence a fissure holds. A siphon holds 4,000.")
                .defineInRange("reservoirLeast", 6_000, 0, 1_000_000);
        FISSURE_RESERVOIR_MOST = SERVER_BUILDER
                .comment("Most it may hold. How much any one fissure has is rolled from where it is,",
                        "so the answer is the same for everybody and survives the chunk unloading.")
                .defineInRange("reservoirMost", 18_000, 0, 1_000_000);
        FISSURE_DRAIN_RATE = SERVER_BUILDER
                .comment("Millibuckets a tick a fissure gives up, shared between every vessel in range.",
                        "The tear is the limit rather than the plumbing: a ring of siphons fills no",
                        "faster than one, it just holds more of what comes out.")
                .defineInRange("drainRate", 12, 1, 10_000);
        FISSURE_SIPHON_RADIUS = SERVER_BUILDER
                .comment("Blocks from the fissure a Spatial Siphon may stand and still draw from it.")
                .defineInRange("siphonRadius", 6, 1, 32);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Rift Storms: rare weather in which space is already coming apart. While one",
                        "rages, a Rift Drive's cooldown after a warp is shortened, and every drive's exit",
                        "is as unsure as a Singularity's - the Singularity's own instability is added on",
                        "top of the drive's. Storms rage in every dimension with a sky and no ceiling,",
                        "freeze with the doWeatherCycle gamerule like the rain does, and can be started",
                        "with /weather rift_storm and ended with /weather clear.")
                .push("riftStorm");
        NATURAL_RIFT_STORMS = SERVER_BUILDER
                .comment("Let Rift Storms arrive on their own. Off, they only happen when commanded.")
                .define("naturalStorms", true);
        RIFT_STORM_CALM_LEAST = SERVER_BUILDER
                .comment("Fewest ticks between one natural storm and the next. 24000 is one day.")
                .defineInRange("calmLeastTicks", 144_000, 1, 100_000_000);
        RIFT_STORM_CALM_MOST = SERVER_BUILDER
                .comment("Most ticks between one natural storm and the next.")
                .defineInRange("calmMostTicks", 480_000, 1, 100_000_000);
        RIFT_STORM_LENGTH_LEAST = SERVER_BUILDER
                .comment("Shortest a storm lasts, in ticks, when it arrives on its own or is commanded",
                        "without a duration.")
                .defineInRange("stormLeastTicks", 6_000, 20, 10_000_000);
        RIFT_STORM_LENGTH_MOST = SERVER_BUILDER
                .comment("Longest a storm lasts, in ticks.")
                .defineInRange("stormMostTicks", 12_000, 20, 10_000_000);
        RIFT_STORM_COOLDOWN_MULTIPLIER = SERVER_BUILDER
                .comment("Fraction of a drive's cooldown that is left when it completes a warp during a",
                        "storm. 0.5 halves it; 0 removes it; 1 leaves it alone.")
                .defineInRange("cooldownMultiplier", 0.5D, 0.0D, 1.0D);
        RIFT_STORM_CALLABLE = SERVER_BUILDER
                .comment("Let a player call a storm by breaking a set Stormglass - the only way to start one",
                        "without /weather. A storm is server-wide and changes every pilot's warps, so calling",
                        "one is a public act. Refused while one rages, while doWeatherCycle is off, and within",
                        "callMinimumCalmTicks of the last one ending; never allowed when naturalStorms is off.")
                .define("callable", true);
        RIFT_STORM_CALL_MINIMUM_CALM = SERVER_BUILDER
                .comment("Ticks after a storm ends before a Stormglass may call the next. 24000 is one day.")
                .defineInRange("callMinimumCalmTicks", 24_000, 0, 100_000_000);
        STORMGLASS_SET_TICKS = SERVER_BUILDER
                .comment("Ticks an Unset Stormglass has to spend out in a storm - held by a player standing",
                        "under open sky, or dropped under it - before it sets.")
                .defineInRange("stormglassSetTicks", 600, 1, 72_000);
        RIFT_RAIN_RATE = SERVER_BUILDER
                .comment("Millibuckets a second of rift rain caught under open sky during a storm, shared",
                        "between every Spatial Siphon in the same chunk (or aboard the same ship). A ring of",
                        "siphons fills no faster than one. 0 turns rift rain off.")
                .defineInRange("riftRainRate", 1, 0, 1_000);
        RIFT_RAIN_CLOUD_HEIGHT = SERVER_BUILDER
                .comment("World height above which a siphon counts as above the clouds and catches more.",
                        "The server cannot read a client's cloud height, so it is set here; vanilla's is 192.")
                .defineInRange("riftRainCloudHeight", 192, -64, 4_096);
        RIFT_RAIN_CLOUD_MULTIPLIER = SERVER_BUILDER
                .comment("How much more rift rain a siphon above riftRainCloudHeight catches.")
                .defineInRange("riftRainCloudMultiplier", 3.0D, 1.0D, 100.0D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The crafting layer: rifting at a standing rift, and the Collapse Cradle's folds and",
                        "its ritual. The recipes themselves are data - a data pack can add its own rifting,",
                        "folding or collapse recipes.")
                .push("crafting");
        BRAZIER_BURN_RATE = SERVER_BUILDER
                .comment("Millibuckets of Rift Essence a Rift Brazier burns each second while it is lit.")
                .defineInRange("brazierBurnRate", 2, 0, 1_000);
        RIFTING_HOPS = SERVER_BUILDER
                .comment("Let a creature caught in a rifting fan's stream take a short hop, like chorus fruit.",
                        "Never one aboard a ship or riding a Create contraption.")
                .define("riftingHops", true);
        RIFTING_HOP_RANGE = SERVER_BUILDER
                .comment("Furthest a rifting hop may carry a creature, in blocks.")
                .defineInRange("riftingHopRange", 8, 1, 32);
        RIFT_PEARL_HOPS = SERVER_BUILDER
                .comment("Let a thrown Rift Pearl hop whatever living thing it hits somewhere near, like chorus",
                        "fruit, through the chorus fruit's teleport event. Never a boss, or anything aboard a",
                        "ship or riding a Create contraption. Off, it only bursts where it lands.")
                .define("riftPearlHops", true);
        COLLAPSE_DEPOT_REACH = SERVER_BUILDER
                .comment("Blocks from a Collapse Cradle, on its own level, that a Depot may stand and still",
                        "hold an offering for it. Up to eight depots are used.")
                .defineInRange("collapseDepotReach", 3, 1, 8);
        COLLAPSE_STABILISER_REACH = SERVER_BUILDER
                .comment("Blocks from a Collapse Cradle a switched-on Spatial Stabiliser may stand and still",
                        "steady its collapse.")
                .defineInRange("collapseStabiliserReach", 6, 1, 16);
        COLLAPSE_SETTLE_BELOW = SERVER_BUILDER
                .comment("Final instability below which a collapse settles, leaving its core on the cradle.")
                .defineInRange("collapseSettleBelow", 0.35D, 0.0D, 1.0D);
        COLLAPSE_SPIT_ABOVE = SERVER_BUILDER
                .comment("Final instability at or above which a collapse spits: the Rift Core and every offering",
                        "are thrown out whole, and only the Scar Heart and the essence are spent. Between the",
                        "two bands the core is made but flung from the cradle.")
                .defineInRange("collapseSpitAbove", 0.70D, 0.0D, 1.0D);
        COLLAPSE_FLING_RANGE = SERVER_BUILDER
                .comment("Furthest a flung core lands from the cradle, in blocks.")
                .defineInRange("collapseFlingRange", 8, 1, 64);
        COLLAPSE_PULL_RANGE = SERVER_BUILDER
                .comment("Blocks round a Collapse Cradle within which a ritual draws loose items, creatures and",
                        "players in as it winds - gently, then hard in its last seconds. Never faster than a",
                        "player can walk away. 0 turns the pull off.")
                .defineInRange("collapsePullRange", 12, 0, 32);
        COLLAPSE_BLAST_RANGE = SERVER_BUILDER
                .comment("Blocks round a Collapse Cradle that its collapse knocks back. 0 turns the shockwave,",
                        "and any harm it does, off.")
                .defineInRange("collapseBlastRange", 8, 0, 32);
        COLLAPSE_BLAST_DAMAGE = SERVER_BUILDER
                .comment("Most harm, in half-hearts, an unsteady collapse deals: at the cradle itself, falling off",
                        "to nothing at the edge of collapseBlastRange, and less behind cover. A spat collapse deals",
                        "all of it, a flung one half, and a settled one none. It breaks no blocks and harms no",
                        "items. 0 makes every collapse harmless.")
                .defineInRange("collapseBlastDamage", 14.0D, 0.0D, 100.0D);
        COLLAPSE_RESUME_GRACE = SERVER_BUILDER
                .comment("Ticks after a paused collapse resumes during which a lack of rotation is forgiven.",
                        "Create reports no speed for the first ticks after a chunk loads.")
                .defineInRange("collapseResumeGraceTicks", 60, 0, 1_200);
        COLLAPSE_FOLD_MINIMUM_SPEED = SERVER_BUILDER
                .comment("The slowest, in RPM, a Collapse Cradle has to be turned to fold. A collapse asks for its",
                        "recipe's drive tier's own speed instead; a fold is lesser work and asks less.")
                .defineInRange("collapseFoldMinimumSpeed", 64.0D, 0.0D, 256.0D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The Rift Plotter: a rift torn beside the machine whose far end opens round a target",
                        "anywhere in the same world. Whatever crosses the near rift while it stands comes out of the",
                        "far one. There is no range limit; distance is paid for in Rift Essence.")
                .push("plotter");
        PLOTTER_ENABLED = SERVER_BUILDER
                .comment("Let Rift Plotters open rifts at all. Off, a plotter can still be built and aimed, and",
                        "refuses to open, saying so.")
                .define("plotterEnabled", true);
        PLOTTER_BASE_COST = SERVER_BUILDER
                .comment("Millibuckets of Rift Essence every plot costs, however short.")
                .defineInRange("plotterBaseCost", 500, 0, 1_000_000);
        PLOTTER_COST_PER_KM = SERVER_BUILDER
                .comment("Millibuckets of Rift Essence a plot costs for each thousand blocks from the plotter's",
                        "lens to the exit. The only limit on range: a far plot is not refused, it is expensive.")
                .defineInRange("plotterCostPerKilometre", 250, 0, 1_000_000);
        PLOTTER_COST_PER_RADIUS = SERVER_BUILDER
                .comment("Millibuckets of Rift Essence a plot costs for each block of the rift's radius.")
                .defineInRange("plotterCostPerRadius", 40, 0, 1_000_000);
        PLOTTER_DRAW_RATE = SERVER_BUILDER
                .comment("Millibuckets a tick a plotter draws from its tank into its charge, turning at",
                        "plotterMinimumSpeed. Faster turns draw more, up to four times this. The tank is a buffer:",
                        "a plot longer than it holds keeps drawing as pipes refill it.")
                .defineInRange("plotterDrawRate", 10, 1, 100_000);
        PLOTTER_MINIMUM_SPEED = SERVER_BUILDER
                .comment("The slowest, in RPM, a Rift Plotter has to be turned to charge and to hold its rift",
                        "open. Let it slow below this with the rift open and the rift collapses.")
                .defineInRange("plotterMinimumSpeed", 64.0D, 1.0D, 256.0D);
        PLOTTER_STRESS = SERVER_BUILDER
                .comment("Create stress impact of a Rift Plotter, in stress units per RPM.")
                .defineInRange("plotterStressImpact", 16.0D, 0.0D, 8192.0D);
        PLOTTER_MAX_OPEN_SECONDS = SERVER_BUILDER
                .comment("The longest, in seconds, a player may set a plotter's rifts to stand open for a strike.",
                        "Each plotter sets its own, up to this. A lane stands open until stood down however long.")
                .defineInRange("plotterMaxOpenSeconds", 60, 1, 600);
        PLOTTER_MAX_EXIT_SPEED = SERVER_BUILDER
                .comment("The fastest, in blocks a second, a player may set things to come out of a plotter's far rift.",
                        "A plotter can also leave things at the speed they went in. Airships are also capped by",
                        "flight.maxCommandedSpeed (blocks per tick, x20).")
                .defineInRange("plotterMaxExitSpeed", 100.0D, 0.0D, 960.0D);
        PLOTTER_LANE_UPKEEP = SERVER_BUILDER
                .comment("What a plotter's lane costs to hold open, each second, as a percentage of what opening the",
                        "same plot costs - so a lane to the far side of the world costs more to keep than one down the",
                        "road. Never less than 1 mB a second.")
                .defineInRange("plotterLaneUpkeepPercent", 1.0D, 0.0D, 100.0D);
        PLOTTER_DOCK_REACH = SERVER_BUILDER
                .comment("How far behind a plotter's entry, in blocks, its dock helper catches a vehicle waiting to go",
                        "through - on a plotter whose dock nobody has sized. A player can set each plotter's own",
                        "depth at its table, up to plotterMaxDockDepth.")
                .defineInRange("plotterDockReach", 32, 4, 256);
        PLOTTER_MAX_DOCK_DEPTH = SERVER_BUILDER
                .comment("The furthest behind its entry, in blocks, a player may set a plotter's dock helper to",
                        "reach.")
                .defineInRange("plotterMaxDockDepth", 96, 4, 256);
        PLOTTER_MAX_DOCK_RADIUS = SERVER_BUILDER
                .comment("The furthest from its rift's axis, in blocks, a player may set a plotter's dock helper to",
                        "reach. A dock nobody has sized is half as wide again as its rift and six blocks more,",
                        "whatever this says.")
                .defineInRange("plotterMaxDockRadius", 48, 2, 256);
        PLOTTER_DOCK_CAPTURE_SPEED = SERVER_BUILDER
                .comment("The fastest, in blocks a second, a vehicle may be moving and still be caught by the dock",
                        "helper. Anything faster is left alone - a bombing run straight through is not slowed down.")
                .defineInRange("plotterDockCaptureSpeed", 8.0D, 0.0D, 200.0D);
        PLOTTER_LAUNCH_SPEED = SERVER_BUILDER
                .comment("How fast, in blocks a second, the dock helper pushes a lined-up vehicle through the entry.",
                        "What it comes out at is the plotter's own exit speed setting.")
                .defineInRange("plotterLaunchSpeed", 20.0D, 1.0D, 200.0D);
        PLOTTER_MAX_RADIUS = SERVER_BUILDER
                .comment("The widest, in blocks, a plotter may tear its rifts. A vehicle must fit: its shadow on",
                        "the rift's face has to fit inside the circle.")
                .defineInRange("plotterMaxRadius", 16.0D, 2.0D, 64.0D);
        PLOTTER_SURVEY_CHUNKS = SERVER_BUILDER
                .comment("How many chunks out from the target the plotting table's model of the ground reaches.",
                        "Only those chunks are loaded to build it, for as long as somebody is looking, and again",
                        "while a plot charges. 5 is an area eleven chunks across.")
                .defineInRange("plotterSurveyChunks", 5, 1, 8);
        PLOTTER_CARRIES_ENTITIES = SERVER_BUILDER
                .comment("Let a plotter's rift carry loose things - players, creatures, projectiles, TNT - as",
                        "well as vehicles. Off, only Create: Aeronautics vehicles (and whoever is aboard) go through.")
                .define("plotterCarriesEntities", true);
        PLOTTER_CATCHMENT = SERVER_BUILDER
                .comment("How far either side of the near rift, in blocks, a plotter watches for things about to",
                        "cross it. Anything faster than this in a tick - a very fast shell - can pass unseen.")
                .defineInRange("plotterCatchment", 24, 4, 128);
        PLOTTER_ARRIVAL_HOLD_SECONDS = SERVER_BUILDER
                .comment("Seconds a vehicle that came out of a plotter's exit is held loaded, so an uncrewed one",
                        "keeps flying at its target rather than being put away the moment it arrives.")
                .defineInRange("plotterArrivalHoldSeconds", 30, 0, 3_600);
        PLOTTER_REACH_TIMEOUT = SERVER_BUILDER
                .comment("Ticks a charged plotter waits for its far end to load before giving up. The charge is",
                        "kept.")
                .defineInRange("plotterReachTimeoutTicks", 600, 20, 12_000);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The Rift Ward: a relic that holds a field over the ground round it. A rift a stranger aims",
                        "into the field - a Rift Drive's arrival, a Rift Plotter's far end - comes out at the field's rim",
                        "instead. The ward's owner, the members of its network and anyone bound for an anchor inside",
                        "the field that they may use are let through.")
                .push("ward");
        WARD_ENABLED = SERVER_BUILDER
                .comment("Let Rift Wards hold fields at all. Off, a ward can still be built and filled, and turns",
                        "nothing aside.")
                .define("wardEnabled", true);
        WARD_MAX_RADIUS = SERVER_BUILDER
                .comment("The widest, in blocks, a player may set a ward's field. Each ward sets its own, up to this.",
                        "Upkeep is paid by area, so a field twice as wide costs four times as much to hold.")
                .defineInRange("wardMaxRadius", 96, 16, 512);
        WARD_MINIMUM_SPEED = SERVER_BUILDER
                .comment("The slowest, in RPM, a Rift Ward has to be turned to draw essence into its charge. A ward",
                        "turned slower than this, or not at all, still holds its field on what it has banked.")
                .defineInRange("wardMinimumSpeed", 32.0D, 1.0D, 256.0D);
        WARD_STRESS = SERVER_BUILDER
                .comment("Create stress impact of a Rift Ward, in stress units per RPM, while it is switched on.")
                .defineInRange("wardStressImpact", 8.0D, 0.0D, 8192.0D);
        WARD_CAPACITY = SERVER_BUILDER
                .comment("Millibuckets of Rift Essence a ward can bank as charge, besides what is in its tank. The",
                        "charge is what the field runs on while nobody is there to feed it.")
                .defineInRange("wardCapacity", 32_000, 1_000, 10_000_000);
        WARD_DRAW_RATE = SERVER_BUILDER
                .comment("Millibuckets a tick a ward draws from its tank into its charge, turning at",
                        "wardMinimumSpeed. Faster turns draw more, up to four times this.")
                .defineInRange("wardDrawRate", 5, 1, 100_000);
        WARD_UPKEEP = SERVER_BUILDER
                .comment("Millibuckets a second a field 32 blocks in radius costs to hold. Scales with the area of",
                        "the field as set, whether or not a storm has thinned it.")
                .defineInRange("wardUpkeep", 1.0D, 0.0D, 10_000.0D);
        WARD_DEFLECT_BASE = SERVER_BUILDER
                .comment("Millibuckets of charge it costs a ward to turn a ship aside, however small the ship.")
                .defineInRange("wardDeflectBase", 500, 0, 1_000_000);
        WARD_DEFLECT_PER_MASS = SERVER_BUILDER
                .comment("Millibuckets more for each cost.massReference units of the mass turned aside - the whole",
                        "vessel's, and a flotilla's summed. A fleet is how a ward is worn down.")
                .defineInRange("wardDeflectPerMass", 100.0D, 0.0D, 1_000_000.0D);
        WARD_PLOTTER_BASE = SERVER_BUILDER
                .comment("Millibuckets of charge it costs a ward to turn a Rift Plotter's exit aside, and again each",
                        "time a lane held open is renewed.")
                .defineInRange("wardPlotterBase", 250, 0, 1_000_000);
        WARD_PLOTTER_PER_RADIUS = SERVER_BUILDER
                .comment("Millibuckets more for each block of that exit's radius.")
                .defineInRange("wardPlotterPerRadius", 40, 0, 1_000_000);
        WARD_SCAN_COST = SERVER_BUILDER
                .comment("Millibuckets of charge it costs a ward to scramble one stranger's scan of its ground.")
                .defineInRange("wardScanCost", 25, 0, 1_000_000);
        WARD_RECOVER_FRACTION = SERVER_BUILDER
                .comment("A ward that cannot pay lets the rift through, and its field drops. It comes back once the",
                        "charge has climbed past this share of wardCapacity.")
                .defineInRange("wardRecoverFraction", 0.25D, 0.0D, 1.0D);
        WARD_STORM_THINNING = SERVER_BUILDER
                .comment("The share of its radius a field keeps while a Rift Storm rages in its dimension. 1 leaves",
                        "wards untouched by storms.")
                .defineInRange("wardStormThinning", 0.6D, 0.0D, 1.0D);
        WARD_DEFLECTS_PLOTTERS = SERVER_BUILDER
                .comment("Turn a Rift Plotter's far end aside as a ship's arrival is.")
                .define("wardDeflectsPlotters", true);
        WARD_SCRAMBLES_SCANS = SERVER_BUILDER
                .comment("Return interference in place of the ground inside a field to a stranger's Rift Probe, and",
                        "to a stranger's Rift Plotter table.")
                .define("wardScramblesScans", true);
        WARD_SPECTACLE_RANGE = SERVER_BUILDER
                .comment("Blocks from a deflection within which players are shown it: the tear dragged across the",
                        "sky, the dimming, the break.")
                .defineInRange("wardSpectacleRange", 192, 32, 512);
        WARD_TOLL_RANGE = SERVER_BUILDER
                .comment("Blocks from a ward within which its toll, and the boom at the rim, are heard.")
                .defineInRange("wardTollRange", 128, 16, 512);
        SERVER_BUILDER.comment("How far into a field each drive pushes before it is turned aside, as a share of the",
                        "field's radius. At 0 a drive is turned at the rim; at 0.5 it lands where it aimed anywhere",
                        "in the outer half and is turned only from the inner; at 1 a ward does not touch it.")
                .push("penetration");
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            WARD_PENETRATION.put(tier, SERVER_BUILDER.defineInRange(tier.getSerializedName(),
                    uk.co.iceconchy.aerowarptics.ward.WardRules.defaultPenetration(tier.index()), 0.0D, 1.0D));
        }
        SERVER_BUILDER.pop();
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The Rift Wellhead: a frame of Rift Casing and Warp Glass built round a found fissure,",
                        "which digests the blocks fed to it into Rift Essence and has to be kept steady while it",
                        "does. What it digests is data - a data pack can add its own aerowarptics:agitating recipes.",
                        "A tier is a frame's size: i is three blocks along an edge, ii is five, iii is seven.")
                .push("wellhead");
        WELLHEAD_ENABLED = SERVER_BUILDER
                .comment("Let a Rift Collar form a frame at all. Off, a built frame stays casing and glass.")
                .define("wellheadEnabled", true);
        {
            double[] digestion = {0.5D, 1.5D, 3.0D};
            int[] gullet = {128, 512, 2_048};
            int[] tank = {4_000, 16_000, 64_000};
            int[] stabilisers = {2, 4, 8};
            double[] speed = {32.0D, 64.0D, 128.0D};
            double[] stress = {8.0D, 16.0D, 32.0D};
            int[] crater = {16, 28, 48};
            for (uk.co.iceconchy.aerowarptics.wellhead.WellheadTier tier : uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.values()) {
                int index = tier.ordinal();
                SERVER_BUILDER.comment("A frame " + tier.size() + " blocks along an edge.").push(tier.getSerializedName());
                WELLHEAD_DIGESTION.put(tier, SERVER_BUILDER
                        .comment("Millibuckets of Rift Essence a second a calm well digests out of what it is fed.")
                        .defineInRange("digestion", digestion[index], 0.01D, 1_000.0D));
                WELLHEAD_GULLET.put(tier, SERVER_BUILDER
                        .comment("How much undigested matter the well holds, in the millibuckets it will come to.")
                        .defineInRange("gullet", gullet[index], 16, 1_000_000));
                WELLHEAD_TANK.put(tier, SERVER_BUILDER
                        .comment("Millibuckets of Rift Essence the well holds until it is piped away. A full tank",
                                "does not stop the well: what it cannot hold goes back into the tear as strain.")
                        .defineInRange("tank", tank[index], 1_000, 10_000_000));
                WELLHEAD_STABILISERS.put(tier, SERVER_BUILDER
                        .comment("Switched-on Spatial Stabilisers that make a full cage. Fewer damp less.")
                        .defineInRange("stabilisers", stabilisers[index], 0, 64));
                WELLHEAD_MINIMUM_SPEED.put(tier, SERVER_BUILDER
                        .comment("RPM below which the collar damps nothing. It damps fully at twice this.")
                        .defineInRange("minimumSpeed", speed[index], 1.0D, 256.0D));
                WELLHEAD_STRESS.put(tier, SERVER_BUILDER
                        .comment("Stress impact of a formed well, per RPM.")
                        .defineInRange("stress", stress[index], 0.0D, 8_192.0D));
                WELLHEAD_CRATER.put(tier, SERVER_BUILDER
                        .comment("Radius, in blocks, of the ground a meltdown takes. The largest is close to half a",
                                "million blocks - see wellheadCarveBudget for what that costs a tick.")
                        .defineInRange("craterRadius", crater[index], 1, 60));
                SERVER_BUILDER.pop();
            }
        }
        WELLHEAD_YIELD_GAIN = SERVER_BUILDER
                .comment("How much faster a strained well digests: its rate is multiplied by 1 + this x strain.")
                .defineInRange("wellheadYieldGain", 1.8D, 0.0D, 20.0D);
        WELLHEAD_PRESSURE = SERVER_BUILDER
                .comment("Strain a second a calm well puts on its fissure while digesting the roughest feed.")
                .defineInRange("wellheadPressure", 0.02D, 0.0D, 1.0D);
        WELLHEAD_FEEDBACK = SERVER_BUILDER
                .comment("How much harder a strained tear is strained by the same feed: 1 + this x strain. This and",
                        "wellheadYieldGain together are what make a neglected well run away.")
                .defineInRange("wellheadFeedback", 1.5D, 0.0D, 20.0D);
        WELLHEAD_DAMPING = SERVER_BUILDER
                .comment("Strain a second, for each whole unit of strain, a well takes off when it is turned at",
                        "full speed inside a full, balanced cage with a sound frame.")
                .defineInRange("wellheadDamping", 0.2D, 0.0D, 10.0D);
        WELLHEAD_CAGE_FLOOR = SERVER_BUILDER
                .comment("The share of that damping rotation gives alone, with no stabilisers at all.")
                .defineInRange("wellheadCageFloor", 0.5D, 0.0D, 1.0D);
        WELLHEAD_STORM_FACTOR = SERVER_BUILDER
                .comment("What a raging Rift Storm multiplies a well's digestion, and so its strain, by.")
                .defineInRange("wellheadStormFactor", 1.3D, 1.0D, 10.0D);
        WELLHEAD_BLEED = SERVER_BUILDER
                .comment("Millibuckets a second a fissure loses from its own reservoir while its well is tearing.",
                        "A fissure bled dry seals, and leaves its Scar Heart.")
                .defineInRange("wellheadBleed", 20, 0, 100_000);
        WELLHEAD_STABILISER_REACH = SERVER_BUILDER
                .comment("Blocks from the fissure, along each axis, within which a switched-on Spatial Stabiliser",
                        "counts towards a well's cage.")
                .defineInRange("wellheadStabiliserReach", 8, 4, 32);
        WELLHEAD_RESUME_GRACE = SERVER_BUILDER
                .comment("Ticks after its chunk loads for which a well neither digests nor strains. Create reports",
                        "no rotation for the first ticks after a load, which a well would take for a stopped shaft.")
                .defineInRange("wellheadResumeGrace", 60, 0, 1_200);
        WELLHEAD_SCRAM_LOCKOUT = SERVER_BUILDER
                .comment("Seconds a well's hatch stays shut after a scram has dumped its gullet.")
                .defineInRange("wellheadScramLockout", 30, 0, 3_600);
        WELLHEAD_HARM = SERVER_BUILDER
                .comment("Damage a second a tearing well does to whatever living stands close to it.")
                .defineInRange("wellheadHarm", 2.0D, 0.0D, 100.0D);
        WELLHEAD_MELTDOWN_BREAKS_BLOCKS = SERVER_BUILDER
                .comment("Let a meltdown take the ground round it. Off, it takes the frame and the fissure and",
                        "hurts what stands near, and leaves the land alone.")
                .define("wellheadMeltdownBreaksBlocks", true);
        WELLHEAD_MELTDOWN_TICKS = SERVER_BUILDER
                .comment("How long a meltdown takes from the frame failing to the last of the fallout, in ticks.",
                        "The ground goes about two thirds of the way through.")
                .defineInRange("wellheadMeltdownLength", 900, 200, 6_000);
        WELLHEAD_CARVE_BUDGET = SERVER_BUILDER
                .comment("The most blocks of ground a meltdown takes in one tick. The scene's own pace asks for about",
                        "6,500 at worst for the largest crater; whatever it asks for past this is carried over and",
                        "finished in the fallout. Lower it if the server stutters at the detonation.")
                .defineInRange("wellheadCarveBudget", 8_000, 200, 100_000);
        WELLHEAD_DEBRIS = SERVER_BUILDER
                .comment("Loose pieces of ground a small meltdown may have in the air at once. A medium one may",
                        "have twice this and a large one three times. Past it, ground is swallowed where it lies.")
                .defineInRange("wellheadDebris", 150, 0, 1_000);
        WELLHEAD_SPECTACLE_RANGE = SERVER_BUILDER
                .comment("Blocks from a meltdown within which players are shown it.")
                .defineInRange("wellheadSpectacleRange", 384, 32, 1_024);
        WELLHEAD_ZONE_MINUTES = SERVER_BUILDER
                .comment("Minutes the ground a meltdown took stays unsettled: hurting and hopping whatever living",
                        "stands in it, less as it fades. 0 leaves no zone.")
                .defineInRange("wellheadZoneMinutes", 20, 0, 1_440);
        WELLHEAD_ZONE_HARM = SERVER_BUILDER
                .comment("Damage a second at the middle of fresh unsettled ground. Less towards its edge and as it fades.")
                .defineInRange("wellheadZoneHarm", 3.0D, 0.0D, 100.0D);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("The Rift Sunderer: a single-use heart for a ring of Rift Gate Frame, which dials nowhere",
                        "and tears a fissure where the aperture should have been.")
                .push("sunderer");
        SUNDERER_ENABLED = SERVER_BUILDER
                .comment("Let a Rift Sunderer fire at all. Off, it refuses every edge and spends nothing.")
                .define("sundererEnabled", true);
        SUNDERER_CHARGE = SERVER_BUILDER
                .comment("Millibuckets of Rift Essence one tear spends, all of it at the redstone edge. The tank",
                        "holds exactly this. A misfire hands half of it back.")
                .defineInRange("sundererCharge", 4_000, 0, 64_000);
        SUNDERER_MINIMUM_SPEED = SERVER_BUILDER
                .comment("The slowest a Sunderer may be turned and still fire, or hold a tear once it has.")
                .defineInRange("sundererMinimumSpeed", 128.0D, 0.0D, 256.0D);
        SUNDERER_STRESS = SERVER_BUILDER
                .comment("Stress units a Sunderer draws per RPM.")
                .defineInRange("sundererStress", 16.0D, 0.0D, 1_024.0D);
        SUNDERER_LEAST_OPENING = SERVER_BUILDER
                .comment("The narrowest and shortest opening, in blocks, a Sunderer will tear.")
                .defineInRange("sundererLeastOpening", 3, 2, 15);
        SUNDERER_MOST_OPENING = SERVER_BUILDER
                .comment("The widest and tallest opening, in blocks, a Sunderer will tear.")
                .defineInRange("sundererMostOpening", 7, 2, 15);
        SUNDERER_SHATTER = SERVER_BUILDER
                .comment("How much of the frame bursts as the ring tears, as a share. Never none and never all.")
                .defineInRange("sundererShatter", 0.3D, 0.0D, 1.0D);
        SUNDERER_HAND_CHANCE = SERVER_BUILDER
                .comment("The chance, each tear, that something on the far side reaches through first. 0 never.")
                .defineInRange("sundererHandChance", 0.02D, 0.0D, 1.0D);
        SUNDERER_SPACING = SERVER_BUILDER
                .comment("Blocks a tear must be from any fissure, Rift Wound or other sundering.")
                .defineInRange("sundererSpacing", 48, 0, 1_024);
        SUNDERER_ANCHOR_SPACING = SERVER_BUILDER
                .comment("Blocks a tear must be from any Warp Anchor, which pins the space round it.")
                .defineInRange("sundererAnchorSpacing", 32, 0, 1_024);
        SUNDERER_FISSURE_ESSENCE = SERVER_BUILDER
                .comment("Millibuckets a conjured fissure holds. It never drains into a siphon; this is only what a",
                        "tearing well can bleed from it before it seals.")
                .defineInRange("sundererFissureEssence", 3_000, 1, 100_000);
        SUNDERER_UNSETTLED_MINUTES = SERVER_BUILDER
                .comment("Minutes a conjured fissure lasts with no Rift Wellhead holding it, before it gutters shut.")
                .defineInRange("sundererUnsettledMinutes", 20, 1, 1_440);
        SUNDERER_RUPTURE_CRATER = SERVER_BUILDER
                .comment("Radius, in blocks, of the crater a sundering leaves when it ruptures.")
                .defineInRange("sundererRuptureCrater", 5, 1, 16);
        SUNDERER_RUPTURE_ZONE_MINUTES = SERVER_BUILDER
                .comment("Minutes the ground a rupture took stays unsettled. 0 leaves no zone.")
                .defineInRange("sundererRuptureZoneMinutes", 5, 0, 1_440);
        SUNDERER_RUPTURE_BREAKS_BLOCKS = SERVER_BUILDER
                .comment("Let a rupture take the ground round it. Off, it hurts and throws what stands near and takes",
                        "the ring, and leaves the land alone.")
                .define("sundererRuptureBreaksBlocks", true);
        SUNDERER_SPECTACLE_RANGE = SERVER_BUILDER
                .comment("Blocks from a sundering within which players are shown it.")
                .defineInRange("sundererSpectacleRange", 128, 32, 1_024);
        SUNDERER_FORBIDDEN_DIMENSIONS = SERVER_BUILDER
                .comment("Dimensions, by id, in which no fissure may be torn.")
                .defineListAllowEmpty("sundererForbiddenDimensions", () -> java.util.List.of(), () -> "minecraft:the_end",
                        entry -> entry instanceof String);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.comment("Diagnostics. Off by default and of no interest during normal play.")
                .push("debug");
        TRACE = SERVER_BUILDER
                .comment("Log everything needed to diagnose a problem with this mod, at INFO so it lands in",
                        "latest.log without touching the logging configuration. Every line starts with a",
                        "bracketed topic - [drive], [course], [warp], [launch], [arrival], [gate], [fleet]",
                        "and so on - and comes from the AeroWarptics logger, so searching the log for",
                        "\"aerowarptics\" finds all of it.",
                        "Covers: a header each time tracing starts (versions, this file's path, every value",
                        "in it, and each drive tier's real reach at a range of ship masses); every refusal,",
                        "including the ones that are otherwise silent - a lever pulled with no course set or",
                        "an uncharged drive, a warp refused for range or charge, a hull another drive still",
                        "holds - each with the drive, ship and price behind it; every drive state change,",
                        "course set and console action; the whole of each warp, tick by tick; arrival",
                        "searches; gates, probes, flotillas, beacons, anchors and storms. Players connected",
                        "while this is on trace their own side too, into their own log.",
                        "This file is per world: in single player it is saves/<world>/serverconfig/. The",
                        "command /aerowarptics trace on switches it without finding the file - operators, or",
                        "the owner of a single-player world with cheats off.",
                        "Turn it on to report a problem, reproduce the problem, then send latest.log. It is",
                        "verbose - a warp writes a line every tick of its flight - so leave it off otherwise.")
                .define("trace", false);
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
                            .defineInRange("instability", d.instability(), 0.0D, 1.0D),
                    SERVER_BUILDER.comment("Top speed of a free cruise at full throttle, in blocks per second.",
                                    "The ship really flies this fast, so keep it within what chunks can load:",
                                    "a cruise slows for ground that has not loaded yet whatever this says.",
                                    "Capped by flight.maxCommandedSpeed (blocks per tick, x20).")
                            .defineInRange("cruiseSpeed", d.cruiseSpeed(), 1.0D, 960.0D),
                    SERVER_BUILDER.comment("Escorts a flagship with this drive may lead in a flotilla, through a Rift",
                                    "Coupler. -1 is unlimited.")
                            .defineInRange("flotillaEscorts",
                                    uk.co.iceconchy.aerowarptics.flotilla.FlotillaRules.defaultEscorts(tier.index()),
                                    -1, 64)));
            SERVER_BUILDER.pop();
        }
        SERVER_BUILDER.pop();

        SERVER_SPEC = SERVER_BUILDER.build();
    }

    // ---------------------------------------------------------------- client

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ModConfigSpec.BooleanValue RIFT_DISTORTION;
    public static final ModConfigSpec.BooleanValue PLOTTER_DOCK_GUIDE;
    public static final ModConfigSpec.BooleanValue CRADLE_LINKS;
    public static final ModConfigSpec.BooleanValue WARD_SPECTACLE;
    public static final ModConfigSpec.BooleanValue WELLHEAD_SPECTACLE;
    public static final ModConfigSpec.DoubleValue WELLHEAD_SHAKE;
    public static final ModConfigSpec.BooleanValue WARD_MOTES;
    public static final ModConfigSpec.BooleanValue RIFT_LIGHTNING;
    public static final ModConfigSpec.BooleanValue RIFT_LENSING;
    public static final ModConfigSpec.BooleanValue WARP_CORRIDOR;
    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ModConfigSpec.BooleanValue WARP_FOV;
    public static final ModConfigSpec.EnumValue<uk.co.iceconchy.aerowarptics.gate.GatePassageStyle> GATE_PASSAGE;
    public static final ModConfigSpec.DoubleValue EFFECT_VOLUME;
    public static final ModConfigSpec.BooleanValue WARP_SOUNDS;
    public static final ModConfigSpec.BooleanValue RIFT_STORM_WEATHER;
    public static final ModConfigSpec.BooleanValue SLOT_MARKERS;
    public static final ModConfigSpec.BooleanValue HARMONIC_TETHERS;
    public static final ModConfigSpec.BooleanValue STATION_HUD;
    public static final ModConfigSpec.BooleanValue FLOTILLA_TITLES;
    public static final ModConfigSpec.DoubleValue COUNTDOWN_SHAKE;
    public static final ModConfigSpec.DoubleValue COUNTDOWN_DIMMING;
    public static final ModConfigSpec.IntValue FORCE_FOLD_HOLD_TICKS;
    public static final ModConfigSpec.BooleanValue CLIENT_TRACE;

    public static final ModConfigSpec CLIENT_SPEC;

    static {
        CLIENT_BUILDER.comment("Presentation of the warp sequence. None of this affects gameplay.").push("visualEffects");
        PARTICLE_DENSITY = CLIENT_BUILDER
                .comment("Scales every particle count the warp effects emit. 0 disables warp particles.")
                .defineInRange("particleDensity", 1.0D, 0.0D, 4.0D);
        RIFT_DISTORTION = CLIENT_BUILDER
                .comment("Draw the spatial distortion disc when a rift forms.")
                .define("riftDistortion", true);
        RIFT_LIGHTNING = CLIENT_BUILDER
                .comment("Draw lightning sparking off a rift's aperture and down its corridor.")
                .define("riftLightning", true);
        RIFT_LENSING = CLIENT_BUILDER
                .comment("Bend the view around a rift whose theme does it (Mostly Dangerous) - the sky and",
                        "the world behind it pulled round for a moment. Copies the frame while it is bending.",
                        "Stands down by itself under Fabulous graphics or while a shader pack is running; the",
                        "rift draws a ring of bent light instead, as it also does with this off.")
                .define("riftLensing", true);
        WARP_CORRIDOR = CLIENT_BUILDER
                .comment("Draw the corridor streaks and wake while the airship is in warp.")
                .define("warpCorridor", true);
        SCREEN_SHAKE = CLIENT_BUILDER
                .comment("Shake the camera during rift formation and exit.")
                .define("screenShake", true);
        WARP_FOV = CLIENT_BUILDER
                .comment("Widen the field of view while your ship is in a warp corridor or a free cruise.",
                        "Off, the view stays exactly as you set it for the whole journey. The corridor and",
                        "the starlines are still drawn; those follow warpCorridor.")
                .define("warpFov", true);
        GATE_PASSAGE = CLIENT_BUILDER
                .comment("What you see while a Rift Gate carries you across. Yours alone: every player",
                        "picks their own, and it changes nothing about the passage itself. The Passage",
                        "button on any gate's dial panel sets this.",
                        "KAWOOSH - the pane's light bursts over you and you are flung down a twisting",
                        "conduit of blue and violet plasma, to a flare of light at the far end.",
                        "TIMEY_WIMEY - a spiral of storm cloud, red sliding to blue, with lightning.",
                        "PUNCH_IT - the stars pull out into lines, then the blue-white lane rushes past.",
                        "ENERGISE - a column of light and rising motes; you arrive a speck at a time.",
                        "FULL_OF_STARS - two planes of every colour streaming out of a burning slit.",
                        "DOWN_THE_PIPE - a green pipe in big square pixels, between two irises.",
                        "QUIET - no tunnel and no motion: the view dims, holds, and brightens.",
                        "The lens easing back as you arrive follows warpFov, and the sound warpSounds.")
                .defineEnum("gatePassage", uk.co.iceconchy.aerowarptics.gate.GatePassageStyle.KAWOOSH);
        EFFECT_VOLUME = CLIENT_BUILDER
                .comment("How loud every sound of this mod's own is: drives, rifts, gates, flotillas, the",
                        "Collapse Cradle, the Rift Plotter and the Rift Ward, whether your own or somebody",
                        "else's. 1 is the mod's own level, 0 is silence, and 2 is twice as loud - which is how",
                        "loud they all used to be. How far a sound carries does not change.")
                .defineInRange("effectVolume", 1.0D, 0.0D, 2.0D);
        WARP_SOUNDS = CLIENT_BUILDER
                .comment("Play the sounds of a warp: a Rift Drive charging, locking on, winding up, venting",
                        "and faulting, the rift opening and closing, and the corridor. Off, none of them is",
                        "heard, from your own ship or anybody else's. A few other machines borrow the same",
                        "sounds - a Rift Brazier catching, a Rift Probe scanning, a Rift Plotter's rift - and",
                        "go quiet with them.")
                .define("warpSounds", true);
        RIFT_STORM_WEATHER = CLIENT_BUILDER
                .comment("Draw a Rift Storm's weather: the darkened violet sky and clouds, the rift rain that",
                        "replaces ordinary rain while it lasts, the flashes that light the sky, the aurora, the",
                        "tears, the vortex overhead, the streaks, the flickering clouds, and the patches of void",
                        "on Rift Drives. Off leaves the sky, rain and drives as vanilla draws them. The storm's",
                        "bolts still follow riftLightning and its sparks particleDensity, the cloud flashes need",
                        "riftLightning as well as this, and the storm itself still happens.")
                .define("riftStormWeather", true);
        PLOTTER_DOCK_GUIDE = CLIENT_BUILDER
                .comment("Draw the dock of every Rift Plotter whose dock helper is on: the space behind its entry",
                        "where a waiting vehicle is caught, the mark it is held at, and the rim of the rift it will",
                        "be sent through. Amber while it waits, brighter while it holds a vehicle, teal once that",
                        "vehicle is lined up.")
                .define("plotterDockGuide", true);
        CRADLE_LINKS = CLIENT_BUILDER
                .comment("Draw a Collapse Cradle's pull on its ring: motes of light drifting slowly into the machine",
                        "from every Depot it is linked to and every switched-on Spatial Stabiliser steadying it,",
                        "so what is connected can be seen. They fade while a collapse runs.")
                .define("cradleLinks", true);
        WELLHEAD_SPECTACLE = CLIENT_BUILDER
                .comment("Draw a Rift Wellhead's meltdown and the unsettled ground it leaves: the tear swelling out of",
                        "its frame, the darkened sky, the flash, the ring of air, the pull towards it, and the motes",
                        "and ticking of the zone afterwards. Off, the ground still goes and the zone still hurts.")
                .define("wellheadSpectacle", true);
        WELLHEAD_SHAKE = CLIENT_BUILDER
                .comment("How much a Rift Wellhead's meltdown shakes the view, as a share of what it would: 0 for none",
                        "at all, 1 for all of it. Under screenShake, which turns every shake of the mod's off.")
                .defineInRange("wellheadShake", 1.0D, 0.0D, 1.0D);
        WARD_SPECTACLE = CLIENT_BUILDER
                .comment("Draw a Rift Ward turning a rift aside: the tear caught over the field and dragged across",
                        "the sky to its rim, the dimming, and the corridor wrenched sideways for those aboard. Off,",
                        "the ward's weight still swings and its toll is still heard.")
                .define("wardSpectacle", true);
        WARD_MOTES = CLIENT_BUILDER
                .comment("Draw the few slow motes that settle at the rim of a standing Rift Ward's field.")
                .define("wardMotes", true);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Flotillas: what a fleet shows its crews while it musters and folds.").push("flotilla");
        SLOT_MARKERS = CLIENT_BUILDER
                .comment("Draw each escort's slot in the sky while a flotilla musters: amber while empty or",
                        "adrift, green once a ship is in station.")
                .define("slotMarkers", true);
        HARMONIC_TETHERS = CLIENT_BUILDER
                .comment("Draw the harmonic tethers between a flagship's coupler and its escorts'.")
                .define("harmonicTethers", true);
        STATION_HUD = CLIENT_BUILDER
                .comment("Show the escort pilot's drift and the way back to the slot while a muster is on.")
                .define("stationHud", true);
        FLOTILLA_TITLES = CLIENT_BUILDER
                .comment("Show the titles: SOUND STATIONS, each slot's state in the countdown, FLOTILLA",
                        "ASSEMBLED and SHAKEN LOOSE. The sounds play either way.")
                .define("flotillaTitles", true);
        COUNTDOWN_SHAKE = CLIENT_BUILDER
                .comment("How hard the camera shakes as a fold counts down. 0 is still. screenShake off also",
                        "stops it.")
                .defineInRange("countdownShake", 1.0D, 0.0D, 2.0D);
        COUNTDOWN_DIMMING = CLIENT_BUILDER
                .comment("How far the sky dims as a fold counts down. 0 leaves it alone.")
                .defineInRange("countdownDimming", 0.5D, 0.0D, 1.0D);
        FORCE_FOLD_HOLD_TICKS = CLIENT_BUILDER
                .comment("Ticks the Fold button has to be held to fold with escorts that are not ready,",
                        "leaving them behind.")
                .defineInRange("forceFoldHoldTicks", 60, 10, 200);
        CLIENT_BUILDER.pop();

        CLIENT_BUILDER.comment("Diagnostics. Off by default and of no interest during normal play.").push("debug");
        CLIENT_TRACE = CLIENT_BUILDER
                .comment("Log what this client is told about warps, gates and fleets and what it does with",
                        "it - each rift cue, a hull jumping across a fold and whether this client had it,",
                        "the refusal messages shown to you - plus the graphics settings the effects depend",
                        "on. Lines start with [client:...]. Switched on for you whenever you are connected",
                        "to a server tracing with debug.trace, so this is only needed to trace this client",
                        "on a server that is not.")
                .define("trace", false);
        CLIENT_BUILDER.pop();

        CLIENT_SPEC = CLIENT_BUILDER.build();
    }
}
