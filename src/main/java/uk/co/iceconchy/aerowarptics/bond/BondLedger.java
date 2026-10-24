package uk.co.iceconchy.aerowarptics.bond;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which Rift Bonds are standing, by the bond they share.
 *
 * <p>A bond is a name two or more blocks have in common and nothing else - there is no joint between them
 * for Sable to walk, which is the whole point of the block. So the only way from one bonded section of a
 * ship to the rest is to ask who else is carrying the same name, and this is who is asked. Kept free of
 * Minecraft, like {@link uk.co.iceconchy.aerowarptics.airship.VesselChain}, so the bookkeeping can be
 * tested without a world.
 *
 * <p>Members are compared by identity. A block entity that is unloaded and loaded again is a new object,
 * and the old one has to be gone when it is.
 *
 * @param <T> what stands in a bond - a block entity, in the game
 */
public final class BondLedger<T> {

    private final Map<UUID, Set<T>> standing = new HashMap<>();

    /** Records a member under a bond. Doing it twice is doing it once. */
    public void join(UUID bond, T member) {
        standing.computeIfAbsent(bond, key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(member);
    }

    /** Takes a member out of a bond, and forgets the bond when nobody is left in it. */
    public void leave(UUID bond, T member) {
        Set<T> members = standing.get(bond);
        if (members != null && members.remove(member) && members.isEmpty()) {
            standing.remove(bond);
        }
    }

    /** Everybody standing in a bond, the asker included. A copy, so it may be walked while members leave. */
    public List<T> members(UUID bond) {
        Set<T> members = standing.get(bond);
        return members == null ? List.of() : List.copyOf(members);
    }

    /** How many bonds have anybody in them. */
    public int bonds() {
        return standing.size();
    }

    /** Forgets everything - a server stopping, so that nothing of one world is asked about in the next. */
    public void clear() {
        standing.clear();
    }
}
