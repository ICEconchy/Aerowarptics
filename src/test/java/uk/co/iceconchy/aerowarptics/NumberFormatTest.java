package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The number formatters every screen reads its values through.
 *
 * <p>Written after a real defect of exactly the kind this suite exists to catch. {@code distance} used
 * to abbreviate large values with a {@code k} and leave the unit to the caller - and five of the six
 * call sites then appended {@code " m"}, so a Creative drive's reach rendered as
 * {@code "100,000.0k m"}. That is not a distance in any unit, it compiled, and the only way to find it
 * was to open the console and read it. The sixth appended nothing at all and showed a bare number.
 *
 * <p>So the property worth holding is not any particular string: it is that a formatter which can
 * abbreviate <em>owns the unit it abbreviated into</em>, and that no caller has to know when it did.
 */
class NumberFormatTest {

    /** Every distance carries exactly one unit, and the unit matches the magnitude. */
    @Test
    void everyDistanceCarriesItsOwnUnit() {
        for (double blocks : new double[]{0, 1, 999, 4_000, 9_999, 10_000, 24_000, 120_000,
                2_000_000, 100_000_000}) {
            String shown = AWLang.distance(blocks);
            boolean metres = shown.endsWith(" m");
            boolean kilometres = shown.endsWith(" km");
            assertTrue(metres || kilometres, blocks + " formatted without a unit: " + shown);
            // The exact failure that shipped: an abbreviating "k" left stranded against a metre sign.
            assertFalse(shown.contains("k m"), blocks + " formatted as kilo-metres: " + shown);
        }
    }

    @Test
    void distanceSwitchesToKilometresWhenMetresStopBeingReadable() {
        assertEquals("0 m", AWLang.distance(0));
        assertEquals("999 m", AWLang.distance(999));
        assertEquals("9,999 m", AWLang.distance(9_999));
        assertEquals("10.0 km", AWLang.distance(10_000));
        assertEquals("24.0 km", AWLang.distance(24_000));
        // Past a million blocks a tenth of a kilometre is noise, so the decimal goes.
        assertEquals("2,000 km", AWLang.distance(2_000_000));
        assertEquals("100,000 km", AWLang.distance(100_000_000));
    }

    /**
     * A count is not a length.
     *
     * <p>Hull mass went through the distance formatter purely because it also wanted thousands
     * separators. It read correctly only while every hull was small enough not to be abbreviated; the
     * first one over ten thousand blocks would have claimed to be measured in kilometres.
     */
    @Test
    void aCountNeverClaimsAUnit() {
        for (double amount : new double[]{0, 37, 1_234, 9_999, 10_000, 250_000}) {
            String shown = AWLang.count(amount);
            assertFalse(shown.contains("m"), amount + " picked up a unit: " + shown);
        }
        assertEquals("37", AWLang.count(37));
        assertEquals("1,234", AWLang.count(1_234));
        assertEquals("10.0k", AWLang.count(10_000));
    }

    /** A tank reading is separated on both sides and carries one unit at the end. */
    @Test
    void aTankReadingIsFormattedOnBothSides() {
        assertEquals("500 / 500 mB", AWLang.essence(500, 500));
        assertEquals("10.1k / 10.1k mB", AWLang.essence(10_105, 10_105));
        assertEquals("250 mB", AWLang.essence(250));
        // One unit, at the end, never in the middle.
        assertEquals(1, AWLang.essence(10_105, 10_105).split("mB", -1).length - 1);
    }

    @Test
    void percentIsClampedToItsRange() {
        assertEquals("0%", AWLang.percent(-1.0D));
        assertEquals("0%", AWLang.percent(0.0D));
        assertEquals("62%", AWLang.percent(0.615D));
        assertEquals("100%", AWLang.percent(1.0D));
        assertEquals("100%", AWLang.percent(4.0D));
    }
}
