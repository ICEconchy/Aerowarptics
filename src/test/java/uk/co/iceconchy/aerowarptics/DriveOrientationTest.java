package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Rift Drive facing up or down still draws inside its own block.
 *
 * <p>This exists because of a bug that shipped. A {@code DirectionalKineticBlock} takes all six
 * facings, and placing a drive while looking down at the ground sets {@code FACING} to {@code UP} -
 * which is right, because the shaft enters along the axis the drive faces. GeckoLib then turned the
 * model a quarter turn about the origin its block renderer sets up, which is the middle of the
 * block's <em>base</em>, not its centre. The drive's geometry stands on its base plate, so tipping
 * it about the floor put half the model under the ground and the rest in the next block along. It
 * looked like the drive had been placed rotated, and the model appeared to hang outside its own
 * selection box.
 *
 * <p>What is checked here is the geometry that makes the fix necessary and the arithmetic that makes
 * it work. Whether it <em>looks</em> right is a question for a running game, and this test does not
 * pretend to answer it - but it will fail if the model is ever re-authored centred on the block, at
 * which point {@code RiftDriveRenderer.rotateBlock} would be correcting something that no longer
 * needs correcting.
 */
class DriveOrientationTest {

    private static final Path GEO =
            Path.of("src/main/resources/assets/aerowarptics/geo/rift_drive.geo.json");

    /** Pixels per block, the units a Blockbench model is authored in. */
    private static final double PIXELS = 16.0D;

    /** A model's bounding box, in blocks, relative to the middle of the block's floor. */
    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    @Test
    void theModelStandsOnTheBlockFloorRatherThanBeingCentredOnIt() {
        Box model = driveModelBounds();

        // Bottom-anchored: this is what makes a floor pivot wrong. If this ever stops being true,
        // the renderer's override is correcting a problem that no longer exists.
        assertEquals(0.0D, model.minY(), 1.0E-6D,
                "the drive model no longer sits on the block floor - re-check RiftDriveRenderer.rotateBlock");
        assertTrue(model.maxY() > 0.5D,
                "the drive model is shorter than half a block, so tipping it about the floor would "
                        + "no longer throw it out of the block");

        // And centred horizontally, which is why the four horizontal facings were always fine.
        assertEquals(-0.5D, model.minX(), 1.0E-6D);
        assertEquals(0.5D, model.maxX(), 1.0E-6D);
        assertEquals(-0.5D, model.minZ(), 1.0E-6D);
        assertEquals(0.5D, model.maxZ(), 1.0E-6D);
    }

    /**
     * GeckoLib's own pivot throws the model out of the block, which is the bug.
     *
     * <p>Asserting the broken behaviour is deliberate. It is the half of the pair that says
     * <em>why</em> the override is there, and it fails loudly if a future GeckoLib changes where it
     * rotates from - at which point the override becomes a double correction.
     */
    @Test
    void tippingAboutTheBlockFloorLeavesTheBlock() {
        Box tipped = tipAboutFloor(driveModelBounds());

        assertTrue(tipped.minY() < 0.0D, "expected the floor pivot to sink the model below the block");
        assertTrue(tipped.maxZ() > 0.5D, "expected the floor pivot to push the model into the neighbour");
    }

    /** And the fix: the same quarter turn, taken about the middle of the block, stays put. */
    @Test
    void tippingAboutTheBlockCentreStaysInsideTheBlock() {
        for (boolean up : new boolean[] {true, false}) {
            Box tipped = tipAboutCentre(driveModelBounds(), up);
            String facing = up ? "UP" : "DOWN";

            assertTrue(tipped.minY() >= -1.0E-6D, facing + " sinks below the block: " + tipped.minY());
            assertTrue(tipped.maxY() <= 1.0D + 1.0E-6D, facing + " pokes out of the top: " + tipped.maxY());
            assertTrue(tipped.minZ() >= -0.5D - 1.0E-6D, facing + " overhangs to the north: " + tipped.minZ());
            assertTrue(tipped.maxZ() <= 0.5D + 1.0E-6D, facing + " overhangs to the south: " + tipped.maxZ());
            // The turn is about X, so the model's width is untouched.
            assertEquals(-0.5D, tipped.minX(), 1.0E-6D);
            assertEquals(0.5D, tipped.maxX(), 1.0E-6D);
        }
    }

