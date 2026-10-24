package uk.co.iceconchy.aerowarptics.plotter;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's model is the artist's, and the arithmetic that aims it is {@link PlotGeometry}'s and
 * {@link PlotterWorks}'; this reads the shipped geometry back to hold the two together.
 *
 * <p>The one that matters most is the lens: the entry rift is thrown from {@link PlotGeometry#lens}, and if that is
 * not where the yoke tips, the beam leaves the machine from somewhere the rings are not and the rift tears where they
 * are not pointing. The next is the spin: the rings and the ball turn about their own pivots, and a pivot off the
 * beam swings them round it instead of turning them in place.
 */
class PlotterRigTest {

    private static final Path GEO = Path.of("src/main/resources/assets/aerowarptics/geo/rift_plotter.geo.json");
    private static final Path RENDERER =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/render/RiftPlotterRenderer.java");

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

    @Test
    void everyBoneTheRendererPosesExists() throws IOException {
        Map<String, JsonObject> bones = bones();
        String renderer = Files.readString(RENDERER, StandardCharsets.UTF_8);
        for (String name : List.of("turntable", "elevation", "pinion", "ring_middle", "ball")) {
            assertNotNull(bones.get(name), "the model has no bone " + name);
            assertTrue(renderer.contains("\"" + name + "\""), "the renderer never poses " + name);
        }
    }

    /** The yoke tips where the entry is thrown from, on the machine's upright axis. */
    @Test
    void theYokeTipsWhereTheEntryIsThrownFrom() {
        double[] elevation = pivot(bones().get("elevation"));
        assertEquals(PlotGeometry.LENS_HEIGHT * 16.0D, elevation[1], 1.0e-9D);
        assertEquals(0.0D, elevation[0], 1.0e-9D);
        assertEquals(0.0D, elevation[2], 1.0e-9D);
    }

    /**
     * The rings stand on their edge in the yoke: the bone that carries them is turned a quarter about x, which lays the
     * model's own y - the axis the rings were drawn round - along the beam. Where a point of theirs ends up is that
     * turn, about that bone's pivot.
     */
    private static double[] standUp(double[] point) {
        JsonObject ring = bones().get("ring");
        JsonArray rotation = ring.getAsJsonArray("rotation");
        assertEquals(90.0D, rotation.get(0).getAsDouble(), 1.0e-9D, "the rings no longer stand a quarter turn up");
        assertEquals(0.0D, rotation.get(1).getAsDouble(), 1.0e-9D);
        assertEquals(0.0D, rotation.get(2).getAsDouble(), 1.0e-9D);
        double[] about = pivot(ring);
        // GeckoLib negates a turn about x, so in the file's numbers a positive one is clockwise looking down +x.
        double dy = point[1] - about[1];
        double dz = point[2] - about[2];
        return new double[]{point[0], about[1] + dz, about[2] - dy};
    }

    /** The ball is at the focus, on the beam, where the arithmetic starts it. */
    @Test
    void theBallIsAtTheFocus() {
        JsonObject ball = bones().get("ball");
        JsonObject cube = ball.getAsJsonArray("cubes").get(0).getAsJsonObject();
        JsonArray origin = cube.getAsJsonArray("origin");
        JsonArray size = cube.getAsJsonArray("size");
        double[] middle = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            middle[axis] = origin.get(axis).getAsDouble() + size.get(axis).getAsDouble() / 2.0D;
        }
        if (cube.has("rotation")) {
            // A turn about its own upright, about its own pivot, moves its middle round that pivot.
            double turn = Math.toRadians(cube.getAsJsonArray("rotation").get(1).getAsDouble());
            JsonArray about = cube.getAsJsonArray("pivot");
            double x = middle[0] - about.get(0).getAsDouble();
            double z = middle[2] - about.get(2).getAsDouble();
            middle[0] = x * Math.cos(turn) + z * Math.sin(turn) + about.get(0).getAsDouble();
            middle[2] = -x * Math.sin(turn) + z * Math.cos(turn) + about.get(2).getAsDouble();
        }
        double[] at = standUp(middle);
        assertEquals(0.0D, at[0], 0.25D, "the ball is off the beam");
        assertEquals(PlotGeometry.LENS_HEIGHT * 16.0D, at[1], 0.25D, "the ball is off the beam");
        assertEquals(PlotGeometry.FOCUS * 16.0D, at[2], 0.25D, "the ball is not where the beam starts");
    }

    /** What spins, spins in place: the rings' and the ball's pivots are on the beam, once the rings are stood up. */
    @Test
    void theRingsAndTheBallTurnAboutTheBeam() {
        for (String name : List.of("ring_middle", "ball")) {
            JsonObject bone = bones().get(name);
            assertTrue(!bone.has("rotation"), name + " has a turn of its own, which moving its pivot would change");
            double[] at = standUp(pivot(bone));
            assertEquals(0.0D, at[0], 0.25D, name + " turns about a point off the beam");
            assertEquals(PlotGeometry.LENS_HEIGHT * 16.0D, at[1], 0.25D, name + " turns about a point off the beam");
        }
    }

    /** The yoke turns on the turntable: aim is yaw, then pitch, and everything that spins rides in the yoke. */
    @Test
    void theAimIsYawThenPitch() {
        Map<String, JsonObject> bones = bones();
        assertEquals("turntable", bones.get("elevation").get("parent").getAsString());
        assertEquals("elevation", bones.get("ring").get("parent").getAsString());
        assertEquals("ring", bones.get("ring_middle").get("parent").getAsString());
        assertEquals("ring_middle", bones.get("ring_inner").get("parent").getAsString());
        assertEquals("ring", bones.get("ball").get("parent").getAsString());
        assertEquals(0.0D, pivot(bones.get("turntable"))[0], 1.0e-9D, "the turntable turns off the machine's axis");
        assertEquals(0.0D, pivot(bones.get("turntable"))[2], 1.0e-9D, "the turntable turns off the machine's axis");
    }
}
