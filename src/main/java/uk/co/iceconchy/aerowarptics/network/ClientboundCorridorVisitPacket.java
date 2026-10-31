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
 * How long one player has left in the Rift Corridor, for the countdown on their own view.
 *
 * <p>Sent when they step in through a gate, again each second while they are in there, and once with
 * {@link #gone()} when they are out. Said on the view and never in chat: a player in a corridor is there to get at
 * a train that has stopped in it, and a line of chat every second while they work is not a clock.
 *
 * <p>Re-sent every second rather than left to the client to count, because the clock stops while they are standing
 * on a train - which is the whole point of going in - and a client counting on its own could not know that.
 *
 * @param ticks how long they may stay, as the server has it now. Zero means they are out, or going out.
 */
public record ClientboundCorridorVisitPacket(int ticks) implements CustomPacketPayload {

    /** The longest visit a client will believe, in ticks. Beyond anything the server config allows. */
    public static final int MAX_TICKS = 144_000;

    public static final Type<ClientboundCorridorVisitPacket> TYPE = new Type<>(AeroWarptics.id("corridor_visit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCorridorVisitPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeVarInt(packet.ticks),
                    buf -> new ClientboundCorridorVisitPacket(
                            Math.max(0, Math.min(MAX_TICKS, buf.readVarInt()))));

    public static ClientboundCorridorVisitPacket left(int ticks) {
        return new ClientboundCorridorVisitPacket(Math.max(0, Math.min(MAX_TICKS, ticks)));
    }

    public static ClientboundCorridorVisitPacket gone() {
        return new ClientboundCorridorVisitPacket(0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundCorridorVisitPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.setCorridorVisit(packet.ticks));
    }
}
