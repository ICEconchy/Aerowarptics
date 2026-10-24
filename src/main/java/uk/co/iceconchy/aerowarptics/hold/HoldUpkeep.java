package uk.co.iceconchy.aerowarptics.hold;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server-side half of holding ships: lets go of what nobody renewed, and keeps levels with held ships
 * awake.
 *
 * <p>Runs on the server tick rather than inside anything that holds, for the same reason
 * {@code FlotillaUpkeep} does: the case that matters is the one where the owner is not there any more to
 * tidy up after itself.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class HoldUpkeep {

    private static final int INTERVAL = 20;

    /** How often to look for tickets of ours the ledger has no record of. A minute; they are rare. */
    private static final int ORPHAN_SWEEP_INTERVAL = 1200;

    /** Whether this server has swept for orphaned tickets yet. The first sweep is on the first pass. */
    private static boolean swept;

    private HoldUpkeep() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        swept = false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % INTERVAL != 0L) {
            return;
        }
        HoldLedger ledger = HoldLedger.get(server);
        lapse(server, ledger, now);
        if (!swept || now % ORPHAN_SWEEP_INTERVAL == 0L) {
            swept = true;
            try {
                sweepOrphans(server, ledger);
            } catch (LinkageError incompatible) {
                // A safety net must never be what brings a server down. See Holds.incompatible.
                Holds.incompatible("reading its tickets", incompatible);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            keepAwake(level, ledger, now);
        }
    }

    /**
     * Lets go of every hold whose lease has run out.
     *
     * <p>An owner that is still there renews long before this; so a hold that lapses is one whose owner has
     * gone - broken without its removal hook running, lost with a crash, or on a ship that has since been
     * taken apart. Sable drops a ticket by itself when its ship is removed for good, so for those this only
     * tidies the ledger.
     */
    private static void lapse(MinecraftServer server, HoldLedger ledger, long now) {
        for (HoldLedger.ShipHold hold : ledger.holds()) {
            if (!HoldRules.lapsed(hold.leaseUntil(), now)) {
                continue;
            }
            boolean dropped = Holds.dropTicket(server, hold);
            ledger.removeHold(hold.key(), hold.ship());
            Trace.log(Trace.HOLD, "{}'s {} hold on ship {} lapsed {}t ago - its owner stopped renewing it{}",
                    hold.key().owner(), hold.key().purpose().id(), hold.ship(), now - hold.leaseUntil(),
                    dropped ? "; ticket taken off" : "; the ship is not loaded, so Sable has already let it go");
        }
    }

    /**
     * Takes this mod's ticket off any ship the ledger does not know is held.
     *
     * <p>The ledger and Sable's tickets are saved separately. A world restored from a backup of one and not
     * the other, or a ledger lost to a crash, would otherwise leave ships loaded for good with nothing that
     * would ever let them go.
     */
    private static void sweepOrphans(MinecraftServer server, HoldLedger ledger) {
        if (!SableTickets.available() || Holds.shipHoldsUnavailable()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            List<Orphan> orphans = new ArrayList<>();
            for (Map.Entry<UUID, SubLevelTicketInfo> entry : container.getAllTickets().entrySet()) {
                for (SubLevelLoadingTicket<?> ticket : entry.getValue().tickets()) {
                    // Through SableTickets, never the ticket's own getters: Sable renamed them in 2.0.5.
                    if (SableTickets.type(ticket) == Holds.SHIP_TICKET && SableTickets.key(ticket) instanceof HoldKey key
                            && ledger.hold(key, entry.getKey()) == null) {
                        orphans.add(new Orphan(entry.getKey(), key));
                    }
                }
            }
            for (Orphan orphan : orphans) {
                ServerSubLevel ship = Holds.present(level, orphan.ship());
                if (ship != null && container.removeForceLoadTicket(ship, Holds.SHIP_TICKET, orphan.key())) {
                    Trace.always(Trace.HOLD, "took an orphaned {} ticket off ship {} in {} - {} held it, but no"
                                    + " record of the hold survived", orphan.key().purpose().id(), orphan.ship(),
                            level.dimension().location(), orphan.key().owner());
                }
            }
        }
    }

    private record Orphan(UUID ship, HoldKey key) {
    }

    /**
     * Keeps one chunk forced in each level that has a held ship in it, and none in a level that does not.
     *
     * <p>See {@link HoldRules#wakeChunk} for why one is enough and which one.
     */
    private static void keepAwake(ServerLevel level, HoldLedger ledger, long now) {
        List<Long> candidates = new ArrayList<>();
        boolean flying = false;
        for (HoldLedger.ShipHold hold : ledger.holds()) {
            if (!hold.dimension().equals(level.dimension()) || HoldRules.lapsed(hold.leaseUntil(), now)) {
                continue;
            }
            ServerSubLevel ship = Holds.present(level, hold.ship());
            if (ship != null) {
                Vector3dc centre = ship.logicalPose().position();
                candidates.add(ChunkPos.asLong((int) Math.floor(centre.x()) >> 4, (int) Math.floor(centre.z()) >> 4));
                flying |= hold.key().purpose() == HoldPurpose.FLIGHT || hold.key().purpose() == HoldPurpose.PLOTTED;
            }
        }
        Long current = ledger.wakeChunk(level.dimension());
        Long chosen = HoldRules.wakeChunk(current, candidates, flying);
        if (current == null ? chosen == null : current.equals(chosen)) {
            return;
        }
        AirshipResidency.wake(level, current == null ? 0L : current, current != null,
                chosen == null ? 0L : chosen, chosen != null);
        ledger.setWakeChunk(level.dimension(), chosen);
    }
}
