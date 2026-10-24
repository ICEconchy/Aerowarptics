package uk.co.iceconchy.aerowarptics.ward;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who a Rift Ward lets through, what its field costs, and the limits it keeps - each as a table. */
class WardRulesTest {

    // ------------------------------------------------------------------ who passes

    /** The whole rule, as the table a player reads off the panel: the mode down the side, who is asking across. */
    @Test
    void theModeIsTheWholeOfTheRule() {
        // mode, in a listed network -> passes, for somebody who is not the owner.
        assertFalse(WardAccess.passes(WardMode.BLOCK_ALL, false, false, false), "Block all let a stranger through");
        assertFalse(WardAccess.passes(WardMode.BLOCK_ALL, false, false, true), "Block all let a listed network through");
        assertFalse(WardAccess.passes(WardMode.ALLOW_NETWORKS, false, false, false), "Allow let a stranger through");
        assertTrue(WardAccess.passes(WardMode.ALLOW_NETWORKS, false, false, true), "Allow turned the network it allows");
        assertTrue(WardAccess.passes(WardMode.BLOCK_NETWORKS, false, false, false), "Block networks turned a stranger");
        assertFalse(WardAccess.passes(WardMode.BLOCK_NETWORKS, false, false, true), "Block networks let its network in");
    }

    /** The owner comes home whatever the ward is set to - and, having asked to be turned too, is turned like anyone. */
    @Test
    void theOwnerPassesEveryModeUntilTheyAskToBeTurnedToo() {
        for (WardMode mode : WardMode.values()) {
            for (boolean listed : new boolean[]{false, true}) {
                assertTrue(WardAccess.passes(mode, true, false, listed), "its own ward turned its owner in " + mode);
                assertEquals(WardAccess.passes(mode, false, false, listed), WardAccess.passes(mode, true, true, listed),
                        "an owner who asked to be turned too is not treated as anyone else is, in " + mode);
            }
        }
        assertFalse(WardAccess.passes(WardMode.BLOCK_ALL, true, true, false),
                "Turn me too with Block all is how one player tries their own ward, and it let them through");
    }

    /** A setting that names networks means nothing for a ward set to turn everyone. */
    @Test
    void onlyTheTwoNetworkModesUseTheNetworks() {
        assertFalse(WardMode.BLOCK_ALL.usesNetworks());
        assertTrue(WardMode.ALLOW_NETWORKS.usesNetworks());
        assertTrue(WardMode.BLOCK_NETWORKS.usesNetworks());
    }

    /** By ordinal over the wire and by name on disk: the order and the names are both kept promises. */
    @Test
    void theModesKeepTheirOrderAndTheirNames() {
        assertEquals(java.util.List.of("BLOCK_ALL", "ALLOW_NETWORKS", "BLOCK_NETWORKS"),
                java.util.Arrays.stream(WardMode.values()).map(Enum::name).toList());
        for (WardMode mode : WardMode.values()) {
            assertEquals(mode, WardMode.byName(mode.name()));
            assertEquals(mode, WardMode.byIndex(mode.ordinal()));
            assertEquals(mode, mode.next().next().next(), "the panel's button does not come back round");
        }
        assertEquals(WardMode.BLOCK_ALL, WardMode.byName("nonsense"), "an unreadable mode opens a ward");
        assertEquals(WardMode.BLOCK_ALL, WardMode.byIndex(99));
    }

    // ------------------------------------------------------------------ the charge

    @Test
    void upkeepGoesByTheAreaHeld() {
        assertEquals(1.0D, WardCharge.upkeepPerSecond(32.0D, 1.0D), 1.0e-9D);
        assertEquals(4.0D, WardCharge.upkeepPerSecond(64.0D, 1.0D), 1.0e-9D);
        assertEquals(9.0D, WardCharge.upkeepPerSecond(96.0D, 1.0D), 1.0e-9D);
        assertEquals(0.0D, WardCharge.upkeepPerSecond(96.0D, 0.0D));
    }

    /**
     * An unloaded ward is counted up once, whenever something asks. That has to come to what a loaded one, counted
     * every tick, would have - or a base is protected for longer, or shorter, with nobody at it.
     */
    @Test
    void countedOnceAfterAnHourIsCountedEveryTickForAnHour() {
        double upkeep = WardCharge.upkeepPerSecond(48.0D, 1.0D);
        for (double start : new double[]{32_000.0D, 5_000.0D, 100.0D}) {
            double ticked = start;
            for (int tick = 0; tick < 72_000; tick++) {
                ticked = WardCharge.after(ticked, 1L, upkeep);
            }
            assertEquals(ticked, WardCharge.after(start, 72_000L, upkeep), 1.0e-6D, "from " + start);
        }
    }

