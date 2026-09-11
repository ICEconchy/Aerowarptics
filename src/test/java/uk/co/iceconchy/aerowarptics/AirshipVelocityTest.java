package uk.co.iceconchy.aerowarptics;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The velocity ceiling that catches the yeet.
 *
 * <p>Even with the departure and arrival paths proven clear, a solver glitch, a frame slip in the
 * emerge command, or momentum that stacked could still hand the pipeline an enormous velocity and
 * fling the hull across the world. The clamp is the backstop, and this pins down that it caps the
 * speed without bending the bearing, leaves an ordinary command untouched, and never turns a zero or a
 * NaN into a division by zero.
 */
class AirshipVelocityTest {

    private static final double EPSILON = 1.0e-9D;
    private static final double CEILING = 48.0D;

    @Test
    void aSpeedUnderTheCeilingIsLeftAlone() {
        Vector3d command = new Vector3d(3.0D, 0.0D, 4.0D); // length 5
        Vector3d clamped = Airship.clampSpeed(command, CEILING);
        assertEquals(5.0D, clamped.length(), EPSILON, "a legitimate passage speed must not be touched");
        assertEquals(command.x, clamped.x, EPSILON);
        assertEquals(command.y, clamped.y, EPSILON);
        assertEquals(command.z, clamped.z, EPSILON);
    }

    @Test
    void aSpeedAboveTheCeilingIsScaledToExactlyTheCeiling() {
        Vector3d command = new Vector3d(9000.0D, 0.0D, 0.0D); // a yeet
        Vector3d clamped = Airship.clampSpeed(command, CEILING);
        assertEquals(CEILING, clamped.length(), 1.0e-6D, "an ejection speed must be capped at the ceiling");
    }

    @Test
    void theBearingSurvivesTheClamp() {
        Vector3d command = new Vector3d(300.0D, -400.0D, 1200.0D);
        Vector3d clamped = Airship.clampSpeed(command, CEILING);
        // Same direction: the normalised vectors agree.
        Vector3d a = new Vector3d(command).normalize();
        Vector3d b = new Vector3d(clamped).normalize();
        assertEquals(a.x, b.x, 1.0e-6D);
        assertEquals(a.y, b.y, 1.0e-6D);
        assertEquals(a.z, b.z, 1.0e-6D);
        assertEquals(CEILING, clamped.length(), 1.0e-6D);
    }

    @Test
    void aStandstillCommandIsNotADivisionByZero() {
        Vector3d clamped = Airship.clampSpeed(new Vector3d(0.0D, 0.0D, 0.0D), CEILING);
        assertTrue(Double.isFinite(clamped.length()), "a zero command must not produce a NaN");
        assertEquals(0.0D, clamped.length(), EPSILON);
    }

    @Test
    void aNonFiniteCommandIsLeftAloneRatherThanScaled() {
        Vector3d command = new Vector3d(Double.POSITIVE_INFINITY, 0.0D, 0.0D);
        Vector3d clamped = Airship.clampSpeed(command, CEILING);
        // Scaling by ceiling/Inf would be zero, which would hide the fault; leaving it lets the
        // reported-velocity abort see it and put the hull back.
        assertTrue(Double.isInfinite(clamped.x), "a non-finite command should pass through unchanged");
    }

    /**
     * The emerge command stays bounded however far the hull has to coast.
     *
     * <p>{@code tickEmerge} commands {@code remaining * emergeFraction}. A frame slip that made
     * {@code remaining} a plot coordinate rather than a world one would be an over-command of
     * astronomical size; the clamp is what makes even that bounded before the pipeline sees it.
     */
    @Test
    void theEmergeCommandCannotOverCommandPastTheCeiling() {
        for (double remaining : new double[]{8.0D, 64.0D, 512.0D, 20_000_000.0D}) {
            for (int ticksLeft = 1; ticksLeft <= 40; ticksLeft++) {
                double fraction = WarpFlight.emergeFraction(ticksLeft);
                Vector3d command = new Vector3d(remaining * fraction, 0.0D, 0.0D);
                double speed = Airship.clampSpeed(command, CEILING).length();
                assertTrue(speed <= CEILING + 1.0e-6D,
                        "emerge command for remaining=" + remaining + " ticksLeft=" + ticksLeft
                                + " was " + speed);
            }
        }
    }

    /** The configured ceiling falls back to a sane default when the config is not loaded, as in tests. */
    @Test
    void theCeilingHasASafeDefaultWithoutTheConfig() {
        assertEquals(CEILING, Airship.maxCommandedSpeed(), EPSILON,
                "the fallback ceiling must match the config default so a test-time clamp is realistic");
    }

    /** A copy is always returned, never the caller's own vector aliased back. */
    @Test
    void theResultIsAlwaysAFreshVector() {
        Vector3d command = new Vector3d(1.0D, 2.0D, 3.0D);
        assertSame(Vector3d.class, Airship.clampSpeed(command, CEILING).getClass());
        assertTrue(command != Airship.clampSpeed(command, CEILING), "must not alias the input");
    }
}
