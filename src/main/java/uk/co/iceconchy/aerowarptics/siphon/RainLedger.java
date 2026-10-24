package uk.co.iceconchy.aerowarptics.siphon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Who has taken how much of each second's rift rain.
 *
 * <p>A Rift Storm's rain is a budget, not a flow into each vessel: so many millibuckets a second for every
 * chunk of open ground, and for every ship, shared between whatever siphons stand in it. The first vessel to
 * ask in a second takes what it has room for and the rest share what is left - exactly as a fissure's drain is
 * shared - so a ring of siphons fills no faster than one. Free of Minecraft, so that is a test rather than a
 * promise.
 */
public final class RainLedger {

    /** key -> {the second it was last drawn in, how much was drawn in that second} */
    private final Map<Object, long[]> drawn = new HashMap<>();

    /**
     * Takes rain for one vessel.
     *
     * @param key    what the budget belongs to: a chunk, or a ship
     * @param second which second of the storm it is
     * @param room   what the vessel can hold
     * @param budget millibuckets the key's rain is worth in a second
     * @return what the vessel gets, never more than is left of the second's budget
     */
    public int take(Object key, long second, int room, int budget) {
        long[] entry = drawn.get(key);
        if (entry == null || entry[0] != second) {
            entry = new long[]{second, 0L};
            drawn.put(key, entry);
        }
        int left = (int) Math.max(0L, budget - entry[1]);
        int amount = Math.max(0, Math.min(left, room));
        entry[1] += amount;
        return amount;
    }

    /** Forgets every key not drawn from since before {@code second}, so the ledger does not grow for ever. */
    public void forgetBefore(long second) {
        Iterator<long[]> entries = drawn.values().iterator();
        while (entries.hasNext()) {
            if (entries.next()[0] < second) {
                entries.remove();
            }
        }
    }

    public int size() {
        return drawn.size();
    }
}
