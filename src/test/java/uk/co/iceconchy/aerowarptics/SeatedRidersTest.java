package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.SeatedRiders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether a mob loaded on a ship's seat has been carried somewhere it never went.
 *
 * <p>Both halves matter: a rider on its seat must never be moved, and the one Sable has carried out of the plot a
 * second time must always be. The gap between the two is millions of blocks, which is what makes one number enough.
 */
class SeatedRidersTest {

    /** Anything that sits on a seat, a saddle or a cart is within a few blocks of it. */
    @Test
    void aRiderOnItsSeatIsLeftAlone() {
        for (double blocks : new double[]{0.0D, 0.5D, 1.5D, 3.0D, 16.0D}) {
            assertFalse(SeatedRiders.misplaced(blocks), blocks + " blocks from a seat is sitting on it");
        }
    }

    /** Where Sable puts a rider it carries out twice is nowhere near the ship. */
    @Test
    void aRiderCarriedOutOfThePlotTwiceIsPutBack() {
        for (double blocks : new double[]{16.01D, 200.0D, 20_000_000.0D}) {
            assertTrue(SeatedRiders.misplaced(blocks), blocks + " blocks from a seat is not on it");
        }
    }

    /**
     * The positions seen in game: a pig on a seat at (2.5, -48.6, 13.5) came back from a reload at
     * (-20481029.5, -226.6, -20481008.5), below the world and twenty million blocks off.
     */
    @Test
    void thePositionSeenInGameCountsAsMisplaced() {
        double dx = -20_481_029.5D - 2.5D;
        double dy = -226.6D - -48.6D;
        double dz = -20_481_008.5D - 13.5D;
        assertTrue(SeatedRiders.misplaced(Math.sqrt(dx * dx + dy * dy + dz * dz)));
    }
}
