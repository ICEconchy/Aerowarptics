package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where you come out, and which way round you are when you get there.
 *
 * <p>Two gates are rarely the same size, and are usually not the same way round either, so a crossing
 * has to be carried over as a <em>fraction</em> of the opening and a <em>rotation</em> of everything
 * else. Distances would deposit somebody who entered a large gate near its edge into the frame of a
 * small one; leaving the attitude alone would spit a vehicle out sideways from a gate at right angles
 * to the one it drove into.
 *
 * <p>Pure arithmetic, deliberately. Getting a portal's handedness wrong produces a mirror image rather
 * than a turn - a vehicle that comes out with its left on its right - and that is not something anybody
 * spots by looking at one gate.
 */
public final class GateTraversal {

    /**
     * How far past the far gate's plane a traveller is put, in blocks.
     *
     * <p>Enough to be unambiguously through. Landing exactly on the plane leaves the crossing test
     * unable to say which side anything is on, and the next tick would send them straight back.
     */
    public static final double CLEARANCE = 1.25D;

    private GateTraversal() {
    }

    /**
     * Which way a gate's positive face looks, in Minecraft's yaw.
     *
     * <p>Zero is south and grows clockwise, so a gate whose normal runs along Z faces south, and one
     * whose normal runs along X faces east.
     */
    public static float yawOf(RiftGateShape shape) {
        return shape.normal() == Direction.Axis.Z ? 0.0F : -90.0F;
    }

    /** How far a traveller is turned passing from one gate to the other. */
    public static float yawDelta(RiftGateShape from, RiftGateShape to) {
        return Mth.wrapDegrees(yawOf(to) - yawOf(from));
    }

    /**
     * Whether the turn maps the first gate's width onto the second's, or onto its mirror.
     *
     * <p>This is the handedness. When two gates sit at right angles, turning a traveller through
     * ninety degrees carries their own left and right round with them - which in world terms means the
     * offset across the opening lands on the opposite side of the far gate. Getting this backwards
     * looks almost right, which is why it is worth naming.
     *
     * @return {@code +1} when across maps to across, {@code -1} when it maps to the other side
     */
    public static double spanSign(RiftGateShape from, RiftGateShape to) {
        Vec3 turned = rotateYaw(unit(from.span()), yawDelta(from, to));
        Vec3 target = unit(to.span());
        double dot = turned.x * target.x + turned.z * target.z;
        return dot >= 0.0D ? 1.0D : -1.0D;
    }

    /** Rotates a vector about the vertical axis, in Minecraft's yaw. */
    public static Vec3 rotateYaw(Vec3 vector, float degrees) {
        float radians = degrees * Mth.DEG_TO_RAD;
        double cos = Mth.cos(radians);
        double sin = Mth.sin(radians);
        // Yaw grows clockwise seen from above, so this is the rotation that takes a heading of zero
        // to a heading of `degrees` - not the textbook counter-clockwise one.
        return new Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
    }

    /**
     * A traveller's arrival at the far gate.
     *
     * @param position where they come out
     * @param motion   their momentum, turned to match
     * @param yawDelta how far they have been turned, to be added to a look direction or an orientation
     */
    public record Arrival(Vec3 position, Vec3 motion, float yawDelta) {
    }

    /**
     * Works out an arrival from a crossing.
     *
     * @param from    the gate being left
     * @param to      the gate being arrived at
     * @param crossed where the traveller was after crossing, which is what says which way they went
     * @param motion  their velocity going in
     */
    public static Arrival map(RiftGateShape from, RiftGateShape to, Vec3 crossed, Vec3 motion) {
        float delta = yawDelta(from, to);
        double across = from.acrossFraction(crossed) * spanSign(from, to);
        double up = from.upFraction(crossed);

        // The side of the far gate they leave by mirrors the side of the near one they went out
        // through, so driving back the way you came returns you the way you came.
        double side = from.distanceToPlane(crossed) >= 0.0D ? 1.0D : -1.0D;

        return new Arrival(to.pointAt(across, up, side * CLEARANCE), rotateYaw(motion, delta), delta);
    }

    private static Vec3 unit(Direction.Axis axis) {
        return axis == Direction.Axis.X ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
    }
}
