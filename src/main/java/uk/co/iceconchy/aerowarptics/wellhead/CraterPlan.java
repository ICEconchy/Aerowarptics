package uk.co.iceconchy.aerowarptics.wellhead;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which blocks a meltdown takes, and in what order: a ball round the fissure, from the middle outwards.
 *
 * <p>Sorted into shells a block thick so the ground can be taken a shell at a time and be seen to go - a crater
 * that appears whole in one tick is a hole, not an event. Pure arithmetic over offsets, so the order and the
 * count can be tested without a world, and so the server and the client agree on where the edge is.
 *
 * <p>An offset is one packed {@code int} rather than an object. The largest crater is nearly half a million
 * blocks, and half a million small objects is a great deal of memory to hold for the half a minute it is
 * wanted; packed it is under two megabytes. Shells are worked out once a radius and kept until
 * {@link #forget()}, which whatever is playing meltdowns calls when the last of them ends.
 */
public final class CraterPlan {

    /** The furthest an offset can be along one axis and still pack: seven bits a side. */
    public static final int MOST = 63;

    private static final int BIAS = 64;

    private static final Map<Integer, int[][]> SHELLS = new ConcurrentHashMap<>();

    private CraterPlan() {
    }

    public static int pack(int dx, int dy, int dz) {
        return ((dx + BIAS) << 14) | ((dy + BIAS) << 7) | (dz + BIAS);
    }

    public static int x(int packed) {
        return ((packed >> 14) & 0x7F) - BIAS;
    }

    public static int y(int packed) {
        return ((packed >> 7) & 0x7F) - BIAS;
    }

    public static int z(int packed) {
        return (packed & 0x7F) - BIAS;
    }

    /**
     * Every offset within the radius, shell by shell: {@code shells(r)[n]} holds the offsets whose distance from
     * the middle rounds down to {@code n}. The middle itself is shell nought.
     */
    public static int[][] shells(int radius) {
        if (radius < 0 || radius > MOST) {
            throw new IllegalArgumentException("a crater of radius " + radius + " does not pack");
        }
        return SHELLS.computeIfAbsent(radius, CraterPlan::build);
    }

    private static int[][] build(int radius) {
        int[] counts = new int[radius + 1];
        // Half a block over, so the ball is round rather than a diamond with a single block at each pole.
        double reach = (radius + 0.5D) * (radius + 0.5D);
        for (int pass = 0; pass < 2; pass++) {
            int[][] shells = new int[radius + 1][];
            if (pass == 1) {
                for (int shell = 0; shell <= radius; shell++) {
                    shells[shell] = new int[counts[shell]];
                    counts[shell] = 0;
                }
            }
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int squared = dx * dx + dy * dy + dz * dz;
                        if (squared > reach) {
                            continue;
                        }
                        int shell = Math.min(radius, (int) Math.floor(Math.sqrt(squared)));
                        if (pass == 1) {
                            shells[shell][counts[shell]] = pack(dx, dy, dz);
                        }
                        counts[shell]++;
                    }
                }
            }
            if (pass == 1) {
                return shells;
            }
        }
        throw new AssertionError();
    }

    /** Lets go of every shell worked out so far. */
    public static void forget() {
        SHELLS.clear();
    }

    /** How many blocks a crater of this radius reaches, for a budget and for a test. */
    public static int blocks(int radius) {
        int count = 0;
        for (int[] shell : shells(radius)) {
            count += shell.length;
        }
        return count;
    }

    /** Whether an offset is inside a crater of this radius. */
    public static boolean within(int dx, int dy, int dz, int radius) {
        return dx * dx + dy * dy + dz * dz <= (radius + 0.5D) * (radius + 0.5D);
    }

    /**
     * The crater's wall: every offset just outside it, a block or two deep. What is solid there is turned to
     * Scarred Stone, so the bowl is lined rather than left as whatever the ground happened to be.
     */
    public static int[] lining(int radius) {
        int outer = Math.min(MOST, radius + 2);
        double reach = (radius + 2.0D) * (radius + 2.0D);
        List<Integer> found = new ArrayList<>();
        for (int dy = -outer; dy <= outer; dy++) {
            for (int dx = -outer; dx <= outer; dx++) {
                for (int dz = -outer; dz <= outer; dz++) {
                    if (!within(dx, dy, dz, radius) && dx * dx + dy * dy + dz * dz <= reach) {
                        found.add(pack(dx, dy, dz));
                    }
                }
            }
        }
        int[] lining = new int[found.size()];
        for (int i = 0; i < lining.length; i++) {
            lining[i] = found.get(i);
        }
        return lining;
    }

    /**
     * Blobs of ground a meltdown leaves hanging in the bowl: each {@code {x, y, z, radius}} as offsets from the
     * middle. Below the fissure's own level, where the ground is, and well inside the crater, so each hangs
     * clear of the wall. The same for the same seed, so a meltdown that is saved and resumed leaves the ones
     * it meant to.
     */
    public static List<int[]> islands(long seed, int radius, int count) {
        Random random = new Random(seed);
        List<int[]> islands = new ArrayList<>(count);
        for (int tried = 0; tried < count * 8 && islands.size() < count; tried++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double out = radius * (0.2D + 0.45D * random.nextDouble());
            int size = 2 + random.nextInt(radius >= 40 ? 4 : 3);
            int x = (int) Math.round(Math.cos(angle) * out);
            int z = (int) Math.round(Math.sin(angle) * out);
            int y = -(int) Math.round(radius * (0.08D + 0.42D * random.nextDouble()));
            // Wholly inside the crater, with room to spare, or it is a lump on the wall and not an island.
            if (Math.sqrt(x * x + y * y + z * z) + size + 2.0D > radius) {
                continue;
            }
            boolean clear = true;
            for (int[] other : islands) {
                double apart = Math.sqrt(Math.pow(x - other[0], 2) + Math.pow(y - other[1], 2) + Math.pow(z - other[2], 2));
                clear &= apart > size + other[3] + 2.0D;
            }
            if (clear) {
                islands.add(new int[]{x, y, z, size});
            }
        }
        return islands;
    }

    /** Whether an offset is part of one of the blobs left hanging. */
    public static boolean inIsland(int dx, int dy, int dz, List<int[]> islands) {
        for (int[] island : islands) {
            int x = dx - island[0];
            // Flattened a little: a lump of ground left in the air reads as ground if it is wider than tall.
            double y = (dy - island[1]) * 1.4D;
            int z = dz - island[2];
            if (x * x + y * y + z * z <= (island[3] + 0.5D) * (island[3] + 0.5D)) {
                return true;
            }
        }
        return false;
    }
}
