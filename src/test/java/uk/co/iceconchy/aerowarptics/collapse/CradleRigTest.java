package uk.co.iceconchy.aerowarptics.collapse;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Collapse Cradle's model is the artist's, and the arithmetic that poses it is {@link CradleWorks}'; this reads
 * the shipped geometry back to hold the two together.
 *
 * <p>Every way they drift apart draws, wrongly: a ring a pixel wider than {@link CradleWorks} thinks and the orbit
 * and the sparks are laid out round a ring that is not there; a ring that sweeps further than the one outside it
 * leaves room for and the rings grind through each other as they tumble; a bone renamed and the renderer poses
 * nothing and says nothing.
 */
class CradleRigTest {

    private static final Path GEO = Path.of("src/main/resources/assets/aerowarptics/geo/collapse_cradle.geo.json");
    private static final Path RENDERER =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/render/CollapseCradleRenderer.java");

    private static Map<String, JsonObject> bones() {
        try (Reader reader = Files.newBufferedReader(GEO, StandardCharsets.UTF_8)) {
            JsonObject geometry = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            Map<String, JsonObject> bones = new HashMap<>();
            for (JsonElement bone : geometry.getAsJsonArray("bones")) {
                bones.put(bone.getAsJsonObject().get("name").getAsString(), bone.getAsJsonObject());
            }
            return bones;
        } catch (IOException e) {
            throw new AssertionError("could not read " + GEO, e);
        }
    }

    private static double[] pivot(JsonObject bone) {
        JsonArray pivot = bone.getAsJsonArray("pivot");
        return new double[]{pivot.get(0).getAsDouble(), pivot.get(1).getAsDouble(), pivot.get(2).getAsDouble()};
    }

    /** Every bone the renderer names is in the model: a missing one is posed by nobody and missed by nobody. */
    @Test
    void everyBoneTheRendererPosesExists() throws IOException {
        Map<String, JsonObject> bones = bones();
        List<String> wanted = new ArrayList<>(List.of("sun", "ring_outer", "ring_middle", "ring_inner", "lock"));
        for (int i = 0; i < 4; i++) {
            wanted.add("piston_" + i);
        }
        for (String name : wanted) {
            assertNotNull(bones.get(name), "the model has no bone " + name);
        }
        String renderer = Files.readString(RENDERER, StandardCharsets.UTF_8);
        for (String name : new String[]{"\"sun\"", "\"ring_outer\"", "\"ring_middle\"", "\"ring_inner\"", "\"lock\"",
                "\"piston_\""}) {
            assertTrue(renderer.contains(name), "the renderer never poses " + name);
        }
    }

    /** Each ring turns about the middle of the hole, is as wide and as tall as the arithmetic says, and nests. */
    @Test
    void theRingsAreWhereTheArithmeticThinksTheyAre() {
        Map<String, JsonObject> bones = bones();
        assertEquals("ring_outer", bones.get("ring_middle").get("parent").getAsString());
        assertEquals("ring_middle", bones.get("ring_inner").get("parent").getAsString());
        Map<String, double[]> rings = Map.of("ring_outer", CradleWorks.OUTER_RING, "ring_middle", CradleWorks.MIDDLE_RING,
                "ring_inner", CradleWorks.INNER_RING);
        for (Map.Entry<String, double[]> ring : rings.entrySet()) {
            JsonObject bone = bones.get(ring.getKey());
            double[] pivot = pivot(bone);
            assertEquals(0.0D, pivot[0], 1.0e-9D);
            assertEquals(CradleWorks.CENTRE, pivot[1], 1.0e-9D, ring.getKey() + " does not turn about the hole");
            assertEquals(0.0D, pivot[2], 1.0e-9D);
            double[] measured = measure(bone);
            assertEquals(ring.getValue()[0], measured[0], 0.01D, ring.getKey() + "'s inside");
            assertEquals(ring.getValue()[1], measured[1], 0.01D, ring.getKey() + "'s outside");
            for (JsonElement element : bone.getAsJsonArray("cubes")) {
                JsonObject cube = element.getAsJsonObject();
                double low = cube.getAsJsonArray("origin").get(1).getAsDouble();
                double height = cube.getAsJsonArray("size").get(1).getAsDouble();
                assertEquals(ring.getValue()[2], height, 1.0e-9D, ring.getKey() + "'s height");
                assertEquals(CradleWorks.CENTRE, low + height / 2.0D, 1.0e-9D, ring.getKey() + " is not level with the hole");
            }
        }
    }

