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

    /**
     * Cycling backwards reaches everything and wraps the same way forwards does.
     *
     * <p>The case this exists for is {@link RiftModulatorTheme#STANDARD}, where the naive
     * {@code (ordinal() - 1) % length} is negative and would throw the moment a player shift-clicked
     * the theme button on the first theme - which is both the default and the one they are most
     * likely to be sitting on.
     */
    @Test
    void cyclingBackwardsVisitsEveryThemeAndComesBackRound() {
        Set<RiftModulatorTheme> seen = new HashSet<>();
        RiftModulatorTheme theme = RiftModulatorTheme.STANDARD;
        for (int i = 0; i < RiftModulatorTheme.values().length; i++) {
            seen.add(theme);
            theme = theme.previous();
        }
        assertEquals(RiftModulatorTheme.values().length, seen.size(), "cycling back must reach every theme");
        assertEquals(RiftModulatorTheme.STANDARD, theme, "and wrap back round to where it started");
    }

    /** Forwards then back is where you started, from every theme - including the two that wrap. */
    @Test
    void nextAndPreviousUndoOneAnother() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            assertEquals(theme, theme.next().previous(), "next then previous moved " + theme);
            assertEquals(theme, theme.previous().next(), "previous then next moved " + theme);
        }
    }

    /**
     * The order of this enum is a save format.
     *
     * <p>A theme travels as its ordinal, in the Modulator's NBT and on the wire, so the five that
     * shipped first have to keep the indices they shipped with. Reordering them would repaint every
     * Modulator already placed in a world, silently and irreversibly - so the original five are
     * pinned here by index rather than merely being present.
     */
    @Test
    void theOriginalThemesKeepTheIndicesTheyShippedWith() {
        assertEquals(0, RiftModulatorTheme.STANDARD.ordinal());
        assertEquals(1, RiftModulatorTheme.EMBER.ordinal());
        assertEquals(2, RiftModulatorTheme.STARLIGHT.ordinal());
        assertEquals(3, RiftModulatorTheme.ARCANE.ordinal());
        assertEquals(4, RiftModulatorTheme.CLOCKWORK.ordinal());
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
