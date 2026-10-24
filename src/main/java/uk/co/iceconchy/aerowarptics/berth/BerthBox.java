package uk.co.iceconchy.aerowarptics.berth;

import org.joml.Vector3d;

/**
 * A berth's slip: a level box of air, turned about the upright to face wherever its owner pointed it.
 *
 * <p>The box is what a ship is put down in, and the only shape a berth has. It lies level - a berth is
 * a yaw and nothing more - and is measured from the middle of its floor, because that is the one point
 * that does not move when a builder makes it longer, wider or taller.
 *
 * <p>Its yaw is Minecraft's: nought faces south (+z), ninety faces west (-x). {@link #facing()} is the
 * way a moored ship's bow points; {@link #across()} is a quarter turn from it, as
 * {@link uk.co.iceconchy.aerowarptics.warp.FlightGeometry} takes its own.
 *
 * <p>Pure, so slips can be turned and overlapped in a test without a world.
 *
 * @param centreX middle of the floor, world x
 * @param floorY  the floor: the lowest air of the slip
 * @param centreZ middle of the floor, world z
 * @param length  along the facing
 * @param width   across it
 * @param height  up from the floor
 * @param yaw     degrees, Minecraft's sense
 */
public record BerthBox(double centreX, double floorY, double centreZ, double length, double width, double height,
                       double yaw) {

    /** The way a moored ship's bow points: level, unit length. */
    public Vector3d facing() {
        double radians = Math.toRadians(yaw);
        return new Vector3d(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    /** A quarter turn of the facing about the upright: the way the slip's width runs. */
    public Vector3d across() {
        Vector3d along = facing();
        return new Vector3d(-along.z, 0.0D, along.x);
    }

    /**
     * The way a ship travels as it comes in: along the facing bow first, against it stern first. Either way
     * it ends with its bow along the facing.
     */
    public Vector3d approach(boolean sternFirst) {
        return sternFirst ? facing().negate() : facing();
    }

    public double volume() {
        return length * width * height;
    }

    /**
     * A world point in the slip's own terms: x across, y up from the floor, z along the facing, each measured
     * from the middle of the floor.
     */
    public Vector3d local(double x, double y, double z) {
        Vector3d along = facing();
        Vector3d side = across();
        double dx = x - centreX;
        double dz = z - centreZ;
        return new Vector3d(dx * side.x + dz * side.z, y - floorY, dx * along.x + dz * along.z);
    }

    /** The world point a place in the slip's own terms is - {@link #local} the other way. */
    public Vector3d world(double acrossBy, double up, double alongBy) {
        Vector3d along = facing();
        Vector3d side = across();
        return new Vector3d(centreX + side.x * acrossBy + along.x * alongBy, floorY + up,
                centreZ + side.z * acrossBy + along.z * alongBy);
    }

    public boolean contains(double x, double y, double z) {
        Vector3d at = local(x, y, z);
        return Math.abs(at.x) <= width * 0.5D && at.y >= 0.0D && at.y <= height && Math.abs(at.z) <= length * 0.5D;
    }

    /**
     * Where a vessel's middle rests: over the middle of the floor, its keel the clearance above it.
     *
     * <p>Not the middle of the box. A slip is usually built taller than the ships it takes, and a small ship
     * hung in the middle of a tall one is a ship nobody can step aboard from the quay.
     *
     * @param hullHeight the vessel's height as it will sit
     * @param clearance  the air kept under its keel, which is the air the arrival is proved against
     */
    public Vector3d mark(double hullHeight, double clearance) {
        return new Vector3d(centreX, floorY + clearance + hullHeight * 0.5D, centreZ);
    }

    /**
     * The lane an arriving hull flies down: a box as wide and as tall as the slip, reaching {@code reach} back
     * from its mouth against the way the hull comes in.
     *
     * @param reach how far behind the slip the hull first appears - nothing, for a hull that appears inside it
     */
    public BerthBox lane(double reach, boolean sternFirst) {
        double back = Math.max(0.0D, reach);
        Vector3d approach = approach(sternFirst);
        double shift = length * 0.5D + back * 0.5D;
        return new BerthBox(centreX - approach.x * shift, floorY, centreZ - approach.z * shift, back, width, height,
                yaw);
    }

    /**
     * How far behind the slip's mouth a hull first appears, given how far back from its resting place it does.
     *
     * @param runOut   from the hull's resting place back to where it reappears
     * @param hullSpan the hull's length along its travel
     */
    public double laneReach(double runOut, double hullSpan) {
        return Math.max(0.0D, runOut + hullSpan * 0.5D - length * 0.5D);
    }

    /** The upright world box that just encloses the slip: minX, minY, minZ, maxX, maxY, maxZ. */
    public double[] bounds() {
        Vector3d along = facing();
        Vector3d side = across();
        double halfX = Math.abs(along.x) * length * 0.5D + Math.abs(side.x) * width * 0.5D;
        double halfZ = Math.abs(along.z) * length * 0.5D + Math.abs(side.z) * width * 0.5D;
        return new double[] {centreX - halfX, floorY, centreZ - halfZ, centreX + halfX, floorY + height,
                centreZ + halfZ};
    }

    /**
     * Whether an upright world box shares any air with the slip.
     *
     * <p>Exact, not the enclosing box's answer: two slips side by side at forty-five degrees have enclosing
     * boxes that overlap by half their width, and a ship in one would be counted as fouling the other. The
     * two are level, so it is their heights and then four axes across the ground - the world's two and the
     * slip's two.
     */
    public boolean overlaps(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxY <= floorY || minY >= floorY + height) {
            return false;
        }
        double[] mine = bounds();
        if (maxX <= mine[0] || minX >= mine[3] || maxZ <= mine[2] || minZ >= mine[5]) {
            return false;
        }
        // The other box's shadow on each of the slip's own axes, about the slip's middle.
        double otherX = (minX + maxX) * 0.5D - centreX;
        double otherZ = (minZ + maxZ) * 0.5D - centreZ;
        double halfX = (maxX - minX) * 0.5D;
        double halfZ = (maxZ - minZ) * 0.5D;
        Vector3d along = facing();
        Vector3d side = across();
        double onAlong = Math.abs(otherX * along.x + otherZ * along.z);
        double reachAlong = halfX * Math.abs(along.x) + halfZ * Math.abs(along.z);
        if (onAlong >= length * 0.5D + reachAlong) {
            return false;
        }
        double onSide = Math.abs(otherX * side.x + otherZ * side.z);
        double reachSide = halfX * Math.abs(side.x) + halfZ * Math.abs(side.z);
        return onSide < width * 0.5D + reachSide;
    }
}
