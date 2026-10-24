package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.DimensionChangeTracking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player leaving a dimension is taken off every ship's tracking list there, and nobody else is.
 *
 * <p>Left on even one list, Sable sends that ship's removal a tick late, by grid slot, to a client that
 * is already in another dimension - and deletes whatever ship that client has in the same slot there.
 */
class DimensionChangeTrackingTest {

    private static final UUID LEAVING = UUID.randomUUID();
    private static final UUID STAYING = UUID.randomUUID();

    @Test
    void theLeavingPlayerIsTakenOffEveryListThatHadThem() {
        Set<UUID> parked = new HashSet<>(List.of(LEAVING, STAYING));
        Set<UUID> warped = new HashSet<>(List.of(LEAVING));
        Set<UUID> faraway = new HashSet<>(List.of(STAYING));
        List<Set<UUID>> tracking = new ArrayList<>(List.of(parked, warped, faraway));

        assertEquals(2, DimensionChangeTracking.forget(LEAVING, tracking));
        assertFalse(parked.contains(LEAVING));
        assertFalse(warped.contains(LEAVING));
        assertTrue(parked.contains(STAYING), "everyone who stayed keeps seeing the ship");
        assertTrue(faraway.contains(STAYING));
    }

    @Test
    void aPlayerWhoWasTrackingNothingChangesNothing() {
        Set<UUID> only = new HashSet<>(List.of(STAYING));
        assertEquals(0, DimensionChangeTracking.forget(LEAVING, List.of(only)));
        assertEquals(Set.of(STAYING), only);
    }
}
