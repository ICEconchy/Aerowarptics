package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules an anchor's or a probe's arrival height is held to on its way to the arrival search.
 *
 * <p>None of these would throw if they were wrong. A negative height starts the search inside the
 * block it is meant to clear, an unset one read as zero puts the keel on the ground, and a ceiling
 * applied only when the slider moves lets a height set before an admin lowered it straight through.
 */
class ArrivalHeightTest {

    /** Measured the way the old buffer was: from the top of the target block to the underside. */
    @Test
    void theLowestUndersideIsMeasuredFromTheTopOfTheBlock() {
        assertEquals(71, ArrivalHeight.lowestUnderside(64, 6));
        assertEquals(65, ArrivalHeight.lowestUnderside(64, 0), "a height of zero rests on the block, not in it");
        assertEquals(65, ArrivalHeight.lowestUnderside(64, -20), "a negative height is never below the block");
    }

    /**
     * The one number that must not change: an untouched destination arrives exactly where every
     * destination did before heights were adjustable - {@code anchor + 1 + arrivalGroundBuffer}.
     */
    @Test
    void anUnsetHeightArrivesWhereTheOldBufferDid() {
        int height = ArrivalHeight.resolve(ArrivalHeight.UNSET);
        assertEquals(ArrivalHeight.serverDefault(), height);
        assertEquals(64 + 1 + 6, ArrivalHeight.lowestUnderside(64, height),
                "with the shipped config, an unset height is the shipped six-block buffer");
    }

    @Test
    void aHeightIsClampedIntoRange() {
        assertEquals(0, ArrivalHeight.clamp(-1, 128));
        assertEquals(40, ArrivalHeight.clamp(40, 128));
        assertEquals(128, ArrivalHeight.clamp(5_000, 128));
        assertEquals(0, ArrivalHeight.clamp(40, -3), "a nonsense ceiling is no ceiling above zero");
    }

    /**
     * Resolving re-applies the ceiling, so an anchor set to two hundred on a server that has since
     * lowered {@code maxArrivalHeight} is planned at the new ceiling rather than at two hundred.
     */
    @Test
    void resolvingReappliesTheCeiling() {
        assertEquals(ArrivalHeight.maximum(), ArrivalHeight.resolve(ArrivalHeight.maximum() + 500));
        assertEquals(12, ArrivalHeight.resolve(12));
    }

    /** The sentinel can never be mistaken for a height anybody could set. */
    @Test
    void theUnsetSentinelIsNotAHeight() {
        assertTrue(ArrivalHeight.UNSET < 0);
        assertEquals(0, ArrivalHeight.clamp(ArrivalHeight.UNSET, ArrivalHeight.maximum()));
        assertTrue(ArrivalHeight.serverDefault() <= ArrivalHeight.maximum(),
                "the default a new anchor starts at is above the height it is allowed");
    }
}
