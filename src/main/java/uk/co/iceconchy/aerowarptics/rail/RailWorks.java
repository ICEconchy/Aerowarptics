package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackPropagator;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.gate.RiftSocketBlock;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.Comparator;
import java.util.UUID;

/**
 * Lays and takes up the parts of a rail lane that this mod puts down itself: the corridor, and the stub of track
 * that comes out of the far gate.
 *
 * <h2>How track is bound</h2>
 * Exactly as Create binds the two ends of a Nether portal's track, because Create is what will read them: each end
 * is a track block in its portal shape for the way it points, with a block entity, and each block entity names the
 * other's dimension and position. Create lays the near pair itself - it asks {@link RiftPortalTracks} where the
 * far side of the "portal" is, and puts the corridor's west track there. The far pair, from the corridor's east end
 * to the far gate, is laid here, the same way, because no player is standing at the far gate to lay it.
 *
 * <h2>Taking a bound track up</h2>
 * A Create portal track destroys the track it is bound to when it is removed - loading that track's chunk to do so
 * if it has to. That is right for a Nether portal and wrong here, where a lane is re-routed by taking up one end and
 * keeping the other. So every bound track this takes up has its binding cleared first ({@link #unbind}), and its
 * partner is dealt with deliberately.
 *
 * <h2>Loading</h2>
 * Nothing here loads a chunk itself. The corridor is in a dimension of void, and its chunks are held through
 * {@link Holds#ground} before anything is put in them. A far gate's chunks are held the same way and waited for: a
 * far end whose chunk is not loaded is left {@link RailLaneState#FAR_PENDING}, and {@link #onServerTick} lays it
 * once it is.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class RailWorks {

    /** How long a hold on ground being worked on lasts. Renewed while the work is waiting. */
    private static final int WORKS_LIFESPAN = 300;

    private static final TicketType<ChunkPos> RAIL_WORKS =
            TicketType.create("aerowarptics:rail_works", Comparator.comparingLong(ChunkPos::toLong), WORKS_LIFESPAN);

    /**
     * Chunks round a far stub held while it is laid: block-ticking out to two chunks either way, which takes in a
     * station built just beyond the apron.
     */
    private static final int FAR_HOLD_RADIUS = 3;

    /** Ticks between looks at lanes waiting for a far end, and stubs waiting to be taken up. */
    private static final int WORK_INTERVAL = 20;

    /** How high a mouth's sheet of shimmer stands, from the track up. */
    private static final int MOUTH_HEIGHT = 4;

    private RailWorks() {
    }

    // ------------------------------------------------------------- corridor

    /**
     * Lays a corridor: a barrier floor, a mouth at each end, and straight track between them - all but the two
     * tracks at the ends, which are bound to the gates and laid as they are bound.
     *
     * @param material the track block to lay, the one the player ran into the gate
     */
    public static void layCorridor(ServerLevel corridor, int slot, int length, Block material) {
        holdCorridor(corridor, slot, length);
        int z = CorridorSlots.z(slot);
        int y = CorridorSlots.TRACK_Y;
        int flags = Block.UPDATE_CLIENTS;
        for (int x = -1; x <= length + 1; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                corridor.setBlock(new BlockPos(x, y - 1, z + dz), Blocks.BARRIER.defaultBlockState(), flags);
            }
        }
        for (int mouth : new int[] {CorridorSlots.westMouth(), CorridorSlots.eastMouth(length)}) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < MOUTH_HEIGHT; dy++) {
                    corridor.setBlock(new BlockPos(mouth, y + dy, z + dz),
                            AWBlocks.CORRIDOR_MOUTH.get().defaultBlockState(), flags);
                }
            }
        }
        BlockState straight = straight(material, Direction.Axis.X);
        for (int x = CorridorSlots.westTrack() + 1; x < CorridorSlots.eastTrack(length); x++) {
            // A full update, so Create's track ticks and joins the run into one stretch of its graph.
            corridor.setBlock(new BlockPos(x, y, z), straight, Block.UPDATE_ALL);
        }
        joinGraph(corridor, new BlockPos(CorridorSlots.westTrack() + 1, y, z));
        Trace.log(Trace.RAIL, "laid corridor {} ({} blocks of {})", slot, length,
                BuiltInRegistries.BLOCK.getKey(material));
    }

    /** Takes a corridor up entirely, leaving the void it was laid in. */
    public static void clearCorridor(ServerLevel corridor, int slot, int length) {
        holdCorridor(corridor, slot, length);
        int z = CorridorSlots.z(slot);
        int y = CorridorSlots.TRACK_Y;
        // The bound ends first, unbound, so neither reaches out to destroy what it is bound to.
        unbind(corridor, new BlockPos(CorridorSlots.westTrack(), y, z));
        unbind(corridor, new BlockPos(CorridorSlots.eastTrack(length), y, z));
        for (int x = CorridorSlots.westTrack(); x <= CorridorSlots.eastTrack(length); x++) {
            corridor.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int x = -1; x <= length + 1; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy < MOUTH_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(x, y + dy, z + dz);
                    if (!corridor.getBlockState(pos).isAir()) {
                        corridor.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        Trace.log(Trace.RAIL, "took up corridor {}", slot);
    }

    public static BlockPos westTrack(int slot) {
        return new BlockPos(CorridorSlots.westTrack(), CorridorSlots.TRACK_Y, CorridorSlots.z(slot));
    }

    public static BlockPos eastTrack(int slot, int length) {
        return new BlockPos(CorridorSlots.eastTrack(length), CorridorSlots.TRACK_Y, CorridorSlots.z(slot));
    }

    /**
     * Holds every chunk of a corridor at block-ticking level while it is worked on.
     *
     * <p>Every chunk, each the centre of its own ticket: a region ticket of radius one is block-ticking only at its
     * centre, and its ring is not. Create joins a track into its graph from a scheduled tick on the block, and a
     * scheduled tick in a chunk that does not block-tick never runs - so held every other chunk, as this once was,
     * half the corridor's track was never part of any graph and no train could find a path through it.
     */
    private static void holdCorridor(ServerLevel corridor, int slot, int length) {
        int chunkZ = CorridorSlots.z(slot) >> 4;
        for (int chunkX = (CorridorSlots.westMouth() - 1) >> 4; chunkX <= (length + 1) >> 4; chunkX++) {
            Holds.ground(corridor, RAIL_WORKS, new ChunkPos(chunkX, chunkZ), 1);
        }
    }

    /**
     * Joins track just laid into Create's graph now, rather than waiting for its scheduled tick.
     *
     * <p>A belt and braces: the tick still comes, and asks again, which Create takes in its stride. But a lane that
     * waited for it was only as good as the chunks' ticket levels at the moment, and a train's path search sees the
     * graph, not the blocks.
     */
    private static void joinGraph(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrackBlock) {
            TrackPropagator.onRailAdded(level, pos, state);
        }
    }

    // -------------------------------------------------------------- far end

    /**
     * Lays a lane's far end - the corridor's east track and the stub out of the far gate - if it can be laid now.
     *
     * @return the lane as it now is: {@link RailLaneState#READY}, still pending, or blocked
     */
    public static RailLane layFar(MinecraftServer server, RailLane lane) {
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        RiftGateRegistry gates = RiftGateRegistry.get(server);
        if (lane.far() == null) {
            return settle(lanes, lane, RailLaneState.NO_ROUTE);
        }
        RiftGate near = gates.byId(lane.gate());
        RiftGate far = gates.byId(lane.far());
        ServerLevel corridor = RailCorridor.level(server);
        if (near == null || far == null || corridor == null) {
            Trace.changed("rail-far:" + lane.id(), Trace.RAIL, "lane {} has no far end to lay: near gate {}, far gate"
                    + " {}, corridor {}", lane.id(), near != null, far != null, corridor != null);
            return settle(lanes, lane, RailLaneState.NO_ROUTE);
        }
        ServerLevel farLevel = server.getLevel(far.dimension());
        if (farLevel == null) {
            return settle(lanes, lane, RailLaneState.NO_ROUTE);
        }
        RailLaneMap.Near entry = new RailLaneMap.Near(lane.cell(), RailLaneMap.acrossOf(near.shape(), lane.cell()),
                lane.heading());
        RailLaneMap.Far exit = RailLaneMap.far(near.shape(), entry, far.shape(), AWConfig.RAIL_APRON_LENGTH.get());
        if (exit == null) {
            return settle(lanes, lane, RailLaneState.NO_ROUTE);
        }
        // Wider than the stub's own chunk, and block-ticking across it: laying the stub changes Create's graph along
        // the track it joins, and a station on that track re-attaches to the graph only on its own tick. Held to the
        // stub's chunk alone, the station a little way along it sat out of the graph until somebody walked by.
        holdGround(farLevel, exit.stub(), FAR_HOLD_RADIUS);
        if (!farLevel.isLoaded(exit.stub()) || !farLevel.isLoaded(exit.cell())) {
            Trace.changed("rail-far:" + lane.id(), Trace.RAIL, "lane {} waiting for {} to load before laying its far"
                    + " end at {}", lane.id(), far.displayName(), Trace.pos(exit.stub()));
            return settle(lanes, lane, RailLaneState.FAR_PENDING);
        }

        RailLane other = lanes.atTrack(far.dimension(), exit.stub());
        if (other != null && !other.id().equals(lane.id())) {
            Trace.changed("rail-far:" + lane.id(), Trace.RAIL, "lane {} cannot come out of {} at {}: lane {} already"
                    + " does", lane.id(), far.displayName(), Trace.pos(exit.stub()), other.id());
            lastFarFailure = GateFailure.RAIL_FAR_LANE_TAKEN;
            return settle(lanes, lane, RailLaneState.FAR_BLOCKED);
        }
        BlockState cellState = farLevel.getBlockState(exit.cell());
        BlockState stubState = farLevel.getBlockState(exit.stub());
        boolean cellOpen = RiftSocketBlock.isLaneCell(cellState) || cellState.isAir() || cellState.canBeReplaced();
        boolean stubOurs = stubState.getBlock() instanceof TrackBlock && boundTo(farLevel, exit.stub(), corridor,
                eastTrack(lane.slot(), lane.length()));
        if (!cellOpen || !stubOurs && !stubState.canBeReplaced()) {
            Trace.changed("rail-far:" + lane.id(), Trace.RAIL, "lane {} cannot come out of {}: {} at the stub {},"
                            + " {} in the cell {}", lane.id(), far.displayName(), stubState, Trace.pos(exit.stub()),
                    cellState, Trace.pos(exit.cell()));
            lastFarFailure = GateFailure.RAIL_FAR_BLOCKED;
            return settle(lanes, lane, RailLaneState.FAR_BLOCKED);
        }

        Block material = BuiltInRegistries.BLOCK.getOptional(lane.material())
                .filter(block -> block instanceof TrackBlock)
                .orElse(com.simibubi.create.AllBlocks.TRACK.get());
        // The cell first, so the stub is placed against a portal Create already recognises and is not eaten by
        // its own first shape update.
        if (!RiftSocketBlock.isLaneCell(cellState)) {
            farLevel.setBlock(exit.cell(), AWBlocks.RIFT_SOCKET.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        holdCorridor(corridor, lane.slot(), lane.length());
        BlockPos east = eastTrack(lane.slot(), lane.length());
        unbind(corridor, east);
        corridor.setBlock(east, portal(material, Direction.EAST), Block.UPDATE_ALL);
        if (!stubOurs) {
            unbind(farLevel, exit.stub());
            farLevel.setBlock(exit.stub(),
                    ProperWaterloggedBlock.withWater(farLevel, portal(material, exit.heading().getOpposite()), exit.stub()),
                    Block.UPDATE_ALL);
        }
        bind(corridor, east, farLevel.dimension(), exit.stub());
        bind(farLevel, exit.stub(), corridor.dimension(), east);
        // Both ends, now that both are bound: each finds the other through its binding as it joins the graph.
        joinGraph(corridor, east);
        joinGraph(farLevel, exit.stub());

        BlockState straight = straight(material, exit.heading().getAxis());
        for (BlockPos apron : exit.apron()) {
            BlockState there = farLevel.getBlockState(apron);
            if (there.getBlock() instanceof TrackBlock || !there.canBeReplaced() || !farLevel.isLoaded(apron)) {
                break; // somebody's own track, or something in the way: the apron is a courtesy, not a demand
            }
            farLevel.setBlock(apron, ProperWaterloggedBlock.withWater(farLevel, straight, apron), Block.UPDATE_ALL);
        }

        RailLane laid = lane.withFarEnd(far.dimension(), exit.cell(), exit.stub(), exit.heading());
        lanes.put(laid);
        // The far gate's own cells have changed: a cell that was air is now a lane's, and must not be left empty.
        RiftGateBlockEntity farGate = farLevel.getBlockEntity(far.controller()) instanceof RiftGateBlockEntity gate
                ? gate : null;
        if (farGate != null) {
            farGate.laneCellsChanged();
        }
        Trace.log(Trace.RAIL, "lane {} now comes out of {} at {} heading {}", lane.id(), far.displayName(),
                Trace.pos(exit.stub()), exit.heading());
        return laid;
    }

    /** The reason the last far end refused, for the gate that asked to be told. Server thread only. */
    private static GateFailure lastFarFailure = GateFailure.NONE;

    public static GateFailure lastFarFailure() {
        return lastFarFailure;
    }

    /**
     * Takes a lane's far end up: the stub out of the far gate and the corridor's east track.
     *
     * <p>A stub in chunks that are not loaded is left where it is and owed a tidy-up, rather than loaded now to be
     * taken away - see {@link RailLaneRegistry.Cleanup}.
     */
    public static void takeUpFar(MinecraftServer server, RailLane lane) {
        ServerLevel corridor = RailCorridor.level(server);
        if (corridor != null) {
            holdCorridor(corridor, lane.slot(), lane.length());
            BlockPos east = eastTrack(lane.slot(), lane.length());
            unbind(corridor, east);
            corridor.setBlock(east, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (lane.farDimension() == null || lane.farStub() == null || lane.farCell() == null) {
            return;
        }
        RailLaneRegistry.Cleanup owed = new RailLaneRegistry.Cleanup(lane.farDimension(), lane.farStub(),
                lane.farCell());
        ServerLevel farLevel = server.getLevel(lane.farDimension());
        if (farLevel == null || !farLevel.isLoaded(lane.farStub())) {
            RailLaneRegistry.get(server).owe(owed);
            Trace.log(Trace.RAIL, "lane {}'s old far stub at {} is not loaded; it is taken up when it next is",
                    lane.id(), Trace.pos(lane.farStub()));
            return;
        }
        settle(server, farLevel, owed, lane.id());
    }

    /**
     * Takes a lane's near end up, leaving the player's own track as ordinary straight track.
     *
     * <p>Only once the cell it pointed into is no longer a portal - the gate has emptied it - or Create's first tick
     * on the track would find the portal still there and ask for the lane all over again.
     */
    public static void takeUpNear(MinecraftServer server, RailLane lane) {
        ServerLevel level = server.getLevel(lane.dimension());
        if (level == null || !level.isLoaded(lane.track())) {
            return;
        }
        unbind(level, lane.track());
        BlockState there = level.getBlockState(lane.track());
        if (there.getBlock() instanceof TrackBlock && there.getValue(TrackBlock.SHAPE).isPortal()) {
            level.setBlock(lane.track(), there.setValue(TrackBlock.SHAPE,
                    lane.heading().getAxis() == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO)
                    .setValue(TrackBlock.HAS_BE, false), Block.UPDATE_ALL);
        }
    }

    /**
     * Takes up one owed stub, now that it is loaded: the stub, and the socket beside it if no lane still runs
     * through that cell and the gate is shut.
     *
     * @param except a lane whose own cells do not count as still in use, because it is the one being taken up
     */
    private static void settle(MinecraftServer server, ServerLevel level, RailLaneRegistry.Cleanup owed,
                               @Nullable UUID except) {
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        RailLane now = lanes.atTrack(owed.dimension(), owed.stub());
        if (now != null && !now.id().equals(except)) {
            // Laid again since - a lane came back out of the same place. Not ours to take up any more.
            lanes.settled(owed);
            return;
        }
        unbind(level, owed.stub());
        BlockState stub = level.getBlockState(owed.stub());
        if (stub.getBlock() instanceof TrackBlock && stub.getValue(TrackBlock.SHAPE).isPortal()) {
            level.setBlock(owed.stub(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (level.getBlockState(owed.cell()).is(AWBlocks.RIFT_SOCKET.get()) && !cellInUse(lanes, owed, except)) {
            level.setBlock(owed.cell(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        lanes.settled(owed);
        Trace.log(Trace.RAIL, "took up an old far stub at {} in {}", Trace.pos(owed.stub()),
                owed.dimension().location());
    }

    private static boolean cellInUse(RailLaneRegistry lanes, RailLaneRegistry.Cleanup owed, @Nullable UUID except) {
        for (RailLane lane : lanes.all()) {
            if (lane.id().equals(except)) {
                continue;
            }
            if (lane.dimension().equals(owed.dimension()) && lane.cell().equals(owed.cell())
                    || owed.dimension().equals(lane.farDimension()) && owed.cell().equals(lane.farCell())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- binding

    /** A track block in its portal shape, pointing into the portal the way given, with its block entity. */
    static BlockState portal(Block material, Direction towardPortal) {
        return material.defaultBlockState()
                .setValue(TrackBlock.SHAPE, TrackShape.asPortal(towardPortal))
                .setValue(TrackBlock.HAS_BE, true);
    }

    static BlockState straight(Block material, Direction.Axis axis) {
        return material.defaultBlockState()
                .setValue(TrackBlock.SHAPE, axis == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO);
    }

    private static void bind(ServerLevel level, BlockPos pos, ResourceKey<Level> otherDimension, BlockPos other) {
        if (level.getBlockEntity(pos) instanceof TrackBlockEntity track) {
            track.bind(otherDimension, other);
        }
    }

    /**
     * Forgets what a portal track is bound to, so that removing it does not reach out and destroy its partner.
     */
    static void unbind(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TrackBlockEntity track
                && track.boundLocation != null) {
            track.boundLocation = null;
            track.setChanged();
        }
    }

    private static boolean boundTo(ServerLevel level, BlockPos pos, ServerLevel other, BlockPos otherPos) {
        return level.getBlockEntity(pos) instanceof TrackBlockEntity track && track.boundLocation != null
                && other.dimension().equals(track.boundLocation.getFirst())
                && otherPos.equals(track.boundLocation.getSecond());
    }

    private static void holdGround(ServerLevel level, BlockPos pos) {
        holdGround(level, pos, 1);
    }

    private static void holdGround(ServerLevel level, BlockPos pos, int radius) {
        Holds.ground(level, RAIL_WORKS, new ChunkPos(pos), radius);
    }

    private static RailLane settle(RailLaneRegistry lanes, RailLane lane, RailLaneState state) {
        if (lane.state() == state) {
            return lane;
        }
        RailLane next = lane.withState(state);
        lanes.put(next);
        return next;
    }

    // --------------------------------------------------------------- re-route

    /**
     * Turns every lane of a gate towards a new far gate: each one's old stub taken up, and a new one laid out of
     * the new gate as soon as it is loaded.
     */
    public static void reroute(MinecraftServer server, UUID gate, @Nullable UUID target) {
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        for (RailLane lane : lanes.ownedBy(gate)) {
            if (java.util.Objects.equals(lane.far(), target) && lane.ready()) {
                continue;
            }
            takeUpFar(server, lane);
            RailLane turned = lane.routedTo(target);
            lanes.put(turned);
            Trace.log(Trace.RAIL, "lane {} re-routed from {} to {}", lane.id(), lane.far(), target);
            if (target != null) {
                layFar(server, turned);
            }
        }
    }

    /**
     * Lays the far end of every lane joining two gates that has none, now that both are loaded - which is what a
     * dial between them means. This is also how a far end found blocked, or a stub somebody took up, is tried again:
     * on the next dial, not under their feet while they are clearing the way.
     */
    public static void layBetween(MinecraftServer server, UUID one, UUID other) {
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        for (RailLane lane : lanes.touching(one)) {
            if (lane.joins(one, other) && !lane.ready()) {
                layFar(server, lane.state() == RailLaneState.FAR_BLOCKED || lane.state() == RailLaneState.NO_ROUTE
                        ? lane.withState(RailLaneState.FAR_PENDING) : lane);
            }
        }
    }

    /** Takes a lane up entirely - near end, corridor, far end - and forgets it. */
    public static void tearDown(MinecraftServer server, RailLane lane, String why) {
        takeUpFar(server, lane);
        takeUpNear(server, lane);
        ServerLevel corridor = RailCorridor.level(server);
        if (corridor != null) {
            clearCorridor(corridor, lane.slot(), lane.length());
        }
        RailLaneRegistry.get(server).remove(lane.id());
        Trace.log(Trace.RAIL, "lane {} into {} taken up: {}", lane.id(), lane.gate(), why);
    }

    // ------------------------------------------------------------------ tick

    /**
     * Corridor ends Create is about to lay - a provider hands Create the place and Create lays the track there once
     * the provider has returned - to be joined into the graph on the next tick, when they are there.
     */
    private static final java.util.Set<BlockPos> TO_JOIN = new java.util.HashSet<>();

    /** Asks for a corridor end Create is laying now to be joined into the graph next tick. */
    static void joinSoon(BlockPos corridorTrack) {
        TO_JOIN.add(corridorTrack.immutable());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!TO_JOIN.isEmpty()) {
            ServerLevel corridor = RailCorridor.level(server);
            if (corridor != null) {
                for (BlockPos pos : TO_JOIN) {
                    if (corridor.isLoaded(pos)) {
                        joinGraph(corridor, pos);
                    }
                }
            }
            TO_JOIN.clear();
        }
        long now = server.overworld().getGameTime();
        if (now % WORK_INTERVAL != 0L) {
            return;
        }
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        for (RailLane lane : lanes.all()) {
            if (lane.state() == RailLaneState.FAR_PENDING && lane.far() != null) {
                layFar(server, lane);
            }
        }
        for (RailLaneRegistry.Cleanup owed : lanes.cleanups()) {
            ServerLevel level = server.getLevel(owed.dimension());
            if (level == null) {
                lanes.settled(owed);
                continue;
            }
            holdGround(level, owed.stub());
            if (level.isLoaded(owed.stub())) {
                settle(server, level, owed, null);
            }
        }
    }
}
