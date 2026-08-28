package uk.co.iceconchy.aerowarptics.beacon;

import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

/**
 * What a Rift Beacon is allowed to do, decided without touching a level.
 *
 * <p>Separated from the item for the same reason the rest of this mod's rules are: the interesting
 * part is the order the conditions are asked in, and that is worth a test rather than a playthrough.
 *
 * <p>The order matters in one place in particular. A beacon that was never bound and a beacon whose
 * ship has since been taken apart are two different problems with two different fixes - "point this
 * at a drive first" against "your drive is gone" - and a player told the wrong one goes and does the
 * wrong thing. So being unbound is checked before anything is looked for.
 */
public final class RiftBeaconRules {

    private RiftBeaconRules() {
    }

    /**
     * Whether a summon may proceed, given what the server found.
     *
     * @param bound          the beacon carries a binding at all
     * @param sameDimension  the bound drive is in the dimension the player is standing in
     * @param driveFound     a Rift Drive is still at the bound position and loaded
     * @param driveOnAirship that drive is part of an assembled, active airship
     * @return {@link WarpFailure#NONE} when the summon may go ahead
     */
    public static WarpFailure check(boolean bound, boolean sameDimension, boolean driveFound,
                                    boolean driveOnAirship) {
        if (!bound) {
            return WarpFailure.BEACON_UNBOUND;
        }
        // Before "is it there", because a beacon bound in the Nether is not broken - it is simply
        // not usable from here, and telling somebody their drive is missing would send them home to
        // look for a drive that is exactly where they left it.
        if (!sameDimension) {
            return WarpFailure.DIMENSION_UNSUPPORTED;
        }
        if (!driveFound) {
            return WarpFailure.BEACON_DRIVE_MISSING;
        }
        if (!driveOnAirship) {
            return WarpFailure.NO_AIRSHIP;
        }
        return WarpFailure.NONE;
    }
}
