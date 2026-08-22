package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.util.StringRepresentable;

/**
 * What a Rift Probe is doing.
 *
 * <p>Three of these are visible for a second or more and one is not: {@link #REACHING} is the probe
 * waiting for ground that may never have existed until it asked. That wait is the honest cost of a
 * blind jump - the server is generating terrain - so it is a state with a progress bar rather than
 * something hidden behind a spinner.
 */
public enum ProbeState implements StringRepresentable {

    /** Nothing thrown. Waiting for a bearing and a range. */
    IDLE("idle"),

    /** A rift is out there and the ground it landed on is being brought into being. */
    REACHING("reaching"),

    /** A reading came back and is on the panel. */
    COMPLETE("complete"),

    /** The sounding found nothing to aim at. */
    FAILED("failed");

    private final String name;

    ProbeState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "probe.state." + name;
    }

    public boolean busy() {
        return this == REACHING;
    }

    public int index() {
        return ordinal();
    }

    public static ProbeState byIndex(int index) {
        ProbeState[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
