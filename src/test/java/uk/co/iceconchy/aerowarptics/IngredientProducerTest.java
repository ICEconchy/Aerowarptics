package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ingredient in every recipe can be got hold of somehow.
 *
 * <p>This is the guard {@code RecipeBalanceTest} cannot be. That test asks whether an ingredient has a
 * price; a price is a number somebody typed, and an item with a price and no way to obtain it is exactly the
 * mistake that shipped twice. Create's chromatic line (shadow steel, refined radiance) has no creation path
 * left in Create 6, and Aeronautics' levitite has no item form at all - both were in this mod's recipes,
 * both priced, and both made the drive ladder a dead end after the Mk II.
 *
 * <p>So an ingredient passes only if something produces it: a recipe of any type, in this mod or any other
 * on the classpath; a loot table - a block's drop, a mob's, a chest's; or one of the few things this mod
 * declares the world produces directly. Ids from another mod are checked against that mod's own jar, and
 * skipped - with a note in the output - when its jar is not in {@code Depends/}, as the Dimensional Sable
 * test is.
 */
class IngredientProducerTest {

    /**
     * What this mod's world makes, rather than a recipe or a loot table.
     *
     * <p>Each is a decision that something is obtainable, written down where it can be argued with.
     */
    private static final Map<String, String> WORLD_SOURCES = Map.of(
            "aerowarptics:rift_essence", "caught by a Spatial Siphon from a warp, a fissure or a storm's rain",
            "aerowarptics:scar_heart", "dropped by a Rift Fissure as it seals",
            "aerowarptics:relic_armillary", "found in the caches left in rift scars' ruins",
            "aerowarptics:stormglass", "an Unset Stormglass setting in a Rift Storm");

    @Test
    void everyIngredientHasAProducer() {
        Producers producers = Producers.scan();
        Set<String> wanted = new TreeSet<>();
        Set<String> tags = new TreeSet<>();
        for (RecipeBook book : List.of(RecipeBook.standard(), RecipeBook.classic())) {
            for (RecipeBook.Recipe recipe : book.all()) {
                wanted.addAll(recipe.inputs());
                tags.addAll(recipe.tags().keySet());
            }
        }

        List<String> problems = new ArrayList<>();
        Set<String> skipped = new TreeSet<>();
        for (String id : wanted) {
            if (WORLD_SOURCES.containsKey(id) || producers.outputs.contains(id)) {
                continue;
            }
            String namespace = id.substring(0, id.indexOf(':'));
            if (!namespace.equals("aerowarptics") && !producers.namespaces.contains(namespace)) {
                skipped.add(id);
                continue;
            }
            problems.add(id + " is asked for, but no recipe, loot table or world source produces it");
        }
        for (String tag : tags) {
            if (!producers.tags.contains(tag)) {
                problems.add("#" + tag + " is asked for, but no tag file defines it");
            }
        }
        if (!skipped.isEmpty()) {
            System.out.println("IngredientProducerTest: no jar on the classpath for " + skipped + " - not checked");
        }
        assertTrue(producers.outputs.size() > 1_000,
                "only " + producers.outputs.size() + " produced things found - is vanilla's data on the classpath?");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Declared world sources have to be things the mod actually has, or the declaration is a hole. */
    @Test
    void everyDeclaredWorldSourceIsSomethingTheModHas() throws IOException {
        String items = Files.readString(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/registry/AWItems.java"));
        String fluids = Files.readString(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/registry/AWFluids.java"));
        for (String id : WORLD_SOURCES.keySet()) {
            String path = id.substring(id.indexOf(':') + 1);
            assertTrue(items.contains("\"" + path + "\"") || fluids.contains("\"" + path + "\""),
                    id + " is declared a world source but the mod registers nothing by that name");
        }
    }

    // ------------------------------------------------------------------- scanning

    /** Everything produced by any recipe or loot table on the classpath, and which namespaces have data. */
    private static final class Producers {

        final Set<String> outputs = new HashSet<>();
        final Set<String> namespaces = new HashSet<>();
        final Set<String> tags = new HashSet<>();

        static Producers scan() {
            Producers producers = new Producers();
            Set<String> seen = new LinkedHashSet<>();
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
                if (entry.isBlank() || !seen.add(entry)) {
                    continue;
                }
                File file = new File(entry);
                if (file.isDirectory()) {
                    producers.scanDirectory(file.toPath());
                } else if (file.getName().endsWith(".jar") && file.isFile()) {
                    producers.scanJar(file);
                }
            }
            // This mod's own data, straight from the source tree, whatever the build put on the classpath.
            producers.scanDirectory(Path.of("src/main/resources"));
            return producers;
        }

        void scanDirectory(Path root) {
            Path data = root.resolve("data");
            if (!Files.isDirectory(data)) {
                return;
            }
            try (Stream<Path> files = Files.walk(data)) {
                for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String name = root.relativize(path).toString().replace('\\', '/');
                    try (InputStream in = Files.newInputStream(path)) {
                        read(name, in);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        void scanJar(File jar) {
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("data/") && name.endsWith(".json")) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            read(name, in);
                        }
                    }
                }
            } catch (IOException ignored) {
                // Not a readable jar: nothing of interest in it.
            }
        }

        private void read(String name, InputStream in) {
            String[] parts = name.split("/");
            if (parts.length < 4) {
                return;
            }
            String namespace = parts[1];
            String kind = parts[2];
            boolean recipe = kind.equals("recipe") || kind.equals("recipes");
            boolean loot = kind.equals("loot_table") || kind.equals("loot_tables");
            boolean tag = kind.equals("tags") && parts.length >= 5;
            if (!recipe && !loot && !tag) {
                return;
            }
            namespaces.add(namespace);
            if (tag) {
                String path = String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length));
                tags.add(namespace + ":" + path.substring(0, path.length() - ".json".length()));
                return;
            }
            JsonElement json;
            try {
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (RuntimeException malformed) {
                return;
            }
            if (recipe) {
                recipeOutputs(json);
            } else {
                lootNames(json);
            }
        }

        private void recipeOutputs(JsonElement element) {
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject json = element.getAsJsonObject();
            if (json.has("result")) {
                JsonElement result = json.get("result");
                if (result.isJsonPrimitive()) {
                    outputs.add(result.getAsString());
                } else if (result.isJsonObject()) {
                    addId(result.getAsJsonObject());
                }
            }
            if (json.has("results") && json.get("results").isJsonArray()) {
                for (JsonElement result : json.getAsJsonArray("results")) {
                    if (result.isJsonObject()) {
                        addId(result.getAsJsonObject());
                    }
                }
            }
        }

        private void addId(JsonObject result) {
            for (String key : new String[]{"id", "item", "fluid"}) {
                if (result.has(key) && result.get(key).isJsonPrimitive()) {
                    outputs.add(result.get(key).getAsString());
                }
            }
        }

        /** Every item a loot table can hand out, however deeply it is nested. */
        private void lootNames(JsonElement element) {
            if (element.isJsonObject()) {
                JsonObject json = element.getAsJsonObject();
                if (json.has("type") && json.get("type").isJsonPrimitive()
                        && json.get("type").getAsString().equals("minecraft:item")
                        && json.has("name") && json.get("name").isJsonPrimitive()) {
                    outputs.add(json.get("name").getAsString());
                }
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    lootNames(entry.getValue());
                }
            } else if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement child : array) {
                    lootNames(child);
                }
            }
        }
    }
}
