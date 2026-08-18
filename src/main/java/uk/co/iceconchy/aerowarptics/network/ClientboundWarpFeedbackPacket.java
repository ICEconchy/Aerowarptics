package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

/** The server's verdict on a warp command, shown to the commanding player as a status message. */
public record ClientboundWarpFeedbackPacket(WarpFailure failure) implements CustomPacketPayload {

    public static final Type<ClientboundWarpFeedbackPacket> TYPE =
            new Type<>(AeroWarptics.id("warp_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarpFeedbackPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeEnum(packet.failure),
                    buf -> new ClientboundWarpFeedbackPacket(buf.readEnum(WarpFailure.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundWarpFeedbackPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.showWarpFeedback(packet.failure()));
    }
}
