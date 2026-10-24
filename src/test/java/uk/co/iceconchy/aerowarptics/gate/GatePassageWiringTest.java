package uk.co.iceconchy.aerowarptics.gate;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.network.ClientboundGatePassagePacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gate passage is hooked up, and hooked up the safe way.
 *
 * <p>{@link GatePassageTest} and {@link PassageClockTest} hold the timing, but the ledger that protects a
 * traveller cannot be run without a server, and every way its wiring can come loose is silent: a gate
 * still carries people, they still arrive, and the only difference is a traveller sent straight back
 * through the gate they were being held at, a Spatial Wave Detector that never hears a player arrive, or
 * - worst - a player left invulnerable by a server that stopped at the wrong moment. So this reads the
 * sources for the handful of lines those depend on.
 */
class GatePassageWiringTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static String source(String path) {
        try {
            return Files.readString(SOURCES.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The body of a method, from its signature to the next line that closes a member at class depth. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "could not find " + signature);
        Matcher end = Pattern.compile("\\R    }\\R").matcher(source);
        assertTrue(end.find(start), "could not find the end of " + signature);
        return source.substring(start, end.end());
    }

    /** Whether {@code first} appears in a method, and before {@code second} does. */
    private static boolean before(String body, String first, String second) {
        int one = body.indexOf(first);
        int two = body.indexOf(second);
        return one >= 0 && two >= 0 && one < two;
    }

    // -------------------------------------------------------------- the gate

    /**
     * Somebody held at a far gate is standing in that gate's catchment. Asked after their side has been
     * noted, the gate remembers a side for them, and the step they take on being let go is a crossing.
     */
    @Test
    void aGateIgnoresAnyoneInPassageBeforeItNotesWhichSideTheyAreOn() {
        String gate = source("gate/RiftGateBlockEntity.java");
        String entities = method(gate, "private void moveEntities(");
        assertTrue(before(entities, "GatePassages.inPassage(entity)", "stepped(entity.getUUID()"),
                "moveEntities notes a traveller's side before asking whether they are in a passage");
        String vehicles = method(gate, "private void moveVehicles(");
        assertTrue(before(vehicles, "GatePassages.holding(vehicle.uuid())", "stepped(vehicle.uuid()"),
                "moveVehicles notes a held hull's side before asking whether it is being held");
    }

    /** The traveller's client has to be drawing the passage before it is told it is at the far gate. */
    @Test
    void aPassageBeginsBeforeTheTravellerIsMoved() {
        String entities = method(source("gate/RiftGateBlockEntity.java"), "private void moveEntities(");
        assertTrue(before(entities, "GatePassages.begin(", "player.connection.teleport("),
                "a traveller is moved to the far gate before their passage has begun");
        assertTrue(entities.contains("entity.setDeltaMovement(travellers.isEmpty() ? worldArrivalMotion : Vec3.ZERO)"),
                "a traveller in passage is given their momentum at once, and walks out of the far gate blind");
    }

    /** A crewed hull waits; an empty one, with nobody to wait for, is driven straight off as it always was. */
    @Test
    void onlyACrewedVehicleIsHeld() {
        String vehicles = method(source("gate/RiftGateBlockEntity.java"), "private void moveVehicles(");
        assertTrue(vehicles.contains("boolean passage = GatePassages.enabled() && !aboard.isEmpty();"),
                "a vehicle is held, or not, on something other than having a crew");
        assertTrue(before(vehicles, "List<ServerPlayer> aboard = vehicle.crew();", "vehicle.relocate("),
                "the crew are read after the hull has moved, when they are found somewhere else");
        assertTrue(vehicles.contains("GatePassages.beginAboard("), "a crewed vehicle is no longer put into a passage");
    }

    // ------------------------------------------------------------ the arrival

