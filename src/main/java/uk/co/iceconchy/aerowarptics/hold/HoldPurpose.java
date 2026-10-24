package uk.co.iceconchy.aerowarptics.hold;

import java.util.Locale;

/**
 * Why something is being kept loaded.
 *
 * <p>Every hold names one, so that {@code /aerowarptics holds list} and the trace can say what a loaded
 * chunk is <em>for</em> - "a chute is holding its partner" is an answer, "something forced this" is not.
 *
 * <p>Four of them hold a ship with Sable's own force-load ticket, which Sable persists with the world:
 * {@link #RESIDENCY}, {@link #FLIGHT}, {@link #PLOTTED} and {@link #BERTHED}. They are the only holds that outlive a restart, and each is on a
 * lease in {@link HoldLedger} so that it cannot outlive its owner either. The rest are expiring region
 * tickets on the ground, which end with the server by construction - a preview, a reach, a chute's
 * partner and a gate's far end are all asked for by something happening now, and nothing is happening
 * once the server has stopped.
 */
public enum HoldPurpose {

    /** A parked ship kept loaded by its drive, so it is still there when somebody comes back for it. */
    RESIDENCY,
    /** A ship in the middle of a warp, kept loaded wherever the warp puts it. */
    FLIGHT,
    /** The ground round an anchor, while somebody is looking at it on an Astrolabe. */
    PREVIEW,
    /** The ground under a ship that is not loaded, while something is trying to reach it. */
    REACH,
    /** A Rift Chute's partner, while items are flowing to it. */
    CHUTE_PARTNER,
    /** The far end of a connected Rift Gate. */
    GATE_FAR_END,
    /**
     * A vehicle that has just come out of a Rift Plotter's exit, held a while so an uncrewed one flies on at its
     * target instead of being put away by Sable the moment it arrives on ground nobody is standing near.
     */
    PLOTTED,
    /**
     * A ship a Berth holds in its slip, kept loaded so that a ship parked at a station is still parked there
     * when its owner comes back - and so that the berth, which holds it from the server's tick, has a ship to hold.
     */
    BERTHED;

    /** Whether this purpose holds a whole ship with Sable's ticket rather than ground with a region ticket. */
    public boolean holdsShip() {
        return this == RESIDENCY || this == FLIGHT || this == PLOTTED || this == BERTHED;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The purpose a saved id names, or {@code null} for one this version does not know. */
    public static HoldPurpose byId(String id) {
        for (HoldPurpose purpose : values()) {
            if (purpose.id().equals(id)) {
                return purpose;
            }
        }
        return null;
    }
}
