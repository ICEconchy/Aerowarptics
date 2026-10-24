package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import uk.co.iceconchy.aerowarptics.collapse.CradleStructure;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Rift Plotter's footprint: the Collapse Cradle's, three blocks across and three high on its middle floor
 * block.
 *
 * <p>Both relics were built to one pattern by the same hands, so the plotter borrows the cradle's arithmetic rather
 * than keeping a copy of it that could drift: a master in the middle of the bottom layer, and twenty-six blocks round
 * it that know their master only by their place in the footprint - eight {@link RiftPlotterBaseBlock}s across the
 * floor, eighteen {@link RiftPlotterPartBlock}s above. What is the plotter's own is here - where its lens is.
 */
public final class PlotterStructure {

    /** Blocks along each side, and high. */
    public static final int SIZE = CradleStructure.SIZE;

    private PlotterStructure() {
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
