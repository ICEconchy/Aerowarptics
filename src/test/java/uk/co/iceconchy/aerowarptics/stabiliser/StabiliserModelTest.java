package uk.co.iceconchy.aerowarptics.stabiliser;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stabiliser a player sees is the stabiliser a player can click, and the switch shows.
 *
 * <p>The model is the artist's and the hit box is written by hand in
 * {@link SpatialStabiliserBlock}. Nothing ties the two together, and a mismatch is silent: a cap you can
 * see but not target, or an outline floating a pixel off the drum. Read from the files rather than the
 * block class, which cannot be loaded without a bootstrapped Minecraft.
 */
class StabiliserModelTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/aerowarptics");
    private static final Path BLOCK_SOURCE =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/stabiliser/SpatialStabiliserBlock.java");

    private static final Pattern BOX = Pattern.compile(
            "Block\\.box\\(\\s*([\\d.]+)D?,\\s*([\\d.]+)D?,\\s*([\\d.]+)D?,\\s*([\\d.]+)D?,\\s*([\\d.]+)D?,\\s*([\\d.]+)D?\\)");

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static List<List<Double>> modelBoxes(String model) {
        List<List<Double>> boxes = new ArrayList<>();
        for (JsonElement element : read(ASSETS.resolve("models/block/" + model + ".json")).getAsJsonArray("elements")) {
            JsonObject cube = element.getAsJsonObject();
            List<Double> box = new ArrayList<>();
            for (String corner : List.of("from", "to")) {
                JsonArray values = cube.getAsJsonArray(corner);
                for (JsonElement value : values) {
                    box.add(value.getAsDouble());
                }
            }
            boxes.add(box);
        }
        return boxes;
    }

    @Test
    void theHitBoxIsTheModel() throws IOException {
        Matcher matcher = BOX.matcher(Files.readString(BLOCK_SOURCE, StandardCharsets.UTF_8));
        List<List<Double>> shape = new ArrayList<>();
        while (matcher.find()) {
            List<Double> box = new ArrayList<>();
            for (int group = 1; group <= 6; group++) {
                box.add(Double.parseDouble(matcher.group(group)));
            }
            shape.add(box);
        }
        assertTrue(shape.size() >= 1, "found no Block.box calls in " + BLOCK_SOURCE);
        assertEquals(modelBoxes("spatial_stabiliser"), shape);
    }

    @Test
    void switchingOnChangesTheLookAndNotTheShape() {
        assertEquals(modelBoxes("spatial_stabiliser"), modelBoxes("spatial_stabiliser_on"));

        JsonObject off = read(ASSETS.resolve("models/block/spatial_stabiliser.json")).getAsJsonObject("textures");
        JsonObject on = read(ASSETS.resolve("models/block/spatial_stabiliser_on.json")).getAsJsonObject("textures");
        JsonObject offSkin = off.deepCopy();
        JsonObject onSkin = on.deepCopy();
        offSkin.remove("particle");
        onSkin.remove("particle");
        assertTrue(!offSkin.equals(onSkin),
                "a switched-on stabiliser must look switched on, or a pilot cannot read the lever from the deck");

        JsonObject variants = read(ASSETS.resolve("blockstates/spatial_stabiliser.json")).getAsJsonObject("variants");
        assertEquals("aerowarptics:block/spatial_stabiliser",
                variants.getAsJsonObject("powered=false").get("model").getAsString());
        assertEquals("aerowarptics:block/spatial_stabiliser_on",
                variants.getAsJsonObject("powered=true").get("model").getAsString());
    }
}
