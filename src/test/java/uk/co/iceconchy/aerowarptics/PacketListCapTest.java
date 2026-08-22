package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.network.PacketLists;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a list too long to send is cut down deliberately rather than thrown out by the encoder.
 *
 * <p>The bug this covers was a server-side crash, not a display fault. {@code ByteBufCodecs.list(256)}
 * enforces its bound on the write as well as the read, and the query feeding it had no bound at all,
 * so a world with 256 visible anchors threw an {@code EncoderException} every time anybody opened an
 * Astrolabe. The fix is only worth anything if what survives the cut is the part a pilot wanted, so
 * most of what is asserted here is the ranking rather than the size.
 */
class PacketListCapTest {

    private static final Path NETWORK = Path.of("src", "main", "java", "uk", "co", "iceconchy",
            "aerowarptics", "network");

    private static WarpQuote quote(String name, double distance, WarpFailure failure) {
        return new WarpQuote(UUID.randomUUID(), name, Level.OVERWORLD, BlockPos.ZERO, "",
                distance, 0.1D, failure);
    }

    private static List<WarpQuote> quotes(int count) {
        List<WarpQuote> list = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            list.add(quote("anchor " + index, index, WarpFailure.NONE));
        }
        return list;
    }

    @Test
    void aListThatAlreadyFitsIsLeftExactlyAsItWas() {
        List<WarpQuote> original = quotes(PacketLists.MAX_ROWS - 1);
        assertSame(original, PacketLists.cap(original, WarpQuote.NEAREST_USABLE_FIRST));
    }

    @Test
    void aListOfExactlyTheLimitIsNotCut() {
        List<WarpQuote> original = quotes(PacketLists.MAX_ROWS);
        assertEquals(PacketLists.MAX_ROWS,
                PacketLists.cap(original, WarpQuote.NEAREST_USABLE_FIRST).size());
    }

    @Test
    void aLongerListIsCutToTheLimit() {
        assertEquals(PacketLists.MAX_ROWS,
                PacketLists.cap(quotes(400), WarpQuote.NEAREST_USABLE_FIRST).size());
    }

    @Test
    void theNearestDestinationsAreTheOnesKept() {
        List<WarpQuote> capped = PacketLists.cap(quotes(400), WarpQuote.NEAREST_USABLE_FIRST);
        for (WarpQuote quote : capped) {
            assertTrue(quote.distance() < PacketLists.MAX_ROWS,
                    "kept a destination further away than one it dropped: " + quote.name());
        }
    }

    @Test
    void everyReachableDestinationSortsAheadOfEveryUnreachableOne() {
        List<WarpQuote> mixed = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            // Deliberately interleaved, and with the unusable ones nearer: distance must not be
            // allowed to promote a destination the drive would refuse.
            mixed.add(quote("far but usable " + index, 5000 + index, WarpFailure.NONE));
            mixed.add(quote("near but not " + index, index, WarpFailure.DESTINATION_TOO_FAR));
        }
        List<WarpQuote> capped = PacketLists.cap(mixed, WarpQuote.NEAREST_USABLE_FIRST);

        boolean seenUnusable = false;
        for (WarpQuote quote : capped) {
            if (quote.usable()) {
                assertFalse(seenUnusable, "a reachable destination sorted below an unreachable one");
            } else {
                seenUnusable = true;
            }
        }
    }

    @Test
    void distancesAscendAmongWhatIsKept() {
        List<WarpQuote> shuffled = new ArrayList<>(quotes(400));
        java.util.Collections.shuffle(shuffled, new java.util.Random(7));
        List<WarpQuote> capped = PacketLists.cap(shuffled, WarpQuote.NEAREST_USABLE_FIRST);
        for (int index = 1; index < capped.size(); index++) {
            assertTrue(capped.get(index - 1).distance() <= capped.get(index).distance(),
                    "distances are not ascending at row " + index);
        }
    }

    @Test
    void aDestinationInAnotherDimensionDoesNotOutrankOneTheShipCouldReach() {
        List<WarpQuote> mixed = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            // Cross-dimension quotes carry -1, which sorts first on distance alone.
            mixed.add(quote("elsewhere " + index, -1.0D, WarpFailure.NONE));
            mixed.add(quote("here " + index, 1000 + index, WarpFailure.NONE));
        }
        List<WarpQuote> capped = PacketLists.cap(mixed, WarpQuote.NEAREST_USABLE_FIRST);
        assertTrue(capped.getFirst().sameDimension(),
                "a destination with no distance to rank by was sorted to the top of the chart");
    }

    @Test
    void theSameListAlwaysTruncatesTheSameWay() {
        List<WarpQuote> original = quotes(400);
        assertEquals(PacketLists.cap(original, WarpQuote.NEAREST_USABLE_FIRST),
                PacketLists.cap(new ArrayList<>(original), WarpQuote.NEAREST_USABLE_FIRST));
    }

    /**
     * The assertion that stops this coming back.
     *
     * <p>The crash was possible because the bound lived as a literal inside the codec, where nothing
     * connected it to the query that filled the list. Both packets now read the one constant, and a
     * literal creeping back into either is the exact shape of the regression.
     */
    @Test
    void noPacketEncodesAgainstALimitOfItsOwn() {
        try (Stream<Path> files = Files.walk(NETWORK)) {
            List<String> offenders = new ArrayList<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String line : source.split("\n")) {
                    if (line.contains("ByteBufCodecs.list(") && !line.contains("MAX_ROWS")
                            && !line.contains("MAX_LISTED")) {
                        offenders.add(file.getFileName() + ": " + line.strip());
                    }
                }
            }
            assertTrue(offenders.isEmpty(),
                    "a packet bounds its own list instead of sharing the cap that is enforced upstream: "
                            + offenders);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
