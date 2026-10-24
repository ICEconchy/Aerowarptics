package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * The unsettled ground a meltdown left, near the player it is sent to: where, how far, and how much of it is
 * left. Sent once a second while they are near; a client forgets a zone it stops hearing of.
 */
public record ClientboundScarZonePacket(BlockPos centre, int radius, float strength) implements CustomPacketPayload {

    public static final Type<ClientboundScarZonePacket> TYPE = new Type<>(AeroWarptics.id("scar_zone"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundScarZonePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundScarZonePacket::encode, ClientboundScarZonePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundScarZonePacket packet) {
        buf.writeBlockPos(packet.centre);
        buf.writeVarInt(packet.radius);
        buf.writeFloat(packet.strength);
    }

    private static ClientboundScarZonePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos centre = buf.readBlockPos();
        int radius = Math.max(1, Math.min(256, buf.readVarInt()));
        float strength = buf.readFloat();
        return new ClientboundScarZonePacket(centre, radius,
                Float.isFinite(strength) ? Math.max(0.0F, Math.min(1.0F, strength)) : 0.0F);
    }

    public static void handle(ClientboundScarZonePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.scarZone(packet));
    }
}
