package uk.co.iceconchy.aerowarptics.drive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The shape of an extended Rift Drive: a cube of Drive Extenders with the drive itself set into it.
 *
 * <p>One block, two by two by two, and so on up to seven. A lone drive is a working drive, exactly as
 * it always was, so extenders are something a drive grows into rather than something it needs. What a
 * larger frame buys is surface: every extender's face is a face a shaft can drive it from, a module can
 * be bolted to and the console can be opened from, and the machine is drawn the size of its frame.
 *
 * <h2>Where the drive goes</h2>
 * Anywhere in the cube, buried in the middle included. A formed frame carries rotation from any of its
 * faces through to the drive, so there is no face the drive has to be on for a shaft to reach it.
 *
 * <p>That was not always so. The first frames only formed with the drive in the middle of a face its
 * shaft ran through, because the drive then took power along its own axis and nowhere else. In practice
 * that rule meant a player's three-cube came out as a two-by-two in one corner of it: any drive placed
 * anyhow fits <em>some</em> two-cube, and hardly any fits the one three-cube the rule allowed, so the
 * failure looked like the extenders not working past two. Once power could come in anywhere the rule
 * had nothing left to protect.
 *
 * <h2>Which frame wins</h2>
 * The largest complete one. Among complete frames of the same size - a drive sitting in a slab of
 * extenders bigger than any one frame - the one most nearly centred on the drive, so a drive placed in
 * the middle of a big block of casing grows evenly about itself rather than into one corner. Remaining
 * ties break on the lowest corner, so the same build forms the same frame whatever order it went up in.
 *
 * <p>A frame is identified by its <em>origin</em>, the corner with the lowest coordinates, the way an
 * Astrolabe table is: a two-by-two has no middle cell to be identified by, and a corner every size has.
 *
 * <p>Kept apart from the blocks so the rules can be tested for what they are - arithmetic over block
 * positions - without a registry. Everything here takes predicates rather than a level, which is also
 * what stops it caring whether it is running on the ground or inside an airship's plot.
 */
public final class DriveStructure {

    /** Largest frame, in blocks along an edge. */
    public static final int MAX_SIZE = 7;

    /** Smallest frame: the drive on its own, which is still a drive. */
    public static final int MIN_SIZE = 1;

    /** How far from the drive the furthest cell of any frame can be, along each axis. */
    private static final int REACH = MAX_SIZE - 1;

    /**
     * Every extended frame round a drive at the origin, best first: each entry is
     * {@code {size, dx, dy, dz}}, the frame's corner as a step from the drive.
     *
     * <p>The order depends on nothing but the offsets, so it is worked out once rather than for every
     * placement.
     */
    private static final List<int[]> ORDER = order();

    private DriveStructure() {
    }

    /**
     * A frame: where its lowest corner is, and how many blocks along an edge.
     *
     * @param origin the frame's lowest corner
     * @param size   1 to {@link #MAX_SIZE}
     */
    public record Frame(BlockPos origin, int size) {

        public Frame {
            if (size < MIN_SIZE || size > MAX_SIZE) {
                throw new IllegalArgumentException("a drive frame is 1 to " + MAX_SIZE + " blocks along an edge");
            }
            origin = origin.immutable();
        }

        /** The frame of a drive with no extenders: just the drive. */
        public static Frame single(BlockPos drive) {
            return new Frame(drive, MIN_SIZE);
        }

        public boolean isExtended() {
            return size > MIN_SIZE;
        }

        /**
         * Every position in the frame, the drive's included.
         *
         * <p>Ordered by x, then y, then z, so anything iterating them - forming, breaking, checking -
         * visits them in the same order every time.
         */
        public List<BlockPos> cells() {
            List<BlockPos> cells = new ArrayList<>(size * size * size);
            for (int dx = 0; dx < size; dx++) {
                for (int dy = 0; dy < size; dy++) {
                    for (int dz = 0; dz < size; dz++) {
                        cells.add(origin.offset(dx, dy, dz));
                    }
                }
            }
            return cells;
        }

        public boolean covers(BlockPos pos) {
            int dx = pos.getX() - origin.getX();
            int dy = pos.getY() - origin.getY();
            int dz = pos.getZ() - origin.getZ();
            return dx >= 0 && dx < size && dy >= 0 && dy < size && dz >= 0 && dz < size;
        }

        /** The block space the frame fills, from its lowest corner to its highest. */
        public AABB bounds() {
            return new AABB(origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + size, origin.getY() + size, origin.getZ() + size);
        }
    }

