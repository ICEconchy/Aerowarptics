package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.weather.RiftStormCycle;
import uk.co.iceconchy.aerowarptics.weather.RiftStormCycle.Change;

import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Storm's clock.
 *
 * <p>Every failure worth guarding here is a quiet one. A storm that never ends does not crash; it just
 * leaves every drive on the server scattering forever. A new world that opens mid-storm, a frozen
 * weather cycle that still lets storms run out, a commanded storm that announces itself twice - each of
 * those plays, looks plausible, and is wrong.
 */
class RiftStormCycleTest {

    private static final IntSupplier CALM = () -> 5;
    private static final IntSupplier STORM = () -> 3;

    private static Change tick(RiftStormCycle cycle) {
        return cycle.tick(true, true, CALM, STORM);
    }

    @Test
    void aNewWorldDoesNotOpenWithAStorm() {
        RiftStormCycle cycle = new RiftStormCycle();
        assertEquals(Change.NONE, tick(cycle), "the first tick rolls the calm; it must not start a storm");
        assertFalse(cycle.raging());
        assertEquals(5, cycle.calmTicks());
    }

    @Test
    void calmRunsOutIntoAStormThatRunsOutIntoCalm() {
        RiftStormCycle cycle = new RiftStormCycle();
        tick(cycle);

        int ticks = 0;
        Change change;
        do {
            change = tick(cycle);
            ticks++;
        } while (change == Change.NONE && ticks < 100);
        assertEquals(Change.BEGAN, change);
        assertEquals(5, ticks, "the storm should arrive exactly when the rolled calm runs out");
        assertTrue(cycle.raging());

        ticks = 0;
        do {
            change = tick(cycle);
            ticks++;
        } while (change == Change.NONE && ticks < 100);
        assertEquals(Change.ENDED, change);
        assertEquals(3, ticks, "the storm should last exactly its rolled length");
        assertFalse(cycle.raging());
        assertEquals(5, cycle.calmTicks(), "a storm ending must roll the calm that follows it");
    }

    @Test
    void aFrozenWeatherCycleFreezesAStormInProgress() {
        RiftStormCycle cycle = new RiftStormCycle(3, 0);
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(Change.NONE, cycle.tick(false, true, CALM, STORM));
        }
        assertEquals(3, cycle.stormTicks(), "doWeatherCycle off holds a storm exactly as it holds the rain");
    }

    @Test
    void withNaturalStormsOffTheCalmNeverEnds() {
        RiftStormCycle cycle = new RiftStormCycle(0, 2);
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(Change.NONE, cycle.tick(true, false, CALM, STORM));
        }
        assertFalse(cycle.raging());
    }

    @Test
    void withNaturalStormsOffACommandedStormStillEnds() {
        RiftStormCycle cycle = new RiftStormCycle();
        assertTrue(cycle.start(2));
        assertEquals(Change.NONE, cycle.tick(true, false, CALM, STORM));
        assertEquals(Change.ENDED, cycle.tick(true, false, CALM, STORM),
                "turning off natural storms must not make a commanded one last forever");
    }

    @Test
    void startingOverAStormOnlyChangesItsLength() {
        RiftStormCycle cycle = new RiftStormCycle();
        assertTrue(cycle.start(10), "the first start begins a storm");
        assertFalse(cycle.start(40), "a second start re-sets the length; it must not announce a second storm");
        assertEquals(40, cycle.stormTicks());
    }

    @Test
    void aStartIsAlwaysAtLeastOneTick() {
        RiftStormCycle cycle = new RiftStormCycle();
        cycle.start(0);
        assertTrue(cycle.raging(), "a storm commanded with no length still has to be a storm");
    }

    @Test
    void stoppingEndsAStormAndSetsTheCalm() {
        RiftStormCycle cycle = new RiftStormCycle(100, 0);
        assertTrue(cycle.stop(250));
        assertFalse(cycle.raging());
        assertEquals(250, cycle.calmTicks());
        assertFalse(cycle.stop(250), "stopping clear weather ends nothing, so there is nothing to announce");
    }

    @Test
    void stoppingWithNoCalmRollsOneRatherThanStartingAStorm() {
        RiftStormCycle cycle = new RiftStormCycle(100, 0);
        cycle.stop(-1);
        assertEquals(Change.NONE, tick(cycle),
                "an unrolled calm must be rolled, not read as a storm being due");
        assertFalse(cycle.raging());
    }

    @Test
    void aRollOfZeroStillLeavesAGap() {
        RiftStormCycle cycle = new RiftStormCycle();
        cycle.tick(true, true, () -> 0, () -> 0);
        assertEquals(1, cycle.calmTicks(), "a zero-length calm would re-read as unrolled forever");
    }
}
