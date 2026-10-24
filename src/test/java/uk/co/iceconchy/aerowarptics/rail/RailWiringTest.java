package uk.co.iceconchy.aerowarptics.rail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rail gates are hooked up, and hooked up the safe way.
 *
 * <p>None of this can be run without a server and Create's trains, and every way it can come loose is silent. A
 * provider never registered and track simply lays as track. A lane cell left empty when the gate shuts and Create
 * quietly eats the end of the track and the corridor with it. A gate that "notices" a carriage crossing tears it off
 * its own rails. A corridor offered to a probe sends a ship into a void nobody may build in. So this reads the
 * sources for the lines those depend on.
 */
class RailWiringTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static String source(String path) {
        try {
            return Files.readString(SOURCES.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static JsonObject json(String path) {
        try {
            return JsonParser.parseString(Files.readString(RESOURCES.resolve(path), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "could not find " + signature);
        Matcher end = Pattern.compile("\\R    }\\R").matcher(source);
        assertTrue(end.find(start), "could not find the end of " + signature);
        return source.substring(start, end.end());
    }

    private static boolean before(String body, String first, String second) {
        int one = body.indexOf(first);
        int two = body.indexOf(second);
        return one >= 0 && two >= 0 && one < two;
    }

    // ------------------------------------------------------------- providers

    @Test
    void thePaneTheSocketAndTheMouthAreAllPortalsToCreate() {
        String tracks = source("rail/RiftPortalTracks.java");
        String setup = method(tracks, "public static void onCommonSetup(");
        assertTrue(setup.contains("enqueueWork"), "registered off the main thread is registered into a race");
        for (String block : List.of("RIFT_PORTAL", "RIFT_SOCKET", "CORRIDOR_MOUTH")) {
            assertTrue(setup.contains("PortalTrackProvider.REGISTRY.register(AWBlocks." + block),
                    block + " is not a portal Create knows about, so track run up to one is eaten");
        }
        assertTrue(source("AeroWarptics.java").contains("RiftPortalTracks::onCommonSetup"),
                "the providers are never registered");
        assertTrue(source("AeroWarptics.java").contains("RailWake::onRegisterControllers"),
                "the wake's ticket controller is never registered, and forcing a chunk with it throws");
    }

    /** Create drops a refused track and says "Target portal not generated yet". Every refusal has to say why. */
    @Test
    void everyRefusalSaysWhyAndTracesIt() {
        String tracks = source("rail/RiftPortalTracks.java");
        String refuse = method(tracks, "private static PortalTrackProvider.Exit refuse(");
        assertTrue(refuse.contains("Trace.log("));
        assertTrue(refuse.contains("displayClientMessage("));
        String fromGate = method(tracks, "static PortalTrackProvider.Exit fromGate(");
        Matcher nulls = Pattern.compile("return null;").matcher(fromGate);
        int bare = 0;
        while (nulls.find()) {
            bare++;
        }
        assertEquals(1, bare, "one bare null only - the corridor itself, where nobody stands to be told");
    }

    // ---------------------------------------------------------------- the gate

    @Test
    void aLaneCellIsNeverLeftEmpty() {
        String gate = source("gate/RiftGateBlockEntity.java");
        String aperture = method(gate, "private void setAperture(");
        assertTrue(aperture.contains("lane ? socket : Blocks.AIR"),
                "a shut gate must leave a socket in a lane's cell, not air");
        assertTrue(aperture.contains("there.is(socket.getBlock())"), "an opening gate must take its sockets back");
        String open = method(gate, "private boolean isOpen(");
        assertTrue(open.contains("RiftSocketBlock.isLaneCell"), "a ring that does not count its sockets unforms");
        String pane = source("gate/RiftPortalBlock.java");
        assertTrue(method(pane, "private static boolean reachesFrame(").contains("RIFT_SOCKET"),
                "a pane beside a socket reads as unringed and takes itself away");
    }

    /**
     * A socket stands in one cell of an otherwise empty opening, so the pane's ring walk - out through the pane to
     * the frame - stops at the air beside it. Asked that way, every socket took itself away within a couple of
     * minutes of its gate shutting, and Create took the lane with it.
     */
    @Test
    void aSocketAsksTheLanesNotTheRing() {
        String socket = method(source("gate/RiftSocketBlock.java"), "protected void randomTick(");
        assertTrue(socket.contains("isLaneCell("), "a socket must ask the lane registry whether it is still needed");
        assertFalse(socket.contains("stillRinged"), "the ring walk cannot see past the air beside a socket");
    }

    /** Emptied first, then taken up - or Create's next tick finds the portal still there and asks again. */
    @Test
    void aBrokenRingEmptiesItsCellsBeforeItTakesItsLanesUp() {
        String gate = source("gate/RiftGateBlockEntity.java");
        String unform = method(gate, "public void unform(");
        assertTrue(before(unform, "laneCells = Set.of();", "setAperture(shape, false);"));
        assertTrue(before(unform, "setAperture(shape, false);", "takeUpLanes("));
        assertTrue(before(source("rail/RailWorks.java"), "unbind(level, lane.track());",
                "setValue(TrackBlock.HAS_BE, false)"), "a bound track taken up destroys its partner");
    }

    /** The socket put down for newly noticed track is not a lane's yet; tidied after it is laid, it is lost. */
    @Test
    void newTrackIsNoticedAfterTheSocketsAreTidied() {
        String gate = source("gate/RiftGateBlockEntity.java");
        String revalidate = method(gate, "private void revalidate(");
        assertTrue(before(revalidate, "setAperture(shape, false);", "noticeTracks("));
    }

    @Test
    void aGateLeavesTrainsToCreate() {
        String gate = source("gate/RiftGateBlockEntity.java");
        String entities = method(gate, "private void moveEntities(");
        assertTrue(before(entities, "CarriageContraptionEntity", "stepped(entity.getUUID()"),
                "a carriage posed through the pane is seen to cross and torn off its track");
        String traversal = method(gate, "private void tickTraversal(");
        assertTrue(before(traversal, "!far.isInSameDimension(serverLevel)", "moveVehicles("),
                "nothing but a train may follow a route into another dimension");
    }

    @Test
    void aPlayersHangUpWaitsForTrainsInTransit() {
        String packet = source("network/ServerboundGatePacket.java");
        assertTrue(packet.contains("case HANG_UP -> gate.playerHangUp();"));
        String hangUp = method(source("gate/RiftGateBlockEntity.java"), "public GateFailure playerHangUp(");
        assertTrue(hangUp.contains("RailTransit.inTransitBetween"));
    }

    @Test
    void anArrivalIsHeardByTheDetectors() {
        String transit = source("rail/RailTransit.java");
        assertTrue(method(transit, "private static void arrived(").contains("SpatialWaveDetectorBlockEntity.announce"));
    }

    /** Every refusal a train can be held for has a translation - the owner is told it by key. */
    @Test
    void theTrainsOwnerIsToldByKey() {
        JsonObject lang = json("assets/aerowarptics/lang/en_us.json");
        for (String key : List.of("gate.rail.held", "gate.rail.opening", "gate.rail.rescued")) {
            assertTrue(lang.has("aerowarptics." + key), key);
        }
    }

    // ---------------------------------------------------------- the corridor

    @Test
    void theCorridorIsKeptOutOfEverythingElse() {
        assertTrue(method(source("probe/ProbeDimensions.java"), "public static List<ResourceKey<Level>> offered(")
                .contains("RailCorridor.isCorridor(level)"), "a probe would offer to scan the corridor");
        assertTrue(method(source("warp/CrossDimensionWarp.java"), "public static boolean joined(")
                .contains("RailCorridor.isCorridor(destination)"), "a ship could be sent into the corridor");
        JsonObject type = json("data/aerowarptics/dimension_type/rift_corridor.json");
        assertFalse(type.get("has_skylight").getAsBoolean(), "a sky would bring Rift Storms into the corridor");
        assertFalse(type.get("bed_works").getAsBoolean());
        assertFalse(type.get("has_raids").getAsBoolean());
    }

    @Test
    void theDimensionAndItsSkyAreNamedAlike() {
        JsonObject dimension = json("data/aerowarptics/dimension/rift_corridor.json");
        JsonObject type = json("data/aerowarptics/dimension_type/rift_corridor.json");
        assertEquals("aerowarptics:rift_corridor", dimension.get("type").getAsString());
        assertEquals("aerowarptics:rift_corridor", type.get("effects").getAsString());
        assertTrue(source("client/fx/CorridorView.java").contains("AeroWarptics.id(\"rift_corridor\")"),
                "the sky the dimension asks for is never registered, and it draws the Overworld's");
        assertTrue(source("rail/RailCorridor.java").contains("AeroWarptics.id(\"rift_corridor\")"));
        JsonObject generator = dimension.getAsJsonObject("generator").getAsJsonObject("settings");
        assertEquals(0, generator.getAsJsonArray("layers").size(), "the corridor is void");
        int height = type.get("height").getAsInt();
        int min = type.get("min_y").getAsInt();
        assertTrue(CorridorSlots.TRACK_Y - 1 > min && CorridorSlots.TRACK_Y + 4 < min + height,
                "the corridor's track and mouths must fit inside its dimension");
    }

    /** Placed by gates and lanes and by nothing else: no item, no recipe, nothing to drop. */
    @Test
    void theSocketAndTheMouthArePlacedAndNotMade() {
        List<String> problems = new ArrayList<>();
        for (String block : List.of("rift_socket", "corridor_mouth")) {
            if (!Files.exists(RESOURCES.resolve("assets/aerowarptics/blockstates/" + block + ".json"))) {
                problems.add("no blockstate for " + block);
            }
            for (String made : List.of("assets/aerowarptics/models/item/" + block + ".json",
                    "data/aerowarptics/recipe/" + block + ".json",
                    "data/aerowarptics/loot_table/blocks/" + block + ".json")) {
                if (Files.exists(RESOURCES.resolve(made))) {
                    problems.add(block + " has " + made);
                }
            }
        }
        String blocks = source("registry/AWBlocks.java");
        for (String block : List.of("RIFT_SOCKET", "CORRIDOR_MOUTH")) {
            int at = blocks.indexOf("DeferredBlock<");
            assertTrue(at >= 0);
            String declaration = blocks.substring(blocks.indexOf(block + " = BLOCKS.register("));
            declaration = declaration.substring(0, declaration.indexOf(";"));
            if (!declaration.contains(".noLootTable()") || !declaration.contains(".strength(-1.0F")) {
                problems.add(block + " must be unbreakable and drop nothing");
            }
        }
        assertTrue(source("registry/AWItems.java").indexOf("rift_socket") < 0, "the socket has an item");
        assertTrue(source("registry/AWItems.java").indexOf("corridor_mouth") < 0, "the mouth has an item");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Nothing in the corridor may be built or broken by a player who is not a creative operator. */
    @Test
    void nobodyBuildsInTheCorridor() {
        String warden = source("rail/CorridorWarden.java");
        assertTrue(warden.contains("BlockEvent.BreakEvent") && warden.contains("BlockEvent.EntityPlaceEvent"));
        assertTrue(warden.contains("setCanceled(true)"));
    }

    // ----------------------------------------------------------------- mixins

    @Test
    void theMixinsHoldNothingButTheCall() {
        for (String mixin : List.of("mixin/rail/CarriageMixin.java", "mixin/rail/TrainMixin.java")) {
            assertTrue(source(mixin).contains("RailGates."), mixin + " does not go through RailGates");
        }
        String gates = source("rail/RailGates.java");
        assertTrue(method(gates, "private static boolean holds(").contains("catch (RuntimeException"),
                "a bug in a gate must not stop every train on the server");
    }
}
