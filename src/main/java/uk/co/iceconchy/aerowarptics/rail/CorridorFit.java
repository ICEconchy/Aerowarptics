package uk.co.iceconchy.aerowarptics.rail;

/**
 * How long a corridor has to be, and whether a carriage fits through one.
 *
 * <p>Pure. A Create carriage may be in at most two dimensions at once: it refuses to enter a portal while it is
 * still leaving another ("cannot enter a portal whilst leaving another"). So a carriage whose front reaches the
 * far end of the corridor before its back has left the near gate is stuck in the middle of the void for good.
 * The corridor therefore has to be longer than the longest carriage, plus the few ticks Create keeps counting a
 * carriage as present in the dimension it has just left - four, at whatever the train is doing.
 */
public final class CorridorFit {

    /** Blocks added to Create's longest train when sizing a corridor. */
    public static final int MARGIN = 32;

    /** Longest corridor this will lay. Past it a carriage would be a contraption Create would never assemble. */
    public static final int MAX_LENGTH = 1_024;

    /** Shortest: room for the mouths, the two bound tracks and something between them. */
    public static final int MIN_LENGTH = 16;

    /** Ticks Create goes on counting a carriage as present in a dimension after it has gone. */
    public static final int DISCARD_TICKS = 4;

    private CorridorFit() {
    }

    /**
     * The length a new corridor is laid at.
     *
     * @param configured        the server's {@code corridorLength}, or zero to work it out
     * @param maxAssemblyLength Create's own longest train, which no carriage can be longer than
     */
    public static int requiredLength(int configured, int maxAssemblyLength) {
        int needed = Math.max(MIN_LENGTH, maxAssemblyLength + MARGIN);
        return Math.min(MAX_LENGTH, Math.max(configured, needed));
    }

    /**
     * Whether a carriage can cross a corridor without being in three dimensions at once.
     *
     * @param span          the carriage's length, bogey to bogey, in blocks
     * @param length        the corridor's length
     * @param blocksPerTick how fast trains may go, in blocks a tick
     */
    public static boolean fits(int span, int length, double blocksPerTick) {
        double reach = span + 2.0D + DISCARD_TICKS * Math.max(0.0D, blocksPerTick);
        return reach < length - 2;
    }
}
