package uk.co.iceconchy.aerowarptics.ward;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import uk.co.iceconchy.aerowarptics.collapse.CradleStructure;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Rift Ward's footprint: the Collapse Cradle's, three blocks across and three high on its middle floor
 * block.
 *
 * <p>Both relics were built to one pattern by the same hands, so the ward borrows the cradle's arithmetic rather
 * than keeping a copy of it that could drift: a master in the middle of the bottom layer, and twenty-six blocks round
 * it that know their master only by their place in the footprint - eight {@link RiftWardBaseBlock}s across the
 * floor, eighteen {@link RiftWardPartBlock}s above. What is the ward's own is here - where its totem stands.
 */
public final class WardStructure {

    /** Blocks along each side, and high. */
    public static final int SIZE = CradleStructure.SIZE;

    /**
     * How far above the master block's floor the middle of the totem is, in blocks: where the machine's pull is
     * drawn from and its sounds come from. The middle of the artist's {@code core} bone, over sixteen.
     */
    public static final double CORE_HEIGHT = 26.0D / 16.0D;

    /** How far above the master block's floor the machine ends, open to the sky: where what it sheds rises from. */
    public static final double TOP_HEIGHT = 3.0D;

    private WardStructure() {
    }

    public static List<Vec3i> offsets() {
        return CradleStructure.offsets();
    }

    public static List<BlockPos> cells(BlockPos master) {
        return CradleStructure.cells(master);
    }

    public static int[] placeOf(Vec3i offset) {
        return CradleStructure.placeOf(offset);
    }

    public static BlockPos master(BlockPos part, int x, int y, int z) {
        return CradleStructure.master(part, x, y, z);
    }

    public static boolean covers(BlockPos master, BlockPos pos) {
        return CradleStructure.covers(master, pos);
    }

    public static boolean fits(BlockPos master, int minBuildHeight, int maxBuildHeight, Predicate<BlockPos> replaceable) {
        return CradleStructure.fits(master, minBuildHeight, maxBuildHeight, replaceable);
    }

    /** Every position touching the machine from outside, where a Rift Modulator dressing its rifts may stand. */
    public static List<BlockPos> around(BlockPos master) {
        return CradleStructure.around(master);
    }

    /** The machine's box, relative to the master block's lowest corner. */
    public static double[] box() {
        return CradleStructure.box();
    }
}
