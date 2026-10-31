package uk.co.iceconchy.aerowarptics.rail;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.rail.RailGateLogic.Admission;
import uk.co.iceconchy.aerowarptics.rail.RailGateLogic.Call;
import uk.co.iceconchy.aerowarptics.rail.RailGateLogic.Stranded;

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

    /**
     * A train that stops in a corridor is sent on, because a corridor is a place nobody can reach it.
     *
     * <p>This is the bug a rider knocked off the controls half way through a crossing found in game: the train
     * coasted to a halt eleven blocks into a hundred and sixty of corridor, and there was no way left to get it
     * back. Standing still is the whole test - a train being driven is moving.
     */
    @Test
    void aTrainStandingStillInACorridorIsSentOn() {
        assertEquals(Stranded.SEND_ON, RailGateLogic.stranded(0.0D, false, false, false, 60, 60));
        assertEquals(Stranded.NOTHING, RailGateLogic.stranded(0.0D, false, false, false, 59, 60), "still in grace");
        assertEquals(Stranded.NOTHING, RailGateLogic.stranded(0.2D, false, false, false, 10_000, 60), "moving");
        assertEquals(Stranded.NOTHING, RailGateLogic.stranded(-0.2D, false, false, false, 10_000, 60), "backwards");
        assertEquals(Stranded.NOTHING, RailGateLogic.stranded(0.0D, true, false, false, 10_000, 60),
                "a schedule will carry it out on its own");
        assertEquals(Stranded.WAITING, RailGateLogic.stranded(0.0D, false, true, false, 10_000, 60),
                "its chunks are not there yet; it moves when they are");
        assertEquals(Stranded.LIFT_AND_SEND, RailGateLogic.stranded(0.0D, false, false, true, 60, 60));
    }

    /**
     * A derailed train is lifted back on and sent on, rather than left where it is.
     *
     * <p>It used to be left: a train off its track is normally a player's to pick up and set down again. But in a
     * corridor there is no player who can reach it - which is what the whole of this rescue is about - so being
     * derailed in one was still being lost for good. Reported by players still finding trains stuck after the
     * first fix (2026-10-31).
     */
    @Test
    void aDerailedTrainIsLiftedBackOnAndSentOn() {
        for (boolean navigating : BOTH) {
            for (boolean chunks : BOTH) {
                assertEquals(Stranded.LIFT_AND_SEND,
                        RailGateLogic.stranded(0.0D, navigating, chunks, true, 10_000, 60),
                        "neither a schedule nor a chunk excuses leaving it there");
                assertEquals(Stranded.NOTHING, RailGateLogic.stranded(0.0D, navigating, chunks, true, 0, 60),
                        "but not at once - it is given the same grace as anything else");
            }
        }
    }

    /** It finishes the crossing it started, rather than reversing into the gate it has just come through. */
    @Test
    void aTrainIsSentOnTheWayItWasGoing() {
        assertEquals(1.0D, RailGateLogic.onward(0.8D, -1.0D), "the way it was last seen moving");
        assertEquals(-1.0D, RailGateLogic.onward(-0.8D, 1.0D));
        assertEquals(1.0D, RailGateLogic.onward(0.0D, 1.05D), "what its driver last asked for");
        assertEquals(-1.0D, RailGateLogic.onward(0.0D, -1.05D));
        assertEquals(1.0D, RailGateLogic.onward(0.0D, 0.0D), "nothing to go on: either mouth is a way out");
    }
}
