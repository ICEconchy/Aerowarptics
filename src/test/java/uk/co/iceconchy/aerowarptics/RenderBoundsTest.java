package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block entity that says how far it is drawn is only heard if its renderer passes that on.
 *
 * <p>NeoForge 21.1 decides whether a block entity renderer is in view by asking the <em>renderer</em>
 * for a box, and the default is the single block the entity stands in. The block entity's own
 * {@code getRenderBoundingBox()} still compiles as an override - it is Create's hook - and GeckoLib's
 * renderer never reads it. So a machine whose model reaches past its block draws perfectly, right up
 * until its own block leaves the edge of the screen, and then vanishes with the rest of it plainly in
 * view. That shipped for an extended Rift Drive, which is seven blocks of model hanging off one, and had
 * been quietly true of the Astrolabe's three-by-three table for longer.
 *
 * <p>Read from the sources, since neither side can be loaded without a running game: every GeckoLib
 * renderer whose block entity sizes its box must hand that box on.
 */
class RenderBoundsTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static final Pattern GEO_RENDERER = Pattern.compile("extends\\s+GeoBlockRenderer<(\\w+)>");

    @Test
    void everyRendererHandsOnTheBoxItsBlockEntityAsksFor() {
        Map<String, Path> classes;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            classes = files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toMap(path -> path.getFileName().toString().replace(".java", ""),
                            Function.identity(), (first, second) -> first));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        List<String> problems = new ArrayList<>();
        int sized = 0;
        for (Path renderer : classes.values()) {
            String rendererSource = read(renderer);
            Matcher matcher = GEO_RENDERER.matcher(rendererSource);
            if (!matcher.find()) {
                continue;
            }
            Path entity = classes.get(matcher.group(1));
            if (entity == null) {
                problems.add(renderer.getFileName() + " draws " + matcher.group(1) + ", which could not be found");
                continue;
            }
            if (!read(entity).contains("getRenderBoundingBox()")) {
                continue;
            }
            sized++;
            if (!rendererSource.contains("getRenderBoundingBox(" + matcher.group(1))) {
                problems.add(renderer.getFileName() + " never passes on " + matcher.group(1)
                        + ".getRenderBoundingBox(), so it is culled to a single block");
            }
        }
        assertTrue(sized >= 3, "only " + sized + " sized block entities found - has the naming changed?");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
