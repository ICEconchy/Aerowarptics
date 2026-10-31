package uk.co.iceconchy.aerowarptics.ward;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.WarpSoundSwitch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the Rift Ward is hooked up, read off the source - because every way of getting it wrong here compiles, runs,
 * and looks exactly like a server with no wards on it.
 *
 * <p>None of this can say a deflection looks right, or that a field holds in a running game. It says that
 * everything which brings a rift out somewhere asks first, that nothing is paid before it is certain, and that
 * nothing tells a pilot what they are flying at.
 */
class WardWiringTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static String source(String path) {
        try {
            return Files.readString(SOURCES.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A method's text, from its signature to the next thing declared at the same depth. */
    private static String body(String source, String signature) {
        int from = source.indexOf(signature);
        assertTrue(from >= 0, "no longer declares: " + signature);
        int to = source.indexOf("\n    }\n", from);
        assertTrue(to > from, "could not find the end of: " + signature);
        return source.substring(from, to);
    }

    // ------------------------------------------------------------------ a ship's warp

    /**
     * A flagship's arrival is planned twice at most - on the scattered target and on the target itself - and both
     * go past the wards. A bare {@code WarpFlight.plan} in there is an arrival nobody asked a ward about.
     */
    @Test
    void everyArrivalADrivePlansGoesPastTheWards() {
        String begin = body(source("drive/RiftDriveBlockEntity.java"), "private void beginWarp() {");
        assertFalse(begin.contains("WarpFlight.plan("), "beginWarp plans an arrival without asking the wards");
        assertEquals(2, begin.split("planPastWards\\(", -1).length - 1,
                "the scattered target and the target itself are each planned past the wards");
        String past = body(source("drive/RiftDriveBlockEntity.java"), "private WardedPlan planPastWards(");
        assertTrue(past.contains("Wards.turnAside("));
        assertTrue(past.contains("turn.alternatives()"), "a rim with no room at the first place is not walked");
        assertTrue(past.contains("WarpFailure.DESTINATION_WARDED"), "a rim with no room anywhere is not refused");
        assertTrue(past.contains("outsideWards("), "the arrival search may put the hull back inside the field");
    }

    /** Paid once the flight is certain: after the last thing in the pre-flight that can refuse, and before the rift. */
    @Test
    void aWardIsPaidOnlyOnceTheFlightIsCertain() {
        String begin = body(source("drive/RiftDriveBlockEntity.java"), "private void beginWarp() {");
        int paid = begin.indexOf("Wards.commit(");
        assertTrue(paid > 0, "a drive never pays the ward that turned it");
        assertTrue(paid > begin.lastIndexOf("abort("), "a ward is paid for a warp that can still be refused");
        assertTrue(paid < begin.indexOf("openRift(serverLevel, airship, destination, planned, bow);"));
        assertEquals(1, begin.split("Wards\\.commit\\(", -1).length - 1);
    }

    /** Asking changes nothing: {@code turnAside} is asked for arrivals that are then refused for other reasons. */
    @Test
    void askingAWardChangesNothing() {
        String asked = body(source("ward/Wards.java"), "public static Turn turnAside(");
        for (String change : List.of(".register(", "spend(", "drop(", "giveWay(", ".remove(")) {
            assertFalse(asked.contains(change), "turnAside calls " + change + " before anything is certain");
        }
    }

    /** An escort comes out exactly where the flagship's search put it, and the flagship's search asked the wards. */
    @Test
    void anEscortNeverAsksForItself() {
        String escort = body(source("drive/RiftDriveBlockEntity.java"), "private void beginEscortWarp() {");
        assertFalse(escort.contains("Wards."), "an escort asks the wards, and may be turned out of its formation");
    }

    /** The far end is told as the corridor begins - and a turned exit is not stood open there before the tear arrives. */
    @Test
    void theFarEndIsToldAtTheCorridorAndTheExitIsNotOpenedEarly() {
        String drive = source("drive/RiftDriveBlockEntity.java");
        int corridor = drive.indexOf("case ENTER_CORRIDOR -> {");
        String entering = drive.substring(corridor, drive.indexOf("case EXIT_CORRIDOR -> {", corridor));
        assertTrue(entering.contains("announceWard(current, airship);"));
        assertTrue(entering.contains("firstTo(FoldLog.SharedCue.EXIT_OPEN) && !turned"),
                "a turned exit is opened at the rim before the tear is dragged to it");
        assertTrue(body(drive, "private void announceWard(").contains("arrivalLevel()"),
                "the far end's cue goes to the drive's own level, which across dimensions is the wrong world");
    }

    // ------------------------------------------------------------------ a plotter's exit, and scans

    @Test
    void aPlottersExitGoesPastTheWardsAndIsPaidForAsItTears() {
        String plotter = source("plotter/RiftPlotterBlockEntity.java");
        assertTrue(body(plotter, "private void reach(ServerLevel server) {").contains("wardedExit(server, exitFrame())"),
                "a plotter reaches for its exit where it was plotted, ward or no ward");
        String open = body(plotter, "private void open(ServerLevel server, PlotGeometry.Frame near, PlotGeometry.Frame far");
        assertTrue(open.contains("Wards.commit(server, wardTurn, wardPrice());"));
        assertTrue(body(plotter, "private void holdFarEnd(ServerLevel server, boolean now) {").contains("heldExit"),
                "the far end is held where the exit was plotted, which a turned exit may be chunks from");
        assertTrue(plotter.contains("tag.putUUID(\"Owner\", owner);"), "a plotter forgets whose it is");
        // The table shows the exit where it was plotted: a ward says nothing until it acts.
        assertFalse(body(plotter, "public PlotGeometry.Frame exitFrame() {").contains("ward"));
    }

    @Test
    void bothThingsThatLookAtDistantGroundAskTheWards() {
        assertTrue(source("plotter/PlotterSurveys.java").contains("WardScans.table(level, survey, player.getUUID())"));
        assertTrue(source("probe/RiftProbeBlockEntity.java").contains("WardScans.probe(serverLevel, ground, soundedBy)"));
    }

    // ------------------------------------------------------------------ what a pilot is told

    /**
     * Nothing a pilot reads before launching knows a ward is there: not the validator that prices the chart, not
     * the rules behind it, and none of the screens. A chart that greys a warded anchor has told them.
     */
    @Test
    void nothingAPilotReadsBeforeLaunchingKnowsOfAWard() throws IOException {
        for (String quiet : List.of("warp/WarpValidator.java", "warp/WarpRules.java", "warp/WarpQuote.java",
                "astrolabe/AstrolabeBlockEntity.java")) {
            String text = source(quiet);
            assertFalse(text.contains("Wards") || text.contains("WARDED") || text.contains(".ward."),
                    quiet + " knows of wards, and a pilot reads what it says");
        }
        try (Stream<Path> screens = Files.list(SOURCES.resolve("client/screen"))) {
            for (Path screen : screens.toList()) {
                String name = screen.getFileName().toString();
                if (name.equals("RiftWardScreen.java") || name.equals("AWLayouts.java") || !name.endsWith(".java")) {
                    continue;
                }
                String text = Files.readString(screen, StandardCharsets.UTF_8);
                assertFalse(text.contains("aerowarptics.ward.") || text.contains("WARDED"), name + " knows of wards");
            }
        }
    }

    /**
     * Whom a ward turns is its mode and nothing else: no anchor is asked about and no operator looked for. The
     * first version had both, and the owner asked for them to go.
     */
    @Test
    void nothingButTheModeDecidesWhoIsTurned() {
        String wards = source("ward/Wards.java");
        assertFalse(wards.contains("WarpAnchor") || wards.contains("hasPermissions"),
                "a ward asks about an anchor or an operator again");
        assertTrue(body(wards, "public static Turn turnAside(").contains("ward.passes(who, networks)"));
        assertTrue(body(wards, "public static List<WardField.Circle> scrambling(").contains("ward.passes(who, networks)"));
    }

    /** The panel takes the camera; every way out of it has to give the camera back, and it dims nothing. */
    @Test
    void thePanelFliesTheCameraOutAndAlwaysGivesItBack() {
        String panel = source("client/screen/RiftWardScreen.java");
        assertTrue(body(panel, "protected void init() {").contains("PlotterCameraRig.start("));
        assertTrue(body(panel, "public void removed() {").contains("PlotterCameraRig.stop();"),
                "closing the ward's panel leaves the player looking at their ward from the sky");
        assertTrue(panel.contains("protected void renderMenuBackground(GuiGraphics graphics) {\n    }"),
                "the panel dims the world it is there to show");
        assertTrue(source("client/fx/WardEffects.java").contains("minecraft.screen instanceof RiftWardScreen open"),
                "the rim is not drawn while the panel is open");
    }

    /** The four ways a ward says its field stands: lit carvings, turning, a drone, and light on the ground. */
    @Test
    void aStandingWardSaysSoFourWays() {
        assertTrue(source("client/render/WardGlowLayer.java").contains("WardWorks.glow(ward.enabled(), ward.standing(),"));
        assertTrue(source("ward/RiftWardBlockEntity.java").contains("motion.tick(enabled, standing);"));
        assertTrue(body(source("client/fx/WardEffects.java"), "private static void hum(RiftWardBlockEntity ward) {")
                .contains("ward.enabled() && ward.standing()"));
        String machine = source("ward/RiftWardBlockEntity.java");
        assertTrue(body(machine, "public void tick() {").contains("light(standing);"),
                "a field dropping or coming back does not change the light the machine gives");
        assertTrue(source("registry/AWBlocks.java").contains("state.getValue(uk.co.iceconchy.aerowarptics.ward.RiftWardBlock.LIT)"));
    }

    // ------------------------------------------------------------------ the machine

    @Test
    void aNetworkGoneTakesItsWardsOffAndALoadedWardIsTold() {
        // Only a disbanding: a ward may name a network its owner is not in, so a member leaving changes no ward.
        assertTrue(body(source("warpnet/WarpNetworks.java"), "public static int detach(")
                .contains("ownedBy == null ? uk.co.iceconchy.aerowarptics.ward.Wards.forget(server, network)"));
        assertTrue(body(source("ward/Wards.java"), "public static List<RiftWard> forget(")
                .contains("block.forgetNetwork(network);"),
                "a loaded ward struck off a network in the record writes it straight back");
        assertTrue(body(source("ward/RiftWardBlockEntity.java"), "private void adopt(ServerLevel server) {")
                .contains("known.byId(network) != null"), "a ward unloaded while a network went keeps naming it");
    }

    /** Back from an unload, the record's charge wins: it is the one a rift was charged against in the meantime. */
    @Test
    void aWardComingBackTakesItsChargeFromTheRecord() {
        String adopt = body(source("ward/RiftWardBlockEntity.java"), "private void adopt(ServerLevel server) {");
        assertTrue(adopt.contains("WardCharge.after(kept.charge(), away,"));
        assertTrue(body(source("ward/RiftWardBlockEntity.java"), "public void onLoad() {").contains("adopted = false;"));
    }

    /** The master's shafts are the shared rule's, as the other relics' are, and all three blocks refuse to be moved. */
    @Test
    void theWardIsBuiltAsTheOtherRelicsAre() {
        String master = source("ward/RiftWardBlock.java");
        assertTrue(body(master, "public boolean hasShaftTowards(").contains("CradleStructure.shaftTowards(pos, pos, face)"));
        String refusal = body(master, "public static void onCommonSetup(");
        for (String block : List.of("RiftWardBlock", "RiftWardPartBlock", "RiftWardBaseBlock")) {
            assertTrue(refusal.contains("instanceof " + block), block + " can be carried off by a contraption");
        }
        assertTrue(source("collapse/RelicBaseBlock.java").contains("ward.RiftWardPartBlock.master(pos, state)"),
                "a ward's part is not known to belong to a relic");
        assertTrue(source("AeroWarptics.java").contains("ward.RiftWardBlock::onCommonSetup"));
    }

    /**
     * The wrench is a warp's sound, and goes quiet with the rest for a player who turned those off. The toll is
     * not: it is how a ward's owner is told, and stays.
     */
    @Test
    void theWrenchAnswersToTheWarpSoundSwitchAndTheTollDoesNot() {
        assertTrue(WarpSoundSwitch.covers("aerowarptics", "ward_wrench"));
        for (String alarm : List.of("ward_toll", "ward_break", "ward_give_way", "ward_return")) {
            assertFalse(WarpSoundSwitch.covers("aerowarptics", alarm), alarm + " can be switched off as a warp sound");
        }
    }
}
