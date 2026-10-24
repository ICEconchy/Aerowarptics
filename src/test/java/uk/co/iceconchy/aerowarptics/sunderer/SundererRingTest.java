package uk.co.iceconchy.aerowarptics.sunderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ring round a Sunderer: that it is the gate's ring with the Sunderer standing in it, that it is held to
 * the sizes a Sunderer tears, and that what the breach decides about it - where the tear hangs, what bursts,
 * what a hand takes - is the same every time for the same seed and never more than it should be.
 */
class SundererRingTest {

    private static final int Z = 20;

    /** A square ring of frame across X, its opening {@code size} on a side, its lower-left corner at the origin. */
    private static Set<BlockPos> ring(int size) {
        Set<BlockPos> frame = new HashSet<>();
        int last = size + 1;
        for (int i = 0; i <= last; i++) {
            frame.add(new BlockPos(i, 64, Z));
            frame.add(new BlockPos(i, 64 + last, Z));
            frame.add(new BlockPos(0, 64 + i, Z));
            frame.add(new BlockPos(last, 64 + i, Z));
        }
        return frame;
    }

    private static SundererRing.Ring find(Set<BlockPos> built, BlockPos sunderer) {
        Set<BlockPos> frame = new HashSet<>(built);
        frame.remove(sunderer);
        Predicate<BlockPos> isFrame = pos -> frame.contains(pos.immutable());
        Predicate<BlockPos> open = pos -> !built.contains(pos.immutable());
        return SundererRing.find(sunderer, isFrame, open);
    }

    @Test
    void aSundererStandingInARingFindsItsOpening() {
        Set<BlockPos> built = ring(5);
        SundererRing.Ring ring = find(built, new BlockPos(0, 67, Z));
        assertNotNull(ring);
        assertEquals(Direction.Axis.X, ring.shape().span());
        assertEquals(5, ring.shape().width());
        assertEquals(5, ring.shape().height());
        assertFalse(ring.frame().contains(new BlockPos(0, 67, Z)), "the Sunderer was counted as frame to burst");
        assertEquals(built.size() - 1, ring.frame().size(), "some of the frame was missed");
    }

    /** Where anybody would put it: in a corner, which only touches the opening across a diagonal. */
    @Test
    void aSundererInACornerStillFindsIt() {
        for (BlockPos corner : List.of(new BlockPos(0, 64, Z), new BlockPos(6, 70, Z), new BlockPos(0, 70, Z))) {
            assertNotNull(find(ring(5), corner), "no ring from the corner at " + corner);
        }
    }

    @Test
    void aRingWithAGapIsNoRing() {
        Set<BlockPos> built = ring(5);
        built.remove(new BlockPos(3, 70, Z));
        assertNull(find(built, new BlockPos(0, 67, Z)));
    }

    @Test
    void theSizesASundererTears() {
        for (int size = 2; size <= 8; size++) {
            SundererRing.Ring ring = find(ring(size), new BlockPos(0, 65, Z));
            assertNotNull(ring, "no ring of " + size);
            SundererFailure expected = size < 3 ? SundererFailure.RING_TOO_SMALL
                    : size > 7 ? SundererFailure.RING_TOO_LARGE : SundererFailure.NONE;
            assertEquals(expected, SundererRing.size(ring.shape(), 3, 7), "a ring of " + size);
        }
    }

    @Test
    void theTearHangsInsideTheOpening() {
        for (int size = 3; size <= 7; size++) {
            SundererRing.Ring ring = find(ring(size), new BlockPos(0, 65, Z));
            BlockPos centre = ring.centre();
            assertTrue(ring.shape().cells().contains(centre), "the tear would hang outside a ring of " + size);
        }
        assertEquals(new BlockPos(3, 67, Z), find(ring(5), new BlockPos(0, 65, Z)).centre());
    }

    @Test
    void aboutAThirdBurstsAndNeverAllOfIt() {
        SundererRing.Ring ring = find(ring(5), new BlockPos(0, 67, Z));
        int frame = ring.frame().size();
        for (long seed = 0; seed < 200; seed++) {
            List<BlockPos> shattering = SundererRing.shatter(ring, 0.3D, seed);
            assertEquals(Math.round(frame * 0.3D), shattering.size(), "seed " + seed);
            assertEquals(shattering.size(), new HashSet<>(shattering).size(), "a block burst twice, seed " + seed);
            assertTrue(ring.frame().containsAll(shattering), "something not of the frame burst, seed " + seed);
        }
        assertEquals(frame - 1, SundererRing.shatter(ring, 1.0D, 7L).size(), "all of the ring burst");
        assertEquals(1, SundererRing.shatter(ring, 0.0D, 7L).size(), "none of the ring burst");
    }

    @Test
    void theSameSeedBurstsTheSameBlocks() {
        SundererRing.Ring ring = find(ring(6), new BlockPos(0, 67, Z));
        assertEquals(SundererRing.shatter(ring, 0.3D, 42L), SundererRing.shatter(ring, 0.3D, 42L));
        assertEquals(SundererRing.grip(ring, List.of(), 42L), SundererRing.grip(ring, List.of(), 42L));
    }

    @Test
    void aHandTakesFrameThatIsNotBurstingAnyway() {
        SundererRing.Ring ring = find(ring(5), new BlockPos(0, 67, Z));
        for (long seed = 0; seed < 200; seed++) {
            List<BlockPos> shattering = SundererRing.shatter(ring, 0.3D, seed);
            BlockPos grip = SundererRing.grip(ring, shattering, seed);
            assertNotNull(grip);
            assertTrue(ring.frame().contains(grip), "a hand took something not of the frame");
            assertFalse(shattering.contains(grip), "a hand took a block that was bursting");
        }
    }
}
