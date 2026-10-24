package uk.co.iceconchy.aerowarptics.berth;

import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * How a berth wants a ship to sit, and how it gets it there and keeps it there - as arithmetic.
 *
 * <p>Two jobs with one shape. A ship flown in by hand is <em>drawn</em>: glided to its mark and turned to
 * the facing a little each tick, gently, as a Rift Plotter's dock draws one in
 * ({@link uk.co.iceconchy.aerowarptics.plotter.DockAlign}). A ship at its mark is <em>held</em>: the same
 * pull, stiff, so that a hull whose balloons no longer carry it sags a twentieth of a block and no
 * further. Nothing here teleports anything - it only says what velocity to command - and nothing here
 * makes a joint, which is why a moored ship is not a fastened one and its own drive can simply leave.
 *
 * <p>Pure, so a simulated hull can be walked to its mark and held there in a test.
 */
public final class BerthMooring {

    private BerthMooring() {
    }

    /** How close to its mark, in blocks, and to its attitude, a drawn ship has to be to count as moored. */
    public static final double MOORED_DISTANCE = 0.5D;
    public static final double MOORED_ANGLE = Math.toRadians(3.0D);

    /** The most a ship is drawn in a tick, in blocks, and turned, in radians. */
    public static final double MAX_DRAW = 0.35D;
    public static final double MAX_TURN = Math.toRadians(2.5D);

    /** How much of what is left to close is asked for each tick while drawing in: a glide, not a snap. */
    public static final double DRAW_GAIN = 0.08D;
    public static final double TURN_GAIN = 0.12D;

    /**
     * And while holding. A held hull's velocity is replaced every tick, so all gravity can do to it is one
     * tick's fall before it is put back; at half of what is left each tick that settles a hair under its mark
     * instead of a third of a block under it, which is what the gentle gain gave.
     */
    public static final double HOLD_GAIN = 0.5D;

    /** How far past the slip a ship already being drawn may stray and still be the berth's. */
    public static final double KEEP_SLACK = 4.0D;

    private static final Vector3d[] ENDS = {new Vector3d(0, 0, 1), new Vector3d(0, 0, -1), new Vector3d(1, 0, 0),
            new Vector3d(-1, 0, 0)};

    /**
     * The attitude a ship takes in a slip: level, its bow along the slip's facing.
     *
     * <p>Level, and not as it flew. No ship floats quite level - a hull trimmed by hand sits a fraction of a
     * degree over - and a ship measured as it flew is a hair wider than it is, which is enough to refuse it
     * the slip that was built to fit it (seen in game, 2026-10-12: a five-block beam refused a nine-block slip
     * at a seventh of a degree of roll). A berth holds a ship still; it holds it upright too, so what fits a
     * slip is a matter of the ship's own size and nothing else.
     *
     * @param current the ship-to-world rotation it has now
     * @param bow     the way its bow points now, in the world
     */
    public static Quaterniond attitude(Quaterniondc current, Vector3dc bow, BerthBox box) {
        // The bow as the ship itself has it: one of its own level axes, for any drive mounted on a deck.
        Vector3d own = new Quaterniond(current).conjugate().transform(new Vector3d(bow));
        own.y = 0.0D;
        if (own.lengthSquared() < 1.0e-9D) {
            // A bow along the ship's own upright: it has no heading of its own, so the world's is taken.
            own.set(bow.x(), 0.0D, bow.z());
            if (own.lengthSquared() < 1.0e-9D) {
                own.set(0.0D, 0.0D, 1.0D);
            }
            return yawTurning(own.normalize(), box.facing());
        }
        // Snapped to the nearest of those axes. The bow arrives levelled in the world, and a hull tilted two ways
        // at once brings it back a few hundred-thousandths of a radian off its own axis - turned that much in the
        // slip, an exact fit is a hair too wide for it (seen in game, 2026-10-14: a nine-block beam at 0.6 degrees
        // of pitch and a trace of roll refused a thirteen-block slip, 9.00001 across against an allowance of 1e-6).
        return yawTurning(nearestEnd(own), box.facing());
    }

    /** Whichever of a ship's four level ends a level direction in its own frame lies closest to. */
    private static Vector3d nearestEnd(Vector3dc own) {
        Vector3d best = ENDS[0];
        for (Vector3d end : ENDS) {
            if (end.dot(own) > best.dot(own)) {
                best = end;
            }
        }
        return new Vector3d(best);
    }

