package uk.co.iceconchy.aerowarptics.stabiliser;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One stabiliser per hull, per tick.
 *
 * <p>Stacking is the failure this guards, and it is the one a player would walk straight into: bolt a
 * second stabiliser on to make it stronger, and each one absorbs most of what the other let through,
 * until the ship barely answers its own balloons. Nothing about that throws.
 */
class HullClaimsTest {

    private static final UUID HULL = UUID.fromString("7f0c1a52-2c4e-4b9e-9d1e-3b2a6f1c0d11");
    private static final UUID OTHER_HULL = UUID.fromString("0b8e5d33-91aa-4c07-8f0e-6d2c4a9b7e22");
    private static final long FIRST = 11L;
    private static final long SECOND = 42L;

    @Test
    void theFirstStabiliserToAskHasTheHullAndTheSecondStandsBy() {
        HullClaims claims = new HullClaims();
        assertTrue(claims.claim(HULL, 100L, FIRST));
        assertFalse(claims.claim(HULL, 100L, SECOND), "a second stabiliser on the same tick would stack");
    }

    @Test
    void askingAgainOnTheSameTickIsNotAConflictWithItself() {
        HullClaims claims = new HullClaims();
        assertTrue(claims.claim(HULL, 100L, FIRST));
        assertTrue(claims.claim(HULL, 100L, FIRST));
    }

    @Test
    void theClaimLastsOneTick() {
        HullClaims claims = new HullClaims();
        claims.claim(HULL, 100L, FIRST);
        // The one that held it stopped - switched off, broken - and did not ask on the next tick.
        assertTrue(claims.claim(HULL, 101L, SECOND), "a claim nobody renews must not lock the hull forever");
        assertFalse(claims.claim(HULL, 101L, FIRST));
    }

    @Test
    void differentHullsDoNotCompete() {
        HullClaims claims = new HullClaims();
        assertTrue(claims.claim(HULL, 100L, FIRST));
        // Plot positions are per level, so the same long can turn up on a different ship.
        assertTrue(claims.claim(OTHER_HULL, 100L, SECOND));
        assertTrue(claims.claim(OTHER_HULL, 100L, SECOND));
    }

    @Test
    void onlyTheHolderCanGiveAHullBack() {
        HullClaims claims = new HullClaims();
        claims.claim(HULL, 100L, FIRST);

        claims.release(HULL, SECOND);
        assertFalse(claims.claim(HULL, 100L, SECOND), "a stabiliser that was standing by cannot free a hull");

        claims.release(HULL, FIRST);
        assertEquals(0, claims.size(), "a released hull is forgotten rather than left in the map");
        assertTrue(claims.claim(HULL, 100L, SECOND), "and once given back, the one standing by may take over");
    }
}
