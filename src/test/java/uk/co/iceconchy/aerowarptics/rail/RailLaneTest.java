package uk.co.iceconchy.aerowarptics.rail;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lane's record, and the registry that finds one from each of its ends.
 */
class RailLaneTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    private static final ResourceLocation TRACK = ResourceLocation.fromNamespaceAndPath("create", "track");

    private static RailLane lane(UUID gate, UUID far, int slot) {
        return RailLane.laid(gate, OVERWORLD, new BlockPos(2, 64, -1 - slot), new BlockPos(2, 64, 0), Direction.SOUTH,
                slot, 160, TRACK, far);
    }

    @Test
    void aLaneSavesAndLoadsWhole() {
        RailLane laid = lane(UUID.randomUUID(), UUID.randomUUID(), 3)
                .withFarEnd(NETHER, new BlockPos(10, 40, 7), new BlockPos(10, 40, 8), Direction.SOUTH);
        CompoundTag tag = laid.save();
        assertEquals(laid, RailLane.load(tag));
        assertEquals(RailLaneState.READY, RailLane.load(tag).state());
    }

    @Test
    void aReadyLaneWithoutAFarEndLoadsAsPending() {
        CompoundTag tag = lane(UUID.randomUUID(), UUID.randomUUID(), 0).withState(RailLaneState.READY).save();
        RailLane loaded = RailLane.load(tag);
        assertNotNull(loaded);
        assertEquals(RailLaneState.FAR_PENDING, loaded.state());
        assertFalse(loaded.ready());
    }

    @Test
    void aBrokenRecordIsSkippedNotThrown() {
        assertNull(RailLane.load(new CompoundTag()));
    }

    @Test
    void reRoutingForgetsTheOldFarEnd() {
        UUID other = UUID.randomUUID();
        RailLane ready = lane(UUID.randomUUID(), UUID.randomUUID(), 0)
                .withFarEnd(OVERWORLD, new BlockPos(1, 2, 3), new BlockPos(1, 2, 4), Direction.SOUTH);
        RailLane turned = ready.routedTo(other);
        assertEquals(other, turned.far());
        assertNull(turned.farStub());
        assertEquals(RailLaneState.FAR_PENDING, turned.state());
        assertEquals(RailLaneState.NO_ROUTE, ready.routedTo(null).state());
        assertEquals(ready.track(), turned.track(), "the near end and the corridor stay");
        assertEquals(ready.slot(), turned.slot());
    }

    @Test
    void aLaneJoinsItsTwoGatesEitherWayRound() {
        UUID gate = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        RailLane lane = lane(gate, far, 0);
        assertTrue(lane.joins(gate, far));
        assertTrue(lane.joins(far, gate));
        assertFalse(lane.joins(gate, UUID.randomUUID()));
        assertFalse(lane.joins(gate, null));
    }

    @Test
    void theRegistryFindsALaneFromEitherTrack() {
        RailLaneRegistry registry = new RailLaneRegistry();
        UUID gate = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        RailLane laid = lane(gate, far, 0);
        registry.put(laid);
        assertEquals(laid, registry.atTrack(OVERWORLD, laid.track()));
        assertNull(registry.atTrack(NETHER, laid.track()), "the same block in another dimension is another place");
        assertEquals(List.of(laid), registry.ownedBy(gate));
        assertEquals(List.of(laid), registry.comingOutOf(far));
        assertEquals(laid, registry.inSlot(0));
        assertEquals(Set.of(0), registry.usedSlots());

        RailLane ready = laid.withFarEnd(NETHER, new BlockPos(9, 9, 9), new BlockPos(9, 9, 10), Direction.SOUTH);
        registry.put(ready);
        assertEquals(ready, registry.atTrack(NETHER, new BlockPos(9, 9, 10)));
        assertEquals(Set.of(new BlockPos(2, 64, 0)), registry.laneCells(gate));
        assertEquals(Set.of(new BlockPos(9, 9, 9)), registry.laneCells(far));

        UUID elsewhere = UUID.randomUUID();
        registry.put(ready.routedTo(elsewhere));
        assertNull(registry.atTrack(NETHER, new BlockPos(9, 9, 10)), "the old stub is forgotten when re-routed");
        assertTrue(registry.comingOutOf(far).isEmpty());
        assertEquals(1, registry.comingOutOf(elsewhere).size());

        registry.remove(laid.id());
        assertNull(registry.atTrack(OVERWORLD, laid.track()));
        assertTrue(registry.ownedBy(gate).isEmpty());
        assertTrue(registry.usedSlots().isEmpty(), "a freed slot is free");
    }

    /** A gate's cells are its own lanes' and those laid out of it - only once laid. */
    @Test
    void aFarCellCountsOnlyOnceLaid() {
        RailLaneRegistry registry = new RailLaneRegistry();
        UUID gate = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        RailLane pending = lane(gate, far, 0);
        registry.put(pending);
        assertTrue(registry.laneCells(far).isEmpty());
        registry.put(pending.withFarEnd(OVERWORLD, new BlockPos(5, 5, 5), new BlockPos(5, 5, 6), Direction.SOUTH)
                .withState(RailLaneState.FAR_BLOCKED));
        assertTrue(registry.laneCells(far).isEmpty());
    }

    @Test
    void theRegistrySavesAndLoadsItsLanesAndWhatItOwes() throws Exception {
        RailLaneRegistry registry = new RailLaneRegistry();
        RailLane one = lane(UUID.randomUUID(), UUID.randomUUID(), 0);
        RailLane two = lane(UUID.randomUUID(), null, 1);
        registry.put(one);
        registry.put(two);
        RailLaneRegistry.Cleanup owed = new RailLaneRegistry.Cleanup(NETHER, new BlockPos(1, 2, 3), new BlockPos(1, 2, 2));
        registry.owe(owed);
        registry.owe(owed);
        CompoundTag tag = registry.save(new CompoundTag(), null);

        Method load = RailLaneRegistry.class.getDeclaredMethod("load", CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        RailLaneRegistry loaded = (RailLaneRegistry) load.invoke(null, tag, null);
        assertEquals(2, loaded.all().size());
        assertEquals(one, loaded.atTrack(OVERWORLD, one.track()));
        assertEquals(List.of(owed), loaded.cleanups(), "owed once, however often it was owed");
        loaded.settled(owed);
        assertTrue(loaded.cleanups().isEmpty());
    }

    @Test
    void aRailSummaryCrossesTheWireAndCannotBeInflated() {
        RailSummary summary = new RailSummary(3, 2, 1, 4, 5, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        summary.encode(buf);
        assertEquals(summary, RailSummary.decode(buf));

        RailSummary wild = new RailSummary(-5, 1_000_000, 7, 8, 9, false);
        assertEquals(0, wild.lanes());
        assertEquals(0, wild.ready(), "never more lanes laid than there are");
        assertTrue(new RailSummary(1_000_000, 0, 0, 0, 0, false).lanes() < 1_000);
        assertFalse(RailSummary.NONE.any());
        assertTrue(new RailSummary(0, 0, 1, 0, 0, false).any());
    }
}
