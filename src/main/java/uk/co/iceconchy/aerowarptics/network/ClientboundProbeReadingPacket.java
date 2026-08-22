package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.probe.ProbeSounding;

/**
 * The picture a sounding brought back.
 *
 * <p>Kept apart from {@link ClientboundProbePacket} because it is three orders of magnitude larger.
 * The panel updates twice a second; this is sent once, when a reading is new or when a screen that
 * has just been opened asks for the one already on the machine.
 */
public record ClientboundProbeReadingPacket(BlockPos probePos, ProbeSounding sounding)
        implements CustomPacketPayload {

    public static final Type<ClientboundProbeReadingPacket> TYPE =
            new Type<>(AeroWarptics.id("probe_reading"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundProbeReadingPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundProbeReadingPacket::encode, ClientboundProbeReadingPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundProbeReadingPacket packet) {
        buf.writeBlockPos(packet.probePos);
        ProbeSounding.STREAM_CODEC.encode(buf, packet.sounding);
    }

    private static ClientboundProbeReadingPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundProbeReadingPacket(buf.readBlockPos(), ProbeSounding.STREAM_CODEC.decode(buf));
    }

    public static void handle(ClientboundProbeReadingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.acceptProbeReading(packet));
    }
}
