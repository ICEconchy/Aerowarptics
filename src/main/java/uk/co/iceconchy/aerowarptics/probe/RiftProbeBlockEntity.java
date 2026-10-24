package uk.co.iceconchy.aerowarptics.probe;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.List;

/**
 * The Rift Probe: a way to go somewhere nobody has been.
 *
 * <p>Every other destination in this mod is a place a player carried a block to. That makes the map a
 * closed loop - you can only warp to where you have already walked - and the probe is what opens it.
 * It throws a rift at a bearing and a distance, holds the far ground open long enough to read it, and
 * hands back the same survey the Astrolabe draws of an anchor. The pilot then decides on the same
 * evidence they would have had anywhere else.
 *
 * <p>The wait is not theatre. A sounding thrown at unexplored terrain is asking the server to bring
 * that terrain into being, and {@link ProbeTicket} is what makes it happen off the critical path. When
 * the region has not finished arriving by the configured timeout, the probe reads what is there and
 * says the reading is thin - which is the honest answer, and better than either lying or waiting
 * forever.
 *
 * <p>Rift Essence is the cost, which is what ties this to the Spatial Siphon: you pay for somewhere
 * new with what the last journey shed.
 */
public class RiftProbeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /**
     * What the tank is built with, and what it holds while {@code probe.probeEssenceBuffer} is nought:
     * effectively unbounded, so a probe keeps whatever is piped in. The size in force is
     * {@link #capacity()}.
     */
    public static final int CAPACITY = Integer.MAX_VALUE;

    /** How far a sounding reads, in blocks. Matches the Astrolabe so the two charts are comparable. */
    public static final int SURVEY_RADIUS = DestinationSurvey.RADIUS;

    /** Ticks between checks on whether the far ground has arrived. Chunks do not load every tick. */
    private static final int POLL_INTERVAL = 5;

    private final FluidTank tank = new FluidTank(CAPACITY,
            stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        // The buffer is the server's config and can change under a standing probe, so it is asked for
        // when it matters rather than fixed at construction. Never on the client, which has no say in
        // what the tank takes and must not read the server's numbers.
        private void resize() {
            if (level != null && !level.isClientSide) {
                setCapacity(RiftProbeBlockEntity.capacity());
            }
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            resize();
            return super.fill(resource, action);
        }

        @Override
        public int getCapacity() {
            resize();
            return super.getCapacity();
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    private ProbeBearing bearing = ProbeBearing.NORTH;
    private int range = 2_000;
    /**
     * The dimension a sounding is thrown into, or {@code null} for whichever one the probe is in - so a
     * probe nobody has aimed elsewhere goes on sounding where it stands after its ship crosses.
     *
     * <p>Only ever what the panel was offered at the time ({@link ProbeDimensions#offered}), and asked
     * again when it is used: a server can stop crossing dimensions, or take one off its list, while a
     * probe is still aimed at it. See {@link #sounded}.
     */
    @Nullable
    private ResourceKey<Level> dimension;
    /**
     * How far above the ground it finds the probe asks the drive to bring the ship in, or
     * {@link ArrivalHeight#UNSET} until somebody moves the slider - which follows the server default.
     */
    private int arrivalHeight = ArrivalHeight.UNSET;
    private ProbeState state = ProbeState.IDLE;

    /** Where the current sounding is aimed, kept while it reaches so the poll knows what to watch. */
    @Nullable
    private BlockPos target;
    /** The dimension that aim is in, kept with it: the ticket holding its ground was placed there. */
    @Nullable
    private ResourceKey<Level> targetDimension;

    private int reachTicks;

    @Nullable
    private ProbeSounding sounding;
    /**
     * The dimension the reading was taken in, or {@code null} for one saved before a probe could look
     * anywhere but where it stood. Kept beside the reading rather than worked out from the dial, because
     * it is what a course set from the reading names - and a fix flown in the wrong dimension is a ship
     * sent to the same numbers somewhere else entirely.
     */
    @Nullable
    private ResourceKey<Level> soundingDimension;
    /**
     * Whoever threw the sounding now out, for a Rift Ward to ask whose scan it is. Not saved: a sounding read
     * after a reload is nobody's, and a ward shows nobody the ground under its field.
     */
    @Nullable
    private java.util.UUID soundedBy;

    public RiftProbeBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_PROBE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ------------------------------------------------------------------ state

    public IFluidHandler tank() {
        return tank;
    }

    public int essence() {
        return tank.getFluidAmount();
    }

    public ProbeBearing bearing() {
        return bearing;
    }

    public int range() {
        return range;
    }

    /**
     * The level a sounding thrown now would read: the one the dial names while the server still offers
     * it, and otherwise the probe's own.
     */
    private ServerLevel sounded(ServerLevel here) {
        if (dimension == null || dimension.equals(here.dimension()) || !ProbeDimensions.offers(here, dimension)) {
            return here;
        }
        ServerLevel there = here.getServer().getLevel(dimension);
        return there == null ? here : there;
    }

    /** The dimension a sounding thrown now would read. Server side; the probe's own anywhere else. */
    public ResourceKey<Level> soundedDimension() {
        if (level instanceof ServerLevel here) {
            return sounded(here).dimension();
        }
        return level == null ? Level.OVERWORLD : level.dimension();
    }

    /** The dimension the reading is of. Meaningless without one. */
    public ResourceKey<Level> readingDimension() {
        if (soundingDimension != null) {
            return soundingDimension;
        }
        return level == null ? Level.OVERWORLD : level.dimension();
    }

    /** Whether the reading is of a dimension other than the one the probe is in. */
    public boolean readingElsewhere() {
        return sounding != null && level != null && !readingDimension().equals(level.dimension());
    }

    /**
     * What a course set from the reading is called on the drive's console: the sounding's own label, and
     * the dimension when it is not this one - "NE 3,200" says where on a map, and not which map.
     */
    public String courseLabel() {
        if (sounding == null) {
            return "";
        }
        return readingElsewhere()
                ? sounding.label() + ", " + ProbeDimensions.plainName(readingDimension().location())
                : sounding.label();
    }

    /** The arrival height a course set from here would carry. Server side. */
    public int arrivalHeight() {
        return ArrivalHeight.resolve(arrivalHeight);
    }

    public ProbeState state() {
        return state;
    }

    @Nullable
    public ProbeSounding sounding() {
        return sounding;
    }

    /** How far through the wait a sounding is, 0..1, for the bar on the panel. */
    public float reachProgress() {
        if (state != ProbeState.REACHING) {
            return state == ProbeState.IDLE ? 0.0F : 1.0F;
        }
        int timeout = Math.max(1, AWConfig.PROBE_TIMEOUT_TICKS.get());
        return Mth.clamp(reachTicks / (float) timeout, 0.0F, 1.0F);
    }

    /** Millibuckets a sounding at the current settings would cost. */
    public int cost() {
        return costOf(range);
    }

    public static int costOf(int range) {
        double perBlock = AWConfig.PROBE_COST_PER_BLOCK.get();
        long total = AWConfig.PROBE_COST.get() + Math.round(perBlock * Math.max(0, range));
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Millibuckets the longest possible sounding costs: what a "full" supply means on the panel. */
    public static int fullSupply() {
        return Math.max(1, costOf(maximumRange()));
    }

    /**
     * Millibuckets a probe holds: the configured buffer, never less than {@link #fullSupply()} so the
     * longest sounding the dial offers can always be paid for, or {@link #CAPACITY} when it is nought.
     */
    public static int capacity() {
        return capacityOf(AWConfig.PROBE_ESSENCE_BUFFER.get(), fullSupply());
    }

    public static int capacityOf(int buffer, int fullSupply) {
        return buffer <= 0 ? CAPACITY : Math.max(buffer, fullSupply);
    }

    public static int minimumRange() {
        return AWConfig.PROBE_MINIMUM_RANGE.get();
    }

    public static int maximumRange() {
        return AWConfig.PROBE_MAXIMUM_RANGE.get();
    }

    // ----------------------------------------------------------------- airship

    /**
     * The airship this probe is bolted to, or {@code null} when it is standing on the ground.
     *
     * <p>Looked up from the plot grid rather than waited for, for the same reason the Astrolabe does
     * it that way: Sable only registers an actor when a block changes in the plot, so a probe that
     * arrived on an already-assembled hull may never be told it is aboard one.
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

    /** The Rift Drive on this probe's hull, or {@code null} when there is not one. */
    @Nullable
    public RiftDriveBlockEntity drive() {
        Airship airship = airship();
        return airship == null ? null : airship.machine(RiftDriveBlockEntity.class);
    }

    /**
     * Where a sounding is thrown from.
     *
     * <p>The ship's middle when there is one, so a bearing means the same thing regardless of where on
     * the hull the probe is bolted. A probe on the ground sounds from itself, which makes it useful
     * for scouting before there is a ship - it simply has no drive to hand the course to afterwards.
     */
    public Vec3 origin() {
        Airship airship = airship();
        if (airship == null) {
            return Vec3.atCenterOf(worldPosition);
        }
        Vector3d centre = airship.centre(new Vector3d());
        return new Vec3(centre.x, centre.y, centre.z);
    }

    // ----------------------------------------------------------------- setting

    public void setBearing(ProbeBearing next) {
        if (state.busy() || bearing == next) {
            return;
        }
        bearing = next;
        // A reading belongs to the bearing it was taken on. Keeping it while the dial moves would
        // leave a picture of the north-east sitting under a panel that says west.
        discard();
        markDirty();
    }

    public void setRange(int blocks) {
        int clamped = Mth.clamp(blocks, minimumRange(), maximumRange());
        if (state.busy() || range == clamped) {
            return;
        }
        range = clamped;
        discard();
        markDirty();
    }

    /**
     * Aims the probe at a dimension, from the panel's list.
     *
     * <p>Asked again here rather than trusted: the list a panel was sent is as old as the panel, and a
     * sounding is a request to generate ground, which is not made in a dimension the server has not
     * offered. A reading belongs to the dimension it was taken in, as it does to its bearing, so it goes
     * when the dial moves.
     */
    public void setDimension(ResourceKey<Level> next) {
        if (!(level instanceof ServerLevel here) || state.busy()) {
            return;
        }
        if (!ProbeDimensions.offers(here, next)) {
            Trace.log(Trace.PROBE, "{} not aimed at {}: this server does not offer it to a probe in {} (offered {})",
                    Trace.pos(worldPosition), next.location(), here.dimension().location(),
                    ProbeDimensions.offered(here).stream().map(key -> key.location().toString()).toList());
            return;
        }
        ResourceKey<Level> was = sounded(here).dimension();
        dimension = next.equals(here.dimension()) ? null : next;
        if (!was.equals(next)) {
            discard();
        }
        markDirty();
    }

    /**
     * Sets how high a ship sent from here comes in.
     *
     * <p>Unlike the bearing and the range this leaves the reading alone, and is allowed while a
     * sounding is out: it changes nothing about the ground the probe is reading, only where over that
     * ground the ship will be asked to appear. A course already handed to the drive keeps the height it
     * was set with until the pilot sends it again - setting a course is the decision, and moving a
     * slider on another machine afterwards is not.
     */
    public void setArrivalHeight(int blocks) {
        int clamped = ArrivalHeight.clamp(blocks, ArrivalHeight.maximum());
        if (arrivalHeight == clamped) {
            return;
        }
        arrivalHeight = clamped;
        markDirty();
    }

    private void discard() {
        sounding = null;
        soundingDimension = null;
        target = null;
        targetDimension = null;
        reachTicks = 0;
        state = ProbeState.IDLE;
    }

    private void markDirty() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    // --------------------------------------------------------------- sounding

    /**
     * Throws a sounding.
     *
     * <p>The essence is spent here, before anything is known. That is deliberate: a probe is paid for
     * by the attempt, not by the result, and a reading that comes back as bare rock is still a reading
     * somebody now has.
     */
    public WarpFailure sound(ServerPlayer player) {
        WarpFailure result = throwSounding(player);
        Trace.log(Trace.PROBE, "{} sounding by {} {} {} blocks -> {} (state {} essence {}/{}, aim {} in {})",
                Trace.pos(worldPosition), Trace.who(player), bearing, range, result.getSerializedName(), state,
                tank.getFluidAmount(), cost(), Trace.pos(target),
                targetDimension == null ? "no dimension" : targetDimension.location());
        return result;
    }

    private WarpFailure throwSounding(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return WarpFailure.DRIVE_BUSY;
        }
        if (state.busy()) {
            return WarpFailure.DRIVE_BUSY;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > reachSqr()) {
            return WarpFailure.UNAUTHORISED;
        }

        int price = cost();
        if (tank.getFluidAmount() < price) {
            return WarpFailure.INSUFFICIENT_ESSENCE;
        }
        tank.drain(price, IFluidHandler.FluidAction.EXECUTE);

        // Into the dimension the dial names, from where this spot is over there - see ProbeAim. Clamped
        // into that world's own limits, so a sounding thrown at the edge of a bounded world reads the
        // edge rather than nothing at all.
        ServerLevel there = sounded(serverLevel);
        Vec3 from = origin();
        WorldBorder border = there.getWorldBorder();
        double scale = there == serverLevel ? 1.0D
                : DimensionType.getTeleportationScale(serverLevel.dimensionType(), there.dimensionType());
        BlockPos aim = ProbeAim.target(from.x, from.y, from.z, scale, bearing, range,
                border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ(),
                there.getMinBuildHeight(), there.getMaxBuildHeight());

        target = aim;
        targetDimension = there.dimension();
        soundedBy = player.getUUID();
        reachTicks = 0;
        state = ProbeState.REACHING;
        sounding = null;
        soundingDimension = null;
        ProbeTicket.hold(there, aim, SURVEY_RADIUS);
        serverLevel.playSound(null, worldPosition, AWSounds.RIFT_OPEN.get(), SoundSource.BLOCKS, 0.7F, 1.5F);
        markDirty();
        return WarpFailure.NONE;
    }

    private double reachSqr() {
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get();
        return reach * reach;
    }

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null || level.isClientSide || state != ProbeState.REACHING) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || target == null) {
            discard();
            return;
        }
        // Read where it was thrown, not where the dial points now: the two only differ if the server
        // stopped offering that dimension mid-sounding, and the ground being held is still over there.
        ServerLevel there = targetDimension == null ? serverLevel : serverLevel.getServer().getLevel(targetDimension);
        if (there == null) {
            Trace.log(Trace.PROBE, "{} sounding abandoned: {} is no longer a dimension this server has",
                    Trace.pos(worldPosition), targetDimension.location());
            discard();
            markDirty();
            return;
        }

        reachTicks++;
        int floor = AWConfig.PROBE_REACH_TICKS.get();
        int timeout = AWConfig.PROBE_TIMEOUT_TICKS.get();
        boolean timedOut = reachTicks >= timeout;
        if (reachTicks < floor) {
            return;
        }
        if (!timedOut && (reachTicks % POLL_INTERVAL != 0 || !regionReady(there, target))) {
            return;
        }
        read(there, target);
    }

    /**
     * Lets go of a reading the dial no longer points at.
     *
     * <p>The dial cannot be moved off a reading - {@link #setDimension} discards it - but the ground
     * under the probe can: a ship that crosses takes its probe with it, and a probe left aimed at "here"
     * is then aimed somewhere else, over a map of the dimension it has just left. Asked on the slow tick
     * because nothing about it is urgent, and the answer walks the server's levels.
     */
    @Override
    public void lazyTick() {
        super.lazyTick();
        if (sounding == null || state.busy() || !(level instanceof ServerLevel here)) {
            return;
        }
        if (!readingDimension().equals(sounded(here).dimension())) {
            Trace.log(Trace.PROBE, "{} reading of {} dropped: the probe is in {} and aimed at {}",
                    Trace.pos(worldPosition), readingDimension().location(), here.dimension().location(),
                    sounded(here).dimension().location());
            discard();
            markDirty();
        }
    }

    /**
     * Whether enough of the far region has arrived to be worth reading.
     *
     * <p>The centre must be there - without it there is no ground height to aim at and the whole
     * sounding is wasted - and most of the rest, so the picture is a place rather than a few islands.
     */
    private boolean regionReady(ServerLevel serverLevel, BlockPos centre) {
        return DestinationSurvey.ready(serverLevel, centre, ProbeSounding.THIN_COVERAGE);
    }

    private void read(ServerLevel serverLevel, BlockPos centre) {
        DestinationSurvey ground = DestinationSurvey.of(serverLevel, centre);
        // Whatever of it lies under a Rift Ward's field is the ward's to say, to anyone it would turn aside.
        DestinationSurvey survey = uk.co.iceconchy.aerowarptics.ward.WardScans.probe(serverLevel, ground, soundedBy);
        if (survey != ground && soundedBy != null && level instanceof ServerLevel here
                && here.getServer().getPlayerList().getPlayer(soundedBy) instanceof ServerPlayer scout) {
            uk.co.iceconchy.aerowarptics.util.AWLang.translate("message.probe_interference").sendStatus(scout);
        }
        soundedBy = null;
        ProbeSounding result = new ProbeSounding(bearing, range, survey);
        Trace.log(Trace.PROBE, "{} sounding read at {} in {} after {}t{}: ground y {} -> {}", Trace.pos(worldPosition),
                Trace.pos(centre), serverLevel.dimension().location(), reachTicks,
                reachTicks >= AWConfig.PROBE_TIMEOUT_TICKS.get()
                        ? " (TIMED OUT waiting for the ground to load)" : "", survey.groundY(),
                result.usable() ? "usable, fix " + Trace.pos(result.fix()) : "NOT USABLE");
        sounding = result;
        soundingDimension = serverLevel.dimension();
        targetDimension = null;
        state = result.usable() ? ProbeState.COMPLETE : ProbeState.FAILED;
        target = null;
        serverLevel.playSound(null, worldPosition,
                result.usable() ? AWSounds.DESTINATION_LOCK.get() : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.BLOCKS, 0.7F, result.usable() ? 1.3F : 0.7F);
        markDirty();
    }

    /**
     * Hands the current reading to the drive as a course.
     *
     * <p>The probe does not launch anything, for the same reason the Astrolabe does not: committing a
     * whole ship to a journey is the drive's business, and this is a chart room.
     */
    public WarpFailure setCourse(ServerPlayer player) {
        ProbeSounding result = sounding;
        WarpFailure verdict;
        RiftDriveBlockEntity drive = null;
        if (result == null || !result.usable()) {
            verdict = WarpFailure.NO_SAFE_ARRIVAL;
        } else if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > reachSqr()) {
            verdict = WarpFailure.UNAUTHORISED;
        } else if ((drive = drive()) == null) {
            verdict = WarpFailure.NO_AIRSHIP;
        } else if (readingElsewhere() && !CrossDimensionWarp.isSupported()) {
            // The reading outlived the server's willingness to cross. The drive would refuse the course
            // at launch; said here instead, while the pilot is looking at the thing being refused.
            verdict = WarpFailure.DIMENSION_UNSUPPORTED;
        } else {
            verdict = drive.setFixCourse(player, readingDimension(), result.fix(), arrivalHeight(), courseLabel());
        }
        Trace.log(Trace.PROBE, "{} course from the reading of {} by {} to drive {} -> {}{}", Trace.pos(worldPosition),
                readingDimension().location(), Trace.who(player),
                drive == null ? "none found on this hull" : Trace.pos(drive.getBlockPos()),
                verdict.getSerializedName(), result == null ? " (no reading)" : result.usable() ? "" : " (unusable reading)");
        return verdict;
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Bearing", bearing.index());
        tag.putInt("Range", range);
        if (dimension != null) {
            tag.putString("Dimension", dimension.location().toString());
        }
        if (arrivalHeight >= 0) {
            tag.putInt("ArrivalHeight", arrivalHeight);
        }
        tag.putInt("State", state.index());
        tag.putInt("ReachTicks", reachTicks);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        // The survey is large and the panel asks for it separately, so it never rides the block
        // entity's own sync packet - only the shape of the reading does.
        if (sounding != null && !clientPacket) {
            CompoundTag reading = new CompoundTag();
            reading.putInt("Bearing", sounding.bearing().index());
            reading.putInt("Range", sounding.range());
            reading.putLong("Centre", sounding.survey().centre().asLong());
            reading.putInt("GroundY", sounding.survey().groundY());
            reading.putByteArray("Colours", sounding.survey().colours());
            reading.putByteArray("Relief", sounding.survey().relief());
            reading.putInt("Radius", sounding.survey().radius());
            reading.putInt("Step", sounding.survey().step());
            if (soundingDimension != null) {
                reading.putString("Dimension", soundingDimension.location().toString());
            }
            tag.put("Sounding", reading);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        bearing = ProbeBearing.byIndex(tag.getInt("Bearing"));
        range = tag.getInt("Range");
        if (range <= 0) {
            range = minimumRange();
        }
        // Absent on every probe saved before the dial had dimensions on it, which is "where it stands".
        dimension = dimensionOf(tag, "Dimension");
        arrivalHeight = tag.contains("ArrivalHeight") ? tag.getInt("ArrivalHeight") : ArrivalHeight.UNSET;
        state = ProbeState.byIndex(tag.getInt("State"));
        reachTicks = tag.getInt("ReachTicks");
        target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        if (tag.contains("Sounding")) {
            CompoundTag reading = tag.getCompound("Sounding");
            sounding = new ProbeSounding(
                    ProbeBearing.byIndex(reading.getInt("Bearing")),
                    reading.getInt("Range"),
                    new DestinationSurvey(
                            reading.getInt("Radius"),
                            reading.getInt("Step"),
                            BlockPos.of(reading.getLong("Centre")),
                            reading.getInt("GroundY"),
                            reading.getByteArray("Colours"),
                            reading.getByteArray("Relief")));
            soundingDimension = dimensionOf(reading, "Dimension");
        } else if (!clientPacket) {
            sounding = null;
            soundingDimension = null;
        }
        // A sounding cannot survive a reload: the ticket that was holding its ground has gone, and
        // resuming the wait would be waiting on chunks nothing is keeping open.
        if (!clientPacket && state == ProbeState.REACHING) {
            discard();
        }
    }

    /** A dimension saved under {@code key}, or {@code null} for none, or for one that is not an id. */
    @Nullable
    private static ResourceKey<Level> dimensionOf(CompoundTag tag, String key) {
        ResourceLocation id = tag.contains(key) ? ResourceLocation.tryParse(tag.getString(key)) : null;
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_probe").forGoggles(tooltip);
        AWLang.translate("gui.rift_probe.essence", AWLang.count(tank.getFluidAmount()))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        // The dial's dimension is only worth a line when it is not the one the reader is standing in.
        boolean elsewhere = dimension != null && level != null && !dimension.equals(level.dimension());
        (elsewhere
                ? AWLang.translate("gui.rift_probe.aim_in",
                        AWLang.translate(bearing.translationKey()).string(), AWLang.distance(range),
                        ProbeDimensions.name(dimension.location()).getString())
                : AWLang.translate("gui.rift_probe.aim",
                        AWLang.translate(bearing.translationKey()).string(), AWLang.distance(range)))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        AWLang.translate(state.translationKey())
                .style(state == ProbeState.FAILED ? ChatFormatting.RED : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        return true;
    }

    /** A probe is a chart room, not a machine: nothing about it is worth drawing from far off. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.0D);
    }

    /** How much of the longest sounding the supply pays for, 0..1. */
    public float fillLevel() {
        return Math.min(1.0F, tank.getFluidAmount() / (float) fullSupply());
    }

    public FluidStack contents() {
        return tank.getFluid();
    }
}
