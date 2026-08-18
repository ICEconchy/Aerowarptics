package uk.co.iceconchy.aerowarptics.warp;

/**
 * The decision rules behind every warp, expressed over plain values.
 *
 * <p>{@link WarpValidator} gathers the facts from the world - which airship, how far, who is asking -
 * and this class turns them into a verdict. Keeping the two apart means the rules can be exercised
 * exhaustively in tests without a server, and means there is exactly one place where "may this warp
 * happen" is decided.
 */
public final class WarpRules {

    private WarpRules() {
    }

    /**
     * Whether a player is entitled to command a drive.
     *
     * @param driveUsable      the drive exists and is not removed
     * @param airshipActive    the drive is attached to a live airship
     * @param distanceSquared  squared world distance from the player to the drive
     * @param reach            maximum permitted distance
     * @param aboard           the player is standing on the airship
     * @param requireAboard    server setting: must the player be aboard
     * @param operator         the player has server operator permissions
     */
    public static WarpFailure checkAuthority(boolean driveUsable,
                                             boolean airshipActive,
                                             double distanceSquared,
                                             double reach,
                                             boolean aboard,
                                             boolean requireAboard,
                                             boolean operator) {
        if (!driveUsable) {
            return WarpFailure.DRIVE_BUSY;
        }
        if (!airshipActive) {
            return WarpFailure.NO_AIRSHIP;
        }
        if (operator) {
            return WarpFailure.NONE;
        }
        if (distanceSquared > reach * reach) {
            return WarpFailure.UNAUTHORISED;
        }
        if (requireAboard && !aboard) {
            return WarpFailure.UNAUTHORISED;
        }
        return WarpFailure.NONE;
    }

    /**
     * Whether a destination can be warped to.
     *
     * @param anchorEnabled           the anchor's owner has it switched on
     * @param sameDimension           the anchor is in the airship's own dimension
     * @param crossDimensionSupported a handler is registered and the config allows it
     * @param distance                world distance to the anchor
     * @param mass                    airship mass used by the cost formula
     * @param charge                  the drive's stored charge, 0..1
     * @param formula                 the tier's cost settings
     */
    public static WarpFailure checkDestination(boolean anchorEnabled,
                                               boolean sameDimension,
                                               boolean crossDimensionSupported,
                                               double distance,
                                               double mass,
                                               double charge,
                                               WarpCost.Formula formula) {
        if (!anchorEnabled) {
            return WarpFailure.ANCHOR_DISABLED;
        }
        if (!sameDimension) {
            return crossDimensionSupported ? WarpFailure.NONE : WarpFailure.DIMENSION_UNSUPPORTED;
        }

        WarpFailure range = formula.checkRange(distance);
        if (range.isFailure()) {
            return range;
        }
        if (formula.exceedsFullCharge(distance, mass)) {
            return WarpFailure.DESTINATION_TOO_FAR;
        }
        // A hair of tolerance so a jump costing exactly the stored charge is allowed.
        if (charge + 1.0e-4D < formula.cost(distance, mass)) {
            return WarpFailure.INSUFFICIENT_CHARGE;
        }
        return WarpFailure.NONE;
    }
}
