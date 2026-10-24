package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.stabiliser.StabiliserStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything that asks who is in a cradle's ring asks {@link CradleRing}.
 *
 * <p>{@link CradleRingTest} holds the rule, but the rule is only worth having if the cradle that draws the
 * offerings, the stabiliser whose goggles say it is linked, and the client that draws motes in from each all go
 * through it. Any one of them walking the ring for itself compiles and looks right with four depots and four
 * stabilisers close in - and then draws motes off a depot the ritual ignores, or tells a player a stabiliser is
 * linked that the cradle cannot see. Read from the sources, since none of the three can be loaded without a game.
 */
class CradleRingWiringTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static String source(String path) {
        try {
            return Files.readString(SOURCES.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The body of a method, from its signature to the next line that closes a member at class depth. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "could not find " + signature);
        Matcher end = Pattern.compile("\\n    }\\n").matcher(source);
        assertTrue(end.find(start), "could not find the end of " + signature);
        return source.substring(start, end.end());
    }

    @Test
    void theCradleCountsItsRingByTheSharedWalk() {
        String cradle = source("collapse/CollapseCradleBlockEntity.java");
        assertTrue(method(cradle, "private List<double[]> stabiliserOffsets(")
                        .contains("CradleRing.stabilisers(stabiliserReach(),"),
                "the cradle counts a cage it found for itself");
    }

    /**
     * A cradle links to eight depots and keeps to them. It once took whichever eight were nearest at the moment,
     * so each depot set down nearer than the furthest took a link - with sparks - and to the player building the
     * ring every new depot linked, long past the eighth.
     */
    @Test
    void theCradleRemembersWhichDepotsItIsLinkedTo() {
        String cradle = source("collapse/CollapseCradleBlockEntity.java");
        String look = method(cradle, "private void lookAtRing(");
        assertTrue(look.contains("CradleRing.linked(linkedDepots, reach,"),
                "the cradle no longer carries its links from one look to the next");
        assertTrue(look.contains("server.isLoaded("), "a depot in a chunk that is not loaded would lose its place");
        assertTrue(look.contains("setChanged();") && look.contains("sendData();"),
                "a change of links is no longer saved, or no longer sent");

        String depots = method(cradle, "private void depots(");
        assertTrue(depots.contains("lookAtRing(server);") && depots.contains("for (int[] offset : linkedDepots)"),
                "the offerings are drawn from depots other than the ones the cradle is linked to");
        assertFalse(depots.contains("CradleRing.depots("), "the offerings are drawn from the nearest eight again");

        // Saved as well as sent: which eight of more is the order they were set down in, and no world remembers that.
        String write = method(cradle, "protected void write(");
        int links = write.indexOf("tag.putIntArray(\"LinkedDepots\", links);");
        assertTrue(links >= 0, "the links are neither saved nor sent");
        String before = write.substring(0, links);
        assertEquals(count(before, "{"), count(before, "}") + 1, "the links are written only to a client, or only to disk");
        String read = method(cradle, "protected void read(");
        assertTrue(read.contains("tag.getIntArray(\"LinkedDepots\")")
                && read.contains("remembered.size() < CradleRing.MOST_DEPOTS"), "a save could hand the cradle any number of links");
        assertTrue(read.indexOf("linkedDepots = remembered;") < read.indexOf("if (clientPacket) {"),
                "the links are read on one side only");

        String tick = method(cradle, "public void tick()");
        assertTrue(tick.contains("lookAtRing(server);"), "an idle cradle never looks at its ring: a client is never told");
    }

    private static int count(String text, String piece) {
        return text.split(java.util.regex.Pattern.quote(piece), -1).length - 1;
    }

    /** A client never reads the server's config; the reach it walks by is the one the cradle was sent. */
    @Test
    void theClientIsSentTheReachItDrawsBy() {
        String cradle = source("collapse/CollapseCradleBlockEntity.java");
        String write = method(cradle, "protected void write(");
        assertTrue(write.contains("tag.putInt(\"StabiliserReach\", AWConfig.COLLAPSE_STABILISER_REACH.get())"),
                "the cradle no longer tells a client how far its cage reaches");
        String read = method(cradle, "protected void read(");
        assertTrue(read.contains("stabiliserReach = tag.getInt(\"StabiliserReach\")"), "a client no longer reads the reach");

        String links = source("client/fx/CradleLinks.java");
        assertTrue(links.contains("CradleRing.stabilisers(cradle.stabiliserReach(),"),
                "the motes come from stabilisers the client found for itself");
        // Which eight depots is the cradle's to remember. A client that looked for itself would pick the nearest.
        assertTrue(links.contains("cradle.linkedDepots()"), "the motes no longer come from the depots the cradle says");
        assertFalse(links.contains("CradleRing.depots(") || links.contains("CradleRing.linked(") || links.contains("DEPOT.get()"),
                "the client decides for itself which depots are linked");
        assertFalse(links.contains("AWConfig.COLLAPSE_"), "the motes are drawn by a reach read off the server's config");

        String spectacle = source("client/fx/CollapseSpectacle.java");
        assertTrue(method(spectacle, "private void survey(ClientLevel level)").contains("CradleRing.stabilisers(reach,"),
                "a collapse's lightning reaches for stabilisers the client found for itself");
    }

