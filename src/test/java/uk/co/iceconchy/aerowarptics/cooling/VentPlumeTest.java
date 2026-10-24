package uk.co.iceconchy.aerowarptics.cooling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The steam a vent forces out: a blast, then a falling away, then a breath - and no jump between them.
 *
 * <p>A curve that jumps where two of its parts meet is a plume that visibly cuts out, and nothing throws
 * when it does.
 */
class VentPlumeTest {

    private static final int LONG_AFTER = 20 * 60 * 5;

    @Test
    void nothingLeavesAVentBeforeItsDriveBeginsToCool() {
        assertEquals(0.0D, VentPlume.strength(-1));
        assertEquals(0.0D, VentPlume.jets(-1, 1.0D));
        assertFalse(VentPlume.isJet(-1));
    }

    @Test
    void theWarpEndsAndTheVentBlowsAtOnce() {
        assertEquals(1.0D, VentPlume.strength(0), "steam is forced out on the tick the drive starts to cool");
        assertEquals(1.0D, VentPlume.strength(VentPlume.BLAST_TICKS - 1));
        assertTrue(VentPlume.isJet(0));
        assertEquals(VentPlume.BLAST_SPEED, VentPlume.speed(0), 1.0e-9D);
    }

    @Test
    void theBlastFallsAwayWithoutEverRising() {
        double last = VentPlume.strength(0);
        for (int tick = 1; tick <= LONG_AFTER; tick++) {
            double now = VentPlume.strength(tick);
            assertTrue(now <= last + 1.0e-12D, "the plume strengthened at tick " + tick);
            last = now;
        }
    }

    @Test
    void thePartsOfThePlumeJoin() {
        // The steepest the easing ever is, is its first tick; nothing else may step further than that.
        double steepest = 2.0D * (1.0D - VentPlume.BREATH) / VentPlume.EASE_TICKS;
        for (int tick = 1; tick <= LONG_AFTER; tick++) {
            double step = Math.abs(VentPlume.strength(tick) - VentPlume.strength(tick - 1));
            assertTrue(step <= steepest + 1.0e-12D, "the plume jumps by " + step + " at tick " + tick);
        }
    }

    @Test
    void aVentBreathesForAsLongAsItsDriveIsCooling() {
        assertEquals(VentPlume.BREATH, VentPlume.strength(VentPlume.BLAST_TICKS + VentPlume.EASE_TICKS));
        assertEquals(VentPlume.BREATH, VentPlume.strength(LONG_AFTER));
        assertTrue(VentPlume.jets(LONG_AFTER, 1.0D) > 0.0D,
                "a vent that stopped showing anything would look like one whose drive had finished cooling");
        assertEquals(VentPlume.BREATH_SPEED, VentPlume.speed(LONG_AFTER), 1.0e-9D);
        assertFalse(VentPlume.isJet(LONG_AFTER));
    }

    @Test
    void theSteamSlowsAsThePressureFalls() {
        double last = VentPlume.speed(0);
        for (int tick = 1; tick <= LONG_AFTER; tick++) {
            double now = VentPlume.speed(tick);
            assertTrue(now <= last + 1.0e-12D && now >= VentPlume.BREATH_SPEED - 1.0e-12D, "at tick " + tick);
            last = now;
        }
    }

    @Test
    void theParticleSettingScalesThePlumeAndCanSwitchItOff() {
        assertEquals(VentPlume.BLAST_JETS, VentPlume.jets(0, 1.0D), 1.0e-9D);
        assertEquals(VentPlume.BLAST_JETS / 2.0D, VentPlume.jets(0, 0.5D), 1.0e-9D);
        assertEquals(0.0D, VentPlume.jets(0, 0.0D));
        assertEquals(0.0D, VentPlume.jets(0, -1.0D), "a negative setting is no steam, not steam drawn inwards");
    }

    /**
     * A vent a player walks up to half-way through a cooldown joins the plume where it has settled. Joining
     * at the start would be a blast for a warp that ended minutes ago.
     */
    @Test
    void aVentSeenLateJoinsPastTheBlast() {
        assertEquals(VentPlume.BREATH, VentPlume.strength(VentPlume.settled()));
        assertFalse(VentPlume.isJet(VentPlume.settled()));
    }
}
