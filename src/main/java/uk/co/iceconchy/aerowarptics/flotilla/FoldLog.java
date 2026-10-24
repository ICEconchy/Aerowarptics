package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every fold in flight, from the moment it is committed to the moment its last ship is out - or has
 * failed to be.
 *
 * <p>Kept by the {@link FlotillaRegistry} and not by the flagship's coupler, because a fold outlives the
 * block entity that started it: a crossing into another dimension rebuilds the flagship, coupler and all,
 * and the new coupler knows nothing of the muster the old one ran. Each drive reports its own arrival
 * here, by the fold's id from its {@link FoldOrder}, from wherever it came out.
 *
 * <p>Never saved. A fold does not survive a restart; a record of one would only be a record of ships
 * whose sequences were dropped on the floor. Pure, apart from the ids and keys it holds, so the rules for
 * when a flotilla counts as assembled are tested directly.
 */
public final class FoldLog {

    /** How long a fold is remembered at most, whatever became of it. Longer than any flight. */
    public static final long FORGET_TICKS = 20L * 60L * 5L;

    /**
     * The cues of a fold through one rift that every ship reaches on the same tick.
     *
     * <p>The fleet's rift is one aperture, and a client matches a cue to an aperture by where it is: told
     * to open it by every ship, a client stands one on top of another in the same place. So each of these
     * is sent by whichever ship gets there first, and not by the rest. The flagship opens the entry itself,
     * being the only ship there when it opens.
     */
    public enum SharedCue {
        /** The formation reaching the entry: it jumps open and its throat grows. */
        ENTRY_TRANSIT,
        /** The far end opening, as the formation enters the corridor. */
        EXIT_OPEN,
        /** The formation reappearing behind the far end. Each crew is still told by its own ship. */
        EXIT_TRANSIT
    }

    /**
     * What a ship stopping short in a fold through one rift leaves behind it: whether it was the last the
     * entry, or the exit, was still waiting for - in which case it closes that one on its way out.
     */
    public record Closing(boolean entry, boolean exit) {

        public static final Closing NOTHING = new Closing(false, false);
    }

    /** Where one ship came out, and who was aboard. */
    public record Arrival(UUID coupler, ResourceKey<Level> dimension, BlockPos couplerPos, List<UUID> crew,
                          boolean echo) {
    }

    /** One fold. */
    public static final class Fold {
        private final UUID id;
        private final UUID flagship;
        private final Set<UUID> expected;
        private final Set<UUID> echoes = new HashSet<>();
        private final Map<UUID, Arrival> arrived = new LinkedHashMap<>();
        private final Set<UUID> failed = new HashSet<>();
        private final int leftBehind;
        private final boolean full;
        private final long started;
        private boolean assembled;
        /**
         * For a fold through one rift: the ships still to get through its entry, and still to come out of
         * its exit. Each end is closed by the ship that empties its set, so neither closes on a hull still
         * inside it, whichever order the drives happen to tick in.
         */
        private boolean shared;
        private final Set<UUID> entering = new HashSet<>();
        private final Set<UUID> emerging = new HashSet<>();
        private final EnumSet<SharedCue> cued = EnumSet.noneOf(SharedCue.class);

        Fold(UUID id, UUID flagship, Collection<UUID> escorts, int leftBehind, boolean full, long started) {
            this.id = id;
            this.flagship = flagship;
            this.expected = new HashSet<>(escorts);
            this.expected.add(flagship);
            this.leftBehind = leftBehind;
            this.full = full;
            this.started = started;
        }

        public UUID id() {
            return id;
        }

        public UUID flagship() {
            return flagship;
        }

        /** Ships that set off, the flagship among them. */
        public int ships() {
            return expected.size();
        }

        public boolean full() {
            return full;
        }

        public int leftBehind() {
            return leftBehind;
        }

        public Collection<Arrival> arrivals() {
            return arrived.values();
        }

        public boolean anyFailed() {
            return !failed.isEmpty();
        }

        public boolean anyShaken() {
            return !echoes.isEmpty();
        }

        /** Whether this fold goes through one rift. */
        public boolean shared() {
            return shared;
        }

