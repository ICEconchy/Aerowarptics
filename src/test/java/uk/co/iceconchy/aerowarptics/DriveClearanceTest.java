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
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing on the Rift Drive turns through anything else.
 *
 * <p>The drive is a tesseract with an armillary inside it: an outer cage the size of the block, an
 * inner cage hung in the middle of it on eight diagonals, a flat gear and an upright hoop turning
 * about perpendicular axes, a shaft through the north and south faces, the rift itself, and a needle
 * across the top. Seven bones inside sixteen pixels, and every radius in
 * {@code tools/rift_drive_model.py} is an answer to "what will this pass through".
 *
 * <p>Which is why this exists rather than trusting the generator that already checks it. A model
 * that clips still loads, still renders, and still looks broadly right; the only symptom is a gear
 * tooth flickering through a strut once a second, on a machine nobody is looking at straight on.
 * That is exactly the class of failure this suite is for. The geometry is read from the shipped file
 * rather than from the script, so a hand edit fails here too.
 */
class DriveClearanceTest {

    private static final Path GEO =
            Path.of("src/main/resources/assets/aerowarptics/geo/rift_drive.geo.json");

    /** The middle of the block, in model pixels. Every pivot in this model sits here. */
    private static final double CY = 8.0D;

    /**
     * Which bones turn, and about which axis - 0 for X, 1 for Y, 2 for Z.
     *
     * <p>Named here rather than read out of the animation file on purpose. This is the claim being
     * tested: these are the parts that move. A bone that starts turning without being added here is
     * a bone whose clearance nobody has checked.
     */
    private static final Map<String, Integer> SPINNING =
            Map.of("shafts", 2, "gear_wheel", 1, "gear_hoop", 2, "needle", 1);

    /**
     * The two pairs that are meant to touch.
     *
     * <p>The diagonals are bolted into the outer cage's corners - that join is what makes the drawing
     * a tesseract rather than a box inside a box - and the rift strains against the bars of its own
     * cage once it is fully charged, which is the picture and not a fault.
     */
    private static final Set<Set<String>> EXEMPT = Set.of(
            Set.of("cell", "shell"),
            Set.of("core", "cell"));

    /**
     * How much room the parts that change size are given.
     *
     * <p>The inner cage breathes a few per cent in the idle clips, and {@code RiftDriveRenderer}
     * swells the rift with the charge up to its {@code MAX_CORE_SCALE}. Both are measured at the
     * largest they are ever drawn rather than as authored, because "it fits until the drive is
     * charged" is not fitting.
     */
    private static double margin(String bone) {
        return switch (bone) {
            case "cell" -> 0.06D;
            case "core" -> 0.55D;
            default -> 0.0D;
        };
    }

    // ------------------------------------------------------------------------- the shapes

    /**
     * One cube, twice: as authored, and as its own rotation leaves it.
     *
     * <p>Both are needed and they are not interchangeable. A swept annulus has to be measured from
     * the box as authored, because the bounding box of a turned box is bigger than the box and would
     * report a clash that is not there. A box that never turns has to be measured after its rotation,
     * because that is where its corners actually are.
     */
    private record Cube(double[] low, double[] high, double[] turnedLow, double[] turnedHigh,
                        double[] rotation) {
    }

    /**
     * What a turning bone occupies over a whole revolution: an annulus, given as its extent along the
     * axis and the range of distances from it.
     */
    private record Band(int axis, double from, double to, double near, double far) {
    }

