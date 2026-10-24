package uk.co.iceconchy.aerowarptics.collapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Collapse Cradle's footprint, held to what the blocks assume of it: every part finds its way back to the
 * master, nothing outside the machine is mistaken for it, and it only stands where all of it fits.
 */
class CradleStructureTest {

    private static final BlockPos MASTER = new BlockPos(100, 64, -40);

    @Test
    void itIsThreeBlocksEachWayWithTheMasterFirstInTheMiddleOfItsFloor() {
        List<Vec3i> offsets = CradleStructure.offsets();
        assertEquals(27, offsets.size());
        assertEquals(27, new HashSet<>(offsets).size(), "an offset repeats");
        assertEquals(Vec3i.ZERO, offsets.get(0), "the master is not first");
        for (Vec3i offset : offsets) {
            assertTrue(Math.abs(offset.getX()) <= 1 && Math.abs(offset.getZ()) <= 1, offset + " is off the floor");
            assertTrue(offset.getY() >= 0 && offset.getY() <= 2, offset + " is below the floor or over the top");
        }
    }

    /** A part carries only its place; the master has to be recoverable from that and where the part stands. */
    @Test
    void everyPartFindsItsMaster() {
        for (Vec3i offset : CradleStructure.offsets()) {
            int[] place = CradleStructure.placeOf(offset);
            assertTrue(CradleStructure.isPlace(place[0], place[1], place[2]), offset + " has no place");
            assertEquals(offset, CradleStructure.offsetOf(place[0], place[1], place[2]));
            assertEquals(offset.equals(Vec3i.ZERO), CradleStructure.isMaster(place[0], place[1], place[2]));
            assertEquals(MASTER, CradleStructure.master(MASTER.offset(offset), place[0], place[1], place[2]));
        }
    }

    @Test
    void itCoversItsOwnBlocksAndNothingElse() {
        Set<BlockPos> cells = new HashSet<>(CradleStructure.cells(MASTER));
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = MASTER.offset(dx, dy, dz);
                    assertEquals(cells.contains(pos), CradleStructure.covers(MASTER, pos), pos + " disagrees");
                }
            }
        }
    }

    /** Every face of it, nine blocks a side: where a Rift Modulator dressing the ritual may stand. */
    @Test
    void whatIsAroundItTouchesItFromOutside() {
        List<BlockPos> around = CradleStructure.around(MASTER);
        assertEquals(54, around.size());
        for (BlockPos pos : around) {
            assertFalse(CradleStructure.covers(MASTER, pos), pos + " is inside the machine");
            boolean touches = false;
            for (BlockPos cell : CradleStructure.cells(MASTER)) {
                touches |= cell.distManhattan(pos) == 1;
            }
            assertTrue(touches, pos + " does not touch the machine");
        }
    }

    @Test
    void itStandsOnlyWhereAllOfItFits() {
        assertTrue(CradleStructure.fits(MASTER, -64, 320, pos -> true));
        // The master's own block is the player's to place into, and is not asked about.
        assertTrue(CradleStructure.fits(MASTER, -64, 320, pos -> !pos.equals(MASTER)));
        for (Vec3i offset : CradleStructure.offsets()) {
            if (!offset.equals(Vec3i.ZERO)) {
                BlockPos blocked = MASTER.offset(offset);
                assertFalse(CradleStructure.fits(MASTER, -64, 320, pos -> !pos.equals(blocked)),
                        "stood with " + offset + " taken");
            }
        }
        assertFalse(CradleStructure.fits(new BlockPos(0, 318, 0), -64, 320, pos -> true), "its crown is over the top");
        assertTrue(CradleStructure.fits(new BlockPos(0, 317, 0), -64, 320, pos -> true));
        assertFalse(CradleStructure.fits(new BlockPos(0, -65, 0), -64, 320, pos -> true), "its floor is under the world");
    }

    @Test
    void itsBoxIsItsBlocks() {
        assertArrayEquals(new double[]{-1, 0, -1, 2, 3, 2}, CradleStructure.box(), 0.0D);
    }

    // ------------------------------------------------------------------ turned from its sides, passing nothing on

    /**
     * A shaft turns the machine through the middle of each side of its floor, facing out, and from under the master,
     * and through nothing else.
     */
    @Test
    void theMiddleOfEachSideFacesOutAndTheMasterTakesOneFromBelow() {
        List<BlockPos> inputs = CradleStructure.inputs(MASTER);
        assertEquals(5, new HashSet<>(inputs).size());
        assertEquals(MASTER, inputs.get(0), "the master is the first input");
        for (BlockPos cell : CradleStructure.cells(MASTER)) {
            for (Direction face : Direction.values()) {
                boolean outwardFromASide = !cell.equals(MASTER) && inputs.contains(cell)
                        && cell.relative(face.getOpposite()).equals(MASTER);
                boolean underTheMaster = cell.equals(MASTER) && face == Direction.DOWN;
                assertEquals(outwardFromASide || underTheMaster, CradleStructure.shaftTowards(MASTER, cell, face),
                        cell.subtract(MASTER) + " " + face);
            }
        }
        for (BlockPos input : inputs) {
            assertTrue(CradleStructure.bottom(MASTER, input), input + " is not on the floor");
            assertTrue(input.distManhattan(MASTER) <= 1, input + " is neither the master nor the middle of a side");
        }
    }

    /**
     * No rotation goes through the machine. A Create block passes rotation between any two of its faces that have
     * shafts, and two blocks link where their shafts face each other - so no block of the machine may have two, and
     * no shaft may look at another block of the machine.
     */
    @Test
    void noRotationPassesThroughTheMachine() {
        for (BlockPos cell : CradleStructure.cells(MASTER)) {
            int shafts = 0;
            for (Direction face : Direction.values()) {
                if (CradleStructure.shaftTowards(MASTER, cell, face)) {
                    shafts++;
                    assertFalse(CradleStructure.covers(MASTER, cell.relative(face)),
                            cell.subtract(MASTER) + " has a shaft into the machine, " + face);
                    assertTrue(face != Direction.UP, cell.subtract(MASTER) + " takes a shaft from above");
                    assertTrue(face.getAxis().isHorizontal() || cell.equals(MASTER),
                            cell.subtract(MASTER) + " takes a shaft from " + face);
                }
            }
            assertTrue(shafts <= 1, cell.subtract(MASTER) + " has " + shafts + " shafts, and would pass rotation on");
        }
    }

    /** The master has the one shaft, underneath: a line run in from below ends there, and none can leave by it. */
    @Test
    void theMasterHasOneShaftUnderneath() {
        for (Direction face : Direction.values()) {
            assertEquals(face == Direction.DOWN, CradleStructure.shaftTowards(MASTER, MASTER, face),
                    "the master's shaft " + face);
        }
        assertTrue(CradleStructure.bottom(MASTER, MASTER));
        assertFalse(CradleStructure.bottom(MASTER, MASTER.above()));
    }
}
