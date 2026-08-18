package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side gatekeeping for every warp.
 *
 * <p>The client sends at most an anchor id. Every other input - which airship, how far, how much it
 * costs, whether the player is allowed - is recomputed here from server state.
 */
public final class WarpValidator {

    private WarpValidator() {
    }

    /**
     * Checks that this player is entitled to command this drive.
     *
     * <p>Sable and Simulated have no notion of airship ownership, so authority is proximity plus
     * presence: the player must be within reach of the drive and, by default, actually standing on
     * the airship that is about to move.
     */
    public static WarpFailure validatePlayer(Player player, RiftDriveBlockEntity drive) {
        if (drive.isRemoved() || drive.getLevel() == null) {
            return WarpFailure.DRIVE_BUSY;
        }
        Airship airship = drive.airship();
        if (airship == null || !airship.isActive()) {
            return WarpFailure.NO_AIRSHIP;
        }

        // The drive's block position lives in the airship's plot, so compare in world space.
        Vec3 driveWorld = airship.toWorld(Vec3.atCenterOf(drive.getBlockPos()));
        return WarpRules.checkAuthority(
                true,
                true,
                player.position().distanceToSqr(driveWorld),
                AWConfig.MAX_INTERACTION_DISTANCE.get(),
                airship.isAboard(player),
                AWConfig.REQUIRE_PLAYER_ABOARD.get(),
                player.hasPermissions(2));
    }

    /** Checks the destination itself: dimension support, range and affordability. */
    public static WarpFailure validateDestination(Airship airship, WarpAnchor anchor, RiftDriveTier tier, double charge) {
        boolean sameDimension = anchor.isInSameDimension(airship.level());
        return WarpRules.checkDestination(
                anchor.enabled(),
                sameDimension,
                CrossDimensionWarp.isSupported(),
                sameDimension ? distanceTo(airship, anchor) : -1.0D,
                effectiveMass(airship),
                charge,
                WarpCost.fromConfig(tier));
    }

    /** World distance between the airship and an anchor, or {@code -1} across dimensions. */
    public static double distanceTo(Airship airship, WarpAnchor anchor) {
        if (!anchor.isInSameDimension(airship.level())) {
            return -1.0D;
        }
        Vector3d centre = airship.centre(new Vector3d());
        double dx = centre.x - (anchor.pos().getX() + 0.5D);
        double dy = centre.y - (anchor.pos().getY() + 0.5D);
        double dz = centre.z - (anchor.pos().getZ() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The mass the cost formula uses.
     *
     * <p>Sable's physical mass is preferred because it reflects what the airship is actually built
     * from. When the mass tracker has not been built yet - briefly after assembly - the bounding-box
     * volume stands in, scaled to roughly the same magnitude.
     */
    public static double effectiveMass(Airship airship) {
        double mass = airship.mass();
        return mass > 0.0D ? mass : airship.structureVolume() * 100.0D;
    }

    /**
     * Builds the destination list the navigation screen shows.
     *
     * <p>Unusable anchors are included with their failure attached rather than hidden, so the pilot
     * can see <em>why</em> somewhere is out of reach.
     */
    public static List<WarpQuote> quoteAll(ServerLevel level, Player player, Airship airship,
                                           RiftDriveTier tier, double charge) {
        WarpAnchorRegistry registry = WarpAnchorRegistry.get(level);
        WarpCost.Formula formula = WarpCost.fromConfig(tier);

        List<WarpQuote> quotes = new ArrayList<>();
        for (WarpAnchor anchor : registry.visibleTo(player, null)) {
            double distance = distanceTo(airship, anchor);
            double cost = distance < 0.0D ? 1.0D : formula.cost(distance, effectiveMass(airship));
            WarpFailure failure = validateDestination(airship, anchor, tier, charge);
            quotes.add(new WarpQuote(anchor.id(), anchor.displayName(), anchor.dimension(), anchor.pos(),
                    anchor.network(), distance, cost, failure));
        }
        return quotes;
    }
}
