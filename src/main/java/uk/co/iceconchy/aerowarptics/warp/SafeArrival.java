package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;

import java.util.List;

/**
 * Finds somewhere an airship can actually materialise.
 *
 * <p>An airship is never dropped onto an anchor block. The search starts from a <em>clearance
 * point</em>: the height at which the hull's underside would sit a configured buffer above the
 * anchor, computed from the airship's own footprint rotated into the orientation it will arrive with.
 * A skiff clears the anchor by a few blocks; a two-hundred-block dreadnought starts a hundred blocks
 * up, because that is where its keel has to be for its deck not to be inside the mountain.
 *
 * <p>From there {@link ArrivalSearch} climbs the anchor's column, and only once every height in that
 * column is ruled out does it start stepping sideways. So a blocked column means "come in higher",
 * not "come in beside it", which is what a pilot expects and what keeps a large hull out of terrain.
 */
public final class SafeArrival {

    private SafeArrival() {
    }

    /** How many candidate positions the search will consider before giving up. */
    private static final int CANDIDATE_LIMIT = 4096;

    /**
     * A validated arrival.
     *
     * @param origin      world position for the airship's origin
     * @param orientation world orientation for the airship
     * @param bounds      world-space footprint the airship will occupy there
     * @param liftOff     blocks between the anchor and the hull's underside
     */
    public record Result(Vector3d origin, Quaterniondc orientation, BoundingBox3d bounds, double liftOff) {
    }

    /**
     * Searches for a clear volume the airship fits in, near the anchor.
     *
     * <p>The airship does not simply appear at the result: it comes out of the exit rift already
     * moving and coasts the last stretch. That run-out is part of what has to be clear, so the volume
     * tested at each candidate is the hull swept backwards along {@code approach} - the whole path,
     * not just the destination.
     *
     * @param airship      the airship being moved
     * @param destination  level the anchor lives in
     * @param anchorPos    the anchor block
     * @param orientation  orientation the airship will arrive with
     * @param approach     unit vector the airship will be travelling along as it emerges
     * @param runOut       how far back along {@code approach} the airship first appears
     * @return the chosen placement, or {@code null} when nothing within the configured radius fits
     */
    @Nullable
    public static Result find(Airship airship, ServerLevel destination, BlockPos anchorPos,
                              Quaterniondc orientation, Vector3dc approach, double runOut) {
        BoundingBox3ic plotBounds = airship.shipBounds();
        if (plotBounds == null) {
            return null;
        }

        // The airship's footprint in plot coordinates, matching how Sable derives its world bounds.
        BoundingBox3d localBounds = new BoundingBox3d(
                plotBounds.minX(), plotBounds.minY(), plotBounds.minZ(),
                plotBounds.maxX() + 1.0D, plotBounds.maxY() + 1.0D, plotBounds.maxZ() + 1.0D);

        Pose3dc currentPose = airship.pose();
        Pose3d probePose = new Pose3d(currentPose);
        probePose.orientation().set(orientation);

        // Measure the rotated hull once, so the clearance height accounts for how the ship will sit.
        BoundingBox3d measured = new BoundingBox3d();
        probePose.position().set(0.0D, 0.0D, 0.0D);
        localBounds.transform(probePose, measured);
        Vector3d hullSize = measured.size(new Vector3d());
        Vector3d hullCentreOffset = measured.center(new Vector3d());

        double buffer = AWConfig.ARRIVAL_GROUND_BUFFER.get();
        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();

        // Where the hull's centre has to be for its underside to clear the anchor by the buffer.
        Vector3d clearancePoint = new Vector3d(
                anchorPos.getX() + 0.5D,
                anchorPos.getY() + 1.0D + buffer + hullSize.y * 0.5D,
                anchorPos.getZ() + 0.5D);

        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(
                AWConfig.SAFE_ARRIVAL_RADIUS.get(),
                AWConfig.SAFE_ARRIVAL_VERTICAL_RADIUS.get(),
                AWConfig.SAFE_ARRIVAL_STEP.get(),
                CANDIDATE_LIMIT);

        BoundingBox3d worldBounds = new BoundingBox3d();
        for (ArrivalSearch.Candidate candidate : candidates) {
            // Place the hull's centre on the candidate: the pose origin is not the centre, so the
            // measured offset is subtracted rather than assumed to be zero.
            probePose.position().set(
                    clearancePoint.x + candidate.dx() - hullCentreOffset.x,
                    clearancePoint.y + candidate.dy() - hullCentreOffset.y,
                    clearancePoint.z + candidate.dz() - hullCentreOffset.z);
            localBounds.transform(probePose, worldBounds);

            BoundingBox3d padded = sweep(worldBounds, approach, runOut).expand(clearance, new BoundingBox3d());
            if (!isClear(airship, destination, padded)) {
                continue;
            }
            return new Result(
                    new Vector3d(probePose.position()),
                    orientation,
                    new BoundingBox3d(worldBounds),
                    worldBounds.minY() - (anchorPos.getY() + 1.0D));
        }
        return null;
    }

