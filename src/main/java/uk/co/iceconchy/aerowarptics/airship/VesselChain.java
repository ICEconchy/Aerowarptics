package uk.co.iceconchy.aerowarptics.airship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Everything joined to something, by following what each thing says it is joined to.
 *
 * <p>The walk behind {@link Airship#assemblySubLevels()}, kept free of Sable and Minecraft so that it can be
 * tested without a ship. What makes a sub-level part of a vessel is a machine aboard it naming another - a
 * Swivel Bearing its plate's, the plate its bearing's - and the vessel is everything reached that way from
 * the hull.
 *
 * <p>Sable has a walk of its own, {@code SubLevelHelper.getConnectedChain}, and it reads each plot's list
 * of "actors". That list is the weak link: a machine is only on it if its block has changed since the plot
 * was made, so a bearing that came aboard as part of the hull may never be - and then the turret it carries
 * is not in the chain, and every check that asks "is this another ship in my way" answers yes about the
 * ship's own turret. This walk is handed whatever links the caller can find, which lets {@link Airship}
 * read the machines out of the plot's own chunks as well.
 */
public final class VesselChain {

    private VesselChain() {
    }

    /**
     * Everything reachable from a seed: the seed first, then the rest in the order they were found.
     *
     * <p>Compared by identity, as Sable's sub-levels are everywhere else here. A link to nothing - a
     * bearing whose plate is not loaded - is skipped rather than followed, and a ring of links is walked
     * once.
     *
     * @param links what a member is joined to; {@code null}, and {@code null} entries, mean nothing
     */
    public static <T> List<T> from(T seed, Function<T, ? extends Iterable<? extends T>> links) {
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> chain = new ArrayList<>();
        seen.add(seed);
        chain.add(seed);
        // The list is its own queue: everything past the cursor has been found and not yet asked.
        for (int cursor = 0; cursor < chain.size(); cursor++) {
            Iterable<? extends T> joined = links.apply(chain.get(cursor));
            if (joined == null) {
                continue;
            }
            for (T other : joined) {
                if (other != null && seen.add(other)) {
                    chain.add(other);
                }
            }
        }
        return chain;
    }
}
