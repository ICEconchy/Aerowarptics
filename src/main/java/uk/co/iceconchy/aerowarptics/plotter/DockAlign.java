package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;

/**
 * The Rift Plotter's dock helper, as arithmetic: which vehicle waiting behind the entry it catches, how it wants that
 * vehicle to sit, and how it gets it there.
 *
 * <p>The dock is a cylinder behind the entry - the side a vehicle is flown into it from - along the rift's axis, as
 * wide and as deep as the plotter was set ({@link Firing}), or as suits its rift where nobody set it. Behind is behind
 * the way the entry <em>faces</em>, which a player may turn: the dock turns with it. A
 * vehicle that drifts into it slowly enough is caught, and <em>lined up</em>: its middle brought onto the axis a little
 * way back from the plane, and the hull turned level and end-on to the rift. A vehicle has no bow of its own, so
 * "end-on" is whichever of its own four horizontal ends needs the least turning to face through the rift - unless only
 * the other way round fits, in which case that. Once lined up with the rift open, it is launched straight through.
 *
 * <p>It is gentle on purpose: the pull is proportional to how far there is still to go and capped, so a caught ship
 * drifts in and settles rather than being yanked; and it turns about the upright at a few degrees a tick. Nothing here
 * teleports anything - it only says what velocity to command.
 *
 * <p>Pure, so a simulated vehicle can be walked in to its mark in a test.
 */
public final class DockAlign {

    /** Blocks clear of the entry's plane a lined-up vehicle's nearest end is held. */
    public static final double STANDOFF = 2.0D;

    /** Blocks past where the dock stops a vehicle it already holds may sit, so one at the edge is not caught and dropped by turns. */
    public static final double KEEP_SLACK = 4.0D;

    /** How close to its mark, in blocks, and to its attitude, a vehicle has to be to count as lined up. */
    public static final double LINED_UP_DISTANCE = 0.75D;
    public static final double LINED_UP_ANGLE = Math.toRadians(4.0D);

    /** The most a caught vehicle is drawn in a tick, in blocks, and turned, in radians. */
    public static final double MAX_DRAW = 0.35D;
    public static final double MAX_TURN = Math.toRadians(2.5D);

    /** How much of what is left to close is asked for each tick - a gentle glide rather than a snap. */
    public static final double DRAW_GAIN = 0.08D;
    public static final double TURN_GAIN = 0.12D;

    private static final Vector3d[] ENDS = {new Vector3d(0, 0, 1), new Vector3d(0, 0, -1), new Vector3d(1, 0, 0),
            new Vector3d(-1, 0, 0)};

    private DockAlign() {
    }

    /**
     * How far from the rift's axis a dock nobody has sized reaches: a good way wider than the rift, so a pilot need
     * not be exact. A plotter may be given its own ({@link Firing#dockRadius(double)}), wider or narrower.
     */
    public static double zoneRadius(double riftRadius) {
        return riftRadius * 1.5D + 6.0D;
    }

    /**
     * Whether a point is in the dock: behind the entry, no further back than {@code depth}, no further from the axis
     * than {@code zone}.
     *
     * @param zone  the dock's own radius, which is not the rift's - see {@link Firing#dockRadius(double)}
     * @param depth how far behind the entry's plane the dock reaches
     */
    public static boolean inDock(PlotGeometry.Frame entry, double zone, double depth, Vec3 point) {
        Vec3 local = entry.local(point);
        return local.z <= 0.0D && local.z >= -depth && Math.hypot(local.x, local.y) <= zone;
    }

    /**
     * How far behind the entry a vehicle already caught may sit and still be the dock's: the dock's own depth, or as
     * far back as the vehicle's own mark where that is further, and a little slack past either.
     *
     * <p>The mark is half the hull's length behind the plane, and the dock takes a caught hull to it. A hull longer
     * than the dock is deep is therefore carried <em>out of the back of the dock</em> by the dock itself - and, held
     * only to the dock's depth, was let go on the way, drifted, was caught again and let go again. A dock a player
     * has made four blocks deep does that to anything bigger than a rowing boat.
     */
    public static double keepDepth(PlotGeometry.Frame entry, double depth, double[] size, Quaterniondc lined) {
        double marked = -entry.local(mark(entry, size, lined)).z;
        return Math.max(depth, marked) + KEEP_SLACK;
    }

