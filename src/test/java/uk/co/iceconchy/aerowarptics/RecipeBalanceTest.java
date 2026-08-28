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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the crafting progression to a shape a player can actually read.
 *
 * <p>Balance is usually a matter of taste, but some of it is arithmetic, and the arithmetic is worth
 * a test. An earlier version of these recipes asked for <em>four complete Mk III drives</em> and five
 * Singularity Cores to build one Singularity, which cascaded to something in the region of thirty-six
 * Refined Radiance. Nobody wrote that on purpose; it is what happens when a five-by-five pattern is
 * drawn for how it looks and never counted up.
 *
 * <p>So the ladder is asserted rather than eyeballed: each tier consumes exactly one of the tier below,
 * and the whole cost of building one roughly doubles each step. Costs are weighted by rough crafting
 * effort rather than counted, because a Refined Radiance and a brass sheet are not the same thing and
 * a plain item count would let the top of the tree quietly deflate.
 */
class RecipeBalanceTest {

    private static final Path RECIPES = Path.of("src/main/resources/data/aerowarptics/recipe");

    /**
     * Roughly what each raw ingredient costs to get hold of, relative to a brass sheet.
     *
     * <p>Deliberately coarse. It exists to stop the ladder drifting, not to model the economy - the
     * ratios between tiers are what matter, and those barely move if any one of these is a little off.
     * Anything reachable in a recipe must appear here, so adding a new ingredient is a decision rather
     * than an accident.
     */
    private static final Map<String, Double> EFFORT = Map.ofEntries(
            Map.entry("minecraft:book", 3.0D),
            Map.entry("minecraft:glass", 0.5D),
            Map.entry("minecraft:amethyst_shard", 1.0D),
            Map.entry("minecraft:lodestone", 12.0D),
            Map.entry("minecraft:obsidian", 2.0D),
            Map.entry("create:brass_sheet", 1.0D),
            Map.entry("create:brass_ingot", 1.0D),
            Map.entry("create:cogwheel", 1.0D),
            Map.entry("create:goggles", 6.0D),
            Map.entry("create:shaft", 1.0D),
            Map.entry("create:andesite_alloy", 1.0D),
            Map.entry("create:copper_casing", 3.0D),
            Map.entry("create:brass_casing", 4.0D),
            Map.entry("create:railway_casing", 12.0D),
            Map.entry("create:electron_tube", 4.0D),
            Map.entry("create:precision_mechanism", 8.0D),
            Map.entry("create:refined_radiance", 20.0D),
            Map.entry("create:shadow_steel", 20.0D),
            Map.entry("aeronautics:end_stone_powder", 1.0D),
            Map.entry("aeronautics:levitite", 2.0D),
            Map.entry("aeronautics:pearlescent_levitite", 8.0D));

    /** The drive ladder, in order. Each is expected to be built from the one before it. */
    private static final List<String> TIERS = List.of(
            "aerowarptics:rift_drive_mk_i",
            "aerowarptics:rift_drive_mk_ii",
            "aerowarptics:rift_drive_mk_iii",
            "aerowarptics:rift_drive_singularity");

    /** Everything this mod expects to be craftable one way or another. */
    private static final List<String> CRAFTABLE = List.of(
            "aerowarptics:handbook",
            "aerowarptics:rift_goggles",
            "aerowarptics:rift_lens",
            "aerowarptics:rift_core",
            "aerowarptics:stabiliser_ring",
            "aerowarptics:singularity_core",
            "aerowarptics:warp_anchor",
            "aerowarptics:astrolabe",
            "aerowarptics:spatial_siphon",
            "aerowarptics:rift_gate",
            "aerowarptics:rift_gate_frame",
            "aerowarptics:rift_drive_mk_i",
            "aerowarptics:rift_drive_mk_ii",
            "aerowarptics:rift_drive_mk_iii",
            "aerowarptics:rift_drive_singularity");

    // ------------------------------------------------------------------ loading

    /** What one of each output takes, as ingredient id to count. */
    private static Map<String, Map<String, Integer>> load() {
        Map<String, Map<String, Integer>> recipes = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(RECIPES)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = read(path);
                recipes.put(output(json), ingredients(json, path));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return recipes;
    }

    private static String output(JsonObject json) {
        if (json.has("result")) {
            return json.getAsJsonObject("result").get("id").getAsString();
        }
        return json.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString();
    }

