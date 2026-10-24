package uk.co.iceconchy.aerowarptics.wellhead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Wellhead's working, run for minutes at a time: that there is somewhere for a kept well to settle and a
 * cliff for a neglected one to go over, and that each thing a player can get wrong moves it the way they are told.
 */
class WellheadReactorTest {

    /** A full cage for the tier these settings stand for. */
    private static final int NEEDED = 8;

    private static final double COBBLE = 1.0D;
    private static final double AMETHYST = 0.4D;

    private static WellheadReactor.Settings settings() {
        return new WellheadReactor.Settings(0.5D, 1.8D, 0.02D, 1.5D, 0.2D, 0.5D, 0.75D, 1.3D, 0.05D, 0.002D,
                0.01D, 0.005D, 0.4D, 20.0D);
    }

    private static WellheadReactor.Conditions kept(int stabilisers) {
        return conditions(128.0D, stabilisers, 0.0D, false, 1_000_000, false, false);
    }

    private static WellheadReactor.Conditions conditions(double speed, int stabilisers, double imbalance, boolean storm,
                                                         int tankRoom, boolean breached, boolean grace) {
        return new WellheadReactor.Conditions(speed, 64.0D, 128.0D, stabilisers, NEEDED, imbalance, storm, 1.0D,
                tankRoom, breached, grace);
    }

    /** Runs a well with its gullet kept full of one feed, and says how much essence came of it. */
    private static int run(WellheadReactor well, WellheadReactor.Conditions conditions, double feed, int seconds) {
        int essence = 0;
        for (int second = 0; second < seconds; second++) {
            if (well.matter() < 8.0D) {
                well.feed(64.0D, feed);
            }
            essence += well.step(conditions, settings()).essence();
        }
        return essence;
    }

    @Test
    void aKeptWellSettlesCalmAndStaysThere() {
        WellheadReactor well = new WellheadReactor();
        run(well, kept(NEEDED), COBBLE, 600);
        double settled = well.strain();
        assertTrue(settled > 0.0D && settled < WellheadReactor.STRAINING, "settled at " + settled);
        run(well, kept(NEEDED), COBBLE, 600);
        assertEquals(settled, well.strain(), 1.0e-6D, "it had not settled");
        assertEquals(1.0D, well.integrity(), 1.0e-9D);
    }

    /** The reason to run one hot: a thinner cage settles higher, and a higher well pays better. */
    @Test
    void aThinnerCageSettlesHigherAndPaysBetter() {
        WellheadReactor full = new WellheadReactor();
        WellheadReactor thin = new WellheadReactor();
        run(full, kept(NEEDED), COBBLE, 600);
        run(thin, kept(3), COBBLE, 600);
        assertTrue(thin.strain() > full.strain() + 0.1D, thin.strain() + " against " + full.strain());
        assertTrue(thin.strain() < WellheadReactor.TEARING, "three of eight should still hold: " + thin.strain());
        int fullYield = run(full, kept(NEEDED), COBBLE, 600);
        int thinYield = run(thin, kept(3), COBBLE, 600);
        assertTrue(thinYield > fullYield * 1.2D, thinYield + " mB against " + fullYield);
    }

    @Test
    void pastTheCliffItRunsAwayThoughNothingElseChanged() {
        WellheadReactor well = new WellheadReactor();
        run(well, kept(3), COBBLE, 600);
        assertTrue(well.band().ordinal() < WellheadReactor.Band.TEARING.ordinal());
        well.forceStrain(0.96D);
        run(well, kept(3), COBBLE, 120);
        assertEquals(WellheadReactor.Band.MELTDOWN, well.band());
    }

    @Test
    void withNoCageRoughFeedRunsAwayAndCleanFeedHolds() {
        WellheadReactor rough = new WellheadReactor();
        run(rough, kept(0), COBBLE, 600);
        assertEquals(WellheadReactor.Band.MELTDOWN, rough.band());

        WellheadReactor clean = new WellheadReactor();
        run(clean, kept(0), AMETHYST, 600);
        assertTrue(clean.strain() < WellheadReactor.TEARING, "amethyst on rotation alone came to " + clean.strain());
    }

    @Test
    void withoutRotationNothingHoldsIt() {
        WellheadReactor stopped = new WellheadReactor();
        run(stopped, conditions(0.0D, NEEDED, 0.0D, false, 1_000_000, false, false), AMETHYST, 600);
        assertEquals(WellheadReactor.Band.MELTDOWN, stopped.band());

        WellheadReactor slow = new WellheadReactor();
        run(slow, conditions(63.0D, NEEDED, 0.0D, false, 1_000_000, false, false), COBBLE, 600);
        assertEquals(WellheadReactor.Band.MELTDOWN, slow.band(), "under the minimum speed still damped");
    }

    @Test
    void aLopsidedCageAStormAndAFullTankEachRaiseTheStrain() {
        WellheadReactor steady = new WellheadReactor();
        run(steady, kept(NEEDED), COBBLE, 600);

        WellheadReactor lopsided = new WellheadReactor();
        run(lopsided, conditions(128.0D, NEEDED, 0.5D, false, 1_000_000, false, false), COBBLE, 600);
        assertTrue(lopsided.strain() > steady.strain() + 0.02D, "a lopsided cage: " + lopsided.strain());

        WellheadReactor stormy = new WellheadReactor();
        run(stormy, conditions(128.0D, NEEDED, 0.0D, true, 1_000_000, false, false), COBBLE, 600);
        assertTrue(stormy.strain() > steady.strain() + 0.02D, "a storm: " + stormy.strain());
        assertTrue(stormy.strain() < WellheadReactor.TEARING, "a storm alone should not tear a kept well");

        WellheadReactor blocked = new WellheadReactor();
        int essence = run(blocked, conditions(128.0D, NEEDED, 0.0D, false, 0, false, false), COBBLE, 600);
        assertEquals(0, essence, "a full tank took essence");
        assertTrue(blocked.strain() > steady.strain() + 0.02D, "a full tank: " + blocked.strain());
    }

