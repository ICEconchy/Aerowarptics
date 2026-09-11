package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.ObstructionScan;
import uk.co.iceconchy.aerowarptics.warp.ObstructionScan.Hit;
import uk.co.iceconchy.aerowarptics.warp.ObstructionScan.Verdict;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That nothing solid can hide inside a volume a hull is about to occupy.
 *
 * <p>This is the test the old check could not have passed. It sampled on a stride sized to keep a
 * flying city as cheap to test as a skiff, which made a one-block floor invisible to a modest ship
 * and an eight-block wall invisible to a large one - so a warp was cleared and the hull materialised
 * inside a building. Terrain was caught only because terrain is thick.
 *
 * <p>The sweep below is the point of the whole class: a single solid block, at <em>every</em>
 * position in a sizeable volume, one at a time. A scan that steps over anything at all fails it.
 */
class ObstructionScanTest {

    private static final long PLENTY = 10_000_000L;

    /** Nothing anywhere. */
    private static final ObstructionScan.Solid EMPTY = (x, y, z) -> false;

    /** Exactly one solid block, at a named position. */
    private static ObstructionScan.Solid only(int sx, int sy, int sz) {
        return (x, y, z) -> x == sx && y == sy && z == sz;
    }

    @Test
    void anEmptyVolumeIsClear() {
        assertEquals(Verdict.CLEAR,
                ObstructionScan.scan(0, 0, 0, 31, 15, 31, PLENTY, EMPTY));
    }