    private static Map<String, Integer> ingredients(JsonObject json, Path path) {
        Map<String, Integer> counts = new HashMap<>();
        if (json.has("pattern")) {
            JsonObject key = json.getAsJsonObject("key");
            for (JsonElement row : json.getAsJsonArray("pattern")) {
                for (char symbol : row.getAsString().toCharArray()) {
                    if (symbol == ' ') {
                        continue;
                    }
                    JsonElement entry = key.get(String.valueOf(symbol));
                    assertTrue(entry != null, path + ": pattern uses '" + symbol + "' with no key");
                    counts.merge(entry.getAsJsonObject().get("item").getAsString(), 1, Integer::sum);
                }
            }
            return counts;
        }
        JsonArray list = json.getAsJsonArray("ingredients");
        for (JsonElement element : list) {
            counts.merge(element.getAsJsonObject().get("item").getAsString(), 1, Integer::sum);
        }
        return counts;
    }

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** What one of something costs all the way down to raw ingredients. */
    private static double effort(String id, Map<String, Map<String, Integer>> recipes, int depth) {
        Double raw = EFFORT.get(id);
        if (raw != null) {
            return raw;
        }
        assertTrue(depth < 12, "recipes for " + id + " appear to be circular");
        Map<String, Integer> recipe = recipes.get(id);
        assertTrue(recipe != null, id + " is used in a recipe but nothing makes it and it has no "
                + "entry in the effort table - add one or fix the reference");
        double total = 0.0D;
        for (Map.Entry<String, Integer> ingredient : recipe.entrySet()) {
            total += effort(ingredient.getKey(), recipes, depth + 1) * ingredient.getValue();
        }
        return total;
    }

    // -------------------------------------------------------------------- tests

    @Test
    void everythingTheModAddsCanBeMade() {
        Map<String, Map<String, Integer>> recipes = load();
        List<String> missing = new ArrayList<>();
        for (String id : CRAFTABLE) {
            if (!recipes.containsKey(id)) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "no recipe for " + missing);
    }

    @Test
    void everyIngredientIsSomethingWeCanPrice() {
        // Reaching an unknown ingredient means either a typo in an item id or a new one nobody has
        // decided the cost of. Both are worth failing over.
        Map<String, Map<String, Integer>> recipes = load();
        for (String id : recipes.keySet()) {
            effort(id, recipes, 0);
        }
    }

    @Test
    void eachDriveTierIsBuiltFromExactlyOneOfTheTierBelow() {
        Map<String, Map<String, Integer>> recipes = load();
        for (int tier = 1; tier < TIERS.size(); tier++) {
            String previous = TIERS.get(tier - 1);
            String current = TIERS.get(tier);
            Integer used = recipes.get(current).get(previous);
            assertEquals(Integer.valueOf(1), used,
                    current + " should be an upgrade of one " + previous + ", not " + used);
        }
    }

    @Test
    void theLadderRoughlyDoublesEachStep() {
        Map<String, Map<String, Integer>> recipes = load();
        double previous = effort(TIERS.get(0), recipes, 0);
        for (int tier = 1; tier < TIERS.size(); tier++) {
            double current = effort(TIERS.get(tier), recipes, 0);
            double step = current / previous;
            assertTrue(step >= 1.7D && step <= 3.0D, String.format(
                    "%s costs %.0f against %.0f for the tier below - a step of x%.2f, which is %s",
                    TIERS.get(tier), current, previous, step,
                    step < 1.7D ? "barely an upgrade" : "a wall"));
            previous = current;
        }
    }

    @Test
    void anAnchorIsCheapEnoughToBuildANetworkOf() {
        // Anchors are placed in numbers; a warp network of twenty should be a project, not a wall.
        // The expensive component belongs in the drive that reaches them, not in every destination.
        Map<String, Map<String, Integer>> recipes = load();
        double anchor = effort("aerowarptics:warp_anchor", recipes, 0);
        double drive = effort("aerowarptics:rift_drive_mk_i", recipes, 0);
        assertTrue(anchor < drive * 0.5D, String.format(
                "an anchor costs %.0f against %.0f for the cheapest drive - too much for scenery",
                anchor, drive));
    }

    @Test
    void aFullChartTableCostsLessThanTheDriveItSteers() {
        // Nine blocks make one table, so the interesting number is nine crafts' worth - and a craft
        // makes three. A chart room should be an afternoon's brass, not a second drive.
        Map<String, Map<String, Integer>> recipes = load();
        double perCraft = effort("aerowarptics:astrolabe", recipes, 0);
        double table = perCraft * 3.0D;
        double drive = effort("aerowarptics:rift_drive_mk_i", recipes, 0);
        assertTrue(table < drive, String.format(
                "a whole table costs %.0f against %.0f for the cheapest drive - too much for furniture",
                table, drive));
    }

    @Test
    void theTopTierIsExpensiveWithoutBeingAbsurd() {
        // The old Singularity recipe expanded to well over ten thousand once its four Mk III drives
        // and five Singularity Cores were followed down. This is the guard against that returning.
        Map<String, Map<String, Integer>> recipes = load();
        double singularity = effort("aerowarptics:rift_drive_singularity", recipes, 0);
        assertTrue(singularity > 300.0D, "the endgame drive should be a serious build: " + singularity);
        assertTrue(singularity < 1_500.0D, "the endgame drive has run away: " + singularity);
    }
}
