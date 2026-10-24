package uk.co.iceconchy.aerowarptics.hold;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a level ticking while a train waits at a gate in it with nobody about.
 *
 * <p>A level with no players and no forced chunks stops ticking its block entities three hundred ticks after the
 * last player leaves, and a region ticket - which is all a gate's ground is ever held by - does not count. A gate
 * opens over two seconds of its own ticks, so a train waiting at a gate in an empty Nether would wait for ever:
 * the gate would be asked, would start dialling, and would never get to open. While a train is waiting, or
 * running through, one non-ticking forced chunk under the gate keeps its level awake, as
 * {@link AirshipResidency}'s wake does for a held ship.
 *
 * <p>Every one of these is on a short lease renewed while the train is there, and none survives a restart: the
 * controller drops all of them as the level loads, because a train that is still waiting will simply ask again.
 */
public final class RailWake {

    private static final TicketController CONTROLLER = new TicketController(AeroWarptics.id("rail_wake"),
            (level, tickets) -> java.util.List.copyOf(tickets.getEntityTickets().keySet())
                    .forEach(owner -> tickets.removeAllTickets(owner)));

    private static final UUID OWNER = UUID.nameUUIDFromBytes("aerowarptics:rail_wake".getBytes(StandardCharsets.UTF_8));

    /** How long a wake lasts unrenewed. */
    public static final int LEASE_TICKS = 200;

    /** level -> chunk -> game time it lapses at */
    private static final Map<ResourceKey<Level>, Map<Long, Long>> KEPT = new HashMap<>();

    private RailWake() {
    }

    /** Registered on the mod event bus from {@link AeroWarptics}. */
    public static void onRegisterControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /**
     * Keeps the level awake round a loaded chunk for {@link #LEASE_TICKS} more.
     *
     * <p>Only ever for a chunk that is already loaded - NeoForge loads a chunk as it forces it, and this must not be
     * what makes it - and only while the level has nobody in it, since otherwise it is awake anyway.
     */
    public static void keep(ServerLevel level, ChunkPos chunk, long now) {
        if (!level.players().isEmpty() || !level.hasChunk(chunk.x, chunk.z)) {
            return;
        }
        Map<Long, Long> kept = KEPT.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Long previous = kept.put(chunk.toLong(), now + LEASE_TICKS);
        if (previous == null) {
            CONTROLLER.forceChunk(level, OWNER, chunk.x, chunk.z, true, false);
            Trace.log(Trace.HOLD, "{} kept awake at {} for a train at a gate", level.dimension().location(), chunk);
        }
    }

    /** Lets go of every wake nobody has renewed. */
    public static void tick(MinecraftServer server, long now) {
        for (Map.Entry<ResourceKey<Level>, Map<Long, Long>> entry : KEPT.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            Iterator<Map.Entry<Long, Long>> chunks = entry.getValue().entrySet().iterator();
            while (chunks.hasNext()) {
                Map.Entry<Long, Long> chunk = chunks.next();
                if (chunk.getValue() > now) {
                    continue;
                }
                chunks.remove();
                if (level != null) {
                    CONTROLLER.forceChunk(level, OWNER, ChunkPos.getX(chunk.getKey()), ChunkPos.getZ(chunk.getKey()),
                            false, false);
                    Trace.log(Trace.HOLD, "{} no longer kept awake at {} for a train", entry.getKey().location(),
                            new ChunkPos(chunk.getKey()));
                }
            }
        }
    }

    /** Forgets every wake, as the server stops - the controller drops their tickets when it next starts. */
    public static void clear() {
        KEPT.clear();
    }
}
