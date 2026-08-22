package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.WarpPassengers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a passenger who has come off a warping ship gets put back on it.
 *
 * <p>The rule is worth a test of its own because getting it wrong is bad in both directions. Too
 * narrow and somebody is deleted inside a fold in space; too wide and a player who deliberately jumped
 * off a moving ship is teleported back aboard, which is a trap rather than a rescue.
 */
class WarpPassengersTest {

    @Test
    void aPassengerInsideAnApertureIsAlwaysPutBack() {
        assertTrue(WarpPassengers.insideTheFold(WarpFlight.Stage.TRANSIT));
        assertTrue(WarpPassengers.insideTheFold(WarpFlight.Stage.CORRIDOR));
        assertTrue(WarpPassengers.insideTheFold(WarpFlight.Stage.BREACH));
    }

    /**
     * The two stages where the ship is in open air.
     *
     * <p>Falling off a ship is an ordinary thing to do when there is ground under it, so the only
     * thing done to somebody who does is to take back the ship's momentum. Where they land is theirs.
     */
    @Test
    void aPassengerOutInTheWorldIsLeftWhereTheyFall() {
        assertFalse(WarpPassengers.insideTheFold(WarpFlight.Stage.APPROACH));
        assertFalse(WarpPassengers.insideTheFold(WarpFlight.Stage.EMERGE));
    }

    /** Every stage has an answer, so a stage added later cannot quietly default to "abandon them". */
    @Test
    void everyStageOfAFlightIsAccountedFor() {
        int inside = 0;
        for (WarpFlight.Stage stage : WarpFlight.Stage.values()) {
            if (WarpPassengers.insideTheFold(stage)) {
                inside++;
            }
        }
        assertTrue(inside == 3 && WarpFlight.Stage.values().length == 5,
                "a stage was added without deciding what happens to the crew during it");
    }

    /**
     * Recovery covers the run out as well as the fold.
     *
     * <p>It used to stop at the exit aperture, and strays were <em>forgotten</em> during the run out
     * rather than held - so {@code settle} could not put them back either and the last stage of every
     * journey quietly wrote off anyone not yet aboard. The hull is still under the drive's command
     * for the whole of it.
     */
    @Test
    void theRunOutIsStillTheShipsResponsibility() {
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.TRANSIT));
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.CORRIDOR));
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.BREACH));
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.EMERGE),
                "a passenger adrift during the run out is still somewhere they did not choose to be");
    }

    /**
     * The approach is the one stage where stepping off is the player's own business.
     *
     * <p>The ship is where they boarded it and the world underneath is real, so hauling them back
     * would be a trap rather than a rescue.
     */
    @Test
    void steppingOffOnTheApproachIsAllowed() {
        assertFalse(WarpPassengers.recoverable(WarpFlight.Stage.APPROACH));
    }

    /** Every stage is either the approach or recoverable - there is no third case to forget about. */
    @Test
    void everyStageAfterTheApproachIsCovered() {
        for (WarpFlight.Stage stage : WarpFlight.Stage.values()) {
            assertEquals(stage != WarpFlight.Stage.APPROACH, WarpPassengers.recoverable(stage),
                    stage + " is neither clearly the player's business nor clearly the ship's");
        }
    }
}
