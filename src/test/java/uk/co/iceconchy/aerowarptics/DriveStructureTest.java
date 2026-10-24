package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.DriveStructure;
import uk.co.iceconchy.aerowarptics.drive.DriveStructure.Frame;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An extended Rift Drive's formation rules, tested as what they are: arithmetic over block positions.
 *
 * <p>Every failure here would be silent in the world. A frame that forms at a smaller size than the one
 * built reads as the extenders "not working" - which is exactly how the first version shipped, forming
 * a two-by-two in the corner of a finished three-cube - and a module search that misses one face of a
 * seven-cube is a Modulator that dresses the rift from five sides of the machine and not the sixth.
 */
class DriveStructureTest {

    private static final BlockPos SOMEWHERE = new BlockPos(40, 71, -12);

    private static Predicate<BlockPos> within(Set<BlockPos> extenders) {
        return pos -> extenders.contains(pos.immutable());
    }

    /** Every cell of a frame except the drive's, as extenders. */
    private static Set<BlockPos> extendersOf(Frame frame, BlockPos drive) {
        Set<BlockPos> cells = new HashSet<>(frame.cells());
        cells.remove(drive);
        return cells;
    }

    /** The answer the long way: the first candidate, in order, whose every cell is there. */
    private static Frame bruteForce(BlockPos drive, Predicate<BlockPos> usable) {
        for (Frame candidate : DriveStructure.candidates(drive)) {
            if (DriveStructure.isComplete(candidate, drive, usable)) {
                return candidate;
            }
        }
        return Frame.single(drive);
    }

    @Test
    void aFrameIsACubeOfItsOwnSize() {
        for (int size = DriveStructure.MIN_SIZE; size <= DriveStructure.MAX_SIZE; size++) {
            Frame frame = new Frame(SOMEWHERE, size);
            List<BlockPos> cells = frame.cells();
            assertEquals(size * size * size, cells.size());
            assertEquals(cells.size(), new HashSet<>(cells).size(), "a frame listed a cell twice");
            for (BlockPos cell : cells) {
                assertTrue(frame.covers(cell));
            }
            assertTrue(frame.covers(SOMEWHERE));
            assertFalse(frame.covers(SOMEWHERE.offset(-1, 0, 0)));
            assertFalse(frame.covers(SOMEWHERE.offset(0, size, 0)));
            assertEquals(size, frame.bounds().getXsize(), 1.0E-9D);
            assertEquals(size, frame.bounds().getYsize(), 1.0E-9D);
        }
    }

