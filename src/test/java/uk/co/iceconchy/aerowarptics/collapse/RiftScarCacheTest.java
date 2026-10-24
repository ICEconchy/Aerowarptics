package uk.co.iceconchy.aerowarptics.collapse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Relic Armillary is found, never made, and this holds the world to handing it out: a Collapse Cradle cannot
 * be built without one, so a ruin that quietly lost its cache, or a cache whose loot table no longer names it,
 * is a Singularity nobody can reach - and nothing about it throws.
 */
class RiftScarCacheTest {

    private static final Path DATA = Path.of("src/main/resources/data/aerowarptics");
    private static final String CACHE = "aerowarptics:chests/rift_scar_cache";
    private static final String RELIC = "aerowarptics:relic_armillary";

    /** Every kind of ruin a Scar Chart can lead to has a barrel in it that rolls the cache. */
    @Test
    void everyRuinHasACache() throws IOException {
        List<Path> templates;
        try (var stream = Files.list(DATA.resolve("structure/rift_scar"))) {
            templates = stream.filter(path -> path.toString().endsWith(".nbt")).sorted().toList();
        }
        assertTrue(templates.size() >= 3, "fewer ruins than expected: " + templates);
        List<String> problems = new ArrayList<>();
        for (Path template : templates) {
            CompoundTag root = NbtIo.readCompressed(template, NbtAccounter.unlimitedHeap());
            ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
            int caches = 0;
            for (Tag element : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) element;
                String name = palette.getCompound(block.getInt("state")).getString("Name");
                if (name.equals("minecraft:barrel") && CACHE.equals(block.getCompound("nbt").getString("LootTable"))) {
                    caches++;
                }
            }
            if (caches != 1) {
                problems.add(template.getFileName() + " has " + caches + " cache(s)");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The cache can hold the armillary, on its own roll so the scrap never crowds it out. */
    @Test
    void theCacheCanHoldTheArmillary() {
        JsonObject table = read(DATA.resolve("loot_table/chests/rift_scar_cache.json"));
        boolean found = false;
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            JsonObject entries = pool.getAsJsonObject();
            for (JsonElement entry : entries.getAsJsonArray("entries")) {
                if (RELIC.equals(entry.getAsJsonObject().get("name").getAsString())) {
                    assertEquals(1, entries.getAsJsonArray("entries").size(),
                            "the armillary shares its roll with something else");
                    found = true;
                }
            }
        }
        assertTrue(found, "the cache never holds a Relic Armillary");
    }

    /** And a cradle cannot be built without one, which is the point of finding it. */
    @Test
    void aCradleTakesTheArmillary() {
        JsonObject recipe = read(DATA.resolve("recipe/collapse_cradle.json"));
        boolean asked = false;
        for (var key : recipe.getAsJsonObject("key").entrySet()) {
            asked |= RELIC.equals(key.getValue().getAsJsonObject().get("item").getAsString());
        }
        assertTrue(asked, "the Collapse Cradle's recipe does not ask for a Relic Armillary");
    }

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
