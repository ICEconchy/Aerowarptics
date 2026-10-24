package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.*;

/**
 * The Collapse as a set piece, held to its shape.
 *
 * <p>None of what goes wrong with a choreography throws: an offering that jumps at a beat, a rift that cuts
 * inside the hole it is closing onto, a hole that shrinks before the hush, a pull that pushes or a settled
 * collapse that hurts somebody. Each is an assertion here instead.
 */
class CollapseChoreographyTest {

    /** A tick of a minute-long ritual, as progress. */
    private static final double TICK = 1.0D / 1_200.0D;

    private static final double[][] DEPOTS = {{2, 0}, {-2, 1}, {0, 3}, {1, -3}, {3, 3}, {-3, -3}, {-1, 2}, {2, -2}};

    private static final double[] BEATS = {CollapseRules.SPIN_UP_END, ORBIT_FROM, CORE_RISEN, COLUMN_FROM, HOLE_FROM,
            FOLD, DISC_FROM, DISC_WHOLE, WINDING_FROM, HUSH};

    @Test
    void anOfferingLeavesFromWhereItsDepotShowsIt() {
        double[] slots = slotAngles(DEPOTS);
        for (int i = 0; i < DEPOTS.length; i++) {
            assertArrayEquals(new double[]{DEPOTS[i][0], DEPOT_REST, DEPOTS[i][1]},
                    offering(0.0D, DEPOTS[i], slots[i], 0.0D, 0.0D), 1.0e-9D, "offering " + i + " did not start on its depot");
        }
    }

    /** Across every beat, nothing that moves smoothly jumps - the rift and the pull are the two that mean to. */
    @Test
    void nothingJumpsAtABeat() {
        double[] slots = slotAngles(DEPOTS);
        double eps = 1.0e-7D;
        DoubleUnaryOperator[] curves = {
                CollapseChoreography::holeRadius, CollapseChoreography::orbitRadius, CollapseChoreography::column,
                CollapseChoreography::coreHeight, CollapseChoreography::coreScale, CollapseChoreography::heartHeight,
                CollapseChoreography::heartScale, CollapseChoreography::darkness, CollapseChoreography::disc,
                CollapseChoreography::lens, CollapseChoreography::offeringScale, CollapseChoreography::orbitTurns};
        for (double beat : BEATS) {
            for (DoubleUnaryOperator curve : curves) {
                assertEquals(curve.applyAsDouble(beat - eps), curve.applyAsDouble(beat + eps), 1.0e-4D,
                        "a curve jumps at " + beat);
            }
            for (int i = 0; i < DEPOTS.length; i++) {
                double[] before = offering(beat - eps, DEPOTS[i], slots[i], 0.2D, -0.1D);
                double[] after = offering(beat + eps, DEPOTS[i], slots[i], 0.2D, -0.1D);
                assertTrue(distance(before, after) < 1.0e-3D, "offering " + i + " jumps at " + beat);
            }
        }
    }

    /** Tick to tick through a whole ritual, no offering moves further than a thing in flight could. */
    @Test
    void noOfferingTeleports() {
        double[] slots = slotAngles(DEPOTS);
        for (int i = 0; i < DEPOTS.length; i++) {
            double[] last = offering(0.0D, DEPOTS[i], slots[i], 0.0D, 0.0D);
            for (double progress = TICK; progress <= 1.0D; progress += TICK) {
                double[] now = offering(progress, DEPOTS[i], slots[i], 0.0D, 0.0D);
                assertTrue(distance(last, now) < 0.6D, "offering " + i + " leapt " + distance(last, now)
                        + " blocks in a tick at " + progress);
                last = now;
            }
        }
    }

    @Test
    void everythingIsSwallowedByTheEnd() {
        assertEquals(0.0D, holeRadius(1.0D), 1.0e-9D, "the hole is still there at the collapse");
        assertEquals(0.0D, orbitRadius(1.0D), 1.0e-9D, "the orbit never closed");
        assertEquals(0.0D, offeringScale(1.0D), 1.0e-9D, "the offerings were never swallowed");
        assertEquals(0.0D, coreScale(1.0D), 1.0e-9D, "the Rift Core was never crushed");
        assertEquals(0.0D, heartScale(1.0D), 1.0e-9D, "the heart was never swallowed");
        assertEquals(CENTRE, heartHeight(1.0D), 1.0e-9D, "the heart was not drawn up into the point");
        assertEquals(0.0D, column(1.0D), 1.0e-9D, "the column never fell into the hole");
        assertArrayEquals(new double[]{0.0D, CENTRE, 0.0D}, offering(1.0D, DEPOTS[0], 0.4D, 0.0D, 0.0D), 1.0e-9D);
    }

