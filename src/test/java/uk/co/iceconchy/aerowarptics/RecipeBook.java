package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The mod's recipe files, read the way the recipe tests need them: what each one makes, and what it takes.
 *
 * <p>Test-only, and free of Minecraft on purpose - it reads JSON. Every shape the mod's recipes come in is
 * understood here: a crafting grid's pattern and key, a list of ingredients, a Create sequenced assembly
 * (whose base is {@code ingredient}, singular, and whose steps each add one thing per loop), and Create's
 * fluid entries, which are counted in millibuckets rather than items.
 *
 * <p>Two books can be read: the default set, and the default set with the Classic recipes pack laid over it
 * - a file in the pack replaces the file at the same path, exactly as the game resolves it.
 */
final class RecipeBook {

    static final Path RECIPES = Path.of("src/main/resources/data/aerowarptics/recipe");
    static final Path CLASSIC = Path.of("src/main/resources/datapacks/classic_recipes/data/aerowarptics/recipe");

    /**
     * One recipe file.
     *
     * @param output what one craft makes, or null for a recipe that makes no item (none do)
     * @param count  how many one craft makes
     * @param items  item ingredients, id to count, the whole craft's worth
     * @param fluids fluid ingredients, id to millibuckets
     * @param tags   tag ingredients, tag to count
     */
    record Recipe(String file, String type, String output, int count, Map<String, Integer> items,
                  Map<String, Integer> fluids, Map<String, Integer> tags, JsonObject json) {

        /** Every thing this recipe asks for, items and fluids alike. */
        List<String> inputs() {
            List<String> all = new ArrayList<>(items.keySet());
            all.addAll(fluids.keySet());
            return all;
        }
    }

    private final Map<String, Recipe> byFile;

    private RecipeBook(Map<String, Recipe> byFile) {
        this.byFile = byFile;
    }

    static RecipeBook standard() {
        return new RecipeBook(readAll(RECIPES));
    }

    /** The default set with the Classic pack laid over it. */
    static RecipeBook classic() {
        Map<String, Recipe> recipes = readAll(RECIPES);
        recipes.putAll(readAll(CLASSIC));
        return new RecipeBook(recipes);
    }

    List<Recipe> all() {
        return List.copyOf(byFile.values());
    }

    Recipe file(String name) {
        return byFile.get(name);
    }

    /** Every recipe that makes each thing - a list, because some things have more than one road. */
    Map<String, List<Recipe>> byOutput() {
        Map<String, List<Recipe>> outputs = new LinkedHashMap<>();
        for (Recipe recipe : byFile.values()) {
            if (recipe.output() != null) {
                outputs.computeIfAbsent(recipe.output(), key -> new ArrayList<>()).add(recipe);
            }
        }
        return outputs;
    }

    // ------------------------------------------------------------------ reading

    private static Map<String, Recipe> readAll(Path root) {
        Map<String, Recipe> recipes = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return recipes;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                recipes.put(name, parse(name, read(path)));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return recipes;
    }

    static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    static Recipe parse(String file, JsonObject json) {
        String type = json.get("type").getAsString();
        Map<String, Integer> items = new LinkedHashMap<>();
        Map<String, Integer> fluids = new LinkedHashMap<>();
        Map<String, Integer> tags = new LinkedHashMap<>();

        if (json.has("pattern")) {
            JsonObject key = json.getAsJsonObject("key");
            for (JsonElement row : json.getAsJsonArray("pattern")) {
                for (char symbol : row.getAsString().toCharArray()) {
                    if (symbol == ' ') {
                        continue;
                    }
                    JsonElement entry = key.get(String.valueOf(symbol));
                    if (entry == null) {
                        throw new AssertionError(file + ": pattern uses '" + symbol + "' with no key");
                    }
                    count(entry, 1, items, fluids, tags);
                }
            }
        } else if (type.equals("create:sequenced_assembly")) {
            count(json.get("ingredient"), 1, items, fluids, tags);
            int loops = json.has("loops") ? json.get("loops").getAsInt() : 1;
            String transitional = json.getAsJsonObject("transitional_item").get("id").getAsString();
            for (JsonElement step : json.getAsJsonArray("sequence")) {
                for (JsonElement ingredient : step.getAsJsonObject().getAsJsonArray("ingredients")) {
                    if (ingredient.isJsonObject() && transitional.equals(itemOf(ingredient.getAsJsonObject()))) {
                        continue;
                    }
                    count(ingredient, loops, items, fluids, tags);
                }
            }
        } else if (json.has("ingredients")) {
            for (JsonElement ingredient : json.getAsJsonArray("ingredients")) {
                count(ingredient, 1, items, fluids, tags);
            }
        }

        String output = null;
        int count = 1;
        if (json.has("result")) {
            JsonObject result = json.getAsJsonObject("result");
            output = result.get("id").getAsString();
            count = result.has("count") ? result.get("count").getAsInt() : 1;
        } else if (json.has("results")) {
            for (JsonElement element : json.getAsJsonArray("results")) {
                JsonObject result = element.getAsJsonObject();
                if (result.has("amount")) {
                    continue; // a fluid result
                }
                output = result.get("id").getAsString();
                count = result.has("count") ? result.get("count").getAsInt() : 1;
                break;
            }
        }
        return new Recipe(file, type, output, count, items, fluids, tags, json);
    }

    private static void count(JsonElement ingredient, int times, Map<String, Integer> items,
                              Map<String, Integer> fluids, Map<String, Integer> tags) {
        if (ingredient.isJsonArray()) {
            // Alternatives: any one of them will do, so the first stands for the lot.
            JsonArray alternatives = ingredient.getAsJsonArray();
            if (!alternatives.isEmpty()) {
                count(alternatives.get(0), times, items, fluids, tags);
            }
            return;
        }
        JsonObject object = ingredient.getAsJsonObject();
        if (object.has("fluid")) {
            fluids.merge(object.get("fluid").getAsString(), object.get("amount").getAsInt() * times, Integer::sum);
        } else if (object.has("item")) {
            items.merge(object.get("item").getAsString(), times, Integer::sum);
        } else if (object.has("tag")) {
            tags.merge(object.get("tag").getAsString(), times, Integer::sum);
        } else {
            throw new AssertionError("an ingredient this book cannot read: " + object);
        }
    }

    private static String itemOf(JsonObject ingredient) {
        return ingredient.has("item") ? ingredient.get("item").getAsString() : null;
    }
}
