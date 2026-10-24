package uk.co.iceconchy.aerowarptics.siphon;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ship catches one draught per jump, however many siphons it carries.
 *
 * <p>The failure this guards against does not throw: every siphon rolling for itself looks like a ship
 * with more vessels simply doing better, which is the obvious thing for a player to try and the one thing
 * that would make essence free once it became a price.
 */
class SiphonPoolTest {

    private static final int ROOM = 4_000;

    @Test
    void theYieldOfAJumpIsTheSameWhateverTheNumberOfSiphons() {
        for (double distance : new double[]{128.0D, 2_000.0D, 12_000.0D}) {
            for (double spread : new double[]{0.0D, 0.37D, 0.99D}) {
                int draught = SiphonPool.draught(distance, spread, spread);
                for (int siphons = 1; siphons <= 10; siphons++) {
                    int[] rooms = new int[siphons];
                    Arrays.fill(rooms, ROOM);
                    assertEquals(draught, Arrays.stream(SiphonPool.pour(draught, rooms)).sum(),
                            siphons + " siphons caught a different amount from one");
                }
            }
        }
    }

    @Test
    void moreSiphonsAddRoomNotYield() {
        // A full first vessel spills into the next rather than losing the draught - and the ship still
        // catches exactly the one draught between them.
        int[] took = SiphonPool.pour(300, new int[]{120, 4_000, 4_000});
        assertArrayEquals(new int[]{120, 180, 0}, took);
        assertEquals(300, Arrays.stream(took).sum());
    }

    @Test
    void aPourNeverMintsEssenceOrOverfillsAVessel() {
        int[] rooms = {10, 0, 25};
        int[] took = SiphonPool.pour(1_000, rooms);
        for (int i = 0; i < rooms.length; i++) {
            assertTrue(took[i] >= 0 && took[i] <= rooms[i], "vessel " + i + " took " + took[i] + " with " + rooms[i]);
        }
        assertEquals(35, Arrays.stream(took).sum(), "more was poured than there was room for");
        assertEquals(0, Arrays.stream(SiphonPool.pour(-5, rooms)).sum());
    }

    /** Ten siphons on a deck in the rain fill no faster than one. */
    @Test
    void rainIsABudgetSharedNotStacked() {
        RainLedger ledger = new RainLedger();
        UUIDKey deck = new UUIDKey();
        int oneSiphon = 0;
        int tenSiphons = 0;
        RainLedger lone = new RainLedger();
        for (long second = 0; second < 60; second++) {
            oneSiphon += lone.take(deck, second, ROOM, 3);
            for (int siphon = 0; siphon < 10; siphon++) {
                tenSiphons += ledger.take(deck, second, ROOM, 3);
            }
        }
        assertEquals(oneSiphon, tenSiphons, "a ring of siphons out-caught one");
        assertEquals(180, oneSiphon);
        // A different chunk, or a different ship, has a budget of its own.
        assertEquals(3, ledger.take("another chunk", 0L, ROOM, 3));
        // And forgetting old seconds keeps the ledger small without losing the current one.
        ledger.forgetBefore(59L);
        assertEquals(0, ledger.take(deck, 59L, ROOM, 3), "forgetting the past refunded the present");
    }

    private static final class UUIDKey {
    }

    @Test
    void aLongerJumpWidensTheOddsWithoutGuaranteeingAnything() {
        int shortFloor = SiphonPool.draught(128.0D, 0.0D, 0.0D);
        int longFloor = SiphonPool.draught(40_000.0D, 0.0D, 0.0D);
        assertEquals(shortFloor, longFloor, "a long jump's worst roll should be no better than a short one's");
        assertTrue(SiphonPool.draught(40_000.0D, 0.99D, 0.99D) > SiphonPool.draught(128.0D, 0.99D, 0.99D));
        assertEquals(SiphonPool.BASE_YIELD, shortFloor);
        int ceiling = SiphonPool.BASE_YIELD + SiphonPool.BASE_SPREAD - 1 + SiphonPool.REACH_BONUS;
        assertTrue(SiphonPool.draught(1.0e9D, 1.0D, 1.0D) <= ceiling);
    }
}
