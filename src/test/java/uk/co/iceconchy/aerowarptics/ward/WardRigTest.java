package uk.co.iceconchy.aerowarptics.ward;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Ward's model is the artist's, and the arithmetic that poses it is {@link WardWorks}' and
 * {@link WardStructure}'; this holds them together - the geometry as shipped, and what the totem does in each state
 * of the machine.
 */
class WardRigTest {

    private static final Path GEO = Path.of("src/main/resources/assets/aerowarptics/geo/rift_ward.geo.json");
    private static final Path RENDERER =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/render/RiftWardRenderer.java");
    private static final Path BLOCKSTATE = Path.of("src/main/resources/assets/aerowarptics/blockstates/rift_ward.json");

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

    /** A bone the renderer poses and the model lacks is a pose silently never applied. */
    @Test
    void everyBoneTheRendererPosesExists() throws IOException {
        Map<String, JsonObject> bones = bones();
        Matcher named = Pattern.compile("\"(totem_[0-9]|ring_outer|ring_middle)\"")
                .matcher(Files.readString(RENDERER, StandardCharsets.UTF_8));
        int found = 0;
        while (named.find()) {
            found++;
            assertTrue(bones.containsKey(named.group(1)), "the renderer poses a bone the model lacks: " + named.group(1));
        }
        assertEquals(WardWorks.PIECES + 2, found, "the renderer poses the totem's pieces above its foot, and both rings");
    }

    /**
     * Each piece that moves turns about the totem's own axis and hangs from nothing: a piece parented to another
     * would be carried round by it, and the pieces would stop turning against each other.
     */
    @Test
    void thePiecesTurnAboutTheAxisEachOnItsOwn() {
        Map<String, JsonObject> bones = bones();
        for (String name : new String[]{"totem_1", "totem_2", "totem_3", "totem_4", "core", "ring_outer"}) {
            assertTrue(bones.containsKey(name), "the model has no " + name);
            JsonArray pivot = bones.get(name).getAsJsonArray("pivot");
            assertEquals(0.0D, pivot.get(0).getAsDouble(), 1.0e-9D, name + " is off the axis");
            assertEquals(0.0D, pivot.get(2).getAsDouble(), 1.0e-9D, name + " is off the axis");
            assertFalse(bones.get(name).has("parent"), name + " is carried by another bone");
        }
        assertEquals(WardStructure.CORE_HEIGHT * 16.0D, bones.get("core").getAsJsonArray("pivot").get(1).getAsDouble(), 1.0e-9D);
        // The middle ring rides in the outer one, on the same axis, and turns against it.
        assertEquals("ring_outer", bones.get("ring_middle").get("parent").getAsString());
        JsonArray middle = bones.get("ring_middle").getAsJsonArray("pivot");
        assertEquals(0.0D, middle.get(0).getAsDouble(), 1.0e-9D, "ring_middle is off the axis");
        assertEquals(0.0D, middle.get(2).getAsDouble(), 1.0e-9D, "ring_middle is off the axis");
        assertTrue(WardWorks.OUTER_RING_RATE * WardWorks.MIDDLE_RING_RATE < 0.0D, "the rings turn the same way");
    }

