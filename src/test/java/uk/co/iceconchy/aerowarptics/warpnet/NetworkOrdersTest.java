package uk.co.iceconchy.aerowarptics.warpnet;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOrders.Caps;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOrders.Resolved;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What may be asked of a network, and by whom.
 *
 * <p>The panel, a machine's network box and the operator's commands all come through these orders, so a
 * rule that holds here holds whichever of the three somebody used.
 */
class NetworkOrdersTest {

    private static final UUID PILOT = UUID.randomUUID();
    private static final UUID MATE = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static Resolved typed(WarpNetworkRegistry registry, String name, UUID machineOwner, UUID actor,
                                  boolean operator, Caps caps) {
        return NetworkOrders.resolve(registry, name, machineOwner, actor, "Somebody", operator, caps);
    }

    private static WarpNetworkRegistry withHarbour() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork harbour = registry.create("Harbour", PILOT, "Pilot");
        registry.update(harbour.withMember(MATE, "Mate"));
        return registry;
    }

    // ------------------------------------------------------- the network box

    @Test
    void nothingTypedIsNoNetworkAndNoRefusal() {
        Resolved resolved = typed(new WarpNetworkRegistry(), "   ", PILOT, PILOT, false, Caps.NONE);
        assertNull(resolved.network());
        assertFalse(resolved.refused());
    }

    @Test
    void aNewNameFoundsANetworkOwnedByWhoeverTypedIt() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();

        Resolved resolved = typed(registry, " Trade  Route ", PILOT, PILOT, false, Caps.NONE);

        assertTrue(resolved.founded());
        WarpNetwork founded = registry.byId(resolved.network());
        assertEquals("Trade Route", founded.name());
        assertTrue(founded.isOwnedBy(PILOT));
    }

    @Test
    void aNameAlreadyTakenJoinsItForAMemberAndRefusesAStranger() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();

        Resolved member = typed(registry, "harbour", MATE, MATE, false, Caps.NONE);
        assertEquals(harbour, member.network());
        assertFalse(member.founded());

        Resolved stranger = typed(registry, "Harbour", STRANGER, STRANGER, false, Caps.NONE);
        assertTrue(stranger.refused(), "somebody put their machine on a network they are not in");
        assertEquals(NetworkNotice.NOT_MEMBER, stranger.notice().key());
        assertEquals("Harbour", stranger.notice().arg());
        assertEquals(1, registry.all().size(), "a refusal founded a network");
    }

    /**
     * It is the machine's owner who has to be in the network. An operator setting up somebody else's anchor
     * must not leave it on a network its own owner cannot use - it would come straight off again the next
     * time the anchor loaded.
     */
    @Test
    void theMachinesOwnerIsWhoHasToBeInTheNetwork() {
        WarpNetworkRegistry registry = withHarbour();
        UUID operator = UUID.randomUUID();

        assertTrue(typed(registry, "Harbour", STRANGER, operator, true, Caps.NONE).refused(),
                "an operator put a stranger's machine on a network the stranger is not in");
        assertFalse(typed(registry, "Harbour", MATE, operator, true, Caps.NONE).refused(),
                "an operator could not put a member's machine on the member's network");
        assertFalse(typed(registry, "Harbour", null, operator, true, Caps.NONE).refused(),
                "an operator could not put an unowned machine on a network");
        assertTrue(typed(registry, "Harbour", null, STRANGER, false, Caps.NONE).refused(),
                "a stranger put an unowned machine on a network they are not in");
        assertTrue(typed(registry, "Brand New", STRANGER, operator, true, Caps.NONE).refused(),
                "a network was founded from a machine whose owner would not be in it");
        assertEquals(1, registry.all().size());
    }

    @Test
    void aPlayerAtTheirLimitFoundsNoMore() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        Caps two = new Caps(true, 2, 0);
        assertFalse(typed(registry, "One", PILOT, PILOT, false, two).refused());
        assertFalse(typed(registry, "Two", PILOT, PILOT, false, two).refused());

        Resolved third = typed(registry, "Three", PILOT, PILOT, false, two);

        assertEquals(NetworkNotice.TOO_MANY, third.notice().key());
        assertEquals(2, registry.all().size());
        assertFalse(typed(registry, "One", PILOT, PILOT, false, two).refused(),
                "the limit on founding networks stopped a player using one they already have");
        assertFalse(typed(registry, "Other", MATE, MATE, false, two).refused(), "one player's limit held another");
    }

    @Test
    void withNetworksSwitchedOffNothingGoesOnOne() {
        WarpNetworkRegistry registry = withHarbour();
        Caps off = new Caps(false, 0, 0);

        assertEquals(NetworkNotice.DISABLED, typed(registry, "Harbour", PILOT, PILOT, false, off).notice().key());
        assertEquals(NetworkNotice.DISABLED, typed(registry, "New", PILOT, PILOT, false, off).notice().key());
        assertFalse(typed(registry, "", PILOT, PILOT, false, off).refused(),
                "a machine could not be taken off its network while networks are switched off");
    }

    @Test
    void aNameThatWillNotDoFoundsNothing() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        Resolved resolved = typed(registry, "x".repeat(NetworkRules.NAME_MAX + 1), PILOT, PILOT, false, Caps.NONE);
        assertEquals(NetworkNotice.NAME_INVALID, resolved.notice().key());
        assertTrue(registry.all().isEmpty());
    }

    // ------------------------------------------------------ the members panel

    @Test
    void onlyTheOwnerOrAnOperatorManagesANetwork() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();

        assertEquals(NetworkNotice.NOT_OWNER,
                NetworkOrders.rename(registry, harbour, MATE, false, "Mine Now").key());
        assertEquals(NetworkNotice.NOT_OWNER,
                NetworkOrders.add(registry, harbour, MATE, false, STRANGER, "Stranger", Caps.NONE).key());
        assertEquals(NetworkNotice.NOT_OWNER, NetworkOrders.remove(registry, harbour, MATE, false, MATE).key(),
                "a member removed somebody, which is the owner's to do - they may only leave");
        assertEquals(NetworkNotice.NOT_OWNER, NetworkOrders.disband(registry, harbour, MATE, false).key());
        assertEquals("Harbour", registry.nameOf(harbour));
        assertEquals(2, registry.byId(harbour).size());

        assertFalse(NetworkOrders.rename(registry, harbour, STRANGER, true, "Port").refused(),
                "an operator could not rename a network");
        assertFalse(NetworkOrders.rename(registry, harbour, null, true, "Harbour").refused(),
                "the console could not rename a network");
    }

    @Test
    void addingAndRemovingMembers() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();

        NetworkNotice added = NetworkOrders.add(registry, harbour, PILOT, false, STRANGER, "Stranger", Caps.NONE);
        assertFalse(added.refused());
        assertEquals("Stranger", added.arg());
        assertTrue(registry.isMember(harbour, STRANGER));
        assertEquals(NetworkNotice.ALREADY_MEMBER,
                NetworkOrders.add(registry, harbour, PILOT, false, STRANGER, "Stranger", Caps.NONE).key());
        assertEquals(NetworkNotice.ALREADY_MEMBER,
                NetworkOrders.add(registry, harbour, PILOT, false, PILOT, "Pilot", Caps.NONE).key(),
                "the owner was added to their own network as a member");

        NetworkNotice removed = NetworkOrders.remove(registry, harbour, PILOT, false, STRANGER);
        assertFalse(removed.refused());
        assertEquals("Stranger", removed.arg(), "the notice names whoever was removed, as the network knew them");
        assertFalse(registry.isMember(harbour, STRANGER));
        assertTrue(NetworkOrders.remove(registry, harbour, PILOT, false, STRANGER).refused(),
                "somebody not in the network was removed from it");
    }

    @Test
    void aFullNetworkTakesNobodyElse() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();
        Caps two = new Caps(true, 0, 2);

        assertEquals(NetworkNotice.FULL,
                NetworkOrders.add(registry, harbour, PILOT, false, STRANGER, "Stranger", two).key(),
                "the owner counts toward the limit, so two is the owner and one other");
        assertEquals(2, registry.byId(harbour).size());
    }

    /** A network with nobody to run it is one nobody can mend, so its owner neither leaves nor is removed. */
    @Test
    void theOwnerCannotLeaveOrBeRemoved() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();

        assertEquals(NetworkNotice.OWNER_STAYS, NetworkOrders.leave(registry, harbour, PILOT).key());
        assertEquals(NetworkNotice.OWNER_STAYS, NetworkOrders.remove(registry, harbour, null, true, PILOT).key());
        assertTrue(registry.byId(harbour).isOwnedBy(PILOT));

        assertFalse(NetworkOrders.leave(registry, harbour, MATE).refused());
        assertFalse(registry.isMember(harbour, MATE));
        assertTrue(NetworkOrders.leave(registry, harbour, STRANGER).refused(), "a stranger left a network they were not in");
    }

    @Test
    void renamingRefusesAnotherNetworksNameAndSaysWhose() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();
        registry.create("North", MATE, "Mate");

        NetworkNotice taken = NetworkOrders.rename(registry, harbour, PILOT, false, "north");
        assertEquals(NetworkNotice.NAME_TAKEN, taken.key());
        assertEquals("North", taken.arg(), "the notice names the network as it is spelt, not as it was typed");
        assertEquals(NetworkNotice.NAME_INVALID, NetworkOrders.rename(registry, harbour, PILOT, false, "  ").key());

        NetworkNotice done = NetworkOrders.rename(registry, harbour, PILOT, false, " Old  Port ");
        assertFalse(done.refused());
        assertEquals("Old Port", registry.nameOf(harbour));
    }

    @Test
    void disbandingEndsTheNetworkAndEverythingAfterSaysItHasGone() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();

        assertFalse(NetworkOrders.disband(registry, harbour, PILOT, false).refused());

        assertNull(registry.byId(harbour));
        assertEquals(NetworkNotice.GONE, NetworkOrders.rename(registry, harbour, PILOT, false, "Port").key());
        assertEquals(NetworkNotice.GONE, NetworkOrders.leave(registry, harbour, MATE).key());
        assertEquals(NetworkNotice.GONE, NetworkOrders.disband(registry, harbour, PILOT, false).key());
        assertNotNull(registry.create("Harbour", MATE, "Mate"), "a disbanded network's name could not be used again");
    }

    @Test
    void theOperatorsOrders() {
        WarpNetworkRegistry registry = withHarbour();
        UUID harbour = registry.byName("Harbour").id();

        assertEquals(NetworkNotice.NAME_TAKEN, NetworkOrders.found(registry, "harbour", STRANGER, "Stranger").key());
        assertFalse(NetworkOrders.found(registry, "North", STRANGER, "Stranger").refused());
        assertTrue(registry.byName("North").isOwnedBy(STRANGER));

        assertFalse(NetworkOrders.transfer(registry, harbour, MATE, "Mate").refused());
        assertTrue(registry.byId(harbour).isOwnedBy(MATE));
        assertTrue(registry.isMember(harbour, PILOT), "handing a network over put its old owner out of it");
    }
}
