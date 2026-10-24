package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The borrowed themes' colours.
 *
 * <p>Nothing here is about taste. What is worth pinning down is the set of ways a palette goes wrong
 * without anything throwing: a theme that should wear its own colours falling back to the swatch, two
 * themes wearing the same ones, a rim - which is drawn additively - too dark to add anything, and a
 * tartan with a thread nobody can see.
 */
class ThemeLookTest {

    /**
     * The themes drawn in the colours the pilot picked: the five originals, and Sketch - whose paper and
     * pencil are its own, but whose crayons are the swatches.
     */
    private static final Set<RiftModulatorTheme> ORIGINALS = EnumSet.of(RiftModulatorTheme.STANDARD,
            RiftModulatorTheme.EMBER, RiftModulatorTheme.STARLIGHT, RiftModulatorTheme.ARCANE,
            RiftModulatorTheme.CLOCKWORK, RiftModulatorTheme.SKETCH);

    private static Set<RiftModulatorTheme> borrowed() {
        Set<RiftModulatorTheme> out = EnumSet.allOf(RiftModulatorTheme.class);
        out.removeAll(ORIGINALS);
        return out;
    }

    @Test
    void everyThemeWithoutItsOwnColoursWearsThePilotsColours() {
        for (RiftModulatorTheme theme : ORIGINALS) {
            assertNull(ThemeLook.palette(theme, 12_345), theme + " took the swatch away from its pilot");
        }
    }

    @Test
    void everyBorrowedThemeBringsItsOwnColours() {
        // The eight borrowed jumps, and the End.
        assertEquals(9, borrowed().size(), "the borrowed set changed size - check this test still means anything");
        for (RiftModulatorTheme theme : borrowed()) {
            assertNotNull(ThemeLook.palette(theme, 12_345), theme + " has no palette of its own");
        }
    }

    /** Two themes in one palette would read as one theme, whatever their shapes did. */
    @Test
    void noTwoBorrowedThemesShareAPalette() {
        Set<Integer> cores = new HashSet<>();
        for (RiftModulatorTheme theme : borrowed()) {
            if (theme == RiftModulatorTheme.IMPROBABILITY) {
                continue; // rolled per rift - covered below
            }
            assertTrue(cores.add(ThemeLook.palette(theme, 0).core()), theme + " shares its core colour");
        }
    }

    @Test
    void improbabilityIsNeverTheSameColourTwice() {
        Set<Integer> cores = new HashSet<>();
        for (int seed = 0; seed < 32; seed++) {
            cores.add(ThemeLook.palette(RiftModulatorTheme.IMPROBABILITY, seed * 0x9E3779B1).core());
        }
        assertTrue(cores.size() >= 16, "improbability rolled only " + cores.size() + " colours in 32 rifts");
    }

    /**
     * The rim is drawn additively, so a dark rim adds nothing and simply is not there.
     *
     * <p>The core is exempt on purpose. It lands on the opaque face and the bore, which are the only
     * surfaces in a rift that <em>can</em> be dark - and Bedrock's singularity has to be.
     */
    @Test
    void everyRimCanBeSeenAgainstTheDark() {
        for (RiftModulatorTheme theme : borrowed()) {
            for (int seed = 0; seed < 64; seed++) {
                int rim = ThemeLook.palette(theme, seed * 0x85EBCA77).rim();
                assertTrue(ThemeLook.luminance(rim) >= 0.45F,
                        theme + " drew its rim in " + Integer.toHexString(rim) + ", too dark to glow");
            }
        }
    }

    @Test
    void everyThreadOfTheSettCanBeSeen() {
        int units = 0;
        for (int band = 0; band < ThemeLook.settBands(); band++) {
            assertTrue(ThemeLook.settWidth(band) > 0, "thread " + band + " has no width");
            assertTrue(ThemeLook.luminance(ThemeLook.settColour(band)) > 0.04F,
                    "thread " + band + " is too dark to be drawn as light");
            units += ThemeLook.settWidth(band);
        }
        assertEquals(units, ThemeLook.settUnits(), "the sett's width disagrees with its threads");
    }

