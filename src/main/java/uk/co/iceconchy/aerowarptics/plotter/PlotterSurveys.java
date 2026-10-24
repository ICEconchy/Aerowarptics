package uk.co.iceconchy.aerowarptics.plotter;

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
import uk.co.iceconchy.aerowarptics.network.ClientboundPlotterSurveyPacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the ground round a Rift Plotter's target while somebody is plotting on it, so its table has a model.
 *
 * <p>The Astrolabe's {@code PreviewHolds}, for the plotter: the target is typed in, so it is nearly always
 * somewhere nobody is - often somewhere nobody has ever been, whose chunks are generated for the first time here.
 * Only the chunks the model reads are asked for ({@code plotterSurveyChunks} out from the target), on an expiring
 * ticket through {@link Holds}, one target per player, the newest replacing the last. The table says "Surveying"
 * meanwhile, and gets whatever has arrived if the ground takes too long.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class PlotterSurveys {

    /** How long the ground round a surveyed target stays loaded: long enough to plot on it and fire. */
    private static final int LIFESPAN_TICKS = 1_200;

    /** How long to wait for it - generating new ground takes a while - before sending what has come. */
    private static final int WAIT_TICKS = 300;

    private static final int POLL_INTERVAL = 5;

    private static final TicketType<ChunkPos> SURVEY =
            TicketType.create("aerowarptics:plotter_survey", Comparator.comparingLong(ChunkPos::toLong), LIFESPAN_TICKS);

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(BlockPos plotter, ServerLevel level, int x, int z, long deadline) {
    }

    private PlotterSurveys() {
    }

    /** Blocks the model reaches from its target: the configured chunks, and half a chunk over for the edge. */
    public static int radius() {
        return AWConfig.PLOTTER_SURVEY_CHUNKS.get() * 16 + 8;
    }

    /** Asks for a model of the ground round a target, loading it first if it is not there. */
    public static void request(ServerPlayer player, ServerLevel level, BlockPos plotter, int x, int z) {
        PENDING.remove(player.getUUID());
        int radius = radius();
        if (PlotterSurvey.ready(level, x, z, radius)) {
            send(level, player, plotter, PlotterSurvey.of(level, x, z, radius));
            return;
        }
        // One past the chunks read, so every one the model reaches is fully loaded, not only its middle.
        boolean held = Holds.ground(level, SURVEY, new ChunkPos(x >> 4, z >> 4), AWConfig.PLOTTER_SURVEY_CHUNKS.get() + 1);
        if (!held) {
            send(level, player, plotter, PlotterSurvey.of(level, x, z, radius));
            return;
        }
        long now = level.getServer().overworld().getGameTime();
        PENDING.put(player.getUUID(), new Pending(plotter.immutable(), level, x, z, now + WAIT_TICKS));
        Trace.log(Trace.PLOTTER, "{} is plotting on ({}, {}) at the plotter at {}, which is not loaded - loading {} chunks"
                + " out for the table", Trace.who(player), x, z, Trace.pos(plotter), AWConfig.PLOTTER_SURVEY_CHUNKS.get());
    }


    private static void send(ServerLevel level, ServerPlayer player, BlockPos plotter, PlotterSurvey survey) {
        // Whatever of it lies under a Rift Ward's field is the ward's to say, to anyone it would turn aside.
        AWNetwork.sendTo(player, new ClientboundPlotterSurveyPacket(plotter,
                uk.co.iceconchy.aerowarptics.ward.WardScans.table(level, survey, player.getUUID())));
        if (level.getBlockEntity(plotter) instanceof RiftPlotterBlockEntity machine) {
            machine.groundSurveyed(survey.centreX(), survey.centreZ(), survey.groundY());
        }
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
        int radius = radius();
        Iterator<Map.Entry<UUID, Pending>> entries = PENDING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Pending> entry = entries.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null) {
                entries.remove();
                continue;
            }
            boolean ready = PlotterSurvey.ready(pending.level(), pending.x(), pending.z(), radius);
            if (!ready && now < pending.deadline()) {
                continue;
            }
            entries.remove();
            PlotterSurvey survey = PlotterSurvey.of(pending.level(), pending.x(), pending.z(), radius);
            if (!ready) {
                Trace.log(Trace.PLOTTER, "{}'s model of ({}, {}) sent after {}t with {}% of its ground loaded",
                        Trace.who(player), pending.x(), pending.z(), WAIT_TICKS, Math.round(survey.coverage() * 100.0F));
            }
            send(pending.level(), player, pending.plotter(), survey);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
