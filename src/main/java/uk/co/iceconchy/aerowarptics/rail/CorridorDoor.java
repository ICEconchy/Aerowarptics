package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.gate.GateTraversal;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorVisitPacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way in and out of a corridor on foot.
 *
 * <p>A corridor is otherwise sealed: no way in but on a train, nothing anybody may build in it, and the
 * {@link CorridorWarden} puts anyone standing in one back where they came from. That is right until a train stops
 * in there, at which point the one person who could drive it out is the one person who cannot get to it. So a gate
 * with track run into it is also a door: right-click its opening and you step into the corridor at that gate's own
 * end, where the train would have gone. Right-click the mouth at either end and you are back exactly where you
 * clicked from, facing the way you were facing.
 *
 * <p>Open or shut makes no difference - a gate whose train has stopped has usually shut itself after the linger,
 * so needing to pay to dial it first would be a toll on cleaning up. The socket that stands in a lane's cell while
 * the gate is shut is a door just as the pane is.
 *
 * <p>A visit lasts {@link AWConfig#RAIL_VISIT_TICKS}, counted down on the player's own view, and standing on a
 * train does not spend it: the warden stops counting for anybody riding. Nothing here is saved, so a server that
 * restarts with somebody inside has forgotten where they came in - the warden then puts them at the lane's own
 * railhead instead, which is a step from the gate they walked into.
 */
public final class CorridorDoor {

    /** Where somebody was standing when they stepped in, so the mouth can put them back on the spot. */
    private record Entry(ResourceKey<Level> dimension, Vec3 where, float yaw, float pitch) {
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private CorridorDoor() {
    }

    /** Whether this player walked in through a gate, and so is a visitor rather than somebody left behind. */
    public static boolean visiting(UUID player) {
        return ENTRIES.containsKey(player);
    }

    /**
     * The corridor track somebody stepping in at one end of a lane arrives beside - where the train would have
     * gone in, or come out. Pure arithmetic.
     *
     * @param nearEnd whether they clicked the gate a train goes <em>in</em> at, rather than the one it comes out of
     */
    static BlockPos landing(RailLane lane, boolean nearEnd) {
        return nearEnd
                ? RailWorks.westTrack(lane.slot())
                : RailWorks.eastTrack(lane.slot(), lane.length());
    }

    /** How long a visit lasts, in ticks. A player who was left behind by a train gets the short grace instead. */
    public static int visitTicks() {
        return AWConfig.RAIL_VISIT_TICKS.get();
    }

    static void forget(UUID player) {
        ENTRIES.remove(player);
    }

    /**
     * Forgets the way back for anybody who is out of the corridor already.
     *
     * <p>Only for players who are online and somewhere else: one who logged out inside keeps their way back, so
     * that logging in again still puts them where they started rather than at the railhead.
     */
    static void prune(MinecraftServer server, ServerLevel corridor) {
        ENTRIES.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player != null && player.level() != corridor;
        });
    }

    static void clear() {
        ENTRIES.clear();
    }

    /**
     * Steps into the corridor through a gate's opening.
     *
     * @param cell the cell of the opening that was clicked, which must be one a lane runs through
     */
    public static InteractionResult enter(ServerLevel level, BlockPos cell, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        RailLaneRegistry.End end = RailLaneRegistry.get(server).atCell(level.dimension(), cell);
        if (end == null) {
            return InteractionResult.PASS; // not a lane's cell: an ordinary gate opening, to be walked through
        }
        ServerLevel corridor = RailCorridor.level(server);
        if (corridor == null) {
            return InteractionResult.PASS;
        }
        RailLane lane = end.lane();
        BlockPos track = landing(lane, end.nearEnd());
        // Beside the track rather than on it, and facing down the corridor: a visitor is there to look at a train,
        // not to be the thing it runs into.
        Direction along = end.nearEnd() ? Direction.EAST : Direction.WEST;
        Vec3 stand = new Vec3(track.getX() + 0.5D, track.getY(), track.getZ() + 1.5D);
        float yaw = GateTraversal.yawOfDirection(Vec3.atLowerCornerOf(along.getNormal()));

        RailWorks.holdCorridor(corridor, lane.slot(), lane.length());
        ENTRIES.put(player.getUUID(), new Entry(level.dimension(), player.position(), player.getYRot(),
                player.getXRot()));
        player.teleportTo(corridor, stand.x, stand.y, stand.z, yaw, 0.0F);
        player.fallDistance = 0.0F;
        AWNetwork.sendTo(player, ClientboundCorridorVisitPacket.left(visitTicks()));
        Trace.log(Trace.RAIL, "{} stepped into corridor {} at its {} end", Trace.who(player), lane.slot(),
                end.nearEnd() ? "near" : "far");
        return InteractionResult.SUCCESS;
    }

    /** Steps back out of the corridor by a mouth, to wherever the player came in from. */
    public static InteractionResult leave(ServerLevel corridor, BlockPos mouth, ServerPlayer player) {
        if (!RailCorridor.isCorridor(corridor)) {
            return InteractionResult.PASS;
        }
        if (sendBack(corridor.getServer(), player)) {
            return InteractionResult.SUCCESS;
        }
        // Nobody knows where they came in - a restart, or they rode in on a train. The warden's own way out.
        int slot = CorridorSlots.slotAt(mouth.getZ());
        if (slot < 0) {
            return InteractionResult.PASS;
        }
        return CorridorWarden.putBackAtRailhead(corridor.getServer(), player, slot)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    /**
     * Puts a player back where they stepped in, if this is one who stepped in.
     *
     * @return whether they had a way back recorded, and were sent there
     */
    static boolean sendBack(MinecraftServer server, ServerPlayer player) {
        Entry entry = ENTRIES.remove(player.getUUID());
        ServerLevel back = entry == null ? null : server.getLevel(entry.dimension());
        if (entry == null || back == null) {
            return false;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        player.teleportTo(back, entry.where().x, entry.where().y, entry.where().z, entry.yaw(), entry.pitch());
        player.fallDistance = 0.0F;
        AWNetwork.sendTo(player, ClientboundCorridorVisitPacket.gone());
        player.displayClientMessage(AWLang.translate("gate.rail.stepped_back").component(), true);
        Trace.log(Trace.RAIL, "{} stepped back out of the corridor to {}", Trace.who(player),
                entry.dimension().location());
        return true;
    }

    /** Tells one visitor what is left of their visit, for the clock on their view. */
    static void tell(ServerPlayer player, int ticksLeft) {
        AWNetwork.sendTo(player, ClientboundCorridorVisitPacket.left(ticksLeft));
    }

    /** Whether a position is a cell a lane runs through, so a click there is a door rather than a doorway. */
    public static boolean isDoor(@Nullable MinecraftServer server, ResourceKey<Level> dimension, BlockPos cell) {
        return server != null && RailLaneRegistry.get(server).isLaneCell(dimension, cell);
    }
}
