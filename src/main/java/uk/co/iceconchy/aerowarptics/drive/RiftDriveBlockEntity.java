package uk.co.iceconchy.aerowarptics.drive;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
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
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.AirshipWarpData;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** A ship-side claim older than this is treated as debris from an interrupted session. */
    private static final int STALE_CLAIM_TICKS = 20 * 60 * 10;

    /** Ticks between accepting a destination and the stabiliser rings starting to align. */
    private static final int LOCK_IN_TICKS = 10;

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
    private int sequenceTicks;
    private int cooldownTicks;
    private int errorTicks;
    private WarpFailure lastFailure = WarpFailure.NONE;
    @Nullable
    private UUID destinationAnchor;
    @Nullable
    private Vec3 destinationPos;
    @Nullable
    private UUID initiator;
    /** Which quarter of the drive's own frame the bow points along. */
    private DriveHeading heading = DriveHeading.FORWARD;
    /** The journey through the rift, planned in full before the aperture opens. */
    @Nullable
    private WarpFlight flight;

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
    private float syncedStressImpact;

    public RiftDriveBlockEntity(BlockPos pos, BlockState state, RiftDriveTier tier) {
        super(AWBlockEntities.RIFT_DRIVE.get(), pos, state);
        this.tier = tier;
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

        tickCharge();
        tickStateMachine();

        if (stateDirty) {
            stateDirty = false;
            sendData();
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
        boolean powered = rpm >= tier.minimumRpm();

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
                if (sequenceTicks >= tier.stabilizeTicks()) {
                    beginWarp();
                }
            }
            case WARPING, ARRIVING -> {
                sequenceTicks++;
                tickFlight();
            }
            default -> {
            }
        }

        if (state.isSequenceRunning()) {
            Airship airship = airship();
            if (airship == null || !airship.isActive()) {
                abort(WarpFailure.AIRSHIP_LOST);
                return;
            }
            // A rift stays open only while the machinery keeps turning.
            if (!isRunningFastEnough() && state != RiftDriveState.ARRIVING) {
                abort(WarpFailure.INSUFFICIENT_POWER);
                return;
            }
            airship.warpData().advance(state.getSerializedName(), sequenceTicks);
        } else if (state == RiftDriveState.IDLE || state == RiftDriveState.CHARGING) {
            releaseStaleClaim();
        }
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
            data.restoreOrigin(airship);
            data.release(true, level.getGameTime());
            lastFailure = WarpFailure.INTERRUPTED;
            markStateDirty();
        } else if (data.isWarping()) {
            data.releaseIfStale(level.getGameTime(), STALE_CLAIM_TICKS);
        }
    }

    // ------------------------------------------------------------ warp control

    /**
     * Accepts a warp order from a player.
     *
     * <p>Everything is re-derived here from server state: the anchor comes out of the registry by id,
     * the airship comes from Sable, the distance and cost come from the config. Nothing the client
     * sent beyond the anchor id is trusted.
     *
     * @return the failure that stopped the order, or {@link WarpFailure#NONE} when the warp began
     */
    public WarpFailure requestWarp(ServerPlayer player, UUID anchorId) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }

        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return reject(permission);
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

        WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(anchorId);
        if (anchor == null) {
            return reject(WarpFailure.ANCHOR_MISSING);
        }
        if (!anchor.enabled()) {
            return reject(WarpFailure.ANCHOR_DISABLED);
        }
        if (!anchor.isVisibleTo(player)) {
            return reject(WarpFailure.ANCHOR_FORBIDDEN);
        }

        WarpFailure destinationCheck = WarpValidator.validateDestination(airship, anchor, tier, charge);
        if (destinationCheck.isFailure()) {
            return reject(destinationCheck);
        }

        // One warp per airship, enforced on the airship itself rather than in a static map.
        AirshipWarpData data = airship.warpData();
        int totalTicks = LOCK_IN_TICKS + tier.stabilizeTicks() + tier.warpTicks() + tier.arriveTicks();
        Vec3 target = Vec3.atCenterOf(anchor.pos());
        if (!data.claim(worldPosition, anchorId, target, totalTicks, serverLevel.getGameTime())) {
            return reject(WarpFailure.AIRSHIP_ALREADY_WARPING);
        }

        destinationAnchor = anchorId;
        destinationPos = target;
        initiator = player.getUUID();
        committedCost = (float) WarpCost.fromConfig(tier).cost(
                WarpValidator.distanceTo(airship, anchor), WarpValidator.effectiveMass(airship));
        sequenceTicks = 0;
        lastFailure = WarpFailure.NONE;
        transition(RiftDriveState.DESTINATION_SELECTED);
        notifyAnchor(serverLevel, anchor, true);
        playSound(AWSounds.DESTINATION_LOCK.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.DESTINATION_LOCK);
        return WarpFailure.NONE;
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

    private void beginStabilizing() {
        sequenceTicks = 0;
        transition(RiftDriveState.STABILIZING);
        playSound(AWSounds.STABILIZING.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.STABILIZING);
    }

    /**
     * Plans the whole flight, then opens the entry rift.
     *
     * <p>The destination is resolved and the arrival volume proven clear <em>here</em>, before the
     * aperture exists. A warp with nowhere to land is refused while the airship is still safely at its
     * mooring, rather than halfway down the corridor with no way back.
     */
    private void beginWarp() {
        if (!(level instanceof ServerLevel serverLevel)) {
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }
        WarpAnchor anchor = destinationAnchor == null ? null
                : WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
        if (anchor == null) {
            abort(WarpFailure.ANCHOR_MISSING);
            return;
        }
        ServerLevel destination = serverLevel.getServer().getLevel(anchor.dimension());
        if (destination == null || !SafeArrival.isDestinationLoadable(destination, anchor.pos())) {
            abort(WarpFailure.ANCHOR_UNAVAILABLE);
            return;
        }
        if (!destination.dimension().equals(serverLevel.dimension())) {
            WarpFailure crossDimension = CrossDimensionWarp.transfer(airship, destination, anchor.pos());
            if (crossDimension.isFailure()) {
                abort(crossDimension);
                return;
            }
        }

        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        WarpFlight planned = WarpFlight.plan(airship, destination, anchor.pos(), bow,
                tier.warpTicks(), tier.arriveTicks());
        if (planned == null) {
            abort(WarpFailure.NO_SAFE_ARRIVAL);
            return;
        }
        if (!SafeArrival.isClear(airship, serverLevel, planned.corridorVolume(airship))) {
            // Another vessel is already running this stretch of corridor.
            abort(WarpFailure.NO_SAFE_ARRIVAL);
            return;
        }

        flight = planned;
        airship.warpData().rememberOrigin(airship.position(), airship.orientation());
        sequenceTicks = 0;
        transition(RiftDriveState.WARPING);
        playSound(AWSounds.RIFT_OPEN.get(), 1.2F, 1.0F);
        broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_OPEN, planned.entryRift());
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

        switch (current.tick(airship)) {
            case CONTINUE -> {
            }
            case ENTER_CORRIDOR -> {
                // The crew is read before the hull moves. Sable's entity tracking needs a tick to
                // catch up with a teleport, so asking afterwards can come back empty.
                List<ServerPlayer> crew = airship.crew();
                if (!airship.relocate(current.corridorEntryOrigin(airship), airship.orientation(), 0.0D)) {
                    abort(WarpFailure.RELOCATION_FAILED);
                    return;
                }
                current.enteredCorridor();
                playSound(AWSounds.WARP_TRAVEL.get(), 1.0F, 1.0F);
                broadcastEffect(ClientboundWarpEffectPacket.Stage.CORRIDOR);
                send(crew, ClientboundCorridorPacket.enter(tier.warpTicks(), tierColour()));
                // Opened now, not on arrival: anyone at the destination gets a few seconds of warning
                // before a hull comes through it.
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_OPEN, current.exitRift());
            }
            case EXIT_CORRIDOR -> {
                List<ServerPlayer> crew = airship.crew();
                if (!airship.relocate(current.emergenceOrigin(), current.arrivalOrientation(), 0.0D)) {
                    abort(WarpFailure.RELOCATION_FAILED);
                    return;
                }
                current.leftCorridor();
                send(crew, ClientboundCorridorPacket.leave());
                onEmerged(airship);
            }
            case ARRIVED -> completeWarp();
        }
    }

    /** The hull is through the far aperture: charge is spent and the drive begins settling it down. */
    private void onEmerged(Airship airship) {
        charge = Math.max(0.0F, charge - committedCost);
        sequenceTicks = 0;
        transition(RiftDriveState.ARRIVING);
        playSound(AWSounds.WARP_EXIT.get(), 1.3F, 1.0F);

        WarpFlight current = flight;
        if (current != null) {
            destinationPos = new Vec3(current.arrivalOrigin().x(), current.arrivalOrigin().y(),
                    current.arrivalOrigin().z());
            broadcastRift(ClientboundWarpEffectPacket.Stage.WARP_EXIT, current.exitRift());
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

    /** The hull has settled: verify, release the airship, and cool down. */
    private void completeWarp() {
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }

        if (level instanceof ServerLevel serverLevel && destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor != null) {
                notifyAnchor(serverLevel, anchor, false);
            }
        }

        airship.driveVelocity(new Vector3d());
        airship.warpData().release(false, level.getGameTime() + tier.cooldownTicks());
        flight = null;
        destinationAnchor = null;
        destinationPos = null;
        initiator = null;
        committedCost = 0.0F;
        sequenceTicks = 0;
        cooldownTicks = tier.cooldownTicks();
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
        Airship airship = airship();
        if (airship != null) {
            // Hand the helm back and, if the hull is out in the corridor, bring it home.
            airship.driveVelocity(new Vector3d());
            send(airship.crew(), ClientboundCorridorPacket.leave());
            AirshipWarpData data = airship.warpData();
            if (flight != null && flight.stage() != WarpFlight.Stage.APPROACH) {
                data.restoreOrigin(airship);
            }
            if (data.isOwnedBy(worldPosition)) {
                data.release(true, level == null ? 0L : level.getGameTime());
            }
            if (reason.penalised() && AWConfig.DANGEROUS_FAILURES.get() && level instanceof ServerLevel) {
                double magnitude = AWConfig.DANGEROUS_FAILURE_IMPULSE.get() * Math.max(1.0D, airship.mass()) * 0.01D;
                airship.applyImpulse(new Vector3d(0.0D, magnitude, 0.0D), new Vector3d());
            }
        }

        if (level instanceof ServerLevel serverLevel && destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor != null) {
                notifyAnchor(serverLevel, anchor, false);
            }
        }

        if (reason.penalised()) {
            charge = Math.max(0.0F, charge - (float) (double) AWConfig.FAILURE_CHARGE_PENALTY.get());
        }
        notifyInitiator(reason);
        flight = null;
        destinationAnchor = null;
        destinationPos = null;
        initiator = null;
        committedCost = 0.0F;
        sequenceTicks = 0;
        lastFailure = reason;
        errorTicks = AWConfig.FAILURE_COOLDOWN_TICKS.get();
        forceState(RiftDriveState.ERROR);
        playSound(AWSounds.WARP_FAILED.get(), 1.0F, 1.0F);
        broadcastEffect(ClientboundWarpEffectPacket.Stage.FAILED);
    }

    private WarpFailure reject(WarpFailure failure) {
        lastFailure = failure;
        markStateDirty();
        return failure;
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
            AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(reason));
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
        state = next;
        markStateDirty();
    }

    /** Used for the error path, which every state is allowed to enter. */
    private void forceState(RiftDriveState next) {
        state = next;
        markStateDirty();
    }

    private void markStateDirty() {
        stateDirty = true;
        setChanged();
    }

    // -------------------------------------------------------------- accessors

    public RiftDriveTier tier() {
        return tier;
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

    public DriveHeading heading() {
        return heading;
    }

    /** The bow in the airship's own frame: the drive's face, turned by the pilot's setting. */
    private Direction shipSpaceBow() {
        return WarpFlight.shipSpaceBow(heading, getBlockState().getValue(RiftDriveBlock.FACING));
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

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** How long the drive's current phase lasts, in ticks. Server-side config lookup. */
    private int phaseDuration() {
        return switch (state) {
            case DESTINATION_SELECTED -> LOCK_IN_TICKS;
            case STABILIZING -> tier.stabilizeTicks();
            case WARPING -> tier.warpTicks();
            case ARRIVING -> tier.arriveTicks();
            case COOLDOWN -> Math.max(1, tier.cooldownTicks());
            case ERROR -> Math.max(1, AWConfig.FAILURE_COOLDOWN_TICKS.get());
            default -> 0;
        };
    }

    /** 0..1 progress through whichever timed phase the drive is in. */
    public float sequenceProgress() {
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
        return Math.abs(getSpeed()) >= requiredRpm();
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
        if (level != null && !level.isClientSide && state.isSequenceRunning()) {
            abort(WarpFailure.INTERRUPTED);
        }
        super.remove();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.0D);
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("State", state.ordinal());
        tag.putFloat("Charge", charge);
        tag.putFloat("CommittedCost", committedCost);
        tag.putInt("SequenceTicks", sequenceTicks);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putInt("ErrorTicks", errorTicks);
        tag.putInt("LastFailure", lastFailure.ordinal());
        tag.putInt("Heading", heading.ordinal());
        if (destinationAnchor != null) {
            tag.putUUID("Anchor", destinationAnchor);
        }
        if (destinationPos != null) {
            tag.putDouble("TargetX", destinationPos.x);
            tag.putDouble("TargetY", destinationPos.y);
            tag.putDouble("TargetZ", destinationPos.z);
        }
        if (initiator != null) {
            tag.putUUID("Initiator", initiator);
        }
        if (flight != null && !clientPacket) {
            tag.put("Flight", flight.save(registries));
        }
        if (clientPacket && flight != null) {
            tag.putString("FlightStage", flight.stage().name());
            tag.putFloat("FlightProgress", flight.stageProgress());
        }
        if (clientPacket) {
            tag.putInt("MinRpm", tier.minimumRpm());
            tag.putInt("PhaseDuration", phaseDuration());
            tag.putFloat("StressImpact", (float) tier.stressImpact());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        state = RiftDriveState.byIndex(tag.getInt("State"));
        charge = tag.getFloat("Charge");
        committedCost = tag.getFloat("CommittedCost");
        sequenceTicks = tag.getInt("SequenceTicks");
        cooldownTicks = tag.getInt("CooldownTicks");
        errorTicks = tag.getInt("ErrorTicks");
        lastFailure = WarpFailure.byIndex(tag.getInt("LastFailure"));
        heading = DriveHeading.byIndex(tag.getInt("Heading"));
        destinationAnchor = tag.hasUUID("Anchor") ? tag.getUUID("Anchor") : null;
        destinationPos = tag.contains("TargetX")
                ? new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"))
                : null;
        initiator = tag.hasUUID("Initiator") ? tag.getUUID("Initiator") : null;
        flight = tag.contains("Flight") ? WarpFlight.load(tag.getCompound("Flight")) : null;
        if (clientPacket) {
            clientFlightStage = tag.contains("FlightStage") ? tag.getString("FlightStage") : "";
            clientFlightProgress = tag.getFloat("FlightProgress");
            clientCharge = charge;
            clientChargeLast = charge;
            syncedMinimumRpm = tag.getInt("MinRpm");
            syncedPhaseDuration = tag.getInt("PhaseDuration");
            syncedStressImpact = tag.getFloat("StressImpact");
        } else if (state.isSequenceRunning()) {
            // A warp cannot survive a reload: drop back to a safe state rather than resuming blind.
            // The airship's own record still holds where it set off from, so a hull left out in the
            // corridor is put back there by the recovery in releaseStaleClaim.
            state = RiftDriveState.ERROR;
            lastFailure = WarpFailure.INTERRUPTED;
            errorTicks = Math.max(errorTicks, 40);
            flight = null;
            destinationAnchor = null;
            destinationPos = null;
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
    private static final int[] TIER_COLOURS = {0x2FA8B8, 0x49D9C4, 0xE0B04A, 0xE45CFF};

    private int tierColour() {
        return TIER_COLOURS[Math.max(0, Math.min(TIER_COLOURS.length - 1, tier.index()))];
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
                worldPosition, stage, tier.index(), airship == null ? null : airship.uuid(), origin));
    }

    /**
     * A cue carrying an aperture.
     *
     * <p>Sent to players near the aperture rather than near the drive, because the exit rift opens
     * thousands of blocks from the machine that made it and the people who should see it are the ones
     * standing at the destination.
     */
    private void broadcastRift(ClientboundWarpEffectPacket.Stage stage, WarpFlight.Rift rift) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Airship airship = airship();
        Vec3 centre = new Vec3(rift.centre().x, rift.centre().y, rift.centre().z);
        AWNetwork.sendToTracking(serverLevel, centre, ClientboundWarpEffectPacket.rift(
                worldPosition, stage, tier.index(), airship == null ? null : airship.uuid(), rift));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_drive").forGoggles(tooltip);
        AWLang.translate(tier.translationKey()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        AWLang.translate(state.translationKey()).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        AWLang.translate("gui.rift_drive.charge", AWLang.percent(charge))
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
            case DESTINATION_SELECTED, STABILIZING -> 2.5D;
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
