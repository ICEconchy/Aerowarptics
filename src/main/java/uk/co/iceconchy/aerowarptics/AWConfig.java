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
    public static final ModConfigSpec.DoubleValue ARRIVAL_BLOCK_CHECK_SCALE;
    public static final ModConfigSpec.BooleanValue KEEP_AIRSHIPS_LOADED;
    public static final ModConfigSpec.BooleanValue REQUIRE_LOADED_ARRIVAL;

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

    public static final ModConfigSpec.BooleanValue ALLOW_CROSS_DIMENSION_WARP;
    public static final ModConfigSpec.BooleanValue ALLOW_REDSTONE_INITIATION;
    public static final ModConfigSpec.BooleanValue ALLOW_PUBLIC_ANCHORS;
    public static final ModConfigSpec.BooleanValue ALLOW_PRIVATE_ANCHORS;
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
    public static final ModConfigSpec.BooleanValue FISSURE_SEEDS_ORE;
    public static final ModConfigSpec.IntValue FISSURE_ORE_CHANCE;
    public static final ModConfigSpec.IntValue FISSURE_ORE_RADIUS;
    public static final ModConfigSpec.IntValue FISSURE_ORE_MAX;

    public static final ModConfigSpec.IntValue GATE_DIAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_GATES_PER_PLAYER;

    public static final ModConfigSpec.IntValue CHUTE_COST_PER_ITEM;
    public static final ModConfigSpec.IntValue CHUTE_BATCH_SIZE;

    public static final ModConfigSpec.IntValue MODULATOR_UPKEEP_COST;
    public static final ModConfigSpec.IntValue MODULATOR_UPKEEP_INTERVAL;

    public static final ModConfigSpec.IntValue BEACON_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue BEACON_BEAM_TICKS;
    public static final ModConfigSpec.IntValue BEACON_AIM_RANGE;
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
    public static final ModConfigSpec.IntValue MANIFEST_GRACE_TICKS;

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
        KEEP_AIRSHIPS_LOADED = SERVER_BUILDER
                .comment("Keep an assembled airship carrying a Rift Drive - and any ground it warps onto -",
                        "loaded, so a ship can jump into unloaded wilderness and simply stay there. The",
                        "drive force-loads its own plot chunk and the ground under its hull, and the claim",
                        "is persisted with the world: an unattended ship parked in the wild is reloaded and",
                        "still there after a server restart. This is what makes a warp to an unattended",
                        "anchor safe rather than a way to lose a ship. The drive releases the claim when it",
                        "is broken or its ship is taken apart. Turn it off to let drive-bearing ships unload",
                        "like any other build - in which case consider turning requireLoadedArrival back on.",
                        "Note: every drive-bearing ship becomes a persistent chunk loader while this is on.")
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

        SERVER_BUILDER.comment("The flight through the rift: the run at the entry aperture, the passage",
                        "through it, and the run out of the far one. The corridor is flown inside the",
                        "entry aperture rather than anywhere else, so it needs no settings of its own -",
                        "how long it lasts comes from the drive's tier.")
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
        FISSURE_SEEDS_ORE = SERVER_BUILDER
                .comment("Let a fissure slowly crystallise the natural stone around it into Warp Crystal",
                        "Ore. This is the world source of the raw material - it is not salted through the",
                        "ground by ordinary worldgen, it grows outward from a tear. Only natural stone,",
                        "deepslate and their variants are converted, so player builds are left alone.")
                .define("seedsWarpCrystalOre", true);
        FISSURE_ORE_CHANCE = SERVER_BUILDER
                .comment("Average ticks between one fissure seeding one block of ore: higher is rarer.",
                        "A roll of 1-in-this is made each tick. At the default a fissure seeds a block",
                        "every 45 seconds or so while it is loaded.")
                .defineInRange("oreChance", 900, 1, 1_000_000);
        FISSURE_ORE_RADIUS = SERVER_BUILDER
                .comment("Blocks from the fissure that a seeded ore may appear within.")
                .defineInRange("oreRadius", 5, 1, 32);
        FISSURE_ORE_MAX = SERVER_BUILDER
                .comment("Most ore blocks one fissure will ever seed, so a long-loaded tear does not",
                        "turn a whole cavern to ore. Once reached, the fissure stops converting stone.")
                .defineInRange("oreMax", 24, 0, 4_096);
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
    public static final ModConfigSpec.BooleanValue RIFT_LIGHTNING;
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
        RIFT_LIGHTNING = CLIENT_BUILDER
                .comment("Draw lightning sparking off a rift's aperture and down its corridor.")
                .define("riftLightning", true);
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
