package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.GateWatch;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Noticing that something has gone through a gate.
 *
 * <p>This exists because the first version of it did not work. Crossings were inferred from an
 * entity's previous position, which depends on when in the tick that entity happened to move - so
 * walking into a dialled gate sometimes did nothing at all, and the gate looked broken rather than
 * unlucky.
 */
class GateWatchTest {

    private static final UUID DRIVER = UUID.nameUUIDFromBytes("driver".getBytes());
    private static final UUID CART = UUID.nameUUIDFromBytes("cart".getBytes());

    private static final boolean NEAR = false;
    private static final boolean FAR = true;

    @Test
    void theFirstSightingIsNeverACrossing() {
        // Something that appears already past the plane - loaded in with the chunk, walked in from
        // the side, or put down by the gate at the other end - has not gone through anything.
        GateWatch watch = new GateWatch();
        assertFalse(watch.stepped(DRIVER, FAR), "a first sighting was read as a crossing");
        assertFalse(new GateWatch().stepped(DRIVER, NEAR), "a first sighting was read as a crossing");
    }

    @Test
    void standingStillIsNeverACrossing() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        for (int tick = 0; tick < 40; tick++) {
            assertFalse(watch.stepped(DRIVER, NEAR), "standing by a gate was read as going through it");
        }
    }

    @Test
    void changingSidesIsACrossing() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        assertTrue(watch.stepped(DRIVER, FAR), "walking through was not noticed");
        // And once, not every tick afterwards.
        assertFalse(watch.stepped(DRIVER, FAR), "the same crossing was counted twice");
    }

    @Test
    void goingBackIsAlsoACrossing() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        assertTrue(watch.stepped(DRIVER, FAR));
        assertTrue(watch.stepped(DRIVER, NEAR), "coming back through was not noticed");
    }

    @Test
    void travellersAreWatchedApart() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        watch.stepped(CART, FAR);
        assertFalse(watch.stepped(CART, FAR), "one traveller's move was read onto another");
        assertTrue(watch.stepped(DRIVER, FAR));
        assertEquals(2, watch.watched());
    }

    /**
     * Forgetting on the way through is what stops a traveller bouncing.
     *
     * <p>Something sent through this gate is about to be somewhere else entirely. Keeping its old side
     * would mean that the next time it wandered past - possibly on the other side - the gate would
     * count that as a crossing and send it away again.
     */
    @Test
    void aTravellerSentThroughIsForgotten() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        assertTrue(watch.stepped(DRIVER, FAR));
        watch.forget(DRIVER);
        assertEquals(0, watch.watched());
        assertFalse(watch.stepped(DRIVER, NEAR), "a returning traveller was read as crossing");
    }

    @Test
    void anythingOutOfRangeIsForgotten() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        watch.stepped(CART, NEAR);
        watch.retain(Set.of(DRIVER));
        assertEquals(1, watch.watched(), "something out of range was still being watched");
        // The one still in range keeps its side, so its next step is still judged properly.
        assertTrue(watch.stepped(DRIVER, FAR));
        // The one that left starts again, rather than being teleported the moment it comes back.
        assertFalse(watch.stepped(CART, FAR));
    }

    @Test
    void clearingDropsEverything() {
        GateWatch watch = new GateWatch();
        watch.stepped(DRIVER, NEAR);
        watch.stepped(CART, FAR);
        watch.clear();
        assertEquals(0, watch.watched());
        assertFalse(watch.stepped(DRIVER, FAR), "a cleared watch still remembered a side");
    }
}
