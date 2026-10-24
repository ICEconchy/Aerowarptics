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
            new String[] {"RiftStormSky.java", "vertex"},
            new String[] {"HyperspaceStreaks.java", "vertex"},
            new String[] {"FlotillaEffects.java", "vertex"},
            new String[] {"CollapseSpectacle.java", "vertex"},
            new String[] {"WellheadSpectacle.java", "vertex"},
            // A Rift Sunderer's tear, and the hand that comes through it, which is handed the same glow buffer.
            new String[] {"SundererSpectacle.java", "fire"},
            new String[] {"RiftHandRenderer.java", "corner"},
            // The Rift Plotter's guides and strike, drawn through its strokes in hologram and fire layers alike.
            new String[] {"LightStrokes.java", "glowVertex"},
            // Outside fx: the brazier's flames are drawn by its block entity renderer, in its own frame.
            new String[] {"../render/RiftBrazierRenderer.java", "vertex"});

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

    /**
     * That the End's surface asks for nothing the solid writer does not supply.
     *
     * <p>The Ender face and bore are written by {@code solidVertex}, which writes a position and a
     * colour, into a buffer on the End portal shader's format. That is safe only because the format is
     * plain position: {@code BufferBuilder} skips an element its format lacks, but throws on one it
     * needs and was not given. Any richer format here - one with texture coordinates, say, which would
     * be the natural thing to reach for next to two textures - is the crash this whole file is about.
     */
    @Test
    void theEndSkyTakesNothingTheSolidWriterDoesNotSupply() {
        String source = read(FX.resolve("AWRenderTypes.java"));
        int start = source.indexOf("RIFT_END_SKY = RenderType.create(");
        assertTrue(start > 0, "RIFT_END_SKY has been renamed or removed");
        String declaration = source.substring(start, source.indexOf(";", start));
        assertTrue(declaration.contains("DefaultVertexFormat.POSITION,"),
                "RIFT_END_SKY is no longer on plain position, which solidVertex cannot be trusted to fill: "
                        + declaration);
        String manager = source();
        assertTrue(manager.contains("getBuffer(AWRenderTypes.RIFT_END_SKY)"),
                "nothing draws into RIFT_END_SKY any more");
    }

    /**
     * That the lens writes exactly what its buffer asks for: a position, a texture coordinate and a colour.
     *
     * <p>The one render type here with a texture coordinate that means something - it is where in the
     * copied frame each corner looks - and so the one a writer could most plausibly be "tidied" into
     * leaving out, or into writing the glow writer's light and normal as well. Either is the crash above.
     * It must also have exactly one writer, for the same reason the rift passes have one each.
     */
    @Test
    void theLensWritesExactlyItsOwnFormat() {
        String types = read(FX.resolve("AWRenderTypes.java"));
        int start = types.indexOf("GRAVITY_LENS = RenderType.create(");
        assertTrue(start > 0, "GRAVITY_LENS has been renamed or removed");
        String declaration = types.substring(start, types.indexOf(";", start));
        assertTrue(declaration.contains("DefaultVertexFormat.POSITION_TEX_COLOR,"),
                "GRAVITY_LENS is no longer on position, texture and colour: " + declaration);

        String lens = read(FX.resolve("GravityLens.java"));
        assertEquals(1, lens.split("\\.addVertex\\(", -1).length - 1,
                "the lens writes vertices from more than one place");
        int writer = lens.indexOf("private static void lensVertex(");
        assertTrue(writer > 0, "lensVertex has been renamed or removed");
        String body = lens.substring(writer, lens.indexOf("\n    }", writer));
        assertTrue(body.contains(".addVertex(") && body.contains(".setUv(") && body.contains(".setColor("),
                "lensVertex no longer writes all of position, texture and colour");
        for (String element : List.of(".setUv2(", ".setNormal(", ".setOverlay(")) {
            assertTrue(!body.contains(element), "lensVertex writes " + element + ", which GRAVITY_LENS has no room for");
        }
        assertTrue(lens.contains("getBuffer(AWRenderTypes.GRAVITY_LENS)"), "nothing draws into GRAVITY_LENS any more");
    }

    /**
     * That a gate passage's quad writes exactly what it is drawn in: a position and a texture coordinate.
     *
     * <p>Not a render type - it is one quad drawn straight through a style's shader, in the GUI's pass -
     * but the same crash is waiting for it, and with nothing in front of it: this quad is the whole of
     * what a traveller sees. Three places have to agree on the format, and each names it separately: the
     * buffer the quad is begun in, the one method that writes its corners, and the shaders' registration.
     */
    @Test
    void aPassagesQuadIsWrittenInExactlyItsOwnFormat() {
        String painter = read(FX.resolve("GatePassagePainter.java"));
        assertTrue(painter.contains(".begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)"),
                "a passage's quad is no longer begun on position and texture");
        assertEquals(1, painter.split("\\.addVertex\\(", -1).length - 1,
                "a passage's quad is written from more than one place");
        int writer = painter.indexOf("private static void corner(");
        assertTrue(writer > 0, "corner has been renamed or removed");
        String body = painter.substring(writer, painter.indexOf("\n    }", writer));
        assertTrue(body.contains(".addVertex(") && body.contains(".setUv("),
                "corner no longer writes both position and texture");
        for (String element : List.of(".setColor(", ".setUv2(", ".setNormal(", ".setOverlay(")) {
            assertTrue(!body.contains(element), "corner writes " + element + ", which its format has no room for");
        }
        String setup = read(FX.resolve("../AWClientSetup.java"));
        assertTrue(java.util.regex.Pattern.compile("AeroWarptics\\.id\\(shader\\),\\s*DefaultVertexFormat\\.POSITION_TEX\\)")
                .matcher(setup).find(), "a passage's shader is no longer registered on position and texture");
    }

    /**
     * The storm's sky, and which of its writers feeds which buffer: a colour writer for the plain position-and-colour
     * types it hangs in the sky ({@code RIFT_AURORA} and {@code RIFT_SKY_SHADE}), and a bare position writer for
     * the End's surface a tear shows.
     */
    private static final List<String[]> SKY_COLOUR_WRITERS = List.of(
            new String[] {"RiftStormAurora.java", "vertex"},
            new String[] {"RiftStormVortex.java", "vertex"},
            new String[] {"RiftStormTears.java", "lightVertex"},
            new String[] {"RiftStormStreaks.java", "vertex"},
            new String[] {"RiftStormCloudFlashes.java", "vertex"});

    private static String body(String source, String method) {
        int start = source.indexOf("void " + method + "(");
        assertTrue(start > 0, method + " has been renamed or removed");
        return source.substring(start, source.indexOf("\n    }", start));
    }

    /**
     * That the storm's sky writes exactly position and colour, and nothing more.
     *
     * <p>Five files, each with its own writer, and every one of them on a format with no room for a texture
     * coordinate, a light or a normal - so the crash here is the other way round from the glow writers': a writer
     * "tidied" into looking like theirs. Every vertex in each file must also come from its writers, or nothing
     * holds it to the format either.
     */
    @Test
    void theStormsSkyWritesPositionAndColourOnly() {
        for (String[] writer : SKY_COLOUR_WRITERS) {
            String source = read(FX.resolve(writer[0]));
            String body = body(source, writer[1]);
            assertTrue(body.contains(".addVertex(") && body.contains(".setColor("),
                    writer[0] + ": " + writer[1] + " no longer writes a position and a colour");
            for (String element : List.of(".setUv(", ".setUv2(", ".setNormal(", ".setLight(", ".setOverlay(")) {
                assertTrue(!body.contains(element), writer[0] + ": " + writer[1] + " writes " + element
                        + ", which a position-and-colour type has no room for");
            }
            String[] lines = source.split("\n");
            for (int line = 0; line < lines.length; line++) {
                if (lines[line].contains(".addVertex(")) {
                    String method = methodAt(lines, line);
                    assertTrue(method.equals(writer[1]) || method.equals("endVertex"),
                            writer[0] + " writes a vertex outside its writers, in " + method + " at line " + (line + 1));
                }
            }
        }
    }

    /** And that the End's surface a tear shows is written as plain position, the whole of its format. */
    @Test
    void aTearsEndSkyIsPlainPosition() {
        String source = read(FX.resolve("RiftStormTears.java"));
        assertTrue(source.contains("getBuffer(AWRenderTypes.RIFT_END_SKY)"), "the tears no longer draw the End's sky");
        String body = body(source, "endVertex");
        assertTrue(body.contains(".addVertex("), "endVertex no longer writes a position");
        for (String element : List.of(".setUv(", ".setUv2(", ".setNormal(", ".setLight(")) {
            assertTrue(!body.contains(element), "endVertex writes " + element + ", which RIFT_END_SKY has no room for");
        }
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
