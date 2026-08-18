package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * Tells one player that they are personally inside the warp corridor.
 *
 * <p>Sent to the exact set of {@code ServerPlayer}s the server found standing on or riding the
 * airship, because the server is the only side that reliably knows who is aboard. The client used to
 * work this out for itself by asking Sable which sub-level was carrying the player, which is fragile:
 * a player merely standing on a deck is not always tracked client-side, so the corridor never showed
 * up for them.
 *
 * @param active   whether the corridor is being entered or left
 * @param duration ticks the corridor is expected to last, as a safety net if the exit cue is missed
 * @param colour   packed RGB of the rift, so the tunnel matches the drive that opened it
 */
public record ClientboundCorridorPacket(boolean active, int duration, int colour) implements CustomPacketPayload {

    public static final Type<ClientboundCorridorPacket> TYPE = new Type<>(AeroWarptics.id("warp_corridor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCorridorPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBoolean(packet.active);
                        buf.writeVarInt(packet.duration);
                        buf.writeInt(packet.colour);
                    },
                    buf -> new ClientboundCorridorPacket(buf.readBoolean(), buf.readVarInt(), buf.readInt()));

    public static ClientboundCorridorPacket enter(int duration, int colour) {
        return new ClientboundCorridorPacket(true, duration, colour);
    }

    public static ClientboundCorridorPacket leave() {
        return new ClientboundCorridorPacket(false, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundCorridorPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.setInWarpCorridor(packet));
    }
}
