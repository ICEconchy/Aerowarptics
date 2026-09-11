package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;

/**
 * Proves the departure path clear before a warp is allowed to open a rift.
 *
 * <p>The departure-side mirror of {@link SafeArrival}. Arrival was searched exhaustively for years
 * while departure was never checked at all - and yet the hull is flown, in real world space over the
 * mooring, at the entry aperture, through it, and then straight on down the corridor for the whole
 * corridor run. Fly any of that into a hillside and Sable's solver resolves the penetration by
 * ejecting the sub-level at speed: the launch-thousands-of-blocks report. This is the check that was
 * missing.
 *
 * <p>The volume proven clear is the hull swept <em>forward</em> along the world bow from its current
 * pose, through the entry plane, and on for the full {@link WarpFlight#corridorReach() corridor
 * reach}. It is widened to the aperture's radius the same way the arrival is, so the entry aperture
 * opening and its throat are proven clear too - the symmetric treatment the two ends deserve, since
 * the entry throat is every bit as real as the exit one.
 *
 * <p>Kept free of Minecraft in the parts that decide the shape of the volume, exactly as
 * {@link ObstructionScan} and {@link SafeArrival#candidateVolume} are: the geometry is a pure
 * function of doubles and the block test is a {@link ObstructionScan.Solid} predicate, so the wall
 * cases can be swept without a server running. The runtime entry point puts a real level behind that
 * predicate through {@link SafeArrival#isClear}, which also excludes the hull's own sub-level so a
 * ship never reads its own deck as an obstruction.
 */
public final class LaunchClearance {

    private LaunchClearance() {
    }

    /**
     * The whole volume the departure has to prove clear.
     *
     * <p>The hull's footprint swept forward along the bow for {@code reach} blocks, widened by the
     * aperture margin and the configured clearance. Pure geometry: the caller decides what "solid"
     * means.
     *
     * @param hull           the hull's world-space footprint at its current pose
     * @param bow            unit vector the hull will fly along, world space
     * @param reach          how far ahead the hull actually flies before the teleport, from
     *                       {@link WarpFlight#corridorReach()}
     * @param clearance      extra breathing room around the volume, in blocks
     * @param apertureMargin how far past the hull the aperture opening reaches, from
     *                       {@link SafeArrival#apertureMargin}
     */
    public static BoundingBox3d departureVolume(BoundingBox3dc hull, Vector3dc bow, double reach,
                                                double clearance, double apertureMargin) {
        // sweep extends backwards along its approach argument, so a negated bow sweeps forwards.
        Vector3d backwards = new Vector3d(bow).negate();
        return SafeArrival.candidateVolume(hull, backwards, reach, clearance, apertureMargin);
    }

    /**
     * The departure path as segments that follow the bow, rather than one box that encloses it.
     *
     * <p>What is actually tested. On a diagonal bearing the single enclosing box carries two large
     * wedges of terrain either side of the corridor that the hull never approaches, and a block in
     * one of them refused a launch whose real path was clear. Marching the hull along the bearing
     * keeps the tested volume within about a hull of where the ship truly goes.
     */
    public static java.util.List<BoundingBox3d> departureSegments(BoundingBox3dc hull, Vector3dc bow,
                                                                  double reach, double clearance,
                                                                  double apertureMargin) {
        Vector3d backwards = new Vector3d(bow).negate();
        return SafeArrival.sweepSegments(hull, backwards, reach, clearance, apertureMargin);
    }

    /**
     * Whether the departure path is clear, tested against a bare predicate.
     *
     * <p>The Minecraft-free core, so a thin wall at the entry plane, at mid-corridor or at the far end
     * of the corridor reach can each be proven caught without a world. The runtime path routes the
     * same geometry through {@link SafeArrival#isClear} instead.
     *
     * @return {@link ObstructionScan.Verdict#CLEAR} only when nothing solid stands anywhere in the
     *         swept volume; {@link ObstructionScan.Verdict#TOO_LARGE} is not an answer and the caller
     *         must treat it as obstructed, exactly as the arrival check does
     */
    public static ObstructionScan.Verdict scanAhead(BoundingBox3dc hull, Vector3dc bow, double reach,
                                                    double clearance, double apertureMargin,
                                                    long budget, ObstructionScan.Solid solid) {
        BoundingBox3d volume = departureVolume(hull, bow, reach, clearance, apertureMargin);
        int minX = (int) Math.floor(volume.minX());
        int minY = (int) Math.floor(volume.minY());
        int minZ = (int) Math.floor(volume.minZ());
        int maxX = (int) Math.ceil(volume.maxX());
        int maxY = (int) Math.ceil(volume.maxY());
        int maxZ = (int) Math.ceil(volume.maxZ());
        return ObstructionScan.scan(minX, minY, minZ, maxX, maxY, maxZ, budget, solid);
    }

