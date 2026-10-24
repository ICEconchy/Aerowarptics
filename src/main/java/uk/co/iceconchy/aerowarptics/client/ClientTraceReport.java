package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.fx.GravityLens;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;
import uk.co.iceconchy.aerowarptics.trace.TraceReport;

/**
 * When the client's trace starts and stops, and the header it starts with.
 *
 * <p>The client's header is about drawing, because that is what goes wrong on this side: which graphics
 * mode and shader pack the rift effects are working under, whether the lens has stood down and why, and
 * the client's own settings. Several past reports were an effect behaving exactly as designed under
 * Fabulous graphics or a shader pack, which the server's log cannot know about.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class ClientTraceReport {

    private ClientTraceReport() {
    }

    /** The server has said whether it is tracing. */
    static void serverTracing(boolean tracing) {
        boolean was = ClientTrace.enabled();
        ClientTrace.serverTracing(tracing);
        if (!was && ClientTrace.enabled()) {
            write("the server is tracing");
        } else if (was && !ClientTrace.enabled()) {
            ClientTrace.always(ClientTrace.SESSION, "==== AeroWarptics client trace stopped: the server stopped tracing ====");
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // The server's word, if it is tracing, arrives a moment later and writes its own header; this one is
        // for a client tracing on its own setting, on a server that is not.
        if (ClientTrace.enabled()) {
            write("client debug.trace is on");
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // The next server has its own say; a trace switched on by this one must not follow the player there.
        ClientTrace.serverTracing(false);
    }

    private static void write(String why) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientTrace.always(ClientTrace.SESSION, "==== AeroWarptics client trace: {} ====", why);
        ClientTrace.always(ClientTrace.SESSION, "versions: {}", TraceReport.versions());
        ClientTrace.always(ClientTrace.SESSION, "graphics={} renderDistance={} shaderPack={} gravityLens={} window={}x{}"
                        + " guiScale={}", minecraft.options.graphicsMode().get(), minecraft.options.renderDistance().get(),
                GravityLens.shaderPackActive() ? "in use" : "none", GravityLens.status(),
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), minecraft.getWindow().getGuiScale());
        for (String line : TraceReport.configLines(AWConfig.CLIENT_SPEC)) {
            ClientTrace.always(ClientTrace.SESSION, "client {}", line);
        }
    }
}
