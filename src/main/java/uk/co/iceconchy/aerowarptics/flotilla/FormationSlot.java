package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.nbt.CompoundTag;

/**
 * Where an escort keeps station, measured from the flagship.
 *
 * <p>In the flagship's own frame and relative to its bow - blocks to starboard, blocks up, blocks ahead
 * - from the middle of the flagship's hull. Not in world axes, because a slot is a place in a formation
 * and a formation turns with the ship that leads it: an escort told to keep forty blocks off the
 * starboard bow is still off the starboard bow after the flagship has come about. Not in ship space
 * either, because a ship's own axes say nothing about which way it flies, and the Fleet Board has to
 * draw the formation with the bow at the top.
 *
 * @param right blocks to starboard of the flagship's centre; negative is to port
 * @param up    blocks above it
 * @param ahead blocks ahead of it along the bow; negative is astern
 */
public record FormationSlot(double right, double up, double ahead) {

    /** The flagship's own place in its formation. */
    public static final FormationSlot FLAGSHIP = new FormationSlot(0.0D, 0.0D, 0.0D);

    public FormationSlot {
        // Off disk and off the wire. A non-finite slot would put an escort's station at infinity, and
        // every comparison against it would quietly come out false.
        right = Double.isFinite(right) ? right : 0.0D;
        up = Double.isFinite(up) ? up : 0.0D;
        ahead = Double.isFinite(ahead) ? ahead : 0.0D;
    }

    /** Straight-line distance from the flagship's centre. */
    public double reach() {
        return Math.sqrt(right * right + up * up + ahead * ahead);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Right", right);
        tag.putDouble("Up", up);
        tag.putDouble("Ahead", ahead);
        return tag;
    }

    public static FormationSlot load(CompoundTag tag) {
        return new FormationSlot(tag.getDouble("Right"), tag.getDouble("Up"), tag.getDouble("Ahead"));
    }
}
