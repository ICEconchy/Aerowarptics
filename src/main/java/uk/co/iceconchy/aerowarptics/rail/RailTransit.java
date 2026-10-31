package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.gate.RiftGateState;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.hold.RailWake;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which trains are waiting at which gates, which are running through which corridors, and the gates' answers.
 *
 * <p>In memory only, and rebuilt from Create's own trains: nothing here is saved. A train that is still waiting
 * after a restart simply reaches the pane again and asks again; one that was running through a corridor is found
 * there by {@link #occupancy}, which is read off where Create says each train is, every tick, and never off what
 * this class remembers. That is also what keeps it honest about a train Create derailed, disassembled or removed:
 * it is not in the list, so it is not in transit.
 *
 * <h2>Who opens a gate</h2>
 * Never the train. A waiting train files a request against the gate it is waiting at, and the server tick here
 * reads requests, holds the gate's ground (and keeps its level awake, if nobody is in it), and asks the gate to
 * dial for it - {@link RiftGateBlockEntity#dialForTrain}, which pays and refuses exactly as a player's dial does.
 * The gate opens on its own ticks, and the next time the train reaches the pane it is let in.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class RailTransit {

    /** Ticks a request stands for after the train last asked. Trains ask every tick they are held. */
    private static final int REQUEST_TICKS = 10;

    /** Ticks an admission is kept with its train not yet seen in the corridor. */
    private static final int ADMISSION_GRACE = 100;

    /** How long, after a train was last told why it is waiting, before it is told again. */
    private static final int TELL_AGAIN_TICKS = 600;

    private static final int GATE_HOLD_LIFESPAN = 100;

    private static final TicketType<ChunkPos> RAIL_CALL =
            TicketType.create("aerowarptics:rail_call", Comparator.comparingLong(ChunkPos::toLong), GATE_HOLD_LIFESPAN);

    /**
     * A train waiting at a gate.
     *
     * @param waiting the gate it is at, which would dial
     * @param other   the lane's other end, which it would dial
     */
    private record Request(UUID waiting, UUID other, UUID lane) {
    }

    private static final class Asking {
        long lastAsked;
        long lastAttempt = Long.MIN_VALUE / 2;
        GateFailure lastAnswer = GateFailure.NONE;
        final Set<UUID> trains = new HashSet<>();
    }

    /**
     * A train let into a lane.
     *
     * @param inAtNear whether it went in at the lane's own gate, rather than out of the far one backwards
     */
    private record Admitted(UUID lane, boolean inAtNear, long at, boolean[] seen) {
    }

    private static final Map<Request, Asking> REQUESTS = new HashMap<>();
    private static final Map<UUID, Admitted> ADMITTED = new HashMap<>();
    /** Trains held at a pane this tick, and why. Read by the train mixin within the same tick. */
    private static final Map<UUID, GateFailure> HELD = new HashMap<>();
    private static final Map<UUID, Told> TOLD = new HashMap<>();
    /** Corridor slot -> trains present in it, as Create says. Rebuilt every tick. */
    private static final Map<Integer, Set<UUID>> OCCUPANCY = new HashMap<>();

    private record Told(GateFailure reason, long at) {
    }

    private RailTransit() {
    }

    // ------------------------------------------------------------- the pane

    /**
     * Whether a carriage reaching a portal edge must stop there. Called from Create's travel, through
     * {@link RailGates}.
     *
     * <p>The two ends of the edge are taken in no particular order. Create hands them over as here-then-there going
     * forwards and there-then-here going backwards, so which way a carriage is crossing is read off where the
     * carriage is - already in the corridor, or straddling the pane - and never off the order.
     */
    static boolean holds(MinecraftServer server, Carriage carriage, TrackNodeLocation from, TrackNodeLocation to) {
        boolean fromCorridor = RailCorridor.isCorridor(from.dimension);
        boolean toCorridor = RailCorridor.isCorridor(to.dimension);
        if (fromCorridor == toCorridor) {
            return false; // a Nether portal, or somebody else's: none of ours
        }
        TrackNodeLocation outside = fromCorridor ? to : from;
        RailLane lane = laneAt(server, outside);
        if (lane == null) {
            return false;
        }
        Train train = carriage.train;
        long now = now(server);
        // In the corridor already - half through this pane, or coming out of the far one - is never held.
        boolean crossing = straddles(carriage, from, to) || carriage.getDimensionalIfPresent(RailCorridor.KEY) != null;
        Admitted admitted = ADMITTED.get(train.id);
        boolean inThisLane = admitted != null && admitted.lane().equals(lane.id());
        boolean atNear = lane.dimension().equals(outside.dimension) && near(outside, lane.track());
        UUID waiting = atNear ? lane.gate() : lane.far();
        UUID other = atNear ? lane.far() : lane.gate();
        RiftGateBlockEntity gate = waiting == null ? null : loadedGate(server, waiting);
        boolean open = gate != null && gate.state() == RiftGateState.OPEN && other != null
                && other.equals(gate.connected());
        boolean fits = CorridorFit.fits(carriage.bogeySpacing, lane.length(), topSpeedPerTick());

        RailGateLogic.Admission verdict = RailGateLogic.admit(crossing, inThisLane, lane.ready(), fits, open);
        switch (verdict) {
            case ALREADY_CROSSING, PASS -> {
                if (verdict == RailGateLogic.Admission.PASS && !inThisLane) {
                    ADMITTED.put(train.id, new Admitted(lane.id(), atNear, now, new boolean[1]));
                    Trace.log(Trace.RAIL, "train {} let into lane {} at {}", train.name.getString(), lane.id(),
                            atNear ? "its own gate" : "the far gate");
                }
                return false;
            }
            default -> {
            }
        }
        GateFailure reason = switch (verdict) {
            case HOLD_LANE_BUILDING -> GateFailure.RAIL_LANE_BUILDING;
            case HOLD_TOO_LONG -> GateFailure.RAIL_CARRIAGE_TOO_LONG;
            default -> GateFailure.NONE;
        };
        if (verdict == RailGateLogic.Admission.HOLD_GATE_SHUT && waiting != null && other != null) {
            Asking asking = REQUESTS.computeIfAbsent(new Request(waiting, other, lane.id()), key -> new Asking());
            asking.lastAsked = now;
            asking.trains.add(train.id);
            reason = asking.lastAnswer;
        }
        HELD.put(train.id, reason);
        return true;
    }

    /** Whether a train was held at a pane this tick. Read by the train mixin. */
    static boolean heldThisTick(Train train) {
        return HELD.containsKey(train.id);
    }

    private static boolean straddles(Carriage carriage, TrackNodeLocation from, TrackNodeLocation to) {
        for (var dimension : carriage.getPresentDimensions()) {
            Carriage.DimensionalCarriageEntity present = carriage.getDimensionalIfPresent(dimension);
            if (present != null && present.pivot != null && (present.pivot.equals(from) || present.pivot.equals(to))) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------- lookups

    /**
     * The lane whose near track or far stub a track node outside the corridor belongs to.
     *
     * <p>A portal edge's node is at its track block's bottom centre, give or take how Create rounds it, so the block
     * it is in and the four beside it are all asked.
     */
    @Nullable
    static RailLane laneAt(MinecraftServer server, TrackNodeLocation node) {
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        BlockPos at = BlockPos.containing(node.getLocation().add(0.0D, 0.25D, 0.0D));
        RailLane lane = lanes.atTrack(node.dimension, at);
        if (lane != null) {
            return lane;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            lane = lanes.atTrack(node.dimension, at.relative(side));
            if (lane != null) {
                return lane;
            }
        }
        return null;
    }

    private static boolean near(TrackNodeLocation node, BlockPos track) {
        return node.getLocation().distanceToSqr(track.getBottomCenter()) <= 2.25D;
    }

    @Nullable
    private static RiftGateBlockEntity loadedGate(MinecraftServer server, UUID id) {
        RiftGate record = RiftGateRegistry.get(server).byId(id);
        if (record == null) {
            return null;
        }
        ServerLevel level = server.getLevel(record.dimension());
        if (level == null || !level.isLoaded(record.controller())) {
            return null;
        }
        return level.getBlockEntity(record.controller()) instanceof RiftGateBlockEntity gate ? gate : null;
    }

    private static double topSpeedPerTick() {
        return AllConfigs.server().trains.poweredTrainTopSpeed.get() / 20.0D;
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ------------------------------------------------------- what gates ask

    /** Whether any train is in, or has just been let into, a lane. */
    public static boolean inTransit(RailLane lane) {
        Set<UUID> present = OCCUPANCY.get(lane.slot());
        if (present != null && !present.isEmpty()) {
            return true;
        }
        for (Admitted admitted : ADMITTED.values()) {
            if (admitted.lane().equals(lane.id())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a train is part of the way through any lane joining these two gates. */
    public static boolean inTransitBetween(MinecraftServer server, UUID one, @Nullable UUID other) {
        if (other == null) {
            return false;
        }
        for (RailLane lane : RailLaneRegistry.get(server).touching(one)) {
            if (lane.joins(one, other) && inTransit(lane)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any lane the gate owns has a train part of the way through it. */
    public static boolean ownLanesInTransit(MinecraftServer server, UUID gate) {
        for (RailLane lane : RailLaneRegistry.get(server).ownedBy(gate)) {
            if (inTransit(lane)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a gate has a train waiting at it, or running through one of its lanes to {@code other}. */
    public static boolean busy(MinecraftServer server, UUID gate, @Nullable UUID other) {
        long now = now(server);
        for (Map.Entry<Request, Asking> entry : REQUESTS.entrySet()) {
            if (entry.getKey().waiting().equals(gate) && now - entry.getValue().lastAsked <= REQUEST_TICKS) {
                return true;
            }
        }
        return inTransitBetween(server, gate, other);
    }

    /** How many trains are waiting at a gate now, for its panel. */
    public static int waitingAt(UUID gate) {
        Set<UUID> trains = new HashSet<>();
        for (Map.Entry<Request, Asking> entry : REQUESTS.entrySet()) {
            if (entry.getKey().waiting().equals(gate)) {
                trains.addAll(entry.getValue().trains);
            }
        }
        return trains.size();
    }

    /** How many trains are in a gate's lanes, either end, for its panel. */
    public static int runningThrough(MinecraftServer server, UUID gate) {
        Set<UUID> trains = new HashSet<>();
        for (RailLane lane : RailLaneRegistry.get(server).touching(gate)) {
            trains.addAll(OCCUPANCY.getOrDefault(lane.slot(), Set.of()));
        }
        return trains.size();
    }

    /**
     * Files a request for a scheduled train still on its way, so the gate is open when it gets there - see
     * {@link RailPreDial}.
     */
    static void askAhead(MinecraftServer server, RailLane lane, boolean atNear, UUID train) {
        UUID waiting = atNear ? lane.gate() : lane.far();
        UUID other = atNear ? lane.far() : lane.gate();
        if (waiting == null || other == null || !lane.ready()) {
            return;
        }
        Asking asking = REQUESTS.computeIfAbsent(new Request(waiting, other, lane.id()), key -> new Asking());
        asking.lastAsked = now(server);
        asking.trains.add(train);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = now(server);
        tellOwners(server, now);
        HELD.clear();
        survey(server);
        settleAdmissions(server, now);
        answerRequests(server, now);
        RailWake.tick(server, now);
        if (now % 10L == 0L && AWConfig.RAIL_GATES.get()) {
            RailPreDial.look(server);
        }
        CorridorWarden.tick(server, now);
        // The occupancy itself, not a flattened set: the warden holds each occupied corridor's ground, and for
        // that it has to know which corridors those are.
        CorridorWarden.tickTrains(server, now, Map.copyOf(OCCUPANCY));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REQUESTS.clear();
        ADMITTED.clear();
        HELD.clear();
        TOLD.clear();
        OCCUPANCY.clear();
        RailWake.clear();
    }

    /** Reads where every train is in the corridor, as Create has it now. */
    private static void survey(MinecraftServer server) {
        OCCUPANCY.clear();
        if (RailCorridor.level(server) == null) {
            return;
        }
        for (Train train : Create.RAILWAYS.trains.values()) {
            for (Carriage carriage : train.carriages) {
                Carriage.DimensionalCarriageEntity present = carriage.getDimensionalIfPresent(RailCorridor.KEY);
                if (present == null || present.positionAnchor == null) {
                    continue;
                }
                int slot = CorridorSlots.slotAt(present.positionAnchor.z);
                if (slot >= 0) {
                    OCCUPANCY.computeIfAbsent(slot, key -> new HashSet<>()).add(train.id);
                }
            }
        }
    }

    /**
     * Forgets admissions once their train is through - seen in the corridor and now gone from it - and says so to
     * the detectors at the end it came out of.
     */
    private static void settleAdmissions(MinecraftServer server, long now) {
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        Iterator<Map.Entry<UUID, Admitted>> entries = ADMITTED.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Admitted> entry = entries.next();
            Admitted admitted = entry.getValue();
            RailLane lane = lanes.get(admitted.lane());
            Train train = Create.RAILWAYS.trains.get(entry.getKey());
            if (lane == null || train == null) {
                entries.remove();
                continue;
            }
            boolean inside = OCCUPANCY.getOrDefault(lane.slot(), Set.of()).contains(train.id);
            if (inside) {
                admitted.seen()[0] = true;
                continue;
            }
            if (admitted.seen()[0]) {
                entries.remove();
                arrived(server, lane, admitted.inAtNear(), train);
            } else if (now - admitted.at() > ADMISSION_GRACE) {
                entries.remove(); // let in but never went - it reversed away, or was stopped short
            }
        }
    }

    private static void arrived(MinecraftServer server, RailLane lane, boolean inAtNear, Train train) {
        ServerLevel level = server.getLevel(inAtNear ? lane.farDimension() : lane.dimension());
        BlockPos out = inAtNear ? lane.farStub() : lane.track();
        Trace.log(Trace.RAIL, "train {} came out of lane {} at {}", train.name.getString(), lane.id(),
                out == null ? "?" : Trace.pos(out));
        if (level == null || out == null) {
            return;
        }
        SpatialWaveDetectorBlockEntity.announce(level, new AABB(out).inflate(2.0D));
        List<ServerPlayer> riders = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.getVehicle() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity carriage
                    && carriage instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity entity
                    && train.id.equals(entity.trainId)) {
                riders.add(player);
            }
        }
        AWCriteria.trainThroughGate(riders);
    }

    /** Dials, or does not, for every gate with a train waiting at it. */
    private static void answerRequests(MinecraftServer server, long now) {
        Iterator<Map.Entry<Request, Asking>> entries = REQUESTS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Request, Asking> entry = entries.next();
            Request request = entry.getKey();
            Asking asking = entry.getValue();
            if (now - asking.lastAsked > REQUEST_TICKS) {
                entries.remove();
                continue;
            }
            asking.trains.removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));
            RiftGate record = RiftGateRegistry.get(server).byId(request.waiting());
            ServerLevel level = record == null ? null : server.getLevel(record.dimension());
            if (record == null || level == null) {
                asking.lastAnswer = GateFailure.DESTINATION_MISSING;
                continue;
            }
            ChunkPos chunk = new ChunkPos(record.controller());
            Holds.ground(level, RAIL_CALL, chunk, 2);
            if (!level.isLoaded(record.controller())
                    || !(level.getBlockEntity(record.controller()) instanceof RiftGateBlockEntity gate)) {
                // Being loaded by the hold just placed, which takes a tick or two. Not a refusal: the owner is told
                // the gate is opening, which is what it is about to do.
                Trace.changed("rail-call:" + request.waiting(), Trace.RAIL, "{} waiting for {} to load to dial for a"
                        + " train", record.displayName(), Trace.pos(record.controller()));
                asking.lastAnswer = GateFailure.NONE;
                continue;
            }
            RailWake.keep(level, chunk, now);
            boolean engagedWithOther = gate.state().engaged() && request.other().equals(gate.connected());
            boolean engagedElsewhere = gate.state().engaged() && !engagedWithOther;
            RailGateLogic.Call call = RailGateLogic.call(engagedWithOther, engagedElsewhere,
                    level.hasNeighborSignal(record.controller()), now - asking.lastAttempt,
                    AWConfig.RAIL_RETRY_TICKS.get());
            switch (call) {
                case NOTHING -> {
                    if (gate.state() == RiftGateState.OPEN) {
                        asking.lastAnswer = GateFailure.NONE;
                    }
                }
                case HELD -> asking.lastAnswer = GateFailure.RAIL_HELD;
                case WAIT -> {
                    if (engagedElsewhere) {
                        asking.lastAnswer = GateFailure.DESTINATION_BUSY;
                    }
                }
                case DIAL -> {
                    asking.lastAttempt = now;
                    GateFailure answer = gate.dialForTrain(request.other());
                    // A far gate aboard a ship being brought in, or one whose chunks are still loading: both clear by
                    // themselves, and neither is the train owner's to fix.
                    asking.lastAnswer = answer == GateFailure.DESTINATION_LOADING ? GateFailure.NONE : answer;
                }
            }
        }
    }

    /**
     * Tells each held train's owner why it is waiting - once, and again only if the reason changes or a good while
     * passes - in the way Create tells them a train has reached the end of its track.
     */
    private static void tellOwners(MinecraftServer server, long now) {
        for (Map.Entry<UUID, GateFailure> entry : HELD.entrySet()) {
            Train train = Create.RAILWAYS.trains.get(entry.getKey());
            if (train == null || train.owner == null) {
                continue;
            }
            Told told = TOLD.get(train.id);
            if (told != null && told.reason() == entry.getValue() && now - told.at() < TELL_AGAIN_TICKS) {
                continue;
            }
            TOLD.put(train.id, new Told(entry.getValue(), now));
            ServerPlayer owner = server.getPlayerList().getPlayer(train.owner);
            Trace.log(Trace.RAIL, "train {} held at a gate: {}", train.name.getString(),
                    entry.getValue().getSerializedName());
            if (owner == null) {
                continue;
            }
            Component why = entry.getValue().isFailure()
                    ? AWLang.translate(entry.getValue().translationKey()).component()
                    : AWLang.translate("gate.rail.opening").component();
            owner.displayClientMessage(AWLang.translate("gate.rail.held", train.name, why).component(), false);
        }
        TOLD.entrySet().removeIf(entry -> !HELD.containsKey(entry.getKey())
                && now - entry.getValue().at() > TELL_AGAIN_TICKS);
    }
}
