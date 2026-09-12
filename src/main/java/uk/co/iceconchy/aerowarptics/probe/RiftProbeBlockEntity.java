package uk.co.iceconchy.aerowarptics.probe;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
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
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
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

    /** Four buckets. Enough for a handful of soundings without standing next to a pipe. */
    public static final int CAPACITY = 8 * AWFluids.BUCKET;

    /** How far a sounding reads, in blocks. Matches the Astrolabe so the two charts are comparable. */
    public static final int SURVEY_RADIUS = DestinationSurvey.RADIUS;

    /** Ticks between checks on whether the far ground has arrived. Chunks do not load every tick. */
    private static final int POLL_INTERVAL = 5;

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

    private ProbeBearing bearing = ProbeBearing.NORTH;
    private int range = 2_000;
    /**
     * How far above the ground it finds the probe asks the drive to bring the ship in, or
     * {@link ArrivalHeight#UNSET} until somebody moves the slider - which follows the server default.
     */
    private int arrivalHeight = ArrivalHeight.UNSET;
    private ProbeState state = ProbeState.IDLE;

    /** Where the current sounding is aimed, kept while it reaches so the poll knows what to watch. */
    @Nullable
    private BlockPos target;

    private int reachTicks;

    @Nullable
    private ProbeSounding sounding;

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
        target = null;
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

        Vec3 from = origin();
        BlockPos aim = BlockPos.containing(
                from.x + bearing.dx() * range,
                from.y,
                from.z + bearing.dz() * range);
        // Clamped into the world's own limits, so a sounding thrown at the edge of a bounded world
        // reads the edge rather than nothing at all.
        int limit = serverLevel.getWorldBorder().getSize() > 0
                ? (int) (serverLevel.getWorldBorder().getSize() / 2.0D) : Integer.MAX_VALUE;
        aim = new BlockPos(
                Mth.clamp(aim.getX(), -limit, limit),
                aim.getY(),
                Mth.clamp(aim.getZ(), -limit, limit));

        target = aim;
        reachTicks = 0;
        state = ProbeState.REACHING;
        sounding = null;
        ProbeTicket.hold(serverLevel, aim, SURVEY_RADIUS);
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

        reachTicks++;
        int floor = AWConfig.PROBE_REACH_TICKS.get();
        int timeout = AWConfig.PROBE_TIMEOUT_TICKS.get();
        boolean timedOut = reachTicks >= timeout;
        if (reachTicks < floor) {
            return;
        }
        if (!timedOut && (reachTicks % POLL_INTERVAL != 0 || !regionReady(serverLevel, target))) {
            return;
        }
        read(serverLevel, target);
    }

    /**
     * Whether enough of the far region has arrived to be worth reading.
     *
     * <p>The centre must be there - without it there is no ground height to aim at and the whole
     * sounding is wasted - and most of the rest, so the picture is a place rather than a few islands.
     */
    private boolean regionReady(ServerLevel serverLevel, BlockPos centre) {
        ChunkPos middle = new ChunkPos(centre);
        if (!serverLevel.hasChunk(middle.x, middle.z)) {
            return false;
        }
        int radius = SURVEY_RADIUS >> 4;
        int total = 0;
        int loaded = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                total++;
                if (serverLevel.hasChunk(middle.x + x, middle.z + z)) {
                    loaded++;
                }
            }
        }
        return loaded >= total * ProbeSounding.THIN_COVERAGE;
    }

    private void read(ServerLevel serverLevel, BlockPos centre) {
        DestinationSurvey survey = DestinationSurvey.of(serverLevel, centre);
        ProbeSounding result = new ProbeSounding(bearing, range, survey);
        sounding = result;
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
        if (result == null || !result.usable()) {
            return WarpFailure.NO_SAFE_ARRIVAL;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > reachSqr()) {
            return WarpFailure.UNAUTHORISED;
        }
        RiftDriveBlockEntity drive = drive();
        if (drive == null) {
            return WarpFailure.NO_AIRSHIP;
        }
        return drive.setFixCourse(player, result.fix(), arrivalHeight(), result.label());
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Bearing", bearing.index());
        tag.putInt("Range", range);
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
        } else if (!clientPacket) {
            sounding = null;
        }
        // A sounding cannot survive a reload: the ticket that was holding its ground has gone, and
        // resuming the wait would be waiting on chunks nothing is keeping open.
        if (!clientPacket && state == ProbeState.REACHING) {
            discard();
        }
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_probe").forGoggles(tooltip);
        AWLang.translate("gui.rift_probe.essence", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        AWLang.translate("gui.rift_probe.aim",
                        AWLang.translate(bearing.translationKey()).string(), AWLang.distance(range))
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

    /** Rift essence is the only thing worth showing a fill level for. */
    public float fillLevel() {
        return tank.getFluidAmount() / (float) CAPACITY;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }
}
