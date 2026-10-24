package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Where a Rift Plotter is in a strike.
 *
 * <p>{@link #CHARGING} draws essence and holds the far end's ground while it comes off the disk; {@link #REACHING}
 * is the same machine paid for and still waiting on the far end. {@link #OPEN} is both rifts standing, carrying
 * whatever crosses the entry. {@link #CLOSING} is the tear healing and the rings running down, after which it is
 * {@link #IDLE} again. Appended to, never reordered: the panel carries the ordinal.
 */
public enum PlotterState implements StringRepresentable {

    IDLE,
    CHARGING,
    REACHING,
    OPEN,
    CLOSING;

    /** Whether a strike is under way, from the first essence drawn to the tear healed. */
    public boolean busy() {
        return this != IDLE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "plotter.state." + getSerializedName();
    }

    public static PlotterState byIndex(int index) {
        PlotterState[] values = values();
        return index >= 0 && index < values.length ? values[index] : IDLE;
    }

    public static PlotterState byName(String name) {
        for (PlotterState state : values()) {
            if (state.getSerializedName().equals(name)) {
                return state;
            }
        }
        return IDLE;
    }
}
