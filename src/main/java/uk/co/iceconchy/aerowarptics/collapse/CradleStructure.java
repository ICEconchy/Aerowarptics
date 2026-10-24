package uk.co.iceconchy.aerowarptics.collapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Collapse Cradle's footprint: three blocks across and three high, standing on its middle block.
 *
 * <p>The cradle is placed whole, the way a bed or a door is, only larger. One block - the <em>master</em>, in
 * the middle of the bottom layer - is the machine: its block entity holds the ritual, it is turned from below or
 * through the middle of each side of its floor, and its renderer draws all twenty-seven blocks' worth of model. The other twenty-six are
 * {@link CollapseCradlePartBlock}s, which draw nothing and hand everything they are asked - a click, a pipe, a
 * funnel, a redstone signal, a pair of goggles - to the master. A part knows its master by where it stands in
 * the footprint, which it carries as three small numbers in its block state; nothing is stored anywhere else,
 * so a part is never out of step with the machine it belongs to.
 *
 * <p>Kept apart from the blocks so the footprint can be tested for what it is - arithmetic over block
 * positions - without a registry, as {@code AstrolabeStructure} is.
 */
public final class CradleStructure {

    /** Blocks along each side, and high. */
    public static final int SIZE = 3;

    /** A part's position in the footprint along each axis, 0..2, where the master is (1, 0, 1). */
    public static final int MASTER_X = 1;
    public static final int MASTER_Y = 0;
    public static final int MASTER_Z = 1;

    private static final List<Vec3i> OFFSETS = offsetsInOrder();

    private CradleStructure() {
    }

    private static List<Vec3i> offsetsInOrder() {
        List<Vec3i> offsets = new ArrayList<>(SIZE * SIZE * SIZE);
        offsets.add(Vec3i.ZERO);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    Vec3i offset = offsetOf(x, y, z);
                    if (!offset.equals(Vec3i.ZERO)) {
                        offsets.add(offset);
                    }
                }
            }
        }
        return List.copyOf(offsets);
    }

    /** Every cell's offset from the master: the master's own first, then the rest bottom up, in a fixed order. */
    public static List<Vec3i> offsets() {
        return OFFSETS;
    }

    /** Every block the machine stands in, master first. */
    public static List<BlockPos> cells(BlockPos master) {
        List<BlockPos> cells = new ArrayList<>(OFFSETS.size());
        for (Vec3i offset : OFFSETS) {
            cells.add(master.offset(offset));
        }
        return cells;
    }

    /** A cell's offset from the master, from its place in the footprint. */
    public static Vec3i offsetOf(int x, int y, int z) {
        return new Vec3i(x - MASTER_X, y - MASTER_Y, z - MASTER_Z);
    }

    /** A cell's place in the footprint, {x, y, z} each 0..2, from its offset from the master. */
    public static int[] placeOf(Vec3i offset) {
        return new int[]{offset.getX() + MASTER_X, offset.getY() + MASTER_Y, offset.getZ() + MASTER_Z};
    }

    /** Whether a place in the footprint is the master's rather than a part's. */
    public static boolean isMaster(int x, int y, int z) {
        return x == MASTER_X && y == MASTER_Y && z == MASTER_Z;
    }

    /** Whether a place is inside the footprint at all. */
    public static boolean isPlace(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }

    /** Where a part's master is, from where the part stands and its place in the footprint. */
    public static BlockPos master(BlockPos part, int x, int y, int z) {
        return part.subtract(offsetOf(x, y, z));
    }

    /** Whether a position is one of the machine's own. */
    public static boolean covers(BlockPos master, BlockPos pos) {
        int dx = pos.getX() - master.getX();
        int dy = pos.getY() - master.getY();
        int dz = pos.getZ() - master.getZ();
        return Math.abs(dx) <= MASTER_X && Math.abs(dz) <= MASTER_Z && dy >= 0 && dy < SIZE;
    }

    /**
     * Whether a machine could stand here: every cell but the master's (which the player is placing into) free
     * to be replaced, and the whole of it inside the world's build height.
     *
     * @param replaceable whether the block at a position may be built over
     */
    public static boolean fits(BlockPos master, int minBuildHeight, int maxBuildHeight, Predicate<BlockPos> replaceable) {
        if (master.getY() < minBuildHeight || master.getY() + SIZE > maxBuildHeight) {
            return false;
        }
        for (Vec3i offset : OFFSETS) {
            if (!offset.equals(Vec3i.ZERO) && !replaceable.test(master.offset(offset))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every position touching the machine from outside - what would once have been "beside" a one-block
     * cradle. A Rift Modulator dressing the ritual has to stand in one of these.
     */
    public static List<BlockPos> around(BlockPos master) {
        Set<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos cell : cells(master)) {
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (!covers(master, next)) {
                    out.add(next);
                }
            }
        }
        return List.copyOf(out);
    }

    /** Whether a position is in the machine's bottom layer - the master's and its eight bases'. */
    public static boolean bottom(BlockPos master, BlockPos pos) {
        return covers(master, pos) && pos.getY() == master.getY();
    }

    /**
     * Whether a block of the machine has a shaft on a face - the rule that lets a shaft turn the machine from any of
     * its four sides or from under its middle, and lets no rotation out of it again.
     *
     * <p>The block in the middle of each side of the floor has one, on its face looking out, and the master has one,
     * on its underside. So no block of the machine has two shafts, which is the only way a Create block passes
     * rotation on, and no shaft looks at another block of the machine, so nothing crosses the floor either: each of
     * the five is the end of whatever line is run into it, and the machine goes by the fastest (see
     * {@link RelicFloor}). A corner has none, since its two outward faces would join one side's line to the next
     * side's; nor has any other block's underside, nor anything's top.
     */
    public static boolean shaftTowards(BlockPos master, BlockPos cell, Direction face) {
        if (cell.equals(master)) {
            return face == Direction.DOWN;
        }
        return face.getAxis().isHorizontal() && cell.relative(face.getOpposite()).equals(master);
    }

    /**
     * The five blocks a shaft can turn the machine through, in a fixed order: the master, from below, then the floor
     * block in the middle of each side.
     */
    public static List<BlockPos> inputs(BlockPos master) {
        List<BlockPos> inputs = new ArrayList<>(5);
        inputs.add(master);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            inputs.add(master.relative(side));
        }
        return inputs;
    }

    /**
     * The machine's box, relative to the master block's lowest corner: {minX, minY, minZ, maxX, maxY, maxZ}.
     * What the renderer draws in, what the whole-machine outline traces, and what a blast measures cover from.
     */
    public static double[] box() {
        return new double[]{-MASTER_X, -MASTER_Y, -MASTER_Z, SIZE - MASTER_X, SIZE - MASTER_Y, SIZE - MASTER_Z};
    }
}
