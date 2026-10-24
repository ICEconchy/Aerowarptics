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
    /** A Rift Probe was asked for a sounding it cannot pay for. */
    INSUFFICIENT_ESSENCE("insufficient_essence"),
    /** A player cancelled the sequence. */
    CANCELLED("cancelled"),
    /** The sequence was interrupted, typically by a server restart. */
    INTERRUPTED("interrupted"),
    /** Sable rejected the move. */
    RELOCATION_FAILED("relocation_failed"),

    /**
     * A Rift Beacon was used before it had been pointed at a drive.
     *
     * <p>Appended rather than filed with the other refusals on purpose: these are sent by ordinal
     * over the wire, so inserting a constant anywhere but the end renames every reason after it on
     * any client that has not updated in step.
     */
    BEACON_UNBOUND("beacon_unbound"),
    /** A Rift Beacon's drive is no longer where it was bound, or its plot is not loaded. */
    BEACON_DRIVE_MISSING("beacon_drive_missing"),

    /**
     * The departure path is blocked: the entry aperture, the run at it, or the corridor run flown
     * behind it would fly the hull into terrain. Refused at the mooring, before the rift opens, so
     * the pilot can turn the ship - the bearing is named in the message.
     *
     * <p>Appended for the same reason the two above it were: reasons cross the wire by ordinal.
     */
    NO_CLEAR_LAUNCH("no_clear_launch"),
    /**
     * The landing zone will not stay loaded on its own once the flight's chunk ticket lapses, and
     * {@code arrival.requireLoadedArrival} refuses rather than risk the ship being unloaded and
     * removed where nobody is watching.
     *
     * <p>Off by default now: {@code arrival.keepAirshipsLoaded} has the drive hold its own landing
     * resident instead, so a warp into empty wilderness is kept rather than forbidden. This is
     * reached only when an admin has turned that off and this policy on.
     */
    ARRIVAL_NOT_LOADED("arrival_not_loaded"),

    /**
     * The departure corridor could not be <em>proved</em> clear - chunks missing, or the check ran
     * out of its block budget - as opposed to being found blocked.
     *
     * <p>Refused exactly as {@link #NO_CLEAR_LAUNCH} is, because an unproven corridor is not a safe
     * one, but told apart from it so the two stop looking identical to a pilot. They used to
     * collapse into the same refusal, which is how a large ship over open water came to report a
     * blocked launch with no block anywhere near it.
     *
     * <p>Appended rather than filed beside the other launch reason: these cross the wire by ordinal.
     */
    LAUNCH_UNPROVEN("launch_unproven"),

    /**
     * No landing could be <em>proved</em> clear - the arrival asked for more ground than
     * {@code arrival.maxArrivalChunks} allows, or every place tried ran out of chunks or budget - as
     * opposed to every place being found blocked.
     *
     * <p>The arrival-side twin of {@link #LAUNCH_UNPROVEN}, split from {@link #NO_SAFE_ARRIVAL} for the
     * same reason: "there is nowhere to land" sends a pilot looking for a different anchor, and for a
     * very large hull that was never the problem.
     *
     * <p>Appended: these cross the wire by ordinal.
     */
    ARRIVAL_UNPROVEN("arrival_unproven"),

    /**
     * A crossing into another dimension found no free plot slot there for every part of the vessel.
     *
     * <p>Sable lays a level's sub-levels out on a fixed grid, and a slot stays taken while the ship in
     * it is saved to disk, so this is a full grid rather than a crowded sky. Appended: these cross the
     * wire by ordinal.
     */
    DIMENSION_NO_ROOM("dimension_no_room"),
    /**
     * The hull is taller than the destination dimension's build height, so a crossing would cut the
     * top or the bottom off it. Appended with {@link #DIMENSION_NO_ROOM}.
     */
    DIMENSION_TOO_TALL("dimension_too_tall"),

    /**
     * An escort's drive was stood down because the fold it had joined was called off before its rift
     * opened: the flagship lost power, its own checks refused, or its coupler was taken away.
     *
     * <p>Not {@link #CANCELLED}, which costs charge and a spell in the error state for a pilot who changed
     * their mind. An escort's pilot did not; the flagship did, and the escort's drive keeps what it had.
     * Appended: these cross the wire by ordinal.
     */
    FOLD_CALLED_OFF("fold_called_off"),
    /**
     * A flotilla fold was refused before anything was committed: the flagship's escorts or its coupler
     * were not ready for it - a forced fold with no stations sounded, an empty coupler, a flotilla with
     * no escorts in station. What exactly is on the Fleet Board. Appended.
     */
    FLOTILLA_NOT_READY("flotilla_not_ready"),
    /**
     * A Rift Beacon reached for a ship that is not loaded, loaded the ground it was last seen over, and it
     * did not come back in time - or it was never seen anywhere this version could note it. Appended.
     */
    SHIP_OUT_OF_REACH("ship_out_of_reach"),
    /**
     * Something ties the vessel to what the warp would leave behind - a Docking Connector locked to the
     * ground, a rope, a Sticker, a ship alongside that is not part of it. What exactly, and where, travels
     * with the refusal as a {@link uk.co.iceconchy.aerowarptics.airship.Fastening}; see
     * {@link uk.co.iceconchy.aerowarptics.airship.Fastenings} for how each is found.
     *
     * <p>Refused because a warp cannot be flown with one. The drive could not move a hull held to the
     * ground, so it sat through its whole run at the rift and was then teleported with the joint still
     * holding - and when the joint let go, it dragged the ship back across the whole jump. Free, like the
     * other refusals made before anything moves: the pilot has only to let go. Appended.
     */
    SHIP_FASTENED("ship_fastened"),
    /**
     * A Rift Ward's field turned the rift aside, and nowhere round the field's rim had room for the hull to come
     * out - a base in a canyon, or under a mountain. Refused at the mooring like any arrival with nowhere to
     * land, and the one time a pilot is ever told a destination is warded: a deflection that can be flown is not
     * a refusal, and says nothing until the ship is in the corridor. See
     * {@link uk.co.iceconchy.aerowarptics.ward.Wards}. Appended.
     */
    DESTINATION_WARDED("destination_warded"),

    /**
     * The anchor is a station - it has Berths - and every berth that would take this ship has one in it, or
     * one on its way. A station gives out berths and nothing else: a ship put down loose beside one is the
     * ship that then has to be flown in by hand, which is what a berth is for. Refused at the mooring, free.
     * See {@link uk.co.iceconchy.aerowarptics.berth.Berths}. Appended, as are the three below.
     */
    NO_FREE_BERTH("no_free_berth"),
    /** The station has no berth open to this pilot that the ship fits, empty or not. */
    BERTH_TOO_SMALL("berth_too_small"),
    /** The station has no berth open to this pilot at all: every one is kept for somebody else. */
    BERTH_FORBIDDEN("berth_forbidden"),
    /**
     * A berth would have taken the ship, and something stands in its slip or in the lane the ship would fly
     * down into it - or that could not be proved either way.
     */
    BERTH_OBSTRUCTED("berth_obstructed"),

    /**
     * A rope has an end on the vessel, and the warp crosses into another dimension. A rope is carried on a
     * warp within one dimension - see {@link uk.co.iceconchy.aerowarptics.compat.simulated.CarriedRopes} - but
     * Dimensional Sable rebuilds the vessel in the other level and knows nothing of the strand, nor of what
     * hangs on it. Refused at the mooring, free. Appended.
     */
    DIMENSION_ROPED("dimension_roped");

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
                 DESTINATION_TOO_FAR, DIMENSION_UNSUPPORTED, UNAUTHORISED, AIRSHIP_ALREADY_WARPING,
                 BEACON_UNBOUND, BEACON_DRIVE_MISSING, DIMENSION_NO_ROOM, DIMENSION_TOO_TALL,
                 FOLD_CALLED_OFF, FLOTILLA_NOT_READY, SHIP_OUT_OF_REACH, SHIP_FASTENED,
                 NO_FREE_BERTH, BERTH_TOO_SMALL, BERTH_FORBIDDEN, BERTH_OBSTRUCTED, DIMENSION_ROPED -> false;
            default -> true;
        };
    }

    public static WarpFailure byIndex(int index) {
        WarpFailure[] values = values();
        return index >= 0 && index < values.length ? values[index] : NONE;
    }
}
