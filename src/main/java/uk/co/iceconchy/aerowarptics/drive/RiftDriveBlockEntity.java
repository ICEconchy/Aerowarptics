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
import uk.co.iceconchy.aerowarptics.airship.AirshipWarpData;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;
import uk.co.iceconchy.aerowarptics.warp.SpinUp;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.warp.CrewManifest;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.WarpPassengers;
import uk.co.iceconchy.aerowarptics.warp.WarpTrace;
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
    private int errorTicks;
    private WarpFailure lastFailure = WarpFailure.NONE;
    @Nullable
    private UUID destinationAnchor;
    @Nullable
    private Vec3 destinationPos;
    /**
     * What to call the committed destination.
     *
     * <p>A fix has no anchor to ask for its name, and an anchor can be renamed while a ship is on its
     * way, so the label the pilot chose by is carried along with the course rather than looked up.
     */
    private String destinationLabel = "";

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

    /** Spin the current jump needs, in ticks at full rate. Set when the destination is committed. */
    private float spinRequired;
    /** Spin accumulated so far. Climbs faster the harder the drive is being turned. */
    private float spinProgress;
    /** Which quarter of the drive's own frame the bow points along. */
    private DriveHeading heading = DriveHeading.FORWARD;
    /** The journey through the rift, planned in full before the aperture opens. */
    @Nullable
    private WarpFlight flight;

    /**
     * Who is aboard for the journey, and what to do when somebody stops being aboard.
     *
     * <p>Not persisted. A drive that comes back off disk mid-warp re-learns the manifest on its next
     * tick from whoever is actually standing on the hull, which is the only answer worth having.
     */
    private final WarpPassengers passengers = new WarpPassengers();

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

        if (interruptedOnLoad) {
            interruptedOnLoad = false;
            recoverFromInterruption();
        }

        tickRedstone();
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
                if (spinProgress >= spinRequired) {
                    beginWarp();
                }
                // The client draws a wind-up bar and animates off this, so it has to see it move.
                markStateDirty();
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
            if (player != null && !anchor.isVisibleTo(player)) {
                return reject(WarpFailure.ANCHOR_FORBIDDEN);
            }
            targetBlock = anchor.pos();
        } else {
            targetBlock = course.fix();
        }

        WarpFailure destinationCheck = anchor != null
                ? WarpValidator.validateDestination(airship, anchor, tier, charge)
                : WarpValidator.validateFix(airship, targetBlock, tier, charge);
        if (destinationCheck.isFailure()) {
            return reject(destinationCheck);
        }

        // One warp per airship, enforced on the airship itself rather than in a static map.
        AirshipWarpData data = airship.warpData();
        int totalTicks = LOCK_IN_TICKS + tier.stabilizeTicks() + tier.warpTicks() + tier.arriveTicks();
        Vec3 target = Vec3.atCenterOf(targetBlock);
        // A fix has no anchor id to claim under, so the claim is keyed on the drive's own identity.
        UUID claimKey = course.isAnchor() ? course.anchorId() : UUID.nameUUIDFromBytes(
                ("fix:" + targetBlock.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!data.claim(worldPosition, claimKey, target, totalTicks, serverLevel.getGameTime())) {
            return reject(WarpFailure.AIRSHIP_ALREADY_WARPING);
        }

        destinationAnchor = course.anchorId();
        destinationPos = target;
        destinationLabel = anchor != null ? anchor.displayName() : course.label();
        // A redstone start has no player in hand, so the warp belongs to whoever set the course.
        // Every reader of this field already copes with it being absent.
        initiator = player != null ? player.getUUID() : standingAuthor;
        committedDistance = WarpValidator.distanceTo(airship, targetBlock);
        committedCost = (float) WarpCost.fromConfig(tier).cost(
                committedDistance, WarpValidator.effectiveMass(airship));
        sequenceTicks = 0;
        lastFailure = WarpFailure.NONE;
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
        if (!rising || !AWConfig.ALLOW_REDSTONE_INITIATION.get()) {
            return;
        }
        WarpCourse course = standingCourse;
        if (course == null || !state.acceptsDestination()) {
            // Nothing set, or the drive is already busy. Both are silent: a redstone input has no
            // one to complain to, and a circuit that clicks every few seconds should not fill a log.
            return;
        }
        startWarp(course, null);
    }

    /**
     * Sets the course this drive will fly when something fires it.
     *
     * <p>Called when a player chooses a destination on an Astrolabe Cartography Table. Choosing is
     * the moment permission is checked, so this is the moment the authorisation a redstone signal
     * later replays is granted.
     */
    public void armCourse(@Nullable WarpCourse course, @Nullable UUID author) {
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
        if (!anchor.isVisibleTo(player)) {
            return WarpFailure.ANCHOR_FORBIDDEN;
        }
        armCourse(WarpCourse.toAnchor(anchorId, anchor.displayName()), player.getUUID());
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
     */
    public WarpFailure setFixCourse(ServerPlayer player, BlockPos fix, String label) {
        WarpFailure permission = WarpValidator.validatePlayer(player, this);
        if (permission.isFailure()) {
            return permission;
        }
        if (!(level instanceof ServerLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        armCourse(WarpCourse.toFix(fix, label), player.getUUID());
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
        transition(RiftDriveState.STABILIZING);
        // Pitched off the rate it is actually managing, so an underpowered drive audibly labours.
        playSound(AWSounds.STABILIZING.get(), 1.0F, 0.7F + 0.5F * (float) spinRate());
        broadcastEffect(ClientboundWarpEffectPacket.Stage.STABILIZING);
    }

    /** How far the committed destination is, or zero when there is nothing to measure to. */
    private double distanceToDestination() {
        Airship airship = airship();
        if (airship == null || destinationPos == null) {
            return 0.0D;
        }
        // Measured against the committed position rather than re-resolved through the registry, so a
        // fix and an anchor are the same question - and so an anchor deleted mid-flight does not
        // silently turn a long jump into a spin-up for a zero-length one.
        Vector3d centre = airship.centre(new Vector3d());
        return centre.distance(destinationPos.x, destinationPos.y, destinationPos.z);
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
        if (!(level instanceof ServerLevel serverLevel)) {
            abort(WarpFailure.RELOCATION_FAILED);
            return;
        }
        Airship airship = airship();
        if (airship == null || !airship.isActive()) {
            abort(WarpFailure.AIRSHIP_LOST);
            return;
        }
        // An anchor still has to be there when the rift opens. A fix is a place, and a place cannot
        // be taken down while a ship is on its way to it.
        ServerLevel destination;
        BlockPos targetBlock;
        if (destinationAnchor != null) {
            WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destinationAnchor);
            if (anchor == null) {
                abort(WarpFailure.ANCHOR_MISSING);
                return;
            }
            destination = serverLevel.getServer().getLevel(anchor.dimension());
            targetBlock = anchor.pos();
        } else if (destinationPos != null) {
            // Soundings are thrown from the ship's own level and never leave it.
            destination = serverLevel;
            targetBlock = BlockPos.containing(destinationPos);
        } else {
            abort(WarpFailure.ANCHOR_MISSING);
            return;
        }

        if (destination == null || !SafeArrival.isDestinationLoadable(destination, targetBlock)) {
            abort(WarpFailure.ANCHOR_UNAVAILABLE);
            return;
        }
        if (!destination.dimension().equals(serverLevel.dimension())) {
            WarpFailure crossDimension = CrossDimensionWarp.transfer(airship, destination, targetBlock);
            if (crossDimension.isFailure()) {
                abort(crossDimension);
                return;
            }
        }

        Vector3d bow = WarpFlight.worldBow(airship, shipSpaceBow());
        WarpFlight planned = WarpFlight.plan(airship, destination, targetBlock, bow,
                tier.warpTicks(), tier.arriveTicks());
        if (planned == null) {
            abort(WarpFailure.NO_SAFE_ARRIVAL);
            return;
        }
        flight = planned;
        airship.warpData().rememberOrigin(airship.position(), airship.orientation());
        sequenceTicks = 0;
        transition(RiftDriveState.WARPING);
        // Claim the ground at the far end now, while the ship still has a whole flight to make.
        // Left until the teleport, the chunks would come off disk at the exact moment the hull is
        // meant to be flying out of the aperture.
        // Who is aboard, promised to arrive with the hull whatever becomes of this machine.
        CrewManifest.board(airship);
        ArrivalTicket.hold(destination, planned.arrivalOrigin(), planned.hullSpan());
        // And the drive's own chunk, so the machine running the sequence cannot be unloaded out from
        // under it partway through - which ends the flight silently and strands whoever was aboard.
        ArrivalTicket.holdDrive(serverLevel, worldPosition);

        WarpTrace.plan(worldPosition, airship, planned, bow);
        playSound(AWSounds.RIFT_OPEN.get(), 1.2F, 1.0F);
        // The entry aperture has to stay open for the run in, the passage, and the corridor run -
        // all of which now happen inside it.
        broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_OPEN, planned.entryRift(),
                AWConfig.APPROACH_LIMIT_TICKS.get() + planned.transitTicks() + tier.warpTicks(),
                planned.entryThroatDepth());
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

        switch (current.tick(airship)) {
            case CONTINUE -> {
            }
            case BEGIN_TRANSIT -> {
                WarpTrace.stage(WarpFlight.Stage.APPROACH, WarpFlight.Stage.TRANSIT,
                        current.stageTicks(), airship, current);
                current.beganTransit();
                playSound(AWSounds.WARP_TRAVEL.get(), 0.8F, 0.8F);
                // The hull stays inside this aperture for the passage *and* the corridor run.
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, current.entryRift(),
                        current.transitTicks() + tier.warpTicks());
                // The wash starts as the bow goes in, not when the hull teleports. The crew's own
                // camera crosses the aperture partway through the passage, and without this they
                // would watch the world go dark and then carry on flying through it for a second.
                send(airship.crew(), ClientboundCorridorPacket.enter(
                        current.transitTicks() + tier.warpTicks(), tierColour()));
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
                send(airship.crew(), ClientboundCorridorPacket.enter(tier.warpTicks(), tierColour()));
                // Opened now, not on arrival: anyone at the destination gets a few seconds of warning
                // before a hull comes through it.
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_OPEN, current.exitRift(),
                        tier.warpTicks() + current.transitTicks(), -current.exitThroatDepth());
            }
            case EXIT_CORRIDOR -> {
                // The crew is read before the hull moves. Sable's entity tracking needs a tick to
                // catch up with a teleport, so asking afterwards can come back empty.
                List<ServerPlayer> crew = airship.crew();
                WarpTrace.stage(WarpFlight.Stage.CORRIDOR, WarpFlight.Stage.BREACH,
                        current.stageTicks(), airship, current);
                Vector3dc departure = new Vector3d(airship.position());
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
                // Sable's pose sync has no way to say "that was a teleport", so a client left to
                // itself reads this as movement, builds a collision volume the length of the jump,
                // and hits the sanity limit that makes it give up on the hull entirely - dropping the
                // crew through the deck and leaving the ship undrawn. Everyone who can see either end
                // is told, because everyone who can see it has the same wrong idea about it.
                announceCrossing(airship, departure, current.emergenceOrigin(), crew);
                // Only now is the entry aperture finished with; the hull was inside it until this tick.
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, current.entryRift(), 0);
                current.leftCorridor();
                // Still behind the far aperture and still inside the fold, so the wash carries on
                // until the hull is properly out the other side.
                send(crew, ClientboundCorridorPacket.enter(current.transitTicks(), tierColour()));
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, current.exitRift(),
                        current.transitTicks());
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
                completeWarp();
            }
        }
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
            broadcastRift(ClientboundWarpEffectPacket.Stage.WARP_EXIT, current.exitRift(), 0);
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

        // Whatever the fold sheds on the way through, the ship's siphons catch here - after the
        // journey is unambiguously finished, so an aborted warp yields nothing.
        if (level instanceof ServerLevel harvestLevel) {
            SpatialSiphonBlockEntity.harvest(airship, committedDistance, harvestLevel.getRandom());
        }

        // Everyone aboard, not just whoever pressed the button - and before committedDistance is
        // cleared below, because that is the only record of how far the ship actually went.
        AWCriteria.warpCompleted(airship.crew(), committedDistance, tier, destinationAnchor == null);

        airship.driveVelocity(new Vector3d());
        passengers.settle(airship);
        CrewManifest.close(airship.uuid());
        airship.warpData().release(false, level.getGameTime() + tier.cooldownTicks());
        flight = null;
        destinationAnchor = null;
        destinationPos = null;
        destinationLabel = "";
        initiator = null;
        committedCost = 0.0F;
        committedDistance = 0.0D;
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
        WarpTrace.abort(worldPosition, reason, flight, sequenceTicks);
        Airship airship = airship();
        if (airship != null) {
            // Hand the helm back and, if the hull is out in the corridor, bring it home.
            airship.driveVelocity(new Vector3d());
            send(airship.crew(), ClientboundCorridorPacket.leave());
            AirshipWarpData data = airship.warpData();
            // Only a hull that has actually been moved needs putting back. A run at the aperture, or
            // a passage through one, both happen where the ship already was.
            if (flight != null && flight.stage() != WarpFlight.Stage.APPROACH
                    && flight.stage() != WarpFlight.Stage.TRANSIT) {
                data.restoreOrigin(airship);
            }
            if (flight != null) {
                // Whichever apertures this warp tore open, they have nothing left to do.
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, flight.entryRift(), 0);
                broadcastRift(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, flight.exitRift(), 0);
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
        destinationLabel = "";
        initiator = null;
        committedCost = 0.0F;
        committedDistance = 0.0D;
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
            // Spin is not measured in ticks any more - see sequenceProgress.
            case STABILIZING -> 0;
            case WARPING -> tier.warpTicks();
            case ARRIVING -> AWConfig.RIFT_TRANSIT_TICKS.get() + tier.arriveTicks();
            case COOLDOWN -> Math.max(1, tier.cooldownTicks());
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
        if (level != null && !level.isClientSide && state.isSequenceRunning()) {
            abort(WarpFailure.INTERRUPTED);
        }
        super.remove();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.0D);
    }

    /**
     * Tells every client that can see this hull that it jumped rather than flew.
     *
     * <p>Sent to the crew and to both ends of the journey: a player standing at the departure point
     * and one waiting at the arrival both hold a copy of the sub-level, and both would otherwise
     * interpolate it across the whole distance.
     */
    private void announceCrossing(Airship airship, Vector3dc departure, Vector3dc arrival,
                                  List<ServerPlayer> crew) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ClientboundFoldCrossedPacket notice = new ClientboundFoldCrossedPacket(airship.uuid());
        send(crew, notice);
        AWNetwork.sendToTracking(serverLevel, new Vec3(departure.x(), departure.y(), departure.z()), notice);
        AWNetwork.sendToTracking(serverLevel, new Vec3(arrival.x(), arrival.y(), arrival.z()), notice);
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
        passengers.settle(airship);
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
        tag.putString("DestinationLabel", destinationLabel);
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
            tag.putFloat("SpinRate", (float) spinRate());
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
        committedDistance = tag.getDouble("CommittedDistance");
        sequenceTicks = tag.getInt("SequenceTicks");
        redstonePowered = tag.getBoolean("Powered");
        standingCourse = WarpCourse.load(
                tag.contains("StandingCourse") ? tag.getCompound("StandingCourse") : null);
        standingAuthor = tag.hasUUID("StandingAuthor") ? tag.getUUID("StandingAuthor") : null;
        spinRequired = tag.getFloat("SpinRequired");
        spinProgress = tag.getFloat("SpinProgress");
        cooldownTicks = tag.getInt("CooldownTicks");
        errorTicks = tag.getInt("ErrorTicks");
        lastFailure = WarpFailure.byIndex(tag.getInt("LastFailure"));
        heading = DriveHeading.byIndex(tag.getInt("Heading"));
        destinationAnchor = tag.hasUUID("Anchor") ? tag.getUUID("Anchor") : null;
        destinationPos = tag.contains("TargetX")
                ? new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"))
                : null;
        destinationLabel = tag.getString("DestinationLabel");
        initiator = tag.hasUUID("Initiator") ? tag.getUUID("Initiator") : null;
        flight = tag.contains("Flight") ? WarpFlight.load(tag.getCompound("Flight")) : null;
        if (clientPacket) {
            clientFlightStage = tag.contains("FlightStage") ? tag.getString("FlightStage") : "";
            clientFlightProgress = tag.getFloat("FlightProgress");
            clientCharge = charge;
            clientChargeLast = charge;
            syncedSpinRate = tag.getFloat("SpinRate");
            syncedMinimumRpm = tag.getInt("MinRpm");
            syncedPhaseDuration = tag.getInt("PhaseDuration");
            syncedStressImpact = tag.getFloat("StressImpact");
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
            destinationLabel = "";
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
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Airship airship = airship();
        Vec3 centre = new Vec3(rift.centre().x, rift.centre().y, rift.centre().z);
        AWNetwork.sendToTracking(serverLevel, centre, ClientboundWarpEffectPacket.rift(
                worldPosition, stage, tier.index(), airship == null ? null : airship.uuid(), rift,
                duration, throat));
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
