package uk.co.iceconchy.aerowarptics.berth;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide index of every Berth, kept on the overworld's data storage as the anchors' and the wards' are.
 *
 * <p>A berth's block registers itself when it loads and takes itself out when it is broken, so the
 * registry is always a superset of the loaded berths - and a berth in an unloaded chunk is still given
 * out, which is the whole point: a ship warping to a station is nearly always somewhere the station is
 * not. What is in a slip is kept here too, so a reservation or a moored ship outlives the chunk the block
 * is in. Nothing here walks the world.
 */
public class BerthRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_berths";
    private static final String KEY_BERTHS = "Berths";

    private final Map<UUID, Berth> byId = new LinkedHashMap<>();

    public BerthRegistry() {
    }

    public static SavedData.Factory<BerthRegistry> factory() {
        return new SavedData.Factory<>(BerthRegistry::new, BerthRegistry::load, null);
    }

    public static BerthRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static BerthRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    // --------------------------------------------------------------- mutate

    /** Adds a berth, or replaces the record of one already known. */
    public void register(Berth berth) {
        Berth previous = byId.put(berth.id(), berth);
        if (berth.equals(previous)) {
            return;
        }
        Trace.log(Trace.BERTH, "{} berth {} '{}' at {} in {} anchor={} owner={} slip={}x{}x{} yaw={} reserve={}"
                        + " enabled={} state={} ship={} ({} registered)", previous == null ? "registered" : "updated",
                berth.id(), berth.displayName(), Trace.pos(berth.pos()), berth.dimension().location(), berth.anchor(),
                berth.ownerName(), berth.length(), berth.width(), berth.height(), berth.yaw(), berth.reserve(),
                berth.enabled(), berth.state(), berth.ship(), byId.size());
        setDirty();
    }

    public boolean remove(UUID id) {
        Berth removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        Trace.log(Trace.BERTH, "removed berth {} at {} ({} left)", id, Trace.pos(removed.pos()), byId.size());
        setDirty();
        return true;
    }

    /**
     * Takes a network that has been disbanded off every berth that named it, and says which they were. A
     * loaded block has then to be told as well, or it writes the network straight back - the caller's job.
     */
    public List<Berth> forgetNetwork(UUID network) {
        List<Berth> changed = new ArrayList<>();
        for (Map.Entry<UUID, Berth> entry : byId.entrySet()) {
            if (network.equals(entry.getValue().network())) {
                Berth off = entry.getValue().withoutNetwork();
                entry.setValue(off);
                changed.add(off);
            }
        }
        if (!changed.isEmpty()) {
            setDirty();
        }
        return changed;
    }

    /** Unlinks every berth of an anchor that has gone, and says which they were. */
    public List<Berth> forgetAnchor(UUID anchor) {
        List<Berth> changed = new ArrayList<>();
        for (Map.Entry<UUID, Berth> entry : byId.entrySet()) {
            if (anchor.equals(entry.getValue().anchor())) {
                Berth off = entry.getValue().withAnchor(null);
                entry.setValue(off);
                changed.add(off);
            }
        }
        if (!changed.isEmpty()) {
            setDirty();
        }
        return changed;
    }

    // ----------------------------------------------------------------- read

    @Nullable
    public Berth byId(@Nullable UUID id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<Berth> all() {
        return List.copyOf(byId.values());
    }

    /** Every berth in a dimension, in the order they were set down. */
    public List<Berth> in(ResourceKey<Level> dimension) {
        List<Berth> out = new ArrayList<>();
        for (Berth berth : byId.values()) {
            if (berth.dimension().equals(dimension)) {
                out.add(berth);
            }
        }
        return out;
    }

    /** Every berth of an anchor's station, switched on or not, in the order they were set down. */
    public List<Berth> of(@Nullable UUID anchor) {
        List<Berth> out = new ArrayList<>();
        if (anchor == null) {
            return out;
        }
        for (Berth berth : byId.values()) {
            if (anchor.equals(berth.anchor())) {
                out.add(berth);
            }
        }
        return out;
    }

    /** Whether an anchor is a station: has a berth that is switched on. Such an anchor gives out berths and nothing else. */
    public boolean isStation(@Nullable UUID anchor) {
        if (anchor == null) {
            return false;
        }
        for (Berth berth : byId.values()) {
            if (anchor.equals(berth.anchor()) && berth.enabled()) {
                return true;
            }
        }
        return false;
    }

    /** The berth a ship is this moment's business of - reserved for it, drawing it, holding it or let go of it. */
    @Nullable
    public Berth concerning(@Nullable UUID ship) {
        if (ship == null) {
            return null;
        }
        for (Berth berth : byId.values()) {
            if (berth.concerns(ship)) {
                return berth;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Berth berth : byId.values()) {
            list.add(berth.save());
        }
        tag.put(KEY_BERTHS, list);
        return tag;
    }

    private static BerthRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BerthRegistry registry = new BerthRegistry();
        ListTag list = tag.getList(KEY_BERTHS, Tag.TAG_COMPOUND);
        int skipped = 0;
        for (int i = 0; i < list.size(); i++) {
            Berth berth = Berth.load(list.getCompound(i));
            if (berth == null || registry.byId.containsKey(berth.id())) {
                skipped++;
                continue;
            }
            registry.byId.put(berth.id(), berth);
        }
        if (skipped > 0) {
            AeroWarptics.LOGGER.warn("Dropped {} unreadable or duplicate berth record(s)", skipped);
            registry.setDirty();
        }
        return registry;
    }
}
