package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.BitSet;
import java.util.Set;

/**
 * The size, place and attitude of a gate's opening, without the opening itself.
 *
 * <p>Split from {@link RiftGateStructure.Opening} because this is the half that has to travel. A gate
 * being dialled from the other side of the world is in a chunk nobody has loaded, so its bounds and
 * its plane have to live in the registry rather than being read off the blocks.
 *
 * <h2>The mask, and why it is here</h2>
 * A ring is flood filled, so its opening is whatever shape the builder made - and that is very often
 * not a rectangle. Carrying only the bounding box, as this record first did, made two separate things
 * wrong at once: the aperture was an ellipse fitted to the box and bulged straight through the frame
 * on an L-shaped ring, and a crossing was any change of side within the box, so brushing the visible
 * fire <em>outside</em> the opening teleported you.
 *
 * <p>Both were justified by the cell set being "far too big to keep or send". It is not.
 * {@link RiftGateStructure#MAX_AREA} caps an opening at 225 cells, so the mask is 225 bits - under
 * thirty bytes, less than this record already spends on nothing in particular. It is kept as a
 * {@link BitSet} rather than a {@code long[]} so that equality stays by content: a record with an
 * array component compares arrays by identity, which would quietly make no two shapes equal.
 *
 * @param span the horizontal axis the gate lies along; the other one is what you pass through
 * @param open one bit per cell of the bounding box, indexed across-then-up
 */
