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

    private static final Set<RiftModulatorTheme> ORIGINALS = EnumSet.of(RiftModulatorTheme.STANDARD,
            RiftModulatorTheme.EMBER, RiftModulatorTheme.STARLIGHT, RiftModulatorTheme.ARCANE,
            RiftModulatorTheme.CLOCKWORK);

    private static Set<RiftModulatorTheme> borrowed() {
        Set<RiftModulatorTheme> out = EnumSet.allOf(RiftModulatorTheme.class);
        out.removeAll(ORIGINALS);
        return out;
    }

    @Test
    void theOriginalFiveStillWearThePilotsColours() {
        for (RiftModulatorTheme theme : ORIGINALS) {
            assertNull(ThemeLook.palette(theme, 12_345), theme + " took the swatch away from its pilot");
        }
    }

    @Test
    void everyBorrowedThemeBringsItsOwnColours() {
        assertEquals(7, borrowed().size(), "the borrowed set changed size - check this test still means anything");
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
            assertTrue(ThemeLook.crackles(theme), theme + " lost its lightning");
        }
        assertFalse(ThemeLook.crackles(RiftModulatorTheme.STARBLOCKS), "hyperspace does not crackle");
        assertFalse(ThemeLook.crackles(RiftModulatorTheme.BOLDLY_GONE), "a warp bubble does not crackle");
        assertTrue(ThemeLook.crackles(RiftModulatorTheme.EVENTFUL_HORIZON), "a gravity drive is violent");
        assertTrue(ThemeLook.crackles(RiftModulatorTheme.VWORP), "the vortex is full of lightning");
    }
}
