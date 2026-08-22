package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;

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
     * few seconds.
     */
    private static final int GRACE_TICKS = 200;

    /**
     * How far from their seat somebody has to be before they are put back.
     *
     * <p>Wider than any deck, because the point is to catch a passenger who is somewhere else
     * entirely - left at the departure point, or falling past the keel - and never to interfere with
     * one who is simply walking around.
     */
    private static final double ADRIFT = 6.0D;

    private static final class Booking {
        private final ResourceKey<Level> dimension;
        private final Map<UUID, Vec3> seats = new HashMap<>();
        private int ticksLeft = GRACE_TICKS;

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
        booking.ticksLeft = GRACE_TICKS;
        record(airship, booking);
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
        booking.ticksLeft = GRACE_TICKS;
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

    public static int graceTicks() {
        return GRACE_TICKS;
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
        for (Map.Entry<UUID, Vec3> seat : booking.seats.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(seat.getKey());
            if (player == null || player.isRemoved()) {
                gone.add(seat.getKey());
                continue;
            }
            if (player.level() != level) {
                continue; // somewhere this manifest has no say over
            }
            Vec3 belongs = airship.toWorld(seat.getValue());
            if (!adrift(player.position().distanceTo(belongs))) {
                continue;
            }
            // The one call that also resets what the client believes it is doing. A raw position
            // write is undone by the next movement packet the client sends, which is the whole
            // reason a player could be left behind by a hull that had plainly moved.
            player.connection.teleport(belongs.x, belongs.y, belongs.z,
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        gone.forEach(booking.seats::remove);
    }
}
