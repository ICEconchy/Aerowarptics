package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The shape of a Rift Gate: a closed ring of frame standing on end, and whatever it encloses.
 *
 * <p>The opening is found the way a nether portal's is - flood filled inward from the controller and
 * bounded by frame - so its size and proportions are the player's rather than the mod's. That matters
 * more here than it would elsewhere, because the aperture only hides what falls inside its own
 * silhouette: <em>the opening is the size limit of what can pass through it</em>. Building a bigger
 * ring is how you pass a bigger machine, and it is why free-form sizing is the whole point of the
 * block rather than a convenience.
 *
 * <p>Kept apart from the block for the same reason {@link uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure}
 * is: this is arithmetic over positions, it takes predicates rather than a level, and it can be tested
 * for what it actually promises without a world to run in.
 */
public final class RiftGateStructure {

    /** Most blocks of opening a gate may enclose. Fifteen square, which will pass anything sane. */
    public static final int MAX_AREA = 225;

    /** Smallest opening worth calling a gate. Below this it is a window. */
    public static final int MIN_AREA = 4;

    /**
     * How much longer a gate may be in one direction than the other.
     *
     * <p>Without this a player can build a one-block letterbox two hundred long, which passes the area
     * cap, renders as an aperture with no discernible shape, and hides nothing usefully.
     */
    public static final int MAX_ASPECT = 4;

    private RiftGateStructure() {
    }

    /**
     * A gate's opening: the shape of the hole, and every position inside it.
     *
     * <p>The geometry lives on {@link RiftGateShape} because that is the half that has to persist and
     * travel - a gate dialled from across the world is in chunks nobody has loaded. The set of
     * interior positions is only wanted at the moment a gate forms, and is far too big to keep.
     */
    public record Opening(RiftGateShape shape, Set<BlockPos> interior) {

        public Direction.Axis span() {
            return shape.span();
        }

        public Direction.Axis normal() {
            return shape.normal();
        }

        public int area() {
            return interior.size();
        }

        public int width() {
            return shape.width();
        }

        public int height() {
            return shape.height();
        }

        public Vec3 centre() {
            return shape.centre();
        }

        public double halfWidth() {
            return shape.halfWidth();
        }

        public double halfHeight() {
            return shape.halfHeight();
        }

        public double acrossFraction(Vec3 point) {
            return shape.acrossFraction(point);
        }

        public double upFraction(Vec3 point) {
            return shape.upFraction(point);
        }

        public Vec3 pointAt(double across, double up, double alongNormal) {
            return shape.pointAt(across, up, alongNormal);
        }

        public double distanceToPlane(Vec3 point) {
            return shape.distanceToPlane(point);
        }

        public boolean covers(BlockPos pos) {
            return interior.contains(pos.immutable());
        }
    }

    /**
     * Finds the opening a controller encloses, in whichever vertical plane it forms one.
     *
     * <p>Both are tried. A ring built across X has open air on either side of it along Z, so a fill in
     * the wrong plane walks straight out into the world and fails on the area cap - which means the
     * axis does not have to be declared anywhere, it falls out of what was built. When both somehow
     * form, the larger wins and ties go to X, so the same structure always forms the same gate.
     *
     * @param controller the block the search starts from, which must itself be frame
     * @param frame      whether a position holds gate frame
     * @param open       whether a position is clear enough to be part of an opening
     */
    @Nullable
    public static Opening find(BlockPos controller, Predicate<BlockPos> frame, Predicate<BlockPos> open) {
        Opening across = find(controller, Direction.Axis.X, frame, open);
        Opening along = find(controller, Direction.Axis.Z, frame, open);
        if (across == null) {
            return along;
        }
        if (along == null) {
            return across;
        }
        return along.area() > across.area() ? along : across;
    }

    /**
     * The same, in one named plane.
     *
     * <p>Seeded from every open position touching the controller in that plane, corners included.
     * The diagonal matters: a controller in the corner of a ring touches the opening only across the
     * diagonal, so seeding from the four square neighbours alone would refuse to form a gate whenever
     * the player put the controller in a corner - which is exactly where anybody would put it.
     *
     * <p>Seeds outside the ring fail on their own by escaping into the world, so there is no need to
     * work out which side of the frame is the inside.
     */
    @Nullable
    public static Opening find(BlockPos controller, Direction.Axis span,
                               Predicate<BlockPos> frame, Predicate<BlockPos> open) {
        for (BlockPos seed : seeds(controller, span)) {
            if (!open.test(seed)) {
                continue;
            }
            Opening opening = fill(seed, span, frame, open);
            if (opening != null) {
                return opening;
            }
        }
        return null;
    }

    /**
     * Flood fills an opening from a seed.
     *
     * <p>Three outcomes, and the distinction between them is the whole validation. Frame stops the
     * fill, which is what a wall is for. Open space continues it. <em>Anything else fails the gate
     * outright</em> - a gate half-buried in a hillside is not a gate with a smaller opening, it is a
     * gate that has to be dug out, and saying so is kinder than quietly forming something the player
     * cannot drive through.
     *
     * <p>A ring with a hole in it fails by growing: the fill escapes into open world and runs past the
     * area cap. That is why the cap is a correctness check and not only a performance one.
     */
    @Nullable
    private static Opening fill(BlockPos seed, Direction.Axis span,
                                Predicate<BlockPos> frame, Predicate<BlockPos> open) {
        Set<BlockPos> interior = new LinkedHashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        pending.add(seed.immutable());

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        while (!pending.isEmpty()) {
            BlockPos pos = pending.poll();
            if (interior.contains(pos)) {
                continue;
            }
            if (frame.test(pos)) {
                continue;
            }
            if (!open.test(pos)) {
                return null;
            }
            if (interior.size() >= MAX_AREA) {
                return null;
            }
            interior.add(pos);

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (BlockPos next : neighbours(pos, span)) {
                if (!interior.contains(next)) {
                    pending.add(next);
                }
            }
        }

        if (interior.size() < MIN_AREA) {
            return null;
        }
        RiftGateShape shape = new RiftGateShape(span, minX, minY, minZ, maxX, maxY, maxZ);
        int longest = Math.max(shape.width(), shape.height());
        int shortest = Math.min(shape.width(), shape.height());
        return longest > shortest * MAX_ASPECT ? null : new Opening(shape, Set.copyOf(interior));
    }

    /**
     * The four positions next to this one inside a vertical plane.
     *
     * <p>Square neighbours only. The fill itself must not travel diagonally, or an opening would leak
     * through a frame corner that touches only at a point - which is a wall as far as anything driving
     * through is concerned.
     */
    private static BlockPos[] neighbours(BlockPos pos, Direction.Axis span) {
        BlockPos sideways = span == Direction.Axis.X ? pos.east() : pos.south();
        BlockPos back = span == Direction.Axis.X ? pos.west() : pos.north();
        return new BlockPos[] {pos.above(), pos.below(), sideways, back};
    }

    /** Everywhere in the plane a fill might be started from, corners included. */
    private static BlockPos[] seeds(BlockPos pos, Direction.Axis span) {
        BlockPos[] seeds = new BlockPos[8];
        int index = 0;
        for (int across = -1; across <= 1; across++) {
            for (int up = -1; up <= 1; up++) {
                if (across == 0 && up == 0) {
                    continue;
                }
                seeds[index++] = span == Direction.Axis.X
                        ? pos.offset(across, up, 0)
                        : pos.offset(0, up, across);
            }
        }
        return seeds;
    }
}
