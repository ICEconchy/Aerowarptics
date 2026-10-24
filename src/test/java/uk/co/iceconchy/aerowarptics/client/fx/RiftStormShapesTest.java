package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shapes of a storm sky's tears, vortex, streaks and flashes.
 *
 * <p>Two failures matter and neither throws. A shape that jumps pops on screen - a tear snapping open, a
 * flash cutting out mid-flicker, a streak's tail ending square. A shape that does not come to nothing at its
 * end leaves something hanging in the sky: a tear that never quite closes, a vortex with a hard rim. So each
 * is sampled finely across its whole range, for bounds, for its ends, and for jumps between neighbours.
 */
class RiftStormShapesTest {

    private static final int SAMPLES = 20_000;
    private static final float EPSILON = 1.0e-6F;

    /** Samples a shape across {@code [from, to]} and fails on any value outside [0, 1] or step larger than {@code step}. */
    private static void boundedAndContinuous(String name, DoubleUnaryOperator shape, double from, double to, double step) {
        double previous = shape.applyAsDouble(from);
        for (int sample = 0; sample <= SAMPLES; sample++) {
            double x = from + (to - from) * sample / SAMPLES;
            double value = shape.applyAsDouble(x);
            assertTrue(value >= -EPSILON && value <= 1.0D + EPSILON, name + " left [0, 1] at " + x + ": " + value);
            assertTrue(Math.abs(value - previous) <= step, name + " jumped at " + x + ": " + previous + " to " + value);
            previous = value;
        }
    }

    @Test
    void aTearOpensAndClosesSmoothly() {
        boundedAndContinuous("tear opening", p -> RiftStormShapes.tearOpening((float) p), -0.1D, 1.1D, 0.01D);
        boundedAndContinuous("tear seam", p -> RiftStormShapes.tearSeam((float) p), -0.1D, 1.1D, 0.01D);
    }

    @Test
    void aTearIsASeamAtBothEndsOfItsLife() {
        assertEquals(0.0F, RiftStormShapes.tearOpening(0.0F));
        assertEquals(0.0F, RiftStormShapes.tearOpening(RiftStormShapes.TEAR_SEAM));
        assertEquals(1.0F, RiftStormShapes.tearOpening(0.5F), EPSILON, "a tear half way through its life is open");
        assertEquals(0.0F, RiftStormShapes.tearOpening(RiftStormShapes.TEAR_HEAL));
        assertEquals(0.0F, RiftStormShapes.tearOpening(1.0F));
        // And the seam outlasts the opening: it is there before it parts and after it heals.
        assertTrue(RiftStormShapes.tearSeam(RiftStormShapes.TEAR_SEAM * 0.9F) > 0.9F);
        assertTrue(RiftStormShapes.tearSeam((RiftStormShapes.TEAR_HEAL + 1.0F) / 2.0F) > 0.0F);
        assertEquals(0.0F, RiftStormShapes.tearSeam(0.0F));
        assertEquals(0.0F, RiftStormShapes.tearSeam(1.0F));
    }

    @Test
    void aTearIsASlitNotABox() {
        assertEquals(0.0F, RiftStormShapes.tearWidth(0.0F));
        assertEquals(0.0F, RiftStormShapes.tearWidth(1.0F));
        assertEquals(1.0F, RiftStormShapes.tearWidth(0.5F), EPSILON);
        boundedAndContinuous("tear width", a -> RiftStormShapes.tearWidth((float) a), 0.0D, 1.0D, 0.02D);
    }

