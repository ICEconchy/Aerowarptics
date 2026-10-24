package uk.co.iceconchy.aerowarptics.sunderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sundering driven tick by tick: that it tears when kept turning, misfires before it commits and ruptures
 * after, and that a pause - a chunk unloading, a reload - never resolves it either way.
 */
class SundererSequenceTest {

    private static final SundererSequence.Conditions TURNING = new SundererSequence.Conditions(true, true);
    private static final SundererSequence.Conditions STOPPED = new SundererSequence.Conditions(false, true);
    private static final SundererSequence.Conditions BROKEN = new SundererSequence.Conditions(true, false);

    /** Runs a sundering to its end under one set of conditions, and says how it ended. */
    private static SundererSequence.Step run(SundererSequence sequence, SundererSequence.Conditions conditions) {
        for (int tick = 0; tick < 10_000; tick++) {
            SundererSequence.Step step = sequence.tick(conditions, 0);
            if (step != SundererSequence.Step.ADVANCED && step != SundererSequence.Step.HELD) {
                return step;
            }
        }
        return SundererSequence.Step.HELD;
    }

    private static void advance(SundererSequence sequence, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            assertEquals(SundererSequence.Step.ADVANCED, sequence.tick(TURNING, 0));
        }
    }

    @Test
    void aSunderingKeptTurningTears() {
        SundererSequence sequence = new SundererSequence(false);
        assertEquals(SundererSequence.Step.TORN, run(sequence, TURNING));
        assertEquals(SundererSequence.length(false), sequence.elapsed());
    }

    @Test
    void theHandAddsItsBeatOnlyWhenItWasRolled() {
        assertEquals(SundererSequence.HAND, SundererSequence.length(true) - SundererSequence.length(false));
        boolean seen = false;
        for (int tick = 0; tick < SundererSequence.length(false); tick++) {
            assertTrue(SundererSequence.at(tick, false) != SundererSequence.Beat.HAND, "a hand nobody rolled at " + tick);
        }
        for (int tick = 0; tick < SundererSequence.length(true); tick++) {
            seen |= SundererSequence.at(tick, true) == SundererSequence.Beat.HAND;
        }
        assertTrue(seen, "a rolled hand never came");
        SundererSequence sequence = new SundererSequence(true);
        assertEquals(SundererSequence.Step.TORN, run(sequence, TURNING));
    }

    @Test
    void aStallWhileReachingMisfiresAndHandsHalfBack() {
        SundererSequence sequence = new SundererSequence(false);
        advance(sequence, 40);
        for (int tick = 0; tick < SundererSequence.REACH_STALL_LIMIT; tick++) {
            assertEquals(SundererSequence.Step.HELD, sequence.tick(STOPPED, 0), "a moment's drop misfired it");
        }
        assertEquals(SundererSequence.Step.MISFIRED, sequence.tick(STOPPED, 0));
        assertEquals(2000, SundererSequence.refund(4000));
    }

    @Test
    void aRingBrokenWhileReachingMisfiresToo() {
        SundererSequence sequence = new SundererSequence(false);
        advance(sequence, 10);
        assertEquals(SundererSequence.Step.MISFIRED, sequence.tick(BROKEN, 0));
    }

    @Test
    void aShortStallWhileStrainingIsWaitedOn() {
        SundererSequence sequence = new SundererSequence(false);
        advance(sequence, SundererSequence.REACHING + 20);
        int before = sequence.elapsed();
        for (int tick = 0; tick < SundererSequence.STALL_LIMIT; tick++) {
            assertEquals(SundererSequence.Step.HELD, sequence.tick(STOPPED, 0));
        }
        assertEquals(before, sequence.elapsed(), "a stalled sundering moved on");
        assertEquals(SundererSequence.Step.ADVANCED, sequence.tick(TURNING, 0));
        assertEquals(0, sequence.stalled(), "turning again did not forgive the stall");
    }

    @Test
    void aStallPastTheLimitWhileStrainingRuptures() {
        SundererSequence sequence = new SundererSequence(false);
        advance(sequence, SundererSequence.REACHING + 20);
        assertEquals(SundererSequence.Step.RUPTURED, run(sequence, STOPPED));
        assertEquals(SundererSequence.STALL_LIMIT + 1, sequence.stalled());
    }

    @Test
    void aRingBrokenWhileStrainingRupturesAtOnce() {
        SundererSequence sequence = new SundererSequence(true);
        advance(sequence, SundererSequence.handStart() + 10);
        assertEquals(SundererSequence.Beat.HAND, sequence.beat());
        assertEquals(SundererSequence.Step.RUPTURED, sequence.tick(BROKEN, 0));
    }

    @Test
    void nothingStopsTheBreach() {
        SundererSequence sequence = new SundererSequence(false);
        advance(sequence, SundererSequence.breachStart(false) + 1);
        assertEquals(SundererSequence.Beat.BREACH, sequence.beat());
        assertEquals(SundererSequence.Step.TORN, run(sequence, new SundererSequence.Conditions(false, false)));
    }

    /** A sundering read back from disk holds through its grace window whatever it is told, and then goes on. */
    @Test
    void aResumedSunderingHoldsThroughItsGrace() {
        SundererSequence sequence = new SundererSequence(false, SundererSequence.REACHING + 30, 0);
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(SundererSequence.Step.HELD, sequence.tick(new SundererSequence.Conditions(false, true), 20));
        }
        assertEquals(SundererSequence.REACHING + 30, sequence.elapsed());
        assertEquals(SundererSequence.Step.ADVANCED, sequence.tick(TURNING, 20));
    }

    /** A sundering that is never ticked - its chunk unloaded - is exactly where it was. */
    @Test
    void aPauseNeverResolves() {
        SundererSequence sequence = new SundererSequence(false, 100, 3);
        assertEquals(100, sequence.elapsed());
        assertEquals(3, sequence.stalled());
        assertEquals(SundererSequence.Beat.REACHING, sequence.beat());
    }

    @Test
    void onlyTheReachingIsUncommitted() {
        for (SundererSequence.Beat beat : SundererSequence.Beat.values()) {
            assertEquals(beat != SundererSequence.Beat.REACHING, beat.committed(), beat.name());
        }
    }
}
