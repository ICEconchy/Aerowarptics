package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Collapse, walked through tick by tick.
 *
 * <p>Every lesson the ritual teaches is here as an assertion: a balanced cage settles and a lopsided one
 * flings, a storm is faster and wilder, the heart is the only stake, and a pause - an unload, a shaft that
 * stops - never decides anything.
 */
class CollapseRulesTest {

    private static final double REACH = 6.0D;
    private static final CollapseInstability.Settings SETTINGS = CollapseInstability.Settings.DEFAULT;

    private static final List<double[]> BALANCED = List.of(
            new double[]{3, 0, 0}, new double[]{-3, 0, 0}, new double[]{0, 0, 3}, new double[]{0, 0, -3});
    private static final List<double[]> LOPSIDED = List.of(
            new double[]{3, 0, 0}, new double[]{3, 0, 1}, new double[]{3, 0, -1}, new double[]{4, 0, 0});

    /** Runs a whole ritual under constant conditions and says how it ended. */
    private static CollapseRules.Outcome ritual(boolean storm, double speed, List<double[]> cage) {
        CollapseRitual ritual = new CollapseRitual(CollapseRules.ritualTicks(1_200, storm));
        CollapseRitual.Conditions conditions = new CollapseRitual.Conditions(speed, 128.0D, 256.0D, storm, 0.0D,
                cage.size(), CollapseInstability.imbalance(cage, REACH));
        CollapseRitual.Step step;
        int guard = 0;
        do {
            step = ritual.tick(conditions, 60, SETTINGS);
            assertTrue(++guard < 10_000, "a ritual never ended");
        } while (step != CollapseRitual.Step.COMPLETE);
        return CollapseRules.outcome(ritual.instability(), 0.35D, 0.70D);
    }

    @Test
    void aBalancedRingCancelsAndALopsidedOneDoesNot() {
        assertEquals(0.0D, CollapseInstability.imbalance(BALANCED, REACH), 1.0e-9D);
        assertTrue(CollapseInstability.imbalance(LOPSIDED, REACH) > 0.4D);
        assertEquals(CollapseRules.Outcome.SETTLED, ritual(false, 256.0D, BALANCED), "a balanced cage did not settle");
        assertEquals(CollapseRules.Outcome.FLUNG, ritual(false, 256.0D, LOPSIDED), "a lopsided cage did not fling");
        assertEquals(CollapseRules.Outcome.FLUNG, ritual(false, 256.0D, List.of()), "no cage at all did not fling");
    }

    @Test
    void symmetryNotQuantity() {
        // Four balanced stabilisers damp more than four lopsided ones, which is the stabiliser's lesson again.
        double balanced = CollapseInstability.damping(4, CollapseInstability.imbalance(BALANCED, REACH), SETTINGS);
        double lopsided = CollapseInstability.damping(4, CollapseInstability.imbalance(LOPSIDED, REACH), SETTINGS);
        assertTrue(balanced < lopsided);
        // More damps more, down to a floor and no further.
        assertTrue(CollapseInstability.damping(2, 0.0D, SETTINGS) > CollapseInstability.damping(4, 0.0D, SETTINGS));
        assertEquals(SETTINGS.floor(), CollapseInstability.damping(40, 0.0D, SETTINGS), 1.0e-9D);
    }

    @Test
    void aStormRaisesInstabilityAndShortensTheRitual() {
        assertTrue(CollapseRules.ritualTicks(1_200, true) < CollapseRules.ritualTicks(1_200, false));
        assertTrue(CollapseInstability.pressure(true, 256.0D, 256.0D, 0.0D, SETTINGS)
                > CollapseInstability.pressure(false, 256.0D, 256.0D, 0.0D, SETTINGS));
        assertEquals(CollapseRules.Outcome.SPAT, ritual(true, 256.0D, List.of()), "a storm with no cage did not spit");
    }

    @Test
    void turningBelowOptimalRaisesInstability() {
        double optimal = CollapseInstability.pressure(false, 256.0D, 256.0D, 0.0D, SETTINGS);
        double slow = CollapseInstability.pressure(false, 128.0D, 256.0D, 0.0D, SETTINGS);
        assertTrue(slow > optimal);
        assertTrue(CollapseInstability.pressure(false, 256.0D, 256.0D, 0.5D, SETTINGS) > optimal,
                "a tank running short did not raise instability");
    }

