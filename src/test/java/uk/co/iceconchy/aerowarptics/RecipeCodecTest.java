package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.collapse.CollapseRecipeParams;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crafting layer's recipe files, read through the part of each codec this mod wrote.
 *
 * <p>A recipe the game cannot parse does not crash anything: the data pack logs one line and leaves it out,
 * and the machine it belongs to simply never makes anything. The ingredients and results are Create's codec,
 * which needs a running game to resolve item ids against; which tier a collapse turns at is this mod's, and is
 * decoded here from the shipped files - so a misspelt tier fails the build rather than a pack's load. A fold asks
 * nothing of its own any more - only essence, as a fluid ingredient - which is read here too.
 */
class RecipeCodecTest {

    private static final Path RECIPES = Path.of("src/main/resources/data/aerowarptics/recipe");

    private static JsonObject shipped(String name) {
        try {
            return JsonParser.parseString(Files.readString(RECIPES.resolve(name))).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static <T> DataResult<T> decode(MapCodec<T> codec, JsonObject json) {
        return codec.decode(JsonOps.INSTANCE, JsonOps.INSTANCE.getMap(json).getOrThrow());
    }

    private static <T> T parsed(MapCodec<T> codec, JsonObject json, String name) {
        DataResult<T> result = decode(codec, json);
        assertTrue(result.isSuccess(), () -> name + " did not parse: "
                + result.error().map(Object::toString).orElse("?"));
        return result.getOrThrow();
    }

    /**
     * Every fold burns Rift Essence: the price the crucible's long jump became when folding moved to the cradle.
     * Asked of the files, one essence ingredient each, and at most the two items a cradle's arms and cup hold.
     */
    @Test
    void everyFoldBurnsEssenceAndFitsTheCradle() {
        for (String name : List.of("folded_warp_crystal.json", "pearlescent_warp_crystal.json", "ship_compass.json")) {
            JsonObject recipe = shipped(name);
            assertEquals("aerowarptics:folding", recipe.get("type").getAsString(), name);
            assertTrue(!recipe.has("reach_of") && !recipe.has("fraction"), name + " still asks for a jump");
            int items = 0;
            int essence = 0;
            for (var ingredient : recipe.getAsJsonArray("ingredients")) {
                JsonObject entry = ingredient.getAsJsonObject();
                if (entry.has("fluid")) {
                    assertEquals("aerowarptics:rift_essence", entry.get("fluid").getAsString(), name);
                    assertTrue(entry.get("amount").getAsInt() > 0, name + " burns no essence");
                    essence++;
                } else {
                    items++;
                }
            }
            assertEquals(1, essence, name + " should burn essence once");
            assertTrue(items >= 1 && items <= 2, name + " asks for " + items + " items; a cradle holds two");
        }
    }

    @Test
    void theCollapseTurnsAtTheSingularitysSpeed() {
        assertEquals(RiftDriveTier.SINGULARITY, parsed(CollapseRecipeParams.Fields.DRIVE_TIER,
                shipped("singularity_core.json"), "singularity_core.json"));
    }

    /** Every recipe file of the three new types names a type the mod registers. */
    @Test
    void everyNewTypeIsOneTheModRegisters() throws IOException {
        String types = Files.readString(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/registry/AWRecipeTypes.java"));
        for (RecipeBook.Recipe recipe : RecipeBook.standard().all()) {
            if (recipe.type().startsWith("aerowarptics:")) {
                String name = recipe.type().substring("aerowarptics:".length()).toUpperCase(java.util.Locale.ROOT);
                assertTrue(types.contains("    " + name + "("), recipe.file() + " is of type " + recipe.type()
                        + ", which AWRecipeTypes does not register");
            }
        }
    }
}
