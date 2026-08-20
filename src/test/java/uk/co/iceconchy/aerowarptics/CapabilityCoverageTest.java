package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every tank in the mod is reachable from outside it.
 *
 * <p>This exists because of a bug that shipped. The Rift Gate held Rift Essence, reported it on its
 * goggles, saved it to disk and drew a readout of it on its own screen - and no pipe in the game could
 * put any in, because nothing registered the capability. None of the parts that looked right went
 * through the capability, so all of them worked; the only symptom was that Create's pipes appeared
 * broken.
 *
 * <p>The check is deliberately made against the source rather than by running the registration. A
 * capability is handed out during a mod event with a live registry behind it, which is not a thing a
 * unit test can stand up - but "is this class named in the method that registers tanks" is exactly the
 * question, and it is answerable by reading.
 */
class CapabilityCoverageTest {

    private static final Path SOURCE = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");
    private static final Path CAPABILITIES = SOURCE.resolve("registry/AWCapabilities.java");

    /** A block entity exposes a tank when it hands one out under that name. */
    private static final Pattern TANK = Pattern.compile(
            "public\\s+(?:IFluidHandler|FluidTank)\\s+tank\\s*\\(");

    @Test
    void everyBlockEntityWithATankIsRegisteredAsAFluidHandler() {
        String registrations = read(CAPABILITIES);
        List<String> problems = new ArrayList<>();

        for (Path path : blockEntitySources()) {
            String source = read(path);
            if (!TANK.matcher(source).find()) {
                continue;
            }
            String name = path.getFileName().toString().replace(".java", "");
            if (!registrations.contains(typeConstant(name))) {
                problems.add(name + " holds a fluid but is never registered in AWCapabilities, so "
                        + "nothing outside the mod can pipe anything into or out of it");
            }
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** And the reverse: a registration naming a class that no longer has a tank is stale. */
    @Test
    void nothingIsRegisteredThatHasNoTank() {
        String registrations = read(CAPABILITIES);
        List<String> problems = new ArrayList<>();

        for (Path path : blockEntitySources()) {
            String name = path.getFileName().toString().replace(".java", "");
            if (!registrations.contains(typeConstant(name))) {
                continue;
            }
            if (!TANK.matcher(read(path)).find()) {
                problems.add(name + " is registered as a fluid handler but no longer has a tank");
            }
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * The registry constant a block entity is registered under.
     *
     * <p>A registration names the block entity <em>type</em> rather than the class - {@code
     * AWBlockEntities.SPATIAL_SIPHON}, not {@code SpatialSiphonBlockEntity} - so that is what has to
     * be looked for. The two are the same name in different clothes, and this test failing because
     * they have stopped being is a perfectly good thing for it to say.
     */
    private static String typeConstant(String className) {
        String bare = className.replace("BlockEntity", "");
        StringBuilder constant = new StringBuilder();
        for (int index = 0; index < bare.length(); index++) {
            char character = bare.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                constant.append('_');
            }
            constant.append(Character.toUpperCase(character));
        }
        return constant.toString();
    }

    private static List<Path> blockEntitySources() {
        try (Stream<Path> stream = Files.walk(SOURCE)) {
            return stream.filter(path -> path.getFileName().toString().endsWith("BlockEntity.java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }
}
