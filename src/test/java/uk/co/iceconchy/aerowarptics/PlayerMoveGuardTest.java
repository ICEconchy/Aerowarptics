package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.PlayerMoveGuard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which player moves the server refuses however the player is exempted.
 *
 * <p>The failure this guards against did not throw: the server believed a move of twenty million blocks and
 * spent most of a minute sweeping a collision box across it, with the whole world frozen mid-warp.
 */
class PlayerMoveGuardTest {

    /**
     * The move from the report: a deck position worked out against the ship's pose from before a
     * 20,276-block jump, landing outside Sable's plot grid and taken as a world position. The player was at
     * (-643, 115, -40607); the packet said roughly (20480573, 239, 20462854).
     */
    @Test
    void theMoveThatFroze_theServerIsRefused() {
        assertTrue(PlayerMoveGuard.impossible(20_480_573.0D + 643.0D, 239.0D - 115.0D, 20_462_854.0D + 40_607.0D));
    }

    /** Anything a ship or a player really does in a tick is left alone, the fastest cruise included. */
    @Test
    void realMovementIsBelieved() {
        assertFalse(PlayerMoveGuard.impossible(0.0D, 0.0D, 0.0D));
        assertFalse(PlayerMoveGuard.impossible(48.0D, 0.0D, 0.0D), "a Singularity cruising flat out was refused");
        assertFalse(PlayerMoveGuard.impossible(-300.0D, 40.0D, 250.0D), "an elytra dive over a lagging tick was refused");
        assertFalse(PlayerMoveGuard.impossible(PlayerMoveGuard.LIMIT_BLOCKS, 0.0D, 0.0D));
        assertTrue(PlayerMoveGuard.impossible(PlayerMoveGuard.LIMIT_BLOCKS + 1.0D, 0.0D, 0.0D));
    }

    /** A sum that overflows, or is not a number, is not quietly treated as small. */
    @Test
    void numbersThatAreNotDistancesAreRefused() {
        assertTrue(PlayerMoveGuard.impossible(Double.NaN, 0.0D, 0.0D));
        assertTrue(PlayerMoveGuard.impossible(Double.POSITIVE_INFINITY, 0.0D, 0.0D));
        assertTrue(PlayerMoveGuard.impossible(1.0e200D, 1.0e200D, 0.0D), "an overflowing square slipped through");
    }
}
