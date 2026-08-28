package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorStatus;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * That every texture this mod ships is actually decodable.
     *
     * <p>A PNG whose header disagrees with its pixel data does not fail loudly. Minecraft swaps in
     * the missing-texture chequer and carries on, and on anything drawn in magenta that reads as a
     * texture doing its job rather than one that never loaded. This mod generates most of its
     * textures from scripts, so a generator that gets a dimension wrong can put a broken file in the
     * jar without anybody noticing until it is on screen.
     *
     * <p>Only the header is checked against the file's own data - enough to catch a size that lies,
     * which is the mistake a generator actually makes.
     */
    @Test
    void everyTextureIsAWellFormedPng() {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ASSETS.resolve("textures"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".png")).toList()) {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length < 24) {
                    problems.add(file + " is too short to be a PNG");
                    continue;
                }
                ByteBuffer header = ByteBuffer.wrap(bytes, 16, 8);
                int width = header.getInt();
                int height = header.getInt();
                if (width <= 0 || height <= 0) {
                    problems.add(file + " declares " + width + "x" + height);
                    continue;
                }
                BufferedImage image;
                try {
                    image = ImageIO.read(file.toFile());
                } catch (IOException failure) {
                    problems.add(file + " could not be decoded: " + failure.getMessage());
                    continue;
                }
                if (image == null) {
                    problems.add(file + " could not be decoded at all");
                } else if (image.getWidth() != width || image.getHeight() != height) {
                    problems.add(file + " header says " + width + "x" + height
                            + " but the data is " + image.getWidth() + "x" + image.getHeight());
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
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

    /**
     * Blocks with no baked model at all: what stands in the world is GeckoLib and nothing else.
     *
     * <p>Their item form is drawn by {@code GeoBlockItemRenderer} from the same geometry, so the item
     * model is the vanilla {@code builtin/entity} marker - "code draws this" - carrying nothing but a
     * particle texture and the display transforms. They used to carry a flat sixteen-pixel sprite
     * instead, which is what {@link #everyGeoRenderedItemIsDrawnRatherThanDrawnUp()} exists to stop
     * coming back.
     */
    private static List<String> geoRenderedBlocks() {
        List<String> blocks = new ArrayList<>(Stream.of(RiftDriveTier.values())
                .map(RiftDriveTier::blockName).toList());
        blocks.add("warp_anchor");
        blocks.add("spatial_siphon");
        blocks.add("rift_chute");
        blocks.add("rift_modulator");
        return blocks;
    }

    /**
     * Blocks a player holds as an ordinary baked model.
     *
     * <p>The Astrolabe is the interesting one. It has a GeckoLib model too, but that model is the
     * whole assembled three-by-three table, and the item is one ninth of it - so the item is the
     * panel's baked model, which is exactly what an unformed cell looks like in the world.
     */
    private static List<String> modelledBlocks() {
        return List.of("astrolabe", "rift_probe", "rift_gate", "rift_gate_frame");
    }

    /** Everything this mod puts in the world, however it is drawn. */
    private static List<String> allBlocks() {
        List<String> blocks = geoRenderedBlocks();
        blocks.addAll(modelledBlocks());
        return blocks;
    }

    @Test
    void everyBlockAndItemHasABlockstateOrModelAndAnIcon() {
        List<String> problems = new ArrayList<>();

        for (String block : allBlocks()) {
            if (!Files.exists(ASSETS.resolve("blockstates").resolve(block + ".json"))) {
                problems.add("no blockstate for " + block);
            }
            if (!Files.exists(ASSETS.resolve("models/item").resolve(block + ".json"))) {
                problems.add("no item model for " + block);
            }
        }
        for (String item : List.of("rift_core", "rift_lens", "stabiliser_ring", "singularity_core",
                "handbook", "rift_goggles")) {
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
        checkAnimations("rift_modulator", List.of("animation.rift_modulator.idle"), problems);

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
        Stream.of(RiftModulatorTheme.values()).map(RiftModulatorTheme::translationKey).forEach(keys::add);

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

        for (String block : allBlocks()) {
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

    /**
     * A Rift Fissure is placed by the world and by nothing else.
     *
     * <p>Asserted rather than skipped, for the same reason the creative drive's lack of a recipe is:
     * every other block here is something a player makes, and a fissure quietly gaining a recipe, an
     * item or a drop would turn a thing you have to go and find into a thing you can put in a chest.
     * It still needs a blockstate, because a block with no model logs an error and renders as missing
     * texture - even one whose render shape is invisible.
     */
    @Test
    void aFissureIsPlacedByTheWorldAndNotByAnybodyElse() {
        Path data = RESOURCES.resolve("data/aerowarptics");
        List<String> problems = new ArrayList<>();

        if (!Files.exists(ASSETS.resolve("blockstates/rift_fissure.json"))) {
            problems.add("no blockstate for rift_fissure");
        }
        if (Files.exists(data.resolve("recipe/rift_fissure.json"))) {
            problems.add("rift_fissure has a recipe - it is not something a player makes");
        }
        if (Files.exists(data.resolve("loot_table/blocks/rift_fissure.json"))) {
            problems.add("rift_fissure has a loot table - it is not something a player collects");
        }
        if (Files.exists(ASSETS.resolve("models/item/rift_fissure.json"))) {
            problems.add("rift_fissure has an item model - it has no item form");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A Rift Portal is put there by a gate and by nobody else.
     *
     * <p>The same shape of rule as the fissure's above, and asserted for the same reason: the pane
     * standing in a gate's opening is scenery the gate owns, not a block anybody makes, carries or
     * mines. It quietly gaining an item would turn a doorway into something you could put in a chest
     * and stand up in your kitchen.
     *
     * <p>It does need a blockstate and two models, one per axis, because unlike a fissure it is meant
     * to be looked at.
     */
    @Test
    void aRiftPortalIsPlacedByAGateAndNotByAnybodyElse() {
        Path data = RESOURCES.resolve("data/aerowarptics");
        List<String> problems = new ArrayList<>();

        if (!Files.exists(ASSETS.resolve("blockstates/rift_portal.json"))) {
            problems.add("no blockstate for rift_portal");
        }
        for (String model : List.of("rift_portal_ns", "rift_portal_ew")) {
            if (!Files.exists(ASSETS.resolve("models/block").resolve(model + ".json"))) {
                problems.add("no model " + model + " - one axis of the pane would not draw");
            }
        }
        if (Files.exists(ASSETS.resolve("models/item/rift_portal.json"))) {
            problems.add("rift_portal has an item model - it has no item form");
        }
        if (Files.exists(data.resolve("recipe/rift_portal.json"))) {
            problems.add("rift_portal has a recipe - a gate opens one, nobody crafts one");
        }
        if (Files.exists(data.resolve("loot_table/blocks/rift_portal.json"))) {
            problems.add("rift_portal has a loot table - there is nothing to collect from a doorway");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A blockstate file covers every combination of the properties it uses.
     *
     * <p>The failure this catches is the one that comes with adding a property to a block that
     * already had one. The Rift Gate's controller had six facings; giving it a three-value glow made
     * eighteen variants, and writing seventeen of them is a single missing line that the game reports
     * as a log warning nobody reads and renders as the missing-texture chequer on one facing of one
     * block in one state - which is to say, on the gate somebody built facing east, the first time
     * they dial it.
     *
     * <p>Read entirely out of the file: the variant keys name the properties and, between them, the
     * values each one takes, so the complete set is their product and anything short of it is a hole.
     * That keeps this test away from the block classes, which cannot be loaded without a bootstrapped
     * Minecraft.
     */
    @Test
    void everyBlockstateCoversEveryCombinationOfItsProperties() {
        List<String> problems = new ArrayList<>();
        for (Path path : allJson(ASSETS.resolve("blockstates"))) {
            JsonObject file = read(path);
            if (!file.has("variants")) {
                continue;
            }
            Set<String> keys = file.getAsJsonObject("variants").keySet();
            if (keys.size() == 1 && keys.contains("")) {
                continue; // a block with no properties at all
            }

            // property -> every value seen for it, in the order the file mentions them
            Map<String, Set<String>> seen = new LinkedHashMap<>();
            for (String key : keys) {
                for (String pair : key.split(",")) {
                    String[] halves = pair.split("=", 2);
                    if (halves.length != 2) {
                        problems.add(path + ": variant key \"" + key + "\" is not property=value");
                        continue;
                    }
                    seen.computeIfAbsent(halves[0].trim(), name -> new LinkedHashSet<>())
                            .add(halves[1].trim());
                }
            }
            if (seen.isEmpty()) {
                continue;
            }

            int expected = 1;
            for (Set<String> values : seen.values()) {
                expected *= values.size();
            }
            if (keys.size() != expected) {
                problems.add(path + ": " + keys.size() + " variants for " + seen.keySet()
                        + ", which needs " + expected + " to cover every combination");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every texture that is a strip of frames ships the metadata that plays it.
     *
     * <p>This is a properly silent one. An animated texture is a column of frames, and without its
     * {@code .mcmeta} the game does not fail to load it - it takes the whole column as a single
     * sprite and squeezes thirty-two frames into one block face. The result is a smear that still
     * looks vaguely like the thing it was meant to be, on a block nobody thinks to suspect.
     *
     * <p>Anything taller than it is wide is taken to be such a strip, which is exactly true of this
     * mod: every other texture here is square. The height also has to be a whole number of frames, or
     * the last one is a slice of the one before it.
     */
    @Test
    void everyAnimatedTextureHasItsMetadata() {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ASSETS.resolve("textures"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".png")).toList()) {
                byte[] bytes = Files.readAllBytes(file);
                ByteBuffer header = ByteBuffer.wrap(bytes, 16, 8);
                int width = header.getInt();
                int height = header.getInt();
                if (height <= width) {
                    continue;
                }
                if (height % width != 0) {
                    problems.add(file + " is " + width + "x" + height
                            + ", which is not a whole number of square frames");
                }
                Path meta = file.resolveSibling(file.getFileName() + ".mcmeta");
                if (!Files.exists(meta)) {
                    problems.add(file + " is a strip of " + (height / width)
                            + " frames with no .mcmeta to animate it");
                } else if (!read(meta).has("animation")) {
                    problems.add(meta + " has no animation section");
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A machine's item is drawn from the machine, not from a picture of one.
     *
     * <p>Three things have to hold together and none of them fails loudly. The item model has to
     * declare {@code builtin/entity}, or the game renders whatever the JSON says instead - which, for
     * a model with no elements, is nothing. The flat sprite has to be gone, or it survives as dead
     * weight that somebody will later "fix" the model to point back at. And something has to be
     * registered to do the drawing, because an item declaring {@code builtin/entity} with no renderer
     * behind it is an invisible item in the slot: no error, no missing-texture chequerboard, nothing.
     */
    @Test
    void everyGeoRenderedItemIsDrawnRatherThanDrawnUp() {
        List<String> problems = new ArrayList<>();

        for (String block : geoRenderedBlocks()) {
            Path model = ASSETS.resolve("models/item").resolve(block + ".json");
            if (!Files.exists(model)) {
                problems.add("no item model for " + block);
                continue;
            }
            JsonObject json = read(model);
            String parent = json.has("parent") ? json.get("parent").getAsString() : "";
            if (!parent.equals("builtin/entity")) {
                problems.add(block + " is drawn by GeckoLib but its item model parents "
                        + (parent.isEmpty() ? "nothing" : parent));
            }
            if (!json.has("display")) {
                problems.add(block + " has no display transforms, so it sits wrong in every slot");
            }
            if (Files.exists(ASSETS.resolve("textures/item").resolve(block + ".png"))) {
                problems.add(block + " still has the flat placeholder icon it was meant to replace");
            }
        }

        // And nothing else claims to be drawn by code, which would be an item nobody can see.
        for (Path path : allJson(ASSETS.resolve("models/item"))) {
            JsonObject json = read(path);
            if (!json.has("parent") || !json.get("parent").getAsString().equals("builtin/entity")) {
                continue;
            }
            String name = path.getFileName().toString().replace(".json", "");
            if (!geoRenderedBlocks().contains(name)) {
                problems.add(name + " declares builtin/entity but nothing is registered to draw it");
            }
        }

        // The wiring itself, checked at the coarsest level that means anything without a running
        // game: the items are built as GeoBlockItems, and the client hands that list a renderer.
        String items = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/registry/AWItems.java"));
        String client = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/AWClientSetup.java"));
        if (!items.contains("new GeoBlockItem(")) {
            problems.add("AWItems no longer builds any GeoBlockItem");
        }
        if (!client.contains("AWItems.geoBlockItems()")) {
            problems.add("AWClientSetup no longer hands the geo block items a renderer");
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static String source(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * The geometry a block item borrows is built like a block: standing on its floor, centred on the
     * other two axes.
     *
     * <p>This is the assumption {@code GeoBlockItemRenderer} corrects for. GeckoLib positions an item
     * as though its model were centred on the origin, an entity is built that way and a block is not,
     * so the renderer drops a block model half a block to make up the difference. That correction is a
     * constant, and a constant is only right while the thing it corrects stays the same shape - so if
     * one of these models is ever rebuilt centred on its own middle, this fails here rather than
     * showing up as one machine sunk into the slot while the rest sit right.
     *
     * <p>The upper bound is the other half of it. A model much taller than a block hangs out of the
     * top of a slot however it is positioned, which is the fault this whole guard exists to describe.
     */
    @Test
    void everyGeoBlockItemModelStandsOnItsFloor() {
        List<String> problems = new ArrayList<>();

        for (String name : List.of("rift_drive", "warp_anchor", "spatial_siphon", "rift_chute",
                "rift_modulator")) {
            Path path = ASSETS.resolve("geo").resolve(name + ".geo.json");
            if (!Files.exists(path)) {
                problems.add("no geometry at " + path);
                continue;
            }

            double[] least = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] most = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            JsonObject geometry = read(path).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();

            for (JsonElement boneElement : geometry.getAsJsonArray("bones")) {
                JsonObject bone = boneElement.getAsJsonObject();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    JsonArray origin = cube.getAsJsonArray("origin");
                    JsonArray size = cube.getAsJsonArray("size");
                    for (int axis = 0; axis < 3; axis++) {
                        double from = origin.get(axis).getAsDouble();
                        double to = from + size.get(axis).getAsDouble();
                        least[axis] = Math.min(least[axis], from);
                        most[axis] = Math.max(most[axis], to);
                    }
                }
            }

            // Standing on the floor: the lowest cube is at or just above zero, never below it and
            // never lifted clear of it.
            if (least[1] < -0.01D || least[1] > 1.0D) {
                problems.add(name + " has its floor at y=" + least[1]
                        + ", so it is not built standing on the origin like a block");
            }
            if (most[1] > 20.0D) {
                problems.add(name + " is " + most[1] + " units tall and will hang out of a slot");
            }
            // Centred on x and z, which is what lets the renderer leave those axes alone.
            for (int axis : new int[]{0, 2}) {
                double offset = least[axis] + most[axis];
                if (Math.abs(offset) > 2.0D) {
                    problems.add(name + " is off centre on " + (axis == 0 ? "x" : "z")
                            + " by " + offset + " units");
                }
                if (most[axis] - least[axis] > 18.0D) {
                    problems.add(name + " is " + (most[axis] - least[axis])
                            + " units across and will not fit a slot");
                }
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
