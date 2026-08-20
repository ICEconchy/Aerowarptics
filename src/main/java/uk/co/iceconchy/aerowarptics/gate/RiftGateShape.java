package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The size, place and attitude of a gate's opening, without the opening itself.
 *
 * <p>Split from {@link RiftGateStructure.Opening} because this is the half that has to travel. A gate
 * being dialled from the other side of the world is in a chunk nobody has loaded, so its bounds and
 * its plane have to live in the registry rather than being read off the blocks - but the set of
 * positions inside the ring is only ever wanted at the moment a gate forms, and is far too big to keep
 * or send.
 *
 * @param span the horizontal axis the gate lies along; the other one is what you pass through
 */
public record RiftGateShape(Direction.Axis span,
                            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

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
        return tag;
    }

    public static RiftGateShape load(CompoundTag tag) {
        Direction.Axis span = "x".equals(tag.getString("Span")) ? Direction.Axis.X : Direction.Axis.Z;
        return new RiftGateShape(span,
                tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ"),
                tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ"));
    }

    private static void encode(RegistryFriendlyByteBuf buf, RiftGateShape shape) {
        buf.writeBoolean(shape.span == Direction.Axis.X);
        buf.writeBlockPos(new BlockPos(shape.minX, shape.minY, shape.minZ));
        buf.writeBlockPos(new BlockPos(shape.maxX, shape.maxY, shape.maxZ));
    }

    private static RiftGateShape decode(RegistryFriendlyByteBuf buf) {
        Direction.Axis span = buf.readBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        return new RiftGateShape(span, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
    }
}
