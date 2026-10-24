package uk.co.iceconchy.aerowarptics.flotilla;

import uk.co.iceconchy.aerowarptics.warp.WarpCost;

/**
 * Whether an escort is ready to fold, and if not, why not.
 *
 * <p>Pure: everything the question depends on is handed in as a {@link Reading}, taken off the live
 * machines by the flagship's coupler. That is what lets the rules - which of four conditions matters
 * first, when harmony starts and stops building - be checked without a server.
 *
 * <h2>The four conditions</h2>
 * <ol>
 *   <li><b>In station</b> - within tolerance of its slot.</li>
 *   <li><b>Charged</b> - its own drive full, turning fast enough, able to reach the flagship's course
 *       and to pay its share of the fleet's mass, and its ship not tied to anything the fold would leave
 *       behind. Every drive in a flotilla still needs its own shaft.</li>
 *   <li><b>Harmonised</b> - its drive has matched the flagship's. Nobody does this: it builds up on its
 *       own while the other two hold and stations have been sounded, and a slip in either starts it
 *       again. That is the whole of what the harmony glyph on the Fleet Board is showing.</li>
 *   <li><b>Clear</b> - its slot at the far end passes the ordinary clearance check.</li>
 * </ol>
 */
public final class Readiness {

    private Readiness() {
    }

    /** What the far end of an escort's slot was last found to be. */
    public enum Survey {
        /** Not looked at yet, or the ground there is not loaded. */
        UNKNOWN,
        CLEAR,
        OBSTRUCTED;

        public static Survey byIndex(int index) {
            Survey[] values = values();
            return index >= 0 && index < values.length ? values[index] : UNKNOWN;
        }
    }

    /**
     * How the flagship's course sits against one drive's range.
     *
     * <p>Three different things to do about it, which is why it is not a yes or a no. They were one "Out of
     * range" once, and a pilot sitting square in their slot read it as the only range a Fleet Board draws -
     * the slot's - when the flagship had simply arrived at its anchor and not been given a new course.
     */
    public enum Reach {
        WITHIN,
        /** Nearer than the shortest jump the drive will make: the flagship needs a course somewhere else. */
        TOO_CLOSE,
        /** Further than the drive's range. */
        TOO_FAR,
        /** In another dimension, on a server that does not allow the crossing. */
        NO_CROSSING
    }

    /**
     * A course against a drive's range, as the drive's own launch would find it.
     *
     * @param distance          how long the jump is, as priced
     * @param crossing          whether the course ends in another dimension
     * @param crossingSupported whether this server allows that
     */
    public static Reach reach(WarpCost.Formula formula, double distance, boolean crossing,
                              boolean crossingSupported) {
        if (crossing && !crossingSupported) {
            return Reach.NO_CROSSING;
        }
        return switch (formula.checkRange(distance)) {
            case DESTINATION_TOO_CLOSE -> Reach.TOO_CLOSE;
            case DESTINATION_TOO_FAR -> Reach.TOO_FAR;
            default -> Reach.WITHIN;
        };
    }

