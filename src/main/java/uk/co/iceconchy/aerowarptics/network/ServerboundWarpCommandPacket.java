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
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

/**
 * The things a player may say to a drive from its own console: "stop", "turn the bow", "switch how you
 * fly", "stop looking before you leap" (or start again), and "let go".
 *
 * <p>Notably absent is "go". A waypoint jump needs a destination chosen at an Astrolabe Cartography
 * Table, and both it and a free cruise are fired by a redstone input - so there is no path from this
 * screen to a ship setting off, and nothing here needs to carry an anchor. Letting go of a cruise is
 * the one exception worth making: it only ever ends a journey, and a pilot in the fold should not have
 * to go looking for the lever.
 */
public record ServerboundWarpCommandPacket(BlockPos drivePos, Action action) implements CustomPacketPayload {

    public enum Action {
        /** Call off a warp that has not opened its rift yet. */
        CANCEL,
        /** Step the drive's bow setting on to the next quarter turn. */
        CYCLE_HEADING,
        /** Switch between flying the standing course and cruising. */
        CYCLE_MODE,
        /** Let go of a free cruise, putting the ship down as far along its bearing as it has come. */
        DISENGAGE,
        /** Switch the drive's clearance checks on or off. Appended: the action crosses the wire by ordinal. */
        TOGGLE_CLEARANCE
    }

    public static final Type<ServerboundWarpCommandPacket> TYPE =
            new Type<>(AeroWarptics.id("warp_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundWarpCommandPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.drivePos);
                        buf.writeEnum(packet.action);
                    },
                    buf -> new ServerboundWarpCommandPacket(buf.readBlockPos(), buf.readEnum(Action.class)));

    public static ServerboundWarpCommandPacket cancel(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.CANCEL);
    }

    public static ServerboundWarpCommandPacket cycleHeading(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.CYCLE_HEADING);
    }

    public static ServerboundWarpCommandPacket cycleMode(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.CYCLE_MODE);
    }

    public static ServerboundWarpCommandPacket disengage(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.DISENGAGE);
    }

    public static ServerboundWarpCommandPacket toggleClearance(BlockPos drivePos) {
        return new ServerboundWarpCommandPacket(drivePos, Action.TOGGLE_CLEARANCE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundWarpCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel)) {
                return;
            }
            RiftDriveBlockEntity drive = AWNetwork.resolveDrive(player, packet.drivePos());
            if (drive == null) {
                Trace.log(Trace.DRIVE, "{} console {} from {} refused: no drive there it could reach",
                        Trace.pos(packet.drivePos()), packet.action(), Trace.who(player));
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(WarpFailure.DRIVE_BUSY));
                return;
            }

            WarpFailure result = switch (packet.action()) {
                case CANCEL -> drive.cancelWarp(player);
                case CYCLE_HEADING -> drive.cycleHeading(player) ? WarpFailure.NONE : WarpFailure.DRIVE_BUSY;
                case CYCLE_MODE -> drive.cycleMode(player) ? WarpFailure.NONE : WarpFailure.DRIVE_BUSY;
                case DISENGAGE -> drive.disengage(player);
                case TOGGLE_CLEARANCE -> drive.toggleClearanceChecks(player) ? WarpFailure.NONE : WarpFailure.DRIVE_BUSY;
            };
            Trace.log(Trace.DRIVE, "{} console {} by {} -> {} (now {} mode={} bow={} checks={})",
                    Trace.pos(drive.getBlockPos()), packet.action(), Trace.who(player), result.getSerializedName(),
                    drive.state(), drive.mode(), drive.heading(), drive.clearanceChecks() ? "on" : "OFF");

            // Only refusals are worth a message. Turning the bow succeeding is visible on the
            // machine, and a successful cancel already announces itself - aborting the sequence tells
            // whoever set the course why it stopped, so saying anything here would either duplicate
            // that or, worse, report the cancellation as a warp having started.
            if (result.isFailure()) {
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(result));
            }

            // Refresh the console so the player sees the drive's new state immediately.
            WarpFailure access = WarpValidator.validatePlayer(player, drive);
            AWNetwork.sendTo(player, ClientboundDriveConsolePacket.of(drive, drive.airship(), access));
        });
    }
}