    /** Extends a hull's footprint backwards along its approach, covering the whole run-out. */
    private static BoundingBox3d sweep(BoundingBox3dc hull, Vector3dc approach, double distance) {
        BoundingBox3d swept = new BoundingBox3d(hull.minX(), hull.minY(), hull.minZ(),
                hull.maxX(), hull.maxY(), hull.maxZ());
        if (distance <= 0.0D) {
            return swept;
        }
        return swept.expandTo(
                hull.minX() - approach.x() * distance,
                hull.minY() - approach.y() * distance,
                hull.minZ() - approach.z() * distance,
                swept)
                .expandTo(
                        hull.maxX() - approach.x() * distance,
                        hull.maxY() - approach.y() * distance,
                        hull.maxZ() - approach.z() * distance,
                        swept);
    }

    /** True when the volume contains no collidable blocks and no other airship. */
    public static boolean isClear(Airship airship, ServerLevel level, BoundingBox3dc bounds) {
        if (bounds.minY() < level.getMinBuildHeight()) {
            return false;
        }
        // Arriving above the build height is fine - an airship belongs in the sky - so only the
        // volume that overlaps the world is worth loading and testing.
        if (bounds.minY() > level.getMaxBuildHeight()) {
            return !intersectsAnotherAirship(airship, level, bounds);
        }
        if (!ensureLoaded(level, bounds)) {
            return false;
        }
        if (intersectsAnotherAirship(airship, level, bounds)) {
            return false;
        }
        return !containsBlocks(level, bounds);
    }

    /**
     * Loads the chunks the volume covers so the obstruction test sees real blocks.
     *
     * @return {@code false} when the volume spans more chunks than is reasonable to load at once
     */
    private static boolean ensureLoaded(ServerLevel level, BoundingBox3dc bounds) {
        int minChunkX = (int) Math.floor(bounds.minX()) >> 4;
        int maxChunkX = (int) Math.floor(bounds.maxX()) >> 4;
        int minChunkZ = (int) Math.floor(bounds.minZ()) >> 4;
        int maxChunkZ = (int) Math.floor(bounds.maxZ()) >> 4;

        long chunkCount = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (chunkCount > 1024L) {
            return false;
        }
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (level.getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Rejects a candidate that would overlap another sub-level, including a docked neighbour. */
    private static boolean intersectsAnotherAirship(Airship airship, ServerLevel level, BoundingBox3dc bounds) {
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, bounds)) {
            if (other != airship.subLevel()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Samples blocks inside the volume.
     *
     * <p>Large airships are sampled on a stride rather than block-by-block, bounded by
     * {@code maxShipBlockSamples}, so the check stays cheap for a flying city while still catching
     * terrain. The stride is chosen from the volume, so a big hull is sampled coarsely and a small one
     * exactly.
     */
    private static boolean containsBlocks(ServerLevel level, BoundingBox3dc bounds) {
        int minX = (int) Math.floor(bounds.minX());
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY()));
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.ceil(bounds.maxX());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(bounds.maxY()));
        int maxZ = (int) Math.ceil(bounds.maxZ());

        long spanX = Math.max(1L, maxX - minX + 1L);
        long spanY = Math.max(1L, maxY - minY + 1L);
        long spanZ = Math.max(1L, maxZ - minZ + 1L);
        long total = spanX * spanY * spanZ;

        int budget = AWConfig.MAX_SHIP_BLOCK_SAMPLES.get();
        int stride = total <= budget ? 1 : (int) Math.max(1L, Math.round(Math.cbrt((double) total / budget)));

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y += stride) {
            for (int x = minX; x <= maxX; x += stride) {
                for (int z = minZ; z <= maxZ; z += stride) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (!state.getCollisionShape(level, cursor).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Convenience: is the anchor's own chunk available at all? */
    public static boolean isDestinationLoadable(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        return level.getChunk(chunkPos.x, chunkPos.z,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true) != null;
    }
}