    /**
     * "An arrival is announced, never searched for." A gate that puts somebody into a passage leaves the
     * announcing to the passage, so the passage has to do it - for a walker and for a hull.
     */
    @Test
    void aPassageAnnouncesItsOwnArrival() {
        String arrive = method(source("gate/GatePassages.java"), "private static void arrive(");
        assertEquals(2, arrive.split("SpatialWaveDetectorBlockEntity\\.announce\\(", -1).length - 1,
                "an arrival out of a passage is not announced once for a hull and once for a traveller");
        assertTrue(arrive.contains("AWCriteria.gateTravelled(travellers, true)")
                        && arrive.contains("AWCriteria.gateTravelled(travellers, false)"),
                "coming out of a passage no longer counts as having travelled by gate");
        assertTrue(arrive.contains("body.setDeltaMovement(motion)") && arrive.contains("vehicle.driveVelocity("),
                "a traveller's momentum is not given back as they arrive");
        // And the gate must not have announced them already, as they went in.
        String entities = method(source("gate/RiftGateBlockEntity.java"), "private void moveEntities(");
        Matcher skipped = Pattern.compile("if \\(!travellers\\.isEmpty\\(\\)\\) \\{\\R\\s+continue;").matcher(entities);
        assertTrue(skipped.find() && skipped.start() < entities.indexOf("SpatialWaveDetectorBlockEntity.announce("),
                "a traveller in passage is announced as they go in, and again as they come out");
    }

    // ------------------------------------------------- nothing on the traveller

    /**
     * The rule the whole ledger exists for. Every one of these is saved with the player, so a server that
     * stopped mid-passage would start again with it still set and nothing that knew to clear it.
     */
    @Test
    void nothingIsSetOnATravellerThatOutlivesTheServer() {
        String ledger = source("gate/GatePassages.java");
        // The event's own setInvulnerable is the answer to a question, and is the one that is allowed.
        Matcher invulnerable = Pattern.compile("(\\w+)\\.setInvulnerable\\(").matcher(ledger);
        while (invulnerable.find()) {
            assertEquals("event", invulnerable.group(1), "a traveller is made invulnerable by a flag that is saved");
        }
        for (String saved : List.of(".setNoGravity(", ".setGameMode(", ".onUpdateAbilities(", "getAbilities()",
                ".addEffect(", ".getPersistentData(")) {
            assertFalse(ledger.contains(saved), "GatePassages calls " + saved + " - whatever that sets is saved with"
                    + " the player, and a crash mid-passage would leave it set");
        }
        // Protection is asked for instead, by all three of the things that can be done to a traveller.
        for (String event : List.of("EntityInvulnerabilityCheckEvent event", "LivingChangeTargetEvent event",
                "LivingKnockBackEvent event")) {
            assertTrue(ledger.contains(event), "GatePassages no longer listens for " + event);
        }
        // And every other way out of a passage is heard of.
        for (String way : List.of("PlayerEvent.PlayerLoggedOutEvent", "PlayerEvent.PlayerChangedDimensionEvent",
                "LivingDeathEvent", "ServerStoppingEvent")) {
            assertTrue(ledger.contains(way), "GatePassages does not hear of " + way);
        }
    }

    // ---------------------------------------------------------------- the client

    /** A player must never be left behind a veil, their keys doing nothing, in a world that has moved on. */
    @Test
    void theClientLetsGoOfAPassageWhenTheWorldChanges() {
        assertTrue(method(source("client/LevelChangeReset.java"), "public static void reset()")
                .contains("GatePassageOverlay.clear()"), "a change of level no longer clears a gate passage");
        String overlay = source("client/fx/GatePassageOverlay.java");
        assertTrue(method(overlay, "public static void onClientTick(").contains("GatePassage.over(age, forecast, arrived)"),
                "the overlay no longer ends by itself");
        // The lock and the veil come off the same clock, so one cannot outlast the other.
        assertTrue(method(overlay, "public static void onMovementInput(").contains("GatePassage.held(age, forecast, arrived)"),
                "the input lock is no longer tied to the passage's own timing");
    }

    @Test
    void thePassageIsDrawnOverEverythingAndItsPacketIsRegistered() {
        assertTrue(source("client/AWClientSetup.java").contains("registerAboveAll(AeroWarptics.id(\"gate_passage\")"),
                "the passage is no longer the top GUI layer, so something is drawn over the veil");
        assertTrue(source("network/AWNetwork.java").contains("registrar.playToClient(ClientboundGatePassagePacket.TYPE"),
                "the passage packet is not registered");
    }

