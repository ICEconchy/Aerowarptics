package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two sheets a Rift Storm draws over a drive: the void, and the violet rim round it.
 *
 * <p>Drawn through vanilla's dissolve shader, which keeps a texel wherever its alpha is at least a threshold,
 * so the alpha of these sheets is not a transparency but the order the void takes each texel in. Every way
 * that can go wrong draws: sheets whose alphas differ put rim where there is no void and void with no rim; a
 * texel off every face's UV rectangle is wasted; one the core or the needle uses eats the rift or the
 * reading, which the storm is not meant to touch - the artist's sheet shares some texels between the
 * needle and the frame, and those stay clean too. And a texel at a full 255 would pass the threshold of a
 * storm that is not there. All of it is read off the shipped files and the shipped model, not the script that
 * wrote them, so a hand edit to either is caught too.
 */
class DriveVoidTextureTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/aerowarptics");
    private static final Path VOID = ASSETS.resolve("textures/block/rift_drive_void.png");
    private static final Path RIM = ASSETS.resolve("textures/block/rift_drive_void_rim.png");
    private static final Path DRIVE = ASSETS.resolve("textures/block/rift_drive_mk_i.png");
    private static final Path GEO = ASSETS.resolve("geo/rift_drive.geo.json");
    /** The artist's names for the rift and the needle, as {@code tools/rift_drive_void.py} has them. */
    private static final Set<String> CLEAN = Set.of("cube", "needle");

    /** One face's UV rectangle, in texels, which may be fractional: a texel is on it if its centre is. */
    private record Box(String bone, double u0, double v0, double u1, double v1) {
        boolean contains(int x, int y) {
            return x + 0.5D >= u0 && x + 0.5D < u1 && y + 0.5D >= v0 && y + 0.5D < v1;
        }
    }

    private static BufferedImage image(Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<Box> boxes() {
        try (Reader reader = Files.newBufferedReader(GEO, StandardCharsets.UTF_8)) {
            JsonObject geometry = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            List<Box> boxes = new ArrayList<>();
            for (JsonElement boneElement : geometry.getAsJsonArray("bones")) {
                JsonObject bone = boneElement.getAsJsonObject();
                if (!bone.has("cubes")) {
                    continue;
                }
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject faces = cubeElement.getAsJsonObject().getAsJsonObject("uv");
                    for (String face : faces.keySet()) {
                        JsonArray uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
                        JsonArray size = faces.getAsJsonObject(face).getAsJsonArray("uv_size");
                        double u = uv.get(0).getAsDouble();
                        double v = uv.get(1).getAsDouble();
                        double w = size.get(0).getAsDouble();
                        double h = size.get(1).getAsDouble();
                        boxes.add(new Box(bone.get("name").getAsString(),
                                Math.min(u, u + w), Math.min(v, v + h), Math.max(u, u + w), Math.max(v, v + h)));
                    }
                }
            }
            return boxes;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    @Test
    void bothSheetsAreTheDrivesSize() {
        BufferedImage drive = image(DRIVE);
        for (Path path : List.of(VOID, RIM)) {
            BufferedImage sheet = image(path);
            assertEquals(drive.getWidth(), sheet.getWidth(), path.toString());
            assertEquals(drive.getHeight(), sheet.getHeight(), path.toString());
        }
    }

    @Test
    void theRimAndTheVoidAreTakenInTheSameOrder() {
        BufferedImage voidSheet = image(VOID);
        BufferedImage rimSheet = image(RIM);
        List<String> problems = new ArrayList<>();
        for (int y = 0; y < voidSheet.getHeight(); y++) {
            for (int x = 0; x < voidSheet.getWidth(); x++) {
                if (alpha(voidSheet, x, y) != alpha(rimSheet, x, y)) {
                    problems.add(x + "," + y);
                }
            }
        }
        assertTrue(problems.isEmpty(), "the two sheets' alphas differ at " + problems);
    }

    @Test
    void everyTakenTexelIsOnTheMetalAndNoneOnTheRiftOrTheNeedle() {
        List<Box> boxes = boxes();
        BufferedImage sheet = image(VOID);
        List<String> problems = new ArrayList<>();
        int taken = 0;
        int metal = 0;
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                int tx = x;
                int ty = y;
                if (boxes.stream().anyMatch(box -> box.contains(tx, ty))
                        && boxes.stream().noneMatch(box -> CLEAN.contains(box.bone()) && box.contains(tx, ty))) {
                    metal++;
                }
                int a = alpha(sheet, x, y);
                if (a == 0) {
                    continue;
                }
                taken++;
                if (a == 255) {
                    problems.add(x + "," + y + " is at 255, which passes the threshold of no storm at all");
                }
                boolean onMetal = false;
                for (Box box : boxes) {
                    if (box.contains(x, y)) {
                        if (CLEAN.contains(box.bone())) {
                            problems.add(x + "," + y + " eats into " + box.bone());
                        }
                        onMetal |= !CLEAN.contains(box.bone());
                    }
                }
                if (!onMetal) {
                    problems.add(x + "," + y + " is on no cube");
                }
            }
        }
        assertTrue(taken * 4 > metal, "only " + taken + " of " + metal
                + " metal texels take any void - has the overlay stopped being painted?");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
