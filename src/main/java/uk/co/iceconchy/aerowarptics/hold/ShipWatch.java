package uk.co.iceconchy.aerowarptics.hold;

import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * Hears Sable load, unload and remove ships, and keeps {@link HoldLedger}'s record of where they are true.
 *
 * <p>The moment that matters is an unload. A ship noted while it was loaded may have moved since, and once
 * Sable has filed it away there is no asking it where it went - so its last box is written down as it goes.
 * That is the box a beacon, a chute or a gate reaches for later ({@link Holds#reachShip}).
 *
 * <p>Only ships already in the ledger are followed, so the world's other ships - most of them - cost
 * nothing here.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class ShipWatch implements SubLevelObserver {

    private final ServerLevel level;

    private ShipWatch(ServerLevel level) {
        this.level = level;
    }

    @SubscribeEvent
    public static void onContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            event.getContainer().addObserver(new ShipWatch(serverLevel));
        }
    }

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        HoldLedger ledger = ledger();
        if (ledger != null && subLevel instanceof ServerSubLevel ship && ledger.ship(ship.getUniqueId()) != null) {
            ledger.noteShip(Holds.record(ship, ledger.ship(ship.getUniqueId()), now()));
        }
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        HoldLedger ledger = ledger();
        if (ledger == null || !(subLevel instanceof ServerSubLevel ship)) {
            return;
        }
        HoldLedger.ShipRecord known = ledger.ship(ship.getUniqueId());
        if (reason == SubLevelRemovalReason.REMOVED) {
            // Taken apart, or rebuilt somewhere else by a crossing under a new id. Sable drops its tickets
            // with it; nothing will ever reach for it again.
            if (known != null || !ledger.holdsOn(ship.getUniqueId()).isEmpty()) {
                ledger.forgetShip(ship.getUniqueId());
                Trace.log(Trace.HOLD, "ship {} ('{}') removed from {} - forgotten", ship.getUniqueId(),
                        known == null ? "" : known.name(), level.dimension().location());
            }
            return;
        }
        if (known != null) {
            HoldLedger.ShipRecord last = Holds.record(ship, known, now());
            ledger.noteShip(last);
            Trace.log(Trace.HOLD, "ship {} ('{}') unloaded in {}, last over ({}, {}) to ({}, {})", ship.getUniqueId(),
                    last.name(), level.dimension().location(), Math.round(last.minX()), Math.round(last.minZ()),
                    Math.round(last.maxX()), Math.round(last.maxZ()));
        }
    }

    /**
     * The ledger, or {@code null} while the server is starting or stopping.
     *
     * <p>Starting: Sable rebuilds every force-loaded ship as its level is constructed, and the overworld -
     * where the ledger lives - may be the level still being built. Nothing is lost by not noting those; each
     * is held, so its drive notes it on its first tick. Stopping: Sable unloads every ship as the server
     * closes, after the world has been saved, and by then the overworld's data may be gone with it. The save
     * already wrote down where every ship was.
     */
    private HoldLedger ledger() {
        MinecraftServer server = level.getServer();
        if (server == null || !server.isRunning() || server.overworld() == null) {
            return null;
        }
        return HoldLedger.get(server);
    }

    private long now() {
        ServerLevel overworld = level.getServer().overworld();
        return overworld == null ? 0L : overworld.getGameTime();
    }
}
