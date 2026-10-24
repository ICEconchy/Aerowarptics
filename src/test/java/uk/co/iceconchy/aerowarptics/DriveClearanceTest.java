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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rift in the middle of the Rift Drive never passes through what is round it.
 *
 * <p>The drive is the artist's: a brass frame the size of the block, a stone housing inside it, a ring turning
 * about the shaft's axis through the housing's open chamber, and the rift - a small cube the clips spin and
 * swell - hung in the middle of the ring. The ring is meant to run through the housing; that is how it shows
 * at the front and back and nowhere else. The rift is not meant to touch anything, and the clips take it very
 * nearly as large as it can go: at its largest it clears the ring by about a tenth of a pixel.
 *
 * <p>A model that clips still loads, still renders and still looks broadly right; the only symptom is a corner
 * of the rift flickering through the ring once a turn, on a machine nobody is looking at straight on. So this
 * reads the shipped model and clips, measures the rift at the largest any clip draws it, turned any way at all,
 * and holds it inside the ring and clear of everything else. A new model or a livelier clip that breaks that
 * fails here rather than in somebody's hangar.
 */
class DriveClearanceTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/aerowarptics");
    private static final Path GEO = ASSETS.resolve("geo/rift_drive.geo.json");
    private static final Path ANIMATIONS = ASSETS.resolve("animations/rift_drive.animation.json");
    private static final Path RENDERER =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/render/RiftDriveRenderer.java");

    /** The artist's names: the rift, and the ring that turns round it. */
    private static final String CORE = "cube";
    private static final String RING = "ring";

    /** An axis-aligned box, in model pixels, turned about {@code z} by {@code turn} degrees about a pivot. */
    private record Cube(String bone, double[] min, double[] max, double turn, double pivotX, double pivotY) {
    }

    @Test
    void theRiftAtItsLargestStaysInsideTheRing() {
        List<Cube> ring = cubes(RING);
        JsonObject ringBone = bone(RING);
        double axisX = ringBone.getAsJsonArray("pivot").get(0).getAsDouble();
        double axisY = ringBone.getAsJsonArray("pivot").get(1).getAsDouble();

        // However the ring has turned, nothing of it comes nearer its axis than this.
        double inner = Double.MAX_VALUE;
        for (Cube cube : ring) {
            inner = Math.min(inner, distanceInPlane(cube, axisX, axisY));
        }

        double reach = coreReach();
        double[] corePivot = pivot(bone(CORE));
        double offAxis = Math.hypot(corePivot[0] - axisX, corePivot[1] - axisY);
        assertTrue(reach + offAxis < inner, "the rift reaches " + (reach + offAxis)
                + " px from the ring's axis at its largest, and the ring's inside is only " + inner + " px out");
    }

    @Test
    void theRiftAtItsLargestTouchesNothingThatStandsStill() {
        double reach = coreReach();
        double[] centre = pivot(bone(CORE));
        List<String> problems = new ArrayList<>();
        for (JsonElement element : geometry().getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            if (name.equals(CORE) || name.equals(RING) || !bone.has("cubes")) {
                continue;
            }
            for (Cube cube : cubes(name)) {
                double gap = distanceToBox(cube, centre);
                if (gap <= reach) {
                    problems.add(name + " is " + gap + " px from the rift's middle, inside its " + reach + " px reach");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The ring turns about the drive's shaft axis and nothing else, so the facing a drive is placed at is the one it turns on. */
    @Test
    void theRingOnlyEverTurnsAboutTheShaftAxis() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonElement> clip : clips().entrySet()) {
            JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (bones == null || !bones.has(RING)) {
                continue;
            }
            JsonObject ring = bones.getAsJsonObject(RING);
            if (ring.has("rotation")) {
                for (double[] value : keyframes(ring.get("rotation"))) {
                    if (Math.abs(value[0]) > 1.0E-6D || Math.abs(value[1]) > 1.0E-6D) {
                        problems.add(clip.getKey() + " turns the ring off its axis");
                    }
                }
            }
            if (ring.has("position") || ring.has("scale")) {
                problems.add(clip.getKey() + " moves or resizes the ring, which this test does not measure");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The renderer only ever shrinks the rift from what the clip draws, so measuring the clips is measuring the rift. */
    @Test
    void theRendererNeverDrawsTheRiftLargerThanTheClips() throws IOException {
        Matcher share = Pattern.compile("EMPTY_CORE_SHARE\\s*=\\s*([0-9.]+)F").matcher(Files.readString(RENDERER));
        assertTrue(share.find(), "RiftDriveRenderer no longer says how small an empty drive's rift is drawn");
        double value = Double.parseDouble(share.group(1));
        assertTrue(value > 0.0D && value <= 1.0D, "the charge would draw the rift at " + value + " of the clip's size");
    }

    // ------------------------------------------------------------------ the rift's reach

    /**
     * How far from its pivot any part of the rift can reach: half the diagonal of its cube, at the largest scale
     * any clip gives it. Half the diagonal because the clips spin it on two axes at once, so every corner points
     * every way sooner or later.
     */
    private static double coreReach() {
        double scale = 1.0D;
        for (Map.Entry<String, JsonElement> clip : clips().entrySet()) {
            JsonObject bones = clip.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (bones == null || !bones.has(CORE) || !bones.getAsJsonObject(CORE).has("scale")) {
                continue;
            }
            JsonElement keys = bones.getAsJsonObject(CORE).get("scale");
            assertTrue(!keys.toString().contains("Back") && !keys.toString().contains("Elastic"),
                    clip.getKey() + " eases the rift's size past its keyframes, which this test cannot measure");
            for (double[] value : keyframes(keys)) {
                for (double component : value) {
                    scale = Math.max(scale, Math.abs(component));
                }
            }
        }
        double[] centre = pivot(bone(CORE));
        double reach = 0.0D;
        for (Cube cube : cubes(CORE)) {
            for (int corner = 0; corner < 8; corner++) {
                double dx = ((corner & 1) == 0 ? cube.min()[0] : cube.max()[0]) - centre[0];
                double dy = ((corner & 2) == 0 ? cube.min()[1] : cube.max()[1]) - centre[1];
                double dz = ((corner & 4) == 0 ? cube.min()[2] : cube.max()[2]) - centre[2];
                reach = Math.max(reach, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        return reach * scale;
    }

    // ------------------------------------------------------------------ geometry

    /**
     * The nearest a cube comes to a point in the x-y plane, the plane the ring turns in.
     *
     * <p>The point is turned into the cube's own frame and measured against its unturned box. Bedrock's x runs
     * the other way to Minecraft's, which GeckoLib undoes by negating it; in the model file's own numbers that
     * makes a positive turn about z clockwise.
     */
    private static double distanceInPlane(Cube cube, double x, double y) {
        double radians = Math.toRadians(cube.turn());
        double dx = x - cube.pivotX();
        double dy = y - cube.pivotY();
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians) + cube.pivotX();
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians) + cube.pivotY();
        double outX = Math.max(0.0D, Math.max(cube.min()[0] - localX, localX - cube.max()[0]));
        double outY = Math.max(0.0D, Math.max(cube.min()[1] - localY, localY - cube.max()[1]));
        return Math.hypot(outX, outY);
    }

    /** The nearest an unturned cube comes to a point. */
    private static double distanceToBox(Cube cube, double[] point) {
        assertTrue(cube.turn() == 0.0D, cube.bone() + " has a turned cube; measure it the way the ring is measured");
        double sum = 0.0D;
        for (int axis = 0; axis < 3; axis++) {
            double out = Math.max(0.0D, Math.max(cube.min()[axis] - point[axis], point[axis] - cube.max()[axis]));
            sum += out * out;
        }
        return Math.sqrt(sum);
    }

    private static List<Cube> cubes(String boneName) {
        JsonObject bone = bone(boneName);
        List<Cube> cubes = new ArrayList<>();
        for (JsonElement element : bone.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            double[] min = new double[3];
            double[] max = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = origin.get(axis).getAsDouble();
                max[axis] = min[axis] + size.get(axis).getAsDouble();
            }
            double turn = 0.0D;
            double pivotX = 0.0D;
            double pivotY = 0.0D;
            if (cube.has("rotation")) {
                JsonArray rotation = cube.getAsJsonArray("rotation");
                assertTrue(rotation.get(0).getAsDouble() == 0.0D && rotation.get(1).getAsDouble() == 0.0D,
                        boneName + " has a cube turned off the z axis, which this test does not measure");
                turn = rotation.get(2).getAsDouble();
                pivotX = cube.getAsJsonArray("pivot").get(0).getAsDouble();
                pivotY = cube.getAsJsonArray("pivot").get(1).getAsDouble();
            }
            cubes.add(new Cube(boneName, min, max, turn, pivotX, pivotY));
        }
        return cubes;
    }

    private static double[] pivot(JsonObject bone) {
        JsonArray pivot = bone.getAsJsonArray("pivot");
        return new double[] {pivot.get(0).getAsDouble(), pivot.get(1).getAsDouble(), pivot.get(2).getAsDouble()};
    }

    /** Every value a keyframed channel takes: a constant, a bare vector, or keyframes by time. */
    private static List<double[]> keyframes(JsonElement channel) {
        List<double[]> values = new ArrayList<>();
        if (channel.isJsonArray()) {
            values.add(vector(channel.getAsJsonArray()));
        } else if (channel.getAsJsonObject().has("vector")) {
            values.add(vector(channel.getAsJsonObject().getAsJsonArray("vector")));
        } else {
            for (Map.Entry<String, JsonElement> key : channel.getAsJsonObject().entrySet()) {
                values.addAll(keyframes(key.getValue()));
            }
        }
        return values;
    }

    private static double[] vector(JsonArray array) {
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static JsonObject bone(String name) {
        for (JsonElement element : geometry().getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) {
                return bone;
            }
        }
        throw new AssertionError("no bone named \"" + name + "\" in " + GEO);
    }

    private static JsonObject geometry() {
        return read(GEO).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static JsonObject clips() {
        return read(ANIMATIONS).getAsJsonObject("animations");
    }

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException failure) {
            throw new AssertionError("could not read " + path, failure);
        }
    }
}
