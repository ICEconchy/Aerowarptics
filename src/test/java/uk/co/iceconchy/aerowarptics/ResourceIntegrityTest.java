package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorStatus;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the resources against the mistakes a compiler cannot catch: a model pointing at a texture
 * that was never drawn, a GeckoLib animation targeting a bone that does not exist, a UV box that
 * runs off the edge of its sheet, or an enum gaining a constant with no translation.
 */
class ResourceIntegrityTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/aerowarptics");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static List<Path> allJson(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".json")).toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyResourceFileIsValidJson() {
        for (Path path : allJson(RESOURCES)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader);
            } catch (Exception e) {
                fail(path + " is not valid JSON: " + e.getMessage());
            }
        }
    }

    @Test
    void everyModelTextureExists() {
        List<String> problems = new ArrayList<>();
        for (Path path : allJson(ASSETS.resolve("models"))) {
            JsonObject model = read(path);
            if (!model.has("textures")) {
                continue;
            }
            for (var entry : model.getAsJsonObject("textures").entrySet()) {
                String reference = entry.getValue().getAsString();
                if (!reference.startsWith("aerowarptics:")) {
                    continue;
                }
                Path texture = ASSETS.resolve("textures")
                        .resolve(reference.substring("aerowarptics:".length()) + ".png");
                if (!Files.exists(texture)) {
                    problems.add(path + " -> missing " + texture);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyBlockstateModelExists() {
        List<String> problems = new ArrayList<>();
        for (Path path : allJson(ASSETS.resolve("blockstates"))) {
            JsonObject variants = read(path).getAsJsonObject("variants");
            for (var entry : variants.entrySet()) {
                String model = entry.getValue().getAsJsonObject().get("model").getAsString();
                Path file = ASSETS.resolve("models")
                        .resolve(model.substring(model.indexOf(':') + 1) + ".json");
                if (!Files.exists(file)) {
                    problems.add(path + " -> missing " + file);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyBlockAndItemHasABlockstateOrModelAndAnIcon() {
        List<String> problems = new ArrayList<>();
        List<String> blocks = new ArrayList<>(Stream.of(RiftDriveTier.values())
                .map(RiftDriveTier::blockName).toList());
        blocks.add("warp_anchor");
        blocks.add("astrolabe");
        blocks.add("spatial_siphon");

        for (String block : blocks) {
            if (!Files.exists(ASSETS.resolve("blockstates").resolve(block + ".json"))) {
                problems.add("no blockstate for " + block);
            }
            if (!Files.exists(ASSETS.resolve("models/item").resolve(block + ".json"))) {
                problems.add("no item model for " + block);
            }
            if (!Files.exists(ASSETS.resolve("textures/item").resolve(block + ".png"))) {
                problems.add("no inventory icon for " + block);
            }
        }
        for (String item : List.of("rift_core", "rift_lens", "stabiliser_ring", "singularity_core")) {
            if (!Files.exists(ASSETS.resolve("models/item").resolve(item + ".json"))) {
                problems.add("no item model for " + item);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Box UVs must fit on the sheet and must not share space, or bones sample each other's pixels. */
    @Test
    void geoUvBoxesFitAndDoNotOverlap() {
        List<String> problems = new ArrayList<>();
        for (Path path : allJson(ASSETS.resolve("geo"))) {
            JsonObject geometry = read(path).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            int sheetWidth = description.get("texture_width").getAsInt();
            int sheetHeight = description.get("texture_height").getAsInt();

            record Box(String bone, int u0, int v0, int u1, int v1) {
            }
            List<Box> boxes = new ArrayList<>();

            for (JsonElement boneElement : geometry.getAsJsonArray("bones")) {
                JsonObject bone = boneElement.getAsJsonObject();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    JsonArray size = cube.getAsJsonArray("size");
                    JsonArray uv = cube.getAsJsonArray("uv");
                    int sx = size.get(0).getAsInt();
                    int sy = size.get(1).getAsInt();
                    int sz = size.get(2).getAsInt();
                    int u = uv.get(0).getAsInt();
                    int v = uv.get(1).getAsInt();
                    int width = 2 * (sz + sx);
                    int height = sz + sy;
                    if (u + width > sheetWidth || v + height > sheetHeight) {
                        problems.add(path + ": bone " + bone.get("name").getAsString()
                                + " needs " + width + "x" + height + " at " + u + "," + v
                                + " which runs off a " + sheetWidth + "x" + sheetHeight + " sheet");
                    }
                    boxes.add(new Box(bone.get("name").getAsString(), u, v, u + width, v + height));
                }
            }

            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    Box a = boxes.get(i);
                    Box b = boxes.get(j);
                    if (a.u0() < b.u1() && b.u0() < a.u1() && a.v0() < b.v1() && b.v0() < a.v1()) {
                        problems.add(path + ": UV overlap between " + a.bone() + " and " + b.bone());
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyDriveStateAndAnchorStatusHasAnAnimationTargetingRealBones() {
        List<String> problems = new ArrayList<>();

        checkAnimations("rift_drive",
                Stream.of(RiftDriveState.values()).map(RiftDriveState::animation).toList(), problems);
        checkAnimations("warp_anchor",
                Stream.of(WarpAnchorStatus.values()).map(WarpAnchorStatus::animation).toList(), problems);
        checkAnimations("astrolabe",
                List.of("animation.astrolabe.idle", "animation.astrolabe.active"), problems);
        checkAnimations("spatial_siphon",
                List.of("animation.spatial_siphon.idle", "animation.spatial_siphon.drawing"), problems);

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void checkAnimations(String model, List<String> required, List<String> problems) {
        JsonObject animations = read(ASSETS.resolve("animations/" + model + ".animation.json"))
                .getAsJsonObject("animations");
        JsonObject geometry = read(ASSETS.resolve("geo/" + model + ".geo.json"))
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

        Set<String> bones = new HashSet<>();
        for (JsonElement bone : geometry.getAsJsonArray("bones")) {
            bones.add(bone.getAsJsonObject().get("name").getAsString());
        }

        for (String name : required) {
            if (!animations.has(name)) {
                problems.add(model + ": no animation named " + name);
            }
        }
        for (var entry : animations.entrySet()) {
            JsonObject animation = entry.getValue().getAsJsonObject();
            if (!animation.has("bones")) {
                continue;
            }
            for (String bone : animation.getAsJsonObject("bones").keySet()) {
                if (!bones.contains(bone)) {
                    problems.add(model + ": animation " + entry.getKey()
                            + " moves bone '" + bone + "' which the model does not have");
                }
            }
        }
    }

    @Test
    void everyEnumConstantHasATranslation() {
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        List<String> problems = new ArrayList<>();

        List<String> keys = new ArrayList<>();
        Stream.of(RiftDriveState.values()).map(RiftDriveState::translationKey).forEach(keys::add);
        Stream.of(RiftDriveTier.values()).map(RiftDriveTier::translationKey).forEach(keys::add);
        Stream.of(WarpFailure.values()).map(WarpFailure::translationKey).forEach(keys::add);
        Stream.of(WarpAnchorStatus.values()).map(WarpAnchorStatus::translationKey).forEach(keys::add);
        Stream.of(WarpAnchorAccess.values()).map(WarpAnchorAccess::translationKey).forEach(keys::add);
        Stream.of(DriveHeading.values()).map(DriveHeading::translationKey).forEach(keys::add);

        for (String key : keys) {
            String full = "aerowarptics." + key;
            if (!lang.has(full)) {
                problems.add("missing translation " + full);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everySoundHasADefinitionAndASubtitle() {
        JsonObject sounds = read(ASSETS.resolve("sounds.json"));
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        List<String> problems = new ArrayList<>();

        for (var entry : sounds.entrySet()) {
            JsonObject definition = entry.getValue().getAsJsonObject();
            if (!definition.has("subtitle")) {
                problems.add(entry.getKey() + " has no subtitle");
                continue;
            }
            String subtitle = definition.get("subtitle").getAsString();
            if (!lang.has(subtitle)) {
                problems.add("missing translation " + subtitle);
            }
            if (!definition.has("sounds") || definition.getAsJsonArray("sounds").isEmpty()) {
                problems.add(entry.getKey() + " has no sound files");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
      * Every block drops itself, and everything a player is meant to build can be built.
      *
      * <p>The creative drive is the one exception, and it is checked <em>for</em> having no recipe
      * rather than skipped: a creative-only item that quietly became craftable would be a balance
      * hole nobody would think to look for.
      */
    @Test
    void everyBlockDropsItselfAndOnlyCreativeGearIsUncraftable() {
        Path data = RESOURCES.resolve("data/aerowarptics");
        List<String> problems = new ArrayList<>();
        List<String> blocks = new ArrayList<>(Stream.of(RiftDriveTier.values())
                .map(RiftDriveTier::blockName).toList());
        blocks.add("warp_anchor");
        blocks.add("astrolabe");
        blocks.add("spatial_siphon");

        for (String block : blocks) {
            boolean craftable = Files.exists(data.resolve("recipe").resolve(block + ".json"));
            boolean creativeOnly = block.equals(RiftDriveTier.CREATIVE.blockName());
            if (craftable && creativeOnly) {
                problems.add(block + " is creative-only but has a recipe");
            }
            if (!craftable && !creativeOnly) {
                problems.add("no recipe for " + block);
            }
            if (!Files.exists(data.resolve("loot_table/blocks").resolve(block + ".json"))) {
                problems.add("no loot table for " + block);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void particleDefinitionsPointAtRealTextures() {
        List<String> problems = new ArrayList<>();
        for (Path path : allJson(ASSETS.resolve("particles"))) {
            for (JsonElement element : read(path).getAsJsonArray("textures")) {
                String reference = element.getAsString();
                Path texture = ASSETS.resolve("textures/particle")
                        .resolve(reference.substring(reference.indexOf(':') + 1) + ".png");
                if (!Files.exists(texture)) {
                    problems.add(path + " -> missing " + texture);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
