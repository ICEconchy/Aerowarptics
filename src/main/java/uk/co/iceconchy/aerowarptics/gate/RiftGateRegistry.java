package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Every Rift Gate on the server, whether or not its chunks are loaded.
 *
 * <p>The same arrangement as {@link uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry}, and for
 * the same reason: a gate is a destination, and a destination that stops existing when nobody is
 * standing near it is not a destination. Held on the overworld's data storage so one file covers every
 * dimension and survives restarts.
 *
 * <p>Controllers register themselves on load and deregister when broken, so this is always a superset
 * of the gates currently in memory.
 */
public class RiftGateRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_rift_gates";
    private static final String KEY_GATES = "Gates";

    private final Map<UUID, RiftGate> byId = new LinkedHashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    public RiftGateRegistry() {
    }

    public static SavedData.Factory<RiftGateRegistry> factory() {
        return new SavedData.Factory<>(RiftGateRegistry::new, RiftGateRegistry::load, null);
    }

    public static RiftGateRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static RiftGateRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    // --------------------------------------------------------------- mutate

    /** Adds or replaces a gate. Re-registering an id refreshes it rather than duplicating it. */
    public void register(RiftGate gate) {
        RiftGate previous = byId.put(gate.id(), gate);
        if (previous != null) {
            byName.remove(key(previous.name()), previous.id());
        }
        indexName(gate);
        setDirty();
    }

    public boolean remove(UUID id) {
        RiftGate removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        byName.remove(key(removed.name()), id);
        setDirty();
        return true;
    }

    /**
     * Renames a gate.
     *
     * @return {@code false} when the name is already taken by a different gate
     */
    public boolean rename(UUID id, String newName) {
        RiftGate gate = byId.get(id);
        if (gate == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (!trimmed.isEmpty()) {
            UUID existing = byName.get(key(trimmed));
            if (existing != null && !existing.equals(id)) {
                return false;
            }
        }
        byName.remove(key(gate.name()), id);
        RiftGate renamed = gate.withName(trimmed);
        byId.put(id, renamed);
        indexName(renamed);
        setDirty();
        return true;
    }

    // ----------------------------------------------------------------- read

    @Nullable
    public RiftGate byId(@Nullable UUID id) {
        return id == null ? null : byId.get(id);
    }

    @Nullable
    public RiftGate byName(String name) {
        UUID id = byName.get(key(name));
        return id == null ? null : byId.get(id);
    }

    public Collection<RiftGate> all() {
        return List.copyOf(byId.values());
    }

    /**
     * Gates a player may dial, sorted by display name.
     *
     * <p>Always filtered to one dimension. Sable cannot move a sub-level between levels, so a gate in
     * another dimension is somewhere a vehicle can never follow its driver - and a door that takes
     * half of what goes through it is worse than no door.
     */
    public List<RiftGate> dialableFrom(Player player, ResourceKey<Level> dimension, @Nullable UUID excluding) {
        return filtered(gate -> gate.enabled()
                && gate.isVisibleTo(player)
                && gate.dimension().equals(dimension)
                && !gate.id().equals(excluding));
    }

    public List<RiftGate> filtered(Predicate<RiftGate> predicate) {
        List<RiftGate> result = new ArrayList<>();
        for (RiftGate gate : byId.values()) {
            if (predicate.test(gate)) {
                result.add(gate);
            }
        }
        result.sort(Comparator.comparing(RiftGate::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RiftGate gate : byId.values()) {
            list.add(gate.save());
        }
        tag.put(KEY_GATES, list);
        return tag;
    }

    private static RiftGateRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftGateRegistry registry = new RiftGateRegistry();
        ListTag list = tag.getList(KEY_GATES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            RiftGate gate = RiftGate.load(list.getCompound(index));
            registry.byId.put(gate.id(), gate);
            registry.indexName(gate);
        }
        return registry;
    }

    private void indexName(RiftGate gate) {
        if (!gate.name().isBlank()) {
            byName.put(key(gate.name()), gate.id());
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
