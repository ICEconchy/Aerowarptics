package uk.co.iceconchy.aerowarptics.drive;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.airship.AttitudeHold;
import uk.co.iceconchy.aerowarptics.airship.AirshipWarpData;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.airship.Fastenings;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.cooling.Coolants;
import uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlock;
import uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlockEntity;
import uk.co.iceconchy.aerowarptics.cooling.DriveCooling;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundSupercruisePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerRole;
import uk.co.iceconchy.aerowarptics.flotilla.FleetFolds;
import uk.co.iceconchy.aerowarptics.flotilla.FleetMuster;
import uk.co.iceconchy.aerowarptics.flotilla.FoldLog;
import uk.co.iceconchy.aerowarptics.flotilla.FoldOrder;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;
import uk.co.iceconchy.aerowarptics.hold.AirshipResidency;
import uk.co.iceconchy.aerowarptics.hold.HoldLedger;
import uk.co.iceconchy.aerowarptics.hold.HoldPurpose;
import uk.co.iceconchy.aerowarptics.hold.HoldRules;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;
import uk.co.iceconchy.aerowarptics.warp.CorridorTime;
import uk.co.iceconchy.aerowarptics.warp.CruiseSensor;
import uk.co.iceconchy.aerowarptics.warp.LaunchClearance;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;
import uk.co.iceconchy.aerowarptics.warp.FleetPassage;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;
import uk.co.iceconchy.aerowarptics.warp.FreeCruise;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;
import uk.co.iceconchy.aerowarptics.warp.ScatterOffset;
import uk.co.iceconchy.aerowarptics.warp.SpinUp;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.warp.CrewManifest;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.WarpPassengers;
import uk.co.iceconchy.aerowarptics.warp.WarpTrace;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;
import uk.co.iceconchy.aerowarptics.weather.RiftStorm;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Rift Drive: a Create kinetic machine that folds space for the airship it is bolted to.
 *
 * <p>Two tick paths meet here.
 * <ul>
 *   <li>{@link #tick()} is Create's ordinary block-entity tick. It converts rotational force into
 *       charge and advances the warp state machine.</li>
 *   <li>{@link #sable$tick(ServerSubLevel)} is Sable's actor tick, which only fires for block
 *       entities that are part of an airship. That call hands the drive its airship directly, so the
 *       drive never has to search for one.</li>
 * </ul>
 *
 * <p>The server owns every field that matters. Clients receive state, charge, tier and destination
 * purely to render; a client can neither start nor finish a warp.
 */
public class RiftDriveBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelActor, GeoBlockEntity, IHaveGoggleInformation {

    /** How many ticks without a Sable actor tick before the drive considers itself grounded. */
    private static final int AIRSHIP_GRACE_TICKS = 3;

    /** How often a drive renews its holds on its ship. Every second: cheap, and well inside every lease. */
    private static final int HOLD_RENEW_INTERVAL = 20;

    /**
     * How long a flight hold lasts unrenewed. Ten seconds: long enough to ride out a slow tick or two, short
     * enough that a flight whose drive vanished lets its ship go soon after.
     */
    private static final int FLIGHT_LEASE_TICKS = 200;

    /** How long a flight hold is kept after the sequence ends, while the hull settles at the far end. */
    private static final int FLIGHT_SETTLE_TICKS = 200;

    /**
     * How long a residency hold lasts unrenewed. A minute. It only has to outlast the drive going quiet for
     * a moment; across a restart it is game time, which stands still while the server is down.
     */
    private static final int RESIDENCY_LEASE_TICKS = 1200;

    /** Below this speed, in blocks a tick, a ship counts as left somewhere rather than going somewhere. */
    private static final double AT_REST_SPEED = 0.05D;

    /** A ship-side claim older than this is treated as debris from an interrupted session. */
    private static final int STALE_CLAIM_TICKS = 20 * 60 * 10;

    /** Ticks between accepting a destination and the stabiliser rings starting to align. */
    private static final int LOCK_IN_TICKS = 10;

    /** How often the drive looks over its frame of extenders. See {@link DriveAssembly#check}. */
    private static final int FRAME_CHECK_INTERVAL = 40;

    private static final Map<RiftDriveState, RawAnimation> ANIMATIONS = new EnumMap<>(RiftDriveState.class);

    static {
        for (RiftDriveState state : RiftDriveState.values()) {
            ANIMATIONS.put(state, RawAnimation.begin().thenLoop(state.animation()));
        }
    }

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private final RiftDriveTier tier;

    // ---- authoritative state -------------------------------------------------
    private RiftDriveState state = RiftDriveState.IDLE;
    private float charge;
    private float committedCost;
    /**
     * How far this warp is throwing the hull, fixed when the course was accepted.
     *
     * <p>Kept rather than re-measured on arrival, because by then the ship is at the far end and the
     * distance to the anchor is nearly zero. Anything that wants to know how big a journey this was -
     * a Spatial Siphon deciding what it caught - has to be told before the ship moves.
     */
    private double committedDistance;
    private int sequenceTicks;
    private int cooldownTicks;
    /**
     * How long the cooldown in progress was when it started, or {@code -1} for the tier's own figure.
     *
     * <p>Not simply the tier's figure, because a Rift Storm shortens it - and measuring progress against
     * the full figure would open a storm-shortened cooldown with its bar already half full. Minus one
     * rather than zero for "unknown", because zero is a real cooldown the multiplier can produce.
     */
    private int cooldownTotal = -1;
    private int errorTicks;

    /**
     * Counts down to the next renewal of this drive's holds on its ship. Starts at zero so a freshly
     * loaded drive asserts them on its first server tick rather than waiting a whole interval. See
     * {@link #tickHolds()}.
     */
    private int holdTimer;

    /**
     * This drive's own id: the owner of every hold it takes on its ship ({@link Holds}).
     *
     * <p>Not its position, which is a plot position a crossing changes, and not its ship's id, which a
     * crossing changes too. Saved with the block entity, so a rebuild on the far side of a crossing carries
     * it, as a coupler carries its own. Minted on first use.
     */
    @Nullable
    private UUID driveId;

    /**
     * Game time the flight hold is let go at, once the sequence is over, or zero while there is none to
     * let go. Held a little past the end, so a hull still settling at the far end is not dropped mid-settle.
     */
    private long flightSettleUntil;

    /**
     * Ground chunks forced by a drive from before ships were held with Sable's ticket, by
     * {@link ChunkPos#asLong}. Read from old saves only; let go of on the first renewal, and never added to
     * again. NeoForge reinstates them on load, which is what brings an old ship back in to be converted.
     */
    private final Set<Long> forcedChunks = new HashSet<>();

    private WarpFailure lastFailure = WarpFailure.NONE;
    /**
     * What tied the ship down the last time this drive was refused for it - the detail
     * {@link WarpFailure#SHIP_FASTENED} is told with. Not saved: it is only ever said at the moment of the
     * refusal, and asked afresh the next time.
     */
    @Nullable
    private Fastening lastFastening;
    @Nullable
    private UUID destinationAnchor;
    @Nullable
    private Vec3 destinationPos;
    /**
     * The height a committed fix asked to arrive at, or {@link ArrivalHeight#UNSET} for the server's.
     *
     * <p>Only a fix's. An anchor's is read off the anchor when the rift opens, alongside its position,
     * so an owner who raises their anchor while a ship is spinning up is heard.
     */
    private int destinationArrivalHeight = ArrivalHeight.UNSET;
    /**
     * Whether the committed course holds the ship's altitude rather than taking the destination's.
     *
     * <p>Taken when the course is committed, like a fix's height, and measured against the hull when the
     * rift opens - the altitude a pilot means is the one the ship is flying at as it goes, not the one
     * it was moored at when somebody first clicked the chart.
     */
    private boolean destinationHoldAltitude;
    /**
     * What to call the committed destination.
     *
     * <p>A fix has no anchor to ask for its name, and an anchor can be renamed while a ship is on its
     * way, so the label the pilot chose by is carried along with the course rather than looked up.
     */
    private String destinationLabel = "";

    /**
     * The dimension the committed course ends in - this drive's own for a fix, the anchor's for an anchor.
     *
     * <p>Kept rather than looked up at the crossing, because the crossing is where the hull is rebuilt if
     * this is not the level it is flying in, and by then it is far too late for an anchor that has been
     * moved to change which world the ship is being put down in.
     */
    @Nullable
    private ResourceKey<Level> destinationDimension;

    /**
     * Set while this drive is being saved to be rebuilt in another dimension: what it writes tells the copy
     * it becomes to carry on flying rather than treat the load as an interrupted warp, and what that copy
     * needs to repair after a rebuild. See {@link #crossInto}.
     */
    @Nullable
    private CompoundTag outgoingHandoff;

    /** What a copy of this drive, rebuilt by a crossing, was handed. Cleared once it has finished it. */
    @Nullable
    private CompoundTag incomingHandoff;

    /** Ticks a rebuilt drive has waited to find the ship it is on. */
    private int crossingWait;

    /** How long a rebuilt drive waits for its ship before calling the flight lost. */
    private static final int CROSSING_WAIT_TICKS = 20;

    /**
     * Set on the drive left behind once its copy has taken over on the far side. It does nothing more: its
     * block is about to be taken away with the rest of the old hull, and anything it did in the meantime -
     * an abort for the airship it can no longer see, above all - would be about a ship that is fine.
     */
    private boolean handedOff;

    /**
     * Set on a drive that was just rebuilt in another dimension in the middle of a crossing. Its first tick
     * finishes the crossing its old self started. See {@link #finishCrossing()}.
     */
    private boolean resumeAfterCrossing;

    /**
     * Set when the drive is read back off disk in the middle of a warp.
     *
     * <p>Not acted on inside {@code read}, because at that point there is no level to look an airship
     * up in. The next server tick sees the flag, says so in the log, and does what it still can for
     * the crew.
     */
    private boolean interruptedOnLoad;

    /** Which stage the dropped flight was in, kept only long enough to name it in the log. */
    private String interruptedStage = "";
    @Nullable
    private UUID initiator;
    /**
     * What a Rift Ward made of the rift this drive has open, from the moment it is planned until the far end is
     * told of it as the hull enters its corridor. Not saved: a drive reloaded mid-flight is {@code INTERRUPTED}
     * anyway, and the deflection itself is in the flight, which is.
     */
    @Nullable
    private uk.co.iceconchy.aerowarptics.ward.Wards.Turn wardTurn;

    /**
     * The berth the flight being planned would come to rest in, between planning it and being certain of it:
     * set by {@link #planBerth}, taken by {@link #beginWarp} once nothing can refuse the flight any more.
     */
    @Nullable
    private uk.co.iceconchy.aerowarptics.berth.Berths.Assignment berthPlanned;
    /**
     * The berth reserved for the flight in hand, by id. Saved, so that a drive rebuilt in another dimension by
     * a crossing still knows which slip is waiting for it; the ship's own id does not survive that.
     */
    @Nullable
    private UUID berthId;
    /**
     * The berths a flagship's fleet was given, by ship, between planning and being certain: the flagship's own
     * is {@link #berthPlanned}, these are its escorts'.
     */
    private java.util.Map<UUID, uk.co.iceconchy.aerowarptics.berth.Berths.Assignment> fleetBerths = java.util.Map.of();
    /** An escort's own berth, as its flagship gave it: where it comes out, turned, in place of its slot. */
    @Nullable
    private uk.co.iceconchy.aerowarptics.berth.Berths.Assignment foldBerth;
    /**
      * The course this drive is set to, waiting for something to fire it.
      *
      * <p>Set when a player picks a destination on a console or a chart - at which point they were
      * checked for permission, reach and whether they could see that anchor at all. A redstone signal
      * fires this and nothing else, which is what keeps wiring a drive up from granting access its
      * owner never had: the signal replays an authorisation rather than creating one.
      */
     @Nullable
    private WarpCourse standingCourse;

    /**
     * Who set the standing course.
     *
     * <p>A redstone-started warp still has somebody responsible for it - the person who chose where
     * this drive points - and that is who it is attributed to and who hears about the arrival. Without
     * this a warp fired by a circuit belongs to nobody, which is both wrong and, as it turned out, a
     * null waiting to be dereferenced.
     */
    @Nullable
    private UUID standingAuthor;

    /** Last tick's redstone reading, so a warp fires on the rising edge rather than while held. */
    private boolean redstonePowered;

    /**
     * The comparator reading the neighbours were last told about, or {@code -1} before the first. Not
     * saved: a freshly loaded drive tells them once, which costs nothing and cannot be stale.
     */
    private int lastComparatorSignal = -1;

    /**
     * The landing being loaded in the background while this drive spins up, or {@code null} when there
     * is none. Not persisted: a drive read back mid-spin has lost its sequence anyway, and the tickets
     * lapse on their own.
     */
    @Nullable
    private List<ArrivalTicket.Square> preloadSquares;
    /** The level that landing is in, which is not this one when an anchor is in another dimension. */
    @Nullable
    private ServerLevel preloadLevel;
    /** Ticks a fully spun drive has held for its landing to finish loading. */
    private int preloadWaited;

    /** Spin the current jump needs, in ticks at full rate. Set when the destination is committed. */
    private float spinRequired;
    /** Spin accumulated so far. Climbs faster the harder the drive is being turned. */
    private float spinProgress;
    /** Which quarter of the drive's own frame the bow points along. */
    private DriveHeading heading = DriveHeading.FORWARD;

    /** Whether a redstone edge flies the standing course or engages a free cruise. */
    private DriveMode mode = DriveMode.WAYPOINT;
    /**
     * Whether this drive looks for terrain before it moves a ship: the bow guard and the corridor proof
     * at the mooring, and the search for clear ground at the far end.
     *
     * <p>A pilot's switch, set at the console, for the ship the checks keep refusing when its pilot can
     * see perfectly well there is nothing in the way. Off, a warp is flown straight from the mooring and
     * set down exactly where the course says at exactly the height it asks for - into whatever happens to
     * be standing there. A free cruise still slows for terrain ahead either way: that is not a check made
     * before setting off, it is how a cruise is flown.
     */
    private boolean clearanceChecks = true;
    /**
     * Who last put this drive into free cruise.
     *
     * <p>The cruise's counterpart of {@link #standingAuthor}: nobody chooses a destination for a cruise,
     * so switching the mode is the moment somebody with authority over the drive said "a signal may send
     * this ship off along its bow". A redstone-started cruise is attributed to them and they hear how it
     * went.
     */
    @Nullable
    private UUID cruiseAuthor;

    /** Where the sequence under way is in being a cruise, or {@link CruisePhase#NONE} for a waypoint. */
    private CruisePhase cruisePhase = CruisePhase.NONE;
    /** Set when somebody at the console asks a cruise to let go, and acted on at its next tick. */
    private boolean cruiseDisengage;
    /** The throttle the redstone input is asking for, 0 to 1, read every tick the drive is set to cruise. */
    private double cruiseThrottle;
    /** How fast the cruise is going, in blocks per tick. */
    private double cruiseSpeed;
    /** The fastest the last look ahead said the ship could go and still stop in time, in blocks per tick. */
    private double cruiseSafeSpeed;
    /** What the last look ahead found - which is why a cruise is going slower than its throttle, if it is. */
    private CruiseSensor.Limit cruiseSight = CruiseSensor.Limit.CLEAR;
    /** Blocks actually flown on this cruise, which is what the charge is drained by. */
    private double cruiseDistance;
    /** The charge the cruise set off holding, which the whole flight is priced against. */
    private float cruiseStartCharge;
    /** Where the hull's centre was last tick, for measuring what was flown; {@code null} before the first. */
    @Nullable
    private Vector3d cruiseLastCentre;

    /**
     * Where a cruise is.
     *
     * <p>Kept alongside the drive's own state rather than as more states of it. A cruise spins up exactly
     * as a jump does and occupies the same states while it flies, so everything that already asks "is a
     * sequence running" - the stabiliser standing down, the console refusing to turn the bow, the lock on
     * the hull - keeps getting the right answer without learning that cruising exists.
     */
    public enum CruisePhase {
        /** Not a cruise. */
        NONE,
        /** Engaged, spinning up; the ship has not been thrown yet. */
        ENGAGING,
        /** Flying under the throttle. */
        CRUISING,
        /** Letting go: braking to a stop wherever the ship is. */
        DROPPING;

        public static CruisePhase byIndex(int index) {
            CruisePhase[] values = values();
            return index >= 0 && index < values.length ? values[index] : NONE;
        }
    }

    /** Why a cruise let go, for the trace and so the pilot can be told when it was not their idea. */
    private enum DropReason {
        ASKED, THROTTLE, STALLED, CHARGE, RANGE
    }

    /**
     * The step from this drive to the lowest corner of its frame of Drive Extenders, and the frame's
     * edge in blocks - {@code ZERO} and 1 for a drive standing on its own.
     *
     * <p>A step rather than a position for the reason every pointer in a frame is one: see
     * {@link DriveStructure#step}. Synced, because the renderer sizes the model from it.
     */
    private BlockPos frameStep = BlockPos.ZERO;
    private int frameSize = DriveStructure.MIN_SIZE;

    /** The journey through the rift, planned in full before the aperture opens. */
    @Nullable
    private WarpFlight flight;

    /**
     * Who is aboard for the journey, and what to do when somebody stops being aboard.
     *
     * <p>Persisted alongside the flight so that an interrupted warp can recover its passengers
     * after a restart. Without this, a passenger who came off the hull inside the fold would be
     * left wherever the crossing happened to leave them.
     */
    private final WarpPassengers passengers = new WarpPassengers();

    /**
     * The flotilla fold this drive has joined, as flagship or escort, or {@code null} for a warp of its own.
     *
     * <p>A fold is still one warp per drive: every ship flies its own flight, on its own charge, through its
     * own rift. What the order changes is small and all of it is here - what the jump costs (the fleet's
     * price, not this hull's), when an escort's rift opens (when the flagship's search has said where its
     * slot is, not when its own spin completes), where it comes out (exactly in that slot, not wherever its
     * own search would have put it), and what the corridor looks like (the flagship's colours). Saved, so a
     * crossing's rebuilt copy of this drive carries it; see {@link FoldOrder}.
     */
    @Nullable
    private FoldOrder foldOrder;
    /** Set on an escort's drive when the flagship has told it where to come out. */
    private boolean foldReleased;
    /** Where an escort's hull centre comes to rest, in the destination's world space. */
    @Nullable
    private Vec3 foldArrival;
    /** Extra ticks in the corridor: a shaken-loose escort comes out through its Wake Echo this much later. */
    private int foldExtraCorridor;
    /** Whether this escort was shaken loose, and comes out through a Wake Echo off its slot. */
    private boolean foldEcho;
    /**
     * The one rift this drive's fold goes through, when its flotilla folds that way: the passage its flight
     * keeps step with, and the two apertures it shares with the rest of the fleet - see {@link FleetPassage}.
     * {@code null} for a warp of its own, for a fold of a rift each, and for an escort shaken loose, which
     * tears its own. Saved with the order, so a crossing's rebuilt copy still closes the fleet's rift and
     * not a rift of its own that nobody opened.
     */
    @Nullable
    private FleetPassage foldPassage;

    /**
     * How long an escort's drive holds at full spin for the flagship's word before giving the fold up.
     *
     * <p>Long enough for the slowest flagship to spin up for its longest jump and count down; short enough
     * that an escort whose flagship vanished - its coupler broken, its chunk unloaded - is not left spinning
     * a charge away for good.
     */
    private static final int FOLD_HOLD_LIMIT = 20 * 60 * 5;

    // ---- transient -----------------------------------------------------------
    @Nullable
    private ServerSubLevel airshipSubLevel;
    private int ticksSinceAirshipTick = Integer.MAX_VALUE;
    private boolean stateDirty;

    // ---- client-only ---------------------------------------------------------
    // The tier's timings and RPM requirement live in the SERVER config, which a client connected to a
    // dedicated server does not have. They are synced with the rest of the drive's state instead.
    private float clientChargeLast;
    private float clientCharge;
    private String clientFlightStage = "";
    private float clientFlightProgress;
    /** The needle's drawn angle, eased towards the setting so it swings instead of snapping. */
    private float needleAngle = Float.NaN;
    private float needleAngleLast;
    private int syncedMinimumRpm;
    private int syncedPhaseDuration;
    private float syncedSpinRate;
    private float syncedStressImpact;
    /** Whether the server says this drive is spun up and waiting on its landing to load. */
    private boolean syncedAwaitingLanding;
    /** How far the cruise under way has come, and how far it may, as the server last said. */
    private float syncedCruiseDistance;
    private float syncedCruiseLimit;
    /** How fast the cruise under way is going in blocks per second, and what it can see ahead. */
    private float syncedCruiseSpeed;
    private int syncedCruiseSight;

    public RiftDriveBlockEntity(BlockPos pos, BlockState state, RiftDriveTier tier) {
        super(AWBlockEntities.RIFT_DRIVE.get(), pos, state);
        this.tier = tier;
    }

    /**
     * Asks a client to draw this machine, in case it arrived after the blocks round it were drawn - see
     * {@link DrawnAboard}.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        DrawnAboard.redraw(this);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    // ------------------------------------------------------------ Sable actor

    /**
     * Sable's per-tick callback for block entities that belong to an airship.
     *
     * <p>Receiving this call <em>is</em> the answer to "which airship am I on", which is why nothing
     * in this class ever scans blocks or iterates sub-levels.
     */
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        airshipSubLevel = subLevel;
        ticksSinceAirshipTick = 0;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaTime) {
        // The drive applies no forces of its own; the warp is a discrete relocation, not thrust.
    }

    /** The airship this drive belongs to, or {@code null} when it is not on one. */
    @Nullable
    public Airship airship() {
        if (airshipSubLevel == null || ticksSinceAirshipTick > AIRSHIP_GRACE_TICKS) {
            return null;
        }
        if (airshipSubLevel.isRemoved()) {
            airshipSubLevel = null;
            return null;
        }
        return Airship.of(airshipSubLevel);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned. A drive that has handed its flight to its copy in
        // another dimension is in the same position a tick early.
        if (handedOff || Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientChargeLast = clientCharge;
            clientCharge += (charge - clientCharge) * 0.2F;
            tickNeedle();
            AWClientHooks.tickDriveEffects(this);
            return;
        }
        if (ticksSinceAirshipTick != Integer.MAX_VALUE) {
            ticksSinceAirshipTick++;
        }

        if (interruptedOnLoad) {
            interruptedOnLoad = false;
            recoverFromInterruption();
        }
        if (resumeAfterCrossing) {
            if (!finishCrossing()) {
                // Still looking for the ship. Nothing below may run until it is found: the state machine
                // would take a ship it cannot see for a lost one.
                return;
            }
            resumeAfterCrossing = false;
        }

        // Staggered by position so a hangar full of drives does not all look over their frames on the
        // same tick.
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), FRAME_CHECK_INTERVAL) == 0L) {
            DriveAssembly.check(level, this);
            // On the same beat, and after it: what cools the drive stands round whatever frame that left.
            refreshCooling();
        }

        tickHolds();
        tickRedstone();
        tickCharge();
        tickStateMachine();
        if (handedOff) {
            // The crossing happened inside this tick. Nothing that follows is about a ship this drive has.
            return;
        }
        tickComparators();

        if (stateDirty) {
            stateDirty = false;
            sendData();
        }
    }

    /** What a comparator reads off this drive, or off any formed extender of its frame. */
    public int comparatorSignal() {
        return DriveSignal.of(state, charge);
    }

    /**
     * Tells the comparators round the drive when its reading changes.
     *
     * <p>A comparator only looks again when it is told to, so a reading that moved without this would sit
     * at whatever it was when the comparator was placed. Asked every tick and acted on only on a change,
     * which is a dozen times in a charge and three or four in a warp. Every cell of the frame is told,
     * because a comparator may be standing against any of them.
     */
    private void tickComparators() {
        int signal = comparatorSignal();
        if (signal == lastComparatorSignal || level == null) {
            return;
        }
        lastComparatorSignal = signal;
        for (BlockPos cell : frame().cells()) {
            if (level.isLoaded(cell)) {
                level.updateNeighbourForOutputSignal(cell, level.getBlockState(cell).getBlock());
            }
        }
    }

    /**
     * Converts rotational force into stored charge.
     *
     * <p>Below the tier's minimum RPM the drive cannot hold a charge cycle at all and slowly bleeds
     * what it has; between minimum and optimal it charges proportionally; above optimal the rate is
     * capped, so overspeeding buys nothing.
     */
    private void tickCharge() {
        float rpm = Math.abs(getSpeed());
        // A creative drive holds its charge whether or not anything is turning. Everything else about
        // how fast it charges is already zero-cost in its tier numbers.
        boolean powered = tier.creative() || rpm >= tier.minimumRpm();

        if (!state.participatesInCharging()) {
            return;
        }

        if (!powered) {
            if (state != RiftDriveState.IDLE) {
                transition(RiftDriveState.IDLE);
            }
            // Charge bleeds away slowly rather than vanishing, so a brief stall is not punishing.
            if (charge > 0.0F) {
                charge = Math.max(0.0F, charge - 1.0F / (tier.chargeTicks() * 8.0F));
                markStateDirty();
            }
            return;
        }

        if (state == RiftDriveState.IDLE) {
            transition(RiftDriveState.CHARGING);
            if (charge < 1.0F) {
                playSound(AWSounds.DRIVE_CHARGING.get(), 0.7F, 0.8F);
            }
        }

        if (charge >= 1.0F) {
            charge = 1.0F;
            if (state == RiftDriveState.CHARGING) {
                transition(RiftDriveState.CHARGED);
                playSound(AWSounds.DRIVE_CHARGED.get(), 0.9F, 1.0F);
            }
            return;
        }

        if (!state.accumulatesCharge()) {
            return;
        }
        double efficiency = Math.min(1.0D, rpm / (double) Math.max(1, tier.optimalRpm()));
        charge = (float) Math.min(1.0D, charge + efficiency / tier.chargeTicks());
        markStateDirty();
    }

    private void tickStateMachine() {
        switch (state) {
            case COOLDOWN -> {
                if (--cooldownTicks <= 0) {
                    cooldownTicks = 0;
                    transition(RiftDriveState.IDLE);
                }
            }
            case ERROR -> {
                if (--errorTicks <= 0) {
                    errorTicks = 0;
                    lastFailure = WarpFailure.NONE;
                    transition(RiftDriveState.IDLE);
                }
            }
            case DESTINATION_SELECTED -> {
                sequenceTicks++;
                if (sequenceTicks >= LOCK_IN_TICKS) {
                    beginStabilizing();
                }
            }
            case STABILIZING -> {
                sequenceTicks++;
                spinProgress += (float) spinRate();
                if (sequenceTicks % ArrivalTicket.PRELOAD_RENEW_INTERVAL == 0) {
                    renewPreload();
                }
                if (foldOrder != null && !foldOrder.lead()) {
                    // An escort's own spin says nothing about when it goes. It holds, turning, until the
                    // flagship's search has put the formation down somewhere and told it which part is its.
                    if (foldReleased) {
                        beginWarp();
                    } else if (sequenceTicks > FOLD_HOLD_LIMIT) {
                        abort(WarpFailure.FOLD_CALLED_OFF);
                    }
                } else if (spinProgress >= spinRequired && landingReady() && foldCountedDown()) {
                    beginWarp();
                }
                // The client draws a wind-up bar and animates off this, so it has to see it move.
                markStateDirty();
            }
            case WARPING, ARRIVING -> {
                sequenceTicks++;
                if (cruiseFlying()) {
                    tickCruise();
                } else {
                    tickFlight();
                }
            }
            default -> {
            }
        }
        if (handedOff) {
            return;
        }

        if (state.isSequenceRunning()) {
            Airship airship = airship();
            if (airship == null || !airship.isActive()) {
                abort(WarpFailure.AIRSHIP_LOST);
                return;
            }
            // A rift stays open only while the machinery keeps turning. A cruise is the exception: it
            // is not holding a rift open, and stopping a ship dead at ninety blocks a second for a
            // slipped belt is worse than the belt - so a stalled cruise brakes and lets go instead.
            if (!isRunningFastEnough() && state != RiftDriveState.ARRIVING && !cruiseFlying()) {
                abort(WarpFailure.INSUFFICIENT_POWER);
                return;
            }
            airship.warpData().advance(state.getSerializedName(), sequenceTicks, level.getGameTime());
        } else if (state == RiftDriveState.IDLE || state == RiftDriveState.CHARGING) {
            releaseStaleClaim();
        }
    }

    /**
     * Keeps this drive's ship loaded for as long as it needs to be: through every warp, and - with
     * {@code keepAirshipsLoaded} - wherever it is left afterwards, across restarts included.
     *
     * <p>Both are holds on the ship itself, with Sable's own ticket ({@link Holds#holdShip}), and both are
     * on leases this renews. That is the whole fix for ships lost at the far end of a warp. Sable keeps a
     * ship only while every column under its box is in block-ticking range, and unloads a crewless one the
     * moment it is not; the ground this used to force was renewed every twenty seconds, and the tickets a
     * flight laid down loaded their outer ring only as far as "loaded". A ship put down by a warp - a
     * beacon's summon above all, with nobody aboard - landed on ground that was not enough, and Sable
     * filed it away under whichever column failed. A big hull never came back, because no player's
     * simulation distance covers the whole of it at once. Sable's ticket instead holds the ground under
     * the ship wherever it goes, from the first tick of a warp to the last.
     *
     * <p><strong>The flight hold</strong> is taken whenever a sequence is running - lock-in, spin-up,
     * flight, cruise - and let go {@link #FLIGHT_SETTLE_TICKS} after it ends. Nothing in the warp itself has
     * to remember it: it follows the state, so a warp that ends any way at all ends its hold.
     *
     * <p><strong>The residency hold</strong> follows {@link HoldRules#residency}: held where the ship is left,
     * let go if it wanders off unmanned, taken again when it comes to rest.
     *
     * <p>A drive saved before either existed forced the ground under its hull chunk by chunk. That ground
     * is what loaded its ship back in; once the new holds are in place, every one of those chunks is let go.
     */
    private void tickHolds() {
        if (--holdTimer > 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        holdTimer = HOLD_RENEW_INTERVAL;
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            // No ship this tick - between Sable's calls, or not on one at all. The leases carry any hold
            // over a gap; one that goes on lapses, which is the point of them.
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        UUID id = driveId();
        long now = server.overworld().getGameTime();

        boolean flying = state.isSequenceRunning();
        if (flying) {
            flightSettleUntil = now + FLIGHT_SETTLE_TICKS;
        }
        // Every part the warp carries is held as the hull is. A part put down at the far end is on ground
        // the hull's ticket does not reach, and Sable would file a crewless one away as soon as it landed.
        List<Airship> vessel = airship.vessel();
        if (flightSettleUntil != 0L && now < flightSettleUntil) {
            // Renewed through the settle as well as the flight. A lease is only rewritten once half of it
            // has gone, so one last renewed as the sequence ended could run out before the settle did - and
            // be let go as if the drive had vanished, which is what the first long warp's trace said.
            for (Airship member : vessel) {
                Holds.holdShip(member, HoldPurpose.FLIGHT, id, FLIGHT_LEASE_TICKS, true);
            }
        } else if (flightSettleUntil != 0L) {
            Holds.releaseShip(server, id, HoldPurpose.FLIGHT, null);
            flightSettleUntil = 0L;
        }

        HoldLedger.ShipHold resident = Holds.heldBy(server, id, HoldPurpose.RESIDENCY, airship.uuid());
        Vector3dc at = airship.position();
        double roamed = resident == null ? 0.0D
                : HoldRules.roamed(resident.originX(), resident.originZ(), at.x(), at.z());
        boolean attended = flying || !airship.crew().isEmpty();
        boolean atRest = airship.velocity().lengthSquared() < AT_REST_SPEED * AT_REST_SPEED;
        int roamLimit = AWConfig.RESIDENCY_ROAM_LIMIT.get();
        switch (HoldRules.residency(AWConfig.KEEP_AIRSHIPS_LOADED.get(), resident != null, roamed, roamLimit,
                attended, atRest)) {
            case HOLD -> vessel.forEach(member ->
                    Holds.holdShip(member, HoldPurpose.RESIDENCY, id, RESIDENCY_LEASE_TICKS, false));
            case REANCHOR -> vessel.forEach(member ->
                    Holds.holdShip(member, HoldPurpose.RESIDENCY, id, RESIDENCY_LEASE_TICKS, true));
            case RELEASE -> {
                if (resident != null) {
                    for (Airship member : vessel) {
                        Holds.releaseShip(server, id, HoldPurpose.RESIDENCY, member.uuid());
                    }
                    Trace.log(Trace.RESIDENCY, "{} lets its ship go: {}", Trace.pos(worldPosition),
                            AWConfig.KEEP_AIRSHIPS_LOADED.get()
                                    ? "it has drifted " + Trace.blocks(roamed) + " unmanned from where it was left"
                                    + " (limit " + roamLimit + ") - held again once it comes to rest"
                                    : "keepAirshipsLoaded is off");
                }
            }
        }
        Holds.noteShip(airship);
        releaseLegacyResidency();
    }

    /** Lets go of every hold this drive has on its ship. Only on a genuine removal - see {@link #remove()}. */
    private void releaseHolds() {
        if (!(level instanceof ServerLevel serverLevel) || driveId == null) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        // Every ship this drive holds, not only its hull: the parts a warp carries are held alongside it.
        Holds.releaseShip(server, driveId, HoldPurpose.FLIGHT, null);
        Holds.releaseShip(server, driveId, HoldPurpose.RESIDENCY, null);
        flightSettleUntil = 0L;
    }

    /** Drops every ground chunk an older version of this drive forced. Needs a live server level. */
    private void releaseLegacyResidency() {
        if (forcedChunks.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (long chunk : forcedChunks) {
            AirshipResidency.releaseLegacy(serverLevel, worldPosition, chunk);
        }
        forcedChunks.clear();
        setChanged();
    }

    /** This drive's id, minted the first time anything asks. */
    public UUID driveId() {
        if (driveId == null) {
            driveId = UUID.randomUUID();
            setChanged();
        }
        return driveId;
    }

    /**
     * Drops a warp claim this drive left on its airship before a restart.
     *
     * <p>Without this, a server that goes down mid-warp would leave the airship permanently locked.
     */
    private void releaseStaleClaim() {
        Airship airship = airship();
        if (airship == null) {
            return;
        }
        AirshipWarpData data = airship.warpData();
        if (data.isWarping() && data.isOwnedBy(worldPosition)) {
            // Left over from a warp this drive was flying when the server stopped. Put the hull back
            // where it set off from before releasing, so nothing is stranded in the corridor.
            String held = data.describe(level.getGameTime());
            boolean moved = data.restoreOrigin(airship);
            data.release(true, level.getGameTime());
            Trace.always(Trace.CLAIM, "{} found its own warp lock left from before a restart ({}); released it{}",
                    Trace.pos(worldPosition), held, moved ? " and put the hull back where that warp set off" : "");
            lastFailure = WarpFailure.INTERRUPTED;
            markStateDirty();
        } else if (data.isWarping()) {
            String held = WarpTrace.enabled() ? data.describe(level.getGameTime()) : "";
            if (data.releaseIfStale(level.getGameTime(), STALE_CLAIM_TICKS)) {
                Trace.always(Trace.CLAIM, "{} released a stale warp lock on its hull ({})",
                        Trace.pos(worldPosition), held.isEmpty() ? "no heartbeat for " + STALE_CLAIM_TICKS + "t" : held);
            } else if (WarpTrace.enabled()) {
                // Only who holds it and in what phase: the full description's ages and progress change every
                // tick, and this is asked every tick.
                BlockPos owner = data.owningDrive();
                Trace.changed("lock:" + worldPosition.asLong(), Trace.CLAIM,
                        "{} its hull is locked by drive {} ({}), so this drive cannot launch until that lets go",
                        Trace.pos(worldPosition), owner == null ? "?" : Trace.pos(owner),
                        data.phase().isEmpty() ? "no phase recorded" : data.phase());
            }
        }
    }

    // ------------------------------------------------------------ warp control

    /**
     * Starts a warp, from a player or from a redstone edge.
     *
     * <p>Everything here is about the machine and the ship: power, charge, whether the anchor is
     * reachable and whether there is anywhere to land. The only things that depend on a player are
     * whether they may command this drive at all and whether they can see the anchor, and both are
     * checked before this - when the course is set, not when it is fired.
     *
     * @param player the player commanding it, or {@code null} when a redstone signal fired a course
     *               a player had already set
     */
    private WarpFailure startWarp(WarpCourse course, @Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }

        if (!isRunningFastEnough()) {
            return reject(WarpFailure.INSUFFICIENT_POWER);
        }
        if (!state.acceptsDestination()) {
            return reject(state.isSequenceRunning() ? WarpFailure.DRIVE_BUSY
                    : charge < 1.0F ? WarpFailure.INSUFFICIENT_CHARGE : WarpFailure.DRIVE_BUSY);
        }

        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return reject(WarpFailure.NO_AIRSHIP);
        }
        WarpFailure tied = fastened(airship, player, standingAuthor);
        if (tied.isFailure()) {
            return reject(tied);
        }

        // An anchor course has to be resolved and permitted; a fix is a bare position that was
        // already vouched for when the sounding it came from was offered.
        WarpAnchor anchor = null;
        BlockPos targetBlock;
        if (course.isAnchor()) {
            anchor = WarpAnchorRegistry.get(serverLevel).byId(course.anchorId());
            if (anchor == null) {
                return reject(WarpFailure.ANCHOR_MISSING);
            }
            if (!anchor.enabled()) {
                return reject(WarpFailure.ANCHOR_DISABLED);
            }
            // Skipped for a redstone start: whoever set this course could see the anchor at the time,
            // and a signal is not a way to reach one they could not.
            if (player != null && !anchor.isVisibleTo(player, WarpNetworkRegistry.get(serverLevel))) {
                return reject(WarpFailure.ANCHOR_FORBIDDEN);
            }
            targetBlock = anchor.pos();
        } else {
            targetBlock = course.fix();
        }

        WarpFailure destinationCheck = anchor != null
                ? WarpValidator.validateDestination(airship, anchor, tier, charge)
                : WarpValidator.validateFix(airship, course, tier, charge);
        if (destinationCheck.isFailure()) {
            return reject(destinationCheck);
        }
        // A fix a Rift Probe took in another dimension names it; every other fix is in this one.
        ResourceKey<Level> fixDimension = course.dimension() != null ? course.dimension() : serverLevel.dimension();

        // A flagship leading a fold pays the fleet's price for its share, not its own hull's. Checked before
        // the hull is claimed, so a refusal leaves nothing to let go of.
        if (foldOrder != null && charge + 1.0e-4F < foldOrder.cost()) {
            return reject(WarpFailure.INSUFFICIENT_CHARGE);
        }

        // One warp per airship, enforced on the airship itself rather than in a static map.
        AirshipWarpData data = airship.warpData();
        int totalTicks = LOCK_IN_TICKS + tier.stabilizeTicks() + tier.warpTicks() + tier.arriveTicks();
        Vec3 target = Vec3.atCenterOf(targetBlock);
        // A fix has no anchor id to claim under, so the claim is keyed on the drive's own identity.
        UUID claimKey = course.isAnchor() ? course.anchorId() : UUID.nameUUIDFromBytes(
                ("fix:" + targetBlock.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (partWarping(airship) || !data.claim(worldPosition, claimKey, target, totalTicks, serverLevel.getGameTime())) {
            return reject(WarpFailure.AIRSHIP_ALREADY_WARPING);
        }

        destinationAnchor = course.anchorId();
        destinationPos = target;
        destinationArrivalHeight = course.arrivalHeight();
        destinationHoldAltitude = course.holdAltitude();
        destinationLabel = anchor != null ? anchor.displayName() : course.label();
        // A redstone start has no player in hand, so the warp belongs to whoever set the course.
        // Every reader of this field already copes with it being absent.
        initiator = player != null ? player.getUUID() : standingAuthor;
        destinationDimension = anchor != null ? anchor.dimension() : fixDimension;
        // Asked of the anchor or the course rather than measured to the block: across dimensions a
        // position means nothing measured from here, and the jump is the configured length instead.
        committedDistance = anchor != null
                ? WarpValidator.warpDistance(airship, anchor)
                : WarpValidator.fixDistance(airship, course);
        committedCost = foldOrder != null ? foldOrder.cost() : (float) WarpCost.fromConfig(tier).cost(
                committedDistance, WarpValidator.effectiveMass(airship));
        sequenceTicks = 0;
        lastFailure = WarpFailure.NONE;
        castOff(serverLevel, airship);
        transition(RiftDriveState.DESTINATION_SELECTED);
        // Nobody is standing at a console when a circuit fires this, so the person who set the course
        // is told it has gone. Without it, a redstone launch is completely silent to the one player
        // who has a reason to care, and its failures are not - which reads as "it only tells me when
        // something is wrong", the wrong lesson to teach about a machine that throws ships.
        notifyInitiator(WarpFailure.NONE);
        if (anchor != null) {
            notifyAnchor(serverLevel, anchor, true);
        }
        playSound(AWSounds.DESTINATION_LOCK.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.DESTINATION_LOCK);
        return WarpFailure.NONE;
    }

    /**
     * Watches the drive's redstone input and fires the standing course on a rising edge.
     *
     * <p>Edge rather than level, so a lever left on does not batter the drive with attempts every
     * tick and a drive that comes back from a cooldown under a live signal stays put until somebody
     * actually flips something.
     *
     * <p>This is what makes Create Simulated's Throttle Lever work as a launch control: it is a
     * redstone source, so it needs nothing from this mod beyond reading the signal it already emits.
     * A vanilla lever, button or pressure plate does just as well.
     */
    private void tickRedstone() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean signal = level.hasNeighborSignal(worldPosition);
        boolean rising = signal && !redstonePowered;
        redstonePowered = signal;
        if (mode == DriveMode.FREE_CRUISE) {
            tickThrottle(rising);
            return;
        }
        if (!rising) {
            return;
        }
        if (!AWConfig.ALLOW_REDSTONE_INITIATION.get()) {
            Trace.log(Trace.DRIVE, "{} redstone signal ignored: permissions.allowRedstoneInitiation is off",
                    Trace.pos(worldPosition));
            return;
        }
        WarpCourse course = standingCourse;
        if (course == null || !state.acceptsDestination()) {
            // Nothing set, or the drive is already busy. Both are silent to the player: a redstone input
            // has no one to complain to, and a circuit that clicks every few seconds should not fill a
            // log. Traced, because "I pulled the lever and nothing happened" is exactly this.
            if (WarpTrace.enabled()) {
                Trace.log(Trace.DRIVE, "{} redstone signal ignored: {} - {}", Trace.pos(worldPosition),
                        course == null ? "no course is set (choose one at an Astrolabe)"
                                : "the drive only launches from CHARGED (fully charged and idle)",
                        traceState());
            }
            return;
        }
        Trace.log(Trace.DRIVE, "{} redstone signal: firing {} set by {}", Trace.pos(worldPosition),
                describeCourse(course), Trace.who(standingAuthor));
        // A flagship fired by a circuit takes its flotilla with it - whoever of it is ready - rather than
        // leaving without a word to ships holding station off its beam.
        WarpFailure fleet = fleetCall(null, FleetMuster.Call.REDSTONE);
        if (fleet == null) {
            startWarp(course, null);
        } else {
            Trace.log(Trace.DRIVE, "{} redstone signal taken by the flotilla: {}", Trace.pos(worldPosition),
                    fleet.getSerializedName());
        }
    }

    /**
     * Sets the course this drive will fly when something fires it.
     *
     * <p>Called when a player chooses a destination on an Astrolabe Cartography Table. Choosing is
     * the moment permission is checked, so this is the moment the authorisation a redstone signal
     * later replays is granted.
     */
    public void armCourse(@Nullable WarpCourse course, @Nullable UUID author) {
        Trace.log(Trace.COURSE, "{} course {} by {} (was {})", Trace.pos(worldPosition), describeCourse(course),
                Trace.who(author), describeCourse(standingCourse));
        standingCourse = course;
        standingAuthor = author;
        markStateDirty();
    }

    @Nullable
    public WarpCourse standingCourse() {
        return standingCourse;
    }

    /**
     * Sets this drive's course without starting anything.
     *
     * <p>This is the authorising moment for everything that fires it afterwards, so it is where the
     * player is checked - may they command this drive, and can they see this anchor at all. A redstone
     * signal later replays that decision; it never makes one.
     */
    public WarpFailure setCourse(ServerPlayer player, UUID anchorId) {
        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return permission;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(anchorId);
        if (anchor == null) {
            return WarpFailure.ANCHOR_MISSING;
        }
        if (!anchor.isVisibleTo(player, WarpNetworkRegistry.get(serverLevel))) {
            return WarpFailure.ANCHOR_FORBIDDEN;
        }
        // Holding altitude carries over from the course being replaced. It is chosen at the same chart
        // as the anchor, and a pilot who asked to keep their height should not have to ask again for
        // every destination they click - a Rift Probe's course never holds, so aiming by probe resets it.
        boolean hold = standingCourse != null && standingCourse.holdAltitude();
        armCourse(WarpCourse.toAnchor(anchorId, anchor.displayName()).withHoldAltitude(hold),
                player.getUUID());
        return WarpFailure.NONE;
    }

    /**
     * Tells the standing course to hold the ship's altitude, or to take the destination's again.
     *
     * <p>Checked the same way as setting a course, because it changes where the ship ends up. The
     * course's author is kept, though: whoever toggles this has not chosen the destination, and the
     * author is who a redstone launch answers to for having been allowed to see that anchor at all.
     *
     * <p>With no course set there is nothing to hold, and nothing happens - the chart does not offer
     * the toggle until there is.
     */
    public WarpFailure setHoldAltitude(ServerPlayer player, boolean hold) {
        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return permission;
        }
        if (standingCourse != null && standingCourse.holdAltitude() != hold) {
            armCourse(standingCourse.withHoldAltitude(hold), standingAuthor);
        }
        return WarpFailure.NONE;
    }

    /**
     * Sets this drive's course to a position nobody has marked.
     *
     * <p>The counterpart of {@link #setCourse} for a Rift Probe's sounding. There is no anchor to ask
     * whether this player may use it, so the permission that matters is the one over the drive
     * itself - and over the probe, which the probe checked before it ever offered the reading.
     *
     * <p>Range and affordability are deliberately <em>not</em> checked here. They are checked when the
     * warp fires, against the charge the drive has then rather than the charge it has now, and a
     * course a pilot cannot afford yet is a course worth keeping while it charges.
     *
     * <p>Nor is whether the ship can cross into {@code dimension} at all, for the same reason an
     * anchor's is not: that is the launch's question, and the probe asked the part of it that does not
     * depend on the ship before it offered the dimension.
     *
     * @param dimension     the dimension the sounding was taken in, which need not be this one
     * @param arrivalHeight blocks above the fix to come in at, as the probe's slider stood when the
     *                      course was sent; clamped again when the rift opens
     */
    public WarpFailure setFixCourse(ServerPlayer player, ResourceKey<Level> dimension, BlockPos fix,
                                    int arrivalHeight, String label) {
        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return permission;
        }
        if (!(level instanceof ServerLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        armCourse(WarpCourse.toFix(dimension, fix, arrivalHeight, label), player.getUUID());
        return WarpFailure.NONE;
    }

    /**
     * Sets a course to a bare position and fires it in the same breath.
     *
     * <p>The one place in this mod where choosing and launching are a single action, because a Rift
     * Beacon is not a console - there is nothing to look at between deciding and committing, and a
     * beacon that only armed a course would need the pilot to go back aboard and pull a lever, which
     * is the entire thing it exists to avoid.
     *
     * <p>What it is <em>not</em> is a way around any of the machine's own conditions. It goes through
     * {@link #startWarp} exactly as a redstone edge does, so speed, charge, drive state, range,
     * affordability, the one-warp-per-hull lock and the search for somewhere the ship actually fits
     * are all asked and answered the same way. The only thing the beacon replaces is <em>who is
     * allowed to ask</em>, and it replaces it with a grant taken earlier - see
     * {@link uk.co.iceconchy.aerowarptics.beacon.RiftBeaconBinding}.
     *
     * @param author the player whose beacon this is, credited with the warp and told how it went
     */
    public WarpFailure summonTo(ServerPlayer author, BlockPos fix, String label) {
        if (!(level instanceof ServerLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        // A beacon has no height of its own to offer, so it asks for the server's - resolved when the
        // rift opens, like any other course that never had one chosen.
        WarpCourse course = WarpCourse.toFix(fix, ArrivalHeight.UNSET, label);
        // Armed before it is fired rather than after, so a refusal leaves the drive holding the
        // course that was refused. A pilot who was told "not enough charge" can then walk aboard,
        // wait, and pull a lever, instead of having to go back and aim the thing again.
        armCourse(course, author.getUUID());
        // A flagship whose flotilla is set to answer the beacon brings it; see FleetMuster.Call.BEACON.
        WarpFailure fleet = fleetCall(author, FleetMuster.Call.BEACON);
        Trace.log(Trace.DRIVE, "{} summoned by {}'s beacon to {}{}", Trace.pos(worldPosition), Trace.who(author),
                Trace.pos(fix), fleet != null ? " - taken by the flotilla: " + fleet.getSerializedName() : "");
        return fleet != null ? fleet : startWarp(course, author);
    }

    /** Player-initiated cancellation. Only legal before the rift actually opens. */
    public WarpFailure cancelWarp(ServerPlayer player) {
        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return permission;
        }
        if (!state.isCancellable()) {
            return WarpFailure.DRIVE_BUSY;
        }
        abort(WarpFailure.CANCELLED);
        return WarpFailure.NONE;
    }

    // ------------------------------------------------------------ flotilla fold

    /** The fold this drive has joined, or {@code null}. */
    @Nullable
    public FoldOrder foldOrder() {
        return foldOrder;
    }

    /**
     * Commits this drive to lead a fold: its standing course, fired as a warp of its own, at the fleet's
     * price. Everything {@link #startWarp} checks is checked; the only thing a fold adds is the order.
     *
     * @param player whoever pressed Fold, or {@code null} for a beacon or a circuit
     */
    public WarpFailure leadFold(FoldOrder order, @Nullable ServerPlayer player) {
        WarpCourse course = standingCourse;
        Trace.log(Trace.DRIVE, "{} leading fold {} by {} at a share of {}", Trace.pos(worldPosition), order.fold(),
                player == null ? "circuit or beacon" : Trace.who(player), Trace.percent(order.cost()));
        if (course == null) {
            return reject(WarpFailure.ANCHOR_MISSING);
        }
        foldOrder = order;
        clearFoldRelease();
        WarpFailure result = startWarp(course, player);
        if (result.isFailure()) {
            foldOrder = null;
        }
        return result;
    }

    /**
     * Commits this drive to follow a flagship into a fold.
     *
     * <p>The machine's own conditions are asked exactly as a warp of its own would ask them - turning,
     * charged, on a ship, the ship not already warping - plus its share of the fleet's price. Where it is
     * going is not settled here: it spins up towards the flagship's target and holds, and only when the
     * flagship's search has proved the whole formation clear does {@link #releaseFold} say where its slot is.
     */
    public WarpFailure followFold(FoldOrder order) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        Trace.log(Trace.DRIVE, "{} ordered into fold {} as an escort: {} blocks to {} at a share of {}",
                Trace.pos(worldPosition), order.fold(), Trace.blocks(order.distance()), Trace.pos(order.target()),
                Trace.percent(order.cost()));
        if (!isRunningFastEnough()) {
            return reject(WarpFailure.INSUFFICIENT_POWER);
        }
        if (!state.acceptsDestination()) {
            return reject(state.isSequenceRunning() ? WarpFailure.DRIVE_BUSY
                    : charge < 1.0F ? WarpFailure.INSUFFICIENT_CHARGE : WarpFailure.DRIVE_BUSY);
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return reject(WarpFailure.NO_AIRSHIP);
        }
        WarpFailure tied = fastened(airship, null, order.initiator());
        if (tied.isFailure()) {
            return reject(tied);
        }
        if (!order.dimension().equals(serverLevel.dimension()) && !CrossDimensionWarp.isSupported()) {
            return reject(WarpFailure.DIMENSION_UNSUPPORTED);
        }
        if (charge + 1.0e-4F < order.cost()) {
            return reject(WarpFailure.INSUFFICIENT_CHARGE);
        }
        int totalTicks = LOCK_IN_TICKS + tier.stabilizeTicks() + tier.warpTicks() + tier.arriveTicks();
        UUID claimKey = UUID.nameUUIDFromBytes(("fold:" + order.fold()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Vec3 target = Vec3.atCenterOf(order.target());
        if (partWarping(airship)
                || !airship.warpData().claim(worldPosition, claimKey, target, totalTicks, serverLevel.getGameTime())) {
            return reject(WarpFailure.AIRSHIP_ALREADY_WARPING);
        }
        foldOrder = order;
        clearFoldRelease();
        destinationAnchor = null;
        destinationPos = target;
        destinationArrivalHeight = ArrivalHeight.UNSET;
        destinationHoldAltitude = false;
        destinationLabel = order.label();
        destinationDimension = order.dimension();
        initiator = order.initiator();
        committedDistance = order.distance();
        committedCost = order.cost();
        sequenceTicks = 0;
        lastFailure = WarpFailure.NONE;
        castOff(serverLevel, airship);
        transition(RiftDriveState.DESTINATION_SELECTED);
        playSound(AWSounds.DESTINATION_LOCK.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.DESTINATION_LOCK);
        return WarpFailure.NONE;
    }

    /**
     * The flagship's word: where this escort's hull comes to rest, and whether it goes the long way.
     *
     * @param centre  its slot at the far end - or, shaken loose, somewhere off it - in the destination's world
     * @param extra   ticks longer in the corridor than its tier would take, for a Wake Echo
     * @param echo    whether it was shaken loose
     * @param passage the fleet's one rift, to go through in step with the rest; {@code null} to tear its own
     */
    public void releaseFold(Vec3 centre, int extra, boolean echo, @Nullable FleetPassage passage) {
        if (foldOrder == null || foldOrder.lead() || foldReleased) {
            return;
        }
        foldReleased = true;
        foldArrival = centre;
        foldExtraCorridor = Math.max(0, extra);
        foldEcho = echo;
        foldPassage = echo ? null : passage;
    }

    /** Stands down an escort whose fold was called off before its rift opened. Costs it nothing. */
    public void callOffFold() {
        if (foldOrder != null && !foldOrder.lead() && state.isCancellable()) {
            abort(WarpFailure.FOLD_CALLED_OFF);
        }
    }

    private void clearFoldRelease() {
        foldReleased = false;
        foldArrival = null;
        foldExtraCorridor = 0;
        foldEcho = false;
        foldPassage = null;
        foldBerth = null;
    }

    private void clearFold() {
        foldOrder = null;
        clearFoldRelease();
    }

    /**
     * Whether a flagship's spun-up drive may open its rift, as far as its fold is concerned: once the
     * countdown has run. The first call starts it. A drive in no fold, or one whose coupler has gone, is
     * never held.
     */
    private boolean foldCountedDown() {
        if (foldOrder == null || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        RiftCouplerBlockEntity coupler = findCoupler();
        return coupler == null || coupler.muster() == null || coupler.muster().readyToTear(serverLevel);
    }

    /** The formation this flagship brings, for its search and its preload. Empty in no fold. */
    private List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> foldCompanions(
            @Nullable FleetPassage passage) {
        if (foldOrder == null || !foldOrder.lead() || !(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        RiftCouplerBlockEntity coupler = findCoupler();
        return coupler == null || coupler.muster() == null ? List.of()
                : coupler.muster().companions(serverLevel, passage);
    }

    /**
     * The passage a flagship takes its fleet through one rift on, from where the fleet is now - or
     * {@code null} for a warp of its own, or a fold of a rift each. See {@link FleetPassage}.
     */
    @Nullable
    private FleetPassage fleetPassage() {
        if (foldOrder == null || !foldOrder.lead() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        RiftCouplerBlockEntity coupler = findCoupler();
        return coupler == null || coupler.muster() == null ? null : coupler.muster().passage(serverLevel);
    }

    /**
     * Asks this drive's coupler whether a launch is a fleet's, and launches it as one if so.
     *
     * @return the fleet's answer, or {@code null} when there is no flotilla to take and the drive should
     * fly on its own as it always did
     */
    @Nullable
    private WarpFailure fleetCall(@Nullable ServerPlayer player, FleetMuster.Call how) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        RiftCouplerBlockEntity coupler = findCoupler();
        if (coupler == null || coupler.role() != CouplerRole.FLAGSHIP || coupler.muster() == null) {
            return null;
        }
        return coupler.muster().call(serverLevel, player, how);
    }

    @Nullable
    private RiftCouplerBlockEntity cachedCoupler;
    private long cachedCouplerTick = Long.MIN_VALUE;

    /**
     * The Rift Coupler fitted to this drive, looked for the way the Modulator is: all round the frame, and
     * trusted only if the coupler agrees this is its drive.
     */
    @Nullable
    public RiftCouplerBlockEntity findCoupler() {
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        if (cachedCouplerTick == now) {
            return cachedCoupler != null && !cachedCoupler.isRemoved() ? cachedCoupler : null;
        }
        cachedCouplerTick = now;
        cachedCoupler = null;
        for (BlockPos around : DriveStructure.surroundings(frame())) {
            if (level.isLoaded(around) && level.getBlockEntity(around) instanceof RiftCouplerBlockEntity coupler
                    && coupler.linkedDrive() == this) {
                cachedCoupler = coupler;
                break;
            }
        }
        return cachedCoupler;
    }

    // ---------------------------------------------------------------- cooling

    /**
     * What stands round this drive and cools it, in the order the frame's surroundings are walked.
     *
     * @param vents turning Cooling Vents that answer to this drive - all of them, counted or not
     * @param ice   blocks of ice against the drive or its frame
     */
    public record Coolers(List<BlockPos> vents, List<BlockPos> ice) {

        static final Coolers NONE = new Coolers(List.of(), List.of());
    }

    private Coolers cachedCoolers = Coolers.NONE;
    private long cachedCoolersTick = Long.MIN_VALUE;

    /** How many coolers of each kind count, and what they leave of a cooldown, as the client was last told. */
    private int syncedCoolingVents;
    private int syncedCoolingIce;
    private float syncedCoolingShare = 1.0F;

    /**
     * The coolers round this drive, resolved at most once a tick. Server side: whether a vent is turning
     * fast enough is the server's to say.
     *
     * <p>Looked for all round the frame, as every module is, so a vent or a block of ice against any face
     * of an extended drive cools it. A vent is trusted only if it agrees this is its drive, for the
     * Modulator's reason - one standing between two drives cools one of them, not both. Ice has no say and
     * is nobody's in particular: a block between two drives cools both, which costs nothing to allow and
     * would take a rule to forbid.
     */
    public Coolers coolers() {
        if (level == null) {
            return Coolers.NONE;
        }
        long now = level.getGameTime();
        if (cachedCoolersTick == now) {
            return cachedCoolers;
        }
        List<BlockPos> vents = new ArrayList<>();
        List<BlockPos> ice = new ArrayList<>();
        for (BlockPos around : DriveStructure.surroundings(frame())) {
            if (!level.isLoaded(around)) {
                continue;
            }
            BlockState beside = level.getBlockState(around);
            if (Coolants.isPassive(beside)) {
                ice.add(around);
            } else if (beside.getBlock() instanceof CoolingVentBlock
                    && level.getBlockEntity(around) instanceof CoolingVentBlockEntity vent && !vent.isRemoved()
                    && vent.isTurning() && vent.linkedDrive() == this) {
                vents.add(around);
            }
        }
        cachedCoolersTick = now;
        cachedCoolers = new Coolers(List.copyOf(vents), List.copyOf(ice));
        return cachedCoolers;
    }

    /** Whether this vent is one of those the drive takes any good from - see {@link DriveCooling#fit}. */
    public boolean ventCounts(CoolingVentBlockEntity vent) {
        return DriveCooling.ventCounts(coolers().vents().indexOf(vent.getBlockPos()), coolingSettings().most());
    }

    private static DriveCooling.Settings coolingSettings() {
        return new DriveCooling.Settings(AWConfig.COOLING_MOST.get(), AWConfig.COOLING_VENT_SHARE.get(),
                AWConfig.COOLING_ICE_SHARE.get());
    }

    /**
     * Shortens the cooldown a warp has just earned by whatever is cooling the drive, and has it show: the
     * vents that counted blow their steam, and the ice hisses.
     *
     * @param earned the cooldown before cooling, in ticks - the tier's, less a storm's share
     */
    private int cool(int earned) {
        Coolers coolers = coolers();
        DriveCooling.Settings settings = coolingSettings();
        DriveCooling.Fit fit = DriveCooling.fit(coolers.vents().size(), coolers.ice().size(), settings.most());
        int cooled = DriveCooling.cooldownTicks(earned, fit, settings);
        // Silent to the player otherwise: the console only ever shows the cooldown that came out of this.
        Trace.log(Trace.DRIVE, "drive at {} cools in {}t of the {}t it earned: {} of {} turning vent(s) and {} of {}"
                        + " ice counted (most {}, a vent leaves {}, ice leaves {})",
                worldPosition, cooled, earned, fit.vents(), coolers.vents().size(), fit.ice(), coolers.ice().size(),
                settings.most(), settings.ventShare(), settings.iceShare());
        refreshCooling();
        if (earned <= 0 || !(level instanceof ServerLevel serverLevel)) {
            // Nothing to carry out. A creative drive has no cooldown, and steam off it would be a lie.
            return cooled;
        }
        for (BlockPos pos : coolers.vents().subList(0, fit.vents())) {
            if (level.getBlockEntity(pos) instanceof CoolingVentBlockEntity vent) {
                vent.blow();
            }
        }
        Airship airship = airship();
        for (BlockPos pos : coolers.ice().subList(0, fit.ice())) {
            // Thrown where the ship puts the block: a particle is in the world, and the ice is in the plot.
            Vec3 at = airship == null ? Vec3.atCenterOf(pos) : airship.toWorld(Vec3.atCenterOf(pos));
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, at.x, at.y, at.z,
                    6, 0.35D, 0.35D, 0.35D, 0.01D);
        }
        if (fit.ice() > 0) {
            playSound(AWSounds.COOLANT_HISS.get(), 0.6F, 1.0F);
        }
        return cooled;
    }

    /**
     * Works out what the goggles say about cooling, and tells the client if it has changed.
     *
     * <p>Kept as three numbers the client is sent rather than counted there, because whether a vent counts
     * turns on the server's config and the client must not read it.
     */
    private void refreshCooling() {
        Coolers coolers = coolers();
        DriveCooling.Settings settings = coolingSettings();
        DriveCooling.Fit fit = DriveCooling.fit(coolers.vents().size(), coolers.ice().size(), settings.most());
        float share = (float) DriveCooling.share(fit, settings);
        if (fit.vents() != syncedCoolingVents || fit.ice() != syncedCoolingIce || share != syncedCoolingShare) {
            syncedCoolingVents = fit.vents();
            syncedCoolingIce = fit.ice();
            syncedCoolingShare = share;
            markStateDirty();
        }
    }

    // ------------------------------------------------------------ free cruise

    /**
     * What the redstone input means to a drive set to cruise: a throttle.
     *
     * <p>Signal strength is how hard to push, so Create Simulated's Throttle Lever is a throttle lever and
     * a comparator reading a tank is cruise control. A signal rising from nothing engages a ready drive.
     * Cutting it to nothing lets go of a cruise in flight - see {@link #tickCruise} - or calls off one still
     * spinning up, which is a lever pulled back before the jump and means what the console's Abort means.
     *
     * <p>Only starting is subject to {@code allowRedstoneInitiation}. A server that forbids redstone from
     * sending ships off has not forbidden it from bringing one home.
     */
    private void tickThrottle(boolean rising) {
        cruiseThrottle = FreeCruise.throttle(level.getBestNeighborSignal(worldPosition));
        if (rising && AWConfig.ALLOW_REDSTONE_INITIATION.get() && state.acceptsDestination()) {
            Trace.log(Trace.DRIVE, "{} redstone signal: engaging a cruise for {} at throttle {}",
                    Trace.pos(worldPosition), Trace.who(cruiseAuthor), Trace.num(cruiseThrottle));
            startCruise(null);
        } else if (cruiseThrottle <= 0.0D && cruisePhase == CruisePhase.ENGAGING && state.isCancellable()) {
            Trace.log(Trace.DRIVE, "{} throttle cut to nothing while spinning up: calling the cruise off",
                    Trace.pos(worldPosition));
            abort(WarpFailure.CANCELLED);
        } else if (rising && WarpTrace.enabled()) {
            Trace.log(Trace.DRIVE, "{} redstone signal ignored in cruise mode: {} - {}", Trace.pos(worldPosition),
                    !AWConfig.ALLOW_REDSTONE_INITIATION.get() ? "permissions.allowRedstoneInitiation is off"
                            : "the drive only engages from CHARGED (fully charged and idle)",
                    traceState());
        }
    }

    /**
     * Engages a free cruise: the same checks {@link #startWarp} makes, less everything about a
     * destination, plus one of its own - that the charge will pay to set off at all.
     *
     * @param player the player commanding it, or {@code null} for a redstone pulse
     */
    private WarpFailure startCruise(@Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        if (!isRunningFastEnough()) {
            return reject(WarpFailure.INSUFFICIENT_POWER);
        }
        if (!state.acceptsDestination()) {
            return reject(state.isSequenceRunning() ? WarpFailure.DRIVE_BUSY
                    : charge < 1.0F ? WarpFailure.INSUFFICIENT_CHARGE : WarpFailure.DRIVE_BUSY);
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return reject(WarpFailure.NO_AIRSHIP);
        }
        WarpFailure tied = fastened(airship, player, cruiseAuthor);
        if (tied.isFailure()) {
            return reject(tied);
        }
        WarpFailure affordable = FreeCruise.canEngage(WarpCost.fromConfig(tier),
                WarpValidator.effectiveMass(airship), charge);
        if (affordable.isFailure()) {
            return reject(affordable);
        }

        // The same one-warp-per-hull lock. A cruise has no destination to key it on, so it is keyed on
        // the drive, and claimed against where the ship is now - it is the only place it has.
        int nominalTicks = LOCK_IN_TICKS + tier.stabilizeTicks() + tier.warpTicks() + tier.arriveTicks();
        UUID claimKey = UUID.nameUUIDFromBytes(
                ("cruise:" + worldPosition.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Vector3d here = airship.centre(new Vector3d());
        if (partWarping(airship) || !airship.warpData().claim(worldPosition, claimKey, new Vec3(here.x, here.y, here.z),
                nominalTicks, serverLevel.getGameTime())) {
            return reject(WarpFailure.AIRSHIP_ALREADY_WARPING);
        }

        destinationAnchor = null;
        destinationPos = null;
        destinationDimension = null;
        destinationArrivalHeight = ArrivalHeight.UNSET;
        destinationHoldAltitude = true;
        destinationLabel = "";
        initiator = player != null ? player.getUUID() : cruiseAuthor;
        committedDistance = 0.0D;
        committedCost = 0.0F;
        sequenceTicks = 0;
        clearCruise();
        cruisePhase = CruisePhase.ENGAGING;
        lastFailure = WarpFailure.NONE;
        castOff(serverLevel, airship);
        transition(RiftDriveState.DESTINATION_SELECTED);
        notifyInitiator(WarpFailure.NONE);
        playSound(AWSounds.DESTINATION_LOCK.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.DESTINATION_LOCK);
        return WarpFailure.NONE;
    }

    /**
     * Lets go of a cruise from the console.
     *
     * <p>Checked like any other order to the drive. Only a cruise in flight can be let go of - one already
     * braking is on its way out, and one still spinning up is the Abort button's.
     */
    public WarpFailure disengage(ServerPlayer player) {
        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return permission;
        }
        if (cruisePhase != CruisePhase.CRUISING) {
            return WarpFailure.DRIVE_BUSY;
        }
        cruiseDisengage = true;
        return WarpFailure.NONE;
    }

    /**
     * Switches between flying the standing course and cruising.
     *
     * <p>Refused mid-sequence, like turning the bow, and for the same kind of reason: the sequence under
     * way was started as one or the other, and a jump cannot turn into a flight halfway down its
     * corridor. Switching to cruise is also the authorising moment for a redstone-started cruise, so it
     * records who did it.
     */
    public boolean cycleMode(ServerPlayer player) {
        if (WarpValidator.validatePlayer(player, this).isFailure() || state.isSequenceRunning()) {
            return false;
        }
        mode = mode.next();
        if (mode == DriveMode.FREE_CRUISE) {
            cruiseAuthor = player.getUUID();
        }
        markStateDirty();
        return true;
    }

    /**
     * Turns this drive's clearance checks on or off.
     *
     * <p>Refused mid-sequence for the same reason the mode is: the checks belong to the moment a warp
     * opens its rift, and a sequence already under way has either made them or is about to, on the
     * setting it started with.
     */
    public boolean toggleClearanceChecks(ServerPlayer player) {
        if (WarpValidator.validatePlayer(player, this).isFailure() || state.isSequenceRunning()) {
            return false;
        }
        clearanceChecks = !clearanceChecks;
        WarpTrace.clearanceChecks(worldPosition, player.getGameProfile().getName(), clearanceChecks);
        markStateDirty();
        return true;
    }

    /** Whether the drive is flying a cruise, as opposed to spinning one up or flying a waypoint jump. */
    private boolean cruiseFlying() {
        return cruisePhase == CruisePhase.CRUISING || cruisePhase == CruisePhase.DROPPING;
    }

    /**
     * How far a cruise may fly, in blocks: on the charge it set off with if one is in flight, or on the
     * charge in hand for the next one. Never negative.
     */
    private double cruiseLimit(Airship airship) {
        float priced = cruiseFlying() ? cruiseStartCharge : charge;
        return Math.max(0.0D, FreeCruise.limit(WarpCost.fromConfig(tier),
                WarpValidator.effectiveMass(airship), priced));
    }

    /**
     * Starts the drive winding up.
     *
     * <p>How much winding is settled here, from how far the jump reaches - the machine has to work
     * harder for a longer throw. How <em>fast</em> it winds is settled every tick from the shaft, so
     * the answer to "why is this taking so long" is either "you picked somewhere far away" or "spin
     * it faster", both of which are things a pilot can see and act on.
     */
    private void beginStabilizing() {
        sequenceTicks = 0;
        spinProgress = 0.0F;
        spinRequired = (float) SpinUp.required(distanceToDestination(), tier.maximumRange(),
                tier.stabilizeTicks(), tier.stabilizeTicksFar());
        Trace.log(Trace.DRIVE, "{} spinning up for {} blocks: {} spin needed, {} a tick at {} rpm",
                Trace.pos(worldPosition), Trace.blocks(distanceToDestination()), Trace.num(spinRequired),
                Trace.num(spinRate()), Trace.num(Math.abs(getSpeed())));
        transition(RiftDriveState.STABILIZING);
        // Pitched off the rate it is actually managing, so an underpowered drive audibly labours.
        playSound(AWSounds.STABILIZING.get(), 1.0F, 0.7F + 0.5F * (float) spinRate());
        broadcastEffect(ClientboundWarpEffectPacket.Stage.STABILIZING);
        // An escort's target is only somewhere to spin up towards; the flagship preloads the formation.
        if (foldOrder == null || foldOrder.lead()) {
            claimPreload();
        }
    }

    /**
     * Starts the landing loading while the drive winds up, so the arrival search finds it ready.
     *
     * <p>The search runs the moment spin completes and reads every block a hull could land in. For a
     * small ship that is a handful of chunks and it hardly matters when they load. For a very large one
     * it is hundreds, and loading them inside the search - synchronously, on the server thread, and in
     * new country generating them too - froze the server at the instant the rift was meant to open.
     * Claiming them now gives them the whole spin-up to arrive in the background instead.
     *
     * <p>Sized off the same geometry the flight will be, around where the search will look: the target,
     * swept back along the bearing for the run-out, widened by the hull's reach, the search radius and
     * the clearance. A Singularity's scatter moves the target after this, and a scattered arrival falls
     * back on loading its own ground the old way.
     */
    private void claimPreload() {
        preloadSquares = null;
        preloadLevel = null;
        preloadWaited = 0;
        syncedAwaitingLanding = false;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return;
        }
        ServerLevel destination;
        BlockPos target;
        if (destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor == null) {
                return; // beginWarp will say so
            }
            destination = serverLevel.getServer().getLevel(anchor.dimension());
            target = anchor.pos();
        } else if (destinationPos != null) {
            // Wherever the course was committed to: a probe's fix, and an escort's slot in a fold, can
            // both be in another dimension, and ground loaded here for either is the wrong ground.
            destination = arrivalLevel();
            target = BlockPos.containing(destinationPos);
        } else {
            return;
        }
        if (destination == null) {
            return;
        }
        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        FlightGeometry geometry = WarpFlight.geometry(airship, bow, tier.arriveTicks());
        if (geometry == null) {
            return;
        }
        // Through the fleet's one rift, the flagship comes out on the whole fleet's longer run.
        FleetPassage passage = fleetPassage();
        double runOut = passage != null ? passage.emergeDistance() : geometry.emergeDistance();
        dev.ryanhcode.sable.companion.math.BoundingBox3dc hull = airship.worldBounds();
        List<ArrivalTicket.Square> squares = ArrivalTicket.preloadSquares(
                target.getX() + 0.5D, target.getZ() + 0.5D, bow, runOut,
                hull.width(), hull.length(),
                AWConfig.SAFE_ARRIVAL_RADIUS.get(), AWConfig.ARRIVAL_CLEARANCE.get(),
                ArrivalTicket.maxChunks());
        // A flagship's search reads its escorts' slots as well as its own ground, so the ground under them
        // is loaded in the background too, rather than on the server thread the instant the rift opens.
        List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions = foldCompanions(passage);
        if (!companions.isEmpty()) {
            List<ArrivalTicket.Square> all = new ArrayList<>(squares);
            for (uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion companion : companions) {
                all.addAll(ArrivalTicket.preloadSquares(target.getX() + 0.5D + companion.offset().x(),
                        target.getZ() + 0.5D + companion.offset().z(), companion.approach(), companion.runOut(),
                        companion.hull().width(), companion.hull().length(), AWConfig.SAFE_ARRIVAL_RADIUS.get(),
                        AWConfig.ARRIVAL_CLEARANCE.get(), ArrivalTicket.maxChunks()));
            }
            squares = ArrivalTicket.withinBudget(all, ArrivalTicket.maxChunks());
        }
        preloadSquares = squares;
        preloadLevel = destination;
        WarpTrace.preloadClaimed(worldPosition, ArrivalTicket.preload(destination, squares));
    }

    /** Re-asserts the landing claim while the drive is still spinning, before its tickets lapse. */
    private void renewPreload() {
        if (preloadSquares != null && preloadLevel != null) {
            ArrivalTicket.preload(preloadLevel, preloadSquares);
        }
    }

    /**
     * Whether a fully spun drive may open its rift, as far as its landing is concerned.
     *
     * <p>Holds at full spin while the landing is still loading, checking every half second, and gives
     * up waiting after {@code arrivalPreloadTimeoutTicks} - at which point the search loads whatever is
     * left itself, exactly as it always did. So waiting can only ever make a warp smoother, never make
     * one fail that would otherwise have flown.
     */
    private boolean landingReady() {
        if (preloadSquares == null || preloadLevel == null) {
            return true;
        }
        if (preloadWaited % 10 == 0 && ArrivalTicket.loaded(preloadLevel, preloadSquares)) {
            if (preloadWaited > 0) {
                WarpTrace.preloadReady(worldPosition, preloadWaited, true);
            }
            setAwaitingLanding(false);
            return true;
        }
        if (preloadWaited >= AWConfig.ARRIVAL_PRELOAD_TIMEOUT_TICKS.get()) {
            WarpTrace.preloadReady(worldPosition, preloadWaited, false);
            setAwaitingLanding(false);
            return true;
        }
        preloadWaited++;
        setAwaitingLanding(true);
        return false;
    }

    private void setAwaitingLanding(boolean awaiting) {
        if (syncedAwaitingLanding != awaiting) {
            syncedAwaitingLanding = awaiting;
            markStateDirty();
        }
    }

    /** How far the committed destination is, or zero when there is nothing to measure to. */
    private double distanceToDestination() {
        Airship airship = airship();
        if (airship == null || destinationPos == null) {
            return 0.0D;
        }
        if (destinationDimension != null && !destinationDimension.equals(airship.level().dimension())) {
            // A position in another dimension is not somewhere to measure to from here. The jump is the
            // length it was priced at.
            return committedDistance;
        }
        // Measured against the committed position rather than re-resolved through the registry, so a
        // fix and an anchor are the same question - and so an anchor deleted mid-flight does not
        // silently turn a long jump into a spin-up for a zero-length one.
        Vector3d centre = airship.centre(new Vector3d());
        return centre.distance(destinationPos.x, destinationPos.y, destinationPos.z);
    }

    /**
     * How long the committed warp holds its corridor, in ticks: the tier's own figure, or longer for a
     * longer jump on a server that has set warps to take longer the further they go - see
     * {@link CorridorTime}.
     *
     * <p>Off the distance the course was committed at, not re-measured: by the time the corridor is flown
     * the ship has run at its rift, and by the time anything asks afterwards it is at the far end.
     */
    public int corridorTicks() {
        return CorridorTime.fromConfig(tier.warpTicks(), committedDistance);
    }

    /** Spin gained per tick at the speed the shaft is currently turning. */
    private double spinRate() {
        return SpinUp.rate(Math.abs(getSpeed()), tier.minimumRpm(), tier.optimalRpm(),
                AWConfig.SPIN_MINIMUM_RATE.get());
    }

    /**
     * Plans the whole flight, then opens the entry rift.
     *
     * <p>The destination is resolved and the arrival volume proven clear <em>here</em>, before the
     * aperture exists. A warp with nowhere to land is refused while the airship is still safely at its
     * mooring, rather than halfway down the corridor with no way back.
     */
    private void beginWarp() {
        // The preload's tickets outlive this for their own lifespan, which covers the search below;
        // the drive has nothing further to do with them.
        preloadSquares = null;
        preloadLevel = null;
        berthPlanned = null;
        if (cruisePhase == CruisePhase.ENGAGING) {
            jumpToCruise();
            return;
        }
        if (foldOrder != null && !foldOrder.lead()) {
            beginEscortWarp();
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }
        // Asked again now the drive has spun up, and before anything moves: a Docking Connector can find
        // its partner and lock in the seconds the drive takes to turn.
        if (fastened(airship, null, initiator).isFailure()) {
            abort(WarpFailure.SHIP_FASTENED);
            return;
        }
        // An anchor still has to be there when the rift opens. A fix is a place, and a place cannot
        // be taken down while a ship is on its way to it.
        ServerLevel destination;
        BlockPos targetBlock;
        int requestedHeight;
        if (destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor == null) {
                abort(WarpFailure.ANCHOR_MISSING);
                return;
            }
            destination = serverLevel.getServer().getLevel(anchor.dimension());
            targetBlock = anchor.pos();
            requestedHeight = anchor.arrivalHeight();
        } else if (destinationPos != null) {
            // The dimension the course named when it was committed, which for a Rift Probe's sounding
            // need not be this one. Null when that dimension has gone, which is refused just below.
            destination = arrivalLevel();
            targetBlock = BlockPos.containing(destinationPos);
            requestedHeight = destinationArrivalHeight;
        } else {
            abort(WarpFailure.ANCHOR_MISSING);
            return;
        }

        if (destination == null || !SafeArrival.isDestinationLoadable(destination, targetBlock)) {
            Trace.log(Trace.LAUNCH, "{} destination {} {}", Trace.pos(worldPosition), Trace.pos(targetBlock),
                    destination == null ? "is in a dimension this server does not have loaded"
                            : "is in a chunk that will not load (outside the world border or build height?)");
            abort(WarpFailure.ANCHOR_UNAVAILABLE);
            return;
        }
        // Holding altitude never applies across dimensions, where a height from one world means nothing
        // in the other; see ArrivalHeight.planned.
        boolean crossing = !destination.dimension().equals(serverLevel.dimension());
        int arrivalHeight = ArrivalHeight.planned(requestedHeight, destinationHoldAltitude, !crossing,
                airship.vesselWorldBounds().minY(), targetBlock.getY());
        // Nothing leaves this dimension here. The hull is rebuilt on the far side at the crossing, deep in
        // the fold, and only after the arrival below has been proven - which is the whole point of doing
        // it there rather than when the rift opens.
        destinationDimension = destination.dimension();

        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());

        // An unstable exit scatters the arrival from the intended target. The offset is horizontal
        // only — the hull arrives at the correct altitude but displaced laterally. The arrival
        // search still proves the scattered spot clear, so a hull can never materialise inside
        // terrain. If the scattered position has no safe arrival, the warp falls back to the
        // original target rather than aborting: the ship always arrives somewhere. A Rift Storm lends
        // every drive the Singularity's instability on top of its own, so it is asked here rather than
        // the tier - see RiftStormRules.
        // A flagship's search proves its whole formation clear, not only its own hull; see SafeArrival.Companion.
        // Taking the fleet through one rift, it plans on the fleet's run, which the search proves too.
        FleetPassage passage = fleetPassage();
        List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions = foldCompanions(passage);
        BlockPos flightTarget = targetBlock;
        boolean scattered = false;
        double scatterRoll = serverLevel.getRandom().nextDouble();
        if (ScatterOffset.isScattered(RiftStorm.instability(serverLevel, tier), scatterRoll)) {
            double angle = serverLevel.getRandom().nextDouble() * Math.PI * 2.0D;
            double magnitude = serverLevel.getRandom().nextDouble();
            Vector3d scatterVec = ScatterOffset.offset(
                    committedDistance, angle, magnitude);
            if (scatterVec.lengthSquared() > 0.0D) {
                flightTarget = BlockPos.containing(
                        targetBlock.getX() + scatterVec.x,
                        targetBlock.getY(),
                        targetBlock.getZ() + scatterVec.z);
                scattered = true;
            }
        }

        // A scattered arrival keeps the height asked for: the scatter is sideways, so the ship still
        // comes in the same distance above the target's own block height, not above the ground it
        // was blown over - the search climbs from there if that ground is higher.
        Trace.log(Trace.LAUNCH, "{} planning to {} in {}: arrival height {} (asked {}{}), bow {}, {} companion(s),"
                        + " checks {}{}", Trace.pos(worldPosition), Trace.pos(flightTarget),
                destination.dimension().location(), arrivalHeight, requestedHeight,
                destinationHoldAltitude ? ", holding altitude" : "", Trace.vec(bow), companions.size(),
                clearanceChecks ? "on" : "OFF", scattered ? ", scattered from " + Trace.pos(targetBlock) : "");
        // A Rift Ward's field over the place aimed at turns the rift aside, to its rim - asked of the target as
        // scattered, and again of the target itself should the scatter have to be given up. Nothing is paid, and
        // nobody is told, until the flight is certain: see Wards. It is not a clearance check, and a drive with
        // its checks switched off is turned like any other, as one tied to the ground is still refused.
        Vec3 wardFrom = crossing ? null : new Vec3(airship.position().x(), airship.position().y(), airship.position().z());
        long wardPrice = wardPrice(airship, companions);
        double wardClearance = wardClearance(airship, bow, companions);
        WardedPlan warded = planPastWards(airship, destination, flightTarget, wardFrom, bow, arrivalHeight,
                companions, passage, wardClearance, wardPrice);

        // If the scattered position has no safe arrival, try the original target. A scatter that
        // lands in open sky settles there; one that lands in a mountainside simply does not happen,
        // rather than aborting a warp that was otherwise committed.
        if (warded.plan().flight() == null && scattered) {
            scattered = false;
            flightTarget = targetBlock;
            warded = planPastWards(airship, destination, targetBlock, wardFrom, bow, arrivalHeight,
                    companions, passage, wardClearance, wardPrice);
        }
        WarpFlight.Plan plan = warded.plan();
        uk.co.iceconchy.aerowarptics.ward.Wards.Turn turn = warded.turn();

        WarpFlight planned = plan.flight();
        if (planned == null) {
            abort(plan.refusal().isFailure() ? plan.refusal() : WarpFailure.NO_SAFE_ARRIVAL);
            return;
        }

        // Whether the far side has room for this vessel at all, asked while it is still at its mooring.
        // Asked again at the crossing, where the answer is the one that counts; this is so a full grid
        // or a hull too tall for the Nether is a refusal here and not a trip down the corridor and back.
        if (crossing) {
            WarpFailure room = CrossDimensionWarp.preflight(airship, destination);
            if (room.isFailure()) {
                abort(room);
                return;
            }
        }

        // The departure path gets the same scrutiny the arrival always had. The hull is about to be
        // flown at the entry rift, through it, and down the corridor - all in real world space over
        // the mooring - and terrain anywhere along that run is flown straight into, which is exactly
        // what makes Sable's solver eject the hull. Refuse at the mooring, with the bearing named, so
        // the pilot can come about; never mid-corridor.
        // The bow guard, always. The hull's own path through open air on its way to the aperture,
        // and only a block actually seen there refuses - the case that genuinely ejects a ship.
        // Bounded by the hull and a fixed lead, so it costs a flying city no more than a skiff.
        // Unless the pilot has switched the checks off at the console, in which case neither this nor
        // the corridor below is read at all - not read and ignored, which would still cost the scan.
        if (!clearanceChecks) {
            WarpTrace.uncheckedLaunch(worldPosition, flightTarget);
        } else if (LaunchClearance.guard(airship, serverLevel, bow)
                == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.OBSTRUCTED) {
            traceBowGuard(serverLevel, airship, bow);
            abort(WarpFailure.NO_CLEAR_LAUNCH);
            return;
        }

        // The whole-corridor proof is now opt-in. As a gate it scaled with the hull until the
        // largest ships were refused every time with nothing in their way, which is worth far more
        // than it protected: past the bow, the pilot is flying the ship.
        if (clearanceChecks && AWConfig.REQUIRE_CLEAR_LAUNCH.get()) {
            uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance corridor =
                    LaunchClearance.clearance(airship, serverLevel, bow, planned);
            if (corridor != uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.CLEAR) {
                if (WarpTrace.enabled()) {
                    BlockPos hit = corridor == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.UNPROVEN ? null
                            : LaunchClearance.firstObstruction(airship, serverLevel, bow, planned);
                    Trace.log(Trace.LAUNCH, "{} corridor {} over {} blocks: {}", Trace.pos(worldPosition), corridor,
                            Trace.blocks(LaunchClearance.launchReach(planned)), hit != null
                                    ? "blocked at " + Trace.pos(hit) + " by " + serverLevel.getBlockState(hit)
                                    : corridor == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.UNPROVEN
                                    ? "ran out of its " + SafeArrival.budgetFor(airship)
                                            + " block reads or found chunks missing - not an obstruction"
                                    : "fouled, but no single block to name");
                }
                // Both refuse, but the pilot is told which: a blocked corridor means come about, an
                // unproven one means the check could not finish and is not about the bearing at all.
                abort(corridor == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.UNPROVEN
                        ? WarpFailure.LAUNCH_UNPROVEN : WarpFailure.NO_CLEAR_LAUNCH);
                return;
            }
        }

        // A ship must arrive where the ground will stay under it. The arrival ticket holds the landing
        // for the flight and then lets go; historically, if nothing else kept it loaded, the world
        // unloaded and Sable removed the hull - the ship was simply lost. That only refuses a warp
        // when the drive is NOT keeping its own landing resident: with keepAirshipsLoaded on (the
        // default), the drive holds the ground it lands on, so refusing would be wrong even where an
        // old config still has requireLoadedArrival set. The refusal is the fallback for a server that
        // has turned the drive's own residency off and wants unattended landings forbidden instead.
        if (!AWConfig.KEEP_AIRSHIPS_LOADED.get() && AWConfig.REQUIRE_LOADED_ARRIVAL.get()
                && !ArrivalTicket.isLandingDurable(destination, planned.arrivalOrigin())) {
            Trace.log(Trace.LAUNCH, "{} landing at {} would unload once the flight lets go, and"
                            + " arrival.keepAirshipsLoaded is off while arrival.requireLoadedArrival is on",
                    Trace.pos(worldPosition), Trace.vec(planned.arrivalOrigin()));
            abort(WarpFailure.ARRIVAL_NOT_LOADED);
            return;
        }

        // Notify via sound and effects when scatter was applied. The crew sees and hears the ship
        // shudder — far more atmospheric than a chat message, and a clear signal that the Singularity
        // did not put them where it was asked to.
        if (scattered) {
            playSound(AWSounds.WARP_SCATTER.get(), 1.0F, 1.0F);
            broadcastEffect(ClientboundWarpEffectPacket.Stage.SCATTER);
            WarpTrace.scatter(worldPosition, targetBlock, flightTarget, committedDistance);
        }

        // The formation is proven: tell the escorts where their slots are, front to back - or, going through
        // one rift, all at once, with the passage every flight in it keeps step with, this one included.
        if (foldOrder != null && foldOrder.lead()) {
            RiftCouplerBlockEntity coupler = findCoupler();
            if (coupler != null && coupler.muster() != null) {
                coupler.muster().berthed(fleetBerths);
                foldPassage = coupler.muster().flagshipPlanned(serverLevel, destination, planned, passage);
                if (foldPassage != null) {
                    planned.keepStep(foldPassage, serverLevel.getGameTime());
                }
            }
        }
        // The flight is certain: the wards that turned it are paid, and one that could not pay drops its field.
        wardTurn = null;
        if (turn.turned() || turn.gaveWay() != null) {
            uk.co.iceconchy.aerowarptics.ward.Wards.commit(destination, turn, wardPrice);
            wardTurn = turn;
        }
        // And the berth it was planned into is taken, for the same reason at the same moment: a slip reserved
        // for a flight that was then refused would stand empty and unoffered until its reservation ran out.
        if (berthPlanned != null) {
            berthId = uk.co.iceconchy.aerowarptics.berth.Berths.reserve(serverLevel.getServer(), berthPlanned, airship,
                    LOCK_IN_TICKS + planned.transitTicks() * 2 + planned.corridorTicks() + planned.emergeTicks()
                            + AWConfig.APPROACH_LIMIT_TICKS.get());
            berthPlanned = null;
            // And every escort's with it: a fleet's berths are one decision, taken at one moment. Each escort
            // sets off a ripple after the last, and may go the long way round, so each is given longer.
            for (uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion companion : companions) {
                uk.co.iceconchy.aerowarptics.berth.Berths.Assignment theirs = fleetBerths.get(companion.ship().uuid());
                if (theirs != null) {
                    uk.co.iceconchy.aerowarptics.berth.Berths.reserve(serverLevel.getServer(), theirs, companion.ship(),
                            LOCK_IN_TICKS + planned.transitTicks() * 2 + planned.corridorTicks() + planned.emergeTicks()
                                    + AWConfig.APPROACH_LIMIT_TICKS.get() + AWConfig.WAKE_ECHO_DELAY_TICKS.get()
                                    + (companions.size() + 1) * AWConfig.RIPPLE_TICKS.get());
                }
            }
            fleetBerths = java.util.Map.of();
        }
        openRift(serverLevel, airship, destination, planned, bow);
    }

    /**
     * Lets a Berth go of this ship as its drive starts a sequence. A berth makes no joint - it is not a
     * {@link uk.co.iceconchy.aerowarptics.airship.Fastening} and never refuses a launch - so the drive has only
     * to say it is leaving, and every way a sequence starts has to say it.
     */
    private static void castOff(ServerLevel serverLevel, Airship airship) {
        uk.co.iceconchy.aerowarptics.berth.Berths.leaving(serverLevel.getServer(), airship, "its drive is starting");
    }

    /** A flight as planned past any Rift Ward in its way, and what the wards made of it. */
    private record WardedPlan(WarpFlight.Plan plan, uk.co.iceconchy.aerowarptics.ward.Wards.Turn turn) {
    }

    /**
     * Plans a flight to where a rift aimed at {@code target} will really come out.
     *
     * <p>Turned aside, the hull must come down outside every field that turned it - the arrival search moves a
     * hull to find it room, and may not move it back in. Where the rim has no room at the place the rift was
     * turned to, the rim is walked either way for somewhere that has; where none of it has, the warp is refused
     * with {@link WarpFailure#DESTINATION_WARDED}, here, before anything has moved.
     */
    private WardedPlan planPastWards(Airship airship, ServerLevel destination, BlockPos target, @Nullable Vec3 from,
                                     Vector3d bow, int arrivalHeight,
                                     List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions,
                                     @Nullable FleetPassage passage, double clearance, long price) {
        uk.co.iceconchy.aerowarptics.ward.Wards.Turn turn = uk.co.iceconchy.aerowarptics.ward.Wards.turnAside(
                destination, target, from, initiator, AWConfig.WARD_PENETRATION.get(tier).get(), clearance, price);
        // Past the wards, and then into a berth: a rift that comes out where it was aimed, at an anchor that
        // is a station, is planned into a slip and nowhere else. One turned to a rim, or scattered off its
        // target, was not put where the station is and is given nothing by it.
        WarpAnchor station = turn.turned() ? null : stationAt(destination, target);
        if (station != null) {
            return new WardedPlan(planBerth(airship, destination, station, bow, companions, passage, null), turn);
        }
        WarpFlight.Plan plan = WarpFlight.plan(airship, destination, turn.target(), bow,
                corridorTicks(), tier.arriveTicks(), arrivalHeight, clearanceChecks, companions, passage);
        if (!turn.turned() || outsideWards(plan, turn)) {
            return new WardedPlan(plan, turn);
        }
        for (BlockPos elsewhere : turn.alternatives()) {
            WarpFlight.Plan again = WarpFlight.plan(airship, destination, elsewhere, bow,
                    corridorTicks(), tier.arriveTicks(), arrivalHeight, clearanceChecks, companions, passage);
            if (outsideWards(again, turn)) {
                Trace.log(Trace.WARD, "{} no room at {} on the rim: coming out at {} instead", Trace.pos(worldPosition),
                        Trace.pos(turn.target()), Trace.pos(elsewhere));
                return new WardedPlan(again, turn.at(elsewhere));
            }
        }
        Trace.log(Trace.WARD, "{} turned from {} to {} and nowhere on the rim has room for the hull",
                Trace.pos(worldPosition), Trace.pos(target), Trace.pos(turn.target()));
        return new WardedPlan(new WarpFlight.Plan(null, WarpFailure.DESTINATION_WARDED), turn);
    }

    /** An anchor by id, if it is a station. */
    @Nullable
    private static WarpAnchor stationOf(ServerLevel destination, UUID anchorId) {
        WarpAnchor anchor = WarpAnchorRegistry.get(destination).byId(anchorId);
        return anchor != null && uk.co.iceconchy.aerowarptics.berth.Berths.isStation(destination.getServer(), anchor)
                ? anchor : null;
    }

    /**
     * The anchor this warp is bound for, if it is a station and {@code target} is the anchor itself - which a
     * scattered target is not.
     */
    @Nullable
    private WarpAnchor stationAt(ServerLevel destination, BlockPos target) {
        if (destinationAnchor == null) {
            return null;
        }
        WarpAnchor anchor = WarpAnchorRegistry.get(destination).byId(destinationAnchor);
        return anchor != null && anchor.pos().equals(target)
                && uk.co.iceconchy.aerowarptics.berth.Berths.isStation(destination.getServer(), anchor) ? anchor : null;
    }

    /**
     * Plans a flight into a berth of a station: the best berth the station offers whose slip and lane are
     * clear, or the reason there is none.
     *
     * <p>Asking changes nothing. The berth the plan settles on is left in {@link #berthPlanned} for
     * {@link #beginWarp} to take once the flight is certain - see {@link uk.co.iceconchy.aerowarptics.berth.Berths}.
     * A berth is not a clearance check: a drive with its checks off is still given one, and only the proof that
     * the slip and the lane are clear is skipped, as the arrival search is for any other warp.
     *
     * @param report a dry run's lines, to say which berth and why; {@code null} for a launch
     */
    private WarpFlight.Plan planBerth(Airship airship, ServerLevel destination, WarpAnchor station, Vector3d bow,
                                      List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions,
                                      @Nullable FleetPassage passage, @Nullable List<Component> report) {
        berthPlanned = null;
        fleetBerths = java.util.Map.of();
        if (passage != null) {
            // Through one rift a fleet is one rigid thing, and a rigid thing cannot be put down in slips that
            // lie wherever a harbour's builder put them.
            Trace.log(Trace.BERTH, "{} a fleet going through one rift was aimed at station {}: refused - set the"
                    + " Fleet Board's Rift to Each", Trace.pos(worldPosition), station.displayName());
            if (report != null) {
                report.add(Component.literal("  berth: REFUSED - a fleet through one rift cannot be berthed;"
                        + " set the Fleet Board's Rift to Each"));
            }
            return new WarpFlight.Plan(null, WarpFailure.FLOTILLA_NOT_READY);
        }
        if (!companions.isEmpty()) {
            return planFleetBerths(airship, destination, station, bow, companions, report);
        }
        uk.co.iceconchy.aerowarptics.berth.Berths.Offer offer = uk.co.iceconchy.aerowarptics.berth.Berths.offers(
                destination, station, airship, bow, initiator);
        if (offer.refusal().isFailure()) {
            if (report != null) {
                report.add(Component.literal("  berth: REFUSED " + offer.refusal().getSerializedName() + " - "
                        + offer.why()));
            }
            return new WarpFlight.Plan(null, offer.refusal());
        }
        for (uk.co.iceconchy.aerowarptics.berth.Berths.Assignment berth : offer.ranked()) {
            WarpFlight.Plan plan = WarpFlight.planInto(airship, berth.centre(), berth.attitude(), berth.approach(), bow,
                    corridorTicks(), tier.arriveTicks());
            WarpFlight planned = plan.flight();
            if (planned == null) {
                return plan;
            }
            if (clearanceChecks) {
                uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance slip =
                        uk.co.iceconchy.aerowarptics.berth.Berths.proof(airship, destination, berth,
                                planned.emergeRunOut());
                if (slip != uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.CLEAR) {
                    Trace.log(Trace.BERTH, "{} berth '{}' at {} is {}: its slip or its {}-block lane - trying the next",
                            Trace.pos(worldPosition), berth.berth().displayName(), Trace.pos(berth.berth().pos()), slip,
                            Trace.blocks(planned.emergeRunOut()));
                    if (report != null) {
                        report.add(Component.literal("  berth: '" + berth.berth().displayName() + "' is " + slip
                                + " - something in its slip or its lane"));
                    }
                    continue;
                }
            }
            if (report != null) {
                report.add(Component.literal(String.format(java.util.Locale.ROOT,
                        "  berth: '%s' at %s, facing %d, %s, lane %.1f blocks%s",
                        berth.berth().displayName(), berth.berth().pos().toShortString(), berth.berth().yaw(),
                        berth.berth().sternFirst() ? "stern first" : "bow first", planned.emergeRunOut(),
                        clearanceChecks ? "" : " - not looked at (checks off)")));
            } else {
                berthPlanned = berth;
            }
            return plan;
        }
        return new WarpFlight.Plan(null, WarpFailure.BERTH_OBSTRUCTED);
    }

    /**
     * Plans a flagship's flight into a station that has a berth for every ship of its fleet, or refuses the
     * lot: a fleet is berthed whole or not at all.
     *
     * <p>One decision for the whole fleet - which slip one ship takes decides what is left for the next - so a
     * slip found fouled is struck off and the station is asked again from the start. The escorts' berths are
     * left in {@link #fleetBerths} beside the flagship's own, taken together once the fold is certain.
     */
    private WarpFlight.Plan planFleetBerths(Airship airship, ServerLevel destination, WarpAnchor station,
                                            Vector3d bow,
                                            List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions,
                                            @Nullable List<Component> report) {
        List<uk.co.iceconchy.aerowarptics.berth.Berths.Asking> fleet = new ArrayList<>();
        fleet.add(new uk.co.iceconchy.aerowarptics.berth.Berths.Asking(airship, bow));
        for (uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion companion : companions) {
            fleet.add(new uk.co.iceconchy.aerowarptics.berth.Berths.Asking(companion.ship(), companion.approach()));
        }
        java.util.Set<UUID> struck = new java.util.HashSet<>();
        // As many askings as the station could have berths: each one that fails strikes one off.
        for (int asking = 0; asking <= AWConfig.BERTH_MAX_PER_ANCHOR.get(); asking++) {
            uk.co.iceconchy.aerowarptics.berth.Berths.FleetOffer offer = uk.co.iceconchy.aerowarptics.berth.Berths.fleetOffers(destination, station, fleet,
                    initiator, struck);
            if (offer.refusal().isFailure()) {
                WarpFailure refusal = struck.isEmpty() ? offer.refusal() : WarpFailure.BERTH_OBSTRUCTED;
                if (report != null) {
                    report.add(Component.literal("  berths: REFUSED " + refusal.getSerializedName() + " - " + offer.why()));
                }
                return new WarpFlight.Plan(null, refusal);
            }
            uk.co.iceconchy.aerowarptics.berth.Berths.Assignment mine = offer.berths().get(0);
            WarpFlight.Plan plan = WarpFlight.planInto(airship, mine.centre(), mine.attitude(), mine.approach(), bow,
                    corridorTicks(), tier.arriveTicks());
            WarpFlight planned = plan.flight();
            if (planned == null) {
                return plan;
            }
            uk.co.iceconchy.aerowarptics.berth.Berth fouled = null;
            if (clearanceChecks) {
                if (uk.co.iceconchy.aerowarptics.berth.Berths.proof(airship, destination, mine, planned.emergeRunOut())
                        != uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.CLEAR) {
                    fouled = mine.berth();
                }
                for (int i = 0; i < companions.size() && fouled == null; i++) {
                    if (uk.co.iceconchy.aerowarptics.berth.Berths.proof(companions.get(i).ship(), destination, offer.berths().get(i + 1),
                            companions.get(i).runOut()) != uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.CLEAR) {
                        fouled = offer.berths().get(i + 1).berth();
                    }
                }
            }
            if (fouled != null) {
                Trace.log(Trace.BERTH, "{} berth '{}' at {} is fouled for the fleet: struck off, asking again",
                        Trace.pos(worldPosition), fouled.displayName(), Trace.pos(fouled.pos()));
                if (report != null) {
                    report.add(Component.literal("  berths: '" + fouled.displayName() + "' has something in its slip"
                            + " or its lane"));
                }
                struck.add(fouled.id());
                continue;
            }
            java.util.Map<UUID, uk.co.iceconchy.aerowarptics.berth.Berths.Assignment> escorts = new java.util.HashMap<>();
            for (int i = 0; i < companions.size(); i++) {
                escorts.put(companions.get(i).ship().uuid(), offer.berths().get(i + 1));
            }
            if (report != null) {
                report.add(Component.literal("  berths: one for each of " + fleet.size() + " ships, this one in '"
                        + mine.berth().displayName() + "' at " + mine.berth().pos().toShortString()));
            } else {
                berthPlanned = mine;
                fleetBerths = escorts;
            }
            return plan;
        }
        return new WarpFlight.Plan(null, WarpFailure.BERTH_OBSTRUCTED);
    }

    /**
     * The flagship's word to an escort bound for a station: the berth it comes out in, in place of its slot.
     * The slip is already reserved for it; this drive gives it back if its own flight fails, and is moored in
     * it when it arrives, as any drive is.
     */
    public void berthFold(uk.co.iceconchy.aerowarptics.berth.Berths.Assignment berth) {
        if (foldOrder == null || foldOrder.lead()) {
            return;
        }
        foldBerth = berth;
        berthId = berth.berth().id();
    }

    /** Lets go of the berth reserved for a flight that is not going to arrive in it. */
    private void releaseBerth() {
        berthPlanned = null;
        fleetBerths = java.util.Map.of();
        foldBerth = null;
        if (berthId != null && level instanceof ServerLevel serverLevel) {
            uk.co.iceconchy.aerowarptics.berth.Berths.unreserve(serverLevel.getServer(), berthId);
        }
        berthId = null;
    }

    /** Whether a plan has a flight, and that flight comes to rest outside every field it was turned from. */
    private static boolean outsideWards(WarpFlight.Plan plan, uk.co.iceconchy.aerowarptics.ward.Wards.Turn turn) {
        WarpFlight flight = plan.flight();
        if (flight == null) {
            return false;
        }
        Vector3d rest = flight.arrivalCentre();
        return !turn.holds(rest.x, rest.z);
    }

    /** What it costs a ward to turn this vessel aside: by its mass, and a flotilla's summed. */
    private static long wardPrice(Airship airship,
                                  List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions) {
        double mass = WarpValidator.effectiveMass(airship);
        for (uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion companion : companions) {
            mass += WarpValidator.effectiveMass(companion.ship());
        }
        return uk.co.iceconchy.aerowarptics.ward.WardCharge.deflection(AWConfig.WARD_DEFLECT_BASE.get(),
                AWConfig.WARD_DEFLECT_PER_MASS.get(), mass, AWConfig.MASS_REFERENCE.get());
    }

    /**
     * How far outside a field's rim this vessel's centre has to come out for all of it to be outside: its own
     * reach, and a formation's - every slot of a fold is outside, not only the flagship's.
     */
    private double wardClearance(Airship airship, Vector3d bow,
                                 List<uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion> companions) {
        double reach = WarpFlight.geometry(airship, bow, tier.arriveTicks()).hullRadius();
        double furthest = 0.0D;
        for (uk.co.iceconchy.aerowarptics.warp.SafeArrival.Companion companion : companions) {
            double out = Math.hypot(companion.offset().x(), companion.offset().z())
                    + Math.hypot(companion.hull().width(), companion.hull().length()) * 0.5D;
            furthest = Math.max(furthest, out);
        }
        return Math.max(reach, furthest) + uk.co.iceconchy.aerowarptics.ward.WardRules.RIM_MARGIN;
    }

    /**
     * Tells the far end, and the crew, that a Rift Ward is turning this rift aside - or failing to. Sent as the
     * hull enters its corridor, so the scene's last beat meets it coming out.
     */
    private void announceWard(WarpFlight flight, Airship airship) {
        uk.co.iceconchy.aerowarptics.ward.Wards.Turn turn = wardTurn;
        wardTurn = null;
        ServerLevel where = arrivalLevel();
        if (turn == null || where == null) {
            return;
        }
        Vector3d exit = cueExit(flight).centre();
        List<ServerPlayer> crew = airship.crew();
        uk.co.iceconchy.aerowarptics.ward.Wards.announce(where, turn, new Vec3(exit.x, exit.y, exit.z),
                flight.corridorTicks(), effectiveColour(), crew, false);
        if (turn.turned()) {
            AWCriteria.turnedAside(crew);
        }
    }

    /**
     * An escort's rift: its slot at the far end, exactly where the flagship's search put it.
     *
     * <p>No search of its own - the flagship's proved the slot clear as part of the formation, and a second
     * search could only move this ship out of formation into ground nobody looked at. Its own bow guard
     * still runs: the flagship's proof is about the far end, and an escort has its own departure to fly.
     */
    private void beginEscortWarp() {
        if (!(level instanceof ServerLevel serverLevel)) {
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }
        if (fastened(airship, null, initiator).isFailure()) {
            abort(WarpFailure.SHIP_FASTENED);
            return;
        }
        FoldOrder order = foldOrder;
        Vec3 centre = foldArrival;
        ServerLevel destination = order == null ? null : serverLevel.getServer().getLevel(order.dimension());
        if (order == null || centre == null || destination == null) {
            abort(WarpFailure.FOLD_CALLED_OFF);
            return;
        }
        destinationDimension = destination.dimension();
        destinationPos = centre;
        // Through the fleet's one rift an escort flies the flagship's bearing on the fleet's run, whichever
        // way it faces - a formation cannot go through a plane in step along more than one heading.
        FleetPassage passage = foldPassage;
        Vector3d bow = passage != null ? new Vector3d(passage.heading()) : WarpFlight.worldBow(airship, shipSpaceBow());
        // Bound for a station, its slot is a berth: it comes out down that slip's lane, turned to it. The
        // flagship proved the slip as it proves a slot, so nothing is searched here either.
        WarpFlight.Plan plan = foldBerth != null && passage == null
                ? WarpFlight.planInto(airship, foldBerth.centre(), foldBerth.attitude(), foldBerth.approach(), bow,
                        corridorTicks() + foldExtraCorridor, tier.arriveTicks())
                : WarpFlight.planTo(airship, new Vector3d(centre.x, centre.y, centre.z), bow,
                corridorTicks() + foldExtraCorridor, tier.arriveTicks(), passage);
        WarpFlight planned = plan.flight();
        if (planned == null) {
            abort(plan.refusal().isFailure() ? plan.refusal() : WarpFailure.NO_SAFE_ARRIVAL);
            return;
        }
        if (passage != null) {
            planned.keepStep(passage, serverLevel.getGameTime());
        }
        if (!destination.dimension().equals(serverLevel.dimension())) {
            WarpFailure room = CrossDimensionWarp.preflight(airship, destination);
            if (room.isFailure()) {
                abort(room);
                return;
            }
        }
        if (!clearanceChecks) {
            WarpTrace.uncheckedLaunch(worldPosition, BlockPos.containing(centre));
        } else if (LaunchClearance.guard(airship, serverLevel, bow)
                == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.OBSTRUCTED) {
            traceBowGuard(serverLevel, airship, bow);
            abort(WarpFailure.NO_CLEAR_LAUNCH);
            return;
        }
        openRift(serverLevel, airship, destination, planned, bow);
    }

    /**
     * Names what the bow guard ran into, for the trace. A second scan, so only when tracing: the guard
     * itself stops at the first block it finds and keeps no note of where.
     */
    private void traceBowGuard(ServerLevel serverLevel, Airship airship, Vector3dc bow) {
        if (!WarpTrace.enabled()) {
            return;
        }
        BlockPos hit = LaunchClearance.guardObstruction(airship, serverLevel, bow);
        Trace.log(Trace.LAUNCH, "{} bow guard BLOCKED over {} blocks along {}: {}", Trace.pos(worldPosition),
                Trace.num(LaunchClearance.guardReach()), Trace.vec(bow), hit != null
                        ? "at " + Trace.pos(hit) + " by " + serverLevel.getBlockState(hit)
                        : fouledBy(serverLevel, airship, bow));
    }

    /**
     * What fouled a bow guard that found no block: the other hulls in its path, by id, against the ids this
     * ship counts as its own - so a turret on a Swivel Bearing read as a stranger shows up as exactly that.
     */
    private static String fouledBy(ServerLevel serverLevel, Airship airship, Vector3dc bow) {
        List<dev.ryanhcode.sable.sublevel.SubLevel> hulls = LaunchClearance.guardHulls(airship, serverLevel, bow);
        if (hulls.isEmpty()) {
            return "fouled, but no block or hull to name (chunks missing?)";
        }
        StringBuilder out = new StringBuilder("fouled by ").append(hulls.size()).append(" other hull(s):");
        for (dev.ryanhcode.sable.sublevel.SubLevel hull : hulls) {
            out.append(' ').append(hull.getUniqueId());
        }
        out.append(" - this vessel is");
        for (Airship member : airship.vessel()) {
            out.append(' ').append(member.uuid());
        }
        return out.toString();
    }

    /**
     * What blocks a refused arrival, straight over the target: at the lowest place the search tries and at
     * the highest. The two together say whether it is the ground or another hull, and whether climbing
     * further would have cleared it.
     */
    private static void arrivalBlockers(List<Component> report, Airship airship, ServerLevel destination,
                                        BlockPos targetBlock, Vector3dc bow, double runOut, int arrivalHeight) {
        SafeArrival.Result lowest = SafeArrival.straightIn(airship, targetBlock,
                new org.joml.Quaterniond(airship.orientation()), arrivalHeight);
        if (lowest == null) {
            return;
        }
        int climb = AWConfig.SAFE_ARRIVAL_VERTICAL_RADIUS.get();
        dev.ryanhcode.sable.companion.math.BoundingBox3d low = lowest.bounds();
        dev.ryanhcode.sable.companion.math.BoundingBox3d high = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                low.minX(), low.minY() + climb, low.minZ(), low.maxX(), low.maxY() + climb, low.maxZ());
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "    over the target, underside at y %.0f: %s", low.minY(),
                arrivalBlocker(SafeArrival.blockedBy(airship, destination, low, bow, runOut)))));
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "    over the target, underside at y %.0f (as high as the search climbs): %s", high.minY(),
                arrivalBlocker(SafeArrival.blockedBy(airship, destination, high, bow, runOut)))));
        StringBuilder own = new StringBuilder("    this vessel is");
        for (Airship member : airship.vessel()) {
            own.append(' ').append(member.uuid());
        }
        report.add(Component.literal(own.toString()));
    }

    private static String arrivalBlocker(SafeArrival.Blocked blocked) {
        if (blocked.nothing()) {
            return "clear here";
        }
        StringBuilder out = new StringBuilder();
        if (blocked.block() != null) {
            out.append("block at ").append(blocked.block().toShortString());
        }
        for (dev.ryanhcode.sable.sublevel.SubLevel hull : blocked.hulls()) {
            dev.ryanhcode.sable.companion.math.BoundingBox3dc box = hull.boundingBox();
            out.append(out.length() == 0 ? "" : "; ").append(String.format(java.util.Locale.ROOT,
                    "hull %s%s, %.0f x %.0f x %.0f at %.0f, %.0f, %.0f", hull.getUniqueId(),
                    hull.getName() == null ? "" : " \"" + hull.getName() + "\"",
                    box.width(), box.height(), box.length(),
                    (box.minX() + box.maxX()) * 0.5D, (box.minY() + box.maxY()) * 0.5D,
                    (box.minZ() + box.maxZ()) * 0.5D));
        }
        return out.toString();
    }

    /** Everything that happens once a flight is planned and every check has passed: the rift opens. */
    private void openRift(ServerLevel serverLevel, Airship airship, ServerLevel destination, WarpFlight planned,
                          Vector3d bow) {
        flight = planned;
        airship.warpData().rememberOrigin(airship.position(), airship.orientation());
        sequenceTicks = 0;
        transition(RiftDriveState.WARPING);
        // Claim the ground at the far end now, while the ship still has a whole flight to make.
        // Left until the teleport, the chunks would come off disk at the exact moment the hull is
        // meant to be flying out of the aperture.
        // Who is aboard, promised to arrive with the hull whatever becomes of this machine.
        CrewManifest.board(airship);
        // Laid along the whole coast, from where the hull reappears to where it rests, so no part of a
        // long arrival - above all the spot the teleport puts it down - sits in a chunk nobody holds.
        holdArrival(destination, planned);
        // The corridor the hull is about to fly down, held resident so the launch-clearance proof
        // stays true for the whole flight rather than only at the instant it was taken.
        ArrivalTicket.holdCorridor(serverLevel, airship.centre(new Vector3d()), bow,
                LaunchClearance.launchReach(planned),
                ArrivalTicket.arrivalHalfWidth(planned.crossSpan(), planned.apertureMargin()),
                planned.hullSpan() * 0.5D);
        // The drive and its ship are held by the flight hold (tickHolds) from lock-in onward, which is what
        // keeps the machine running the sequence from being unloaded out from under it partway through.

        WarpTrace.plan(worldPosition, airship, planned, bow);
        if (foldPassage != null) {
            WarpTrace.passage(worldPosition, foldPassage, planned.stepWait());
        }
        // The entry aperture has to stay open for the run in, the passage, and the corridor run -
        // all of which now happen inside it. The fleet's one rift is the flagship's to tear, and it holds
        // for the fleet's wait as well; the escorts fly into it.
        if (foldPassage == null || foldOrder == null || foldOrder.lead()) {
            playSound(AWSounds.RIFT_OPEN.get(), 1.2F, 1.0F);
            broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_OPEN, cueEntry(planned),
                    planned.stepWait() + AWConfig.APPROACH_LIMIT_TICKS.get() + planned.transitTicks()
                            + planned.corridorTicks(),
                    planned.entryThroatDepth());
        }
    }

    /**
     * The jump: the drive has spun up, and throws the ship into cruise.
     *
     * <p>No rift and no corridor. A cruise does not go anywhere the ship could not have flown; it is given
     * more thrust than anything else aboard could give it, and flown there. So the one thing proven here is
     * the bow guard - that the hull is not about to be driven into something from a standing start. From
     * then on {@link CruiseSensor} watches the way ahead for the whole flight.
     *
     * <p>Setting off is when a cruise starts to cost. The charge held now is what the whole flight is
     * priced against, and the fixed part of the price - the base, and the hull's mass - comes off at once,
     * the way a waypoint jump's whole price does at its crossing.
     */
    private void jumpToCruise() {
        if (!(level instanceof ServerLevel serverLevel)) {
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }
        if (fastened(airship, null, initiator).isFailure()) {
            abort(WarpFailure.SHIP_FASTENED);
            return;
        }
        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        if (clearanceChecks
                && LaunchClearance.guard(airship, serverLevel, bow) == SafeArrival.Clearance.OBSTRUCTED) {
            abort(WarpFailure.NO_CLEAR_LAUNCH);
            return;
        }

        WarpCost.Formula formula = WarpCost.fromConfig(tier);
        double mass = WarpValidator.effectiveMass(airship);
        sequenceTicks = 0;
        cruiseStartCharge = charge;
        cruiseSpeed = 0.0D;
        cruiseDistance = 0.0D;
        cruiseLastCentre = airship.centre(new Vector3d());
        cruisePhase = CruisePhase.CRUISING;
        charge = FreeCruise.chargeAfter(formula, mass, cruiseStartCharge, 0.0D);
        transition(RiftDriveState.WARPING);
        // Nobody is left behind by a ship doing ninety blocks a second: the same promise a warp makes.
        CrewManifest.board(airship);
        lookAhead(serverLevel, airship, bow);
        holdCruiseAhead(serverLevel, airship, bow);

        WarpTrace.cruiseEngaged(worldPosition, airship, bow, tier.cruiseSpeed(), cruiseThrottle,
                FreeCruise.limit(formula, mass, cruiseStartCharge));
        playSound(AWSounds.RIFT_OPEN.get(), 1.2F, 1.5F);
        playSound(AWSounds.WARP_TRAVEL.get(), 1.0F, 1.2F);
        // For anyone watching from outside: a burst of streaks off the drive as the ship leaps away.
        broadcastEffect(ClientboundWarpEffectPacket.Stage.CORRIDOR);
        sendCrew(airship, ClientboundSupercruisePacket.Phase.ENGAGE, bow);
        markStateDirty();
    }

    /**
     * One tick of a cruise: charge for what was flown, decide whether to let go, look ahead, set the speed,
     * and push.
     *
     * <p>The push is horizontal only, along the bow as it points this tick - see
     * {@link Airship#cruiseVelocity}. Everything the ship's own machinery does to its height and heading
     * is left to it, which is how a pilot steers a cruise and holds its altitude: exactly as they would
     * steer and trim the ship with the drive switched off.
     */
    private void tickCruise() {
        Airship airship = airship();
        if (!(level instanceof ServerLevel serverLevel) || airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }
        passengers.hold(airship, WarpFlight.Stage.CORRIDOR);
        CrewManifest.refresh(airship);

        // The same runaway guard a warp has. A cruise never asks for anything near the ceiling, so a hull
        // doing more than it is one the solver has thrown, and flying on would carry it further.
        if (airship.velocity().length() > Airship.maxCommandedSpeed()) {
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }

        WarpCost.Formula formula = WarpCost.fromConfig(tier);
        double mass = WarpValidator.effectiveMass(airship);
        Vector3d centre = airship.centre(new Vector3d());
        if (cruiseLastCentre != null) {
            double moved = Math.hypot(centre.x - cruiseLastCentre.x, centre.z - cruiseLastCentre.z);
            cruiseDistance += FreeCruise.countedTravel(moved, cruiseSpeed);
        }
        cruiseLastCentre = centre;
        charge = FreeCruise.chargeAfter(formula, mass, cruiseStartCharge, cruiseDistance);

        double top = FreeCruise.perTick(tier.cruiseSpeed());
        double brake = FreeCruise.rate(top, AWConfig.CRUISE_BRAKING_TICKS.get());
        double accelerate = FreeCruise.rate(top, AWConfig.CRUISE_ACCELERATION_TICKS.get());
        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());

        if (cruisePhase == CruisePhase.CRUISING) {
            double limit = FreeCruise.limit(formula, mass, cruiseStartCharge);
            DropReason why = cruiseDisengage ? DropReason.ASKED
                    : cruiseThrottle <= 0.0D ? DropReason.THROTTLE
                    : !isRunningFastEnough() ? DropReason.STALLED
                    : FreeCruise.mustDropOut(cruiseDistance, cruiseSpeed, brake, limit)
                    ? (limit >= formula.maximumDistance() ? DropReason.RANGE : DropReason.CHARGE)
                    : null;
            if (why != null) {
                beginDropOut(airship, bow, why);
            }
        }

        if (sequenceTicks % FreeCruise.SENSOR_INTERVAL == 0) {
            lookAhead(serverLevel, airship, bow);
        }
        double wanted = cruisePhase == CruisePhase.CRUISING ? top * cruiseThrottle : 0.0D;
        cruiseSpeed = FreeCruise.nextSpeed(cruiseSpeed, Math.min(wanted, cruiseSafeSpeed), accelerate, brake);
        airship.cruiseVelocity(new Vector3d(bow).mul(cruiseSpeed));

        if (sequenceTicks % FreeCruise.TICKET_INTERVAL == 0) {
            holdCruiseAhead(serverLevel, airship, bow);
        }
        if (sequenceTicks % FreeCruise.CREW_INTERVAL == 0) {
            sendCrew(airship, cruisePhase == CruisePhase.DROPPING
                    ? ClientboundSupercruisePacket.Phase.DROP : ClientboundSupercruisePacket.Phase.CRUISE, bow);
            // The goggles and the console read speed and distance off the synced drive.
            markStateDirty();
        }
        if (sequenceTicks % 40 == 0 && cruisePhase == CruisePhase.CRUISING && wanted > cruiseSafeSpeed + 1.0e-3D) {
            // Otherwise a ship that will not go any faster just looks like a drive that is not working.
            String notice = cruiseNoticeKey(cruiseSight);
            if (notice != null) {
                for (ServerPlayer player : airship.crew()) {
                    player.displayClientMessage(AWLang.translate(notice).component(), true);
                }
            }
        }

        if (cruisePhase == CruisePhase.DROPPING && cruiseSpeed <= FreeCruise.STOPPED) {
            completeCruise(airship);
        }
    }

    /**
     * Reads the way ahead and sets how fast the ship may go.
     *
     * <p>Far enough to stop from the faster of what the ship is doing and what it is being asked to do, so
     * pushing the throttle up can never outrun the last look.
     */
    private void lookAhead(ServerLevel serverLevel, Airship airship, Vector3dc bow) {
        double top = FreeCruise.perTick(tier.cruiseSpeed());
        double brake = FreeCruise.rate(top, AWConfig.CRUISE_BRAKING_TICKS.get());
        double asked = Math.max(cruiseSpeed, top * cruiseThrottle);
        double reach = FreeCruise.sensorReach(asked, brake, FreeCruise.SENSOR_INTERVAL);
        CruiseSensor.Reading reading = CruiseSensor.ahead(airship, serverLevel, bow, reach,
                AWConfig.CRUISE_SENSOR_BUDGET.get());
        cruiseSight = reading.limit();
        cruiseSafeSpeed = reading.limit() == CruiseSensor.Limit.CLEAR
                ? Double.POSITIVE_INFINITY
                : FreeCruise.safeSpeed(reading.distance() - FreeCruise.STANDOFF, brake, FreeCruise.SENSOR_INTERVAL);
    }

    /**
     * Asks for the ground ahead to load, from under the hull to a few seconds of flight past where the
     * ship could stop - and keeps the drive's own chunk, as a warp does.
     */
    private void holdCruiseAhead(ServerLevel serverLevel, Airship airship, Vector3dc bow) {
        FlightGeometry geometry = WarpFlight.geometry(airship, bow, 1);
        double span = geometry == null ? 16.0D : geometry.hullSpan();
        double cross = geometry == null ? 16.0D : geometry.crossSpan();
        double top = FreeCruise.perTick(tier.cruiseSpeed());
        double brake = FreeCruise.rate(top, AWConfig.CRUISE_BRAKING_TICKS.get());
        double asked = Math.max(cruiseSpeed, top * cruiseThrottle);
        double reach = span * 0.5D + FreeCruise.sensorReach(asked, brake, FreeCruise.SENSOR_INTERVAL)
                + asked * FreeCruise.LOAD_AHEAD_TICKS;
        ArrivalTicket.holdCruise(serverLevel, airship.centre(new Vector3d()), bow, reach,
                cross * 0.5D + 16.0D, span * 0.5D + 16.0D);
    }

    /**
     * Starts letting go. The throttle is ignored from here, and the ship brakes to a stop wherever it is.
     *
     * <p>The pilot is told when it was not their idea: a cruise that runs out of charge, range or rotation
     * slows for a reason that is not on the screen, and should say what it was.
     */
    private void beginDropOut(Airship airship, Vector3dc bow, DropReason why) {
        cruisePhase = CruisePhase.DROPPING;
        cruiseDisengage = false;
        WarpTrace.cruiseDropOut(worldPosition, why.name(), cruiseDistance, cruiseSpeed, charge);
        switch (why) {
            case CHARGE -> notifyInitiator(WarpFailure.INSUFFICIENT_CHARGE);
            case RANGE -> notifyInitiator(WarpFailure.DESTINATION_TOO_FAR);
            case STALLED -> notifyInitiator(WarpFailure.INSUFFICIENT_POWER);
            default -> {
            }
        }
        playSound(AWSounds.WARP_EXIT.get(), 1.0F, 1.3F);
        sendCrew(airship, ClientboundSupercruisePacket.Phase.DROP, bow);
        markStateDirty();
    }

    /** The ship has stopped: hand it back, let the crew go, and cool down. */
    private void completeCruise(Airship airship) {
        send(airship.crew(), ClientboundSupercruisePacket.leave());
        // Paid for block by block on the way, so nothing is left to take when it is finished.
        committedDistance = cruiseDistance;
        committedCost = 0.0F;
        WarpTrace.cruiseComplete(worldPosition, cruiseDistance, charge);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.CORRIDOR);
        completeWarp();
    }

    /** Tells the crew how the cruise is going, for the starlines. */
    private void sendCrew(Airship airship, ClientboundSupercruisePacket.Phase phase, Vector3dc bow) {
        double top = FreeCruise.perTick(tier.cruiseSpeed());
        float fraction = top <= 0.0D ? 0.0F : (float) Math.min(1.0D, cruiseSpeed / top);
        send(airship.crew(), new ClientboundSupercruisePacket(phase, fraction, (float) bow.x(), (float) bow.z(),
                effectiveColour(), effectiveIntensity()));
    }

    /** What to tell a pilot whose cruise is slower than they asked, or {@code null} when nothing is holding it back. */
    @Nullable
    private static String cruiseNoticeKey(CruiseSensor.Limit sight) {
        return switch (sight) {
            case TERRAIN -> "gui.rift_drive.terrain_ahead";
            case UNLOADED -> "gui.rift_drive.loading_ahead";
            case CLEAR -> null;
        };
    }

    /**
     * Runs the whole pre-flight - plan, departure clearance, arrival durability - against the standing
     * course and reports the verdict, moving nothing.
     *
     * <p>The reproduction harness behind {@code /aerowarptics warp dryrun}. Every check the real
     * {@link #beginWarp} makes before the rift opens is made here read-only, so a warp that would be
     * refused can be understood at the mooring: which of the departure path, the arrival search or the
     * landing's residency is the one saying no, with the numbers behind it.
     */
    public List<Component> dryRun() {
        List<Component> report = new ArrayList<>(preflightReport());
        report.addAll(flightDryRun());
        // Into the log as well when tracing, so a dry run an admin asked a player for arrives with the rest
        // of the trace instead of as a screenshot of chat.
        if (WarpTrace.enabled()) {
            for (Component line : report) {
                Trace.log(Trace.DRYRUN, "{} {}", Trace.pos(worldPosition), line.getString());
            }
        }
        return report;
    }

    /**
     * The checks a launch makes before any flight is planned: power, charge, the hull's warp lock, and the
     * standing course's price against the charge and the tier's reach.
     *
     * <p>The flight dry run below starts from a course and asks only about the flight, so a drive that
     * would be refused before it ever got that far - a heavy hull whose jump costs more than a full charge,
     * reported to the pilot as "beyond drive range" - used to pass it.
     */
    private List<Component> preflightReport() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        String verdict = !isRunningFastEnough() ? "WOULD REFUSE (insufficient_power)"
                : state.acceptsDestination() ? "ready"
                : "WOULD REFUSE (" + (state.isSequenceRunning() ? "drive_busy"
                        : charge < 1.0F ? "insufficient_charge" : "drive_busy")
                        + ") - a drive launches only when fully charged and idle";
        lines.add(Component.literal(String.format(java.util.Locale.ROOT,
                "pre-flight: %s %s, charge %s, %s rpm (needs %d) -> %s", tier.getSerializedName(), state.name(),
                Trace.percent(charge), Trace.num(Math.abs(getSpeed())), tier.minimumRpm(), verdict)));
        Airship airship = airship();
        if (airship != null && airship.isActive()) {
            lines.add(Component.literal("  hull lock: " + airship.warpData().describe(serverLevel.getGameTime())
                    + (partWarping(airship) ? " - WOULD REFUSE (airship_already_warping): a part of this vessel"
                    + " is warping under another drive" : "")));
            // Everything found, holding or not, so a report shows the docked ship that is fine beside the
            // rope that is not - and the parts, since a warp now moves all of them.
            List<Airship> vessel = airship.vessel();
            List<Fastening> found = Fastenings.of(airship);
            Fastening holding = Fastening.worst(found).orElse(null);
            lines.add(Component.literal("  vessel: the hull and " + (vessel.size() - 1) + " part(s) the warp carries"
                    + (vessel.size() > 1 ? " " + vessel.subList(1, vessel.size()) : "")));
            lines.add(Component.literal("  fastenings: " + (found.isEmpty() ? "none" : found.toString())
                    + (holding == null ? " -> free" : " -> WOULD REFUSE (ship_fastened): " + holding)));
            if (standingCourse != null && mode == DriveMode.WAYPOINT) {
                lines.add(Component.literal("  price: " + tracePricing(serverLevel, airship, standingCourse,
                        WarpValidator.effectiveMass(airship))));
            }
        }
        return lines;
    }

    private List<Component> flightDryRun() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of(Component.literal("dry run: not on a server"));
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return List.of(Component.literal("dry run: this drive is not aboard an assembled airship"));
        }
        if (mode == DriveMode.FREE_CRUISE) {
            return cruiseDryRun(serverLevel, airship);
        }
        WarpCourse course = standingCourse;
        if (course == null) {
            return List.of(Component.literal("dry run: no course is set - aim this drive at an Astrolabe first"));
        }

        ServerLevel destination;
        BlockPos targetBlock;
        int requestedHeight;
        // How long a jump it is, as a launch would commit it: what the corridor's length is worked out from.
        double distance;
        if (course.isAnchor()) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(course.anchorId());
            if (anchor == null) {
                return List.of(Component.literal("dry run: the course anchor is gone"));
            }
            destination = serverLevel.getServer().getLevel(anchor.dimension());
            targetBlock = anchor.pos();
            requestedHeight = anchor.arrivalHeight();
            distance = WarpValidator.warpDistance(airship, anchor);
        } else {
            destination = course.dimension() == null ? serverLevel
                    : serverLevel.getServer().getLevel(course.dimension());
            targetBlock = course.fix();
            requestedHeight = course.arrivalHeight();
            distance = WarpValidator.fixDistance(airship, course);
        }
        int corridorTime = CorridorTime.fromConfig(tier.warpTicks(), distance);
        if (destination == null || !SafeArrival.isDestinationLoadable(destination, targetBlock)) {
            return List.of(Component.literal("dry run: the destination chunk will not load"));
        }
        boolean sameLevel = destination.dimension().equals(serverLevel.dimension());
        int arrivalHeight = ArrivalHeight.planned(requestedHeight, course.holdAltitude(), sameLevel,
                airship.vesselWorldBounds().minY(), targetBlock.getY());

        List<Component> report = new ArrayList<>();
        report.add(Component.literal("dry run to " + course.label() + " " + targetBlock.toShortString()
                + ", arrival height " + arrivalHeight
                + (!course.holdAltitude() ? "" : sameLevel ? " (holding altitude)"
                        : " (hold altitude ignored: another dimension)")));
        if (!sameLevel) {
            // The same question beginWarp asks at the mooring, so a crossing that would be refused for room
            // or height says so here rather than only when somebody pulls the lever.
            WarpFailure crossing = CrossDimensionWarp.preflight(airship, destination);
            report.add(Component.literal("  crossing into " + destination.dimension().location() + ": "
                    + (crossing.isFailure() ? "REFUSED " + crossing.getSerializedName() : "ok")));
        }

        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        // The hull as the flight measures it, along and across the bearing. Printed first because every
        // figure below is derived from it, and because it is the line that shows whether the ship's
        // compass heading is changing the answer - it should not.
        uk.co.iceconchy.aerowarptics.warp.FlightGeometry geometry =
                WarpFlight.geometry(airship, bow, tier.arriveTicks());
        if (geometry != null) {
            report.add(Component.literal(String.format(java.util.Locale.ROOT,
                    "  hull: %.1f along the bow, %.1f across, %.1f high -> rift r %.1f, margin %.1f,"
                            + " run-out %.1f",
                    geometry.hullSpan(), geometry.crossSpan(), geometry.height(), geometry.riftRadius(),
                    geometry.apertureMargin(), geometry.emergeDistance())));
        }
        if (corridorTime != tier.warpTicks()) {
            // Only when distance has changed it: otherwise it is the tier's own figure, which is not news.
            report.add(Component.literal(String.format(java.util.Locale.ROOT,
                    "  time in corridor: %d ticks for %.0f blocks (%d for this drive, flight.warpTimeScaling %s)",
                    corridorTime, distance, tier.warpTicks(), AWConfig.WARP_TIME_SCALING.get())));
        }
        // A station gives out berths and nothing else, so its dry run is the berth's: which, or why not. Asked
        // as the launch asks, less the wards, which a dry run has never known of.
        WarpAnchor station = !course.isAnchor() ? null : stationOf(destination, course.anchorId());
        WarpFlight.Plan plan = station != null
                ? planBerth(airship, destination, station, bow, List.of(), null, report)
                : WarpFlight.plan(airship, destination, targetBlock, bow,
                corridorTime, tier.arriveTicks(), arrivalHeight, clearanceChecks);
        WarpFlight planned = plan.flight();
        if (planned == null) {
            report.add(Component.literal(station != null
                    ? "  arrival: no berth at station " + station.displayName()
                    : plan.refusal() == WarpFailure.ARRIVAL_UNPROVEN
                    ? "  arrival: UNPROVEN - the landing needs more than " + ArrivalTicket.maxChunks()
                            + " chunks (maxArrivalChunks), or every place tried ran out of chunks or budget"
                    : plan.refusal() == WarpFailure.NO_SAFE_ARRIVAL
                    ? "  arrival: NO SAFE ARRIVAL - nowhere clear near the target"
                    : "  arrival: no plan - " + plan.refusal().getSerializedName()));
            if (plan.refusal() == WarpFailure.NO_SAFE_ARRIVAL && geometry != null) {
                arrivalBlockers(report, airship, destination, targetBlock, bow, geometry.emergeDistance(),
                        arrivalHeight);
            }
            report.add(Component.literal("  verdict: WOULD REFUSE (" + plan.refusal().getSerializedName() + ")"));
            return report;
        }

        if (!clearanceChecks) {
            // Said outright rather than left to be inferred from lines that are missing: a report with no
            // bow guard in it would otherwise read as a bow guard that had nothing to say.
            boolean durable = ArrivalTicket.isLandingDurable(destination, planned.arrivalOrigin());
            boolean refuseUndurable = !AWConfig.KEEP_AIRSHIPS_LOADED.get()
                    && AWConfig.REQUIRE_LOADED_ARRIVAL.get();
            report.add(Component.literal("  clearance checks: OFF at the console - no bow guard, corridor"
                    + " or arrival search; nothing ahead or at the landing has been looked at"));
            report.add(Component.literal(String.format(java.util.Locale.ROOT,
                    "  arrival: straight in at (%.1f, %.1f, %.1f), landing %s%s",
                    planned.arrivalOrigin().x(), planned.arrivalOrigin().y(), planned.arrivalOrigin().z(),
                    durable ? "durable" : "will unload",
                    refuseUndurable || durable ? "" : " (drive keeps it loaded)")));
            WarpFailure verdict = refuseUndurable && !durable ? WarpFailure.ARRIVAL_NOT_LOADED : WarpFailure.NONE;
            report.add(Component.literal("  verdict: " + (verdict.isFailure()
                    ? "WOULD REFUSE (" + verdict.getSerializedName() + ")" : "WOULD PROCEED")));
            return report;
        }

        uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance corridor =
                LaunchClearance.clearance(airship, serverLevel, bow, planned);
        boolean launchClear = corridor == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.CLEAR;
        boolean unproven = corridor == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.UNPROVEN;
        // Mirrors beginWarp: an obstructed corridor only refuses while the check is required. With
        // requireClearLaunch off the warp is flown into the terrain anyway, so a fouled corridor is
        // reported but does not change the verdict.
        boolean requireClearLaunch = AWConfig.REQUIRE_CLEAR_LAUNCH.get();
        boolean durable = ArrivalTicket.isLandingDurable(destination, planned.arrivalOrigin());
        // The refusal can only bite when the drive is NOT keeping its own landing loaded; with
        // keepAirshipsLoaded on, an undurable landing is held by the drive rather than refused.
        boolean refuseUndurable = !AWConfig.KEEP_AIRSHIPS_LOADED.get()
                && AWConfig.REQUIRE_LOADED_ARRIVAL.get();

        // The box every departure sweep starts from, to the hundredth. Whether its floor sits just under
        // a whole block - and so whether the ground it rests on is within the contact tolerance - is
        // otherwise invisible from the overlay.
        dev.ryanhcode.sable.companion.math.BoundingBox3dc hullBox = airship.worldBounds();
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "  hull box: (%.2f, %.2f, %.2f) to (%.2f, %.2f, %.2f)",
                hullBox.minX(), hullBox.minY(), hullBox.minZ(),
                hullBox.maxX(), hullBox.maxY(), hullBox.maxZ())));

        // The guard is the gate; the corridor below it is advisory unless requireClearLaunch is on.
        uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance guard =
                LaunchClearance.guard(airship, serverLevel, bow);
        boolean guardBlocked =
                guard == uk.co.iceconchy.aerowarptics.warp.SafeArrival.Clearance.OBSTRUCTED;
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "  bow guard: reach %.1fb, bare hull -> %s",
                LaunchClearance.guardReach(), guardBlocked ? "BLOCKED" : "CLEAR")));
        if (guardBlocked) {
            net.minecraft.core.BlockPos hit =
                    LaunchClearance.guardObstruction(airship, serverLevel, bow);
            report.add(Component.literal(hit != null
                    ? "    collision at " + hit.toShortString() + " - come about"
                    : "    " + fouledBy(serverLevel, airship, bow)));
        }

        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "  corridor: reach %.1fb, bare hull -> %s",
                LaunchClearance.launchReach(planned),
                launchClear ? "CLEAR"
                        : unproven ? "UNPROVEN"
                        : requireClearLaunch ? "BLOCKED" : "BLOCKED (advisory - not a gate)")));
        if (unproven) {
            // Not a bearing problem, so do not send the pilot hunting for a block. Either chunks
            // were missing or the budget ran out, both of which are about the check rather than
            // the corridor.
            report.add(Component.literal(String.format(java.util.Locale.ROOT,
                    "    unproven: ran out of budget (%d block reads) or chunks were missing - "
                            + "not an obstruction", SafeArrival.budgetFor(airship))));
        } else if (!launchClear) {
            // Name where the corridor is fouled, so the pilot knows which way to come about.
            net.minecraft.core.BlockPos collision =
                    LaunchClearance.firstObstruction(airship, serverLevel, bow, planned);
            report.add(Component.literal(collision != null
                    ? "    collision at " + collision.toShortString()
                    : "    collision: fouled, but no single block to name"));
        }
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "  arrival: run-out %.1fb, landing %s%s",
                planned.emergeRunOut(), durable ? "durable" : "will unload",
                refuseUndurable ? "" : durable ? "" : " (drive keeps it loaded)")));

        WarpFailure verdict = guardBlocked ? WarpFailure.NO_CLEAR_LAUNCH
                : requireClearLaunch && !launchClear
                ? (unproven ? WarpFailure.LAUNCH_UNPROVEN : WarpFailure.NO_CLEAR_LAUNCH)
                : refuseUndurable && !durable ? WarpFailure.ARRIVAL_NOT_LOADED
                : WarpFailure.NONE;
        report.add(Component.literal("  verdict: " + (verdict.isFailure()
                ? "WOULD REFUSE (" + verdict.getSerializedName() + ")" : "WOULD PROCEED")));
        return report;
    }

    /**
     * The dry run for a drive set to cruise: whether it would set off, how far and how fast it could go,
     * and what it can see ahead right now.
     *
     * <p>There is no arrival to check. A cruise ends wherever the pilot stops flying, so the useful thing to
     * report at the mooring is what the flight itself would be held to.
     */
    private List<Component> cruiseDryRun(ServerLevel serverLevel, Airship airship) {
        List<Component> report = new ArrayList<>();
        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        WarpCost.Formula formula = WarpCost.fromConfig(tier);
        double mass = WarpValidator.effectiveMass(airship);
        double limit = FreeCruise.limit(formula, mass, charge);
        double top = FreeCruise.perTick(tier.cruiseSpeed());
        double brake = FreeCruise.rate(top, AWConfig.CRUISE_BRAKING_TICKS.get());
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "dry run: free cruise heading %s, top speed %.0f b/s (%d ticks up, %d ticks to stop)",
                Direction.getNearest(bow.x, 0.0D, bow.z).getName(), tier.cruiseSpeed(),
                AWConfig.CRUISE_ACCELERATION_TICKS.get(), AWConfig.CRUISE_BRAKING_TICKS.get())));
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "  reach on this charge: %.0f blocks (range %.0f) - about %.0fs at full throttle",
                Math.max(0.0D, limit), formula.maximumDistance(),
                Math.max(0.0D, limit) / Math.max(1.0e-6D, tier.cruiseSpeed()))));
        double reach = FreeCruise.sensorReach(top, brake, FreeCruise.SENSOR_INTERVAL);
        CruiseSensor.Reading reading = CruiseSensor.ahead(airship, serverLevel, bow, reach,
                AWConfig.CRUISE_SENSOR_BUDGET.get());
        double safe = reading.limit() == CruiseSensor.Limit.CLEAR ? top
                : Math.min(top, FreeCruise.safeSpeed(reading.distance() - FreeCruise.STANDOFF, brake,
                FreeCruise.SENSOR_INTERVAL));
        report.add(Component.literal(String.format(java.util.Locale.ROOT,
                "  ahead: %s after %.0f of %.0f blocks -> would fly at up to %.0f b/s",
                reading.limit(), reading.distance(), reach, safe * FreeCruise.TICKS_PER_SECOND)));
        boolean guardBlocked = clearanceChecks
                && LaunchClearance.guard(airship, serverLevel, bow) == SafeArrival.Clearance.OBSTRUCTED;
        report.add(Component.literal("  bow guard: " + (!clearanceChecks ? "OFF (clearance checks off at the console)"
                : guardBlocked ? "BLOCKED" : "CLEAR")));
        WarpFailure verdict = FreeCruise.canEngage(formula, mass, charge);
        if (!verdict.isFailure() && guardBlocked) {
            verdict = WarpFailure.NO_CLEAR_LAUNCH;
        }
        report.add(Component.literal("  verdict: " + (verdict.isFailure()
                ? "WOULD REFUSE (" + verdict.getSerializedName() + ")" : "WOULD ENGAGE")));
        return report;
    }

    /**
     * The two departure volumes and the blocks fouling them, for the clearance visualiser.
     *
     * <p>Both are lists because the corridor is marched along the bow as a chain of hull-sized
     * segments rather than boxed by one enclosing volume. Drawing the enclosing box would show a
     * corridor far wider than the one actually tested on any diagonal heading - the overlay has to
     * show what the check reads, or it is worse than no overlay.
     *
     * @param core   the bare hull sweep, no padding - what the ship physically flies through, and the
     *               segments actually tested
     * @param padded the same sweep widened by the aperture margin and clearance - where the aperture
     *               reaches, drawn for reference and never tested
     * @param hits   the solid blocks standing in the core, capped for display
     */
    public record ClearanceView(java.util.List<dev.ryanhcode.sable.companion.math.BoundingBox3d> core,
                                java.util.List<dev.ryanhcode.sable.companion.math.BoundingBox3d> padded,
                                java.util.List<net.minecraft.core.BlockPos> hits) {
    }

    /**
     * Measures the departure corridor and finds what stands in it, moving nothing - the data behind
     * {@code /aerowarptics warp clearance}.
     *
     * <p>Needs no standing course: the corridor's shape comes from the hull and this drive's tier, so
     * a pilot can see the clearance before choosing where to go. Returns both the bare hull sweep the
     * check tests and the padded volume it used to test, because the gap between them was exactly the
     * over-sensitivity that was complained about - and the padding is still where the aperture reaches.
     *
     * @return the view, or {@code null} when this drive is not aboard a live airship
     */
    public ClearanceView clearanceVisual() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return null;
        }
        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        WarpFlight.DepartureShape shape = WarpFlight.departureShape(airship, bow);
        if (shape == null) {
            return null;
        }
        double reach = shape.corridorReach() + AWConfig.RIFT_LEAD_DISTANCE.get();
        double margin = shape.apertureMargin();
        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();
        // The hull's own box - what LaunchClearance actually sweeps. Outlining the whole assembly
        // here would draw a corridor wider than the one the check reads, which is the one thing an
        // overlay must never do.
        dev.ryanhcode.sable.companion.math.BoundingBox3dc hull = airship.worldBounds();
        java.util.List<dev.ryanhcode.sable.companion.math.BoundingBox3d> padded =
                LaunchClearance.departureSegments(hull, bow, reach, clearance, margin);
        java.util.List<dev.ryanhcode.sable.companion.math.BoundingBox3d> core =
                LaunchClearance.testedSegments(hull, bow, reach);
        java.util.List<net.minecraft.core.BlockPos> hits = new java.util.ArrayList<>();
        // Collisions are found in the core, because that is what the check reads. A block that only
        // the padding reaches is not in the way of anything, and marking it red would send the pilot
        // coming about for grass.
        for (dev.ryanhcode.sable.companion.math.BoundingBox3d segment : core) {
            if (hits.size() >= 512) {
                break;
            }
            hits.addAll(SafeArrival.obstructions(server, segment, 512 - hits.size()));
        }
        return new ClearanceView(core, padded, hits);
    }

    /**
     * Flies one tick of the journey.
     *
     * <p>The drive commands the hull's velocity outright for the whole run: the pilot's controls, the
     * ship's own thrust and gravity are all overridden until it comes to rest. The only two teleports
     * are the threshold crossings, and both carry passengers across.
     */
    private void tickFlight() {
        WarpFlight current = flight;
        Airship airship = airship();
        if (current == null || airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }

        // Before the hull is moved, not after: the seats worth remembering are the ones the crew were
        // standing in when the tick began, and anybody the last tick threw off has to have the ship's
        // borrowed momentum taken back before it carries them any further.
        passengers.hold(airship, current.stage());
        CrewManifest.refresh(airship);
        // The attitude the flight was planned at, which is the one the crossing restores - or, for a flight
        // into a Berth, turns: so the flight is asked which it holds at this stage, and never simply for the
        // one it arrives at. Held after every physics substep, not just commanded here: the hull's own lift
        // blocks twist it at warp speed.
        AttitudeHold.hold(airship, current.attitude());

        // Belt-and-braces for the yeet. Phases that prove the path clear should mean this never fires,
        // so reaching it is itself the signal that one of them missed something: a penetration
        // ejection, a frame slip in the emerge command, or momentum that stacked. Catch the runaway
        // from the previous tick before another velocity is commanded on top of it, and put the hull
        // back rather than let it travel - abort restores the origin for any stage that has moved.
        double ceiling = Airship.maxCommandedSpeed();
        Vector3d reported = airship.velocity();
        if (reported.length() > ceiling) {
            WarpTrace.flightTick(current.stage(), current.lastCommanded(), reported, ceiling);
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }

        WarpFlight.Stage tickedStage = current.stage();
        switch (current.tick(airship)) {
            case CONTINUE -> renewCorridorHolds(airship, current);
            case BEGIN_TRANSIT -> {
                WarpTrace.stage(WarpFlight.Stage.APPROACH, WarpFlight.Stage.TRANSIT,
                        current.stageTicks(), airship, current);
                current.beganTransit();
                playSound(AWSounds.WARP_TRAVEL.get(), 0.8F, 0.8F);
                // The hull stays inside this aperture for the passage *and* the corridor run. The throat
                // goes with it so that a client which never saw the aperture open can open it here.
                if (firstTo(FoldLog.SharedCue.ENTRY_TRANSIT)) {
                    broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, cueEntry(current),
                            current.transitTicks() + current.corridorTicks(), current.entryThroatDepth());
                }
                // The wash starts as the bow goes in, not when the hull teleports. The crew's own
                // camera crosses the aperture partway through the passage, and without this they
                // would watch the world go dark and then carry on flying through it for a second.
                send(airship.crew(), ClientboundCorridorPacket.enter(
                        current.transitTicks() + current.corridorTicks(), effectiveColour(), effectiveIntensity()));
            }
            case ENTER_CORRIDOR -> {
                // Nothing moves here any more. The hull is deep inside the entry aperture's throat
                // and simply keeps flying down it, so there is no teleport, no second region of the
                // world to load, and no change of pace at the boundary.
                WarpTrace.stage(WarpFlight.Stage.TRANSIT, WarpFlight.Stage.CORRIDOR,
                        current.stageTicks(), airship, current);
                current.enteredCorridor();
                playSound(AWSounds.WARP_TRAVEL.get(), 1.0F, 1.0F);
                broadcastEffect(ClientboundWarpEffectPacket.Stage.CORRIDOR);
                // The crew's wash is already running - it started when the bow went in. Re-arm it to
                // cover the corridor, rather than starting it, so it does not flicker at the seam.
                send(airship.crew(), ClientboundCorridorPacket.enter(
                        current.corridorTicks(), effectiveColour(), effectiveIntensity()));
                // Opened now, not on arrival: anyone at the destination gets a few seconds of warning
                // before a hull comes through it. Only they do - the crew are at this end, and are shown
                // it by the transit cue when they cross.
                // Unless a Rift Ward has turned it aside. Then nothing stands at the rim until the hull breaks
                // out of it: the tear is seen caught over the field and dragged there first, and the transit cue,
                // which can open an aperture by itself, opens this one as the hull crosses. The fold's log is still
                // marked, so that no escort takes itself for the first there and opens it early.
                boolean turned = wardTurn != null && wardTurn.turned();
                if (firstTo(FoldLog.SharedCue.EXIT_OPEN) && !turned) {
                    broadcastRift(arrivalLevel(), ClientboundWarpEffectPacket.Stage.RIFT_OPEN, cueExit(current),
                            current.corridorTicks() + current.transitTicks(), -current.exitThroatDepth(), List.of());
                }
                announceWard(current, airship);
            }
            case EXIT_CORRIDOR -> {
                // The crew is read before the hull moves. Sable's entity tracking needs a tick to
                // catch up with a teleport, so asking afterwards can come back empty.
                List<ServerPlayer> crew = airship.crew();
                WarpTrace.stage(WarpFlight.Stage.CORRIDOR, WarpFlight.Stage.BREACH,
                        current.stageTicks(), airship, current);
                Vector3dc departure = new Vector3d(airship.position());
                ServerLevel arrivalLevel = arrivalLevel();
                if (arrivalLevel != null && arrivalLevel != level) {
                    crossInto(arrivalLevel, airship, current, departure, crew.size());
                    return;
                }
                // The ground the hull is about to be put down on, claimed again now rather than trusted
                // to the claim made at launch several seconds ago. This is the tick it has to be held:
                // a hull teleported over ground nothing holds is unloaded by Sable, which reads the
                // drive back in mid-flight and ends the warp INTERRUPTED.
                if (level instanceof ServerLevel crossingLevel) {
                    holdArrival(crossingLevel, current);
                }
                // The whole journey's one discontinuity, and it happens deep inside a throat at both
                // ends. Momentum is carried across in full: the hull is travelling at passage speed
                // along the bearing when it leaves, and both apertures face the same way, so it
                // arrives still doing exactly that. Nothing about its motion changes - only where it is.
                if (!airship.relocate(current.emergenceOrigin(), current.arrivalOrientation(), 1.0D)) {
                    abort(WarpFailure.RELOCATION_FAILED);
                    return;
                }
                // There is no way back from here, so stop keeping one. Left in place, the origin
                // turns any later recovery into a four-thousand-block tow of a ship that had already
                // arrived - which is exactly what a stalled warp used to do.
                airship.warpData().forgetOrigin();
                WarpTrace.teleport("across the fold", departure, current.emergenceOrigin(), crew.size());
                // Every client holding a copy of the hull has already been told exactly where it went,
                // and the crew that they went with it - relocate does that for any jump, because a
                // client left to read one out of Sable's pose stream draws the hull half-way across
                // and throws whoever is standing on it.
                WarpTrace.crossingNotice(
                        new Vector3d(current.emergenceOrigin()).distance(departure),
                        ClientboundFoldCrossedPacket.JUMP_BLOCKS, crew.size());
                // Only now is the entry aperture finished with; the hull was inside it until this tick.
                closeEntry(current);
                current.leftCorridor();
                // Still behind the far aperture and still inside the fold, so the wash carries on
                // until the hull is properly out the other side.
                send(crew, ClientboundCorridorPacket.enter(
                        current.transitTicks(), effectiveColour(), effectiveIntensity()));
                // The crew's first sight of the far aperture. Its open cue went to the destination while
                // they were still at the entry, far out of range, so this carries the throat and opens
                // it for them - without that the hull came out of an aperture that was not there.
                showExit(arrivalLevel(), current, crew);
                onLeftCorridor(airship);
            }
            case EMERGED -> {
                WarpTrace.stage(WarpFlight.Stage.BREACH, WarpFlight.Stage.EMERGE,
                        current.stageTicks(), airship, current);
                current.breached();
                send(airship.crew(), ClientboundCorridorPacket.leave());
                onEmerged();
            }
            case ARRIVED -> {
                WarpTrace.complete(worldPosition, airship, current.arrivalOrigin());
                if (WarpTrace.enabled()) {
                    // The angle between two unit quaternions: twice the arc-cosine of their dot product, taken
                    // absolute because q and -q are the same turn.
                    double dot = Math.min(1.0D, Math.abs(new org.joml.Quaterniond(airship.orientation())
                            .dot(new org.joml.Quaterniond(current.arrivalOrientation()))));
                    WarpTrace.attitude(worldPosition, Math.toDegrees(2.0D * Math.acos(dot)));
                }
                completeWarp();
            }
        }

        // The yeet detector: what was asked for this tick against what the hull actually did. Skipped
        // when the tick ended the flight (completed or aborted), because there is nothing left to fly.
        // No notice is re-sent to clients that start tracking the hull after the crossing: Sable sends
        // those the hull where it already is, with no jump in it to misread.
        if (flight == current) {
            WarpTrace.flightTick(tickedStage, current.lastCommanded(), airship.velocity(), ceiling);
        }
    }

    /**
     * Keeps the ground at both ends held through a corridor longer than its tickets.
     *
     * <p>The landing and the departure corridor are claimed once, as the rift opens, on tickets that lapse
     * by themselves half a minute later. That covered every warp while a corridor was a few seconds long.
     * With {@code warpTimeScaling} on it can be a minute or more, and a landing let go of part-way through
     * is ground the hull is then put down on unloaded - so both are claimed again at the interval a
     * spinning drive renews its preload at, well inside their lifespan. A warp short enough never gets
     * here.
     */
    private void renewCorridorHolds(Airship airship, WarpFlight current) {
        if (current.stage() != WarpFlight.Stage.CORRIDOR || current.stageTicks() <= 0
                || current.stageTicks() % ArrivalTicket.PRELOAD_RENEW_INTERVAL != 0) {
            return;
        }
        ServerLevel destination = arrivalLevel();
        if (destination != null) {
            holdArrival(destination, current);
        }
        if (level instanceof ServerLevel serverLevel) {
            ArrivalTicket.holdCorridor(serverLevel, airship.centre(new Vector3d()), current.entryRift().normal(),
                    LaunchClearance.launchReach(current),
                    ArrivalTicket.arrivalHalfWidth(current.crossSpan(), current.apertureMargin()),
                    current.hullSpan() * 0.5D);
        }
    }

    /**
     * Holds the arrival coast: from the hull's centre where it reappears behind the far aperture to
     * its centre where it comes to rest, as wide as the hull and its aperture.
     */
    private void holdArrival(ServerLevel destination, WarpFlight planned) {
        int chunks = ArrivalTicket.hold(destination, planned.emergenceCentre(), planned.arrivalCentre(),
                ArrivalTicket.arrivalHalfWidth(planned.crossSpan(), planned.apertureMargin()),
                planned.hullSpan() * 0.5D + AWConfig.ARRIVAL_CLEARANCE.get());
        WarpTrace.arrivalHeld(worldPosition, planned.emergenceCentre(), planned.arrivalCentre(), chunks);
    }

    /** The level the committed course ends in, or {@code null} when it no longer exists. */
    @Nullable
    private ServerLevel arrivalLevel() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return destinationDimension == null || destinationDimension.equals(serverLevel.dimension())
                ? serverLevel
                : serverLevel.getServer().getLevel(destinationDimension);
    }

    /**
     * The crossing, when the far aperture is in another dimension.
     *
     * <p>Where a same-dimension warp teleports the hull, this has Dimensional Sable rebuild it on the far
     * side - see {@link CrossDimensionWarp}. That includes this drive: the machine running the flight is
     * saved with the ship and comes back as a different block entity, at a different plot position, on a
     * ship with a different UUID. So before the save it writes down what its copy needs and cannot work out
     * for itself - where it used to be, which ship it used to be on, that ship's own data, which bearings
     * were turning - and the copy finishes the crossing on its first tick, in {@link #finishCrossing()}.
     * This one is finished the moment the crossing returns.
     *
     * <p>A crossing that is refused, or that fails, leaves the original where it was, and is aborted like
     * any other failure in the corridor: the hull is put back at its mooring.
     */
    private void crossInto(ServerLevel arrivalLevel, Airship airship, WarpFlight current, Vector3dc departure,
                           int crew) {
        // Claimed before the rebuild, so the chunks the new hull is put down in are held by the time it is.
        holdArrival(arrivalLevel, current);

        List<BlockPos> bearings = CrossDimensionWarp.runningBearings(airship);
        CompoundTag handoff = new CompoundTag();
        handoff.put("FromDrive", net.minecraft.nbt.NbtUtils.writeBlockPos(worldPosition));
        handoff.putUUID("FromShip", airship.uuid());
        // Dimensional Sable does not carry a ship's user data, and this mod's claim on the ship lives there.
        CompoundTag userData = airship.subLevel().getUserDataTag();
        if (userData != null) {
            handoff.put("UserData", userData.copy());
        }
        ListTag turning = new ListTag();
        for (BlockPos bearing : bearings) {
            turning.add(net.minecraft.nbt.NbtUtils.writeBlockPos(bearing));
        }
        handoff.put("Bearings", turning);

        // Any ground an older version of this drive forced is in the dimension being left, and only this block
        // entity, in this level, can let go of it. The holds on the ship itself need nothing: Sable drops a
        // ship's tickets as the crossing removes it, and the copy takes its own on the new one.
        releaseLegacyResidency();
        outgoingHandoff = handoff;
        WarpFailure result;
        try {
            result = CrossDimensionWarp.cross(airship, arrivalLevel, current.emergenceOrigin(), bearings);
        } finally {
            outgoingHandoff = null;
        }
        if (result.isFailure()) {
            holdTimer = 0;
            abort(result);
            return;
        }
        handedOff = true;
        AttitudeHold.release(airship.uuid());
        WarpTrace.teleport("across the fold into " + arrivalLevel.dimension().location(), departure,
                current.emergenceOrigin(), crew);
        // The entry aperture is on this side, and this is the last thing that will ever close it.
        closeEntry(current);
    }

    /**
     * Finishes a crossing on the far side: everything a same-dimension crossing does after its teleport,
     * and the repairs a rebuild needs that a teleport does not.
     *
     * <p>Run by the copy of the drive the crossing built, from what its old self wrote. The hull is sitting
     * behind the far aperture, where a teleport would have put it.
     *
     * @return {@code false} when the ship it is on cannot be found yet, so the caller should try again next
     * tick rather than fly on
     */
    private boolean finishCrossing() {
        WarpFlight current = flight;
        CompoundTag handoff = incomingHandoff;
        if (current == null || handoff == null || !(level instanceof ServerLevel here)) {
            interruptedStage = current == null ? "none" : current.stage().name();
            abort(WarpFailure.AIRSHIP_LOST);
            return true;
        }
        // Sable says which ship a machine is on by ticking it, and a copy can tick before Sable has reached
        // it. Asked outright instead, and given a few ticks, rather than aborting a flight on its first tick.
        Airship airship = airship();
        if (airship == null) {
            Airship found = Airship.containing(this);
            if (found != null) {
                airshipSubLevel = found.subLevel();
                ticksSinceAirshipTick = 0;
                airship = found;
            }
        }
        if (airship == null || !airship.isActive()) {
            if (++crossingWait <= CROSSING_WAIT_TICKS) {
                return false;
            }
            interruptedStage = current.stage().name();
            abort(WarpFailure.AIRSHIP_LOST);
            return true;
        }
        incomingHandoff = null;

        // Every block aboard moved by the same amount in the rebuild, so the drive's own move says how far
        // everything remembered in ship space has to follow it.
        BlockPos from = net.minecraft.nbt.NbtUtils.readBlockPos(handoff, "FromDrive").orElse(worldPosition);
        BlockPos moved = worldPosition.subtract(from);
        Vec3 shift = new Vec3(moved.getX(), moved.getY(), moved.getZ());
        UUID fromShip = handoff.hasUUID("FromShip") ? handoff.getUUID("FromShip") : airship.uuid();

        if (handoff.contains("UserData", Tag.TAG_COMPOUND)) {
            CompoundTag carried = handoff.getCompound("UserData").copy();
            CompoundTag fresh = airship.subLevel().getUserDataTag();
            if (fresh != null) {
                // Anything written onto the new ship already stays, unless the old one had it too.
                CompoundTag merged = fresh.copy();
                merged.merge(carried);
                carried = merged;
            }
            airship.subLevel().setUserDataTag(carried);
            airship.warpData().rebindDrive(worldPosition);
        }
        passengers.rebase(shift);
        CrewManifest.crossed(fromShip, airship.uuid(), here.dimension(), shift);
        List<BlockPos> bearings = new ArrayList<>();
        ListTag turning = handoff.getList("Bearings", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < turning.size(); i++) {
            int[] at = turning.getIntArray(i);
            if (at.length == 3) {
                bearings.add(new BlockPos(at[0], at[1], at[2]).offset(moved));
            }
        }
        CrossDimensionWarp.reassembleLater(here.dimension(), bearings);
        // This drive among them: rebuilt, it may read no rotation though its shaft is turning.
        CrossDimensionWarp.relinkLater(here.dimension(), worldPosition);
        WarpTrace.handoff(worldPosition, here.dimension().location().toString(), current.stage().name(),
                moved, fromShip, airship.uuid());

        // There is no way back from here, as on this side of any crossing.
        airship.warpData().forgetOrigin();
        holdArrival(here, current);
        // The flight hold is taken on this new ship by tickHolds, later in this same tick.
        holdTimer = 0;
        // A flight into a Berth turns the hull at the crossing, and the rebuild that brought it here takes no
        // attitude: it is lying behind the far rift as it left. Turned now, where it lies, deep in the throat -
        // by relocate, so that everyone aboard is carried round with the deck rather than left where it was.
        if (current.turns() && !airship.relocate(current.emergenceOrigin(), current.arrivalOrientation(), 1.0D)) {
            Trace.log(Trace.BERTH, "{} could not turn the rebuilt hull to its berth's facing: it arrives as it left",
                    Trace.pos(worldPosition));
        }
        current.leftCorridor();

        // The crew have just changed dimension, which starts their client on a fresh world with nothing
        // drawn in it: the wash has to be sent again, and the transit cue opens the far aperture for them.
        // Not an open cue as well - anyone already here was sent one as the corridor began, and a second
        // would stand a second aperture over the first, playing its opening from the start.
        List<ServerPlayer> crew = airship.crew();
        send(crew, ClientboundCorridorPacket.enter(current.transitTicks(), effectiveColour(), effectiveIntensity()));
        showExit(here, current, crew);
        onLeftCorridor(airship);
        markStateDirty();
        return true;
    }

    /**
     * The hull is out of the corridor and sitting behind the far aperture.
     *
     * <p>The charge is spent here rather than when it comes into view, because from this moment the
     * journey cannot be undone: the hull is at the destination whether or not it has flown out yet.
     * Dropping to ARRIVING also lifts the minimum-RPM guard, so a drive that loses its shaft during
     * the last few seconds sets its ship down rather than aborting it into the fold.
     */
    private void onLeftCorridor(Airship airship) {
        charge = Math.max(0.0F, charge - committedCost);
        sequenceTicks = 0;
        transition(RiftDriveState.ARRIVING);

        WarpFlight current = flight;
        if (current != null) {
            destinationPos = new Vec3(current.arrivalOrigin().x(), current.arrivalOrigin().y(),
                    current.arrivalOrigin().z());
        }

        if (level instanceof ServerLevel serverLevel && destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor != null) {
                ServerLevel destination = serverLevel.getServer().getLevel(anchor.dimension());
                if (destination != null && destination.isLoaded(anchor.pos())
                        && destination.getBlockEntity(anchor.pos()) instanceof WarpAnchorBlockEntity anchorBe) {
                    anchorBe.onAirshipArrived();
                }
                AeroWarptics.LOGGER.debug("{} came out of the rift at anchor {}", airship, anchor.displayName());
            }
        }
    }

    /**
     * The hull is clear of the far aperture and back in the world.
     *
     * <p>This is the moment anyone standing at the destination actually sees a ship, so it is where
     * the arrival is announced. Everything that had to happen for the warp to count already happened
     * a few seconds ago, behind the aperture.
     */
    private void onEmerged() {
        playSound(AWSounds.WARP_EXIT.get(), 1.3F, 1.0F);
        WarpFlight current = flight;
        if (current != null) {
            // The fleet's one exit goes when the last ship is out of it, with the whole fleet's shockwave.
            if (foldPassage == null || foldOrder == null || !(level instanceof ServerLevel serverLevel)
                    || FleetFolds.outOfExit(serverLevel, foldOrder)) {
                broadcastRift(ClientboundWarpEffectPacket.Stage.WARP_EXIT, cueExit(current), 0);
            }
            // Measured from the hull where it now is, so a long ship whose bow comes out beside a
            // detector is heard as arriving beside it. Only without a hull to measure does the rift
            // itself stand in for it.
            Airship airship = airship();
            WarpFlight.Rift exit = current.exitRift();
            AABB arrival = airship != null && airship.isActive()
                    ? airship.assemblyBounds().toMojang()
                    : AABB.ofSize(new Vec3(exit.centre().x(), exit.centre().y(), exit.centre().z()),
                            exit.radius() * 2.0D, exit.radius() * 2.0D, exit.radius() * 2.0D);
            SpatialWaveDetectorBlockEntity.announce(level, arrival);
        }
    }

    /** The hull has settled: verify, release the airship, and cool down. */
    private void completeWarp() {
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }

        // What state the landing is actually in as the hull settles onto it: whether its chunks are
        // loaded, and whether anything other than the warp's own lapsing ticket will keep them so.
        // This is the trace that tells a server admin why a ship arrived and then vanished.
        if (level instanceof ServerLevel arrivalLevel && flight != null) {
            Vector3dc arrival = flight.arrivalOrigin();
            boolean loaded = arrivalLevel.isLoaded(BlockPos.containing(arrival.x(), arrival.y(), arrival.z()));
            boolean durable = ArrivalTicket.isLandingDurable(arrivalLevel, arrival);
            WarpTrace.arrival(worldPosition, loaded, loaded, durable);
        }

        if (level instanceof ServerLevel serverLevel && destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor != null) {
                notifyAnchor(serverLevel, anchor, false);
            }
        }

        // A cruise crossed no fold, so it sheds nothing for a siphon and is not a warp to the advancements.
        // And it has already braked to a stop under its own ship's lift, which a hard reset would undo.
        boolean cruised = cruiseFlying();
        if (!cruised) {
            // Whatever the fold sheds on the way through, the ship's siphons catch here - after the
            // journey is unambiguously finished, so an aborted warp yields nothing.
            if (level instanceof ServerLevel harvestLevel) {
                SpatialSiphonBlockEntity.harvest(airship, committedDistance, harvestLevel.getRandom());
            }

            // Everyone aboard, not just whoever pressed the button - and before committedDistance is
            // cleared below, because that is the only record of how far the ship actually went.
            AWCriteria.warpCompleted(airship.crew(), committedDistance, tier, destinationAnchor == null);

            airship.driveVelocity(new Vector3d());
        }
        passengers.settle(airship);
        CrewManifest.close(airship.uuid());
        AttitudeHold.release(airship.uuid());
        // The ship is in its slip: the berth takes it from here, and holds it from the server's own tick.
        if (berthId != null && level instanceof ServerLevel berthLevel) {
            uk.co.iceconchy.aerowarptics.berth.Berths.moored(berthLevel.getServer(), berthId, airship);
            berthId = null;
        }
        if (foldOrder != null && level instanceof ServerLevel foldLevel) {
            FleetFolds.arrived(foldLevel, foldOrder, findCoupler(), airship, foldEcho);
        }
        clearFold();
        // Fixed now, at the moment the warp completes: a storm that passes while the drive cools does
        // not lengthen it again, and one that arrives part-way through does not shorten it. The same goes
        // for what cools the drive - a vent started half-way through a cooldown is in time for the next.
        int cooldown = cool(RiftStorm.cooldownTicks(level, tier));
        airship.warpData().release(false, level.getGameTime() + cooldown);
        flight = null;
        destinationAnchor = null;
        destinationPos = null;
        destinationDimension = null;
        destinationLabel = "";
        initiator = null;
        committedCost = 0.0F;
        committedDistance = 0.0D;
        sequenceTicks = 0;
        clearCruise();
        cooldownTicks = cooldown;
        cooldownTotal = cooldown;
        // Re-assert the holds now, at the settled position, rather than waiting out the renewal interval:
        // residency counts any later wandering from where the hull actually came to rest.
        holdTimer = 0;
        transition(RiftDriveState.COOLDOWN);
        playSound(AWSounds.DRIVE_COOLDOWN.get(), 0.8F, 1.0F);
    }

    /**
     * Abandons the warp.
     *
     * <p>The airship is never damaged or destroyed by a failure: the worst default consequence is a
     * lost fraction of charge plus a spell in the error state. The optional
     * {@code failure.dangerousFailures} setting adds a shove, and nothing more.
     */
    public void abort(WarpFailure reason) {
        if (WarpTrace.enabled()) {
            WarpTrace.abort(worldPosition, reason, flight, sequenceTicks, traceState());
        }
        Airship airship = airship();
        if (airship != null) {
            // Hand the helm back and, if the hull is out in the corridor, bring it home.
            airship.driveVelocity(new Vector3d());
            send(airship.crew(), ClientboundCorridorPacket.leave());
            send(airship.crew(), ClientboundSupercruisePacket.leave());
            AirshipWarpData data = airship.warpData();
            // Only a hull that has actually been moved needs putting back. A run at the aperture, or
            // a passage through one, both happen where the ship already was.
            if (flight != null && flight.stage() != WarpFlight.Stage.APPROACH
                    && flight.stage() != WarpFlight.Stage.TRANSIT) {
                data.restoreOrigin(airship);
            }
            if (flight != null && foldPassage == null) {
                // Whichever apertures this warp tore open, they have nothing left to do - the far one in
                // the level it was opened in, which before a cross-dimension crossing is not this one.
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, flight.entryRift(), 0);
                broadcastRift(arrivalLevel(), ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, flight.exitRift(),
                        0, 0.0D, List.of());
            }
            if (data.isOwnedBy(worldPosition)) {
                data.release(true, level == null ? 0L : level.getGameTime());
            }
            if (reason.penalised() && AWConfig.DANGEROUS_FAILURES.get() && level instanceof ServerLevel) {
                double magnitude = AWConfig.DANGEROUS_FAILURE_IMPULSE.get() * Math.max(1.0D, airship.mass()) * 0.01D;
                airship.applyImpulse(new Vector3d(0.0D, magnitude, 0.0D), new Vector3d());
            }
            // After the hull has been put back, so anybody who came off inside the fold is returned to
            // where the ship ended up rather than to the coordinates of a rift that no longer exists.
            passengers.settle(airship);
            CrewManifest.close(airship.uuid());
            AttitudeHold.release(airship.uuid());
        }

        if (level instanceof ServerLevel serverLevel && destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor != null) {
                notifyAnchor(serverLevel, anchor, false);
            }
        }

        releaseBerth();

        // A creative drive pays nothing, for a failure as for a jump - see WarpCost.Formula.free.
        if (reason.penalised() && !tier.creative()) {
            charge = Math.max(0.0F, charge - (float) (double) AWConfig.FAILURE_CHARGE_PENALTY.get());
        }
        notifyInitiator(reason);
        if (foldOrder != null && level instanceof ServerLevel foldLevel) {
            if (foldPassage != null) {
                // The fleet's rift is not this ship's to close while anybody else is still to go through it.
                // Whatever end of it was waiting only for this ship closes now; the rest wait for the others.
                // Asked whether or not this drive's flight ever began: an escort refused at its own rift
                // was still one of the ships the fleet's was waiting for.
                FoldLog.Closing closing = FleetFolds.leave(foldLevel, foldOrder);
                if (closing.entry()) {
                    broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, foldPassage.entry(), 0);
                }
                if (closing.exit() && foldPassage.exit() != null) {
                    broadcastRift(arrivalLevel(), ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, foldPassage.exit(),
                            0, 0.0D, List.of());
                }
            }
            FleetFolds.failed(foldLevel, foldOrder);
        }
        clearFold();
        flight = null;
        destinationAnchor = null;
        destinationPos = null;
        destinationDimension = null;
        destinationLabel = "";
        initiator = null;
        committedCost = 0.0F;
        committedDistance = 0.0D;
        sequenceTicks = 0;
        clearCruise();
        lastFailure = reason;
        errorTicks = AWConfig.FAILURE_COOLDOWN_TICKS.get();
        forceState(RiftDriveState.ERROR);
        playSound(AWSounds.WARP_FAILED.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.FAILED);
    }

    /** Forgets everything about a cruise, finished or not. Harmless on a waypoint jump. */
    private void clearCruise() {
        cruisePhase = CruisePhase.NONE;
        cruiseDisengage = false;
        cruiseSpeed = 0.0D;
        cruiseSafeSpeed = 0.0D;
        cruiseSight = CruiseSensor.Limit.CLEAR;
        cruiseDistance = 0.0D;
        cruiseStartCharge = 0.0F;
        cruiseLastCentre = null;
    }

    private WarpFailure reject(WarpFailure failure) {
        if (WarpTrace.enabled()) {
            WarpTrace.refused(worldPosition, failure, traceState());
        }
        lastFailure = failure;
        markStateDirty();
        return failure;
    }

    /**
     * Refuses a vessel that something ties to what the warp would leave behind - see {@link Fastenings}.
     *
     * <p>Asked whether {@link #clearanceChecks} is on or not. That switch skips the proofs about terrain -
     * whether the way ahead is clear - and this is not one of them: a hull held to the ground cannot fly its
     * run at the rift at all, whatever the pilot thinks of the clearance system, and one teleported with the
     * joint still holding is dragged back across the whole warp the moment it lets go.
     *
     * <p>Told to everyone with a reason to hear it rather than only traced. A lever pulled at a tied-down
     * ship has nothing else to say why nothing happened, and this is the one refusal the crew can put right
     * where they stand.
     *
     * @param player whoever commanded it, if anyone did
     * @param author whoever set it going otherwise - the course's author, the cruise's, the fold's
     * @return {@link WarpFailure#SHIP_FASTENED}, or {@link WarpFailure#NONE} when nothing holds it
     */
    private WarpFailure fastened(Airship airship, @Nullable ServerPlayer player, @Nullable UUID author) {
        List<Fastening> found = Fastenings.of(airship);
        Fastening holding = Fastening.worst(found).orElse(null);
        lastFastening = holding;
        if (holding == null) {
            return WarpFailure.NONE;
        }
        Trace.log(Trace.LAUNCH, "{} {} is tied down by {} - everything found: {}", Trace.pos(worldPosition),
                airship, holding, found);

        List<ServerPlayer> audience = new ArrayList<>();
        if (player != null) {
            audience.add(player);
        }
        ServerPlayer authorOnline = author == null ? null
                : airship.level().getServer().getPlayerList().getPlayer(author);
        if (authorOnline != null) {
            audience.add(authorOnline);
        }
        for (Airship member : airship.vessel()) {
            audience.addAll(member.crew());
        }
        ClientboundWarpFeedbackPacket notice = ClientboundWarpFeedbackPacket.of(WarpFailure.SHIP_FASTENED, holding);
        Set<UUID> told = new HashSet<>();
        for (ServerPlayer listener : audience) {
            if (told.add(listener.getUUID())) {
                AWNetwork.sendTo(listener, notice);
            }
        }
        return WarpFailure.SHIP_FASTENED;
    }

    /**
     * Whether some other drive is flying a part of this vessel.
     *
     * <p>Every part goes where the hull goes, so a ship docked to another that has a drive of its own must
     * not have both drives fly the pair at once. Asked both ways round with nothing extra to keep: the other
     * drive's ship sees this hull as one of its own parts, and this hull's claim is what it finds there.
     */
    private static boolean partWarping(Airship airship) {
        for (ServerSubLevel part : airship.parts()) {
            if (Airship.of(part).warpData().isWarping()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whoever ordered the warp why it stopped.
     *
     * <p>A sequence can abort several seconds after the console was closed, so the reason has to reach
     * the pilot rather than only appearing on the machine.
     */
    private void notifyInitiator(WarpFailure reason) {
        if (initiator == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(initiator);
        if (player != null) {
            AWNetwork.sendTo(player, ClientboundWarpFeedbackPacket.of(reason,
                    reason == WarpFailure.SHIP_FASTENED ? lastFastening : null));
        }
    }

    private void notifyAnchor(ServerLevel serverLevel, WarpAnchor anchor, boolean locked) {
        ServerLevel anchorLevel = serverLevel.getServer().getLevel(anchor.dimension());
        if (anchorLevel == null || !anchorLevel.isLoaded(anchor.pos())) {
            return;
        }
        if (anchorLevel.getBlockEntity(anchor.pos()) instanceof WarpAnchorBlockEntity be) {
            if (locked) {
                be.onDestinationLocked();
            } else {
                be.onDestinationReleased();
            }
        }
    }

    // ------------------------------------------------------------- transitions

    /** Applies a transition if the state machine allows it, otherwise logs and stays put. */
    private void transition(RiftDriveState next) {
        if (state == next) {
            return;
        }
        if (!state.canTransitionTo(next)) {
            AeroWarptics.LOGGER.warn("Rejected illegal Rift Drive transition {} -> {} at {}", state, next, worldPosition);
            return;
        }
        WarpTrace.state(worldPosition, state.name(), next.name(), charge, Math.abs(getSpeed()), sequenceTicks);
        state = next;
        markStateDirty();
    }

    /** Used for the error path, which every state is allowed to enter. */
    private void forceState(RiftDriveState next) {
        if (state != next) {
            WarpTrace.state(worldPosition, state.name(), next.name(), charge, Math.abs(getSpeed()), sequenceTicks);
        }
        state = next;
        markStateDirty();
    }

    /**
     * This drive's whole situation on one line, for the trace: the machine, the ship under it, the course
     * it is set to and what that course would cost from here.
     *
     * <p>Written into every refusal and every abort, because each of those used to name a reason and
     * nothing else, and most reasons have several causes. "Insufficient power" is a shaft below the
     * minimum or a belt that slipped for a tick; "beyond drive range" is an anchor past the tier's range or
     * a hull too heavy to reach it on one charge; "already warping" is a busy ship or a lock left by a drive
     * that has since been broken. The numbers here say which.
     *
     * <p>Server-side only, and not cheap - it walks the crew and prices the course - so it is only built
     * behind a check that tracing is on.
     */
    public String traceState() {
        StringBuilder out = new StringBuilder(256);
        out.append(tier.getSerializedName()).append(' ').append(state.name())
                .append(" mode=").append(mode.name())
                .append(" charge=").append(Trace.percent(charge))
                .append(" rpm=").append(Trace.num(Math.abs(getSpeed())))
                .append(" (needs ").append(tier.minimumRpm()).append(", full rate at ").append(tier.optimalRpm()).append(')')
                .append(" checks=").append(clearanceChecks ? "on" : "OFF")
                .append(" bow=").append(heading.name());
        if (state == RiftDriveState.COOLDOWN) {
            out.append(" cooldown=").append(cooldownTicks).append('t');
        }
        if (state == RiftDriveState.ERROR) {
            out.append(" error=").append(errorTicks).append('t');
        }
        if (lastFailure.isFailure()) {
            out.append(" lastFailure=").append(lastFailure.getSerializedName());
        }
        out.append(" course=").append(describeCourse(standingCourse));
        if (standingCourse != null) {
            out.append(" set by ").append(Trace.who(standingAuthor));
        }
        if (mode == DriveMode.FREE_CRUISE) {
            out.append(" cruiseAuthor=").append(Trace.who(cruiseAuthor))
                    .append(" throttle=").append(Trace.num(cruiseThrottle));
        }
        if (foldOrder != null) {
            out.append(" fold=").append(foldOrder.lead() ? "flagship" : "escort")
                    .append(foldOrder.lead() || foldReleased ? "" : " (waiting for the flagship)");
        }
        if (state.isSequenceRunning()) {
            out.append(" committed=\"").append(destinationLabel).append("\" ")
                    .append(Trace.vec(destinationPos)).append(" distance=").append(Trace.blocks(committedDistance))
                    .append(" cost=").append(Trace.percent(committedCost))
                    .append(" spin=").append(Trace.num(spinProgress)).append('/').append(Trace.num(spinRequired));
        }

        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return out.append(" ship=NONE (not aboard an assembled airship)").toString();
        }
        double mass = WarpValidator.effectiveMass(airship);
        dev.ryanhcode.sable.companion.math.BoundingBox3dc box = airship.worldBounds();
        out.append(" | ship=").append(airship.uuid())
                .append(airship.name() == null || airship.name().isEmpty() ? "" : " \"" + airship.name() + "\"")
                .append(" in ").append(airship.level().dimension().location())
                .append(" mass=").append(Trace.blocks(mass)).append(airship.mass() > 0.0D ? "" : " (volume stand-in)")
                .append(" centre=").append(Trace.vec(airship.centre(new Vector3d())))
                .append(" size=").append(Trace.blocks(box.width())).append('x')
                .append(Trace.blocks(box.maxY() - box.minY())).append('x').append(Trace.blocks(box.length()))
                .append(" crew=").append(airship.crew().size())
                .append(" parts=").append(traceParts(airship))
                .append(" lock=").append(level == null ? "?" : airship.warpData().describe(level.getGameTime()));
        if (standingCourse != null && level instanceof ServerLevel serverLevel) {
            out.append(" | ").append(tracePricing(serverLevel, airship, standingCourse, mass));
        }
        return out.toString();
    }

    /**
     * How many sub-levels this vessel carries besides its hull, and whether Sable's own walk agrees. The two
     * differing is a turret or a gondola Sable's list of actors has lost track of, which before
     * {@code Airship.chain} was a ship that read its own turret as another ship in its way.
     */
    private static String traceParts(Airship airship) {
        int parts = airship.parts().size();
        int sable = airship.sableParts();
        return sable == parts ? Integer.toString(parts) : parts + " (Sable's own chain finds " + sable + ")";
    }

    /** The standing course as the trace names it. */
    private static String describeCourse(@Nullable WarpCourse course) {
        if (course == null) {
            return "none";
        }
        return (course.isAnchor() ? "anchor " + course.anchorId() : "fix " + Trace.pos(course.fix())
                + (course.dimension() == null ? "" : " in " + course.dimension().location()))
                + " \"" + course.label() + "\"" + (course.holdAltitude() ? " holding altitude" : "");
    }

    /** What the standing course costs from where the ship is now, and whether the rules would allow it. */
    private String tracePricing(ServerLevel serverLevel, Airship airship, WarpCourse course, double mass) {
        WarpCost.Formula formula = WarpCost.fromConfig(tier);
        if (course.isAnchor()) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(course.anchorId());
            if (anchor == null) {
                return "course anchor no longer exists";
            }
            double distance = WarpValidator.warpDistance(airship, anchor);
            return "to " + Trace.pos(anchor.pos()) + " in " + anchor.dimension().location()
                    + (anchor.enabled() ? "" : " (DISABLED)") + ": "
                    + WarpTrace.pricing(formula, distance, mass)
                    + " -> " + WarpValidator.validateDestination(airship, anchor, tier, charge).getSerializedName();
        }
        double distance = WarpValidator.fixDistance(airship, course);
        return "to " + Trace.pos(course.fix())
                + (course.dimension() == null ? "" : " in " + course.dimension().location()) + ": "
                + WarpTrace.pricing(formula, distance, mass)
                + " -> " + WarpValidator.validateFix(airship, course, tier, charge).getSerializedName();
    }

    private void markStateDirty() {
        stateDirty = true;
        setChanged();
    }

    // -------------------------------------------------------------- accessors

    public RiftDriveTier tier() {
        return tier;
    }

    /** The cube of Drive Extenders this drive is built into - just the drive, when it is on its own. */
    public DriveStructure.Frame frame() {
        return new DriveStructure.Frame(DriveStructure.fromStep(worldPosition, frameStep), frameSize);
    }

    /**
     * Records the frame this drive is built into. Only {@link DriveAssembly} calls this, and only after
     * it has told every cell of the frame the same thing.
     */
    public void setFrame(DriveStructure.Frame frame) {
        BlockPos step = DriveStructure.step(worldPosition, frame.origin());
        if (frameSize == frame.size() && frameStep.equals(step)) {
            return;
        }
        frameStep = step;
        frameSize = frame.size();
        // A module bolted to an extender that has just joined or left is a module this drive has or has
        // not got as of now, not as of the next tick.
        cachedModulatorTick = Long.MIN_VALUE;
        markStateDirty();
    }

    public RiftDriveState state() {
        return state;
    }

    public float charge() {
        return charge;
    }

    /**
     * Eases the drawn needle towards the setting.
     *
     * <p>Client-side dressing only. The angle is unwrapped to the nearest equivalent first, so a
     * turn from left to forward swings the short way round rather than three quarters back.
     */
    private void tickNeedle() {
        float target = heading.needleRadians();
        if (Float.isNaN(needleAngle)) {
            needleAngle = target;
            needleAngleLast = target;
            return;
        }
        int turns = Mth.floor((target - needleAngle) / Mth.TWO_PI + 0.5F);
        needleAngle += turns * Mth.TWO_PI;
        needleAngleLast = needleAngle;
        needleAngle += (target - needleAngle) * 0.25F;
    }

    /** The needle's angle for this frame, in radians about the model's Y axis. */
    public float needleAngle(float partialTicks) {
        if (Float.isNaN(needleAngle)) {
            return heading.needleRadians();
        }
        return Mth.lerp(partialTicks, needleAngleLast, needleAngle);
    }

    public float chargePartial(float partialTicks) {
        return level != null && level.isClientSide
                ? clientChargeLast + (clientCharge - clientChargeLast) * partialTicks
                : charge;
    }

    public WarpFailure lastFailure() {
        return lastFailure;
    }

    /** What tied the ship down when this drive was last refused for it, if that is why it was refused. */
    @Nullable
    public Fastening lastFastening() {
        return lastFailure == WarpFailure.SHIP_FASTENED ? lastFastening : null;
    }

    public DriveHeading heading() {
        return heading;
    }

    public DriveMode mode() {
        return mode;
    }

    /** Whether this drive checks for terrain before a warp - see {@link #clearanceChecks}. */
    public boolean clearanceChecks() {
        return clearanceChecks;
    }

    public CruisePhase cruisePhase() {
        return cruisePhase;
    }

    /** How far the cruise in flight has come, in blocks. The client reads the synced figure. */
    public double cruiseDistance() {
        return level != null && level.isClientSide ? syncedCruiseDistance : cruiseDistance;
    }

    /**
     * How far a cruise may fly on its charge, in blocks - the one in flight, or the next one this drive
     * would engage. The client reads the synced figure.
     */
    public double cruiseReach() {
        if (level != null && level.isClientSide) {
            return syncedCruiseLimit;
        }
        Airship airship = airship();
        return airship == null ? 0.0D : cruiseLimit(airship);
    }

    /** How fast the cruise in flight is going, in blocks per second. The client reads the synced figure. */
    public double cruiseSpeedPerSecond() {
        return level != null && level.isClientSide ? syncedCruiseSpeed : cruiseSpeed * FreeCruise.TICKS_PER_SECOND;
    }

    /** What the cruise in flight can see ahead - why it is slower than its throttle, when it is. */
    public CruiseSensor.Limit cruiseSight() {
        return level != null && level.isClientSide
                ? CruiseSensor.Limit.values()[Math.max(0, Math.min(CruiseSensor.Limit.values().length - 1,
                        syncedCruiseSight))]
                : cruiseSight;
    }

    /**
     * Whether a drive in cruise mode could engage on the charge it has, as far as cost goes.
     * Server-side, for the console.
     */
    public WarpFailure cruiseAffordability() {
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            return WarpFailure.NO_AIRSHIP;
        }
        return FreeCruise.canEngage(WarpCost.fromConfig(tier), WarpValidator.effectiveMass(airship), charge);
    }

    /** The bow in the airship's own frame: the drive's face, turned by the pilot's setting. */
    private Direction shipSpaceBow() {
        return WarpFlight.shipSpaceBow(heading, getBlockState().getValue(RiftDriveBlock.FACING));
    }

    /**
     * The same bow, for a Rift Coupler measuring its formation from it. Always horizontal in ship space,
     * which is what lets a slot be "ahead" and "to starboard" whatever the drive is mounted on.
     */
    public Direction shipBow() {
        return shipSpaceBow();
    }

    /**
     * Where that bow happens to be pointing in the world at this moment, as a compass point.
     *
     * <p>Purely a readout. The setting itself is relative to the hull, so this changes as the vessel
     * turns; it is shown in the console only so a pilot can sanity-check the needle against the
     * landscape before committing.
     */
    public String resolvedBearing(@Nullable Airship airship) {
        if (airship == null || !airship.isActive()) {
            return "";
        }
        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        return Direction.getNearest(bow.x, 0.0D, bow.z).getName();
    }

    /**
     * Turns the bow setting a quarter turn clockwise, and with it the needle on the machine.
     *
     * <p>Refused mid-sequence: the flight plan is built from the heading when the warp starts, so
     * changing it halfway would leave the rift and the ship disagreeing about which way is forward.
     */
    public boolean cycleHeading(ServerPlayer player) {
        if (WarpValidator.validatePlayer(player, this).isFailure() || state.isSequenceRunning()) {
            return false;
        }
        heading = heading.next();
        markStateDirty();
        return true;
    }

    @Nullable
    public UUID destinationAnchor() {
        return destinationAnchor;
    }

    /**
     * What to call the committed destination.
     *
     * <p>A fix has no anchor to ask for its name, and an anchor can be renamed while a ship is on its
     * way, so the label the pilot chose is carried along with the course rather than looked up.
     */
    @Nullable
    public String destinationLabel() {
        return destinationLabel != null && !destinationLabel.isEmpty() ? destinationLabel : null;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** How long the drive's current phase lasts, in ticks. Server-side config lookup. */
    private int phaseDuration() {
        return switch (state) {
            case DESTINATION_SELECTED -> LOCK_IN_TICKS;
            // Spin is not measured in ticks any more - see sequenceProgress.
            case STABILIZING -> 0;
            case WARPING -> flight != null ? flight.corridorTicks() : corridorTicks();
            case ARRIVING -> AWConfig.RIFT_TRANSIT_TICKS.get() + tier.arriveTicks();
            case COOLDOWN -> Math.max(1, cooldownTotal >= 0 ? cooldownTotal : tier.cooldownTicks());
            case ERROR -> Math.max(1, AWConfig.FAILURE_COOLDOWN_TICKS.get());
            default -> 0;
        };
    }

    /** 0..1 progress through whichever timed phase the drive is in. */
    public float sequenceProgress() {
        // Spinning up is measured in spin, not in ticks: the same jump takes a different number of
        // ticks depending on how hard the drive is being turned, so a tick count would be a lie.
        if (state == RiftDriveState.STABILIZING) {
            return spinRequired <= 0.0F ? 0.0F : Math.min(1.0F, spinProgress / spinRequired);
        }
        int total = level != null && level.isClientSide ? syncedPhaseDuration : phaseDuration();
        if (total <= 0) {
            return 0.0F;
        }
        int elapsed = state == RiftDriveState.COOLDOWN ? total - cooldownTicks
                : state == RiftDriveState.ERROR ? total - errorTicks
                : sequenceTicks;
        return Math.max(0.0F, Math.min(1.0F, elapsed / (float) total));
    }

    public boolean isRunningFastEnough() {
        // Losing the shaft mid-warp aborts an ordinary drive into its error state. That is a penalty,
        // and a creative drive is defined by not having any.
        return tier.creative() || Math.abs(getSpeed()) >= requiredRpm();
    }

    /** Which leg of the journey the hull is on, as the client sees it. Empty when not flying. */
    public String flightStage() {
        return clientFlightStage;
    }

    /** 0..1 through the current leg, as the client sees it. */
    public float flightProgress() {
        return clientFlightProgress;
    }

    /** The tier's minimum RPM, taken from the synced value when running on a client. */
    public int requiredRpm() {
        return level != null && level.isClientSide ? syncedMinimumRpm : tier.minimumRpm();
    }

    // ------------------------------------------------------------ create hooks

    /**
     * Create asks for this on both sides - goggle tooltips are drawn client-side - so the client
     * answers from the synced value rather than from the server-only config.
     */
    @Override
    public float calculateStressApplied() {
        if (level != null && level.isClientSide) {
            return syncedStressImpact;
        }
        float impact = (float) tier.stressImpact();
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void remove() {
        if (handedOff || resumeAfterCrossing) {
            // The old half of a crossing, going with the hull it left; or a copy built for a crossing that
            // was then called off. Neither has a warp of its own to abort or ground of its own to release.
            super.remove();
            return;
        }
        if (level != null && !level.isClientSide) {
            if (state.isSequenceRunning()) {
                abort(WarpFailure.INTERRUPTED);
            }
            // Let go of the ship - but only on a genuine removal. On a break the world block is already
            // gone (air) where the cached state still names the drive; on a server shutdown the block is
            // still ours and the holds must survive to disk, or the ship would not load back in.
            // Distinguishing the two is exactly that blockstate mismatch. Missed some other way, the holds
            // lapse by themselves - see HoldUpkeep.
            if (level.getBlockState(worldPosition).getBlock() != getBlockState().getBlock()) {
                releaseLegacyResidency();
                releaseHolds();
            }
        }
        super.remove();
    }

    /** The model is drawn over the whole frame, so the box that decides whether it is in view has to cover it. */
    @Override
    public AABB getRenderBoundingBox() {
        return frame().bounds().inflate(3.0D);
    }

    /**
     * Picks up after a warp that was dropped by a reload.
     *
     * <p>Everything an abort does for the crew, and nothing it does for the hull. Where the ship
     * ended up when the sequence died is where it is: the flight plan that would say otherwise has
     * already been thrown away, and moving a hull on the strength of a plan nobody has any more is a
     * guess with a whole airship riding on it.
     *
     * <p>The manifest does not survive a reload either, so a passenger who had already come off
     * cannot be put back. What is left is worth doing anyway - the crew get their corridor overlay
     * taken away, their borrowed momentum returned and their fall reset, instead of being left
     * looking at a wash that is never going to end.
     */
    private void recoverFromInterruption() {
        WarpTrace.interrupted(worldPosition, interruptedStage, sequenceTicks);
        interruptedStage = "";
        Airship airship = airship();
        if (airship == null) {
            return;
        }
        airship.driveVelocity(new Vector3d());
        send(airship.crew(), ClientboundCorridorPacket.leave());
        send(airship.crew(), ClientboundSupercruisePacket.leave());
        passengers.settle(airship);
        AttitudeHold.release(airship.uuid());
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("State", state.ordinal());
        tag.putFloat("Charge", charge);
        tag.putFloat("CommittedCost", committedCost);
        tag.putDouble("CommittedDistance", committedDistance);
        tag.putInt("SequenceTicks", sequenceTicks);
        tag.putBoolean("Powered", redstonePowered);
        if (standingCourse != null) {
            tag.put("StandingCourse", standingCourse.save());
        }
        if (standingAuthor != null) {
            tag.putUUID("StandingAuthor", standingAuthor);
        }
        tag.putFloat("SpinRequired", spinRequired);
        tag.putFloat("SpinProgress", spinProgress);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putInt("CooldownTotal", cooldownTotal);
        tag.putInt("ErrorTicks", errorTicks);
        tag.putInt("LastFailure", lastFailure.ordinal());
        tag.putInt("Heading", heading.ordinal());
        tag.putInt("Mode", mode.ordinal());
        tag.putBoolean("ClearanceChecks", clearanceChecks);
        if (frameSize > DriveStructure.MIN_SIZE) {
            tag.put("FrameStep", net.minecraft.nbt.NbtUtils.writeBlockPos(frameStep));
            tag.putInt("FrameSize", frameSize);
        }
        if (cruiseAuthor != null && !clientPacket) {
            tag.putUUID("CruiseAuthor", cruiseAuthor);
        }
        // For the client, which draws the odometer. A server reading it back mid-sequence clears it along
        // with the rest of the interrupted warp - see read.
        tag.putInt("CruisePhase", cruisePhase.ordinal());
        if (destinationAnchor != null) {
            tag.putUUID("Anchor", destinationAnchor);
        }
        if (destinationPos != null) {
            tag.putDouble("TargetX", destinationPos.x);
            tag.putDouble("TargetY", destinationPos.y);
            tag.putDouble("TargetZ", destinationPos.z);
            tag.putInt("TargetArrivalHeight", destinationArrivalHeight);
            tag.putBoolean("TargetHoldAltitude", destinationHoldAltitude);
        }
        tag.putString("DestinationLabel", destinationLabel);
        if (destinationDimension != null && !clientPacket) {
            tag.putString("DestinationDimension", destinationDimension.location().toString());
        }
        if (outgoingHandoff != null && !clientPacket) {
            // Read by the copy of this drive a crossing is about to build - see read.
            tag.put("CrossingHandoff", outgoingHandoff);
        }
        if (initiator != null) {
            tag.putUUID("Initiator", initiator);
        }
        if (berthId != null) {
            tag.putUUID("Berth", berthId);
        }
        if (foldOrder != null && !clientPacket) {
            // For a crossing's rebuilt copy above all: an escort mid-fold is still one on the far side.
            tag.put("FoldOrder", foldOrder.save());
            tag.putBoolean("FoldReleased", foldReleased);
            if (foldArrival != null) {
                tag.putDouble("FoldArrivalX", foldArrival.x);
                tag.putDouble("FoldArrivalY", foldArrival.y);
                tag.putDouble("FoldArrivalZ", foldArrival.z);
            }
            tag.putInt("FoldExtra", foldExtraCorridor);
            tag.putBoolean("FoldEcho", foldEcho);
            if (foldPassage != null) {
                tag.put("FoldPassage", foldPassage.save());
            }
        }
        if (flight != null && !clientPacket) {
            tag.put("Flight", flight.save(registries));
            // Seats are saved alongside the flight so that an interrupted warp can recover its
            // passengers after a restart. Without this, the recovery path has nobody to put back.
            tag.put("PassengerSeats", passengers.save());
        }
        if (!clientPacket && driveId != null) {
            tag.putUUID("DriveId", driveId);
        }
        if (!clientPacket && !forcedChunks.isEmpty()) {
            // Ground an older version of this drive forced and has not yet let go of: kept until it does,
            // so a save in between still knows they were ours.
            long[] forced = new long[forcedChunks.size()];
            int i = 0;
            for (long chunk : forcedChunks) {
                forced[i++] = chunk;
            }
            tag.putLongArray("ForcedChunks", forced);
        }
        if (clientPacket && flight != null) {
            tag.putString("FlightStage", flight.stage().name());
            tag.putFloat("FlightProgress", flight.stageProgress());
        }
        if (clientPacket) {
            tag.putFloat("SpinRate", (float) spinRate());
            tag.putInt("MinRpm", tier.minimumRpm());
            tag.putInt("PhaseDuration", phaseDuration());
            tag.putFloat("StressImpact", (float) tier.stressImpact());
            tag.putInt("CoolingVents", syncedCoolingVents);
            tag.putInt("CoolingIce", syncedCoolingIce);
            tag.putFloat("CoolingShare", syncedCoolingShare);
            tag.putBoolean("AwaitingLanding", syncedAwaitingLanding);
            Airship cruising = cruiseFlying() ? airship() : null;
            tag.putFloat("CruiseDistance", (float) cruiseDistance);
            tag.putFloat("CruiseLimit", cruising == null ? 0.0F : (float) cruiseLimit(cruising));
            tag.putFloat("CruiseSpeed", (float) (cruiseSpeed * FreeCruise.TICKS_PER_SECOND));
            tag.putInt("CruiseSight", cruiseSight.ordinal());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state = RiftDriveState.byIndex(tag.getInt("State"));
        charge = tag.getFloat("Charge");
        committedCost = tag.getFloat("CommittedCost");
        committedDistance = tag.getDouble("CommittedDistance");
        sequenceTicks = tag.getInt("SequenceTicks");
        redstonePowered = tag.getBoolean("Powered");
        standingCourse = WarpCourse.load(
                tag.contains("StandingCourse") ? tag.getCompound("StandingCourse") : null);
        standingAuthor = tag.hasUUID("StandingAuthor") ? tag.getUUID("StandingAuthor") : null;
        spinRequired = tag.getFloat("SpinRequired");
        spinProgress = tag.getFloat("SpinProgress");
        cooldownTicks = tag.getInt("CooldownTicks");
        // A drive saved before storms existed has no total, and its cooldown was the tier's own.
        cooldownTotal = tag.contains("CooldownTotal") ? tag.getInt("CooldownTotal") : -1;
        errorTicks = tag.getInt("ErrorTicks");
        lastFailure = WarpFailure.byIndex(tag.getInt("LastFailure"));
        heading = DriveHeading.byIndex(tag.getInt("Heading"));
        // Absent on every drive saved before cruising, which reads as a waypoint - what they all were.
        mode = DriveMode.byIndex(tag.getInt("Mode"));
        // Absent on every drive saved before the switch existed, all of which checked - so absent is on,
        // which a bare getBoolean would have read as off.
        clearanceChecks = !tag.contains("ClearanceChecks") || tag.getBoolean("ClearanceChecks");
        // Absent on a drive standing on its own, and on every drive saved before extenders existed.
        // Clamped rather than trusted: a size out of range would throw building the frame, from a
        // renderer, every frame. A step that does not actually lead to a frame round this drive is left
        // for the next DriveAssembly.check to take apart and form again from what is really there.
        frameSize = tag.contains("FrameStep")
                ? Mth.clamp(tag.getInt("FrameSize"), DriveStructure.MIN_SIZE, DriveStructure.MAX_SIZE)
                : DriveStructure.MIN_SIZE;
        frameStep = frameSize > DriveStructure.MIN_SIZE
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag, "FrameStep").orElse(BlockPos.ZERO)
                : BlockPos.ZERO;
        if (!clientPacket) {
            cruiseAuthor = tag.hasUUID("CruiseAuthor") ? tag.getUUID("CruiseAuthor") : null;
        }
        cruisePhase = CruisePhase.byIndex(tag.getInt("CruisePhase"));
        destinationAnchor = tag.hasUUID("Anchor") ? tag.getUUID("Anchor") : null;
        destinationPos = tag.contains("TargetX")
                ? new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"))
                : null;
        destinationArrivalHeight = tag.contains("TargetArrivalHeight")
                ? tag.getInt("TargetArrivalHeight") : ArrivalHeight.UNSET;
        destinationHoldAltitude = tag.getBoolean("TargetHoldAltitude");
        destinationLabel = tag.getString("DestinationLabel");
        if (!clientPacket) {
            ResourceLocation dimension = tag.contains("DestinationDimension")
                    ? ResourceLocation.tryParse(tag.getString("DestinationDimension")) : null;
            destinationDimension = dimension == null ? null : ResourceKey.create(Registries.DIMENSION, dimension);
        }
        initiator = tag.hasUUID("Initiator") ? tag.getUUID("Initiator") : null;
        berthId = tag.hasUUID("Berth") ? tag.getUUID("Berth") : null;
        flight = tag.contains("Flight") ? WarpFlight.load(tag.getCompound("Flight")) : null;
        if (!clientPacket) {
            foldOrder = FoldOrder.load(tag.contains("FoldOrder") ? tag.getCompound("FoldOrder") : null);
            foldReleased = tag.getBoolean("FoldReleased");
            foldArrival = tag.contains("FoldArrivalX")
                    ? new Vec3(tag.getDouble("FoldArrivalX"), tag.getDouble("FoldArrivalY"), tag.getDouble("FoldArrivalZ"))
                    : null;
            foldExtraCorridor = tag.getInt("FoldExtra");
            foldEcho = tag.getBoolean("FoldEcho");
            foldPassage = FleetPassage.load(tag.contains("FoldPassage") ? tag.getCompound("FoldPassage") : null);
            passengers.load(tag.contains("PassengerSeats") ? tag.getCompound("PassengerSeats") : null);
            if (tag.hasUUID("DriveId")) {
                driveId = tag.getUUID("DriveId");
            }
            forcedChunks.clear();
            for (long chunk : tag.getLongArray("ForcedChunks")) {
                forcedChunks.add(chunk);
            }
        }
        if (clientPacket) {
            clientFlightStage = tag.contains("FlightStage") ? tag.getString("FlightStage") : "";
            clientFlightProgress = tag.getFloat("FlightProgress");
            clientCharge = charge;
            clientChargeLast = charge;
            syncedSpinRate = tag.getFloat("SpinRate");
            syncedMinimumRpm = tag.getInt("MinRpm");
            syncedPhaseDuration = tag.getInt("PhaseDuration");
            syncedStressImpact = tag.getFloat("StressImpact");
            syncedCoolingVents = tag.getInt("CoolingVents");
            syncedCoolingIce = tag.getInt("CoolingIce");
            syncedCoolingShare = tag.getFloat("CoolingShare");
            syncedAwaitingLanding = tag.getBoolean("AwaitingLanding");
            syncedCruiseDistance = tag.getFloat("CruiseDistance");
            syncedCruiseLimit = tag.getFloat("CruiseLimit");
            syncedCruiseSpeed = tag.getFloat("CruiseSpeed");
            syncedCruiseSight = tag.getInt("CruiseSight");
        } else if (state.isSequenceRunning() && flight != null
                && tag.contains("CrossingHandoff", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            // Not a reload: this drive was just built in another dimension by a crossing, from what its
            // old self wrote a moment ago, and the flight it carries is live. The one exception to the rule
            // below, and deliberately the only one - it is written only for the instant of that save.
            resumeAfterCrossing = true;
            incomingHandoff = tag.getCompound("CrossingHandoff").copy();
            crossingWait = 0;
        } else if (state.isSequenceRunning()) {
            // A warp cannot survive a reload: drop back to a safe state rather than resuming blind.
            // The airship's own record still holds where it set off from, so a hull left out in the
            // corridor is put back there by the recovery in releaseStaleClaim.
            // Loud on the next tick. Silently dropping a warp here is what made two earlier
            // passenger fixes look like they had done nothing: the recovery they added stopped being
            // reached at all, and the log said nothing about why.
            interruptedOnLoad = true;
            interruptedStage = flight == null ? "none" : flight.stage().name();
            state = RiftDriveState.ERROR;
            lastFailure = WarpFailure.INTERRUPTED;
            errorTicks = Math.max(errorTicks, 40);
            flight = null;
            destinationAnchor = null;
            destinationPos = null;
            destinationDimension = null;
            destinationLabel = "";
            // A cruise does not survive a reload either, and has nothing to put back: it was flying, so
            // the ship is simply wherever it had got to. The recovery stops it and lets the crew go.
            clearCruise();
            // Nor does a fold. Its flotilla is still a flotilla; this flight of it is over.
            clearFold();
        }
    }

    // -------------------------------------------------------------- feedback

    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        level.playSound(null, worldPosition, sound, net.minecraft.sounds.SoundSource.BLOCKS, volume, pitch);
    }

    /** Tier accent colours, matching the drive's core and the rift it tears open. */
    private static final int[] TIER_COLOURS = {0x2FA8B8, 0x49D9C4, 0xE0B04A, 0xE45CFF, 0xFFFFFF};

    private int tierColour() {
        return TIER_COLOURS[Math.max(0, Math.min(TIER_COLOURS.length - 1, tier.index()))];
    }

    // ------------------------------------------------------------- rift modulator

    /**
     * The Modulator this drive is dressed by, resolved at most once a tick.
     *
     * <p>Checked both ways rather than merely found: two drives either side of one Modulator would
     * otherwise both find it and both believe they were the one it answered to, which is exactly the
     * ambiguity {@link uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity#linkedDrive()}
     * resolves by picking one - so this drive only trusts the module if the module agrees.
     */
    @Nullable
    private uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity cachedModulator;
    private long cachedModulatorTick = Long.MIN_VALUE;

    @Nullable
    private uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity findModulator() {
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        if (cachedModulatorTick == now) {
            return cachedModulator != null && !cachedModulator.isRemoved() ? cachedModulator : null;
        }
        cachedModulatorTick = now;
        cachedModulator = resolveModulator();
        return cachedModulator;
    }

    /**
     * Looked for all round the frame rather than only beside the drive, since a module bolted to any
     * face of an extended drive is that drive's. For a drive on its own the frame's surroundings are its
     * six neighbours, which is where it always looked.
     */
    @Nullable
    private uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity resolveModulator() {
        if (level == null) {
            return null;
        }
        for (BlockPos around : DriveStructure.surroundings(frame())) {
            if (level.isLoaded(around) && level.getBlockEntity(around)
                    instanceof uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity modulator
                    && modulator.linkedDrive() == this) {
                return modulator;
            }
        }
        return null;
    }

    /**
     * The colour this ship's harmonic tether is drawn in, and its Rift Coupler's crystal: the chosen colour
     * of a Modulator fitted to this drive, fuelled or not, or the tier's own.
     *
     * <p>Not {@link #effectiveColour()}, which falls back to the tier the moment a Modulator's tank runs
     * dry. A tether is how the rest of a flotilla tells this ship from the others, and it should not change
     * colour because somebody forgot to top up a tank - which costs nothing here, since the tether is not
     * the Modulator's to pay for. Answers on the client as well, where the renderer asks.
     */
    public int tetherColour() {
        uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity modulator = findModulator();
        return modulator != null ? modulator.colour() & 0xFF_FF_FF : tierColour();
    }

    /** The rift's colour: a linked, fuelled Modulator's choice, or the tier's own colour. */
    private int effectiveColour() {
        if (foldOrder != null && !foldOrder.lead()) {
            // The shared wake is the flagship's; this ship's own colours are for its tether.
            return foldOrder.colour();
        }
        uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity modulator = findModulator();
        int override = modulator == null ? -1 : modulator.activeColour();
        return override >= 0 ? override : tierColour();
    }

    /**
     * The rift's rim colour: a Modulator's second swatch, or {@link #effectiveColour()} again - which
     * draws exactly as a flat single colour always has, since the face blends between the two.
     */
    private int effectiveAccentColour() {
        if (foldOrder != null && !foldOrder.lead()) {
            return foldOrder.accent();
        }
        uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity modulator = findModulator();
        int override = modulator == null ? -1 : modulator.activeAccentColour();
        return override >= 0 ? override : effectiveColour();
    }

    /** The rift's look and opening animation, as a {@code RiftModulatorTheme} ordinal: a Modulator's choice, or STANDARD. */
    private int effectiveTheme() {
        if (foldOrder != null && !foldOrder.lead()) {
            return foldOrder.theme();
        }
        uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity modulator = findModulator();
        int override = modulator == null ? -1 : modulator.activeThemeOrdinal();
        return override >= 0 ? override
                : uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme.STANDARD.ordinal();
    }

    /** How strongly the rift's flourish reads: a Modulator's choice, or 1.0 - unchanged from today. */
    private float effectiveIntensity() {
        if (foldOrder != null && !foldOrder.lead()) {
            // A Wake Echo is a smaller, rougher thing than the wake it trails.
            return foldOrder.intensity() * (foldEcho ? WAKE_ECHO_INTENSITY : 1.0F);
        }
        uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity modulator = findModulator();
        float own = modulator == null ? 1.0F : modulator.activeIntensity();
        // A flagship tears the fleet's way open, first and larger than any of its escorts'.
        return foldOrder != null ? own * FLAGSHIP_INTENSITY : own;
    }

    /** How much larger a flagship's aperture reads when it leads a fold. */
    private static final float FLAGSHIP_INTENSITY = 1.35F;

    /** How much smaller a Wake Echo reads than the wake it trails. */
    private static final float WAKE_ECHO_INTENSITY = 0.55F;

    /** The flagship's look, for its fold's escorts to fly in: colour, rim, theme and intensity. */
    public FoldLook foldLook() {
        return new FoldLook(effectiveColour(), effectiveAccentColour(), effectiveTheme(), effectiveIntensity());
    }

    /** A rift's look, as a fold's order carries it. */
    public record FoldLook(int colour, int accent, int theme, float intensity) {
    }

    /**
     * Sends a payload to a set of players by name.
     *
     * <p>The server is the only side that reliably knows who is on a deck, so the corridor experience
     * is addressed to those players rather than broadcast and worked out client-side.
     */
    private static void send(List<ServerPlayer> players, CustomPacketPayload payload) {
        for (ServerPlayer player : players) {
            AWNetwork.sendTo(player, payload);
        }
    }

    /** A cue anchored on the drive itself: sparks, a lock-on, a failure. */
    private void broadcastEffect(ClientboundWarpEffectPacket.Stage stage) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Airship airship = airship();
        Vec3 origin = airship == null
                ? Vec3.atCenterOf(worldPosition)
                : airship.toWorld(Vec3.atCenterOf(worldPosition));
        AWNetwork.sendToTracking(serverLevel, origin, ClientboundWarpEffectPacket.at(
                worldPosition, stage, tier.index(), airship == null ? null : airship.uuid(), origin,
                effectiveColour(), effectiveAccentColour(), effectiveTheme(), effectiveIntensity()));
    }

    /**
     * A cue carrying an aperture.
     *
     * <p>Sent to players near the aperture rather than near the drive, because the exit rift opens
     * thousands of blocks from the machine that made it and the people who should see it are the ones
     * standing at the destination.
     */
    private void broadcastRift(ClientboundWarpEffectPacket.Stage stage, WarpFlight.Rift rift, int duration) {
        broadcastRift(stage, rift, duration, 0.0D);
    }

    /**
     * @param throat depth of the aperture's throat, signed along its normal: positive for the
     *               aperture the hull flies into, negative for the one it comes back out of, since
     *               the hull is hidden on opposite sides of the two
     */
    private void broadcastRift(ClientboundWarpEffectPacket.Stage stage, WarpFlight.Rift rift,
                               int duration, double throat) {
        broadcastRift(level instanceof ServerLevel serverLevel ? serverLevel : null, stage, rift, duration,
                throat, List.of());
    }

    /**
     * A cue carrying an aperture, to a given level and to the crew as well as whoever is near it.
     *
     * <p>The level is the one the aperture is <em>in</em>, which for the far one is the destination's -
     * before a cross-dimension crossing that is not the level this drive is in. The crew are named because
     * the far aperture is the one they are inside: a long hull's stern sits deeper in its throat than the
     * tracking range reaches from the mouth.
     */
    private void broadcastRift(@Nullable ServerLevel where, ClientboundWarpEffectPacket.Stage stage,
                               WarpFlight.Rift rift, int duration, double throat, List<ServerPlayer> crew) {
        if (where == null) {
            return;
        }
        Vec3 centre = new Vec3(rift.centre().x, rift.centre().y, rift.centre().z);
        AWNetwork.sendToTracking(where, centre, crew, riftCue(stage, rift, duration, throat));
    }

    private ClientboundWarpEffectPacket riftCue(ClientboundWarpEffectPacket.Stage stage, WarpFlight.Rift rift,
                                                int duration, double throat) {
        Airship airship = airship();
        // The fleet's one rift reads as the flagship's, whichever ship happens to send a cue about it.
        float intensity = foldPassage != null && foldOrder != null && !foldOrder.lead()
                ? foldOrder.intensity() * FLAGSHIP_INTENSITY : effectiveIntensity();
        return ClientboundWarpEffectPacket.rift(worldPosition, stage, tier.index(),
                airship == null ? null : airship.uuid(), rift, duration, throat, effectiveColour(),
                effectiveAccentColour(), effectiveTheme(), intensity);
    }

    // ------------------------------------------------------------ one rift

    /** The entry aperture as this drive's cues name it: the fleet's one rift, or its own. */
    private WarpFlight.Rift cueEntry(WarpFlight flight) {
        return foldPassage != null ? foldPassage.entry() : flight.entryRift();
    }

    /** The exit aperture as this drive's cues name it: the fleet's one rift, or its own. */
    private WarpFlight.Rift cueExit(WarpFlight flight) {
        return foldPassage != null && foldPassage.exit() != null ? foldPassage.exit() : flight.exitRift();
    }

    /**
     * Whether this drive sends a cue about its rift, or another ship in its fleet already has. A warp of its
     * own always sends its own; through the fleet's one rift, the first ship to get there sends it for all -
     * see {@link FoldLog.SharedCue}.
     */
    private boolean firstTo(FoldLog.SharedCue cue) {
        return foldPassage == null || foldOrder == null || !(level instanceof ServerLevel serverLevel)
                || FleetFolds.firstTo(serverLevel, foldOrder, cue);
    }

    /**
     * The hull is through its entry aperture: close it - unless it is the fleet's, and other ships have still
     * to come through, in which case the last of them closes it.
     */
    private void closeEntry(WarpFlight current) {
        if (foldPassage != null && foldOrder != null && level instanceof ServerLevel serverLevel
                && !FleetFolds.throughEntry(serverLevel, foldOrder)) {
            return;
        }
        broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, cueEntry(current), 0);
    }

    /**
     * The crew's first sight of the far aperture, and the transit that opens it for them.
     *
     * <p>Through the fleet's one rift every ship reappears on the same tick. The first tells everybody near
     * the rift; the rest tell only their own crews, who have to hear it whichever ship was first, since a
     * crew is named precisely because it may be out of the rift's range.
     */
    private void showExit(@Nullable ServerLevel where, WarpFlight current, List<ServerPlayer> crew) {
        if (firstTo(FoldLog.SharedCue.EXIT_TRANSIT)) {
            broadcastRift(where, ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, cueExit(current),
                    current.transitTicks(), -current.exitThroatDepth(), crew);
            return;
        }
        send(crew, riftCue(ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, cueExit(current),
                current.transitTicks(), -current.exitThroatDepth()));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_drive").forGoggles(tooltip);
        AWLang.translate(tier.translationKey()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if (frameSize > DriveStructure.MIN_SIZE) {
            AWLang.translate("gui.rift_drive.frame", frameSize, frameSize, frameSize)
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        AWLang.translate(cruisePhase == CruisePhase.CRUISING ? "drive.state.cruising" : state.translationKey())
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        AWLang.translate("gui.rift_drive.mode_line", AWLang.translate(mode.translationKey()).string())
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if (!clearanceChecks) {
            // Only when off. On is how every drive is, and a line saying so on each of them is noise; off
            // is the one that explains a ship set down somewhere it should not have been.
            AWLang.translate("gui.rift_drive.clearance_off_line").style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
        }
        if (cruiseFlying()) {
            AWLang.translate("gui.rift_drive.cruise_speed", AWLang.count(syncedCruiseSpeed))
                    .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
            AWLang.cruised(syncedCruiseDistance, syncedCruiseLimit).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
            String notice = cruisePhase == CruisePhase.DROPPING ? "gui.rift_drive.dropping_out"
                    : cruiseNoticeKey(cruiseSight());
            if (notice != null) {
                AWLang.translate(notice).style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
            }
        }
        if (state == RiftDriveState.STABILIZING && syncedAwaitingLanding) {
            // Otherwise a drive at full spin that does not fire looks stuck. It is waiting for the far
            // end to finish loading, which for a very large ship in new country can take a while.
            AWLang.translate("gui.rift_drive.awaiting_landing").style(ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
        }
        if (syncedCoolingVents + syncedCoolingIce > 0) {
            // Only when something is cooling it. Ice has no goggles of its own to say it counted, so the
            // drive is the one place a pilot can read that a block of it is doing anything.
            AWLang.translate("gui.rift_drive.cooling", syncedCoolingVents, syncedCoolingIce,
                            AWLang.percent(syncedCoolingShare))
                    .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        }
        AWLang.translate("gui.rift_drive.charge", AWLang.charge(tier.creative(), charge))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate("gui.rift_drive.required_rpm", requiredRpm())
                .style(isRunningFastEnough() ? ChatFormatting.GRAY : ChatFormatting.RED).forGoggles(tooltip, 1);
        if (lastFailure.isFailure()) {
            AWLang.translate(lastFailure.translationKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    // ------------------------------------------------------------- geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drive", 5, this::driveAnimation));
    }

    private PlayState driveAnimation(AnimationState<RiftDriveBlockEntity> animationState) {
        AnimationController<RiftDriveBlockEntity> controller = animationState.getController();
        controller.setAnimation(ANIMATIONS.get(state));
        controller.setAnimationSpeed(animationSpeed());
        return PlayState.CONTINUE;
    }

    /** Animation tempo follows the machine: faster as it charges, frantic while the rift is open. */
    private double animationSpeed() {
        return switch (state) {
            case IDLE -> 0.5D;
            case CHARGING -> 0.75D + clientCharge * 1.75D;
            case CHARGED -> 2.0D;
            case DESTINATION_SELECTED -> 2.5D;
            // Winding up: the machine visibly turns as fast as it is being driven, and picks up as
            // the spin builds. This is the whole point of a spin drive being a spin drive.
            case STABILIZING -> 1.0D + 2.0D * syncedSpinRate + 1.5D * sequenceProgress();
            case WARPING -> 4.0D;
            case ARRIVING -> 2.0D;
            case COOLDOWN -> 1.0D - sequenceProgress() * 0.75D;
            case ERROR -> 0.35D;
        };
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
