package uk.co.iceconchy.aerowarptics.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import org.slf4j.helpers.MessageFormatter;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.Locale;
import java.util.UUID;

/**
 * The server's diagnostic trace: one switch, {@code debug.trace}, for the whole mod.
 *
 * <p>Every line starts with a bracketed topic - {@code [drive]}, {@code [gate]} - so a report can be
 * searched by subsystem, and every line comes from {@link AeroWarptics#LOGGER}, so searching a log for
 * the mod's name finds all of it. Written at INFO: a player sending a log in has not reconfigured
 * logging, and should not have to.
 *
 * <p>What earns a line is a decision, above all a refusal. The mod's failure modes are mostly silent -
 * a lever pulled at a drive with no course does nothing, by design - and a report that says "it didn't
 * warp" is only answerable if the log says which of a dozen reasons it was, with the numbers behind it.
 * A trace line that only says <em>that</em> something happened, and not why or with what, is not
 * worth adding.
 *
 * <p>Off, every entry point returns before formatting anything. Client-side code does not use this -
 * it reads its own switch, see {@link ClientTrace}.
 */
public final class Trace {

    public static final String SESSION = "trace";
    public static final String DRIVE = "drive";
    public static final String COURSE = "course";
    public static final String ACCESS = "access";
    public static final String CLAIM = "claim";
    public static final String LAUNCH = "launch";
    public static final String ARRIVAL = "arrival";
    public static final String CREW = "crew";
    public static final String RESIDENCY = "residency";
    public static final String HOLD = "hold";
    public static final String CHART = "chart";
    public static final String ANCHOR = "anchor";
    public static final String NETWORK = "network";
    public static final String GATE = "gate";
    public static final String PROBE = "probe";
    public static final String FLEET = "fleet";
    public static final String BEACON = "beacon";
    public static final String STORM = "storm";
    public static final String DETECTOR = "detector";
    public static final String CROSSING = "crossing";
    public static final String SIPHON = "siphon";
    public static final String DRYRUN = "dryrun";
    public static final String RIFTING = "rifting";
    public static final String FOLD = "fold";
    public static final String COLLAPSE = "collapse";
    public static final String PLOTTER = "plotter";
    public static final String WARD = "ward";
    public static final String WELLHEAD = "wellhead";
    public static final String BERTH = "berth";
    public static final String RAIL = "rail";

    /** What was last said about each subject, for {@link #changed}. */
    private static final TraceMemo MEMO = new TraceMemo(1024);

    private Trace() {
    }

    /**
     * Whether the server is tracing.
     *
     * <p>False, rather than an exception, before the server config has loaded - asked on the client's
     * main menu, or by anything that runs during start-up - so no caller has to know when it is safe to ask.
     */
    public static boolean enabled() {
        return AWConfig.SERVER_SPEC.isLoaded() && AWConfig.TRACE.get();
    }

    /** One line, if tracing. {@code format} takes SLF4J's {@code {}} placeholders. */
    public static void log(String topic, String format, Object... args) {
        if (enabled()) {
            AeroWarptics.LOGGER.info("[" + topic + "] " + format, args);
        }
    }

    /**
     * One line whether tracing or not.
     *
     * <p>For what is rare enough never to crowd a log and important enough that nobody should have had
     * to think of turning tracing on first - a drive's clearance checks being switched off, a warp
     * interrupted mid-flight.
     */
    public static void always(String topic, String format, Object... args) {
        AeroWarptics.LOGGER.info("[" + topic + "] " + format, args);
    }

    /** {@link #always}, as a warning. */
    public static void warn(String topic, String format, Object... args) {
        AeroWarptics.LOGGER.warn("[" + topic + "] " + format, args);
    }

    /**
     * One line, if tracing and if it says something different about {@code subject} from last time.
     *
     * <p>For what is re-asked on a timer - see {@link TraceMemo}. The subject names what the line is
     * about, not what it says: a player's access to one drive, a table's quotes for one player.
     */
    public static void changed(String subject, String topic, String format, Object... args) {
        if (!enabled()) {
            return;
        }
        String line = MessageFormatter.arrayFormat(format, args).getMessage();
        if (MEMO.changed(subject, line)) {
            AeroWarptics.LOGGER.info("[{}] {}", topic, line);
        }
    }

    /**
     * Records the current state of a subject without logging it, so that the next different state is
     * heard. For the unremarkable half of a {@link #changed} pair: access granted is not news, but it
     * has to be remembered or a later refusal that matches an older one goes unlogged.
     */
    public static void settled(String subject, String state) {
        if (enabled()) {
            MEMO.changed(subject, state);
        }
    }

    /** Called when tracing starts, so everything is reported afresh rather than as "unchanged". */
    static void resetMemory() {
        MEMO.clear();
    }

    // ---------------------------------------------------------------- names

    /** A player's name, or their id when they are not online to ask. */
    public static String who(@Nullable UUID player) {
        if (player == null) {
            return "nobody";
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(player);
        return online != null ? online.getGameProfile().getName() : player.toString();
    }

    public static String who(@Nullable Player player) {
        return player == null ? "nobody" : player.getGameProfile().getName();
    }

    // ---------------------------------------------------------------- format

    public static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** A whole number of blocks, for distances and ranges where hundredths are noise. */
    public static String blocks(double value) {
        return Double.isInfinite(value) ? "unlimited" : String.format(Locale.ROOT, "%.0f", value);
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0D);
    }

    public static String vec(Vector3dc v) {
        return v == null ? "-" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x(), v.y(), v.z());
    }

    public static String vec(Vec3 v) {
        return v == null ? "-" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    public static String pos(BlockPos pos) {
        return pos == null ? "-" : String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}
