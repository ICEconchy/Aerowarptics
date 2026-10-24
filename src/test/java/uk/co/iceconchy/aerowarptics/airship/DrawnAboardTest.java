package uk.co.iceconchy.aerowarptics.airship;

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
 * Every machine this mod draws stays drawn on a ship that has warped.
 *
 * <p>The report was a Rift Modulator gone from the top of a drive after a warp, still ticking, still read by
 * the goggles, drawing nothing. A GeckoLib machine is drawn only if a build of its chunk section filed it, and
 * there are two ways a warp left one unfiled: it arrived after its section was built, or it was on the
 * off-screen list, which a cancelled build can empty. {@link DrawnAboard} closes both, but only for a machine
 * that uses it - and a new machine that forgets draws perfectly on the ground and vanishes only after a warp.
 *
 * <p>Read from the sources, since neither a renderer nor a block entity can be loaded without a game.
 */
class DrawnAboardTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static final Pattern GEO_RENDERER = Pattern.compile("extends\\s+GeoBlockRenderer<(\\w+)>");
    private static final Pattern ON_LOAD = Pattern.compile(
            "public\\s+void\\s+onLoad\\(\\)\\s*\\{\\s*super\\.onLoad\\(\\);\\s*DrawnAboard\\.redraw\\(this\\);");
    private static final Pattern OFF_SCREEN = Pattern.compile(
            "shouldRenderOffScreen\\(\\w+\\s+(\\w+)\\)\\s*\\{\\s*return\\s+([^;]+);");

    @Test
    void everyRenderedMachineAsksToBeDrawnWhenItArrives() {
        Map<String, Path> classes = classes();
        List<String> problems = new ArrayList<>();
        int rendered = 0;
        for (Map.Entry<Path, String> renderer : renderers().entrySet()) {
            Matcher matcher = GEO_RENDERER.matcher(renderer.getValue());
            matcher.find();
            Path entity = classes.get(matcher.group(1));
            if (entity == null) {
                problems.add(renderer.getKey().getFileName() + " draws " + matcher.group(1)
                        + ", which could not be found");
                continue;
            }
            rendered++;
            if (!ON_LOAD.matcher(read(entity)).find()) {
                problems.add(matcher.group(1) + " never calls DrawnAboard.redraw from onLoad, so after a warp it"
                        + " can be on the ship and invisible");
            }
        }
        assertTrue(rendered >= 7, "only " + rendered + " rendered machines found - has the naming changed?");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void noRendererPutsAShipsMachineOnTheOffScreenList() {
        List<String> problems = new ArrayList<>();
        int asked = 0;
        for (Map.Entry<Path, String> renderer : renderers().entrySet()) {
            Matcher matcher = OFF_SCREEN.matcher(renderer.getValue());
            while (matcher.find()) {
                asked++;
                String answer = matcher.group(2).replaceAll("\\s+", "");
                if (!answer.equals("DrawnAboard.offScreen(" + matcher.group(1) + ")")) {
                    problems.add(renderer.getKey().getFileName() + " answers shouldRenderOffScreen with "
                            + matcher.group(2) + " - aboard a ship that is the list a warp can empty");
                }
            }
        }
        assertTrue(asked >= 4, "only " + asked + " shouldRenderOffScreen overrides found - have they moved?");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static Map<Path, String> renderers() {
        return classes().values().stream()
                .collect(Collectors.toMap(Function.identity(), DrawnAboardTest::read))
                .entrySet().stream()
                .filter(entry -> GEO_RENDERER.matcher(entry.getValue()).find())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, Path> classes() {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toMap(path -> path.getFileName().toString().replace(".java", ""),
                            Function.identity(), (first, second) -> first));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