    @Test
    void theSettRunsOnRoundItsRepeatInBothDirections() {
        int bands = ThemeLook.settBands();
        for (int band = 0; band < bands; band++) {
            assertEquals(ThemeLook.settColour(band), ThemeLook.settColour(band + bands));
            assertEquals(ThemeLook.settColour(band), ThemeLook.settColour(band - bands));
            assertEquals(ThemeLook.settWidth(band), ThemeLook.settWidth(band + 3 * bands));
        }
    }

    /** A channel that overflowed would bleed into its neighbour and turn a blue red. */
    @Test
    void hsvStaysInsideTheColourCube() {
        for (float hue = -2.0F; hue <= 2.0F; hue += 0.013F) {
            for (float saturation = 0.0F; saturation <= 1.0F; saturation += 0.25F) {
                int rgb = ThemeLook.hsv(hue, saturation, 1.0F);
                assertEquals(0, rgb & 0xFF000000, "hsv spilled out of 24 bits at hue " + hue);
            }
        }
        assertEquals(0xFF0000, ThemeLook.hsv(0.0F, 1.0F, 1.0F), "pure red is where the wheel starts");
        assertEquals(ThemeLook.hsv(0.25F, 1.0F, 1.0F), ThemeLook.hsv(1.25F, 1.0F, 1.0F), "the wheel wraps");
    }

    @Test
    void onlyTheQuietBorrowedThemesGoWithoutLightning() {
        for (RiftModulatorTheme theme : ORIGINALS) {
            if (theme == RiftModulatorTheme.SKETCH) {
                continue; // wears the pilot's colours like the originals, but a pencil does not spark
            }
            assertTrue(ThemeLook.crackles(theme), theme + " lost its lightning");
        }
        assertFalse(ThemeLook.crackles(RiftModulatorTheme.STARBLOCKS), "hyperspace does not crackle");
        assertFalse(ThemeLook.crackles(RiftModulatorTheme.BOLDLY_GONE), "a warp bubble does not crackle");
        assertTrue(ThemeLook.crackles(RiftModulatorTheme.EVENTFUL_HORIZON), "a gravity drive is violent");
        assertTrue(ThemeLook.crackles(RiftModulatorTheme.VWORP), "the vortex is full of lightning");
        assertFalse(ThemeLook.crackles(RiftModulatorTheme.ENDER), "the End is silent");
        assertTrue(ThemeLook.crackles(RiftModulatorTheme.MOSTLY_DANGEROUS), "a capital ship's cloud never stops");
        assertFalse(ThemeLook.crackles(RiftModulatorTheme.SKETCH), "a pencil does not spark");
    }

