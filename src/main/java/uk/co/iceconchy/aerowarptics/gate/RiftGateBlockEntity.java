package uk.co.iceconchy.aerowarptics.gate;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.airship.Fastenings;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.rail.RailCorridor;
import uk.co.iceconchy.aerowarptics.rail.RailGateLogic;
import uk.co.iceconchy.aerowarptics.rail.RailLane;
import uk.co.iceconchy.aerowarptics.rail.RailLaneMap;
import uk.co.iceconchy.aerowarptics.rail.RailLaneRegistry;
import uk.co.iceconchy.aerowarptics.rail.RailLaneState;
import uk.co.iceconchy.aerowarptics.rail.RailTransit;
import uk.co.iceconchy.aerowarptics.rail.RailWorks;
import uk.co.iceconchy.aerowarptics.rail.RiftPortalTracks;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Rift Gate: a ring of frame with a hole in space standing in it.
 *
 * <p>Everything a gate is lives on this one block. The frame around it is inert - it is a wall, and a
 * wall does not need a block entity each - so the controller carries the identity, the opening it
 * found, the essence, the shaft and the connection.
 *
 * <h2>What it costs</h2>
 * Essence strikes a connection and rotation holds it. A gate standing dark costs nothing at all: its
 * stress impact is zero until it has an aperture, which is what stops a player being punished for
 * building a doorway they only use twice a day. Let the shaft stop while it is open and the connection
 * drops - that is the whole of what makes rotation the holding cost rather than decoration.
 *
 * <h2>What may pass</h2>
 * The opening is the size limit. Anything wider or taller is refused rather than half-swallowed:
 * there is nowhere at the far end for it to be that is not inside the frame it would be arriving
 * through.
 *
 * <h2>The passage</h2>
 * A mob, an item or an empty vehicle that crosses is at the far gate in the same tick, and that is the
 * end of it. A player is too - but for them it is the start of a passage: {@link GatePassages} holds them
 * there, unseen and unharmed, while their client draws the journey, and it is the passage that says they
 * have arrived. A vehicle with a crew aboard is held with them. So both movers below have two endings,
 * and anybody already in a passage is none of a gate's business at all.
 *
 * <h2>The pane</h2>
 * A gate holding a connection fills its opening with Rift Portal blocks and empties it again
 * afterwards. That is the whole of what a player sees of the hole; it used to be a client-side
 * aperture renewed at every viewer, and it is a block now for the reasons on {@link RiftPortalBlock}.
 * Nothing about a crossing goes through it - see {@code tickTraversal}, which is still the only thing
 * here that may move anybody.
 */
