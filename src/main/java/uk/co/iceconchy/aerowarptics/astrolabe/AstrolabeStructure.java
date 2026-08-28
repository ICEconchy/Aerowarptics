package uk.co.iceconchy.aerowarptics.astrolabe;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shape of an Astrolabe Cartography Table: a square, one block deep, lying flat.
 *
 * <p>One block, two by two, or three by three. A lone block is a working table, which is what makes
 * the machine reachable early, and the larger sizes are worth building because a bigger table charts
 * more ground - see {@code TerrainHologram}.
 *
 * <p>A table is identified by its <em>origin</em>, the corner with the lowest coordinates, rather
 * than by a centre. Three by three has a middle block and two by two does not, so a centre is not
 * something every size has; a corner is. The origin is also the cell that holds the table's state and
 * draws its map.
 *
 * <p>Kept apart from the block so the formation rules can be tested for what they are - arithmetic
 * over block positions - without dragging in a registry. Everything here takes a predicate rather
 * than a level, which is also what stops the search from caring whether it is running on the ground
 * or inside an airship's plot.
 */
public final class AstrolabeStructure {

    /** Largest table, in blocks along a side. */
    public static final int MAX_SIZE = 3;

    /** Smallest table. One block is a table, so an astrolabe is never useless on its own. */
    public static final int MIN_SIZE = 1;

    private AstrolabeStructure() {
    }

    /**
     * A table: where its lowest corner is, and how many blocks along a side.
     *
     * @param origin the cell holding the table's state and drawing its map
     * @param size   1, 2 or 3
     */
    public record Table(BlockPos origin, int size) {

        public Table {
            if (size < MIN_SIZE || size > MAX_SIZE) {
                throw new IllegalArgumentException("an astrolabe table is 1, 2 or 3 blocks a side");
            }
            origin = origin.immutable();
        }

        public List<BlockPos> cells() {
            return AstrolabeStructure.cells(origin, size);
        }

        public boolean covers(BlockPos pos) {
            return AstrolabeStructure.covers(origin, size, pos);
        }

        /** Whether this table lies wholly inside another - the test for absorbing a smaller one. */
        public boolean isInside(Table larger) {
            return cells().stream().allMatch(larger::covers);
        }
    }

    /**
     * The positions a table occupies.
     *
     * <p>Ordered north-west to south-east so anything iterating them - forming, breaking, syncing -
     * visits them in the same order every time.
     */
    public static List<BlockPos> cells(BlockPos origin, int size) {
        List<BlockPos> cells = new ArrayList<>(size * size);
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                cells.add(origin.offset(dx, 0, dz));
            }
        }
        return cells;
    }

    /** Whether a position lands inside a table. */
    public static boolean covers(BlockPos origin, int size, BlockPos pos) {
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        return pos.getY() == origin.getY() && dx >= 0 && dx < size && dz >= 0 && dz < size;
    }

    /** Whether every cell of a table is acceptable to the given test. */
    public static boolean isComplete(Table table, Predicate<BlockPos> usable) {
        for (BlockPos cell : table.cells()) {
            if (!usable.test(cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every table this block could belong to, best first.
     *
     * <p>Ordered largest first, so a slab of nine blocks becomes one three-by-three rather than a
     * one-by-one and some spare parts. Within a size, ties break on the lowest coordinates rather
     * than on whichever was stumbled upon first, so the same layout forms the same table whichever
     * corner of it was placed last. Without that, a player rebuilding a table would get a different
     * answer depending on build order.
     *
     * @param member a position known to hold an astrolabe block
     */
    public static List<Table> candidates(BlockPos member) {
        List<Table> candidates = new ArrayList<>();
        for (int size = MAX_SIZE; size >= MIN_SIZE; size--) {
            for (int dx = size - 1; dx >= 0; dx--) {
                for (int dz = size - 1; dz >= 0; dz--) {
                    candidates.add(new Table(member.offset(-dx, 0, -dz), size));
                }
            }
        }
        return candidates;
    }

    /**
     * The best table this block could form, or {@code null} for none.
     *
     * @param usable whether a given cell may be built into the table under consideration
     */
    public static Table findBest(BlockPos member, Predicate<BlockPos> usable) {
        for (Table candidate : candidates(member)) {
            if (isComplete(candidate, usable)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Every position that could be a cell of a table containing this one.
     *
     * <p>The neighbourhood to re-examine when a block appears or disappears: a table containing a
     * cell reaches at most {@code MAX_SIZE - 1} away from it in each direction.
     */
    public static List<BlockPos> neighbourhood(BlockPos member) {
        List<BlockPos> around = new ArrayList<>();
        int reach = MAX_SIZE - 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                around.add(member.offset(dx, 0, dz));
            }
        }
        return around;
    }
}
