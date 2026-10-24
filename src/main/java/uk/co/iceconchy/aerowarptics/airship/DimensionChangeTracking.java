package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Stops Sable deleting a ship on the client of a player who has just changed dimension.
 *
 * <h2>The bug this is here for</h2>
 * Sable tells a client to forget a ship with a packet that names the ship only by its slot on the plot
 * grid - not by dimension. And it notices a player has left a level only on that level's next tracking
 * tick, when it sends that packet for every ship the player was tracking there. By then the player's
 * client is in the new dimension and may already have been sent that dimension's ships - and both grids
 * fill from slot (0, 0), so the same slot is very often in use in both. The late packet then deletes the
 * <em>new</em> dimension's ship in that slot, and every movement update for it afterwards is logged as
 * "a sub-level movement packet for a non-existent sub-level". The ship is still there on the server;
 * the player simply cannot see it, stand on it, or ride anything aboard it.
 *
 * <p>It was found as a ship that vanished on arrival from a warp into the Nether, with a second ship
 * parked in the Overworld's slot (0, 0), but nothing about it is specific to warps: walking through a
 * portal near a ship can do the same.
 *
 * <h2>What this does instead</h2>
 * As a player's dimension change completes - before either level's next tracking tick - the player is
 * taken off every tracking list in the level they left. The client has already thrown that level away,
 * ships and all, so there is nothing to tell it; and with nothing left to clean up, Sable sends nothing
 * late. Taking the player off early is exactly what Sable would do a tick later, minus the stray packet.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class DimensionChangeTracking {

    private DimensionChangeTracking() {
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getFrom().equals(event.getTo())) {
            return;
        }
        MinecraftServer server = player.getServer();
        ServerLevel left = server == null ? null : server.getLevel(event.getFrom());
        ServerSubLevelContainer container = left == null ? null : SubLevelContainer.getContainer(left);
        if (container == null) {
            return;
        }
        List<Collection<UUID>> tracking = new ArrayList<>();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            tracking.add(subLevel.getTrackingPlayers());
        }
        int forgotten = forget(player.getUUID(), tracking);
        if (forgotten > 0) {
            Trace.log(Trace.CROSSING, "{} left {} for {}: taken off {} ship tracking list(s) there before Sable"
                            + " could send the late removal", player.getGameProfile().getName(),
                    event.getFrom().location(), event.getTo().location(), forgotten);
        }
    }

    /**
     * Takes {@code player} off every list in {@code tracking}.
     *
     * @return how many lists had them
     */
    public static int forget(UUID player, Iterable<? extends Collection<UUID>> tracking) {
        int removed = 0;
        for (Collection<UUID> players : tracking) {
            if (players.remove(player)) {
                removed++;
            }
        }
        return removed;
    }
}