    /**
     * The rift tears at the end of the spin-up, and closes down onto the hole without ever cutting inside it -
     * a rift smaller than the hole would show the hole's edge through the rift's own face - then seals at the fold.
     */
    @Test
    void theRiftClosesOntoTheHoleAndSealsAtTheFold() {
        assertEquals(0.0D, apertureRadius(CollapseRules.SPIN_UP_END - TICK), "the rift tore during the spin-up");
        assertEquals(APERTURE_FIRST, apertureRadius(CollapseRules.SPIN_UP_END), 1.0e-9D);
        for (double progress = CollapseRules.SPIN_UP_END; progress < FOLD; progress += TICK) {
            assertTrue(apertureRadius(progress) >= holeRadius(progress), "the rift cut inside the hole at " + progress);
        }
        assertEquals(holeRadius(FOLD) * APERTURE_OVER_HOLE, apertureRadius(FOLD - 1.0e-9D), 1.0e-6D,
                "the rift was not closed down onto the hole when it sealed");
        assertEquals(0.0D, apertureRadius(FOLD), "the rift never sealed");
        assertEquals(0.0D, apertureRadius(0.8D));
    }

    @Test
    void theHoleGrowsUntilTheHushAndThenFallsToAPoint() {
        double last = 0.0D;
        for (double progress = 0.0D; progress < HUSH; progress += TICK) {
            double now = holeRadius(progress);
            assertTrue(now >= last - 1.0e-12D, "the hole shrank before the hush, at " + progress);
            last = now;
        }
        last = holeRadius(HUSH);
        for (double progress = HUSH + TICK; progress <= 1.0D; progress += TICK) {
            double now = holeRadius(progress);
            assertTrue(now < last, "the hole grew in the hush, at " + progress);
            last = now;
        }
        assertEquals(0.0D, holeRadius(HOLE_FROM - TICK), "the hole was there before the rift");
    }

    @Test
    void theOrbitTightensAndQuickens() {
        double lastRadius = orbitRadius(ORBIT_FROM);
        double lastSpeed = orbitSpeed(ORBIT_FROM);
        for (double progress = ORBIT_FROM + TICK; progress <= 1.0D; progress += TICK) {
            assertTrue(orbitRadius(progress) <= lastRadius + 1.0e-12D, "the orbit widened at " + progress);
            assertTrue(orbitSpeed(progress) > lastSpeed, "the orbit slowed at " + progress);
            lastRadius = orbitRadius(progress);
            lastSpeed = orbitSpeed(progress);
        }
        assertEquals(0.0D, orbitTurns(ORBIT_FROM), 1.0e-12D, "the orbit was turning before anything was in it");
    }

    /** Every offering has a place of its own, evenly round the ring, handed out in the order the depots stand. */
    @Test
    void theSlotsAreEvenAndInDepotOrder() {
        double[] slots = slotAngles(DEPOTS);
        Integer[] byDepot = new Integer[DEPOTS.length];
        for (int i = 0; i < byDepot.length; i++) {
            byDepot[i] = i;
        }
        java.util.Arrays.sort(byDepot, java.util.Comparator.comparingDouble(i -> Math.atan2(DEPOTS[i][1], DEPOTS[i][0])));
        for (int k = 1; k < byDepot.length; k++) {
            assertEquals(Math.PI * 2.0D / DEPOTS.length, slots[byDepot[k]] - slots[byDepot[k - 1]], 1.0e-9D,
                    "the ring's places are uneven or out of the depots' order");
        }
        assertEquals(0, slotAngles(new double[0][]).length);
    }

