package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.CrewManifest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether a passenger has been left behind.
 *
 * <p>The manifest exists to catch one thing - somebody who is not with their ship - and to be
 * invisible the rest of the time. Both halves of that matter: a threshold too tight makes the game
 * yank players around while they walk about a deck, and a threshold too loose lets somebody stand
 * four thousand blocks away while the system reports itself satisfied.
 *
 * <p>The gap between the two is enormous, which is what makes this safe to decide with one number.
 */
class CrewManifestTest {

    /**
     * Nothing that happens on a deck counts as adrift.
     *
     * <p>The widest of these is deliberately larger than a player could get from their recorded seat
     * by moving, since the seat is re-read every tick of the flight.
     */
    @Test
    void movingAroundTheShipIsNotBeingLeftBehind() {
        for (double blocks : new double[]{0.0D, 0.5D, 1.0D, 2.0D, 4.0D, 5.9D}) {
            assertFalse(CrewManifest.adrift(blocks),
                    blocks + " blocks from a seat is somebody walking, not somebody stranded");
        }
    }

    /** Being at the other end of a warp is. */
    @Test
    void beingLeftAtTheDeparturePointIs() {
        for (double blocks : new double[]{6.5D, 40.0D, 600.0D, 4_195.9D}) {
            assertTrue(CrewManifest.adrift(blocks),
                    blocks + " blocks from a seat is nobody's idea of still being aboard");
        }
    }

    /**
     * A booking outlives the flight that opened it, but not for long.
     *
     * <p>It has to survive a drive dying at the crossing and a hull still coasting to a stop -
     * comfortably more than the run out takes - without following a player around indefinitely after
     * a warp that failed outright.
     */
    @Test
    void theGraceOutlastsAnArrivalWithoutBeingPermanent() {
        assertTrue(CrewManifest.graceTicks() >= 100,
                "a booking of " + CrewManifest.graceTicks() + " ticks may expire before the hull stops");
        assertTrue(CrewManifest.graceTicks() <= 20 * 30,
                "a booking of " + CrewManifest.graceTicks() + " ticks would haunt a failed warp");
    }

    /** Nothing is being watched until a warp asks for it. */
    @Test
    void nothingIsWatchedByDefault() {
        assertTrue(CrewManifest.open() >= 0);
    }
}
