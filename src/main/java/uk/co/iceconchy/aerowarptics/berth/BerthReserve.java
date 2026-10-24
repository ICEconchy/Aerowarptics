package uk.co.iceconchy.aerowarptics.berth;

import java.util.Locale;

/**
 * Whom a berth is kept for.
 *
 * <p>A narrowing of its anchor's access and never a widening: nobody is offered a berth at an anchor they
 * may not warp to, because they could never set the course. So a public station can keep a slip for its
 * owner's own ship, and a private one's berths are private whatever they say.
 *
 * <p>Sent and saved by name. {@link #ANYONE} is first so that a berth saved with a name this version does
 * not know reads as open - a berth nobody can be given is a station that refuses everybody.
 */
public enum BerthReserve {

    /** Anyone who may warp to the anchor. */
    ANYONE,
    /** The members of one network, and the berth's owner. */
    NETWORK,
    /** Its owner alone. */
    OWNER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public BerthReserve next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static BerthReserve byName(String name) {
        for (BerthReserve reserve : values()) {
            if (reserve.name().equalsIgnoreCase(name)) {
                return reserve;
            }
        }
        return ANYONE;
    }
}
