package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a lane meets each of its two gates.
 *
 * <p>The near end is laid by Create and the far end by this mod, so the two must agree on the arithmetic a person
 * walking through already uses - a fraction of each opening and a turn of everything else. Get the handedness wrong
 * and the track out of a gate at right angles comes out of the wrong side of the opening: it looks right on a
 * square gate and is a mirror image on every other.
 */
class RailLaneMapTest {

    /** A gate lying along X, so a train passes through it along Z. */
    private static RiftGateShape acrossX(int x, int y, int z, int width, int height) {
        return new RiftGateShape(Direction.Axis.X, x, y, z, x + width - 1, y + height - 1, z);
    }

    /** A gate lying along Z, so a train passes through it along X. */
    private static RiftGateShape acrossZ(int x, int y, int z, int width, int height) {
        return new RiftGateShape(Direction.Axis.Z, x, y, z, x, y + height - 1, z + width - 1);
    }

    private static RailLaneMap.Near near(RiftGateShape shape, BlockPos track, Direction inward) {
        RailLaneMap.NearResult result = RailLaneMap.near(shape, track, inward);
        assertNull(result.refusal(), "refused " + track + " heading " + inward + ": " + result.refusal());
        return result.near();
    }

    // ----------------------------------------------------------------- near

    @Test
    void aTrackIntoTheBottomOfTheOpeningIsALane() {
        RiftGateShape gate = acrossX(0, 64, 0, 5, 5);
        RailLaneMap.Near lane = near(gate, new BlockPos(2, 64, -1), Direction.SOUTH);
        assertEquals(new BlockPos(2, 64, 0), lane.cell());
        assertEquals(2, lane.across());
        assertEquals(Direction.SOUTH, lane.heading());
    }

    @Test
    void aTrackAlongTheGateIsNot() {
        RiftGateShape gate = acrossX(0, 64, 0, 5, 5);
        assertEquals(RailLaneMap.Refusal.WRONG_AXIS,
                RailLaneMap.near(gate, new BlockPos(-1, 64, 0), Direction.EAST).refusal());
    }

    @Test
    void aTrackHalfWayUpTheOpeningIsNot() {
        RiftGateShape gate = acrossX(0, 64, 0, 5, 5);
        assertEquals(RailLaneMap.Refusal.NOT_BOTTOM_ROW,
                RailLaneMap.near(gate, new BlockPos(2, 65, -1), Direction.SOUTH).refusal());
    }

    @Test
    void aTrackIntoTheFrameIsNot() {
        RiftGateShape gate = acrossX(0, 64, 0, 5, 5);
        assertEquals(RailLaneMap.Refusal.NOT_IN_OPENING,
                RailLaneMap.near(gate, new BlockPos(7, 64, -1), Direction.SOUTH).refusal());
        assertEquals(RailLaneMap.Refusal.NOT_IN_OPENING,
                RailLaneMap.near(gate, new BlockPos(2, 63, -1), Direction.SOUTH).refusal());
    }

    /** A notch at the bottom of a flood-filled ring: the bottom of that column is the cell above the notch. */
    @Test
    void theBottomOfAColumnIsItsLowestOpenCell() {
        BitSet open = new BitSet();
        // 3 wide, 3 high; column 1 is closed at the bottom
        for (int up = 0; up < 3; up++) {
            for (int across = 0; across < 3; across++) {
                if (!(across == 1 && up == 0)) {
                    open.set(up * 3 + across);
                }
            }
        }
        RiftGateShape gate = new RiftGateShape(Direction.Axis.X, 0, 64, 0, 2, 66, 0, open);
        RailLaneMap.Near lane = near(gate, new BlockPos(1, 65, -1), Direction.SOUTH);
        assertEquals(new BlockPos(1, 65, 0), lane.cell());
    }

    // ------------------------------------------------------------------ far

    @Test
    void twoGatesTheSameWayRoundComeOutTheSameWayRound() {
        RiftGateShape from = acrossX(0, 64, 0, 5, 5);
        RiftGateShape to = acrossX(100, 70, 50, 5, 5);
        RailLaneMap.Far far = RailLaneMap.far(from, near(from, new BlockPos(1, 64, -1), Direction.SOUTH), to, 2);
        assertNotNull(far);
        assertEquals(new BlockPos(101, 70, 50), far.cell());
        assertEquals(Direction.SOUTH, far.heading());
        assertEquals(new BlockPos(101, 70, 51), far.stub());
        assertEquals(List.of(new BlockPos(101, 70, 52), new BlockPos(101, 70, 53)), far.apron());
    }

