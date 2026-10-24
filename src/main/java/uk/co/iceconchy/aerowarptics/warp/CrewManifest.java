package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The promise that a crew arrives with its ship.
 *
 * <h2>Why this is a system and not a fix</h2>
 * Keeping the crew aboard used to be everybody's job and therefore nobody's. Sable carried entities
 * on a moving sub-level, {@code relocate} wrote positions across the fold, and
 * {@link WarpPassengers} picked up whatever fell through - and each of the three worked, right up
 * until the drive stopped ticking. Then all three stopped at once, because all three ran inside the
 * drive's own tick, and a player was left four thousand blocks from their ship with nothing in the
 * game still looking for them.
 *
 * <p>So the guarantee is made once, in one place, and it does not run inside the drive. A warp opens
 * a manifest naming everyone aboard and the seat they were standing in, in ship space. From then
 * until it is closed, this reconciles that manifest against the world on the server tick: wherever
 * the hull has got to, that is where its crew is put. It does not care how the hull got there, which
 * machine moved it, or whether that machine is still alive.
 *
 * <h2>What it deliberately does not do</h2>
 * It does not hold anyone in place. A booking only acts when somebody is further from their seat than
 * a ship is wide, which cannot happen by walking about a deck - only by being left behind. Standing
 * still, jumping, and wandering the hold all read as no action at all, so the ordinary experience of
 * being aboard is untouched.
 *
 * <p>And it expires. A booking is kept alive by the flight refreshing it, so a warp that dies takes
 * its manifest with it a few seconds later rather than gluing a player to a wreck forever.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class CrewManifest {

    /**
     * How long a booking outlives the last thing that refreshed it.
     *
     * <p>Long enough to cover a drive that dies at the crossing and a hull that is still coasting to
     * a stop, short enough that a warp which fails completely stops following anybody around within a
     * few seconds. Configurable because a very large hull may take more than the old fixed value to
     * settle, and a manifest that expires while the ship is still coasting leaves its crew behind.
     */
    /** Default grace period when the config is not loaded, e.g. during unit tests. */
    static final int DEFAULT_GRACE_TICKS = 600;

    /**
     * How long a booking outlives the last refresh.
     *
     * <p>Falls back to the default when the config is not loaded, which is the case during unit
     * tests. The actual value comes from {@link AWConfig#MANIFEST_GRACE_TICKS}.
     */
    public static int graceTicks() {
        try {
            return AWConfig.MANIFEST_GRACE_TICKS.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet - happens in unit tests.
            return DEFAULT_GRACE_TICKS;
        }
    }

    /**
     * How far from their seat somebody has to be before they are put back.
     *
     * <p>Wider than any deck, because the point is to catch a passenger who is somewhere else
     * entirely - left at the departure point, or falling past the keel - and never to interfere with
     * one who is simply walking around.
     */
    private static final double ADRIFT = 6.0D;

    private static final class Booking {
        private ResourceKey<Level> dimension;
        /** Set once the ship has crossed into another dimension, so its crew are fetched from the old one. */
        private boolean crossed;
        private final Map<UUID, Vec3> seats = new HashMap<>();
        private int ticksLeft = graceTicks();

        private Booking(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    /** Open bookings, keyed by the airship they belong to. */
    private static final Map<UUID, Booking> BOOKINGS = new HashMap<>();

    private CrewManifest() {
    }

    /** Opens a manifest for a warp that is about to happen. */
    public static void board(Airship airship) {
        Booking booking = BOOKINGS.computeIfAbsent(airship.uuid(),
                id -> new Booking(airship.level().dimension()));
        booking.ticksLeft = graceTicks();
        record(airship, booking);
        if (Trace.enabled()) {
            List<String> names = new ArrayList<>();
            for (ServerPlayer player : airship.crew()) {
                names.add(player.getGameProfile().getName());
            }
            Trace.log(Trace.CREW, "ship {} boarded with {} aboard: {}", airship.uuid(), names.size(), names);
        }
    }

    /**
     * Brings a manifest up to date and keeps it alive.
     *
     * <p>Called every tick of a flight. Seats are re-read from where people actually are, so somebody
     * who walks to the bow during the approach arrives at the bow.
     */
    public static void refresh(Airship airship) {
        Booking booking = BOOKINGS.get(airship.uuid());
        if (booking == null) {
            return;
        }
        booking.ticksLeft = graceTicks();
        record(airship, booking);
    }

    /**
     * Records where everybody is, in ship space.
     *
     * <p>Only people who are aboard update their seat. Somebody who has already come adrift keeps the
     * last seat they were really in, which is the one worth putting them back into.
     */
    private static void record(Airship airship, Booking booking) {
        for (ServerPlayer player : airship.crew()) {
            booking.seats.put(player.getUUID(), airship.toShip(player.position()));
        }
    }

    /**
     * The ship has been rebuilt in another dimension, as {@code to}.
     *
     * <p>A rebuilt ship is a new sub-level with a new UUID, so the booking moves to it. Its seats are in
     * ship space, which is plot space, and the rebuild put the plot somewhere else: every seat moves by the
     * same {@code shift}, or the whole crew would be put back thousands of blocks from the deck. And from
     * now on a crew member found in a different level from the ship is exactly the one who was left
     * behind, not somebody who took a portal of their own.
     */
    public static void crossed(UUID from, UUID to, ResourceKey<Level> dimension, Vec3 shift) {
        Booking booking = BOOKINGS.remove(from);
        if (booking == null) {
            return;
        }
        booking.dimension = dimension;
        booking.crossed = true;
        booking.seats.replaceAll((player, seat) -> seat.add(shift));
        BOOKINGS.put(to, booking);
    }

    /** The warp is over and accounted for; stop watching. */
    public static void close(UUID ship) {
        BOOKINGS.remove(ship);
    }

    public static int open() {
        return BOOKINGS.size();
    }

    /** Whether somebody this far from their seat should be put back. Shared with the test. */
    public static boolean adrift(double blocks) {
        return blocks > ADRIFT;
    }


    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BOOKINGS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Booking>> entries = BOOKINGS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Booking> entry = entries.next();
            Booking booking = entry.getValue();
            if (--booking.ticksLeft <= 0) {
                entries.remove();
                continue;
            }
            reconcile(server, entry.getKey(), booking);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BOOKINGS.clear();
    }

    /** Puts anybody who is not with their ship back where they were standing on it. */
    private static void reconcile(MinecraftServer server, UUID shipId, Booking booking) {
        ServerLevel level = server.getLevel(booking.dimension);
        if (level == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(shipId);
        if (!(subLevel instanceof ServerSubLevel server1) || subLevel.isRemoved()) {
            return;
        }
        Airship airship = Airship.of(server1);
        if (!airship.isActive()) {
            return;
        }

        List<UUID> gone = new ArrayList<>();
        org.joml.Vector3d hull = airship.velocity();
        for (Map.Entry<UUID, Vec3> seat : booking.seats.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(seat.getKey());
            if (player == null || player.isRemoved()) {
                gone.add(seat.getKey());
                continue;
            }
            Vec3 belongs = airship.toWorld(seat.getValue());
            if (player.level() != level) {
                if (booking.crossed) {
                    // Left in the dimension the ship crossed out of. Nothing else is ever going to fetch
                    // them: this is the one call that changes a player's dimension and tells their client.
                    Trace.log(Trace.CREW, "{} was left in {} when ship {} crossed into {}: fetched to their seat {}",
                            Trace.who(player), player.level().dimension().location(), shipId,
                            level.dimension().location(), Trace.vec(belongs));
                    player.teleportTo(level, belongs.x, belongs.y, belongs.z, player.getYRot(), player.getXRot());
                    WarpPassengers.calm(player, hull);
                    continue;
                }
                // The player is in another dimension - perhaps through a portal or a dimension
                // change during the warp. Do not remove them from the booking: if they return
                // to this dimension before the grace period expires, the manifest will catch
                // them. Removing them was the silent loss that left crew stranded.
                continue;
            }
            if (!adrift(player.position().distanceTo(belongs))) {
                continue;
            }
            // Every one of these is a player the hull failed to carry - the symptom the manifest exists to
            // paper over - so each is worth a line: how far adrift, and at what point of the warp.
            Trace.log(Trace.CREW, "{} came adrift of ship {} by {} blocks at {}: put back on their seat {}",
                    Trace.who(player), shipId, Trace.num(player.position().distanceTo(belongs)),
                    Trace.vec(player.position()), Trace.vec(belongs));
            // The one call that also resets what the client believes it is doing. A raw position
            // write is undone by the next movement packet the client sends, which is the whole
            // reason a player could be left behind by a hull that had plainly moved.
            player.connection.teleport(belongs.x, belongs.y, belongs.z,
                    player.getYRot(), player.getXRot());
            // Including the motion Sable lent them, which the game's own motion packet cannot reach -
            // left alone, it carries them straight back off the deck they have just been put on.
            WarpPassengers.calm(player, hull);
        }
        gone.forEach(booking.seats::remove);
    }
}
