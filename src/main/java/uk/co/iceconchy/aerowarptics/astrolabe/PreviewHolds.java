package uk.co.iceconchy.aerowarptics.astrolabe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundAstrolabeChartPacket;
import uk.co.iceconchy.aerowarptics.probe.ProbeTicket;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the ground round an anchor while somebody is looking at it on an Astrolabe, so its chart has a map.
 *
 * <p>{@link DestinationSurvey} reads only chunks that are already loaded, and an anchor is usually
 * somewhere nobody is standing - which is the reason to warp there. So a chart of anywhere worth going
 * used to be blank. Asked for a picture of ground that is not loaded, the chart now loads it: an expiring
 * ticket over the survey, and the picture is sent once the ground is there. The screen says
 * "Surveying..." meanwhile, which is exactly what is happening.
 *
 * <p>Held only because a player is looking: one anchor per player, the new selection replacing the last,
 * for {@link #LIFESPAN_TICKS} and no longer. Nothing is loaded at an anchor that nobody has asked about.
 * The anchor already exists, so its ground has been generated before; this only brings it off disk.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class PreviewHolds {

    /** How long the ground round a previewed anchor stays loaded. Long enough to read a chart and pick. */
    private static final int LIFESPAN_TICKS = 600;

    /** How long to wait for it before sending whatever has arrived. */
    private static final int WAIT_TICKS = 200;

    private static final int POLL_INTERVAL = 5;

    private static final TicketType<ChunkPos> PREVIEW =
            TicketType.create("aerowarptics:preview", Comparator.comparingLong(ChunkPos::toLong), LIFESPAN_TICKS);

    /** Everyone's outstanding picture, one each. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(UUID anchorId, ServerLevel level, BlockPos centre, long deadline) {
    }

    private PreviewHolds() {
    }

    /**
     * Asks for a picture of an anchor, loading its ground first if it is not there.
     *
     * @return {@code true} when the picture will be sent later, {@code false} when the caller should
     * survey and send it now - because the ground is already loaded, or loading it is switched off
     */
    public static boolean request(ServerPlayer player, UUID anchorId, ServerLevel level, BlockPos centre) {
        // Whatever this player was waiting on before is no longer what they are looking at.
        PENDING.remove(player.getUUID());
        if (!AWConfig.ASTROLABE_LOADS_PREVIEWS.get() || DestinationSurvey.ready(level, centre, 1.0D)) {
            return false;
        }
        boolean held = Holds.ground(level, PREVIEW, new ChunkPos(centre),
                ProbeTicket.radiusFor(DestinationSurvey.RADIUS));
        if (!held) {
            return false;
        }
        long now = level.getServer().overworld().getGameTime();
        PENDING.put(player.getUUID(), new Pending(anchorId, level, centre.immutable(), now + WAIT_TICKS));
        Trace.log(Trace.CHART, "{} is looking at anchor {} at {} in {}, which is not loaded - loading its"
                        + " surroundings for the chart", Trace.who(player), anchorId, Trace.pos(centre),
                level.dimension().location());
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % POLL_INTERVAL != 0L) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> entries = PENDING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Pending> entry = entries.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null) {
                entries.remove();
                continue;
            }
            boolean ready = DestinationSurvey.ready(pending.level(), pending.centre(), 1.0D);
            if (!ready && now < pending.deadline()) {
                continue;
            }
            entries.remove();
            if (!ready) {
                Trace.log(Trace.CHART, "{}'s chart of anchor {} sent after {}t with its ground only partly loaded",
                        Trace.who(player), pending.anchorId(), WAIT_TICKS);
            }
            AWNetwork.sendTo(player, new ClientboundAstrolabeChartPacket.Preview(pending.anchorId(),
                    DestinationSurvey.of(pending.level(), pending.centre())));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
