package uk.co.iceconchy.aerowarptics.warpnet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every network on the server.
 *
 * <p>The same arrangement as {@link uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry}: held on the
 * overworld's data storage, so one file covers every dimension and survives restarts. Unlike an anchor
 * or a gate a network has no block anywhere, so this file is the only place one exists.
 *
 * <p>This is the record and its own invariants - one network per name, no owner among the members.
 * Who may ask for what is {@link NetworkOrders}'.
 */
public class WarpNetworkRegistry extends SavedData implements Membership {

    private static final String FILE_ID = AeroWarptics.MODID + "_warp_networks";
    private static final String KEY_NETWORKS = "Networks";

    private final Map<UUID, WarpNetwork> byId = new LinkedHashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    public WarpNetworkRegistry() {
    }

    public static SavedData.Factory<WarpNetworkRegistry> factory() {
        return new SavedData.Factory<>(WarpNetworkRegistry::new, WarpNetworkRegistry::load, null);
    }

    /** Fetches the shared registry. Always stored on the overworld regardless of the caller's level. */
    public static WarpNetworkRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static WarpNetworkRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    // --------------------------------------------------------------- mutate

    /**
     * Founds a network.
     *
     * @param name already cleaned - see {@link NetworkRules#clean}
     * @return the new network, or {@code null} when the name will not do or another network has it
     */
    @Nullable
    public WarpNetwork create(String name, UUID owner, String ownerName) {
        if (!NetworkRules.validName(name) || byName.containsKey(NetworkRules.key(name))) {
            return null;
        }
        WarpNetwork network = WarpNetwork.create(UUID.randomUUID(), name, owner, ownerName);
        byId.put(network.id(), network);
        byName.put(NetworkRules.key(name), network.id());
        setDirty();
        return network;
    }

    /**
     * Renames a network.
     *
     * @param name already cleaned
     * @return {@code false} when there is no such network, the name will not do, or a different network
     * has it; renaming a network to its own name in another case is allowed
     */
    public boolean rename(UUID id, String name) {
        WarpNetwork network = byId.get(id);
        if (network == null || !NetworkRules.validName(name)) {
            return false;
        }
        UUID holder = byName.get(NetworkRules.key(name));
        if (holder != null && !holder.equals(id)) {
            return false;
        }
        byName.remove(NetworkRules.key(network.name()), id);
        byId.put(id, network.withName(name));
        byName.put(NetworkRules.key(name), id);
        setDirty();
        return true;
    }

    /**
     * Replaces a network's record with a changed copy of itself - a member more, a member fewer, a new
     * owner. The name is not this method's to change; that is {@link #rename}, which keeps the index.
     *
     * @return {@code false} when there is no such network
     */
    public boolean update(WarpNetwork changed) {
        WarpNetwork current = byId.get(changed.id());
        if (current == null) {
            return false;
        }
        byId.put(changed.id(), changed.withName(current.name()));
        setDirty();
        return true;
    }

    public boolean remove(UUID id) {
        WarpNetwork removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        byName.remove(NetworkRules.key(removed.name()), id);
        setDirty();
        return true;
    }

    // ----------------------------------------------------------------- read

    @Nullable
    public WarpNetwork byId(@Nullable UUID id) {
        return id == null ? null : byId.get(id);
    }

    @Nullable
    public WarpNetwork byName(@Nullable String name) {
        UUID id = byName.get(NetworkRules.key(name));
        return id == null ? null : byId.get(id);
    }

    public Collection<WarpNetwork> all() {
        return List.copyOf(byId.values());
    }

    /** A network's name, or nothing for no network or one that has gone. */
    public String nameOf(@Nullable UUID id) {
        WarpNetwork network = byId(id);
        return network == null ? "" : network.name();
    }

    public int countOwnedBy(UUID owner) {
        return (int) byId.values().stream().filter(network -> network.isOwnedBy(owner)).count();
    }

    /** Every network a player is in, as owner or member, sorted by name. */
    public List<WarpNetwork> networksOf(UUID player) {
        List<WarpNetwork> result = new ArrayList<>();
        for (WarpNetwork network : byId.values()) {
            if (network.has(player)) {
                result.add(network);
            }
        }
        result.sort(Comparator.comparing(WarpNetwork::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public boolean isMember(@Nullable UUID network, UUID player) {
        WarpNetwork found = byId(network);
        return found != null && found.has(player);
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarpNetwork network : byId.values()) {
            list.add(network.save());
        }
        tag.put(KEY_NETWORKS, list);
        return tag;
    }

    private static WarpNetworkRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        WarpNetworkRegistry registry = new WarpNetworkRegistry();
        ListTag list = tag.getList(KEY_NETWORKS, Tag.TAG_COMPOUND);
        int skipped = 0;
        for (int index = 0; index < list.size(); index++) {
            WarpNetwork network = WarpNetwork.load(list.getCompound(index));
            // A second network of the same id or the same name is dropped along with the unreadable: the
            // name is how a player picks one, and two answering to it is one too many to choose between.
            if (network == null || registry.byId.containsKey(network.id())
                    || registry.byName.containsKey(NetworkRules.key(network.name()))) {
                skipped++;
                continue;
            }
            registry.byId.put(network.id(), network);
            registry.byName.put(NetworkRules.key(network.name()), network.id());
        }
        if (skipped > 0) {
            AeroWarptics.LOGGER.warn("Dropped {} unreadable or duplicate network record(s)", skipped);
            registry.setDirty();
        }
        return registry;
    }
}