    /** The master's blockstate names both of its states, or a lit ward is a missing model. */
    @Test
    void theBlockstateNamesTheWardLitAndUnlit() throws IOException {
        JsonObject variants = JsonParser.parseString(Files.readString(BLOCKSTATE, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("variants");
        assertTrue(variants.has("lit=false") && variants.has("lit=true"), "the blockstate names " + variants.keySet());
    }

    // ------------------------------------------------------------------ what the totem does

    /** Shut, each piece sits where it was carved; open, each hangs clear of the one below, the head highest. */
    @Test
    void thePiecesLiftApartAndTheHeadLiftsFurthest() {
        double below = 0.0D;
        for (int piece = 0; piece < WardWorks.PIECES; piece++) {
            assertEquals(0.0D, WardWorks.lift(piece, 0.0D), 1.0e-9D);
            double lifted = WardWorks.lift(piece, 1.0D);
            assertEquals(WardWorks.GAP, lifted - below, 1.0e-9D, "piece " + piece + " does not hang a gap above the one below");
            assertEquals(lifted, WardWorks.lift(piece, 5.0D), 1.0e-9D, "a piece lifted past fully open");
            below = lifted;
        }
    }

    /** No two neighbours turn together, or the totem would turn as one stone and not be seen to be broken. */
    @Test
    void neighbouringPiecesTurnAgainstEachOther() {
        for (int piece = 0; piece + 1 < WardWorks.PIECES; piece++) {
            assertTrue(WardWorks.turn(piece, 1.0D) * WardWorks.turn(piece + 1, 1.0D) < 0.0D,
                    "pieces " + piece + " and " + (piece + 1) + " turn the same way");
        }
    }

    /**
     * Dragged, a piece goes along the bearing it is pulled on - in the game's frame, where the model's x has been
     * mirrored - and the head goes furthest.
     */
    @Test
    void aDraggedPieceGoesAtItsPullAndTheHeadGoesFurthest() {
        for (int bearing = 0; bearing < 360; bearing += 15) {
            double ux = Math.cos(Math.toRadians(bearing));
            double uz = Math.sin(Math.toRadians(bearing));
            double last = 0.0D;
            for (int piece = 0; piece < WardWorks.PIECES; piece++) {
                // The mirror GeckoLib applies to x as it bakes the model, undone.
                double x = -WardWorks.shearX(piece, ux, 1.0D);
                double z = WardWorks.shearZ(piece, uz, 1.0D);
                assertEquals(ux * WardWorks.shear(piece), x, 1.0e-9D, "bearing " + bearing);
                assertEquals(uz * WardWorks.shear(piece), z, 1.0e-9D, "bearing " + bearing);
                assertEquals(-x, -WardWorks.shearX(piece, ux, -1.0D), 1.0e-9D, "ringing back is not the way it went");
                assertEquals(x, -WardWorks.shearX(piece, ux, 3.0D), 1.0e-9D, "a piece dragged past its limit");
                assertTrue(Math.hypot(x, z) > last, "a lower piece is dragged further than the one above it");
                last = Math.hypot(x, z);
            }
        }
    }

    /** The four states a player has to be able to tell apart at a glance, in order of how bright each is. */
    @Test
    void offIsDarkDownGuttersStandingIsLitAndStrainFlares() {
        for (double time = 0.0D; time < 500.0D; time += 3.0D) {
            double off = WardWorks.glow(false, true, 1.0D, 1.0D, time);
            double down = WardWorks.glow(true, false, 1.0D, 0.0D, time);
            double empty = WardWorks.glow(true, true, 0.0D, 0.0D, time);
            double full = WardWorks.glow(true, true, 1.0D, 0.0D, time);
            double strained = WardWorks.glow(true, true, 0.5D, 1.0D, time);
            assertEquals(0.0D, off, "a ward switched off is lit");
            assertTrue(down > 0.0D && down < empty && empty < full && full < strained,
                    "at " + time + ": " + down + " " + empty + " " + full + " " + strained);
            assertTrue(strained <= 1.0D);
        }
    }

    /**
     * Switched on, the totem opens by degrees and gathers way; out of charge it stops turning and stays open;
     * switched off it shuts. None of it at once.
     */
    @Test
    void theTotemOpensGathersWayWindsDownAndShutsByDegrees() {
        WardWorks.Motion motion = new WardWorks.Motion();
        assertEquals(0.0D, motion.spread(1.0F));
        double last = 0.0D;
        for (int tick = 0; tick < 200; tick++) {
            motion.tick(true, true);
            assertTrue(motion.spread(1.0F) >= last && motion.spread(1.0F) - last < 0.08D, "the totem sprang open");
            assertTrue(motion.spread(0.5F) <= motion.spread(1.0F));
            last = motion.spread(1.0F);
        }
        assertTrue(last > 0.99D, "the totem never opened");
        double before = motion.angle(1.0F);
        motion.tick(true, true);
        assertEquals(WardWorks.TURN_RATE, motion.angle(1.0F) - before, 1.0e-4D, "a standing ward is not up to speed");

        // The field drops: it stays open, and winds down to a stop.
        for (int tick = 0; tick < 300; tick++) {
            motion.tick(true, false);
        }
        assertTrue(motion.spread(1.0F) > 0.99D, "a ward out of charge shut, as one switched off does");
        before = motion.angle(1.0F);
        motion.tick(true, false);
        assertTrue(motion.angle(1.0F) - before < WardWorks.TURN_RATE * 0.001D, "a ward out of charge is still turning");

        for (int tick = 0; tick < 200; tick++) {
            motion.tick(false, false);
        }
        assertTrue(motion.spread(1.0F) < 0.01D, "a ward switched off never shut");
    }
}
