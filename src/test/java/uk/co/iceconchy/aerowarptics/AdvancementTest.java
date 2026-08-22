package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the advancement tree against the ways it fails silently.
 *
 * <p>An advancement with a typo in it does not crash anything. A criterion naming a trigger that was
 * never registered simply never fires, a parent pointing at nothing quietly drops a whole branch out
 * of the screen, and a missing title renders as {@code advancements.aerowarptics.long_haul.title}.
 * All three ship happily and are only noticed by a player wondering why nothing happened.
 *
 * <p>{@code tools/advancements.py} writes the tree; this checks the result hangs together.
 */
class AdvancementTest {

    private static final Path ADVANCEMENTS = Path.of("src/main/resources/data/aerowarptics/advancement");
    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");
    private static final Path ITEM_MODELS = Path.of("src/main/resources/assets/aerowarptics/models/item");
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path CRITERIA =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/advancement/AWCriteria.java");

    private static final String MOD = "aerowarptics";

    /** Trigger ids the mod actually registers, read from the registrations themselves. */
    private static final Pattern REGISTERED = Pattern.compile("TRIGGERS\\.register\\(\\s*\"([^\"]+)\"");

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static Map<String, JsonObject> tree() {
        Map<String, JsonObject> found = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(ADVANCEMENTS)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = path.getFileName().toString().replace(".json", "");
                found.put(name, read(path));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(!found.isEmpty(), "no advancements found under " + ADVANCEMENTS);
        return found;
    }

    private static Set<String> registeredTriggers() {
        try {
            Matcher matcher = REGISTERED.matcher(Files.readString(CRITERIA, StandardCharsets.UTF_8));
            Set<String> names = new HashSet<>();
            while (matcher.find()) {
                names.add(MOD + ":" + matcher.group(1));
            }
            assertTrue(!names.isEmpty(), "no triggers registered in " + CRITERIA);
            return names;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyAdvancementIsShaped() {
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            JsonObject advancement = entry.getValue();
            assertTrue(advancement.has("display"), entry.getKey() + " has no display block");
            assertTrue(advancement.has("criteria"), entry.getKey() + " has no criteria");
            assertTrue(advancement.has("requirements"), entry.getKey() + " has no requirements");

            Set<String> criteria = advancement.getAsJsonObject("criteria").keySet();
            assertTrue(!criteria.isEmpty(), entry.getKey() + " has an empty criteria block");

            // A requirement naming a criterion that is not there can never be satisfied.
            for (JsonElement group : advancement.getAsJsonArray("requirements")) {
                for (JsonElement required : group.getAsJsonArray()) {
                    assertTrue(criteria.contains(required.getAsString()),
                            entry.getKey() + " requires \"" + required.getAsString()
                                    + "\", which is not one of its criteria " + criteria);
                }
            }
        }
    }

    /** Exactly one root, and every other advancement hanging off something that exists. */
    @Test
    void theTreeIsConnected() {
        Map<String, JsonObject> tree = tree();
        List<String> roots = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : tree.entrySet()) {
            JsonObject advancement = entry.getValue();
            if (!advancement.has("parent")) {
                roots.add(entry.getKey());
                assertTrue(advancement.getAsJsonObject("display").has("background"),
                        entry.getKey() + " is a root advancement but has no background texture");
                continue;
            }
            String parent = advancement.get("parent").getAsString();
            assertTrue(parent.startsWith(MOD + ":"),
                    entry.getKey() + " hangs off \"" + parent + "\", which is not this mod's");
            assertTrue(tree.containsKey(parent.substring(MOD.length() + 1)),
                    entry.getKey() + " hangs off \"" + parent + "\", which does not exist");
        }
        assertEquals(List.of("root"), roots, "expected exactly one root advancement");
    }

