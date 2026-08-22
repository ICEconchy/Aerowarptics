package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a gate's opening is the hole somebody built, not the box around it.
 *
 * <p>A ring is flood filled, so its opening is whatever shape the builder made, and it is very often
 * not a rectangle. Carrying only the bounding box made two separate things wrong at once - the
 * aperture bulged through the frame, and brushing the fire beside the hole counted as going through
 * it - so both halves of the fix are pinned here.
 */
class RiftGateOpeningTest {

    /** A five-by-five gate lying along X, with an L-shaped hole cut in it. */
    private static RiftGateShape lShaped() {
        // Open cells, across x up, with the top-right quadrant missing:
        //   . . . x x
        //   . . . x x
        //   . . . . .
        //   . . . . .
        //   . . . . .
        BitSet mask = new BitSet(25);
        for (int up = 0; up < 5; up++) {
            for (int across = 0; across < 5; across++) {
                boolean missing = up >= 3 && across >= 3;
                if (!missing) {
                    mask.set(up * 5 + across);
                }
            }
        }
        return new RiftGateShape(Direction.Axis.X, 0, 64, 20, 4, 68, 20, mask);
    }

    private static RiftGateShape rectangle() {
        return new RiftGateShape(Direction.Axis.X, 0, 64, 20, 4, 68, 20);
    }

    /** A shape built without a mask is a full rectangle, which is what every old gate was. */
    @Test
    void aShapeWithoutAMaskIsWhollyOpen() {
        RiftGateShape shape = rectangle();
        assertEquals(shape.area(), shape.openCells(), "a rectangular gate lost cells to the mask");
        for (int up = 0; up < shape.height(); up++) {
            for (int across = 0; across < shape.width(); across++) {
                assertTrue(shape.openAt(across, up), across + "," + up + " was not open");
            }
        }
    }

    /** The mask records exactly the cells that were filled, and nothing else. */
    @Test
    void anLShapedOpeningKnowsWhichCellsAreMissing() {
        RiftGateShape shape = lShaped();
        assertEquals(21, shape.openCells(), "the notch was not cut out");
        assertTrue(shape.openAt(0, 0));
        assertTrue(shape.openAt(4, 0), "the bottom row should be open all the way across");
        assertFalse(shape.openAt(4, 4), "the notch is still reported open");
        assertFalse(shape.openAt(3, 3), "the notch is still reported open");
    }

    /** Cells outside the bounding box are never open, however far out they are asked about. */
    @Test
    void nothingOutsideTheBoxIsOpen() {
        RiftGateShape shape = rectangle();
        assertFalse(shape.openAt(-1, 0));
        assertFalse(shape.openAt(0, -1));
        assertFalse(shape.openAt(shape.width(), 0));
        assertFalse(shape.openAt(0, shape.height()));
    }

    /**
     * This is the teleport bug, in one assertion.
     *
     * <p>A point in the notch is inside the gate's bounding box and under the aperture that gets
     * drawn over it, but it is not in the hole - and anything standing there is standing in front of
     * solid frame.
     */
    @Test
    void aPointInTheNotchIsNotInsideTheOpening() {
        RiftGateShape shape = lShaped();
        Vec3 throughTheHole = new Vec3(0.5D, 64.5D, 20.5D);
        Vec3 throughTheWall = new Vec3(4.5D, 68.5D, 20.5D);

        assertTrue(shape.contains(throughTheHole), "a point in the opening was refused");
        assertFalse(shape.contains(throughTheWall), "a point in solid frame counted as the opening");
    }

    /** Which side of the plane a point is on is a separate question from whether it may cross. */
    @Test
    void containmentIgnoresWhichSideOfThePlaneYouAreOn() {
        RiftGateShape shape = lShaped();
        // Same cell of the opening, a block either side of the plane.
        assertTrue(shape.contains(new Vec3(0.5D, 64.5D, 19.2D)));
        assertTrue(shape.contains(new Vec3(0.5D, 64.5D, 21.8D)));
    }

    /**
     * A rectangular gate's aperture is unchanged.
     *
     * <p>The profile scales the rim, so a gate that fills its own box has to come back as one
     * everywhere or every existing gate in the world would visibly shrink.
     */
    @Test
    void aRectangularOpeningReachesItsFullEllipse() {
        RiftGateShape shape = rectangle();
        for (int step = 0; step < 32; step++) {
            double angle = step * (Math.PI * 2.0D) / 32.0D;
            assertTrue(shape.reachAt(angle) > 0.9D,
                    "a full rectangle only reached " + shape.reachAt(angle) + " at " + angle);
        }
    }

    /**
     * The notch pulls the aperture in where the hole is missing, and nowhere else.
     *
     * <p>Towards the missing quadrant the rim has to come in; towards the arms that are still open it
     * must not, or fixing the bulge would just shrink the whole thing.
     */
    @Test
    void aNotchPullsTheApertureInOnlyWhereItIsMissing() {
        RiftGateShape shape = lShaped();
        double intoTheNotch = shape.reachAt(Math.PI / 4.0D);
        double intoTheOpening = shape.reachAt(Math.PI * 1.25D);

        assertTrue(intoTheNotch < 0.8D,
                "the aperture still reached " + intoTheNotch + " into solid frame");
        assertTrue(intoTheOpening > 0.9D,
                "the aperture was pulled in to " + intoTheOpening + " where the hole is open");
    }

    /** The reach is a fraction, never negative and never past the edge of the box. */
    @Test
    void everyReachIsAFraction() {
        RiftGateShape shape = lShaped();
        for (int step = 0; step < 128; step++) {
            double reach = shape.reachAt(step * (Math.PI * 2.0D) / 128.0D);
            assertTrue(reach >= 0.0D && reach <= 1.0D, "reach of " + reach + " is not a fraction");
        }
    }

    /** A mask survives being written and read back, or a gate changes shape on every reload. */
    @Test
    void theMaskSurvivesNbt() {
        RiftGateShape shape = lShaped();
        RiftGateShape loaded = RiftGateShape.load(shape.save());
        assertEquals(shape, loaded, "the shape came back different");
        assertEquals(shape.openCells(), loaded.openCells());
        assertFalse(loaded.openAt(4, 4), "the notch was filled in by a round trip");
    }

    /**
     * Two shapes with the same cells are equal.
     *
     * <p>Worth pinning because the obvious way to hold a mask is a {@code long[]}, and a record with
     * an array component compares by identity - which would silently make no two shapes equal.
     */
    @Test
    void shapesWithTheSameOpeningAreEqual() {
        assertEquals(lShaped(), lShaped());
        assertEquals(lShaped().hashCode(), lShaped().hashCode());
    }
}
