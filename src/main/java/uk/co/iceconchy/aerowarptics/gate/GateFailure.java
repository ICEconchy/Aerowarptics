package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.util.StringRepresentable;

/**
 * Why a gate will not do what it was asked.
 *
 * <p>Separate from {@code WarpFailure} rather than bolted onto it. A drive and a gate refuse for
 * almost entirely different reasons, and a shared enum would end up as a list where two thirds of the
 * entries are impossible for whichever machine is reading it - which is how a refusal ends up being
 * reported as something that makes no sense.
 */
public enum GateFailure implements StringRepresentable {

    NONE("none"),

    /** The ring is broken, blocked, or nothing was ever built. */
    NOT_FORMED("not_formed"),
    /** Nothing dialled. */
    NO_DESTINATION("no_destination"),
    /** The far gate is gone from the registry. */
    DESTINATION_MISSING("destination_missing"),
    /** The far gate exists but has been switched off by its owner. */
    DESTINATION_DISABLED("destination_disabled"),
    /** The far gate is already holding a connection to somewhere else. */
    DESTINATION_BUSY("destination_busy"),
    /** The far gate's ring is broken, or its chunks hold something other than a gate. */
    DESTINATION_UNFORMED("destination_unformed"),
    /** The far gate is in another dimension, which no vehicle could follow anybody through. */
    DESTINATION_ANOTHER_DIMENSION("destination_another_dimension"),
    /** Dialling itself. */
    DESTINATION_IS_SELF("destination_is_self"),

    /** Not enough Rift Essence to strike the connection. */
    INSUFFICIENT_ESSENCE("insufficient_essence"),
    /** Not turning fast enough to hold the aperture open. */
    INSUFFICIENT_POWER("insufficient_power"),

    /** The player may not dial that gate. */
    UNAUTHORISED("unauthorised"),
    /** Whatever tried to cross is bigger than the opening at one end or the other. */
    TOO_LARGE("too_large"),
    /**
     * The far gate is aboard a ship that is not loaded, and the dial has just started bringing it in.
     * Dialled again once it is there, it connects. Appended.
     */
    DESTINATION_LOADING("destination_loading"),
    /**
     * Whatever tried to cross is tied to something it would leave behind - a Docking Connector locked to the
     * ground, a rope, a Sticker. The gate carries a vehicle and everything joined to it, but not the ground,
     * and not a line whose middle it cannot move. Appended.
     */
    FASTENED("fastened"),

    // Rail gates. Appended, every one: a failure goes over the wire by its ordinal.

    /** Track was run into a gate that has no route to send a train along. */
    RAIL_NO_ROUTE("rail_no_route"),
    /** Track was run into a gate aboard a ship. Create track in a ship's plot is not something this can bind. */
    RAIL_ABOARD_SHIP("rail_aboard_ship"),
    /** Something is standing where the track out of the far gate has to go. */
    RAIL_FAR_BLOCKED("rail_far_blocked"),
    /** The track points into a cell with more opening below it: a lane runs along the bottom of a gate. */
    RAIL_NOT_BOTTOM_ROW("rail_not_bottom_row"),
    /** The gate already has as many lanes as the server allows. */
    RAIL_TOO_MANY_LANES("rail_too_many_lanes"),
    /** Another lane already comes out of the far gate where this one would. */
    RAIL_FAR_LANE_TAKEN("rail_far_lane_taken"),
    /** A train is part of the way through, and the gate will not be shut on it or turned elsewhere. */
    RAIL_TRAIN_IN_TRANSIT("rail_train_in_transit"),
    /** A carriage too long for the corridor: it would be in three dimensions at once, which Create refuses. */
    RAIL_CARRIAGE_TOO_LONG("rail_carriage_too_long"),
    /** The lane's far end has not been laid yet - the far gate is not loaded. */
    RAIL_LANE_BUILDING("rail_lane_building"),
    /** This server has rail gates switched off, or has no Rift Corridor to run them through. */
    RAIL_DISABLED("rail_disabled"),
    /** The far gate is in another dimension. Trains go through; nothing else can follow them. */
    DESTINATION_RAIL_ONLY("destination_rail_only"),
    /** A redstone signal on the controller is holding trains back. */
    RAIL_HELD("rail_held"),
    /** The track runs along the gate, not through it. */
    RAIL_WRONG_WAY("rail_wrong_way");

    private final String name;

    GateFailure(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "gate.failure." + name;
    }

    public boolean isFailure() {
        return this != NONE;
    }
}
