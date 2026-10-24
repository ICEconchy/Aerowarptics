package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.co.iceconchy.aerowarptics.collapse.CradleWorks.*;

/**
 * The Collapse Cradle as a machine, held to its shape.
 *
 * <p>Nothing about a machine's motion throws when it goes wrong: a part that snaps from one pose to the next at a
 * beat, a piston out of its cylinder, a ring that stops short of flat. Each is an assertion here.
 */
class CradleWorksTest {

    /** A tick of a minute-long ritual, as progress. */
    private static final double TICK = 1.0D / 1_200.0D;
    private static final double AFTER_TICK = 1.0D / RETURN_TICKS;

    private static final double[] BEATS = {CollapseChoreography.CORE_LIFT_FROM, CollapseChoreography.CORE_HANDED_OFF,
            RELEASED, TUMBLE_FROM, TUMBLE_FULL, UNLOCKED,
            CollapseChoreography.WINDING_FROM, CollapseChoreography.HUSH};


    // ------------------------------------------------------------------ continuity

    @Test
    void nothingJumpsAtABeat() {
        double eps = 1.0e-7D;
        for (double beat : BEATS) {
            assertEquals(lock(beat - eps, true, 1.0D), lock(beat + eps, true, 1.0D), 1.0e-4D, "the lock jumps at " + beat);
            assertEquals(work(beat - eps, true), work(beat + eps, true), 1.0e-4D, "the pistons jump at " + beat);
            // The drive cuts out at the hush on purpose - it is what brakes the rings, which never jump (see
            // theRingsBrakeFlatInTheHush). Everywhere else it only ever eases.
            if (beat != CollapseChoreography.HUSH) {
                assertEquals(tumble(beat - eps, true), tumble(beat + eps, true), 1.0e-4D, "the rings jump at " + beat);
            }
        }
    }

    // ------------------------------------------------------------------ a fold

    /** Through a fold the outer ring whirls and the inner two lie flat round what is being folded. */
    @Test
    void theRingsLieFlatThroughAFold() {
        for (double rpm : new double[]{256.0D, -256.0D, 32.0D}) {
            Rings rings = new Rings();
            for (int tick = 0; tick <= CradleFold.SHORTEST; tick++) {
                double yaw = rings.yaw(1.0F);
                rings.tick(rpm, 0.0D, false, tick / (double) CradleFold.SHORTEST);
                assertTrue(rings.parked(), "a ring tumbles through a fold at tick " + tick + " at " + rpm);
                assertTrue(Math.abs(rings.yaw(1.0F) - yaw) <= FASTEST_TUMBLE, "the outer ring whips round at " + rpm);
            }
        }
    }

    @Test
    void whatIsFoldedGoesIntoTheRiftAndComesOutMade() {
        assertEquals(CENTRE, foldHeight((FOLD_RISEN + FOLD_DESCENT) / 2.0D), 1.0e-9D, "the fold is not at the point");
        assertEquals(0.0D, foldScale(FOLD_FLASH - 1.0e-9D), 1.0e-6D, "it has not shrunk away by the flash");
        assertEquals(1.0D, foldScale(0.0D), 1.0e-9D);
        assertEquals(1.0D, foldScale(1.0D), 1.0e-9D);
        assertTrue(folded(FOLD_FLASH) && !folded(FOLD_FLASH - 1.0e-9D));
        assertTrue(foldRift(FOLD_FLASH) > 0.0D, "the rift is shut at the fold");
        assertEquals(0.0D, foldRift(0.0D), 0.0D);
        assertEquals(0.0D, foldRift(1.0D), 0.0D);
        for (double f = 0.0D; f <= 1.0D; f += 0.001D) {
            double glow = foldGlow(f);
            assertTrue(glow >= 0.0D && glow <= 1.0D, "fold glow " + glow + " at " + f);
            assertTrue(foldRift(f) >= 0.0D && foldRift(f) <= FOLD_RIFT + 1.0e-9D, "the rift overgrows at " + f);
        }
    }

    // ------------------------------------------------------------------ failing

    @Test
    void aSteadyCollapseDoesNotFail() {
        for (double heading = 0.0D; heading <= 0.3D; heading += 0.01D) {
            assertEquals(0.0D, strain(heading), 0.0D, "the machine fails heading for " + heading);
        }
        assertEquals(1.0D, strain(0.9D), 1.0e-9D);
        assertEquals(1.0D, strain(1.0D), 1.0e-9D);
        double last = 0.0D;
        for (double heading = 0.0D; heading <= 1.0D; heading += 0.01D) {
            assertTrue(strain(heading) >= last - 1.0e-12D, "the strain eases as it gets worse, at " + heading);
            last = strain(heading);
        }
        for (long tick = 0; tick < 100; tick++) {
            assertArrayEquals(new double[3], shudder(0.0D, tick), 0.0D);
            assertEquals(0.0D, misfire(0.0D, tick, 2), 0.0D);
            assertEquals(1.0D, flicker(0.0D, tick), 0.0D);
        }
        assertArrayEquals(new double[]{1.0D, 1.0D, 1.0D}, hot(0.0D), 0.0D);
    }

    /** The same moment always jolts the same way, so every client and every redraw of a tick agree. */
    @Test
    void theJoltsAreRepeatableAndBounded() {
        for (long tick = -50; tick < 5_000; tick += 7) {
            for (int stream = 0; stream < 40; stream++) {
                double value = jitter(tick, stream);
                assertTrue(value >= -1.0D && value <= 1.0D, "jitter " + value);
                assertEquals(value, jitter(tick, stream), 0.0D);
            }
            for (double axis : shudder(1.0D, tick)) {
                assertTrue(Math.abs(axis) <= MOST_SHUDDER, "the machine shudders further than it should");
            }
            assertTrue(misfire(1.0D, tick, 0) >= 0.0D && misfire(1.0D, tick, 0) <= 1.0D);
            double light = flicker(1.0D, tick);
            assertTrue(light >= 0.0D && light <= 1.0D);
        }
        for (double channel : hot(1.0D)) {
            assertTrue(channel >= 0.0D && channel <= 1.0D);
        }
    }

