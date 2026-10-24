package uk.co.iceconchy.aerowarptics.warpnet;

import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;

/**
 * Who may travel to a destination, as one rule.
 *
 * <p>An anchor and a gate ask the same question and used to answer it in two copies of the same switch.
 * With a third access mode that is one switch too many to keep in step by hand, and the failure - a
 * destination open to somebody it should be shut to - draws and runs exactly like the right answer. So
 * the rule is here, takes only facts, and is tested as a table.
 */
public final class NetworkAccess {

    private NetworkAccess() {
    }

    /**
     * Whether a player may pick a destination.
     *
     * @param access        the destination's access mode
     * @param modePermitted whether the server currently allows destinations with that mode at all; where
     *                      it does not, the destination falls back to its owner alone rather than opening
     * @param isOwner       whether the player owns the destination
     * @param isOperator    whether the player is an operator, who bypasses the mode so an admin can always
     *                      recover a ship
     * @param isMember      whether the player is in the destination's network - false for a destination
     *                      with no network, which is what makes Network with nothing behind it Private
     */
    public static boolean permits(WarpAnchorAccess access, boolean modePermitted, boolean isOwner,
                                  boolean isOperator, boolean isMember) {
        if (!modePermitted) {
            return isOwner;
        }
        return switch (access) {
            case PUBLIC -> true;
            case NETWORK -> isOwner || isOperator || isMember;
            case PRIVATE -> isOwner || isOperator;
        };
    }
}