    /**
     * Everything the flagship's coupler read off one escort this tick.
     *
     * @param contact       the escort's coupler is loaded and its ship is still there
     * @param busy          its drive is doing something other than waiting to be used
     * @param powered       its drive is turning fast enough to hold a charge
     * @param charged       its drive is full
     * @param reach         how the flagship's course sits against its drive's range
     * @param affordable    a full charge pays its share of the fold
     * @param drift         blocks between the escort and its slot
     * @param tolerance     the station tolerance
     * @param harmonyTicks  how long it has been harmonising, from {@link #nextHarmony}
     * @param settleTicks   how long harmonising takes
     * @param survey        what its slot at the far end was last found to be
     * @param fastened      something ties its ship to what the fold would leave behind
     */
    public record Reading(boolean contact, boolean busy, boolean powered, boolean charged, Reach reach,
                          boolean affordable, double drift, double tolerance, int harmonyTicks,
                          int settleTicks, Survey survey, boolean fastened) {

        /** A reading of a ship with nothing tying it down. */
        public Reading(boolean contact, boolean busy, boolean powered, boolean charged, Reach reach,
                       boolean affordable, double drift, double tolerance, int harmonyTicks,
                       int settleTicks, Survey survey) {
            this(contact, busy, powered, charged, reach, affordable, drift, tolerance, harmonyTicks,
                    settleTicks, survey, false);
        }

        public boolean inStation() {
            return Formation.inStation(drift, tolerance);
        }

        /** Whether the flagship's course is one this escort's drive would fly. */
        public boolean inRange() {
            return reach == Reach.WITHIN;
        }

        /** The second condition: every part of "its drive is ready to go". */
        public boolean driveReady() {
            return contact && !busy && !fastened && powered && charged && inRange() && affordable;
        }

        public boolean harmonised() {
            return harmonyTicks >= Math.max(1, settleTicks);
        }
    }

    /** The one thing most worth telling the escort's pilot. */
    public static StationStatus status(Reading reading) {
        if (!reading.contact()) {
            return StationStatus.OUT_OF_CONTACT;
        }
        if (reading.busy()) {
            return StationStatus.BUSY;
        }
        if (reading.fastened()) {
            return StationStatus.FASTENED;
        }
        if (!reading.powered()) {
            return StationStatus.UNPOWERED;
        }
        if (!reading.charged()) {
            return StationStatus.UNCHARGED;
        }
        // No default, so a new way for a course to be out of reach will not compile until it has a name.
        StationStatus course = switch (reading.reach()) {
            case WITHIN -> null;
            case TOO_CLOSE -> StationStatus.TOO_CLOSE;
            case TOO_FAR -> StationStatus.TOO_FAR;
            case NO_CROSSING -> StationStatus.NO_CROSSING;
        };
        if (course != null) {
            return course;
        }
        if (!reading.affordable()) {
            return StationStatus.UNAFFORDABLE;
        }
        if (!reading.inStation()) {
            return StationStatus.ADRIFT;
        }
        if (!reading.harmonised()) {
            return StationStatus.HARMONISING;
        }
        return switch (reading.survey()) {
            case OBSTRUCTED -> StationStatus.OBSTRUCTED;
            case UNKNOWN -> StationStatus.SURVEYING;
            case CLEAR -> StationStatus.READY;
        };
    }

    /**
     * One tick of harmonising.
     *
     * <p>Builds while stations have been sounded and the escort is both in station and ready to go, and
     * drops straight back to nothing the moment any of that stops being true - a drive that lost its
     * shaft for a second has to find the flagship's note again, it does not pick up where it was.
     * Stops at {@code settleTicks}, so a ship that has been steady for an hour is no more harmonised
     * than one that has been steady for the settling time.
     */
    public static int nextHarmony(int ticks, boolean sounded, boolean inStation, boolean driveReady, int settleTicks) {
        if (!sounded || !inStation || !driveReady) {
            return 0;
        }
        return Math.min(Math.max(1, settleTicks), Math.max(0, ticks) + 1);
    }

    /** How far through harmonising, 0 to 1 - what the wave glyph settles by. */
    public static float harmony(int ticks, int settleTicks) {
        return Math.max(0.0F, Math.min(1.0F, ticks / (float) Math.max(1, settleTicks)));
    }

    /** Whether the Fold button may be pressed: at least one escort, and every one of them ready. */
    public static boolean allReady(java.util.List<StationStatus> rows) {
        if (rows.isEmpty()) {
            return false;
        }
        for (StationStatus row : rows) {
            if (!row.ready()) {
                return false;
            }
        }
        return true;
    }

    /** How many are ready, for "3/4 in station" and the Display Link. */
    public static int countReady(java.util.List<StationStatus> rows) {
        int ready = 0;
        for (StationStatus row : rows) {
            if (row.ready()) {
                ready++;
            }
        }
        return ready;
    }
}
