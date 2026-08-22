package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a translation has room for everything handed to it.
 *
 * <p>{@code LangCoverageTest} already proves every key a call site asks for actually ships. This is
 * the other half: that a key used with arguments has somewhere to put them.
 *
 * <p>Written because of a real regression, and one that is invisible in exactly the way this suite
 * exists to catch. {@code gui.astrolabe.course} was "Course: %s" and used by the Astrolabe's goggles;
 * a later change repurposed it as a bare "Course:" label for the chart's course strip, which draws
 * its value separately. Nothing failed. The goggles carried on passing a destination name into a
 * string with nowhere to put it, {@code MessageFormat} dropped it silently, and every Astrolabe in
 * the game read "Course:" followed by nothing for weeks.
 */
class LangFormatTest {

    private static final Path SOURCE = Path.of("src", "main", "java");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "aerowarptics",
            "lang", "en_us.json");

    private static final String PREFIX = "aerowarptics.";

    /**
     * A call with at least one argument: {@code AWLang.translate("key", something)}.
     *
     * <p>Only the opening of the argument list is matched, because a full parse of a Java argument
     * list with a regex is a losing game - and the count is taken separately by balancing brackets.
     */
    private static final Pattern WITH_ARGS =
            Pattern.compile("AWLang\\.(?:translate|component)\\(\\s*\"([^\"\\\\]+)\"\\s*,");

    private static JsonObject lang() {
        try {
            return JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** How many {@code %s}-style placeholders a translation has. */
    private static int placeholders(String value) {
        int count = 0;
        Matcher matcher = Pattern.compile("%(?:\\d+\\$)?[sd]").matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * How many arguments a call passes, counted by walking the argument list.
     *
     * <p>Commas nested inside brackets or quotes belong to an inner call, not to this one, so they
     * are skipped rather than counted - otherwise a perfectly ordinary
     * {@code translate("k", other(a, b))} would look like two arguments.
     */
    private static int argumentsAfter(String text, int from) {
        int depth = 0;
        int count = 1;
        boolean inString = false;
        for (int index = from; index < text.length(); index++) {
            char c = text.charAt(index);
            if (inString) {
                if (c == '\\') {
                    index++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '(', '[' -> depth++;
                case ')' -> {
                    if (depth == 0) {
                        return count;
                    }
                    depth--;
                }
                case ']' -> depth--;
                case ',' -> {
                    if (depth == 0) {
                        count++;
                    }
                }
                default -> {
                }
            }
        }
        return count;
    }

    @Test
    void everyTranslationHasRoomForItsArguments() {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                Matcher matcher = WITH_ARGS.matcher(text);
                while (matcher.find()) {
                    String key = PREFIX + matcher.group(1);
                    if (!lang.has(key)) {
                        // LangCoverageTest owns missing keys; nothing to add here.
                        continue;
                    }
                    String value = lang.get(key).getAsString();
                    int slots = placeholders(value);
                    int given = argumentsAfter(text, matcher.end());
                    if (slots < given) {
                        problems.add(file.getFileName() + ": " + key + " is \"" + value
                                + "\" with " + slots + " placeholder(s), but is passed "
                                + given + " argument(s) - the extras are dropped silently");
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Nothing asks for a placeholder it never fills.
     *
     * <p>The mirror of the above, and the louder failure of the two: an unfilled {@code %s} renders
     * as itself, so a player sees the raw token rather than merely missing a word.
     */
    @Test
    void nothingLeavesAPlaceholderUnfilled() {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();

        Pattern anyCall = Pattern.compile("AWLang\\.(?:translate|component)\\(\\s*\"([^\"\\\\]+)\"");
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                Matcher matcher = anyCall.matcher(text);
                while (matcher.find()) {
                    String key = PREFIX + matcher.group(1);
                    if (!lang.has(key)) {
                        continue;
                    }
                    int slots = placeholders(lang.get(key).getAsString());
                    if (slots == 0) {
                        continue;
                    }
                    // A call with no comma straight after the key passes nothing at all.
                    int after = matcher.end();
                    boolean hasArgs = after < text.length()
                            && text.substring(after).stripLeading().startsWith(",");
                    int given = hasArgs
                            ? argumentsAfter(text, text.indexOf(',', after) + 1)
                            : 0;
                    if (given < slots) {
                        problems.add(file.getFileName() + ": " + key + " has " + slots
                                + " placeholder(s) but is given " + given
                                + " - the rest render as literal %s to the player");
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Every entry that ships is at least well-formed as a format string. */
    @Test
    void noTranslationHasAStrayPercent() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : lang().entrySet()) {
            String value = entry.getValue().getAsString();
            Matcher stray = Pattern.compile("%(?![sd%]|\\d+\\$[sd])").matcher(value);
            if (stray.find()) {
                problems.add(entry.getKey() + " = \"" + value + "\" has a stray %");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