    /**
     * Turned any way about its own axis, a ring's furthest corner stays inside the ring round it, and the core inside
     * the innermost: the rings tumble inside one another through a collapse, and nothing of them may meet.
     */
    @Test
    void theRingsTumbleClearOfEachOtherAndOfTheCore() {
        Map<String, JsonObject> bones = bones();
        double outerInside = measure(bones.get("ring_outer"))[0];
        double middle = measure(bones.get("ring_middle"))[2];
        double middleInside = measure(bones.get("ring_middle"))[0];
        double inner = measure(bones.get("ring_inner"))[2];
        double innerInside = measure(bones.get("ring_inner"))[0];
        assertTrue(middle < outerInside, "the middle ring reaches " + middle + " px, into the outer's " + outerInside);
        assertTrue(inner < middleInside, "the inner ring reaches " + inner + " px, into the middle's " + middleInside);
        assertTrue(CradleWorks.CORE_HALF < innerInside,
                "the core is drawn " + CradleWorks.CORE_HALF + " px each way, into the inner ring's " + innerInside);
    }

    /**
     * A ring's nearest inner face to its axis, its furthest outer face, and its furthest corner, in pixels across the
     * plane it lies in. Each segment is turned about its own pivot first, the way GeckoLib draws it: in the file's
     * own numbers a positive turn about y is anticlockwise looking down.
     */
    private static double[] measure(JsonObject bone) {
        double[] pivot = pivot(bone);
        double inside = Double.MAX_VALUE;
        double outside = 0.0D;
        double corner = 0.0D;
        for (JsonElement element : bone.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            double turn = cube.has("rotation") ? Math.toRadians(cube.getAsJsonArray("rotation").get(1).getAsDouble()) : 0.0D;
            double turnX = cube.has("pivot") ? cube.getAsJsonArray("pivot").get(0).getAsDouble() : 0.0D;
            double turnZ = cube.has("pivot") ? cube.getAsJsonArray("pivot").get(2).getAsDouble() : 0.0D;
            double[][] points = new double[4][];
            int n = 0;
            double cx = 0.0D;
            double cz = 0.0D;
            for (int i = 0; i < 2; i++) {
                for (int k = 0; k < 2; k++) {
                    double x = origin.get(0).getAsDouble() + i * size.get(0).getAsDouble() - turnX;
                    double z = origin.get(2).getAsDouble() + k * size.get(2).getAsDouble() - turnZ;
                    double tx = x * Math.cos(turn) + z * Math.sin(turn) + turnX - pivot[0];
                    double tz = -x * Math.sin(turn) + z * Math.cos(turn) + turnZ - pivot[2];
                    points[n++] = new double[]{tx, tz};
                    cx += tx / 4.0D;
                    cz += tz / 4.0D;
                }
            }
            double out = Math.hypot(cx, cz);
            for (double[] point : points) {
                double along = (point[0] * cx + point[1] * cz) / out;
                inside = Math.min(inside, along);
                outside = Math.max(outside, along);
                corner = Math.max(corner, Math.hypot(point[0], point[1]));
            }
        }
        return new double[]{inside, outside, corner};
    }

    /** The plinth's top is where the arithmetic stands the sun gear. */
    @Test
    void theDeckIsWhereTheSunGearStands() {
        double top = 0.0D;
        for (JsonElement element : bones().get("plinth").getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            if (size.get(0).getAsDouble() >= 40.0D && size.get(2).getAsDouble() >= 40.0D) {
                top = Math.max(top, origin.get(1).getAsDouble() + size.get(1).getAsDouble());
            }
        }
        assertEquals(CradleWorks.DECK, top, 1.0e-9D);
    }
}
