package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.gate.GateTraversal;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the Rift Corridor a corridor: nobody builds in it, and nobody is left standing in it.
 *
 * <p>Create carries a seated rider from one dimension to the next by lifting them off, moving them, and putting
 * them back on once the carriage exists on the far side - so for a moment, every time, a rider is a player standing
 * in the void. That is what the barrier floor is for, and why there is a grace period. A player who has got off,
 * or been left behind by a train that went on without them, is put back in front of the gate the lane runs into
 * once the grace runs out - or at once, if they have somehow fallen past the floor.
 *
 * <p>Creative operators are left alone, to look at a corridor or mend one.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class CorridorWarden {

    /** Below this, a player has got past the floor and is rescued at once. */
    private static final int FLOOR = CorridorSlots.TRACK_Y - 8;

    private static final Map<UUID, Integer> STRANDED = new HashMap<>();

    private CorridorWarden() {
    }

    static void tick(MinecraftServer server, long now) {
        ServerLevel corridor = RailCorridor.level(server);
        if (corridor == null) {
            STRANDED.clear();
            return;
        }
        STRANDED.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || player.level() != corridor;
        });
        for (ServerPlayer player : corridor.players()) {
            if (player.isSpectator() || exempt(player)) {
                continue;
            }
            if (player.isPassenger() && player.getY() > FLOOR) {
                STRANDED.remove(player.getUUID());
                continue;
            }
            int ticks = STRANDED.merge(player.getUUID(), 1, Integer::sum);
            if (ticks > AWConfig.RAIL_RESCUE_GRACE_TICKS.get() || player.getY() < FLOOR) {
                rescue(server, player);
                STRANDED.remove(player.getUUID());
            }
        }
    }

    /** Puts a stranded player back in front of the gate whose corridor they are in. */
    private static void rescue(MinecraftServer server, ServerPlayer player) {
        int slot = CorridorSlots.slotAt(player.getZ());
        RailLane lane = slot < 0 ? null : RailLaneRegistry.get(server).inSlot(slot);
        ServerLevel back = lane == null ? null : server.getLevel(lane.dimension());
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (lane != null && back != null) {
            // On the track, a step back from the pane and facing away from it, so walking on does not take them
            // straight back in.
            BlockPos stand = lane.track().relative(lane.heading().getOpposite());
            float yaw = GateTraversal.yawOfDirection(net.minecraft.world.phys.Vec3.atLowerCornerOf(
                    lane.heading().getOpposite().getNormal()));
            player.teleportTo(back, stand.getX() + 0.5D, stand.getY() + 0.1D, stand.getZ() + 0.5D, yaw, 0.0F);
        } else {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), 0.0F);
        }
        player.fallDistance = 0.0F;
        player.displayClientMessage(AWLang.translate("gate.rail.rescued").component(), true);
        Trace.log(Trace.RAIL, "{} rescued from corridor {} back to {}", Trace.who(player), slot,
                lane == null ? "the world spawn" : Trace.pos(lane.track()));
    }

    private static boolean exempt(Player player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    private static boolean corridor(LevelAccessor level) {
        return level instanceof ServerLevel server && RailCorridor.isCorridor(server);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (corridor(event.getLevel()) && !exempt(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (corridor(event.getLevel()) && !(event.getEntity() instanceof Player player && exempt(player))) {
            event.setCanceled(true);
        }
    }
}
