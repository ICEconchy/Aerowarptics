package uk.co.iceconchy.aerowarptics.berth;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.AttitudeHold;
import uk.co.iceconchy.aerowarptics.hold.HoldPurpose;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where Berths meet everything else: the one place a warp asks a station for a slip, the one place a
 * slip is taken, and the one place a ship is held in it.
 *
 * <p>A drive bound for an anchor that is a station asks {@link #offers} for the berths it could have,
 * plans a flight into the best whose slip is {@link #proof proved} clear, and only once that flight is
 * certain {@link #reserve}s it - the wards' shape, for the wards' reason: a berth is asked about for
 * arrivals that are then refused for something else, so asking changes nothing. A new kind of warp that
 * plans its own arrival at an anchor and does not ask here puts a ship down loose in the middle of a
 * station, and nothing fails.
 *
 * <p>A berth is asked and held whether or not its block is loaded. A loaded block is told of every change
 * ({@link #write}), so its lamp and its comparator follow; an unloaded one takes the record up when its
 * chunk loads. Nothing here loads a chunk.
 */
public final class Berths {

    private Berths() {
    }

    // ------------------------------------------------------------------ asking

    /**
     * A berth as offered to one ship.
     *
     * @param centre   where the vessel's centre comes to rest
     * @param attitude the ship-to-world rotation it rests at: as it is now, turned to the berth's facing
     * @param approach the level unit vector it travels along as it comes in
     * @param height   how tall it sits, which with the clearance is how tall a volume it is proved against
     * @param along    how long it sits, along the slip
     * @param across   how wide it sits, across it
     */
    public record Assignment(Berth berth, Vector3d centre, Quaterniond attitude, Vector3d approach, double height,
                             double along, double across) {
    }

    /**
     * What a station offered a ship.
     *
     * @param ranked  the berths it could have, best first; empty when refused
     * @param refusal why it was offered none
     * @param why     the same, with the numbers, for a trace or a dry run
     */
    public record Offer(List<Assignment> ranked, WarpFailure refusal, String why) {
    }

    /** Whether berths do anything on this server, and this anchor has one switched on. */
    public static boolean isStation(MinecraftServer server, WarpAnchor anchor) {
        return AWConfig.BERTH_ENABLED.get() && BerthRegistry.get(server).isStation(anchor.id());
    }

    /**
     * The berths a station would give a ship, best first, or why it gives it none. Changes nothing: see
     * {@link #reserve}.
     *
     * @param bow the way the ship's bow points now, in the world
     * @param who whoever opened the rift, or {@code null} for a rift nobody can be named for
     */
    public static Offer offers(ServerLevel destination, WarpAnchor anchor, Airship airship, Vector3dc bow,
                               @Nullable UUID who) {
        BoundingBox3d vessel = airship.vesselShipBounds();
        if (vessel == null) {
            return new Offer(List.of(), WarpFailure.AIRSHIP_LOST, "the hull has no measurable shape");
        }
        MinecraftServer server = destination.getServer();
        BerthAssign.Vessel asking = new BerthAssign.Vessel(airship.uuid(), vessel.maxX() - vessel.minX(),
                vessel.maxY() - vessel.minY(), vessel.maxZ() - vessel.minZ(), new Quaterniond(airship.orientation()),
                new Vector3d(bow), who);
        List<Berth> berths = new ArrayList<>();
        for (Berth berth : BerthRegistry.get(server).of(anchor.id())) {
            if (berth.dimension().equals(destination.dimension())) {
                berths.add(berth);
            }
        }
        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();
        BerthAssign.Station station = new BerthAssign.Station(berths, anchor.pos().getX() + 0.5D,
                anchor.pos().getZ() + 0.5D, clearance, now(server), WarpNetworkRegistry.get(server));
        // A hull somebody simply left in a slip is in nobody's record. Where the ground is loaded it can be
        // seen, and a slip with one in it is taken; where it is not, the proof of the slip finds it.
        Set<UUID> taken = new HashSet<>();
        Set<SubLevel> own = airship.assemblySubLevels();
        for (Berth berth : berths) {
            if (berth.enabled() && occupiedByAnother(destination, berth, own)) {
                taken.add(berth.id());
            }
        }
        List<BerthAssign.Choice> ranked = BerthAssign.ranked(station, asking, taken);
        if (ranked.isEmpty()) {
            BerthAssign.Refusal reason = BerthAssign.why(station, asking, taken);
            WarpFailure refusal = failure(reason == BerthAssign.Refusal.NONE ? BerthAssign.Refusal.NO_FREE_BERTH : reason);
            String why = String.format(java.util.Locale.ROOT, "station '%s' has %d berth(s), %d with a hull lying in"
                            + " them; the vessel is %.1f x %.1f x %.1f, clearance %.1f, pilot %s", anchor.displayName(),
                    berths.size(), taken.size(), asking.sizeX(), asking.sizeY(), asking.sizeZ(), clearance,
                    Trace.who(who));
            Trace.log(Trace.BERTH, "{} is offered no berth: {} - {}", airship.uuid(), refusal, why);
            return new Offer(List.of(), refusal, why);
        }
        List<Assignment> out = new ArrayList<>(ranked.size());
        for (BerthAssign.Choice choice : ranked) {
            out.add(assignment(choice, clearance));
        }
        Trace.log(Trace.BERTH, "{} is offered {} berth(s) at station '{}', first '{}' at {}", airship.uuid(),
                out.size(), anchor.displayName(), out.get(0).berth().displayName(), Trace.pos(out.get(0).berth().pos()));
        return new Offer(out, WarpFailure.NONE, "");
    }

    /**
     * One ship of a fleet, asking.
     *
     * @param bow the way its bow points now, in the world
     */
    public record Asking(Airship airship, Vector3dc bow) {
    }

    /**
     * What a station offered a fleet.
     *
     * @param berths  one for every ship asked about, in the order they were asked; empty when refused
     * @param refusal why the fleet was offered none - a fleet is berthed whole or not at all
     */
    public record FleetOffer(List<Assignment> berths, WarpFailure refusal, String why) {
    }

    /**
     * A berth for every ship of a fleet, or none for any of them. Changes nothing: see {@link #reserve}.
     *
     * <p>One choice, not a ranking: which berth one ship takes decides what is left for the next, so a caller
     * that finds a slip fouled strikes that berth off and asks again for the whole fleet.
     *
     * @param who    whoever ordered the fold, which is who every ship in it is berthed as
     * @param struck berths not to offer - found obstructed on an earlier asking
     */
    public static FleetOffer fleetOffers(ServerLevel destination, WarpAnchor anchor, List<Asking> fleet,
                                         @Nullable UUID who, Set<UUID> struck) {
        MinecraftServer server = destination.getServer();
        List<BerthAssign.Vessel> vessels = new ArrayList<>();
        Set<SubLevel> own = new HashSet<>();
        for (Asking asking : fleet) {
            BoundingBox3d vessel = asking.airship().vesselShipBounds();
            if (vessel == null) {
                return new FleetOffer(List.of(), WarpFailure.AIRSHIP_LOST, "a hull has no measurable shape");
            }
            vessels.add(new BerthAssign.Vessel(asking.airship().uuid(), vessel.maxX() - vessel.minX(),
                    vessel.maxY() - vessel.minY(), vessel.maxZ() - vessel.minZ(),
                    new Quaterniond(asking.airship().orientation()), new Vector3d(asking.bow()), who));
            own.addAll(asking.airship().assemblySubLevels());
        }
        List<Berth> berths = new ArrayList<>();
        for (Berth berth : BerthRegistry.get(server).of(anchor.id())) {
            // A berth struck off, or with somebody else's hull lying in it, is simply not there to be given.
            if (berth.dimension().equals(destination.dimension()) && !struck.contains(berth.id())
                    && !(berth.enabled() && occupiedByAnother(destination, berth, own))) {
                berths.add(berth);
            }
        }
        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();
        BerthAssign.Fleet made = BerthAssign.fleet(new BerthAssign.Station(berths, anchor.pos().getX() + 0.5D,
                anchor.pos().getZ() + 0.5D, clearance, now(server), WarpNetworkRegistry.get(server)), vessels);
        if (!made.berthed()) {
            WarpFailure refusal = failure(made.refusal());
            String why = String.format(java.util.Locale.ROOT, "station '%s' has %d berth(s) to give a fleet of %d,"
                            + " %d struck off as fouled; the ship it could not place is %.1f x %.1f x %.1f", anchor.displayName(),
                    berths.size(), fleet.size(), struck.size(), made.refused() == null ? 0.0D : made.refused().sizeX(),
                    made.refused() == null ? 0.0D : made.refused().sizeY(),
                    made.refused() == null ? 0.0D : made.refused().sizeZ());
            Trace.log(Trace.BERTH, "a fleet of {} is offered no berths: {} - {}", fleet.size(), refusal, why);
            return new FleetOffer(List.of(), refusal, why);
        }
        List<Assignment> out = new ArrayList<>(made.choices().size());
        for (BerthAssign.Choice choice : made.choices()) {
            out.add(assignment(choice, clearance));
        }
        return new FleetOffer(out, WarpFailure.NONE, "");
    }

    private static Assignment assignment(BerthAssign.Choice choice, double clearance) {
        BerthBox box = choice.berth().box();
        BerthFit.Sitting sitting = BerthFit.sitting(choice.vessel().sizeX(), choice.vessel().sizeY(),
                choice.vessel().sizeZ(), choice.attitude(), box);
        return new Assignment(choice.berth(), box.mark(sitting.up(), clearance), choice.attitude(),
                box.approach(choice.berth().sternFirst()), sitting.up(), sitting.along(), sitting.across());
    }

    private static WarpFailure failure(BerthAssign.Refusal refusal) {
        return switch (refusal) {
            case NONE -> WarpFailure.NONE;
            case NO_FREE_BERTH -> WarpFailure.NO_FREE_BERTH;
            case BERTH_TOO_SMALL -> WarpFailure.BERTH_TOO_SMALL;
            case BERTH_FORBIDDEN -> WarpFailure.BERTH_FORBIDDEN;
            case BERTH_OBSTRUCTED -> WarpFailure.BERTH_OBSTRUCTED;
        };
    }

    /** Whether a hull that is not the asker's lies in a berth's slip. Only ever true of loaded ground. */
    private static boolean occupiedByAnother(ServerLevel level, Berth berth, Set<SubLevel> own) {
        if (!level.isLoaded(berth.pos())) {
            return false;
        }
        BerthBox box = berth.box();
        double[] b = box.bounds();
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(b[0], b[1], b[2], b[3], b[4], b[5]))) {
            if (!own.contains(other) && overlaps(box, other.boundingBox())) {
                return true;
            }
        }
        return false;
    }

    static boolean overlaps(BerthBox box, BoundingBox3dc other) {
        return box.overlaps(other.minX(), other.minY(), other.minZ(), other.maxX(), other.maxY(), other.maxZ());
    }

    /**
     * Whether the way into a berth is clear for the ship it was offered to: the air it will lie in and the
     * lane it flies down to get there, and the clearance round both.
     *
     * <p>Read as the slip lies and not as the box round it, so a slip built to fit is not refused for its
     * own quay - see {@link SafeArrival.Within}. A ship that fits its berth sweeps nothing outside the slip
     * and its lane, because the clearance proved here is the clearance it was fitted with.
     *
     * @param runOut how far back from its resting place the hull reappears
     */
    public static SafeArrival.Clearance proof(Airship airship, ServerLevel destination, Assignment berth,
                                              double runOut) {
        BerthBox swept = swept(berth, runOut, AWConfig.ARRIVAL_CLEARANCE.get());
        double[] b = swept.bounds();
        return SafeArrival.clearanceWithin(airship, destination, new BoundingBox3d(b[0], b[1], b[2], b[3], b[4], b[5]),
                new SafeArrival.Within() {
                    @Override
                    public boolean cell(int x, int y, int z) {
                        return swept.overlaps(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
                    }

                    @Override
                    public boolean box(BoundingBox3dc other) {
                        return overlaps(swept, other);
                    }
                });
    }

    /**
     * Everything a hull passes through on its way into a berth, as one turned box: the hull where it rests,
     * drawn back along the way it came by its run out, and grown by the clearance.
     */
    public static BerthBox swept(Assignment berth, double runOut, double clearance) {
        double back = Math.max(0.0D, runOut);
        Vector3d approach = berth.approach();
        BerthBox slip = berth.berth().box();
        return new BerthBox(berth.centre().x - approach.x * back * 0.5D,
                berth.centre().y - berth.height() * 0.5D - clearance,
                berth.centre().z - approach.z * back * 0.5D,
                berth.along() + back + clearance * 2.0D, berth.across() + clearance * 2.0D,
                berth.height() + clearance * 2.0D, slip.yaw());
    }

    // ------------------------------------------------------------------ taking

    /**
     * Takes a berth for a flight that is now certain. Call it once nothing can refuse the flight, and not
     * before.
     *
     * @param flightTicks how long the flight is expected to take; the reservation outlasts that by
     *                    {@code berth.reserveMarginTicks} and then lapses, so a warp that is lost frees the slip
     * @return the berth's id, which is what the drive keeps
     */
    public static UUID reserve(MinecraftServer server, Assignment assignment, Airship airship, int flightTicks) {
        Berth berth = current(server, assignment.berth());
        long until = now(server) + flightTicks + AWConfig.BERTH_RESERVE_MARGIN.get();
        Trace.log(Trace.BERTH, "berth {} '{}' at {} reserved for {} '{}' for {} ticks", berth.id(), berth.displayName(),
                Trace.pos(berth.pos()), airship.uuid(), airship.name(), until - now(server));
        write(server, berth.reserved(airship.uuid(), name(airship), until));
        return berth.id();
    }

    /** Gives back a berth reserved for a flight that will not arrive in it. */
    public static void unreserve(MinecraftServer server, UUID berthId) {
        Berth berth = BerthRegistry.get(server).byId(berthId);
        if (berth != null && berth.state() == BerthState.RESERVED) {
            Trace.log(Trace.BERTH, "berth {} '{}' at {} is given back: the flight it was reserved for is off",
                    berth.id(), berth.displayName(), Trace.pos(berth.pos()));
            write(server, berth.emptied());
        }
    }

    /**
     * A ship has come to rest in the berth reserved for it: the berth takes it.
     *
     * <p>By the berth's id, not the ship's, which a crossing into another dimension changes on the way. A
     * berth broken while the ship was in its corridor takes nothing; the ship has simply arrived.
     */
    public static void moored(MinecraftServer server, UUID berthId, Airship airship) {
        Berth berth = BerthRegistry.get(server).byId(berthId);
        if (berth == null || !berth.enabled() || !AWConfig.BERTH_ENABLED.get()) {
            Trace.log(Trace.BERTH, "{} '{}' arrived for berth {}, which is {}: not moored", airship.uuid(),
                    airship.name(), berthId, berth == null ? "gone" : "switched off");
            if (berth != null && berth.state() == BerthState.RESERVED) {
                write(server, berth.emptied());
            }
            return;
        }
        Trace.log(Trace.BERTH, "berth {} '{}' at {} moors {} '{}'", berth.id(), berth.displayName(),
                Trace.pos(berth.pos()), airship.uuid(), airship.name());
        write(server, berth.moored(airship.uuid(), name(airship)));
    }

    /** A ship's name, or nothing for one nobody has named - which is most of them. */
    static String name(Airship airship) {
        String name = airship.name();
        return name == null ? "" : name;
    }

    // ------------------------------------------------------------------ letting go

    /** Lets go of the ship a berth holds or is drawing in. The ship stays where it lies. */
    public static void castOff(MinecraftServer server, Berth berth, String why) {
        Berth current = BerthRegistry.get(server).byId(berth.id());
        if (current == null || !current.state().holds()) {
            return;
        }
        release(server, current, why);
        write(server, current.letGo());
    }

    /** A ship is leaving under its own drive: whichever berth holds it lets it go. */
    public static void leaving(MinecraftServer server, Airship airship, String why) {
        for (Airship member : airship.vessel()) {
            Berth berth = BerthRegistry.get(server).concerning(member.uuid());
            if (berth != null && berth.state().holds()) {
                castOff(server, berth, why);
            }
        }
    }

    /** The berth that holds a ship or is drawing it in, if one does. */
    @Nullable
    public static Berth mooredAt(MinecraftServer server, UUID ship) {
        Berth berth = BerthRegistry.get(server).concerning(ship);
        return berth != null && berth.state().holds() ? berth : null;
    }

    private static void release(MinecraftServer server, Berth berth, String why) {
        Trace.log(Trace.BERTH, "berth {} '{}' at {} casts off {} '{}': {}", berth.id(), berth.displayName(),
                Trace.pos(berth.pos()), berth.ship(), berth.shipName(), why);
        letGoOf(server, berth);
    }

    /**
     * Stops holding a berth's ship: its attitude is its own again, and the berth no longer keeps it loaded.
     * The ship's own drive, if it has one, keeps it resident as it would anywhere.
     */
    static void letGoOf(MinecraftServer server, Berth berth) {
        BerthMoorings.forget(berth.id());
        if (berth.ship() != null) {
            AttitudeHold.release(berth.ship());
        }
        Holds.releaseShip(server, berth.id(), HoldPurpose.BERTHED, null);
    }

    // ------------------------------------------------------------------ the record

    /** A berth as the registry has it this moment, or as it was handed over if the registry has let it go. */
    private static Berth current(MinecraftServer server, Berth berth) {
        Berth current = BerthRegistry.get(server).byId(berth.id());
        return current == null ? berth : current;
    }

    /** Writes a new version of a berth's record, through its block where that is loaded. */
    public static void write(MinecraftServer server, Berth updated) {
        BerthBlockEntity block = loaded(server, updated);
        if (block != null) {
            block.take(updated);
        } else {
            BerthRegistry.get(server).register(updated);
        }
    }

    /**
     * A berth's owner has changed its settings. A slip that has moved, turned or changed size holds its ship
     * somewhere else, so what it was holding it at is forgotten and worked out again - and a ship that no
     * longer fits is cast off on the next tick, which is the tick that finds out.
     */
    public static void changed(MinecraftServer server, Berth before, Berth after) {
        if (!before.box().equals(after.box())) {
            BerthMoorings.forget(after.id());
        }
        write(server, after);
    }

    /** A berth's block, if it is in the world and loaded. Never loads it. */
    @Nullable
    public static BerthBlockEntity loaded(MinecraftServer server, Berth berth) {
        ServerLevel level = server.getLevel(berth.dimension());
        if (level == null || !level.isLoaded(berth.pos())) {
            return null;
        }
        return level.getBlockEntity(berth.pos()) instanceof BerthBlockEntity block
                && berth.id().equals(block.berthId()) ? block : null;
    }

    /** A berth's block has been broken: whatever it holds is let go, and its record goes. */
    public static void removed(MinecraftServer server, Berth berth) {
        Berth current = BerthRegistry.get(server).byId(berth.id());
        if (current != null && current.state().holds()) {
            release(server, current, "its block was broken");
        }
        BerthRegistry.get(server).remove(berth.id());
    }

    // ------------------------------------------------------------------ the station

    /** The clock reservations and leases are counted on: the overworld's, as the wards' upkeep is. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Every switched-on berth's slip in a level, for an arrival search to keep out of. */
    public static List<BerthBox> slips(ServerLevel level) {
        if (!AWConfig.BERTH_ENABLED.get()) {
            return List.of();
        }
        List<BerthBox> out = new ArrayList<>();
        for (Berth berth : BerthRegistry.get(level).in(level.dimension())) {
            if (berth.enabled()) {
                out.add(berth.box());
            }
        }
        return out;
    }

    /** Whether a hull's world box shares any air with any of these slips. */
    public static boolean inASlip(List<BerthBox> slips, BoundingBox3dc hull) {
        for (BerthBox slip : slips) {
            if (overlaps(slip, hull)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The nearest Warp Anchor a berth set down here could belong to: in this dimension, in reach, its
     * owner's, and with room for another berth.
     *
     * @param owner whoever is setting the berth down; nobody's berth is linked to nothing
     */
    @Nullable
    public static WarpAnchor nearestAnchor(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        if (owner == null) {
            return null;
        }
        double reach = AWConfig.BERTH_LINK_REACH.get();
        BerthRegistry berths = BerthRegistry.get(level);
        WarpAnchor best = null;
        double bestDistance = reach * reach;
        for (WarpAnchor anchor : WarpAnchorRegistry.get(level).all()) {
            if (!anchor.isInSameDimension(level) || !anchor.isOwnedBy(owner)
                    || berths.of(anchor.id()).size() >= AWConfig.BERTH_MAX_PER_ANCHOR.get()) {
                continue;
            }
            double distance = anchor.pos().distSqr(pos);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = anchor;
            }
        }
        return best;
    }
}
