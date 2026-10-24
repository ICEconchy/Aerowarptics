package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds somewhere an airship can actually materialise.
 *
 * <p>An airship is never dropped onto an anchor block. The search starts from a <em>clearance
 * point</em>: the height at which the hull's underside would sit the destination's
 * {@link ArrivalHeight arrival height} above the anchor, computed from the airship's own footprint
 * rotated into the orientation it will arrive with.
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
     * What an arrival search came back with.
     *
     * @param result  the chosen placement, or {@code null} when none was found
     * @param verdict {@link Clearance#CLEAR} with a result; otherwise {@link Clearance#OBSTRUCTED} when
     *                at least one place was genuinely found blocked, and {@link Clearance#UNPROVEN} when
     *                nothing could be proved either way - the arrival was too big to check, or every
     *                place tried ran out of chunks or budget
     */
    public record Search(@Nullable Result result, Clearance verdict) {
    }

    /**
     * Another hull that has to arrive with this one, somewhere fixed relative to it: an escort in a
     * flotilla, whose slot comes out wherever the flagship does.
     *
     * <p>A formation is proven clear as one thing. At every place the search tries for the flagship, each
     * companion's hull and run-out is tried too, where its slot puts it; the first place where every one of
     * them is clear is the one taken. So the flagship never settles on a spot that suits it and drops an
     * escort into a hillside, and no two ships of the fleet are put down in the same piece of sky - the
     * slots were kept apart when they were set, and a fold does not turn anything.
     *
     * @param ship     the companion, whose own blocks are not an obstruction to itself
     * @param offset   from the flagship's hull centre to the companion's, in world space
     * @param hull     the companion's world box as it stands now, which is its shape at the far end
     * @param approach the companion's own bearing, along which it runs out of its own rift
     * @param runOut   how far it runs out
     */
    public record Companion(Airship ship, Vector3dc offset, BoundingBox3dc hull, Vector3dc approach, double runOut) {
    }

    /**
     * Searches for a clear volume the airship fits in, near the anchor.
     *
     * <p>The airship does not simply appear at the result: it comes out of the exit rift already
     * moving and coasts the last stretch. That run-out is part of what has to be clear, so the volume
     * tested at each candidate is the hull swept backwards along {@code approach} - the whole path,
     * not just the destination.
     *
     * <p>The volume proven clear is the hull's own sweep and the configured clearance, and nothing
     * wider - see {@link #arrivalSegments} for why the aperture no longer pads it.
     *
     * <p>Every candidate reads a volume of the same shape, so if the first asks for more ground than
     * {@code maxArrivalChunks} allows, all of them will: the search stops there and says so, rather
     * than trying four thousand places that were each going to be refused for the same reason.
     *
     * @param airship        the airship being moved
     * @param destination    level the anchor lives in
     * @param anchorPos      the anchor block
     * @param orientation    orientation the airship will arrive with
     * @param approach       unit vector the airship will be travelling along as it emerges
     * @param runOut         how far back along {@code approach} the airship first appears
     * @param arrivalHeight  blocks between the top of the anchor and the hull's underside at the
     *                       first candidate, already {@link ArrivalHeight#resolve resolved}
     */
    public static Search find(Airship airship, ServerLevel destination, BlockPos anchorPos,
                              Quaterniondc orientation, Vector3dc approach, double runOut,
                              int arrivalHeight) {
        return find(airship, destination, anchorPos, orientation, approach, runOut, arrivalHeight, List.of());
    }

    /**
     * The same search, for a flagship bringing a formation with it. See {@link Companion}.
     */
    public static Search find(Airship airship, ServerLevel destination, BlockPos anchorPos,
                              Quaterniondc orientation, Vector3dc approach, double runOut,
                              int arrivalHeight, List<Companion> companions) {
        Placement placement = Placement.of(airship, anchorPos, orientation, arrivalHeight);
        if (placement == null) {
            Trace.log(Trace.ARRIVAL, "search for {} at {}: UNPROVEN - the hull has no measurable shape to place",
                    airship.uuid(), Trace.pos(anchorPos));
            return new Search(null, Clearance.UNPROVEN);
        }
        BoundingBox3d localBounds = placement.localBounds();
        Pose3d probePose = placement.pose();
        Vector3d hullCentreOffset = placement.hullCentreOffset();
        Vector3d clearancePoint = placement.clearancePoint();

        double clearance = AWConfig.ARRIVAL_CLEARANCE.get();

        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(
                AWConfig.SAFE_ARRIVAL_RADIUS.get(),
                AWConfig.SAFE_ARRIVAL_VERTICAL_RADIUS.get(),
                AWConfig.SAFE_ARRIVAL_STEP.get(),
                CANDIDATE_LIMIT);

        BoundingBox3d worldBounds = new BoundingBox3d();
        long budget = budgetFor(airship);
        int chunkCap = maxArrivalChunks();
        // Chunks already proven loaded by an earlier candidate. Neighbouring candidates are a few
        // blocks apart and read nearly the same ground, so without this every one of them asked the
        // chunk source for the same few hundred columns all over again.
        LongOpenHashSet loaded = new LongOpenHashSet();
        boolean anyObstructed = false;
        boolean first = true;
        // A place in somebody's slip is not a place to land: a ship that was given no berth - a fix, a
        // summons, a rift turned or scattered - is kept out of every one, taken or not.
        List<uk.co.iceconchy.aerowarptics.berth.BerthBox> slips =
                uk.co.iceconchy.aerowarptics.berth.Berths.slips(destination);
        int inSlips = 0;
        // Counted for the trace: a refusal after four thousand tries into solid rock and one after a single
        // try that ran out of chunks are the same WarpFailure and entirely different problems.
        int tried = 0;
        int obstructed = 0;
        for (ArrivalSearch.Candidate candidate : candidates) {
            tried++;
            // Place the hull's centre on the candidate: the pose origin is not the centre, so the
            // measured offset is subtracted rather than assumed to be zero.
            probePose.position().set(
                    clearancePoint.x + candidate.dx() - hullCentreOffset.x,
                    clearancePoint.y + candidate.dy() - hullCentreOffset.y,
                    clearancePoint.z + candidate.dz() - hullCentreOffset.z);
            localBounds.transform(probePose, worldBounds);
            if (uk.co.iceconchy.aerowarptics.berth.Berths.inASlip(slips, worldBounds)) {
                inSlips++;
                anyObstructed = true;
                obstructed++;
                continue;
            }

            // The run-out marched along the approach rather than boxed around it, so a candidate is
            // not rejected for terrain sitting off to the side of a diagonal arrival.
            List<BoundingBox3d> volume = arrivalSegments(worldBounds, approach, runOut, clearance);
            List<List<BoundingBox3d>> escorts = companionVolumes(worldBounds, companions, clearance);
            if (first) {
                first = false;
                int columns = chunkColumns(volume);
                for (List<BoundingBox3d> escort : escorts) {
                    columns += chunkColumns(escort);
                }
                if (columns > chunkCap) {
                    Trace.log(Trace.ARRIVAL, "search for {} at {} height {}: UNPROVEN - the landing and its run-out"
                                    + " cover {} chunk columns, over arrival.maxArrivalChunks {} ({} companion(s))",
                            airship.uuid(), Trace.pos(anchorPos), arrivalHeight, columns, chunkCap, companions.size());
                    return new Search(null, Clearance.UNPROVEN);
                }
            }
            Clearance verdict = clearance(airship, destination, volume, budget, loaded);
            for (int i = 0; i < escorts.size() && verdict == Clearance.CLEAR; i++) {
                Airship ship = companions.get(i).ship();
                verdict = clearance(ship, destination, escorts.get(i), budgetFor(ship), loaded);
            }
            if (verdict != Clearance.CLEAR) {
                anyObstructed |= verdict == Clearance.OBSTRUCTED;
                obstructed += verdict == Clearance.OBSTRUCTED ? 1 : 0;
                continue;
            }
            Trace.log(Trace.ARRIVAL, "search for {} at {} height {}: CLEAR at offset ({}, {}, {}) after {} place(s)"
                            + " - {} blocked, {} unproven; hull centre {}, {} chunk(s) read, budget {} reads each",
                    airship.uuid(), Trace.pos(anchorPos), arrivalHeight, candidate.dx(), candidate.dy(), candidate.dz(),
                    tried, obstructed, tried - 1 - obstructed, Trace.vec(worldBounds.center(new Vector3d())),
                    loaded.size(), budget);
            return new Search(new Result(
                    new Vector3d(probePose.position()),
                    orientation,
                    new BoundingBox3d(worldBounds),
                    worldBounds.minY() - (anchorPos.getY() + 1.0D)), Clearance.CLEAR);
        }
        Trace.log(Trace.ARRIVAL, "search for {} at {} height {}: {} after all {} place(s) - {} blocked ({} of them"
                        + " in a berth's slip), {} unproven (out of chunks or of its {} block reads); radius {}"
                        + " vertical {} step {}",
                airship.uuid(), Trace.pos(anchorPos), arrivalHeight, anyObstructed ? "OBSTRUCTED" : "UNPROVEN",
                tried, obstructed, inSlips, tried - obstructed, budget, AWConfig.SAFE_ARRIVAL_RADIUS.get(),
                AWConfig.SAFE_ARRIVAL_VERTICAL_RADIUS.get(), AWConfig.SAFE_ARRIVAL_STEP.get());
        return new Search(null, anyObstructed ? Clearance.OBSTRUCTED : Clearance.UNPROVEN);
    }

    /**
     * Each companion's arrival volume for a flagship whose hull, at this candidate, fills {@code flagship}.
     */
    public static List<List<BoundingBox3d>> companionVolumes(BoundingBox3dc flagship, List<Companion> companions,
                                                             double clearance) {
        if (companions.isEmpty()) {
            return List.of();
        }
        Vector3d centre = flagship.center(new Vector3d());
        List<List<BoundingBox3d>> out = new java.util.ArrayList<>(companions.size());
        for (Companion companion : companions) {
            out.add(arrivalSegments(companionBox(centre, companion), companion.approach(), companion.runOut(),
                    clearance));
        }
        return out;
    }

    /** A companion's hull box, where its slot puts it with the flagship's centre at {@code flagshipCentre}. */
    public static BoundingBox3d companionBox(Vector3dc flagshipCentre, Companion companion) {
        BoundingBox3dc hull = companion.hull();
        Vector3d now = hull.center(new Vector3d());
        double dx = flagshipCentre.x() + companion.offset().x() - now.x;
        double dy = flagshipCentre.y() + companion.offset().y() - now.y;
        double dz = flagshipCentre.z() + companion.offset().z() - now.z;
        return new BoundingBox3d(hull.minX() + dx, hull.minY() + dy, hull.minZ() + dz,
                hull.maxX() + dx, hull.maxY() + dy, hull.maxZ() + dz);
    }

    /**
     * Where the hull comes in when nobody looks first: the search's own first candidate, taken without
     * reading a single block.
     *
     * <p>For a drive whose pilot has switched its clearance checks off. Deliberately the same placement
     * {@link #find} would have tried first - straight above the target, underside the arrival height
     * over it, turned the way the ship will arrive - so switching the checks off changes only whether
     * the ground is looked at, and never where a ship is aimed. Nothing is loaded here either; the drive's
     * arrival ticket holds the landing as it does for any warp.
     *
     * @return the placement, or {@code null} when the hull has no plot to measure
     */
    /**
     * The hull placed with its centre exactly at {@code centre}, turned as it is now, and nothing read.
     *
     * <p>For an escort in a fold: the flagship's search has already proved its slot clear, as part of the
     * formation, and searching again on the escort's own account could only move it out of formation - into
     * whatever the flagship's proof did not cover.
     *
     * @return the placement, or {@code null} when the hull has no plot to measure
     */
    @Nullable
    public static Result exactly(Airship airship, Vector3dc centre) {
        Vector3d origin = new Vector3d(airship.position());
        Vector3d now = airship.centre(new Vector3d());
        origin.add(centre.x() - now.x, centre.y() - now.y, centre.z() - now.z);
        BoundingBox3dc hull = airship.worldBounds();
        double dx = centre.x() - now.x;
        double dy = centre.y() - now.y;
        double dz = centre.z() - now.z;
        BoundingBox3d bounds = new BoundingBox3d(hull.minX() + dx, hull.minY() + dy, hull.minZ() + dz,
                hull.maxX() + dx, hull.maxY() + dy, hull.maxZ() + dz);
        return new Result(origin, new org.joml.Quaterniond(airship.orientation()), bounds, 0.0D);
    }

    /**
     * The whole vessel placed with its centre exactly at {@code centre}, <em>turned</em> to
     * {@code orientation}, and nothing read.
     *
     * <p>For a ship given a Berth: the berth says where it lies and which way, and proves its own slip. Measured
     * from {@link Airship#vesselShipBounds()} turned to the attitude it will have, as the search measures a
     * hull, so the centre put on the mark is the centre of the ship as it will sit and not as it sits now.
     *
     * @return the placement, or {@code null} when the hull has no plot to measure
     */
    @Nullable
    public static Result exactly(Airship airship, Vector3dc centre, Quaterniondc orientation) {
        BoundingBox3d localBounds = airship.vesselShipBounds();
        if (localBounds == null) {
            return null;
        }
        Pose3d pose = new Pose3d(airship.pose());
        pose.orientation().set(orientation);
        pose.position().set(0.0D, 0.0D, 0.0D);
        BoundingBox3d measured = new BoundingBox3d();
        localBounds.transform(pose, measured);
        Vector3d origin = new Vector3d(centre).sub(measured.center(new Vector3d()));
        pose.position().set(origin);
        BoundingBox3d worldBounds = new BoundingBox3d();
        localBounds.transform(pose, worldBounds);
        return new Result(origin, new org.joml.Quaterniond(orientation), worldBounds, 0.0D);
    }

    @Nullable
    public static Result straightIn(Airship airship, BlockPos anchorPos, Quaterniondc orientation,
                                    int arrivalHeight) {
        Placement placement = Placement.of(airship, anchorPos, orientation, arrivalHeight);
        if (placement == null) {
            return null;
        }
        Pose3d pose = placement.pose();
        pose.position().set(placement.clearancePoint()).sub(placement.hullCentreOffset());
        BoundingBox3d worldBounds = new BoundingBox3d();
        placement.localBounds().transform(pose, worldBounds);
        return new Result(new Vector3d(pose.position()), orientation, worldBounds,
                worldBounds.minY() - (anchorPos.getY() + 1.0D));
    }

    /**
     * The measurements every arrival candidate is placed from.
     *
     * @param localBounds      the hull's plot box, in plot coordinates
     * @param pose             a working pose turned to the arrival orientation; its position is the
     *                         caller's to set
     * @param hullCentreOffset from the pose origin to the hull's centre, once turned
     * @param clearancePoint   where the hull's centre sits for its underside to clear the target by the
     *                         arrival height - the first candidate
     */
    private record Placement(BoundingBox3d localBounds, Pose3d pose, Vector3d hullCentreOffset,
                             Vector3d clearancePoint) {

        @Nullable
        static Placement of(Airship airship, BlockPos anchorPos, Quaterniondc orientation, int arrivalHeight) {
            // The hull's own footprint in plot coordinates, and deliberately only that. Measuring the
            // whole assembly instead folded in every connected sub-level and every bearing-mounted
            // propeller's swept box, and Create keeps that box wide enough to enclose a full rotation -
            // so a ship with propellers asked for a landing site several blocks larger on every side
            // than the ship, and a hull that fitted perfectly well was told there was nowhere to arrive.
            // Null only when the hull has no plot to measure.
            //
            // The connected sub-levels are back, and the propellers are not. A part of the vessel arrives
            // with the hull, so room has to be proved for it; leaving the parts out put a ship built in
            // sections down with only its drive's section looked at. Airship.vesselShipBounds() is the hull's
            // plot grown round its parts, in the hull's own frame, which for a ship of one sub-level is the
            // plot box this always was.
            BoundingBox3d localBounds = airship.vesselShipBounds();
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

            // Where the hull's centre has to be for its underside to clear the anchor by the arrival
            // height. The destination's own figure rather than the server's: see ArrivalHeight.
            Vector3d clearancePoint = new Vector3d(
                    anchorPos.getX() + 0.5D,
                    ArrivalHeight.lowestUnderside(anchorPos.getY(), arrivalHeight) + hullSize.y * 0.5D,
                    anchorPos.getZ() + 0.5D);
            return new Placement(localBounds, probePose, hullCentreOffset, clearancePoint);
        }
    }

    /**
     * The segments one arrival candidate actually tests: the bare hull swept back along its run-out,
     * plus the configured clearance, and nothing wider.
     *
     * <p>It used to be widened by the aperture's margin as well, on every side - underneath included -
     * on the reasoning that a block clearing the hull could still foul the opening it flies out of. The
     * departure side gave that reasoning up first: the rift is a picture, not a solid, and nothing about
     * an aperture collides with terrain. At the arrival end it did worse than refuse grass. The margin
     * is scaled from the hull, so a big enough ship had its tested volume pushed tens of blocks into
     * the ground beneath its own keel, further than the search is allowed to climb - and a
     * four-hundred-block hull was refused a landing over flat ground on every attempt. So the arrival
     * tests the ship, the same as the launch does.
     *
     * <p>One method, so the search and anything that draws or reports its volume cannot drift apart.
     */
    public static List<BoundingBox3d> arrivalSegments(BoundingBox3dc hull, Vector3dc approach,
                                                      double runOut, double clearance) {
        return sweepSegments(hull, approach, runOut, clearance, 0.0D);
    }

    /**
     * How many distinct chunk columns a set of volumes covers.
     *
     * <p>What the chunk cap is measured against. Counted over the union rather than per volume, because
     * a sweep's segments overlap and share most of their ground, and summing them would refuse an
     * arrival for ground it counted twice.
     */
    public static int chunkColumns(List<? extends BoundingBox3dc> volumes) {
        LongOpenHashSet columns = new LongOpenHashSet();
        for (BoundingBox3dc bounds : volumes) {
            int minChunkX = (int) Math.floor(bounds.minX()) >> 4;
            int maxChunkX = (int) Math.floor(bounds.maxX()) >> 4;
            int minChunkZ = (int) Math.floor(bounds.minZ()) >> 4;
            int maxChunkZ = (int) Math.floor(bounds.maxZ()) >> 4;
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    columns.add(ArrivalTicket.pack(x, z));
                }
            }
        }
        return columns.size();
    }

    /** {@code arrival.maxArrivalChunks}, or its default where the config is not loaded. */
    private static int maxArrivalChunks() {
        return ArrivalTicket.maxChunks();
    }

    /**
     * How far past the hull's own footprint an aperture reaches.
     *
     * <p>The rift the hull flies through is scaled to a multiple of the hull's radius, so its opening
     * stands off the hull on every side by this much. Neither end tests it any more - the rift is a
     * picture, not a solid - but the clearance overlay draws it so a pilot can see where the aperture
     * will reach, and the chunk claims hold it, which is cheap and refuses nothing. Never negative: an
     * aperture is never narrower than its hull.
     *
     * @param apertureRadius the rift aperture's radius, {@code hullRadius * riftRadiusFactor}
     * @param hullRadius     the hull's own radius the aperture was scaled from
     */
    public static double apertureMargin(double apertureRadius, double hullRadius) {
        return Math.max(0.0D, apertureRadius - hullRadius);
    }

    /**
     * The single box enclosing a hull swept back along its run-out, widened by the configured clearance
     * and, where a caller asks, by an aperture margin.
     *
     * <p>The arrival test itself passes no margin - see {@link #arrivalSegments} - and reads the
     * segmented sweep rather than this box. Kept as a pure function of doubles so the geometry can be checked without a world - the block
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
        return clearance(airship, level, volumes, budget, new LongOpenHashSet());
    }

    /**
     * The same, remembering which chunks have already been proven loaded.
     *
     * @param loaded chunks known to be loaded, by {@link ArrivalTicket#pack}; read and added to, so a
     *               caller asking about many nearby volumes can share one set across all of them
     */
    public static Clearance clearance(Airship airship, ServerLevel level,
                                      List<? extends BoundingBox3dc> volumes, long budget,
                                      LongOpenHashSet loaded) {
        long[] spent = {0L};
        boolean unproven = false;
        // Resolved once for the whole path rather than per segment: the chain does not change
        // between two segments of one sweep, and a corridor is routinely dozens of segments.
        Set<SubLevel> own = airship.assemblySubLevels();
        for (BoundingBox3dc bounds : volumes) {
            Clearance verdict = clearanceOf(level, bounds, own, budget, spent, loaded);
            if (verdict == Clearance.OBSTRUCTED) {
                return Clearance.OBSTRUCTED;
            }
            unproven |= verdict == Clearance.UNPROVEN;
        }
        return unproven ? Clearance.UNPROVEN : Clearance.CLEAR;
    }

    private static Clearance clearanceOf(ServerLevel level, BoundingBox3dc bounds,
                                         Set<SubLevel> own, long budget, long[] spent,
                                         LongOpenHashSet loaded) {
        if (bounds.minY() > level.getMaxBuildHeight()) {
            return intersectsAnotherAirship(own, level, bounds)
                    ? Clearance.OBSTRUCTED : Clearance.CLEAR;
        }
        if (!ensureLoaded(level, bounds, loaded)) {
            return Clearance.UNPROVEN;
        }
        if (intersectsAnotherAirship(own, level, bounds)) {
            return Clearance.OBSTRUCTED;
        }
        return scanVolume(level, bounds, budget, spent, null);
    }

    /**
     * A shape that is not an upright box: a Berth's slip and its lane, which lie whichever way their owner
     * turned them.
     *
     * <p>Every other volume here is an upright box, because every other arrival is in open air, where the
     * box round a hull lying at forty-five degrees costs nothing but a few blocks of sky read for no reason.
     * A slip is built to fit, and the box round a ship in one takes in the quay on both sides of it.
     */
    public interface Within {

        /** Whether the block at these coordinates has any part inside the shape. */
        boolean cell(int x, int y, int z);

        /** Whether an upright box - another vessel's - shares any air with the shape. */
        boolean box(BoundingBox3dc other);
    }

    /**
     * What a turned volume is: clear, obstructed or not provable - {@link #clearance}, for a shape that is not
     * an upright box.
     *
     * @param enclosing the upright box round the shape, which says what ground has to be loaded
     * @param within    the shape itself; nothing outside it is read, and no vessel outside it counts
     */
    public static Clearance clearanceWithin(Airship airship, ServerLevel level, BoundingBox3dc enclosing,
                                            Within within) {
        if (enclosing.minY() < level.getMinBuildHeight()) {
            return Clearance.OBSTRUCTED;
        }
        boolean above = enclosing.minY() > level.getMaxBuildHeight();
        if (!above && !ensureLoaded(level, enclosing, new LongOpenHashSet())) {
            return Clearance.UNPROVEN;
        }
        Set<SubLevel> own = airship.assemblySubLevels();
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, enclosing)) {
            if (!own.contains(other) && within.box(other.boundingBox())) {
                return Clearance.OBSTRUCTED;
            }
        }
        return above ? Clearance.CLEAR : scanVolume(level, enclosing, budgetFor(airship), new long[] {0L}, within);
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
     * <p>Synchronous, and so a last resort. A warp claims its landing while the drive spins up (see
     * {@link ArrivalTicket#preload}), which means that by the time a search runs these are normally
     * already loaded and this is a hash lookup per column.
     *
     * @param loaded chunks already proven loaded; skipped here, and added to as more are
     * @return {@code false} when the volume spans more than {@code maxArrivalChunks} columns, or a
     *         chunk would not load
     */
    private static boolean ensureLoaded(ServerLevel level, BoundingBox3dc bounds, LongOpenHashSet loaded) {
        int minChunkX = (int) Math.floor(bounds.minX()) >> 4;
        int maxChunkX = (int) Math.floor(bounds.maxX()) >> 4;
        int minChunkZ = (int) Math.floor(bounds.minZ()) >> 4;
        int maxChunkZ = (int) Math.floor(bounds.maxZ()) >> 4;

        long chunkCount = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (chunkCount > maxArrivalChunks()) {
            return false;
        }
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                long key = ArrivalTicket.pack(x, z);
                if (loaded.contains(key)) {
                    continue;
                }
                if (level.getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true) == null) {
                    return false;
                }
                loaded.add(key);
            }
        }
        return true;
    }

    /**
     * Rejects a candidate that would overlap a sub-level belonging to <em>some other</em> vessel,
     * including a docked neighbour.
     *
     * <p>"Other" is the whole word. This used to excuse only the hull's own plot, and a vessel built
     * as several constrained sub-levels - a tender, a gondola, a turret on its own plot - therefore
     * read its own attached plots as another ship parked in its path. Since those plots are joined to
     * the hull they are always in the volume swept from it, so the verdict was OBSTRUCTED on every
     * bearing, at every heading, with nothing whatever in the way: the report that sub-levels stop
     * warps. The whole connected chain is now self, from
     * {@link Airship#assemblySubLevels()}.
     */
    private static boolean intersectsAnotherAirship(Set<SubLevel> own, ServerLevel level,
                                                    BoundingBox3dc bounds) {
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, bounds)) {
            if (!own.contains(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The other vessels standing in a path, for a trace or a dry run to name.
     *
     * <p>A path fouled by another hull has no block to point at, and "no single block to name" was all a
     * refusal could say about it - which is no help in telling a ship moored across the bow from the
     * ship's own turret being read as one. The same test {@link #clearance} makes, kept for the report.
     */
    public static List<SubLevel> otherHulls(Airship airship, ServerLevel level,
                                            List<? extends BoundingBox3dc> volumes) {
        Set<SubLevel> own = airship.assemblySubLevels();
        List<SubLevel> found = new ArrayList<>();
        for (BoundingBox3dc bounds : volumes) {
            for (SubLevel other : Sable.HELPER.getAllIntersecting(level, bounds)) {
                if (!own.contains(other) && !found.contains(other)) {
                    found.add(other);
                }
            }
        }
        return found;
    }

    /**
     * What stands where a hull would arrive, for a dry run to name.
     *
     * @param block the first solid block in the hull's run-out, or {@code null} when there is none to name
     * @param hulls the other vessels standing in it
     */
    public record Blocked(@Nullable BlockPos block, List<SubLevel> hulls) {

        public boolean nothing() {
            return block == null && hulls.isEmpty();
        }
    }

    /**
     * What blocks one place an arrival search would try: the hull at {@code hull}, run out as
     * {@link #arrivalSegments} runs it.
     *
     * <p>A refused arrival used to say only that nowhere was clear, and a hillside and a ship parked over
     * the target are different things to do something about. Reads only chunks already loaded, which after
     * a search of the same ground is all of them.
     */
    public static Blocked blockedBy(Airship airship, ServerLevel level, BoundingBox3dc hull, Vector3dc approach,
                                    double runOut) {
        List<BoundingBox3d> volume = arrivalSegments(hull, approach, runOut, AWConfig.ARRIVAL_CLEARANCE.get());
        BlockPos block = null;
        for (BoundingBox3d segment : volume) {
            block = firstObstruction(level, segment);
            if (block != null) {
                break;
            }
        }
        return new Blocked(block, otherHulls(airship, level, volume));
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
                                        long[] reads, @Nullable Within within) {
        ObstructionScan.BlockSpan span = blocksInside(level, bounds);
        int minX = span.minX();
        int minY = span.minY();
        int minZ = span.minZ();
        int maxX = span.maxX();
        int maxY = span.maxY();
        int maxZ = span.maxZ();
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return Clearance.CLEAR;
        }

        // Charged for blocks actually read, not for the span of every section walked. The old
        // accounting deducted a section's whole volume even when the section was walked and found
        // clear, so proving an empty corridor cost exactly as much budget as proving a solid one.
        // The counter is the caller's, so a path cut into segments shares one allowance.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ObstructionScan.Solid solid = (x, y, z) -> {
            // Outside a turned shape is not in the way, and is not a read: the budget is for the shape.
            if (within != null && !within.cell(x, y, z)) {
                return false;
            }
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
        // The whole vessel's volume: it is the whole vessel's sweep that is read.
        return budgetFor(airship.vesselVolume(), scale, ceiling);
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
        ObstructionScan.BlockSpan span = blocksInside(level, bounds);
        int minX = span.minX();
        int minY = span.minY();
        int minZ = span.minZ();
        int maxX = span.maxX();
        int maxY = span.maxY();
        int maxZ = span.maxZ();
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
        ObstructionScan.BlockSpan span = blocksInside(level, bounds);
        int minX = span.minX();
        int minY = span.minY();
        int minZ = span.minZ();
        int maxX = span.maxX();
        int maxY = span.maxY();
        int maxZ = span.maxZ();
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
     * The blocks inside a volume, clipped to the world's build range.
     *
     * <p>One conversion for the gate, the collision report and the overlay's red blocks, so none of
     * them can take in a block the others leave out - see {@link ObstructionScan.BlockSpan} for why a
     * block touching the volume's face is not in it.
     */
    private static ObstructionScan.BlockSpan blocksInside(ServerLevel level, BoundingBox3dc bounds) {
        ObstructionScan.BlockSpan span = ObstructionScan.BlockSpan.inside(bounds.minX(), bounds.minY(),
                bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        return new ObstructionScan.BlockSpan(span.minX(),
                Math.max(level.getMinBuildHeight(), span.minY()), span.minZ(), span.maxX(),
                Math.min(level.getMaxBuildHeight() - 1, span.maxY()), span.maxZ());
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