    @Test
    void theMotesAreTickedFromTheCradleAndCanBeTurnedOff() {
        assertTrue(method(source("client/ClientRuntime.java"), "static void tickCradle(").contains("CradleLinks.tick(cradle)"),
                "nothing ticks the motes: none would ever be drawn");
        String tick = method(source("client/fx/CradleLinks.java"), "public static void tick(");
        assertTrue(tick.contains("AWConfig.CRADLE_LINKS.get()"), "cradleLinks no longer turns the motes off");
        assertTrue(tick.contains("CradleRing.shown(watch.shown, !cradle.running())"),
                "the motes no longer fade while a collapse runs");
    }

    /**
     * The pull is a force, not a display: motes, and nothing with an edge. The first version drew threads with a
     * dark edging, rings and a ghost through terrain, and was sent back as a hologram laid over an ancient machine.
     */
    @Test
    void thePullIsDrawnAsMotesAndNothingElse() {
        String links = source("client/fx/CradleLinks.java");
        assertTrue(links.contains("CradleRing.mote(from, t, bow, turn)") && links.contains("CradleRing.drawn(t)"),
                "the motes are no longer where the ring says they are");
        assertFalse(links.contains("Layer.SHADE") || links.contains("Layer.THROUGH"),
                "the pull has been given a dark edging or a ghost through terrain");
        assertFalse(links.contains("LightStrokes.ring(") || links.contains("LightStrokes.arc(")
                || links.contains("LightStrokes.band(") || links.contains("LightStrokes.tube("),
                "the pull has been given a ring or a wall of light");
    }

    /**
     * A stabiliser in a cradle's ring is at work, and its goggles say so. Before this they named the fault of a
     * stabiliser with no drive, in red, to a player who had built exactly what the cradle asks for.
     */
    @Test
    void aStabiliserInARingIsNotToldItHasNoDrive() {
        String stabiliser = source("stabiliser/SpatialStabiliserBlockEntity.java");
        String tick = method(stabiliser, "public void tick()");
        int fault = tick.indexOf("if (next.isFault())");
        int sent = tick.indexOf("if (next != status)");
        assertTrue(fault >= 0 && tick.indexOf("next = atACradle(next);") > fault,
                "a stabiliser with no ship no longer looks for a cradle");
        assertTrue(sent > fault, "the status is sent before the cradle is looked for");

        String look = method(stabiliser, "private StabiliserStatus atACradle(");
        assertTrue(look.contains("CollapseCradleBlockEntity.steadiedFrom(level, worldPosition)"),
                "a stabiliser decides for itself whether a cradle counts it");

        String cradle = source("collapse/CollapseCradleBlockEntity.java");
        String steadied = method(cradle, "public static CollapseCradleBlockEntity steadiedFrom(");
        assertTrue(steadied.contains("CradleRing.steadies("), "a stabiliser is linked by a rule the cradle does not count by");
        assertTrue(steadied.contains("AWConfig.COLLAPSE_STABILISER_REACH.get()"),
                "a stabiliser is linked by a reach the cradle does not look by");
        // Asked twice a second by every stabiliser in a ring, on the server.
        assertTrue(steadied.contains(".getChunkNow(") && !steadied.contains("getBlockState("),
                "looking for a cradle may now load a chunk, or reads the whole cube for it");
    }

    @Test
    void theTwoThingsACradleGivesAStabiliserToSayAreWorkNotFaults() {
        for (StabiliserStatus status : new StabiliserStatus[]{StabiliserStatus.ATTENDING, StabiliserStatus.STEADYING}) {
            assertTrue(status.isWorking(), status + " is not drawn as a machine at work");
            assertFalse(status.isFault(), status + " is drawn as a fault");
        }
        assertTrue(StabiliserStatus.NOT_LINKED.isFault() && !StabiliserStatus.NOT_LINKED.isWorking());
        assertFalse(StabiliserStatus.OFF.isWorking());
        // Sent by ordinal: the six a stabiliser had before it had a second job are where they were.
        StabiliserStatus[] first = {StabiliserStatus.CUSHIONING, StabiliserStatus.OFF, StabiliserStatus.NOT_LINKED,
                StabiliserStatus.GROUNDED, StabiliserStatus.STANDING_DOWN, StabiliserStatus.DEFERRING};
        for (int i = 0; i < first.length; i++) {
            assertEquals(i, first[i].ordinal(), first[i] + " has moved");
            assertEquals(first[i], StabiliserStatus.byIndex(i));
        }
        assertEquals(StabiliserStatus.STEADYING, StabiliserStatus.byIndex(StabiliserStatus.STEADYING.ordinal()));
    }
}
