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
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.List;
import java.util.UUID;

/**
 * The only thing a client may ever say about a warp: "start the one to this anchor", or "stop".
 *
 * <p>No position, no cost, no progress and no charge crosses this boundary. The server looks the
 * anchor up itself and refuses anything it does not like.
 */
public record ServerboundWarpCommandPacket(BlockPos drivePos, Action action, UUID anchorId)
        implements CustomPacketPayload {

    public enum Action {
        INITIATE,
        CANCEL,
        /** Step the drive's bearing on to the next setting. */
        CYCLE_HEADING
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundWarpCommandPacket> TYPE =
            new Type<>(AeroWarptics.id("warp_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundWarpCommandPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.drivePos);
                        buf.writeEnum(packet.action);
                        buf.writeUUID(packet.anchorId == null ? NIL : packet.anchorId);
                    },
                    buf -> new ServerboundWarpCommandPacket(
                            buf.readBlockPos(), buf.readEnum(Action.class), buf.readUUID()));

    public static ServerboundWarpCommandPacket initiate(BlockPos drivePos, UUID anchorId) {
        return new ServerboundWarpCommandPacket(drivePos, Action.INITIATE, anchorId);
    }

    public static ServerboundWarpCommandPacket cancel(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.CANCEL, NIL);
    }

    public static ServerboundWarpCommandPacket cycleHeading(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.CYCLE_HEADING, NIL);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundWarpCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftDriveBlockEntity drive = AWNetwork.resolveDrive(player, packet.drivePos());
            if (drive == null) {
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(WarpFailure.DRIVE_BUSY));
                return;
            }

            WarpFailure result = switch (packet.action()) {
                case INITIATE -> drive.requestWarp(player, packet.anchorId());
                case CANCEL -> drive.cancelWarp(player);
                case CYCLE_HEADING -> drive.cycleHeading(player) ? WarpFailure.NONE : WarpFailure.DRIVE_BUSY;
            };

            // Changing the bearing is a setting, not a warp; do not report it as one having started.
            if (packet.action() != Action.CYCLE_HEADING || result.isFailure()) {
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(result));
            }

            // Refresh the console so the player sees the drive's new state immediately.
            Airship airship = drive.airship();
            WarpFailure access = WarpValidator.validatePlayer(player, drive);
            List<uk.co.iceconchy.aerowarptics.warp.WarpQuote> quotes = airship == null || access.isFailure()
                    ? List.of()
                    : WarpValidator.quoteAll(level, player, airship, drive.tier(), drive.charge());
            AWNetwork.sendTo(player, ClientboundNavigationDataPacket.of(drive, airship, quotes, access));
        });
    }
}
