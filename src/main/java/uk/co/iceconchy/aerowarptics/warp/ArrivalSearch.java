package uk.co.iceconchy.aerowarptics.warp;

import java.util.ArrayList;
import java.util.List;

/**
 * The order in which places to drop an airship are tried.
 *
 * <p>The airspace directly over an anchor is the one place a pilot expects to arrive, so the search
 * climbs that column first and only starts stepping sideways once every height in it has been ruled
 * out. That matters most for the ships it is hardest to place: a two-hundred-block hull will not fit
 * in a valley, but it will fit above one, and a search that sorted purely by distance from the anchor
 * would wander off sideways into the hillside long before it thought to gain altitude.
 *
 * <pre>
 *   column above the anchor, rising      ┆   then rings, each rising
 *            ↑ ↑ ↑                       ┆      ↑   ↑ ↑ ↑   ↑
 *            │ │ │                       ┆      │   │ │ │   │
 *            ● ● ●                       ┆      ●   ● ● ●   ●
 *              ▲ anchor                  ┆    r=1     r=2
 * </pre>
 *
 * <p>Kept free of Minecraft types so the ordering can be tested directly.
 */
public final class ArrivalSearch {

    private ArrivalSearch() {
    }

    /**
     * One candidate offset from the anchor's clearance point, in blocks.
     *
     * @param dx horizontal offset
     * @param dy additional height above the clearance point; never negative
     * @param dz horizontal offset
     */
    public record Candidate(int dx, int dy, int dz) {

        /** Horizontal distance from the anchor's column, which is what the search widens by. */
        public double horizontalDistance() {
            return Math.sqrt((double) dx * dx + (double) dz * dz);
        }
    }

    /**
     * Builds the ordered list of positions to try.
     *
     * <p>The first entry is always {@code (0, 0, 0)} - straight above the anchor at the caller's
     * clearance height. From there the list rises through the column, then repeats the climb at each
     * widening ring. The search never goes below the clearance point: an airship is not squeezed in
     * underneath its own anchor.
     *
     * @param horizontalRadius furthest the search may step sideways, in blocks
     * @param verticalRadius   furthest the search may climb above the clearance point, in blocks
     * @param step             spacing between candidates
     * @param limit            hard cap on how many candidates are produced
     */
    public static List<Candidate> candidates(int horizontalRadius, int verticalRadius, int step, int limit) {
        int safeStep = Math.max(1, step);
        int hr = Math.max(0, horizontalRadius);
        int vr = Math.max(0, verticalRadius);
        int safeLimit = Math.max(1, limit);

        List<Candidate> result = new ArrayList<>();

        // The anchor's own column, from the clearance height upwards.
        for (int dy = 0; dy <= vr && result.size() < safeLimit; dy += safeStep) {
            result.add(new Candidate(0, dy, 0));
        }

        // Then rings of increasing radius, each climbing the same range.
        for (int radius = safeStep; radius <= hr && result.size() < safeLimit; radius += safeStep) {
            for (int[] offset : ring(radius, safeStep)) {
                for (int dy = 0; dy <= vr; dy += safeStep) {
                    if (result.size() >= safeLimit) {
                        return result;
                    }
                    result.add(new Candidate(offset[0], dy, offset[1]));
                }
            }
        }

        return result;
    }

    /**
     * The perimeter of a square ring at the given radius, walked clockwise from the north face.
     *
     * <p>A square rather than a circle because the candidates are block offsets and a square ring
     * covers the annulus without gaps or repeats.
     */
    private static List<int[]> ring(int radius, int step) {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x += step) {
            offsets.add(new int[]{x, -radius});
            offsets.add(new int[]{x, radius});
        }
        for (int z = -radius + step; z <= radius - step; z += step) {
            offsets.add(new int[]{-radius, z});
            offsets.add(new int[]{radius, z});
        }
        // Nearest-first within the ring keeps the arrival tidy when several positions work.
        offsets.sort((a, b) -> Double.compare(
                (double) a[0] * a[0] + (double) a[1] * a[1],
                (double) b[0] * b[0] + (double) b[1] * b[1]));
        return offsets;
    }
}