    @Test
    void theVortexHasAClearEyeAndNoHardRim() {
        for (float angle = 0.0F; angle < 6.3F; angle += 0.1F) {
            assertEquals(0.0F, RiftStormShapes.vortexShade(0.0F, angle, 0.0F), "cloud in the eye at " + angle);
            assertEquals(0.0F, RiftStormShapes.vortexShade(1.0F, angle, 0.0F), "a hard rim at " + angle);
            assertEquals(0.0F, RiftStormShapes.vortexShade(1.5F, angle, 0.0F), "cloud past the rim at " + angle);
            final float bearing = angle;
            boundedAndContinuous("vortex radially at " + angle,
                    r -> RiftStormShapes.vortexShade((float) r, bearing, 0.7F), 0.0D, 1.0D, 0.02D);
        }
        for (float r = 0.1F; r < 1.0F; r += 0.1F) {
            final float radius = r;
            boundedAndContinuous("vortex round at " + r,
                    a -> RiftStormShapes.vortexShade(radius, (float) a, 0.7F), 0.0D, Math.PI * 2.0D, 0.02D);
        }
    }

    @Test
    void theVortexClosesRoundWithoutASeam() {
        // The mesh's first and last sectors meet at angle zero and a full turn: they must agree there.
        for (float r = 0.05F; r < 1.0F; r += 0.05F) {
            assertEquals(RiftStormShapes.vortexShade(r, 0.0F, 1.3F),
                    RiftStormShapes.vortexShade(r, (float) (Math.PI * 2.0D), 1.3F), 1.0e-4F, "a seam at radius " + r);
        }
    }

    @Test
    void theVortexHasArmsNotAnEvenHaze() {
        float least = 1.0F;
        float most = 0.0F;
        for (float angle = 0.0F; angle < 6.3F; angle += 0.01F) {
            float shade = RiftStormShapes.vortexShade(0.4F, angle, 0.0F);
            least = Math.min(least, shade);
            most = Math.max(most, shade);
        }
        assertTrue(most > least * 2.0F, "round the vortex its arms should be at least twice as dark as the gaps");
    }

    @Test
    void theEyeFadesToNothingAtItsEdge() {
        assertEquals(1.0F, RiftStormShapes.vortexEye(0.0F), EPSILON);
        assertEquals(0.0F, RiftStormShapes.vortexEye(1.0F));
        boundedAndContinuous("eye", r -> RiftStormShapes.vortexEye((float) r), 0.0D, 1.2D, 0.01D);
    }

    @Test
    void aStreaksTrailTapersToNothing() {
        assertEquals(1.0F, RiftStormShapes.streakTrail(0.0F));
        assertEquals(0.0F, RiftStormShapes.streakTrail(1.0F));
        boundedAndContinuous("trail", s -> RiftStormShapes.streakTrail((float) s), 0.0D, 1.0D, 0.01D);
        boundedAndContinuous("streak life", p -> RiftStormShapes.streakLife((float) p), -0.1D, 1.1D, 0.01D);
        assertEquals(0.0F, RiftStormShapes.streakLife(0.0F));
        assertEquals(0.0F, RiftStormShapes.streakLife(1.0F));
    }

    @Test
    void aCloudFlashFlickersAndGoesOut() {
        for (int pulses = 1; pulses <= 4; pulses++) {
            final int count = pulses;
            boundedAndContinuous(pulses + " pulses", p -> RiftStormShapes.sheetFlicker((float) p, count),
                    -0.1D, 1.1D, 0.01D);
            assertEquals(0.0F, RiftStormShapes.sheetFlicker(0.0F, pulses));
            assertEquals(0.0F, RiftStormShapes.sheetFlicker(1.0F, pulses));
            // Dark between pulses, which is what makes it flicker rather than swell.
            for (int between = 1; between < pulses; between++) {
                assertEquals(0.0F, RiftStormShapes.sheetFlicker(between / (float) pulses, pulses), 1.0e-5F,
                        "no dark between pulse " + between + " and the next of " + pulses);
            }
        }
    }

    @Test
    void eachPulseIsWeakerThanTheOneBefore() {
        float first = RiftStormShapes.sheetFlicker(1.0F / 6.0F, 3);
        float second = RiftStormShapes.sheetFlicker(3.0F / 6.0F, 3);
        float third = RiftStormShapes.sheetFlicker(5.0F / 6.0F, 3);
        assertTrue(first > second && second > third, first + ", " + second + ", " + third);
    }
}
