package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.List;

/**
 * "Open the navigation console on the drive at this position."
 *
 * <p>Carries nothing but a block position. The server decides which anchors the player is allowed to
 * see and what each of them costs.
 */
public record ServerboundNavigationRequestPacket(BlockPos drivePos) implements CustomPacketPayload {

    public static final Type<ServerboundNavigationRequestPacket> TYPE =
            new Type<>(AeroWarptics.id("navigation_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundNavigationRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBlockPos(packet.drivePos),
                    buf -> new ServerboundNavigationRequestPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundNavigationRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftDriveBlockEntity drive = AWNetwork.resolveDrive(player, packet.drivePos());
            if (drive == null) {
                return;
            }

            Airship airship = drive.airship();
            WarpFailure access = WarpValidator.validatePlayer(player, drive);
            List<WarpQuote> quotes = airship == null || access.isFailure()
                    ? List.of()
                    : WarpValidator.quoteAll(level, player, airship, drive.tier(), drive.charge());

            AWNetwork.sendTo(player, ClientboundNavigationDataPacket.of(drive, airship, quotes, access));
        });
    }
}
