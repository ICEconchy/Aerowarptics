package uk.co.iceconchy.aerowarptics.gate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A passage lasts as long as it says. Two countdowns in this codebase have run a tick long, and a
 * traveller's client is told the number: one that disagrees with the server by a tick is a flare that
 * peaks before, or after, the traveller is let go.
 */
class PassageClockTest {

    /** Counts ticks until the clock says the traveller has arrived, from - and including - the one it began in. */
    private static int ticksToArrive(PassageClock clock) {
        for (int tick = 0; tick < 10_000; tick++) {
            if (clock.tick()) {
                return tick;
            }
        }
        throw new AssertionError("the clock never arrived");
    }

    @Test
    void aPassageOfSixtyTicksArrivesSixtyTicksAfterTheOneItBeganIn() {
        for (int ticks : new int[] {1, 2, 60, 200}) {
            assertEquals(ticks, ticksToArrive(new PassageClock(ticks, 14)), "a passage of " + ticks + " ticks");
        }
    }

    /** The tick a passage begins in is the gate's, part-way through: it counts nothing. */
    @Test
    void theTickItBeginsInIsNotOneOfItsOwn() {
        PassageClock clock = new PassageClock(3, 0);
        assertFalse(clock.tick(), "the tick it began in");
        assertEquals(PassageClock.Stage.HELD, clock.stage());
        assertFalse(clock.tick());
        assertFalse(clock.tick());
        assertTrue(clock.tick());
    }

    @Test
    void itArrivesExactlyOnce() {
        PassageClock clock = new PassageClock(5, 14);
        int arrivals = 0;
        for (int tick = 0; tick < 100; tick++) {
            if (clock.tick()) {
                arrivals++;
            }
        }
        assertEquals(1, arrivals);
        assertEquals(PassageClock.Stage.OVER, clock.stage());
    }

    /** Protected for as long after arriving as the light takes to clear, and not a tick more. */
    @Test
    void theGraceRunsItsOwnLengthAfterArriving() {
        PassageClock clock = new PassageClock(60, GatePassage.EMERGE_TICKS);
        ticksToArrive(clock);
        assertEquals(PassageClock.Stage.GRACE, clock.stage(), "an arrived traveller lost their grace at once");
        for (int tick = 1; tick < GatePassage.EMERGE_TICKS; tick++) {
            assertFalse(clock.tick());
            assertEquals(PassageClock.Stage.GRACE, clock.stage(), "the grace ran out after " + tick + " ticks");
        }
        clock.tick();
        assertEquals(PassageClock.Stage.OVER, clock.stage(), "the grace outlasted the light");
    }

    @Test
    void aPassageWithNoGraceIsOverAsItArrives() {
        PassageClock clock = new PassageClock(4, 0);
        ticksToArrive(clock);
        assertEquals(PassageClock.Stage.OVER, clock.stage());
    }

    /** Cut short - its hull gone, its travellers left - it is held no longer, and is not reported twice. */
    @Test
    void cutShortItIsNoLongerHeldAndDoesNotArriveAgain() {
        PassageClock clock = new PassageClock(60, 3);
        clock.tick();
        clock.tick();
        clock.cutShort();
        assertEquals(PassageClock.Stage.GRACE, clock.stage());
        for (int tick = 0; tick < 10; tick++) {
            assertFalse(clock.tick(), "a passage cut short arrived a second time");
        }
        assertEquals(PassageClock.Stage.OVER, clock.stage());
    }

    /** Even in the tick it began in, before that tick's end has been counted. */
    @Test
    void cutShortInItsFirstTickItStillDoesNotArriveAgain() {
        PassageClock clock = new PassageClock(60, 2);
        clock.cutShort();
        assertFalse(clock.tick());
        assertFalse(clock.tick());
        assertEquals(PassageClock.Stage.OVER, clock.stage());
    }

    /** A server config of nought never reaches this - the gate is instant - but a clock is never zero long. */
    @Test
    void aPassageIsAtLeastOneTick() {
        assertEquals(1, ticksToArrive(new PassageClock(0, 0)));
        assertEquals(1, ticksToArrive(new PassageClock(-5, 0)));
    }
}