    /** The orbit's plane, however it is tipped, passes through the middle of the hole. */
    @Test
    void theOrbitIsAPlaneThroughTheHole() {
        for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += 0.3D) {
            double[] here = orbitPoint(1.5D, angle, 0.3D, -0.2D);
            double[] opposite = orbitPoint(1.5D, angle + Math.PI, 0.3D, -0.2D);
            assertEquals(CENTRE, (here[1] + opposite[1]) / 2.0D, 1.0e-9D);
            assertEquals(CENTRE, orbitPoint(1.5D, angle, 0.0D, 0.0D)[1], 1.0e-12D);
        }
    }

    @Test
    void thePullWaitsForTheWindingAndGrowsHardInTheHush() {
        assertEquals(0.0D, pull(WINDING_FROM - TICK), "things were pulled before the rift was open");
        assertTrue(pull(0.5D) > 0.0D);
        assertTrue(pull(0.9D) > pull(0.5D));
        assertTrue(pull(HUSH) > pull(HUSH - TICK));
        assertEquals(0.0D, pullReach(0.0D, 12.0D), "the axis itself is pulled - what arrives there would jitter");
        assertEquals(0.0D, pullReach(12.0D, 12.0D), 1.0e-12D);
        assertEquals(0.0D, pullReach(20.0D, 12.0D), 1.0e-12D);
        assertEquals(0.0D, pullReach(5.0D, 0.0D), "a range of nothing still pulled");
        assertTrue(pullReach(3.0D, 12.0D) > pullReach(9.0D, 12.0D), "the pull does not fall off with distance");
    }

    @Test
    void thePullNeverPushesAndNeverOutrunsItsCap() {
        java.util.Random random = new java.util.Random(7);
        for (int trial = 0; trial < 2_000; trial++) {
            double vx = random.nextGaussian() * 0.3D;
            double vz = random.nextGaussian() * 0.3D;
            double toX = random.nextGaussian() * 5.0D;
            double toZ = random.nextGaussian() * 5.0D;
            double length = Math.sqrt(toX * toX + toZ * toZ);
            double before = (vx * toX + vz * toZ) / length;
            double[] after = pulled(vx, vz, toX, toZ, PULL_HUSH, PULL_CAP);
            double inward = (after[0] * toX + after[1] * toZ) / length;
            assertTrue(inward >= before - 1.0e-12D, "the pull pushed something outward");
            assertTrue(inward <= Math.max(before, PULL_CAP) + 1.0e-12D, "the pull outran its cap");
            // Only ever along the way in: nothing is turned aside.
            double sideBefore = (vx * -toZ + vz * toX) / length;
            double sideAfter = (after[0] * -toZ + after[1] * toX) / length;
            assertEquals(sideBefore, sideAfter, 1.0e-9D, "the pull turned something aside");
        }
    }

    /**
     * On the ground, the hardest pull drifts a player in slower than they can walk away: vanilla's ground
     * friction keeps a steady push at {@code push / (1 - 0.546)}, and a player walks at about 0.216 blocks a tick.
     */
    @Test
    void aPlayerCanAlwaysWalkAway() {
        double groundDrag = 0.91D * 0.6D;
        double walking = 0.216D;
        assertTrue(PULL_HUSH / (1.0D - groundDrag) < walking, "the hush drags a walking player in");
        assertTrue(PULL_LAST / (1.0D - groundDrag) < walking * 0.25D, "the winding is more than a nudge");
        assertTrue(PULL_CAP < walking, "the winding can draw a player in faster than they walk");
    }

    @Test
    void aSettledCollapseHurtsNobodyAndAnUnsteadyOneHurtsLessFurtherOff() {
        assertEquals(0.0D, damage(0.0D, 8.0D, 14.0D, CollapseRules.Outcome.SETTLED), "a settled collapse hurt someone");
        assertTrue(damage(2.0D, 8.0D, 14.0D, CollapseRules.Outcome.SPAT) > damage(2.0D, 8.0D, 14.0D, CollapseRules.Outcome.FLUNG));
        assertTrue(damage(2.0D, 8.0D, 14.0D, CollapseRules.Outcome.FLUNG) > 0.0D);
        assertTrue(damage(1.0D, 8.0D, 14.0D, CollapseRules.Outcome.SPAT) > damage(6.0D, 8.0D, 14.0D, CollapseRules.Outcome.SPAT));
        assertEquals(14.0D, damage(0.0D, 8.0D, 14.0D, CollapseRules.Outcome.SPAT), 1.0e-9D);
        assertEquals(0.0D, damage(8.0D, 8.0D, 14.0D, CollapseRules.Outcome.SPAT), "harm at the edge of the blast");
        assertEquals(0.0D, damage(1.0D, 0.0D, 14.0D, CollapseRules.Outcome.SPAT), "harm with the blast turned off");
        assertEquals(0.0D, damage(1.0D, 8.0D, 0.0D, CollapseRules.Outcome.SPAT), "harm with the harm turned off");
    }

    @Test
    void theBlastThrowsOutwardAndHarderTheWorseItWent() {
        for (CollapseRules.Outcome outcome : CollapseRules.Outcome.values()) {
            assertTrue(knockback(1.0D, 8.0D, outcome) > knockback(6.0D, 8.0D, outcome));
            assertEquals(0.0D, knockback(8.0D, 8.0D, outcome));
            assertEquals(0.0D, knockback(1.0D, 0.0D, outcome));
            assertEquals(0.0D, knockUp(9.0D, 8.0D));
        }
        assertTrue(knockback(2.0D, 8.0D, CollapseRules.Outcome.SPAT) > knockback(2.0D, 8.0D, CollapseRules.Outcome.FLUNG));
        assertTrue(knockback(2.0D, 8.0D, CollapseRules.Outcome.FLUNG) > knockback(2.0D, 8.0D, CollapseRules.Outcome.SETTLED));
        assertTrue(shake(2.0D, CollapseRules.Outcome.SPAT) > shake(40.0D, CollapseRules.Outcome.SPAT));
        assertEquals(0.0D, shake(SHAKE_GONE_BEYOND, CollapseRules.Outcome.SPAT), 1.0e-12D);
    }

    @Test
    void theHeartQuickensAndSoDoesTheLightning() {
        assertEquals(BEAT_SLOW, heartbeat(0.05D, 0.0D));
        assertTrue(heartbeat(0.9D, 0.0D) < heartbeat(0.4D, 0.0D), "the heart does not quicken as it winds");
        assertTrue(heartbeat(0.6D, 1.0D) < heartbeat(0.6D, 0.0D), "the heart does not quicken when unsteady");
        assertTrue(arcPeriod(0.9D, 0.0D) < arcPeriod(0.4D, 0.0D));
        assertTrue(arcPeriod(0.6D, 1.0D) < arcPeriod(0.6D, 0.0D));
        for (double progress = 0.0D; progress <= 1.0D; progress += 0.01D) {
            assertTrue(heartbeat(progress, 1.0D) >= BEAT_FASTEST);
            assertTrue(arcPeriod(progress, 1.0D) >= ARC_FASTEST);
        }
    }

    @Test
    void theDayDimsAsItGoesAndTheDimmingReachesALongWay() {
        double last = -1.0D;
        for (double progress = 0.0D; progress <= 1.0D; progress += TICK) {
            double now = darkness(progress);
            assertTrue(now >= last - 1.0e-12D && now <= 1.0D, "the day brightened mid-ritual at " + progress);
            last = now;
        }
        assertEquals(1.0D, darknessReach(0.0D), 1.0e-12D);
        assertEquals(1.0D, darknessReach(DIM_FULL_WITHIN), 1.0e-12D);
        assertEquals(0.0D, darknessReach(DIM_GONE_BEYOND), 1.0e-12D);
        assertTrue(darknessReach(64.0D) > 0.5D, "a landmark dims so little of the country round it");
    }

    // ------------------------------------------------------------------ the cradle, read

    /** The blast goes before anything is thrown out, so the collapse never throws its own results about. */
    @Test
    void theBlastGoesBeforeAnythingIsThrownOut() {
        String cradle = cradle();
        int finish = cradle.indexOf("private void finish(");
        int blast = cradle.indexOf("blast(server, outcome);", finish);
        int thrown = cradle.indexOf("throwOut(", finish);
        int flung = cradle.indexOf("fling(server", finish);
        assertTrue(blast > finish && blast < thrown && blast < flung, "the blast comes after the throw");
    }

    /** A player's movement is the player's: the server draws in everything but players, and their clients them. */
    @Test
    void theServerNeverPullsAPlayer() {
        String cradle = cradle();
        String pullable = cradle.substring(cradle.indexOf("private static boolean pullable("),
                cradle.indexOf("private static boolean movable("));
        assertTrue(pullable.contains("!(entity instanceof Player)"), "the server pulls players too");
        String spectacle = read("src/main/java/uk/co/iceconchy/aerowarptics/client/fx/CollapseSpectacle.java");
        assertTrue(spectacle.contains("CollapseChoreography.pulled("), "no client pulls its own player");
    }

    /** A collapse is not an explosion: no block is broken, and nothing but a living thing is ever hurt. */
    @Test
    void theBlastBreaksNoBlockAndHurtsOnlyTheLiving() {
        String cradle = cradle();
        int start = cradle.indexOf("private void blast(");
        String blast = cradle.substring(start, cradle.indexOf("\n    }\n", start));
        for (String breaking : new String[]{"setBlock", "destroyBlock", "removeBlock", ".explode("}) {
            assertTrue(!blast.contains(breaking), "the blast calls " + breaking);
        }
        int hurt = blast.indexOf(".hurt(");
        assertTrue(hurt > blast.indexOf("instanceof LivingEntity living"), "the blast hurts something that is not alive");
        String movable = cradle.substring(cradle.indexOf("private static boolean movable("));
        movable = movable.substring(0, movable.indexOf("\n    }\n"));
        assertTrue(movable.contains("!(entity instanceof ArmorStand)"), "the blast would knock down armour stands");
    }

    private static String cradle() {
        return read("src/main/java/uk/co/iceconchy/aerowarptics/collapse/CollapseCradleBlockEntity.java");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static double distance(double[] a, double[] b) {
        double x = a[0] - b[0];
        double y = a[1] - b[1];
        double z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }
}
