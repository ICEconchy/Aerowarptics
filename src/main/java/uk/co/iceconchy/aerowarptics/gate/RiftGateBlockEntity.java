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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.util.AWLang;

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
            unform();
            return false;
        }
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
        // After the hang-up, not instead of it: `hangUp` moves to CLOSING, which still counts as
        // having an aperture, so nothing has taken the pane down yet and there is about to be no
        // shape left to take it down with.
        setAperture(shape, false);
        shape = null;
        state = RiftGateState.UNFORMED;
        stateTicks = 0;
        if (level instanceof ServerLevel serverLevel && gateId != null) {
            RiftGateRegistry.get(serverLevel).remove(gateId);
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
        return state.is(AWBlocks.RIFT_PORTAL.get()) || state.isAir() || state.canBeReplaced();
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
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos cell : which.cells()) {
            BlockState there = serverLevel.getBlockState(cell);
            if (!present) {
                if (there.is(pane.getBlock())) {
                    serverLevel.setBlock(cell, Blocks.AIR.defaultBlockState(), flags);
                }
            } else if (there != pane && (there.isAir() || there.canBeReplaced())) {
                serverLevel.setBlock(cell, pane, flags);
            }
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
        registry.register(existing == null
                ? RiftGate.create(gateId(), serverLevel.dimension(), worldPosition, shape, null)
                : existing.withShape(shape));
    }

    // -------------------------------------------------------------- dialling

    /**
     * Strikes a connection to another gate.
     *
     * <p>Every refusal is named rather than being a silent no-op, because a gate that will not open
     * has exactly one interesting property and it is why.
     */
    public GateFailure dial(@Nullable ServerPlayer player, @Nullable UUID target) {
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
        if (!far.isInSameDimension(serverLevel)) {
            return fail(GateFailure.DESTINATION_ANOTHER_DIMENSION);
        }
        if (player != null && !far.isVisibleTo(player)) {
            return fail(GateFailure.UNAUTHORISED);
        }

        // The far end has to be loaded before anything else can be said about it. This is the one
        // place a gate is allowed to pull chunks in, and it is a deliberate player action.
        GateTicket.hold(serverLevel, far.shape().centre(), Math.max(far.shape().width(), far.shape().height()));
        RiftGateBlockEntity farGate = resolve(serverLevel, far.controller());
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
        destination = target;
        connect(target, true);
        farGate.connect(gateId(), false);
        lastFailure = GateFailure.NONE;
        return GateFailure.NONE;
    }

    /** Brings this end up, and remembers which end is paying. */
    private void connect(UUID other, boolean paying) {
        connected = other;
        dialler = paying;
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
        UUID other = connected;
        boolean wasPaying = dialler;
        connected = null;
        dialler = false;
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
            if (far != null) {
                RiftGateBlockEntity farGate = resolve(serverLevel, far.controller());
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
        if (state.hasAperture()) {
            // Re-asserted rather than assumed. This is what heals a pane that was interfered with -
            // a cell filled with water, a chunk that came back without it - and it costs a few
            // blockstate reads every few seconds, only while a gate is actually connected. The stage
            // is re-asserted with it so a pane that grew back defaults to OPENING and is immediately
            // corrected to whatever the gate is actually doing.
            setAperture(shape, true);
            setPortalStage(shape, RiftPortalStage.of(state));
        }
    }

    private void renewTicket() {
        if (--ticketTimer > 0 || !(level instanceof ServerLevel serverLevel) || shape == null) {
            return;
        }
        ticketTimer = GateTicket.RENEW_INTERVAL;
        GateTicket.hold(serverLevel, shape.centre(), Math.max(shape.width(), shape.height()));
        RiftGate far = farGate();
        if (far != null) {
            GateTicket.hold(serverLevel, far.shape().centre(),
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

        for (ServerSubLevel sub : crossing) {
            Airship vehicle = Airship.of(sub);
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

            AABB bounds = vehicle.worldBounds().toMojang();
            if (!shape.admits(shape.extentAcross(bounds), bounds.getYsize())
                    || !far.shape().admits(far.shape().extentAcross(bounds), bounds.getYsize())) {
                refuse(level, vehicle, origin, localCentre, nearAirship);
                continue;
            }

            // Read the crew before the hull moves: afterwards it is somewhere else entirely, and
            // whoever was aboard is found by looking at where it is now.
            List<ServerPlayer> aboard = vehicle.crew();

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
            if (vehicle.relocate(new Vector3d(arrivalOrigin.x, arrivalOrigin.y, arrivalOrigin.z),
                    orientation, 0.0D)) {
                vehicle.driveVelocity(
                        new Vector3d(worldArrivalMotion.x, worldArrivalMotion.y, worldArrivalMotion.z));
                settling.put(vehicle.uuid(), REENTRY_TICKS);
                watch.forget(vehicle.uuid());
                markArrived(level, far);
                AWCriteria.gateTravelled(aboard, true);
            }
        }
    }

    /**
     * A vehicle too big for one of the openings, put back rather than passed.
     *
     * <p>Refusing has to be visible or it reads as the gate being broken, so the vehicle is stopped
     * dead on the near side rather than being allowed to drift through a hole it does not fit.
     */
    private void refuse(ServerLevel level, Airship vehicle, Vec3 origin, Vec3 localCentreBefore,
                        @Nullable Airship nearAirship) {
        // Which way to put it back is decided by where its middle was, and where to put it is its
        // origin - the two are different points and mixing them up parks a vehicle in its own gate.
        // outwardTowards answers in the gate's own local frame, so it is turned back into world before
        // it is added to a world-space origin.
        Vec3 localOutward = shape.outwardTowards(localCentreBefore).scale(GateTraversal.CLEARANCE);
        Vec3 back = origin.add(toWorldDirection(localOutward, nearAirship));
        vehicle.relocate(new Vector3d(back.x, back.y, back.z), new Quaterniond(vehicle.orientation()), 0.0D);
        vehicle.driveVelocity(new Vector3d());
        settling.put(vehicle.uuid(), REENTRY_TICKS);
        lastFailure = GateFailure.TOO_LARGE;
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
                continue;
            }

            GateTraversal.Arrival arrival = GateTraversal.map(shape, far.shape(), localPoint,
                    toLocalDirection(entity.getDeltaMovement(), nearAirship));
            Vec3 worldArrivalPosition = toWorldPoint(arrival.position(), farAirship);
            Vec3 worldArrivalMotion = toWorldDirection(arrival.motion(), farAirship);
            float yaw = worldYaw(entity.getYRot(), arrival.yawDelta(), nearAirship, farAirship);
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(worldArrivalPosition.x, worldArrivalPosition.y,
                        worldArrivalPosition.z, yaw, player.getXRot());
            } else {
                entity.teleportTo(worldArrivalPosition.x, worldArrivalPosition.y, worldArrivalPosition.z);
                entity.setYRot(yaw);
            }
            entity.setDeltaMovement(worldArrivalMotion);
            entity.hurtMarked = true;
            entity.fallDistance = 0.0F;
            settling.put(entity.getUUID(), REENTRY_TICKS);
            watch.forget(entity.getUUID());
            markArrived(level, far);
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
