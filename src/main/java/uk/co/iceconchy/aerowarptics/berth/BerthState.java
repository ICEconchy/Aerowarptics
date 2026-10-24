package uk.co.iceconchy.aerowarptics.berth;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a berth is doing, which is also what its lamp shows and what a comparator reads off it.
 *
 * <p>Saved by name; sent by ordinal, so a new state is appended.
 */
public enum BerthState implements StringRepresentable {

    /** Nothing in the slip and nothing on its way. */
    EMPTY(0),
    /** A warp has been planned into the slip and has not arrived. */
    RESERVED(4),
    /** A ship flown in by hand is being drawn to its mark. */
    DRAWING(8),
    /** A ship is held in the slip. */
    MOORED(15);

    private final int signal;

    BerthState(int signal) {
        this.signal = signal;
    }

    /**
     * What a comparator reads. Fifteen means exactly "a ship is held here", the bargain
     * {@code DriveSignal} keeps with fourteen: a circuit waiting to unload a ship must not be shown it a
     * tick early.
     */
    public int signal() {
        return signal;
    }

    /** Whether a ship is in the berth's keeping - held, or on its way to being. */
    public boolean holds() {
        return this == DRAWING || this == MOORED;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BerthState byName(String name) {
        for (BerthState state : values()) {
            if (state.name().equalsIgnoreCase(name)) {
                return state;
            }
        }
        return EMPTY;
    }

    public static BerthState byIndex(int index) {
        BerthState[] values = values();
        return index >= 0 && index < values.length ? values[index] : EMPTY;
    }
}
