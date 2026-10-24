package uk.co.iceconchy.aerowarptics.flotilla;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An escort's four conditions, which one it is told about first, and how harmony builds and breaks. */
class ReadinessTest {

    private static final Readiness.Reach WITHIN = Readiness.Reach.WITHIN;
    private static final Readiness.Reach TOO_CLOSE = Readiness.Reach.TOO_CLOSE;
    private static final Readiness.Reach TOO_FAR = Readiness.Reach.TOO_FAR;
    private static final Readiness.Reach NO_CROSSING = Readiness.Reach.NO_CROSSING;

    private static Readiness.Reading ready() {
        return new Readiness.Reading(true, false, true, true, WITHIN, true, 1.0D, 6.0D, 60, 60,
                Readiness.Survey.CLEAR);
    }

    /** A drive that jumps no less than 128 blocks and no more than 4,000. */
    private static WarpCost.Formula drive() {
        return new WarpCost.Formula(0.20D, 0.00004D, 0.15D, 40_000.0D, 1.0D, 128.0D, 4_000.0D);
    }

    @Test
    void anEscortWithAllFourIsReady() {
        assertEquals(StationStatus.READY, Readiness.status(ready()));
    }

    @Test
    void theFirstThingWrongIsTheOneReported() {
        Readiness.Reading r = ready();
        assertEquals(StationStatus.OUT_OF_CONTACT, Readiness.status(new Readiness.Reading(false, true, false, false,
                TOO_FAR, false, 99, 6, 0, 60, Readiness.Survey.OBSTRUCTED)), "out of contact outranks everything");
        assertEquals(StationStatus.UNPOWERED, Readiness.status(new Readiness.Reading(true, false, false, false,
                WITHIN, true, 99, 6, 0, 60, Readiness.Survey.UNKNOWN)), "no point steering a ship with no power");
        assertEquals(StationStatus.UNCHARGED, Readiness.status(new Readiness.Reading(true, false, true, false,
                WITHIN, true, 99, 6, 0, 60, Readiness.Survey.UNKNOWN)));
        assertEquals(StationStatus.ADRIFT, Readiness.status(new Readiness.Reading(true, false, true, true,
                WITHIN, true, 7, 6, 0, 60, Readiness.Survey.OBSTRUCTED)));
        assertEquals(StationStatus.HARMONISING, Readiness.status(new Readiness.Reading(true, false, true, true,
                WITHIN, true, 1, 6, 59, 60, Readiness.Survey.CLEAR)));
        assertEquals(StationStatus.OBSTRUCTED, Readiness.status(new Readiness.Reading(true, false, true, true,
                WITHIN, true, 1, 6, 60, 60, Readiness.Survey.OBSTRUCTED)));
        assertEquals(StationStatus.SURVEYING, Readiness.status(new Readiness.Reading(true, false, true, true,
                WITHIN, true, 1, 6, 60, 60, Readiness.Survey.UNKNOWN)));
        assertEquals(StationStatus.TOO_FAR, Readiness.status(new Readiness.Reading(true, false, true, true,
                TOO_FAR, false, 1, 6, 60, 60, Readiness.Survey.CLEAR)));
        assertEquals(StationStatus.UNAFFORDABLE, Readiness.status(new Readiness.Reading(true, false, true, true,
                WITHIN, false, 1, 6, 60, 60, Readiness.Survey.CLEAR)));
        assertTrue(r.driveReady());
    }

    /**
     * A course out of a drive's reach is said by which way it is out. All three were one "Out of range" once,
     * and the commonest - a flagship that has arrived at its anchor and still has it as its course - was read
     * as the escort being out of range of its slot, by a ship sitting in the middle of it.
     */
    @Test
    void aCourseOutOfReachIsNamedForWhichWayItIsOut() {
        assertEquals(StationStatus.TOO_CLOSE, Readiness.status(new Readiness.Reading(true, false, true, true,
                TOO_CLOSE, true, 0, 6, 60, 60, Readiness.Survey.CLEAR)), "dead on its slot, and it is the course");
        assertEquals(StationStatus.TOO_FAR, Readiness.status(new Readiness.Reading(true, false, true, true,
                TOO_FAR, true, 0, 6, 60, 60, Readiness.Survey.CLEAR)));
        assertEquals(StationStatus.NO_CROSSING, Readiness.status(new Readiness.Reading(true, false, true, true,
                NO_CROSSING, true, 0, 6, 60, 60, Readiness.Survey.CLEAR)));
        for (Readiness.Reach reach : Readiness.Reach.values()) {
            Readiness.Reading reading = new Readiness.Reading(true, false, true, true, reach, true, 0, 6, 60, 60,
                    Readiness.Survey.CLEAR);
            assertEquals(reach == WITHIN, reading.driveReady(), reach + ": harmony builds only on a course it can fly");
            assertEquals(reach == WITHIN, Readiness.status(reading).ready(), reach.toString());
        }
    }