    /** Every frame larger than a lone drive that the drive at this position could be part of, best first. */
    public static List<Frame> candidates(BlockPos drive) {
        List<Frame> candidates = new ArrayList<>(ORDER.size());
        for (int[] entry : ORDER) {
            candidates.add(new Frame(drive.offset(entry[1], entry[2], entry[3]), entry[0]));
        }
        return candidates;
    }

    /**
     * How far a frame's middle is from the drive's, squared, in half blocks - which keeps it a whole
     * number for even and odd frames alike.
     */
    public static int offCentre(Frame frame, BlockPos drive) {
        int dx = 2 * (frame.origin().getX() - drive.getX()) + frame.size() - 1;
        int dy = 2 * (frame.origin().getY() - drive.getY()) + frame.size() - 1;
        int dz = 2 * (frame.origin().getZ() - drive.getZ()) + frame.size() - 1;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Whether the drive at this position may be the drive of this frame. */
    public static boolean fits(Frame frame, BlockPos drive) {
        return frame.isExtended() ? frame.covers(drive) : frame.origin().equals(drive);
    }

    /**
     * The best frame a drive can form, or {@link Frame#single} when it can form none.
     *
     * <p>Reads each position round the drive once, into a running total over the box every frame could
     * reach, and then answers "is this whole cube usable" for each of the seven hundred and eighty-three
     * candidates by adding up eight corners of that total. Walking every candidate's cells instead is a
     * hundred thousand reads in the worst case - a nearly finished seven-cube, which is exactly what is
     * being checked every time a player lays one of its last blocks.
     *
     * @param usable whether a cell other than the drive's own may be built into the frame - which is
     *               to say, whether it holds an extender that belongs to no other drive
     */
    public static Frame findBest(BlockPos drive, Predicate<BlockPos> usable) {
        // Every frame larger than one block has an extender against the drive, so a drive with none
        // beside it - the common case, every time a lone drive looks - is settled with six reads.
        boolean touching = false;
        for (Direction direction : Direction.values()) {
            if (usable.test(drive.relative(direction))) {
                touching = true;
                break;
            }
        }
        if (!touching) {
            return Frame.single(drive);
        }

        int span = 2 * REACH + 1;
        int[] total = new int[(span + 1) * (span + 1) * (span + 1)];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < span; x++) {
            for (int y = 0; y < span; y++) {
                for (int z = 0; z < span; z++) {
                    cursor.set(drive.getX() + x - REACH, drive.getY() + y - REACH, drive.getZ() + z - REACH);
                    int here = x == REACH && y == REACH && z == REACH || usable.test(cursor) ? 1 : 0;
                    total[index(x + 1, y + 1, z + 1, span)] = here
                            + total[index(x, y + 1, z + 1, span)]
                            + total[index(x + 1, y, z + 1, span)]
                            + total[index(x + 1, y + 1, z, span)]
                            - total[index(x, y, z + 1, span)]
                            - total[index(x, y + 1, z, span)]
                            - total[index(x + 1, y, z, span)]
                            + total[index(x, y, z, span)];
                }
            }
        }

        for (int[] entry : ORDER) {
            int size = entry[0];
            // Box corners in the running total's own coordinates: the drive sits at REACH on each axis.
            int x0 = entry[1] + REACH;
            int y0 = entry[2] + REACH;
            int z0 = entry[3] + REACH;
            int x1 = x0 + size;
            int y1 = y0 + size;
            int z1 = z0 + size;
            int usableCells = total[index(x1, y1, z1, span)]
                    - total[index(x0, y1, z1, span)] - total[index(x1, y0, z1, span)] - total[index(x1, y1, z0, span)]
                    + total[index(x0, y0, z1, span)] + total[index(x0, y1, z0, span)] + total[index(x1, y0, z0, span)]
                    - total[index(x0, y0, z0, span)];
            if (usableCells == size * size * size) {
                return new Frame(drive.offset(entry[1], entry[2], entry[3]), size);
            }
        }
        return Frame.single(drive);
    }

