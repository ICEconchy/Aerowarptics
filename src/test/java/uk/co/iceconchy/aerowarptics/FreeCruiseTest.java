package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.DriveMode;
import uk.co.iceconchy.aerowarptics.warp.FreeCruise;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic of a free cruise.
 *
 * <p>Most of what could go wrong here would fly, and some of it would fly into a hill: a ship that
 * cannot actually stop in the distance it was allowed, a throttle that reads a full signal as less than
 * full, a charge that drains by a different price than a waypoint jump over the same ground.
 */
class FreeCruiseTest {

    /** The shipped cost settings, pinned so these do not move with the defaults. */
    private static WarpCost.Formula formula(double efficiency, double maxRange) {
        return new WarpCost.Formula(0.20D, 0.00004D, 0.15D, 40_000.0D, efficiency, 128.0D, maxRange);
    }

    @Test
    void theSignalIsTheThrottle() {
        assertEquals(0.0D, FreeCruise.throttle(0), 1.0e-9D);
        assertEquals(1.0D, FreeCruise.throttle(15), 1.0e-9D, "a full signal is full throttle");
        assertEquals(7.0D / 15.0D, FreeCruise.throttle(7), 1.0e-9D);
        assertEquals(1.0D, FreeCruise.throttle(99), 1.0e-9D);
        assertEquals(0.0D, FreeCruise.throttle(-3), 1.0e-9D);
    }

    @Test
    void speedBuildsAndBrakesAtItsOwnRates() {
        double accelerate = 0.05D;
        double brake = 0.2D;
        assertEquals(0.05D, FreeCruise.nextSpeed(0.0D, 3.0D, accelerate, brake), 1.0e-9D,
                "getting going is gradual");
        assertEquals(2.8D, FreeCruise.nextSpeed(3.0D, 0.0D, accelerate, brake), 1.0e-9D,
                "stopping uses the braking rate, not the acceleration");
        assertEquals(1.0D, FreeCruise.nextSpeed(0.98D, 1.0D, accelerate, brake), 1.0e-9D,
                "and neither overshoots what was asked for");
        assertEquals(0.0D, FreeCruise.nextSpeed(0.1D, 0.0D, accelerate, brake), 1.0e-9D);
    }

    /**
     * The promise the whole sensor rests on: a ship going {@code safeSpeed} for the sensor's interval and
     * then braking stops inside the clear distance. Simulated tick by tick, the way the drive flies it.
     */
    @Test
    void aShipAtTheSafeSpeedStopsInsideTheClearDistance() {
        double top = FreeCruise.perTick(90.0D);
        double brake = FreeCruise.rate(top, 40);
        for (double clear : new double[]{3.0D, 12.0D, 40.0D, 95.0D, 250.0D}) {
            double speed = FreeCruise.safeSpeed(clear, brake, FreeCruise.SENSOR_INTERVAL);
            double travelled = 0.0D;
            // It does not notice the wall until the next look.
            for (int tick = 0; tick < FreeCruise.SENSOR_INTERVAL; tick++) {
                travelled += speed;
            }
            while (speed > 0.0D) {
                speed = FreeCruise.nextSpeed(speed, 0.0D, 0.0D, brake);
                travelled += speed;
            }
            assertTrue(travelled <= clear + 1.0e-6D,
                    "at the safe speed for " + clear + " blocks the ship ran " + travelled);
        }
    }

    @Test
    void nothingToSeeMeansNoSpeed() {
        assertEquals(0.0D, FreeCruise.safeSpeed(0.0D, 0.1D, 4), 1.0e-9D);
        assertEquals(0.0D, FreeCruise.safeSpeed(-5.0D, 0.1D, 4), 1.0e-9D);
        assertEquals(0.0D, FreeCruise.safeSpeed(50.0D, 0.0D, 4), 1.0e-9D, "a ship that cannot brake may not move");
    }

    @Test
    void theSensorReadsFarEnoughToStopIn() {
        double brake = 0.1D;
        double speed = 3.0D;
        double reach = FreeCruise.sensorReach(speed, brake, FreeCruise.SENSOR_INTERVAL);
        assertTrue(FreeCruise.safeSpeed(reach - FreeCruise.STANDOFF, brake, FreeCruise.SENSOR_INTERVAL)
                >= speed - 1.0e-9D, "a clear look at the full reach must allow the speed it was sized for");
    }

    @Test
    void theLimitIsWhereAWaypointJumpWouldStartBeingRefused() {
        WarpCost.Formula f = formula(1.0D, 1_000_000.0D);
        double mass = 20_000.0D;
        double charge = 0.6D;
        double limit = FreeCruise.limit(f, mass, charge);

        assertTrue(limit > 0.0D);
        assertEquals(WarpFailure.NONE,
                WarpRules.checkDestination(true, true, false, limit - 1.0D, mass, charge, f));
        assertEquals(WarpFailure.INSUFFICIENT_CHARGE,
                WarpRules.checkDestination(true, true, false, limit + 10.0D, mass, charge, f));
    }

