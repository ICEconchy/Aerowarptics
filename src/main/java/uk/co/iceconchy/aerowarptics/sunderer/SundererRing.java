package uk.co.iceconchy.aerowarptics.sunderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.gate.RiftGateStructure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The ring a Rift Sunderer stands in, and the decisions made about it when it tears: where the fissure hangs,
 * which of the frame bursts, and which block a hand takes.
 *
 * <p>The opening is the gate's own - {@link RiftGateStructure#find}, flood filled inward and bounded by frame -
 * with the Sunderer counted as frame, standing where a gate's controller would. It is the gate a scar's ruin
 * shows, built to go wrong: a ring of frame with a fissure hanging where the aperture should have been.
 *
 * <p>Arithmetic over positions with predicates for the world, like the gate's structure, so it can be held to
 * what it promises without a world to run in. Every choice is rolled from a seed the Sunderer saves, so a
 * sundering that resumes after a restart tears the ring it meant to.
 */
public final class SundererRing {

    private SundererRing() {
    }

    /** A ring that formed: its opening, and the frame round it, the Sunderer not included. */
    public record Ring(RiftGateShape shape, BlockPos sunderer, List<BlockPos> frame) {

        /** The cell the fissure hangs in: the opening's cell nearest its middle. */
        public BlockPos centre() {
            return SundererRing.centre(shape);
        }
    }

    /**
     * Finds the ring round a Sunderer.
     *
     * @param sunderer where the Sunderer stands
     * @param frame    whether a position holds Rift Gate Frame - not the Sunderer, which is added here
     * @param open     whether a position is clear enough to be part of an opening
     * @return the ring, or null where there is no closed ring for it to be part of
     */
    @Nullable
    public static Ring find(BlockPos sunderer, Predicate<BlockPos> frame, Predicate<BlockPos> open) {
        BlockPos at = sunderer.immutable();
        Predicate<BlockPos> wall = pos -> pos.equals(at) || frame.test(pos);
        RiftGateStructure.Opening opening = RiftGateStructure.find(at, wall, open);
        if (opening == null) {
            return null;
        }
        return new Ring(opening.shape(), at, frameRound(opening.shape(), at, frame));
    }

    /** Whether an opening this size may be torn: {@link SundererFailure#NONE} where it may. */
    public static SundererFailure size(RiftGateShape shape, int least, int most) {
        if (shape.width() < least || shape.height() < least) {
            return SundererFailure.RING_TOO_SMALL;
        }
        if (shape.width() > most || shape.height() > most) {
            return SundererFailure.RING_TOO_LARGE;
        }
        return SundererFailure.NONE;
    }

    /**
     * Every block of frame that holds the opening: the frame touching an open cell, corners included, in a fixed
     * order (by height, then along the plane) so a seed picks the same blocks every time.
     */
    static List<BlockPos> frameRound(RiftGateShape shape, BlockPos sunderer, Predicate<BlockPos> frame) {
        Set<BlockPos> found = new LinkedHashSet<>();
        for (BlockPos cell : shape.cells()) {
            for (int across = -1; across <= 1; across++) {
                for (int up = -1; up <= 1; up++) {
                    BlockPos next = shape.span() == Direction.Axis.X
                            ? cell.offset(across, up, 0)
                            : cell.offset(0, up, across);
                    if (!next.equals(sunderer) && frame.test(next)) {
                        found.add(next.immutable());
                    }
                }
            }
        }
        List<BlockPos> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return ordered;
    }

    /**
     * The opening's cell nearest the middle of its box. On an L-shaped ring the box's middle can be outside the
     * opening altogether; the nearest open cell is never outside it.
     */
    public static BlockPos centre(RiftGateShape shape) {
        Vec3 middle = shape.centre();
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos cell : shape.cells()) {
            double distance = middle.distanceToSqr(cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D);
            if (distance < nearest - 1.0e-9D) {
                nearest = distance;
                best = cell;
            }
        }
        return best;
    }

    /**
     * Which of the frame bursts as the ring tears: about {@code share} of it, never none and never all of it,
     * as one to three unbroken runs round the ring - the broken arcs a scar's ruin has, not a scatter.
     */
    public static List<BlockPos> shatter(Ring ring, double share, long seed) {
        List<BlockPos> round = aroundTheRing(ring);
        int count = round.size();
        if (count < 2) {
            return List.of();
        }
        int taking = (int) Math.round(count * Math.max(0.0D, Math.min(1.0D, share)));
        taking = Math.max(1, Math.min(count - 1, taking));
        int runs = Math.max(1, Math.min(3, taking / 3));
        Random random = new Random(seed);
        int first = random.nextInt(count);
        List<BlockPos> taken = new ArrayList<>(taking);
        int left = taking;
        for (int run = 0; run < runs; run++) {
            int length = left / (runs - run);
            int start = first + run * count / runs;
            for (int step = 0; step < length; step++) {
                taken.add(round.get(Math.floorMod(start + step, count)));
            }
            left -= length;
        }
        return taken;
    }

    /**
     * The block a hand takes back with it: one of the frame that is not bursting anyway, or null when the
     * breach would take all of it.
     */
    @Nullable
    public static BlockPos grip(Ring ring, List<BlockPos> shattering, long seed) {
        List<BlockPos> left = new ArrayList<>(ring.frame());
        left.removeAll(shattering);
        if (left.isEmpty()) {
            return null;
        }
        return left.get(new Random(seed ^ 0x6A09E667F3BCC909L).nextInt(left.size()));
    }

    /** The frame ordered by its angle round the opening's middle, so a run of it is an arc. */
    static List<BlockPos> aroundTheRing(Ring ring) {
        Vec3 middle = ring.shape().centre();
        boolean acrossX = ring.shape().span() == Direction.Axis.X;
        List<BlockPos> round = new ArrayList<>(ring.frame());
        round.sort(Comparator.comparingDouble(pos -> {
            double across = (acrossX ? pos.getX() + 0.5D - middle.x : pos.getZ() + 0.5D - middle.z);
            double up = pos.getY() + 0.5D - middle.y;
            return Math.atan2(up, across);
        }));
        return round;
    }
}
