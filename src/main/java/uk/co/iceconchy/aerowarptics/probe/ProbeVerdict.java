package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.util.StringRepresentable;

/** How a completed sounding reads, in one word. */
public enum ProbeVerdict implements StringRepresentable {

    /** Solid ground, well covered. Somewhere a ship can be sent. */
    CLEAR("clear"),

    /** Ground found, but the reading has holes in it. Usable, with less known than usual. */
    PARTIAL("partial"),

    /** Nothing came back to aim at. */
    NO_GROUND("no_ground");

    private final String name;

    ProbeVerdict(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "probe.verdict." + name;
    }

    public boolean usable() {
        return this != NO_GROUND;
    }
}
