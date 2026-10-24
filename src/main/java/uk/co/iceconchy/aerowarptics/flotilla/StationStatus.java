package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.util.StringRepresentable;

/**
 * One escort's readiness to fold, as the single thing most worth telling its pilot.
 *
 * <p>An escort can be wrong in several ways at once - adrift <em>and</em> uncharged - and a row on the
 * Fleet Board has room for one word. The constants are declared in the order they are worth fixing:
 * there is no point harmonising a drive that has no power, and no point looking at the ground at the
 * far end for a ship that is not in station yet. {@link Readiness#status} returns the first that
 * applies.
 */
public enum StationStatus implements StringRepresentable {

    /** The escort's coupler is not loaded, or its ship has gone. */
    OUT_OF_CONTACT("out_of_contact"),
    /** Its drive is cooling down, in an error, or flying something of its own. */
    BUSY("busy"),
    /**
     * Something ties its ship to what a fold would leave behind - a Docking Connector locked to the pier, a
     * rope. Worth fixing before anything but contact: no charge or station-keeping gets a tied-down ship
     * through a rift. Declared here rather than appended, which the wire allows because the network version
     * moved with it.
     */
    FASTENED("fastened"),
    /** Its drive is not turning fast enough to hold a charge. */
    UNPOWERED("unpowered"),
    /** Its drive is turning but not yet full. */
    UNCHARGED("uncharged"),
    /**
     * The flagship's course is nearer than the shortest jump this escort's drive will make - most often a
     * flagship that has just arrived at its anchor and still has it as its course. Said apart from
     * {@link #TOO_FAR}, and both as the course's, because "Out of range" on a board that draws slots was read
     * as the slot's range by a ship sitting in the middle of one.
     */
    TOO_CLOSE("too_close"),
    /** The flagship's course is further than this escort's drive can reach. */
    TOO_FAR("too_far"),
    /** The flagship's course leaves the dimension and this server does not allow that. */
    NO_CROSSING("no_crossing"),
    /** In range, but a full charge will not pay for its share of the fleet's mass. */
    UNAFFORDABLE("unaffordable"),
    /** Further from its slot than the station tolerance. */
    ADRIFT("adrift"),
    /** In station and charged, still matching the flagship's drive. */
    HARMONISING("harmonising"),
    /** Something stands in its slot at the far end. */
    OBSTRUCTED("obstructed"),
    /** Its slot at the far end has not been looked at yet, or could not be. */
    SURVEYING("surveying"),
    /** All four: in station, charged, harmonised, clear. */
    READY("ready");

    private final String name;

    StationStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "flotilla.status." + name;
    }

    public boolean ready() {
        return this == READY;
    }

    public static StationStatus byIndex(int index) {
        StationStatus[] values = values();
        return index >= 0 && index < values.length ? values[index] : OUT_OF_CONTACT;
    }
}
