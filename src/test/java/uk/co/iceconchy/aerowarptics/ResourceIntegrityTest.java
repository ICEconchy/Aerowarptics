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
import uk.co.iceconchy.aerowarptics.gate.GatePassageStyle;
import uk.co.iceconchy.aerowarptics.gate.PassageFrame;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
import uk.co.iceconchy.aerowarptics.cooling.VentStatus;
import uk.co.iceconchy.aerowarptics.stabiliser.StabiliserStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * That the lens's shader is all there, and asks for nothing the lens does not give it.
     *
     * <p>A core shader that fails to load is logged and the lens stands down - see
     * {@code AWClientSetup.registerShaders} - so a missing program or a misnamed uniform would not crash
     * anything. It would simply never bend the view again, and nothing would say so but a line in a log.
     */
    @Test
    void theLensShaderIsCompleteAndConsistent() throws IOException {
        Path core = ASSETS.resolve("shaders/core");
        shaderIsWhole(core, "gravity_lens");
        String vertex = Files.readString(core.resolve("gravity_lens.vsh"));
        String fragment = Files.readString(core.resolve("gravity_lens.fsh"));
        // The format the lens is drawn in, element for element.
        for (String input : List.of("in vec3 Position;", "in vec2 UV0;", "in vec4 Color;")) {
            assertTrue(vertex.contains(input), "the lens's vertex program no longer takes " + input);
        }
        assertTrue(fragment.contains("Sampler0"), "the lens's fragment program no longer reads the copied frame");
    }

    /**
     * That a core shader's programs are both there, and that it uses no uniform it does not declare.
     *
     * @return the uniforms its JSON declares
     */
    private static Set<String> shaderIsWhole(Path core, String shaderName) throws IOException {
        JsonObject shader = read(core.resolve(shaderName + ".json"));
        Set<String> declared = new LinkedHashSet<>();
        for (JsonElement uniform : shader.getAsJsonArray("uniforms")) {
            declared.add(uniform.getAsJsonObject().get("name").getAsString());
        }
        for (String stage : List.of("vertex", "fragment")) {
            String name = shader.get(stage).getAsString();
            assertTrue(name.startsWith("aerowarptics:"),
                    shaderName + "'s " + stage + " program is looked for in the minecraft namespace: " + name);
            String extension = stage.equals("vertex") ? ".vsh" : ".fsh";
            Path program = core.resolve(name.substring("aerowarptics:".length()) + extension);
            assertTrue(Files.exists(program), shaderName + "'s " + stage + " program is missing: " + program);
            for (String used : uniformsOf(Files.readString(program))) {
                assertTrue(declared.contains(used), shaderName + " uses " + used + " but never declares it");
            }
        }
        return declared;
    }

    /** The uniforms a shader program asks for, samplers aside - those are declared separately. */
    private static Set<String> uniformsOf(String program) {
        Set<String> uniforms = new LinkedHashSet<>();
        java.util.regex.Matcher uniform = java.util.regex.Pattern
                .compile("(?m)^\\s*uniform\\s+(\\w+)\\s+(\\w+)\\s*;").matcher(program);
        while (uniform.find()) {
            if (!uniform.group(1).startsWith("sampler")) {
                uniforms.add(uniform.group(2));
            }
        }
        return uniforms;
    }

    /** Every core shader the mod ships, the lens's and any after it, held to the same. */
    @Test
    void everyCoreShaderIsWhole() throws IOException {
        Path core = ASSETS.resolve("shaders/core");
        List<String> shaders;
        try (Stream<Path> files = Files.list(core)) {
            shaders = files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length())).sorted().toList();
        }
        assertTrue(shaders.contains("gravity_lens") && shaders.contains("passage_kawoosh"),
                "a core shader has gone missing: " + shaders);
        for (String shader : shaders) {
            shaderIsWhole(core, shader);
        }
        // And none is left over from a style that has gone: every passage shader is some style's.
        Set<String> styles = new HashSet<>();
        for (GatePassageStyle style : GatePassageStyle.values()) {
            styles.add(String.valueOf(style.shader()));
        }
        for (String shader : shaders) {
            assertTrue(!shader.startsWith("passage_") || styles.contains(shader),
                    shader + " is no gate passage style's shader any more");
        }
    }

    /**
     * That every gate passage style's shader is there, and is told everything it asks, by name.
     *
     * <p>A uniform is matched by its name three times over - given in {@code PassageFrame}, declared in the
     * shader's JSON, read in its fragment program - and a name that is in one and not another fails without
     * a word: {@code safeGetUniform} hands back a dummy for a name the JSON does not have, and the JSON's
     * default is drawn for one nobody sets. A conduit that never moves, or never flares, is what that looks
     * like, and the flare is how a traveller is let out. A style declares the uniforms it reads and no
     * others, so the three lists are not equal - but each has to sit inside the next.
     */
    @Test
    void everyPassageStyleIsToldEveryUniformItAsksFor() throws IOException {
        Path core = ASSETS.resolve("shaders/core");
        boolean anyShaded = false;
        for (GatePassageStyle style : GatePassageStyle.values()) {
            String shader = style.shader();
            if (shader == null) {
                continue;
            }
            anyShaded = true;
            assertTrue(Files.exists(core.resolve(shader + ".json")), style + " has no " + shader + ".json");
            Set<String> declared = new LinkedHashSet<>(shaderIsWhole(core, shader));
            // Filled in by the game for any shader that declares them.
            declared.removeAll(Set.of("ModelViewMat", "ProjMat"));

            Set<String> given = new LinkedHashSet<>(PassageFrame.UNIFORMS);
            assertTrue(given.containsAll(declared), shader + ".json declares uniforms a passage's frame does not give: "
                    + difference(declared, given));
            Set<String> read = uniformsOf(Files.readString(core.resolve(shader + ".fsh")));
            assertTrue(read.containsAll(declared), shader + ".json declares uniforms its fragment program never reads: "
                    + difference(declared, read));
            // Whatever else a style leaves out, these are the passage: where it hides, and how it ends.
            assertTrue(declared.containsAll(Set.of("Veil", "Flare", "Splash", "Aspect")),
                    shader + " does not read all of the veil, the flare, the pane's light and the view's shape");
        }
        assertTrue(anyShaded, "no gate passage style is drawn with a shader any more");

        // The names a frame gives are the names it says it gives.
        assertEquals(new LinkedHashSet<>(PassageFrame.UNIFORMS),
                PassageFrame.at(10.0D, 60.0D, -1.0D, 1.0D).uniforms(1.5F).keySet(),
                "PassageFrame.UNIFORMS and the uniforms a frame hands over have come apart");

        // Position and texture, as the quad is written and as every style's shader is registered.
        String vertex = Files.readString(core.resolve("passage.vsh"));
        for (String input : List.of("in vec3 Position;", "in vec2 UV0;")) {
            assertTrue(vertex.contains(input), "the passage's vertex program no longer takes " + input);
        }
    }

    private static Set<String> difference(Set<String> from, Set<String> without) {
        Set<String> left = new LinkedHashSet<>(from);
        left.removeAll(without);
        return left;
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
     * That every one of this mod's ids a tag names still exists.
     *
     * <p>A tag naming something that is not registered does not lose that one entry: the game refuses the whole
     * tag, and says so once in a log nobody reads. {@code #minecraft:mineable/pickaxe} naming a removed block
     * would leave every block of this mod's in it mined at hand speed and dropping nothing. So a removed block
     * must leave the tags too - which the Fold Crucible's removal very nearly did not. A block is judged by its
     * blockstate, a damage type or a structure by its own file.
     */
    @Test
    void everyTagNamesOnlyWhatExists() {
        List<String> problems = new ArrayList<>();
        Path data = RESOURCES.resolve("data");
        for (Path path : allJson(data)) {
            String relative = data.relativize(path).toString().replace('\\', '/');
            int tags = relative.indexOf("/tags/");
            if (tags < 0) {
                continue;
            }
            String kind = relative.substring(tags + "/tags/".length());
            JsonObject tag = read(path);
            for (var element : tag.getAsJsonArray("values")) {
                String id = element.isJsonObject() ? element.getAsJsonObject().get("id").getAsString()
                        : element.getAsString();
                if (!id.startsWith("aerowarptics:")) {
                    continue;
                }
                String name = id.substring("aerowarptics:".length());
                Path needed;
                if (kind.startsWith("block/")) {
                    needed = ASSETS.resolve("blockstates/" + name + ".json");
                } else if (kind.startsWith("item/")) {
                    // Every item this mod registers has an item model, so a missing one means no such item.
                    needed = ASSETS.resolve("models/item/" + name + ".json");
                } else if (kind.startsWith("damage_type/")) {
                    needed = data.resolve("aerowarptics/damage_type/" + name + ".json");
                } else if (kind.startsWith("worldgen/structure/")) {
                    needed = data.resolve("aerowarptics/worldgen/structure/" + name + ".json");
                } else {
                    problems.add(relative + " names " + id + ", and this test does not know how to look for a "
                            + kind + " - teach it");
                    continue;
                }
                if (!Files.isRegularFile(needed)) {
                    problems.add(relative + " names " + id + ", which does not exist");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
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

    /**
     * A texture named in code rather than in a model is one no model test ever sees.
     *
     * <p>The render types and GeckoLib models name theirs as a path string. A missing one is not an
     * error anywhere: the render type binds the missing-texture chequer and draws with it - which, for
     * the storm's rain, would be a curtain of magenta and black squares falling out of the sky.
     *
     * <p>A path handed straight to {@code ResourceLocation.withDefaultNamespace} is vanilla's, not this mod's -
     * the storm's rain is vanilla's own texture, recoloured as it loads - and is left to vanilla to have.
     */
    @Test
    void everyTextureTheCodeNamesExists() {
        java.util.regex.Pattern literal = java.util.regex.Pattern.compile(
                "(withDefaultNamespace\\(\\s*)?\"(textures/[^\"]+\\.png)\"");
        List<String> problems = new ArrayList<>();
        int found = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                java.util.regex.Matcher matcher = literal.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (matcher.group(1) != null) {
                        continue;
                    }
                    found++;
                    if (!Files.exists(ASSETS.resolve(matcher.group(2)))) {
                        problems.add(file + " -> missing " + matcher.group(2));
                    }
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertTrue(found >= 7, "only found " + found + " texture paths in code - has the naming changed?");
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

    /**
     * The Ship's Compass is drawn from its own thirty-two frames, each filed where vanilla files its own.
     *
     * <p>It used to be vanilla's dial with a violet badge laid over it, and nothing above would notice it
     * going back: a texture in the {@code minecraft:} namespace is vanilla's to have, so
     * {@link #everyModelTextureExists()} does not look at it. Nor would anything notice a frame filed
     * under the wrong number, which is a needle that points the wrong way through one thirty-second of
     * a turn and the right way through the rest.
     *
     * <p>The frames are numbered as vanilla numbers its compass - 16 is the needle pointing straight up
     * the icon - because the angle comes from vanilla's {@code CompassItemPropertyFunction}. So the
     * overrides are vanilla's table too: 0 is the base model, and from a sixty-fourth of a turn each
     * further thirty-second is the next frame round from 17.
     */
    @Test
    void theShipsCompassIsDrawnFromItsOwnThirtyTwoFrames() {
        List<String> problems = new ArrayList<>();
        Path models = ASSETS.resolve("models/item");

        JsonObject base = read(models.resolve("ship_compass.json"));
        Map<String, JsonObject> drawn = new LinkedHashMap<>();
        drawn.put("ship_compass_16", base);
        for (int frame = 0; frame < 32; frame++) {
            String name = String.format("ship_compass_%02d", frame);
            Path model = models.resolve(name + ".json");
            if (!Files.exists(model)) {
                problems.add("no model for " + name);
                continue;
            }
            JsonObject json = read(model);
            if (frame == 16 && !json.getAsJsonObject("textures").equals(base.getAsJsonObject("textures"))) {
                problems.add("ship_compass and ship_compass_16 are the same needle and are not drawn the same");
            }
            drawn.put(name, json);
        }
        for (var entry : drawn.entrySet()) {
            JsonObject textures = entry.getValue().getAsJsonObject("textures");
            String wanted = "aerowarptics:item/" + entry.getKey();
            if (textures.size() != 1 || !textures.has("layer0")
                    || !textures.get("layer0").getAsString().equals(wanted)) {
                problems.add(entry.getKey() + " should be drawn from " + wanted + " alone, but has " + textures);
            }
        }

        JsonArray overrides = base.getAsJsonArray("overrides");
        if (overrides.size() != 33) {
            problems.add("ship_compass has " + overrides.size() + " overrides, and a full turn takes 33");
        }
        for (int step = 0; step < overrides.size(); step++) {
            JsonObject override = overrides.get(step).getAsJsonObject();
            double angle = override.getAsJsonObject("predicate").get("angle").getAsDouble();
            double wantedAngle = step == 0 ? 0.0D : (step - 0.5D) / 32.0D;
            String wantedModel = step == 0 ? "aerowarptics:item/ship_compass"
                    : String.format("aerowarptics:item/ship_compass_%02d", (16 + step) % 32);
            if (angle != wantedAngle || !override.get("model").getAsString().equals(wantedModel)) {
                problems.add("override " + step + " is " + override + ", not " + wantedModel + " from "
                        + wantedAngle);
            }
        }

        if (Files.exists(ASSETS.resolve("textures/item/ship_compass_badge.png"))) {
            problems.add("the badge the compass used to wear is still here, with nothing left to wear it");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyBlockstateModelExists() {
        List<String> problems = new ArrayList<>();
        for (Path path : allJson(ASSETS.resolve("blockstates"))) {
            // A multipart blockstate names its models under "apply": the Rift Wellhead's parts have hundreds of
            // states that differ only in where they stand, which a list of variants would have to spell out.
            List<String> models = new ArrayList<>();
            JsonObject file = read(path);
            if (file.has("variants")) {
                for (var entry : file.getAsJsonObject("variants").entrySet()) {
                    models.add(entry.getValue().getAsJsonObject().get("model").getAsString());
                }
            }
            if (file.has("multipart")) {
                for (var part : file.getAsJsonArray("multipart")) {
                    models.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
                }
            }
            if (models.isEmpty()) {
                problems.add(path + " names no model at all");
            }
            for (String model : models) {
                Path target = ASSETS.resolve("models")
                        .resolve(model.substring(model.indexOf(':') + 1) + ".json");
                if (!Files.exists(target)) {
                    problems.add(path + " -> missing " + target);
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
        blocks.add("rift_coupler");
        blocks.add("rift_brazier");
        blocks.add("collapse_cradle");
        blocks.add("rift_plotter");
        blocks.add("rift_ward");
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
        return List.of("astrolabe", "rift_probe", "rift_gate", "rift_gate_frame", "spatial_stabiliser",
                "spatial_wave_detector", "drive_extender", "rift_casing", "warp_glass", "cooling_vent", "rift_bond",
                "berth");
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
                "handbook", "rift_goggles", "rift_pearl", "incomplete_rift_core", "opened_rift_lens",
                "folded_warp_crystal", "scar_heart", "scar_chart", "ship_compass", "unset_stormglass",
                "stormglass", "relic_armillary")) {
            if (!Files.exists(ASSETS.resolve("models/item").resolve(item + ".json"))) {
                problems.add("no item model for " + item);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every UV must fit on its sheet, and box UVs - the layout the generators in {@code tools/} pack - must not
     * share space, or bones sample each other's pixels.
     *
     * <p>A model the artist drew lays its sheet out face by face, and reuses texels on purpose - one strip of brass
     * trim for every rail of a frame - so its faces are held to the sheet but not to each other. A face with no
     * area draws nothing and is not checked at all.
     */
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
                    if (cube.get("uv").isJsonObject()) {
                        checkFaceUvs(path, bone.get("name").getAsString(), cube.getAsJsonObject("uv"),
                                sheetWidth, sheetHeight, problems);
                        continue;
                    }
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

    /**
     * Faces the artist's Warp Anchor samples from off its 32-pixel sheet. Its ring was cut from the drive's, whose
     * sheet is 64 pixels, and these few kept the drive's coordinates: the ends and seams of the ring's pieces,
     * each butted against the next piece, so little if anything of them should show. Listed here rather than waved through,
     * so a new stray face is not missed among them; they go when the artist redraws the ring's UVs.
     */
    private static final Set<String> OFF_SHEET_BY_THE_ARTIST = Set.of("warp_anchor.geo.json ring");

    private static void checkFaceUvs(Path path, String bone, JsonObject faces, int sheetWidth, int sheetHeight,
                                     List<String> problems) {
        if (OFF_SHEET_BY_THE_ARTIST.contains(path.getFileName() + " " + bone)) {
            return;
        }
        for (String face : faces.keySet()) {
            JsonArray uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
            JsonArray size = faces.getAsJsonObject(face).getAsJsonArray("uv_size");
            double u = uv.get(0).getAsDouble();
            double v = uv.get(1).getAsDouble();
            double w = size.get(0).getAsDouble();
            double h = size.get(1).getAsDouble();
            if (Math.abs(w) < 1.0E-6D || Math.abs(h) < 1.0E-6D) {
                continue;
            }
            if (Math.min(u, u + w) < 0.0D || Math.min(v, v + h) < 0.0D
                    || Math.max(u, u + w) > sheetWidth || Math.max(v, v + h) > sheetHeight) {
                problems.add(path + ": bone " + bone + "'s " + face + " face samples " + u + "," + v
                        + " by " + w + "x" + h + ", off a " + sheetWidth + "x" + sheetHeight + " sheet");
            }
        }
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
        checkAnimations("rift_coupler", List.of("animation.rift_coupler.idle", "animation.rift_coupler.muster"),
                problems);
        checkAnimations("rift_brazier", List.of("animation.rift_brazier.idle", "animation.rift_brazier.lit"), problems);
        // The cradle's block is posed, never animated (see CradleWorks); the one clip is its item's.
        checkAnimations("collapse_cradle", List.of("animation.collapse_cradle.idle"), problems);

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
        Stream.of(StabiliserStatus.values()).map(StabiliserStatus::translationKey).forEach(keys::add);
        Stream.of(VentStatus.values()).map(VentStatus::translationKey).forEach(keys::add);

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
     * A Rift Fissure is placed by the world, or from the creative tab, and by nothing else.
     *
     * <p>Asserted rather than skipped, for the same reason the creative drive's lack of a recipe is:
     * every other block here is something a player makes, and a fissure quietly gaining a recipe or a
     * drop would turn a thing you have to go and find into a thing you can put in a chest. It has an
     * item - uncraftable, for whoever is setting up a build or a test (the owner, 2026-10-09) - and that
     * item needs its model; what it must never have is a way to be made or picked up.
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
        if (!Files.exists(ASSETS.resolve("models/item/rift_fissure.json"))) {
            problems.add("rift_fissure has no item model - its uncraftable item would draw as a missing texture");
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
                } else {
                    // A frame the strip does not have is one warning in the log and a frame quietly dropped
                    // from the loop: the Scar Heart's shipped a forty-first for a strip of forty.
                    JsonObject animation = read(meta).getAsJsonObject("animation");
                    int frames = height / width;
                    if (animation.has("frames")) {
                        for (JsonElement frame : animation.getAsJsonArray("frames")) {
                            int index = frame.isJsonObject()
                                    ? frame.getAsJsonObject().get("index").getAsInt() : frame.getAsInt();
                            if (index < 0 || index >= frames) {
                                problems.add(meta + " plays frame " + index + " of a strip of " + frames);
                            }
                        }
                    }
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
     *
     * <p>A machine placed whole - the Collapse Cradle, three blocks each way - is built at its full size and
     * drawn shrunk by {@code GeoBlockItem.blocks()}, so it is measured in its own blocks.
     */
    @Test
    void everyGeoBlockItemModelStandsOnItsFloor() {
        List<String> problems = new ArrayList<>();

        for (String name : List.of("rift_drive", "warp_anchor", "spatial_siphon", "rift_chute",
                "rift_modulator", "rift_coupler", "rift_brazier", "collapse_cradle")) {
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
                    for (double[] corner : turnedCorners(cubeElement.getAsJsonObject())) {
                        for (int axis = 0; axis < 3; axis++) {
                            least[axis] = Math.min(least[axis], corner[axis]);
                            most[axis] = Math.max(most[axis], corner[axis]);
                        }
                    }
                }
            }

            // Measured at the size the item is drawn: the cradle shrunk to one block, the anchor to its own share.
            double blocks = switch (name) {
                case "collapse_cradle" -> uk.co.iceconchy.aerowarptics.collapse.CradleStructure.SIZE;
                case "warp_anchor" -> 1.0D / uk.co.iceconchy.aerowarptics.registry.AWItems.ANCHOR_ITEM_SCALE;
                default -> 1.0D;
            };
            for (int axis = 0; axis < 3; axis++) {
                least[axis] /= blocks;
                most[axis] /= blocks;
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

    /**
     * A cube's eight corners where GeckoLib draws them: turned about the cube's pivot, if it has a turn. The artist
     * places some pieces well outside the block and turns them back into it - the Rift Brazier's corner fins - so
     * the corners as written say nothing about where the cube is.
     *
     * <p>GeckoLib mirrors Bedrock's x and negates its turns about x and y, so in the file's own numbers a positive
     * turn about x or z is clockwise looking down the axis, and one about y is anticlockwise. Each piece here turns
     * about one axis, so the order the three are taken in does not matter.
     */
    private static List<double[]> turnedCorners(JsonObject cube) {
        JsonArray origin = cube.getAsJsonArray("origin");
        JsonArray size = cube.getAsJsonArray("size");
        JsonArray rotation = cube.has("rotation") ? cube.getAsJsonArray("rotation") : null;
        JsonArray pivot = cube.has("pivot") ? cube.getAsJsonArray("pivot") : null;
        List<double[]> corners = new ArrayList<>();
        for (int corner = 0; corner < 8; corner++) {
            double[] point = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                point[axis] = origin.get(axis).getAsDouble() + ((corner >> axis & 1) == 0 ? 0.0D : size.get(axis).getAsDouble());
            }
            if (rotation != null && pivot != null) {
                double x = point[0] - pivot.get(0).getAsDouble();
                double y = point[1] - pivot.get(1).getAsDouble();
                double z = point[2] - pivot.get(2).getAsDouble();
                double rx = -Math.toRadians(rotation.get(0).getAsDouble());
                double ry = Math.toRadians(rotation.get(1).getAsDouble());
                double rz = -Math.toRadians(rotation.get(2).getAsDouble());
                double y1 = y * Math.cos(rx) - z * Math.sin(rx);
                double z1 = y * Math.sin(rx) + z * Math.cos(rx);
                double x2 = x * Math.cos(ry) + z1 * Math.sin(ry);
                double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
                double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
                double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
                point = new double[] {x3 + pivot.get(0).getAsDouble(), y3 + pivot.get(1).getAsDouble(),
                        z2 + pivot.get(2).getAsDouble()};
            }
            corners.add(point);
        }
        return corners;
    }

    /** A one-block astrolabe's map floats over its compass: nothing of the instrument pokes up through it. */
    @Test
    void theSingleAstrolabesMapClearsTheInstrument() {
        double floor = uk.co.iceconchy.aerowarptics.client.fx.AstrolabeProjections.SINGLE_PROJECTION_HEIGHT * 16.0D;
        for (String name : List.of("astrolabe_1x")) {
            JsonObject geometry = read(ASSETS.resolve("geo/" + name + ".geo.json"))
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            double top = 0.0D;
            for (JsonElement bone : geometry.getAsJsonArray("bones")) {
                if (!bone.getAsJsonObject().has("cubes")) {
                    continue;
                }
                for (JsonElement cube : bone.getAsJsonObject().getAsJsonArray("cubes")) {
                    for (double[] corner : turnedCorners(cube.getAsJsonObject())) {
                        top = Math.max(top, corner[1]);
                    }
                }
            }
            assertTrue(top < floor, name + " reaches " + top + " px, through the map at " + floor);
        }
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
