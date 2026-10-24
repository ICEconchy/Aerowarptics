package uk.co.iceconchy.aerowarptics.ward;

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
 * Server-wide index of every Rift Ward, kept on the overworld's data storage as the anchors' is.
 *
 * <p>A ward's block registers itself when it loads and takes itself out when it is broken, so the registry is
 * always a superset of the loaded wards - and a ward in an unloaded chunk still holds its field, which is the
 * whole point of one. Nothing here walks the world.
 */
public class WardRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_rift_wards";
    private static final String KEY_WARDS = "Wards";

    private final Map<UUID, RiftWard> byId = new LinkedHashMap<>();

    public WardRegistry() {
    }

    public static SavedData.Factory<WardRegistry> factory() {
        return new SavedData.Factory<>(WardRegistry::new, WardRegistry::load, null);
    }

    public static WardRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static WardRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    // --------------------------------------------------------------- mutate

    /** Adds a ward, or replaces the record of one already known. */
    public void register(RiftWard ward) {
        RiftWard previous = byId.put(ward.id(), ward);
        // A loaded ward writes its charge here every second, so only a change to what it is is worth a line.
        if (previous == null || !same(previous, ward)) {
            Trace.log(Trace.WARD, "{} ward {} at {} in {} owner={} mode={} networks={} selfTest={} enabled={} radius={}"
                            + " charge={} standing={} ({} registered)", previous == null ? "registered" : "updated",
                    ward.id(), Trace.pos(ward.pos()), ward.dimension().location(), ward.ownerName(), ward.mode(),
                    ward.networks(), ward.selfTest(), ward.enabled(), ward.radius(), Trace.num(ward.charge()),
                    ward.standing(), byId.size());
        }
        setDirty();
    }

    private static boolean same(RiftWard a, RiftWard b) {
        return a.pos().equals(b.pos()) && a.dimension().equals(b.dimension()) && a.enabled() == b.enabled()
                && a.radius() == b.radius() && a.standing() == b.standing()
                && a.mode() == b.mode() && a.selfTest() == b.selfTest() && a.networks().equals(b.networks())
                && java.util.Objects.equals(a.owner(), b.owner());
    }

    public boolean remove(UUID id) {
        RiftWard removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        Trace.log(Trace.WARD, "removed ward {} at {} ({} left)", id, Trace.pos(removed.pos()), byId.size());
        setDirty();
        return true;
    }

    /**
     * Strikes a network that has been disbanded off every ward that names it, and says which they were. What a
     * ward then does is whatever its mode makes of the networks it has left - one allowing only that network turns
     * everybody, one blocking only that network turns nobody. A loaded block has then to be told as well, or it
     * writes the network straight back - the caller's job.
     */
    public List<RiftWard> forget(UUID network) {
        List<RiftWard> changed = new ArrayList<>();
        for (Map.Entry<UUID, RiftWard> entry : byId.entrySet()) {
            RiftWard ward = entry.getValue();
            if (ward.networks().contains(network)) {
                RiftWard off = ward.without(network);
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
    public RiftWard byId(@Nullable UUID id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<RiftWard> all() {
        return List.copyOf(byId.values());
    }

    /** Every ward in a dimension, in the order they were set down. */
    public List<RiftWard> in(ResourceKey<Level> dimension) {
        List<RiftWard> out = new ArrayList<>();
        for (RiftWard ward : byId.values()) {
            if (ward.dimension().equals(dimension)) {
                out.add(ward);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RiftWard ward : byId.values()) {
            list.add(ward.save());
        }
        tag.put(KEY_WARDS, list);
        return tag;
    }

    private static WardRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        WardRegistry registry = new WardRegistry();
        ListTag list = tag.getList(KEY_WARDS, Tag.TAG_COMPOUND);
        int skipped = 0;
        for (int i = 0; i < list.size(); i++) {
            RiftWard ward = RiftWard.load(list.getCompound(i));
            if (ward == null || registry.byId.containsKey(ward.id())) {
                skipped++;
                continue;
            }
            registry.byId.put(ward.id(), ward);
        }
        if (skipped > 0) {
            AeroWarptics.LOGGER.warn("Dropped {} unreadable or duplicate rift ward record(s)", skipped);
            registry.setDirty();
        }
        return registry;
    }
}