    /**
     * Only the three themes that are different in kind have a different surface.
     *
     * <p>A theme landing on the End's shader by mistake would still hide a hull - it is just as opaque -
     * but a theme landing on paper by mistake would lose its fire, its haze and its furniture without a
     * word, since every light pass skips a paper rift; and one landing in the cloud would lose its fire
     * and grow a thundercloud nobody asked for.
     */
    @Test
    void onlyTheThemesDifferentInKindChangeWhatTheFaceIsMadeOf() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            ThemeLook.Surface expected = switch (theme) {
                case ENDER -> ThemeLook.Surface.END_SKY;
                case SKETCH -> ThemeLook.Surface.PAPER;
                case MOSTLY_DANGEROUS -> ThemeLook.Surface.NEBULA;
                default -> ThemeLook.Surface.LIT;
            };
            assertEquals(expected, ThemeLook.surface(theme), theme + " is made of the wrong thing");
            assertEquals(theme == RiftModulatorTheme.SKETCH, ThemeLook.inked(theme),
                    theme + " is drawn in the wrong medium");
        }
    }

    /**
     * A wind-up's sounds are moments inside the wind-up, in order.
     *
     * <p>A beat at or past one would never be heard - the tick only listens while the rift is still
     * winding up - and one out of order would be heard late, after the stroke it belongs to.
     */
    @Test
    void everyBeatFallsInsideItsWindUpInOrder() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            float[] beats = ThemeLook.beats(theme);
            for (int beat = 0; beat < beats.length; beat++) {
                assertTrue(beats[beat] > 0.0F && beats[beat] < 1.0F,
                        theme + " has a beat outside its wind-up: " + beats[beat]);
                if (beat > 0) {
                    assertTrue(beats[beat] > beats[beat - 1], theme + " has its beats out of order");
                }
            }
            // A sketch's pencil is heard from its own plan, not from the wind-up - see SketchPlanTest.
            int expected = theme == RiftModulatorTheme.ENDER || theme == RiftModulatorTheme.MOSTLY_DANGEROUS ? 1 : 0;
            assertEquals(expected, beats.length, theme + " makes the wrong number of sounds winding up");
        }
        assertEquals(ThemeLook.STARE, ThemeLook.beats(RiftModulatorTheme.ENDER)[0],
                "the stare is heard at the moment the eyes start to open");
        assertEquals(ThemeLook.GATHER, ThemeLook.beats(RiftModulatorTheme.MOSTLY_DANGEROUS)[0],
                "the cloud is heard gathering from the start, which is when it starts to gather");
    }

    /** Handing out the beats must not hand out the table they are kept in. */
    @Test
    void theBeatsCannotBeChangedFromOutside() {
        ThemeLook.beats(RiftModulatorTheme.ENDER)[0] = 0.99F;
        assertEquals(ThemeLook.STARE, ThemeLook.beats(RiftModulatorTheme.ENDER)[0]);
    }

    /** The eyes are open before the pane goes, since from then on the standing pair takes over, open. */
    @Test
    void theEyesAreOpenBeforeThePaneGoes() {
        assertTrue(ThemeLook.STARE + ThemeLook.STARE_OPENING < 1.0F, "the eyes are still opening at the break");
    }

    /**
     * The swatches are greyed out for exactly the themes that ignore them.
     *
     * <p>Both mistakes are quiet. Grey them out for a theme that uses them and a pilot cannot colour
     * their rift; leave them live for one that does not and every click changes nothing they can see.
     */
    @Test
    void onlyTheThemesThatUseTheSwatchesOfferThem() {
        Set<RiftModulatorTheme> expected = EnumSet.copyOf(ORIGINALS);
        expected.add(RiftModulatorTheme.SKETCH);
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            assertEquals(expected.contains(theme), ThemeLook.takesSwatches(theme),
                    theme + (expected.contains(theme) ? " greys out swatches it uses" : " offers swatches it ignores"));
        }
    }

    /**
     * A sketch is given longer to let go than everything else, because its close is an animation rather
     * than a fade - scribbled out, screwed up and thrown away - and none of that reads in a quarter of a
     * second. So is the cloud: its collapse is quick, but the trails it leaves have to hang somewhere
     * afterwards. Nothing is waiting on either - the hull has left the aperture before it is told to close.
     */
    @Test
    void onlyTheThemesThatLeaveSomethingBehindTakeLongerToLetGo() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            int expected = switch (theme) {
                case SKETCH -> 40;
                case MOSTLY_DANGEROUS -> 45;
                default -> 25;
            };
            assertEquals(expected, ThemeLook.closeTicks(theme), theme + " lets go over the wrong stretch");
        }
        // The collapse itself is still over faster than any other theme's whole close.
        assertTrue(Nebula.COLLAPSE_TO * ThemeLook.closeTicks(RiftModulatorTheme.MOSTLY_DANGEROUS) < 25.0F,
                "the cloud's collapse is slower than an ordinary rift's close - it is meant to be violent");
    }

    /**
     * The cloud's lightning is drawn additively, so each of its three colours has to be bright enough to
     * add something - and they have to be three colours, or the white, violet and blue are one colour.
     */
    @Test
    void theCloudsLightningComesInThreeBrightColours() {
        Set<Integer> colours = new HashSet<>();
        for (int which = 0; which < 3; which++) {
            int colour = ThemeLook.arcColour(which);
            assertTrue(ThemeLook.luminance(colour) >= 0.45F,
                    Integer.toHexString(colour) + " is too dark to be lightning");
            colours.add(colour);
        }
        assertEquals(3, colours.size(), "two of the cloud's lightning colours are the same");
        assertTrue(ThemeLook.luminance(ThemeLook.NEBULA_BLACK) < 0.03F, "the cloud is not dark");
    }

    /** Ink has to be seen on the paper it is drawn on. */
    @Test
    void graphiteReadsOnPaper() {
        assertTrue(ThemeLook.luminance(ThemeLook.PAPER) - ThemeLook.luminance(ThemeLook.GRAPHITE) >= 0.6F,
                "the pencil is too pale for the paper");
    }
}
