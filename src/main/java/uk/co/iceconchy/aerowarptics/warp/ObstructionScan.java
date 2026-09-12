package uk.co.iceconchy.aerowarptics.warp;

import org.jetbrains.annotations.Nullable;

/**
 * Whether anything solid stands inside a volume.
 *
 * <p>Deliberately free of Minecraft, in the same way {@code AWLayout} and {@code ChuteTransfer} are:
 * it takes a predicate rather than a level, so the thing that decides whether a hull may materialise
 * somewhere can be swept across thousands of arrangements without a server running.
 *
 * <h2>Why this is exhaustive</h2>
 * The check it replaces sampled on a <em>stride</em>, chosen so that a large hull cost about the same
 * to test as a small one. That is the right instinct for a cost estimate and quite wrong for this,
 * because an obstruction test has exactly one job and a sampler cannot do it:
 *
 * <pre>
 *   hull 30x20x60      stride 2   a one-block floor is invisible
 *   hull 60x40x120     stride 4   a three-block wall is invisible
 *   hull 200x80x200    stride 9   an eight-block wall is invisible
 * </pre>
 *
 * <p>So a ship could be cleared to arrive inside a building, a bridge deck or a tree, and would -
 * the sampler stepped straight over them. Anything thicker than the stride was caught, which is why
 * mountains were fine and rooms were not.
 *
 * <p>This visits every position instead. What makes that affordable is not sampling but skipping:
 * the caller is expected to hand over one already-non-empty region at a time, and the scan bails the
 * instant it finds something. A hull arriving in open sky touches almost nothing; a hull arriving
 * inside a hill finds rock on its first read.
 *
 * <h2>Failing safe</h2>
 * There is still a budget, because a very large volume of non-solid-but-not-air blocks - an ocean,
 * a forest canopy - has nothing to early-exit on. When it is exhausted the answer is
 * {@link Verdict#TOO_LARGE}, and the caller treats that as obstructed. That is the whole difference:
 * the old budget made the test quietly coarser and let a ship through, this one makes it refuse.
 */
public final class ObstructionScan {

    private ObstructionScan() {
    }

    /** What a scan concluded. */
    public enum Verdict {
        /** Nothing solid anywhere in the volume. */
        CLEAR,
        /** Something solid was found; the scan stopped there. */
        OBSTRUCTED,
        /**
         * The volume was too big to prove clear within the budget.
         *
         * <p>Not an answer, and must never be read as one. A caller treats it exactly as
         * {@link #OBSTRUCTED}: an unproven volume is not a safe one.
         */
        TOO_LARGE
    }

    /** Whether the block at a position would stop a hull. */
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    /** A solid position a scan stopped on. */
    public record Hit(int x, int y, int z) {
    }

    /**
     * How far a block may reach into a volume and still count as merely touching it.
     *
     * <p>A hull resting on the ground is never exactly on a block boundary: Sable settles it a few
     * hundredths into whatever it sits on, and its world box is a double. Without this, the layer of
     * ground under a moored ship read as being inside the ship's own sweep and refused the launch -
     * every block of it, the whole length of the corridor. An eighth of a block is two pixels: far
     * more than the solver's settling, and far less than anything a pilot would call "in the way".
     */
    public static final double CONTACT_TOLERANCE = 0.125D;

    /**
     * The inclusive block coordinates whose cells actually lie inside a box, rather than beside it.
     *
     * <p>The block at {@code b} occupies {@code [b, b+1]}. The old conversion was
     * {@code floor(min)..ceil(max)} inclusive, which is one block too many on every far side - a box
     * from 0 to 10 took in block 10, the one sitting against its face - and on the near side took in
     * the whole block below whenever {@code min} fell a hair under a whole number. Both are blocks
     * adjacent to the volume, and both were read as obstructions. Here a block counts only when it
     * reaches more than {@link #CONTACT_TOLERANCE} into the box.
     *
     * <p>May come out empty ({@code min > max} on an axis) for a box thinner than twice the tolerance,
     * which holds no block and is correctly clear.
     */
    public record BlockSpan(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        public static BlockSpan inside(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
            return new BlockSpan(first(minX), first(minY), first(minZ),
                    last(maxX), last(maxY), last(maxZ));
        }

        private static int first(double min) {
            return (int) Math.floor(min + CONTACT_TOLERANCE);
        }

        private static int last(double max) {
            return (int) Math.ceil(max - CONTACT_TOLERANCE) - 1;
        }
    }

