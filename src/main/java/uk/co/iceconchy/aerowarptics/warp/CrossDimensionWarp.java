package uk.co.iceconchy.aerowarptics.warp;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.compat.dimensionalsable.DimensionalSableBridge;
import uk.co.iceconchy.aerowarptics.compat.simulated.CarriedRopes;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warping into another dimension, through Dimensional Sable.
 *
 * <p>Sable keeps each airship in one {@code ServerLevel}'s plot grid and has no way to hand one to another
 * level. Dimensional Sable, an optional dependency, does: it saves the vessel and rebuilds it in the
 * destination - see {@link DimensionalSableBridge}. A crossing therefore needs both that mod installed and
 * {@code permissions.allowCrossDimensionWarp} on, which is off by default.
 *
 * <p>This class is what stands between the warp and that one call, because the call does exactly one job
 * and a warp needs a few more around it:
 * <ul>
 *   <li>A <b>preflight</b> for the things that would make it throw halfway: no free slot on the far grid,
 *       or a hull that reaches sections the far dimension does not have. Asked at the mooring, so a ship
 *       with nowhere to go never leaves.</li>
 *   <li>A <b>clean-up</b> when it throws anyway. It builds the copies before it removes the originals, so a
 *       failure leaves copies behind in the destination and the originals where they were; the copies are
 *       taken back out, and the warp's ordinary recovery puts the hull back at its mooring.</li>
 *   <li><b>Seats and bearings.</b> It stands everyone up and takes every contraption apart, and leaves them
 *       that way. Crew are sat back down here; bearings that were running are started again once the new
 *       hull has had a tick to settle.</li>
 *   <li><b>Rotation.</b> It rebuilds every machine from what it was saved as, and a Create machine is saved
 *       with the position of the block that turns it - a position in the plot it has just left. Create
 *       checks that on a machine's first tick and, finding nothing there, stops it. Machines that lost their
 *       source that way are joined to their neighbours again - see {@link #relinkLater}.</li>
 * </ul>
 * What it loses that is this mod's own - the ship's UUID and its user data - the drive carries across itself;
 * see {@code RiftDriveBlockEntity.finishCrossing}.
 *
 * <h2>The order that matters</h2>
 * Nothing moves when the rift opens. The crossing happens where a same-dimension warp teleports, deep
 * inside the fold, after the arrival has been proven.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class CrossDimensionWarp {

    /** A bearing to start again, once its chunk has had a tick. */
    private record Reassembly(ResourceKey<Level> dimension, BlockPos pos, int[] ticksLeft) {
    }

    private static final List<Reassembly> REASSEMBLE = new ArrayList<>();

    /** A rebuilt vessel whose machines are to be given their rotation back, found again by a machine aboard it. */
    private record Relink(ResourceKey<Level> dimension, BlockPos aboard, int[] ticksLeft) {
    }

    private static final List<Relink> RELINK = new ArrayList<>();

    /**
     * Ticks between a rebuilt hull's first and the relinking of its machines. Every machine has to have had
     * its first tick by then, since that tick is the one that stops it; a few to spare cost nothing, and a
     * machine relinked before it was stopped is simply stopped afterwards.
     */
    private static final int RELINK_AFTER_TICKS = 5;

    private static boolean warnedMissing;

    private CrossDimensionWarp() {
    }

    /** Whether this server can send a warp into another dimension at all. */
    public static boolean isSupported() {
        if (!AWConfig.ALLOW_CROSS_DIMENSION_WARP.get()) {
            return false;
        }
        if (DimensionalSableBridge.available()) {
            return true;
        }
        if (!warnedMissing) {
            warnedMissing = true;
            AeroWarptics.LOGGER.warn("permissions.allowCrossDimensionWarp is on, but Dimensional Sable ({}) is not "
                    + "installed - warps will stay in their own dimension.", DimensionalSableBridge.MOD_ID);
        }
        return false;
    }

    /**
     * Whether anything could cross from {@code source} into {@code destination}: the part of
     * {@link #preflight} that is about the two dimensions and asks nothing of a ship.
     *
     * <p>What a Rift Probe asks before it offers a dimension to scan, when there may be no ship at all -
     * and so a dimension Sable keeps on a grid of another size is never offered, rather than scanned,
     * paid for and then refused by every drive that is sent there.
     */
    public static boolean joined(ServerLevel source, ServerLevel destination) {
        if (!isSupported() || source == destination
                || uk.co.iceconchy.aerowarptics.rail.RailCorridor.isCorridor(destination)) {
            return false; // the Rift Corridor is for trains; nothing sails into it
        }
        ServerSubLevelContainer from = SubLevelContainer.getContainer(source);
        ServerSubLevelContainer to = SubLevelContainer.getContainer(destination);
        return from != null && to != null && from.getLogPlotSize() == to.getLogPlotSize();
    }

    /**
     * Whether the vessel could cross into {@code destination} now, without moving anything.
     *
     * <p>Dimensional Sable puts every section back at the index it was saved at, and the Overworld has
     * twenty-four where the Nether and the End have sixteen - so a hull reaching high enough in the
     * Overworld's plot has sections the Nether cannot hold, and loading it would throw part-way. That is
     * asked here instead, as {@link WarpFailure#DIMENSION_TOO_TALL}.
     */
    public static WarpFailure preflight(Airship airship, ServerLevel destination) {
        if (!isSupported()) {
            Trace.log(Trace.CROSSING, "crossing into {} unsupported: permissions.allowCrossDimensionWarp is off or"
                    + " Dimensional Sable is not installed", destination.dimension().location());
            return WarpFailure.DIMENSION_UNSUPPORTED;
        }
        ServerLevel sourceLevel = airship.level();
        ServerSubLevelContainer source = SubLevelContainer.getContainer(sourceLevel);
        ServerSubLevelContainer target = SubLevelContainer.getContainer(destination);
        if (source == null || target == null || sourceLevel == destination
                // A saved plot refuses to load into a plot of another size.
                || source.getLogPlotSize() != target.getLogPlotSize()) {
            Trace.log(Trace.CROSSING, "crossing {} -> {} unsupported: {}", sourceLevel.dimension().location(),
                    destination.dimension().location(), source == null || target == null ? "no Sable plot grid there"
                            : sourceLevel == destination ? "same level"
                            : "plot sizes differ (" + source.getLogPlotSize() + " vs " + target.getLogPlotSize() + ")");
            return WarpFailure.DIMENSION_UNSUPPORTED;
        }

        List<ServerSubLevel> members = members(airship);
        Set<UUID> vessel = new HashSet<>();
        for (ServerSubLevel member : members) {
            vessel.add(member.getUniqueId());
        }
        if (CarriedRopes.anyTouching(sourceLevel, vessel)) {
            Trace.log(Trace.CROSSING, "crossing into {} refused: a rope is fastened to the vessel, and Dimensional"
                    + " Sable does not move ropes", destination.dimension().location());
            return WarpFailure.DIMENSION_ROPED;
        }
        int side = 1 << target.getLogSideLength();
        int free = side * side - target.getOccupancy().cardinality();
        if (free < members.size()) {
            Trace.log(Trace.CROSSING, "crossing into {} has no room: {} part(s) to move, {} free plot slot(s) of {}",
                    destination.dimension().location(), members.size(), free, side * side);
            return WarpFailure.DIMENSION_NO_ROOM;
        }
        for (ServerSubLevel member : members) {
            BoundingBox3ic hull = member.getPlot().getBoundingBox();
            if (hull == null) {
                continue;
            }
            if (!fits(hull.minY(), hull.maxY(), sourceLevel.getMinBuildHeight(), destination.getSectionsCount())) {
                Trace.log(Trace.CROSSING, "crossing into {}: part {} spans y {}..{}, which does not fit {} sections"
                                + " there", destination.dimension().location(), member.getUniqueId(), hull.minY(),
                        hull.maxY(), destination.getSectionsCount());
                return WarpFailure.DIMENSION_TOO_TALL;
            }
        }
        return WarpFailure.NONE;
    }

    /**
     * Whether a hull spanning {@code minY..maxY} in a level whose floor is {@code sourceFloor} keeps every
     * section inside a level of {@code destinationSections}, when each section keeps its index.
     */
    public static boolean fits(int minY, int maxY, int sourceFloor, int destinationSections) {
        int lowest = Math.floorDiv(minY - sourceFloor, 16);
        int highest = Math.floorDiv(maxY - sourceFloor, 16);
        return lowest >= 0 && highest < destinationSections;
    }

    /** Where the hull's running bearings are, to start them again on the far side. */
    public static List<BlockPos> runningBearings(Airship airship) {
        List<BlockPos> running = new ArrayList<>();
        for (MechanicalBearingBlockEntity bearing : airship.machines(MechanicalBearingBlockEntity.class)) {
            if (bearing.isRunning()) {
                running.add(bearing.getBlockPos().immutable());
            }
        }
        return running;
    }

    /**
     * Sends the vessel into {@code destination}, with the hull's pose position at {@code origin} and its
     * orientation as it is.
     *
     * @param bearings what {@link #runningBearings} said before the crossing, restarted in the source if
     *                 the crossing fails; the drive restarts them on the far side if it succeeds
     */
    public static WarpFailure cross(Airship airship, ServerLevel destination, Vector3dc origin,
                                    List<BlockPos> bearings) {
        WarpFailure ready = preflight(airship, destination);
        if (ready.isFailure()) {
            refused(airship, destination, ready);
            return ready;
        }
        ServerLevel sourceLevel = airship.level();
        ServerSubLevelContainer target = SubLevelContainer.getContainer(destination);
        Set<UUID> alreadyThere = new HashSet<>();
        for (ServerSubLevel existing : target.getAllSubLevels()) {
            alreadyThere.add(existing.getUniqueId());
        }
        Map<UUID, UUID> seated = seated(airship);
        UUID ship = airship.uuid();

        long started = System.nanoTime();
        int parts;
        try {
            parts = DimensionalSableBridge.warp(airship.subLevel(), destination, new Vector3d(origin));
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failed) {
            AeroWarptics.LOGGER.error("Dimensional Sable could not move {} into {}", airship,
                    destination.dimension().location(), failed);
            // It builds every copy before it removes any original, so the originals are still standing
            // and whatever it had built in the destination is the only thing to undo.
            for (ServerSubLevel copy : List.copyOf(target.getAllSubLevels())) {
                if (!alreadyThere.contains(copy.getUniqueId())) {
                    target.removeSubLevel(copy, SubLevelRemovalReason.REMOVED);
                }
            }
            reassembleLater(sourceLevel.dimension(), bearings);
            refused(airship, destination, WarpFailure.RELOCATION_FAILED);
            return WarpFailure.RELOCATION_FAILED;
        }

        seatAgain(seated, destination);
        WarpTrace.crossDimension(ship, sourceLevel.dimension().location().toString(),
                destination.dimension().location().toString(), parts, bearings.size(),
                (System.nanoTime() - started) / 1_000L);
        return WarpFailure.NONE;
    }

    /** Starts the bearings at {@code positions} again a tick or two from now. */
    public static void reassembleLater(ResourceKey<Level> dimension, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            REASSEMBLE.add(new Reassembly(dimension, pos, new int[]{2}));
        }
    }

    /**
     * Gives the machines aboard a rebuilt vessel their rotation back, a few ticks from now.
     *
     * <p>Every Create machine remembers where its rotation comes from, and Dimensional Sable rebuilds each one
     * with the position it was saved with: the old plot's. On its first tick Create looks there, finds no
     * machine, and takes the rotation away. What happens next is down to the order the machines tick in. One
     * that is stopped before the generator beside it has had its own first tick is started again by that
     * tick; one stopped after it is left dead, because nothing in Create asks twice. So after a crossing some
     * machines turned and some did not, a different set each time - a Rift Drive that read no rotation and
     * refused every launch on the first crossing flown in a game, and the Cooling Vent beside it on the way
     * back (2026-10-10).
     *
     * <p>Joining a stopped machine to its neighbours is what placing it does, and it carries on down the line
     * from there, so one pass over the stopped ones is enough whatever order they come in.
     *
     * @param aboard any block of the vessel, as it stands after the crossing - the drive's own position
     */
    public static void relinkLater(ResourceKey<Level> dimension, BlockPos aboard) {
        RELINK.add(new Relink(dimension, aboard.immutable(), new int[]{RELINK_AFTER_TICKS}));
    }

    /**
     * Whether a machine was left with no rotation by a crossing: turning at nothing and fed by nothing. One
     * that is turning is either a generator or still has its source, and is left exactly as it is.
     */
    public static boolean lostItsRotation(float theoreticalSpeed, boolean hasSource) {
        return theoreticalSpeed == 0.0F && !hasSource;
    }

    private static void relink(ServerLevel level, BlockPos aboard) {
        Airship hull = Airship.containing(level, aboard);
        if (hull == null || !hull.isActive()) {
            Trace.log(Trace.CROSSING, "no ship at {} in {} to give its rotation back to", Trace.pos(aboard),
                    level.dimension().location());
            return;
        }
        int found = 0;
        int stopped = 0;
        int started = 0;
        for (Airship part : hull.vessel()) {
            List<KineticBlockEntity> machines = part.machines(KineticBlockEntity.class);
            List<KineticBlockEntity> dead = new ArrayList<>();
            found += machines.size();
            for (KineticBlockEntity machine : machines) {
                if (!machine.isRemoved() && lostItsRotation(machine.getTheoreticalSpeed(), machine.hasSource())) {
                    dead.add(machine);
                }
            }
            stopped += dead.size();
            for (KineticBlockEntity machine : dead) {
                // Asked again, not taken from the list: the one before it may have started this one already.
                if (lostItsRotation(machine.getTheoreticalSpeed(), machine.hasSource())) {
                    machine.attachKinetics();
                }
            }
            for (KineticBlockEntity machine : dead) {
                if (machine.hasSource()) {
                    started++;
                }
            }
        }
        Trace.log(Trace.CROSSING, "{} in {}: {} kinetic machine(s) aboard, {} left with no rotation by the crossing,"
                + " {} of them turning again", hull, level.dimension().location(), found, stopped, started);
    }

    private static void refused(Airship airship, ServerLevel destination, WarpFailure failure) {
        // Always said, not only when tracing: a refusal nobody can see the reason for is a bug report
        // with nothing in it.
        AeroWarptics.LOGGER.warn("[warp] crossing of {} into {} refused: {}", airship,
                destination.dimension().location(), failure.getSerializedName());
    }

    /** The hull first, then everything Sable has joined to it - which is what Dimensional Sable moves. */
    private static List<ServerSubLevel> members(Airship airship) {
        List<ServerSubLevel> members = new ArrayList<>();
        members.add(airship.subLevel());
        members.addAll(airship.parts());
        return members;
    }

    /** Who is sitting in what, across the whole vessel: rider to vehicle. */
    private static Map<UUID, UUID> seated(Airship airship) {
        Map<UUID, UUID> seated = new HashMap<>();
        for (ServerSubLevel member : members(airship)) {
            for (ServerPlayer player : Airship.of(member).crew()) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    seated.put(player.getUUID(), vehicle.getUUID());
                }
            }
        }
        return seated;
    }

    /**
     * Sits everyone back down. Dimensional Sable dismounts every rider before it moves them, and moves the
     * seat separately, so a player who was sitting arrives standing next to their seat - or, on a moving
     * hull, next to where it was.
     */
    private static void seatAgain(Map<UUID, UUID> seated, ServerLevel destination) {
        MinecraftServer server = destination.getServer();
        for (Map.Entry<UUID, UUID> entry : seated.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Entity vehicle = destination.getEntity(entry.getValue());
            if (player != null && vehicle != null && player.level() == destination && !player.isPassenger()) {
                player.startRiding(vehicle, true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Iterator<Relink> stopped = RELINK.iterator();
        while (stopped.hasNext()) {
            Relink next = stopped.next();
            if (--next.ticksLeft()[0] > 0) {
                continue;
            }
            stopped.remove();
            ServerLevel level = server.getLevel(next.dimension());
            if (level != null) {
                relink(level, next.aboard());
            }
        }
        if (REASSEMBLE.isEmpty()) {
            return;
        }
        Iterator<Reassembly> pending = REASSEMBLE.iterator();
        while (pending.hasNext()) {
            Reassembly next = pending.next();
            if (--next.ticksLeft()[0] > 0) {
                continue;
            }
            pending.remove();
            ServerLevel level = server.getLevel(next.dimension());
            if (level != null && level.getBlockEntity(next.pos()) instanceof MechanicalBearingBlockEntity bearing
                    && !bearing.isRunning()) {
                bearing.assemble();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        REASSEMBLE.clear();
        RELINK.clear();
    }
}
