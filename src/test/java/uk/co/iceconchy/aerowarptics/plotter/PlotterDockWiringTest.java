package uk.co.iceconchy.aerowarptics.plotter;

import org.junit.jupiter.api.Test;

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
 * The dock a player sizes at the table is the dock that catches ships, and the dock that is drawn.
 *
 * <p>{@link FiringTest} and {@link DockAlignTest} hold the arithmetic, but a size nobody asks for sizes nothing, and
 * every way this can come unhooked is silent. A machine that still read the server's depth and sized its own radius
 * off the rift would compile, take the sliders, save them, draw them - and go on catching ships in the dock it always
 * had, some other shape than the one glowing in the air. Read from the sources, since the machine cannot be loaded
 * without a game.
 */
class PlotterDockWiringTest {

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

    private static int count(String text, String piece) {
        int count = 0;
        for (int at = text.indexOf(piece); at >= 0; at = text.indexOf(piece, at + 1)) {
            count++;
        }
        return count;
    }

    @Test
    void theMachineCatchesInTheDockItWasSet() {
        String machine = source("plotter/RiftPlotterBlockEntity.java");
        String dock = method(machine, "private void dock(ServerLevel server)");
        assertTrue(dock.contains("double zone = dockRadius();") && dock.contains("double reach = dockDepth();"),
                "the dock helper no longer asks how big this plotter's dock is");
        assertTrue(dock.contains("catchOne(server, near, radius, zone, reach)"),
                "ships are no longer caught in the dock that was set");
        assertTrue(dock.contains("held(vehicle, near, radius, zone, reach)"),
                "a caught ship is no longer held to the dock that was set");
        assertEquals("firing.dockRadius(plot.radius())", returned(machine, "public double dockRadius()"));
        assertEquals("firing.dockDepth(dockReach())", returned(machine, "public double dockDepth()"));
        // The server's own depth is the depth of a dock nobody sized, and nothing else: read once for that, and once
        // to tell the clients what it is.
        assertEquals(2, count(machine, "AWConfig.PLOTTER_DOCK_REACH"),
                "something new reads the server's dock depth past the plotter's own setting");
        assertFalse(machine.contains("DockAlign.zoneRadius("),
                "the machine sizes a dock off its rift again, whatever the plotter was set to");
        String held = method(machine, "private static boolean held(");
        assertTrue(held.contains("DockAlign.keepDepth(near, reach, size, lined)"),
                "a hull longer than its dock is deep would be dropped on the way to its own mark");
    }

    /** The guide is the dock: drawn from the same two questions the machine catches by, draft and standing alike. */
    @Test
    void theDockDrawnIsTheDockThatCatches() {
        String effects = source("client/fx/PlotterEffects.java");
        assertTrue(effects.contains("plotter.dockRadius(),") && effects.contains("plotter.dockDepth(),"),
                "a standing plotter's guide is no longer drawn at its dock's own size");
        String guides = source("client/fx/PlotterGuides.java");
        assertTrue(guides.contains("firing.dockRadius(radius), firing.dockDepth(dockReach)"),
                "the dock being set at the table is no longer drawn at the size being set");
        assertFalse(guides.contains("DockAlign.zoneRadius("), "the guide sizes the dock off the rift again");
    }

    /** What a one-line accessor returns. */
    private static String returned(String source, String signature) {
        String body = method(source, signature);
        Matcher statement = Pattern.compile("return (.+);").matcher(body);
        assertTrue(statement.find(), signature + " returns nothing this test can read");
        return statement.group(1);
    }
}
