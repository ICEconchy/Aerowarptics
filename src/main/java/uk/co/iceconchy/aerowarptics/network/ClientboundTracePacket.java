package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * "This server is tracing" or "it has stopped".
 *
 * <p>Sent to each player as they join and to everyone when the switch changes, so a player on a traced
 * server traces their own side into their own log without being asked to find a second setting. The
 * client does not read the server's config for this, by the same rule as everything else: the packet
 * carries it.
 */
public record ClientboundTracePacket(boolean tracing) implements CustomPacketPayload {

    public static final Type<ClientboundTracePacket> TYPE = new Type<>(AeroWarptics.id("trace"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundTracePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundTracePacket::encode, ClientboundTracePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundTracePacket packet) {
        buf.writeBoolean(packet.tracing);
    }

    private static ClientboundTracePacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundTracePacket(buf.readBoolean());
    }

    public static void handle(ClientboundTracePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.serverTracing(packet.tracing()));
    }
}
