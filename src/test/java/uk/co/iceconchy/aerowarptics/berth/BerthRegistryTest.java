package uk.co.iceconchy.aerowarptics.berth;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the server remembers of a Berth while its block is away. */
class BerthRegistryTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

    private static Berth berth(BlockPos pos, ResourceKey<Level> dimension, UUID anchor) {
        return Berth.create(UUID.randomUUID(), dimension, pos, anchor, UUID.randomUUID(), "Ada", 3, 1, -8, 270);
    }

    private static BerthRegistry reload(BerthRegistry registry) {
        CompoundTag saved = registry.save(new CompoundTag(), null);
        return BerthRegistry.factory().deserializer().apply(saved, null);
    }

    /** Everything that decides what an unloaded berth does has to come back from disk exactly as it went. */
    @Test
    void aBerthComesBackFromDiskAsItWent() {
        UUID anchor = UUID.randomUUID();
        UUID network = UUID.randomUUID();
        BerthRegistry registry = new BerthRegistry();
        Berth plain = berth(new BlockPos(120, 70, -3300), OVERWORLD, anchor);
        Berth set = berth(new BlockPos(-4, 40, 9), NETHER, anchor)
                .withSettings("Slip Three", -12, 4, 30, 64, 20, 18, 135, 7, false, true, false, BerthReserve.NETWORK,
                        network)
                .moored(UUID.randomUUID(), "Kestrel");
        Berth reserved = berth(new BlockPos(0, 64, 0), OVERWORLD, null).reserved(UUID.randomUUID(), "Wren", 123_456L);
        Berth leftLying = berth(new BlockPos(9, 64, 9), OVERWORLD, anchor).moored(UUID.randomUUID(), "Tern").letGo();
        for (Berth berth : List.of(plain, set, reserved, leftLying)) {
            registry.register(berth);
        }

        BerthRegistry loaded = reload(registry);
        for (Berth berth : List.of(plain, set, reserved, leftLying)) {
            assertEquals(berth, loaded.byId(berth.id()));
        }
        assertEquals(List.of(plain, reserved, leftLying), loaded.in(OVERWORLD));
        assertEquals(List.of(plain, set, leftLying), loaded.of(anchor));
    }

    @Test
    void aBrokenRecordIsDroppedRatherThanKept() {
        assertNull(Berth.load(new CompoundTag()));
        CompoundTag noPlace = berth(BlockPos.ZERO, OVERWORLD, null).save();
        noPlace.remove("Pos");
        assertNull(Berth.load(noPlace));
    }

    /**
     * A size missing from a save reads as nought. Left at that the record raises it to the smallest slip there
     * is, and every berth saved before a setting existed takes nothing bigger than a rowing boat.
     */
    @Test
    void aSettingMissingFromASaveIsTheOrdinaryOne() {
        CompoundTag tag = berth(BlockPos.ZERO, OVERWORLD, null).save();
        for (String key : List.of("Length", "Width", "Height", "Priority", "Enabled", "Tractor", "Reserve", "State")) {
            tag.remove(key);
        }
        Berth loaded = Berth.load(tag);
        assertEquals(BerthRules.DEFAULT_LENGTH, loaded.length());
        assertEquals(BerthRules.DEFAULT_WIDTH, loaded.width());
        assertEquals(BerthRules.DEFAULT_HEIGHT, loaded.height());
        assertEquals(BerthRules.DEFAULT_PRIORITY, loaded.priority());
        assertTrue(loaded.enabled());
        assertTrue(loaded.tractor());
        assertEquals(BerthReserve.ANYONE, loaded.reserve());
        assertEquals(BerthState.EMPTY, loaded.state());
    }

    /** A state about a ship, with no ship, is a slip that could never be given out again. */
    @Test
    void aStateWithNoShipBehindItIsEmpty() {
        CompoundTag tag = berth(BlockPos.ZERO, OVERWORLD, null).moored(UUID.randomUUID(), "Kestrel").save();
        tag.remove("Ship");
        Berth loaded = Berth.load(tag);
        assertEquals(BerthState.EMPTY, loaded.state());
        assertTrue(loaded.free(0L));
    }

    @Test
    void settingsAreHeldToTheirLimits() {
        Berth wild = berth(BlockPos.ZERO, OVERWORLD, null).withSettings("x".repeat(80), 9_999, -9_999, 0, 1, 100_000,
                0, 367, 0, true, false, true, BerthReserve.ANYONE, null);
        assertEquals(BerthRules.NAME_LENGTH, wild.name().length());
        assertEquals(BerthRules.MAX_OFFSET, wild.offsetX());
        assertEquals(-BerthRules.MAX_OFFSET, wild.offsetY());
        assertEquals(BerthRules.MIN_SIZE, wild.length());
        assertEquals(BerthRules.HARD_MAX_SIZE, wild.width());
        assertEquals(BerthRules.MIN_SIZE, wild.height());
        assertEquals(5, wild.yaw());
        assertEquals(BerthRules.MIN_PRIORITY, wild.priority());
        assertEquals(355, BerthRules.yaw(-5));
        assertEquals(0, BerthRules.yaw(358));
    }

    @Test
    void registeringAgainReplacesAndRemovingForgets() {
        BerthRegistry registry = new BerthRegistry();
        Berth first = berth(new BlockPos(0, 64, 0), OVERWORLD, null);
        registry.register(first);
        Berth held = first.moored(UUID.randomUUID(), "Kestrel");
        registry.register(held);
        assertEquals(1, registry.all().size());
        assertSame(held, registry.byId(first.id()));
        assertSame(held, registry.concerning(held.ship()));
        assertNull(registry.concerning(UUID.randomUUID()));
        assertTrue(registry.remove(first.id()));
        assertFalse(registry.remove(first.id()));
        assertNull(registry.byId(first.id()));
    }

    /** An anchor is a station while it has a berth switched on, and stops being one with its last. */
    @Test
    void anAnchorIsAStationWhileItHasABerthSwitchedOn() {
        UUID anchor = UUID.randomUUID();
        BerthRegistry registry = new BerthRegistry();
        assertFalse(registry.isStation(anchor));
        Berth on = berth(new BlockPos(0, 64, 0), OVERWORLD, anchor);
        registry.register(on);
        assertTrue(registry.isStation(anchor));
        assertFalse(registry.isStation(UUID.randomUUID()));
        assertFalse(registry.isStation(null));
        registry.register(on.withSettings("", 0, 0, 0, 16, 9, 9, 0, 50, false, false, true, BerthReserve.ANYONE, null));
        assertFalse(registry.isStation(anchor), "a station of berths all switched off still refused the ordinary arrival");
        assertEquals(1, registry.forgetAnchor(anchor).size());
        assertTrue(registry.of(anchor).isEmpty());
    }

    @Test
    void aDisbandedNetworkComesOffEveryBerthThatNamedIt() {
        UUID gone = UUID.randomUUID();
        BerthRegistry registry = new BerthRegistry();
        Berth named = berth(new BlockPos(0, 64, 0), OVERWORLD, null)
                .withSettings("", 0, 0, 0, 16, 9, 9, 0, 50, true, false, true, BerthReserve.NETWORK, gone);
        Berth other = berth(new BlockPos(50, 64, 0), OVERWORLD, null);
        registry.register(named);
        registry.register(other);
        assertEquals(1, registry.forgetNetwork(gone).size());
        assertNull(registry.byId(named.id()).network());
        assertEquals(BerthReserve.OWNER, registry.byId(named.id()).reserve());
        assertEquals(other, registry.byId(other.id()));
        assertTrue(registry.forgetNetwork(gone).isEmpty());
    }

    @Test
    void theComparatorReadsFifteenOnlyForAShipHeld() {
        assertEquals(0, BerthState.EMPTY.signal());
        for (BerthState state : BerthState.values()) {
            assertEquals(state == BerthState.MOORED, state.signal() == 15, state.name());
        }
        assertTrue(BerthState.RESERVED.signal() < BerthState.DRAWING.signal());
    }
}
