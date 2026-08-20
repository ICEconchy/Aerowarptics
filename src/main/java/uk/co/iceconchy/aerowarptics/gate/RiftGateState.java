package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.util.StringRepresentable;

/**
 * What a Rift Gate is doing.
 *
 * <p>Shorter than a drive's sequence because a gate has far less to be in the middle of. It is either
 * a ring of blocks, a ring with a hole in space in it, or on its way between the two.
 */
public enum RiftGateState implements StringRepresentable {

    /** Blocks placed, but no closed ring around a clear opening. */
    UNFORMED("unformed"),
    /** A complete gate, standing dark. */
    IDLE("idle"),
    /** Essence spent, the far gate answering, the pane breaking. */
    DIALLING("dialling"),
    /** Open at both ends and holding. This is the only state anything may pass through. */
    OPEN("open"),
    /** Letting go. */
    CLOSING("closing");

    private final String name;

    RiftGateState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "gate.state." + name;
    }

    /** Whether the gate is showing an aperture, in any stage of its life. */
    public boolean hasAperture() {
        return this == DIALLING || this == OPEN || this == CLOSING;
    }

    /** Whether anything may cross. Only ever true when both ends are up. */
    public boolean passable() {
        return this == OPEN;
    }

    /** Whether the gate is committed to something and should refuse a new dial. */
    public boolean engaged() {
        return this == DIALLING || this == OPEN;
    }

    public int index() {
        return ordinal();
    }

    public static RiftGateState byIndex(int index) {
        RiftGateState[] values = values();
        return index < 0 || index >= values.length ? UNFORMED : values[index];
    }
}
