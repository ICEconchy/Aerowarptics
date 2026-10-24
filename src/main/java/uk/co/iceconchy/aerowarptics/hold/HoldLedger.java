package uk.co.iceconchy.aerowarptics.hold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every ship this mod is holding, and where every ship it might need to reach was last seen.
 *
 * <p>World data on the overworld, like the other registries, so it is readable whether or not any ship is
 * loaded - which is the entire point of both halves.
 *
 * <p><strong>Holds.</strong> Sable persists its force-load tickets itself, and a ticket nobody lets go of
 * keeps a ship loaded forever. So each one this mod takes is written here too, with a lease: its owner
 * renews it while it wants it, and {@link HoldUpkeep} lets go of whatever stops being renewed. A drive
 * broken by something that bypassed its removal hook, or a ship whose drive was lost in a crash, cannot
 * leave a ship loaded for good - the lease simply runs out. The same record is what the load-time check
 * of the wake chunks is judged against.
 *
 * <p><strong>Ships.</strong> A ship Sable has unloaded is not anywhere a lookup can find it: its blocks
 * are on disk, filed under whichever ground chunk it was over. To bring one back, something has to load
 * that ground, which means knowing where it was. Every ship carrying a drive is noted here as it is held,
 * and again by {@link ShipWatch} at the moment Sable unloads it; its plot range is kept so a plot position
 * - which is all a beacon or a chute knows about a machine aboard - can be turned back into the ship.
 * Sable gives a ship back the same plot slot when it reloads, so that range stays true.
 */
