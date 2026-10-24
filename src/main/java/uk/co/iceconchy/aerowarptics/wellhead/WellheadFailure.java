package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Why a Rift Collar would not form its frame. Every one of these is found before a block is changed: a refusal
 * leaves the casing, the glass and the fissure exactly as they stood.
 *
 * <p>Checked in the order declared, which is the order a player fixes things in - find a tear before worrying
 * what the frame round it is made of, and finish the frame before seating a better crystal.
 */
public enum WellheadFailure implements StringRepresentable {

    NONE,
    /** Aboard a ship. A fissure never moves, so nothing aboard can be built round one - but a collar can be carried. */
    ABOARD_SHIP,
    /** Inside the ground a meltdown left behind, while it is still unsettled. */
    IN_SCAR_ZONE,
    /** No fissure one, two or three blocks straight behind the collar. */
    NO_FISSURE,
    /** The fissure behind the collar is already closing. */
    FISSURE_SEALING,
    /** Another frame already holds that fissure. */
    FISSURE_TAKEN,
    /** No crystal in the collar, or one good only for a smaller frame than this. */
    CRYSTAL_TOO_WEAK,
    /** A second fissure stands inside the frame, or where one of its blocks would go. */
    SECOND_FISSURE,
    /** Something stands inside the frame that is not air. */
    INTERIOR_BLOCKED,
    /** A block of the shell is missing or is the wrong block. */
    FRAME_INCOMPLETE;

    public boolean isFailure() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "wellhead.failure." + getSerializedName();
    }
}
