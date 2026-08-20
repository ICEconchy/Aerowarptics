package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.WarpPassengers;

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
}
