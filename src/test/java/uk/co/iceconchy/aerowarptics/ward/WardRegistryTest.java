package uk.co.iceconchy.aerowarptics.ward;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.plotter.PlotterSurvey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server remembers of a Rift Ward while its block is away, and what a stranger's scan is shown of the
 * ground under one.
 */
class WardRegistryTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

    private static RiftWard ward(BlockPos pos, ResourceKey<Level> dimension, UUID owner, UUID network) {
        return new RiftWard(UUID.randomUUID(), dimension, pos, owner, "Ada",
                network == null ? WardMode.BLOCK_ALL : WardMode.ALLOW_NETWORKS,
                network == null ? List.of() : List.of(network), false, true, 48, 12_345.5D, 987_654L, true);
    }

    private static WardRegistry reload(WardRegistry registry) {
        CompoundTag saved = registry.save(new CompoundTag(), null);
        return WardRegistry.factory().deserializer().apply(saved, null);
    }

    // ------------------------------------------------------------------ the record

    /** Everything that decides what an unloaded ward does has to come back from disk exactly as it went. */
    @Test
    void aWardComesBackFromDiskAsItWent() {
        WardRegistry registry = new WardRegistry();
        RiftWard kept = ward(new BlockPos(120, 70, -3300), OVERWORLD, UUID.randomUUID(), UUID.randomUUID());
        RiftWard unowned = new RiftWard(UUID.randomUUID(), NETHER, new BlockPos(-4, 40, 9), null, "",
                WardMode.BLOCK_NETWORKS, List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), true, false, 16,
                0.0D, 5L, false);
        registry.register(kept);
        registry.register(unowned);

        WardRegistry loaded = reload(registry);
        assertEquals(kept, loaded.byId(kept.id()));
        assertEquals(unowned, loaded.byId(unowned.id()));
        assertEquals(List.of(kept), loaded.in(OVERWORLD));
        assertEquals(List.of(unowned), loaded.in(NETHER));
    }

    @Test
    void aBrokenRecordIsDroppedRatherThanKept() {
        assertNull(RiftWard.load(new CompoundTag()));
        CompoundTag noPlace = ward(BlockPos.ZERO, OVERWORLD, null, null).save();
        noPlace.remove("Pos");
        assertNull(RiftWard.load(noPlace));
    }

    /** A ward saved with a radius narrower than any ward may hold comes back with a field, not a point. */
    @Test
    void aRecordNeverLoadsNarrowerThanAWardMayBe() {
        CompoundTag tag = ward(BlockPos.ZERO, OVERWORLD, null, null).save();
        tag.putInt("Radius", 0);
        assertEquals(WardRules.MIN_RADIUS, RiftWard.load(tag).radius());
    }

    @Test
    void registeringAgainReplacesAndRemovingForgets() {
        WardRegistry registry = new WardRegistry();
        RiftWard first = ward(new BlockPos(0, 64, 0), OVERWORLD, null, null);
        registry.register(first);
        RiftWard counted = first.counted(40.0D, 1_000_000L, false);
        registry.register(counted);
        assertEquals(1, registry.all().size());
        assertEquals(counted, registry.byId(first.id()));
        assertFalse(registry.byId(first.id()).standing());
        assertTrue(registry.remove(first.id()));
        assertFalse(registry.remove(first.id()));
        assertNull(registry.byId(first.id()));
    }

    /**
     * A network disbanded comes off every ward that named it, and off no other; what each ward names besides is
     * left as it was, in the order it was named.
     */
    @Test
    void aDisbandedNetworkComesOffEveryWardThatNamedIt() {
        UUID gone = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        WardRegistry registry = new WardRegistry();
        RiftWard both = new RiftWard(UUID.randomUUID(), OVERWORLD, new BlockPos(0, 64, 0), UUID.randomUUID(), "Ada",
                WardMode.BLOCK_NETWORKS, List.of(kept, gone), false, true, 32, 100.0D, 1L, true);
        RiftWard other = ward(new BlockPos(900, 64, 0), OVERWORLD, UUID.randomUUID(), kept);
        registry.register(both);
        registry.register(other);

        List<RiftWard> off = registry.forget(gone);
        assertEquals(1, off.size());
        assertEquals(List.of(kept), registry.byId(both.id()).networks());
        assertEquals(WardMode.BLOCK_NETWORKS, registry.byId(both.id()).mode(), "losing a network changed a ward's mode");
        assertEquals(List.of(kept), registry.byId(other.id()).networks());
        assertTrue(registry.forget(gone).isEmpty());
    }

    /**
     * A ward saved while it had one network box, before modes: with a network in it, it let that network through
     * and turned everyone else, which is Allow; with none, it let its owner through alone, which is Block all.
     */
    @Test
    void aWardSavedBeforeModesTurnsWhomItTurnedThen() {
        UUID network = UUID.randomUUID();
        CompoundTag old = ward(BlockPos.ZERO, OVERWORLD, UUID.randomUUID(), null).save();
        old.remove("Mode");
        old.remove("Networks");
        old.remove("SelfTest");
        RiftWard none = RiftWard.load(old.copy());
        assertEquals(WardMode.BLOCK_ALL, none.mode());
        assertTrue(none.networks().isEmpty());
        assertFalse(none.selfTest());

        old.putUUID("NetworkId", network);
        RiftWard one = RiftWard.load(old);
        assertEquals(WardMode.ALLOW_NETWORKS, one.mode());
        assertEquals(List.of(network), one.networks());
    }

    /** Who a ward's own facts let through: its lists are asked of the networks, and nobody is in a network unnamed. */
    @Test
    void aWardAsksTheNetworksItNamesAndNoOthers() {
        UUID named = UUID.randomUUID();
        UUID unnamed = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        uk.co.iceconchy.aerowarptics.warpnet.Membership memberships = (network, player) ->
                (named.equals(network) && member.equals(player)) || (unnamed.equals(network) && outsider.equals(player));
        RiftWard allow = new RiftWard(UUID.randomUUID(), OVERWORLD, BlockPos.ZERO, owner, "Ada", WardMode.ALLOW_NETWORKS,
                List.of(named), false, true, 32, 1.0D, 1L, true);
        assertTrue(allow.passes(member, memberships));
        assertFalse(allow.passes(outsider, memberships), "a member of a network the ward does not name was let through");
        assertFalse(allow.passes(null, memberships), "a rift nobody can be named for passed a ward that allows only some");
        assertTrue(allow.passes(owner, memberships));

        RiftWard block = new RiftWard(UUID.randomUUID(), OVERWORLD, BlockPos.ZERO, owner, "Ada", WardMode.BLOCK_NETWORKS,
                List.of(named), true, true, 32, 1.0D, 1L, true);
        assertFalse(block.passes(member, memberships));
        assertTrue(block.passes(outsider, memberships));
        assertTrue(block.passes(null, memberships), "nobody's rift is not one of the networks a ward shuts out");
        assertTrue(block.passes(owner, memberships), "an owner turned too is still not in the network being shut out");
    }

    // ------------------------------------------------------------------ scans

    private static PlotterSurvey table(int radius) {
        int size = 2 * radius + 1;
        byte[] colours = new byte[size * size];
        short[] heights = new short[size * size];
        for (int i = 0; i < heights.length; i++) {
            colours[i] = (byte) (4 * (1 + i % 50) + 2);
            heights[i] = (short) (60 + i % 30);
        }
        return new PlotterSurvey(1_000, -2_000, radius, 70, colours, heights);
    }

    /** Inside the field is noise; outside it is the ground, to the block. */
    @Test
    void aStrangersTableShowsNoiseInsideTheFieldAndTheGroundOutsideIt() {
        PlotterSurvey ground = table(40);
        WardField.Circle field = new WardField.Circle(1_010.5D, -2_005.5D, 18.0D);
        List<WardField.Circle> touched = new ArrayList<>();
        PlotterSurvey shown = WardScans.scramble(ground, List.of(field), 77L, touched);

        assertEquals(List.of(field), touched);
        int changed = 0;
        int inside = 0;
        for (int z = 0; z < ground.size(); z++) {
            for (int x = 0; x < ground.size(); x++) {
                boolean held = field.contains(ground.worldX(x) + 0.5D, ground.worldZ(z) + 0.5D);
                boolean same = ground.height(x, z) == shown.height(x, z) && ground.colour(x, z) == shown.colour(x, z);
                if (!held) {
                    assertTrue(same, "ground outside the field was changed at " + x + ", " + z);
                } else {
                    inside++;
                    changed += same ? 0 : 1;
                    assertTrue(shown.known(x, z), "interference reads as ground nobody could see, which is a hole");
                }
            }
        }
        assertTrue(inside > 900, "the field covers too little of the table to test");
        assertTrue(changed > inside * 0.9D, "only " + changed + " of " + inside + " columns in the field were scrambled");
        // The table as it was handed in is not written on: a member is sent the same arrays.
        assertEquals(60, ground.height(0, 0));
    }

    @Test
    void aSecondScanShowsWhatTheFirstDid() {
        PlotterSurvey ground = table(30);
        List<WardField.Circle> fields = List.of(new WardField.Circle(1_000.0D, -2_000.0D, 22.0D));
        PlotterSurvey once = WardScans.scramble(ground, fields, 5L, new ArrayList<>());
        PlotterSurvey twice = WardScans.scramble(ground, fields, 5L, new ArrayList<>());
        assertArrayEquals(once.heights(), twice.heights());
        assertArrayEquals(once.colours(), twice.colours());
    }

    @Test
    void aScanThatReadsNoFieldIsHandedBackUntouched() {
        PlotterSurvey ground = table(20);
        List<WardField.Circle> touched = new ArrayList<>();
        assertSame(ground, WardScans.scramble(ground, List.of(new WardField.Circle(5_000.0D, 5_000.0D, 64.0D)), 5L, touched));
        assertTrue(touched.isEmpty());
        assertSame(ground, WardScans.scramble(ground, List.of(), 5L, touched));
    }

    /** A Rift Probe's sounding is scrambled the same way, and the height it will hand a drive is left as it was read. */
    @Test
    void aStrangersSoundingIsScrambledUnderTheFieldAndKeepsItsFix() {
        int size = 2 * DestinationSurvey.RADIUS / DestinationSurvey.STEP + 1;
        byte[] colours = new byte[size * size];
        byte[] relief = new byte[size * size];
        java.util.Arrays.fill(colours, (byte) 30);
        BlockPos centre = new BlockPos(300, 64, 300);
        DestinationSurvey ground = new DestinationSurvey(DestinationSurvey.RADIUS, DestinationSurvey.STEP, centre, 71,
                colours, relief);
        WardField.Circle field = new WardField.Circle(300.5D, 300.5D, 20.0D);
        List<WardField.Circle> touched = new ArrayList<>();
        DestinationSurvey shown = WardScans.scramble(ground, List.of(field), 3L, touched);

        assertEquals(List.of(field), touched);
        assertEquals(71, shown.groundY());
        assertEquals(centre, shown.centre());
        int middle = (size / 2) * size + size / 2;
        assertNotEquals(0, shown.relief()[middle] | shown.relief()[middle + 1] | shown.relief()[middle + 2],
                "the ground under the middle of the field came back flat, as it was");
        assertEquals(30, shown.colours()[0], "the corner of the sounding is outside the field");
        assertEquals(0, shown.relief()[0]);
        assertEquals(30, ground.colours()[middle], "the sounding handed in was written on");
    }
}
