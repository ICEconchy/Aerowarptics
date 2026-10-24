package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An effect that waits for a shader pack to finish has to be drawn afterwards, and the two lists are kept by hand.
 *
 * <p>{@code AfterShaders.defer} tells an effect to draw nothing; {@code AfterShaders.replay} calls the effects on its
 * own list once the pack is done. One that asks and is not on the list is never drawn under a pack again, with
 * nothing failing and nothing wrong with no pack installed - which is how everybody but a shader user plays. One on
 * the list that does not ask is drawn twice. Neither can be seen without a running game and a pack, so this reads
 * the source for both.
 */
class AfterShadersTest {

    private static final Path FX = Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/fx");

    @Test
    void everyEffectThatDefersIsReplayedAndNoOther() {
        Set<String> deferring = new TreeSet<>();
        try (Stream<Path> files = Files.list(FX)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (!name.equals("AfterShaders") && read(file).contains("AfterShaders.defer(event)")) {
                    deferring.add(name);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        Set<String> replayed = new TreeSet<>();
        Matcher listed = Pattern.compile("(\\w+)::onRenderLevel").matcher(read(FX.resolve("AfterShaders.java")));
        while (listed.find()) {
            replayed.add(listed.group(1));
        }
        assertTrue(deferring.contains("RiftEffectManager"), "the rifts themselves no longer wait for the pack");
        assertEquals(deferring, replayed, "what defers and what AfterShaders replays have drifted apart");
    }

    /** Asked before anything else, or an effect's own early return leaves the stage unrecorded on a frame that needs it. */
    @Test
    void anEffectAsksBeforeItDoesAnythingElse() {
        List<String> late = new ArrayList<>();
        Matcher listed = Pattern.compile("(\\w+)::onRenderLevel").matcher(read(FX.resolve("AfterShaders.java")));
        while (listed.find()) {
            String source = read(FX.resolve(listed.group(1) + ".java"));
            int method = source.indexOf("static void onRenderLevel(RenderLevelStageEvent event) {");
            int asked = source.indexOf("if (AfterShaders.defer(event)) {", method);
            int firstIf = indexOfCode(source, method);
            if (method < 0 || asked != firstIf) {
                late.add(listed.group(1));
            }
        }
        assertTrue(late.isEmpty(), "these do something before asking AfterShaders.defer: " + late);
    }

    /** Where the first statement of a method body starts, past any comment lines. */
    private static int indexOfCode(String source, int method) {
        int at = source.indexOf('\n', method) + 1;
        while (true) {
            int end = source.indexOf('\n', at);
            String line = source.substring(at, end).strip();
            if (!line.isEmpty() && !line.startsWith("//")) {
                return at + source.substring(at, end).indexOf(line);
            }
            at = end + 1;
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path).replace("\r\n", "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
