package uk.co.iceconchy.aerowarptics.anchor;

import net.minecraft.util.StringRepresentable;
import uk.co.iceconchy.aerowarptics.AWConfig;

/** Who is allowed to warp to a Warp Anchor. */
public enum WarpAnchorAccess implements StringRepresentable {

    /** Anybody may select this anchor as a destination. */
    PUBLIC("public"),
    /** Only the anchor's owner may select it. */
    PRIVATE("private");

    private final String name;

    WarpAnchorAccess(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "anchor.access." + name;
    }

    public WarpAnchorAccess next() {
        return this == PUBLIC ? PRIVATE : PUBLIC;
    }

    /** Whether the server config currently permits anchors with this access mode. */
    public boolean permitted() {
        return this == PUBLIC ? AWConfig.ALLOW_PUBLIC_ANCHORS.get() : AWConfig.ALLOW_PRIVATE_ANCHORS.get();
    }

    public static WarpAnchorAccess byName(String name) {
        for (WarpAnchorAccess access : values()) {
            if (access.name.equalsIgnoreCase(name)) {
                return access;
            }
        }
        return PUBLIC;
    }
}
