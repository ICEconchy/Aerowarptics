package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.util.StringRepresentable;

/**
 * Where a lane is in being laid.
 *
 * <p>A lane is three pieces in up to three dimensions - the player's track at the near gate, the corridor, and
 * the stub at the far gate - and the far one cannot always be laid when the first two are: the far gate may be
 * in chunks nobody is in. A lane that is not {@link #READY} has no track out of the far gate, so no train can
 * find a path through it and none is let into it.
 */
public enum RailLaneState implements StringRepresentable {

    /** The near track and the corridor are down; the far stub is waiting for the far gate to load. */
    FAR_PENDING("far_pending"),
    /** All three pieces are down and bound. */
    READY("ready"),
    /** The far stub could not be laid - something is standing where it goes - and will be tried again. */
    FAR_BLOCKED("far_blocked"),
    /** The gate has no route, or its route is gone, so there is nowhere to lay a far end at all. */
    NO_ROUTE("no_route");

    private final String name;

    RailLaneState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "gate.rail.lane." + name;
    }

    public static RailLaneState byName(String name) {
        for (RailLaneState value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        return FAR_PENDING;
    }
}
