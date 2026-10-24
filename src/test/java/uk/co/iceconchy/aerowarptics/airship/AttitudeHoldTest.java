package uk.co.iceconchy.aerowarptics.airship;

import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holding a hull at its launch attitude while a drive flies it.
 *
 * <p>The report was a ship coming out of the far rift tilted. The drive zeroes a hull's spin every tick,
 * which looks as though it should stop any turning; what it does not do is undo the turn a hull has
 * already made, and Sable's lift blocks twist a hull hard at warp speed in the two physics substeps
 * between one zeroing and the next. What is pinned here is that arithmetic, and that the hold which
 * replaces it measures an attitude honestly and lets go when nothing renews it.
 */
class AttitudeHoldTest {

    private static final double EPSILON = 1.0e-9D;

    @AfterEach
    void forgetEveryHold() {
        for (int tick = 0; tick <= AttitudeHold.GRACE_TICKS; tick++) {
            AttitudeHold.expire();
        }
    }

    /**
     * Why zeroing the spin was not enough.
     *
     * <p>Sable steps a tick as two substeps, and integrates the way Rapier does: velocity first, then
     * position with the new velocity. A steady twist - an unbalanced sail at passage speed - with the
     * spin zeroed at the top of every tick still turns the hull by the same amount every tick. It never
     * settles; it only adds up, so the longer the run the worse the lean.
     */
    @Test
    void zeroingTheSpinEachTickStillLetsATwistAddUp() {
        double half = lean(30);
        double whole = lean(60);          // one passage through an aperture at the default timing
        assertEquals(2.0D * half, whole, EPSILON, "the lean grows with the run rather than settling");
        assertTrue(Math.toDegrees(whole) > 10.0D,
                "a whole passage of it is a visible lean, was " + Math.toDegrees(whole) + " degrees");
    }

    /** How far a steady twist turns a hull whose spin is zeroed at the top of every tick. */
    private static double lean(int ticks) {
        double twist = 4.0D;              // radians per second squared, from an unbalanced sail
        double substep = 0.05D / 2.0D;    // Sable's default: two substeps a tick
        double leaned = 0.0D;
        for (int tick = 0; tick < ticks; tick++) {
            double spin = 0.0D;           // the drive zeroes it once a tick
            for (int step = 0; step < 2; step++) {
                spin += twist * substep;
                leaned += spin * substep;
            }
        }
        return leaned;
    }

    @Test
    void theSameAttitudeIsNoErrorEvenWrittenTheOtherWayRound() {
        Quaterniond attitude = new Quaterniond().rotateY(Math.toRadians(37.0D)).rotateX(Math.toRadians(8.0D));
        assertEquals(0.0D, AttitudeHold.error(attitude, attitude), 1.0e-6D);
        // q and -q are the same rotation; a hold that read them as a full turn apart would spin the hull.
        Quaterniond negated = new Quaterniond(-attitude.x, -attitude.y, -attitude.z, -attitude.w);
        assertEquals(0.0D, AttitudeHold.error(attitude, negated), 1.0e-6D);
    }

    @Test
    void aLeanIsMeasuredAsTheAngleThroughWhichItTurned() {
        Quaterniond level = new Quaterniond().rotateY(Math.toRadians(120.0D));
        Quaterniond rolled = new Quaterniond(level).rotateZ(Math.toRadians(25.0D));
        assertEquals(25.0D, Math.toDegrees(AttitudeHold.error(rolled, level)), 1.0e-6D);
    }

    @Test
    void aHoldLapsesWhenTheFlightStopsRenewingIt() {
        UUID ship = UUID.randomUUID();
        AttitudeHold.hold(ship, Level.OVERWORLD, new Quaterniond());
        for (int tick = 1; tick < AttitudeHold.GRACE_TICKS; tick++) {
            AttitudeHold.expire();
        }
        assertEquals(1, AttitudeHold.held(), "a drive that misses a tick or two keeps its hold");
        AttitudeHold.expire();
        assertEquals(0, AttitudeHold.held(), "a drive that has stopped flying must not pin a hull for ever");
    }

    @Test
    void renewingAHoldKeepsItAlive() {
        UUID ship = UUID.randomUUID();
        for (int tick = 0; tick < AttitudeHold.GRACE_TICKS * 4; tick++) {
            AttitudeHold.hold(ship, Level.OVERWORLD, new Quaterniond());
            AttitudeHold.expire();
        }
        assertEquals(1, AttitudeHold.held());
        AttitudeHold.release(ship);
        assertEquals(0, AttitudeHold.held(), "a finished flight hands the hull back at once");
    }
}
