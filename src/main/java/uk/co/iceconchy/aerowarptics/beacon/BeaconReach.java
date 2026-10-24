package uk.co.iceconchy.aerowarptics.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.hold.HoldLedger;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A beacon's summon of a ship that is not loaded, while the ship is brought back.
 *
 * <p>A summon has to talk to the drive - it is the drive that checks charge, range and cost and flies the
 * warp - and a drive aboard a ship Sable has unloaded is not anywhere to talk to. So the beacon loads the
 * ground the ship was last seen over ({@link Holds#reachShip}), waits for Sable to rebuild the ship there,
 * and then summons exactly as it would have if the ship had been loaded all along. From that moment the
 * warp holds its own ship; the reach only has to last until the drive answers, and lapses by itself.
 *
 * <p>One per player. The player is told the beacon is reaching, and told the outcome the same way any
 * refused warp is.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class BeaconReach {

    private static final int POLL_INTERVAL = 5;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(ServerLevel level, RiftBeaconBinding binding, HoldLedger.ShipRecord ship,
                           BlockPos target, long started, long deadline, long renewAt) {

        Pending renewed(long now) {
            return new Pending(level, binding, ship, target, started, deadline, now + Holds.REACH_RENEW_INTERVAL);
        }
    }

    private BeaconReach() {
    }

    /**
     * Starts reaching for the ship a binding names, if it can be found.
     *
     * @return {@code true} if the ship is on its way in and the summon will follow; {@code false} when there
     * is no record of where it is, and the caller should refuse as it always has
     */
    public static boolean begin(ServerLevel level, ServerPlayer player, RiftBeaconBinding binding, BlockPos target) {
        if (!AWConfig.BEACON_REACHES_SHIPS.get()) {
            return false;
        }
        HoldLedger.ShipRecord ship = Holds.shipAt(level, binding.drivePos());
        if (ship == null) {
            Trace.log(Trace.BEACON, "{} summon: the bound ship at {} is not loaded and was never noted anywhere",
                    Trace.who(player), Trace.pos(binding.drivePos()));
            return false;
        }
        Holds.Reach reach = Holds.reachShip(level, ship, "beacon of " + player.getGameProfile().getName(), false);
        if (reach != Holds.Reach.REACHING) {
            // Present, so the drive being missing is the drive's own doing - broken, or moved - and the
            // ordinary refusal is the true one.
            return false;
        }
        long now = level.getServer().overworld().getGameTime();
        PENDING.put(player.getUUID(), new Pending(level, binding, ship, target.immutable(), now,
                now + AWConfig.SUMMON_REACH_TIMEOUT_TICKS.get(), now + Holds.REACH_RENEW_INTERVAL));
        String name = ship.name().isBlank() ? binding.shipLabel() : ship.name();
        player.displayClientMessage(AWLang.translate("beacon.reaching", RiftBeaconItem.shipName(name)).component(),
                true);
        Trace.log(Trace.BEACON, "{} summon to {}: ship {} is not loaded; reaching for it where it was last seen",
                Trace.who(player), Trace.pos(target), ship.ship());
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % POLL_INTERVAL != 0L) {
            return;
        }
        Iterator<Map.Entry<UUID, Pending>> entries = PENDING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Pending> entry = entries.next();
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != pending.level()) {
                // Logged off, or gone to another dimension, where the summon could not have been made.
                entries.remove();
                continue;
            }
            // Back, and its drive knows it is aboard: Sable says which ship a machine is on by ticking it, and
            // a drive rebuilt a moment ago may not have been told yet - asked then, it would refuse the summon
            // for having no airship.
            if (Holds.present(pending.level(), pending.ship().ship()) != null
                    && RiftBeaconItem.readyToSummon(pending.level(), pending.binding())) {
                entries.remove();
                Trace.log(Trace.BEACON, "{}: ship {} is back after {}t - summoning", Trace.who(player),
                        pending.ship().ship(), now - pending.started());
                RiftBeaconItem.complete(pending.level(), player, pending.binding(), pending.target());
                continue;
            }
            if (now >= pending.deadline()) {
                entries.remove();
                Trace.log(Trace.BEACON, "{}: ship {} did not come back within {}t of reaching for it",
                        Trace.who(player), pending.ship().ship(), AWConfig.SUMMON_REACH_TIMEOUT_TICKS.get());
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(WarpFailure.SHIP_OUT_OF_REACH));
                continue;
            }
            if (now >= pending.renewAt()) {
                Holds.reachShip(pending.level(), pending.ship(), "beacon of " + player.getGameProfile().getName(), false);
                entry.setValue(pending.renewed(now));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
