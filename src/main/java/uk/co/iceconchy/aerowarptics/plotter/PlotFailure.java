package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Why a Rift Plotter would not open, or why it let something go past, or why a rift ended early.
 *
 * <p>The refusals before a charge are found before any essence is drawn; the ones at the far end are found before
 * anything moves, and leave the charge in the machine for the next plot. Checked in the order declared, which is
 * the order a player fixes things in.
 *
 * <p>Appended to, never reordered: the panel carries the ordinal.
 */
public enum PlotFailure implements StringRepresentable {

    NONE,
    /** The server has plotters switched off. */
    DISABLED,
    /** Already charging or open. */
    BUSY,
    /** No target has ever been set. */
    NO_TARGET,
    /** Turned slower than the plotter's minimum speed. */
    NOT_TURNING,
    /** The target is past the world border, or the exit above or below what can be built on. */
    OUTSIDE_WORLD,
    /** The far end never loaded in time to be read. */
    FAR_END_UNREACHED,
    /** Most of the exit's disc is in the ground: nothing could come out of it. */
    EXIT_BURIED,
    /** Most of the entry's disc is in the ground near the plotter: nothing could go into it. */
    ENTRY_BURIED,
    /** The shaft slowed below the minimum while the rift stood open, and it could not be held. */
    LOST_ROTATION,
    /** A vehicle wider than the rift was turned back. */
    TOO_LARGE,
    /** A vehicle tied to the ground, or to something outside its own vessel, was turned back. */
    FASTENED,
    /** A lane ran out of essence to hold itself open, and closed. */
    LANE_STARVED;

    public boolean isFailure() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "plotter.failure." + getSerializedName();
    }

    public static PlotFailure byIndex(int index) {
        PlotFailure[] values = values();
        return index >= 0 && index < values.length ? values[index] : NONE;
    }

    public static PlotFailure byName(String name) {
        for (PlotFailure failure : values()) {
            if (failure.getSerializedName().equals(name)) {
                return failure;
            }
        }
        return NONE;
    }
}
