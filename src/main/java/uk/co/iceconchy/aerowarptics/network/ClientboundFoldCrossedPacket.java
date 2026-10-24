package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.HullJump;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

import java.util.UUID;

/**
 * "The hull you are watching jumped; it did not fly there. Here is exactly where it went."
 *
 * <p>Sable's pose sync carries no way to say that, and a client left to itself interpolates a
 * teleport like any other move: for a tick it draws the hull part of the way across, in chunks it does
 * not have, and anyone standing on it is carried along the same impossible stretch. See
 * {@link uk.co.iceconchy.aerowarptics.client.FoldCrossings} for the whole of it.
 *
 * <p>It used to carry only the ship's id, which was enough to recognise a jump after the fact but not
 * to undo it. It now carries the jump itself - both placements of the hull's origin - so a client can
 * move everything it has buffered for that hull onto the far side at once, before any of it is drawn.
 *
 * @param carry whether this client's own player was carried across with the hull. The server moved
 *              them in the same tick, and they are told this way rather than with a teleport so that
 *              they land on the same plank of the deck in the client's own view of it, not merely at
 *              the same world coordinates in the server's.
 */
public record ClientboundFoldCrossedPacket(UUID shipId, Vector3d from, Quaterniond fromOrientation,
                                           Vector3d to, Quaterniond toOrientation, boolean carry)
        implements CustomPacketPayload {

    public static final Type<ClientboundFoldCrossedPacket> TYPE =
            new Type<>(AeroWarptics.id("fold_crossed"));

    /**
     * A one-tick move beyond this many blocks cannot be flight - only a fold crossing.
     *
     * <p>The single source of truth for the jump threshold, kept here rather than in the client-only
     * {@code FoldCrossings} so the server can measure a crossing against it too (the warp trace does).
     * An airship under way covers a couple of blocks a tick and the corridor passage is under two, so
     * anything past this is a discontinuity, and the only thing in this mod that produces one is the
     * crossing itself.
     */
    public static final double JUMP_BLOCKS = 128.0D;

    /**
     * Past this, a coordinate is not a place in any world. Minecraft's own border stops at thirty
     * million; plot space runs a little further, so this is generous rather than exact - it exists to
     * refuse a corrupt or hostile packet, not to police the map.
     */
    private static final double FAR_LIMIT = 1.0E9D;

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFoldCrossedPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundFoldCrossedPacket::encode, ClientboundFoldCrossedPacket::decode);

    public static ClientboundFoldCrossedPacket of(UUID shipId, HullJump jump, boolean carry) {
        return new ClientboundFoldCrossedPacket(shipId,
                new Vector3d(jump.from()), new Quaterniond(jump.fromOrientation()),
                new Vector3d(jump.to()), new Quaterniond(jump.toOrientation()), carry);
    }

    /** The jump this describes. */
    public HullJump jump() {
        return HullJump.between(from, fromOrientation, to, toOrientation);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundFoldCrossedPacket packet) {
        buf.writeUUID(packet.shipId);
        writeVector(buf, packet.from);
        writeRotation(buf, packet.fromOrientation);
        writeVector(buf, packet.to);
        writeRotation(buf, packet.toOrientation);
        buf.writeBoolean(packet.carry);
    }

    private static ClientboundFoldCrossedPacket decode(RegistryFriendlyByteBuf buf) {
        UUID shipId = buf.readUUID();
        Vector3d from = readVector(buf);
        Quaterniond fromOrientation = readRotation(buf);
        Vector3d to = readVector(buf);
        Quaterniond toOrientation = readRotation(buf);
        return new ClientboundFoldCrossedPacket(shipId, from, fromOrientation, to, toOrientation, buf.readBoolean());
    }

    private static void writeVector(RegistryFriendlyByteBuf buf, Vector3d v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static void writeRotation(RegistryFriendlyByteBuf buf, Quaterniond q) {
        buf.writeDouble(q.x);
        buf.writeDouble(q.y);
        buf.writeDouble(q.z);
        buf.writeDouble(q.w);
    }

    private static Vector3d readVector(RegistryFriendlyByteBuf buf) {
        Vector3d v = new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        if (!v.isFinite() || Math.abs(v.x) > FAR_LIMIT || Math.abs(v.y) > FAR_LIMIT || Math.abs(v.z) > FAR_LIMIT) {
            throw new IllegalArgumentException("fold crossing names a place that is not in any world: " + v);
        }
        return v;
    }

    private static Quaterniond readRotation(RegistryFriendlyByteBuf buf) {
        Quaterniond q = new Quaterniond(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        double length = q.lengthSquared();
        if (!Double.isFinite(length) || length < 1.0E-6D) {
            throw new IllegalArgumentException("fold crossing carries no usable rotation");
        }
        return q.normalize();
    }

    public static void handle(ClientboundFoldCrossedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.foldCrossed(packet));
    }
}
