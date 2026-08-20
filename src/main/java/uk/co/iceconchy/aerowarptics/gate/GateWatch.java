package uk.co.iceconchy.aerowarptics.gate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which side of a gate everything near it was on, last time the gate looked.
 *
 * <p>A crossing is a change of side between two sightings, and the sightings are the gate's own. That
 * matters more than it sounds. Working it out from an entity's previous position instead means relying
 * on when in the tick the entity happened to move - which for a player is whenever their packet
 * arrived - so a crossing inferred that way is one that sometimes simply is not noticed, and a gate
 * that sometimes does nothing is a gate players stop trusting.
 *
 * <p>Kept apart from the block entity because the rules are small, exact, and were wrong once: a first
 * sighting is never a crossing, and anything that leaves is forgotten rather than remembered on the
 * wrong side of a plane it never went through.
 */
public final class GateWatch {

    private final Map<UUID, Boolean> sides = new HashMap<>();

    /**
     * Notes where something is, and says whether it has just gone through.
     *
     * <p>The first time anything is seen the answer is always no. Something that appears already past
     * the plane - walked into range from the side, arrived through the gate, or was loaded in with the
     * chunk - has not crossed it, and treating that as a crossing would send it somewhere it never
     * asked to go.
     *
     * @param side which side of the plane it is on now
     * @return {@code true} only when this is a different side from the last sighting
     */
    public boolean stepped(UUID id, boolean side) {
        Boolean previous = sides.put(id, side);
        return previous != null && previous != side;
    }

    /**
     * Stops watching something.
     *
     * <p>Used the moment a traveller is sent through, so the far end of their journey is a fresh
     * arrival rather than half of a crossing recorded on this side.
     */
    public void forget(UUID id) {
        sides.remove(id);
    }

    /** Forgets everything not in this tick's sweep, so walking away and back is a fresh approach. */
    public void retain(Set<UUID> seen) {
        sides.keySet().retainAll(seen);
    }

    public void clear() {
        sides.clear();
    }

    /** How many things are being watched, for tests and for goggles that might want to say. */
    public int watched() {
        return sides.size();
    }
}
