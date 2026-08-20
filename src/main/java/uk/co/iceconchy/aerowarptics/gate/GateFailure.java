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
    TOO_LARGE("too_large");

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
