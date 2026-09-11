package uk.co.iceconchy.aerowarptics.client.screen;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Modulator's theme-change flourish. Free of Minecraft, so the thing worth checking - that a burst
 * lives, moves, draws its motif and then dies - can be checked without a running game, by handing it an
 * {@link AWDraw} that records rather than paints.
 */
class ThemeMotesTest {

    /** An {@link AWDraw} that remembers what it was asked to draw, so a test can measure a burst. */
    private static final class Recorder implements AWDraw {
        int fills;
        long sumX;
        long sumY;

        @Override
        public void fill(int left, int top, int right, int bottom, int argb) {
            fills++;
            sumX += (left + right) / 2;
            sumY += (top + bottom) / 2;
        }

        float centreY() {
            return fills == 0 ? Float.NaN : sumY / (float) fills;
        }
    }

    @Test
    void everyThemeMapsToAMotif() {
        Map<RiftModulatorTheme, ThemeMotes.Motif> seen = new EnumMap<>(RiftModulatorTheme.class);
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            ThemeMotes.Motif motif = ThemeMotes.motifFor(theme);
            assertNotNull(motif, "no motif for " + theme);
            seen.put(theme, motif);
        }
        assertEquals(RiftModulatorTheme.values().length, seen.size());
        // The ones named by hand, pinned so they cannot quietly swap.
        assertEquals(ThemeMotes.Motif.COG, ThemeMotes.motifFor(RiftModulatorTheme.CLOCKWORK));
        assertEquals(ThemeMotes.Motif.RUNE, ThemeMotes.motifFor(RiftModulatorTheme.ARCANE));
        assertEquals(ThemeMotes.Motif.STREAK, ThemeMotes.motifFor(RiftModulatorTheme.STARBLOCKS));
        assertEquals(ThemeMotes.Motif.CHECK, ThemeMotes.motifFor(RiftModulatorTheme.LUDICROUS));
        assertEquals(ThemeMotes.Motif.ODDMENT, ThemeMotes.motifFor(RiftModulatorTheme.IMPROBABILITY));
    }

    /**
     * No two themes throw the same shape.
     *
     * <p>The flourish exists to answer "what did I just pick?" without the player reading the button,
     * so two themes sharing a motif defeats the entire feature while still drawing something and still
     * passing every other test here.
     */
    @Test
    void everyThemeThrowsItsOwnShape() {
        Map<ThemeMotes.Motif, RiftModulatorTheme> claimed = new EnumMap<>(ThemeMotes.Motif.class);
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            ThemeMotes.Motif motif = ThemeMotes.motifFor(theme);
            RiftModulatorTheme already = claimed.put(motif, theme);
            assertEquals(null, already,
                    theme + " throws the same shape as " + already + ", so the two are indistinguishable");
        }
    }

    /**
     * An improbable mote always resolves to a shape that actually draws.
     *
     * <p>{@code ODDMENT} is not a shape - it is a roll for one - so it has to land on one of the
     * others every time. The failure this guards is an off-by-one in that roll picking {@code ODDMENT}
     * itself, which would draw the fallback for a whole burst and read as the theme having no
     * flourish at all rather than as an error.
     */
    @Test
    void improbableMotesAlwaysDrawARealShape() {
        for (int seed = 0; seed < 40; seed++) {
            ThemeMotes motes = new ThemeMotes(new Random(seed));
            motes.burst(RiftModulatorTheme.IMPROBABILITY, 90F, 90F);
            motes.tick();
            Recorder recorder = new Recorder();
            motes.render(recorder, 0.5F);
            assertTrue(recorder.fills > 0, "an improbable burst drew nothing at seed " + seed);
        }
    }

    @Test
    void aBurstSpawnsMotesAndTheyAllEventuallyDie() {
        ThemeMotes motes = new ThemeMotes(new Random(1));
        assertEquals(0, motes.count());
        motes.burst(RiftModulatorTheme.CLOCKWORK, 100F, 100F);
        assertTrue(motes.count() > 0, "a burst should spawn motes");

        // Longer than the longest life any motif is given, so nothing can linger.
        for (int i = 0; i < 120; i++) {
            motes.tick();
            assertTrue(motes.count() >= 0);
        }
        assertEquals(0, motes.count(), "every mote must eventually die");
    }

    @Test
    void repeatedBurstsStayBounded() {
        ThemeMotes motes = new ThemeMotes(new Random(2));
        for (int i = 0; i < 60; i++) {
            motes.burst(RiftModulatorTheme.ARCANE, 50F, 50F);
        }
        // Capped well below the 60 * 16 it would reach if nothing dropped the oldest.
        assertTrue(motes.count() > 0);
        assertTrue(motes.count() < 60 * 16, "leaning on the button must not grow the list without bound");
    }

    @Test
    void everyMotifDrawsSomething() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            ThemeMotes motes = new ThemeMotes(new Random(3));
            motes.burst(theme, 80F, 80F);
            motes.tick();
            Recorder recorder = new Recorder();
            motes.render(recorder, 0.5F);
            assertTrue(recorder.fills > 0, theme + " should draw at least one pixel");
        }
    }

    @Test
    void deadMotesDrawNothing() {
        ThemeMotes motes = new ThemeMotes(new Random(4));
        motes.burst(RiftModulatorTheme.STANDARD, 60F, 60F);
        for (int i = 0; i < 120; i++) {
            motes.tick();
        }
        Recorder recorder = new Recorder();
        motes.render(recorder, 0.5F);
        assertEquals(0, recorder.fills, "a burst that has died should draw nothing");
    }

    @Test
    void aBurstRisesUpThePanel() {
        // Embers are the clearest case: they live long and are given a gentle upward drift, so the
        // centre of the burst should sit higher (a smaller y) after a few ticks than when it started.
        ThemeMotes motes = new ThemeMotes(new Random(5));
        motes.burst(RiftModulatorTheme.EMBER, 100F, 100F);
        motes.tick();

        Recorder early = new Recorder();
        motes.render(early, 0F);
        for (int i = 0; i < 8; i++) {
            motes.tick();
        }
        Recorder later = new Recorder();
        motes.render(later, 0F);

        assertTrue(later.centreY() < early.centreY(),
                "the burst should rise: " + later.centreY() + " !< " + early.centreY());
    }
}