        /** Whether a straggler has still to come out of its Wake Echo. */
        public boolean stragglersDue() {
            for (UUID echo : echoes) {
                if (!arrived.containsKey(echo) && !failed.contains(echo)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Whether the fleet is out: the flagship arrived, and every ship that was not shaken loose has
         * either arrived or failed. Stragglers are not waited for - that is the whole point of them.
         */
        boolean assemblable() {
            if (!arrived.containsKey(flagship)) {
                return false;
            }
            for (UUID ship : expected) {
                if (!echoes.contains(ship) && !arrived.containsKey(ship) && !failed.contains(ship)) {
                    return false;
                }
            }
            return true;
        }

        /** Whether every ship has come out or failed, so nothing more will be heard of this fold. */
        boolean finished() {
            for (UUID ship : expected) {
                if (!arrived.containsKey(ship) && !failed.contains(ship)) {
                    return false;
                }
            }
            return true;
        }

        /** "Nobody Left Behind": three ships or more, every one of them out in its slot, none left or lost. */
        public boolean nobodyLeftBehind() {
            return ships() >= 3 && echoes.isEmpty() && failed.isEmpty() && leftBehind == 0
                    && arrived.size() == expected.size();
        }

        /** "Fleet in Being": as many escorts as the flagship's drive can lead, all of them out. */
        public boolean fleetInBeing() {
            return full && failed.isEmpty() && arrived.size() == expected.size();
        }
    }

    /**
     * What an arrival or a failure changed, for the caller to announce.
     *
     * @param assembledNow the fold, if this report is the one that brought the fleet together
     * @param straggler    the arrival, if it came out of a Wake Echo
     * @param finishedNow  the fold, if this was the last ship to be accounted for
     */
    public record Outcome(@Nullable Fold assembledNow, @Nullable Arrival straggler, @Nullable Fold finishedNow) {

        public static final Outcome NOTHING = new Outcome(null, null, null);
    }

    private final Map<UUID, Fold> folds = new HashMap<>();

    public Fold begin(UUID id, UUID flagship, Collection<UUID> escorts, int leftBehind, boolean full, long now) {
        Fold fold = new Fold(id, flagship, escorts, leftBehind, full, now);
        folds.put(id, fold);
        return fold;
    }

    @Nullable
    public Fold fold(UUID id) {
        return folds.get(id);
    }

    /** An escort was shaken loose: it is still coming, later, and not waited for. */
    public void shaken(UUID id, UUID coupler) {
        Fold fold = folds.get(id);
        if (fold != null) {
            fold.echoes.add(coupler);
        }
    }

    /** An escort never went after all: its drive was stood down or failed before its rift opened. */
    public Outcome failed(UUID id, UUID coupler) {
        Fold fold = folds.get(id);
        if (fold == null || !fold.expected.contains(coupler)) {
            return Outcome.NOTHING;
        }
        fold.failed.add(coupler);
        fold.entering.remove(coupler);
        fold.emerging.remove(coupler);
        return settle(fold, null);
    }

    // ------------------------------------------------------------ one rift

    /**
     * The fold goes through one rift, and these ships are going through it: the flagship, and every escort
     * that was not shaken loose. A straggler tears its own and is not waited for here either.
     */
    public void share(UUID id, Collection<UUID> ships) {
        Fold fold = folds.get(id);
        if (fold == null) {
            return;
        }
        fold.shared = true;
        for (UUID ship : ships) {
            if (fold.expected.contains(ship) && !fold.failed.contains(ship)) {
                fold.entering.add(ship);
                fold.emerging.add(ship);
            }
        }
    }

    /**
     * Whether a ship is the first to reach a cue the whole fold shares, and so the one to send it.
     *
     * <p>A fold nobody remembers answers yes. Only a fold that has run past {@link #FORGET_TICKS} is
     * forgotten with ships still in it, and a cue sent twice is a far smaller fault than one never sent.
     */
    public boolean firstTo(UUID id, SharedCue cue) {
        Fold fold = folds.get(id);
        return fold == null || fold.cued.add(cue);
    }

    /** A ship is through the fold's entry. Whether it was the last, so that the entry may close behind it. */
    public boolean throughEntry(UUID id, UUID coupler) {
        Fold fold = folds.get(id);
        return fold == null || fold.entering.remove(coupler) && fold.entering.isEmpty();
    }

    /** A ship is out of the fold's exit. Whether it was the last, so that the exit may close behind it. */
    public boolean outOfExit(UUID id, UUID coupler) {
        Fold fold = folds.get(id);
        return fold == null || fold.emerging.remove(coupler) && fold.emerging.isEmpty();
    }

    /**
     * A ship in a fold through one rift has stopped short, wherever it was: before its flight began, on its
     * run at the rift, or inside either end. Whatever of the rift it had still to pass is no longer waiting
     * for it.
     */
    public Closing leave(UUID id, UUID coupler) {
        Fold fold = folds.get(id);
        if (fold == null) {
            // Forgotten with a ship still in it: close both ends rather than leave them to their own clocks.
            return new Closing(true, true);
        }
        if (!fold.shared) {
            return Closing.NOTHING;
        }
        boolean entry = fold.entering.remove(coupler) && fold.entering.isEmpty();
        boolean exit = fold.emerging.remove(coupler) && fold.emerging.isEmpty();
        return new Closing(entry, exit);
    }

    public Outcome arrived(UUID id, Arrival arrival) {
        Fold fold = folds.get(id);
        if (fold == null || !fold.expected.contains(arrival.coupler())) {
            return Outcome.NOTHING;
        }
        fold.arrived.put(arrival.coupler(), arrival);
        return settle(fold, arrival.echo() ? arrival : null);
    }

    private Outcome settle(Fold fold, @Nullable Arrival straggler) {
        Fold assembledNow = null;
        if (!fold.assembled && fold.assemblable()) {
            fold.assembled = true;
            assembledNow = fold;
        }
        Fold finishedNow = null;
        if (fold.finished()) {
            folds.remove(fold.id);
            finishedNow = fold;
        }
        return new Outcome(assembledNow, straggler, finishedNow);
    }

    /** Lets go of folds nobody has heard from in {@link #FORGET_TICKS}. */
    public void expire(long now) {
        List<UUID> stale = new ArrayList<>();
        for (Fold fold : folds.values()) {
            if (now - fold.started > FORGET_TICKS) {
                stale.add(fold.id);
            }
        }
        stale.forEach(folds::remove);
    }

    public int open() {
        return folds.size();
    }
}
