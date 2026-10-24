package uk.co.iceconchy.aerowarptics.warpnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Founding, naming, membership and persistence of networks, and the rules about their names. */
class WarpNetworkRegistryTest {

    private static final UUID PILOT = UUID.randomUUID();
    private static final UUID MATE = UUID.randomUUID();

    /** Round-trips a registry through NBT the way the world save does. */
    private static WarpNetworkRegistry reload(WarpNetworkRegistry registry) {
        CompoundTag saved = registry.save(new CompoundTag(), null);
        return WarpNetworkRegistry.factory().deserializer().apply(saved, null);
    }

    // ------------------------------------------------------------------ names

    @Test
    void aNameIsCleanedBeforeItIsKept() {
        assertEquals("Trade Route", NetworkRules.clean("  Trade   Route "));
        assertEquals("", NetworkRules.clean(null));
        assertEquals("trade route", NetworkRules.key(" TRADE  route"));
    }

    @Test
    void aNameHasToBeSomethingAndNotTooMuch() {
        assertTrue(NetworkRules.validName("H"));
        assertTrue(NetworkRules.validName("x".repeat(NetworkRules.NAME_MAX)));
        assertFalse(NetworkRules.validName(""));
        assertFalse(NetworkRules.validName("x".repeat(NetworkRules.NAME_MAX + 1)));
        assertFalse(NetworkRules.validName("line\nbreak"));
        assertFalse(NetworkRules.validName("§cRed"), "a formatting code would colour every list it is drawn in");
    }

    @Test
    void namesAreUniqueRegardlessOfCaseAndSpacing() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork harbour = registry.create("Harbour", PILOT, "Pilot");
        assertNotNull(harbour);

