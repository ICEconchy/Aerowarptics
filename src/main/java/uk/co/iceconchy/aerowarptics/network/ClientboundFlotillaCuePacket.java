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
 * One beat of a flotilla's muster or fold, for a crew's client to dress: a title, a chord, a flash.
 *
 * <p>Sounds that belong to a ship are played in the world by the server; what arrives here is what
 * belongs to the person hearing it, so a player who has turned the titles off keeps the sounds.
 *
 * @param cue   which beat
 * @param slot  the escort's slot number, 1 up, for {@link Cue#SLOT_STATE}; otherwise 0
 * @param good  whether it is good news - in station rather than adrift, everyone out rather than a
 *              straggler still due
 * @param ticks how long the beat lasts, for {@link Cue#COUNTDOWN}; otherwise 0
 */
public record ClientboundFlotillaCuePacket(Cue cue, int slot, boolean good, int ticks) implements CustomPacketPayload {

    public enum Cue {
        SOUND_STATIONS,
        TETHERS_OUT,
        RESOLVED,
        STOOD_DOWN,
        COUNTDOWN,
        SLOT_STATE,
        CALLED_OFF,
        ASSEMBLED,
        ECHO_INBOUND,
        SHAKEN_LOOSE,
        LEFT_BEHIND
    }

    public ClientboundFlotillaCuePacket(Cue cue, int slot, boolean good) {
        this(cue, slot, good, 0);
    }

    public static final Type<ClientboundFlotillaCuePacket> TYPE = new Type<>(AeroWarptics.id("flotilla_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFlotillaCuePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundFlotillaCuePacket::encode, ClientboundFlotillaCuePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundFlotillaCuePacket packet) {
        buf.writeEnum(packet.cue);
        buf.writeVarInt(packet.slot);
        buf.writeBoolean(packet.good);
        buf.writeVarInt(packet.ticks);
    }

    private static ClientboundFlotillaCuePacket decode(RegistryFriendlyByteBuf buf) {
        Cue cue = buf.readEnum(Cue.class);
        // Bounded, so a malformed packet cannot ask for a countdown the length of a day.
        int slot = Math.max(0, Math.min(999, buf.readVarInt()));
        boolean good = buf.readBoolean();
        int ticks = Math.max(0, Math.min(20 * 60, buf.readVarInt()));
        return new ClientboundFlotillaCuePacket(cue, slot, good, ticks);
    }

    public static void handle(ClientboundFlotillaCuePacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptFlotillaCue(packet));
    }
}