    /**
     * The one a compiler could never catch: a criterion naming a trigger this mod does not register
     * loads without complaint and then never fires.
     */
    @Test
    void everyCustomTriggerIsRegistered() {
        Set<String> registered = registeredTriggers();
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            JsonObject criteria = entry.getValue().getAsJsonObject("criteria");
            for (String name : criteria.keySet()) {
                String trigger = criteria.getAsJsonObject(name).get("trigger").getAsString();
                if (!trigger.startsWith(MOD + ":")) {
                    continue; // vanilla's problem, not ours
                }
                assertTrue(registered.contains(trigger),
                        entry.getKey() + "/" + name + " fires on \"" + trigger
                                + "\", which this mod never registers. Known: " + registered);
            }
        }
    }

    @Test
    void everyTitleAndDescriptionIsTranslated() {
        JsonObject lang = read(LANG);
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            JsonObject display = entry.getValue().getAsJsonObject("display");
            for (String field : List.of("title", "description")) {
                String key = display.getAsJsonObject(field).get("translate").getAsString();
                assertTrue(lang.has(key),
                        entry.getKey() + " has no " + field + " for \"" + key
                                + "\". Run tools/advancements.py.");
            }
        }
    }

    /** An icon naming an item that does not exist draws as the missing-model cube. */
    @Test
    void everyIconIsAnItemThisModHas() {
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            String icon = entry.getValue().getAsJsonObject("display")
                    .getAsJsonObject("icon").get("id").getAsString();
            if (!icon.startsWith(MOD + ":")) {
                continue;
            }
            Path model = ITEM_MODELS.resolve(icon.substring(MOD.length() + 1) + ".json");
            assertTrue(Files.isRegularFile(model),
                    entry.getKey() + " uses icon \"" + icon + "\", which has no item model at " + model);
        }
    }

    @Test
    void everyBackgroundTextureExists() {
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            JsonObject display = entry.getValue().getAsJsonObject("display");
            if (!display.has("background")) {
                continue;
            }
            String background = display.get("background").getAsString();
            String[] split = background.split(":", 2);
            assertEquals(2, split.length, entry.getKey() + " has an unnamespaced background");
            if (!split[0].equals(MOD)) {
                continue;
            }
            Path texture = RESOURCES.resolve("assets").resolve(split[0]).resolve(split[1]);
            assertTrue(Files.isRegularFile(texture),
                    entry.getKey() + " uses background \"" + background + "\", missing at " + texture);
        }
    }

    /** Translations for advancements that were renamed or deleted would linger unnoticed. */
    @Test
    void nothingIsTranslatedThatNoAdvancementUses() {
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            JsonObject display = entry.getValue().getAsJsonObject("display");
            for (String field : List.of("title", "description")) {
                used.add(display.getAsJsonObject(field).get("translate").getAsString());
            }
        }
        List<String> orphans = new ArrayList<>();
        for (String key : read(LANG).keySet()) {
            if (key.startsWith("advancements." + MOD + ".") && !used.contains(key)) {
                orphans.add(key);
            }
        }
        assertTrue(orphans.isEmpty(),
                "these belong to no advancement. Run tools/advancements.py.\n  "
                        + String.join("\n  ", orphans));
    }

    /** Unused JSON in the folder is either a mistake or a leftover; both are worth noticing. */
    @Test
    void everyAdvancementIsReachableFromTheRoot() {
        Map<String, JsonObject> tree = tree();
        Set<String> reachable = new HashSet<>();
        for (String name : tree.keySet()) {
            List<String> chain = new ArrayList<>();
            String current = name;
            while (current != null && !reachable.contains(current)) {
                assertTrue(!chain.contains(current), "advancements form a cycle: " + chain);
                chain.add(current);
                JsonObject advancement = tree.get(current);
                current = advancement.has("parent")
                        ? advancement.get("parent").getAsString().substring(MOD.length() + 1)
                        : null;
            }
            reachable.addAll(chain);
        }
        assertEquals(tree.keySet(), reachable);
    }

    /** JEI shows one of these per item; a missing key renders the raw string in the tooltip pane. */
    @Test
    void everyJeiDescriptionIsTranslated() {
        JsonObject lang = read(LANG);
        List<String> keys = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (key.startsWith(MOD + ".jei.info.")) {
                keys.add(key);
            }
        }
        assertTrue(keys.size() >= 10,
                "expected a JEI description for each machine and component, found " + keys);
        for (String key : keys) {
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
        }
    }

    /** JSON in this folder that no other file links to. */
    @Test
    void everyAdvancementFileIsJson() {
        try (Stream<Path> stream = Files.list(ADVANCEMENTS)) {
            for (Path path : stream.toList()) {
                assertTrue(path.toString().endsWith(".json"),
                        path + " is not an advancement file");
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Requirements must not be empty, or the advancement is unearnable. */
    @Test
    void noAdvancementIsImpossible() {
        for (Map.Entry<String, JsonObject> entry : tree().entrySet()) {
            JsonArray requirements = entry.getValue().getAsJsonArray("requirements");
            assertTrue(!requirements.isEmpty(), entry.getKey() + " has no requirements at all");
            for (JsonElement group : requirements) {
                assertTrue(!group.getAsJsonArray().isEmpty(),
                        entry.getKey() + " has an empty requirement group, which can never be met");
            }
        }
    }
}