public class RiftGateBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    /** How much essence a gate can hold. Enough for a good many dials without constant refilling. */
    public static final int CAPACITY = 8 * AWFluids.BUCKET;

    /** Ticks between checks that the ring is still a ring. Formation is event-driven; this is a net. */
    private static final int VALIDATE_INTERVAL = 20;

    /** Ticks a traveller is ignored for after arriving, so an arrival is not read as a departure. */
    private static final int REENTRY_TICKS = 20;

    /** How far either side of the plane a crossing is looked for. */
    private static final double CATCHMENT_DEPTH = 3.0D;

    /** Ticks the aperture takes to fall away once let go of. */
    private static final int CLOSE_TICKS = 20;

    private UUID gateId;
    /**
     * Whoever set this controller down, or {@code null} for a gate from before gates had owners.
     *
     * <p>Kept on the block and not only in the registry's record, because that record is thrown away when
     * the ring comes apart and made afresh when it is mended.
     */
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /**
     * What this gate was called, who it was open to and which network it was on when its ring last came
     * apart, to be given back when it is mended.
     *
     * <p>A gate's record goes when its ring does. Mended, it used to come back unnamed and public - so
     * knocking one block out of a private gate's ring and putting it back opened that gate to everybody.
     */
    private String keptName = "";
    private WarpAnchorAccess keptAccess = WarpAnchorAccess.PUBLIC;
    @Nullable
    private UUID keptNetwork;
    @Nullable
    private RiftGateShape shape;
    private RiftGateState state = RiftGateState.UNFORMED;
    private int stateTicks;

    /** The gate this one is holding a connection to, whichever end dialled it. */
    @Nullable
    private UUID connected;
    /** Whether this end struck the connection, and so is the end paying for it. */
    private boolean dialler;
    /** The gate last chosen at this controller, kept so a redial does not mean re-picking. */
    @Nullable
    private UUID destination;

    private GateFailure lastFailure = GateFailure.NONE;
    private int validateTimer;
    private int ticketTimer;
    /** Ticks until the next upkeep charge. Not saved: a sub-second countdown is not worth a tag. */
    private int upkeepTimer;

    /**
     * Whether a train, rather than a player, struck this connection - which makes it the gate's to shut again once
     * trains are done with it. A connection a player struck is theirs, and is left alone.
     */
    private boolean railOpened;
    /** Ticks a train-struck connection has stood with no train waiting or running through. Not saved. */
    private int railIdleTicks;
    /**
     * The cells of the opening a rail lane runs through, either end. They hold the pane while the gate is open and
     * a {@link RiftSocketBlock} while it is shut, and are never left empty - see that class for why. Server only,
     * and read from the lane registry, which is the authority.
     */
    private Set<BlockPos> laneCells = Set.of();

    /** Travellers who have just arrived, and must not immediately be sent back. */
    private final Map<UUID, Integer> settling = new HashMap<>();

    /** Which side of the plane each nearby traveller was on when the gate last looked. */
    private final GateWatch watch = new GateWatch();

    private final FluidTank tank = new FluidTank(CAPACITY,
            stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    public RiftGateBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_GATE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    // ------------------------------------------------------------- identity

    public UUID gateId() {
        if (gateId == null) {
            gateId = UUID.randomUUID();
            setChanged();
        }
        return gateId;
    }

    @Nullable
    public RiftGateShape shape() {
        return shape;
    }

    public RiftGateState state() {
        return state;
    }

    public GateFailure lastFailure() {
        return lastFailure;
    }

    /**
     * The airship this gate's controller is bolted to, or {@code null} when it stands on the ground.
     *
     * <p>Looked up from the plot grid rather than waited for, the same reason the Astrolabe and the
     * probe do it that way: Sable only registers an actor when a block changes in the plot, so a gate
     * that arrived on an already-assembled hull may never be told it is aboard one.
     */
    @Nullable
    public Airship airship() {
        if (level == null || level.isClientSide) {
            return null;
        }
        if (!(Sable.HELPER.getContaining(level, worldPosition) instanceof ServerSubLevel subLevel)
                || subLevel.isRemoved()) {
            return null;
        }
        Airship airship = Airship.of(subLevel);
        return airship.isActive() ? airship : null;
    }

    @Nullable
    public UUID destination() {
        return destination;
    }

    @Nullable
    public UUID connected() {
        return connected;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    public int essence() {
        return tank.getFluidAmount();
    }

    public boolean isFormed() {
        return shape != null && state != RiftGateState.UNFORMED;
    }

    // ------------------------------------------------------------ formation

    /**
     * Looks for a ring around this controller and forms a gate out of it.
     *
     * <p>Called when the blocks around it change rather than on a clock, because the moment a ring
     * becomes a ring is the moment somebody placed the last block of it - there is nothing for a poll
     * to notice that placement does not.
     */
    public boolean tryForm() {
        if (level == null || level.isClientSide) {
            return false;
        }
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(worldPosition, this::isFrame, this::isOpen);
        if (opening == null) {
            Trace.changed("gate-form:" + worldPosition.asLong(), Trace.GATE,
                    "{} is not a complete ring with a clear opening", Trace.pos(worldPosition));
            unform();
            return false;
        }
        Trace.changed("gate-form:" + worldPosition.asLong(), Trace.GATE, "{} formed: {}x{} opening on the {} axis",
                Trace.pos(worldPosition), opening.shape().width(), opening.shape().height(), opening.shape().span());
        shape = opening.shape();
        if (state == RiftGateState.UNFORMED) {
            state = RiftGateState.IDLE;
            stateTicks = 0;
        }
        registerSelf();
        notifyUpdate();
        return true;
    }

    /** Drops any connection and forgets the opening. The gate is a pile of blocks again. */
    public void unform() {
        if (state == RiftGateState.UNFORMED && shape == null) {
            return; // already nothing; saying so again is a block update a second, forever
        }
        if (state.hasAperture()) {
            hangUp(GateFailure.NOT_FORMED);
        }
        // Rail lanes go with the ring. Their cells are emptied with the rest of the opening first - no socket
        // kept - so that the tracks they leave behind find no portal there and stay ordinary track.
        List<RailLane> lanes = level instanceof ServerLevel here && gateId != null
                ? RailLaneRegistry.get(here.getServer()).touching(gateId) : List.of();
        laneCells = Set.of();
        // After the hang-up, not instead of it: `hangUp` moves to CLOSING, which still counts as
        // having an aperture, so nothing has taken the pane down yet and there is about to be no
        // shape left to take it down with.
        setAperture(shape, false);
        if (level instanceof ServerLevel here && !lanes.isEmpty()) {
            takeUpLanes(here, lanes);
        }
        shape = null;
        state = RiftGateState.UNFORMED;
        stateTicks = 0;
        if (level instanceof ServerLevel serverLevel && gateId != null) {
            RiftGateRegistry registry = RiftGateRegistry.get(serverLevel);
            RiftGate record = registry.byId(gateId);
            if (record != null) {
                keptName = record.name();
                keptAccess = record.access();
                keptNetwork = record.network();
                setChanged();
            }
            registry.remove(gateId);
        }
        notifyUpdate();
    }

    private boolean isFrame(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(AWBlocks.RIFT_GATE_FRAME.get()) || state.is(AWBlocks.RIFT_GATE.get());
    }

    /**
     * Whether a cell counts as part of the opening.
     *
     * <p>The gate's own pane counts. It has to: the ring is re-flood-filled every few seconds to
     * catch a frame block being knocked out, and a gate that stopped recognising the hole the moment
     * it filled it would unform itself one tick after opening.
     */
    private boolean isOpen(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // A socket too: it is a rail lane's cell of this very opening, standing in for the pane while the gate
        // is shut, and a ring that stopped recognising it would unform the moment a lane was laid.
        return RiftSocketBlock.isLaneCell(state) || state.isAir() || state.canBeReplaced();
    }

    // ------------------------------------------------------------------ the pane

    /**
     * Puts the pane of Rift Portal blocks into an opening, or takes it out again.
     *
     * <p>Only ever into space the gate already counted as part of its opening, and only ever removing
     * its own blocks. Those two together are what stop a doorway eating a build: the fill takes air
     * and things that are replaceable anyway - the water or the grass that {@code isOpen} let the ring
     * form around in the first place - and the empty puts back air only where the pane itself stands,
     * never clearing whatever is in the cell by the time the gate shuts.
     *
     * <p>Takes the shape as an argument rather than reading the field, because the one case that
     * needs care is a ring being rebuilt into a different shape: the old cells have to be emptied
     * before the new ones are filled, and by then the field is the new shape.
     *
     * <p>Placed with the flags a Nether portal uses rather than a full update. There is nothing in
     * these cells for a neighbour update to tell anybody about, and a thirty-block gate opening would
     * otherwise fire two hundred of them in one tick.
     */
    private void setAperture(@Nullable RiftGateShape which, boolean present) {
        if (which == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Newly placed panes always start OPENING - registerDefaultState's own default - and the
        // caller sets the stage that actually belongs to the transition right afterwards. See
        // setPortalStage.
        BlockState pane = AWBlocks.RIFT_PORTAL.get().defaultBlockState()
                .setValue(RiftPortalBlock.AXIS, which.span());
        // A rail lane's cell is never emptied: shut, it gets a socket instead of air, because a Create portal track
        // pointing into a cell that is not a portal turns itself to air - see RiftSocketBlock.
        BlockState socket = AWBlocks.RIFT_SOCKET.get().defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos cell : which.cells()) {
            BlockState there = serverLevel.getBlockState(cell);
            boolean lane = laneCells.contains(cell);
            if (!present) {
                if (there.is(pane.getBlock()) || there.is(socket.getBlock()) && !lane) {
                    serverLevel.setBlock(cell, lane ? socket : Blocks.AIR.defaultBlockState(), flags);
                } else if (lane && (there.isAir() || there.canBeReplaced())) {
                    serverLevel.setBlock(cell, socket, flags);
                }
            } else if (there != pane && (there.isAir() || there.canBeReplaced() || there.is(socket.getBlock()))) {
                serverLevel.setBlock(cell, pane, flags);
            }
        }
    }

    /**
     * Reads which cells of the opening a rail lane runs through, either end, from the lane registry.
     *
     * <p>Asked every time the pane is put in or taken out, and on every revalidation, rather than kept up to date
     * by being told: a lane laid out of this gate from somewhere else entirely is laid while this gate's block
     * entity may not even be loaded.
     */
    private void refreshLaneCells() {
        if (level instanceof ServerLevel serverLevel && gateId != null && shape != null) {
            laneCells = RailLaneRegistry.get(serverLevel.getServer()).laneCells(gateId);
        }
    }

    /** A lane has been laid into or out of this gate, so its cells may need a socket now. */
    public void laneCellsChanged() {
        refreshLaneCells();
        if (shape != null && !state.hasAperture()) {
            setAperture(shape, false);
        }
    }

    /**
     * Writes what a held pane is doing onto every cell of it: forming, standing, or falling apart.
     *
     * <p>This is the opening and closing animation. The pane already exists by the time this is
     * called - {@link #setAperture} put it there - so all this does is swap which texture the block
     * points at, which costs nothing per tick because a blockstate is not a thing anybody has to
     * tick. See {@link RiftPortalStage} for why that texture is a looping motif rather than a literal
     * progress bar.
     */
    private void setPortalStage(@Nullable RiftGateShape which, RiftPortalStage stage) {
        if (which == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos cell : which.cells()) {
            BlockState there = serverLevel.getBlockState(cell);
            if (!there.is(AWBlocks.RIFT_PORTAL.get()) || there.getValue(RiftPortalBlock.STAGE) == stage) {
                continue;
            }
            serverLevel.setBlock(cell, there.setValue(RiftPortalBlock.STAGE, stage), flags);
        }
    }

    private void registerSelf() {
        if (!(level instanceof ServerLevel serverLevel) || shape == null) {
            return;
        }
        RiftGateRegistry registry = RiftGateRegistry.get(serverLevel);
        RiftGate existing = registry.byId(gateId());
        if (existing != null) {
            // A record with no owner is one made before the block knew who placed it - the gate forms as the
            // controller goes down, a moment before anybody is told who set it there.
            registry.register(existing.owner() == null && owner != null
                    ? existing.withShape(shape).withOwner(owner, ownerName) : existing.withShape(shape));
            reviveLanes(serverLevel);
            return;
        }
        // A network that has gone, or that the owner has left, while the ring was down is not come back to.
        UUID network = NetworkRules.mayStay(WarpNetworkRegistry.get(serverLevel).byId(keptNetwork), owner)
                ? keptNetwork : null;
        WarpAnchorAccess access = keptAccess == WarpAnchorAccess.NETWORK && network == null
                ? WarpAnchorAccess.PRIVATE : keptAccess;
        registry.register(RiftGate.create(gateId(), serverLevel.dimension(), worldPosition, shape, owner, ownerName)
                .withAccess(access).withNetwork(network));
        // Last, and allowed to fail: another gate may have taken the name while this one was in pieces.
        registry.rename(gateId(), keptName);
        reviveLanes(serverLevel);
    }

    /**
     * Lanes routed here that were left with nowhere to come out while this gate was in pieces are laid again.
     *
     * <p>A gate keeps its id across a broken ring, so a lane from elsewhere still points at it; it only stopped
     * being something a far end could be laid out of.
     */
    private void reviveLanes(ServerLevel serverLevel) {
        RailLaneRegistry lanes = RailLaneRegistry.get(serverLevel.getServer());
        for (RailLane lane : lanes.comingOutOf(gateId())) {
            if (lane.state() == RailLaneState.NO_ROUTE) {
                lanes.put(lane.withState(RailLaneState.FAR_PENDING));
            }
        }
    }

    /**
     * Takes up every lane that touches this gate, because its ring has come apart.
     *
     * <p>Its own lanes go entirely, the player's track left as ordinary track. Lanes laid out of it from elsewhere
     * lose their far end only: their own gate still has its track and its corridor, and they come out of here again
     * if the ring is mended.
     */
    private void takeUpLanes(ServerLevel serverLevel, List<RailLane> lanes) {
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        for (RailLane lane : lanes) {
            if (lane.gate().equals(gateId)) {
                RailWorks.tearDown(server, lane, "the ring of the gate it runs into came apart");
            } else {
                RailWorks.takeUpFar(server, lane);
                RailLaneRegistry.get(server).put(lane.routedTo(lane.far()).withState(RailLaneState.NO_ROUTE));
            }
        }
    }

    /**
     * Records who set this controller down, so the gate has an owner.
     *
     * <p>The ring is usually finished by placing the controller, so the gate has already formed and
     * registered itself, unowned, by the time this is called - which is why it claims the record as well.
     */
    public void onPlaced(@Nullable Player placer) {
        if (placer == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        owner = placer.getUUID();
        ownerName = placer.getGameProfile().getName();
        setChanged();
        RiftGateRegistry registry = RiftGateRegistry.get(serverLevel);
        RiftGate record = registry.byId(gateId());
        if (record != null && record.owner() == null) {
            registry.register(record.withOwner(owner, ownerName));
        }
        Trace.log(Trace.GATE, "{} set down by {}, who owns it", Trace.pos(worldPosition), ownerName);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    // -------------------------------------------------------------- dialling

    /**
     * Strikes a connection to another gate.
     *
     * <p>Every refusal is named rather than being a silent no-op, because a gate that will not open
     * has exactly one interesting property and it is why.
     */
    public GateFailure dial(@Nullable ServerPlayer player, @Nullable UUID target) {
        GateFailure result = tryDial(player, target, false);
        Trace.log(Trace.GATE, "{} dialled {} by {} -> {} (state {} essence {}/{} rpm {} needs {})",
                Trace.pos(worldPosition), target, player == null ? "a circuit" : Trace.who(player),
                result.getSerializedName(), state, tank.getFluidAmount(), dialCost(),
                Trace.num(Math.abs(getSpeed())), AWConfig.GATE_MINIMUM_RPM.get());
        return result;
    }

    /**
     * Strikes a connection for a train waiting at this gate, to the far end of the lane it is waiting in.
     *
     * <p>Exactly a dial - the same essence, the same rotation, the same refusals - except that nobody is standing
     * here, so nobody's access is asked (whoever laid the lane could dial it), the far gate may be in another
     * dimension if the server allows rail routes there, and the gate's route is not touched: a train coming back
     * through a lane laid out of this gate from elsewhere dials that lane's own gate, not wherever this one is routed.
     * A connection struck this way is the gate's to shut again, once trains are done with it.
     */
    public GateFailure dialForTrain(UUID target) {
        GateFailure result = tryDial(null, target, true);
        Trace.log(Trace.RAIL, "{} dialled {} for a train -> {} (state {} essence {}/{} rpm {} needs {})",
                Trace.pos(worldPosition), target, result.getSerializedName(), state, tank.getFluidAmount(), dialCost(),
                Trace.num(Math.abs(getSpeed())), AWConfig.GATE_MINIMUM_RPM.get());
        return result;
    }

    private GateFailure tryDial(@Nullable ServerPlayer player, @Nullable UUID target, boolean forTrain) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return GateFailure.NOT_FORMED;
        }
        if (shape == null || state == RiftGateState.UNFORMED) {
            return fail(GateFailure.NOT_FORMED);
        }
        if (target == null) {
            return fail(GateFailure.NO_DESTINATION);
        }
        if (target.equals(gateId())) {
            return fail(GateFailure.DESTINATION_IS_SELF);
        }

        RiftGateRegistry registry = RiftGateRegistry.get(serverLevel);
        RiftGate far = registry.byId(target);
        if (far == null) {
            return fail(GateFailure.DESTINATION_MISSING);
        }
        if (!far.enabled()) {
            return fail(GateFailure.DESTINATION_DISABLED);
        }
        if (!far.isInSameDimension(serverLevel) && !(forTrain && railsCrossDimensions())) {
            // A person can never follow a train into another dimension through a gate, so a route there is
            // something to set, not to dial - and said so, rather than as a plain "another dimension".
            return fail(railsCrossDimensions() ? GateFailure.DESTINATION_RAIL_ONLY
                    : GateFailure.DESTINATION_ANOTHER_DIMENSION);
        }
        if (player != null && !far.isVisibleTo(player, WarpNetworkRegistry.get(serverLevel))) {
            return fail(GateFailure.UNAUTHORISED);
        }
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (!forTrain && player != null && !target.equals(destination)
                && !RailLaneRegistry.get(server).ownedBy(gateId()).isEmpty()) {
            // Dialling elsewhere turns this gate's track elsewhere too - a whole railway's worth of routing - so it is
            // the gate's owner's to do, not every passer-by's who may dial.
            RiftGate own = RiftGateRegistry.get(serverLevel).byId(gateId());
            if (own != null && !own.mayEdit(player)) {
                return fail(GateFailure.UNAUTHORISED);
            }
            if (RailTransit.ownLanesInTransit(server, gateId())) {
                return fail(GateFailure.RAIL_TRAIN_IN_TRANSIT);
            }
        }
        ServerLevel farLevel = server.getLevel(far.dimension());
        if (farLevel == null) {
            return fail(GateFailure.DESTINATION_MISSING);
        }

        // The far end has to be loaded before anything else can be said about it. This is the one
        // place a gate is allowed to pull chunks in, and it is a deliberate player action - or a train's.
        GateTicket.hold(farLevel, far.shape().centre(), Math.max(far.shape().width(), far.shape().height()));
        RiftGateBlockEntity farGate = resolve(farLevel, far.controller());
        if (farGate == null && Holds.inPlot(farLevel, far.controller())
                && Holds.shipAt(farLevel, far.controller()) != null) {
            // Aboard a ship that has sailed off and unloaded. A plot is not ground that loads on request -
            // the hold above has instead started bringing the ship back where it was last seen, which takes
            // longer than a dial does. Said as what it is, rather than as a broken ring nobody can mend.
            return fail(GateFailure.DESTINATION_LOADING);
        }
        if (farGate == null || !farGate.isFormed()) {
            return fail(GateFailure.DESTINATION_UNFORMED);
        }
        if (farGate.state.engaged() && !gateId().equals(farGate.connected)) {
            return fail(GateFailure.DESTINATION_BUSY);
        }

        int cost = dialCost();
        if (tank.getFluidAmount() < cost) {
            return fail(GateFailure.INSUFFICIENT_ESSENCE);
        }
        if (!isSpinningFastEnough()) {
            return fail(GateFailure.INSUFFICIENT_POWER);
        }

        tank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
        if (!forTrain) {
            UUID previous = destination;
            destination = target;
            if (!target.equals(previous)) {
                RailWorks.reroute(server, gateId(), target);
            }
        }
        railOpened = forTrain;
        railIdleTicks = 0;
        connect(target, true);
        farGate.connect(gateId(), false);
        // Both ends are loaded now, which is the moment a lane's far end that could not be laid before can be.
        RailWorks.layBetween(server, gateId(), target);
        lastFailure = GateFailure.NONE;
        return GateFailure.NONE;
    }

    private static boolean railsCrossDimensions() {
        return AWConfig.RAIL_GATES.get() && AWConfig.RAIL_CROSS_DIMENSION.get();
    }

    /**
     * Sets where this gate's rail lanes come out, without dialling.
     *
     * <p>A route is the gate's destination - the gate a dial from here strikes, and the gate every lane run into it
     * comes out of. Setting it here costs nothing, opens nothing, and may name a gate in another dimension, which a
     * dial may not: that is the only way a gate is routed to one.
     */
    public GateFailure setRoute(ServerPlayer player, @Nullable UUID target) {
        GateFailure result = trySetRoute(player, target);
        Trace.log(Trace.RAIL, "{} routed to {} by {} -> {}", Trace.pos(worldPosition), target, Trace.who(player),
                result.getSerializedName());
        return result;
    }

    private GateFailure trySetRoute(ServerPlayer player, @Nullable UUID target) {
        if (!(level instanceof ServerLevel serverLevel) || shape == null || state == RiftGateState.UNFORMED) {
            return fail(GateFailure.NOT_FORMED);
        }
        if (target == null) {
            return fail(GateFailure.NO_DESTINATION);
        }
        if (target.equals(gateId())) {
            return fail(GateFailure.DESTINATION_IS_SELF);
        }
        RiftGateRegistry registry = RiftGateRegistry.get(serverLevel);
        RiftGate own = registry.byId(gateId());
        RiftGate far = registry.byId(target);
        if (far == null) {
            return fail(GateFailure.DESTINATION_MISSING);
        }
        if (!far.enabled()) {
            return fail(GateFailure.DESTINATION_DISABLED);
        }
        if (own != null && !own.mayEdit(player) || !far.isVisibleTo(player, WarpNetworkRegistry.get(serverLevel))) {
            return fail(GateFailure.UNAUTHORISED);
        }
        if (!far.isInSameDimension(serverLevel) && !railsCrossDimensions()) {
            return fail(GateFailure.DESTINATION_ANOTHER_DIMENSION);
        }
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (target.equals(destination)) {
            // The same route again: worth something all the same, as the way to have a far end that was found
            // blocked tried again.
            RailWorks.layBetween(server, gateId(), target);
            lastFailure = GateFailure.NONE;
            notifyUpdate();
            return GateFailure.NONE;
        }
        if (!RailGateLogic.mayChange(RailTransit.ownLanesInTransit(server, gateId()))) {
            return fail(GateFailure.RAIL_TRAIN_IN_TRANSIT);
        }
        destination = target;
        setChanged();
        RailWorks.reroute(server, gateId(), target);
        lastFailure = GateFailure.NONE;
        notifyUpdate();
        return GateFailure.NONE;
    }

    /** Brings this end up, and remembers which end is paying. */
    private void connect(UUID other, boolean paying) {
        connected = other;
        dialler = paying;
        if (!paying) {
            railOpened = false; // only the end that dialled shuts a train's connection
        }
        // A full interval before the first charge: the dial cost has only just been taken, and
        // billing again on the same tick reads as the dial costing more than it said it would.
        upkeepTimer = AWConfig.GATE_UPKEEP_INTERVAL.get();
        transition(RiftGateState.DIALLING);
        playSound(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.9F, 0.7F);
    }

    /** Lets go of a connection at both ends. */
    public void hangUp(GateFailure reason) {
        if (!state.hasAperture()) {
            return;
        }
        Trace.log(Trace.GATE, "{} hanging up on {}: {} (essence {} upkeep {} rpm {})", Trace.pos(worldPosition),
                connected, reason == GateFailure.NONE ? "closed" : reason.getSerializedName(), tank.getFluidAmount(),
                upkeepCost(), Trace.num(Math.abs(getSpeed())));
        UUID other = connected;
        boolean wasPaying = dialler;
        connected = null;
        dialler = false;
        railOpened = false;
        railIdleTicks = 0;
        lastFailure = reason;
        transition(RiftGateState.CLOSING);
        playSound(SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.8F);
        if (wasPaying) {
            // Still showing an aperture, so `transition` saw no change worth reporting - but this end
            // has stopped paying for it, and the network is entitled to know that straight away.
            refreshKinetics();
        }

        if (level instanceof ServerLevel serverLevel && other != null) {
            RiftGate far = RiftGateRegistry.get(serverLevel).byId(other);
            ServerLevel farLevel = far == null ? null : serverLevel.getServer().getLevel(far.dimension());
            if (far != null && farLevel != null) {
                RiftGateBlockEntity farGate = resolve(farLevel, far.controller());
                // Only if it is still pointed at us: a gate that has since been re-dialled elsewhere
                // is somebody else's connection now and closing it would be closing a stranger's door.
                if (farGate != null && gateId().equals(farGate.connected)) {
                    farGate.connected = null;
                    farGate.dialler = false;
                    farGate.transition(RiftGateState.CLOSING);
                    farGate.watch.clear();
                }
            }
        }
    }

    /**
     * A player's Hang Up: refused while a train is part of the way through, which would otherwise be cut in two -
     * Create keeps a carriage that is half through a pane in both dimensions, and a shut gate cannot be gone on
     * through.
     */
    public GateFailure playerHangUp() {
        if (!state.hasAperture()) {
            return GateFailure.NONE;
        }
        if (level instanceof ServerLevel serverLevel
                && !RailGateLogic.mayChange(RailTransit.inTransitBetween(serverLevel.getServer(), gateId(), connected))) {
            Trace.log(Trace.RAIL, "{} would not hang up on {}: a train is part of the way through",
                    Trace.pos(worldPosition), connected);
            return fail(GateFailure.RAIL_TRAIN_IN_TRANSIT);
        }
        hangUp(GateFailure.NONE);
        return GateFailure.NONE;
    }

    private GateFailure fail(GateFailure reason) {
        lastFailure = reason;
        notifyUpdate();
        return reason;
    }

    @Nullable
    private static RiftGateBlockEntity resolve(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RiftGateBlockEntity gate ? gate : null;
    }

    private void transition(RiftGateState next) {
        if (state == next) {
            return;
        }
        boolean apertureChanged = state.hasAperture() != next.hasAperture();
        state = next;
        stateTicks = 0;
        if (apertureChanged) {
            refreshLaneCells();
            refreshKinetics();
            // The pane goes in as the gate starts dialling and comes out when it has finished
            // closing, which is what `hasAperture` already means everywhere else.
            setAperture(shape, next.hasAperture());
        }
        if (next.hasAperture()) {
            // On every transition into or within an aperture, not only the ones that change whether
            // there is one: DIALLING and OPEN both have a pane standing and are meant to look
            // different, which is the whole point of an opening animation being visible, and OPEN
            // into CLOSING is the one transition where the pane stays put and only the stage moves.
            setPortalStage(shape, RiftPortalStage.of(next));
        }
        notifyUpdate();
    }

    /**
     * Makes the kinetic network ask what this gate costs again.
     *
     * <p>Create caches a block's stress impact, so an impact that depends on what the machine is doing
     * has to say when that changes - both when an aperture appears or goes, and when this end stops
     * being the one paying for it.
     */
    private void refreshKinetics() {
        detachKinetics();
        lastStressApplied = -1.0F;
        attachKinetics();
    }

    // ------------------------------------------------------------------ cost

    /** Essence to strike a connection. Bigger doorway, bigger tear. */
    public int dialCost() {
        int area = shape == null ? 0 : shape.area();
        return AWConfig.GATE_DIAL_COST.get() + AWConfig.GATE_DIAL_COST_PER_BLOCK.get() * area;
    }

    /**
     * Essence to hold the connection standing, per upkeep interval.
     *
     * <p>A doorway is not a thing you build and then have; it is a tear somebody is holding apart,
     * and it costs for as long as it is held. Dialling still buys the tear - that is the expensive
     * moment - but leaving one open across a base now runs a bill, which is what makes a Spatial
     * Siphon's output something you plan around rather than something you accumulate.
     *
     * <p>Scaled by area on the same reasoning as the dial cost: a wider doorway is more of a hole.
     */
    public int upkeepCost() {
        int area = shape == null ? 0 : shape.area();
        return AWConfig.GATE_UPKEEP_COST.get() + AWConfig.GATE_UPKEEP_COST_PER_BLOCK.get() * area;
    }

    /** Ticks between upkeep charges. */
    public static int upkeepInterval() {
        return AWConfig.GATE_UPKEEP_INTERVAL.get();
    }

    /**
     * An interval in whole seconds, for the reading beside the cost.
     *
     * <p>Rounded up rather than to nearest, so a sub-second interval reads as "every 1s" instead of
     * "every 0s", which is a reading that says the gate costs nothing per no time at all.
     */
    private static int secondsOf(int ticks) {
        return Math.max(1, (ticks + 19) / 20);
    }

    @Override
    public float calculateStressApplied() {
        // The far end of a connection draws nothing, for the same reason it needs no rotation: it is
        // not the end holding the aperture open, it is the end one was opened onto.
        if (!state.hasAperture() || !dialler || shape == null) {
            this.lastStressApplied = 0.0F;
            return 0.0F;
        }
        float impact = (float) (AWConfig.GATE_STRESS.get()
                + AWConfig.GATE_STRESS_PER_BLOCK.get() * shape.area());
        this.lastStressApplied = impact;
        return impact;
    }

    public boolean isSpinningFastEnough() {
        return Math.abs(getSpeed()) >= AWConfig.GATE_MINIMUM_RPM.get();
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            // Nothing to draw. The opening used to be a client-side aperture renewed from here every
            // tick; it is a pane of Rift Portal blocks now, so it arrives with the chunk, lights
            // itself, and is already standing for a player who walks up long after it opened.
            return;
        }
        stateTicks++;

        if (--validateTimer <= 0) {
            validateTimer = VALIDATE_INTERVAL;
            revalidate();
        }

        // Not setValue: that returns the old value, so the count would run one tick long.
        settling.replaceAll((id, ticks) -> ticks - 1);
        settling.values().removeIf(ticks -> ticks <= 0);

        switch (state) {
            case DIALLING -> {
                if (!holdable()) {
                    return;
                }
                if (stateTicks >= AWConfig.GATE_DIAL_TICKS.get()) {
                    transition(RiftGateState.OPEN);
                    playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.4F);
                }
            }
            case OPEN -> {
                if (!holdable()) {
                    return;
                }
                renewTicket();
                tickTraversal();
                lingerForTrains();
            }
            case CLOSING -> {
                if (stateTicks >= CLOSE_TICKS) {
                    transition(RiftGateState.IDLE);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Shuts a connection a train struck, once nothing has used it for {@code lingerTicks}.
     *
     * <p>Only at the end that dialled, and only for a connection a train struck: one a player struck is theirs to
     * hang up. A second train close behind the first keeps it open - it is waiting at the pane, or on its way with
     * the gate on its path - and so does one still running through.
     */
    private void lingerForTrains() {
        if (!railOpened || !dialler || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean busy = RailTransit.busy(serverLevel.getServer(), gateId(), connected);
        railIdleTicks = busy ? 0 : railIdleTicks + 1;
        if (RailGateLogic.shutsNow(true, busy, railIdleTicks, AWConfig.RAIL_LINGER_TICKS.get())) {
            Trace.log(Trace.RAIL, "{} shutting the connection to {} it struck for trains: none for {} ticks",
                    Trace.pos(worldPosition), connected, railIdleTicks);
            hangUp(GateFailure.NONE);
        }
    }

    /**
     * Whether the connection can still stand.
     *
     * <p>Rotation is checked <em>only at the end that struck the connection</em>. A gate at the far
     * end is a doorway somebody built at a mine and walked away from: it needs to be there, and it
     * needs to be a gate, and that is all. Requiring a working drive at both ends made every remote
     * gate hang up on its first tick and take the dialling one down with it, which reads as a gate
     * that simply does not work.
     */
    private boolean holdable() {
        if (shape == null) {
            hangUp(GateFailure.NOT_FORMED);
            return false;
        }
        if (dialler && (!isSpinningFastEnough() || isOverStressed())) {
            hangUp(GateFailure.INSUFFICIENT_POWER);
            return false;
        }
        if (dialler && !payUpkeep()) {
            hangUp(GateFailure.INSUFFICIENT_ESSENCE);
            return false;
        }
        return true;
    }

    /**
     * Takes this interval's essence out of the tank, if one has come due.
     *
     * <p>Charged at the dialling end only, on the same rule as the stress and for the same reason:
     * the far end is a doorway somebody built at a mine and walked away from, and a destination gate
     * that quietly drained a tank nobody was filling would be a doorway that switches itself off
     * while you are through it.
     *
     * <p>Simulated before it is spent, so a gate that is one millibucket short shuts with that
     * millibucket still in it rather than emptying itself on the way down. The difference matters at
     * the tank window: "not enough" reads as a supply problem, and an empty tank reads as a leak.
     */
    private boolean payUpkeep() {
        if (--upkeepTimer > 0) {
            return true;
        }
        upkeepTimer = AWConfig.GATE_UPKEEP_INTERVAL.get();
        int cost = upkeepCost();
        if (cost <= 0) {
            return true;
        }
        if (tank.drain(cost, IFluidHandler.FluidAction.SIMULATE).getAmount() < cost) {
            return false;
        }
        tank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private void revalidate() {
        if (state == RiftGateState.UNFORMED) {
            tryForm();
            return;
        }
        // A gate aboard a ship says where that ship is, so the far end of a dial can reach for it once it
        // has sailed off and unloaded. Cheap: the ledger only changes when the ship has moved.
        Airship own = Airship.containing(this);
        if (own != null) {
            Holds.noteShip(own);
        }
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(worldPosition, this::isFrame, this::isOpen);
        if (opening == null) {
            unform();
            return;
        }
        if (!opening.shape().equals(shape)) {
            // A ring rebuilt into a different shape. Empty the old cells first: they are not part of
            // the opening any more, and a pane left standing in one is a wall through the new frame.
            setAperture(shape, false);
            shape = opening.shape();
            registerSelf();
            notifyUpdate();
        }
        tendLanes();
        if (state.hasAperture()) {
            // Re-asserted rather than assumed. This is what heals a pane that was interfered with -
            // a cell filled with water, a chunk that came back without it - and it costs a few
            // blockstate reads every few seconds, only while a gate is actually connected. The stage
            // is re-asserted with it so a pane that grew back defaults to OPENING and is immediately
            // corrected to whatever the gate is actually doing.
            setAperture(shape, true);
            setPortalStage(shape, RiftPortalStage.of(state));
        } else {
            // Shut, the same pass puts a socket back in any lane's cell that lost one, and takes away any socket
            // left standing where no lane runs any more.
            setAperture(shape, false);
        }
        // Last, after the sockets are tidied: the socket this puts down for new track is not a lane's yet, and
        // tidying after it would take it straight away again before Create had looked.
        if (level instanceof ServerLevel serverLevel) {
            noticeTracks(serverLevel, RailLaneRegistry.get(serverLevel.getServer()));
        }
    }

    /**
     * Looks after the rail lanes that run through this gate: takes up any whose track has gone, notes any whose
     * far stub here has been taken up, and notices track run up to the gate while it was shut.
     *
     * <p>Track run up to an open gate meets a pane and Create asks for a lane at once. Shut, the cell is empty, so
     * Create sees no portal and lays ordinary track. This notices that track on its next pass, puts a socket in the
     * cell, and has Create look at the track again - and then it finds a portal.
     */
    private void tendLanes() {
        if (!(level instanceof ServerLevel serverLevel) || shape == null) {
            return;
        }
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        Set<BlockPos> cells = new HashSet<>(shape.cells());
        for (RailLane lane : lanes.ownedBy(gateId())) {
            if (!serverLevel.isLoaded(lane.track())) {
                continue;
            }
            boolean cellGone = !cells.contains(lane.cell());
            if (cellGone || !RiftPortalTracks.isBoundPortal(serverLevel, lane.track())) {
                RailWorks.tearDown(server, lane, cellGone ? "its cell is no longer part of the opening"
                        : "its track was taken up");
            }
        }
        for (RailLane lane : lanes.comingOutOf(gateId())) {
            if (lane.state() != RailLaneState.READY || lane.farStub() == null
                    || !serverLevel.dimension().equals(lane.farDimension()) || !serverLevel.isLoaded(lane.farStub())) {
                continue;
            }
            if (!RiftPortalTracks.isBoundPortal(serverLevel, lane.farStub())) {
                // Taken up by somebody here. Left so until the gates are next dialled, rather than put straight
                // back under their pickaxe - see RailWorks.layBetween.
                Trace.log(Trace.RAIL, "lane {}'s stub out of {} was taken up", lane.id(), Trace.pos(worldPosition));
                lanes.put(lane.withState(RailLaneState.FAR_BLOCKED));
            }
        }
        refreshLaneCells();
    }

    private void noticeTracks(ServerLevel serverLevel, RailLaneRegistry lanes) {
        if (!AWConfig.RAIL_GATES.get() || destination == null || state.hasAperture() || airship() != null) {
            return;
        }
        Direction.Axis normal = shape.normal();
        for (int across = 0; across < shape.width(); across++) {
            int up = RailLaneMap.lowestOpen(shape, across);
            if (up < 0) {
                continue;
            }
            BlockPos cell = RailLaneMap.cellAt(shape, across, up);
            BlockState there = serverLevel.getBlockState(cell);
            if (!there.isAir() && !there.canBeReplaced()) {
                continue; // a socket already: track laid against one is a lane the moment it is laid
            }
            for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                BlockPos track = cell.relative(Direction.fromAxisAndDirection(normal, sign));
                if (!serverLevel.isLoaded(track)) {
                    continue;
                }
                BlockState laid = serverLevel.getBlockState(track);
                if (RiftPortalTracks.runsInto(laid, normal) && lanes.atTrack(serverLevel.dimension(), track) == null) {
                    serverLevel.setBlock(cell, AWBlocks.RIFT_SOCKET.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    serverLevel.scheduleTick(track, laid.getBlock(), 1);
                    Trace.log(Trace.RAIL, "{} noticed track at {} run up to it while shut", Trace.pos(worldPosition),
                            Trace.pos(track));
                    break;
                }
            }
        }
    }

    private void renewTicket() {
        if (--ticketTimer > 0 || !(level instanceof ServerLevel serverLevel) || shape == null) {
            return;
        }
        ticketTimer = GateTicket.RENEW_INTERVAL;
        GateTicket.hold(serverLevel, shape.centre(), Math.max(shape.width(), shape.height()));
        RiftGate far = farGate();
        ServerLevel farLevel = far == null ? null : serverLevel.getServer().getLevel(far.dimension());
        if (far != null && farLevel != null) {
            GateTicket.hold(farLevel, far.shape().centre(),
                    Math.max(far.shape().width(), far.shape().height()));
        }
    }

    @Nullable
    private RiftGate farGate() {
        if (connected == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return RiftGateRegistry.get(serverLevel).byId(connected);
    }

    // ------------------------------------------------------------- traversal

    /**
     * Moves whatever went through this tick.
     *
     * <p>Vehicles first. A sub-level carries its own passengers across, so anybody standing on a car
     * has to be dealt with as part of the car rather than separately - handled twice, a driver would
     * be teleported out of their own vehicle mid-crossing.
     */
    private void tickTraversal() {
        if (shape == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        RiftGate far = farGate();
        if (far == null) {
            hangUp(GateFailure.DESTINATION_MISSING);
            return;
        }
        if (!far.isInSameDimension(serverLevel)) {
            // Open for trains to another dimension, which Create carries and nothing else here can follow.
            turnBackWalkers(serverLevel);
            return;
        }
        // Resolved once per tick rather than cached on the field - see Airship's own note on why an
        // instance is a cheap, short-lived view rather than something to hold onto.
        Airship nearAirship = airship();
        Airship farAirship = Airship.containing(serverLevel, far.controller());
        Set<UUID> seen = new HashSet<>();
        moveVehicles(serverLevel, far, nearAirship, farAirship, seen);
        moveEntities(serverLevel, far, nearAirship, farAirship, seen);
        // Anything that has left the catchment is forgotten, so walking away and coming back is a
        // fresh approach rather than half of a crossing recorded minutes ago.
        watch.retain(seen);
    }

    /**
     * Tells anybody who walks through a gate open to another dimension that it is for trains, and leaves them where
     * they are. The crossing is noted as any other is, so it is said once per crossing, not every tick.
     */
    private void turnBackWalkers(ServerLevel serverLevel) {
        Airship nearAirship = airship();
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class,
                worldCatchment(nearAirship, CATCHMENT_DEPTH), candidate -> !candidate.isPassenger())) {
            boolean stepped = stepped(player.getUUID(), toLocalPoint(player.position(), nearAirship), seen);
            if (stepped && !settling.containsKey(player.getUUID())) {
                player.displayClientMessage(
                        AWLang.translate(GateFailure.DESTINATION_RAIL_ONLY.translationKey()).component(), true);
                settling.put(player.getUUID(), REENTRY_TICKS);
            }
        }
        watch.retain(seen);
    }

    // ------------------------------------------------------- plot / world space

    /**
     * A gate's own {@link RiftGateShape} is built straight off {@link BlockPos} coordinates, and for a
     * gate standing on the ground those coordinates <em>are</em> world coordinates - which is the only
     * reason the traversal math below ever worked without knowing Sable exists. A gate whose blocks
     * belong to a sub-level has no such luck: a block entity's {@code BlockPos} there is a position
     * inside the hull's own plot, not a world position (see the note on {@link Airship#containing}),
     * so every point this class compares against an entity's real, world-space position has to cross
     * that boundary explicitly. These four are the crossing point, all of it: world into a gate's own
     * local frame and back, for a position and for a direction, collapsing to the identity the moment
     * the airship handed in is {@code null} - which is every gate this mod has ever supported until
     * now, so the ground case is provably unchanged.
     */
    private static Vec3 toLocalPoint(Vec3 world, @Nullable Airship airship) {
        return airship == null ? world : airship.toShip(world);
    }

    private static Vec3 toWorldPoint(Vec3 local, @Nullable Airship airship) {
        return airship == null ? local : airship.toWorld(local);
    }

    private static Vec3 toLocalDirection(Vec3 world, @Nullable Airship airship) {
        return airship == null ? world : toVec(airship.toShipDirection(toVector3d(world)));
    }

    private static Vec3 toWorldDirection(Vec3 local, @Nullable Airship airship) {
        return airship == null ? local : toVec(airship.toWorldDirection(toVector3d(local)));
    }

    private static Vector3d toVector3d(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    /**
     * The catchment, moved into world space when this gate's own blocks sit on a sub-level.
     *
     * <p>{@link RiftGateShape#catchment} is built entirely from the shape's own {@code BlockPos}
     * bounds, so for a gate on the ground it already <em>is</em> the world-space slab
     * {@code level.getEntities} needs. For a gate on a hull it is a plot-space slab instead, and
     * {@link dev.ryanhcode.sable.companion.math.BoundingBox3d#transform} carries the whole box - full
     * orientation, not just yaw - into the world position the deck actually occupies this tick.
     */
    private AABB worldCatchment(@Nullable Airship airship, double depth) {
        AABB local = shape.catchment(depth);
        return airship == null ? local : new BoundingBox3d(local).transform(airship.pose()).toMojang();
    }

    /**
     * Turns a traveller's own facing across the crossing, the same way their momentum already is.
     *
     * <p>{@link GateTraversal}'s yaw delta only means anything inside a gate's own local frame - the
     * one {@link RiftGateShape}'s axes are defined in, which for a gate bolted to a hull is the deck,
     * not the world. So facing is read off as a direction, turned from world into the near gate's
     * local frame, rotated exactly as momentum already is by {@link GateTraversal#map}, and turned
     * back into world out of the far gate's own local frame. The moment neither end is aboard
     * anything both frames are the world frame, and this reduces to plain addition of the delta - the
     * one thing it is replacing.
     */
    private static float worldYaw(float currentYaw, float localYawDelta,
                                  @Nullable Airship nearAirship, @Nullable Airship farAirship) {
        if (nearAirship == null && farAirship == null) {
            return currentYaw + localYawDelta;
        }
        Vec3 localFacing = toLocalDirection(GateTraversal.directionOfYaw(currentYaw), nearAirship);
        Vec3 turned = GateTraversal.rotateYaw(localFacing, localYawDelta);
        return GateTraversal.yawOfDirection(toWorldDirection(turned, farAirship));
    }

    /**
     * Notes where something is, and says whether it has just gone through the opening.
     *
     * <p>The side is always recorded, but a change of side only counts as a crossing where the
     * opening actually is. A ring is flood filled and is very often not a rectangle, so the aperture
     * is drawn over the bounding box while the hole is some shape inside it - and without this,
     * brushing the fire beside an L-shaped opening sent a traveller through a piece of solid wall.
     *
     * <p>Recording the side even outside the opening is deliberate. Somebody who walks round the
     * frame from one face to the other has genuinely changed sides, and forgetting that would have
     * them counted as crossing the moment they stepped back into the hole.
     */
    private boolean stepped(UUID id, Vec3 point, Set<UUID> seen) {
        seen.add(id);
        boolean changedSide = watch.stepped(id, shape.side(point));
        return changedSide && shape.contains(point);
    }

    private void moveVehicles(ServerLevel level, RiftGate far, @Nullable Airship nearAirship,
                              @Nullable Airship farAirship, Set<UUID> seen) {
        AABB catchment = worldCatchment(nearAirship, CATCHMENT_DEPTH);
        List<ServerSubLevel> crossing = new ArrayList<>();
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(catchment))) {
            // A gate mounted on a hull has its own catchment sitting right on top of that hull in
            // world space once it is transformed there, and without this a gate would occasionally
            // find itself in its own crossing list.
            if (sub instanceof ServerSubLevel server && !server.isRemoved()
                    && (nearAirship == null || server != nearAirship.subLevel())) {
                crossing.add(server);
            }
        }

        // A vehicle takes everything joined to it through with it, so a trailer the car has already carried
        // across is not in the catchment any more - and must not be moved a second time from where it was.
        Set<SubLevel> carried = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ServerSubLevel sub : crossing) {
            if (carried.contains(sub)) {
                continue;
            }
            Airship vehicle = Airship.of(sub);
            if (GatePassages.holding(vehicle.uuid())) {
                // Held at a far gate while its crew are in passage - very likely this one, with its middle
                // sitting in this catchment. Not noted, so that it is a fresh sighting once it is let go.
                continue;
            }
            // Measured on the hull's centre, not its pose origin. On most builds those are not the
            // same point, and a vehicle whose origin is out at one corner would be judged to have
            // crossed while most of it was still on the near side.
            Vec3 origin = toVec(vehicle.position());
            Vec3 centre = toVec(vehicle.centre(new Vector3d()));
            Vec3 localCentre = toLocalPoint(centre, nearAirship);
            boolean stepped = stepped(vehicle.uuid(), localCentre, seen);
            if (settling.containsKey(vehicle.uuid()) || !stepped) {
                continue;
            }

            AABB bounds = vehicle.assemblyBounds().toMojang();
            if (!shape.admits(shape.extentAcross(bounds), bounds.getYsize())
                    || !far.shape().admits(far.shape().extentAcross(bounds), bounds.getYsize())) {
                Trace.log(Trace.GATE, "{} turned back vehicle {}: {} across by {} high will not fit {} (here {}x{},"
                                + " far end {}x{})", Trace.pos(worldPosition), vehicle.uuid(),
                        Trace.num(shape.extentAcross(bounds)), Trace.num(bounds.getYsize()),
                        shape.admits(shape.extentAcross(bounds), bounds.getYsize()) ? "the far end" : "this end",
                        shape.width(), shape.height(), far.shape().width(), far.shape().height());
                refuse(level, vehicle, origin, localCentre, nearAirship, GateFailure.TOO_LARGE);
                continue;
            }
            Fastening tie = Fastenings.holding(vehicle).orElse(null);
            if (tie != null) {
                Trace.log(Trace.GATE, "{} turned back vehicle {}: tied down by {}", Trace.pos(worldPosition),
                        vehicle.uuid(), tie);
                refuse(level, vehicle, origin, localCentre, nearAirship, GateFailure.FASTENED);
                continue;
            }

            // Read the crew before the hull moves: afterwards it is somewhere else entirely, and
            // whoever was aboard is found by looking at where it is now.
            List<ServerPlayer> aboard = vehicle.crew();
            List<Airship> vessel = vehicle.vessel();
            vessel.forEach(member -> carried.add(member.subLevel()));

            // Position and detection are exact under any tilt - see the note above worldCatchment.
            // The turn a hull itself is given below is not: it is the same local, cardinal-yaw delta
            // a walking traveller's momentum is turned by, which is honest for a level deck at any
            // world heading but not for one actively banking as something drives through it - the
            // same approximation this class has always made for a traveller's own facing, now also
            // covering the much rarer case of a vehicle crossing a gate that is itself aboard one.
            GateTraversal.Arrival arrival = GateTraversal.map(
                    shape, far.shape(), localCentre, toLocalDirection(toVec(vehicle.velocity()), nearAirship));
            Vec3 worldArrivalPosition = toWorldPoint(arrival.position(), farAirship);
            Vec3 worldArrivalMotion = toWorldDirection(arrival.motion(), farAirship);

            // The arrival is where the hull's middle goes, and relocate places its origin, so the one
            // has to be turned into the other - through the same rotation as everything else.
            Vec3 fromCentre = origin.subtract(centre);
            Vec3 arrivalOrigin =
                    worldArrivalPosition.add(GateTraversal.rotateYaw(fromCentre, arrival.yawDelta()));

            Quaterniond orientation = new Quaterniond(vehicle.orientation())
                    .rotateY(Math.toRadians(-arrival.yawDelta()));
            boolean moved = vehicle.relocate(new Vector3d(arrivalOrigin.x, arrivalOrigin.y, arrivalOrigin.z),
                    orientation, 0.0D);
            Trace.log(Trace.GATE, "{} carried vehicle {} ({} crew) from {} to {} turned {} degrees{}",
                    Trace.pos(worldPosition), vehicle.uuid(), aboard.size(), Trace.vec(origin), Trace.vec(arrivalOrigin),
                    Trace.num(arrival.yawDelta()), moved ? "" : " - Sable REFUSED the move");
            if (moved) {
                // A crew is carried through as a passage: the hull waits at the far gate, stopped, until
                // they arrive, and is driven off then. With nobody aboard there is nobody to wait for.
                boolean passage = GatePassages.enabled() && !aboard.isEmpty();
                vehicle.driveVelocity(passage ? new Vector3d()
                        : new Vector3d(worldArrivalMotion.x, worldArrivalMotion.y, worldArrivalMotion.z));
                for (Airship member : vessel) {
                    settling.put(member.uuid(), REENTRY_TICKS);
                    watch.forget(member.uuid());
                }
                markArrived(level, far);
                if (passage) {
                    // Which also does the announcing and the awarding, when they come out.
                    GatePassages.beginAboard(level, vehicle, vessel, aboard, farAirship, arrival.motion());
                } else {
                    SpatialWaveDetectorBlockEntity.announce(level, vehicle.assemblyBounds().toMojang());
                    AWCriteria.gateTravelled(aboard, true);
                }
            }
        }
    }

    /**
     * A vehicle that may not cross - too big for one of the openings, or tied to something it would leave
     * behind - put back rather than passed.
     *
     * <p>Refusing has to be visible or it reads as the gate being broken, so the vehicle is stopped
     * dead on the near side rather than being allowed to drift through a hole it does not fit.
     */
    private void refuse(ServerLevel level, Airship vehicle, Vec3 origin, Vec3 localCentreBefore,
                        @Nullable Airship nearAirship, GateFailure reason) {
        // Which way to put it back is decided by where its middle was, and where to put it is its
        // origin - the two are different points and mixing them up parks a vehicle in its own gate.
        // outwardTowards answers in the gate's own local frame, so it is turned back into world before
        // it is added to a world-space origin.
        Vec3 localOutward = shape.outwardTowards(localCentreBefore).scale(GateTraversal.CLEARANCE);
        Vec3 back = origin.add(toWorldDirection(localOutward, nearAirship));
        vehicle.relocate(new Vector3d(back.x, back.y, back.z), new Quaterniond(vehicle.orientation()), 0.0D);
        vehicle.driveVelocity(new Vector3d());
        settling.put(vehicle.uuid(), REENTRY_TICKS);
        lastFailure = reason;
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7F, 0.6F);
        notifyUpdate();
    }

    private void moveEntities(ServerLevel level, RiftGate far, @Nullable Airship nearAirship,
                              @Nullable Airship farAirship, Set<UUID> seen) {
        for (Entity entity : level.getEntities((Entity) null, worldCatchment(nearAirship, CATCHMENT_DEPTH),
                candidate -> !candidate.isRemoved())) {
            if (entity.isPassenger()) {
                continue; // moved by whatever is carrying it
            }
            if (entity instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity) {
                // A train's carriage, posed through the pane by Create as it crosses a rail lane. Create carries it,
                // and its riders; a gate that also "noticed" it cross would tear it off its own track.
                continue;
            }
            if (GatePassages.inPassage(entity)) {
                // Part of the way through a gate already - most likely held just beyond this one, having
                // come out of it. Not this gate's to move, and not noted either: a side remembered now
                // would be half of a crossing the moment they are let go.
                continue;
            }
            SubLevel tracking = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            // Aboard a DIFFERENT hull, which crosses as one thing or not at all - moveVehicles' job,
            // not this loop's. Aboard this gate's own hull is not that: it is exactly what walking up
            // to a grounded gate is, and excluding it unconditionally (as this once did) meant nobody
            // standing on a hull could ever be seen crossing a gate mounted on that same hull - only
            // the ground-to-hull direction of every pair ever worked.
            if (tracking != null && (nearAirship == null || tracking != nearAirship.subLevel())) {
                continue;
            }
            Vec3 localPoint = toLocalPoint(entity.position(), nearAirship);
            boolean stepped = stepped(entity.getUUID(), localPoint, seen);
            if (settling.containsKey(entity.getUUID()) || !stepped) {
                continue;
            }
            AABB bounds = entity.getBoundingBox();
            if (!far.shape().admits(far.shape().extentAcross(bounds), bounds.getYsize())) {
                // Silent to the traveller - they simply walk through the frame - so this is the only record.
                Trace.changed("gate-too-big:" + entity.getUUID(), Trace.GATE,
                        "{} let {} walk through without crossing: too big for the far end ({}x{})",
                        Trace.pos(worldPosition), entity.getName().getString(), far.shape().width(),
                        far.shape().height());
                continue;
            }

            GateTraversal.Arrival arrival = GateTraversal.map(shape, far.shape(), localPoint,
                    toLocalDirection(entity.getDeltaMovement(), nearAirship));
            Vec3 worldArrivalPosition = toWorldPoint(arrival.position(), farAirship);
            Vec3 worldArrivalMotion = toWorldDirection(arrival.motion(), farAirship);
            float yaw = worldYaw(entity.getYRot(), arrival.yawDelta(), nearAirship, farAirship);
            // A player, on foot or riding, goes through as a passage: put at the far gate now, held there
            // unseen and unharmed, and given their momentum back when they arrive. Begun before they are
            // moved, so their client is drawing the passage before it is told where it is.
            List<ServerPlayer> travellers = GatePassages.travellers(entity);
            Vec3 from = entity.position();
            if (!travellers.isEmpty()) {
                GatePassages.begin(level, entity, travellers, from, worldArrivalPosition, farAirship,
                        arrival.position(), arrival.motion());
            }
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(worldArrivalPosition.x, worldArrivalPosition.y,
                        worldArrivalPosition.z, yaw, player.getXRot());
            } else {
                entity.teleportTo(worldArrivalPosition.x, worldArrivalPosition.y, worldArrivalPosition.z);
                entity.setYRot(yaw);
            }
            Trace.log(Trace.GATE, "{} carried {} from {} to {}", Trace.pos(worldPosition),
                    entity.getName().getString(), Trace.vec(from), Trace.vec(worldArrivalPosition));
            entity.setDeltaMovement(travellers.isEmpty() ? worldArrivalMotion : Vec3.ZERO);
            entity.hurtMarked = true;
            entity.fallDistance = 0.0F;
            settling.put(entity.getUUID(), REENTRY_TICKS);
            watch.forget(entity.getUUID());
            markArrived(level, far);
            if (!travellers.isEmpty()) {
                continue; // announced, and awarded, by the passage as they come out of it
            }
            SpatialWaveDetectorBlockEntity.announce(level, entity.getBoundingBox());
            if (entity instanceof ServerPlayer walker) {
                AWCriteria.gateTravelled(List.of(walker), false);
            }
        }
    }

    /** Tells the far end something has just come out of it, so it does not send it straight back. */
    private void markArrived(ServerLevel level, RiftGate far) {
        RiftGateBlockEntity farGate = resolve(level, far.controller());
        if (farGate != null) {
            farGate.settling.putAll(settling);
        }
    }

    private static Vec3 toVec(org.joml.Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    // -------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_gate").forGoggles(tooltip);
        AWLang.translate(state.translationKey()).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if (shape != null) {
            AWLang.translate("gui.rift_gate.opening", shape.width(), shape.height())
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        AWLang.translate("gui.rift_gate.essence", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        // Only at the end that is paying, and only while it is actually paying. A destination gate
        // showing an upkeep it does not owe is the quickest way to convince somebody their far gate
        // needs plumbing.
        if (dialler && state.hasAperture() && upkeepCost() > 0) {
            AWLang.translate("gui.rift_gate.upkeep", upkeepCost(), secondsOf(upkeepInterval()))
                    .style(tank.getFluidAmount() >= upkeepCost() ? ChatFormatting.GRAY : ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }
        if (lastFailure.isFailure()) {
            AWLang.translate(lastFailure.translationKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        return true;
    }

    // ------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (gateId != null) {
            tag.putUUID("GateId", gateId);
        }
        if (shape != null) {
            tag.put("Shape", shape.save());
        }
        tag.putString("State", state.getSerializedName());
        tag.putInt("StateTicks", stateTicks);
        tag.putString("Failure", lastFailure.getSerializedName());
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        if (destination != null) {
            tag.putUUID("Destination", destination);
        }
        if (connected != null) {
            tag.putUUID("Connected", connected);
        }
        tag.putBoolean("Dialler", dialler);
        if (clientPacket) {
            return;
        }
        tag.putBoolean("RailOpened", railOpened);
        // The server's business only: nothing a client draws depends on who owns a gate.
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        if (!keptName.isBlank()) {
            tag.putString("KeptName", keptName);
        }
        tag.putString("KeptAccess", keptAccess.getSerializedName());
        if (keptNetwork != null) {
            tag.putUUID("KeptNetwork", keptNetwork);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        gateId = tag.hasUUID("GateId") ? tag.getUUID("GateId") : null;
        shape = tag.contains("Shape") ? RiftGateShape.load(tag.getCompound("Shape")) : null;
        state = byName(tag.getString("State"));
        stateTicks = tag.getInt("StateTicks");
        lastFailure = failureByName(tag.getString("Failure"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        destination = tag.hasUUID("Destination") ? tag.getUUID("Destination") : null;
        connected = tag.hasUUID("Connected") ? tag.getUUID("Connected") : null;
        dialler = tag.getBoolean("Dialler");
        if (clientPacket) {
            return;
        }
        railOpened = tag.getBoolean("RailOpened");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        keptName = tag.getString("KeptName");
        keptAccess = WarpAnchorAccess.byName(tag.getString("KeptAccess"));
        keptNetwork = tag.hasUUID("KeptNetwork") ? tag.getUUID("KeptNetwork") : null;
    }

    private static RiftGateState byName(String name) {
        for (RiftGateState value : RiftGateState.values()) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return RiftGateState.UNFORMED;
    }

    private static GateFailure failureByName(String name) {
        for (GateFailure value : GateFailure.values()) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return GateFailure.NONE;
    }

    /**
     * The chunk is going away, but the gate is not.
     *
     * <p>Only the connection is dropped. A gate whose registry entry vanished every time nobody was
     * standing near it would stop being a destination the moment it stopped being watched, which is
     * the opposite of what a gate is for.
     */
    @Override
    public void remove() {
        if (level != null && !level.isClientSide) {
            hangUp(GateFailure.NONE);
        }
        super.remove();
    }

    /** The controller has actually been broken, so the gate is gone. */
    @Override
    public void destroy() {
        if (level != null && !level.isClientSide) {
            unform();
        }
        super.destroy();
    }
}
