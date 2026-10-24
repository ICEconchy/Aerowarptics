package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundFlotillaCuePacket;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a drive in a fold reports, and what the fleet hears when it does.
 *
 * <p>Called by the drive itself - at its arrival, or at whatever stopped it going - because the drive is
 * the one thing certain to be there at the end of its own flight, in whichever dimension that turned out
 * to be. Everything here is looked up again from the {@link FlotillaRegistry}'s {@link FoldLog} and the
 * server; nothing is kept.
 */
public final class FleetFolds {

    private FleetFolds() {
    }

    /** A ship in a fold is out the other side and has come to rest. */
    public static void arrived(ServerLevel level, FoldOrder order, @Nullable RiftCouplerBlockEntity coupler,
                               Airship ship, boolean echo) {
        List<UUID> crew = new ArrayList<>();
        for (ServerPlayer player : ship.crew()) {
            crew.add(player.getUUID());
        }
        BlockPos at = coupler == null ? BlockPos.ZERO : coupler.getBlockPos();
        FoldLog.Outcome outcome = FlotillaRegistry.get(level).folds().arrived(order.fold(),
                new FoldLog.Arrival(order.coupler(), level.dimension(), at, crew, echo));
        announce(level.getServer(), outcome);
        if (outcome.straggler() != null) {
            FleetCues.shakenLoose(level, ship);
        }
    }

    /** A ship in a fold never went: stood down, or refused, before its rift opened. */
    public static void failed(ServerLevel level, FoldOrder order) {
        announce(level.getServer(), FlotillaRegistry.get(level).folds().failed(order.fold(), order.coupler()));
    }

    // ------------------------------------------------------------ one rift

    /** Whether this ship sends a cue its whole fold shares, or another ship already has - see {@link FoldLog.SharedCue}. */
    public static boolean firstTo(ServerLevel level, FoldOrder order, FoldLog.SharedCue cue) {
        return FlotillaRegistry.get(level).folds().firstTo(order.fold(), cue);
    }

    /** This ship is through its fold's one entry: whether it was the last, and the entry may close. */
    public static boolean throughEntry(ServerLevel level, FoldOrder order) {
        return FlotillaRegistry.get(level).folds().throughEntry(order.fold(), order.coupler());
    }

    /** This ship is out of its fold's one exit: whether it was the last, and the exit may close. */
    public static boolean outOfExit(ServerLevel level, FoldOrder order) {
        return FlotillaRegistry.get(level).folds().outOfExit(order.fold(), order.coupler());
    }

    /** This ship stopped short in a fold through one rift: which ends of it are left waiting for nobody. */
    public static FoldLog.Closing leave(ServerLevel level, FoldOrder order) {
        return FlotillaRegistry.get(level).folds().leave(order.fold(), order.coupler());
    }

    private static void announce(MinecraftServer server, FoldLog.Outcome outcome) {
        FoldLog.Fold assembled = outcome.assembledNow();
        if (assembled != null) {
            boolean stragglers = assembled.stragglersDue();
            for (FoldLog.Arrival arrival : assembled.arrivals()) {
                ServerLevel level = server.getLevel(arrival.dimension());
                if (level != null && level.isLoaded(arrival.couplerPos())) {
                    level.playSound(null, arrival.couplerPos(), AWSounds.FLOTILLA_BELL.get(), SoundSource.BLOCKS,
                            2.0F, 1.0F);
                    if (stragglers) {
                        level.playSound(null, arrival.couplerPos(), AWSounds.WAKE_ECHO.get(), SoundSource.BLOCKS,
                                2.0F, 0.9F);
                    }
                }
                for (ServerPlayer player : players(server, arrival)) {
                    AWNetwork.sendTo(player, new ClientboundFlotillaCuePacket(
                            ClientboundFlotillaCuePacket.Cue.ASSEMBLED, 0, !stragglers));
                    if (stragglers) {
                        AWNetwork.sendTo(player, new ClientboundFlotillaCuePacket(
                                ClientboundFlotillaCuePacket.Cue.ECHO_INBOUND, 0, false));
                    }
                }
            }
        }
        FoldLog.Fold finished = outcome.finishedNow();
        if (finished != null && finished.ships() >= 2) {
            List<ServerPlayer> crews = new ArrayList<>();
            for (FoldLog.Arrival arrival : finished.arrivals()) {
                crews.addAll(players(server, arrival));
            }
            AWCriteria.flotillaFolded(crews, finished.ships(), finished.fleetInBeing(), finished.nobodyLeftBehind());
        }
    }

    private static List<ServerPlayer> players(MinecraftServer server, FoldLog.Arrival arrival) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : arrival.crew()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }
}
