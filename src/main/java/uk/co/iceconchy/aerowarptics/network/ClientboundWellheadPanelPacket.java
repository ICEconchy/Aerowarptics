package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * Everything a Rift Wellhead's panel shows, as the server has it: sent when the panel is opened and in answer to
 * every order from it, the refresh included.
 *
 * <p>A client works none of this out. How hard the cage damps, what the well is heading for and how fast it is
 * digesting are all the server's config at work, which a client never reads.
 *
 * @param tier        0, 1 or 2
 * @param yield       millibuckets a second it is digesting at now
 * @param speedShare  what rotation is giving of its damping, 0 to 1
 * @param cageShare   what the cage is giving, 0 to 1
 * @param open        whether this is the answer to a click on the machine, and so opens the panel; an answer to
 *                    an order only ever brings an open panel up to date, or one closed a moment ago would reopen
 */
public record ClientboundWellheadPanelPacket(BlockPos collar, int tier, float strain, float heading, float integrity,
                                             int matter, int gullet, int tank, int tankCapacity, int stabilisers,
                                             int needed, float imbalance, float speedShare, float cageShare,
                                             float yield, int lockout, boolean hatchShut, boolean storm, boolean open)
        implements CustomPacketPayload {

    public static final Type<ClientboundWellheadPanelPacket> TYPE = new Type<>(AeroWarptics.id("wellhead_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWellheadPanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundWellheadPanelPacket::encode, ClientboundWellheadPanelPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundWellheadPanelPacket packet) {
        buf.writeBlockPos(packet.collar);
        buf.writeVarInt(packet.tier);
        buf.writeFloat(packet.strain);
        buf.writeFloat(packet.heading);
        buf.writeFloat(packet.integrity);
        buf.writeVarInt(packet.matter);
        buf.writeVarInt(packet.gullet);
        buf.writeVarInt(packet.tank);
        buf.writeVarInt(packet.tankCapacity);
        buf.writeVarInt(packet.stabilisers);
        buf.writeVarInt(packet.needed);
        buf.writeFloat(packet.imbalance);
        buf.writeFloat(packet.speedShare);
        buf.writeFloat(packet.cageShare);
        buf.writeFloat(packet.yield);
        buf.writeVarInt(packet.lockout);
        buf.writeBoolean(packet.hatchShut);
        buf.writeBoolean(packet.storm);
        buf.writeBoolean(packet.open);
    }

    private static ClientboundWellheadPanelPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundWellheadPanelPacket(buf.readBlockPos(), Math.max(0, Math.min(2, buf.readVarInt())),
                unit(buf.readFloat()), unit(buf.readFloat()), unit(buf.readFloat()), count(buf.readVarInt()),
                count(buf.readVarInt()), count(buf.readVarInt()), count(buf.readVarInt()),
                Math.max(0, Math.min(4_096, buf.readVarInt())), Math.max(0, Math.min(64, buf.readVarInt())),
                unit(buf.readFloat()), unit(buf.readFloat()), unit(buf.readFloat()),
                amount(buf.readFloat()), Math.max(0, Math.min(86_400, buf.readVarInt())), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean());
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    private static float amount(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0e6F, value)) : 0.0F;
    }

    private static int count(int value) {
        return Math.max(0, Math.min(100_000_000, value));
    }

    public static void handle(ClientboundWellheadPanelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.wellheadPanel(packet));
    }
}
