package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Anchor registration, lookup, renaming and persistence. */
class WarpAnchorRegistryTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

    private static WarpAnchor anchor(String name, BlockPos pos) {
        return anchor(UUID.randomUUID(), name, pos, OVERWORLD);
    }

    private static WarpAnchor anchor(UUID id, String name, BlockPos pos, ResourceKey<Level> dimension) {
        return new WarpAnchor(id, name, dimension, pos, null, "", WarpAnchorAccess.PUBLIC, null, true, 6);
    }

    /** Round-trips a registry through NBT the way the world save does. */
    private static WarpAnchorRegistry reload(WarpAnchorRegistry registry) {
        CompoundTag saved = registry.save(new CompoundTag(), null);
        return WarpAnchorRegistry.factory().deserializer().apply(saved, null);
    }

    // ------------------------------------------------------------ registration

    @Test
    void anchorsAreFoundByIdAndByName() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor brasshaven = anchor("Brasshaven", new BlockPos(1250, 110, -8400));
        registry.register(brasshaven);

        assertEquals(brasshaven, registry.byId(brasshaven.id()));
        assertEquals(brasshaven, registry.byName("Brasshaven"));
        assertEquals(brasshaven, registry.byName("  brasshaven "), "lookup is trimmed and case-insensitive");
        assertNull(registry.byName("Ironreach"));
    }

    @Test
    void registeringTheSameIdTwiceUpdatesRatherThanDuplicates() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        UUID id = UUID.randomUUID();
        registry.register(anchor(id, "Skyport", new BlockPos(0, 64, 0), OVERWORLD));
        registry.register(anchor(id, "Skyport Alpha", new BlockPos(10, 70, 10), OVERWORLD));

        assertEquals(1, registry.all().size());
        assertEquals("Skyport Alpha", registry.byId(id).name());
        assertNull(registry.byName("Skyport"), "the stale name index entry is dropped");
        assertNotNull(registry.byName("Skyport Alpha"));
    }

    @Test
    void removingAnAnchorClearsBothIndexes() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor a = anchor("New Avalon", new BlockPos(4, 5, 6));
        registry.register(a);

        assertTrue(registry.remove(a.id()));
        assertFalse(registry.remove(a.id()), "removing twice reports nothing was there");
        assertNull(registry.byId(a.id()));
        assertNull(registry.byName("New Avalon"));
    }

    // ---------------------------------------------------------------- renaming

    @Test
    void renamingRefusesToCollideWithAnotherAnchor() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor a = anchor("Brasshaven", new BlockPos(0, 0, 0));
        WarpAnchor b = anchor("Ironreach", new BlockPos(1, 0, 0));
        registry.register(a);
        registry.register(b);

        assertFalse(registry.rename(b.id(), "brasshaven"), "names are unique regardless of case");
        assertEquals("Ironreach", registry.byId(b.id()).name());
        assertTrue(registry.rename(b.id(), "Ironreach"), "renaming to its own name is a no-op, not a clash");
        assertTrue(registry.rename(b.id(), "Skyport Alpha"));
        assertEquals(b.id(), registry.byName("Skyport Alpha").id());
        assertNull(registry.byName("Ironreach"));
    }

    @Test
    void blankNamesAreAllowedAndAreNotIndexed() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor a = anchor("", new BlockPos(2, 3, 4));
        WarpAnchor b = anchor("", new BlockPos(5, 6, 7));
        registry.register(a);
        registry.register(b);

        assertEquals(2, registry.all().size());
        assertNull(registry.byName(""));
        assertTrue(a.displayName().startsWith("Anchor-"), "an unnamed anchor still shows something usable");
    }

    @Test
    void nameCollisionIsReportedIndependentlyOfTheAnchorItself() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor a = anchor("Brasshaven", new BlockPos(0, 0, 0));
        registry.register(a);

        assertTrue(registry.isNameTaken("Brasshaven", UUID.randomUUID()));
        assertFalse(registry.isNameTaken("Brasshaven", a.id()));
        assertFalse(registry.isNameTaken("Somewhere Else", null));
    }

    // ------------------------------------------------------------- persistence

    @Test
    void anchorsSurviveASaveAndReload() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        UUID owner = UUID.randomUUID();
        WarpAnchor a = new WarpAnchor(UUID.randomUUID(), "Brasshaven", OVERWORLD, new BlockPos(1250, 110, -8400),
                owner, "Pilot", WarpAnchorAccess.NETWORK, UUID.randomUUID(), false, 40);
        WarpAnchor b = anchor(UUID.randomUUID(), "Ashfall", new BlockPos(-40, 32, 90), NETHER);
        registry.register(a);
        registry.register(b);

        WarpAnchorRegistry reloaded = reload(registry);

        assertEquals(2, reloaded.all().size());
        WarpAnchor restored = reloaded.byId(a.id());
        assertEquals(a, restored, "every field must round-trip");
        assertEquals(NETHER, reloaded.byId(b.id()).dimension(), "dimensions other than the overworld persist");
        assertEquals(a.id(), reloaded.byName("Brasshaven").id(), "the name index is rebuilt on load");
    }

    /**
     * An anchor saved before arrival heights were adjustable comes in where every anchor used to.
     *
     * <p>Without the fallback a missing key reads as zero, and every existing anchor on an upgraded
     * server would quietly start bringing ships in with their keels on the block instead of six blocks
     * clear of it.
     */
    @Test
    void anAnchorSavedBeforeArrivalHeightsKeepsTheOldHeight() {
        CompoundTag old = anchor("Brasshaven", new BlockPos(1, 2, 3)).withArrivalHeight(40).save();
        old.remove("ArrivalHeight");

        WarpAnchor loaded = WarpAnchor.load(old);

        assertNotNull(loaded);
        assertEquals(ArrivalHeight.serverDefault(), loaded.arrivalHeight());
        assertEquals(ArrivalHeight.FALLBACK, loaded.arrivalHeight(),
                "with no config loaded, the default is the shipped arrivalGroundBuffer");
    }

    /** Every other field is kept when the height changes, and the height is kept when they do. */
    @Test
    void theArrivalHeightSurvivesEveryOtherEdit() {
        WarpAnchor anchor = anchor("Brasshaven", new BlockPos(1, 2, 3)).withArrivalHeight(40);
        WarpAnchor edited = anchor.withName("Ironreach").withAccess(WarpAnchorAccess.PRIVATE)
                .withNetwork(UUID.randomUUID()).withEnabled(false).withPosition(NETHER, new BlockPos(9, 9, 9));

        assertEquals(40, edited.arrivalHeight());
        assertEquals("Ironreach", edited.withArrivalHeight(12).name());
    }

    /** A record never holds a height the arrival search would start inside the block from. */
    @Test
    void anAnchorNeverHoldsANegativeHeight() {
        assertEquals(0, anchor("Brasshaven", BlockPos.ZERO).withArrivalHeight(-5).arrivalHeight());
    }

    @Test
    void corruptedRecordsAreDroppedInsteadOfFailingTheLoad() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor good = anchor("Brasshaven", new BlockPos(1, 2, 3));
        registry.register(good);

        CompoundTag saved = registry.save(new CompoundTag(), null);
        ListTag anchors = saved.getList("Anchors", 10);
        anchors.add(new CompoundTag());                       // missing everything
        CompoundTag badDimension = good.save();
        badDimension.putUUID("Id", UUID.randomUUID());
        badDimension.putString("Dimension", "not a resource location");
        anchors.add(badDimension);

        WarpAnchorRegistry reloaded = WarpAnchorRegistry.factory().deserializer().apply(saved, null);

        assertEquals(1, reloaded.all().size());
        assertNotNull(reloaded.byId(good.id()));
    }

    @Test
    void duplicateIdsOnDiskDoNotCorruptTheIndex() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        WarpAnchor good = anchor("Brasshaven", new BlockPos(1, 2, 3));
        registry.register(good);

        CompoundTag saved = registry.save(new CompoundTag(), null);
        saved.getList("Anchors", 10).add(good.save());

        WarpAnchorRegistry reloaded = WarpAnchorRegistry.factory().deserializer().apply(saved, null);

        assertEquals(1, reloaded.all().size());
        assertEquals(good, reloaded.byId(good.id()));
    }

    // ---------------------------------------------------------------- networks

    /**
     * A member put out of a network takes their own anchors off it and nobody else's, in the record,
     * whether or not the blocks are loaded - and an anchor that was open to the network is shut, not opened.
     */
    @Test
    void anchorsComeOffANetworkByOwnerOrAllTogether() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        UUID network = UUID.randomUUID();
        UUID pilot = UUID.randomUUID();
        UUID mate = UUID.randomUUID();
        WarpAnchor pilots = new WarpAnchor(UUID.randomUUID(), "Pilot's", OVERWORLD, BlockPos.ZERO,
                pilot, "Pilot", WarpAnchorAccess.NETWORK, network, true, 6);
        WarpAnchor mates = new WarpAnchor(UUID.randomUUID(), "Mate's", OVERWORLD, new BlockPos(1, 0, 0),
                mate, "Mate", WarpAnchorAccess.NETWORK, network, true, 6);
        WarpAnchor matesPublic = new WarpAnchor(UUID.randomUUID(), "Mate's Dock", OVERWORLD, new BlockPos(2, 0, 0),
                mate, "Mate", WarpAnchorAccess.PUBLIC, network, true, 6);
        WarpAnchor elsewhere = new WarpAnchor(UUID.randomUUID(), "Elsewhere", OVERWORLD, new BlockPos(3, 0, 0),
                mate, "Mate", WarpAnchorAccess.NETWORK, UUID.randomUUID(), true, 6);
        for (WarpAnchor anchor : new WarpAnchor[]{pilots, mates, matesPublic, elsewhere}) {
            registry.register(anchor);
        }

        int off = registry.detach(anchor -> network.equals(anchor.network()) && anchor.isOwnedBy(mate)).size();

        assertEquals(2, off);
        assertNull(registry.byId(mates.id()).network());
        assertEquals(WarpAnchorAccess.PRIVATE, registry.byId(mates.id()).access(),
                "an anchor open to its network was left open to it, or opened, as its owner left");
        assertEquals(WarpAnchorAccess.PUBLIC, registry.byId(matesPublic.id()).access());
        assertEquals(pilots, registry.byId(pilots.id()), "the owner's anchor came off with the member's");
        assertEquals(elsewhere, registry.byId(elsewhere.id()), "an anchor on another network was touched");
        assertEquals(mates.id(), registry.byName("Mate's").id(), "coming off a network lost an anchor its name");

        assertEquals(1, registry.detach(anchor -> network.equals(anchor.network())).size());
        assertTrue(registry.detach(anchor -> network.equals(anchor.network())).isEmpty());
        assertEquals(WarpAnchorAccess.PRIVATE, reload(registry).byId(pilots.id()).access());
    }

    /**
     * A save from before networks were real holds a label nothing ever read. It loads, with no network:
     * the anchor's own block still has the label and adopts it when it next loads.
     */
    @Test
    void anAnchorSavedWithAnOldNetworkLabelLoadsWithNoNetwork() {
        CompoundTag old = anchor("Brasshaven", new BlockPos(1, 2, 3)).save();
        old.putString("Network", "trade-route");

        WarpAnchor loaded = WarpAnchor.load(old);

        assertNotNull(loaded);
        assertNull(loaded.network());
        assertEquals("Brasshaven", loaded.name());
    }

    @Test
    void anEmptyRegistryRoundTripsCleanly() {
        WarpAnchorRegistry reloaded = reload(new WarpAnchorRegistry());
        assertTrue(reloaded.all().isEmpty());
    }

    @Test
    void ownershipCountingIsPerPlayer() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        UUID pilot = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        registry.register(new WarpAnchor(UUID.randomUUID(), "A", OVERWORLD, BlockPos.ZERO,
                pilot, "Pilot", WarpAnchorAccess.PUBLIC, null, true, 6));
        registry.register(new WarpAnchor(UUID.randomUUID(), "B", OVERWORLD, new BlockPos(1, 0, 0),
                pilot, "Pilot", WarpAnchorAccess.PUBLIC, null, true, 6));
        registry.register(new WarpAnchor(UUID.randomUUID(), "C", OVERWORLD, new BlockPos(2, 0, 0),
                other, "Other", WarpAnchorAccess.PUBLIC, null, true, 6));

        assertEquals(2, registry.countOwnedBy(pilot));
        assertEquals(1, registry.countOwnedBy(other));
        assertEquals(0, registry.countOwnedBy(UUID.randomUUID()));
    }

    @Test
    void filteringSortsByDisplayNameCaseInsensitively() {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        registry.register(anchor("zephyr", new BlockPos(0, 0, 0)));
        registry.register(anchor("Ashfall", new BlockPos(1, 0, 0)));
        registry.register(anchor("brasshaven", new BlockPos(2, 0, 0)));

        var names = registry.filtered(a -> true).stream().map(WarpAnchor::displayName).toList();
        assertEquals(java.util.List.of("Ashfall", "brasshaven", "zephyr"), names);
    }
}
