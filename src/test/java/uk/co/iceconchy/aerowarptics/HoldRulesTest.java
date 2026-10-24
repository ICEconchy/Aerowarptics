package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.hold.HoldRules;
import uk.co.iceconchy.aerowarptics.hold.HoldRules.Residency;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decisions behind holding ships and ground, without a game.
 *
 * <p>Every one of these fails silently in play. A lease that never lapses is a ship loaded forever; one that
 * lapses early is a ship lost at the far end of a warp; a wake chunk that moves about re-forces a chunk every
 * second; a residency hold that follows an unmanned ship generates the country it drifts across.
 */
class HoldRulesTest {

    /**
     * A lease is counted in game time, which stands still while the server is down - so a restart neither
     * spends it nor refreshes it.
     *
     * <p>The owner of a hold renewed just before a shutdown has the whole lease to load and renew it again
     * afterwards; an owner that has gone for good lets it run out the same number of ticks after the restart.
     */
    @Test
    void aLeaseSurvivesARestartAndStillRunsOut() {
        long renewed = 10_000L;
        long until = HoldRules.leaseUntil(renewed, 1200);
        // The server stops ten ticks later and comes back; game time carries on from where it stopped.
        long afterRestart = renewed + 10L;
        assertFalse(HoldRules.lapsed(until, afterRestart), "a restart used up a lease nobody had let run");
        assertFalse(HoldRules.lapsed(until, until - 1L));
        assertTrue(HoldRules.lapsed(until, until), "a lease outlived its own end");
        assertTrue(HoldRules.lapsed(HoldRules.leaseUntil(renewed, 0), renewed + 1L),
                "a zero lease is still a lease of at least a tick, and lapses");
    }

    /** A lease is only rewritten once half of it is gone, so an owner asking every second does not dirty the save. */
    @Test
    void aLeaseIsRenewedOnlyOnceHalfOfItIsSpent() {
        long until = HoldRules.leaseUntil(0L, 1200);
        assertFalse(HoldRules.dueForRenewal(until, 20L, 1200));
        assertFalse(HoldRules.dueForRenewal(until, 600L, 1200));
        assertTrue(HoldRules.dueForRenewal(until, 601L, 1200));
    }

    /**
     * Residency follows a ship while someone is with it, holds it where it was left, lets it go if it wanders
     * off unmanned, and takes it again once it stops.
     */
    @Test
    void residencyHoldsWhereAShipIsLeftAndNoFurther() {
        assertEquals(Residency.RELEASE, HoldRules.residency(false, true, 0.0D, 256.0D, true, true),
                "residency switched off still holds");
        assertEquals(Residency.REANCHOR, HoldRules.residency(true, true, 5_000.0D, 256.0D, true, false),
                "a ship with crew aboard, or in a warp, is followed however far it goes");
        assertEquals(Residency.HOLD, HoldRules.residency(true, true, 100.0D, 256.0D, false, false));
        assertEquals(Residency.RELEASE, HoldRules.residency(true, true, 300.0D, 256.0D, false, false),
                "an unmanned ship drifting off is followed across the country");
        assertEquals(Residency.RELEASE, HoldRules.residency(true, false, 0.0D, 256.0D, false, false),
                "a ship let go for drifting is taken again while still under way");
        assertEquals(Residency.REANCHOR, HoldRules.residency(true, false, 0.0D, 256.0D, false, true),
                "a ship that has come to rest is not held again");
    }

    @Test
    void roamingIsMeasuredAcrossTheGroundOnly() {
        assertEquals(5.0D, HoldRules.roamed(0.0D, 0.0D, 3.0D, 4.0D), 1.0e-9D);
    }

    /**
     * One chunk keeps a level awake. It stays put while it is under a held ship, and while anything is in
     * flight; it settles under a held ship once nothing is; and it goes with the last held ship.
     *
     * <p>The first version followed a flying ship: a twenty-thousand-block warp's trace showed the chunk
     * released and re-forced every second of the flight. Never following at all would leave it loading the
     * ground a ship set off from for as long as the ship stays held somewhere else.
     */
    @Test
    void theWakeChunkIsStableAndGoesWithTheLastShip() {
        assertNull(HoldRules.wakeChunk(null, List.of(), false));
        assertNull(HoldRules.wakeChunk(7L, List.of(), false), "a level with no held ships is kept awake anyway");
        assertNull(HoldRules.wakeChunk(7L, List.of(), true), "a level with no held ships is kept awake anyway");
        assertEquals(3L, HoldRules.wakeChunk(null, List.of(9L, 3L, 5L), false), "the first is chosen from under a ship");
        assertEquals(9L, HoldRules.wakeChunk(9L, List.of(3L, 9L, 5L), false), "the wake chunk moved while still under a ship");
        assertEquals(4L, HoldRules.wakeChunk(4L, List.of(9L, 3L, 5L), true), "the wake chunk chased a ship in flight");
        assertEquals(3L, HoldRules.wakeChunk(4L, List.of(9L, 3L, 5L), false),
                "the wake chunk stayed behind, loading ground no ship is at, after the flying was over");
    }

    /**
     * Only the inner part of a region ticket is enough for Sable to keep a ship.
     *
     * <p>A ticket of radius {@code r} is block-ticking to {@code r - 1} chunks out and merely loaded at
     * {@code r}. Every piece of ground a ship is held on is sized from this.
     */
    @Test
    void aTicketsRimIsLoadedButNotEnoughToKeepAShip() {
        assertEquals(31, HoldRules.ticketLevel(2, 0));
        assertTrue(HoldRules.blockTicking(2, 1));
        assertFalse(HoldRules.blockTicking(2, 2), "a ticket's outer ring counted as block-ticking");
        assertFalse(HoldRules.blockTicking(0, 0), "a zero-radius ticket counted as block-ticking");
    }

    /** The ground reached for a ship is its box and a margin, on both sides of zero. */
    @Test
    void chunksUnderABoxIncludeTheMargin() {
        assertArrayEquals(new int[]{-1, -1, 1, 1}, HoldRules.chunksUnder(0.0D, 0.0D, 15.0D, 15.0D, 2.0D));
        assertArrayEquals(new int[]{-2, 0, -1, 0}, HoldRules.chunksUnder(-20.0D, 4.0D, -1.0D, 10.0D, 0.0D));
    }
}
