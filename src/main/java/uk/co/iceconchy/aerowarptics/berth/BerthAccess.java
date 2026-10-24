package uk.co.iceconchy.aerowarptics.berth;

/**
 * Who may be given a berth, as one rule.
 *
 * <p>It takes only facts and is tested as a table, as {@link uk.co.iceconchy.aerowarptics.ward.WardAccess}
 * is: the wrong answer here draws and runs exactly like the right one.
 *
 * <p>There is nothing here for an operator. An operator who needs a ship out of somebody's slip casts it
 * off; being handed a private berth by rank would make the panel's three settings mean something a player
 * cannot read off it.
 */
public final class BerthAccess {

    private BerthAccess() {
    }

    /**
     * @param isOwner  whether they own the berth
     * @param isMember whether they are in the network the berth names - false where it names none, or names
     *                 one that has gone, so a berth left pointing at a disbanded network closes rather than opens
     */
    public static boolean permits(BerthReserve reserve, boolean isOwner, boolean isMember) {
        return switch (reserve) {
            case ANYONE -> true;
            case NETWORK -> isOwner || isMember;
            case OWNER -> isOwner;
        };
    }
}