    // ------------------------------------------------------------------ the roof

    @Test
    void theLockTurnsBackOnceItIsOver() {
        assertEquals(lock(1.0D, true, 1.0D), lock(0.0D, false, 0.0D), 1.0e-12D);
        assertEquals(0.0D, lock(0.0D, false, 1.0D), 1.0e-12D);
    }

    // ------------------------------------------------------------------ the plinth

    @Test
    void thePistonsStayInTheirCylinders() {
        for (int index = 0; index < 4; index++) {
            for (double shaft = 0.0D; shaft < 40.0D; shaft += 0.05D) {
                double sunk = piston(index, shaft, 1.0D);
                assertTrue(sunk <= 0.0D && sunk >= -PISTON_STROKE, "piston " + index + " leaves its stroke");
            }
        }
        assertEquals(0.0D, work(CollapseChoreography.HUSH + 0.02D, true), 1.0e-9D, "the pistons still pump in the hush");
    }

    @Test
    void theGlowStaysALight() {
        for (double progress = 0.0D; progress <= 1.0D; progress += TICK) {
            for (double beat : new double[]{0.0D, 1.0D}) {
                double glow = glow(progress, true, 1.0D, beat);
                assertTrue(glow >= 0.0D && glow <= 1.0D, "glow " + glow + " at " + progress);
            }
        }
        assertEquals(glow(0.0D, false, 1.0D, 0.0D), glow(0.3D, false, 1.0D, 0.0D), 1.0e-12D);
    }

    // ------------------------------------------------------------------ the rings

    /** Before the core is among them the rings are not driven to tumble, and at rest they only drift. */
    @Test
    void theRingsWaitForTheCore() {
        for (double progress = 0.0D; progress < TUMBLE_FROM; progress += TICK) {
            assertEquals(0.0D, tumble(progress, true), 0.0D, "the rings tumble before the core is in at " + progress);
        }
        assertEquals(0.0D, tumble(0.5D, false), 0.0D);
        assertEquals(0.0D, tumble(CollapseChoreography.HUSH, true), 0.0D, "the rings are still driven in the hush");
    }

    /**
     * A whole ritual, turned at the fastest a Singularity asks for and at the slowest, full length and cut short
     * by a storm: the rings never jump from one tick to the next, and are lying flat and still by the end.
     */
    @Test
    void theRingsBrakeFlatInTheHush() {
        for (double rpm : new double[]{256.0D, -256.0D, 128.0D}) {
            for (int length : new int[]{1_200, CollapseRules.ritualTicks(1_200, true)}) {
                Rings rings = new Rings();
                for (int tick = 0; tick <= length; tick++) {
                    double beforePitch = rings.pitch(1.0F);
                    double beforeRoll = rings.roll(1.0F);
                    rings.tick(rpm, tick / (double) length, true);
                    assertTrue(Math.abs(rings.pitch(1.0F) - beforePitch) <= FASTEST_TUMBLE + 1.0e-9D,
                            "the middle ring jumps at tick " + tick + " of " + length + " at " + rpm);
                    assertTrue(Math.abs(rings.roll(1.0F) - beforeRoll) <= FASTEST_TUMBLE + 1.0e-9D,
                            "the inner ring jumps at tick " + tick + " of " + length + " at " + rpm);
                }
                assertTrue(rings.parked(), "the rings are not flat at the end of a " + length + "-tick ritual at "
                        + rpm + " rpm: " + rings.pitch(1.0F) + ", " + rings.roll(1.0F));
            }
        }
    }

    /** However fast a ring was going, braking brings it to a flat position ahead of it, and stops there. */
    @Test
    void aBrakingRingStopsFlatWithoutGoingBack() {
        for (double rate : new double[]{0.4D, -0.4D, 0.13D, -0.02D, 0.0D, 0.004D}) {
            for (double start : new double[]{0.0D, 1.0D, -2.5D, 40.0D}) {
                Ring ring = new Ring();
                ring.angle = start;
                ring.rate = rate;
                double direction = Math.signum(rate);
                double previous = ring.angle;
                for (int tick = 0; tick < 200 && !ring.parked(); tick++) {
                    ring.settle();
                    if (direction != 0.0D) {
                        assertTrue((ring.angle - previous) * direction >= -1.0e-12D, "a braking ring turned back");
                    }
                    previous = ring.angle;
                }
                assertTrue(ring.parked(), "a ring at " + start + " turning " + rate + " never came to rest flat");
                assertEquals(0.0D, Math.IEEEremainder(ring.angle, Math.PI), 1.0e-9D);
            }
        }
    }

    @Test
    void aDrivenRingIsNeverDrivenPastItsFastest() {
        Ring ring = new Ring();
        ring.drive(3.0D);
        assertEquals(FASTEST_TUMBLE, ring.angle, 1.0e-12D);
        ring.drive(-3.0D);
        assertEquals(0.0D, ring.angle, 1.0e-12D);
    }

    /** Taking whole turns off a long-spinning ring moves where it was a tick ago with it, so nothing sweeps round. */
    @Test
    void keepingARingSmallNeverSpinsItBetweenTicks() {
        Ring ring = new Ring();
        for (int tick = 0; tick < 5_000; tick++) {
            ring.drive(FASTEST_TUMBLE);
            assertEquals(FASTEST_TUMBLE, ring.angle - ring.last, 1.0e-9D, "a ring swept round at tick " + tick);
        }
    }
}
