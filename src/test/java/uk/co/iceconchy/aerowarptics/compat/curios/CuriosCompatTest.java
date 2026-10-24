package uk.co.iceconchy.aerowarptics.compat.curios;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Curios stays optional: only two classes name it, and nothing reaches them except through
 * {@link CuriosBridge}. And the data that lets Rift Goggles into a Curios head slot is where Curios
 * looks for it.
 */
class CuriosCompatTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");
    private static final Path DATA = Path.of("src/main/resources/data");

    /** The only files that may import Curios; the game would fail to start without it otherwise. */
    private static final Set<String> MAY_NAME_CURIOS = Set.of("CuriosSlots.java", "CuriosClient.java");

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static boolean contains(JsonArray array, String value) {
        for (var element : array) {
            if (element.getAsString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void onlyTheCompatClassesNameCurios() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !MAY_NAME_CURIOS.contains(path.getFileName().toString()))
                    .filter(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8).contains("top.theillusivec4");
                        } catch (IOException e) {
                            throw new AssertionError("could not read " + path, e);
                        }
                    })
                    .toList();
        }
        assertEquals(List.of(), offenders, "these name Curios outside compat/curios");
    }

    @Test
    void theCompatClassesAreReachedOnlyThroughTheBridge() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getParent().endsWith(Path.of("compat", "curios")))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            return source.contains("CuriosSlots")
                                    || source.contains("CuriosClient") && !guardedByBridge(source);
                        } catch (IOException e) {
                            throw new AssertionError("could not read " + path, e);
                        }
                    })
                    .toList();
        }
        assertEquals(List.of(), offenders, "these reach Curios without asking CuriosBridge.loaded() first");
    }

    /** The client hook is allowed only where the same file asks whether Curios is there. */
    private static boolean guardedByBridge(String source) {
        return source.contains("CuriosBridge.loaded()");
    }

    @Test
    void withoutCuriosNobodyWearsAnythingInASlot() {
        // No mod list in a unit test - the same answer as a game without Curios.
        assertFalse(CuriosBridge.loaded());
        assertFalse(CuriosBridge.wears(null, null));
    }

    @Test
    void riftGogglesAreTaggedForTheHeadSlot() {
        JsonObject tag = read(DATA.resolve("curios/tags/item/head.json"));
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(),
                "replacing curios:head would throw out every other mod's head curios");
        assertTrue(contains(tag.getAsJsonArray("values"), "aerowarptics:rift_goggles"));
    }

    @Test
    void playersHaveAHeadSlot() {
        // Create assigns one too, but the goggles should not rely on another mod's data to fit.
        JsonObject slots = read(DATA.resolve("aerowarptics/curios/entities/players.json"));
        assertTrue(contains(slots.getAsJsonArray("entities"), "minecraft:player"));
        assertTrue(contains(slots.getAsJsonArray("slots"), "head"));
    }
}
