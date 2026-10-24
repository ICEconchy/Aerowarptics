package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity;

/**
 * An order from a Rift Wellhead's panel: scram it, open or shut its hatch, or only say again how it stands.
 *
 * <p>Honoured for a formed collar the sender is standing within reach of, and for nothing else - a packet names
 * a position, and a position is all a stranger across the world needs to write one. Every order is answered
 * with the panel again, which is how the panel hears whether it was obeyed.
 */
public record ServerboundWellheadOrderPacket(BlockPos collar, Order order) implements CustomPacketPayload {

    public enum Order {
        REFRESH,
        SCRAM,
        HATCH
    }

    public static final Type<ServerboundWellheadOrderPacket> TYPE = new Type<>(AeroWarptics.id("wellhead_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundWellheadOrderPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeBlockPos(packet.collar);
                buf.writeEnum(packet.order);
            }, buf -> new ServerboundWellheadOrderPacket(buf.readBlockPos(), buf.readEnum(Order.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundWellheadOrderPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.level().isLoaded(packet.collar)
                    || !(player.level().getBlockEntity(packet.collar) instanceof RiftCollarBlockEntity collar)
                    || !collar.inReach(player)) {
                return;
            }
            switch (packet.order) {
                case SCRAM -> {
                    boolean obeyed = collar.scram();
                    Trace.log(Trace.WELLHEAD, "{} ordered a scram at {} from the panel: {}", Trace.who(player),
                            Trace.pos(packet.collar), obeyed ? "obeyed" : "too late, or no frame");
                }
                case HATCH -> collar.toggleHatch();
                case REFRESH -> {
                }
            }
            collar.showPanel(player);
        });
    }
}
