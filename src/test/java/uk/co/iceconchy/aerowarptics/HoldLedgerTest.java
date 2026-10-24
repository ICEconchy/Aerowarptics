package uk.co.iceconchy.aerowarptics;

import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.hold.HoldKey;
import uk.co.iceconchy.aerowarptics.hold.HoldLedger;
import uk.co.iceconchy.aerowarptics.hold.HoldPurpose;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record of what is held and where ships were last seen, across a save.
 *
 * <p>The ledger is what lets go of a hold whose owner has gone, and what a beacon, a chute or a gate reaches
 * for a ship by. A field that did not survive a save is a hold nothing ever lets go of, or a ship nobody can
 * find - both silent.
 */
class HoldLedgerTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));

    private static final UUID DRIVE = UUID.fromString("00000000-0000-0000-0000-00000000d71e");
    private static final UUID SHIP = UUID.fromString("00000000-0000-0000-0000-0000000005a1");

    private static HoldLedger.ShipRecord ship(UUID id, ResourceKey<Level> dimension, int plotX, int plotZ) {
        return new HoldLedger.ShipRecord(id, dimension, ChunkPos.asLong(plotX, plotZ),
                ChunkPos.asLong(plotX + 3, plotZ + 3), -10.0D, 60.0D, -20.0D, 30.0D, 80.0D, 400.0D, "Kestrel", 99L);
    }

    private static HoldLedger reload(HoldLedger ledger) {
        CompoundTag saved = ledger.save(new CompoundTag(), null);
        return HoldLedger.factory().deserializer().apply(saved, null);
    }

    @Test
    void everyFieldSurvivesASave() {
        HoldLedger ledger = new HoldLedger();
        HoldKey key = new HoldKey(DRIVE, HoldPurpose.RESIDENCY);
        ledger.putHold(new HoldLedger.ShipHold(key, SHIP, NETHER, 12_345L, 1.5D, -2.5D));
        ledger.noteShip(ship(SHIP, NETHER, 1_280_000, 1_280_064));
        ledger.setWakeChunk(NETHER, ChunkPos.asLong(-4, 9));

        HoldLedger loaded = reload(ledger);

        HoldLedger.ShipHold hold = loaded.hold(key, SHIP);
        assertNotNull(hold, "the hold did not survive a save - nothing would ever let it go");
        assertEquals(new HoldLedger.ShipHold(key, SHIP, NETHER, 12_345L, 1.5D, -2.5D), hold);
        assertEquals(ship(SHIP, NETHER, 1_280_000, 1_280_064), loaded.ship(SHIP));
        assertEquals(ChunkPos.asLong(-4, 9), loaded.wakeChunk(NETHER));
        assertNull(loaded.wakeChunk(OVERWORLD));
    }

    /**
     * A plot position finds the ship it is aboard, in its own dimension only.
     *
     * <p>All a beacon or a chute knows about a machine aboard a ship is a plot position. Plot grids in every
     * dimension start from the same slot, so the same position in another dimension is a different ship.
     */
    @Test
    void aPlotPositionFindsItsShipInItsOwnDimension() {
        HoldLedger ledger = new HoldLedger();
        ledger.noteShip(ship(SHIP, OVERWORLD, 1_280_000, 1_280_064));
        BlockPos aboard = new BlockPos((1_280_000 + 2) * 16 + 5, 90, (1_280_064 + 1) * 16);
        BlockPos beside = new BlockPos((1_280_000 + 4) * 16, 90, 1_280_064 * 16);

        assertEquals(SHIP, ledger.shipAt(OVERWORLD, aboard).ship());
        assertNull(ledger.shipAt(OVERWORLD, beside), "a position past the plot found the ship");
        assertNull(ledger.shipAt(NETHER, aboard), "a plot position found a ship in another dimension");
    }

    /** Only holds still within their lease keep a level's wake chunk at load; a lapsed one does not. */
    @Test
    void onlyALiveHoldKeepsALevelAwake() {
        HoldLedger ledger = new HoldLedger();
        ledger.putHold(new HoldLedger.ShipHold(new HoldKey(DRIVE, HoldPurpose.FLIGHT), SHIP, NETHER, 500L, 0.0D, 0.0D));
        assertTrue(ledger.anyLiveHoldIn(NETHER, 499L));
        assertFalse(ledger.anyLiveHoldIn(NETHER, 500L), "a lapsed hold kept a level awake");
        assertFalse(ledger.anyLiveHoldIn(OVERWORLD, 0L), "a hold in one level kept another awake");
    }

    /** A ship taken apart takes its record and every hold on it with it. */
    @Test
    void forgettingAShipForgetsItsHolds() {
        HoldLedger ledger = new HoldLedger();
        ledger.noteShip(ship(SHIP, OVERWORLD, 0, 0));
        ledger.putHold(new HoldLedger.ShipHold(new HoldKey(DRIVE, HoldPurpose.FLIGHT), SHIP, OVERWORLD, 9L, 0.0D, 0.0D));
        ledger.putHold(new HoldLedger.ShipHold(new HoldKey(DRIVE, HoldPurpose.RESIDENCY), SHIP, OVERWORLD, 9L, 0.0D, 0.0D));
        ledger.forgetShip(SHIP);
        assertNull(ledger.ship(SHIP));
        assertTrue(ledger.holdsOn(SHIP).isEmpty());
    }

    /**
     * A ship noted again where it already was does not mark the ledger for saving.
     *
     * <p>Every drive, chute and gate aboard a ship notes it about once a second. If each of those dirtied the
     * ledger, the world would write it out at every autosave for no change anybody could see.
     */
    @Test
    void notingAShipThatHasNotMovedChangesNothing() {
        HoldLedger ledger = new HoldLedger();
        ledger.noteShip(ship(SHIP, OVERWORLD, 0, 0));
        ledger.setDirty(false);
        HoldLedger.ShipRecord same = ship(SHIP, OVERWORLD, 0, 0);
        ledger.noteShip(new HoldLedger.ShipRecord(same.ship(), same.dimension(), same.plotMin(), same.plotMax(),
                same.minX() + 0.25D, same.minY(), same.minZ(), same.maxX(), same.maxY(), same.maxZ(), same.name(), 5_000L));
        assertFalse(ledger.isDirty(), "a ship that moved a quarter of a block was worth a save");
        ledger.noteShip(new HoldLedger.ShipRecord(same.ship(), same.dimension(), same.plotMin(), same.plotMax(),
                same.minX() + 40.0D, same.minY(), same.minZ(), same.maxX() + 40.0D, same.maxY(), same.maxZ(), same.name(), 5_001L));
        assertTrue(ledger.isDirty(), "a ship that moved forty blocks was not noted");
    }

    /**
     * The key Sable saves with this mod's ticket reads back as itself, and one it cannot read is skipped.
     *
     * <p>Sable parses the key when the level loads; a key that no longer parses - a purpose some later
     * version dropped - makes Sable log and skip the ticket, which is the right end for a hold nobody can
     * renew. It must not throw.
     */
    @Test
    void theTicketKeyRoundTripsAndABadOneIsAnErrorNotACrash() {
        HoldKey key = new HoldKey(DRIVE, HoldPurpose.FLIGHT);
        Tag encoded = HoldKey.CODEC.encodeStart(NbtOps.INSTANCE, key).getOrThrow();
        assertEquals(key, HoldKey.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());

        CompoundTag unknown = ((CompoundTag) encoded).copy();
        unknown.putString("purpose", "teleport_everything");
        DataResult<HoldKey> parsed = HoldKey.CODEC.parse(NbtOps.INSTANCE, unknown);
        assertTrue(parsed.error().isPresent(), "an unknown purpose parsed as something");
    }
}
