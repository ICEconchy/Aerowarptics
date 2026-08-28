package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Astrolabe's formation rules, tested as what they are: arithmetic over block positions.
 *
 * <p>Worth testing in isolation because the failure mode is subtle and only shows up in the world -
 * a table that forms differently depending on which corner you laid last is not obviously broken, it
 * just quietly puts the chart in the wrong place.
 *
 * <p>Since a table may be one, two or three blocks a side, the rule that matters most here is that
 * the <em>largest</em> one wins. A slab of nine blocks that formed as a one-by-one and some spare
 * parts would be a working table, and would be wrong every time.
 */
class AstrolabeStructureTest {

    private static Predicate<BlockPos> occupying(Set<BlockPos> cells) {
        return pos -> cells.contains(pos.immutable());
    }

    private static Set<BlockPos> square(BlockPos origin, int size) {
        return new HashSet<>(AstrolabeStructure.cells(origin, size));
    }

    @Test
    void aTableIsAFlatSquareOfItsOwnSize() {
        assertEquals(1, AstrolabeStructure.cells(BlockPos.ZERO, 1).size());
        assertEquals(4, AstrolabeStructure.cells(BlockPos.ZERO, 2).size());
        assertEquals(9, AstrolabeStructure.cells(BlockPos.ZERO, 3).size());
        // Anchored at the lowest corner, so the origin is a cell rather than a midpoint.
        assertTrue(AstrolabeStructure.cells(BlockPos.ZERO, 3).contains(BlockPos.ZERO));
        assertTrue(AstrolabeStructure.cells(BlockPos.ZERO, 3).contains(new BlockPos(2, 0, 2)));
        assertFalse(AstrolabeStructure.cells(BlockPos.ZERO, 3).contains(new BlockPos(-1, 0, 0)));
        // Flat: one block deep, so nothing above or below is part of it.
        assertFalse(AstrolabeStructure.cells(BlockPos.ZERO, 3).contains(new BlockPos(0, 1, 0)));
    }

    @Test
    void thereIsNoSuchThingAsAFourBlockTable() {
        assertThrows(IllegalArgumentException.class, () -> new Table(BlockPos.ZERO, 4));
        assertThrows(IllegalArgumentException.class, () -> new Table(BlockPos.ZERO, 0));
    }

    @Test
    void aLoneBlockIsAWorkingTable() {
        Set<BlockPos> cells = Set.of(BlockPos.ZERO);
        Table formed = AstrolabeStructure.findBest(BlockPos.ZERO, occupying(cells));
        assertNotNull(formed, "a single astrolabe must be a table on its own");
        assertEquals(1, formed.size());
        assertEquals(BlockPos.ZERO, formed.origin());
    }

    @Test
    void twoBlocksAreStillOnlyOneTableEach() {
        // A domino is not a square, so the best either block can manage is a one-by-one.
        Set<BlockPos> cells = Set.of(BlockPos.ZERO, new BlockPos(1, 0, 0));
        assertEquals(1, AstrolabeStructure.findBest(BlockPos.ZERO, occupying(cells)).size());
    }

    @Test
    void aFullSquareFormsAtItsLargestSize() {
        for (int size = 1; size <= AstrolabeStructure.MAX_SIZE; size++) {
            BlockPos origin = new BlockPos(40, 71, -12);
            Set<BlockPos> cells = square(origin, size);
            // Found from any cell, not only from the corner - a player finishes a table at whichever
            // cell happens to be last.
            for (BlockPos member : cells) {
                Table formed = AstrolabeStructure.findBest(member, occupying(cells));
                assertNotNull(formed, "nothing formed from " + member);
                assertEquals(size, formed.size(), "wrong size forming from " + member);
                assertEquals(origin, formed.origin(), "wrong origin forming from " + member);
            }
        }
    }

    @Test
    void anIncompleteSquareFallsBackRatherThanFormingNothing() {
        // Three of a two-by-two's four cells. The odd one out cannot make a two-by-two, but every
        // one of them is still a perfectly good table on its own.
        Set<BlockPos> cells = new HashSet<>(square(BlockPos.ZERO, 2));
        cells.remove(new BlockPos(1, 0, 1));
        Table formed = AstrolabeStructure.findBest(BlockPos.ZERO, occupying(cells));
        assertNotNull(formed);
        assertEquals(1, formed.size());
    }

    @Test
    void nothingFormsWhereThereIsNothing() {
        assertNull(AstrolabeStructure.findBest(BlockPos.ZERO, pos -> false));
    }

    @Test
    void aSquareOneBlockUpIsNotPartOfTheTable() {
        BlockPos origin = BlockPos.ZERO;
        Set<BlockPos> cells = square(origin, 3);
        cells.addAll(square(origin.above(), 3));
        // The lower square is still a three-by-three; the upper one being there must not confuse it
        // into forming something four blocks tall.
        Table formed = AstrolabeStructure.findBest(origin, occupying(cells));
        assertEquals(3, formed.size());
        assertEquals(origin, formed.origin());
    }