    @Test
    void aCourseIsMeasuredAgainstBothEndsOfADrivesRange() {
        WarpCost.Formula drive = drive();
        assertEquals(TOO_CLOSE, Readiness.reach(drive, 40.0D, false, true), "a flagship sitting at its anchor");
        assertEquals(TOO_CLOSE, Readiness.reach(drive, 127.9D, false, true));
        assertEquals(WITHIN, Readiness.reach(drive, 128.0D, false, true));
        assertEquals(WITHIN, Readiness.reach(drive, 4_000.0D, false, true));
        assertEquals(TOO_FAR, Readiness.reach(drive, 4_000.1D, false, true));
        assertEquals(NO_CROSSING, Readiness.reach(drive, 1_000.0D, true, false),
                "a crossing this server does not allow is not a matter of distance");
        assertEquals(WITHIN, Readiness.reach(drive, 1_000.0D, true, true));
        assertEquals(TOO_FAR, Readiness.reach(drive, 9_000.0D, true, true), "an allowed crossing is still a jump");
    }

    /**
     * A tied-down escort is told so before anything but losing contact or a busy drive: no charge, no
     * station-keeping and no harmony gets it through a rift while a Docking Connector holds it to the pier.
     */
    @Test
    void aTiedDownEscortIsToldSoBeforeItsDriveOrItsStation() {
        Readiness.Reading tied = new Readiness.Reading(true, false, false, false, TOO_FAR, false, 99, 6, 0, 60,
                Readiness.Survey.OBSTRUCTED, true);
        assertEquals(StationStatus.FASTENED, Readiness.status(tied));
        assertEquals(StationStatus.BUSY, Readiness.status(new Readiness.Reading(true, true, true, true, WITHIN, true,
                1, 6, 60, 60, Readiness.Survey.CLEAR, true)), "a busy drive is still the first thing to wait for");
        assertEquals(StationStatus.FASTENED, Readiness.status(new Readiness.Reading(true, false, true, true, WITHIN,
                true, 1, 6, 60, 60, Readiness.Survey.CLEAR, true)), "otherwise ready is not ready while tied down");

        Readiness.Reading readyButTied = new Readiness.Reading(true, false, true, true, WITHIN, true, 1, 6, 60, 60,
                Readiness.Survey.CLEAR, true);
        assertFalse(readyButTied.driveReady(), "harmony must not build on a ship that cannot leave");
        assertFalse(ready().fastened(), "the shorter reading is of a ship with nothing tying it down");
    }

    @Test
    void harmonyBuildsOnlyWhileStationsAreSoundedAndEverythingHolds() {
        assertEquals(1, Readiness.nextHarmony(0, true, true, true, 60));
        assertEquals(0, Readiness.nextHarmony(30, false, true, true, 60), "nothing harmonises before the horn");
        assertEquals(0, Readiness.nextHarmony(30, true, false, true, 60), "drifting out starts it again");
        assertEquals(0, Readiness.nextHarmony(30, true, true, false, 60), "so does losing the shaft");
        assertEquals(60, Readiness.nextHarmony(60, true, true, true, 60), "and it stops at settled");
    }

    @Test
    void harmonisingTakesTheSettlingTime() {
        int ticks = 0;
        int count = 0;
        while (Readiness.harmony(ticks, 40) < 1.0F) {
            ticks = Readiness.nextHarmony(ticks, true, true, true, 40);
            count++;
        }
        assertEquals(40, count);
    }

    @Test
    void foldWantsAtLeastOneEscortAndEveryOneReady() {
        assertFalse(Readiness.allReady(List.of()), "a flotilla of one is not a fold");
        assertTrue(Readiness.allReady(List.of(StationStatus.READY, StationStatus.READY)));
        assertFalse(Readiness.allReady(List.of(StationStatus.READY, StationStatus.ADRIFT)));
        assertEquals(1, Readiness.countReady(List.of(StationStatus.READY, StationStatus.ADRIFT)));
    }
}
