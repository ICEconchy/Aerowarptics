package uk.co.iceconchy.aerowarptics.astrolabe;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shape of an Astrolabe Cartography Table: three by three, one block deep, lying flat.
 *
 * <p>Kept apart from the block so the formation rules can be tested for what they are - arithmetic
 * over block positions - without dragging in a registry. Everything here takes a predicate rather
 * than a level, which is also what stops the search from caring whether it is running on the ground
 * or inside an airship's plot.
 */
public final class AstrolabeStructure {

    /** Width and depth of a complete table, in blocks. */
    public static final int SIZE = 3;

    private AstrolabeStructure() {
    }

    /**
     * The nine positions a table centred here occupies.
     *
     * <p>Ordered north-west to south-east so anything iterating them - forming, breaking, syncing -
     * visits them in the same order every time.
     */
    public static List<BlockPos> cells(BlockPos centre) {
        List<BlockPos> cells = new ArrayList<>(SIZE * SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                cells.add(centre.offset(dx, 0, dz));
            }
        }
        return cells;
    }

    /** Whether every cell of a table centred here is an astrolabe block. */
    public static boolean isComplete(BlockPos centre, Predicate<BlockPos> present) {
        for (BlockPos cell : cells(centre)) {
            if (!present.test(cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the table this block would belong to.
     *
     * <p>A single block sits in nine possible tables - it could be any of the nine cells - so all
     * nine centres are tried. Ties are broken by taking the lowest coordinates rather than the first
     * one stumbled upon, so a four-by-four slab of blocks forms the same table whichever corner of it
     * was placed last. Without that, the same layout would form differently depending on build order,
     * and a player rebuilding a table would get a different answer each time.
     *
     * @param member  a position known to hold an astrolabe block
     * @param present whether a given position holds an astrolabe block that is free to join a table
     * @return the centre of the table to form, or {@code null} when there is not a complete one
     */
    @Nullable
    public static BlockPos findCentre(BlockPos member, Predicate<BlockPos> present) {
        BlockPos best = null;
        for (BlockPos candidate : cells(member)) {
            if (!isComplete(candidate, present)) {
                continue;
            }
            if (best == null || compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best == null ? null : best.immutable();
    }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    /** Whether an offset from a centre lands inside that centre's table. */
    public static boolean covers(BlockPos centre, BlockPos pos) {
        return pos.getY() == centre.getY()
                && Math.abs(pos.getX() - centre.getX()) <= 1
                && Math.abs(pos.getZ() - centre.getZ()) <= 1;
    }
}