    @Test
    void thereIsNoSuchThingAsAnEightBlockDrive() {
        assertThrows(IllegalArgumentException.class, () -> new Frame(BlockPos.ZERO, DriveStructure.MAX_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> new Frame(BlockPos.ZERO, 0));
    }

    @Test
    void aDriveOnItsOwnIsStillADrive() {
        Frame frame = DriveStructure.findBest(SOMEWHERE, pos -> false);
        assertEquals(Frame.single(SOMEWHERE), frame);
        assertFalse(frame.isExtended());
        assertTrue(DriveStructure.fits(frame, SOMEWHERE));
        assertFalse(DriveStructure.fits(Frame.single(SOMEWHERE.above()), SOMEWHERE));
    }

    @Test
    void casingThatDoesNotTouchTheDriveIsNotAFrame() {
        // Extenders on every diagonal round the drive and none against a face of it.
        Set<BlockPos> extenders = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) {
                        extenders.add(SOMEWHERE.offset(dx, dy, dz));
                    }
                }
            }
        }
        assertEquals(Frame.single(SOMEWHERE), DriveStructure.findBest(SOMEWHERE, within(extenders)));
    }

    @Test
    void everyCubeContainingTheDriveIsACandidateBestFirst() {
        List<Frame> candidates = DriveStructure.candidates(SOMEWHERE);
        assertEquals(candidates.size(), new HashSet<>(candidates).size(), "a candidate was listed twice");
        Frame previous = null;
        for (Frame frame : candidates) {
            assertTrue(frame.isExtended(), "a lone drive is not a candidate frame");
            assertTrue(frame.covers(SOMEWHERE), frame + " does not contain its drive");
            if (previous != null) {
                assertTrue(frame.size() <= previous.size(), "candidates must run largest first");
                if (frame.size() == previous.size()) {
                    assertTrue(DriveStructure.offCentre(frame, SOMEWHERE) >= DriveStructure.offCentre(previous, SOMEWHERE),
                            "within a size, candidates must run most centred first");
                }
            }
            previous = frame;
        }
        for (int size = 2; size <= DriveStructure.MAX_SIZE; size++) {
            int count = size;
            // One for every cell of the cube the drive could be.
            assertEquals(size * size * size, candidates.stream().filter(frame -> frame.size() == count).count());
        }
    }

    /**
     * The bug that shipped: a finished three-cube formed as a two-by-two, because the drive had to sit in
     * one exact place for three to count. Wherever the drive is in a complete cube, that cube forms.
     */
    @Test
    void aCompleteCubeFormsWhereverInItTheDriveIs() {
        for (Frame frame : DriveStructure.candidates(SOMEWHERE)) {
            Frame formed = DriveStructure.findBest(SOMEWHERE, within(extendersOf(frame, SOMEWHERE)));
            assertEquals(frame, formed, "a complete " + frame.size() + "-cube did not form with the drive at "
                    + SOMEWHERE.subtract(frame.origin()) + " inside it");
        }
    }

    @Test
    void aThreeCubeBuiltAnyWayRoundAnyDriveFormsAsThree() {
        // Spelled out for the case in the report, rather than trusted to the sweep above.
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    BlockPos corner = SOMEWHERE.offset(-dx, -dy, -dz);
                    Frame cube = new Frame(corner, 3);
                    assertEquals(cube, DriveStructure.findBest(SOMEWHERE, within(extendersOf(cube, SOMEWHERE))));
                }
            }
        }
    }

    @Test
    void aCubeShortOfAnyOneExtenderDoesNotFormAtThatSize() {
        Frame three = new Frame(SOMEWHERE.offset(-1, -1, -1), 3);
        for (BlockPos missing : extendersOf(three, SOMEWHERE)) {
            Set<BlockPos> extenders = extendersOf(three, SOMEWHERE);
            extenders.remove(missing);
            Frame formed = DriveStructure.findBest(SOMEWHERE, within(extenders));
            assertTrue(formed.size() < 3, "a three-cube missing " + missing + " still formed as " + formed);
        }
    }

    @Test
    void aDriveInABlockOfCasingBiggerThanAnyFrameGrowsEvenlyAboutItself() {
        // Nine a side, which no frame can be, so it is laid out by hand rather than as a Frame.
        Set<BlockPos> extenders = new HashSet<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    extenders.add(SOMEWHERE.offset(dx, dy, dz));
                }
            }
        }
        extenders.remove(SOMEWHERE);
        Frame formed = DriveStructure.findBest(SOMEWHERE, within(extenders));
        assertEquals(new Frame(SOMEWHERE.offset(-3, -3, -3), DriveStructure.MAX_SIZE), formed);
        assertEquals(0, DriveStructure.offCentre(formed, SOMEWHERE));
    }

    @Test
    void equallyGoodFramesSettleOnTheLowestCorner() {
        // Two two-cubes either side of the drive along x, equally far off centre, and nothing bigger.
        Frame west = new Frame(SOMEWHERE.offset(-1, 0, 0), 2);
        Frame east = new Frame(SOMEWHERE, 2);
        assertEquals(DriveStructure.offCentre(west, SOMEWHERE), DriveStructure.offCentre(east, SOMEWHERE));
        Set<BlockPos> both = extendersOf(west, SOMEWHERE);
        both.addAll(extendersOf(east, SOMEWHERE));
        assertEquals(west, DriveStructure.findBest(SOMEWHERE, within(both)));
    }

    /**
     * The search reads a running total rather than walking each candidate, so it is checked against the
     * walk. A random scatter is the fastest way to meet the layouts nobody thought to write a test for,
     * and an off-by-one in a box sum is exactly the kind of mistake that still gets most of them right.
     */
    @Test
    void theFastSearchAgreesWithWalkingEveryCandidate() {
        Random random = new Random(20260914L);
        int reach = DriveStructure.MAX_SIZE - 1;
        for (int trial = 0; trial < 300; trial++) {
            Set<BlockPos> extenders = new HashSet<>();
            double density = 0.55D + random.nextDouble() * 0.45D;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        if (random.nextDouble() < density) {
                            extenders.add(SOMEWHERE.offset(dx, dy, dz));
                        }
                    }
                }
            }
            extenders.remove(SOMEWHERE);
            Frame formed = DriveStructure.findBest(SOMEWHERE, within(extenders));
            assertEquals(bruteForce(SOMEWHERE, within(extenders)), formed, "trial " + trial);
            assertTrue(DriveStructure.fits(formed, SOMEWHERE));
            assertTrue(DriveStructure.isComplete(formed, SOMEWHERE, within(extenders)));
        }
    }

    @Test
    void theSearchNeverLooksPastAFramesReach() {
        // Casing that is really there beyond reach must not be what completes a frame: a predicate that
        // throws out there proves nothing was asked about it.
        int reach = DriveStructure.MAX_SIZE - 1;
        Frame seven = new Frame(SOMEWHERE.offset(-3, -3, -3), DriveStructure.MAX_SIZE);
        Set<BlockPos> extenders = extendersOf(seven, SOMEWHERE);
        Frame formed = DriveStructure.findBest(SOMEWHERE, pos -> {
            if (Math.abs(pos.getX() - SOMEWHERE.getX()) > reach || Math.abs(pos.getY() - SOMEWHERE.getY()) > reach
                    || Math.abs(pos.getZ() - SOMEWHERE.getZ()) > reach) {
                throw new AssertionError("asked about " + pos + ", out of any frame's reach");
            }
            return extenders.contains(pos.immutable());
        });
        assertEquals(seven, formed);
    }

    @Test
    void aModuleCanStandAgainstEveryFaceOfTheFrameAndNowhereElse() {
        assertEquals(
                Set.of(SOMEWHERE.north(), SOMEWHERE.south(), SOMEWHERE.east(), SOMEWHERE.west(),
                        SOMEWHERE.above(), SOMEWHERE.below()),
                new HashSet<>(DriveStructure.surroundings(Frame.single(SOMEWHERE))),
                "a lone drive's modules stand exactly where they always have");

        for (int size = 2; size <= DriveStructure.MAX_SIZE; size++) {
            Frame frame = new Frame(SOMEWHERE, size);
            List<BlockPos> around = DriveStructure.surroundings(frame);
            assertEquals(6 * size * size, around.size());
            assertEquals(around.size(), new HashSet<>(around).size(), "a place was listed twice");
            Set<BlockPos> expected = new HashSet<>();
            for (BlockPos cell : frame.cells()) {
                for (Direction direction : Direction.values()) {
                    BlockPos next = cell.relative(direction);
                    if (!frame.covers(next)) {
                        expected.add(next);
                    }
                }
            }
            assertEquals(expected, new HashSet<>(around), "surroundings of a " + size + "-cube");
        }
    }

    @Test
    void theSearchFromAnExtenderFindsTheDrivesItsExtendersTouch() {
        BlockPos drive = SOMEWHERE;
        BlockPos other = SOMEWHERE.offset(20, 0, 0);
        // A line of extenders out from the first drive, and one lying loose beside the second.
        Set<BlockPos> extenders = new HashSet<>();
        for (int i = 1; i <= 4; i++) {
            extenders.add(drive.offset(0, 0, i));
        }
        extenders.add(other.offset(0, 1, 0));
        Set<BlockPos> drives = Set.of(drive, other);

        assertEquals(List.of(drive), DriveStructure.nearbyDrives(List.of(drive.offset(0, 0, 4)),
                within(extenders), drives::contains));
        assertEquals(List.of(other), DriveStructure.nearbyDrives(List.of(other.offset(0, 1, 0)),
                within(extenders), drives::contains));
        // Two starts at once see both, in order.
        assertEquals(List.of(drive, other), DriveStructure.nearbyDrives(
                List.of(drive.offset(0, 0, 1), other.offset(0, 1, 0)), within(extenders), drives::contains));
        // A start that is not an extender - the block that was just broken - leads nowhere by itself.
        assertEquals(List.of(), DriveStructure.nearbyDrives(List.of(drive.offset(5, 5, 5)),
                within(extenders), drives::contains));
    }

    @Test
    void theWalkFromAnExtenderDoesNotGoFurtherThanAFrameCouldReach() {
        // A long wall of spare extenders with a drive at the far end, well out of reach of any frame that
        // could include the block being placed.
        Set<BlockPos> extenders = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            extenders.add(SOMEWHERE.offset(i, 0, 0));
        }
        BlockPos farDrive = SOMEWHERE.offset(40, 0, 0);
        assertEquals(List.of(), DriveStructure.nearbyDrives(List.of(SOMEWHERE), within(extenders),
                pos -> pos.equals(farDrive)));
        BlockPos nearDrive = SOMEWHERE.offset(DriveStructure.MAX_SIZE - 1, 1, 0);
        assertEquals(List.of(nearDrive), DriveStructure.nearbyDrives(List.of(SOMEWHERE), within(extenders),
                pos -> pos.equals(nearDrive) || pos.equals(farDrive)));
    }

    /**
     * The pointers survive a hull assembling. Every block of the frame is moved into the plot grid by the
     * same offset, so a step between two of them is the same number after as before.
     */
    @Test
    void pointersSurviveTheWholeFrameBeingMovedIntoAShip() {
        BlockPos drive = SOMEWHERE;
        BlockPos extender = SOMEWHERE.offset(2, -3, 1);
        BlockPos step = DriveStructure.step(extender, drive);
        assertEquals(drive, DriveStructure.fromStep(extender, step));

        BlockPos plot = new BlockPos(20_480_000, 40, 20_488_192);
        BlockPos movedDrive = drive.offset(plot);
        BlockPos movedExtender = extender.offset(plot);
        assertEquals(step, DriveStructure.step(movedExtender, movedDrive));
        assertEquals(movedDrive, DriveStructure.fromStep(movedExtender, step));
    }
}
