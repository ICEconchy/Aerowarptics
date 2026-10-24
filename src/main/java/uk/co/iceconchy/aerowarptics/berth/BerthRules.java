package uk.co.iceconchy.aerowarptics.berth;

/**
 * The limits a berth's settings are held to, wherever they come from - a panel, a save, a packet.
 *
 * <p>The ceilings here are the hard ones, wide enough for any server's config to sit under; the server's own
 * (`berth.maxLength` and the rest) are applied where an order is taken, and sent to the panel.
 */
public final class BerthRules {

    private BerthRules() {
    }

    /** The smallest a slip may be on any side: room for a rowing boat and the air round it. */
    public static final int MIN_SIZE = 3;
    /** The largest any server may allow. */
    public static final int HARD_MAX_SIZE = 512;

    /**
     * The slip a berth is set down with. A ship needs the arrival clearance - two blocks, as the mod ships - on
     * every side, so this takes one up to twenty long and nine across and high: a fair first airship.
     */
    public static final int DEFAULT_LENGTH = 24;
    public static final int DEFAULT_WIDTH = 13;
    public static final int DEFAULT_HEIGHT = 13;

    /** How far the middle of a slip's floor may be set from its block, on each axis. */
    public static final int MAX_OFFSET = 96;

    /** A facing is set in steps of this many degrees. */
    public static final int YAW_STEP = 5;

    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 99;
    public static final int DEFAULT_PRIORITY = 50;

    public static final int NAME_LENGTH = 32;

    public static int size(int asked, int serverMax) {
        return Math.max(MIN_SIZE, Math.min(asked, Math.max(MIN_SIZE, Math.min(serverMax, HARD_MAX_SIZE))));
    }

    public static int offset(int asked) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, asked));
    }

    /** A yaw brought into 0..359 and onto a step. */
    public static int yaw(int asked) {
        int stepped = Math.round(asked / (float) YAW_STEP) * YAW_STEP;
        return Math.floorMod(stepped, 360);
    }

    public static int priority(int asked) {
        return Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, asked));
    }

    public static String name(String asked) {
        String trimmed = asked == null ? "" : asked.strip();
        return trimmed.length() > NAME_LENGTH ? trimmed.substring(0, NAME_LENGTH) : trimmed;
    }
}