    /**
     * The drive takes its shaft along the model's Z axis.
     *
     * <p>GeckoLib's convention is that {@code NORTH} is the unrotated model, so a machine whose
     * shafts run along Z is one whose facing and geometry agree. Shafts authored along X would mean
     * every facing was a quarter turn out, which is a far more confusing version of this same bug.
     */
    @Test
    void theShaftsRunAlongTheModelsFacingAxis() {
        JsonArray cubes = bone("shafts").getAsJsonArray("cubes");
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        for (int index = 0; index < cubes.size(); index++) {
            JsonObject cube = cubes.get(index).getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            minX = Math.min(minX, origin.get(0).getAsDouble());
            maxX = Math.max(maxX, origin.get(0).getAsDouble() + size.get(0).getAsDouble());
            minZ = Math.min(minZ, origin.get(2).getAsDouble());
            maxZ = Math.max(maxZ, origin.get(2).getAsDouble() + size.get(2).getAsDouble());
        }
        // The shafts reach the block's north and south faces, and stop well short of east and west.
        assertEquals(-8.0D, minZ, 1.0E-6D, "shafts do not reach the model's north face");
        assertEquals(8.0D, maxZ, 1.0E-6D, "shafts do not reach the model's south face");
        assertTrue(maxX - minX < 16.0D, "shafts span the model's X axis - they run the wrong way");
    }

    // ------------------------------------------------------------------ the arithmetic under test

    /**
     * A quarter turn about the middle of the block, which is what the renderer does.
     *
     * <p>{@code translate(0, +1/2, 0)}, rotate about X, {@code translate(0, -1/2, 0)}: a model point
     * {@code (x, y, z)} lands at {@code (x, 1/2 -/+ z, +/- (y - 1/2))}.
     */
    private static Box tipAboutCentre(Box box, boolean up) {
        double sign = up ? 1.0D : -1.0D;
        return fromCorners(box, (x, y, z) ->
                new double[] {x, 0.5D - sign * z, sign * (y - 0.5D)});
    }

    /** The same turn taken about the block floor, which is GeckoLib's default and the bug. */
    private static Box tipAboutFloor(Box box) {
        return fromCorners(box, (x, y, z) -> new double[] {x, -z, y});
    }

    @FunctionalInterface
    private interface Corner {
        double[] apply(double x, double y, double z);
    }

    /** Transforms all eight corners and takes the bounding box of the result. */
    private static Box fromCorners(Box box, Corner transform) {
        double[] xs = {box.minX(), box.maxX()};
        double[] ys = {box.minY(), box.maxY()};
        double[] zs = {box.minZ(), box.maxZ()};
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double[] point = transform.apply(x, y, z);
                    for (int axis = 0; axis < 3; axis++) {
                        lo[axis] = Math.min(lo[axis], point[axis]);
                        hi[axis] = Math.max(hi[axis], point[axis]);
                    }
                }
            }
        }
        return new Box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    // ------------------------------------------------------------------ reading the model

    /** The whole model's bounds, in blocks, relative to the middle of the block's floor. */
    private static Box driveModelBounds() {
        JsonArray bones = geometry().getAsJsonArray("bones");
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (int index = 0; index < bones.size(); index++) {
            JsonObject bone = bones.get(index).getAsJsonObject();
            if (!bone.has("cubes")) {
                continue;
            }
            JsonArray cubes = bone.getAsJsonArray("cubes");
            for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
                JsonObject cube = cubes.get(cubeIndex).getAsJsonObject();
                JsonArray origin = cube.getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonArray("size");
                for (int axis = 0; axis < 3; axis++) {
                    double from = origin.get(axis).getAsDouble();
                    double to = from + size.get(axis).getAsDouble();
                    lo[axis] = Math.min(lo[axis], from);
                    hi[axis] = Math.max(hi[axis], to);
                }
            }
        }
        return new Box(lo[0] / PIXELS, lo[1] / PIXELS, lo[2] / PIXELS,
                hi[0] / PIXELS, hi[1] / PIXELS, hi[2] / PIXELS);
    }

    private static JsonObject bone(String name) {
        JsonArray bones = geometry().getAsJsonArray("bones");
        for (int index = 0; index < bones.size(); index++) {
            JsonObject bone = bones.get(index).getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) {
                return bone;
            }
        }
        throw new AssertionError("no bone named \"" + name + "\" in " + GEO);
    }

    private static JsonObject geometry() {
        try (Reader reader = Files.newBufferedReader(GEO, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + GEO, e);
        }
    }
}