    @Test
    void aChargeNeverGoesBelowNothingAndTimeNeverRunsBackwards() {
        assertEquals(0.0D, WardCharge.after(10.0D, 1_000_000L, 5.0D));
        assertEquals(10.0D, WardCharge.after(10.0D, -50L, 5.0D), "a clock set back refunds nothing");
        assertEquals(0.0D, WardCharge.after(-3.0D, 0L, 5.0D));
    }

    /** A dropped field waits for its charge; a standing one does not drop until it has none. */
    @Test
    void aFieldThatDropsStaysDownUntilItsChargeRecovers() {
        double most = 32_000.0D;
        assertTrue(WardCharge.standing(true, true, 1.0D, most, 0.25D), "a standing field holds on its last millibucket");
        assertFalse(WardCharge.standing(true, true, 0.0D, most, 0.25D));
        assertFalse(WardCharge.standing(false, true, 7_999.0D, most, 0.25D), "a dropped field came back early");
        assertTrue(WardCharge.standing(false, true, 8_000.0D, most, 0.25D));
        assertFalse(WardCharge.standing(true, false, most, most, 0.25D), "a ward switched off holds a field");
    }

    @Test
    void aHeavierVesselAndAWiderRiftCostMoreToTurn() {
        assertEquals(500L, WardCharge.deflection(500.0D, 100.0D, 0.0D, 40_000.0D));
        assertEquals(600L, WardCharge.deflection(500.0D, 100.0D, 40_000.0D, 40_000.0D));
        assertEquals(1_500L, WardCharge.deflection(500.0D, 100.0D, 400_000.0D, 40_000.0D));
        assertEquals(250L + 40L * 16L, WardCharge.plotted(250.0D, 40.0D, 16.0D));
        assertTrue(WardCharge.affords(600.0D, 600L));
        assertFalse(WardCharge.affords(599.9D, 600L));
    }

    // ------------------------------------------------------------------ limits

    @Test
    void aBetterDrivePushesFurtherInAndACreativeOneIsNotAWardsToStop() {
        double last = -1.0D;
        for (int tier = 0; tier <= 4; tier++) {
            double push = WardRules.defaultPenetration(tier);
            assertTrue(push > last, "tier " + tier + " pushes no further than the one below it");
            last = push;
        }
        assertEquals(0.0D, WardRules.defaultPenetration(0));
        assertEquals(1.0D, WardRules.defaultPenetration(4));
    }

    @Test
    void aRadiusIsKeptBetweenTheNarrowestAndWhatTheServerAllows() {
        assertEquals(WardRules.MIN_RADIUS, WardRules.clampRadius(0, 96));
        assertEquals(96, WardRules.clampRadius(500, 96));
        assertEquals(40, WardRules.clampRadius(40, 96));
        assertEquals(WardRules.MIN_RADIUS, WardRules.clampRadius(40, 4), "a ceiling under the floor still gives a field");
    }

    /** A comparator reading nought means empty, so a launch circuit can tell a dead ward from a low one. */
    @Test
    void aComparatorReadsNoughtOnlyOffAnEmptyWard() {
        assertEquals(0, WardRules.comparator(0.0D, 32_000.0D));
        assertEquals(1, WardRules.comparator(1.0D, 32_000.0D));
        assertEquals(15, WardRules.comparator(32_000.0D, 32_000.0D));
        assertEquals(7, WardRules.comparator(16_000.0D, 32_000.0D));
    }

    @Test
    void interferenceIsTheSameEveryTimeAndStaysInItsSpan() {
        Set<Integer> seen = new HashSet<>();
        for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
                int height = WardRules.interference(x, z, 99L, 60, 20);
                assertTrue(height >= 60 && height < 80, "out of its span at " + x + ", " + z);
                assertEquals(height, WardRules.interference(x, z, 99L, 60, 20), "a second scan read something new");
                seen.add(height);
            }
        }
        assertEquals(20, seen.size(), "interference that never takes some of its heights is a pattern");
        assertEquals(60, WardRules.interference(3, 4, 99L, 60, 1));
    }
}
