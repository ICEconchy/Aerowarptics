package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One flotilla: a flagship, the escorts that have taken station with it, and who is waiting to.
 *
 * <p>Held by {@link FlotillaRegistry}, which is the only thing that changes it; everything else reads
 * it. Known by its flagship's coupler id, because a flotilla is nothing but the ships gathered round one
 * flagship and ends when that flagship stops leading.
 *
 * <p>Flat, deliberately: an escort cannot lead escorts of its own. A fold is one flagship proving the
 * ground for everybody at once, and a formation of formations would need that proof to reach through
 * ships that are themselves only placed relative to other ships.
 */
public final class Flotilla {

    /** An escort that has taken station, and where it keeps station. */
    public record Member(UUID coupler, FormationSlot slot) {
    }

    private final UUID flagship;
    private final List<Member> escorts = new ArrayList<>();
    /** Couplers waiting on the flagship's owner, oldest first. */
    private final List<UUID> requests = new ArrayList<>();
    /** Take ships whose coupler this flagship's owner placed without asking. */
    private boolean autoAccept = true;
    /** Whether a Rift Beacon bound to this flagship calls the whole flotilla or the flagship alone. */
    private boolean beaconCallsAll = true;
    /**
     * Whether the flotilla folds through one rift the flagship tears for all of it, rather than each ship
     * through its own. Off for a new flotilla: one rift asks the fleet to move as one, and a pilot should
     * choose that - see {@link uk.co.iceconchy.aerowarptics.warp.FleetPassage}.
     */
    private boolean sharedRift;

    Flotilla(UUID flagship) {
        this.flagship = flagship;
    }

    public UUID flagship() {
        return flagship;
    }

    public List<Member> escorts() {
        return Collections.unmodifiableList(escorts);
    }

    public List<UUID> requests() {
        return Collections.unmodifiableList(requests);
    }

    public boolean autoAccept() {
        return autoAccept;
    }

    public boolean beaconCallsAll() {
        return beaconCallsAll;
    }

    public boolean sharedRift() {
        return sharedRift;
    }

    @Nullable
    public Member member(UUID coupler) {
        for (Member member : escorts) {
            if (member.coupler().equals(coupler)) {
                return member;
            }
        }
        return null;
    }

    public boolean has(UUID coupler) {
        return member(coupler) != null;
    }

    /** Every escort's slot, in the order the escorts took station. */
    public List<FormationSlot> slots() {
        List<FormationSlot> out = new ArrayList<>();
        for (Member member : escorts) {
            out.add(member.slot());
        }
        return out;
    }

    // ---------------------------------------------------------- changed by the registry only

    void add(Member member) {
        escorts.add(member);
    }

    boolean remove(UUID coupler) {
        return escorts.removeIf(member -> member.coupler().equals(coupler));
    }

    boolean setSlot(UUID coupler, FormationSlot slot) {
        for (int i = 0; i < escorts.size(); i++) {
            if (escorts.get(i).coupler().equals(coupler)) {
                escorts.set(i, new Member(coupler, slot));
                return true;
            }
        }
        return false;
    }

    void addRequest(UUID coupler) {
        if (!requests.contains(coupler)) {
            requests.add(coupler);
        }
    }

    boolean removeRequest(UUID coupler) {
        return requests.remove(coupler);
    }

    void setAutoAccept(boolean value) {
        autoAccept = value;
    }

    void setBeaconCallsAll(boolean value) {
        beaconCallsAll = value;
    }

    void setSharedRift(boolean value) {
        sharedRift = value;
    }

    // --------------------------------------------------------------------- nbt

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Flagship", flagship);
        ListTag members = new ListTag();
        for (Member member : escorts) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Coupler", member.coupler());
            entry.put("Slot", member.slot().save());
            members.add(entry);
        }
        tag.put("Escorts", members);
        ListTag waiting = new ListTag();
        for (UUID request : requests) {
            waiting.add(NbtUtils.createUUID(request));
        }
        tag.put("Requests", waiting);
        tag.putBoolean("AutoAccept", autoAccept);
        tag.putBoolean("BeaconCallsAll", beaconCallsAll);
        tag.putBoolean("SharedRift", sharedRift);
        return tag;
    }

    @Nullable
    static Flotilla load(CompoundTag tag) {
        if (!tag.hasUUID("Flagship")) {
            return null;
        }
        Flotilla flotilla = new Flotilla(tag.getUUID("Flagship"));
        ListTag members = tag.getList("Escorts", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag entry = members.getCompound(i);
            if (entry.hasUUID("Coupler")) {
                UUID coupler = entry.getUUID("Coupler");
                // A coupler listed twice - a hand-edited save, or an older bug - keeps its first slot.
                if (!flotilla.has(coupler) && !coupler.equals(flotilla.flagship)) {
                    flotilla.escorts.add(new Member(coupler, FormationSlot.load(entry.getCompound("Slot"))));
                }
            }
        }
        ListTag waiting = tag.getList("Requests", Tag.TAG_INT_ARRAY);
        for (Tag request : waiting) {
            UUID coupler = NbtUtils.loadUUID(request);
            if (!flotilla.has(coupler)) {
                flotilla.addRequest(coupler);
            }
        }
        // Absent on nothing yet, but read as the defaults a new flotilla has rather than as false.
        flotilla.autoAccept = !tag.contains("AutoAccept") || tag.getBoolean("AutoAccept");
        flotilla.beaconCallsAll = !tag.contains("BeaconCallsAll") || tag.getBoolean("BeaconCallsAll");
        // Off when absent, which is also what a new flotilla is.
        flotilla.sharedRift = tag.getBoolean("SharedRift");
        return flotilla;
    }
}