    /**
     * A single block anywhere in the volume is found.
     *
     * <p>Every position, not a sample of them. This is the assertion that the old stride sampler
     * fails at any hull big enough to earn a stride above one.
     */
    @Test
    void aSingleBlockIsFoundWhereverItIs() {
        int minX = -3;
        int minY = 60;
        int minZ = 7;
        int maxX = 12;
        int maxY = 75;
        int maxZ = 22;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Verdict verdict = ObstructionScan.scan(minX, minY, minZ, maxX, maxY, maxZ,
                            PLENTY, only(x, y, z));
                    assertEquals(Verdict.OBSTRUCTED, verdict,
                            "a block at " + x + "," + y + "," + z + " was stepped over");
                }
            }
        }
    }

    /**
     * A one-block-thick floor is found at every height it could sit at.
     *
     * <p>The shape that actually bit: a ship cleared to arrive inside a building, because the stride
     * happened to step across the floor. A wall or a roof is the same test rotated.
     */
    @Test
    void aOneBlockFloorIsNeverMissed() {
        for (int floor = 0; floor <= 40; floor++) {
            int at = floor;
            assertEquals(Verdict.OBSTRUCTED,
                    ObstructionScan.scan(0, 0, 0, 40, 40, 40, PLENTY, (x, y, z) -> y == at),
                    "a floor at y=" + at + " was stepped over");
        }
    }

    /** And the same for a wall at every offset along each horizontal axis. */
    @Test
    void aOneBlockWallIsNeverMissed() {
        for (int wall = 0; wall <= 40; wall++) {
            int at = wall;
            assertEquals(Verdict.OBSTRUCTED,
                    ObstructionScan.scan(0, 0, 0, 40, 40, 40, PLENTY, (x, y, z) -> x == at),
                    "a wall at x=" + at + " was stepped over");
            assertEquals(Verdict.OBSTRUCTED,
                    ObstructionScan.scan(0, 0, 0, 40, 40, 40, PLENTY, (x, y, z) -> z == at),
                    "a wall at z=" + at + " was stepped over");
        }
    }

    /** The scan stops at the first thing it finds rather than reading the whole volume. */
    @Test
    void theScanStopsAtTheFirstObstruction() {
        int[] reads = {0};
        ObstructionScan.Solid counting = (x, y, z) -> {
            reads[0]++;
            return true;
        };
        assertEquals(Verdict.OBSTRUCTED,
                ObstructionScan.scan(0, 0, 0, 63, 63, 63, PLENTY, counting));
        assertEquals(1, reads[0], "the scan carried on after finding something");
    }

    /**
     * A volume too big to prove clear is refused, never waved through.
     *
     * <p>The whole difference from the budget this replaced. That one made the check coarser when it
     * ran out; this one declines to answer, and the caller treats an unproven volume as obstructed.
     */
    @Test
    void anUnprovableVolumeIsRefusedRatherThanPassed() {
        Verdict verdict = ObstructionScan.scan(0, 0, 0, 999, 999, 999, 4096L, EMPTY);
        assertEquals(Verdict.TOO_LARGE, verdict);
        assertNotEquals(Verdict.CLEAR, verdict, "an unprovable volume was reported clear");
    }

    /** A budget that exactly covers the volume is enough; one block short is not. */
    @Test
    void theBudgetIsCountedInPositions() {
        long exact = ObstructionScan.volumeOf(0, 0, 0, 9, 9, 9);
        assertEquals(1000L, exact);
        assertEquals(Verdict.CLEAR, ObstructionScan.scan(0, 0, 0, 9, 9, 9, exact, EMPTY));
        assertEquals(Verdict.TOO_LARGE, ObstructionScan.scan(0, 0, 0, 9, 9, 9, exact - 1, EMPTY));
    }

    /**
     * {@code firstSolid} names the exact block, wherever it sits.
     *
     * <p>The reporting counterpart to {@link Verdict#OBSTRUCTED}: the dry run quotes this coordinate
     * to the pilot, so it has to be the block itself, not just "something is in here".
     */
    @Test
    void firstSolidNamesTheBlockWhereverItIs() {
        int minX = -3;
        int minY = 60;
        int minZ = 7;
        int maxX = 12;
        int maxY = 75;
        int maxZ = 22;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Hit hit = ObstructionScan.firstSolid(minX, minY, minZ, maxX, maxY, maxZ,
                            PLENTY, only(x, y, z));
                    assertEquals(new Hit(x, y, z), hit,
                            "the block at " + x + "," + y + "," + z + " was not named");
                }
            }
        }
    }

    /**
     * {@code firstSolid} stops on the same block {@code scan} does.
     *
     * <p>They walk the same order, so a reported collision is genuinely the first one the clearance
     * check would have hit - not some other block deeper in the volume.
     */
    @Test
    void firstSolidAgreesWithScanOnWhichBlockIsFirst() {
        // Two solid blocks; the one scanned first (lowest y, then x, then z) is the one to report.
        Hit lower = new Hit(5, 61, 9);
        Hit higher = new Hit(2, 70, 4);
        ObstructionScan.Solid both = (x, y, z) ->
                (x == lower.x() && y == lower.y() && z == lower.z())
                        || (x == higher.x() && y == higher.y() && z == higher.z());
        assertEquals(lower, ObstructionScan.firstSolid(-3, 60, 0, 12, 75, 22, PLENTY, both));
    }

    /** Nothing solid: no coordinate to name. */
    @Test
    void firstSolidIsNullWhenTheVolumeIsClear() {
        assertNull(ObstructionScan.firstSolid(0, 0, 0, 31, 15, 31, PLENTY, EMPTY));
    }

    /**
     * A volume too large to prove yields no coordinate, exactly as {@link Verdict#TOO_LARGE} yields
     * no answer. Null here is "no block to name", never "clear".
     */
    @Test
    void firstSolidIsNullWhenTheVolumeIsTooLargeToProve() {
        assertNull(ObstructionScan.firstSolid(0, 0, 0, 999, 999, 999, 4096L, (x, y, z) -> true));
    }

    /**
     * {@code collectSolids} gathers every solid block, in the same order {@code scan} walks.
     *
     * <p>The plural of {@code firstSolid}, for the clearance visualiser that marks all the blocks a
     * departure would hit. Ordering matters: the list a pilot sees should start with the block the
     * check itself would stop on.
     */
    @Test
    void collectSolidsGathersEveryBlockLowestFirst() {
        ObstructionScan.Solid three = (x, y, z) ->
                (x == 2 && y == 60 && z == 4)
                        || (x == 5 && y == 61 && z == 9)
                        || (x == 1 && y == 61 && z == 2);
        List<Hit> hits = ObstructionScan.collectSolids(-3, 60, 0, 12, 75, 22, 10, three);
        assertEquals(3, hits.size());
        assertEquals(new Hit(2, 60, 4), hits.get(0), "the lowest block should be reported first");
    }

    /** The cap bounds the result: a wholly-solid volume yields the first {@code cap} positions. */
    @Test
    void collectSolidsStopsAtTheCap() {
        List<Hit> hits = ObstructionScan.collectSolids(0, 0, 0, 9, 9, 9, 5, (x, y, z) -> true);
        assertEquals(5, hits.size());
    }

    /** With a cap of one it agrees with {@code firstSolid}. */
    @Test
    void collectSolidsAtCapOneMatchesFirstSolid() {
        ObstructionScan.Solid two = (x, y, z) ->
                (x == 5 && y == 61 && z == 9) || (x == 2 && y == 70 && z == 4);
        List<Hit> hits = ObstructionScan.collectSolids(-3, 60, 0, 12, 75, 22, 1, two);
        assertEquals(1, hits.size());
        assertEquals(ObstructionScan.firstSolid(-3, 60, 0, 12, 75, 22, PLENTY, two), hits.get(0));
    }

    /** Nothing solid, nothing collected. */
    @Test
    void collectSolidsIsEmptyWhenTheVolumeIsClear() {
        assertTrue(ObstructionScan.collectSolids(0, 0, 0, 15, 15, 15, 100, EMPTY).isEmpty());
    }

    /** An inverted or empty box holds nothing and is trivially clear. */
    @Test
    void anInvertedBoxIsClear() {
        assertEquals(Verdict.CLEAR, ObstructionScan.scan(5, 0, 0, 4, 0, 0, PLENTY, (x, y, z) -> true));
        assertEquals(0L, ObstructionScan.volumeOf(5, 0, 0, 4, 0, 0));
    }

    /** A single block is a volume of one, not of zero. */
    @Test
    void aSingleCellVolumeIsScanned() {
        assertEquals(1L, ObstructionScan.volumeOf(3, 4, 5, 3, 4, 5));
        assertEquals(Verdict.OBSTRUCTED,
                ObstructionScan.scan(3, 4, 5, 3, 4, 5, PLENTY, only(3, 4, 5)));
    }

    /**
     * A span wide enough to overflow a long comes out enormous, not negative.
     *
     * <p>Bounds arrive as floored doubles from a hull's swept pose. A nonsense pose that wrapped the
     * multiplication would produce a negative volume, slip under the budget and be reported clear -
     * the one way this class could still wave a ship into a wall.
     */
    @Test
    void anAbsurdVolumeSaturatesRatherThanWrapping() {
        long volume = ObstructionScan.volumeOf(
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertTrue(volume > 0L, "an absurd volume wrapped to " + volume);
        assertEquals(Verdict.TOO_LARGE,
                ObstructionScan.scan(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, PLENTY, EMPTY));
    }
}
