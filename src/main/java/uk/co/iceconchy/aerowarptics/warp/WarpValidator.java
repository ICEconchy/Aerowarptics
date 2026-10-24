package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
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
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

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
            if (Trace.enabled()) {
                Trace.changed("access:" + player.getUUID() + "@" + drive.getBlockPos().asLong(), Trace.ACCESS,
                        "{} may not command drive {}: no_airship - the drive is not part of an assembled airship"
                                + " (Sable has not ticked it as one)", Trace.who(player), Trace.pos(drive.getBlockPos()));
            }
            return WarpFailure.NO_AIRSHIP;
        }

        // The drive's block position lives in the airship's plot, so the player is taken into ship space
        // rather than the drive out of it - a hull only turns and moves, so distances survive the trip.
        //
        // Measured to the nearest part of the frame the drive is built into, not to the drive's own
        // block: the far side of a seven-block drive is its console as much as the drive is, and a
        // player standing there is otherwise several blocks further off than the machine in front of
        // them. The box runs between the middles of the frame's outer cells, so a drive on its own is
        // measured to its centre exactly as it always was.
        Vec3 playerShip = airship.toShip(player.position());
        net.minecraft.world.phys.AABB reach = drive.frame().bounds().deflate(0.5D);
        double dx = Math.max(0.0D, Math.max(reach.minX - playerShip.x, playerShip.x - reach.maxX));
        double dy = Math.max(0.0D, Math.max(reach.minY - playerShip.y, playerShip.y - reach.maxY));
        double dz = Math.max(0.0D, Math.max(reach.minZ - playerShip.z, playerShip.z - reach.maxZ));
        boolean aboard = airship.isAboard(player);
        WarpFailure verdict = WarpRules.checkAuthority(
                true,
                true,
                dx * dx + dy * dy + dz * dz,
                AWConfig.MAX_INTERACTION_DISTANCE.get(),
                aboard,
                AWConfig.REQUIRE_PLAYER_ABOARD.get(),
                player.hasPermissions(2));
        // Asked again on every console and chart refresh, so only a change is worth a line. Being let in is
        // not news, but it is remembered, or a second refusal matching an earlier one would go unheard.
        if (Trace.enabled()) {
            String subject = "access:" + player.getUUID() + "@" + drive.getBlockPos().asLong();
            if (verdict.isFailure()) {
                Trace.changed(subject, Trace.ACCESS,
                        "{} may not command drive {}: {} - {} blocks from its frame (reach {}), aboard={}"
                                + " (required={}), operator={}",
                        Trace.who(player), Trace.pos(drive.getBlockPos()), verdict.getSerializedName(),
                        Trace.blocks(Math.sqrt(dx * dx + dy * dy + dz * dz)),
                        Trace.blocks(AWConfig.MAX_INTERACTION_DISTANCE.get()), aboard,
                        AWConfig.REQUIRE_PLAYER_ABOARD.get(), player.hasPermissions(2));
            } else {
                Trace.settled(subject, "ok");
            }
        }
        return verdict;
    }

    /** Checks the destination itself: dimension support, range and affordability. */
    public static WarpFailure validateDestination(Airship airship, WarpAnchor anchor, RiftDriveTier tier, double charge) {
        return validateDestination(airship, anchor, charge, effectiveMass(airship), WarpCost.fromConfig(tier));
    }

    /** The same, for a caller that has already weighed the vessel - a chart asks this once per anchor. */
    private static WarpFailure validateDestination(Airship airship, WarpAnchor anchor, double charge, double mass,
                                                   WarpCost.Formula formula) {
        return WarpRules.checkDestination(
                anchor.enabled(),
                anchor.isInSameDimension(airship.level()),
                CrossDimensionWarp.isSupported(),
                warpDistance(airship, anchor),
                mass,
                charge,
                formula);
    }

    /**
     * Checks a destination that nobody has marked.
     *
     * <p>The counterpart of {@link #validateDestination} for a Rift Probe's fix. It asks the same
     * rules the same way - is it in range, can the drive afford it, can the ship leave its dimension
     * for it - minus the one question only an anchor can answer: a position cannot be switched off.
     *
     * @param course a fix course; where it is, and which dimension it is in, are both the course's to say
     */
    public static WarpFailure validateFix(Airship airship, WarpCourse course, RiftDriveTier tier, double charge) {
        return WarpRules.checkDestination(
                true,
                course.fixIsIn(airship.level().dimension()),
                CrossDimensionWarp.isSupported(),
                fixDistance(airship, course),
                effectiveMass(airship),
                charge,
                WarpCost.fromConfig(tier));
    }

    /**
     * How long a jump to this fix is, for the price, the range and the spin-up: the real distance in the
     * ship's own dimension, and the server's {@code crossDimensionDistance} into another - an anchor's
     * rule, see {@link #warpDistance}, for a sounding a Rift Probe took somewhere else.
     */
    public static double fixDistance(Airship airship, WarpCourse course) {
        return course.fixIsIn(airship.level().dimension())
                ? distanceTo(airship, course.fix())
                : AWConfig.CROSS_DIMENSION_DISTANCE.get();
    }

    /** World distance between the airship's centre and a block. */
    public static double distanceTo(Airship airship, BlockPos target) {
        Vector3d centre = airship.centre(new Vector3d());
        double dx = centre.x - (target.getX() + 0.5D);
        double dy = centre.y - (target.getY() + 0.5D);
        double dz = centre.z - (target.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * How long a jump to this anchor is, for the price, the range and the spin-up.
     *
     * <p>The real distance in the ship's own dimension. Across dimensions there is none - the two sets of
     * coordinates are not in the same space - so the jump counts as the server's
     * {@code crossDimensionDistance}, which keeps a crossing inside the same rules as every other warp
     * rather than making the far side of a dimension the one place any drive reaches for nothing.
     */
    public static double warpDistance(Airship airship, WarpAnchor anchor) {
        return anchor.isInSameDimension(airship.level())
                ? distanceTo(airship, anchor)
                : AWConfig.CROSS_DIMENSION_DISTANCE.get();
    }

    /**
     * World distance between the airship and an anchor, or {@code -1} across dimensions.
     *
     * <p>What the chart shows. For what a jump costs, use {@link #warpDistance}.
     */
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
     *
     * <p>The whole vessel's: the hull and every {@link Airship#parts() part} a warp carries with it. A ship
     * docked alongside goes through the rift too, and weighing only the hull would fly it for nothing.
     */
    public static double effectiveMass(Airship airship) {
        double total = ownMass(airship);
        for (ServerSubLevel part : airship.parts()) {
            total += ownMass(Airship.of(part));
        }
        return total;
    }

    /**
     * How far this vessel can actually be thrown by a drive of this tier, on a full charge.
     *
     * <p>The tier's range is what the drive could reach with nothing to carry. A hull spends part of every
     * charge on its own mass, so a heavy one runs out of charge well short of that range - which is the
     * figure a pilot needs, and the one the screens used not to show.
     *
     * @return the reach in blocks, or {@code -1} when this vessel is too heavy for the drive to jump at all
     */
    public static double reach(Airship airship, RiftDriveTier tier) {
        return WarpCost.fromConfig(tier).reach(effectiveMass(airship));
    }

    private static double ownMass(Airship airship) {
        double mass = airship.mass();
        return mass > 0.0D ? mass : airship.structureVolume() * 100.0D;
    }

    /**
     * Every anchor a player may pick as a destination, by name - the public ones, their own, and those of
     * the networks they are in.
     */
    public static List<WarpAnchor> destinations(ServerLevel level, Player player) {
        return WarpAnchorRegistry.get(level).visibleTo(player, WarpNetworkRegistry.get(level), null);
    }

    /**
     * Builds the destination list the navigation screen shows.
     *
     * <p>Unusable anchors are included with their failure attached rather than hidden, so the pilot
     * can see <em>why</em> somewhere is out of reach.
     *
     * @param anchors the anchors to price - {@link #destinations}, or the part of it a chart is filtered to.
     *                Filtering before pricing, and before the list is cut to fit a packet, is what lets a
     *                filtered chart reach anchors the cut would otherwise hide
     */
    public static List<WarpQuote> quote(ServerLevel level, List<WarpAnchor> anchors, Airship airship,
                                        RiftDriveTier tier, double charge) {
        WarpNetworkRegistry networks = WarpNetworkRegistry.get(level);
        WarpCost.Formula formula = WarpCost.fromConfig(tier);

        // Weighed once for the whole list. The vessel's mass is the same for every anchor on it, and
        // weighing it walks every machine aboard to find what the hull carries.
        double mass = effectiveMass(airship);
        List<WarpQuote> quotes = new ArrayList<>();
        for (WarpAnchor anchor : anchors) {
            double distance = distanceTo(airship, anchor);
            double cost = formula.cost(warpDistance(airship, anchor), mass);
            WarpFailure failure = validateDestination(airship, anchor, charge, mass, formula);
            quotes.add(new WarpQuote(anchor.id(), anchor.displayName(), anchor.dimension(), anchor.pos(),
                    networks.nameOf(anchor.network()), distance, cost, failure));
        }
        return quotes;
    }
}
