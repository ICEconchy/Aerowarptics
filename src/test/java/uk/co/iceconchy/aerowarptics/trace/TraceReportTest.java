package uk.co.iceconchy.aerowarptics.trace;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.AWConfig;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The header's dump of the settings, walked over the real server spec.
 *
 * <p>This is the part of the trace that runs against live NeoForge state and nothing else exercises
 * before a server starts with tracing on - so a section the walk skipped, or a value it could not read,
 * would first show up as a header silently missing the one setting a report turned on. The spec is
 * loaded here with its defaults, as a world's fresh file would be, through FML's own loaded-config record.
 */
class TraceReportTest {

    private static final Pattern PAIR = Pattern.compile("(?:^|\\s)[A-Za-z0-9_]+=");

    @Test
    void theHeaderListsEveryServerSettingOnce() throws Exception {
        CommentedConfig defaults = CommentedConfig.inMemory();
        AWConfig.SERVER_SPEC.correct(defaults);
        AWConfig.SERVER_SPEC.acceptConfig(loaded(defaults));
        try {
            List<String> lines = TraceReport.configLines(AWConfig.SERVER_SPEC);

            int shown = 0;
            for (String line : lines) {
                Matcher pairs = PAIR.matcher(line.substring(line.indexOf(": ") + 2));
                while (pairs.find()) {
                    shown++;
                }
            }
            assertEquals(leaves(AWConfig.SERVER_SPEC.getValues()), shown, "every setting, and each once:\n"
                    + String.join("\n", lines));

            assertTrue(lines.contains("config debug: trace=false"), "the switch itself must be in the header");
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("config drives.mk_i: ")
                    && line.contains("maximumRange=4000.0")), "per-tier sections are nested and must be walked");
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("config cost: ")
                    && line.contains("sizeMultiplier=0.15")), "the cost formula's inputs are what a range report needs");
        } finally {
            AWConfig.SERVER_SPEC.acceptConfig(null);
        }
    }

    @Test
    void anUnloadedSpecSaysSoRatherThanThrowing() {
        assertEquals(List.of("config: not loaded"), TraceReport.configLines(AWConfig.CLIENT_SPEC));
    }

    private static int leaves(UnmodifiableConfig section) {
        int count = 0;
        for (Object value : section.valueMap().values()) {
            if (value instanceof UnmodifiableConfig nested) {
                count += leaves(nested);
            } else if (value instanceof ModConfigSpec.ConfigValue<?>) {
                count++;
            }
        }
        return count;
    }

    /** FML's record for a loaded config is package-private; this is the one thing a test needs from it. */
    private static IConfigSpec.ILoadedConfig loaded(CommentedConfig config) throws Exception {
        Class<?> type = Class.forName("net.neoforged.fml.config.LoadedConfig");
        Constructor<?> make = type.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        make.setAccessible(true);
        return (IConfigSpec.ILoadedConfig) make.newInstance(config, null, null);
    }
}
