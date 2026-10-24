package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.astrolabe.PreviewHolds;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkFilter;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOption;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Everything a player can do at an Astrolabe Cartography Table.
 *
 * <p>One payload for all of them because they share every check: the table has to exist, be complete, be
 * within reach, and belong to a hull the player may command. Splitting them into separate packets would
 * mean writing that gate once for each.
 *
 * @param tablePos the cell the player clicked, in its own level's coordinates - inside an airship's
 *                 plot, not the world. The centre is resolved from it server-side.
 * @param action   what the player is doing
 * @param anchorId destination for {@link Action#SELECT} and {@link Action#PREVIEW}, otherwise ignored
 * @param hold     whether to hold altitude, for {@link Action#HOLD_ALTITUDE}, otherwise ignored
 * @param filter   the network the chart is filtered to, or nil for every destination. Sent with every
 *                 action, because every action but a preview is answered with the chart again and the
 *                 answer has to be the same list the player is looking at
 */
public record ServerboundAstrolabePacket(BlockPos tablePos, Action action, UUID anchorId, boolean hold,
                                         UUID filter)
        implements CustomPacketPayload {

    public enum Action {
        /** Open the chart: send this table's destination list back. */
        OPEN,
        /** Point the table, and the drive on its hull, at an anchor. */
        SELECT,
        /** Survey the ground around an anchor, for the preview panel. */
        PREVIEW,
        /** Hold the ship's altitude on the way to the standing course, or stop holding it. */
        HOLD_ALTITUDE
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundAstrolabePacket> TYPE = new Type<>(AeroWarptics.id("astrolabe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAstrolabePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundAstrolabePacket::encode, ServerboundAstrolabePacket::decode);

    public static ServerboundAstrolabePacket open(BlockPos tablePos, @Nullable UUID filter) {
        return new ServerboundAstrolabePacket(tablePos, Action.OPEN, NIL, false, orNil(filter));
    }

    public static ServerboundAstrolabePacket select(BlockPos tablePos, UUID anchorId, @Nullable UUID filter) {
        return new ServerboundAstrolabePacket(tablePos, Action.SELECT, anchorId, false, orNil(filter));
    }

    public static ServerboundAstrolabePacket preview(BlockPos tablePos, UUID anchorId) {
        return new ServerboundAstrolabePacket(tablePos, Action.PREVIEW, anchorId, false, NIL);
    }

    public static ServerboundAstrolabePacket holdAltitude(BlockPos tablePos, boolean hold, @Nullable UUID filter) {
        return new ServerboundAstrolabePacket(tablePos, Action.HOLD_ALTITUDE, NIL, hold, orNil(filter));
    }

    private static UUID orNil(@Nullable UUID id) {
        return id == null ? NIL : id;
    }

    /** The filter asked for, as the rest of the server speaks of it: {@code null} for none. */
    @Nullable
    private UUID wantedFilter() {
        return filter == null || NIL.equals(filter) ? null : filter;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundAstrolabePacket packet) {
        buf.writeBlockPos(packet.tablePos);
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.anchorId == null ? NIL : packet.anchorId);
        buf.writeBoolean(packet.hold);
        buf.writeUUID(orNil(packet.filter));
    }

    private static ServerboundAstrolabePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundAstrolabePacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUUID(),
                buf.readBoolean(), buf.readUUID());
    }

    public static void handle(ServerboundAstrolabePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            AstrolabeBlockEntity table = resolve(player, level, packet.tablePos);
            if (table == null) {
                Trace.log(Trace.CHART, "{} {} from {} refused: no complete table there within reach",
                        Trace.pos(packet.tablePos), packet.action, Trace.who(player));
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(WarpFailure.NO_AIRSHIP));
                return;
            }

            switch (packet.action) {
                case OPEN -> sendChart(player, level, table, packet.wantedFilter());
                case SELECT -> {
                    WarpFailure result = table.select(player, packet.anchorId);
                    WarpAnchor chosen = WarpAnchorRegistry.get(level).byId(packet.anchorId);
                    Trace.log(Trace.CHART, "{} {} chose anchor {} \"{}\" -> {}", Trace.pos(table.getBlockPos()),
                            Trace.who(player), packet.anchorId, chosen == null ? "?" : chosen.displayName(),
                            result.getSerializedName());
                    if (result.isFailure()) {
                        AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(result));
                    }
                    sendChart(player, level, table, packet.wantedFilter());
                }
                case PREVIEW -> sendPreview(player, level, packet.anchorId);
                case HOLD_ALTITUDE -> {
                    WarpFailure result = table.setHoldAltitude(player, packet.hold);
                    Trace.log(Trace.CHART, "{} {} set hold altitude {} -> {}", Trace.pos(table.getBlockPos()),
                            Trace.who(player), packet.hold, result.getSerializedName());
                    if (result.isFailure()) {
                        AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(result));
                    }
                    // Sent back either way, so a refused toggle springs back to what the course says.
                    sendChart(player, level, table, packet.wantedFilter());
                }
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

    /**
     * @param wanted the network the player's chart is filtered to, or {@code null} for every destination
     */
    private static void sendChart(ServerPlayer player, ServerLevel level, AstrolabeBlockEntity table,
                                  @Nullable UUID wanted) {
        RiftDriveBlockEntity drive = table.drive();
        Airship airship = table.airship();

        WarpFailure access = drive == null ? WarpFailure.NO_AIRSHIP : WarpValidator.validatePlayer(player, drive);
        RiftDriveTier tier = drive == null ? RiftDriveTier.MK_I : drive.tier();
        float charge = drive == null ? 0.0F : drive.charge();
        boolean usable = drive != null && airship != null && !access.isFailure();

        // The filter is offered from everything the player may see and applied before anything is priced
        // or cut: the networks on offer must not depend on which one is chosen, and a chart cut to the
        // nearest 256 first would have nothing left of a network whose anchors are all far away.
        List<WarpAnchor> visible = usable ? WarpValidator.destinations(level, player) : List.<WarpAnchor>of();
        WarpNetworkRegistry networks = WarpNetworkRegistry.get(level);
        List<NetworkOption> offered = PacketLists.cap(
                NetworkFilter.options(visible.stream().map(WarpAnchor::network).toList(), networks::nameOf),
                NetworkFilter.BY_NAME);
        UUID filter = NetworkFilter.applied(offered, wanted);
        List<WarpAnchor> listed = filter == null ? visible
                : visible.stream().filter(anchor -> filter.equals(anchor.network())).toList();
        List<WarpQuote> available = usable
                ? WarpValidator.quote(level, listed, airship, tier, charge)
                : List.<WarpQuote>of();
        // Nothing upstream bounds this: anchors are unlimited by default and every one the player may
        // see is quoted. Over the packet's limit the encoder throws rather than truncating, so the cap
        // has to happen here, and the untruncated total goes with it so the chart can say so.
        List<WarpQuote> quotes = PacketLists.cap(available, WarpQuote.NEAREST_USABLE_FIRST);
        traceChart(player, table, drive, airship, tier, access, available.size(), quotes,
                filter == null ? "all" : networks.nameOf(filter));

        // The drive's own course, not the table's memory of what was last clicked here. A Rift Probe
        // can aim the same drive at a bare position, and a chart that kept showing its own last
        // selection would be confidently pointing at an anchor the ship is no longer going to.
        WarpCourse course = drive == null ? null : drive.standingCourse();
        UUID selected = course != null && course.isAnchor() ? course.anchorId() : null;
        BlockPos fix = course != null && course.isFix() ? course.fix() : null;
        boolean fixElsewhere = fix != null && !course.fixIsIn(level.dimension());
        String label = course == null ? "" : course.label();

        AWNetwork.sendTo(player, new ClientboundAstrolabeChartPacket(
                table.getBlockPos(),
                tier.index(),
                charge,
                tier.maximumRange(),
                drive == null || airship == null ? ClientboundDriveConsolePacket.NO_SHIP
                        : WarpValidator.reach(airship, tier),
                airship == null || airship.name() == null ? "" : airship.name(),
                drive != null,
                access,
                selected,
                fix,
                fixElsewhere,
                label,
                course != null && course.holdAltitude(),
                available.size(),
                quotes,
                filter,
                offered));
    }

    /** How many quotes one chart line names before it just counts the rest. */
    private static final int TRACED_QUOTES = 24;

    /**
     * What this player was shown at this table, whenever it differs from last time.
     *
     * <p>The chart re-asks every couple of seconds, so it is traced on change: the line appears when the
     * chart is first opened and again when an anchor's price or verdict moves - a drive finishing its
     * charge, a ship drifting past the edge of its reach. Distances are to the nearest ten blocks and costs
     * to the percent, so a hull drifting on its moorings does not redraw the line every refresh; the refusal
     * a launch then makes carries the exact figures.
     */
    private static void traceChart(ServerPlayer player, AstrolabeBlockEntity table,
                                   @org.jetbrains.annotations.Nullable RiftDriveBlockEntity drive,
                                   @org.jetbrains.annotations.Nullable Airship airship, RiftDriveTier tier,
                                   WarpFailure access, int total, List<WarpQuote> quotes, String filter) {
        if (!Trace.enabled()) {
            return;
        }
        StringBuilder listed = new StringBuilder();
        int shown = Math.min(quotes.size(), TRACED_QUOTES);
        for (int i = 0; i < shown; i++) {
            WarpQuote quote = quotes.get(i);
            listed.append(i == 0 ? "" : "; ").append('"').append(quote.name()).append("\" ")
                    .append(quote.distance() < 0.0D ? "other dimension"
                            : Math.round(quote.distance() / 10.0D) * 10L + "b")
                    .append(' ').append(Math.round(quote.cost() * 100.0D)).append("% ")
                    .append(quote.failure().isFailure() ? quote.failure().getSerializedName() : "ok");
        }
        if (total > shown) {
            listed.append("; and ").append(total - shown).append(" more");
        }
        // The reach at this hull's mass, beside the range the chart's header shows: when the first is the
        // shorter, anchors between the two are refused as out of range though the header says otherwise.
        double mass = airship == null ? 0.0D : WarpValidator.effectiveMass(airship);
        double reach = airship == null ? tier.maximumRange()
                : uk.co.iceconchy.aerowarptics.warp.WarpCost.fromConfig(tier).reach(mass);
        Trace.changed("chart:" + player.getUUID() + "@" + table.getBlockPos().asLong(), Trace.CHART,
                "{} shown to {}: drive {} {} range {} mass {} (reach at that mass {}) access {} filter={} - {} anchor(s): {}",
                Trace.pos(table.getBlockPos()), Trace.who(player),
                drive == null ? "none" : Trace.pos(drive.getBlockPos()), tier.getSerializedName(),
                Trace.blocks(tier.maximumRange()), airship == null ? "-" : Math.round(mass / 100.0D) * 100L,
                reach < 0.0D ? "NONE" : Trace.blocks(reach),
                access.isFailure() ? access.getSerializedName() : "ok", filter, total, listed);
    }

    /**
     * Surveys the ground around an anchor and sends the picture back.
     *
     * <p>Gated on the player being allowed to see the anchor in the first place. Without that, the
     * preview would be a way to read terrain anywhere somebody else had ever placed a private anchor.
     */
    private static void sendPreview(ServerPlayer player, ServerLevel level, UUID anchorId) {
        WarpAnchor anchor = WarpAnchorRegistry.get(level).byId(anchorId);
        if (anchor == null || !anchor.isVisibleTo(player, WarpNetworkRegistry.get(level))) {
            return;
        }
        ServerLevel target = level.getServer().getLevel(anchor.dimension());
        if (target == null) {
            return;
        }
        // An anchor in ground nobody is standing in: load its surroundings and answer once they are there.
        // The chart says "Surveying..." until then, which is exactly what is happening.
        if (PreviewHolds.request(player, anchorId, target, anchor.pos())) {
            return;
        }
        AWNetwork.sendTo(player, new ClientboundAstrolabeChartPacket.Preview(
                anchorId, DestinationSurvey.of(target, anchor.pos())));
    }
}
