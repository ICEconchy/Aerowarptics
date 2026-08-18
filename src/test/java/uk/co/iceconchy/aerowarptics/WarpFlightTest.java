package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which way a hull faces, and the geometry the client is told about. */
class WarpFlightTest {

    private static final double EPSILON = 1.0e-9D;

    // --------------------------------------------------------------- heading

    @Test
    void theBowIsTheDrivesFaceTurnedByTheSetting() {
        assertEquals(Direction.NORTH, WarpFlight.shipSpaceBow(DriveHeading.FORWARD, Direction.NORTH));
        assertEquals(Direction.EAST, WarpFlight.shipSpaceBow(DriveHeading.RIGHT, Direction.NORTH));
        assertEquals(Direction.NORTH, WarpFlight.shipSpaceBow(DriveHeading.LEFT, Direction.EAST));
    }

    @Test
    void theBowIsAlwaysHorizontal() {
        for (Direction facing : Direction.values()) {
            for (DriveHeading heading : DriveHeading.values()) {
                Direction bow = WarpFlight.shipSpaceBow(heading, facing);
                assertEquals(0, bow.getStepY(), heading + " on a " + facing + "-facing drive");
            }
        }
    }

    @Test
    void aVerticallyMountedDriveFallsBackToTheShipsOwnNorth() {
        // A drive taking power from a vertical shaft has no front face to measure from, so the hull's
        // own axes stand in. The setting still covers all four ways round, and the needle reads the
        // same rule, so it is a reference the pilot can still aim.
        for (Direction facing : new Direction[]{Direction.UP, Direction.DOWN}) {
            assertEquals(Direction.NORTH, WarpFlight.shipSpaceBow(DriveHeading.FORWARD, facing));
            assertEquals(Direction.EAST, WarpFlight.shipSpaceBow(DriveHeading.RIGHT, facing));
            assertEquals(Direction.SOUTH, WarpFlight.shipSpaceBow(DriveHeading.BACK, facing));
            assertEquals(Direction.WEST, WarpFlight.shipSpaceBow(DriveHeading.LEFT, facing));
        }
    }

    @Test
    void turningTheDriveRoundTurnsTheShipRound() {
        // The whole point of measuring from the drive: remount it the other way and the same setting
        // sends the ship the other way, with no world compass involved anywhere.
        for (DriveHeading heading : DriveHeading.values()) {
            Direction fromNorth = WarpFlight.shipSpaceBow(heading, Direction.NORTH);
            Direction fromSouth = WarpFlight.shipSpaceBow(heading, Direction.SOUTH);
            assertEquals(fromNorth.getOpposite(), fromSouth, heading.toString());
        }
    }

    @Test
    void everySettingReachesADifferentBow() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Set<Direction> reached = new HashSet<>();
            for (DriveHeading heading : DriveHeading.values()) {
                assertTrue(reached.add(WarpFlight.shipSpaceBow(heading, facing)),
                        "two settings collide on a " + facing + "-facing drive");
            }
        }
    }

    // ------------------------------------------------------------------ rift

    @Test
    void riftGeometrySurvivesBeingWrittenOut() {
        WarpFlight.Rift rift = new WarpFlight.Rift(
                new Vector3d(1250.5D, 110.25D, -8399.75D),
                new Vector3d(0.0D, 0.0D, -1.0D),
                37.5D);

        CompoundTag tag = rift.save();
        WarpFlight.Rift restored = WarpFlight.Rift.load(tag);

        assertEquals(rift.centre(), restored.centre());
        assertEquals(rift.normal(), restored.normal());
        assertEquals(rift.radius(), restored.radius(), EPSILON);
    }

    @Test
    void facingVectorsMatchTheirDirections() {
        for (Direction direction : Direction.values()) {
            Vector3d vector = WarpFlight.facingVector(direction);
            assertEquals(direction.getStepX(), vector.x, EPSILON);
            assertEquals(direction.getStepY(), vector.y, EPSILON);
            assertEquals(direction.getStepZ(), vector.z, EPSILON);
            assertTrue(vector.length() > 0.0D);
        }
    }
}