public class HoldLedger extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_holds";

    /**
     * One ship held by one owner for one purpose.
     *
     * @param leaseUntil overworld game time the hold lapses at unless renewed
     * @param originX    where a residency hold counts wandering from
     */
    public record ShipHold(HoldKey key, UUID ship, ResourceKey<Level> dimension, long leaseUntil,
                           double originX, double originZ) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Owner", key.owner());
            tag.putString("Purpose", key.purpose().id());
            tag.putUUID("Ship", ship);
            tag.putString("Dimension", dimension.location().toString());
            tag.putLong("Lease", leaseUntil);
            tag.putDouble("OriginX", originX);
            tag.putDouble("OriginZ", originZ);
            return tag;
        }

        @Nullable
        static ShipHold load(CompoundTag tag) {
            HoldPurpose purpose = HoldPurpose.byId(tag.getString("Purpose"));
            ResourceKey<Level> dimension = parseDimension(tag.getString("Dimension"));
            if (purpose == null || dimension == null || !tag.hasUUID("Owner") || !tag.hasUUID("Ship")) {
                return null;
            }
            return new ShipHold(new HoldKey(tag.getUUID("Owner"), purpose), tag.getUUID("Ship"), dimension,
                    tag.getLong("Lease"), tag.getDouble("OriginX"), tag.getDouble("OriginZ"));
        }
    }

    /**
     * Where a ship was last seen.
     *
     * @param plotMin the lowest chunk of its plot's allocation, packed; a plot position inside
     *                {@code plotMin..plotMax} is aboard this ship
     * @param minX    its world box when last seen, in blocks
     * @param seen    overworld game time it was last seen
     */
    public record ShipRecord(UUID ship, ResourceKey<Level> dimension, long plotMin, long plotMax,
                             double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                             String name, long seen) {

        /** Whether a plot-space block position is aboard this ship. */
        public boolean contains(ResourceKey<Level> level, BlockPos plotPos) {
            if (!dimension.equals(level)) {
                return false;
            }
            int x = plotPos.getX() >> 4;
            int z = plotPos.getZ() >> 4;
            return x >= ChunkPos.getX(plotMin) && x <= ChunkPos.getX(plotMax)
                    && z >= ChunkPos.getZ(plotMin) && z <= ChunkPos.getZ(plotMax);
        }

        /** Whether this says the same thing as {@code other} to within a block - not worth a save if so. */
        boolean sameAs(ShipRecord other) {
            return other != null && dimension.equals(other.dimension) && plotMin == other.plotMin
                    && plotMax == other.plotMax && name.equals(other.name)
                    && Math.abs(minX - other.minX) < 1.0D && Math.abs(minZ - other.minZ) < 1.0D
                    && Math.abs(maxX - other.maxX) < 1.0D && Math.abs(maxZ - other.maxZ) < 1.0D
                    && Math.abs(minY - other.minY) < 1.0D && Math.abs(maxY - other.maxY) < 1.0D;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Ship", ship);
            tag.putString("Dimension", dimension.location().toString());
            tag.putLong("PlotMin", plotMin);
            tag.putLong("PlotMax", plotMax);
            tag.putDouble("MinX", minX);
            tag.putDouble("MinY", minY);
            tag.putDouble("MinZ", minZ);
            tag.putDouble("MaxX", maxX);
            tag.putDouble("MaxY", maxY);
            tag.putDouble("MaxZ", maxZ);
            tag.putString("Name", name);
            tag.putLong("Seen", seen);
            return tag;
        }

        @Nullable
        static ShipRecord load(CompoundTag tag) {
            ResourceKey<Level> dimension = parseDimension(tag.getString("Dimension"));
            if (dimension == null || !tag.hasUUID("Ship")) {
                return null;
            }
            return new ShipRecord(tag.getUUID("Ship"), dimension, tag.getLong("PlotMin"), tag.getLong("PlotMax"),
                    tag.getDouble("MinX"), tag.getDouble("MinY"), tag.getDouble("MinZ"),
                    tag.getDouble("MaxX"), tag.getDouble("MaxY"), tag.getDouble("MaxZ"),
                    tag.getString("Name"), tag.getLong("Seen"));
        }
    }

    /** Held ships, keyed by owner, purpose and ship - one owner may hold several ships under one key. */
    private final Map<Slot, ShipHold> holds = new LinkedHashMap<>();
    private final Map<UUID, ShipRecord> ships = new LinkedHashMap<>();
    /** The one chunk forced in each level to keep it ticking - see {@link HoldRules#wakeChunk}. */
    private final Map<ResourceKey<Level>, Long> wake = new HashMap<>();

    private record Slot(HoldKey key, UUID ship) {
    }

    public HoldLedger() {
    }

    public static SavedData.Factory<HoldLedger> factory() {
        return new SavedData.Factory<>(HoldLedger::new, HoldLedger::load, null);
    }

    public static HoldLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    // ------------------------------------------------------------------- holds

    @Nullable
    public ShipHold hold(HoldKey key, UUID ship) {
        return holds.get(new Slot(key, ship));
    }

    public void putHold(ShipHold hold) {
        holds.put(new Slot(hold.key(), hold.ship()), hold);
        setDirty();
    }

    @Nullable
    public ShipHold removeHold(HoldKey key, UUID ship) {
        ShipHold removed = holds.remove(new Slot(key, ship));
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Every ship held under one key. Usually one; more only if two machines share an id. */
    public List<ShipHold> holdsBy(HoldKey key) {
        List<ShipHold> found = new ArrayList<>();
        for (ShipHold hold : holds.values()) {
            if (hold.key().equals(key)) {
                found.add(hold);
            }
        }
        return found;
    }

    /** Every hold on one ship. */
    public List<ShipHold> holdsOn(UUID ship) {
        List<ShipHold> found = new ArrayList<>();
        for (ShipHold hold : holds.values()) {
            if (hold.ship().equals(ship)) {
                found.add(hold);
            }
        }
        return found;
    }

    public List<ShipHold> holds() {
        return List.copyOf(holds.values());
    }

    /** Whether any hold in this level is still within its lease - what keeps a wake chunk at load. */
    public boolean anyLiveHoldIn(ResourceKey<Level> dimension, long now) {
        for (ShipHold hold : holds.values()) {
            if (hold.dimension().equals(dimension) && !HoldRules.lapsed(hold.leaseUntil(), now)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------- ships

    /** Records where a ship is. Only marks the ledger for saving when that has meaningfully changed. */
    public void noteShip(ShipRecord record) {
        ShipRecord previous = ships.get(record.ship());
        if (record.sameAs(previous)) {
            return;
        }
        ships.put(record.ship(), record);
        setDirty();
    }

    @Nullable
    public ShipRecord ship(UUID ship) {
        return ships.get(ship);
    }

    /** The ship a plot position is aboard, loaded or not, or {@code null} if no ship here was ever noted. */
    @Nullable
    public ShipRecord shipAt(ResourceKey<Level> dimension, BlockPos plotPos) {
        for (ShipRecord record : ships.values()) {
            if (record.contains(dimension, plotPos)) {
                return record;
            }
        }
        return null;
    }

    public void forgetShip(UUID ship) {
        boolean changed = ships.remove(ship) != null;
        changed |= holds.values().removeIf(hold -> hold.ship().equals(ship));
        if (changed) {
            setDirty();
        }
    }

    public List<ShipRecord> ships() {
        return List.copyOf(ships.values());
    }

    // -------------------------------------------------------------------- wake

    @Nullable
    public Long wakeChunk(ResourceKey<Level> dimension) {
        return wake.get(dimension);
    }

    public void setWakeChunk(ResourceKey<Level> dimension, @Nullable Long chunk) {
        Long previous = chunk == null ? wake.remove(dimension) : wake.put(dimension, chunk);
        if (previous == null ? chunk != null : !previous.equals(chunk)) {
            setDirty();
        }
    }

    // ------------------------------------------------------------- persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag heldList = new ListTag();
        for (ShipHold hold : holds.values()) {
            heldList.add(hold.save());
        }
        tag.put("Holds", heldList);
        ListTag shipList = new ListTag();
        for (ShipRecord record : ships.values()) {
            shipList.add(record.save());
        }
        tag.put("Ships", shipList);
        CompoundTag wakeTag = new CompoundTag();
        wake.forEach((dimension, chunk) -> wakeTag.putLong(dimension.location().toString(), chunk));
        tag.put("Wake", wakeTag);
        return tag;
    }

    private static HoldLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        HoldLedger ledger = new HoldLedger();
        ListTag heldList = tag.getList("Holds", Tag.TAG_COMPOUND);
        for (int i = 0; i < heldList.size(); i++) {
            ShipHold hold = ShipHold.load(heldList.getCompound(i));
            if (hold != null) {
                ledger.holds.put(new Slot(hold.key(), hold.ship()), hold);
            }
        }
        ListTag shipList = tag.getList("Ships", Tag.TAG_COMPOUND);
        for (int i = 0; i < shipList.size(); i++) {
            ShipRecord record = ShipRecord.load(shipList.getCompound(i));
            if (record != null) {
                ledger.ships.put(record.ship(), record);
            }
        }
        CompoundTag wakeTag = tag.getCompound("Wake");
        for (String key : wakeTag.getAllKeys()) {
            ResourceKey<Level> dimension = parseDimension(key);
            if (dimension != null) {
                ledger.wake.put(dimension, wakeTag.getLong(key));
            }
        }
        return ledger;
    }

    @Nullable
    private static ResourceKey<Level> parseDimension(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
    }
}