    /**
     * Like {@link #scan}, but hands back <em>where</em> the first solid block was rather than just
     * that there was one. For reporting a collision to a pilot; the runtime clearance path uses
     * {@link #scan} and only needs the verdict.
     *
     * @return the first solid position, walked in the same order {@link #scan} uses, or {@code null}
     *         when nothing solid was found or the volume was too large to prove within the budget - a
     *         null is "no coordinate to name", never "proven clear"
     */
    @Nullable
    public static Hit firstSolid(int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ,
                                 long budget, Solid solid) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        if (budget <= 0L || volumeOf(minX, minY, minZ, maxX, maxY, maxZ) > budget) {
            return null;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (solid.at(x, y, z)) {
                        return new Hit(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Up to {@code cap} solid positions in the volume, walked in the same order {@link #scan} uses.
     *
     * <p>The plural of {@link #firstSolid}: for a visualiser that wants every block a hull would hit,
     * not just the first. The cap bounds the work and the result, so a wholly-solid volume returns the
     * first {@code cap} positions rather than all of them. Unlike {@link #scan} it takes no budget - a
     * caller collecting hits hands over one already-non-empty region at a time.
     */
    public static java.util.List<Hit> collectSolids(int minX, int minY, int minZ,
                                                    int maxX, int maxY, int maxZ,
                                                    int cap, Solid solid) {
        java.util.List<Hit> hits = new java.util.ArrayList<>();
        if (cap <= 0 || minX > maxX || minY > maxY || minZ > maxZ) {
            return hits;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (solid.at(x, y, z)) {
                        hits.add(new Hit(x, y, z));
                        if (hits.size() >= cap) {
                            return hits;
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * Visits every position in the volume, stopping at the first solid one.
     *
     * <p>Bounds are inclusive, which is what the callers have: a volume from {@code minY} to
     * {@code maxY} contains both of them.
     *
     * @param budget how many positions may be read before giving up; must be positive
     */
    public static Verdict scan(int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ,
                               long budget, Solid solid) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return Verdict.CLEAR;
        }
        if (budget <= 0L) {
            return Verdict.TOO_LARGE;
        }
        if (volumeOf(minX, minY, minZ, maxX, maxY, maxZ) > budget) {
            return Verdict.TOO_LARGE;
        }

        // Y outermost, then X, then Z. A hull usually meets the ground before anything else, so
        // walking up from the bottom finds the common obstruction on the first layer.
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (solid.at(x, y, z)) {
                        return Verdict.OBSTRUCTED;
                    }
                }
            }
        }
        return Verdict.CLEAR;
    }

    /**
     * How many positions a volume holds, saturating rather than overflowing.
     *
     * <p>A hull's swept bounds are doubles that have been floored and ceiled, and a nonsense pose
     * could produce a span wide enough to wrap a signed long. Saturating means a silly volume comes
     * out as "enormous" and is refused, rather than coming out negative and being waved through.
     */
    public static long volumeOf(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long spanX = (long) maxX - minX + 1L;
        long spanY = (long) maxY - minY + 1L;
        long spanZ = (long) maxZ - minZ + 1L;
        if (spanX <= 0L || spanY <= 0L || spanZ <= 0L) {
            return 0L;
        }
        long area = saturatingMultiply(spanX, spanZ);
        return saturatingMultiply(area, spanY);
    }

    private static long saturatingMultiply(long a, long b) {
        long result = a * b;
        if (a != 0L && (result / a != b || (a == -1L && b == Long.MIN_VALUE))) {
            return Long.MAX_VALUE;
        }
        return result;
    }
}
