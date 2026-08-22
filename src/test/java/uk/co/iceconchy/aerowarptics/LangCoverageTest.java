package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every key the code asks for is a key the language file has.
 *
 * <p>A missing translation does not crash and does not fail to compile. It renders as
 * {@code aerowarptics.gui.rift_probe.coverage} in the middle of a screen, and the only way to find
 * out was to open that screen and look at it - which, for a panel that only appears when a sounding
 * has failed, might be a very long time.
 *
 * <p>This reads the sources rather than the compiled classes so it can see the literal keys. Keys
 * assembled from a prefix and a variable are skipped, because a regex cannot know what the variable
 * held; {@code AdvancementTest} covers the one place that does that.
 */
class LangCoverageTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");
    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    /** {@code AWLang.translate("gui.thing")} and {@code AWLang.component("gui.thing")}. */
    private static final Pattern USED =
            Pattern.compile("AWLang\\.(?:translate|component)\\(\\s*\"([^\"\\\\]+)\"");

    private static JsonObject lang() {
        try (Reader reader = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + LANG, e);
        }
    }

    private static List<Path> sources() {
        try (Stream<Path> stream = Files.walk(SOURCES)) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyKeyTheCodeUsesIsTranslated() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (Path source : sources()) {
            String text;
            try {
                text = Files.readString(source, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            Matcher matcher = USED.matcher(text);
            while (matcher.find()) {
                String key = matcher.group(1);
                // A trailing dot means the key is finished off with a variable at runtime.
                if (key.endsWith(".")) {
                    continue;
                }
                checked++;
                String full = "aerowarptics." + key;
                if (!lang.has(full)) {
                    missing.add(full + "   (" + SOURCES.relativize(source) + ")");
                }
            }
        }

        assertTrue(checked > 50, "only found " + checked + " keys - has AWLang been renamed?");
        if (!missing.isEmpty()) {
            fail("these would render as raw keys on screen:\n  " + String.join("\n  ", missing));
        }
    }

    /**
     * Every enum that names a translation key has one for each of its constants.
     *
     * <p>Adding a constant to a state or failure enum is a one-line change that silently leaves a new
     * value with nothing to say for itself.
     */
    @Test
    void everyEnumConstantIsTranslated() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        record Family(String prefix, String[] names) {
        }

        List<Family> families = List.of(
                new Family("probe.bearing.", names(uk.co.iceconchy.aerowarptics.probe.ProbeBearing.values())),
                new Family("probe.state.", names(uk.co.iceconchy.aerowarptics.probe.ProbeState.values())),
                new Family("probe.verdict.", names(uk.co.iceconchy.aerowarptics.probe.ProbeVerdict.values())),
                new Family("warp.failure.", names(uk.co.iceconchy.aerowarptics.warp.WarpFailure.values())),
                new Family("gate.failure.", names(uk.co.iceconchy.aerowarptics.gate.GateFailure.values())));

        for (Family family : families) {
            for (String name : family.names()) {
                String key = "aerowarptics." + family.prefix() + name;
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("enum constants with no translation:\n  " + String.join("\n  ", missing));
        }
    }

    private static String[] names(net.minecraft.util.StringRepresentable[] values) {
        String[] names = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            names[index] = values[index].getSerializedName();
        }
        return names;
    }
}
