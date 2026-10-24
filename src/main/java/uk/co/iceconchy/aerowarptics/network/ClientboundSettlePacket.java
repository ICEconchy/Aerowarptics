package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.BorrowedMotion;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * "Stop - and keep only as much of the hull's motion as the hull itself still has."
 *
 * <p>What the game's own entity-motion packet used to be sent for, and could not do. A player's client
 * owns its position, and Sable keeps the motion a deck lent its passengers in a vector of its own that
 * the vanilla packet never touches (see {@link BorrowedMotion}). So a crew member the drive had
 * "calmed" went on flying at whatever the hull had last been doing, and on a warp that stopped dead in
 * the corridor, or a client that had read a teleport as movement, that was fast enough to throw them
 * clean off the ship.
 *
 * @param hullVelocity what the hull is doing now, in blocks per tick. Zero once it has settled; its
 *                     flight velocity when a passenger is put back on a deck that is still moving.
 */
public record ClientboundSettlePacket(Vector3d hullVelocity) implements CustomPacketPayload {

    public static final Type<ClientboundSettlePacket> TYPE = new Type<>(AeroWarptics.id("settle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSettlePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeDouble(packet.hullVelocity.x);
                        buf.writeDouble(packet.hullVelocity.y);
                        buf.writeDouble(packet.hullVelocity.z);
                    },
                    // A velocity that could itself be a fling is not honoured - the decode is where that is
                    // cheapest to be sure of.
                    buf -> new ClientboundSettlePacket(
                            BorrowedMotion.sane(new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))));

    public static ClientboundSettlePacket of(Vector3dc hullVelocity) {
        return new ClientboundSettlePacket(BorrowedMotion.sane(hullVelocity));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundSettlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.settle(packet.hullVelocity()));
    }
}
