package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.warp.CorridorTime;
import uk.co.iceconchy.aerowarptics.warp.CorridorTime.Scaling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long a warp lasts once a server lets distance decide.
 *
 * <p>A wrong figure here flies: the ship still arrives, only sooner or later than the setting said. And
 * the worst of them is not a wrong length at all but a warp that never ends, so the limit and the
 * overflow get the same attention as the curves.
 */
class CorridorTimeTest {

    private static final int BASE = 100;
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    @Test
    void withScalingOffEveryWarpLastsItsTiersOwnTime() {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            int base = tier.defaults().warpTicks();
            for (double distance : new double[]{0.0D, 128.0D, 4_000.0D, 2_000_000.0D}) {
                assertEquals(base, CorridorTime.ticks(base, distance, Scaling.NONE, 5.0D, NO_LIMIT),
                        tier + " at " + distance);
            }
        }
    }

    @Test
    void aMultiplierOfNothingIsScalingOff() {
        for (Scaling scaling : Scaling.values()) {
            assertEquals(BASE, CorridorTime.ticks(BASE, 50_000.0D, scaling, 0.0D, NO_LIMIT), scaling.toString());
        }
    }

    @Test
    void aStraightLineAddsTheSameForEveryKilometre() {
        // Half the tier's time again for each kilometre.
        assertEquals(150, CorridorTime.ticks(BASE, 1_000.0D, Scaling.LINEAR, 0.5D, NO_LIMIT));
        assertEquals(200, CorridorTime.ticks(BASE, 2_000.0D, Scaling.LINEAR, 0.5D, NO_LIMIT));
        assertEquals(600, CorridorTime.ticks(BASE, 10_000.0D, Scaling.LINEAR, 0.5D, NO_LIMIT));
    }

    @Test
    void theSquareRootDoublesTheExtraForFourTimesTheDistance() {
        assertEquals(200, CorridorTime.ticks(BASE, 1_000.0D, Scaling.SQUARE_ROOT, 1.0D, NO_LIMIT));
        assertEquals(300, CorridorTime.ticks(BASE, 4_000.0D, Scaling.SQUARE_ROOT, 1.0D, NO_LIMIT));
        assertEquals(500, CorridorTime.ticks(BASE, 16_000.0D, Scaling.SQUARE_ROOT, 1.0D, NO_LIMIT));
    }

    @Test
    void theLogarithmAddsTheSameForEachDoubling() {
        // One, three, seven kilometres: one, two and three doublings of "one plus the distance".
        assertEquals(200, CorridorTime.ticks(BASE, 1_000.0D, Scaling.LOGARITHMIC, 1.0D, NO_LIMIT));
        assertEquals(300, CorridorTime.ticks(BASE, 3_000.0D, Scaling.LOGARITHMIC, 1.0D, NO_LIMIT));
        assertEquals(400, CorridorTime.ticks(BASE, 7_000.0D, Scaling.LOGARITHMIC, 1.0D, NO_LIMIT));
    }

    @Test
    void theCurvesAgreeAtOneKilometreAndPartAfterIt() {
        // The multiplier means the same thing on each of them: what one kilometre adds.
        double linear = CorridorTime.factor(1_000.0D, Scaling.LINEAR, 0.25D);
        assertEquals(linear, CorridorTime.factor(1_000.0D, Scaling.SQUARE_ROOT, 0.25D), 1.0e-9D);
        assertEquals(linear, CorridorTime.factor(1_000.0D, Scaling.LOGARITHMIC, 0.25D), 1.0e-9D);
        // Past it a straight line is the steepest and the logarithm the gentlest, which is the whole
        // choice a server is making between them.
        double far = 100_000.0D;
        assertTrue(CorridorTime.factor(far, Scaling.LINEAR, 0.25D)
                > CorridorTime.factor(far, Scaling.SQUARE_ROOT, 0.25D));
        assertTrue(CorridorTime.factor(far, Scaling.SQUARE_ROOT, 0.25D)
                > CorridorTime.factor(far, Scaling.LOGARITHMIC, 0.25D));
    }

    @Test
    void scalingNeverMakesAWarpShorterAndFurtherIsNeverQuicker() {
        for (Scaling scaling : Scaling.values()) {
            int previous = BASE;
            for (double distance = 0.0D; distance <= 200_000.0D; distance += 250.0D) {
                int ticks = CorridorTime.ticks(BASE, distance, scaling, 0.3D, NO_LIMIT);
                assertTrue(ticks >= BASE, scaling + " made a warp of " + distance + " shorter than its tier's");
                assertTrue(ticks >= previous, scaling + " got quicker at " + distance);
                previous = ticks;
            }
        }
    }

    @Test
    void theLimitStopsAWarpGrowingForEver() {
        assertEquals(1_200, CorridorTime.ticks(BASE, 2_000_000.0D, Scaling.LINEAR, 1.0D, 1_200));
        // Short of the limit it is not rounded up to it.
        assertEquals(150, CorridorTime.ticks(BASE, 500.0D, Scaling.LINEAR, 1.0D, 1_200));
    }

    @Test
    void theLimitNeverCutsATierBelowItsOwnTime() {
        // A limit set under a tier's figure is a limit on the stretch, not a new, shorter tier.
        assertEquals(BASE, CorridorTime.ticks(BASE, 50_000.0D, Scaling.LINEAR, 1.0D, 40));
        assertEquals(BASE, CorridorTime.ticks(BASE, 0.0D, Scaling.LINEAR, 1.0D, 40));
    }

    @Test
    void anAbsurdJumpDoesNotOverflow() {
        // A hundred thousand kilometres at a multiplier of a hundred is far past what an int holds.
        int ticks = CorridorTime.ticks(12_000, 1.0e8D, Scaling.LINEAR, 100.0D, NO_LIMIT);
        assertEquals(Integer.MAX_VALUE, ticks);
        assertTrue(CorridorTime.ticks(12_000, 1.0e8D, Scaling.LINEAR, 100.0D, 72_000) == 72_000);
    }

    @Test
    void nonsenseInReadsAsNoDistance() {
        for (Scaling scaling : Scaling.values()) {
            assertEquals(BASE, CorridorTime.ticks(BASE, -500.0D, scaling, 1.0D, NO_LIMIT));
            assertEquals(BASE, CorridorTime.ticks(BASE, Double.NaN, scaling, 1.0D, NO_LIMIT));
            assertEquals(BASE, CorridorTime.ticks(BASE, 5_000.0D, scaling, -2.0D, NO_LIMIT));
            assertEquals(BASE, CorridorTime.ticks(BASE, 5_000.0D, scaling, Double.NaN, NO_LIMIT));
        }
        // A tier's time is never less than a tick, whatever it was handed.
        assertEquals(1, CorridorTime.ticks(0, 0.0D, Scaling.NONE, 1.0D, NO_LIMIT));
    }
}