    /**
     * The reason ties are broken by coordinate rather than by search order.
     *
     * <p>A four-by-four slab contains four complete three-by-threes, and a cell of it sits inside
     * more than one of them. Which table forms is settled by coordinate, so laying the same slab out
     * twice gives the same table both times - a hash-order tie-break would move the chart between
     * builds for no reason a player could see.
     */
    @Test
    void anOversizedSlabFormsTheSameTableEveryTime() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                cells.add(new BlockPos(x, 64, z));
            }
        }
        BlockPos member = new BlockPos(1, 64, 1);
        Table first = AstrolabeStructure.findBest(member, occupying(cells));
        assertEquals(3, first.size(), "a four-by-four slab holds a three-by-three");
        for (int repeat = 0; repeat < 8; repeat++) {
            assertEquals(first, AstrolabeStructure.findBest(member, occupying(cells)),
                    "the same layout formed differently on attempt " + repeat);
        }
    }

    /** Whatever forms is a complete square containing the block it formed from. */
    @Test
    void whateverFormsIsCompleteAndContainsItsMember() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                cells.add(new BlockPos(x, 64, z));
            }
        }
        for (BlockPos member : cells) {
            Table found = AstrolabeStructure.findBest(member, occupying(cells));
            assertNotNull(found, "nothing formed at " + member);
            assertTrue(AstrolabeStructure.isComplete(found, occupying(cells)),
                    "formed an incomplete table at " + found);
            assertTrue(found.covers(member), found + " does not actually contain " + member);
        }
    }

    @Test
    void coverageMatchesTheCellsOfATable() {
        for (int size = 1; size <= AstrolabeStructure.MAX_SIZE; size++) {
            Table table = new Table(new BlockPos(5, 5, 5), size);
            for (BlockPos cell : table.cells()) {
                assertTrue(table.covers(cell));
            }
            assertFalse(table.covers(new BlockPos(5 - 1, 5, 5)), "reaches below its own origin");
            assertFalse(table.covers(new BlockPos(5 + size, 5, 5)), "reaches past its own size");
            assertFalse(table.covers(new BlockPos(5, 6, 5)), "reaches upward");
        }
    }

    /**
     * The containment test that lets a table grow.
     *
     * <p>A lone block forms as a one-by-one immediately, so building up to a bigger table means the
     * bigger one has to be allowed to swallow it. What must stay refused is swallowing a table that
     * pokes outside - that would be dismantling somebody's working chart to build your own.
     */
    @Test
    void aSmallerTableIsAbsorbedOnlyWhenItLiesWhollyInside() {
        Table big = new Table(BlockPos.ZERO, 3);
        assertTrue(new Table(BlockPos.ZERO, 1).isInside(big));
        assertTrue(new Table(new BlockPos(2, 0, 2), 1).isInside(big));
        assertTrue(new Table(new BlockPos(1, 0, 1), 2).isInside(big));
        assertTrue(big.isInside(big), "a table contains itself");

        assertFalse(new Table(new BlockPos(2, 0, 2), 2).isInside(big), "overhangs the far corner");
        assertFalse(new Table(new BlockPos(-1, 0, 0), 1).isInside(big), "sits outside entirely");
        assertFalse(new Table(BlockPos.ZERO.above(), 1).isInside(big), "is on another layer");
    }

    /** The window a placement or a break has to re-examine really does cover every table involved. */
    @Test
    void theNeighbourhoodReachesEveryTableACellCouldBeIn() {
        BlockPos member = new BlockPos(9, 64, 9);
        Set<BlockPos> around = new HashSet<>(AstrolabeStructure.neighbourhood(member));
        for (Table candidate : AstrolabeStructure.candidates(member)) {
            assertTrue(around.contains(candidate.origin()),
                    "a table at " + candidate.origin() + " would be missed when " + member + " changes");
            for (BlockPos cell : candidate.cells()) {
                assertTrue(around.contains(cell), "cell " + cell + " would be missed");
            }
        }
    }

    /** Candidates are offered largest first, so the greedy search cannot settle for a small table. */
    @Test
    void candidatesAreOfferedLargestFirst()  {
        int previous = Integer.MAX_VALUE;
        for (Table candidate : AstrolabeStructure.candidates(BlockPos.ZERO)) {
            assertTrue(candidate.size() <= previous, "candidate sizes are not descending");
            previous = candidate.size();
            assertTrue(candidate.covers(BlockPos.ZERO), "offered a table that does not contain the member");
        }
        assertEquals(AstrolabeStructure.MIN_SIZE, previous, "the smallest table is never offered");
    }
}
