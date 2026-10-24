package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Wellhead's frame, held to what the blocks assume of it: every cell of the cube is exactly one thing,
 * a part finds its way back to its fissure, and the machine has one shaft and no more.
 */
class WellheadStructureTest {

    private static final BlockPos CENTRE = new BlockPos(100, 64, -40);

    @Test
    void aFrameIsBuiltFromTwentySixNinetyEightOrTwoHundredAndEighteenBlocks() {
        assertEquals(26, WellheadStructure.shellBlocks(1));
        assertEquals(98, WellheadStructure.shellBlocks(2));
        assertEquals(218, WellheadStructure.shellBlocks(3));
        for (int radius = 1; radius <= WellheadStructure.MAX_RADIUS; radius++) {
            List<Vec3i> shell = WellheadStructure.shell(radius);
            assertEquals(WellheadStructure.shellBlocks(radius), shell.size());
            assertEquals(shell.size(), new HashSet<>(shell).size(), "a shell cell repeats");
        }
    }

    /** The whole cube, and a block beyond it each way, sorted into what the shell and the interior say it is. */
    @Test
    void everyCellOfTheCubeIsExactlyOneThing() {
        for (int radius = 1; radius <= WellheadStructure.MAX_RADIUS; radius++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                HashSet<Vec3i> shell = new HashSet<>(WellheadStructure.shell(radius));
                HashSet<Vec3i> interior = new HashSet<>(WellheadStructure.interior(radius));
                for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                    for (int dy = -radius - 1; dy <= radius + 1; dy++) {
                        for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                            Vec3i offset = new Vec3i(dx, dy, dz);
                            WellheadStructure.Cell cell = WellheadStructure.cell(offset, radius, side);
                            assertEquals(shell.contains(offset), cell.isShell(), offset + " at radius " + radius);
                            assertEquals(interior.contains(offset), cell == WellheadStructure.Cell.INSIDE,
                                    offset + " at radius " + radius);
                            assertEquals(offset.equals(Vec3i.ZERO), cell == WellheadStructure.Cell.CENTRE);
                        }
                    }
                }
            }
        }
    }

    @Test
    void aFrameHasOneCollarOneHatchFourTapsAndTwelveEdgesOfCasing() {
        for (int radius = 1; radius <= WellheadStructure.MAX_RADIUS; radius++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Map<WellheadStructure.Cell, Integer> count = new EnumMap<>(WellheadStructure.Cell.class);
                for (Vec3i offset : WellheadStructure.shell(radius)) {
                    count.merge(WellheadStructure.cell(offset, radius, side), 1, Integer::sum);
                }
                int size = radius * 2 + 1;
                assertEquals(1, count.get(WellheadStructure.Cell.COLLAR));
                assertEquals(1, count.get(WellheadStructure.Cell.HATCH));
                assertEquals(4, count.get(WellheadStructure.Cell.TAP));
                // Eight corners and twelve edges less their corners.
                assertEquals(8 + 12 * (size - 2), count.get(WellheadStructure.Cell.CASING));
                // Six faces less their rims, less the six middles. A three-cube's faces are all middle.
                assertEquals(6 * (size - 2) * (size - 2) - 6, count.getOrDefault(WellheadStructure.Cell.GLASS, 0));
            }
        }
    }

    @Test
    void theCollarIsInTheMiddleOfItsSideTheHatchOnTopAndATapUnderneath() {
        for (int radius = 1; radius <= WellheadStructure.MAX_RADIUS; radius++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Vec3i collar = side.getNormal().multiply(radius);
                assertEquals(WellheadStructure.Cell.COLLAR, WellheadStructure.cell(collar, radius, side));
                assertEquals(WellheadStructure.Cell.HATCH, WellheadStructure.cell(0, radius, 0, radius, side));
                assertEquals(WellheadStructure.Cell.TAP, WellheadStructure.cell(0, -radius, 0, radius, side));
                assertEquals(WellheadStructure.Cell.TAP,
                        WellheadStructure.cell(side.getOpposite().getNormal().multiply(radius), radius, side));
                BlockPos at = WellheadStructure.collar(CENTRE, side, radius);
                assertEquals(CENTRE.offset(collar), at);
                assertEquals(CENTRE, WellheadStructure.centre(at, side, radius));
            }
        }
    }

    /** A part carries only its offset; its fissure has to be recoverable from that and where the part stands. */
    @Test
    void everyPartFindsItsFissure() {
        for (int radius = 1; radius <= WellheadStructure.MAX_RADIUS; radius++) {
            for (Vec3i offset : WellheadStructure.shell(radius)) {
                int[] state = WellheadStructure.stateOf(offset);
                for (int number : state) {
                    assertTrue(number >= 0 && number <= 2 * WellheadStructure.STATE_BIAS, offset + " does not fit a state");
                }
                assertEquals(offset, WellheadStructure.offsetOf(state[0], state[1], state[2]));
                assertEquals(CENTRE, WellheadStructure.centre(CENTRE.offset(offset), state[0], state[1], state[2]));
                assertEquals(radius, WellheadStructure.radiusOf(offset));
            }
        }
    }

    /** One shaft, on the collar, looking out. A second anywhere makes the frame a gearbox. */
    @Test
    void theMachineHasOneShaftAndItLooksOutOfTheCollar() {
        for (int radius = 1; radius <= WellheadStructure.MAX_RADIUS; radius++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int shafts = 0;
                for (Vec3i offset : WellheadStructure.shell(radius)) {
                    for (Direction face : Direction.values()) {
                        if (WellheadStructure.shaftTowards(offset, face, radius, side)) {
                            shafts++;
                            assertEquals(side, face);
                            assertEquals(side.getNormal().multiply(radius), offset);
                        }
                    }
                }
                assertEquals(1, shafts);
            }
        }
    }

    @Test
    void theNearestFissureBehindTheCollarSetsTheSize() {
        assertEquals(0, WellheadStructure.radius(back -> false));
        assertEquals(1, WellheadStructure.radius(back -> back == 1));
        assertEquals(2, WellheadStructure.radius(back -> back == 2));
        assertEquals(3, WellheadStructure.radius(back -> back == 3));
        assertEquals(1, WellheadStructure.radius(back -> back == 1 || back == 3), "a further tear changed the frame");
        assertEquals(0, WellheadStructure.radius(back -> back == 4), "it looked past a seven-cube");
    }

    @Test
    void aTierIsItsRadiusAndABetterCrystalPermitsASmallerFrame() {
        assertEquals(WellheadTier.I, WellheadTier.ofRadius(1));
        assertEquals(WellheadTier.III, WellheadTier.ofRadius(3));
        assertNull(WellheadTier.ofRadius(0));
        assertNull(WellheadTier.ofRadius(4));
        assertEquals(7, WellheadTier.III.size());
        assertTrue(WellheadTier.III.permits(WellheadTier.I));
        assertTrue(WellheadTier.II.permits(WellheadTier.II));
        assertFalse(WellheadTier.I.permits(WellheadTier.II));
        assertFalse(WellheadTier.III.permits(null));
        assertNotEquals(WellheadTier.I.translationKey(), WellheadTier.II.translationKey());
    }
}
