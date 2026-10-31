package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every rail lane on the server, whether or not anything of it is loaded.
 *
 * <p>World data on the overworld, as the gate registry is, because a lane spans up to three dimensions and
 * belongs to none of them. Indexed by the gate that owns it, by the gate it comes out of, and by both of its
 * tracks - the near one a player laid and the far stub this mod laid - since every question asked of a lane
 * starts from one of those four.
 *
 * <p>Also keeps the far stubs that are owed a tidy-up: a stub at a gate whose chunks were not loaded when its
 * lane was routed elsewhere or taken up. They are taken away when somebody next loads them - see
 * {@link RailWorks}.
 */
public class RailLaneRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_rail_lanes";

    /** A track somewhere, in some dimension. */
    public record Place(ResourceKey<Level> dimension, BlockPos pos) {
        public Place {
            pos = pos.immutable();
        }
    }

    /**
     * A far stub left behind, to be taken up when its chunk is next loaded.
     *
     * @param stub the portal track
     * @param cell the gate cell beside it, where a socket may have been left standing
     */
    public record Cleanup(ResourceKey<Level> dimension, BlockPos stub, BlockPos cell) {
    }

    private final Map<UUID, RailLane> lanes = new LinkedHashMap<>();
    private final List<Cleanup> cleanups = new ArrayList<>();

    private final Map<Place, UUID> byTrack = new HashMap<>();
    private final Map<UUID, Set<UUID>> byGate = new HashMap<>();
    private final Map<UUID, Set<UUID>> byFar = new HashMap<>();
    private final Map<Integer, UUID> bySlot = new HashMap<>();

    public RailLaneRegistry() {
    }

    public static SavedData.Factory<RailLaneRegistry> factory() {
        return new SavedData.Factory<>(RailLaneRegistry::new, RailLaneRegistry::load, null);
    }

    public static RailLaneRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    // --------------------------------------------------------------- mutate

    /** Adds a lane, or replaces the one with its id. */
    public void put(RailLane lane) {
        RailLane previous = lanes.put(lane.id(), lane);
        if (previous != null) {
            unindex(previous);
        }
        index(lane);
        setDirty();
    }

    @Nullable
    public RailLane remove(UUID id) {
        RailLane removed = lanes.remove(id);
        if (removed != null) {
            unindex(removed);
            setDirty();
        }
        return removed;
    }

    public void owe(Cleanup cleanup) {
        if (!cleanups.contains(cleanup)) {
            cleanups.add(cleanup);
            setDirty();
        }
    }

    public void settled(Cleanup cleanup) {
        if (cleanups.remove(cleanup)) {
            setDirty();
        }
    }

    // ----------------------------------------------------------------- read

    @Nullable
    public RailLane get(@Nullable UUID id) {
        return id == null ? null : lanes.get(id);
    }

    public Collection<RailLane> all() {
        return List.copyOf(lanes.values());
    }

    public List<Cleanup> cleanups() {
        return List.copyOf(cleanups);
    }

    /** The lane whose near track or far stub stands here, if there is one. */
    @Nullable
    public RailLane atTrack(ResourceKey<Level> dimension, BlockPos pos) {
        return get(byTrack.get(new Place(dimension, pos)));
    }

    /** The lane in a corridor slot. */
    @Nullable
    public RailLane inSlot(int slot) {
        return get(bySlot.get(slot));
    }

    /** Lanes whose near track runs into this gate. */
    public List<RailLane> ownedBy(UUID gate) {
        return collect(byGate.get(gate));
    }

    /** Lanes that come out of this gate. */
    public List<RailLane> comingOutOf(UUID gate) {
        return collect(byFar.get(gate));
    }

    /** Every lane that touches this gate, either end. */
    public List<RailLane> touching(UUID gate) {
        Set<UUID> ids = new HashSet<>();
        ids.addAll(byGate.getOrDefault(gate, Set.of()));
        ids.addAll(byFar.getOrDefault(gate, Set.of()));
        return collect(ids);
    }

    /**
     * The cells of this gate's opening that a lane runs through: its own lanes' near cells, and the far cells of
     * lanes laid out of it. These are the cells that must always hold a pane or a socket.
     */
    public Set<BlockPos> laneCells(UUID gate) {
        Set<BlockPos> cells = new HashSet<>();
        for (RailLane lane : ownedBy(gate)) {
            cells.add(lane.cell());
        }
        for (RailLane lane : comingOutOf(gate)) {
            if (lane.farCell() != null && lane.state() == RailLaneState.READY) {
                cells.add(lane.farCell());
            }
        }
        return cells;
    }

    /** Whether any lane runs through this cell of a gate, at either of its ends. */
    public boolean isLaneCell(ResourceKey<Level> dimension, BlockPos cell) {
        return atCell(dimension, cell) != null;
    }

    /**
     * A lane at one of its two cells, and which end this is.
     *
     * @param nearEnd whether this is the cell the lane runs <em>into</em> - the end a train goes in at - rather
     *                than the far cell it comes out of
     */
    public record End(RailLane lane, boolean nearEnd) {
    }

    /** The lane running through this cell of a gate, at either of its ends, or {@code null} if none does. */
    @Nullable
    public End atCell(ResourceKey<Level> dimension, BlockPos cell) {
        for (RailLane lane : lanes.values()) {
            if (lane.dimension().equals(dimension) && lane.cell().equals(cell)) {
                return new End(lane, true);
            }
            if (dimension.equals(lane.farDimension()) && cell.equals(lane.farCell())) {
                return new End(lane, false);
            }
        }
        return null;
    }

    public Set<Integer> usedSlots() {
        return Set.copyOf(bySlot.keySet());
    }

    private List<RailLane> collect(@Nullable Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<RailLane> found = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            RailLane lane = lanes.get(id);
            if (lane != null) {
                found.add(lane);
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- index

    private void index(RailLane lane) {
        byTrack.put(new Place(lane.dimension(), lane.track()), lane.id());
        if (lane.farDimension() != null && lane.farStub() != null) {
            byTrack.put(new Place(lane.farDimension(), lane.farStub()), lane.id());
        }
        byGate.computeIfAbsent(lane.gate(), key -> new HashSet<>()).add(lane.id());
        if (lane.far() != null) {
            byFar.computeIfAbsent(lane.far(), key -> new HashSet<>()).add(lane.id());
        }
        bySlot.put(lane.slot(), lane.id());
    }

    private void unindex(RailLane lane) {
        byTrack.remove(new Place(lane.dimension(), lane.track()), lane.id());
        if (lane.farDimension() != null && lane.farStub() != null) {
            byTrack.remove(new Place(lane.farDimension(), lane.farStub()), lane.id());
        }
        Set<UUID> owned = byGate.get(lane.gate());
        if (owned != null) {
            owned.remove(lane.id());
        }
        if (lane.far() != null) {
            Set<UUID> into = byFar.get(lane.far());
            if (into != null) {
                into.remove(lane.id());
            }
        }
        bySlot.remove(lane.slot(), lane.id());
    }

    // ------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (RailLane lane : lanes.values()) {
            list.add(lane.save());
        }
        tag.put("Lanes", list);
        ListTag owed = new ListTag();
        for (Cleanup cleanup : cleanups) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", cleanup.dimension().location().toString());
            entry.putLong("Stub", cleanup.stub().asLong());
            entry.putLong("Cell", cleanup.cell().asLong());
            owed.add(entry);
        }
        tag.put("Cleanups", owed);
        return tag;
    }

    private static RailLaneRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RailLaneRegistry registry = new RailLaneRegistry();
        ListTag list = tag.getList("Lanes", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            RailLane lane = RailLane.load(list.getCompound(index));
            if (lane != null) {
                registry.lanes.put(lane.id(), lane);
                registry.index(lane);
            }
        }
        ListTag owed = tag.getList("Cleanups", Tag.TAG_COMPOUND);
        for (int index = 0; index < owed.size(); index++) {
            CompoundTag entry = owed.getCompound(index);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension != null) {
                registry.cleanups.add(new Cleanup(ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("Stub")), BlockPos.of(entry.getLong("Cell"))));
            }
        }
        return registry;
    }
}
