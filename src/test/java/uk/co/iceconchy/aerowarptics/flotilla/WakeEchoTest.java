package uk.co.iceconchy.aerowarptics.flotilla;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a ship that was shaken loose comes out - and that it always comes out somewhere. */
class WakeEchoTest {

    @Test
    void everyCandidateIsInTheRingAndLevelWithTheSlot() {
        List<FormationSlot> candidates = WakeEcho.candidates(42L, 24, 12.0D, 48.0D);
        assertEquals(24, candidates.size());
        for (FormationSlot offset : candidates) {
            double distance = Math.hypot(offset.right(), offset.ahead());
            assertTrue(distance >= 12.0D - 1.0e-9D && distance <= 48.0D + 1.0e-9D, "offset " + distance);
            assertEquals(0.0D, offset.up(), 1.0e-9D, "a straggler is thrown sideways, not dropped on anyone");
        }
    }

    @Test
    void theSameStragglerTriesTheSamePlacesInTheSameOrder() {
        assertEquals(WakeEcho.candidates(7L, 12, 10, 30), WakeEcho.candidates(7L, 12, 10, 30));
    }

    @Test
    void theCandidatesGoAllTheWayRound() {
        List<FormationSlot> candidates = WakeEcho.candidates(99L, 16, 20, 20);
        boolean[] quadrant = new boolean[4];
        for (FormationSlot offset : candidates) {
            quadrant[(offset.right() >= 0 ? 0 : 1) + (offset.ahead() >= 0 ? 0 : 2)] = true;
        }
        for (boolean seen : quadrant) {
            assertTrue(seen, "a ring that only went one way would throw every straggler to the same side");
        }
    }

    @Test
    void theFirstClearCandidateIsTakenAsASlot() {
        FormationSlot slot = new FormationSlot(30, 0, 10);
        List<FormationSlot> candidates = List.of(new FormationSlot(15, 0, 0), new FormationSlot(-15, 0, 0),
                new FormationSlot(0, 0, 15));
        WakeEcho.Placement placement = WakeEcho.place(slot, candidates, at -> at.right() < 30);
        assertTrue(placement.thrown());
        assertEquals(new FormationSlot(15, 0, 10), placement.slot());
    }

    @Test
    void withNowhereClearItComesOutInItsOwnSlot() {
        FormationSlot slot = new FormationSlot(-20, 4, 0);
        WakeEcho.Placement placement = WakeEcho.place(slot, WakeEcho.candidates(1L, 30, 12, 48), at -> false);
        assertFalse(placement.thrown());
        assertEquals(slot, placement.slot(), "late, but where the flagship's search already proved clear");
    }

    @Test
    void aRingWithItsEndsSwappedIsStillARing() {
        for (FormationSlot offset : WakeEcho.candidates(3L, 8, 40, 20)) {
            double distance = Math.hypot(offset.right(), offset.ahead());
            assertTrue(distance >= 20.0D - 1.0e-9D && distance <= 40.0D + 1.0e-9D);
        }
    }
}
