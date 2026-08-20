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
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * The aperture only hides what falls inside its own silhouette, so the opening is the size limit.
 * Anything wider or taller is refused rather than half-swallowed, because a vehicle visible at both
 * ends of the journey at once is the one thing this illusion cannot survive.
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
    private int idleTicks;

    /** Travellers who have just arrived, and must not immediately be sent back. */
    private final Map<UUID, Integer> settling = new HashMap<>();

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

    private boolean isOpen(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
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
        idleTicks = 0;
        transition(RiftGateState.DIALLING);
        playSound(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.9F, 0.7F);
    }

    /** Lets go of a connection at both ends. */
    public void hangUp(GateFailure reason) {
        if (!state.hasAperture()) {
            return;
        }
        UUID other = connected;
        connected = null;
        dialler = false;
        lastFailure = reason;
        transition(RiftGateState.CLOSING);

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
            // Create caches a block's stress impact, so an impact that depends on what the machine is
            // doing has to say so. Detaching and re-attaching is how the network is made to ask again.
            detachKinetics();
            lastStressApplied = -1.0F;
            attachKinetics();
        }
        notifyUpdate();
    }

    // ------------------------------------------------------------------ cost

    /** Essence to strike a connection. Bigger doorway, bigger tear. */
    public int dialCost() {
        int area = shape == null ? 0 : shape.area();
        return AWConfig.GATE_DIAL_COST.get() + AWConfig.GATE_DIAL_COST_PER_BLOCK.get() * area;
    }

    @Override
    public float calculateStressApplied() {
        if (!state.hasAperture() || shape == null) {
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
            // The aperture is renewed from here rather than pushed by the server, so a player who
            // walks up to a gate that opened before they arrived still sees it standing.
            uk.co.iceconchy.aerowarptics.client.AWClientHooks.tickGateAperture(this);
            return;
        }
        stateTicks++;

        if (--validateTimer <= 0) {
            validateTimer = VALIDATE_INTERVAL;
            revalidate();
        }

        settling.entrySet().removeIf(entry -> entry.setValue(entry.getValue() - 1) <= 0);

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
                int crossings = tickTraversal();
                idleTicks = crossings > 0 ? 0 : idleTicks + 1;
                int limit = AWConfig.GATE_IDLE_TICKS.get();
                if (limit > 0 && idleTicks > limit) {
                    hangUp(GateFailure.NONE);
                }
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

    /** Whether the connection can still stand. Losing rotation drops it, which is the holding cost. */
    private boolean holdable() {
        if (!isSpinningFastEnough() || isOverStressed()) {
            hangUp(GateFailure.INSUFFICIENT_POWER);
            return false;
        }
        if (shape == null) {
            hangUp(GateFailure.NOT_FORMED);
            return false;
        }
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
        } else if (!opening.shape().equals(shape)) {
            shape = opening.shape();
            registerSelf();
            notifyUpdate();
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
     *
     * @return how many things crossed, which is what keeps an idle gate from holding forever
     */
    private int tickTraversal() {
        if (shape == null || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        RiftGate far = farGate();
        if (far == null) {
            hangUp(GateFailure.DESTINATION_MISSING);
            return 0;
        }
        return moveVehicles(serverLevel, far) + moveEntities(serverLevel, far);
    }

    private int moveVehicles(ServerLevel level, RiftGate far) {
        AABB catchment = shape.catchment(CATCHMENT_DEPTH);
        int moved = 0;
        List<ServerSubLevel> crossing = new ArrayList<>();
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(catchment))) {
            if (sub instanceof ServerSubLevel server && !server.isRemoved()) {
                crossing.add(server);
            }
        }

        for (ServerSubLevel sub : crossing) {
            Airship vehicle = Airship.of(sub);
            if (settling.containsKey(vehicle.uuid())) {
                continue;
            }
            // Measured on the hull's centre, not its pose origin. On most builds those are not the
            // same point, and a vehicle whose origin is out at one corner would be judged to have
            // crossed while most of it was still on the near side.
            Vec3 origin = toVec(vehicle.position());
            Vec3 step = origin.subtract(toVec(sub.lastPose().position()));
            Vec3 centre = toVec(vehicle.centre(new Vector3d()));
            if (!GateTraversal.crossed(shape, centre.subtract(step), centre)) {
                continue;
            }

            AABB bounds = vehicle.worldBounds().toMojang();
            if (!shape.admits(shape.extentAcross(bounds), bounds.getYsize())
                    || !far.shape().admits(far.shape().extentAcross(bounds), bounds.getYsize())) {
                refuse(level, vehicle, origin, centre.subtract(step));
                continue;
            }

            GateTraversal.Arrival arrival =
                    GateTraversal.map(shape, far.shape(), centre, toVec(vehicle.velocity()));
            // The arrival is where the hull's middle goes, and relocate places its origin, so the one
            // has to be turned into the other - through the same rotation as everything else.
            Vec3 fromCentre = origin.subtract(centre);
            Vec3 arrivalOrigin =
                    arrival.position().add(GateTraversal.rotateYaw(fromCentre, arrival.yawDelta()));

            Quaterniond orientation = new Quaterniond(vehicle.orientation())
                    .rotateY(Math.toRadians(-arrival.yawDelta()));
            if (vehicle.relocate(new Vector3d(arrivalOrigin.x, arrivalOrigin.y, arrivalOrigin.z),
                    orientation, 0.0D)) {
                vehicle.driveVelocity(new Vector3d(arrival.motion().x, arrival.motion().y, arrival.motion().z));
                settling.put(vehicle.uuid(), REENTRY_TICKS);
                markArrived(level, far);
                moved++;
            }
        }
        return moved;
    }

    /**
     * A vehicle too big for one of the openings, put back rather than passed.
     *
     * <p>Refusing has to be visible or it reads as the gate being broken, so the vehicle is stopped
     * dead on the near side rather than being allowed to drift through a hole it does not fit.
     */
    private void refuse(ServerLevel level, Airship vehicle, Vec3 origin, Vec3 centreBefore) {
        // Which way to put it back is decided by where its middle was, and where to put it is its
        // origin - the two are different points and mixing them up parks a vehicle in its own gate.
        Vec3 outward = shape.outwardTowards(centreBefore).scale(GateTraversal.CLEARANCE);
        Vec3 back = origin.add(outward);
        vehicle.relocate(new Vector3d(back.x, back.y, back.z), new Quaterniond(vehicle.orientation()), 0.0D);
        vehicle.driveVelocity(new Vector3d());
        settling.put(vehicle.uuid(), REENTRY_TICKS);
        lastFailure = GateFailure.TOO_LARGE;
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7F, 0.6F);
        notifyUpdate();
    }

    private int moveEntities(ServerLevel level, RiftGate far) {
        int moved = 0;
        for (Entity entity : level.getEntities((Entity) null, shape.catchment(CATCHMENT_DEPTH),
                candidate -> !candidate.isRemoved())) {
            if (entity.isPassenger()) {
                continue; // moved by whatever is carrying it
            }
            if (Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null) {
                continue; // standing on a vehicle, which crosses as one thing or not at all
            }
            if (settling.containsKey(entity.getUUID())) {
                continue;
            }
            Vec3 before = new Vec3(entity.xo, entity.yo, entity.zo);
            if (!GateTraversal.crossed(shape, before, entity.position())) {
                continue;
            }
            AABB bounds = entity.getBoundingBox();
            if (!far.shape().admits(far.shape().extentAcross(bounds), bounds.getYsize())) {
                continue;
            }

            GateTraversal.Arrival arrival = GateTraversal.map(shape, far.shape(), entity.position(),
                    entity.getDeltaMovement());
            float yaw = entity.getYRot() + arrival.yawDelta();
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(arrival.position().x, arrival.position().y, arrival.position().z,
                        yaw, player.getXRot());
            } else {
                entity.teleportTo(arrival.position().x, arrival.position().y, arrival.position().z);
                entity.setYRot(yaw);
            }
            entity.setDeltaMovement(arrival.motion());
            entity.hurtMarked = true;
            entity.fallDistance = 0.0F;
            settling.put(entity.getUUID(), REENTRY_TICKS);
            markArrived(level, far);
            moved++;
        }
        return moved;
    }

    /** Tells the far end something has just come out of it, so it does not send it straight back. */
    private void markArrived(ServerLevel level, RiftGate far) {
        RiftGateBlockEntity farGate = resolve(level, far.controller());
        if (farGate != null) {
            farGate.idleTicks = 0;
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
        AWLang.translate("gui.rift_gate.essence", tank.getFluidAmount(), CAPACITY)
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
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
