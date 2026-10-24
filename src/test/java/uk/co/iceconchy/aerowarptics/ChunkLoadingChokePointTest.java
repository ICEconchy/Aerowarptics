package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing loads a chunk except through {@code hold/}.
 *
 * <p>{@code Holds} is where the rules that keep a world safe live - never a ticket at a ship's plot
 * coordinates, a ship held by Sable's ticket rather than by forcing its ground, a lease on everything that
 * persists. A new ticket placed straight from some block entity compiles, runs, and quietly bypasses every
 * one of them; the first forced chunk in plot space cost every world with a drive in it the ability to save
 * on exit, and nothing in the log said why. So the calls that load chunks are allowed in one package and
 * this reads the rest of the source for them.
 *
 * <p>Checked by breaking it: a stray {@code addRegionTicket} added to a gate fails this test.
 */
class ChunkLoadingChokePointTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "uk", "co", "iceconchy", "aerowarptics");

    /** Every way the game offers to hold a chunk: region tickets, forced chunks, and Sable's ship tickets. */
    private static final Pattern LOADS = Pattern.compile(
            "\\.(addRegionTicket|forceChunk|setChunkForced|addForceLoadTicket|removeForceLoadTicket)\\s*\\(");

    @Test
    void onlyTheHoldPackageLoadsChunks() {
        List<String> offenders = new ArrayList<>();
        Path holds = SOURCE.resolve("hold");
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (file.startsWith(holds)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("*") || line.startsWith("//")) {
                        continue;
                    }
                    if (LOADS.matcher(line).find()) {
                        offenders.add(SOURCE.relativize(file) + ":" + (i + 1) + "  " + line);
                    }
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertTrue(offenders.isEmpty(), "chunks loaded outside hold/Holds, past its plot-space and lease rules:\n  "
                + String.join("\n  ", offenders));
    }

    /** The scan itself still finds what it is looking for, or a rename would make it pass by seeing nothing. */
    @Test
    void theScanStillSeesTheCallsItPolices() throws IOException {
        String holds = Files.readString(SOURCE.resolve("hold").resolve("Holds.java"));
        String residency = Files.readString(SOURCE.resolve("hold").resolve("AirshipResidency.java"));
        assertTrue(LOADS.matcher(holds).find(), "Holds no longer places a ticket the scan recognises");
        assertTrue(LOADS.matcher(residency).find(), "AirshipResidency no longer forces a chunk the scan recognises");
    }
}
