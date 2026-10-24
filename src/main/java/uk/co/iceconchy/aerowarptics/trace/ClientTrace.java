package uk.co.iceconchy.aerowarptics.trace;

import org.slf4j.helpers.MessageFormatter;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * The client's half of the trace: what this client was told, and what it made of it.
 *
 * <p>A separate switch from {@link Trace}, because client code does not read server settings - it
 * reads the client's own {@code debug.trace}, or what the server has told it with
 * {@code ClientboundTracePacket}. A server that is tracing tells every client that joins it, so a
 * report from a single-player world, or from a server an admin has switched on, carries both halves in
 * each player's own {@code latest.log} with nobody having to find a second setting.
 *
 * <p>Lines are tagged {@code [client:topic]} so the two halves of an integrated server's log - which
 * is one file - can be told apart.
 *
 * <p>Common-safe: it names nothing client-only, so the dedicated server can load a class that calls it.
 * There, the client config is never loaded and this is simply off.
 */
public final class ClientTrace {

    public static final String CUE = "cue";
    public static final String FOLD = "fold";
    public static final String FEEDBACK = "feedback";
    public static final String DRAW = "draw";
    public static final String FLEET = "fleet";
    public static final String STORM = "storm";
    public static final String WARD = "ward";
    public static final String SESSION = "session";

    private static final TraceMemo MEMO = new TraceMemo(512);

    /** Whether the server this client is connected to is tracing. Cleared on disconnect. */
    private static volatile boolean serverTracing;

    private ClientTrace() {
    }

    public static boolean enabled() {
        return serverTracing || (AWConfig.CLIENT_SPEC.isLoaded() && AWConfig.CLIENT_TRACE.get());
    }

    /** The server's word on whether it is tracing, as it said on joining or on being switched. */
    public static void serverTracing(boolean tracing) {
        boolean was = enabled();
        serverTracing = tracing;
        if (!was && enabled()) {
            MEMO.clear();
        }
    }

    public static void log(String topic, String format, Object... args) {
        if (enabled()) {
            AeroWarptics.LOGGER.info("[client:" + topic + "] " + format, args);
        }
    }

    /** One line whether tracing or not, for a header somebody has decided to write. */
    public static void always(String topic, String format, Object... args) {
        AeroWarptics.LOGGER.info("[client:" + topic + "] " + format, args);
    }

    /** {@link Trace#changed}, for the client. */
    public static void changed(String subject, String topic, String format, Object... args) {
        if (!enabled()) {
            return;
        }
        String line = MessageFormatter.arrayFormat(format, args).getMessage();
        if (MEMO.changed(subject, line)) {
            AeroWarptics.LOGGER.info("[client:{}] {}", topic, line);
        }
    }
}