    @Test
    void theOutcomeBands() {
        assertEquals(CollapseRules.Outcome.SETTLED, CollapseRules.outcome(0.10D, 0.35D, 0.70D));
        assertEquals(CollapseRules.Outcome.FLUNG, CollapseRules.outcome(0.35D, 0.35D, 0.70D));
        assertEquals(CollapseRules.Outcome.FLUNG, CollapseRules.outcome(0.69D, 0.35D, 0.70D));
        assertEquals(CollapseRules.Outcome.SPAT, CollapseRules.outcome(0.70D, 0.35D, 0.70D));
        // Bands given the wrong way round still leave a settle and a spit, never a flung core past the spit.
        assertEquals(CollapseRules.Outcome.SPAT, CollapseRules.outcome(0.90D, 0.80D, 0.50D));
    }

    @Test
    void noHeartIsARefusalAndEveryRefusalIsNamed() {
        CollapseRules.Preflight ready = new CollapseRules.Preflight(false, false, true, true, true, 4_000, 4_000,
                256.0D, 128.0D);
        assertEquals(CollapseFailure.NONE, CollapseRules.preflight(ready));
        assertEquals(CollapseFailure.NO_HEART, CollapseRules.preflight(new CollapseRules.Preflight(false, false,
                true, false, true, 4_000, 4_000, 256.0D, 128.0D)));
        assertEquals(CollapseFailure.NO_CORE, CollapseRules.preflight(new CollapseRules.Preflight(false, false,
                false, true, true, 4_000, 4_000, 256.0D, 128.0D)));
        assertEquals(CollapseFailure.NO_RECIPE, CollapseRules.preflight(new CollapseRules.Preflight(false, false,
                true, true, false, 4_000, 4_000, 256.0D, 128.0D)));
        assertEquals(CollapseFailure.NOT_ENOUGH_ESSENCE, CollapseRules.preflight(new CollapseRules.Preflight(false,
                false, true, true, true, 3_999, 4_000, 256.0D, 128.0D)));
        assertEquals(CollapseFailure.TOO_SLOW, CollapseRules.preflight(new CollapseRules.Preflight(false, false,
                true, true, true, 4_000, 4_000, 96.0D, 128.0D)));
        assertEquals(CollapseFailure.ABOARD_SHIP, CollapseRules.preflight(new CollapseRules.Preflight(false, true,
                true, true, true, 4_000, 4_000, 256.0D, 128.0D)));
        assertEquals(CollapseFailure.BUSY, CollapseRules.preflight(new CollapseRules.Preflight(true, false,
                true, true, true, 4_000, 4_000, 256.0D, 128.0D)));
    }

    /** The cradle asks every question before it takes anything: nothing is spent on a refusal. */
    @Test
    void everyRefusalComesBeforeAnySpend() {
        String cradle = read("src/main/java/uk/co/iceconchy/aerowarptics/collapse/CollapseCradleBlockEntity.java");
        int begin = cradle.indexOf("private void begin(");
        int preflight = cradle.indexOf("CollapseRules.preflight(", begin);
        int refused = cradle.indexOf("if (failure.isFailure())", begin);
        int returned = cradle.indexOf("return;", refused);
        int take = cradle.indexOf("takeOfferings(copies, false)", begin);
        int extract = cradle.indexOf(".extractItem(0, used, false)", begin);
        assertTrue(preflight > begin && refused > preflight && returned > refused, "the pre-flight moved");
        assertTrue(take > returned && extract > returned, "something is taken before the refusal returns");
        assertTrue(!cradle.substring(begin, returned).contains("tank.drain("), "essence is drained before a refusal");
    }

    /** A spit hands back the core and every offering, and spends only the heart and the essence. */
    @Test
    void aSpitReturnsTheCoreAndOfferingsAndSpendsOnlyTheHeart() {
        String cradle = read("src/main/java/uk/co/iceconchy/aerowarptics/collapse/CollapseCradleBlockEntity.java");
        int finish = cradle.indexOf("private void finish(");
        int spat = cradle.indexOf("case SPAT ->", finish);
        String spit = cradle.substring(spat, cradle.indexOf("}", cradle.indexOf("for (ItemStack offering : drawn)", spat)));
        // One core and one heart: the arms may hold more than one of something, and only one is at stake.
        assertTrue(spit.contains("throwOut(server, spout, slots.extractItem(CORE, 1, false)"),
                "a spit does not hand back the Rift Core");
        assertTrue(spit.contains("for (ItemStack offering : drawn)"), "a spit does not hand back the offerings");
        assertTrue(cradle.substring(finish, spat).contains("slots.extractItem(HEART, 1, false)"),
                "the heart is not spent");
    }

