package uk.co.iceconchy.aerowarptics.collapse;

/**
 * Which of a relic machine's inputs is turning it - see {@link RelicFloor}.
 *
 * <p>The five inputs are five ends of five lines, and the machine goes by one of them: the fastest, and of two as
 * fast as each other the first, so a server and a client, and every block asked whether the load is its own, give the
 * same answer. It is chosen by the speed the line is <em>trying</em> to turn at, never by whether that line is
 * overstressed - the load goes on the one chosen, so a choice that moved off an overstressed line would take the load
 * with it, free the line, and move straight back.
 *
 * <p>Free of Minecraft, for {@code RelicFeedTest}.
 */
public final class RelicFeed {

    /** No input is turning. */
    public static final int NONE = -1;

    private RelicFeed() {
    }

    /** The input the machine goes by, as its place in the speeds given, or {@link #NONE} if none of them turns. */
    public static int feeder(float... speeds) {
        int feeder = NONE;
        float fastest = 0.0F;
        for (int i = 0; i < speeds.length; i++) {
            float speed = Math.abs(speeds[i]);
            if (speed > fastest) {
                fastest = speed;
                feeder = i;
            }
        }
        return feeder;
    }
}
