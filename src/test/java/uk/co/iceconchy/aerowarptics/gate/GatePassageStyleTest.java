package uk.co.iceconchy.aerowarptics.gate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list of passage styles, as the dial's button and the client config see it.
 *
 * <p>A style is a line in an enum, a shader, two translations and a name in a config file, and the first
 * three are checked where they are used. This holds the list itself: that the button can reach every
 * style from every other in either direction, that the names a config file holds stay what they were,
 * and that the plain style - the one every other falls back to - is there and needs nothing.
 */
class GatePassageStyleTest {

    private static final Path CORE = Path.of("src/main/resources/assets/aerowarptics/shaders/core");

    /** Stepping on from any style reaches every style, and comes back to where it began. */
    @Test
    void theButtonStepsThroughEveryStyleAndRound() {
        int count = GatePassageStyle.values().length;
        for (GatePassageStyle start : GatePassageStyle.values()) {
            Set<GatePassageStyle> seen = new HashSet<>();
            GatePassageStyle style = start;
            for (int step = 0; step < count; step++) {
                seen.add(style);
                style = style.next();
            }
            assertEquals(count, seen.size(), "stepping on from " + start + " misses a style");
            assertEquals(start, style, "stepping on from " + start + " does not come back to it");
        }
    }

    /** A shifted click undoes a click, from every style - the first included, where the arithmetic goes negative. */
    @Test
    void steppingBackUndoesSteppingOn() {
        for (GatePassageStyle style : GatePassageStyle.values()) {
            assertEquals(style, style.next().previous());
            assertEquals(style, style.previous().next());
        }
    }

    /**
     * The names a player's config file holds. Renaming one silently puts every player who chose it back on
     * the default, the next time their config is read.
     */
    @Test
    void theNamesAConfigFileHoldsHaveNotChanged() {
        for (String name : new String[] {"KAWOOSH", "TIMEY_WIMEY", "PUNCH_IT", "ENERGISE", "FULL_OF_STARS",
                "DOWN_THE_PIPE", "QUIET"}) {
            assertNotNull(GatePassageStyle.valueOf(name));
        }
    }

    /** The plain style is what every other falls back to, so it can need nothing - and it comes last. */
    @Test
    void thePlainStyleNeedsNoShaderAndComesLast() {
        assertNull(GatePassageStyle.QUIET.shader());
        GatePassageStyle[] values = GatePassageStyle.values();
        assertEquals(GatePassageStyle.QUIET, values[values.length - 1]);
    }

    /** Every other style's shader is on disk, under a name of its own, with the vertex program they share. */
    @Test
    void everyShadedStyleHasItsShader() {
        Set<String> shaders = new HashSet<>();
        for (GatePassageStyle style : GatePassageStyle.values()) {
            String shader = style.shader();
            if (shader == null) {
                continue;
            }
            assertTrue(shaders.add(shader), "two styles share " + shader);
            assertTrue(Files.exists(CORE.resolve(shader + ".json")), style + " has no " + shader + ".json");
            assertTrue(Files.exists(CORE.resolve(shader + ".fsh")), style + " has no " + shader + ".fsh");
        }
        assertTrue(shaders.size() >= 6, "styles have lost their shaders: " + shaders);
        assertTrue(Files.exists(CORE.resolve("passage.vsh")), "the vertex program every passage shares is gone");
    }

    /** Each style's two translation keys are its own. */
    @Test
    void everyStyleHasKeysOfItsOwn() {
        Set<String> keys = new HashSet<>();
        for (GatePassageStyle style : GatePassageStyle.values()) {
            assertTrue(keys.add(style.translationKey()), style + " shares its name's key");
            assertTrue(keys.add(style.aboutKey()), style + " shares its description's key");
            assertTrue(style.translationKey().startsWith("gate.passage."));
        }
    }
}
