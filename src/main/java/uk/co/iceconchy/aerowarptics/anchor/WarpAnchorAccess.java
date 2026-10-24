package uk.co.iceconchy.aerowarptics.anchor;

import net.minecraft.util.StringRepresentable;
import uk.co.iceconchy.aerowarptics.AWConfig;

import java.util.function.Predicate;

/** Who is allowed to warp to a Warp Anchor, or dial a Rift Gate. */
public enum WarpAnchorAccess implements StringRepresentable {

    /** Anybody may select this anchor as a destination. */
    PUBLIC("public"),
    /** Only the anchor's owner may select it. */
    PRIVATE("private"),
    /**
     * Only the players in the anchor's network may select it.
     *
     * <p>Declared last, though it sits between the other two when cycled: these go over the wire by
     * ordinal, and a new value anywhere but the end would renumber the ones already there.
     */
    NETWORK("network");

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

    /**
     * The mode after this one, as a button cycles them: Public, Network, Private, and round again.
     *
     * @param hasNetwork whether the destination is on a network. Without one Network is skipped, since it
     *                   would be Private under a name that promises more
     */
    public WarpAnchorAccess next(boolean hasNetwork) {
        return switch (this) {
            case PUBLIC -> hasNetwork ? NETWORK : PRIVATE;
            case NETWORK -> PRIVATE;
            case PRIVATE -> PUBLIC;
        };
    }

    /**
     * The next mode that is allowed, or this one if no other is.
     *
     * <p>With two modes a refused step could simply be dropped. With three, dropping it would stop the
     * button at Public for good on a server that allows Private but not Network.
     */
    public WarpAnchorAccess next(boolean hasNetwork, Predicate<WarpAnchorAccess> allowed) {
        WarpAnchorAccess candidate = this;
        for (int step = 0; step < values().length; step++) {
            candidate = candidate.next(hasNetwork);
            if (candidate == this || allowed.test(candidate)) {
                return candidate;
            }
        }
        return this;
    }

    /** Whether the server config currently permits anchors with this access mode. */
    public boolean permitted() {
        return switch (this) {
            case PUBLIC -> AWConfig.ALLOW_PUBLIC_ANCHORS.get();
            case PRIVATE -> AWConfig.ALLOW_PRIVATE_ANCHORS.get();
            case NETWORK -> AWConfig.ALLOW_ANCHOR_NETWORKS.get();
        };
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
