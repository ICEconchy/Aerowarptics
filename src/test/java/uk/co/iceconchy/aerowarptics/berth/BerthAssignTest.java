package uk.co.iceconchy.aerowarptics.berth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which berth a ship is given, who may have one, and why a ship is given none. */
class BerthAssignTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));

    private static final UUID ANCHOR = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID NETWORK = UUID.randomUUID();
    private static final Membership MEMBERS = (network, player) -> NETWORK.equals(network) && MEMBER.equals(player);
    private static final long NOW = 10_000L;

    private static int counter;

    private static Berth berth(int x, int length, int width, int height) {
        // Ids in a known order, so that nothing in a test turns on which random id sorts first.
        UUID id = new UUID(0L, ++counter);
        return Berth.create(id, OVERWORLD, new BlockPos(x, 64, 0), ANCHOR, OWNER, "Ada", 0, 1, 0, 0)
                .withSettings("", 0, 1, 0, length, width, height, 0, BerthRules.DEFAULT_PRIORITY, true, false, true,
                        BerthReserve.ANYONE, null);
    }

    private static Berth with(Berth berth, int priority, BerthReserve reserve, UUID network, boolean enabled) {
        return berth.withSettings(berth.name(), berth.offsetX(), berth.offsetY(), berth.offsetZ(), berth.length(),
                berth.width(), berth.height(), berth.yaw(), priority, enabled, berth.sternFirst(), berth.tractor(),
                reserve, network);
    }

    private static BerthAssign.Vessel ship(double width, double height, double length, UUID who) {
        return new BerthAssign.Vessel(UUID.randomUUID(), width, height, length, new Quaterniond(),
                new Vector3d(0, 0, 1), who);
    }

    private static BerthAssign.Station station(Berth... berths) {
        return new BerthAssign.Station(List.of(berths), 0.5D, 0.5D, 1.0D, NOW, MEMBERS);
    }

    // ------------------------------------------------------------------ the choice

    /** The smallest slip that holds the ship, so a skiff does not take the dreadnought's. */
    @Test
    void aShipIsGivenTheSmallestBerthItFits() {
        Berth great = berth(10, 80, 30, 30);
        Berth small = berth(200, 16, 9, 9);
        Berth middling = berth(100, 40, 16, 16);
        BerthAssign.Station station = station(great, small, middling);

        assertSame(small, BerthAssign.ranked(station, ship(5, 5, 12, STRANGER), Set.of()).get(0).berth());
        assertSame(middling, BerthAssign.ranked(station, ship(10, 10, 30, STRANGER), Set.of()).get(0).berth());
        assertSame(great, BerthAssign.ranked(station, ship(20, 20, 70, STRANGER), Set.of()).get(0).berth());
        assertEquals(3, BerthAssign.ranked(station, ship(5, 5, 12, STRANGER), Set.of()).size());
    }

    /** Equal slips go by priority, lower first; and equal priorities by which is nearer the anchor. */
    @Test
    void equalBerthsGoByPriorityAndThenByDistance() {
        Berth near = berth(10, 16, 9, 9);
        Berth far = berth(300, 16, 9, 9);
        BerthAssign.Vessel skiff = ship(5, 5, 12, STRANGER);
        assertSame(near, BerthAssign.ranked(station(far, near), skiff, Set.of()).get(0).berth());

        Berth preferred = with(far, 10, BerthReserve.ANYONE, null, true);
        assertSame(preferred, BerthAssign.ranked(station(near, preferred), skiff, Set.of()).get(0).berth());
    }

    @Test
    void aBerthStruckOffIsNotOffered() {
        Berth one = berth(10, 16, 9, 9);
        Berth two = berth(300, 16, 9, 9);
        List<BerthAssign.Choice> offered =
                BerthAssign.ranked(station(one, two), ship(5, 5, 12, STRANGER), Set.of(one.id()));
        assertEquals(1, offered.size());
        assertSame(two, offered.get(0).berth());
    }

    // ------------------------------------------------------------------ what is in it

    @Test
    void aBerthWithAShipInItOrOnItsWayIsNotFree() {
        UUID other = UUID.randomUUID();
        Berth empty = berth(10, 16, 9, 9);
        assertTrue(empty.free(NOW));
        assertFalse(empty.moored(other, "Kestrel").free(NOW));
        assertFalse(empty.drawing(other, "Kestrel").free(NOW));
        assertFalse(empty.reserved(other, "Kestrel", NOW + 1).free(NOW));
        assertFalse(empty.moored(other, "Kestrel").letGo().free(NOW), "a ship let go is still lying in the slip");
        assertTrue(empty.moored(other, "Kestrel").letGo().emptied().free(NOW));
        assertFalse(with(empty, 50, BerthReserve.ANYONE, null, false).free(NOW), "a berth switched off was given out");
    }

    /** A warp that was lost never arrives to end its reservation, so the reservation ends itself. */
    @Test
    void aReservationThatHasRunOutIsNoReservation() {
        Berth reserved = berth(10, 16, 9, 9).reserved(UUID.randomUUID(), "Kestrel", NOW);
        assertTrue(reserved.free(NOW));
        assertFalse(reserved.free(NOW - 1));
    }

    /** Planned twice - a dry run, then the launch - a ship is offered the berth already reserved for it. */
    @Test
    void aShipMayHaveTheBerthThatIsAlreadyItsOwn() {
        BerthAssign.Vessel skiff = ship(5, 5, 12, STRANGER);
        Berth reserved = berth(10, 16, 9, 9).reserved(skiff.ship(), "Kestrel", NOW + 600);
        assertEquals(1, BerthAssign.ranked(station(reserved), skiff, Set.of()).size());
        Berth leftLying = berth(10, 16, 9, 9).moored(skiff.ship(), "Kestrel").letGo();
        assertEquals(1, BerthAssign.ranked(station(leftLying), skiff, Set.of()).size());
        // But not one that is holding it: a moored ship is not on its way anywhere.
        Berth holding = berth(10, 16, 9, 9).moored(skiff.ship(), "Kestrel");
        assertTrue(BerthAssign.ranked(station(holding), skiff, Set.of()).isEmpty());
        assertTrue(BerthAssign.ranked(station(reserved), ship(5, 5, 12, STRANGER), Set.of()).isEmpty());
    }

    // ------------------------------------------------------------------ who

    @Test
    void accessIsATable() {
        assertTrue(BerthAccess.permits(BerthReserve.ANYONE, false, false));
        assertTrue(BerthAccess.permits(BerthReserve.NETWORK, true, false));
        assertTrue(BerthAccess.permits(BerthReserve.NETWORK, false, true));
        assertFalse(BerthAccess.permits(BerthReserve.NETWORK, false, false));
        assertTrue(BerthAccess.permits(BerthReserve.OWNER, true, true));
        assertFalse(BerthAccess.permits(BerthReserve.OWNER, false, true));
    }

    @Test
    void aBerthAsksItsOwnNetworkAndFailsShutWithoutOne() {
        Berth networked = with(berth(10, 16, 9, 9), 50, BerthReserve.NETWORK, NETWORK, true);
        assertTrue(networked.permits(MEMBER, MEMBERS));
        assertTrue(networked.permits(OWNER, MEMBERS));
        assertFalse(networked.permits(STRANGER, MEMBERS));
        assertFalse(networked.permits(null, MEMBERS), "nobody's rift was given a berth kept for a network");

        Berth orphaned = with(berth(10, 16, 9, 9), 50, BerthReserve.NETWORK, null, true);
        assertFalse(orphaned.permits(MEMBER, MEMBERS), "a berth kept for no network was open to a member of one");
        // Coming off a network closes a berth to its owner alone; it never opens it.
        assertEquals(BerthReserve.OWNER, networked.withoutNetwork().reserve());
        assertEquals(BerthReserve.ANYONE, berth(10, 16, 9, 9).withoutNetwork().reserve());

        assertTrue(berth(10, 16, 9, 9).permits(null, MEMBERS), "an open berth refused a rift with no author");
    }

    // ------------------------------------------------------------------ why not

    @Test
    void theReasonIsTheOneThePilotCanActOn() {
        BerthAssign.Vessel skiff = ship(5, 5, 12, STRANGER);
        BerthAssign.Vessel liner = ship(20, 20, 70, STRANGER);
        Berth small = berth(10, 16, 9, 9);
        Berth owners = with(berth(100, 80, 30, 30), 50, BerthReserve.OWNER, null, true);

        // Nothing open to them at all.
        assertEquals(BerthAssign.Refusal.BERTH_FORBIDDEN, BerthAssign.why(station(owners), skiff, Set.of()));
        // Something open, and nothing open is big enough - the owner's great slip does not count.
        assertEquals(BerthAssign.Refusal.BERTH_TOO_SMALL, BerthAssign.why(station(small, owners), liner, Set.of()));
        // Big enough, and taken.
        assertEquals(BerthAssign.Refusal.NO_FREE_BERTH,
                BerthAssign.why(station(small.moored(UUID.randomUUID(), "Kestrel")), skiff, Set.of()));
        assertEquals(BerthAssign.Refusal.NO_FREE_BERTH, BerthAssign.why(station(small), skiff, Set.of(small.id())));
        // And none where there is one.
        assertEquals(BerthAssign.Refusal.NONE, BerthAssign.why(station(small), skiff, Set.of()));
        // A berth switched off is not a berth: a station of them is open to nobody.
        assertEquals(BerthAssign.Refusal.BERTH_FORBIDDEN,
                BerthAssign.why(station(with(small, 50, BerthReserve.ANYONE, null, false)), skiff, Set.of()));
    }

    // ------------------------------------------------------------------ fleets

    /**
     * The largest ship chooses first. In the order asked, the flagship here takes the only slip its escort
     * fits and a fleet the station has room for is refused.
     */
    @Test
    void aFleetIsBerthedLargestFirstAndAnsweredInTheOrderAsked() {
        Berth great = berth(10, 80, 30, 30);
        Berth small = berth(100, 16, 9, 9);
        BerthAssign.Vessel flagship = ship(5, 5, 12, STRANGER);
        BerthAssign.Vessel escort = ship(20, 20, 70, STRANGER);
        // The flagship would take the great slip if the small one were struck off; it must not get the chance.
        BerthAssign.Fleet fleet = BerthAssign.fleet(station(great, small), List.of(flagship, escort));
        assertTrue(fleet.berthed());
        assertSame(flagship, fleet.choices().get(0).vessel());
        assertSame(small, fleet.choices().get(0).berth());
        assertSame(great, fleet.choices().get(1).berth());
    }

    @Test
    void aFleetWithOneShipTooManyIsRefusedWhole() {
        Berth one = berth(10, 16, 9, 9);
        Berth two = berth(100, 16, 9, 9);
        BerthAssign.Vessel a = ship(5, 5, 12, STRANGER);
        BerthAssign.Vessel b = ship(5, 5, 11, STRANGER);
        BerthAssign.Vessel c = ship(5, 5, 10, STRANGER);
        BerthAssign.Fleet fleet = BerthAssign.fleet(station(one, two), List.of(a, b, c));
        assertFalse(fleet.berthed());
        assertTrue(fleet.choices().isEmpty(), "part of a fleet was berthed");
        assertEquals(BerthAssign.Refusal.NO_FREE_BERTH, fleet.refusal());
        assertSame(c, fleet.refused());

        BerthAssign.Vessel liner = ship(20, 20, 70, STRANGER);
        BerthAssign.Fleet tooBig = BerthAssign.fleet(station(one, two), List.of(a, liner));
        assertEquals(BerthAssign.Refusal.BERTH_TOO_SMALL, tooBig.refusal());
        assertSame(liner, tooBig.refused());
    }

    @Test
    void noTwoShipsOfAFleetShareABerth() {
        Berth[] berths = {berth(10, 16, 9, 9), berth(100, 16, 9, 9), berth(200, 16, 9, 9)};
        List<BerthAssign.Vessel> ships = List.of(ship(5, 5, 12, STRANGER), ship(5, 5, 12, STRANGER),
                ship(5, 5, 12, STRANGER));
        BerthAssign.Fleet fleet = BerthAssign.fleet(station(berths), ships);
        assertTrue(fleet.berthed());
        assertEquals(3, fleet.choices().stream().map(choice -> choice.berth().id()).distinct().count());
    }
}
