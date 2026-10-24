package uk.co.iceconchy.aerowarptics.trace;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The header a trace starts with: what is installed, where the settings came from, what they are, and
 * what they add up to.
 *
 * <p>Half of all "it doesn't work" reports are a setting, and the other half cannot be read without
 * knowing the settings. This writes every one of them into the log at the moment tracing starts, so a
 * log sent in answers "which config file" and "what is it set to" without anybody having to be asked -
 * and in single player the file is per world, which is precisely where a player who edited the wrong one
 * would otherwise go on insisting it was switched on.
 *
 * <p>The tier lines are the settings worked through: how far each drive can really jump on a full charge
 * at a range of hull masses, beside the range its console shows.
 */
public final class TraceReport {

    /** Hull masses the reach table is worked at. The README's 24x14x62 example hull is about 42,000. */
    static final double[] MASSES = {0.0D, 25_000.0D, 50_000.0D, 100_000.0D, 200_000.0D, 400_000.0D, 800_000.0D};

    /** Mods whose version bears on what this one does, in the order a reader wants them. */
    private static final String[] MODS = {"minecraft", "neoforge", AeroWarptics.MODID, "create", "sable", "simulated",
            "aeronautics", "geckolib", "dimensional_sable", "jei", "flywheel", "sodium", "embeddium", "iris", "oculus"};

    private TraceReport() {
    }

    /** Writes the header, whether tracing or not - it was asked for. */
    public static void write(MinecraftServer server, String why) {
        for (String line : lines(server, why)) {
            Trace.always(Trace.SESSION, "{}", line);
        }
    }

    static List<String> lines(MinecraftServer server, String why) {
        List<String> lines = new ArrayList<>();
        lines.add("==== AeroWarptics trace: " + why + " ====");
        lines.add("server config file: " + serverConfigPath() + " (debug.trace=" + Trace.enabled() + ")");
        lines.add("versions: " + versions() + " java=" + System.getProperty("java.version"));
        lines.add(world(server));
        lines.addAll(configLines(AWConfig.SERVER_SPEC));
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            lines.add(reachLine(tier.getSerializedName(), WarpCost.fromConfig(tier)));
        }
        lines.add("==== end of header - searching the log for \"aerowarptics\" finds every trace line ====");
        return lines;
    }

    private static String serverConfigPath() {
        for (ModConfig config : ModConfigs.getModConfigs(AeroWarptics.MODID)) {
            if (config.getType() == ModConfig.Type.SERVER) {
                try {
                    return config.getFullPath().toString();
                } catch (IllegalStateException notOnDisk) {
                    return config.getFileName() + " (defaults, not loaded from a file)";
                }
            }
        }
        return "? (not registered)";
    }

    /** The versions of this mod and those it works with, as {@code id=version} pairs. Shared with the client's header. */
    public static String versions() {
        StringBuilder out = new StringBuilder();
        ModList mods = ModList.get();
        for (String id : MODS) {
            String version = mods == null ? null : mods.getModContainerById(id)
                    .map(container -> container.getModInfo().getVersion().toString()).orElse(null);
            // Optional mods are named only when present; a required one missing is worth saying.
            if (version != null) {
                out.append(out.isEmpty() ? "" : " ").append(id).append('=').append(version);
            } else if (id.equals("dimensional_sable")) {
                out.append(out.isEmpty() ? "" : " ").append(id).append("=absent");
            }
        }
        return out.toString();
    }

    /** Who is on, and how many ships and anchors each world holds. */
    private static String world(MinecraftServer server) {
        StringBuilder out = new StringBuilder(server.isDedicatedServer() ? "dedicated server" : "single player");
        out.append(", ").append(server.getPlayerList().getPlayerCount()).append(" player(s) online");
        out.append(", ").append(WarpAnchorRegistry.get(server).all().size()).append(" warp anchor(s); ships:");
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            int ships = container instanceof ServerSubLevelContainer serverContainer
                    ? serverContainer.getAllSubLevels().size() : 0;
            out.append(' ').append(level.dimension().location()).append('=').append(ships);
        }
        return out.toString();
    }

    /** Every value in a spec, one line per section, as {@code config <section>: key=value ...}. */
    public static List<String> configLines(ModConfigSpec spec) {
        List<String> lines = new ArrayList<>();
        if (!spec.isLoaded()) {
            lines.add("config: not loaded");
            return lines;
        }
        walk(spec.getValues(), "", lines);
        return lines;
    }

    private static void walk(UnmodifiableConfig section, String path, List<String> lines) {
        StringBuilder leaves = new StringBuilder();
        List<Map.Entry<String, Object>> nested = new ArrayList<>();
        for (Map.Entry<String, Object> entry : section.valueMap().entrySet()) {
            if (entry.getValue() instanceof UnmodifiableConfig) {
                nested.add(entry);
            } else if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                Object shown;
                try {
                    shown = value.get();
                } catch (RuntimeException unreadable) {
                    shown = "?";
                }
                leaves.append(leaves.isEmpty() ? "" : " ").append(entry.getKey()).append('=').append(shown);
            }
        }
        if (!leaves.isEmpty()) {
            lines.add("config " + (path.isEmpty() ? "(top)" : path) + ": " + leaves);
        }
        for (Map.Entry<String, Object> entry : nested) {
            walk((UnmodifiableConfig) entry.getValue(), path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(),
                    lines);
        }
    }

    /**
     * How far one tier can really jump on a full charge, at each of {@link #MASSES}, beside its range.
     *
     * <p>Where the reach falls short of the range, anchors in between are refused as out of range even
     * though every screen shows them inside it. Pure, so it is tested.
     */
    public static String reachLine(String tier, WarpCost.Formula formula) {
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "tier %s: range %s-%s, efficiency %.2f; reach on a full charge by hull mass:", tier,
                Trace.blocks(formula.minimumDistance()), Trace.blocks(formula.maximumDistance()), formula.efficiency()));
        for (double mass : MASSES) {
            double reach = formula.reach(mass);
            out.append(' ').append(mass == 0.0D ? "0" : Math.round(mass / 1000.0D) + "k").append('=')
                    .append(reach < 0.0D ? "NONE" : Trace.blocks(reach));
        }
        return out.toString();
    }
}
