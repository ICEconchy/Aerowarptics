package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.List;
import java.util.UUID;

/**
 * Everything a player can do at an Astrolabe Cartography Table.
 *
 * <p>One payload for all three because they share every check: the table has to exist, be complete, be
 * within reach, and belong to a hull the player may command. Splitting them into three packets would
 * mean writing that gate three times.
 *
 * @param tablePos the cell the player clicked, in its own level's coordinates - inside an airship's
 *                 plot, not the world. The centre is resolved from it server-side.
 * @param action   what the player is doing
 * @param anchorId destination for {@link Action#SELECT} and {@link Action#PREVIEW}, otherwise ignored
 */
public record ServerboundAstrolabePacket(BlockPos tablePos, Action action, UUID anchorId)
        implements CustomPacketPayload {

    public enum Action {
        /** Open the chart: send this table's destination list back. */
        OPEN,
        /** Point the table, and the drive on its hull, at an anchor. */
        SELECT,
        /** Survey the ground around an anchor, for the preview panel. */
        PREVIEW
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundAstrolabePacket> TYPE = new Type<>(AeroWarptics.id("astrolabe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAstrolabePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundAstrolabePacket::encode, ServerboundAstrolabePacket::decode);

    public static ServerboundAstrolabePacket open(BlockPos tablePos) {
        return new ServerboundAstrolabePacket(tablePos, Action.OPEN, NIL);
    }

    public static ServerboundAstrolabePacket select(BlockPos tablePos, UUID anchorId) {
        return new ServerboundAstrolabePacket(tablePos, Action.SELECT, anchorId);
    }

    public static ServerboundAstrolabePacket preview(BlockPos tablePos, UUID anchorId) {
        return new ServerboundAstrolabePacket(tablePos, Action.PREVIEW, anchorId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundAstrolabePacket packet) {
        buf.writeBlockPos(packet.tablePos);
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.anchorId == null ? NIL : packet.anchorId);
    }

    private static ServerboundAstrolabePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundAstrolabePacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUUID());
    }

    public static void handle(ServerboundAstrolabePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            AstrolabeBlockEntity table = resolve(player, level, packet.tablePos);
            if (table == null) {
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(WarpFailure.NO_AIRSHIP));
                return;
            }

            switch (packet.action) {
                case OPEN -> sendChart(player, level, table);
                case SELECT -> {
                    WarpFailure result = table.select(player, packet.anchorId);
                    if (result.isFailure()) {
                        AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(result));
                    }
                    sendChart(player, level, table);
                }
                case PREVIEW -> sendPreview(player, level, packet.anchorId);
            }
        });
    }

    /**
     * Finds the table's centre, having first checked the player is entitled to be looking at it.
     *
     * <p>A table on an airship lives in a plot chunk, which is loaded whenever the hull is, so an
     * unloaded position means the client is talking about something that is not there. The reach
     * bound is deliberately generous and deliberately cheap - it exists to stop a client driving a
     * table on the far side of the world, not to be the authority on who may set a course. That
     * authority is the drive's, and it is applied when the course is actually set.
     */
    private static AstrolabeBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AstrolabeBlockEntity cell)) {
            return null;
        }
        AstrolabeBlockEntity centre = cell.controller();
        if (centre == null) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        if (centre.airship() == null && player.blockPosition().distSqr(pos) > reach * reach) {
            return null;
        }
        return centre;
    }

    private static void sendChart(ServerPlayer player, ServerLevel level, AstrolabeBlockEntity table) {
        RiftDriveBlockEntity drive = table.drive();
        Airship airship = table.airship();

        WarpFailure access = drive == null ? WarpFailure.NO_AIRSHIP : WarpValidator.validatePlayer(player, drive);
        RiftDriveTier tier = drive == null ? RiftDriveTier.MK_I : drive.tier();
        float charge = drive == null ? 0.0F : drive.charge();
        List<WarpQuote> available = drive == null || airship == null || access.isFailure()
                ? List.<WarpQuote>of()
                : WarpValidator.quoteAll(level, player, airship, tier, charge);
        // Nothing upstream bounds this: anchors are unlimited by default and every one the player may
        // see is quoted. Over the packet's limit the encoder throws rather than truncating, so the cap
        // has to happen here, and the untruncated total goes with it so the chart can say so.
        List<WarpQuote> quotes = PacketLists.cap(available, WarpQuote.NEAREST_USABLE_FIRST);

        // The drive's own course, not the table's memory of what was last clicked here. A Rift Probe
        // can aim the same drive at a bare position, and a chart that kept showing its own last
        // selection would be confidently pointing at an anchor the ship is no longer going to.
        WarpCourse course = drive == null ? null : drive.standingCourse();
        UUID selected = course != null && course.isAnchor() ? course.anchorId() : null;
        BlockPos fix = course != null && course.isFix() ? course.fix() : null;
        String label = course == null ? "" : course.label();

        AWNetwork.sendTo(player, new ClientboundAstrolabeChartPacket(
                table.getBlockPos(),
                tier.index(),
                charge,
                tier.maximumRange(),
                airship == null || airship.name() == null ? "" : airship.name(),
                drive != null,
                access,
                selected,
                fix,
                label,
                available.size(),
                quotes));
    }

    /**
     * Surveys the ground around an anchor and sends the picture back.
     *
     * <p>Gated on the player being allowed to see the anchor in the first place. Without that, the
     * preview would be a way to read terrain anywhere somebody else had ever placed a private anchor.
     */
    private static void sendPreview(ServerPlayer player, ServerLevel level, UUID anchorId) {
        WarpAnchor anchor = WarpAnchorRegistry.get(level).byId(anchorId);
        if (anchor == null || !anchor.isVisibleTo(player)) {
            return;
        }
        ServerLevel target = level.getServer().getLevel(anchor.dimension());
        if (target == null) {
            return;
        }
        AWNetwork.sendTo(player, new ClientboundAstrolabeChartPacket.Preview(
                anchorId, DestinationSurvey.of(target, anchor.pos())));
    }
}
