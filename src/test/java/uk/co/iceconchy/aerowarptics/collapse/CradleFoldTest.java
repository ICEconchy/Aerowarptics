package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fold at the Collapse Cradle, as rules: what refuses one, in the order a player fixes things in, and how many
 * crafts a run makes - never more than the arms, the tray, the tank or a stack of sixteen allow.
 */
class CradleFoldTest {

    /** A fold that would go: sixteen crystals, room for them, essence for them, turning fast enough. */
    private static CradleFold.Preflight ready() {
        return new CradleFold.Preflight(false, false, true, 16, 64, 1_600, 100, 64.0D, 32.0D);
    }

    private static CradleFold.Preflight with(boolean busy, boolean aboard, boolean holding, int crafts, int room,
                                             int held, int per, double speed) {
        return new CradleFold.Preflight(busy, aboard, holding, crafts, room, held, per, speed, 32.0D);
    }

    @Test
    void aReadyFoldGoes() {
        assertEquals(CollapseFailure.NONE, CradleFold.preflight(ready()));
        assertEquals(16, CradleFold.count(ready()));
    }

    @Test
    void eachRefusalIsItsOwn() {
        assertEquals(CollapseFailure.BUSY, CradleFold.preflight(with(true, false, true, 16, 64, 1_600, 100, 64.0D)));
        assertEquals(CollapseFailure.ABOARD_SHIP,
                CradleFold.preflight(with(false, true, true, 16, 64, 1_600, 100, 64.0D)));
        assertEquals(CollapseFailure.NO_CORE, CradleFold.preflight(with(false, false, false, 0, 64, 1_600, 100, 64.0D)));
        assertEquals(CollapseFailure.NO_FOLD, CradleFold.preflight(with(false, false, true, 0, 64, 1_600, 100, 64.0D)));
        assertEquals(CollapseFailure.NO_ROOM, CradleFold.preflight(with(false, false, true, 16, 0, 1_600, 100, 64.0D)));
        assertEquals(CollapseFailure.NOT_ENOUGH_ESSENCE,
                CradleFold.preflight(with(false, false, true, 16, 64, 99, 100, 64.0D)));
        assertEquals(CollapseFailure.NOT_TURNING,
                CradleFold.preflight(with(false, false, true, 16, 64, 1_600, 100, 16.0D)));
    }

    /** With everything wrong at once, the panel names the first thing to fix, not the last. */
    @Test
    void theFirstThingToFixIsNamedFirst() {
        assertEquals(CollapseFailure.BUSY, CradleFold.preflight(with(true, true, false, 0, 0, 0, 100, 0.0D)));
        assertEquals(CollapseFailure.ABOARD_SHIP, CradleFold.preflight(with(false, true, false, 0, 0, 0, 100, 0.0D)));
        assertEquals(CollapseFailure.NO_CORE, CradleFold.preflight(with(false, false, false, 0, 0, 0, 100, 0.0D)));
        assertEquals(CollapseFailure.NO_FOLD, CradleFold.preflight(with(false, false, true, 0, 0, 0, 100, 0.0D)));
        assertEquals(CollapseFailure.NO_ROOM, CradleFold.preflight(with(false, false, true, 3, 0, 0, 100, 0.0D)));
        assertEquals(CollapseFailure.NOT_ENOUGH_ESSENCE,
                CradleFold.preflight(with(false, false, true, 3, 5, 0, 100, 0.0D)));
    }

    @Test
    void eitherWayRoundCounts() {
        assertEquals(CollapseFailure.NONE, CradleFold.preflight(with(false, false, true, 16, 64, 1_600, 100, -64.0D)));
    }

    @Test
    void aFoldBurningNothingNeverWantsForEssence() {
        CradleFold.Preflight free = with(false, false, true, 5, 64, 0, 0, 64.0D);
        assertEquals(CollapseFailure.NONE, CradleFold.preflight(free));
        assertEquals(5, CradleFold.count(free));
    }

    /** A run makes as many as the least of what it has, has room for and can pay for - and at most sixteen. */
    @Test
    void aRunIsAsLongAsItsShortestSupply() {
        assertEquals(CradleFold.MOST, CradleFold.count(with(false, false, true, 64, 64, 100_000, 100, 64.0D)));
        assertEquals(7, CradleFold.count(with(false, false, true, 7, 64, 100_000, 100, 64.0D)));
        assertEquals(3, CradleFold.count(with(false, false, true, 16, 3, 100_000, 100, 64.0D)));
        assertEquals(4, CradleFold.count(with(false, false, true, 16, 64, 499, 100, 64.0D)));
        assertEquals(0, CradleFold.count(with(false, false, true, 16, 64, 0, 100, 64.0D)));
    }

    @Test
    void aFoldIsLongEnoughToWatch() {
        assertEquals(CradleFold.SHORTEST, CradleFold.ticks(0));
        assertEquals(CradleFold.SHORTEST, CradleFold.ticks(20));
        assertEquals(240, CradleFold.ticks(240));
    }

    @Test
    void theTrayTakesOnlyWhatFits() {
        assertEquals(64, CradleFold.room(0, true, 1, 64));
        assertEquals(40, CradleFold.room(24, true, 1, 64));
        assertEquals(0, CradleFold.room(24, false, 1, 64), "a different thing in the tray leaves no room");
        assertEquals(0, CradleFold.room(0, true, 2, 1), "a craft that makes more than the tray holds");
        assertEquals(20, CradleFold.room(24, true, 2, 64));
        assertEquals(1, CradleFold.room(0, true, 1, 1), "a compass, which does not stack");
        assertEquals(0, CradleFold.room(1, true, 1, 1));
        assertEquals(CradleFold.MOST, CradleFold.room(5, false, 0, 64), "a craft that makes nothing needs no room");
    }
}
