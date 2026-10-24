package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * "A Rift Ward is turning a rift aside: here is where it was aimed, where it is going, and how long until it
 * gets there."
 *
 * <p>The whole of a deflection as a client needs it. The flight was planned to the rim before the ship left, so
 * this is told once, as the ship enters its corridor, and everything after is the client's own clock running
 * {@code WardChoreography} - nothing is sent again, and a client that joins late sees no scene, only the hull.
 *
 * <p>Its own packet rather than another stage of {@code ClientboundWarpEffectPacket}: that one describes an
 * aperture, and this describes a track between two places and the machine that drew it.
 *
 * @param ward      the ward's master block
 * @param aimed     where the rift was aimed
 * @param went      where it comes out; the same as {@code aimed} for a ward that gave way
 * @param total     ticks from now until it breaks open
 * @param colour    the rift's own colour, for the tear
 * @param tollRange blocks from the ward within which its toll is heard - the server's setting, which a client
 *                  never reads for itself
 * @param aboard    whether this client's player is on the ship being turned
 */
public record ClientboundWardCuePacket(BlockPos ward, Vec3 aimed, Vec3 went, int total, Kind kind, int colour,
                                       int tollRange, boolean aboard) implements CustomPacketPayload {

    /** What the ward did. Sent by ordinal: append, never insert. */
    public enum Kind {
        /** A ship turned aside: the whole scene. */
        DEFLECTED,
        /** The ward could not pay: the weight starts for the tear and is let drop. */
        GAVE_WAY,
        /** A Rift Plotter's exit turned aside: the same scene, small and near. */
        PLOTTED
    }

    public static final Type<ClientboundWardCuePacket> TYPE = new Type<>(AeroWarptics.id("ward_cue"));

    /** Past this, a coordinate is not a place in any world - see {@code ClientboundFoldCrossedPacket}. */
    private static final double FAR_LIMIT = 1.0E9D;

    /** The longest run-up a cue may ask a client to play: a corridor's own ceiling, and some. */
    private static final int MOST_TICKS = 24_000;

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWardCuePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundWardCuePacket::encode, ClientboundWardCuePacket::decode);

    /** This cue, for somebody aboard the ship. */
    public ClientboundWardCuePacket forCrew() {
        return new ClientboundWardCuePacket(ward, aimed, went, total, kind, colour, tollRange, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundWardCuePacket packet) {
        buf.writeBlockPos(packet.ward);
        buf.writeDouble(packet.aimed.x);
        buf.writeDouble(packet.aimed.y);
        buf.writeDouble(packet.aimed.z);
        buf.writeDouble(packet.went.x);
        buf.writeDouble(packet.went.y);
        buf.writeDouble(packet.went.z);
        buf.writeVarInt(packet.total);
        buf.writeEnum(packet.kind);
        buf.writeInt(packet.colour);
        buf.writeVarInt(packet.tollRange);
        buf.writeBoolean(packet.aboard);
    }

    private static ClientboundWardCuePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos ward = buf.readBlockPos();
        Vec3 aimed = sane(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 went = sane(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int total = Math.max(1, Math.min(MOST_TICKS, buf.readVarInt()));
        Kind kind = buf.readEnum(Kind.class);
        int colour = buf.readInt();
        int tollRange = Math.max(0, Math.min(1024, buf.readVarInt()));
        boolean aboard = buf.readBoolean();
        return new ClientboundWardCuePacket(ward, aimed, went, total, kind, colour, tollRange, aboard);
    }

    /** A place that is one, or the origin for anything that is not. */
    private static Vec3 sane(double x, double y, double z) {
        boolean finite = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) < FAR_LIMIT && Math.abs(y) < FAR_LIMIT && Math.abs(z) < FAR_LIMIT;
        return finite ? new Vec3(x, y, z) : Vec3.ZERO;
    }

    public static void handle(ClientboundWardCuePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.wardCue(packet));
    }
}
