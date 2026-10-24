package uk.co.iceconchy.aerowarptics.flotilla;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.drive.DriveAssembly;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.UUID;

/**
 * The Rift Coupler: a module for a Rift Drive that lets its ship fold with others, as a flotilla.
 *
 * <h2>What it is</h2>
 * One of two things, as its pilot sets it: a <b>flagship</b>, whose drive holds the course and commits
 * the jump for everybody, or an <b>escort</b>, which takes station with a flagship and rides its wake.
 * Nothing about the coupler moves a ship. Every ship in a fold is flown by its own drive, on its own
 * charge, through its own rift; the coupler is what gets them to go together and come out in formation.
 *
 * <h2>Finding its drive</h2>
 * Exactly as the Modulator does: whichever drive, or formed Drive Extender of one, is found first among
 * its six neighbours, looked up at most once a tick.
 *
 * <h2>Who it answers to</h2>
 * The player who placed it, and operators. Neither Sable nor Simulated has any idea who owns a ship,
 * and a flotilla needs somebody to say yes to a stranger taking station or to cast one off. A coupler
 * placed by something that is not a player - a command, a structure - answers to anyone, the way an
 * unowned Rift Gate does.
 *
 * <h2>What the server remembers</h2>
 * The coupler's identity, owner and role live here, and it reports itself to the
 * {@link FlotillaRegistry} every second it is loaded. The flotilla - who follows whom, and where - lives
 * in the registry, which is world data. Only the flagship's muster, the few seconds of a fold, lives
 * here and dies with the block entity; see {@link FleetMuster}.
 */
public class RiftCouplerBlockEntity extends SmartBlockEntity implements GeoBlockEntity, IHaveGoggleInformation {

    /** A bucket: enough for a flotilla's worth of folds, not a reservoir. */
    public static final int CAPACITY = AWFluids.BUCKET;

    /** How often a loaded coupler tells the registry where it is. */
    private static final int TRACK_INTERVAL = 20;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_coupler.idle");
    private static final RawAnimation MUSTER = RawAnimation.begin().thenLoop("animation.rift_coupler.muster");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

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

    /** This coupler's own identity. Carried by the block entity, so it survives a crossing's rebuild. */
    @Nullable
    private UUID couplerId;
    @Nullable
    private UUID owner;
    private CouplerRole role = CouplerRole.ESCORT;

    /** The flagship's muster and fold. Server only, flagships only, and never saved. */
    @Nullable
    private FleetMuster muster;

    private int trackTimer;

    /** Whether a muster is on that this coupler is part of - what the model and the goggles show. */
    private boolean mustering;

    @Nullable
    private RiftDriveBlockEntity cachedDrive;
    private long cachedDriveTick = Long.MIN_VALUE;

