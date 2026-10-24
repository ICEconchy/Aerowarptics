package uk.co.iceconchy.aerowarptics.rail;

import java.util.Collection;

/**
 * Where each lane's corridor lies in the Rift Corridor dimension.
 *
 * <p>Pure arithmetic. A slot is a number; its corridor runs east along X from {@code x = 0} at a fixed height,
 * one slot every {@link #SPACING} blocks down Z. Corridors are far enough apart that a rider in one cannot see
 * the next at any ordinary render distance - an empty void with somebody else's train going past in it would
 * give the trick away.
 *
 * <p>{@link #SPACING} is a constant rather than a setting on purpose. A lane remembers its slot, not its
 * coordinates, so changing the spacing would move every corridor already laid out from under its track.
 */
public final class CorridorSlots {

    /** Blocks between one corridor and the next, down Z. */
    public static final int SPACING = 256;

    /** The height every corridor's track stands at. */
    public static final int TRACK_Y = 64;

    /**
     * How many slots there can be. Kept well inside the default world border (29,999,984 blocks out), with
     * room to spare for a border somebody has drawn in a little.
     */
    public static final int MAX_SLOTS = 100_000;

    private CorridorSlots() {
    }

    /** The Z every block of a slot's corridor stands at. */
    public static int z(int slot) {
        return slot * SPACING + 8;
    }

    /** How far either side of a corridor's line something still counts as being on it. */
    public static final int REACH = 4;

    /** The slot a Z in the corridor belongs to, or {@code -1} if it is not near any corridor's line. */
    public static int slotAt(double z) {
        long slot = Math.round((z - 8.0D - 0.5D) / SPACING);
        if (slot < 0 || slot >= MAX_SLOTS) {
            return -1;
        }
        double line = z((int) slot) + 0.5D;
        return Math.abs(z - line) <= REACH ? (int) slot : -1;
    }

    /**
     * The lowest slot nobody is using.
     *
     * @return the slot, or {@code -1} when every slot is taken
     */
    public static int allocate(Collection<Integer> used) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /** The X of the mouth block at the west end, which the west track takes for a portal. */
    public static int westMouth() {
        return 0;
    }

    /** The X of the track bound to the near gate. Create lays it; the corridor leaves it empty for that. */
    public static int westTrack() {
        return 1;
    }

    /** The X of the track bound to the far gate's stub. */
    public static int eastTrack(int length) {
        return length - 1;
    }

    /** The X of the mouth block at the east end. */
    public static int eastMouth(int length) {
        return length;
    }
}