    /**
     * How a vehicle wants to sit to go through: level, turned about the upright so one of its own horizontal ends points
     * through the rift. The end that needs the least turning, unless the hull only fits the rift the other way round.
     * A rift facing straight up or down has no "through" across the ground; there the hull is only levelled.
     *
     * @param current the hull's orientation, ship to world
     * @param size    the hull's own block box, x y z
     */
    public static Quaterniond lineUp(Quaterniondc current, double[] size, PlotGeometry.Frame entry, double riftRadius) {
        Vector3d through = new Vector3d(entry.normal().x, 0.0D, entry.normal().z);
        if (through.lengthSquared() < 1.0e-6D) {
            // Straight up or down: keep the heading it has, only level it.
            Vector3d nose = current.transform(new Vector3d(0, 0, 1));
            nose.y = 0.0D;
            if (nose.lengthSquared() < 1.0e-9D) {
                nose.set(0, 0, 1);
            }
            return yawTurning(new Vector3d(0, 0, 1), nose.normalize());
        }
        through.normalize();
        Quaterniond best = null;
        double bestDot = -2.0D;
        Quaterniond fitting = null;
        double fittingDot = -2.0D;
        for (Vector3d end : ENDS) {
            Vector3d pointing = current.transform(new Vector3d(end));
            pointing.y = 0.0D;
            double dot = pointing.lengthSquared() < 1.0e-9D ? -1.0D : pointing.normalize().dot(through);
            Quaterniond turned = yawTurning(end, through);
            if (dot > bestDot) {
                bestDot = dot;
                best = turned;
            }
            if (dot > fittingDot && PlotGeometry.needs(size[0], size[1], size[2], turned, entry) <= riftRadius) {
                fittingDot = dot;
                fitting = turned;
            }
        }
        return fitting != null ? fitting : best;
    }

    /** Whether a hull fits the rift in any of the four level ways {@link #lineUp} could turn it. */
    public static boolean fitsSomeWay(double[] size, PlotGeometry.Frame entry, double riftRadius) {
        Vector3d through = new Vector3d(entry.normal().x, 0.0D, entry.normal().z);
        if (through.lengthSquared() < 1.0e-6D) {
            through.set(0, 0, 1);
        }
        through.normalize();
        for (Vector3d end : ENDS) {
            if (PlotGeometry.needs(size[0], size[1], size[2], yawTurning(end, through), entry) <= riftRadius) {
                return true;
            }
        }
        return false;
    }

    /** The level turn about the upright that points a hull's own horizontal {@code end} along {@code towards}. */
    static Quaterniond yawTurning(Vector3d end, Vector3d towards) {
        // Rotating (x, z) by t about +y gives (x cos t + z sin t, -x sin t + z cos t).
        double cos = end.x * towards.x + end.z * towards.z;
        double sin = end.z * towards.x - end.x * towards.z;
        return new Quaterniond().rotateY(Math.atan2(sin, cos));
    }

    /**
     * Where a lined-up vehicle's middle is held: on the rift's axis, behind the plane by half its own length through the
     * rift and the standoff, so its nearest end waits {@link #STANDOFF} short of the rift.
     */
    public static Vec3 mark(PlotGeometry.Frame entry, double[] size, Quaterniondc lined) {
        double through = FlightGeometry.extentAlong(size[0], size[1], size[2], lined,
                new Vector3d(entry.normal().x, entry.normal().y, entry.normal().z));
        return entry.centre().subtract(entry.normal().scale(through * 0.5D + STANDOFF));
    }

    /**
     * What to command a caught vehicle this tick.
     *
     * @param linear  blocks a tick
     * @param angular radians a tick, about a world axis
     */
    public record Steer(Vec3 linear, Vec3 angular, double distance, double angle) {

        public boolean linedUp() {
            return distance <= LINED_UP_DISTANCE && angle <= LINED_UP_ANGLE;
        }
    }

    /** The glide in to a mark and the turn to an attitude: proportional to what is left, and capped. */
    public static Steer steer(Vec3 centre, Vec3 mark, Quaterniondc current, Quaterniondc wanted) {
        Vec3 error = mark.subtract(centre);
        double distance = error.length();
        Vec3 linear = error.scale(DRAW_GAIN);
        if (linear.length() > MAX_DRAW) {
            linear = linear.normalize().scale(MAX_DRAW);
        }
        // The world-frame turn that takes the hull from where it is to where it is wanted, the short way round.
        Quaterniond delta = new Quaterniond(wanted).mul(new Quaterniond(current).conjugate()).normalize();
        if (delta.w < 0.0D) {
            delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
        }
        AxisAngle4d turn = new AxisAngle4d(delta);
        double angle = Math.abs(turn.angle);
        Vec3 angular = Vec3.ZERO;
        if (angle > 1.0e-6D) {
            double rate = Math.min(angle * TURN_GAIN, MAX_TURN);
            angular = new Vec3(turn.x, turn.y, turn.z).normalize().scale(rate * Math.signum(turn.angle));
        }
        return new Steer(linear, angular, distance, angle);
    }

    /** The push through the rift, once lined up and open: along the rift's axis, holding the attitude it has. */
    public static Vec3 launch(PlotGeometry.Frame entry, double blocksPerTick) {
        return entry.normal().scale(blocksPerTick);
    }
}