    // ------------------------------------------------------------- the dial's button

    /** What a player sees going through a gate is their own: the button writes their config and sends nothing. */
    @Test
    void theDialsPassageButtonChangesOnlyThisPlayersOwnSetting() {
        String dial = source("client/screen/RiftGateDialScreen.java");
        String step = method(dial, "private void stepPassage()");
        assertTrue(step.contains("AWConfig.GATE_PASSAGE.set(") && step.contains("AWConfig.GATE_PASSAGE.save()"),
                "the passage button no longer sets and saves the player's own client setting");
        assertFalse(step.contains("PacketDistributor"), "the passage button tells the server what a player sees");
        assertTrue(step.contains("now.previous()") && step.contains("now.next()"),
                "the passage button no longer steps both ways");
        // The gate's owner greys out the controls that are the gate's. This one is not the gate's.
        assertFalse(method(dial, "private void updatePassage()").contains(".active"),
                "the passage button can be greyed out, on somebody else's gate");
    }

    /**
     * Stepping the style plays nothing. The dial once showed the style chosen, full screen behind the panel,
     * and was asked not to: the button names the style, its tooltip says what it is, and the place to see a
     * passage is a gate.
     */
    @Test
    void theDialPlaysNoPassageOfItsOwn() {
        String dial = source("client/screen/RiftGateDialScreen.java");
        assertFalse(dial.contains("GatePassagePainter") || dial.contains("PassageFrame"),
                "the dial draws a passage of its own again");
        // The tooltip is then the only thing that says what a style is before it is used.
        assertTrue(method(dial, "private void updatePassage()").contains("style.aboutKey()"),
                "the passage button no longer says what the chosen style looks like");
    }

    /** A style whose shader did not load is the plain passage - never no passage, and never a closed shader. */
    @Test
    void aStyleWhoseShaderDidNotLoadIsDrawnPlain() {
        assertTrue(method(source("client/fx/GatePassageOverlay.java"), "private static void enter(")
                .contains("style = GatePassagePainter.drawable(chosen);"), "a passage is begun in a style that may"
                + " have no shader to draw it");
        String painter = source("client/fx/GatePassagePainter.java");
        assertTrue(method(painter, "public static GatePassageStyle drawable(").contains("GatePassageStyle.QUIET"),
                "a style with no shader no longer falls back to the plain one");
        assertTrue(method(painter, "public static void draw(").contains("quiet(graphics, frame)"),
                "the painter has nothing to draw when a style's shader is missing");
        assertTrue(source("client/AWClientSetup.java").contains("GatePassagePainter.setShader(style, null)"),
                "a shader that fails on a reload leaves the closed one it replaced in place");
    }

    // ------------------------------------------------------------------ the wire

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    @Test
    void theCueSurvivesTheWire() {
        for (ClientboundGatePassagePacket sent : List.of(ClientboundGatePassagePacket.enter(417, 60),
                ClientboundGatePassagePacket.arrive(417))) {
            RegistryFriendlyByteBuf buf = buffer();
            ClientboundGatePassagePacket.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, ClientboundGatePassagePacket.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes(), "the decoder left some of the cue unread");
        }
    }

    /** A client is not held behind a veil for as long as a sender cares to say. */
    @Test
    void aPassageLongerThanAnyRealOneIsCutDownOnTheWayIn() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeEnum(ClientboundGatePassagePacket.Phase.ENTER);
        buf.writeVarInt(417);
        buf.writeVarInt(Integer.MAX_VALUE);
        assertEquals(ClientboundGatePassagePacket.MAX_TICKS, ClientboundGatePassagePacket.STREAM_CODEC.decode(buf).ticks());

        buf = buffer();
        buf.writeEnum(ClientboundGatePassagePacket.Phase.ENTER);
        buf.writeVarInt(417);
        buf.writeVarInt(-40);
        assertEquals(0, ClientboundGatePassagePacket.STREAM_CODEC.decode(buf).ticks());
    }
}
