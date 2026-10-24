package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.Optional;

/**
 * The server's verdict on a warp command, shown to the commanding player as a status message.
 *
 * @param fastening what is tying the ship down and where, when the verdict is
 *                  {@link WarpFailure#SHIP_FASTENED} - "the ship is tied down" alone would leave a pilot
 *                  searching the whole deck for the one block that is doing it
 */
public record ClientboundWarpFeedbackPacket(WarpFailure failure, Optional<Fastening> fastening)
        implements CustomPacketPayload {

    public static final Type<ClientboundWarpFeedbackPacket> TYPE =
            new Type<>(AeroWarptics.id("warp_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarpFeedbackPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeEnum(packet.failure);
                        Fastening.OPTIONAL_STREAM_CODEC.encode(buf, packet.fastening);
                    },
                    buf -> new ClientboundWarpFeedbackPacket(buf.readEnum(WarpFailure.class),
                            Fastening.OPTIONAL_STREAM_CODEC.decode(buf)));

    public ClientboundWarpFeedbackPacket(WarpFailure failure) {
        this(failure, Optional.empty());
    }

    /** A verdict with what caused it, if anything did. */
    public static ClientboundWarpFeedbackPacket of(WarpFailure failure, @Nullable Fastening fastening) {
        return new ClientboundWarpFeedbackPacket(failure, Optional.ofNullable(fastening));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundWarpFeedbackPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.showWarpFeedback(packet.failure(), packet.fastening().orElse(null)));
    }
}
