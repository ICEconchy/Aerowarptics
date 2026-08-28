package uk.co.iceconchy.aerowarptics.modulator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Rift Modulator's theme: purely a client-side selector, so this is what there is to test. */
class RiftModulatorThemeTest {

    @Test
    void everyThemeHasItsOwnTranslationKey() {
        Set<String> keys = new HashSet<>();
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            assertTrue(keys.add(theme.translationKey()), "duplicate key for " + theme);
        }
        assertEquals(RiftModulatorTheme.values().length, keys.size());
    }

    @Test
    void cyclingVisitsEveryThemeAndComesBackRound() {
        Set<RiftModulatorTheme> seen = new HashSet<>();
        RiftModulatorTheme theme = RiftModulatorTheme.STANDARD;
        for (int i = 0; i < RiftModulatorTheme.values().length; i++) {
            seen.add(theme);
            theme = theme.next();
        }
        assertEquals(RiftModulatorTheme.values().length, seen.size(), "cycling must reach every theme");
        assertEquals(RiftModulatorTheme.STANDARD, theme, "and wrap back round to where it started");
    }

    @Test
    void indexRoundTripIsStableAndBoundsSafe() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            assertEquals(theme, RiftModulatorTheme.byIndex(theme.ordinal()));
        }
        assertEquals(RiftModulatorTheme.STANDARD, RiftModulatorTheme.byIndex(-1));
        assertEquals(RiftModulatorTheme.STANDARD, RiftModulatorTheme.byIndex(99));
    }
}
