package uk.co.iceconchy.aerowarptics.flotilla;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Rift Coupler the server knows of, and every flotilla they make up.
 *
 * <p>World data, saved with the world, in the same place as the anchor, gate and chute registries: the
 * overworld's data storage, one file for the whole server. Not a static map - a flotilla has to survive
 * a restart, and a static map would also leak one world's flotillas into the next world a single-player
 * client opens.
 *
 * <p>Couplers are remembered whether or not they are loaded, so the Fleet Board can say an escort is out
 * of contact rather than simply forgetting it; each coupler re-announces itself every second it is
 * loaded, and forgets itself only when it is broken. Every change that could break a flotilla's rules -
 * one flotilla per ship, no nesting, the escort cap - is made here and checked here, so the packet
 * handlers can only ask for things and never arrange them.
 */
public class FlotillaRegistry extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_flotillas";

    /** Requests a flagship holds before it stops taking more. Enough to be useful, not enough to be spam. */
    public static final int MAX_REQUESTS = 16;

    private final Map<UUID, CouplerRecord> couplers = new LinkedHashMap<>();
    /** Keyed by the flagship's coupler id. */
    private final Map<UUID, Flotilla> flotillas = new LinkedHashMap<>();
    /** Escort coupler to the flagship it follows. Derived; rebuilt on load. */
    private final Map<UUID, UUID> following = new HashMap<>();
    /**
     * Couplers whose block went away, and the game time it did. Forgotten for good by {@link #sweep} once
     * they have been gone for the grace period without turning up anywhere else.
     */
    private final Map<UUID, Long> gone = new LinkedHashMap<>();
    /** Folds in flight. Not saved - see {@link FoldLog}. */
    private final FoldLog folds = new FoldLog();

    public FlotillaRegistry() {
    }

    public static SavedData.Factory<FlotillaRegistry> factory() {
        return new SavedData.Factory<>(FlotillaRegistry::new, FlotillaRegistry::load, null);
    }

    /** The server's registry. Always stored on the overworld, whichever level asks. */
    public static FlotillaRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static FlotillaRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    /** Every fold in flight, as its ships report in. */
    public FoldLog folds() {
        return folds;
    }

    // ------------------------------------------------------------------ couplers

    @Nullable
    public CouplerRecord coupler(UUID id) {
        return id == null ? null : couplers.get(id);
    }

    public Collection<CouplerRecord> couplers() {
        return Collections.unmodifiableCollection(couplers.values());
    }

    /**
     * Records where a coupler is and what it is set to, as it reports itself.
     *
     * <p>A coupler that has stopped being a flagship stops leading: its flotilla is disbanded, and the
     * escorts it had are returned so the caller can tell them. One that has become a flagship takes
     * back any request it had waiting elsewhere, since a flagship cannot follow.
     *
     * @return escorts released because this coupler stopped leading; usually empty
     */
    public List<UUID> track(CouplerRecord record) {
        CouplerRecord previous = couplers.put(record.id(), record);
        if (gone.remove(record.id()) != null) {
            setDirty();
        }
        List<UUID> released = List.of();
        if (record.role() != CouplerRole.FLAGSHIP && flotillas.containsKey(record.id())) {
            released = disband(record.id());
        }
        if (record.role() == CouplerRole.FLAGSHIP) {
            withdraw(record.id());
            UUID lead = following.get(record.id());
            if (lead != null) {
                breakOff(record.id());
            }
            flotillas.computeIfAbsent(record.id(), Flotilla::new);
        }
        if (previous == null || !previous.sameAs(record)) {
            setDirty();
        }
        return released;
    }

    /**
     * Forgets a coupler that has been broken: out of any flotilla it followed, its own flotilla disbanded.
     *
     * @return escorts released because it was leading them
     */
    public List<UUID> forget(UUID id) {
        if (couplers.remove(id) == null && !flotillas.containsKey(id) && !following.containsKey(id)) {
            return List.of();
        }
        List<UUID> released = disband(id);
        breakOff(id);
        withdraw(id);
        gone.remove(id);
        setDirty();
        return released;
    }

    /**
     * Notes that a coupler's block has gone, without forgetting it yet.
     *
     * <p>A block going away is not proof the coupler was broken. Sable's assembler moves blocks into a
     * plot by writing them there and wiping where they stood, and a crossing into another dimension
     * builds the whole ship again before taking the old one away - either of which looks, to the block
     * entity being left behind, exactly like a pickaxe. The copy reports itself within a second or two and
     * {@link #track} takes the mark off; a coupler that really was broken stays marked and {@link #sweep}
     * forgets it.
     *
     * @param pos       where the block was; ignored when the record already names somewhere else,
     *                  because then the copy has reported itself first
     */
    public void markGone(UUID id, ResourceKey<Level> dimension, net.minecraft.core.BlockPos pos, long now) {
        CouplerRecord record = couplers.get(id);
        if (record == null || !record.dimension().equals(dimension) || !record.pos().equals(pos)) {
            return;
        }
        Trace.log(Trace.FLEET, "coupler {} at {} gone - kept for its grace in case a crossing or an assembly is"
                + " moving it", id, Trace.pos(pos));
        gone.put(id, now);
        setDirty();
    }

    /** Whether a coupler is marked as gone and not yet seen again. */
    public boolean isGone(UUID id) {
        return gone.containsKey(id);
    }

    /** A coupler forgotten by {@link #sweep}: what it was, whom it followed, and whom it released. */
    public record Forgotten(CouplerRecord record, @Nullable UUID followed, List<UUID> released) {
    }

    /**
     * Forgets every coupler that has been gone for {@code grace} ticks.
     *
     * @return what was forgotten, so the ships it touched can be told
     */
    public List<Forgotten> sweep(long now, long grace) {
        if (gone.isEmpty()) {
            return List.of();
        }
        List<Forgotten> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : List.copyOf(gone.entrySet())) {
            if (now - entry.getValue() < grace) {
                continue;
            }
            UUID id = entry.getKey();
            gone.remove(id);
            CouplerRecord record = couplers.get(id);
            UUID followed = following.get(id);
            List<UUID> released = forget(id);
            Trace.log(Trace.FLEET, "coupler {} forgotten after {}t gone: it followed {}, released {} escort(s)", id,
                    now - entry.getValue(), followed, released.size());
            if (record != null) {
                out.add(new Forgotten(record, followed, released));
            }
        }
        setDirty();
        return out;
    }

    /**
     * Flagships within reach of a point, nearest first, leaving out one ship's own.
     *
     * @param range     how far to look, in blocks
     * @param ownShip   the asking ship, which is never offered to itself
     */
    public List<CouplerRecord> flagshipsNear(ResourceKey<Level> dimension, double x, double y, double z,
                                             double range, @Nullable UUID ownShip) {
        double reach = range * range;
        List<CouplerRecord> out = new ArrayList<>();
        for (CouplerRecord record : couplers.values()) {
            if (record.role() != CouplerRole.FLAGSHIP || !record.dimension().equals(dimension)
                    || record.ship() == null || record.ship().equals(ownShip)
                    || record.distanceSqr(x, y, z) > reach) {
                continue;
            }
            out.add(record);
        }
        out.sort(Comparator.comparingDouble(record -> record.distanceSqr(x, y, z)));
        return out;
    }

    // ----------------------------------------------------------------- flotillas

    /** The flotilla this coupler leads, or {@code null}. */
    @Nullable
    public Flotilla led(UUID flagship) {
        return flagship == null ? null : flotillas.get(flagship);
    }

    /** The flagship this coupler follows, or {@code null}. */
    @Nullable
    public UUID flagshipOf(UUID escort) {
        return escort == null ? null : following.get(escort);
    }

    /** The flotilla this coupler belongs to, as flagship or escort, or {@code null}. */
    @Nullable
    public Flotilla flotillaOf(UUID coupler) {
        Flotilla led = flotillas.get(coupler);
        if (led != null) {
            return led;
        }
        UUID flagship = following.get(coupler);
        return flagship == null ? null : flotillas.get(flagship);
    }

    /** The flagship this escort has asked to join and is still waiting on, or {@code null}. */
    @Nullable
    public UUID pendingWith(UUID escort) {
        for (Flotilla flotilla : flotillas.values()) {
            if (flotilla.requests().contains(escort)) {
                return flotilla.flagship();
            }
        }
        return null;
    }

    /**
     * Asks to take station with a flagship.
     *
     * <p>Whatever the answer, the escort gives up any request it had waiting with anybody else first: a
     * ship asks one flagship at a time, and asking a second is changing its mind about the first.
     *
     * @param admit take it straight into the flotilla rather than leaving the request for the flagship's
     *              owner - for an owner's own ship with auto-accept on
     * @param slot  where it keeps station if it is taken straight in
     * @param cap   the flagship's escort cap, as configured; negative is unlimited
     */
    public JoinResult request(UUID escort, UUID flagship, boolean admit, FormationSlot slot, int cap) {
        JoinResult result = tryRequest(escort, flagship, admit, slot, cap);
        Trace.log(Trace.FLEET, "escort {} asked to join flagship {}{} -> {} (cap {})", escort, flagship,
                admit ? " (auto-accept)" : "", result, cap < 0 ? "unlimited" : cap);
        return result;
    }

    private JoinResult tryRequest(UUID escort, UUID flagship, boolean admit, FormationSlot slot, int cap) {
        CouplerRecord follower = couplers.get(escort);
        CouplerRecord lead = couplers.get(flagship);
        if (follower == null || lead == null) {
            return JoinResult.UNKNOWN;
        }
        if (follower.role() != CouplerRole.ESCORT) {
            return JoinResult.NOT_AN_ESCORT;
        }
        if (lead.role() != CouplerRole.FLAGSHIP) {
            return JoinResult.NOT_A_FLAGSHIP;
        }
        if (escort.equals(flagship) || follower.ship() != null && follower.ship().equals(lead.ship())) {
            return JoinResult.SAME_SHIP;
        }
        UUID current = following.get(escort);
        if (current != null) {
            return current.equals(flagship) ? JoinResult.ALREADY_MEMBER : JoinResult.IN_ANOTHER;
        }
        Flotilla flotilla = flotillas.computeIfAbsent(flagship, Flotilla::new);
        withdraw(escort);
        if (admit) {
            if (!FlotillaRules.hasRoom(flotilla.escorts().size(), cap)) {
                return JoinResult.FULL;
            }
            join(flotilla, escort, slot);
            return JoinResult.JOINED;
        }
        if (flotilla.requests().size() >= MAX_REQUESTS) {
            return JoinResult.TOO_MANY_REQUESTS;
        }
        flotilla.addRequest(escort);
        setDirty();
        return JoinResult.REQUESTED;
    }

    /** The flagship's owner says yes to a request waiting with them. */
    public JoinResult accept(UUID flagship, UUID escort, FormationSlot slot, int cap) {
        JoinResult result = tryAccept(flagship, escort, slot, cap);
        Trace.log(Trace.FLEET, "flagship {} accepted escort {} -> {}", flagship, escort, result);
        return result;
    }

    private JoinResult tryAccept(UUID flagship, UUID escort, FormationSlot slot, int cap) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla == null || !flotilla.requests().contains(escort)) {
            return JoinResult.UNKNOWN;
        }
        CouplerRecord follower = couplers.get(escort);
        if (follower == null || follower.role() != CouplerRole.ESCORT) {
            flotilla.removeRequest(escort);
            setDirty();
            return JoinResult.UNKNOWN;
        }
        if (following.containsKey(escort)) {
            flotilla.removeRequest(escort);
            setDirty();
            return JoinResult.IN_ANOTHER;
        }
        if (!FlotillaRules.hasRoom(flotilla.escorts().size(), cap)) {
            return JoinResult.FULL;
        }
        flotilla.removeRequest(escort);
        join(flotilla, escort, slot);
        return JoinResult.JOINED;
    }

    /** The flagship's owner says no. */
    public boolean refuse(UUID flagship, UUID escort) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla != null && flotilla.removeRequest(escort)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Casting off: the flagship lets one escort go. */
    public boolean castOff(UUID flagship, UUID escort) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla == null || !flotilla.remove(escort)) {
            return false;
        }
        Trace.log(Trace.FLEET, "flagship {} cast off escort {}", flagship, escort);
        following.remove(escort);
        setDirty();
        return true;
    }

    /** Breaking off: the escort leaves, or takes back a request it had waiting. */
    public boolean breakOff(UUID escort) {
        Trace.log(Trace.FLEET, "escort {} broke off from {}", escort, following.get(escort));
        UUID flagship = following.remove(escort);
        boolean changed = false;
        if (flagship != null) {
            Flotilla flotilla = flotillas.get(flagship);
            if (flotilla != null) {
                flotilla.remove(escort);
            }
            changed = true;
        }
        changed |= withdraw(escort);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Moves an escort's slot. The caller has already checked the new one is clear of the others. */
    public boolean setSlot(UUID flagship, UUID escort, FormationSlot slot) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla == null || !flotilla.setSlot(escort, slot)) {
            return false;
        }
        setDirty();
        return true;
    }

    public void setAutoAccept(UUID flagship, boolean value) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla != null && flotilla.autoAccept() != value) {
            flotilla.setAutoAccept(value);
            setDirty();
        }
    }

    public void setBeaconCallsAll(UUID flagship, boolean value) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla != null && flotilla.beaconCallsAll() != value) {
            flotilla.setBeaconCallsAll(value);
            setDirty();
        }
    }

    public void setSharedRift(UUID flagship, boolean value) {
        Flotilla flotilla = flotillas.get(flagship);
        if (flotilla != null && flotilla.sharedRift() != value) {
            flotilla.setSharedRift(value);
            setDirty();
        }
    }

    /**
     * Ends a flotilla. Every escort is let go, and the requests waiting with it lapse.
     *
     * @return the escorts that were let go
     */
    public List<UUID> disband(UUID flagship) {
        Flotilla flotilla = flotillas.remove(flagship);
        if (flotilla == null) {
            return List.of();
        }
        List<UUID> released = new ArrayList<>();
        for (Flotilla.Member member : flotilla.escorts()) {
            following.remove(member.coupler());
            released.add(member.coupler());
        }
        Trace.log(Trace.FLEET, "flotilla of {} disbanded, {} escort(s) released", flagship, released.size());
        setDirty();
        return released;
    }

    private void join(Flotilla flotilla, UUID escort, FormationSlot slot) {
        flotilla.add(new Flotilla.Member(escort, slot));
        following.put(escort, flotilla.flagship());
        setDirty();
    }

    /** Takes back every request this coupler has waiting anywhere. */
    private boolean withdraw(UUID coupler) {
        boolean any = false;
        for (Flotilla flotilla : flotillas.values()) {
            any |= flotilla.removeRequest(coupler);
        }
        return any;
    }

    // ----------------------------------------------------------------------- nbt

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        for (CouplerRecord record : couplers.values()) {
            records.add(record.save());
        }
        tag.put("Couplers", records);
        ListTag fleets = new ListTag();
        for (Flotilla flotilla : flotillas.values()) {
            fleets.add(flotilla.save());
        }
        tag.put("Flotillas", fleets);
        ListTag missing = new ListTag();
        for (Map.Entry<UUID, Long> entry : gone.entrySet()) {
            CompoundTag mark = new CompoundTag();
            mark.putUUID("Id", entry.getKey());
            mark.putLong("Since", entry.getValue());
            missing.add(mark);
        }
        tag.put("Gone", missing);
        return tag;
    }

    private static FlotillaRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        FlotillaRegistry registry = new FlotillaRegistry();
        ListTag records = tag.getList("Couplers", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            CouplerRecord record = CouplerRecord.load(records.getCompound(i));
            if (record != null) {
                registry.couplers.put(record.id(), record);
            }
        }
        ListTag fleets = tag.getList("Flotillas", Tag.TAG_COMPOUND);
        for (int i = 0; i < fleets.size(); i++) {
            Flotilla flotilla = Flotilla.load(fleets.getCompound(i));
            if (flotilla == null || registry.flotillas.containsKey(flotilla.flagship())) {
                continue;
            }
            registry.flotillas.put(flotilla.flagship(), flotilla);
        }
        ListTag missing = tag.getList("Gone", Tag.TAG_COMPOUND);
        for (int i = 0; i < missing.size(); i++) {
            CompoundTag mark = missing.getCompound(i);
            if (mark.hasUUID("Id") && registry.couplers.containsKey(mark.getUUID("Id"))) {
                registry.gone.put(mark.getUUID("Id"), mark.getLong("Since"));
            }
        }
        // One flotilla per escort, and no escort that is itself leading. Enforced on the way in as well as
        // on every change, because a save is the one place the rules can be broken by something other than
        // this class: a coupler that appears in two flotillas stays in the first it was found in.
        for (Flotilla flotilla : registry.flotillas.values()) {
            for (Flotilla.Member member : List.copyOf(flotilla.escorts())) {
                UUID coupler = member.coupler();
                if (registry.following.containsKey(coupler) || registry.flotillas.containsKey(coupler)) {
                    flotilla.remove(coupler);
                    continue;
                }
                registry.following.put(coupler, flotilla.flagship());
            }
        }
        return registry;
    }
}