        assertNull(registry.create("harbour", MATE, "Mate"), "a second network took the first one's name");
        assertEquals(harbour, registry.byName("  HARBOUR "));
        assertEquals(1, registry.all().size());
    }

    @Test
    void renamingKeepsTheIndexAndRefusesAnotherNetworksName() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork harbour = registry.create("Harbour", PILOT, "Pilot");
        WarpNetwork north = registry.create("North", PILOT, "Pilot");

        assertFalse(registry.rename(north.id(), "HARBOUR"));
        assertTrue(registry.rename(harbour.id(), "harbour"), "a network may be recased under its own name");
        assertTrue(registry.rename(harbour.id(), "Port"));

        assertNull(registry.byName("Harbour"), "the old name still found it");
        assertEquals(harbour.id(), registry.byName("Port").id());
        assertEquals("Port", registry.nameOf(harbour.id()));
        assertNotNull(registry.create("Harbour", MATE, "Mate"), "the name it gave up could not be taken");
    }

    /** An update is for who is in a network. It must not be a way round the name index. */
    @Test
    void anUpdateCannotChangeTheName() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork harbour = registry.create("Harbour", PILOT, "Pilot");

        assertTrue(registry.update(harbour.withName("Sneaky").withMember(MATE, "Mate")));

        assertEquals("Harbour", registry.byId(harbour.id()).name());
        assertTrue(registry.byId(harbour.id()).has(MATE));
        assertNull(registry.byName("Sneaky"));
        assertFalse(registry.update(WarpNetwork.create(UUID.randomUUID(), "Elsewhere", PILOT, "Pilot")),
                "a network that was never founded was written in");
    }

    // ------------------------------------------------------------- membership

    @Test
    void theOwnerIsInTheNetworkAndNeverAmongItsMembers() {
        WarpNetwork harbour = WarpNetwork.create(UUID.randomUUID(), "Harbour", PILOT, "Pilot")
                .withMember(MATE, "Mate").withMember(PILOT, "Pilot");

        assertTrue(harbour.has(PILOT));
        assertTrue(harbour.has(MATE));
        assertFalse(harbour.has(UUID.randomUUID()));
        assertFalse(harbour.has(null));
        assertEquals(List.of(MATE), List.copyOf(harbour.members().keySet()), "the owner was listed twice");
        assertEquals(2, harbour.size());
    }

    /** Handing a network over is not walking out of it: the old owner's machines are still on it. */
    @Test
    void aNewOwnerKeepsTheOldOneOnAsAMember() {
        WarpNetwork handed = WarpNetwork.create(UUID.randomUUID(), "Harbour", PILOT, "Pilot")
                .withMember(MATE, "Mate").withOwner(MATE, "Mate");

        assertTrue(handed.isOwnedBy(MATE));
        assertFalse(handed.isOwnedBy(PILOT));
        assertTrue(handed.has(PILOT));
        assertEquals(List.of(PILOT), List.copyOf(handed.members().keySet()));
    }

    @Test
    void aPlayersNetworksAreTheOnesTheyAreInByName() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork zephyr = registry.create("zephyr", PILOT, "Pilot");
        WarpNetwork harbour = registry.create("Harbour", MATE, "Mate");
        registry.create("Elsewhere", UUID.randomUUID(), "Stranger");
        registry.update(harbour.withMember(PILOT, "Pilot"));

        assertEquals(List.of("Harbour", "zephyr"),
                registry.networksOf(PILOT).stream().map(WarpNetwork::name).toList());
        assertEquals(1, registry.countOwnedBy(PILOT));
        assertEquals(zephyr.id(), registry.networksOf(PILOT).get(1).id());
    }

    // ------------------------------------------------------------ persistence

    @Test
    void networksSurviveASaveAndReload() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork harbour = registry.create("Harbour", PILOT, "Pilot");
        registry.update(harbour.withMember(MATE, "Mate"));
        registry.create("North", MATE, "Mate");

        WarpNetworkRegistry reloaded = reload(registry);

        assertEquals(2, reloaded.all().size());
        assertEquals(registry.byId(harbour.id()), reloaded.byId(harbour.id()));
        assertEquals(harbour.id(), reloaded.byName("harbour").id());
        assertTrue(reloaded.isMember(harbour.id(), MATE));
    }

    @Test
    void damagedAndDoubledRecordsAreDroppedInsteadOfFailingTheLoad() {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        WarpNetwork harbour = registry.create("Harbour", PILOT, "Pilot");

        CompoundTag saved = registry.save(new CompoundTag(), null);
        ListTag networks = saved.getList("Networks", 10);
        networks.add(new CompoundTag());                                    // missing everything
        CompoundTag ownerless = harbour.save();
        ownerless.putUUID("Id", UUID.randomUUID());
        ownerless.putString("Name", "Ownerless");
        ownerless.remove("Owner");
        networks.add(ownerless);
        CompoundTag namesake = harbour.save();                              // another network of the same name
        namesake.putUUID("Id", UUID.randomUUID());
        namesake.putString("Name", "HARBOUR");
        networks.add(namesake);
        networks.add(harbour.save());                                       // the same network twice

        WarpNetworkRegistry reloaded = WarpNetworkRegistry.factory().deserializer().apply(saved, null);

        assertEquals(1, reloaded.all().size());
        assertEquals(harbour, reloaded.byId(harbour.id()));
        assertEquals(harbour.id(), reloaded.byName("Harbour").id(), "the namesake took the name from the original");
    }

    @Test
    void anEmptyRegistryRoundTripsCleanly() {
        assertTrue(reload(new WarpNetworkRegistry()).all().isEmpty());
    }

    // ------------------------------------------------------------------ rules

    @Test
    void aMachineStaysOnItsNetworkOnlyWhileItsOwnerIsInIt() {
        WarpNetwork harbour = WarpNetwork.create(UUID.randomUUID(), "Harbour", PILOT, "Pilot").withMember(MATE, "Mate");

        assertTrue(NetworkRules.mayStay(harbour, PILOT));
        assertTrue(NetworkRules.mayStay(harbour, MATE));
        assertTrue(NetworkRules.mayStay(harbour, null), "an unowned machine needs only the network to exist");
        assertFalse(NetworkRules.mayStay(harbour, UUID.randomUUID()), "a stranger's machine stayed on the network");
        assertFalse(NetworkRules.mayStay(null, PILOT), "a machine stayed on a network that has gone");
    }

    @Test
    void aCapOfNoughtIsNoCap() {
        assertFalse(NetworkRules.atCap(10_000, 0));
        assertFalse(NetworkRules.atCap(15, 16));
        assertTrue(NetworkRules.atCap(16, 16));
    }
}
