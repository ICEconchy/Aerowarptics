package uk.co.iceconchy.aerowarptics.chute;

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
 * Every Rift Chute on the server, whether or not its chunks are loaded.
 *
 * <p>The same arrangement as {@link uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry}, and for a
 * sharper version of the same reason. A chute's partner is usually aboard a ship that has sailed
 * somewhere else, and a binding that evaporated whenever the far end unloaded would be a binding you
 * could never rely on - which is precisely what this block exists to stop being true of docking.
 */
public class RiftChuteRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_rift_chutes";
    private static final String KEY_CHUTES = "Chutes";

    /**
     * Most chutes one panel will list.
     *
     * <p>The list is bounded here rather than only at the codec, because a list codec throws on
     * <em>encode</em> when it is handed too many - so an uncapped list is a server-side crash rather
     * than a truncated screen. Sorted before it is cut, so what survives is the nearest rather than
     * whichever happened to be registered first.
     */
    public static final int MAX_LISTED = 256;

    private final Map<UUID, RiftChute> byId = new LinkedHashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();

    public RiftChuteRegistry() {
    }

    public static SavedData.Factory<RiftChuteRegistry> factory() {
        return new SavedData.Factory<>(RiftChuteRegistry::new, RiftChuteRegistry::load, null);
    }

    public static RiftChuteRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static RiftChuteRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    // --------------------------------------------------------------- mutate

    public void register(RiftChute chute) {
        RiftChute previous = byId.put(chute.id(), chute);
        if (previous != null) {
            byName.remove(key(previous.name()), previous.id());
        }
        indexName(chute);
        setDirty();
    }

    /**
     * Forgets a chute, and unbinds anything that was pointing at it.
     *
     * <p>The second half matters more than it looks. A chute left bound to an id that no longer
     * exists would sit there reporting "sending" forever while its buffer filled up, and the player
     * would have no way to tell that from a full belt at the far end.
     */
    public boolean remove(UUID id) {
        RiftChute removed = byId.remove(id);
        if (removed == null) {
            return false;
        }
        byName.remove(key(removed.name()), id);
        for (Map.Entry<UUID, RiftChute> entry : byId.entrySet()) {
            if (id.equals(entry.getValue().partner())) {
                entry.setValue(entry.getValue().withPartner(null));
            }
        }
        setDirty();
        return true;
    }

    /**
     * Binds two chutes to each other.
     *
     * <p>A pair, not a direction. Each end pushes its own buffer to the other and pays for what it
     * sends, so the binding is symmetric and is stored symmetrically - a chute pointing at a partner
     * that was not pointing back would be a pipe that worked one way for reasons invisible on either
     * panel.
     *
     * <p>Binding replaces whatever either end was previously bound to, and unbinds the chutes those
     * were pointing at. A chute may only be in one pair at a time, which is what keeps "where does
     * this go" answerable by looking at one screen.
     *
     * @param to {@code null} breaks the pair {@code from} is in
     * @return {@code false} if either chute is gone, or if something tried to bind a chute to itself
     */
    public boolean bind(UUID from, @Nullable UUID to) {
        RiftChute sender = byId.get(from);
        if (sender == null) {
            return false;
        }
        if (to != null && (to.equals(from) || !byId.containsKey(to))) {
            return false;
        }
        detach(from);
        if (to != null) {
            detach(to);
            byId.put(to, byId.get(to).withPartner(from));
            byId.put(from, byId.get(from).withPartner(to));
        }
        setDirty();
        return true;
    }

    /** Breaks whatever pair this chute is in, at both ends. */
    private void detach(UUID id) {
        RiftChute chute = byId.get(id);
        if (chute == null) {
            return;
        }
        UUID was = chute.partner();
        byId.put(id, chute.withPartner(null));
        if (was != null) {
            RiftChute other = byId.get(was);
            if (other != null && id.equals(other.partner())) {
                byId.put(was, other.withPartner(null));
            }
        }
    }

    /** @return {@code false} when the name is already taken by a different chute */
    public boolean rename(UUID id, String newName) {
        RiftChute chute = byId.get(id);
        if (chute == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (!trimmed.isEmpty()) {
            UUID existing = byName.get(key(trimmed));
            if (existing != null && !existing.equals(id)) {
                return false;
            }
        }
        byName.remove(key(chute.name()), id);
        RiftChute renamed = chute.withName(trimmed);
        byId.put(id, renamed);
        indexName(renamed);
        setDirty();
        return true;
    }

    // ----------------------------------------------------------------- read

    @Nullable
    public RiftChute byId(@Nullable UUID id) {
        return id == null ? null : byId.get(id);
    }

    @Nullable
    public RiftChute byName(String name) {
        UUID id = byName.get(key(name));
        return id == null ? null : byId.get(id);
    }

    public Collection<RiftChute> all() {
        return List.copyOf(byId.values());
    }

    /**
     * Forgets chutes whose block has gone, where that can actually be proved.
     *
     * <p>Only positions that are <em>currently loaded</em> are judged. A loaded position with no
     * chute at it is proof the block is gone; an unloaded one is proof of nothing at all, and
     * pruning those would delete every chute aboard a ship that happened to be somewhere else -
     * which is the exact failure this registry exists to prevent.
     *
     * <p>This is a net rather than the mechanism. Deregistration happens properly in
     * {@code RiftChuteBlock.onRemove}; this catches chutes orphaned before that was wired up, and
     * anything a world edit or a crash removes behind the game's back. Running it when a panel is
     * opened means a player who sees a stale entry is already doing the thing that clears it.
     *
     * @return how many were forgotten
     */
    public int prune(ServerLevel level) {
        List<UUID> gone = new ArrayList<>();
        for (RiftChute chute : byId.values()) {
            if (!chute.dimension().equals(level.dimension()) || !level.isLoaded(chute.pos())) {
                continue;
            }
            // The id has to match, not merely "there is a chute here". Break a chute and build a
            // new one in the same hole and the position is occupied by a chute that is not this one
            // - which is how a ghost survived the first version of this check.
            if (!(level.getBlockEntity(chute.pos()) instanceof RiftChuteBlockEntity standing)
                    || !chute.id().equals(standing.chuteId())) {
                gone.add(chute.id());
            }
        }
        gone.forEach(this::remove);
        return gone.size();
    }

    /**
     * Chutes a player may bind to from a given one, nearest first and capped.
     *
     * <p>Same dimension only, for the same reason a gate is: this mod cannot move anything between
     * levels, so a chute in another dimension is one that could never be sent to.
     */
    public List<RiftChute> bindableFrom(Player player, ResourceKey<Level> dimension, UUID excluding,
                                        net.minecraft.core.BlockPos from) {
        List<RiftChute> result = filtered(chute -> chute.enabled()
                && chute.isVisibleTo(player)
                && chute.dimension().equals(dimension)
                && !chute.id().equals(excluding));
        result.sort(Comparator.comparingDouble(chute -> chute.pos().distSqr(from)));
        return result.size() <= MAX_LISTED ? result : new ArrayList<>(result.subList(0, MAX_LISTED));
    }

    public List<RiftChute> filtered(Predicate<RiftChute> predicate) {
        List<RiftChute> result = new ArrayList<>();
        for (RiftChute chute : byId.values()) {
            if (predicate.test(chute)) {
                result.add(chute);
            }
        }
        result.sort(Comparator.comparing(RiftChute::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RiftChute chute : byId.values()) {
            list.add(chute.save());
        }
        tag.put(KEY_CHUTES, list);
        return tag;
    }

    private static RiftChuteRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        ListTag list = tag.getList(KEY_CHUTES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            RiftChute chute = RiftChute.load(list.getCompound(index));
            registry.byId.put(chute.id(), chute);
            registry.indexName(chute);
        }
        return registry;
    }

    private void indexName(RiftChute chute) {
        if (!chute.name().isBlank()) {
            byName.put(key(chute.name()), chute.id());
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