    /** Whether every cell of a frame but the drive's is acceptable to the test. */
    public static boolean isComplete(Frame frame, BlockPos drive, Predicate<BlockPos> usable) {
        for (BlockPos cell : frame.cells()) {
            if (!cell.equals(drive) && !usable.test(cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every position outside a frame that shares a face with it: where a module bolted to the machine
     * can stand.
     *
     * <p>For a lone drive this is its six neighbours, which is exactly where a module has always had to
     * be. An outside position touches at most one cell by a face, so there are no repeats.
     */
    public static List<BlockPos> surroundings(Frame frame) {
        int size = frame.size();
        BlockPos o = frame.origin();
        List<BlockPos> around = new ArrayList<>(6 * size * size);
        for (int a = 0; a < size; a++) {
            for (int b = 0; b < size; b++) {
                around.add(o.offset(-1, a, b));
                around.add(o.offset(size, a, b));
                around.add(o.offset(a, -1, b));
                around.add(o.offset(a, size, b));
                around.add(o.offset(a, b, -1));
                around.add(o.offset(a, b, size));
            }
        }
        return around;
    }

    /**
     * Every drive that might now complete a frame including any of these extenders, lowest first.
     *
     * <p>Found by walking the extenders connected to them, face to face, and noting every drive beside
     * that walk. A frame is a solid cube, so the drive of any frame an extender could be part of touches
     * an extender connected to it - which is what lets this look only at the extenders actually there
     * rather than at every position a drive could stand in, a thirteen-block cube around each one.
     *
     * <p>The walk never strays more than a frame's reach from the positions it started at, so a player's
     * wall of spare extenders is not searched end to end every time one more is laid against it.
     *
     * @param starts   positions to walk from; ones that are not extenders are ignored
     * @param extender whether a position holds a Drive Extender
     * @param drive    whether a position holds a Rift Drive
     */
    public static List<BlockPos> nearbyDrives(Collection<BlockPos> starts, Predicate<BlockPos> extender,
                                              Predicate<BlockPos> drive) {
        if (starts.isEmpty()) {
            return List.of();
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos start : starts) {
            minX = Math.min(minX, start.getX());
            minY = Math.min(minY, start.getY());
            minZ = Math.min(minZ, start.getZ());
            maxX = Math.max(maxX, start.getX());
            maxY = Math.max(maxY, start.getY());
            maxZ = Math.max(maxZ, start.getZ());
        }
        minX -= REACH;
        minY -= REACH;
        minZ -= REACH;
        maxX += REACH;
        maxY += REACH;
        maxZ += REACH;

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        TreeSet<BlockPos> drives = new TreeSet<>();
        for (BlockPos start : starts) {
            BlockPos immutable = start.immutable();
            if (seen.add(immutable) && extender.test(immutable)) {
                queue.add(immutable);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos here = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = here.relative(direction);
                if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY
                        || next.getZ() < minZ || next.getZ() > maxZ || !seen.add(next)) {
                    continue;
                }
                if (extender.test(next)) {
                    queue.add(next);
                } else if (drive.test(next)) {
                    drives.add(next);
                }
            }
        }
        return List.copyOf(drives);
    }

    /**
     * The step from one cell to another.
     *
     * <p>Every pointer in a frame is stored this way - an extender's to its drive, a drive's to its
     * frame's corner - for the reason the Astrolabe's are: assembling an airship relocates every block
     * into the plot grid at once, so an absolute position would still name the old world spot and the
     * whole frame would go dark on the deck. A step between two blocks that travel together never
     * changes.
     */
    public static BlockPos step(BlockPos from, BlockPos to) {
        return to.subtract(from);
    }

    /** The position a {@link #step} leads to. */
    public static BlockPos fromStep(BlockPos from, BlockPos step) {
        return from.offset(step.getX(), step.getY(), step.getZ());
    }

    private static int index(int x, int y, int z, int span) {
        int side = span + 1;
        return (x * side + y) * side + z;
    }

    /** Largest first, then most nearly centred on the drive, then lowest corner. */
    private static List<int[]> order() {
        List<int[]> entries = new ArrayList<>();
        for (int size = MAX_SIZE; size > MIN_SIZE; size--) {
            for (int dx = -(size - 1); dx <= 0; dx++) {
                for (int dy = -(size - 1); dy <= 0; dy++) {
                    for (int dz = -(size - 1); dz <= 0; dz++) {
                        entries.add(new int[]{size, dx, dy, dz});
                    }
                }
            }
        }
        entries.sort(Comparator
                .<int[]>comparingInt(entry -> -entry[0])
                .thenComparingInt(entry -> offCentre(new Frame(new BlockPos(entry[1], entry[2], entry[3]), entry[0]),
                        BlockPos.ZERO))
                .thenComparing(entry -> new BlockPos(entry[1], entry[2], entry[3])));
        return List.copyOf(entries);
    }
}
