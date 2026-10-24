package uk.co.iceconchy.aerowarptics.rail;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where corridors lie, and how long they must be.
 *
 * <p>The failure this guards is the one Create cannot recover from: a carriage that reaches the far end of a corridor
 * before it has left the near one is refused the far portal for being in two dimensions already, and is stuck in the
 * void for good.
 */
class CorridorTest {

    @Test
    void everySlotHasItsOwnLine() {
        Set<Integer> zs = new HashSet<>();
        for (int slot = 0; slot < 50; slot++) {
            assertTrue(zs.add(CorridorSlots.z(slot)));
            assertEquals(slot, CorridorSlots.slotAt(CorridorSlots.z(slot) + 0.5D));
            assertEquals(slot, CorridorSlots.slotAt(CorridorSlots.z(slot) + 0.5D + CorridorSlots.REACH));
            assertEquals(-1, CorridorSlots.slotAt(CorridorSlots.z(slot) + 0.5D + CorridorSlots.REACH + 1.0D));
        }
        assertEquals(-1, CorridorSlots.slotAt(-100.0D));
    }

    @Test
    void theLastSlotIsInsideTheWorldBorder() {
        assertTrue(CorridorSlots.z(CorridorSlots.MAX_SLOTS - 1) + CorridorSlots.SPACING < 29_000_000);
        assertTrue(CorridorSlots.SPACING > 160, "neighbouring corridors would be in sight of each other");
    }

    @Test
    void aFreedSlotIsUsedAgain() {
        assertEquals(0, CorridorSlots.allocate(Set.of()));
        assertEquals(2, CorridorSlots.allocate(Set.of(0, 1, 3)));
        assertEquals(4, CorridorSlots.allocate(Set.of(0, 1, 2, 3)));
    }

    @Test
    void theEndsOfACorridorAreInOrder() {
        int length = CorridorFit.requiredLength(0, 128);
        assertTrue(CorridorSlots.westMouth() < CorridorSlots.westTrack());
        assertTrue(CorridorSlots.westTrack() + 1 < CorridorSlots.eastTrack(length));
        assertEquals(CorridorSlots.eastTrack(length) + 1, CorridorSlots.eastMouth(length));
    }

    @Test
    void aCorridorIsLongerThanCreatesLongestTrain() {
        assertEquals(160, CorridorFit.requiredLength(0, 128));
        assertEquals(300, CorridorFit.requiredLength(300, 128), "a longer corridor asked for is a longer corridor");
        assertEquals(160, CorridorFit.requiredLength(40, 128), "a shorter one is not");
        assertTrue(CorridorFit.requiredLength(0, 5) >= CorridorFit.MIN_LENGTH);
        assertEquals(CorridorFit.MAX_LENGTH, CorridorFit.requiredLength(5_000, 128));
    }

    /** Every carriage Create can build fits a corridor laid for it, at Create's top powered speed. */
    @Test
    void everyCarriageCreateCanBuildFits() {
        for (int longest = 5; longest <= 512; longest++) {
            int length = CorridorFit.requiredLength(0, longest);
            assertTrue(CorridorFit.fits(longest, length, 40.0D / 20.0D), "a " + longest + "-block carriage in a "
                    + length + "-block corridor");
        }
    }

    @Test
    void aCarriageLongerThanTheCorridorDoesNot() {
        assertFalse(CorridorFit.fits(158, 160, 2.0D));
        assertFalse(CorridorFit.fits(150, 160, 2.0D), "four ticks at top speed are part of the reach");
        assertTrue(CorridorFit.fits(140, 160, 2.0D));
    }
}