    public RiftCouplerBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_COUPLER.get(), pos, state);
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
        // Not kinetic, and filled by a pipe against the fluid capability, like the Modulator.
    }

    // ------------------------------------------------------------------ linking

    /** The Rift Drive this coupler is fitted to, or {@code null}. See the class doc. */
    @Nullable
    public RiftDriveBlockEntity linkedDrive() {
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        if (cachedDriveTick == now) {
            return cachedDrive != null && !cachedDrive.isRemoved() ? cachedDrive : null;
        }
        cachedDriveTick = now;
        cachedDrive = null;
        for (Direction direction : Direction.values()) {
            RiftDriveBlockEntity drive = DriveAssembly.driveAt(level, worldPosition.relative(direction));
            if (drive != null) {
                cachedDrive = drive;
                break;
            }
        }
        return cachedDrive;
    }

    /** The ship this coupler is aboard: through its drive when it has one, which already knows. */
    @Nullable
    public Airship airship() {
        RiftDriveBlockEntity drive = linkedDrive();
        Airship airship = drive == null ? null : drive.airship();
        if (airship == null && level != null && !level.isClientSide) {
            airship = Airship.containing(this);
        }
        return airship != null && airship.isActive() ? airship : null;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (couplerId == null) {
            couplerId = UUID.randomUUID();
            setChanged();
        }
        if (--trackTimer <= 0) {
            trackTimer = TRACK_INTERVAL;
            track(serverLevel);
        }
        if (role == CouplerRole.FLAGSHIP) {
            if (muster == null) {
                muster = new FleetMuster(this);
            }
            muster.tick(serverLevel);
        } else if (muster != null) {
            muster.stand(serverLevel, FleetMuster.StandDown.ROLE_CHANGED);
            muster = null;
        }
        boolean now = role == CouplerRole.FLAGSHIP ? muster != null && muster.active() : escortMustering(serverLevel);
        if (now != mustering) {
            mustering = now;
            sendData();
        }
    }

    /** Whether the flagship this escort follows has a muster on. */
    private boolean escortMustering(ServerLevel serverLevel) {
        RiftCouplerBlockEntity flagship = flagship(serverLevel);
        return flagship != null && flagship.muster != null && flagship.muster.active();
    }

    /**
     * Tells the registry where this coupler is and what it is, and passes on anything that changed as a
     * result - an escort released because this coupler stopped leading.
     */
    void track(ServerLevel serverLevel) {
        if (couplerId == null) {
            return;
        }
        Airship airship = airship();
        RiftDriveBlockEntity drive = linkedDrive();
        Vector3d centre = airship == null ? new Vector3d(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) : airship.centre(new Vector3d());
        String name = airship == null || airship.name() == null ? "" : airship.name();
        CouplerRecord record = new CouplerRecord(couplerId, serverLevel.dimension(), worldPosition,
                airship == null ? null : airship.uuid(), owner, name, centre.x, centre.y, centre.z, role,
                drive == null ? -1 : drive.tier().index());
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        CouplerRecord known = registry.coupler(couplerId);
        if (known != null && !known.pos().equals(worldPosition) && find(serverLevel, known) != null) {
            // Another coupler in this level, still standing where the record says, answers to this id: this
            // one was copied from it with its data, by a creative pick-block or a structure. The original
            // keeps the name; this one becomes somebody new rather than two couplers sharing a flotilla.
            couplerId = UUID.randomUUID();
            role = CouplerRole.ESCORT;
            setChanged();
            record = new CouplerRecord(couplerId, record.dimension(), record.pos(), record.ship(), owner,
                    record.shipName(), record.x(), record.y(), record.z(), role, record.tier());
        }
        List<UUID> released = registry.track(record);
        FleetNotices.released(serverLevel, released, record);
    }

    // ----------------------------------------------------------------- identity

    @Nullable
    public UUID couplerId() {
        return couplerId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID newOwner) {
        owner = newOwner;
        setChanged();
    }

    public CouplerRole role() {
        return role;
    }

    public boolean mustering() {
        return mustering;
    }

    /**
     * Whether this player may give this coupler orders: its owner, an operator, or anyone at all for a
     * coupler nobody placed.
     */
    public boolean mayCommand(ServerPlayer player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /**
     * Sets the coupler to lead or to follow.
     *
     * <p>Leaving the flagship's role disbands its flotilla, and the escorts are told; taking it gives up
     * any station this ship had with somebody else, since a flotilla is flat. Refused while a fold is in
     * hand, which is a poor moment to change which ship is in charge of it.
     */
    public boolean setRole(ServerPlayer player, CouplerRole newRole) {
        if (!mayCommand(player) || newRole == role || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (muster != null && muster.folding()) {
            return false;
        }
        role = newRole;
        setChanged();
        sendData();
        track(serverLevel);
        return true;
    }

    /** The flagship coupler this escort follows, if it is loaded in this level. */
    @Nullable
    public RiftCouplerBlockEntity flagship(ServerLevel serverLevel) {
        if (couplerId == null) {
            return null;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        UUID lead = registry.flagshipOf(couplerId);
        return lead == null ? null : find(serverLevel, registry.coupler(lead));
    }

    /**
     * A coupler by its record, if it is loaded in this level and still the coupler the record names.
     *
     * <p>Checked by id as well as by position: a plot position can be reused by a different ship once the
     * first has gone, and a record that points at somebody else's coupler must not be believed.
     */
    @Nullable
    public static RiftCouplerBlockEntity find(ServerLevel serverLevel, @Nullable CouplerRecord record) {
        if (record == null || !record.dimension().equals(serverLevel.dimension())
                || !serverLevel.isLoaded(record.pos())) {
            return null;
        }
        return serverLevel.getBlockEntity(record.pos()) instanceof RiftCouplerBlockEntity coupler
                && record.id().equals(coupler.couplerId) && !coupler.isRemoved() ? coupler : null;
    }

    /** The flagship's muster, or {@code null} on an escort. */
    @Nullable
    public FleetMuster muster() {
        return muster;
    }

    // ------------------------------------------------------------- taking station

    /**
     * Asks to take station with a flagship: the Station screen's Take Station button.
     *
     * <p>Checked here, on the server, whatever the screen offered: that this player may command this
     * coupler, that it is an escort on a ship with a drive, and that the flagship is a real one, loaded,
     * in this dimension and within {@code takeStationRange}. The flagship's owner then has to say yes,
     * unless their auto-accept is on and the same player placed both couplers - or the player asking is
     * the flagship's owner themself.
     */
    public JoinResult takeStation(ServerPlayer player, UUID flagshipId) {
        if (!(level instanceof ServerLevel serverLevel) || couplerId == null || !mayCommand(player)) {
            return JoinResult.UNKNOWN;
        }
        if (role != CouplerRole.ESCORT) {
            return JoinResult.NOT_AN_ESCORT;
        }
        Airship ship = airship();
        if (ship == null || linkedDrive() == null) {
            return JoinResult.OUT_OF_REACH;
        }
        track(serverLevel);
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        RiftCouplerBlockEntity lead = find(serverLevel, registry.coupler(flagshipId));
        if (lead == null || lead.role != CouplerRole.FLAGSHIP) {
            return lead == null ? JoinResult.OUT_OF_REACH : JoinResult.NOT_A_FLAGSHIP;
        }
        Airship flagshipShip = lead.airship();
        RiftDriveBlockEntity flagshipDrive = lead.linkedDrive();
        if (flagshipShip == null || flagshipDrive == null) {
            return JoinResult.OUT_OF_REACH;
        }
        double range = AWConfig.TAKE_STATION_RANGE.get();
        if (flagshipShip.centre(new Vector3d()).distanceSquared(ship.centre(new Vector3d())) > range * range) {
            return JoinResult.OUT_OF_REACH;
        }
        if (lead.muster != null && lead.muster.folding()) {
            return JoinResult.OUT_OF_REACH;
        }
        lead.track(serverLevel);
        Flotilla flotilla = registry.led(flagshipId);
        boolean sameOwner = owner != null && owner.equals(lead.owner);
        boolean admit = lead.mayCommand(player) && (lead.owner == null || player.getUUID().equals(lead.owner))
                || flotilla != null && flotilla.autoAccept() && sameOwner;
        FormationSlot slot = FleetSpace.slotOf(flagshipShip, flagshipDrive.shipBow(), ship);
        JoinResult result = registry.request(couplerId, flagshipId, admit, slot,
                flagshipDrive.tier().flotillaEscorts());
        FleetNotices.joined(serverLevel, result, registry.coupler(couplerId), registry.coupler(flagshipId));
        return result;
    }

    /** Breaking off: this escort leaves its flotilla, or takes back a request. */
    public boolean breakOff(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || couplerId == null || !mayCommand(player)) {
            return false;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        UUID lead = registry.flagshipOf(couplerId);
        RiftCouplerBlockEntity flagship = flagship(serverLevel);
        if (flagship != null && flagship.muster != null && flagship.muster.folding()) {
            // Mid-fold the escort's drive is already committed; breaking off now would leave it flying a
            // formation that has forgotten it. It can break off the moment it is out the other side.
            return false;
        }
        boolean changed = registry.breakOff(couplerId);
        if (changed && lead != null) {
            FleetNotices.brokeOff(serverLevel, registry.coupler(couplerId), registry.coupler(lead));
        }
        return changed;
    }

    // -------------------------------------------------------------- the fleet board

    /**
     * Whether this player may give this flagship's flotilla orders: the flagship's coupler must be theirs,
     * it must lead, and no fold may be in hand - the escorts' drives have their orders by then.
     */
    private boolean mayOrder(ServerPlayer player) {
        return role == CouplerRole.FLAGSHIP && couplerId != null && mayCommand(player)
                && (muster == null || !muster.folding());
    }

    /** The flagship's owner takes a ship that asked. */
    public JoinResult accept(ServerPlayer player, UUID escort) {
        if (!(level instanceof ServerLevel serverLevel) || !mayOrder(player)) {
            return JoinResult.UNKNOWN;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        RiftCouplerBlockEntity follower = find(serverLevel, registry.coupler(escort));
        Airship flagshipShip = airship();
        RiftDriveBlockEntity drive = linkedDrive();
        Airship escortShip = follower == null ? null : follower.airship();
        if (flagshipShip == null || drive == null || escortShip == null) {
            return JoinResult.OUT_OF_REACH;
        }
        double range = AWConfig.TAKE_STATION_RANGE.get();
        if (flagshipShip.centre(new Vector3d()).distanceSquared(escortShip.centre(new Vector3d())) > range * range) {
            return JoinResult.OUT_OF_REACH;
        }
        FormationSlot slot = FleetSpace.slotOf(flagshipShip, drive.shipBow(), escortShip);
        JoinResult result = registry.accept(couplerId, escort, slot, drive.tier().flotillaEscorts());
        FleetNotices.joined(serverLevel, result, registry.coupler(escort), registry.coupler(couplerId));
        return result;
    }

    public boolean refuse(ServerPlayer player, UUID escort) {
        if (!(level instanceof ServerLevel serverLevel) || !mayOrder(player)) {
            return false;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        boolean refused = registry.refuse(couplerId, escort);
        if (refused) {
            FleetNotices.refused(serverLevel, registry.coupler(escort), registry.coupler(couplerId));
        }
        return refused;
    }

    /** Casting off: the flagship lets one escort go. */
    public boolean castOff(ServerPlayer player, UUID escort) {
        if (!(level instanceof ServerLevel serverLevel) || !mayOrder(player)) {
            return false;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        boolean cast = registry.castOff(couplerId, escort);
        if (cast) {
            FleetNotices.castOff(serverLevel, registry.coupler(escort), registry.coupler(couplerId));
        }
        return cast;
    }

    /**
     * Moves an escort's slot, as dragged or scrolled on the Fleet Board.
     *
     * <p>Everything about the new slot is decided here and not on the screen: it is snapped to the block,
     * pulled in to {@code formationReach}, and refused outright if it would bring that ship within reach of
     * another - the flagship included - allowing for both their footprints and both their tolerances. The
     * board is drawn from above, so a drag sends the slot's height back unchanged and a scroll over a
     * marker changes only its height.
     *
     * @return {@link CouplerNotice#NONE} when the slot moved; otherwise why it did not, for the board to say
     */
    public CouplerNotice moveSlot(ServerPlayer player, UUID escort, double right, double up, double ahead) {
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(ahead)) {
            return CouplerNotice.of(CouplerNotice.SLOT_UNREAD);
        }
        return placeSlot(player, escort, new FormationSlot(right, up, ahead));
    }

    /**
     * Gives an escort the slot it is flying in now, height and all: the Fleet Board's reset, for a slot that
     * has wandered off from its ship or a ship that has wandered off from its slot.
     *
     * <p>Read here from where the ship actually is, never from the screen, which has only ever seen the
     * formation from above - and so could put a slot back under its ship but never back at its height. That
     * is what dragging a marker back onto its ship used to do: the dot sat in the middle of the box and the
     * row still said adrift, because the drift was straight up.
     */
    public CouplerNotice slotWhereItIs(ServerPlayer player, UUID escort) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return CouplerNotice.of(CouplerNotice.SLOT_UNREAD);
        }
        Airship flagshipShip = airship();
        RiftDriveBlockEntity drive = linkedDrive();
        RiftCouplerBlockEntity follower = find(serverLevel, FlotillaRegistry.get(serverLevel).coupler(escort));
        Airship escortShip = follower == null ? null : follower.airship();
        if (flagshipShip == null || drive == null || escortShip == null) {
            return CouplerNotice.of(CouplerNotice.SLOT_UNREAD);
        }
        return placeSlot(player, escort, FleetSpace.slotOf(flagshipShip, drive.shipBow(), escortShip));
    }

    /** Snaps, pulls in and checks a slot, and gives it to the escort if it is clear of everybody else. */
    private CouplerNotice placeSlot(ServerPlayer player, UUID escort, FormationSlot asked) {
        if (!(level instanceof ServerLevel serverLevel) || !mayOrder(player)) {
            return CouplerNotice.NONE;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(serverLevel);
        Flotilla flotilla = registry.led(couplerId);
        Flotilla.Member member = flotilla == null ? null : flotilla.member(escort);
        Airship flagshipShip = airship();
        RiftCouplerBlockEntity follower = find(serverLevel, registry.coupler(escort));
        Airship escortShip = follower == null ? null : follower.airship();
        if (member == null || flagshipShip == null || escortShip == null) {
            return CouplerNotice.of(CouplerNotice.SLOT_UNREAD);
        }
        FormationSlot wanted = Formation.clampReach(Formation.snap(asked, 1.0D), AWConfig.FORMATION_REACH.get());
        List<FormationSlot> others = new java.util.ArrayList<>();
        List<Formation.Footprint> footprints = new java.util.ArrayList<>();
        List<UUID> owners = new java.util.ArrayList<>();
        others.add(FormationSlot.FLAGSHIP);
        footprints.add(FleetSpace.footprint(flagshipShip));
        owners.add(couplerId);
        for (Flotilla.Member other : flotilla.escorts()) {
            if (other.coupler().equals(escort)) {
                continue;
            }
            RiftCouplerBlockEntity ship = find(serverLevel, registry.coupler(other.coupler()));
            Airship hull = ship == null ? null : ship.airship();
            others.add(other.slot());
            footprints.add(hull == null ? new Formation.Footprint(0, 0) : FleetSpace.footprint(hull));
            owners.add(other.coupler());
        }
        double margin = 2.0D * AWConfig.STATION_TOLERANCE.get();
        int clash = Formation.firstClash(wanted, FleetSpace.footprint(escortShip), others, footprints, margin);
        if (clash == 0) {
            return CouplerNotice.of(CouplerNotice.SLOT_NEAR_FLAGSHIP);
        }
        if (clash > 0) {
            CouplerRecord record = registry.coupler(owners.get(clash));
            return CouplerNotice.of(CouplerNotice.SLOT_NEAR_SHIP, record == null ? "" : record.shipName());
        }
        registry.setSlot(couplerId, escort, wanted);
        return CouplerNotice.NONE;
    }

    public void setAutoAccept(ServerPlayer player, boolean value) {
        if (level instanceof ServerLevel serverLevel && mayOrder(player)) {
            FlotillaRegistry.get(serverLevel).setAutoAccept(couplerId, value);
        }
    }

    public void setBeaconCallsAll(ServerPlayer player, boolean value) {
        if (level instanceof ServerLevel serverLevel && mayOrder(player)) {
            FlotillaRegistry.get(serverLevel).setBeaconCallsAll(couplerId, value);
        }
    }

    /**
     * Whether the flotilla folds through one rift or a rift each. Taken when Fold is pressed, so changing it
     * with a fold in hand changes the next one - but the board does not offer it then, and nor does this.
     */
    public void setSharedRift(ServerPlayer player, boolean value) {
        if (level instanceof ServerLevel serverLevel && mayOrder(player) && (muster == null || !muster.folding())) {
            FlotillaRegistry.get(serverLevel).setSharedRift(couplerId, value);
        }
    }

    /**
     * Renames this coupler's own ship, through Sable, which tells every client.
     *
     * <p>Only a ship's own coupler renames it - the flagship's from the Fleet Board, an escort's from its
     * Station screen - so nobody names a ship that is not theirs. Refused if another ship in the same
     * flotilla already has the name.
     */
    public boolean rename(ServerPlayer player, String raw) {
        if (!(level instanceof ServerLevel serverLevel) || !mayCommand(player)) {
            return false;
        }
        Airship ship = airship();
        if (ship == null) {
            return false;
        }
        String name = FleetNames.clean(raw);
        if (!CouplerPanels.nameFree(serverLevel, couplerId, name)) {
            return false;
        }
        ship.subLevel().setName(name.isEmpty() ? null : name);
        track(serverLevel);
        return true;
    }

    /** Sound Stations, from the Fleet Board. */
    public boolean soundStations(ServerPlayer player) {
        return level instanceof ServerLevel serverLevel && role == CouplerRole.FLAGSHIP && muster != null
                && muster.soundStations(serverLevel, player);
    }

    /** Stand Down, from the Fleet Board: the muster sounded by mistake, or no longer wanted. */
    public boolean standDown(ServerPlayer player) {
        return level instanceof ServerLevel serverLevel && role == CouplerRole.FLAGSHIP && muster != null
                && muster.standDown(serverLevel, player);
    }

    /** Fold, from the Fleet Board; {@code forced} when the button was held through the confirmation. */
    public boolean fold(ServerPlayer player, boolean forced) {
        return level instanceof ServerLevel serverLevel && role == CouplerRole.FLAGSHIP && muster != null
                && muster.fold(serverLevel, player, forced);
    }

    // ------------------------------------------------------------------ essence

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    /** Takes essence if there is that much, and says whether it did. */
    boolean spend(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (tank.drain(amount, IFluidHandler.FluidAction.SIMULATE).getAmount() < amount) {
            return false;
        }
        tank.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    // ----------------------------------------------------------------- removal

    @Override
    public void remove() {
        if (level instanceof ServerLevel serverLevel && couplerId != null) {
            if (muster != null) {
                muster.stand(serverLevel, FleetMuster.StandDown.COUPLER_LOST);
            }
            // Only when the block has gone, which the drive tells apart from an unload the same way: the
            // block in the world is no longer this one. An unloaded coupler stays in its flotilla, out of
            // contact. And even then only marked, not forgotten - see FlotillaRegistry.markGone for the two
            // ways a coupler's block goes away without anybody breaking it.
            if (serverLevel.getBlockState(worldPosition).getBlock() != getBlockState().getBlock()) {
                FlotillaRegistry.get(serverLevel).markGone(couplerId, serverLevel.dimension(), worldPosition,
                        serverLevel.getGameTime());
            }
        }
        super.remove();
    }

    // ------------------------------------------------------------- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "coupler", 5, state -> {
            state.setAnimation(mustering ? MUSTER : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_coupler").forGoggles(tooltip);
        AWLang.translate(role.translationKey()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        if (linkedDrive() == null) {
            AWLang.translate("gui.rift_coupler.not_linked").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        if (mustering) {
            AWLang.translate("gui.rift_coupler.mustering").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        AWLang.translate("gui.rift_coupler.essence", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        return true;
    }

    // ------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Role", role.ordinal());
        if (couplerId != null) {
            tag.putUUID("CouplerId", couplerId);
        }
        if (owner != null && !clientPacket) {
            tag.putUUID("Owner", owner);
        }
        if (clientPacket) {
            tag.putBoolean("Mustering", mustering);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        role = CouplerRole.byIndex(tag.getInt("Role"));
        if (tag.hasUUID("CouplerId")) {
            couplerId = tag.getUUID("CouplerId");
        }
        if (!clientPacket) {
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        } else {
            mustering = tag.getBoolean("Mustering");
        }
    }

    /** Whether this coupler is aboard an airship rather than standing on the ground. */
    public boolean aboard() {
        return airship() != null;
    }
}
