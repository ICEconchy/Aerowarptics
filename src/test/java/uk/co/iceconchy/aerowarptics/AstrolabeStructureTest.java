package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Astrolabe's formation rules, tested as what they are: arithmetic over block positions.
 *
 * <p>Worth testing in isolation because the failure mode is subtle and only shows up in the world -
 * a table that forms differently depending on which corner you laid last is not obviously broken, it
 * just quietly puts the chart in the wrong place.
 */
class AstrolabeStructureTest {

    private static Predicate<BlockPos> occupying(Set<BlockPos> cells) {
        return pos -> cells.contains(pos.immutable());
    }

    private static Set<BlockPos> square(BlockPos centre) {
        return new HashSet<>(AstrolabeStructure.cells(centre));
    }

    @Test
    void aTableIsNineCellsAroundItsCentre() {
        assertEquals(9, AstrolabeStructure.cells(BlockPos.ZERO).size());
        assertTrue(AstrolabeStructure.cells(BlockPos.ZERO).contains(BlockPos.ZERO));
        assertTrue(AstrolabeStructure.cells(BlockPos.ZERO).contains(new BlockPos(1, 0, 1)));
        // Flat: a table is one block deep, so nothing above or below is part of it.
        assertFalse(AstrolabeStructure.cells(BlockPos.ZERO).contains(new BlockPos(0, 1, 0)));
    }

    @Test
    void anIncompleteSquareFormsNothing() {
        Set<BlockPos> cells = square(BlockPos.ZERO);
        cells.remove(new BlockPos(1, 0, 1));
        assertNull(AstrolabeStructure.findCentre(BlockPos.ZERO, occupying(cells)));
    }

    @Test
    void aCompleteSquareFormsAroundItsMiddle() {
        BlockPos centre = new BlockPos(40, 71, -12);
        Set<BlockPos> cells = square(centre);
        // Found from any of the nine, not only from the middle - a player finishes a table at
        // whichever cell happens to be last.
        for (BlockPos member : cells) {
            assertEquals(centre, AstrolabeStructure.findCentre(member, occupying(cells)),
                    "forming from " + member);
        }
    }

    @Test
    void aSquareOneBlockUpIsNotPartOfTheTable() {
        BlockPos centre = BlockPos.ZERO;
        Set<BlockPos> cells = square(centre);
        cells.addAll(square(centre.above()));
        // The lower square is still a table; the upper one being there must not confuse it into
        // forming something four blocks tall.
        assertEquals(centre, AstrolabeStructure.findCentre(centre, occupying(cells)));
    }

    /**
     * The reason ties are broken by coordinate rather than by search order.
     *
     * <p>A four-by-four slab contains four complete three-by-threes, and a corner of it sits inside
     * more than one of them. Which table forms is settled by coordinate, so laying the same slab out
     * twice gives the same table both times - a hash-order tie-break would move the chart between
     * builds for no reason a player could see.
     *
     * <p>Note what this does <em>not</em> claim: two different cells of an oversized slab may well
     * form different tables, because each only looks at the nine squares it could be part of. That is
     * ordinary greedy multiblock behaviour, and it is safe because a cell already in a table is not
     * offered to the next one.
     */
    @Test
    void anOversizedSlabFormsTheSameTableEveryTime() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                cells.add(new BlockPos(x, 64, z));
            }
        }
        // The corner at (1,1) is inside all four candidate tables. The lowest wins.
        BlockPos fromCorner = AstrolabeStructure.findCentre(new BlockPos(1, 64, 1), occupying(cells));
        assertEquals(new BlockPos(1, 64, 1), fromCorner);
        for (int repeat = 0; repeat < 8; repeat++) {
            assertEquals(fromCorner, AstrolabeStructure.findCentre(new BlockPos(1, 64, 1), occupying(cells)),
                    "the same layout formed differently on attempt " + repeat);
        }
    }

    /** Whatever forms, it is a complete square - never a centre with a hole in its ring. */
    @Test
    void whateverFormsIsComplete() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                cells.add(new BlockPos(x, 64, z));
            }
        }
        for (BlockPos member : cells) {
            BlockPos found = AstrolabeStructure.findCentre(member, occupying(cells));
            if (found == null) {
                continue;
            }
            assertTrue(AstrolabeStructure.isComplete(found, occupying(cells)),
                    "formed an incomplete table at " + found);
            assertTrue(AstrolabeStructure.covers(found, member),
                    found + " does not actually contain " + member);
        }
    }

    @Test
    void coverageMatchesTheCellsAroundACentre() {
        BlockPos centre = new BlockPos(5, 5, 5);
        for (BlockPos cell : AstrolabeStructure.cells(centre)) {
            assertTrue(AstrolabeStructure.covers(centre, cell));
        }
        assertFalse(AstrolabeStructure.covers(centre, new BlockPos(7, 5, 5)));
        assertFalse(AstrolabeStructure.covers(centre, new BlockPos(5, 6, 5)));
    }
}
