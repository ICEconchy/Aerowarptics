package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;

/**
 * Where a Rift Plotter's two rifts are, and how something going into one comes out of the other.
 *
 * <p>Both rifts are discs, each described by a {@link Frame}: a middle, the way through it, and an up and a right
 * across its face. The entry is thrown out from the machine's lens, and faces straight out along that line unless
 * it has been turned to face its own way; the exit is set round the target by {@link Plot}'s distance, bearing and
 * height, and faces the target unless told otherwise. A thing
 * that crosses the entry's disc is carried to the same place on the exit's - so far right of the middle, so far
 * up - and every direction it had is turned by the one rotation that takes the entry's frame onto the exit's.
 * That is a proper rotation because both frames are built the same way round, so a ship comes out the right way
 * up, a player keeps their bearing relative to the rift, and a shell keeps its speed.
 *
 * <p>The entry is a way through from either face. Crossing it backwards uses the entry turned about its own up,
 * so whatever goes in comes out of the exit's front: a pilot never has to know which face is which, and a crate
 * dropped through a floor rift from above lands the same as one lobbed up through it from below.
 *
 * <p>A crossing is found on a line between two sightings the plotter took itself - the rule {@code GateWatch} keeps
 * for gates - rather than as a change of side alone, because a cannon shell covers more ground in a tick than a
 * gate's catchment is deep. The line is what lets something that fast be caught at all.
 *
 * <p>Kept free of the world so every piece of it can be tested; the block entity is the bridge.
 */
public final class PlotGeometry {

    /** How high above the master block's floor the yoke tips, in blocks: the artist's {@code elevation} pivot. */
    public static final double LENS_HEIGHT = 30.0D / 16.0D;

    /** How far in front of that pivot the ball floats, in blocks: the middle of the artist's {@code ball}, on the beam. */
    public static final double FOCUS = 8.0D / 16.0D;

    /** Blocks past the exit's plane an arrival is put, so it is out of the rift rather than in its face. */
    public static final double CLEARANCE = 1.0D;

    private PlotGeometry() {
    }

    /**
     * One rift's disc: its middle, the way through it, and the two directions across its face. Right-handed:
     * {@code right = normal x up}, which is a traveller's own right as they go through.
     */
    public record Frame(Vec3 centre, Vec3 normal, Vec3 up, Vec3 right) {

        /** How far in front of the disc's plane a point is; negative behind it. */
        public double side(Vec3 point) {
            return point.subtract(centre).dot(normal);
        }

        /** A world point in the frame's own terms: across to the right, up, and through. */
        public Vec3 local(Vec3 point) {
            Vec3 offset = point.subtract(centre);
            return new Vec3(offset.dot(right), offset.dot(up), offset.dot(normal));
        }

        /** The world point so far right, up and through this frame. */
        public Vec3 world(double across, double upward, double through) {
            return centre.add(right.scale(across)).add(up.scale(upward)).add(normal.scale(through));
        }

        /** The same disc approached from behind: turned half round its own up, still right-handed. */
        public Frame reversed() {
            return new Frame(centre, normal.scale(-1.0D), up, right.scale(-1.0D));
        }

        /** Columns right, up, normal: the rotation from a frame's own terms into the world's. */
        Matrix3d basis() {
            return new Matrix3d(right.x, right.y, right.z, up.x, up.y, up.z, normal.x, normal.y, normal.z);
        }
    }

    /** A crossing of a rift's disc: where the line met it, whether it was going through the front, how far past. */
    public record Crossing(Vec3 point, boolean forward, double past) {
    }

    /**
     * Where something that crossed the entry comes out.
     *
     * @param position where its middle goes
     * @param motion   its velocity, turned
     * @param turn     the rotation that turned it, for a hull's orientation and a creature's facing
     */
    public record Arrival(Vec3 position, Vec3 motion, Quaterniond turn) {
    }

    // ------------------------------------------------------------------ directions

    /** Minecraft's look vector: yaw 0 south, 90 west; pitch -90 straight up, 90 straight down. */
    public static Vec3 direction(double yaw, double pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        double level = Math.cos(p);
        return new Vec3(-Math.sin(y) * level, -Math.sin(p), Math.cos(y) * level);
    }

