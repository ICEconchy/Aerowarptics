package uk.co.iceconchy.aerowarptics.berth;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.AttitudeHold;
import uk.co.iceconchy.aerowarptics.hold.HoldPurpose;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds every moored ship in its slip, draws in every ship a berth has caught, and looks for ships to catch.
 *
 * <p>On the server's tick and from the record, never from a berth's block. A block ticks only while its
 * chunk is loaded, and a ship kept loaded by its berth is very often kept loaded where nobody is - so a
 * berth that held its ship from its own tick would let go of it the moment its owner walked away, and a
 * hull whose balloons no longer carry it would be on the ground when they came back. The same reasoning as
 * a gate's passages, which run here so that a gate that unloads cannot leave anybody held.
 *
 * <p>What it does to a ship is only ever a velocity commanded and an attitude held
 * ({@link BerthMooring}): no joint, nothing saved on the ship, nothing a server that stops mid-tick comes
 * back with still set. A ship moored when the server stopped is moored again on its first tick, from the
 * record.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class BerthMoorings {

    private BerthMoorings() {
    }

    /** How often a berth looks at its slip for a ship to catch, or for one it let go to have left. */
    static final int LOOK_INTERVAL = 20;

    /** How long a hold on a moored ship lasts unrenewed, and how often it is asked for again. */
    static final int HOLD_LEASE_TICKS = 1_200;
    static final int HOLD_RENEW_INTERVAL = 100;

    /**
     * How long a berth whose ground is loaded waits for a ship it holds to be there, before taking it for
     * gone - taken apart, or carried off by something that never cast it off. Long enough to cover a ship
     * that loads a moment after the ground it is over.
     */
    static final int ABSENT_TICKS = 200;

    /** What a held ship is being held at, worked out once and kept so that it is held <em>there</em>. */
    private record Station(Quaterniond attitude, Vector3d mark) {
    }

    private static final Map<UUID, Station> STATIONS = new HashMap<>();
    private static final Map<UUID, Integer> ABSENT = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        BerthRegistry registry = BerthRegistry.get(server);
        if (registry.all().isEmpty()) {
            return;
        }
        long now = Berths.now(server);
        boolean enabled = AWConfig.BERTH_ENABLED.get();
        for (Berth berth : registry.all()) {
            switch (berth.state()) {
                case MOORED, DRAWING -> {
                    if (enabled && berth.enabled()) {
                        tend(server, berth, now);
                    } else {
                        Berths.castOff(server, berth, enabled ? "it was switched off" : "berths are switched off");
                    }
                }
                case RESERVED -> {
                    if (berth.until() <= now) {
                        Trace.log(Trace.BERTH, "berth {} '{}' at {}: the ship it was reserved for never came",
                                berth.id(), berth.displayName(), Trace.pos(berth.pos()));
                        Berths.write(server, berth.emptied());
                    }
                }
                case EMPTY -> {
                    // Staggered by the berth, so a harbour of them does not all look on the same tick.
                    if (Math.floorMod(now + berth.id().hashCode(), LOOK_INTERVAL) != 0) {
                        continue;
                    }
                    if (berth.castOff()) {
                        watchLeave(server, berth);
                    } else if (enabled && berth.enabled() && berth.tractor()) {
                        lookForAShip(server, berth);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STATIONS.clear();
        ABSENT.clear();
    }

    /** Forgets what a berth was holding a ship at: it has let go, or been changed. */
    static void forget(UUID berth) {
        STATIONS.remove(berth);
        ABSENT.remove(berth);
    }

    // ------------------------------------------------------------------ holding

    /** One tick of a berth with a ship in its keeping. */
    private static void tend(MinecraftServer server, Berth berth, long now) {
        ServerLevel level = server.getLevel(berth.dimension());
        ServerSubLevel ship = level == null || berth.ship() == null ? null : Holds.present(level, berth.ship());
        if (ship == null) {
            waitFor(server, level, berth);
            return;
        }
        ABSENT.remove(berth.id());
        Airship airship = Airship.of(ship);
        if (airship == null || !airship.isActive()) {
            return;
        }
        if (airship.warpData().isWarping()) {
            // Something is flying it - a drive that started without saying so. Never fight a flight for a hull.
            Berths.castOff(server, berth, "its ship is in a warp");
            return;
        }
        Station station = station(berth, airship);
        if (station == null) {
            Berths.castOff(server, berth, "its ship no longer fits the slip");
            return;
        }
        Vector3d centre = airship.vesselCentre(new Vector3d());
        if (berth.state() == BerthState.DRAWING) {
            draw(server, berth, airship, station, centre);
        } else {
            BerthMooring.Pull pull = BerthMooring.hold(centre, station.mark(), airship.orientation(),
                    station.attitude());
            airship.steerVelocity(pull.linear(), new Vector3d());
            // Held after every physics substep, as a flight's is: lift blocks and anything walking the deck
            // turn a hull between one tick's command and the next.
            AttitudeHold.hold(airship, station.attitude());
        }
        if (AWConfig.BERTH_KEEP_MOORED_LOADED.get() && Math.floorMod(now + berth.id().hashCode(), HOLD_RENEW_INTERVAL) == 0) {
            for (Airship member : airship.vessel()) {
                Holds.holdShip(member, HoldPurpose.BERTHED, berth.id(), HOLD_LEASE_TICKS, false);
            }
        }
    }

    /** A ship being drawn in: glided to its mark, turned to the facing, and moored once it is there. */
    private static void draw(MinecraftServer server, Berth berth, Airship airship, Station station, Vector3d centre) {
        // A crouching crew member always breaks a ship free while it is being drawn in - the plotter's dock's
        // rule. Never once it is moored: people sneak about a deck all day.
        if (airship.crew().stream().anyMatch(ServerPlayer::isShiftKeyDown)) {
            Berths.castOff(server, berth, "a crew member broke it free");
            return;
        }
        if (!BerthMooring.keeps(berth.box(), centre)) {
            Berths.castOff(server, berth, "it strayed from the slip while being drawn in");
            return;
        }
        BerthMooring.Pull pull = BerthMooring.draw(centre, station.mark(), airship.orientation(), station.attitude());
        airship.steerVelocity(pull.linear(), pull.angular());
        if (pull.moored()) {
            Trace.log(Trace.BERTH, "berth {} '{}' at {} has drawn in {} '{}' and moors it", berth.id(),
                    berth.displayName(), Trace.pos(berth.pos()), airship.uuid(), Berths.name(airship));
            Berths.write(server, berth.moored(airship.uuid(), Berths.name(airship)));
        }
    }

    /**
     * Where a berth holds its ship, and how it lies: worked out once and kept.
     *
     * <p>Kept, because the attitude is the ship's own turned to the facing, and worked out afresh each tick
     * from wherever the ship had got to it would follow the ship instead of holding it - a hull that rolled a
     * degree would be held a degree over. After a restart it is worked out again from the ship as it is found,
     * which is as it was left.
     */
    @Nullable
    private static Station station(Berth berth, Airship airship) {
        Station known = STATIONS.get(berth.id());
        if (known != null) {
            return known;
        }
        BoundingBox3d vessel = airship.vesselShipBounds();
        if (vessel == null) {
            return null;
        }
        double sizeX = vessel.maxX() - vessel.minX();
        double sizeY = vessel.maxY() - vessel.minY();
        double sizeZ = vessel.maxZ() - vessel.minZ();
        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();
        BerthBox box = berth.box();
        Quaterniond attitude = BerthMooring.lineUp(airship.orientation(), sizeX, sizeY, sizeZ, box, clearance);
        if (attitude == null) {
            return null;
        }
        BerthFit.Sitting sitting = BerthFit.sitting(sizeX, sizeY, sizeZ, attitude, box);
        Station station = new Station(attitude, box.mark(sitting.up(), clearance));
        STATIONS.put(berth.id(), station);
        return station;
    }

    /**
     * A berth's ship is not loaded. Away with its ground, it is simply away, and the berth is still its own
     * when it comes back. Away with its ground loaded for long enough, it is gone.
     */
    private static void waitFor(MinecraftServer server, @Nullable ServerLevel level, Berth berth) {
        if (level == null || !level.isLoaded(berth.pos())) {
            ABSENT.remove(berth.id());
            return;
        }
        int absent = ABSENT.merge(berth.id(), 1, Integer::sum);
        if (absent >= ABSENT_TICKS) {
            Trace.log(Trace.BERTH, "berth {} '{}' at {}: {} '{}' has not been there for {} ticks with its ground"
                            + " loaded - taken for gone", berth.id(), berth.displayName(), Trace.pos(berth.pos()),
                    berth.ship(), berth.shipName(), absent);
            Berths.letGoOf(server, berth);
            Berths.write(server, berth.emptied());
        }
    }

    // ------------------------------------------------------------------ looking

    /** A ship that was cast off is still the slip's until it has left it, or gone. */
    private static void watchLeave(MinecraftServer server, Berth berth) {
        ServerLevel level = server.getLevel(berth.dimension());
        if (level == null || !level.isLoaded(berth.pos())) {
            return;
        }
        ServerSubLevel ship = berth.ship() == null ? null : Holds.present(level, berth.ship());
        if (ship != null && Berths.overlaps(berth.box(), ship.boundingBox())) {
            ABSENT.remove(berth.id());
            return;
        }
        // Not there is not yet gone: a ship loads a moment after the ground it is over, and one taken for gone
        // in that moment is a ship its owner cast off, found lying in an empty slip and caught again (seen in
        // game, 2026-10-12, on the first tick after a restart).
        if (ship == null && ABSENT.merge(berth.id(), LOOK_INTERVAL, Integer::sum) < ABSENT_TICKS) {
            return;
        }
        ABSENT.remove(berth.id());
        Trace.log(Trace.BERTH, "berth {} '{}' at {}: {} '{}' has {} - the slip is free", berth.id(),
                berth.displayName(), Trace.pos(berth.pos()), berth.ship(), berth.shipName(),
                ship == null ? "gone" : "left the slip");
        Berths.write(server, berth.emptied());
    }

    /** Looks in an empty slip for a ship flown into it by hand, slowly enough to be parking, and catches it. */
    private static void lookForAShip(MinecraftServer server, Berth berth) {
        ServerLevel level = server.getLevel(berth.dimension());
        if (level == null || !level.isLoaded(berth.pos())) {
            return;
        }
        BerthBox box = berth.box();
        double[] b = box.bounds();
        BerthRegistry registry = BerthRegistry.get(server);
        Membership memberships = WarpNetworkRegistry.get(server);
        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();
        double captureSpeed = AWConfig.BERTH_CAPTURE_SPEED.get() / 20.0D;
        for (SubLevel found : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(b[0], b[1], b[2], b[3], b[4], b[5]))) {
            if (!(found instanceof ServerSubLevel candidate) || candidate.isRemoved()
                    || registry.concerning(Airship.of(candidate).uuid()) != null) {
                continue;
            }
            Airship airship = Airship.of(candidate);
            BoundingBox3d vessel = airship == null || !airship.isActive() ? null : airship.vesselShipBounds();
            if (vessel == null || airship.warpData().isWarping()) {
                continue;
            }
            List<ServerPlayer> crew = airship.crew();
            // Somebody aboard has to be one the berth is kept for; a ship with nobody aboard is nobody's, and is
            // taken only by a berth kept for anyone. And nobody aboard may be saying no.
            boolean permitted = crew.isEmpty() ? berth.permits(null, memberships)
                    : crew.stream().anyMatch(player -> berth.permits(player.getUUID(), memberships));
            if (!permitted || crew.stream().anyMatch(ServerPlayer::isShiftKeyDown)) {
                continue;
            }
            double sizeX = vessel.maxX() - vessel.minX();
            double sizeY = vessel.maxY() - vessel.minY();
            double sizeZ = vessel.maxZ() - vessel.minZ();
            Quaterniond attitude = BerthMooring.lineUp(airship.orientation(), sizeX, sizeY, sizeZ, box, clearance);
            Vector3d centre = airship.vesselCentre(new Vector3d());
            if (!BerthMooring.catches(box, centre, airship.velocity().length(), captureSpeed, attitude != null)) {
                continue;
            }
            Trace.log(Trace.BERTH, "berth {} '{}' at {} catches {} '{}': {} from its middle, {} b/t, {} aboard",
                    berth.id(), berth.displayName(), Trace.pos(berth.pos()), airship.uuid(), Berths.name(airship),
                    Trace.blocks(centre.distance(box.mark(sizeY, clearance))), Trace.num(airship.velocity().length()),
                    crew.size());
            forget(berth.id());
            Berths.write(server, berth.drawing(airship.uuid(), Berths.name(airship)));
            return;
        }
    }
}