    /**
     * How a ship flown in by hand is turned: by whichever of its own four level ends needs the least turning
     * to lie along the facing and still fits the slip that way round. A hull has no bow of its own, so a
     * berth takes it the way it came - unless only another way fits.
     *
     * @return the attitude, or {@code null} for a ship that fits no way round
     */
    @Nullable
    public static Quaterniond lineUp(Quaterniondc current, double sizeX, double sizeY, double sizeZ, BerthBox box,
                                     double clearance) {
        Quaterniond best = null;
        double bestDot = -2.0D;
        Vector3d facing = box.facing();
        for (Vector3d end : ENDS) {
            Vector3d pointing = current.transform(new Vector3d(end));
            pointing.y = 0.0D;
            if (pointing.lengthSquared() < 1.0e-9D) {
                continue;
            }
            pointing.normalize();
            // Level, with that end of its own along the facing - see attitude().
            Quaterniond turned = yawTurning(end, facing);
            double dot = pointing.dot(facing);
            if (dot > bestDot && BerthFit.fits(sizeX, sizeY, sizeZ, turned, box, clearance)) {
                bestDot = dot;
                best = turned;
            }
        }
        return best;
    }

    /** The turn about the upright that carries a level unit vector {@code from} onto {@code towards}. */
    static Quaterniond yawTurning(Vector3dc from, Vector3dc towards) {
        // Rotating (x, z) by t about +y gives (x cos t + z sin t, -x sin t + z cos t).
        double cos = from.x() * towards.x() + from.z() * towards.z();
        double sin = from.z() * towards.x() - from.x() * towards.z();
        return new Quaterniond().rotateY(Math.atan2(sin, cos));
    }

    /**
     * Whether a ship in the slip is one to catch: slow enough, and able to sit there.
     *
     * @param speed        how fast it is going, blocks a tick
     * @param captureSpeed the fastest a berth catches, blocks a tick - a ship flying through is not parking
     */
    public static boolean catches(BerthBox box, Vector3dc centre, double speed, double captureSpeed, boolean fits) {
        return fits && speed <= captureSpeed && box.contains(centre.x(), centre.y(), centre.z());
    }

    /** Whether a ship being drawn in is still near enough the slip to keep drawing. */
    public static boolean keeps(BerthBox box, Vector3dc centre) {
        Vector3d at = box.local(centre.x(), centre.y(), centre.z());
        return Math.abs(at.x) <= box.width() * 0.5D + KEEP_SLACK && Math.abs(at.z) <= box.length() * 0.5D + KEEP_SLACK
                && at.y >= -KEEP_SLACK && at.y <= box.height() + KEEP_SLACK;
    }

    /**
     * What to command a ship this tick.
     *
     * @param linear  blocks a tick
     * @param angular radians a tick, about a world axis
     */
    public record Pull(Vector3d linear, Vector3d angular, double distance, double angle) {

        public boolean moored() {
            return distance <= MOORED_DISTANCE && angle <= MOORED_ANGLE;
        }
    }

    /** The glide to a mark and the turn to an attitude for a ship being drawn in. */
    public static Pull draw(Vector3dc centre, Vector3dc mark, Quaterniondc current, Quaterniondc wanted) {
        return pull(centre, mark, current, wanted, DRAW_GAIN, TURN_GAIN);
    }

    /** The same pull, stiff, for a ship already at its mark. */
    public static Pull hold(Vector3dc centre, Vector3dc mark, Quaterniondc current, Quaterniondc wanted) {
        return pull(centre, mark, current, wanted, HOLD_GAIN, HOLD_GAIN);
    }

    private static Pull pull(Vector3dc centre, Vector3dc mark, Quaterniondc current, Quaterniondc wanted,
                             double drawGain, double turnGain) {
        Vector3d error = new Vector3d(mark).sub(centre);
        double distance = error.length();
        Vector3d linear = error.mul(drawGain, new Vector3d());
        if (linear.length() > MAX_DRAW) {
            linear.normalize().mul(MAX_DRAW);
        }
        // The world-frame turn that takes the hull from where it is to where it is wanted, the short way round.
        Quaterniond delta = new Quaterniond(wanted).mul(new Quaterniond(current).conjugate()).normalize();
        if (delta.w < 0.0D) {
            delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        }
        AxisAngle4d turn = new AxisAngle4d(delta);
        double angle = Math.abs(turn.angle);
        Vector3d angular = new Vector3d();
        if (angle > 1.0e-6D) {
            double rate = Math.min(angle * turnGain, MAX_TURN);
            angular.set(turn.x, turn.y, turn.z).normalize().mul(rate * Math.signum(turn.angle));
        }
        return new Pull(linear, angular, distance, angle);
    }
}