    @Test
    void aTrainComesOutOfTheSideItWasHeadingFor() {
        RiftGateShape from = acrossX(0, 64, 0, 5, 5);
        RiftGateShape to = acrossX(100, 70, 50, 5, 5);
        RailLaneMap.Far far = RailLaneMap.far(from, near(from, new BlockPos(2, 64, 1), Direction.NORTH), to, 0);
        assertNotNull(far);
        assertEquals(Direction.NORTH, far.heading());
        assertEquals(new BlockPos(102, 70, 49), far.stub());
        assertTrue(far.apron().isEmpty());
    }

    @Test
    void aGateAtRightAnglesTurnsTheTrackAQuarter() {
        RiftGateShape from = acrossX(0, 64, 0, 5, 5);
        RiftGateShape to = acrossZ(100, 70, 50, 5, 5);
        RailLaneMap.Far far = RailLaneMap.far(from, near(from, new BlockPos(2, 64, -1), Direction.SOUTH), to, 1);
        assertNotNull(far);
        assertEquals(Direction.EAST, far.heading(), "heading south through a gate across X is east out of one across Z");
        assertEquals(far.cell().east(), far.stub());
        assertEquals(new BlockPos(100, 70, 52), far.cell(), "the middle column maps to the middle column");
    }

    /**
     * The handedness, held as a round trip: a train backing out of the far stub through the far gate must arrive
     * at the column it went in by. Every column, both ways, parallel and at right angles.
     */
    @Test
    void goingBackLandsWhereItWentIn() {
        RiftGateShape from = acrossX(0, 64, 0, 5, 3);
        for (RiftGateShape to : List.of(acrossX(100, 70, 50, 5, 4), acrossZ(-40, 30, 20, 5, 3))) {
            for (Direction inward : List.of(Direction.SOUTH, Direction.NORTH)) {
                for (int across = 0; across < 5; across++) {
                    BlockPos track = new BlockPos(across, 64, 0).relative(inward.getOpposite());
                    RailLaneMap.Near going = near(from, track, inward);
                    RailLaneMap.Far there = RailLaneMap.far(from, going, to, 0);
                    assertNotNull(there);
                    RailLaneMap.Near coming = near(to, there.stub(), there.heading().getOpposite());
                    RailLaneMap.Far back = RailLaneMap.far(to, coming, from, 0);
                    assertNotNull(back);
                    assertEquals(going.cell(), back.cell(), "column " + across + " heading " + inward + " into " + to);
                    assertEquals(inward.getOpposite(), back.heading());
                    assertEquals(track, back.stub(), "the way back comes out onto the track it went in by");
                }
            }
        }
    }

    @Test
    void aNarrowGateSpreadsItsLanesAcrossAWideOne() {
        RiftGateShape from = acrossX(0, 64, 0, 3, 3);
        RiftGateShape to = acrossX(100, 64, 0, 5, 3);
        int[] expected = {100, 102, 104};
        for (int across = 0; across < 3; across++) {
            RailLaneMap.Far far = RailLaneMap.far(from,
                    near(from, new BlockPos(across, 64, -1), Direction.SOUTH), to, 0);
            assertNotNull(far);
            assertEquals(expected[across], far.cell().getX(), "column " + across);
        }
    }

    @Test
    void aColumnWithNoOpeningGivesWayToTheNearestThatHasOne() {
        BitSet open = new BitSet();
        // 3 wide, 2 high; column 0 is solid frame all the way up
        for (int up = 0; up < 2; up++) {
            open.set(up * 3 + 1);
            open.set(up * 3 + 2);
        }
        RiftGateShape to = new RiftGateShape(Direction.Axis.X, 100, 64, 0, 102, 65, 0, open);
        RiftGateShape from = acrossX(0, 64, 0, 3, 2);
        RailLaneMap.Far far = RailLaneMap.far(from, near(from, new BlockPos(0, 64, -1), Direction.SOUTH), to, 0);
        assertNotNull(far);
        assertEquals(new BlockPos(101, 64, 0), far.cell());
    }

    @Test
    void anOpeningWithNoCellsHasNoFarEnd() {
        RiftGateShape to = new RiftGateShape(Direction.Axis.X, 100, 64, 0, 102, 65, 0, new BitSet());
        RiftGateShape from = acrossX(0, 64, 0, 3, 2);
        assertNull(RailLaneMap.far(from, near(from, new BlockPos(0, 64, -1), Direction.SOUTH), to, 0));
    }

    @Test
    void aCellIsNamedTheSameWayItIsRead() {
        for (RiftGateShape shape : List.of(acrossX(5, 64, -3, 4, 3), acrossZ(5, 64, -3, 4, 3))) {
            for (int across = 0; across < shape.width(); across++) {
                BlockPos cell = RailLaneMap.cellAt(shape, across, 0);
                assertEquals(across, RailLaneMap.acrossOf(shape, cell));
                assertTrue(shape.cells().contains(cell));
            }
        }
    }
}
