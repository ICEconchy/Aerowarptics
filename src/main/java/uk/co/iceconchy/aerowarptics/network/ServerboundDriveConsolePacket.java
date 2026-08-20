package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

/**
 * "Show me what the drive at this position is doing."
 *
 * <p>Carries nothing but a block position, and asks for nothing that changes anything.
 */
public record ServerboundDriveConsolePacket(BlockPos drivePos) implements CustomPacketPayload {

    public static final Type<ServerboundDriveConsolePacket> TYPE =
            new Type<>(AeroWarptics.id("drive_console_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundDriveConsolePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBlockPos(packet.drivePos),
                    buf -> new ServerboundDriveConsolePacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundDriveConsolePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel)) {
                return;
            }
            RiftDriveBlockEntity drive = AWNetwork.resolveDrive(player, packet.drivePos());
            if (drive == null) {
                return;
            }
            WarpFailure access = WarpValidator.validatePlayer(player, drive);
            AWNetwork.sendTo(player, ClientboundDriveConsolePacket.of(drive, drive.airship(), access));
        });
    }
}
