package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.gate.GateTraversal;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a lane's track meets each of its two gates.
 *
 * <p>Pure: shapes and block positions in, block positions out, no level. A lane is laid by Create at the near
 * end - a player runs straight track up to a cell of the opening and Create asks this mod where the far side of
 * that "portal" is - and by this mod at the far end, so the two have to agree on which cell of the far gate a
 * train comes out of, which way it is facing, and where the stub of track it comes out onto stands.
 *
 * <p>They agree by doing what a person walking through does: {@link GateTraversal#map} carries a traveller across
 * as a fraction of each opening and a turn of everything else, with the handedness fixed by
 * {@link GateTraversal#spanSign}. A lane is the same crossing pinned to whole blocks - the column it enters is
 * carried across as a fraction, and it leaves on the bottom of that column on the side the train was heading.
 */
public final class RailLaneMap {

    private RailLaneMap() {
    }

    /** Why a track cannot be a lane. */
    public enum Refusal {
        /** It does not run through the gate - it runs along it. */
        WRONG_AXIS,
        /** The cell it points into is not part of the opening. */
        NOT_IN_OPENING,
        /** There is opening below the cell it points into: a lane has to run along the bottom. */
        NOT_BOTTOM_ROW
    }

    /**
     * The near end of a lane: where a track meets an opening.
     *
     * @param cell    the cell of the opening the track points into
     * @param across  that cell's column, counted along the gate's span from its low end
     * @param heading which way a train on the track is going as it enters
     */
    public record Near(BlockPos cell, int across, Direction heading) {
    }

    /**
     * The far end of a lane.
     *
     * @param cell    the far gate's cell a train comes out of
     * @param heading which way it is going as it comes out
     * @param stub    where the portal track bound to the corridor stands, just outside that cell
     * @param apron   straight track laid on from the stub, for players to build from
     */
    public record Far(BlockPos cell, Direction heading, BlockPos stub, List<BlockPos> apron) {
    }

    /** A lane's near end, or why it cannot have one. Exactly one of the two is set. */
    public record NearResult(@Nullable Near near, @Nullable Refusal refusal) {
    }

    /**
     * Works out a lane from a track Create has found pointing into an opening.
     *
     * @param track        where the track stands, outside the opening
     * @param towardPortal the way from the track into the opening, which is the way a train on it enters
     */
    public static NearResult near(RiftGateShape shape, BlockPos track, Direction towardPortal) {
        if (towardPortal.getAxis() != shape.normal()) {
            return new NearResult(null, Refusal.WRONG_AXIS);
        }
        BlockPos cell = track.relative(towardPortal);
        int across = acrossOf(shape, cell);
        int up = cell.getY() - shape.minY();
        if (!onPlane(shape, cell) || !shape.openAt(across, up)) {
            return new NearResult(null, Refusal.NOT_IN_OPENING);
        }
        if (shape.openAt(across, up - 1)) {
            return new NearResult(null, Refusal.NOT_BOTTOM_ROW);
        }
        return new NearResult(new Near(cell.immutable(), across, towardPortal), null);
    }

    /**
     * Where a lane entering {@code from} at {@code near} comes out of {@code to}.
     *
     * @return the far end, or {@code null} if {@code to} has no opening at all
     */
    @Nullable
    public static Far far(RiftGateShape from, Near near, RiftGateShape to, int apronLength) {
        double fraction = from.acrossFraction(near.cell().getCenter()) * GateTraversal.spanSign(from, to);
        int wanted = (int) Math.floor(fraction * to.halfWidth() + to.halfWidth());
        wanted = Math.max(0, Math.min(to.width() - 1, wanted));
        int column = nearestColumnWithOpening(to, wanted);
        if (column < 0) {
            return null;
        }
        int up = lowestOpen(to, column);
        BlockPos cell = cellAt(to, column, up);

        // The side a train comes out of mirrors the side it went in through, as a walker's does: heading
        // along the positive normal of one gate is heading along the positive normal of the other.
        Direction heading = Direction.fromAxisAndDirection(to.normal(), near.heading().getAxisDirection());
        BlockPos stub = cell.relative(heading);
        List<BlockPos> apron = new ArrayList<>(Math.max(0, apronLength));
        for (int step = 1; step <= apronLength; step++) {
            apron.add(stub.relative(heading, step));
        }
        return new Far(cell, heading, stub, List.copyOf(apron));
    }

    /** The cell of an opening a column and a height name. */
    public static BlockPos cellAt(RiftGateShape shape, int across, int up) {
        return shape.span() == Direction.Axis.X
                ? new BlockPos(shape.minX() + across, shape.minY() + up, shape.minZ())
                : new BlockPos(shape.minX(), shape.minY() + up, shape.minZ() + across);
    }

    /** Which column of the opening a block is in. */
    public static int acrossOf(RiftGateShape shape, BlockPos pos) {
        return shape.span() == Direction.Axis.X ? pos.getX() - shape.minX() : pos.getZ() - shape.minZ();
    }

    /** Whether a block is in the plane of the opening at all - the thin axis, not the mask. */
    private static boolean onPlane(RiftGateShape shape, BlockPos pos) {
        return shape.span() == Direction.Axis.X ? pos.getZ() == shape.minZ() : pos.getX() == shape.minX();
    }

    /** The lowest open cell of a column, or {@code -1} if it has none. */
    public static int lowestOpen(RiftGateShape shape, int across) {
        for (int up = 0; up < shape.height(); up++) {
            if (shape.openAt(across, up)) {
                return up;
            }
        }
        return -1;
    }

    /**
     * The column nearest to the one wanted that has any opening in it, preferring the lower side on a tie.
     *
     * <p>A flood-filled ring is often not a rectangle, so the column a lane maps onto can be solid frame all the
     * way up - a notch. The nearest open column is where a person crossing at that point would have been put.
     */
    public static int nearestColumnWithOpening(RiftGateShape shape, int wanted) {
        for (int distance = 0; distance < shape.width(); distance++) {
            for (int candidate : new int[] {wanted - distance, wanted + distance}) {
                if (candidate >= 0 && candidate < shape.width() && lowestOpen(shape, candidate) >= 0) {
                    return candidate;
                }
            }
        }
        return -1;
    }
}