    /**
     * Proves the departure path of a planned flight clear against a real level.
     *
     * <p>Called at the mooring, before the rift opens and before any velocity is commanded, so a
     * blocked launch is a pure refusal with the ship untouched - never a hull ejected mid-corridor.
     *
     * @param airship the vessel about to leave, whose current pose the sweep starts from
     * @param level   the level the hull is flying through, i.e. its own
     * @param bow     the world bow the flight will fly along
     * @param flight  the planned flight, for its corridor reach and aperture margin
     * @return {@code true} when the path ahead is clear
     */
    public static boolean isClear(Airship airship, ServerLevel level, Vector3dc bow, WarpFlight flight) {
        return clearance(airship, level, bow, flight) == SafeArrival.Clearance.CLEAR;
    }

    /**
     * What the departure corridor actually is: clear, obstructed, or not provable.
     *
     * <p>The refusal is the same for the last two - an unproven corridor is not a safe one - but the
     * pilot is told which, because "there is a wall on that bearing" and "I could not finish
     * checking" call for different responses.
     */
    public static SafeArrival.Clearance clearance(Airship airship, ServerLevel level, Vector3dc bow,
                                                  WarpFlight flight) {
        return SafeArrival.clearance(airship, level,
                departureSegments(airship.assemblyBounds(), bow, launchReach(flight),
                        AWConfig.ARRIVAL_CLEARANCE.get(), flight.apertureMargin()),
                SafeArrival.budgetFor(airship));
    }

    /**
     * The first solid block standing in a planned flight's departure corridor, or {@code null} when
     * none is found.
     *
     * <p>The reporting counterpart to {@link #isClear}: same volume, same level, but it names where
     * the corridor is fouled so a refused launch can point the pilot at the collision. A {@code null}
     * is "no block to name" - a clear corridor, or one too large to prove - and never a clearance
     * verdict in its own right; {@link #isClear} decides whether a warp may go.
     */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.core.BlockPos firstObstruction(Airship airship, ServerLevel level,
                                                               Vector3dc bow, WarpFlight flight) {
        for (BoundingBox3d segment : departureSegments(airship.assemblyBounds(), bow,
                launchReach(flight), AWConfig.ARRIVAL_CLEARANCE.get(), flight.apertureMargin())) {
            net.minecraft.core.BlockPos hit = SafeArrival.firstObstruction(level, segment);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * How far the hull flies through open air before the aperture begins to take it.
     *
     * <p>The bow guard's whole extent. Past this the ship is being drawn into the throat, and past
     * that it is inside one; this is the stretch a pilot can actually see and steer around.
     */
    public static double guardReach() {
        return AWConfig.RIFT_LEAD_DISTANCE.get();
    }

    /**
     * The short, cheap departure test that runs whatever the config says.
     *
     * <p>The bare hull swept forward to the aperture - no aperture margin, no arrival clearance, no
     * corridor. It answers the one question the full proof was really there for: is the ship about
     * to be driven into a hillside directly ahead, which is what makes Sable's solver eject it.
     *
     * <p>Sized from the hull and a fixed lead rather than from the corridor, so it costs the same for
     * a flying city as for a skiff and cannot become a size limit on ships the way proving the whole
     * corridor did. It also refuses only on a block that was actually <em>seen</em>: an unproven
     * volume is waved through here rather than treated as an obstruction, because a guard that
     * refuses what it could not read is the failure this replaced.
     */
    public static SafeArrival.Clearance guard(Airship airship, ServerLevel level, Vector3dc bow) {
        return SafeArrival.clearance(airship, level,
                departureSegments(airship.assemblyBounds(), bow, guardReach(), 0.0D, 0.0D),
                SafeArrival.budgetFor(airship));
    }

    /** Where the bow guard is fouled, for telling the pilot which way to come about. */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.core.BlockPos guardObstruction(Airship airship, ServerLevel level,
                                                               Vector3dc bow) {
        for (BoundingBox3d segment : departureSegments(airship.assemblyBounds(), bow,
                guardReach(), 0.0D, 0.0D)) {
            net.minecraft.core.BlockPos hit = SafeArrival.firstObstruction(level, segment);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * The full length of departure path to prove clear and keep resident, from the hull's leading
     * face.
     *
     * <p>The bow first runs {@code riftLeadDistance} blocks out to reach the entry plane, then the
     * passage draws the hull through, then the corridor run carries on - all in real world space over
     * the mooring. The corridor reach on its own is measured from the plane, so the standoff to the
     * plane is added to cover the run at the aperture as well as the run past it.
     */
    public static double launchReach(WarpFlight flight) {
        return flight.corridorReach() + AWConfig.RIFT_LEAD_DISTANCE.get();
    }
}
