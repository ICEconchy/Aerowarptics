package uk.co.iceconchy.aerowarptics.sunderer;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Why a Rift Sunderer would not fire. Every one of these is found before anything is spent: a refusal drains no
 * essence and leaves the ring standing, which is the crafting plan's fourth rule for anything that begins on a
 * redstone edge.
 *
 * <p>Checked in the order declared, which is the order a player fixes things in - get it off the ship, close the
 * ring, size it, fill and turn it, and only then worry about what is nearby.
 */
public enum SundererFailure implements StringRepresentable {

    NONE,
    /** A sundering already running cannot be started again. */
    BUSY,
    /** Aboard a ship. A fissure refuses to move, so one is never torn where it would be carried. */
    ABOARD_SHIP,
    /** The Sunderer is not part of a closed ring of Rift Gate Frame standing on end. */
    NO_RING,
    /** The opening is narrower or shorter than the smallest a Sunderer will tear. */
    RING_TOO_SMALL,
    /** The opening is wider or taller than the largest a Sunderer will tear. */
    RING_TOO_LARGE,
    /** The tank holds less than one tear's charge. */
    NOT_CHARGED,
    /** Turned slower than the minimum speed. */
    TOO_SLOW,
    /** Inside a scar zone, or over a meltdown still running: that ground is not to be torn again. */
    IN_SCAR_ZONE,
    /** Too near a fissure, a Rift Wound or another sundering. */
    NEAR_TEAR,
    /** Too near a Warp Anchor: an anchor pins the space round it. */
    NEAR_ANCHOR,
    /** Inside a Rift Ward's field, which turns rifts aside. */
    IN_WARD_FIELD,
    /** The server does not allow fissures to be torn in this dimension. */
    DIMENSION;

    public boolean isFailure() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "sunderer.failure." + getSerializedName();
    }
}
