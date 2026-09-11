package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

import java.util.UUID;

/**
 * "The hull you are watching jumped; it did not fly there."
 *
 * <p>Sable's pose sync carries no way to say that, and a client that reads a four-thousand-block
 * teleport as movement builds a collision volume four thousand blocks long - which Sable refuses,
 * dropping the crew through the deck and leaving the hull undrawn. See
 * {@link uk.co.iceconchy.aerowarptics.client.FoldCrossings} for the whole of it.
 *
 * <p>Carries an id and nothing else. The client cannot be told <em>where</em> the ship went, because
 * by the time it can act on that it has already been told by Sable; what it needs is permission to
 * treat the next impossible-looking move as what it actually was.
 */
public record ClientboundFoldCrossedPacket(UUID shipId) implements CustomPacketPayload {

    public static final Type<ClientboundFoldCrossedPacket> TYPE =
            new Type<>(AeroWarptics.id("fold_crossed"));

    /**
     * A one-tick move beyond this many blocks cannot be flight - only a fold crossing.
     *
     * <p>The single source of truth for the jump threshold, kept here rather than in the client-only
     * {@code FoldCrossings} so the server can measure a crossing against it too (the warp trace does).
     * An airship under way covers a couple of blocks a tick and the corridor passage is under two, so
     * anything past this is a discontinuity, and the only thing in this mod that produces one is the
     * crossing itself.
     */
    public static final double JUMP_BLOCKS = 128.0D;

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFoldCrossedPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundFoldCrossedPacket::encode, ClientboundFoldCrossedPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundFoldCrossedPacket packet) {
        buf.writeUUID(packet.shipId);
    }

    private static ClientboundFoldCrossedPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundFoldCrossedPacket(buf.readUUID());
    }

    public static void handle(ClientboundFoldCrossedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.foldCrossed(packet.shipId()));
    }
}
