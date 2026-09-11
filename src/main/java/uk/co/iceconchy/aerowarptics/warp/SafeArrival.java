package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;

import java.util.ArrayList;
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
     * <p>The volume proven clear is not merely the hull's own box. The hull comes out of a rift
     * aperture that is wider than it is, and it flies out through the throat behind that aperture, so
     * a block that clears the hull but fouls the opening it flies through is still an obstruction. The
     * {@code apertureMargin} widens the whole run-out to the aperture's radius, which is what proves
     * the exit disc and the throat clear as well as the hull - folded into the per-candidate test so a
     * position that clears the hull but not its aperture is rejected and the search carries on, rather
     * than being committed and failing later.
     *
     * @param airship        the airship being moved
     * @param destination    level the anchor lives in
     * @param anchorPos      the anchor block
     * @param orientation    orientation the airship will arrive with
     * @param approach       unit vector the airship will be travelling along as it emerges
     * @param runOut         how far back along {@code approach} the airship first appears
     * @param apertureMargin how far past the hull's own footprint the aperture opening reaches, from
     *                       {@link #apertureMargin}; zero reduces this to the old hull-only sweep
     * @return the chosen placement, or {@code null} when nothing within the configured radius fits
     */
    @Nullable
    public static Result find(Airship airship, ServerLevel destination, BlockPos anchorPos,
                              Quaterniondc orientation, Vector3dc approach, double runOut,
                              double apertureMargin) {
        // The whole assembly's footprint in plot coordinates - the hull plus any connected sub-levels
        // and bearing-mounted propellers - so the space proven clear at the far end holds everything
        // that arrives, not just the bare plot. Null only when the hull has no plot to measure.
        BoundingBox3d localBounds = airship.assemblyShipBounds();
        if (localBounds == null) {
            return null;
        }

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

            // The run-out marched along the approach rather than boxed around it, so a candidate is
            // not rejected for terrain sitting off to the side of a diagonal arrival.
            if (clearance(airship, destination,
                    sweepSegments(worldBounds, approach, runOut, clearance, apertureMargin),
                    budgetFor(airship)) != Clearance.CLEAR) {
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

    /**
     * How far past the hull's own footprint an aperture reaches.
     *
     * <p>The rift the hull flies through is scaled to a multiple of the hull's radius, so its opening
     * stands off the hull on every side by this much. Proving only the hull's box clear leaves that
     * ring untested, and a block sitting in it fouls the aperture the hull flies out through even
     * though it never touches the hull. Never negative: an aperture is never narrower than its hull.
     *
     * @param apertureRadius the rift aperture's radius, {@code hullRadius * riftRadiusFactor}
     * @param hullRadius     the hull's own radius the aperture was scaled from
     */
    public static double apertureMargin(double apertureRadius, double hullRadius) {
        return Math.max(0.0D, apertureRadius - hullRadius);
    }

    /**
     * The whole volume one arrival candidate has to prove clear: the hull swept back along its
     * run-out, widened to the aperture's radius, plus the configured clearance.
     *
     * <p>Kept as a pure function of doubles so the geometry can be checked without a world - the block
     * test that consumes it is {@link #isClear}, and the exhaustive scan behind that is
     * {@link ObstructionScan}, each tested in its own right.
     */
    public static BoundingBox3d candidateVolume(BoundingBox3dc hull, Vector3dc approach, double runOut,
                                                double clearance, double apertureMargin) {
        return sweep(hull, approach, runOut)
                .expand(clearance + Math.max(0.0D, apertureMargin), new BoundingBox3d());
    }

    /**
     * The same swept path as {@link #candidateVolume}, cut into segments that follow the bearing
     * instead of one box that encloses it.
     *
     * <p>A single axis-aligned box around a <em>diagonal</em> sweep is enormously bigger than the
     * path it describes: a run of {@code d} blocks at forty-five degrees spans {@code d / sqrt(2)}
     * on each horizontal axis, so the enclosing box picks up two large wedges of terrain either side
     * of the corridor that the hull never goes near. Those wedges were tested like everything else,
     * and a block sitting in one of them refused a launch whose actual path was clear - the
     * over-sensitivity pilots complained about, and on a long corridor most of the volume being read.
     *
     * <p>Marching the hull's own footprint along the bearing instead keeps the tested volume within
     * about a hull of the real path whatever the heading. Each segment is the union of the hull at
     * the two ends of its sub-interval, which is a superset of the true sweep across it - so this is
     * tighter than the old box but never misses anything the hull would actually hit.
     *
     * <p>The step is the hull's own shadow along the bearing, so segments tile the corridor rather
     * than overlapping heavily; on an axis-aligned bearing this reduces to the old box cut into
     * slices, and costs the same.
     *
     * @return at least one box; the union of them all is contained by {@link #candidateVolume}
     */
    public static List<BoundingBox3d> sweepSegments(BoundingBox3dc hull, Vector3dc approach,
                                                    double distance, double clearance,
                                                    double apertureMargin) {
        double pad = clearance + Math.max(0.0D, apertureMargin);
        List<BoundingBox3d> segments = new ArrayList<>();
        if (!(distance > 0.0D)) {
            segments.add(sweep(hull, approach, 0.0D).expand(pad, new BoundingBox3d()));
            return segments;
        }

        // Cutting a sweep up is not free: each segment carries the hull's own extent as well as its
        // share of the run, so consecutive segments overlap by a hull. On an axis-aligned bearing the
        // enclosing box is already tight and slicing it only adds that overlap - which is why the
        // count is chosen rather than assumed. One segment (the old single box) is always among the
        // candidates, so this can never come out worse than what it replaced; on a diagonal, where
        // the box carries wedges the hull never enters, a higher count wins easily.
        List<BoundingBox3d> best = null;
        double bestVolume = Double.MAX_VALUE;
        for (int count : SEGMENT_COUNTS) {
            if (count > 1 && distance / count < MIN_SEGMENT_STEP) {
                break; // slices this fine are all overlap
            }
            List<BoundingBox3d> candidate = slice(hull, approach, distance, pad, count);
            double volume = 0.0D;
            for (BoundingBox3d box : candidate) {
                volume += box.width() * box.height() * box.length();
            }
            if (volume < bestVolume) {
                bestVolume = volume;
                best = candidate;
            }
        }
        return best == null ? slice(hull, approach, distance, pad, 1) : best;
    }

    /** The sweep cut into {@code count} equal sub-sweeps, each padded. */
    private static List<BoundingBox3d> slice(BoundingBox3dc hull, Vector3dc approach, double distance,
                                             double pad, int count) {
        List<BoundingBox3d> segments = new ArrayList<>(count);
        double span = distance / count;
        for (int i = 0; i < count; i++) {
            double from = i * span;
            double to = Math.min(distance, (i + 1) * span);
            // The hull at `from`, grown to also hold the hull at `to`: the sub-sweep between them.
            BoundingBox3d segment = sweep(shift(hull, approach, from), approach, to - from);
            segments.add(segment.expand(pad, new BoundingBox3d()));
        }
        return segments;
    }

    /**
     * Segment counts considered, smallest first. One is the old single enclosing box.
     *
     * <p>Finely spaced in the low counts because that is where the optimum sits: total volume falls
     * as the segments stop enclosing wedges, then rises again as per-segment hull overlap takes
     * over, and the minimum is usually under twenty.
     */
    private static final int[] SEGMENT_COUNTS = {1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64};

    /** The hull's footprint moved {@code distance} along the sweep, matching {@link #sweep}'s sign. */
    private static BoundingBox3d shift(BoundingBox3dc hull, Vector3dc approach, double distance) {
        return new BoundingBox3d(
                hull.minX() - approach.x() * distance,
                hull.minY() - approach.y() * distance,
                hull.minZ() - approach.z() * distance,
                hull.maxX() - approach.x() * distance,
                hull.maxY() - approach.y() * distance,
                hull.maxZ() - approach.z() * distance);
    }

    /** A floor under the step, so a degenerate hull cannot ask for thousands of segments. */
    private static final double MIN_SEGMENT_STEP = 8.0D;

    /** And a ceiling on how many there can ever be, whatever the geometry. */
    private static final int MAX_SEGMENTS = 64;

    /** Extends a hull's footprint backwards along its approach, covering the whole run-out. */
    public static BoundingBox3d sweep(BoundingBox3dc hull, Vector3dc approach, double distance) {
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
        return clearance(airship, level, bounds) == Clearance.CLEAR;
    }

    /**
     * What a volume actually is: clear, obstructed, or not provable.
     *
     * <p>The three used to collapse into one boolean, which is why a corridor the check could not
     * finish reading looked exactly like a corridor with a wall in it - refused, with no block to
     * name, and nothing in the report to say which had happened.
     */
    public static Clearance clearance(Airship airship, ServerLevel level, BoundingBox3dc bounds) {
        return clearance(airship, level, List.of(bounds), budgetFor(airship));
    }

    /**
     * The same question asked of a path cut into segments, with one budget shared across all of them.
     *
     * <p>The budget is shared rather than granted per segment on purpose: a corridor's cost is the
     * whole corridor's, and handing each slice its own allowance would let a path cost sixty-four
     * times what a single box was permitted simply for having been divided up.
     *
     * <p>Obstruction wins over unprovability. A segment with a wall in it is a definite answer and
     * worth reporting as one even if a later segment could not be finished.
     */
    public static Clearance clearance(Airship airship, ServerLevel level,
                                      List<? extends BoundingBox3dc> volumes, long budget) {
        long[] spent = {0L};
        boolean unproven = false;
        for (BoundingBox3dc bounds : volumes) {
            Clearance verdict = clearanceOf(airship, level, bounds, budget, spent);
            if (verdict == Clearance.OBSTRUCTED) {
                return Clearance.OBSTRUCTED;
            }
            unproven |= verdict == Clearance.UNPROVEN;
        }
        return unproven ? Clearance.UNPROVEN : Clearance.CLEAR;
    }

    private static Clearance clearanceOf(Airship airship, ServerLevel level, BoundingBox3dc bounds,
                                         long budget, long[] spent) {
        if (bounds.minY() > level.getMaxBuildHeight()) {
            return intersectsAnotherAirship(airship, level, bounds)
                    ? Clearance.OBSTRUCTED : Clearance.CLEAR;
        }
        if (!ensureLoaded(level, bounds)) {
            return Clearance.UNPROVEN;
        }
        if (intersectsAnotherAirship(airship, level, bounds)) {
            return Clearance.OBSTRUCTED;
        }
        return scanVolume(level, bounds, budget, spent);
    }

    /** Whether a volume is clear, and if not, whether that is a fact or merely unproven. */
    public enum Clearance {
        /** Nothing solid stands in the volume. */
        CLEAR,
        /** Something solid stands in it, or another airship does. */
        OBSTRUCTED,
        /**
         * The volume could not be proved either way: chunks missing, or the budget ran out.
         *
         * <p>Refused exactly as {@link #OBSTRUCTED} is - an unproven volume is not a safe one - but
         * reported differently, because "I could not check" and "there is a wall" want different
         * things from the pilot.
         */
        UNPROVEN
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
     * Whether anything solid stands in the volume - checked at every block, not sampled.
     *
     * <p>This used to walk a stride chosen so a flying city cost about as much to test as a skiff.
     * That made it a sampler, and a sampler cannot answer this question: at a stride of two a
     * one-block floor is invisible, at four a three-block wall is, and a two-hundred-block hull
     * stepped over anything up to eight blocks thick. Mountains were caught because mountains are
     * thick. Buildings, bridge decks and tree trunks were not, and a ship cleared to arrive would
     * materialise inside them.
     *
     * <p>What makes an exhaustive check affordable is skipping rather than sampling. Almost all of
     * the volume a hull sweeps is open air, and a chunk section that holds nothing but air knows so -
     * {@code LevelChunkSection.hasOnlyAir} is a flag, not a search. So whole sixteen-block cubes of
     * sky are dismissed without a single block read, and only sections with something in them are
     * walked, one at a time, stopping at the first thing that would stop the hull.
     *
     * <p>The budget is shared across those sections and is a safety valve rather than a shortcut:
     * running out means the volume could not be <em>proved</em> clear, and an unproven volume is
     * treated as obstructed. That is the whole difference from the old budget, which quietly made
     * the test coarser and let the ship through.
     */
    private static Clearance scanVolume(ServerLevel level, BoundingBox3dc bounds, long budget,
                                        long[] reads) {
        int minX = (int) Math.floor(bounds.minX());
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY()));
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.ceil(bounds.maxX());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(bounds.maxY()));
        int maxZ = (int) Math.ceil(bounds.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return Clearance.CLEAR;
        }

        // Charged for blocks actually read, not for the span of every section walked. The old
        // accounting deducted a section's whole volume even when the section was walked and found
        // clear, so proving an empty corridor cost exactly as much budget as proving a solid one.
        // The counter is the caller's, so a path cut into segments shares one allowance.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ObstructionScan.Solid solid = (x, y, z) -> {
            reads[0]++;
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            return !state.isAir() && !state.getCollisionShape(level, cursor).isEmpty();
        };

        // Column-major: a chunk is fetched once and its sections walked, rather than re-fetched for
        // every section of every column. ensureLoaded has already made these resident, so this asks
        // for what is loaded instead of generating terrain a second time.
        for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    return Clearance.UNPROVEN;
                }
                for (int sectionY = minY >> 4; sectionY <= (maxY >> 4); sectionY++) {
                    if (isSectionEmpty(chunk, sectionY)) {
                        continue;
                    }
                    // Only the part of this section the hull actually reaches into.
                    int fromX = Math.max(minX, chunkX << 4);
                    int toX = Math.min(maxX, (chunkX << 4) + 15);
                    int fromY = Math.max(minY, sectionY << 4);
                    int toY = Math.min(maxY, (sectionY << 4) + 15);
                    int fromZ = Math.max(minZ, chunkZ << 4);
                    int toZ = Math.min(maxZ, (chunkZ << 4) + 15);

                    long remaining = budget - reads[0];
                    if (remaining <= 0L) {
                        return Clearance.UNPROVEN;
                    }
                    ObstructionScan.Verdict verdict = ObstructionScan.scan(
                            fromX, fromY, fromZ, toX, toY, toZ, Math.max(remaining, 4096L), solid);
                    if (verdict == ObstructionScan.Verdict.OBSTRUCTED) {
                        return Clearance.OBSTRUCTED;
                    }
                    if (verdict == ObstructionScan.Verdict.TOO_LARGE) {
                        return Clearance.UNPROVEN;
                    }
                }
            }
        }
        return reads[0] > budget ? Clearance.UNPROVEN : Clearance.CLEAR;
    }

    /**
     * How many blocks a hull is allowed to read while proving one volume clear.
     *
     * <p>Tied to the ship rather than to a flat number. A large hull legitimately has to prove a
     * large volume, and a fixed budget quietly became a size limit on ships: past a certain hull the
     * check ran out before it finished, and an unproven volume is refused - so the biggest vessels
     * were refused every time, with nothing in the way and no block to name.
     * {@code maxArrivalBlockChecks} stays the hard ceiling.
     */
    public static long budgetFor(Airship airship) {
        double scale;
        int ceiling;
        try {
            scale = AWConfig.ARRIVAL_BLOCK_CHECK_SCALE.get();
            ceiling = AWConfig.MAX_ARRIVAL_BLOCK_CHECKS.get();
        } catch (IllegalStateException e) {
            return 4_000_000L; // config not loaded - unit tests
        }
        return budgetFor(airship.structureVolume(), scale, ceiling);
    }

    /**
     * The budget rule itself, free of Sable so it can be checked without a world.
     *
     * @param hullVolume the hull's block volume; a hull of nothing still gets the floor
     * @param scale      block reads allowed per block of hull
     * @param ceiling    {@code maxArrivalBlockChecks}, the hard cap however large the ship
     */
    public static long budgetFor(double hullVolume, double scale, long ceiling) {
        if (!Double.isFinite(hullVolume) || hullVolume <= 0.0D) {
            return Math.min(BUDGET_FLOOR, Math.max(1L, ceiling));
        }
        double scaled = hullVolume * scale;
        long asLong = scaled >= (double) Long.MAX_VALUE ? Long.MAX_VALUE : (long) scaled;
        return Math.max(Math.min(BUDGET_FLOOR, Math.max(1L, ceiling)), Math.min(asLong, ceiling));
    }

    /** Below this the budget is not worth having: even a skiff reads more than this proving a wall. */
    private static final long BUDGET_FLOOR = 65_536L;

    /**
     * Every solid block standing in a volume, up to a cap.
     *
     * <p>The plural of {@link #firstObstruction}: it walks the same section-skipping way but collects
     * rather than stopping at the first, so a clearance visualiser can mark all the blocks a departure
     * would fly into. Capped so a fouled hundred-block corridor cannot return tens of thousands of
     * positions; the cap is a display limit, not a correctness one - {@link #isClear} remains the
     * authority on whether a warp may go.
     */
    public static List<BlockPos> obstructions(ServerLevel level, BoundingBox3dc bounds, int cap) {
        List<BlockPos> hits = new ArrayList<>();
        if (cap <= 0 || bounds.minY() > level.getMaxBuildHeight()) {
            return hits;
        }
        int minX = (int) Math.floor(bounds.minX());
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY()));
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.ceil(bounds.maxX());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(bounds.maxY()));
        int maxZ = (int) Math.ceil(bounds.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return hits;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int sectionY = minY >> 4; sectionY <= (maxY >> 4); sectionY++) {
            for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++) {
                for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null || isSectionEmpty(chunk, sectionY)) {
                        continue;
                    }
                    int fromX = Math.max(minX, chunkX << 4);
                    int toX = Math.min(maxX, (chunkX << 4) + 15);
                    int fromY = Math.max(minY, sectionY << 4);
                    int toY = Math.min(maxY, (sectionY << 4) + 15);
                    int fromZ = Math.max(minZ, chunkZ << 4);
                    int toZ = Math.min(maxZ, (chunkZ << 4) + 15);
                    ObstructionScan.Solid solid = (x, y, z) -> {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        return !state.isAir() && !state.getCollisionShape(level, cursor).isEmpty();
                    };
                    for (ObstructionScan.Hit hit : ObstructionScan.collectSolids(
                            fromX, fromY, fromZ, toX, toY, toZ, cap - hits.size(), solid)) {
                        hits.add(new BlockPos(hit.x(), hit.y(), hit.z()));
                    }
                    if (hits.size() >= cap) {
                        return hits;
                    }
                }
            }
        }
        return hits;
    }

    /**
     * The first solid block standing in a volume, or {@code null} when nothing solid is found.
     *
     * <p>The reporting counterpart to {@link #containsBlocks}: it walks the same section-skipping way
     * and stops at the same first block, but hands back where that block is so a refused launch can
     * name the collision to the pilot. A {@code null} means "no block to point at" - either the volume
     * is genuinely clear of blocks, or it was too large to prove within the budget - so a caller must
     * not read null as clearance; {@link #isClear} remains the authority on whether a warp may go.
     */
    @Nullable
    public static BlockPos firstObstruction(ServerLevel level, BoundingBox3dc bounds) {
        if (bounds.minY() > level.getMaxBuildHeight()) {
            return null;
        }
        int minX = (int) Math.floor(bounds.minX());
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY()));
        int minZ = (int) Math.floor(bounds.minZ());
        int maxX = (int) Math.ceil(bounds.maxX());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(bounds.maxY()));
        int maxZ = (int) Math.ceil(bounds.maxZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }

        long budget = Math.max(1L, (long) AWConfig.MAX_ARRIVAL_BLOCK_CHECKS.get());
        long[] reads = {0L};
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ObstructionScan.Solid solid = (x, y, z) -> {
            reads[0]++;
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            return !state.isAir() && !state.getCollisionShape(level, cursor).isEmpty();
        };

        for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (int sectionY = minY >> 4; sectionY <= (maxY >> 4); sectionY++) {
                    if (isSectionEmpty(chunk, sectionY)) {
                        continue;
                    }
                    int fromX = Math.max(minX, chunkX << 4);
                    int toX = Math.min(maxX, (chunkX << 4) + 15);
                    int fromY = Math.max(minY, sectionY << 4);
                    int toY = Math.min(maxY, (sectionY << 4) + 15);
                    int fromZ = Math.max(minZ, chunkZ << 4);
                    int toZ = Math.min(maxZ, (chunkZ << 4) + 15);

                    if (reads[0] > budget) {
                        return null;
                    }
                    ObstructionScan.Hit hit = ObstructionScan.firstSolid(
                            fromX, fromY, fromZ, toX, toY, toZ, Long.MAX_VALUE, solid);
                    if (hit != null) {
                        return new BlockPos(hit.x(), hit.y(), hit.z());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Whether a chunk section holds nothing but air.
     *
     * <p>Outside the world's own vertical range there is no section at all, which is as empty as it
     * gets - a hull above the build limit is in open sky by definition.
     */
    private static boolean isSectionEmpty(LevelChunk chunk, int sectionY) {
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= chunk.getSections().length) {
            return true;
        }
        LevelChunkSection section = chunk.getSections()[index];
        if (section.hasOnlyAir()) {
            return true;
        }
        // A section that is "not empty" is not the same as one that could stop a hull. Water, lava,
        // kelp, seagrass, grass and flowers are all non-air and all pass straight through - and an
        // ocean is thousands of such sections, every one of which used to be read block by block,
        // found nothing, and charged its whole volume to the budget. That is what refused a large
        // ship over open water with no block to name.
        //
        // maybeHas walks the section's *palette* - a handful of distinct states - rather than its
        // 4096 positions, so the same ocean section is dismissed in about two predicate calls.
        return !section.maybeHas(SafeArrival::mightCollide);
    }

    /**
     * Whether a block state could have a collision shape anywhere, judged without a position.
     *
     * <p>Deliberately generous: this only ever decides whether a section is worth reading properly,
     * and the real per-block test behind it is exact. Two cheap tests are unioned because neither
     * alone is sound - {@code blocksMotion} misses a ladder, which has a collision box without
     * blocking movement - and anything that cannot be judged without a world is assumed collidable
     * rather than skipped.
     */
    private static boolean mightCollide(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.blocksMotion()) {
            return true;
        }
        try {
            return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        } catch (RuntimeException e) {
            // A state that needs a real world to describe itself. Read the section properly.
            return true;
        }
    }

    /** Convenience: is the anchor's own chunk available at all? */
    public static boolean isDestinationLoadable(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        return level.getChunk(chunkPos.x, chunkPos.z,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true) != null;
    }
}
