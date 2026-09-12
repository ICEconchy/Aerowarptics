package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every rift vertex is written in the format its buffer expects.
 *
 * <p>Written after a crash, and one worth describing because the shape of it is not obvious. The
 * apertures draw into three buffers: an opaque face on plain position-and-colour, and two glow passes
 * that were moved onto the beacon beam's format so that shader packs would stop lighting them. A
 * vertex helper that writes the wrong set of attributes for the buffer it was handed does not draw
 * badly. It throws {@code IllegalStateException: Missing elements in vertex} from deep inside
 * {@code BufferBuilder} and takes the client down in the middle of a frame.
 *
 * <p>The first version of that change converted three of the five helpers and missed two. One of the
 * two was reached the moment a rift opened; the other only when a hull was actually passing through
 * one, which is a far rarer thing to be doing and would have shipped.
 *
 * <p>So the invariant is structural rather than behavioural: <em>exactly two methods in the whole
 * class may touch a buffer</em>, one per format, and everything else routes through them. That cannot
 * be checked by rendering something in a unit test, but it can be read off the source, and a helper
 * that starts writing its own vertices again is exactly what this catches.
 */
class RiftVertexFormatTest {

    private static final Path FX = Path.of("src", "main", "java", "uk", "co", "iceconchy",
            "aerowarptics", "client", "fx");

    private static final Path SOURCE = FX.resolve("RiftEffectManager.java");

    /** The only two methods in {@link #SOURCE} allowed to put a vertex into a buffer. */
    private static final List<String> WRITERS = List.of("glowVertex", "solidVertex");

    /**
     * Every file known to draw into a glow buffer, and the method in it that does the writing.
     *
     * <p>Listed rather than discovered so that a new one has to be added here deliberately. That is
     * the point: the second and third crash sites of this bug were both places nobody remembered were
     * drawing into the same buffer.
     */
    private static final List<String[]> GLOW_WRITERS = List.of(
            new String[] {"RiftEffectManager.java", "glowVertex"},
            new String[] {"SummonBeacons.java", "vertex"},
            new String[] {"RiftShimmer.java", "vertex"},
            new String[] {"RiftStormSky.java", "vertex"});

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String source() {
        return read(SOURCE);
    }

    /** Which method a given line of the file sits in, by the last signature seen above it. */
    private static String methodAt(String[] lines, int index) {
        for (int line = index; line >= 0; line--) {
            String text = lines[line];
            if (text.startsWith("    private static ") || text.startsWith("    public static ")) {
                int open = text.indexOf('(');
                if (open < 0) {
                    continue;
                }
                String head = text.substring(0, open);
                return head.substring(head.lastIndexOf(' ') + 1);
            }
        }
        return "<unknown>";
    }

    @Test
    void onlyTheTwoDesignatedWritersTouchABuffer() {
        String[] lines = source().split("\n");
        List<String> offenders = new ArrayList<>();
        int found = 0;
        for (int line = 0; line < lines.length; line++) {
            if (!lines[line].contains(".addVertex(")) {
                continue;
            }
            found++;
            String method = methodAt(lines, line);
            if (!WRITERS.contains(method)) {
                offenders.add(method + " at line " + (line + 1));
            }
        }
        assertEquals(2, found, "expected exactly one buffer write per vertex format");
        assertTrue(offenders.isEmpty(),
                "these write vertices directly instead of going through " + WRITERS
                        + ", so nothing guarantees they match the buffer's format: " + offenders);
    }

    /**
     * That every glow writer supplies everything the beacon beam format asks for.
     *
     * <p>Position and colour are not enough for it: leaving any of the other three off is the exact
     * crash this class exists for.
     */
    @Test
    void everyGlowWriterSuppliesEveryElementOfItsFormat() {
        for (String[] writer : GLOW_WRITERS) {
            String source = read(FX.resolve(writer[0]));
            int start = source.indexOf("void " + writer[1] + "(");
            assertTrue(start > 0, writer[1] + " in " + writer[0] + " has been renamed or removed");
            String body = source.substring(start, source.indexOf("\n    }", start));
            for (String element : List.of(".setColor(", ".setUv(", ".setUv2(", ".setNormal(")) {
                assertTrue(body.contains(element), writer[0] + ": " + writer[1]
                        + " no longer writes " + element + ", which the beacon beam format requires");
            }
        }
    }

    /**
     * That nothing has quietly started drawing into a glow buffer without being checked above.
     *
     * <p>This crash was found once and then found twice more, in two other places that were also
     * drawing rifts and had not been converted. A fourth would be found the same way - by somebody's
     * game closing mid-frame - unless adding one trips this first.
     */
    @Test
    void everyFileDrawingIntoAGlowBufferIsOneThisTestChecks() {
        List<String> checked = new ArrayList<>();
        for (String[] writer : GLOW_WRITERS) {
            checked.add(writer[0]);
        }
        List<String> unchecked = new ArrayList<>();
        try (var files = Files.list(FX)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("AWRenderTypes.java") || checked.contains(name)) {
                    continue;
                }
                String source = read(file);
                if (source.contains("AWRenderTypes.RIFT_FIRE")
                        || source.contains("AWRenderTypes.RIFT_SHARD")) {
                    unchecked.add(name);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        assertTrue(unchecked.isEmpty(),
                "these draw into a glow buffer but nothing checks they write its vertex format: "
                        + unchecked);
    }

    /** And that the solid writer stays plain, because the membrane's format never changed. */
    @Test
    void theSolidWriterStaysOnPositionAndColour() {
        String source = source();
        int start = source.indexOf("private static void solidVertex(");
        assertTrue(start > 0, "solidVertex has been renamed or removed");
        String body = source.substring(start, source.indexOf("\n    }", start));
        assertTrue(body.contains(".setColor("), "solidVertex must still write a colour");
        for (String element : List.of(".setUv(", ".setUv2(", ".setNormal(")) {
            assertTrue(!body.contains(element),
                    "solidVertex writes " + element + ", which the membrane's format has no room for");
        }
    }
}
