package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flotilla registry: who follows whom, the rules that keep it flat and capped, and that all of it
 * survives a save.
 */
class FlotillaRegistryTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static CouplerRecord coupler(UUID id, CouplerRole role, UUID owner, double x, String name) {
        return new CouplerRecord(id, OVERWORLD, new BlockPos(20_480_000 + (int) x, 100, 20_480_000), UUID.randomUUID(),
                owner, name, x, 120, 0, role, 2);
    }

    private static FlotillaRegistry reload(FlotillaRegistry registry) {
        CompoundTag saved = registry.save(new CompoundTag(), null);
        return FlotillaRegistry.factory().deserializer().apply(saved, null);
    }

    private static final FormationSlot SLOT = new FormationSlot(20, 0, -10);

    @Test
    void anEscortAdmittedStraightInTakesStation() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, "Brassknuckle"));
        registry.track(coupler(escort, CouplerRole.ESCORT, ALICE, 40, "Tern"));

        assertEquals(JoinResult.JOINED, registry.request(escort, flagship, true, SLOT, 4));
        assertEquals(flagship, registry.flagshipOf(escort));
        assertEquals(SLOT, registry.led(flagship).member(escort).slot());
        assertEquals(JoinResult.ALREADY_MEMBER, registry.request(escort, flagship, true, SLOT, 4));
    }

    @Test
    void aStrangerAsksAndWaitsUntilTheOwnerAnswers() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, BOB, 40, ""));

        assertEquals(JoinResult.REQUESTED, registry.request(escort, flagship, false, SLOT, 4));
        assertNull(registry.flagshipOf(escort), "asking is not joining");
        assertEquals(flagship, registry.pendingWith(escort));
        assertEquals(JoinResult.JOINED, registry.accept(flagship, escort, SLOT, 4));
        assertEquals(flagship, registry.flagshipOf(escort));
        assertNull(registry.pendingWith(escort), "the request is used up");
    }

    @Test
    void aRefusedRequestIsGone() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, BOB, 40, ""));
        registry.request(escort, flagship, false, SLOT, 4);
        assertTrue(registry.refuse(flagship, escort));
        assertNull(registry.pendingWith(escort));
        assertEquals(JoinResult.UNKNOWN, registry.accept(flagship, escort, SLOT, 4), "nothing left to accept");
    }

    @Test
    void theCapIsTheFlagshipsDrive() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.track(coupler(first, CouplerRole.ESCORT, ALICE, 40, ""));
        registry.track(coupler(second, CouplerRole.ESCORT, ALICE, 80, ""));
        assertEquals(JoinResult.JOINED, registry.request(first, flagship, true, SLOT, 1));
        assertEquals(JoinResult.FULL, registry.request(second, flagship, true, SLOT, 1), "a Mk I leads one");
        assertEquals(JoinResult.JOINED, registry.request(second, flagship, true, SLOT, -1), "unlimited is unlimited");
    }

    @Test
    void aShipSailsWithOneFlotillaAtATime() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(a, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(b, CouplerRole.FLAGSHIP, BOB, 100, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, ALICE, 40, ""));
        registry.request(escort, a, true, SLOT, 4);
        assertEquals(JoinResult.IN_ANOTHER, registry.request(escort, b, true, SLOT, 4));
        assertEquals(a, registry.flagshipOf(escort));
    }

    @Test
    void askingASecondFlagshipTakesBackTheFirstRequest() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(a, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(b, CouplerRole.FLAGSHIP, ALICE, 100, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, BOB, 40, ""));
        registry.request(escort, a, false, SLOT, 4);
        registry.request(escort, b, false, SLOT, 4);
        assertEquals(b, registry.pendingWith(escort));
        assertFalse(registry.led(a).requests().contains(escort));
    }

    @Test
    void flotillasAreFlat() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(other, CouplerRole.FLAGSHIP, ALICE, 200, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, ALICE, 40, ""));
        assertEquals(JoinResult.NOT_AN_ESCORT, registry.request(other, flagship, true, SLOT, 4),
                "a flagship cannot follow");
        UUID bystander = UUID.randomUUID();
        registry.track(coupler(bystander, CouplerRole.ESCORT, ALICE, 90, ""));
        assertEquals(JoinResult.NOT_A_FLAGSHIP, registry.request(bystander, escort, true, SLOT, 4),
                "an escort cannot be followed");

        registry.request(escort, flagship, true, SLOT, 4);
        // The escort's pilot makes it a flagship: it leaves the flotilla it was in, rather than leading
        // one while following another.
        registry.track(coupler(escort, CouplerRole.FLAGSHIP, ALICE, 40, ""));
        assertNull(registry.flagshipOf(escort));
        assertTrue(registry.led(flagship).escorts().isEmpty());
    }

    @Test
    void aFlagshipThatStopsLeadingLetsEveryoneGo() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(first, CouplerRole.ESCORT, ALICE, 40, ""));
        registry.track(coupler(second, CouplerRole.ESCORT, ALICE, 80, ""));
        registry.request(first, flagship, true, SLOT, 4);
        registry.request(second, flagship, true, new FormationSlot(-20, 0, -10), 4);

        List<UUID> released = registry.track(coupler(flagship, CouplerRole.ESCORT, ALICE, 0, ""));
        assertEquals(List.of(first, second), released);
        assertNull(registry.flagshipOf(first));
        assertNull(registry.led(flagship));
    }

    @Test
    void castingOffAndBreakingOffBothLeaveTheOthersAlone() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(first, CouplerRole.ESCORT, ALICE, 40, ""));
        registry.track(coupler(second, CouplerRole.ESCORT, BOB, 80, ""));
        registry.request(first, flagship, true, SLOT, 4);
        registry.request(second, flagship, true, SLOT, 4);

        assertTrue(registry.castOff(flagship, first));
        assertNull(registry.flagshipOf(first));
        assertEquals(flagship, registry.flagshipOf(second));
        assertTrue(registry.breakOff(second));
        assertTrue(registry.led(flagship).escorts().isEmpty());
        assertFalse(registry.breakOff(second), "breaking off twice changes nothing");
    }

    @Test
    void theWholeFlotillaSurvivesASave() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        UUID asking = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, "Brassknuckle"));
        registry.track(coupler(escort, CouplerRole.ESCORT, ALICE, 40, "Tern"));
        registry.track(coupler(asking, CouplerRole.ESCORT, BOB, 60, ""));
        registry.request(escort, flagship, true, SLOT, 4);
        registry.request(asking, flagship, false, SLOT, 4);
        registry.setAutoAccept(flagship, false);
        registry.setBeaconCallsAll(flagship, false);
        registry.setSharedRift(flagship, true);

        FlotillaRegistry loaded = reload(registry);
        assertEquals(flagship, loaded.flagshipOf(escort));
        assertEquals(SLOT, loaded.led(flagship).member(escort).slot());
        assertEquals(List.of(asking), loaded.led(flagship).requests());
        assertFalse(loaded.led(flagship).autoAccept());
        assertFalse(loaded.led(flagship).beaconCallsAll());
        assertTrue(loaded.led(flagship).sharedRift(), "one rift for the whole fleet survives a save");
        assertEquals("Tern", loaded.coupler(escort).shipName());
        assertEquals(ALICE, loaded.coupler(flagship).owner());
        assertEquals(CouplerRole.FLAGSHIP, loaded.coupler(flagship).role());
    }

    @Test
    void aSaveThatBreaksTheRulesIsMendedOnTheWayIn() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(a, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(coupler(b, CouplerRole.FLAGSHIP, ALICE, 100, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, ALICE, 40, ""));
        registry.request(escort, a, true, SLOT, 4);
        CompoundTag saved = registry.save(new CompoundTag(), null);
        // Hand-edit the save so the same escort is listed under the second flagship as well.
        CompoundTag second = saved.getList("Flotillas", 10).getCompound(1);
        CompoundTag member = new CompoundTag();
        member.putUUID("Coupler", escort);
        member.put("Slot", SLOT.save());
        second.getList("Escorts", 10).add(member);

        FlotillaRegistry loaded = FlotillaRegistry.factory().deserializer().apply(saved, null);
        assertEquals(a, loaded.flagshipOf(escort), "it stays where it was found first");
        assertTrue(loaded.led(b).escorts().isEmpty());
    }

    @Test
    void aCouplerWhoseBlockWentIsKeptUntilTheGraceRunsOut() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        CouplerRecord record = coupler(escort, CouplerRole.ESCORT, ALICE, 40, "");
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(record);
        registry.request(escort, flagship, true, SLOT, 4);

        registry.markGone(escort, OVERWORLD, record.pos(), 1_000L);
        assertTrue(registry.isGone(escort));
        assertTrue(registry.sweep(1_100L, 200L).isEmpty(), "still within the grace");
        assertEquals(flagship, registry.flagshipOf(escort));

        List<FlotillaRegistry.Forgotten> forgotten = registry.sweep(1_200L, 200L);
        assertEquals(1, forgotten.size());
        assertEquals(flagship, forgotten.get(0).followed());
        assertNull(registry.coupler(escort));
        assertNull(registry.flagshipOf(escort));
    }

    @Test
    void aCouplerRebuiltElsewhereKeepsItsPlace() {
        // A crossing into another dimension builds the ship again and takes the old one away: the old block
        // entity marks itself gone, and the copy reports itself from its new plot before the grace is out.
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        CouplerRecord before = coupler(escort, CouplerRole.ESCORT, ALICE, 40, "Tern");
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        registry.track(before);
        registry.request(escort, flagship, true, SLOT, 4);

        registry.markGone(escort, OVERWORLD, before.pos(), 1_000L);
        registry.track(new CouplerRecord(escort, NETHER, new BlockPos(-20_480_000, 70, 3), UUID.randomUUID(), ALICE,
                "Tern", 5, 70, 5, CouplerRole.ESCORT, 2));
        assertFalse(registry.isGone(escort));
        assertTrue(registry.sweep(99_999L, 200L).isEmpty());
        assertEquals(flagship, registry.flagshipOf(escort));
    }

    @Test
    void aStaleMarkFromTheOldPlotIsIgnoredIfTheCopyReportedFirst() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID escort = UUID.randomUUID();
        CouplerRecord before = coupler(escort, CouplerRole.ESCORT, ALICE, 40, "");
        registry.track(before);
        registry.track(new CouplerRecord(escort, OVERWORLD, before.pos().offset(512, 0, 0), UUID.randomUUID(), ALICE,
                "", 40, 120, 0, CouplerRole.ESCORT, 2));
        registry.markGone(escort, OVERWORLD, before.pos(), 5L);
        assertFalse(registry.isGone(escort));
    }

    @Test
    void theGoneMarksSurviveASave() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID escort = UUID.randomUUID();
        CouplerRecord record = coupler(escort, CouplerRole.ESCORT, ALICE, 40, "");
        registry.track(record);
        registry.markGone(escort, OVERWORLD, record.pos(), 77L);
        FlotillaRegistry loaded = reload(registry);
        assertTrue(loaded.isGone(escort));
        assertEquals(1, loaded.sweep(77L + 200L, 200L).size());
    }

    @Test
    void onlyFlagshipsNearbyAndOnOtherShipsAreOffered() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        UUID nearer = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        registry.track(coupler(near, CouplerRole.FLAGSHIP, ALICE, 100, ""));
        registry.track(coupler(far, CouplerRole.FLAGSHIP, ALICE, 1_000, ""));
        registry.track(coupler(nearer, CouplerRole.FLAGSHIP, BOB, 50, ""));
        registry.track(coupler(escort, CouplerRole.ESCORT, ALICE, 200, ""));
        registry.track(new CouplerRecord(UUID.randomUUID(), NETHER, BlockPos.ZERO, UUID.randomUUID(), ALICE, "",
                0, 120, 0, CouplerRole.FLAGSHIP, 2));

        List<CouplerRecord> offered = registry.flagshipsNear(OVERWORLD, 0, 120, 0, 256, null);
        assertEquals(List.of(nearer, near), offered.stream().map(CouplerRecord::id).toList());
        UUID ownShip = registry.coupler(near).ship();
        assertEquals(List.of(nearer), registry.flagshipsNear(OVERWORLD, 0, 120, 0, 256, ownShip).stream()
                .map(CouplerRecord::id).toList(), "a ship is never offered to itself");
    }

    @Test
    void anAutoAcceptSettingIsOnForANewFlotilla() {
        FlotillaRegistry registry = new FlotillaRegistry();
        UUID flagship = UUID.randomUUID();
        registry.track(coupler(flagship, CouplerRole.FLAGSHIP, ALICE, 0, ""));
        assertNotNull(registry.led(flagship));
        assertTrue(registry.led(flagship).autoAccept());
        assertTrue(registry.led(flagship).beaconCallsAll());
        assertFalse(registry.led(flagship).sharedRift(), "a new flotilla tears a rift each until told otherwise");
    }
}
