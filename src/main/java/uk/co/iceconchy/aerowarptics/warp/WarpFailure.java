package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.util.StringRepresentable;

/**
 * Every reason a warp can be refused or abandoned.
 *
 * <p>The server produces these; the client only renders them, so a client can never invent a
 * "success". {@link #NONE} means no failure.
 */
public enum WarpFailure implements StringRepresentable {

    NONE("none"),

    /** The Rift Drive is not turning fast enough to hold a rift open. */
    INSUFFICIENT_POWER("insufficient_power"),
    /** The drive has stored charge but not enough for this particular jump. */
    INSUFFICIENT_CHARGE("insufficient_charge"),
    /** The drive is mid-warp, cooling down, or in an error state. */
    DRIVE_BUSY("drive_busy"),
    /** The drive is not attached to an assembled airship. */
    NO_AIRSHIP("no_airship"),
    /** The airship went away mid-sequence: disassembled, split or unloaded. */
    AIRSHIP_LOST("airship_lost"),
    /** Another Rift Drive on the same airship already holds the warp lock. */
    AIRSHIP_ALREADY_WARPING("airship_already_warping"),

    /** No anchor with that id is registered. */
    ANCHOR_MISSING("anchor_missing"),
    /** The anchor exists but its owner switched it off. */
    ANCHOR_DISABLED("anchor_disabled"),
    /** The anchor is private and the player is not its owner. */
    ANCHOR_FORBIDDEN("anchor_forbidden"),
    /** The anchor's chunk could not be loaded. */
    ANCHOR_UNAVAILABLE("anchor_unavailable"),
    /** The anchor is the airship's current position, or close enough not to matter. */
    DESTINATION_TOO_CLOSE("destination_too_close"),
    /** Beyond this drive tier's reach, or beyond the global limit. */
    DESTINATION_TOO_FAR("destination_too_far"),
    /** The anchor is in another dimension and cross-dimension warping is unavailable. */
    DIMENSION_UNSUPPORTED("dimension_unsupported"),

    /** No clear volume large enough for the airship was found near the anchor. */
    NO_SAFE_ARRIVAL("no_safe_arrival"),
    /** The player is not aboard, not close enough, or lacks permission. */
    UNAUTHORISED("unauthorised"),
    /** A player cancelled the sequence. */
    CANCELLED("cancelled"),
    /** The sequence was interrupted, typically by a server restart. */
    INTERRUPTED("interrupted"),
    /** Sable rejected the move. */
    RELOCATION_FAILED("relocation_failed");

    private final String name;

    WarpFailure(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Namespace-relative key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "warp.failure." + name;
    }

    public boolean isFailure() {
        return this != NONE;
    }

    /** Aborted warps that should still cost the pilot charge and a cooldown. */
    public boolean penalised() {
        return switch (this) {
            case NONE, INSUFFICIENT_POWER, INSUFFICIENT_CHARGE, DRIVE_BUSY, NO_AIRSHIP,
                 ANCHOR_MISSING, ANCHOR_DISABLED, ANCHOR_FORBIDDEN, DESTINATION_TOO_CLOSE,
                 DESTINATION_TOO_FAR, DIMENSION_UNSUPPORTED, UNAUTHORISED, AIRSHIP_ALREADY_WARPING -> false;
            default -> true;
        };
    }

    public static WarpFailure byIndex(int index) {
        WarpFailure[] values = values();
        return index >= 0 && index < values.length ? values[index] : NONE;
    }
}
