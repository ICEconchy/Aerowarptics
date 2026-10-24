package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.weather.StormForecast;

/**
 * "A Rift Storm is raging" or "it has passed".
 *
 * <p>One flag, and what a Stormglass reads - see {@link StormForecast}. The reading is sent whenever it changes,
 * not only when a storm begins or ends, because a storm drawing within a day changes it on its own. The storm is
 * server-wide, so the client does not need telling which
 * dimension it is over: it can see for itself whether the level it is standing in has a sky, and asks
 * that each tick rather than being re-sent the state on every change of dimension. How long is left is
 * not sent either - nothing drawn depends on it, and the end arrives as its own packet.
 */
public record ClientboundRiftStormPacket(boolean raging, int reading) implements CustomPacketPayload {

    public static final Type<ClientboundRiftStormPacket> TYPE =
            new Type<>(AeroWarptics.id("rift_storm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRiftStormPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundRiftStormPacket::encode, ClientboundRiftStormPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundRiftStormPacket packet) {
        buf.writeBoolean(packet.raging);
        buf.writeByte(packet.reading);
    }

    private static ClientboundRiftStormPacket decode(RegistryFriendlyByteBuf buf) {
        boolean raging = buf.readBoolean();
        // Only a reading the client knows; anything else from a confused server reads as clear.
        int reading = buf.readUnsignedByte();
        return new ClientboundRiftStormPacket(raging, reading < StormForecast.Reading.values().length ? reading : 0);
    }

    public static void handle(ClientboundRiftStormPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            AWClientHooks.setRiftStorm(packet.raging());
            AWClientHooks.setStormReading(packet.reading());
        });
    }
}
