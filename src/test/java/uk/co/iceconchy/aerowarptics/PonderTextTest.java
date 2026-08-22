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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the Ponder scenes and the language file in step.
 *
 * <p>This is guarding a failure that is invisible to the compiler and loud to the player. Ponder
 * looks scene text up through {@code I18n} and does not fall back to the string written in the
 * storyboard, so a missing entry renders as {@code aerowarptics.ponder.rift_gate.text_3} in the
 * middle of a tutorial.
 *
 * <p>And the keys are positional: {@code text_1}, {@code text_2} and so on are handed out in the
 * order the {@code .text(...)} calls run. Adding a sentence to the middle of a scene renumbers
 * every sentence after it, so the wrong text under the wrong picture is a one-line change away.
 * {@code tools/ponder_lang.py} regenerates the entries; this fails the build when nobody has.
 */
class PonderTextTest {

    private static final Path SCENES =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/compat/ponder/scene");
    private static final Path REGISTRATION =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/compat/ponder/AWPonderScenes.java");
    private static final Path LANG =
            Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");
    private static final Path SCHEMATICS =
            Path.of("src/main/resources/assets/aerowarptics/ponder");

    private static final String PREFIX = "aerowarptics.ponder.";

    /** Matches either a scene title or a line of scene text, so one pass reads them in program order. */
    private static final Pattern ENTRY = Pattern.compile(
            "scene\\.title\\(\\s*\"([^\"\\\\]+)\"\\s*,\\s*\"([^\"\\\\]+)\"\\s*\\)"
                    + "|\\.text\\(\\s*\"([^\"\\\\]+)\"\\s*\\)");

    private static final Pattern STORY_BOARD =
            Pattern.compile("addStoryBoard\\(\\s*\"([^\"\\\\]+)\"");

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The keys the storyboards imply, in the order Ponder would hand them out. */
    private static Map<String, String> expected() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (Path source : sources()) {
            Matcher matcher = ENTRY.matcher(read(source));
            String scene = null;
            int index = 0;
            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    scene = matcher.group(1);
                    index = 0;
                    entries.put(PREFIX + scene + ".header", matcher.group(2));
                } else {
                    assertTrue(scene != null,
                            source.getFileName() + " has scene text before any scene.title(...)");
                    entries.put(PREFIX + scene + ".text_" + (++index), matcher.group(3));
                }
            }
        }
        return entries;
    }

    private static List<Path> sources() {
        try (Stream<Path> stream = Files.list(SCENES)) {
            List<Path> found = new ArrayList<>(stream.filter(p -> p.toString().endsWith(".java")).sorted().toList());
            assertTrue(!found.isEmpty(), "no ponder scene sources found under " + SCENES);
            return found;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static JsonObject lang() {
        try (Reader reader = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + LANG, e);
        }
    }

    @Test
    void everyLineOfSceneTextIsTranslated() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected().entrySet()) {
            if (!lang.has(entry.getKey())) {
                missing.add(entry.getKey() + "  =  " + entry.getValue());
            }
        }
        if (!missing.isEmpty()) {
            fail("Ponder would show these as raw keys. Run tools/ponder_lang.py.\n  "
                    + String.join("\n  ", missing));
        }
    }

    /**
     * The half that catches renumbering.
     *
     * <p>A sentence added to the middle of a scene shifts every later key by one, and every key still
     * exists - so only comparing the values against the source catches it.
     */
    @Test
    void translatedTextMatchesTheSceneItBelongsTo() {
        JsonObject lang = lang();
        for (Map.Entry<String, String> entry : expected().entrySet()) {
            if (lang.has(entry.getKey())) {
                assertEquals(entry.getValue(), lang.get(entry.getKey()).getAsString(),
                        entry.getKey() + " has drifted from the storyboard. Run tools/ponder_lang.py.");
            }
        }
    }

    /** Entries for scenes that no longer exist would sit in the file forever, unnoticed. */
    @Test
    void nothingIsTranslatedThatNoSceneShows() {
        Map<String, String> expected = expected();
        List<String> orphans = new ArrayList<>();
        for (String key : lang().keySet()) {
            if (key.startsWith(PREFIX) && !expected.containsKey(key)) {
                orphans.add(key);
            }
        }
        assertTrue(orphans.isEmpty(),
                "these translations belong to no scene. Run tools/ponder_lang.py.\n  "
                        + String.join("\n  ", orphans));
    }

    /**
     * A scene whose schematic is missing plays as an empty room with text over it - no crash, no
     * warning, just a lesson about nothing.
     */
    @Test
    void everySceneHasItsSchematic() {
        Matcher matcher = STORY_BOARD.matcher(read(REGISTRATION));
        int found = 0;
        while (matcher.find()) {
            found++;
            Path schematic = SCHEMATICS.resolve(matcher.group(1) + ".nbt");
            assertTrue(Files.isRegularFile(schematic),
                    "scene \"" + matcher.group(1) + "\" has no schematic at " + schematic
                            + ". Run tools/ponder_schematics.py.");
        }
        assertTrue(found > 0, "no storyboards found in " + REGISTRATION);
    }

    /** Every scene registered is a scene that was written, and the reverse. */
    @Test
    void everySchematicIsUsedByAScene() {
        Matcher matcher = STORY_BOARD.matcher(read(REGISTRATION));
        List<String> used = new ArrayList<>();
        while (matcher.find()) {
            used.add(matcher.group(1) + ".nbt");
        }
        try (Stream<Path> stream = Files.list(SCHEMATICS)) {
            for (Path schematic : stream.toList()) {
                String name = schematic.getFileName().toString();
                assertTrue(used.contains(name),
                        name + " is not used by any scene - delete it, or register a storyboard for it.");
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
