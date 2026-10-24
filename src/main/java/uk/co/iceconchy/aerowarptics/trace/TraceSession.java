package uk.co.iceconchy.aerowarptics.trace;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundTracePacket;

/**
 * When tracing starts and stops, and who is told.
 *
 * <p>The switch can change three ways - the server starting with it on, somebody editing the file while
 * the server runs (NeoForge reloads it), and {@code /aerowarptics trace} - and every one of them should
 * start the log off the same way: with the {@link TraceReport header}, and with every connected client
 * told to trace its own side. Watching the value each tick catches all three without caring which it was.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class TraceSession {

    /** What the switch said last tick, so a change is noticed once. */
    private static boolean tracing;

    private TraceSession() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        tracing = Trace.enabled();
        if (tracing) {
            started(event.getServer(), "the server started with debug.trace on");
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        tracing = false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        boolean now = Trace.enabled();
        if (now == tracing) {
            return;
        }
        tracing = now;
        if (now) {
            started(event.getServer(), "debug.trace was switched on");
        } else {
            stopped(event.getServer(), "debug.trace was switched off");
        }
    }

    /** A joining player is told whether to trace their own side, and the log says who arrived. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Trace.log(Trace.SESSION, "{} joined in {} at {}", Trace.who(player), player.level().dimension().location(),
                    Trace.vec(player.position()));
            AWNetwork.sendTo(player, new ClientboundTracePacket(Trace.enabled()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Trace.log(Trace.SESSION, "{} left", Trace.who(event.getEntity()));
    }

    /**
     * Sets the switch, saves it to the world's config file, and starts or stops the trace now rather than
     * on the next tick, so the command's own reply comes after the header rather than before it.
     */
    public static void set(MinecraftServer server, boolean on, String who) {
        AWConfig.TRACE.set(on);
        AWConfig.TRACE.save();
        tracing = on;
        if (on) {
            started(server, "switched on by " + who);
        } else {
            stopped(server, "switched off by " + who);
        }
    }

    private static void started(MinecraftServer server, String why) {
        Trace.resetMemory();
        TraceReport.write(server, why);
        PacketDistributor.sendToAllPlayers(new ClientboundTracePacket(true));
    }

    private static void stopped(MinecraftServer server, String why) {
        Trace.always(Trace.SESSION, "==== AeroWarptics trace stopped: {} ====", why);
        PacketDistributor.sendToAllPlayers(new ClientboundTracePacket(false));
    }
}
