package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much ground a warp claims, and whether a landing will keep.
 *
 * <p>Two silent failures live here. The arrival ticket sized from the hull's span alone leaves the
 * leading edge of a long run-out in an unheld chunk, briefly unloaded at the exact moment it matters;
 * and a warp to an anchor nobody is near lands the ship, holds the ground for the flight, and then
 * lets go - and the world unloads out from under a hull that had arrived perfectly well.
 */
class ArrivalTicketTest {

    /** The whole arrival footprint, not the bare hull span. */
    @Test
    void theArrivalSpanCountsTheRunOutAndTheAperture() {
        // hull 40, run-out 120, aperture margin 6 -> 40 + 120 + 12.
        assertEquals(172.0D, ArrivalTicket.arrivalSpan(40.0D, 120.0D, 6.0D), 1.0e-9D);
        // Negatives cannot shrink it below the hull.
        assertEquals(40.0D, ArrivalTicket.arrivalSpan(40.0D, -10.0D, -1.0D), 1.0e-9D);
    }

    /**
     * The arrival radius grows with the footprint and is capped.
     *
     * <p>A skiff claims the floor of two chunks either side; a dreadnought's whole footprint is
     * covered up to the cap, past which force-loading more is a server cost not worth paying.
     */
    @Test
    void theArrivalRadiusFollowsTheFootprintAndIsCapped() {
        assertEquals(2, ArrivalTicket.radiusFor(0.0D), "a tiny hull still claims a floor of 2");
        assertEquals(3, ArrivalTicket.radiusFor(8.0D), "half a chunk of footprint still rounds up to a chunk");
        assertTrue(ArrivalTicket.radiusFor(200.0D) > ArrivalTicket.radiusFor(40.0D),
                "a bigger footprint claims more");
        assertEquals(8, ArrivalTicket.radiusFor(10_000.0D), "capped so a huge hull cannot ask for the world");
    }

    /** The corridor radius has to reach half the run and is capped higher, being a long thin thing. */
    @Test
    void theCorridorRadiusReachesHalfTheRunAndIsCapped() {
        assertEquals(2, ArrivalTicket.radiusForReach(0.0D));
        assertTrue(ArrivalTicket.radiusForReach(400.0D) > ArrivalTicket.radiusForReach(80.0D));
        assertEquals(12, ArrivalTicket.radiusForReach(100_000.0D), "even a very long corridor is capped");
    }

    /**
     * A landing keeps only when a player can see it or a loader holds it.
     *
     * <p>The pure decision behind the commit-time refusal. An anchor alone in unloaded wilderness is
     * neither, which is exactly the warp that loses a ship once the flight's own ticket lapses.
     */
    @Test
    void aLandingIsDurableOnlyWithAPlayerOrALoader() {
        assertTrue(ArrivalTicket.durableLanding(true, false), "a chunk loader keeps it");
        assertTrue(ArrivalTicket.durableLanding(false, true), "a nearby player keeps it");
        assertTrue(ArrivalTicket.durableLanding(true, true));
        assertFalse(ArrivalTicket.durableLanding(false, false),
                "unattended wilderness with no loader is not somewhere to leave a ship");
    }
}
