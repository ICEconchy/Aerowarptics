package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Destination validation and player authority. */
class WarpRulesTest {

    private static final WarpCost.Formula FORMULA =
            new WarpCost.Formula(0.20D, 0.00004D, 0.15D, 40_000.0D, 1.0D, 128.0D, 24_000.0D);

    // ------------------------------------------------------------- authority

    @Test
    void aDeadDriveOrShipIsRefusedBeforeAnythingElse() {
        assertEquals(WarpFailure.DRIVE_BUSY,
                WarpRules.checkAuthority(false, true, 0.0D, 12.0D, true, true, false));
        assertEquals(WarpFailure.NO_AIRSHIP,
                WarpRules.checkAuthority(true, false, 0.0D, 12.0D, true, true, false));
    }

    @Test
    void aPlayerOutOfReachIsRefused() {
        assertEquals(WarpFailure.NONE,
                WarpRules.checkAuthority(true, true, 100.0D, 12.0D, true, true, false));
        assertEquals(WarpFailure.UNAUTHORISED,
                WarpRules.checkAuthority(true, true, 145.0D, 12.0D, true, true, false));
    }

    @Test
    void aPlayerOffTheShipIsRefusedWhenTheServerRequiresThemAboard() {
        assertEquals(WarpFailure.UNAUTHORISED,
                WarpRules.checkAuthority(true, true, 4.0D, 12.0D, false, true, false));
        assertEquals(WarpFailure.NONE,
                WarpRules.checkAuthority(true, true, 4.0D, 12.0D, false, false, false),
                "the requirement is configurable and off means off");
    }

    @Test
    void operatorsBypassProximityAndPresenceButNotAMissingAirship() {
        assertEquals(WarpFailure.NONE,
                WarpRules.checkAuthority(true, true, 1.0e9D, 12.0D, false, true, true));
        assertEquals(WarpFailure.NO_AIRSHIP,
                WarpRules.checkAuthority(true, false, 0.0D, 12.0D, true, true, true));
    }

    // ----------------------------------------------------------- destination

    @Test
    void aDisabledAnchorIsNeverAValidDestination() {
        assertEquals(WarpFailure.ANCHOR_DISABLED,
                WarpRules.checkDestination(false, true, false, 5_000.0D, 0.0D, 1.0D, FORMULA));
    }

    @Test
    void crossDimensionIsRefusedUnlessAHandlerIsRegistered() {
        assertEquals(WarpFailure.DIMENSION_UNSUPPORTED,
                WarpRules.checkDestination(true, false, false, -1.0D, 0.0D, 1.0D, FORMULA));
        assertEquals(WarpFailure.NONE,
                WarpRules.checkDestination(true, false, true, -1.0D, 0.0D, 1.0D, FORMULA));
    }

    @Test
    void rangeIsCheckedBeforeAffordability() {
        assertEquals(WarpFailure.DESTINATION_TOO_CLOSE,
                WarpRules.checkDestination(true, true, false, 20.0D, 0.0D, 0.0D, FORMULA));
        assertEquals(WarpFailure.DESTINATION_TOO_FAR,
                WarpRules.checkDestination(true, true, false, 30_000.0D, 0.0D, 1.0D, FORMULA));
    }

    @Test
    void anUnaffordableWarpIsRefusedRatherThanStartedAndAborted() {
        assertEquals(WarpFailure.INSUFFICIENT_CHARGE,
                WarpRules.checkDestination(true, true, false, 5_000.0D, 0.0D, 0.1D, FORMULA));
        assertEquals(WarpFailure.NONE,
                WarpRules.checkDestination(true, true, false, 5_000.0D, 0.0D, 1.0D, FORMULA));
    }

    @Test
    void aWarpCostingExactlyTheStoredChargeIsAllowed() {
        double distance = 5_000.0D;
        double mass = 12_000.0D;
        double exact = FORMULA.cost(distance, mass);
        assertEquals(WarpFailure.NONE,
                WarpRules.checkDestination(true, true, false, distance, mass, exact, FORMULA));
    }

    @Test
    void aHeavierAirshipCanBecomeUnaffordableAtTheSameCharge() {
        double distance = 12_000.0D;
        assertEquals(WarpFailure.NONE,
                WarpRules.checkDestination(true, true, false, distance, 0.0D, 0.72D, FORMULA));
        assertEquals(WarpFailure.INSUFFICIENT_CHARGE,
                WarpRules.checkDestination(true, true, false, distance, 40_000.0D, 0.72D, FORMULA));
    }

    @Test
    void anAirshipTooHeavyForTheDriveIsOutOfReachRatherThanMerelyUnaffordable() {
        // Raw cost above a full charge is a range problem, not something more charge could fix.
        assertEquals(WarpFailure.DESTINATION_TOO_FAR,
                WarpRules.checkDestination(true, true, false, 12_000.0D, 200_000.0D, 1.0D, FORMULA));
    }

    // -------------------------------------------------------------- failures

    @Test
    void refusalsBeforeCommitmentCostTheDriveNothing() {
        assertFalse(WarpFailure.INSUFFICIENT_CHARGE.penalised());
        assertFalse(WarpFailure.DESTINATION_TOO_FAR.penalised());
        assertFalse(WarpFailure.ANCHOR_FORBIDDEN.penalised());
        assertFalse(WarpFailure.AIRSHIP_ALREADY_WARPING.penalised());
        assertFalse(WarpFailure.NONE.penalised());
    }

    @Test
    void abortsAfterCommitmentDoCostTheDrive() {
        assertTrue(WarpFailure.NO_SAFE_ARRIVAL.penalised());
        assertTrue(WarpFailure.CANCELLED.penalised());
        assertTrue(WarpFailure.AIRSHIP_LOST.penalised());
        assertTrue(WarpFailure.RELOCATION_FAILED.penalised());
    }

    @Test
    void failureIndexRoundTripIsBoundsSafe() {
        for (WarpFailure failure : WarpFailure.values()) {
            assertEquals(failure, WarpFailure.byIndex(failure.ordinal()));
        }
        assertEquals(WarpFailure.NONE, WarpFailure.byIndex(-3));
        assertEquals(WarpFailure.NONE, WarpFailure.byIndex(4242));
    }
}
