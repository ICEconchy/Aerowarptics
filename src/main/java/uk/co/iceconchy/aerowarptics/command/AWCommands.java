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
 * The point of it is the gap between the volume actually tested and the hull's bare sweep - the
 * over-sensitivity a later pass is meant to narrow, shown so it can be judged by eye.
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
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("warp")
                                .then(Commands.literal("dryrun")
                                        .executes(AWCommands::warpDryRun))
                                .then(Commands.literal("clearance")
                                        .executes(AWCommands::warpClearance))));
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
                "Clearance overlay on: %d%s block%s fouling the corridor (teal = tested volume, amber = hull sweep, red = collisions). Run again anywhere to turn it off.",
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
