package uk.co.iceconchy.aerowarptics.ward;

/**
 * Who a Rift Ward lets through, as one rule.
 *
 * <p>It takes only facts and is tested as a table, because the wrong answer here draws and runs exactly like the
 * right one - a base that turns nobody aside looks the same as a base nobody has tried.
 *
 * <p>The ward's {@link WardMode} is the whole of it, with one exception: its owner. There is nothing here for an
 * operator, and nothing for an anchor standing inside the field - the first version let anyone through who was
 * bound for an anchor they could use, and the owner asked for that to go (2026-10-07): three settings a player
 * can read off the panel, and no rule behind them.
 */
public final class WardAccess {

    private WardAccess() {
    }

    /**
     * Whether whoever opened a rift into a field comes out where they aimed.
     *
     * <p>A rift with no author at all - a drive armed before authors were kept, a plotter set down before
     * plotters had owners - is nobody's and in no network. So it is turned by a ward set to block everyone or to
     * allow some networks, and passes one set only to block particular networks: it is not one of theirs.
     *
     * @param isOwner  whether they own the ward
     * @param selfTest whether the owner has asked to be turned too, so a ward can be tried by one player alone
     * @param listed   whether they are in any of the networks the ward names
     */
    public static boolean passes(WardMode mode, boolean isOwner, boolean selfTest, boolean listed) {
        if (isOwner && !selfTest) {
            return true;
        }
        return switch (mode) {
            case BLOCK_ALL -> false;
            case ALLOW_NETWORKS -> listed;
            case BLOCK_NETWORKS -> !listed;
        };
    }
}
