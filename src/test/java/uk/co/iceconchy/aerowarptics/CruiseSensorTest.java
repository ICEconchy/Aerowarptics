package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.CruiseSensor;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The look ahead of a cruising ship.
 *
 * <p>Two ways for this to be wrong, and they fail in opposite directions. Missing a wall flies a ship
 * into it at speed, which is what makes Sable throw it; seeing a wall that is not in the path - the ground
 * under the keel, a hill beside the heading - stops a cruise for nothing. Both are put in front of a hull
 * here, and so is the case the sensor exists for: country the server has not loaded yet.
 */
class CruiseSensorTest {

    /** A hull ten wide, six tall and twenty long, hovering with its keel at exactly y = 70. */
    private static final double[] HULL = {0.0D, 70.0D, 0.0D, 10.0D, 76.0D, 20.0D};

    /** A world of solid blocks wherever {@code solid} says, loaded wherever {@code loaded} says. */
    private record Grid(Solid solid, IntPredicate loadedChunkX) implements CruiseSensor.World {
        interface Solid {
            boolean at(int x, int y, int z);
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 319;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return loadedChunkX.test(chunkX);
        }

        @Override
        public boolean sectionEmpty(int chunkX, int sectionY, int chunkZ) {
            return false; // read everything; the skip is an optimisation, not what is under test
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return solid.at(x, y, z);
        }
    }

    private static final IntPredicate EVERYWHERE = chunk -> true;

    private static CruiseSensor.Reading ahead(double bowX, double bowZ, double reach, CruiseSensor.World world) {
        return CruiseSensor.ahead(HULL[0], HULL[1], HULL[2], HULL[3], HULL[4], HULL[5], bowX, bowZ, reach,
                10_000_000L, world);
    }

    @Test
    void openSkyIsClearForTheWholeReach() {
        CruiseSensor.Reading reading = ahead(0.0D, 1.0D, 120.0D, new Grid((x, y, z) -> false, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.CLEAR, reading.limit());
        assertEquals(120.0D, reading.distance(), 1.0e-9D);
    }

    @Test
    void aWallAheadIsFoundBeforeTheHullReachesIt() {
        // One block thick, across the whole path, sixty blocks off the bow (the bow is at z = 20).
        CruiseSensor.Reading reading = ahead(0.0D, 1.0D, 200.0D,
                new Grid((x, y, z) -> z == 80, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.TERRAIN, reading.limit());
        assertTrue(reading.distance() <= 60.0D, "reported " + reading.distance() + " for a wall 60 blocks off");
        assertTrue(reading.distance() > 60.0D - CruiseSensor.STEP - 1.0D, "and not wildly short of it");
    }

    @Test
    void theGroundUnderTheKeelIsNotAWall() {
        // Flat ground right up to the keel: y = 69 is the top block, and the hull floats at 70.
        CruiseSensor.Reading reading = ahead(0.0D, 1.0D, 150.0D, new Grid((x, y, z) -> y <= 69, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.CLEAR, reading.limit(),
                "a ship skimming the ground must not read the ground as ahead of it");
    }

    @Test
    void aHillBesideThePathIsNotInTheWay() {
        // A cliff running alongside, just clear of the hull's right side at x = 10.
        CruiseSensor.Reading reading = ahead(0.0D, 1.0D, 150.0D, new Grid((x, y, z) -> x >= 11, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.CLEAR, reading.limit());
    }

    @Test
    void aHillTheBowWillClipIsInTheWay() {
        // One block into the hull's width, over its whole height.
        CruiseSensor.Reading reading = ahead(0.0D, 1.0D, 150.0D,
                new Grid((x, y, z) -> x == 9 && z >= 100, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.TERRAIN, reading.limit());
    }

    @Test
    void unloadedGroundAheadStopsTheLook() {
        // Everything past chunk x = 3 (block 64) is not loaded; flying east from x = 10.
        CruiseSensor.Reading reading = ahead(1.0D, 0.0D, 200.0D,
                new Grid((x, y, z) -> false, chunk -> chunk <= 3));
        assertEquals(CruiseSensor.Limit.UNLOADED, reading.limit());
        assertTrue(reading.distance() <= 54.0D, "the ship may not count on ground it cannot see");
    }

    @Test
    void aDiagonalHeadingSeesAWallOnEitherLeadingFace() {
        double d = Math.sqrt(0.5D);
        // A wall across the Z axis only: a diagonal heading must still meet it with its Z face.
        CruiseSensor.Reading reading = ahead(d, d, 200.0D, new Grid((x, y, z) -> z == 60, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.TERRAIN, reading.limit());
        assertTrue(reading.distance() <= 40.0D / d, "reported " + reading.distance());
    }

    @Test
    void runningOutOfBudgetIsNotSeeingClear() {
        CruiseSensor.Reading reading = CruiseSensor.ahead(HULL[0], HULL[1], HULL[2], HULL[3], HULL[4], HULL[5],
                0.0D, 1.0D, 200.0D, 500L, new Grid((x, y, z) -> false, EVERYWHERE));
        assertEquals(CruiseSensor.Limit.UNLOADED, reading.limit(),
                "a look that gave up must slow the ship, not wave it through");
    }

    @Test
    void anotherShipAheadIsFoundByItsBox() {
        // A hull the same height, 30 blocks ahead of the bow and overlapping the path.
        double hit = CruiseSensor.boxHitDistance(HULL[0], HULL[1], HULL[2], HULL[3], HULL[4], HULL[5], 0.0D, 1.0D,
                5.0D, 72.0D, 50.0D, 25.0D, 80.0D, 70.0D, 200.0D);
        assertEquals(30.0D, hit, 1.0e-9D);
        // Beside the path: never met.
        double miss = CruiseSensor.boxHitDistance(HULL[0], HULL[1], HULL[2], HULL[3], HULL[4], HULL[5], 0.0D, 1.0D,
                12.0D, 72.0D, 50.0D, 25.0D, 80.0D, 70.0D, 200.0D);
        assertEquals(200.0D, miss, 1.0e-9D);
        // Above it: never met either.
        double over = CruiseSensor.boxHitDistance(HULL[0], HULL[1], HULL[2], HULL[3], HULL[4], HULL[5], 0.0D, 1.0D,
                5.0D, 90.0D, 50.0D, 25.0D, 99.0D, 70.0D, 200.0D);
        assertEquals(200.0D, over, 1.0e-9D);
    }
}