    @Test
    void aFreeDriveWithAnUnlimitedRangeCruisesForever() {
        // A creative drive's cruise, as WarpCost.fromConfig builds it: nothing to pay and no world wide enough
        // to hold its range to. It used to stop at 2,495 km, which is what its goggles said.
        WarpCost.Formula creative = formula(100.0D, Double.POSITIVE_INFINITY).free();
        double mass = 40_000.0D;
        double limit = FreeCruise.limit(creative, mass, 1.0D);
        assertEquals(Double.POSITIVE_INFINITY, limit);
        assertEquals(WarpFailure.NONE, FreeCruise.canEngage(creative, mass, 0.0D), "even an empty free drive may go");
        assertEquals(1.0F, FreeCruise.chargeAfter(creative, mass, 1.0D, 1.0e12D), "a free cruise spent some charge");
        assertFalse(FreeCruise.mustDropOut(1.0e12D, 90.0D / 20.0D, 0.05D, limit), "a free cruise ran out of room");
    }

    @Test
    void theLimitNeverPassesTheTiersRange() {
        assertEquals(4_000.0D, FreeCruise.limit(formula(100.0D, 4_000.0D), 0.0D, 1.0D), 1.0e-9D);
    }

    @Test
    void aFullDriveDoesNotReachPastAFullCharge() {
        // Formula.cost clamps at one. Solving against the clamped cost would let a full drive fly forever.
        WarpCost.Formula f = formula(1.0D, 1.0e9D);
        double limit = FreeCruise.limit(f, 0.0D, 1.0D);
        assertEquals((1.0D - 0.20D) / 0.00004D, limit, 1.0D);
        assertTrue(f.exceedsFullCharge(limit + 100.0D, 0.0D));
    }

    @Test
    void theChargeDrainsByWhatAJumpOverTheSameGroundWouldCost() {
        WarpCost.Formula f = formula(1.6D, 100_000.0D);
        double mass = 12_000.0D;
        float left = FreeCruise.chargeAfter(f, mass, 1.0D, 5_000.0D);
        assertEquals(1.0D - f.cost(5_000.0D, mass), left, 1.0e-6D);
        assertEquals(1.0D - f.cost(0.0D, mass), FreeCruise.chargeAfter(f, mass, 1.0D, 0.0D), 1.0e-6D,
                "setting off costs the fixed part at once");
        assertEquals(0.0F, FreeCruise.chargeAfter(f, mass, 0.3D, 1.0e9D), 1.0e-9F, "and never below empty");
    }

    @Test
    void engagingOnlyNeedsEnoughToSetOff() {
        WarpCost.Formula f = formula(1.0D, 100_000.0D);
        assertEquals(WarpFailure.NONE, FreeCruise.canEngage(f, 0.0D, 0.21D),
                "a cruise is flying, and has no minimum distance to afford");
        assertEquals(WarpFailure.INSUFFICIENT_CHARGE, FreeCruise.canEngage(f, 0.0D, 0.1D));
    }

    @Test
    void aHullTooHeavyForAFullDriveIsNotToldToCharge() {
        WarpCost.Formula f = formula(1.0D, 100_000.0D);
        assertEquals(WarpFailure.DESTINATION_TOO_FAR, FreeCruise.canEngage(f, 400_000.0D, 1.0D));
    }

    @Test
    void theDriveLetsGoInTimeToStopByTheLimit() {
        double brake = 0.1D;
        double speed = 4.0D; // stopping distance 80
        assertFalse(FreeCruise.mustDropOut(900.0D, speed, brake, 1_000.0D));
        assertTrue(FreeCruise.mustDropOut(921.0D, speed, brake, 1_000.0D),
                "braking from here would carry it past what it can pay for");
    }

    @Test
    void aGateJumpMidCruiseIsNotChargedAsFlight() {
        assertEquals(4.0D, FreeCruise.countedTravel(4.0D, 4.5D), 1.0e-9D);
        assertEquals(13.5D, FreeCruise.countedTravel(3_000.0D, 4.5D), 1.0e-9D);
        assertEquals(0.0D, FreeCruise.countedTravel(Double.NaN, 4.5D), 1.0e-9D);
        assertEquals(0.0D, FreeCruise.countedTravel(-2.0D, 4.5D), 1.0e-9D);
    }

    @Test
    void cruiseSpeedsAreQuotedPerSecond() {
        assertEquals(4.5D, FreeCruise.perTick(90.0D), 1.0e-9D);
        assertEquals(0.1125D, FreeCruise.rate(4.5D, 40), 1.0e-9D);
    }

    @Test
    void anUnknownModeReadsAsAWaypoint() {
        assertEquals(DriveMode.WAYPOINT, DriveMode.byIndex(0));
        assertEquals(DriveMode.FREE_CRUISE, DriveMode.byIndex(1));
        assertEquals(DriveMode.WAYPOINT, DriveMode.byIndex(-1), "a drive saved before modes has none");
        assertEquals(DriveMode.WAYPOINT, DriveMode.byIndex(99));
        assertEquals(DriveMode.WAYPOINT, DriveMode.FREE_CRUISE.next(), "the button cycles back round");
    }
}
