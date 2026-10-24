package uk.co.iceconchy.aerowarptics.warpnet;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may travel to a destination, as a table.
 *
 * <p>The failure this is here for does not throw and does not look wrong: a destination open to somebody
 * it should be shut to draws, lists and warps exactly as a public one does. So every combination is
 * written out, and the rule is held to it.
 */
class NetworkAccessTest {

    private static boolean permits(WarpAnchorAccess access, boolean owner, boolean operator, boolean member) {
        return NetworkAccess.permits(access, true, owner, operator, member);
    }

    @Test
    void aPublicDestinationIsEverybodys() {
        assertTrue(permits(WarpAnchorAccess.PUBLIC, false, false, false));
    }

    @Test
    void aPrivateDestinationIsItsOwnersAndAnOperators() {
        assertTrue(permits(WarpAnchorAccess.PRIVATE, true, false, false));
        assertTrue(permits(WarpAnchorAccess.PRIVATE, false, true, false));
        assertFalse(permits(WarpAnchorAccess.PRIVATE, false, false, false));
        assertFalse(permits(WarpAnchorAccess.PRIVATE, false, false, true),
                "being in its network does not open a private destination");
    }

    @Test
    void aNetworkDestinationIsItsMembers() {
        assertTrue(permits(WarpAnchorAccess.NETWORK, false, false, true));
        assertTrue(permits(WarpAnchorAccess.NETWORK, true, false, false), "its owner, whatever the network says");
        assertTrue(permits(WarpAnchorAccess.NETWORK, false, true, false), "an operator can always recover a ship");
        assertFalse(permits(WarpAnchorAccess.NETWORK, false, false, false),
                "somebody outside the network was let in");
    }

    /** A destination with no network has nobody in it, so Network access on one is Private under another name. */
    @Test
    void networkAccessWithNoNetworkBehindItIsPrivate() {
        for (boolean owner : new boolean[]{false, true}) {
            for (boolean operator : new boolean[]{false, true}) {
                assertEquals(permits(WarpAnchorAccess.PRIVATE, owner, operator, false),
                        permits(WarpAnchorAccess.NETWORK, owner, operator, false));
            }
        }
    }

    /** A mode the server has switched off falls back to the owner alone - it never opens. */
    @Test
    void aModeTheServerForbidsIsTheOwnersAlone() {
        for (WarpAnchorAccess access : WarpAnchorAccess.values()) {
            assertTrue(NetworkAccess.permits(access, false, true, false, false), access + ": the owner was shut out");
            assertFalse(NetworkAccess.permits(access, false, false, true, true),
                    access + " is forbidden and was still open to somebody who is not its owner");
        }
    }

    /** The registry is what answers in the game; a network that is not there has nobody in it. */
    @Test
    void aNetworkThatHasGoneHasNobodyInIt() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        java.util.UUID owner = java.util.UUID.randomUUID();
        WarpNetwork harbour = registry.create("Harbour", owner, "Pilot");

        assertTrue(registry.isMember(harbour.id(), owner));
        assertFalse(registry.isMember(null, owner), "no network is not a network everybody is in");
        registry.remove(harbour.id());
        assertFalse(registry.isMember(harbour.id(), owner), "a disbanded network still had its owner in it");
        assertFalse(Membership.NOBODY.isMember(harbour.id(), owner));
    }

    // ------------------------------------------------------------- the button

    private static List<WarpAnchorAccess> cycle(boolean hasNetwork) {
        List<WarpAnchorAccess> seen = new ArrayList<>();
        WarpAnchorAccess access = WarpAnchorAccess.PUBLIC;
        do {
            seen.add(access);
            access = access.next(hasNetwork);
        } while (access != WarpAnchorAccess.PUBLIC && seen.size() < 10);
        return seen;
    }

    @Test
    void theButtonOffersNetworkOnlyToAMachineOnOne() {
        assertEquals(List.of(WarpAnchorAccess.PUBLIC, WarpAnchorAccess.NETWORK, WarpAnchorAccess.PRIVATE),
                cycle(true));
        assertEquals(List.of(WarpAnchorAccess.PUBLIC, WarpAnchorAccess.PRIVATE), cycle(false));
    }

    /**
     * With three modes, a step the server forbids has to be stepped over. Dropped instead, as it could be
     * when there were two, the button would stop at Public for good where Network is switched off.
     */
    @Test
    void aForbiddenModeIsSteppedOverRatherThanStoppedAt() {
        assertEquals(WarpAnchorAccess.PRIVATE,
                WarpAnchorAccess.PUBLIC.next(true, mode -> mode != WarpAnchorAccess.NETWORK));
        assertEquals(WarpAnchorAccess.PUBLIC,
                WarpAnchorAccess.NETWORK.next(true, mode -> mode != WarpAnchorAccess.PRIVATE));
        assertEquals(WarpAnchorAccess.PUBLIC, WarpAnchorAccess.PUBLIC.next(true, mode -> false),
                "with nothing else allowed the mode stays where it is");
    }

    /** These go over the wire, and into saves, by ordinal and by name. Neither may move. */
    @Test
    void theModesAlreadyInSavesKeepTheirPlaces() {
        assertEquals(0, WarpAnchorAccess.PUBLIC.ordinal());
        assertEquals(1, WarpAnchorAccess.PRIVATE.ordinal());
        assertEquals(WarpAnchorAccess.NETWORK, WarpAnchorAccess.byName("network"));
        assertEquals(WarpAnchorAccess.PUBLIC, WarpAnchorAccess.byName("something from a newer version"));
    }
}
