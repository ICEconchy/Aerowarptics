package uk.co.iceconchy.aerowarptics.anchor;

import net.minecraft.util.StringRepresentable;

/**
 * Visible state of a Warp Anchor.
 *
 * <p>The server decides which value applies and syncs it; the client only maps it onto a GeckoLib
 * animation and a status colour.
 */
public enum WarpAnchorStatus implements StringRepresentable {

    /** Not registered, or switched off by its owner. */
    OFFLINE("offline", "offline", 0x6B4A34),
    /** Registered, powered and accepting arrivals. */
    ONLINE("online", "online", 0x3BD1C8),
    /** A Rift Drive somewhere has locked onto this anchor. */
    LOCKED("destination_lock", "destination_lock", 0xFFC24A),
    /** An airship is arriving right now. */
    WARP_ACTIVE("warp_active", "warp_active", 0xE45CFF),
    /** Just received an airship and is settling. */
    COOLDOWN("cooldown", "cooldown", 0x8A6BFF),
    /** Registered but unusable - obstructed, duplicated, or its record was lost. */
    ERROR("error", "error", 0xE0402F);

    private final String name;
    private final String animation;
    private final int colour;

    WarpAnchorStatus(String name, String animation, int colour) {
        this.name = name;
        this.animation = animation;
        this.colour = colour;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Name of the animation in {@code assets/aerowarptics/animations/warp_anchor.animation.json}. */
    public String animation() {
        return "animation.warp_anchor." + animation;
    }

    public int colour() {
        return colour;
    }

    public String translationKey() {
        return "anchor.status." + name;
    }

    public boolean acceptsArrivals() {
        return this == ONLINE || this == LOCKED;
    }

    public static WarpAnchorStatus byIndex(int index) {
        WarpAnchorStatus[] values = values();
        return index >= 0 && index < values.length ? values[index] : OFFLINE;
    }
}
