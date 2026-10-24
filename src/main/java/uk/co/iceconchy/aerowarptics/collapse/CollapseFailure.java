package uk.co.iceconchy.aerowarptics.collapse;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Why a Collapse Cradle would not begin. Every one of these is found before anything is spent: a refusal
 * takes nothing off a depot, drains no essence and leaves the heart where it lies.
 *
 * <p>Checked in the order declared, which is the order a player fixes things in - move the cradle off the
 * ship before worrying what is on the depots, fill the slots before the tank.
 */
public enum CollapseFailure implements StringRepresentable {

    NONE,
    /** A running ritual or fold cannot be started again. */
    BUSY,
    /** Aboard a ship. A collapse is anchored to the ground it falls into. */
    ABOARD_SHIP,
    /** Nothing in the arms: neither a Rift Core to collapse nor anything to fold. */
    NO_CORE,
    /** Nothing in the heart slot: nothing for it to fall into. */
    NO_HEART,
    /** What is on the cradle and the depots round it matches no collapse recipe. */
    NO_RECIPE,
    /** What the arms and the cup hold folds into nothing. */
    NO_FOLD,
    /** No room in the output for what a fold would make. */
    NO_ROOM,
    /** The tank cannot pay for the whole ritual, or for a single craft of a fold. */
    NOT_ENOUGH_ESSENCE,
    /** Turned slower than the recipe's drive tier's minimum speed. */
    TOO_SLOW,
    /** Turned slower than a fold needs. */
    NOT_TURNING;

    public boolean isFailure() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "collapse.failure." + getSerializedName();
    }
}