    @Test
    void nothingTurnsThroughAnythingElse() {
        Map<String, List<Cube>> bones = readBones();
        List<String> names = new ArrayList<>(bones.keySet());
        List<String> problems = new ArrayList<>();

        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String first = names.get(i);
                String second = names.get(j);
                if (EXEMPT.contains(Set.of(first, second))) {
                    continue;
                }
                if (meets(first, bones.get(first), second, bones.get(second))) {
                    problems.add(first + " passes through " + second);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A turning bone's cubes may only be turned about the axis that bone turns on.
     *
     * <p>This is what makes the arithmetic above exact rather than approximate: a box turned about
     * the axis it will be swept about sweeps the same annulus it would have swept unturned, which is
     * the whole reason the rings can be twelve boxes at twelve angles. A cube turned about anything
     * else would be measured against a circle it is not on.
     */
    @Test
    void aTurningBonesCubesOnlyTurnOnItsOwnAxis() {
        Set<String> offenders = new TreeSet<>();
        for (Map.Entry<String, List<Cube>> bone : readBones().entrySet()) {
            Integer axis = SPINNING.get(bone.getKey());
            for (Cube cube : bone.getValue()) {
                for (int other = 0; other < 3; other++) {
                    if (cube.rotation()[other] != 0.0D && (axis == null || axis != other)) {
                        offenders.add(bone.getKey());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "cubes turn off their bone's axis: " + offenders);
    }

    /**
     * Every cube that turns pivots on the middle of the block.
     *
     * <p>The same assumption from the other side. An annulus is only an annulus about the axis its
     * pivot sits on, so a cube turned about a pivot somewhere else would be checked against the wrong
     * circle and would pass a test it should fail.
     */
    @Test
    void everyTurnedCubePivotsOnTheBlockCentre() {
        Set<String> offenders = new TreeSet<>();
        for (JsonElement element : geometry().getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            for (JsonElement cubeElement : cubes(bone)) {
                JsonObject cube = cubeElement.getAsJsonObject();
                if (!cube.has("rotation")) {
                    continue;
                }
                JsonArray pivot = cube.getAsJsonArray("pivot");
                if (pivot == null
                        || pivot.get(0).getAsDouble() != 0.0D
                        || pivot.get(1).getAsDouble() != CY
                        || pivot.get(2).getAsDouble() != 0.0D) {
                    offenders.add(bone.get("name").getAsString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "turned cubes pivot off the block centre: " + offenders);
    }

    /**
     * Every bone this test knows about is actually in the model, and there are no others.
     *
     * <p>Without this, renaming a bone would quietly switch its clearance check off: the pair would
     * stop being compared and the test above would still pass, which is the worst way for a guard to
     * fail.
     */
    @Test
    void theBonesThisTestKnowsAboutAreTheBonesTheModelHas() {
        Set<String> present = readBones().keySet();
        for (String bone : SPINNING.keySet()) {
            assertTrue(present.contains(bone), "no bone named \"" + bone + "\" in " + GEO);
        }
        for (Set<String> pair : EXEMPT) {
            for (String bone : pair) {
                assertTrue(present.contains(bone), "no bone named \"" + bone + "\" in " + GEO);
            }
        }
        assertEquals(Set.of("shell", "shafts", "cell", "gear_wheel", "gear_hoop", "core", "needle"),
                Set.copyOf(present), "the drive gained, lost or renamed a bone");
    }

    // ----------------------------------------------------------------------- the arithmetic

    private static boolean meets(String first, List<Cube> one, String second, List<Cube> other) {
        Integer firstAxis = SPINNING.get(first);
        Integer secondAxis = SPINNING.get(second);

        if (firstAxis != null && secondAxis != null) {
            for (Band a : bands(one, firstAxis)) {
                for (Band b : bands(other, secondAxis)) {
                    if (bandMeetsBand(a, b)) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (firstAxis != null) {
            return bandMeetsBoxes(bands(one, firstAxis), other);
        }
        if (secondAxis != null) {
            return bandMeetsBoxes(bands(other, secondAxis), one);
        }
        for (Cube a : one) {
            for (Cube b : other) {
                if (overlaps(a.turnedLow()[0], a.turnedHigh()[0], b.turnedLow()[0], b.turnedHigh()[0])
                        && overlaps(a.turnedLow()[1], a.turnedHigh()[1], b.turnedLow()[1], b.turnedHigh()[1])
                        && overlaps(a.turnedLow()[2], a.turnedHigh()[2], b.turnedLow()[2], b.turnedHigh()[2])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean bandMeetsBoxes(List<Band> bands, List<Cube> cubes) {
        for (Band band : bands) {
            for (Cube cube : cubes) {
                if (bandMeetsBox(band, cube)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An annulus of revolution against a box. Exact.
     *
     * <p>A box's distance from the axis runs over one connected interval - from the nearest point of
     * its cross section out to its furthest corner - so the two solids meet exactly when their
     * extents along the axis overlap and those two radial intervals do.
     */
    private static boolean bandMeetsBox(Band band, Cube cube) {
        double[] low = cube.turnedLow();
        double[] high = cube.turnedHigh();
        if (!overlaps(band.from(), band.to(), low[band.axis()], high[band.axis()])) {
            return false;
        }
        double[] radial = radialRange(low, high, band.axis());
        return overlaps(band.near(), band.far(), radial[0], radial[1]);
    }

    /**
     * Two annuli. Coaxial ones are exact; perpendicular ones are walked at a hundredth of a pixel.
     *
     * <p>Perpendicular is the case this model turns on, and the one worth spelling out. Write p for a
     * point measured from the middle of the block. The flat gear asks that p sits within a band of
     * distances from the vertical, and inside a slice of it; the upright hoop asks the same about the
     * shaft axis. Fix the two coordinates the slices constrain and one is left over, with an interval
     * demanded of its square by each ring - so the rings meet exactly when both intervals can be
     * satisfied at once. Walking the two fixed coordinates is short and can be checked by eye, which
     * the closed form cannot.
     */
    private static boolean bandMeetsBand(Band first, Band second) {
        if (first.axis() == second.axis()) {
            return overlaps(first.from(), first.to(), second.from(), second.to())
                    && overlaps(first.near(), first.far(), second.near(), second.far());
        }
        double step = 0.01D;
        int alongSteps = Math.max(1, (int) Math.ceil((first.to() - first.from()) / step));
        int acrossSteps = Math.max(1, (int) Math.ceil((second.to() - second.from()) / step));
        for (int i = 0; i <= alongSteps; i++) {
            double along = first.from() + (first.to() - first.from()) * i / alongSteps;
            for (int j = 0; j <= acrossSteps; j++) {
                double across = second.from() + (second.to() - second.from()) * j / acrossSteps;
                double low = Math.max(0.0D, Math.max(square(first.near()) - square(across),
                        square(second.near()) - square(along)));
                double high = Math.min(square(first.far()) - square(across),
                        square(second.far()) - square(along));
                if (high - low > 1.0E-6D) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A bone's cubes as the annuli they sweep, measured from the boxes as authored.
     *
     * <p>As authored, not as turned: a box turned about the axis it will be swept about sweeps
     * exactly what it would have swept unturned, and its bounding box after turning is larger than
     * either. {@link #aTurningBonesCubesOnlyTurnOnItsOwnAxis()} is what makes that safe to rely on.
     */
    private static List<Band> bands(List<Cube> cubes, int axis) {
        List<Band> bands = new ArrayList<>();
        for (Cube cube : cubes) {
            double[] radial = radialRange(cube.low(), cube.high(), axis);
            bands.add(new Band(axis, cube.low()[axis], cube.high()[axis], radial[0], radial[1]));
        }
        return bands;
    }

    /** How near and how far a box's cross section reaches from the axis running through the centre. */
    private static double[] radialRange(double[] low, double[] high, int axis) {
        double near = 0.0D;
        double far = 0.0D;
        for (int other = 0; other < 3; other++) {
            if (other == axis) {
                continue;
            }
            double outside = Math.max(low[other], Math.max(0.0D, -high[other]));
            near += outside * outside;
            double reach = Math.max(Math.abs(low[other]), Math.abs(high[other]));
            far += reach * reach;
        }
        return new double[] {Math.sqrt(near), Math.sqrt(far)};
    }

    private static double square(double value) {
        return value * value;
    }

    /** Two intervals sharing more than a rounding error. Touching is not sharing. */
    private static boolean overlaps(double a0, double a1, double b0, double b1) {
        return Math.min(a1, b1) - Math.max(a0, b0) > 1.0E-6D;
    }

    // ------------------------------------------------------------------- reading the model

    /** Every bone's cubes, measured from the middle of the block and grown by that bone's room. */
    private static Map<String, List<Cube>> readBones() {
        Map<String, List<Cube>> bones = new java.util.LinkedHashMap<>();
        for (JsonElement element : geometry().getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            double grow = 1.0D + margin(name);
            List<Cube> read = new ArrayList<>();
            for (JsonElement cubeElement : cubes(bone)) {
                read.add(cube(cubeElement.getAsJsonObject(), grow));
            }
            bones.put(name, read);
        }
        return bones;
    }

    private static JsonArray cubes(JsonObject bone) {
        return bone.has("cubes") ? bone.getAsJsonArray("cubes") : new JsonArray();
    }

    private static Cube cube(JsonObject cube, double grow) {
        JsonArray origin = cube.getAsJsonArray("origin");
        JsonArray size = cube.getAsJsonArray("size");
        double[] rotation = new double[3];
        if (cube.has("rotation")) {
            JsonArray turn = cube.getAsJsonArray("rotation");
            for (int axis = 0; axis < 3; axis++) {
                rotation[axis] = Math.toRadians(turn.get(axis).getAsDouble());
            }
        }

        double[] low = new double[3];
        double[] high = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double from = origin.get(axis).getAsDouble() - (axis == 1 ? CY : 0.0D);
            low[axis] = from * grow;
            high[axis] = (from + size.get(axis).getAsDouble()) * grow;
        }

        double[] turnedLow = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] turnedHigh = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int corner = 0; corner < 8; corner++) {
            double[] point = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                point[axis] = ((corner >> axis) & 1) == 1 ? high[axis] : low[axis];
            }
            turn(point, rotation);
            for (int axis = 0; axis < 3; axis++) {
                turnedLow[axis] = Math.min(turnedLow[axis], point[axis]);
                turnedHigh[axis] = Math.max(turnedHigh[axis], point[axis]);
            }
        }
        return new Cube(low, high, turnedLow, turnedHigh, rotation);
    }

    /** A cube's own rotation, about the middle of the block, which is where its pivot always is. */
    private static void turn(double[] point, double[] rotation) {
        if (rotation[0] != 0.0D) {
            double y = point[1];
            double z = point[2];
            point[1] = y * Math.cos(rotation[0]) - z * Math.sin(rotation[0]);
            point[2] = y * Math.sin(rotation[0]) + z * Math.cos(rotation[0]);
        }
        if (rotation[1] != 0.0D) {
            double x = point[0];
            double z = point[2];
            point[0] = x * Math.cos(rotation[1]) + z * Math.sin(rotation[1]);
            point[2] = -x * Math.sin(rotation[1]) + z * Math.cos(rotation[1]);
        }
        if (rotation[2] != 0.0D) {
            double x = point[0];
            double y = point[1];
            point[0] = x * Math.cos(rotation[2]) - y * Math.sin(rotation[2]);
            point[1] = x * Math.sin(rotation[2]) + y * Math.cos(rotation[2]);
        }
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
