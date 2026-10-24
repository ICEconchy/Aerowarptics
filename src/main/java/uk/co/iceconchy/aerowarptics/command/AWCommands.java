package uk.co.iceconchy.aerowarptics.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundClearancePacket;
import uk.co.iceconchy.aerowarptics.hold.HoldCommands;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.trace.TraceReport;
import uk.co.iceconchy.aerowarptics.trace.TraceSession;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkCommands;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mod's debug commands, gated at operator permission and of no interest during ordinary play.
 *
 * <p>{@code /aerowarptics warp dryrun} is the reproduction harness the whole warp stability work is
 * verified against: it runs the same pre-flight the drive runs - plan, departure clearance, arrival
 * durability - against the standing course and prints the verdict, moving nothing.
 *
 * <p>{@code /aerowarptics warp clearance} is a toggle. Run on an airship it turns on a live wireframe
 * of that drive's departure corridor, refreshed as the ship moves; run again anywhere it turns off.
 * The amber hull sweep is what the check tests; the teal around it is where the aperture reaches, and
 * was the tested volume until this overlay showed that padding to be the source of false refusals.
 *
 * <p>{@code /aerowarptics trace [on|off|report]} is the diagnostic trace's switch - see
 * {@link Trace} - without anybody having to find the per-world config file it lives in.
 *
 * <p>{@code /aerowarptics holds [list|release|reach]} says what the mod is keeping loaded and lets an
 * operator let go of it - see {@link HoldCommands}.
 *
 * <p>{@code /aerowarptics network ...} is the one set here that is not for debugging: it manages the
 * networks players share destinations through - see {@link NetworkCommands}.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class AWCommands {

    /** Players with the clearance overlay switched on, by id. Touched only on the server thread. */
    private static final Set<UUID> CLEARANCE_ON = ConcurrentHashMap.newKeySet();

    /** How often, in ticks, a live overlay is re-measured and resent. */
    private static final int REFRESH_TICKS = 10;

    /** How long one snapshot lasts on the client - longer than the refresh, so it never flickers. */
    private static final int SNAPSHOT_TICKS = 30;

    private AWCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("aerowarptics")
                        .requires(source -> source.hasPermission(2) || ownsThisWorld(source))
                        .then(Commands.literal("warp")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("dryrun")
                                        .executes(AWCommands::warpDryRun))
                                .then(Commands.literal("clearance")
                                        .executes(AWCommands::warpClearance)))
                        .then(Commands.literal("trace")
                                .executes(AWCommands::traceStatus)
                                .then(Commands.literal("on")
                                        .executes(context -> traceSet(context, true)))
                                .then(Commands.literal("off")
                                        .executes(context -> traceSet(context, false)))
                                .then(Commands.literal("report")
                                        .executes(AWCommands::traceReport)))
                        .then(HoldCommands.node())
                        .then(NetworkCommands.node())
                        .then(uk.co.iceconchy.aerowarptics.ward.WardCommands.node())
                        .then(uk.co.iceconchy.aerowarptics.wellhead.WellheadCommands.node())
                        .then(uk.co.iceconchy.aerowarptics.sunderer.SundererCommands.node()));
    }

    /**
     * The player whose single-player world this is, whether or not they allowed cheats.
     *
     * <p>Let at the trace, which only writes to the log, and not at the warp commands, which read and move
     * things. A single-player report is the common case, and "turn cheats on first" is one more step for a
     * player to get wrong before a single line has been traced.
     */
    private static boolean ownsThisWorld(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && source.getServer().isSingleplayerOwner(player.getGameProfile());
    }

    /** Whether the server is tracing, and which file the switch lives in. */
    private static int traceStatus(CommandContext<CommandSourceStack> context) {
        boolean on = Trace.enabled();
        context.getSource().sendSuccess(() -> Component.literal("AeroWarptics trace is " + (on ? "ON" : "off")
                + " (debug.trace in this world's aerowarptics-server.toml). "
                + (on ? "It is being written to latest.log." : "/aerowarptics trace on starts it.")), false);
        return on ? 1 : 0;
    }

    /**
     * Switches tracing on or off and saves it into the world's config file.
     *
     * <p>The command exists because the switch is per world - in single player the file is under
     * {@code saves/<world>/serverconfig/}, not {@code config/} - and a report that begins "I turned tracing
     * on" and arrives with no trace in it was, more than once, somebody who edited the other file.
     */
    private static int traceSet(CommandContext<CommandSourceStack> context, boolean on) {
        CommandSourceStack source = context.getSource();
        TraceSession.set(source.getServer(), on, source.getTextName());
        source.sendSuccess(() -> Component.literal(on
                ? "AeroWarptics trace ON and saved. Reproduce the problem, then send latest.log - every connected"
                        + " player's own latest.log now carries their side too."
                : "AeroWarptics trace off."), true);
        return 1;
    }

    /**
     * Writes the header into the log now, tracing or not, and the whole situation of the drive aboard the
     * ship the player is standing on, if there is one.
     */
    private static int traceReport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TraceReport.write(source.getServer(), "report asked for by " + source.getTextName());
        String drive = "";
        if (source.getEntity() instanceof ServerPlayer player) {
            Airship airship = Airship.aboard(player);
            RiftDriveBlockEntity found = airship == null ? null : airship.machine(RiftDriveBlockEntity.class);
            if (found != null) {
                Trace.always(Trace.DRIVE, "{} as reported: {}", Trace.pos(found.getBlockPos()), found.traceState());
                drive = " and the drive aboard this ship";
            }
        }
        String written = drive;
        source.sendSuccess(() -> Component.literal("AeroWarptics header" + written + " written to latest.log."), false);
        return 1;
    }

    private static int warpDryRun(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        Airship airship = Airship.aboard(player);
        if (airship == null) {
            source.sendFailure(Component.literal("Stand on the airship whose drive you want to test."));
            return 0;
        }
        RiftDriveBlockEntity drive = airship.machine(RiftDriveBlockEntity.class);
        if (drive == null) {
            source.sendFailure(Component.literal("This airship has no Rift Drive aboard."));
            return 0;
        }

        List<Component> report = drive.dryRun();
        for (Component line : report) {
            source.sendSuccess(() -> line, false);
        }
        return report.size();
    }

    private static int warpClearance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        // Toggling off works anywhere - no airship required to stop looking at one.
        if (CLEARANCE_ON.remove(player.getUUID())) {
            AWNetwork.sendTo(player, ClientboundClearancePacket.off());
            source.sendSuccess(() -> Component.literal("Clearance overlay off."), false);
            return 1;
        }

        // Toggling on needs a drive to measure.
        Airship airship = Airship.aboard(player);
        if (airship == null) {
            source.sendFailure(Component.literal("Stand on the airship whose drive you want to check."));
            return 0;
        }
        RiftDriveBlockEntity drive = airship.machine(RiftDriveBlockEntity.class);
        if (drive == null) {
            source.sendFailure(Component.literal("This airship has no Rift Drive aboard."));
            return 0;
        }
        RiftDriveBlockEntity.ClearanceView view = drive.clearanceVisual();
        if (view == null) {
            source.sendFailure(Component.literal("The drive is not aboard a live airship."));
            return 0;
        }

        CLEARANCE_ON.add(player.getUUID());
        sendSnapshot(player, view);

        int found = view.hits().size();
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "Clearance overlay on: %d%s block%s fouling the corridor (amber = hull sweep, tested; teal = aperture reach, not tested; red = collisions). Run again anywhere to turn it off.",
                found, found >= 512 ? "+" : "", found == 1 ? "" : "s")), false);
        return 1;
    }

    /** Re-measures and resends the overlay for everyone who has it on, a few times a second. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CLEARANCE_ON.isEmpty() || event.getServer().getTickCount() % REFRESH_TICKS != 0) {
            return;
        }
        Iterator<UUID> iterator = CLEARANCE_ON.iterator();
        while (iterator.hasNext()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(iterator.next());
            if (player == null) {
                iterator.remove();
                continue;
            }
            Airship airship = Airship.aboard(player);
            RiftDriveBlockEntity drive = airship == null ? null : airship.machine(RiftDriveBlockEntity.class);
            RiftDriveBlockEntity.ClearanceView view = drive == null ? null : drive.clearanceVisual();
            // Off the ship there is nothing to measure; the last snapshot simply fades. The toggle
            // stays on, so stepping back aboard brings it straight back - only re-running turns it off.
            if (view != null) {
                sendSnapshot(player, view);
            }
        }
    }

    /** Drop a leaver's toggle so the set does not accumulate ids of players who are gone. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CLEARANCE_ON.remove(event.getEntity().getUUID());
    }

    private static void sendSnapshot(ServerPlayer player, RiftDriveBlockEntity.ClearanceView view) {
        AWNetwork.sendTo(player, ClientboundClearancePacket.on(
                toAabbs(view.core()), toAabbs(view.padded()), view.hits(), SNAPSHOT_TICKS));
    }

    private static List<AABB> toAabbs(List<? extends BoundingBox3dc> boxes) {
        List<AABB> converted = new java.util.ArrayList<>(boxes.size());
        for (BoundingBox3dc box : boxes) {
            converted.add(new AABB(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()));
        }
        return converted;
    }
}
