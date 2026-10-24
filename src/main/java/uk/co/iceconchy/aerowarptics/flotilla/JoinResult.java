package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.util.StringRepresentable;

/** What came of asking to take station - or of a flagship's answer to someone who asked. */
public enum JoinResult implements StringRepresentable {

    /** Taken station: the escort is in the flotilla. */
    JOINED("joined"),
    /** Asked; waiting on the flagship's owner. */
    REQUESTED("requested"),
    /** The flagship already leads as many escorts as its drive can hold. */
    FULL("full"),
    /** Already in this flotilla. */
    ALREADY_MEMBER("already_member"),
    /** Already sailing with someone else; break off first. */
    IN_ANOTHER("in_another"),
    /** That coupler is not set to lead. */
    NOT_A_FLAGSHIP("not_a_flagship"),
    /** This coupler is set to lead, and a flagship cannot follow. */
    NOT_AN_ESCORT("not_an_escort"),
    /** A ship cannot escort itself. */
    SAME_SHIP("same_ship"),
    /** Too far away, in another dimension, or gone. */
    OUT_OF_REACH("out_of_reach"),
    /** The flagship has as many requests waiting as it will hold. */
    TOO_MANY_REQUESTS("too_many_requests"),
    /** Nothing by that name is known. */
    UNKNOWN("unknown");

    private final String name;

    JoinResult(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "flotilla.join." + name;
    }

    public boolean succeeded() {
        return this == JOINED || this == REQUESTED;
    }
}
