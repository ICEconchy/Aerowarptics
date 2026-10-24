package uk.co.iceconchy.aerowarptics.collapse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ring round a Collapse Cradle: which Depots and Spatial Stabilisers it counts, and the pull that says so.
 *
 * <p>Three things ask who is in the ring. The cradle, when it draws its offerings and counts its cage; a
 * stabiliser, when its goggles say what it is doing; and every client, when it draws motes drifting into the
 * machine from each thing the cradle counts. They ask here, by the same rule, because the ways they could
 * disagree are all silent: a mote drawn off a depot the ritual will never draw from, or a stabiliser that says
 * it is linked from a block further than the cradle looks.
 *
 * <p><b>Depots are remembered; stabilisers are not.</b> A cradle links to eight depots and no more, and a depot
 * it has linked to keeps its place until it goes - {@link #linked}. Were it simply "the nearest eight", every
 * depot set down nearer than the furthest would take a link off another, and to a player building round the
 * machine each new depot would seem to link, long past the eighth. A stabiliser needs no memory: every one in
 * reach that is switched on counts, however many.
 *
 * <p>Everything is an offset from the cradle's master, {x, y, z} in blocks. What a client draws is {@link #mote}:
 * dust drawn along an unseen line into the rings, on the clock in {@link #drift}; {@code CradleLinks} only
 * draws it.
 *
 * <p>Free of Minecraft, for {@code CradleRingTest}.
 */
public final class CradleRing {

    /** The most depots a cradle links to, and so the most a collapse draws from. */
    public static final int MOST_DEPOTS = 8;

    /** How far out from the middle of the rings the pull ends: on the outer ring's rim, as it lies at rest. */
    public static final double HOLD = CradleWorks.OUTER_RING[1] / 16.0D + 0.02D;
    /** Where a stabiliser is drawn on from: the top of its cap. */
    public static final double STABILISER_TOP = 1.0D;
    /** How far a depot's line bows upward at its middle, as an offering's flight does. A stabiliser's is straight. */
    public static final double DEPOT_BOW = 0.45D;

    /** How fast a mote drifts in, blocks a tick: under a block a second. Dust in a draught, not a signal. */
    public static final double DRIFT = 0.045D;
    /** How far apart the motes on one line are, in blocks. */
    public static final double SPACING = 1.6D;
    /** How far a mote wanders from its line at the middle of its way, and how many times it turns about it. */
    public static final double SWIRL = 0.09D;
    public static final double SWIRL_TURNS = 1.25D;
    /** How much of its way a mote spends coming out of nothing, and going back to it. */
    public static final double EASE = 0.2D;
    /** How much of the motes comes or goes in a tick: half a second to fade, either way. */
    public static final double FADE = 0.1D;

    private static final double GOLDEN = 0.6180339887D;

    /** Nearest the master first; then by x, then z, then y, so any two walks fill a free place the same way. */
    private static final Comparator<int[]> NEAREST = Comparator
            .<int[]>comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2])
            .thenComparingInt(offset -> offset[0])
            .thenComparingInt(offset -> offset[2])
            .thenComparingInt(offset -> offset[1]);

    /** What stands at an offset from the cradle's master, asked of whoever is looking - a server's level or a client's. */
    @FunctionalInterface
    public interface Sight {
        boolean sees(int dx, int dy, int dz);
    }

    private CradleRing() {
    }

    // ------------------------------------------------------------------ who is in it

    /** Whether a switched-on stabiliser this far from the master steadies its collapse: anywhere in a cube. */
    public static boolean steadies(int dx, int dy, int dz, int reach) {
        return Math.abs(dx) <= reach && Math.abs(dy) <= reach && Math.abs(dz) <= reach;
    }

    /** Whether a depot this far from the master may hold an offering: on the master's own level, in a square. */
    public static boolean offers(int dx, int dy, int dz, int reach) {
        return dy == 0 && Math.abs(dx) <= reach && Math.abs(dz) <= reach && (dx != 0 || dz != 0);
    }

    /**
     * The depots a cradle is linked to, a look on from the ones it was.
     *
     * <p>Every depot it was linked to that still stands in reach keeps its place, in the order it took it. Only a
     * place that is free - there were fewer than {@link #MOST_DEPOTS}, or one has gone - is given out, to the
     * nearest depot that has none. So a ninth depot gets nothing however near it is set down, and breaking a
     * linked one is how its place is passed on.
     *
     * @param before the depots it was linked to; from a save, so trusted for nothing - doubles and extras are dropped
     * @param depot  whether a Depot stands at an offset
     */
    public static List<int[]> linked(List<int[]> before, int reach, Sight depot) {
        List<int[]> now = new ArrayList<>();
        for (int[] offset : before) {
            if (now.size() < MOST_DEPOTS && offset.length == 3 && offers(offset[0], offset[1], offset[2], reach)
                    && !holds(now, offset) && depot.sees(offset[0], offset[1], offset[2])) {
                now.add(offset);
            }
        }
        if (now.size() >= MOST_DEPOTS) {
            return now;
        }
        List<int[]> free = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int[] offset = {dx, 0, dz};
                if (offers(dx, 0, dz, reach) && !holds(now, offset) && depot.sees(dx, 0, dz)) {
                    free.add(offset);
                }
            }
        }
        free.sort(NEAREST);
        for (int[] offset : free) {
            if (now.size() >= MOST_DEPOTS) {
                break;
            }
            now.add(offset);
        }
        return now;
    }

    /** The depots a cradle that remembers none links to: the nearest, nearest first - one saved before it remembered. */
    public static List<int[]> depots(int reach, Sight depot) {
        return linked(List.of(), reach, depot);
    }

    /**
     * The stabilisers steadying a collapse: every one in reach that is switched on. However many - it is how
     * evenly they stand that counts, see {@link CollapseInstability}.
     *
     * @param working whether a switched-on Spatial Stabiliser stands at an offset
     */
    public static List<int[]> stabilisers(int reach, Sight working) {
        List<int[]> found = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (steadies(dx, dy, dz, reach) && working.sees(dx, dy, dz)) {
                        found.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        found.sort(NEAREST);
        return found;
    }

    /** Whether a ring holds an offset. */
    public static boolean holds(List<int[]> ring, int[] offset) {
        for (int[] member : ring) {
            if (member[0] == offset[0] && member[1] == offset[1] && member[2] == offset[2]) {
                return true;
            }
        }
        return false;
    }

    /** Whether two rings are the same offsets in the same order. */
    public static boolean same(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            int[] left = a.get(i);
            int[] right = b.get(i);
            if (left[0] != right[0] || left[1] != right[1] || left[2] != right[2]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the pull

    /** Where a stabiliser is drawn on from, from the middle of the master's base: the top of its cap. */
    public static double[] stabiliserEnd(int[] offset) {
        return new double[]{offset[0], offset[1] + STABILISER_TOP, offset[2]};
    }

    /** Where a depot is drawn on from: where the depot shows what it holds. */
    public static double[] depotEnd(int[] offset) {
        return new double[]{offset[0], offset[1] + CollapseChoreography.DEPOT_REST, offset[2]};
    }

    /**
     * Where the line from a point meets the machine: on the rim of the rings, on the side the point is on, so
     * nothing is drawn through the armillary to reach its middle.
     */
    public static double[] hold(double[] from) {
        double x = from[0];
        double y = from[1] - CollapseChoreography.CENTRE;
        double z = from[2];
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-6D) {
            return new double[]{HOLD, CollapseChoreography.CENTRE, 0.0D};
        }
        double scale = HOLD / length;
        return new double[]{x * scale, CollapseChoreography.CENTRE + y * scale, z * scale};
    }

    /** How long the line from a point to the machine is, straight, in blocks. */
    public static double length(double[] from) {
        double[] to = hold(from);
        double x = to[0] - from[0];
        double y = to[1] - from[1];
        double z = to[2] - from[2];
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * A point on the unseen line, {@code t} of the way from the block to where the machine holds it: straight,
     * lifted at its middle by the bow.
     */
    public static double[] thread(double[] from, double t, double bow) {
        double[] to = hold(from);
        return new double[]{
                from[0] + (to[0] - from[0]) * t,
                from[1] + (to[1] - from[1]) * t + Math.sin(Math.PI * t) * bow,
                from[2] + (to[2] - from[2]) * t};
    }

    /** How many motes a line of this length carries: one for each {@link #SPACING} of it, and never none. */
    public static int motes(double length) {
        return Math.max(1, (int) Math.round(length / SPACING));
    }

    /**
     * How far along its line a mote is, 0 at the block and towards 1 at the machine. Every mote drifts at
     * {@link #DRIFT} whatever the length of its line, the motes of one line are evenly strung along it, and
     * each line is out of step with the next by the golden ratio, so a ring of them never moves as one.
     *
     * @param line  which of the cradle's lines
     * @param mote  which mote of that line, of {@code motes}
     */
    public static double drift(double time, double length, int line, int mote, int motes) {
        double turns = time * DRIFT / Math.max(0.1D, length) + mote / (double) Math.max(1, motes) + line * GOLDEN;
        return turns - Math.floor(turns);
    }

    /** The angle a mote begins its turn about its line at, in turns: its own, so no two keep step. */
    public static double turn(int line, int mote) {
        double turns = line * 0.37D + mote * GOLDEN;
        return turns - Math.floor(turns);
    }

    /**
     * Where a mote is, {@code t} of the way along its line: on the line, and turning slowly about it, a little
     * wide of it at the middle of its way and dead on it at either end - so it rises out of the block, wanders in
     * as dust in a draught does, and is drawn onto the rim.
     *
     * @param turn the angle it begins at, in turns - see {@link #turn}
     */
    public static double[] mote(double[] from, double t, double bow, double turn) {
        double[] on = thread(from, t, bow);
        double[] to = hold(from);
        double ax = to[0] - from[0];
        double ay = to[1] - from[1];
        double az = to[2] - from[2];
        double along = Math.sqrt(ax * ax + ay * ay + az * az);
        if (along < 1.0e-6D) {
            return on;
        }
        ax /= along;
        ay /= along;
        az /= along;
        // Across the line and level, then square to both. A line straight up has no level across of its own.
        double sx = -az;
        double sz = ax;
        double side = Math.sqrt(sx * sx + sz * sz);
        if (side < 1.0e-6D) {
            sx = 1.0D;
            sz = 0.0D;
            side = 1.0D;
        }
        sx /= side;
        sz /= side;
        double lx = -sz * ay;
        double ly = sz * ax - sx * az;
        double lz = sx * ay;
        double angle = Math.PI * 2.0D * (turn + SWIRL_TURNS * t);
        double wide = SWIRL * Math.sin(Math.PI * t);
        double across = Math.cos(angle) * wide;
        double up = Math.sin(angle) * wide;
        return new double[]{on[0] + sx * across + lx * up, on[1] + ly * up, on[2] + sz * across + lz * up};
    }

    /**
     * How much of a mote is drawn, 0..1, by how far along it is: nothing as it leaves the block, whole along
     * its way, nothing as the machine takes it. A mote going round again does so unseen.
     */
    public static double drawn(double t) {
        double edge = Math.min(t, 1.0D - t) / EASE;
        if (edge <= 0.0D) {
            return 0.0D;
        }
        if (edge >= 1.0D) {
            return 1.0D;
        }
        return edge * edge * (3.0D - 2.0D * edge);
    }

    /**
     * How much of the motes is drawn at all, a tick on: fading in while the cradle wants them, and out while a
     * collapse runs - its own lightning is what reaches for the stabilisers then, and its offerings have left
     * their depots.
     */
    public static double shown(double current, boolean wanted) {
        double next = current + (wanted ? FADE : -FADE);
        return Math.max(0.0D, Math.min(1.0D, next));
    }
}
