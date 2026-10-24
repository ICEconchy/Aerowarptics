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

    /**
     * Holding altitude is the height that puts the underside back where it is now.
     *
     * <p>Checked through {@link ArrivalHeight#lowestUnderside}, the function the arrival search itself
     * places the hull with, so a held altitude that was off by the block's own thickness would show up
     * here as a ship arriving one block lower than it left.
     */
    @Test
    void holdingAltitudeArrivesAtTheUndersideItLeftWith() {
        assertEquals(135, ArrivalHeight.lowestUnderside(64, ArrivalHeight.holding(135.0D, 64)));
        assertEquals(135, ArrivalHeight.lowestUnderside(64, ArrivalHeight.holding(134.6D, 64)),
                "a hull a fraction of a block off the grid is rounded, not dropped a block");
        assertEquals(-20, ArrivalHeight.lowestUnderside(-40, ArrivalHeight.holding(-20.0D, -40)),
                "below sea level is an altitude like any other");
    }

    /**
     * A ship lower than its destination comes in on top of it, never inside the ground under it.
     *
     * <p>The search only ever climbs from where it starts, so a start below the target would begin
     * inside the hill the anchor stands on - and on a tall enough hill, climb out of its vertical radius
     * before it cleared the top.
     */
    @Test
    void holdingAltitudeBelowTheTargetRestsOnIt() {
        assertEquals(0, ArrivalHeight.holding(70.0D, 150));
        assertEquals(0, ArrivalHeight.holding(65.0D, 64), "exactly on top of the block is a height of zero");
    }

    /**
     * Holding altitude is not held to the slider's ceiling.
     *
     * <p>The ceiling is on what a player dials in. A ship already cruising well above it has dialled
     * nothing, and lowering it to the ceiling would make the toggle silently do nothing up there.
     */
    @Test
    void holdingAltitudeIsNotCappedByTheSliderCeiling() {
        int far = ArrivalHeight.maximum() + 300;
        assertEquals(far, ArrivalHeight.holding(64 + 1 + far, 64));
    }

    @Test
    void aHullWithNoMeasurableUndersideFallsBackToTheDefault() {
        assertEquals(ArrivalHeight.serverDefault(), ArrivalHeight.holding(Double.NaN, 64));
    }

    /**
     * The toggle decides between the two, and only within one dimension.
     *
     * <p>Across dimensions a Y level means nothing comparable - a ship cruising at two hundred in the
     * Overworld would hold that height straight onto the Nether's roof - so the destination's own
     * height is used whatever the course asked for.
     */
    @Test
    void holdingAltitudeAppliesOnlyWithinOneLevel() {
        assertEquals(70, ArrivalHeight.planned(12, true, true, 135.0D, 64), "held");
        assertEquals(12, ArrivalHeight.planned(12, false, true, 135.0D, 64), "not asked to hold");
        assertEquals(12, ArrivalHeight.planned(12, true, false, 135.0D, 64), "another dimension");
        assertEquals(ArrivalHeight.serverDefault(),
                ArrivalHeight.planned(ArrivalHeight.UNSET, true, false, 135.0D, 64),
                "falling back still resolves an unset height");
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