    @Test
    void aStormPaysBetterToo() {
        WellheadReactor calm = new WellheadReactor();
        WellheadReactor stormy = new WellheadReactor();
        run(calm, kept(NEEDED), COBBLE, 600);
        run(stormy, conditions(128.0D, NEEDED, 0.0D, true, 1_000_000, false, false), COBBLE, 600);
        int calmYield = run(calm, kept(NEEDED), COBBLE, 300);
        int stormYield = run(stormy, conditions(128.0D, NEEDED, 0.0D, true, 1_000_000, false, false), COBBLE, 300);
        assertTrue(stormYield > calmYield * 1.2D, stormYield + " mB against " + calmYield);
    }

    @Test
    void aScramBringsARunawayBack() {
        WellheadReactor well = new WellheadReactor();
        well.feed(64.0D, COBBLE);
        well.forceStrain(0.95D);
        well.dump();
        assertEquals(0.0D, well.matter());
        for (int second = 0; second < 120; second++) {
            well.step(kept(NEEDED), settings());
        }
        assertTrue(well.strain() < 0.01D, "a scrammed well still at " + well.strain());
    }

    /** With no matter and nothing damping, a tear still comes down of its own accord - slowly. */
    @Test
    void anUntendedEmptyWellIsNeverStuck() {
        WellheadReactor well = new WellheadReactor(0.0D, 0.0D, 0.5D, 1.0D);
        WellheadReactor.Conditions abandoned = conditions(0.0D, 0, 0.0D, false, 0, true, false);
        well.step(abandoned, settings());
        assertTrue(well.strain() < 0.5D);
        for (int second = 0; second < 600; second++) {
            well.step(abandoned, settings());
        }
        assertEquals(0.0D, well.strain(), 1.0e-9D);
    }

    @Test
    void aBreachPutsTheGulletIntoTheTearAtOnce() {
        WellheadReactor full = new WellheadReactor();
        full.feed(256.0D, COBBLE);
        full.breach(256.0D);
        assertEquals(WellheadReactor.Band.MELTDOWN, full.band());
        assertEquals(0.0D, full.matter());

        WellheadReactor nearlyEmpty = new WellheadReactor();
        nearlyEmpty.feed(16.0D, COBBLE);
        nearlyEmpty.breach(256.0D);
        assertEquals(WellheadReactor.Band.CALM, nearlyEmpty.band());

        WellheadReactor empty = new WellheadReactor();
        empty.breach(256.0D);
        assertEquals(0.0D, empty.strain());
    }

    @Test
    void aTearingWellWearsItsFrameAndBleedsItsFissureAndACalmOneMends() {
        WellheadReactor well = new WellheadReactor(0.0D, 0.0D, 0.7D, 1.0D);
        WellheadReactor.Step step = well.step(conditions(128.0D, NEEDED, 0.0D, false, 0, true, false), settings());
        assertEquals(WellheadReactor.Band.TEARING, step.band());
        assertEquals(20, step.bleed());
        assertTrue(well.integrity() < 1.0D);

        WellheadReactor calm = new WellheadReactor(0.0D, 0.0D, 0.1D, 0.5D);
        WellheadReactor.Step mending = calm.step(kept(NEEDED), settings());
        assertEquals(0, mending.bleed());
        assertTrue(calm.integrity() > 0.5D);

        WellheadReactor worn = new WellheadReactor(0.0D, 0.0D, 0.8D, 0.4D);
        worn.step(conditions(128.0D, NEEDED, 0.0D, false, 0, true, false), settings());
        assertEquals(0.4D, worn.integrity(), 1.0e-9D, "wear went through the floor");
    }

    /** Create reports no speed for the first ticks after a load. A well must not take that for a stopped shaft. */
    @Test
    void nothingMovesInTheGraceAfterALoad() {
        WellheadReactor well = new WellheadReactor(64.0D, COBBLE, 0.5D, 0.8D);
        WellheadReactor.Step step = well.step(conditions(0.0D, 0, 0.0D, true, 0, false, true), settings());
        assertEquals(0, step.essence());
        assertEquals(0.5D, well.strain());
        assertEquals(64.0D, well.matter());
        assertEquals(0.8D, well.integrity());
    }

    @Test
    void theHeadingSaysWhereItWillEndWhileItIsStillCalm() {
        WellheadReactor well = new WellheadReactor();
        well.feed(8.0D, COBBLE);
        assertTrue(well.heading(kept(NEEDED), settings(), 600) < WellheadReactor.STRAINING);
        assertEquals(1.0D, well.heading(kept(0), settings(), 600), 1.0e-9D);
        assertEquals(0.0D, well.strain(), "asking where it was heading moved it");
        assertEquals(8.0D, well.matter());
    }

    @Test
    void fifteenMeansCriticalAndNothingLess() {
        assertEquals(0, WellheadSignal.of(false, 0.99D));
        assertEquals(0, WellheadSignal.of(true, 0.0D));
        assertEquals(1, WellheadSignal.of(true, 0.0001D));
        int last = 0;
        for (int step = 0; step <= 1000; step++) {
            double strain = step / 1000.0D;
            int signal = WellheadSignal.of(true, strain);
            assertTrue(signal >= last, "the reading fell as strain rose at " + strain);
            assertEquals(strain >= WellheadReactor.CRITICAL, signal == WellheadSignal.ALARM, "at " + strain);
            last = signal;
        }
        assertEquals(WellheadSignal.ALARM, last);
    }
}
