package uk.co.iceconchy.aerowarptics.anchor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;

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
 * Server-wide index of every registered Warp Anchor.
 *
 * <p>Backed by {@link SavedData} on the overworld's data storage so a single file holds anchors from
 * every dimension and survives restarts, world reloads and dimension unloads. Lookups by id and by
 * name are both O(1); nothing here walks the world or iterates loaded block entities.
 *
 * <p>Anchor block entities register themselves on load and deregister on removal, so the registry is
 * always a superset of the currently loaded anchors - an anchor in an unloaded chunk stays a valid
 * destination, which is the whole point of long-range warping.
 */
public class WarpAnchorRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_warp_anchors";
    private static final String KEY_ANCHORS = "Anchors";

    private final Map<UUID, WarpAnchor> byId = new LinkedHashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    public WarpAnchorRegistry() {
    }

    public static SavedData.Factory<WarpAnchorRegistry> factory() {
        return new SavedData.Factory<>(WarpAnchorRegistry::new, WarpAnchorRegistry::load, null);
    }

    /** Fetches the shared registry. Always stored on the overworld regardless of the caller's level. */
    public static WarpAnchorRegistry get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static WarpAnchorRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    // --------------------------------------------------------------- mutate

    /**
     * Adds or replaces an anchor.
     *
     * <p>Registering an id that already exists overwrites it, which is how a re-loaded anchor block
     * refreshes its own record without creating a duplicate.
     */
    public void register(WarpAnchor anchor) {
        WarpAnchor previous = byId.put(anchor.id(), anchor);
        if (previous != null) {
            byName.remove(key(previous.name()), previous.id());
        }
        // Re-registered every time an anchor's chunk loads, so only a real change is worth a line.
        if (!anchor.equals(previous)) {
            Trace.log(Trace.ANCHOR, "{} {} \"{}\" at {} in {} owner={} access={} network={} enabled={}"
                            + " arrivalHeight={} ({} registered)", previous == null ? "registered" : "updated",
                    anchor.id(), anchor.name(), Trace.pos(anchor.pos()), anchor.dimension().location(),
                    anchor.ownerName(), anchor.access(), anchor.network(), anchor.enabled(), anchor.arrivalHeight(),
                    byId.size());
        }
        indexName(anchor);
        setDirty();
    }

    public boolean remove(UUID id) {
        WarpAnchor removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        Trace.log(Trace.ANCHOR, "removed {} \"{}\" at {} ({} left)", id, removed.name(), Trace.pos(removed.pos()),
                byId.size());
        byName.remove(key(removed.name()), id);
        setDirty();
        return true;
    }

    /**
     * Renames an anchor.
     *
     * @return {@code false} when the name is already taken by a different anchor
     */
    public boolean rename(UUID id, String newName) {
        WarpAnchor anchor = byId.get(id);
        if (anchor == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (!trimmed.isEmpty()) {
            UUID existing = byName.get(key(trimmed));
            if (existing != null && !existing.equals(id)) {
                Trace.log(Trace.ANCHOR, "rename of {} to \"{}\" refused: {} already has that name", id, trimmed,
                        existing);
                return false;
            }
        }
        byName.remove(key(anchor.name()), id);
        WarpAnchor renamed = anchor.withName(trimmed);
        byId.put(id, renamed);
        indexName(renamed);
        setDirty();
        return true;
    }

    // ----------------------------------------------------------------- read

    @Nullable
    public WarpAnchor byId(UUID id) {
        return id == null ? null : byId.get(id);
    }

    @Nullable
    public WarpAnchor byName(String name) {
        UUID id = byName.get(key(name));
        return id == null ? null : byId.get(id);
    }

    public boolean isNameTaken(String name, @Nullable UUID excluding) {
        UUID existing = byName.get(key(name));
        return existing != null && !existing.equals(excluding);
    }

    public Collection<WarpAnchor> all() {
        return List.copyOf(byId.values());
    }

    public int countOwnedBy(UUID owner) {
        return (int) byId.values().stream().filter(a -> a.isOwnedBy(owner)).count();
    }

    /** Whether the given player may register another anchor under the configured cap. */
    public boolean canRegisterMore(UUID owner) {
        int cap = AWConfig.MAX_ANCHORS_PER_PLAYER.get();
        return cap <= 0 || countOwnedBy(owner) < cap;
    }

    /**
     * Anchors a player is allowed to see, sorted by display name.
     *
     * @param networks      who is in which network, for the anchors set to Network access
     * @param sameDimension when non-null, only anchors in that dimension are returned
     */
    public List<WarpAnchor> visibleTo(Player player, Membership networks,
                                      @Nullable net.minecraft.resources.ResourceKey<Level> sameDimension) {
        return filtered(anchor -> anchor.isVisibleTo(player, networks)
                && (sameDimension == null || anchor.dimension().equals(sameDimension)));
    }

    /**
     * Takes every anchor the predicate picks off its network, and says which they were.
     *
     * <p>For a network disbanded, or a member gone from one: their anchors come off it here, in the record,
     * whether or not the blocks are loaded. A loaded block has then to be told as well, or it will write
     * the network straight back the next time it re-asserts itself - the caller's job, since this class
     * knows nothing of the world.
     */
    public List<WarpAnchor> detach(Predicate<WarpAnchor> which) {
        List<WarpAnchor> changed = new ArrayList<>();
        for (Map.Entry<UUID, WarpAnchor> entry : byId.entrySet()) {
            WarpAnchor anchor = entry.getValue();
            if (anchor.network() != null && which.test(anchor)) {
                WarpAnchor off = anchor.withoutNetwork();
                entry.setValue(off);
                changed.add(off);
            }
        }
        if (!changed.isEmpty()) {
            setDirty();
        }
        return changed;
    }

    public List<WarpAnchor> filtered(Predicate<WarpAnchor> predicate) {
        List<WarpAnchor> result = new ArrayList<>();
        for (WarpAnchor anchor : byId.values()) {
            if (predicate.test(anchor)) {
                result.add(anchor);
            }
        }
        result.sort(Comparator.comparing(WarpAnchor::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarpAnchor anchor : byId.values()) {
            list.add(anchor.save());
        }
        tag.put(KEY_ANCHORS, list);
        return tag;
    }

    private static WarpAnchorRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        WarpAnchorRegistry registry = new WarpAnchorRegistry();
        ListTag list = tag.getList(KEY_ANCHORS, Tag.TAG_COMPOUND);
        int skipped = 0;
        for (int i = 0; i < list.size(); i++) {
            WarpAnchor anchor = WarpAnchor.load(list.getCompound(i));
            if (anchor == null) {
                skipped++;
                continue;
            }
            if (registry.byId.containsKey(anchor.id())) {
                // Duplicate id on disk: keep the first, drop the rest rather than corrupting the index.
                skipped++;
                continue;
            }
            registry.byId.put(anchor.id(), anchor);
            registry.indexName(anchor);
        }
        if (skipped > 0) {
            AeroWarptics.LOGGER.warn("Dropped {} unreadable or duplicate warp anchor record(s)", skipped);
            registry.setDirty();
        }
        return registry;
    }

    private void indexName(WarpAnchor anchor) {
        if (anchor.name().isBlank()) {
            return;
        }
        // A name collision surviving from an older save loses its index entry but keeps its record,
        // so the anchor stays reachable by id and can be renamed to recover.
        byName.putIfAbsent(key(anchor.name()), anchor.id());
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