    /** The yaw a direction faces, Minecraft's way round. A vertical direction answers 0. */
    public static double yawOf(Vec3 direction) {
        if (direction.x * direction.x + direction.z * direction.z < 1.0e-12D) {
            return 0.0D;
        }
        return Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    /** The pitch a direction faces, Minecraft's way round: positive looks down. */
    public static double pitchOf(Vec3 direction) {
        double length = direction.length();
        return length < 1.0e-12D ? 0.0D : Math.toDegrees(-Math.asin(Math.max(-1.0D, Math.min(1.0D, direction.y / length))));
    }

    /**
     * A disc facing the way a yaw and pitch look.
     *
     * <p>Its up is the look turned a quarter up in the same vertical plane, so a disc facing straight up or down
     * still has an up - the yaw it was given - where one taken from the world's up would have none.
     */
    public static Frame frame(Vec3 centre, double yaw, double pitch) {
        Vec3 normal = direction(yaw, pitch);
        Vec3 up = direction(yaw, pitch - 90.0D);
        return new Frame(centre, normal, up, normal.cross(up));
    }

    // ------------------------------------------------------------------ the two rifts

    /** Where the dish pivots, for a plotter whose master stands at {@code master}. */
    public static Vec3 lens(BlockPos master) {
        return new Vec3(master.getX() + 0.5D, master.getY() + LENS_HEIGHT, master.getZ() + 0.5D);
    }

    /** Where the armillary ball floats, at the dish's focus, with the dish turned towards where the entry is. */
    public static Vec3 focus(BlockPos master, Plot plot) {
        return lens(master).add(direction(plot.inputYaw(), plot.inputPitch()).scale(FOCUS));
    }

    /**
     * The entry: thrown out from the dish along the plot's bearing and elevation, and facing the plot's own way.
     *
     * <p>Where it is and which way it faces are two things. Left alone it faces straight out along the line it was
     * thrown on; turned ({@link Plot#inputTurned}), it stays where it was thrown and faces the yaw and pitch it was
     * given. Either way it is built by {@link #frame} from a yaw and a pitch, as the exit is - which is what keeps the
     * turn between them a rotation, and a hull that goes in the right way up coming out the right way up.
     */
    public static Frame input(BlockPos master, Plot plot) {
        Vec3 along = direction(plot.inputYaw(), plot.inputPitch());
        return frame(lens(master).add(along.scale(plot.inputReach())), plot.facingYaw(), plot.facingPitch());
    }

    /** The target's point: the middle of the top of its column, at the ground the plot says is there. */
    public static Vec3 target(Plot plot) {
        return new Vec3(plot.targetX() + 0.5D, plot.targetY() + 1.0D, plot.targetZ() + 0.5D);
    }

    /** Where the exit's middle is: out from the target along the plot's bearing, and up. */
    public static Vec3 exitCentre(Vec3 target, Plot plot) {
        double bearing = Math.toRadians(plot.bearing());
        return target.add(Math.sin(bearing) * plot.distance(), plot.height(), -Math.cos(bearing) * plot.distance());
    }

    /**
     * The exit, facing the target - or turned off it by the plot's own aim.
     *
     * <p>Facing the target is the default and the point: a player who moves the exit round the target keeps it
     * aimed there. With the exit directly over the target, "at it" is straight down, still turned to the bearing
     * the exit was put on so its up has somewhere to point.
     */
    public static Frame exit(Vec3 target, Plot plot) {
        Vec3 centre = exitCentre(target, plot);
        // An exit north of its target faces south: in Minecraft's yaw that is the bearing's own number.
        double facing = plot.bearing();
        double yaw;
        double pitch;
        if (plot.aimed()) {
            Vec3 toTarget = target.subtract(centre);
            yaw = plot.distance() < 1.0e-3D ? facing : yawOf(toTarget);
            pitch = toTarget.lengthSqr() < 1.0e-9D ? 0.0D : pitchOf(toTarget);
        } else {
            yaw = facing + plot.aimYaw();
            pitch = plot.aimPitch();
        }
        return frame(centre, yaw, pitch);
    }

    // ------------------------------------------------------------------ crossing

    /**
     * Whether the line between two sightings went through a disc, and where.
     *
     * <p>A change of side alone is not enough: the line has to meet the plane inside the disc. Walking round the
     * rim is not going through, and something that passes beside the rift at speed must not be sent anywhere.
     *
     * @return the crossing, or {@code null} for a line that stayed on one side or passed outside the disc
     */
    @Nullable
    public static Crossing crossing(Frame frame, double radius, Vec3 before, Vec3 after) {
        double from = frame.side(before);
        double to = frame.side(after);
        boolean wasFront = from >= 0.0D;
        boolean isFront = to >= 0.0D;
        if (wasFront == isFront) {
            return null;
        }
        double t = from / (from - to);
        Vec3 point = before.add(after.subtract(before).scale(t));
        Vec3 local = frame.local(point);
        if (local.x * local.x + local.y * local.y > radius * radius) {
            return null;
        }
        return new Crossing(point, isFront, Math.abs(to));
    }

    /**
     * How far from a disc's middle the line between two sightings met its plane - or NaN for a line that stayed on one
     * side. More than the radius is the crossing {@link #crossing} turns away: for the trace, which is otherwise told
     * nothing about something that went past the rim and was left where it was.
     */
    public static double metPlaneAt(Frame frame, Vec3 before, Vec3 after) {
        double from = frame.side(before);
        double to = frame.side(after);
        if ((from >= 0.0D) == (to >= 0.0D)) {
            return Double.NaN;
        }
        Vec3 local = frame.local(before.add(after.subtract(before).scale(from / (from - to))));
        return Math.hypot(local.x, local.y);
    }

    /**
     * How many ticks until something at {@code at}, moving {@code velocity} a tick, goes through a disc - or infinity
     * if on that line, at that speed, it never does: moving away, along the plane, or passing outside the rim. Either
     * face counts, as it does for a crossing.
     *
     * <p>What the exit opens for. A rift at the target stands only while something is on its way to it, so it is
     * asked of everything near the entry every tick the entry is open.
     */
    public static double ticksToCross(Frame frame, double radius, Vec3 at, Vec3 velocity) {
        double side = frame.side(at);
        double closing = velocity.dot(frame.normal());
        if (Math.abs(closing) < 1.0e-9D || side * closing > 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        double ticks = -side / closing;
        Vec3 local = frame.local(at.add(velocity.scale(ticks)));
        return local.x * local.x + local.y * local.y <= radius * radius ? ticks : Double.POSITIVE_INFINITY;
    }

    /**
     * Carries a crossing from the entry to the exit.
     *
     * <p>The place on the disc is kept, and the ground the traveller had covered past the entry's plane is given
     * back past the exit's - so a shell does not lose a tick's flight, and a hull whose middle was just through
     * comes out just through.
     */
    public static Arrival map(Frame entry, Frame exit, Crossing crossing, Vec3 motion) {
        Frame from = crossing.forward() ? entry : entry.reversed();
        Vec3 local = from.local(crossing.point());
        Vec3 position = exit.world(local.x, local.y, CLEARANCE + crossing.past());
        Quaterniond turn = turn(from, exit);
        return new Arrival(position, rotate(turn, motion), turn);
    }

    /**
     * How fast something leaves the exit: the speed it went in with, turned through the rift - or, where the plotter sets
     * one, straight out of the exit's front at that speed. Nought is a delivery: out, and at rest.
     *
     * @param blocksPerSecond the plotter's exit speed, or negative ({@link Firing#KEEP_SPEED}) to keep its own
     */
    public static Vec3 exitMotion(Arrival arrival, Frame exit, double blocksPerSecond) {
        if (!(blocksPerSecond >= 0.0D)) {
            return arrival.motion();
        }
        return exit.normal().scale(blocksPerSecond / 20.0D);
    }

    /** The rotation that takes one frame onto another. */
    public static Quaterniond turn(Frame from, Frame to) {
        Matrix3d rotation = new Matrix3d(to.basis()).mul(new Matrix3d(from.basis()).transpose());
        return rotation.getNormalizedRotation(new Quaterniond());
    }

    /** A direction turned by a rotation. */
    public static Vec3 rotate(Quaterniondc turn, Vec3 vector) {
        Vector3d turned = turn.transform(new Vector3d(vector.x, vector.y, vector.z));
        return new Vec3(turned.x, turned.y, turned.z);
    }

    // ------------------------------------------------------------------ fit

    /**
     * The radius a hull needs to pass through a disc face-on: half the diagonal of the shadow its turned block box
     * casts on the disc's face - the circle round that shadow.
     *
     * <p>Measured off the hull's own box turned by its orientation, never its world box, which would make whether
     * a long ship fits depend on which way the compass happened to point - see {@link FlightGeometry}.
     */
    public static double needs(double sizeX, double sizeY, double sizeZ, Quaterniondc orientation, Frame frame) {
        double across = FlightGeometry.extentAlong(sizeX, sizeY, sizeZ, orientation, joml(frame.right()));
        double upward = FlightGeometry.extentAlong(sizeX, sizeY, sizeZ, orientation, joml(frame.up()));
        return 0.5D * Math.hypot(across, upward);
    }

    private static Vector3d joml(Vec3 vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }
}