public record RiftGateShape(Direction.Axis span,
                            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                            BitSet open) {

    /**
     * A shape whose opening fills its bounding box.
     *
     * <p>Correct for any rectangular ring, which is most of them, and the reading anything from
     * before the mask existed should be given.
     */
    public RiftGateShape(Direction.Axis span, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ) {
        this(span, minX, minY, minZ, maxX, maxY, maxZ,
                filled(spanOf(span, minX, minZ, maxX, maxZ) * (maxY - minY + 1)));
    }

    private static int spanOf(Direction.Axis span, int minX, int minZ, int maxX, int maxZ) {
        return (span == Direction.Axis.X ? maxX - minX : maxZ - minZ) + 1;
    }

    private static BitSet filled(int bits) {
        BitSet set = new BitSet(Math.max(1, bits));
        set.set(0, Math.max(1, bits));
        return set;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftGateShape> STREAM_CODEC =
            StreamCodec.of(RiftGateShape::encode, RiftGateShape::decode);

    /** The horizontal axis a traveller moves along to pass through. */
    public Direction.Axis normal() {
        return span == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** Blocks across, along the gate's own horizontal axis. */
    public int width() {
        return (span == Direction.Axis.X ? maxX - minX : maxZ - minZ) + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int area() {
        return width() * height();
    }

    /** The middle of the opening, on the plane the aperture is drawn in. */
    public Vec3 centre() {
        return new Vec3((minX + maxX + 1) / 2.0D, (minY + maxY + 1) / 2.0D, (minZ + maxZ + 1) / 2.0D);
    }

    /** Radius of the aperture along the gate's own horizontal axis, in blocks. */
    public double halfWidth() {
        return width() * 0.5D;
    }

    public double halfHeight() {
        return height() * 0.5D;
    }

    /** Whether a given cell of the bounding box is part of the opening. */
    public boolean openAt(int across, int up) {
        if (across < 0 || up < 0 || across >= width() || up >= height()) {
            return false;
        }
        return open.get(up * width() + across);
    }

    /**
     * Whether a point in the world is inside the opening itself, rather than merely inside the box
     * around it.
     *
     * <p>This is the check that stops a traveller being sent through by touching the fire beside an
     * L-shaped ring. The aperture is drawn to cover the opening, but "drawn over" and "inside" are
     * different questions and only this one may move anybody.
     */
    public boolean contains(Vec3 point) {
        int across = (span == Direction.Axis.X
                ? Mth.floor(point.x) - minX
                : Mth.floor(point.z) - minZ);
        return openAt(across, Mth.floor(point.y) - minY);
    }

    /** How many cells the opening actually has, which is not {@link #area()} unless it is a rectangle. */
    public int openCells() {
        return open.cardinality();
    }

    /**
     * How far the opening reaches from its middle at a given angle, as a fraction of the ellipse the
     * aperture would otherwise be.
     *
     * <p>This is what lets the animation bend to the ring. Everything the renderer draws - the face,
     * the torn rim, the fire, the cracks, the glass - is radial, so scaling the reach per angle bends
     * all of it at once rather than needing each to learn about the shape separately.
     *
     * <p>Marched rather than solved. The opening is a polyomino of at most 225 cells and this is
     * computed once when an aperture opens, so stepping outwards until it leaves the mask is both
     * simpler and more obviously correct than intersecting a ray with the boundary.
     *
     * @param angle radians, measured from the across axis towards the up axis
     * @return 0..1, where 1 is the edge of the bounding ellipse
     */
    public double reachAt(double angle) {
        double halfWidth = halfWidth();
        double halfHeight = halfHeight();
        if (halfWidth <= 0.0D || halfHeight <= 0.0D) {
            return 0.0D;
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double last = 0.0D;
        // Marched finely enough that the rim can follow one block's edge, and cheap enough to redo
        // whenever a ring is rebuilt: a couple of hundred steps over a mask of at most 225 cells.
        int steps = 192;
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            double across = cos * t * halfWidth;
            double up = sin * t * halfHeight;
            if (!openAt(Mth.floor(across + halfWidth), Mth.floor(up + halfHeight))) {
                break;
            }
            last = t;
        }
        return last;
    }

    /** Unit vector a traveller passes along, pointing at whichever side the given point is on. */
    public Vec3 outwardTowards(Vec3 point) {
        double side = distanceToPlane(point) >= 0.0D ? 1.0D : -1.0D;
        return normal() == Direction.Axis.X ? new Vec3(side, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, side);
    }

    /**
     * Which side of the gate's plane a point is on.
     *
     * <p>A plain boolean rather than a sign, because the only thing anything ever does with it is
     * compare it against the last one - and a sign has three values, one of which is a point sitting
     * exactly on the plane with nothing useful to say.
     */
    public boolean side(Vec3 point) {
        return distanceToPlane(point) >= 0.0D;
    }

    /** How far a point sits from the gate's plane, signed along the normal axis. */
    public double distanceToPlane(Vec3 point) {
        Vec3 centre = centre();
        return normal() == Direction.Axis.X ? point.x - centre.x : point.z - centre.z;
    }

    /**
     * How far a traveller is from the middle of the opening, along the gate's own axes.
     *
     * <p>Carried over as a <em>fraction</em> of the opening rather than as a distance, because two
     * gates are rarely the same size. Somebody who drives in through the top left corner comes out of
     * the top left corner of the far gate whatever size it happens to be, and nobody is ever put
     * outside an opening they entered inside.
     *
     * @return across and up, each -1 to 1 at the edges of the opening
     */
    public double acrossFraction(Vec3 point) {
        Vec3 centre = centre();
        double across = span == Direction.Axis.X ? point.x - centre.x : point.z - centre.z;
        return clampToEdge(across, halfWidth());
    }

    public double upFraction(Vec3 point) {
        return clampToEdge(point.y - centre().y, halfHeight());
    }

    /** A point in the opening, given fractions from {@link #acrossFraction} and {@link #upFraction}. */
    public Vec3 pointAt(double across, double up, double alongNormal) {
        Vec3 centre = centre();
        double x = centre.x;
        double z = centre.z;
        if (span == Direction.Axis.X) {
            x += across * halfWidth();
            z += alongNormal;
        } else {
            z += across * halfWidth();
            x += alongNormal;
        }
        return new Vec3(x, centre.y + up * halfHeight(), z);
    }

    /**
     * The slab of world a crossing is looked for in.
     *
     * <p>Only as deep as it needs to be. A thicker box would catch things standing beside the gate
     * rather than going through it, and the actual test for a crossing is a change of sign across the
     * plane, not presence in here.
     */
    public AABB catchment(double depth) {
        Vec3 centre = centre();
        double halfSpanX = span == Direction.Axis.X ? halfWidth() : depth;
        double halfSpanZ = span == Direction.Axis.X ? depth : halfWidth();
        return new AABB(centre.x - halfSpanX, centre.y - halfHeight(), centre.z - halfSpanZ,
                centre.x + halfSpanX, centre.y + halfHeight(), centre.z + halfSpanZ);
    }

    /**
     * Whether something of a given size could pass through this opening.
     *
     * <p>The aperture only hides what falls inside its own silhouette, so anything wider or taller
     * than the ring is visible sticking out of the portal at both ends at once. Refusing it is not a
     * balance decision - it is the one thing the illusion cannot survive.
     */
    public boolean admits(double acrossExtent, double verticalExtent) {
        return acrossExtent <= width() && verticalExtent <= height();
    }

    /** How wide a box is along this gate's own horizontal axis. */
    public double extentAcross(AABB box) {
        return span == Direction.Axis.X ? box.getXsize() : box.getZsize();
    }

    private static double clampToEdge(double offset, double half) {
        if (half <= 0.0D) {
            return 0.0D;
        }
        return Math.max(-1.0D, Math.min(1.0D, offset / half));
    }

    // ------------------------------------------------------------------- nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Span", span.getSerializedName());
        tag.putInt("MinX", minX);
        tag.putInt("MinY", minY);
        tag.putInt("MinZ", minZ);
        tag.putInt("MaxX", maxX);
        tag.putInt("MaxY", maxY);
        tag.putInt("MaxZ", maxZ);
        tag.putByteArray("Open", open.toByteArray());
        return tag;
    }

    public static RiftGateShape load(CompoundTag tag) {
        Direction.Axis span = "x".equals(tag.getString("Span")) ? Direction.Axis.X : Direction.Axis.Z;
        int minX = tag.getInt("MinX");
        int minY = tag.getInt("MinY");
        int minZ = tag.getInt("MinZ");
        int maxX = tag.getInt("MaxX");
        int maxY = tag.getInt("MaxY");
        int maxZ = tag.getInt("MaxZ");
        // A gate saved before the mask existed was necessarily being treated as a full rectangle, so
        // that is what it comes back as. It re-forms with a real mask the next time it validates.
        if (!tag.contains("Open")) {
            return new RiftGateShape(span, minX, minY, minZ, maxX, maxY, maxZ);
        }
        return new RiftGateShape(span, minX, minY, minZ, maxX, maxY, maxZ,
                BitSet.valueOf(tag.getByteArray("Open")));
    }

    private static void encode(RegistryFriendlyByteBuf buf, RiftGateShape shape) {
        buf.writeBoolean(shape.span == Direction.Axis.X);
        buf.writeBlockPos(new BlockPos(shape.minX, shape.minY, shape.minZ));
        buf.writeBlockPos(new BlockPos(shape.maxX, shape.maxY, shape.maxZ));
        buf.writeByteArray(shape.open.toByteArray());
    }

    private static RiftGateShape decode(RegistryFriendlyByteBuf buf) {
        Direction.Axis span = buf.readBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        // Bounded by MAX_AREA bits, so a byte array far larger than that is a broken or hostile
        // sender rather than a big gate.
        byte[] mask = buf.readByteArray(RiftGateStructure.MAX_AREA);
        return new RiftGateShape(span, min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ(), BitSet.valueOf(mask));
    }
}
