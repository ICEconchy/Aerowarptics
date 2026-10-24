package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Forgets couplers that were really broken, once they have had time to turn up somewhere else.
 *
 * <p>The only thing in the flotilla system that runs on the server tick rather than inside a coupler,
 * because a broken coupler has no block entity left to do it. Holds nothing of its own: everything it
 * looks at is in the {@link FlotillaRegistry}, which is world data.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class FlotillaUpkeep {

    /**
     * How long a coupler whose block went away is kept before it is forgotten, in ticks.
     *
     * <p>Long enough for a ship being assembled, or rebuilt on the far side of a crossing, to report its
     * coupler from the new plot - which takes a second - with plenty to spare for a slow tick.
     */
    static final long GRACE_TICKS = 200L;

    private static final int SWEEP_INTERVAL = 20;

    private FlotillaUpkeep() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (now % SWEEP_INTERVAL != 0L) {
            return;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(server);
        registry.folds().expire(now);
        for (FlotillaRegistry.Forgotten forgotten : registry.sweep(now, GRACE_TICKS)) {
            FleetNotices.released(overworld, forgotten.released(), forgotten.record());
            if (forgotten.followed() != null) {
                FleetNotices.brokeOff(overworld, forgotten.record(), registry.coupler(forgotten.followed()));
            }
        }
    }
}
