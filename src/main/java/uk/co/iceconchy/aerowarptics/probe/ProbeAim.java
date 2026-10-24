package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.core.BlockPos;

/**
 * Where a sounding lands.
 *
 * <p>In the probe's own dimension that is the old sum: where it is thrown from, plus the range along the
 * bearing. Thrown into another dimension there is a step before it, because "where it is thrown from" is
 * a position in this one. The game already has an answer to what a place here is called over there - the
 * coordinate scale a Nether portal uses, eight blocks of Overworld to one of Nether - and a sounding takes
 * it, so a probe aimed at the Nether starts from the spot a portal built under the ship would come out
 * at. The range is then blocks of the dimension being sounded: three thousand north-east is three
 * thousand of the ground the ship will arrive over, not of the ground it left.
 *
 * <p>Kept free of the level so it can be tested: the level supplies the scale and the limits, and this
 * does the arithmetic.
 */
public final class ProbeAim {

    private ProbeAim() {
    }

    /**
     * @param scale blocks of the sounded dimension to one block of this one; {@code 1} in the probe's own
     * @param minX  the sounded world's border, which a sounding thrown past it reads the edge of rather
     *              than nothing at all; likewise {@code maxX}, {@code minZ} and {@code maxZ}
     * @param minY  the lowest block the sounded dimension has
     * @param maxY  one past its highest
     * @return the middle of the ground the sounding reads. Its height is only where the probe happened to
     * be, brought inside the sounded dimension: the survey finds the ground's own
     */
    public static BlockPos target(double fromX, double fromY, double fromZ, double scale, ProbeBearing bearing,
                                  int range, double minX, double maxX, double minZ, double maxZ, int minY, int maxY) {
        int x = floor(fromX * scale + bearing.dx() * range);
        int z = floor(fromZ * scale + bearing.dz() * range);
        return new BlockPos(
                clamp(x, floor(minX), ceil(maxX) - 1),
                clamp(floor(fromY), minY, maxY - 1),
                clamp(z, floor(minZ), ceil(maxZ) - 1));
    }

    private static int clamp(int value, int low, int high) {
        // A border narrower than a block leaves nothing between its edges; the low edge wins.
        return high < low ? low : Math.max(low, Math.min(high, value));
    }

    private static int floor(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.floor(value)));
    }

    private static int ceil(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.ceil(value)));
    }
}