    @Test
    void aPauseNeverResolves() {
        CollapseRitual ritual = new CollapseRitual(200);
        CollapseRitual.Conditions stopped = new CollapseRitual.Conditions(0.0D, 128.0D, 256.0D, false, 0.0D, 0, 1.0D);
        for (int tick = 0; tick < 10_000; tick++) {
            assertEquals(CollapseRitual.Step.HELD, ritual.tick(stopped, 0, SETTINGS));
        }
        assertEquals(0.0D, ritual.progress(), "a held ritual moved");
        assertEquals(0.0D, ritual.instability(), "a held ritual wound up");
    }

    @Test
    void theResumeGraceWindowHolds() {
        CollapseRitual ritual = new CollapseRitual(1_000, 500, 0.2D);
        // Read back from disk mid-ritual: it resumes, and for the grace window a zero speed - which is what
        // Create reports for the first ticks after a load - neither advances it nor winds it up.
        CollapseRitual.Conditions reloading = new CollapseRitual.Conditions(0.0D, 128.0D, 256.0D, false, 0.0D, 0, 1.0D);
        for (int tick = 0; tick < 60; tick++) {
            assertEquals(CollapseRitual.Step.HELD, ritual.tick(reloading, 60, SETTINGS));
        }
        assertEquals(0.5D, ritual.progress(), 1.0e-9D);
        assertEquals(0.2D, ritual.instability(), 1.0e-9D);
        CollapseRitual.Conditions turning = new CollapseRitual.Conditions(256.0D, 128.0D, 256.0D, false, 0.0D, 0, 1.0D);
        assertEquals(CollapseRitual.Step.ADVANCED, ritual.tick(turning, 60, SETTINGS), "the grace window never ended");
    }

    /**
     * What a running collapse shows is where its instability is heading. Told at the first tick, it is where a
     * ritual under steady conditions ends - so a lopsided cage shows as one from the start - and it still is
     * halfway through the winding.
     */
    @Test
    void theHeadingShownIsWhereASteadyRitualEnds() {
        double balancedHeading = -1.0D;
        double lopsidedHeading = -1.0D;
        for (List<double[]> cage : List.of(BALANCED, LOPSIDED, List.<double[]>of())) {
            CollapseRitual ritual = new CollapseRitual(1_200);
            CollapseRitual.Conditions conditions = new CollapseRitual.Conditions(256.0D, 128.0D, 256.0D, false, 0.0D,
                    cage.size(), CollapseInstability.imbalance(cage, REACH));
            double told = ritual.heading(conditions, SETTINGS);
            double midway = -1.0D;
            while (ritual.tick(conditions, 0, SETTINGS) != CollapseRitual.Step.COMPLETE) {
                if (midway < 0.0D && ritual.progress() >= 0.6D) {
                    midway = ritual.heading(conditions, SETTINGS);
                }
            }
            assertEquals(ritual.instability(), told, 1.0e-6D, "the heading told at the start was not where it ended");
            assertEquals(ritual.instability(), midway, 1.0e-6D, "the heading told midway was not where it ended");
            if (cage == BALANCED) {
                balancedHeading = told;
            } else if (cage == LOPSIDED) {
                lopsidedHeading = told;
            }
        }
        assertTrue(lopsidedHeading > balancedHeading, "a lopsided cage looked no worse than a balanced one");
    }

    @Test
    void instabilityOnlyBuildsWhileWinding() {
        CollapseRitual ritual = new CollapseRitual(1_000);
        CollapseRitual.Conditions wild = new CollapseRitual.Conditions(256.0D, 128.0D, 256.0D, true, 1.0D, 0, 1.0D);
        while (ritual.phase() != CollapseRules.Phase.WINDING) {
            ritual.tick(wild, 0, SETTINGS);
        }
        assertEquals(0.0D, ritual.instability(), "instability built before the rift was open");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
