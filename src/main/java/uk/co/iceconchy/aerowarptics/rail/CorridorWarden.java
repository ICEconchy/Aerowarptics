package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.gate.GateTraversal;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorVisitPacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
 * <p>It keeps no train either. Create brings a train with no driver and no schedule to a halt and leaves it where
 * it stopped, which in a corridor is out of everybody's reach for good - a rider knocked off the controls half way
 * through cost somebody their train in game (2026-10-25). So a train found standing still in a corridor is sent on
 * the way it was going, which is the same bargain the barrier floor and the grace period strike for a player.
 *
 * <p>Creative operators are left alone, to look at a corridor or mend one.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class CorridorWarden {

    /** Below this, a player has got past the floor and is rescued at once. */
    private static final int FLOOR = CorridorSlots.TRACK_Y - 8;

    /** Ticks between sweeps for anything left loose in a corridor. */
    private static final int STRAY_INTERVAL = 20;

    private static final Map<UUID, Integer> STRANDED = new HashMap<>();

    /** Ticks each train in a corridor has stood still for. */
    private static final Map<UUID, Integer> STILL = new HashMap<>();

    /** The last speed each train in a corridor was seen moving at, which is the way it is sent on. */
    private static final Map<UUID, Double> WENT = new HashMap<>();

    /** Trains being pushed out of a corridor, kept up every tick until they are out of it. */
    private static final Set<UUID> SENDING = new HashSet<>();

    private CorridorWarden() {
    }

    /** How fast a train is sent on out of a corridor: half of Create's top speed, in blocks a tick. */
    private static double crawl() {
        return AllConfigs.server().trains.poweredTrainTopSpeed.get() / 40.0D;
    }

    static void tick(MinecraftServer server, long now) {
        ServerLevel corridor = RailCorridor.level(server);
        if (corridor == null) {
            STRANDED.clear();
            STILL.clear();
            WENT.clear();
            SENDING.clear();
            return;
        }
        STRANDED.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || player.level() != corridor;
        });
        CorridorDoor.prune(server, corridor);
        for (ServerPlayer player : corridor.players()) {
            if (player.isSpectator() || exempt(player)) {
                continue;
            }
            boolean visiting = CorridorDoor.visiting(player.getUUID());
            int grace = visiting ? CorridorDoor.visitTicks() : AWConfig.RAIL_RESCUE_GRACE_TICKS.get();
            if (player.isPassenger() && player.getY() > FLOOR) {
                // Riding is what a visitor went in to do, and what somebody left behind is carried out by. The
                // clock stops either way, which is why the client is told the figure rather than left to count.
                STRANDED.remove(player.getUUID());
                if (visiting && now % 20L == 0L) {
                    CorridorDoor.tell(player, grace);
                }
                continue;
            }
            int ticks = STRANDED.merge(player.getUUID(), 1, Integer::sum);
            if (visiting && now % 20L == 0L) {
                CorridorDoor.tell(player, Math.max(0, grace - ticks));
            }
            if (ticks > grace || player.getY() < FLOOR) {
                rescue(server, player);
                STRANDED.remove(player.getUUID());
            }
        }
        if (now % STRAY_INTERVAL == 0L) {
            evictStrays(server, corridor);
        }
    }

    /**
     * Puts out anything else loose in a corridor: an item a rider dropped, a mob, a boat somebody brought in.
     *
     * <p>Never a carriage and never anything riding one. A train in a corridor is the one thing that belongs
     * there, and lifting a carriage out of it by hand is exactly how a train comes apart - so the train is left to
     * {@link #tickTrains}, which drives it out whole.
     */
    private static void evictStrays(MinecraftServer server, ServerLevel corridor) {
        for (Entity stray : corridor.getAllEntities()) {
            if (stray instanceof Player || stray instanceof AbstractContraptionEntity || stray.isPassenger()
                    || stray.isRemoved()) {
                continue;
            }
            int slot = CorridorSlots.slotAt(stray.getZ());
            RailLane lane = slot < 0 ? null : RailLaneRegistry.get(server).inSlot(slot);
            ServerLevel back = lane == null ? null : server.getLevel(lane.dimension());
            if (lane == null || back == null) {
                continue;
            }
            BlockPos stand = lane.track().relative(lane.heading().getOpposite());
            Vec3 to = new Vec3(stand.getX() + 0.5D, stand.getY() + 0.1D, stand.getZ() + 0.5D);
            Entity moved = stray.changeDimension(new DimensionTransition(back, to, Vec3.ZERO, stray.getYRot(),
                    stray.getXRot(), DimensionTransition.DO_NOTHING));
            Trace.log(Trace.RAIL, "put {} out of corridor {} at {}",
                    stray.getType().getDescriptionId(), slot, Trace.pos(stand));
            if (moved == null) {
                break; // it would not go; leave the rest for the next sweep rather than spinning on it
            }
        }
    }

    /**
     * Sends on every train standing still in a corridor.
     *
     * <p>{@code manualTick} is what makes the push stick. It is the flag Create's own controls set, and the one
     * thing that stops {@code Train.tickPassiveSlowdown} taking the speed straight back off on the train's next
     * tick - which that same tick then clears, so the push has to be made again every tick until the train is out.
     * That is why a train, once being sent on, is kept on the list rather than judged afresh each tick: judged
     * afresh, it would be moving, be left alone, coast to a stop, and have to be found all over again.
     *
     * @param inCorridor every train with a carriage in a corridor, as this tick's survey found them
     */
    static void tickTrains(MinecraftServer server, long now, Map<Integer, Set<UUID>> occupancy) {
        ServerLevel corridor = RailCorridor.level(server);
        Set<UUID> inCorridor = new HashSet<>();
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        for (Map.Entry<Integer, Set<UUID>> slot : occupancy.entrySet()) {
            if (slot.getValue().isEmpty()) {
                continue;
            }
            inCorridor.addAll(slot.getValue());
            // The piece that was missing, and why trains stayed stuck however hard this pushed them: nothing held a
            // corridor's ground while a train stood in it. The ticket that laid the corridor lasts 300 ticks, so a
            // quarter of a minute after its gate stopped working the chunks went out, the carriage could no longer
            // tick, and the train waited on chunks nobody was ever going to ask for again. Held here instead, for
            // as long as anything is in there, and renewed well inside that lifespan.
            RailLane lane = lanes.inSlot(slot.getKey());
            if (corridor != null && lane != null && now % 20L == 0L) {
                RailWorks.holdCorridor(corridor, lane.slot(), lane.length());
            }
        }
        STILL.keySet().retainAll(inCorridor);
        WENT.keySet().retainAll(inCorridor);
        SENDING.retainAll(inCorridor);
        int grace = AWConfig.RAIL_RESCUE_GRACE_TICKS.get();
        for (UUID id : inCorridor) {
            Train train = Create.RAILWAYS.trains.get(id);
            if (train == null) {
                continue;
            }
            if (SENDING.contains(id)) {
                push(train, id);
                continue;
            }
            int still;
            if (train.speed != 0.0D) {
                WENT.put(id, train.speed);
                STILL.remove(id);
                still = 0;
            } else {
                still = STILL.merge(id, 1, Integer::sum);
            }
            boolean navigating = train.navigation != null && train.navigation.destination != null;
            RailGateLogic.Stranded what = RailGateLogic.stranded(train.speed, navigating,
                    train.carriageWaitingForChunks > 0, train.derailed, still, grace);
            if (what == RailGateLogic.Stranded.WAITING && still > 0 && still % 200 == 0) {
                // Said, rather than waited out in silence: the corridor's chunks are held for as long as a train is
                // in one, so a train still waiting on them is something nobody would otherwise ever hear about.
                Trace.log(Trace.RAIL, "train {} has waited {} ticks in a corridor for its chunks",
                        train.name.getString(), still);
            }
            if (what == RailGateLogic.Stranded.LIFT_AND_SEND) {
                // A derailed train is normally somebody's to pick up and set down again, and in a corridor there is
                // nobody who can reach it. So the corridor lifts it back on and drives it out itself.
                train.derailed = false;
                Trace.log(Trace.RAIL, "train {} had come off the track in a corridor; put back on and sent on",
                        train.name.getString());
            } else if (what != RailGateLogic.Stranded.SEND_ON) {
                continue;
            }
            SENDING.add(id);
            double speed = push(train, id);
            Trace.log(Trace.RAIL, "train {} had stood still in a corridor for {} ticks; sending it on at {}",
                    train.name.getString(), still, Trace.num(speed));
            ServerPlayer owner = train.owner == null ? null : server.getPlayerList().getPlayer(train.owner);
            if (owner != null) {
                owner.displayClientMessage(AWLang.translate("gate.rail.sent_on", train.name).component(), false);
            }
        }
    }

    /** Drives a train on for one tick, the way it was going. */
    private static double push(Train train, UUID id) {
        double speed = RailGateLogic.onward(WENT.getOrDefault(id, 0.0D), train.targetSpeed) * crawl();
        train.manualTick = true;
        train.speed = speed;
        return speed;
    }

    /**
     * Puts a player back out of the corridor.
     *
     * <p>A visitor who walked in through a gate goes back to the exact spot they clicked from; anybody else - a
     * rider a train left behind, or a visitor whose way back a restart forgot - goes to the lane's own railhead.
     */
    private static void rescue(MinecraftServer server, ServerPlayer player) {
        if (CorridorDoor.sendBack(server, player)) {
            return;
        }
        int slot = CorridorSlots.slotAt(player.getZ());
        if (putBackAtRailhead(server, player, slot)) {
            return;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, player.getYRot(), 0.0F);
        player.fallDistance = 0.0F;
        player.displayClientMessage(AWLang.translate("gate.rail.rescued").component(), true);
        AWNetwork.sendTo(player, ClientboundCorridorVisitPacket.gone());
        Trace.log(Trace.RAIL, "{} rescued from corridor {} back to the world spawn", Trace.who(player), slot);
    }

    /**
     * Puts a player on the near track of the lane whose corridor this is.
     *
     * @return whether that lane could be found, and they were sent there
     */
    static boolean putBackAtRailhead(MinecraftServer server, ServerPlayer player, int slot) {
        RailLane lane = slot < 0 ? null : RailLaneRegistry.get(server).inSlot(slot);
        ServerLevel back = lane == null ? null : server.getLevel(lane.dimension());
        if (lane == null || back == null) {
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        // On the track, a step back from the pane and facing away from it, so walking on does not take them
        // straight back in.
        BlockPos stand = lane.track().relative(lane.heading().getOpposite());
        float yaw = GateTraversal.yawOfDirection(net.minecraft.world.phys.Vec3.atLowerCornerOf(
                lane.heading().getOpposite().getNormal()));
        player.teleportTo(back, stand.getX() + 0.5D, stand.getY() + 0.1D, stand.getZ() + 0.5D, yaw, 0.0F);
        player.fallDistance = 0.0F;
        player.displayClientMessage(AWLang.translate("gate.rail.rescued").component(), true);
        AWNetwork.sendTo(player, ClientboundCorridorVisitPacket.gone());
        CorridorDoor.forget(player.getUUID());
        Trace.log(Trace.RAIL, "{} put back from corridor {} at {}", Trace.who(player), slot,
                Trace.pos(lane.track()));
        return true;
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
