package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bow setting: a quarter turn from the drive's own face, in the airship's frame. */
class DriveHeadingTest {

    private static final float EPSILON = 1.0e-6F;

    @Test
    void forwardIsTheDrivesOwnFace() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(facing, DriveHeading.FORWARD.apply(facing));
        }
    }

    @Test
    void theSettingsAreQuarterTurnsClockwiseFromTheFace() {
        assertEquals(Direction.EAST, DriveHeading.RIGHT.apply(Direction.NORTH));
        assertEquals(Direction.SOUTH, DriveHeading.BACK.apply(Direction.NORTH));
        assertEquals(Direction.WEST, DriveHeading.LEFT.apply(Direction.NORTH));
    }

    @Test
    void everySettingIsADistinctHorizontalDirectionFromAnyFace() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Set<Direction> reached = new HashSet<>();
            for (DriveHeading heading : DriveHeading.values()) {
                Direction bow = heading.apply(facing);
                assertEquals(0, bow.getStepY(), heading + " must be horizontal");
                assertTrue(reached.add(bow), "two settings reached " + bow + " from " + facing);
            }
            assertEquals(4, reached.size(), "the four settings must cover all four ways round");
        }
    }

    @Test
    void aVerticalReferenceIsRejectedRatherThanQuietlyWrong() {
        // The drive resolves this before it gets here, by standing the ship's own north in for a
        // face that does not exist. Silently returning something would hide that decision.
        for (Direction facing : new Direction[]{Direction.UP, Direction.DOWN}) {
            assertThrows(IllegalArgumentException.class, () -> DriveHeading.FORWARD.apply(facing));
        }
    }

    @Test
    void turningTheSettingTurnsTheNeedleTheSameWay() {
        // Clockwise from above, which GeckoLib's anticlockwise bone rotation makes negative.
        assertEquals(0.0F, DriveHeading.FORWARD.needleRadians(), EPSILON);
        assertEquals((float) -Math.PI / 2.0F, DriveHeading.RIGHT.needleRadians(), EPSILON);
        assertEquals((float) -Math.PI, DriveHeading.BACK.needleRadians(), EPSILON);
        assertEquals((float) -Math.PI * 1.5F, DriveHeading.LEFT.needleRadians(), EPSILON);
    }

    @Test
    void theNeedleAgreesWithTheBowItPointsAt() {
        // Rotating the model's authored front (-Z) by the needle angle about YP must land on the
        // same direction the flight planner will use. If these ever disagree, the machine lies.
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DriveHeading heading : DriveHeading.values()) {
                Direction bow = heading.apply(facing);
                double angle = heading.needleRadians() + yawOf(facing);
                assertEquals(bow.getStepX(), -Math.sin(angle), 1.0e-6D, heading + " from " + facing);
                assertEquals(bow.getStepZ(), -Math.cos(angle), 1.0e-6D, heading + " from " + facing);
            }
        }
    }

    /** The YP rotation the block renderer applies for a given facing, in radians. */
    private static double yawOf(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0D;
            case WEST -> Math.PI / 2.0D;
            case SOUTH -> Math.PI;
            case EAST -> -Math.PI / 2.0D;
            default -> throw new IllegalArgumentException(facing.toString());
        };
    }

    @Test
    void cyclingVisitsEverySettingAndComesBackRound() {
        Set<DriveHeading> seen = new HashSet<>();
        DriveHeading heading = DriveHeading.FORWARD;
        for (int i = 0; i < DriveHeading.values().length; i++) {
            seen.add(heading);
            heading = heading.next();
        }
        assertEquals(DriveHeading.values().length, seen.size(), "cycling must reach every setting");
        assertEquals(DriveHeading.FORWARD, heading, "and wrap back round to where it started");
    }

    @Test
    void cyclingTurnsTheBowAQuarterClockwise() {
        DriveHeading heading = DriveHeading.FORWARD;
        Direction bow = heading.apply(Direction.NORTH);
        for (int i = 0; i < 4; i++) {
            heading = heading.next();
            Direction next = heading.apply(Direction.NORTH);
            assertEquals(bow.getClockWise(), next, "cycling must turn the bow one quarter clockwise");
            bow = next;
        }
    }

    @Test
    void indexRoundTripIsStableAndBoundsSafe() {
        for (DriveHeading heading : DriveHeading.values()) {
            assertEquals(heading, DriveHeading.byIndex(heading.ordinal()));
        }
        assertEquals(DriveHeading.FORWARD, DriveHeading.byIndex(-1));
        assertEquals(DriveHeading.FORWARD, DriveHeading.byIndex(99));
    }

    @Test
    void everySettingHasItsOwnTranslationKey() {
        Set<String> keys = new HashSet<>();
        for (DriveHeading heading : DriveHeading.values()) {
            assertTrue(keys.add(heading.translationKey()), "duplicate key for " + heading);
        }
        assertNotEquals(0, keys.size());
    }
}
