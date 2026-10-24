package uk.co.iceconchy.aerowarptics.rail;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.rail.RailGateLogic.Admission;
import uk.co.iceconchy.aerowarptics.rail.RailGateLogic.Call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules a gate keeps with trains, as a table.
 */
class RailGateLogicTest {

    private static final boolean[] BOTH = {false, true};

    /** Nothing already crossing is ever held - whatever else is true. A held half-carriage is a train cut in two. */
    @Test
    void nothingPartOfTheWayThroughIsHeld() {
        for (boolean admitted : BOTH) {
            for (boolean ready : BOTH) {
                for (boolean fits : BOTH) {
                    for (boolean open : BOTH) {
                        assertEquals(Admission.ALREADY_CROSSING, RailGateLogic.admit(true, admitted, ready, fits, open));
                    }
                }
            }
        }
    }

    /** A train let in is let in, carriage after carriage, even if the gate has since lost its power. */
    @Test
    void aTrainLetInIsLetInWhole() {
        for (boolean ready : BOTH) {
            for (boolean fits : BOTH) {
                assertEquals(Admission.PASS, RailGateLogic.admit(false, true, ready, fits, false));
            }
        }
    }

    @Test
    void aFreshTrainNeedsALaidLaneARoomyCorridorAndAnOpenGate() {
        assertEquals(Admission.PASS, RailGateLogic.admit(false, false, true, true, true));
        assertEquals(Admission.HOLD_LANE_BUILDING, RailGateLogic.admit(false, false, false, true, true));
        assertEquals(Admission.HOLD_TOO_LONG, RailGateLogic.admit(false, false, true, false, true));
        assertEquals(Admission.HOLD_GATE_SHUT, RailGateLogic.admit(false, false, true, true, false));
    }

    @Test
    void aGateDialsOnlyWhenIdleUnheldAndNotJustRefused() {
        assertEquals(Call.DIAL, RailGateLogic.call(false, false, false, 100, 40));
        assertEquals(Call.NOTHING, RailGateLogic.call(true, false, false, 0, 40), "already open, or opening, to it");
        assertEquals(Call.NOTHING, RailGateLogic.call(true, false, true, 0, 40), "redstone does not shut it on a train");
        assertEquals(Call.HELD, RailGateLogic.call(false, false, true, 100, 40));
        assertEquals(Call.WAIT, RailGateLogic.call(false, true, false, 100, 40), "busy with another connection");
        assertEquals(Call.WAIT, RailGateLogic.call(false, false, false, 39, 40), "too soon after a refusal");
        assertEquals(Call.DIAL, RailGateLogic.call(false, false, false, 40, 40));
    }

    @Test
    void aGateATrainOpenedShutsOnceTrainsAreDoneWithIt() {
        assertFalse(RailGateLogic.shutsNow(true, true, 1_000, 60), "a train still waiting or running through");
        assertFalse(RailGateLogic.shutsNow(true, false, 59, 60), "still lingering");
        assertTrue(RailGateLogic.shutsNow(true, false, 60, 60));
    }

    @Test
    void aGateAPlayerOpenedIsTheirs() {
        for (boolean busy : BOTH) {
            assertFalse(RailGateLogic.shutsNow(false, busy, 100_000, 0));
        }
    }

    /** The train that opens a gate pays to; one that finds it open rides on the upkeep somebody is paying. */
    @Test
    void eachTrainPaysAtMostOnce() {
        assertEquals(370, RailGateLogic.toll(false, 370));
        assertEquals(0, RailGateLogic.toll(true, 370));
    }

    @Test
    void nothingIsShutOrTurnedOnATrain() {
        assertFalse(RailGateLogic.mayChange(true));
        assertTrue(RailGateLogic.mayChange(false));
    }
}
