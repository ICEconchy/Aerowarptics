package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.util.StringRepresentable;

/**
 * Whether the flagship itself is ready to lead a fold, and if not, the first thing in the way.
 *
 * <p>The flagship's counterpart of {@link StationStatus}, in the same order-of-fixing: a drive with no
 * course has nothing to survey, and an empty tank is worth mentioning only once the drive could go.
 */
public enum FlagshipStatus implements StringRepresentable {

    /** The coupler is not fitted to a drive. */
    NO_DRIVE("no_drive"),
    /** The drive is not aboard a ship. */
    NOT_ABOARD("not_aboard"),
    /** Nobody has set a course at an Astrolabe. */
    NO_COURSE("no_course"),
    /** The drive is set to Free Cruise, which has no destination to lead anyone to. */
    CRUISE("cruise"),
    /** The course leaves the dimension and this server does not allow that. */
    NO_CROSSING("no_crossing"),
    /** The drive is cooling down, in an error, or flying. */
    BUSY("busy"),
    /** Something ties the flagship to what the fold would leave behind; see {@link StationStatus#FASTENED}. */
    FASTENED("fastened"),
    UNPOWERED("unpowered"),
    UNCHARGED("uncharged"),
    /** The course is nearer than the shortest jump the drive will make; see {@link StationStatus#TOO_CLOSE}. */
    TOO_CLOSE("too_close"),
    /** The course is beyond the drive's range. */
    TOO_FAR("too_far"),
    UNAFFORDABLE("unaffordable"),
    /** Stations have not been sounded yet. */
    NOT_SOUNDED("not_sounded"),
    /** Not enough Rift Essence in the coupler for the whole fold. */
    NO_ESSENCE("no_essence"),
    /** Something stands where the flagship would come out. */
    OBSTRUCTED("obstructed"),
    /** The flagship's own arrival has not been looked at, or could not be. */
    SURVEYING("surveying"),
    READY("ready"),
    /** A fold is under way. */
    FOLDING("folding");

    private final String name;

    FlagshipStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "flotilla.flagship." + name;
    }

    /**
     * Whether a fold may be forced from here, leaving unready escorts behind. Everything about the
     * flagship's own drive has to be right; only the survey may be missing, since the fold's own search
     * is the survey that counts.
     */
    public boolean forceable() {
        return this == READY || this == SURVEYING || this == OBSTRUCTED;
    }

    public static FlagshipStatus byIndex(int index) {
        FlagshipStatus[] values = values();
        return index >= 0 && index < values.length ? values[index] : NO_DRIVE;
    }
}
