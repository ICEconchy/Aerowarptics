package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * Where a Rift Wellhead's meltdown has got to: sent as it begins, at every beat, and once a second between, to
 * everyone near enough to be shown it.
 *
 * <p>It says how far through the scene is rather than what to do next, so a player who arrives half-way joins it
 * half-way, and one whose client stuttered is put right by the next. The range rides along because it is the
 * server's to set and a client never reads the server's config.
 *
 * @param crater  radius of the ground it takes, in blocks
 * @param frame   radius of the frame that failed
 * @param length  how long the whole scene is, in ticks
 * @param elapsed how many of those have gone
 * @param range   blocks from it within which it is shown
 * @param dry     the scene only: nothing in the world is changed, and the client pulls nobody
 */
public record ClientboundMeltdownCuePacket(BlockPos centre, int crater, int frame, int length, int elapsed, int range,
                                           boolean dry)
        implements CustomPacketPayload {

    public static final Type<ClientboundMeltdownCuePacket> TYPE = new Type<>(AeroWarptics.id("meltdown_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMeltdownCuePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundMeltdownCuePacket::encode, ClientboundMeltdownCuePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundMeltdownCuePacket packet) {
        buf.writeBlockPos(packet.centre);
        buf.writeVarInt(packet.crater);
        buf.writeVarInt(packet.frame);
        buf.writeVarInt(packet.length);
        buf.writeVarInt(packet.elapsed);
        buf.writeVarInt(packet.range);
        buf.writeBoolean(packet.dry);
    }

    private static ClientboundMeltdownCuePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos centre = buf.readBlockPos();
        int crater = Math.max(1, Math.min(63, buf.readVarInt()));
        int frame = Math.max(1, Math.min(3, buf.readVarInt()));
        int length = Math.max(20, Math.min(24_000, buf.readVarInt()));
        int elapsed = Math.max(0, Math.min(length, buf.readVarInt()));
        int range = Math.max(16, Math.min(1_024, buf.readVarInt()));
        return new ClientboundMeltdownCuePacket(centre, crater, frame, length, elapsed, range, buf.readBoolean());
    }

    public static void handle(ClientboundMeltdownCuePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.meltdownCue(packet));
    }
}
