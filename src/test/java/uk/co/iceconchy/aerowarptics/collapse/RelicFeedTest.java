package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which input a relic machine goes by, and that its blocks ask the one rule for their shafts. The rule itself is
 * {@code CradleStructureTest}'s; that Create then links nothing across the machine can only be seen in a game.
 */
class RelicFeedTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    @Test
    void nothingTurningIsNoFeeder() {
        assertEquals(RelicFeed.NONE, RelicFeed.feeder());
        assertEquals(RelicFeed.NONE, RelicFeed.feeder(0.0F, 0.0F, 0.0F, 0.0F));
    }

    @Test
    void theFastestInputFeedsWhicheverWayItTurns() {
        assertEquals(2, RelicFeed.feeder(0.0F, 32.0F, -128.0F, 64.0F));
        assertEquals(0, RelicFeed.feeder(-256.0F, 32.0F, 128.0F, 64.0F));
        assertEquals(3, RelicFeed.feeder(0.0F, 0.0F, 0.0F, 1.0F));
    }

    /** Two as fast as each other: the first, every time, so every block and both sides of the wire agree. */
    @Test
    void aTieGoesToTheFirst() {
        assertEquals(1, RelicFeed.feeder(0.0F, 64.0F, -64.0F, 64.0F));
        assertEquals(1, RelicFeed.feeder(0.0F, -64.0F, 64.0F, 64.0F));
    }

    /** A master or a base that answers for its own shafts could grow a second, and be a way through the machine. */
    @Test
    void theBlocksAskTheOneRule() throws IOException {
        for (String master : new String[]{"collapse/CollapseCradleBlock.java", "plotter/RiftPlotterBlock.java",
                "ward/RiftWardBlock.java"}) {
            String body = shaftBody(Files.readString(SOURCES.resolve(master)));
            assertTrue(body.contains("CradleStructure.shaftTowards(pos, pos, face)"), master + " decides its own shafts");
            assertFalse(body.contains("return face"), master + " decides its own shafts");
        }
        String base = shaftBody(Files.readString(SOURCES.resolve("collapse/RelicBaseBlock.java")));
        assertTrue(base.contains("CradleStructure.shaftTowards("), "a base decides its own shafts");
    }

    private static String shaftBody(String source) {
        int from = source.indexOf("public boolean hasShaftTowards(");
        assertTrue(from >= 0, "no hasShaftTowards");
        return source.substring(from, source.indexOf("\n    }", from));
    }
}
