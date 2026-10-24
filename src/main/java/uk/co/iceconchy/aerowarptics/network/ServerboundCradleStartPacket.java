package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleMenu;

/**
 * The Collapse Cradle's Start, pressed on its panel.
 *
 * <p>Honoured only from a player who has that very cradle's panel open and is still near enough to keep it open -
 * the menu is the permission, as a chest's is. What it starts is decided on the server, exactly as a redstone
 * pulse's is: a collapse or a fold, from what the arms hold, refused before anything is spent.
 */
public record ServerboundCradleStartPacket(BlockPos cradlePos) implements CustomPacketPayload {

    public static final Type<ServerboundCradleStartPacket> TYPE = new Type<>(AeroWarptics.id("cradle_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundCradleStartPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> buf.writeBlockPos(packet.cradlePos),
                    buf -> new ServerboundCradleStartPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundCradleStartPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof CollapseCradleMenu menu)
                    || !menu.pos().equals(packet.cradlePos) || !menu.stillValid(player)
                    || !(player.level().getBlockEntity(packet.cradlePos) instanceof CollapseCradleBlockEntity cradle)) {
                return;
            }
            cradle.start(player);
        });
    }
}
