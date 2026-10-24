package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * One discontinuous move of a hull, as the rigid transform that carries its old placement onto its new
 * one.
 *
 * <p>Everything that was riding the hull when it jumped - the deck, the crew standing on it, the poses a
 * client has buffered to interpolate between - has to go through the <em>same</em> transform, or the
 * two stop agreeing about where the other is. That is the whole of both bugs this exists for. A client
 * that interpolated the hull across a teleport drew it half-way between the two ends, in chunks it did
 * not have, and carried whoever was standing on it along the same impossible stretch - which Sable then
 * remembered as that player's momentum. The ship vanished and the crew were thrown off it, and both
 * were one mistake.
 *
 * <p>A hull's pose maps a point in its plot to the world as {@code position + orientation * ((p -
 * rotationPoint) * scale)}. A teleport changes only position and orientation, so the map from the old
 * world placement to the new is {@code to + turn * (x - from)} with {@code turn = to * from^-1}, and
 * applying it to any pose of the same hull gives that pose's counterpart on the far side. Pure JOML, so
 * it is testable without a game.
 */
public final class HullJump {

    private final Vector3d from;
    private final Vector3d to;
    private final Quaterniond fromOrientation;
    private final Quaterniond toOrientation;
    private final Quaterniond turn;

    private HullJump(Vector3dc from, Quaterniondc fromOrientation, Vector3dc to, Quaterniondc toOrientation) {
        this.from = new Vector3d(from);
        this.to = new Vector3d(to);
        this.fromOrientation = new Quaterniond(fromOrientation).normalize();
        this.toOrientation = new Quaterniond(toOrientation).normalize();
        this.turn = new Quaterniond(this.toOrientation).mul(new Quaterniond(this.fromOrientation).conjugate())
                .normalize();
    }

    /** The jump from one placement of a hull's origin to another. */
    public static HullJump between(Vector3dc from, Quaterniondc fromOrientation,
                                   Vector3dc to, Quaterniondc toOrientation) {
        return new HullJump(from, fromOrientation, to, toOrientation);
    }

    /** The jump between two poses of the same hull. Only position and orientation are read. */
    public static HullJump between(Pose3dc from, Pose3dc to) {
        return new HullJump(from.position(), from.orientation(), to.position(), to.orientation());
    }

    public Vector3dc from() {
        return from;
    }

    public Vector3dc to() {
        return to;
    }

    public Quaterniondc fromOrientation() {
        return fromOrientation;
    }

    public Quaterniondc toOrientation() {
        return toOrientation;
    }

    /** How far the hull's origin went. */
    public double distance() {
        return from.distance(to);
    }

    /** Where a world point that was riding the hull ends up. */
    public Vector3d apply(Vector3dc world, Vector3d dest) {
        return turn.transform(dest.set(world).sub(from)).add(to);
    }

    /** Turns a world direction the way the hull turned. Directions are not moved, only rotated. */
    public Vector3d applyToDirection(Vector3dc direction, Vector3d dest) {
        return turn.transform(dest.set(direction));
    }

    /**
     * Moves a pose of this hull to the far side, in place.
     *
     * <p>Rotation point and scale are the hull's own and a teleport leaves them alone, which is what lets
     * this touch only the two parts that did change.
     */
    public void applyTo(Pose3d pose) {
        apply(pose.position(), pose.position());
        pose.orientation().premul(turn).normalize();
    }

    /** A copy of a pose, moved to the far side. */
    public Pose3d moved(Pose3dc pose) {
        Pose3d copy = new Pose3d(pose);
        applyTo(copy);
        return copy;
    }

    /**
     * Whether a pose of the hull still belongs to the side it jumped from.
     *
     * <p>Decided by which end it is nearer. That is safe exactly because a jump is far longer than
     * anything a hull does in the few ticks a client keeps: the last poses before the jump sit within a
     * handful of blocks of {@link #from()} and every pose after it within a handful of {@link #to()}.
     * It is also what makes moving poses idempotent - one already carried across is nearer the far end
     * and is never carried again.
     */
    public boolean behind(Vector3dc position) {
        return position.distanceSquared(from) < position.distanceSquared(to);
    }
}
