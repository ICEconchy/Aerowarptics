package uk.co.iceconchy.aerowarptics.advancement;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.List;

/**
 * The things this mod can tell an advancement about.
 *
 * <p>Everything else worth rewarding is already expressible in vanilla terms - holding an item, or
 * standing somewhere - so only the events with no vanilla equivalent get a trigger of their own:
 * finishing a warp, coming out of a Rift Gate, watching a fissure seal, and calling a ship down to
 * you.
 *
 * <p>The {@code fire} helpers exist so the places that raise these events do not have to know about
 * criteria at all. A gate is not the right place to reason about advancement plumbing.
 */
public final class AWCriteria {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, AeroWarptics.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, WarpCompletedTrigger> WARP_COMPLETED =
            TRIGGERS.register("warp_completed", WarpCompletedTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, GateTravelTrigger> GATE_TRAVEL =
            TRIGGERS.register("gate_travel", GateTravelTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, FissureClosedTrigger> FISSURE_CLOSED =
            TRIGGERS.register("fissure_closed", FissureClosedTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, ShipSummonedTrigger> SHIP_SUMMONED =
            TRIGGERS.register("ship_summoned", ShipSummonedTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, FlotillaFoldTrigger> FLOTILLA_FOLD =
            TRIGGERS.register("flotilla_fold", FlotillaFoldTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, FoldCompletedTrigger> FOLD_COMPLETED =
            TRIGGERS.register("fold_completed", FoldCompletedTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, CollapseFinishedTrigger> COLLAPSE_FINISHED =
            TRIGGERS.register("collapse_finished", CollapseFinishedTrigger::new);

    /** A Rift Wellhead formed, was scrammed at the brink, or melted down, with a player standing by. */
    public static final DeferredHolder<CriterionTrigger<?>, WellheadTrigger> WELLHEAD =
            TRIGGERS.register("wellhead", WellheadTrigger::new);

    /** A Rift Sunderer tore a fissure, ruptured, or had something reach through it, with a player standing by. */
    public static final DeferredHolder<CriterionTrigger<?>, SundererTrigger> SUNDERER =
            TRIGGERS.register("sunderer", SundererTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, StormCalledTrigger> STORM_CALLED =
            TRIGGERS.register("storm_called", StormCalledTrigger::new);

    /** A train came out of a Rift Gate, for everybody riding it. */
    public static final DeferredHolder<CriterionTrigger<?>, TrainThroughGateTrigger> TRAIN_THROUGH_GATE =
            TRIGGERS.register("train_through_gate", TrainThroughGateTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, PlotStruckTrigger> PLOT_STRUCK =
            TRIGGERS.register("plot_struck", PlotStruckTrigger::new);

    /** A Rift Ward turned a rift aside, for whoever was standing by it. */
    public static final DeferredHolder<CriterionTrigger<?>, WardTrigger> WARD_HELD =
            TRIGGERS.register("ward_held", WardTrigger::new);

    /** A Rift Ward turned a ship aside, for whoever was aboard it. */
    public static final DeferredHolder<CriterionTrigger<?>, WardTrigger> TURNED_ASIDE =
            TRIGGERS.register("turned_aside", WardTrigger::new);

    /**
     * How far from a closing fissure still counts as having been there.
     *
     * <p>Comfortably wider than the range a siphon draws from, so a player who set the vessel down and
     * stepped back to watch is included, and narrow enough that somebody on the far side of a hill is
     * not handed an advancement for a thing they never saw.
     */
    private static final double WITNESS_RANGE = 24.0D;

    private AWCriteria() {
    }

    public static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }

    /**
     * Everyone who was aboard has just finished a warp.
     *
     * @param toFix whether the destination was a Rift Probe's fix rather than a placed anchor
     */
    public static void warpCompleted(List<ServerPlayer> crew, double distance, RiftDriveTier tier,
                                     boolean toFix) {
        for (ServerPlayer player : crew) {
            WARP_COMPLETED.get().trigger(player, distance, tier, toFix);
        }
    }

    /** A player has come out of the far side of a gate, on foot or as cargo. */
    public static void gateTravelled(List<ServerPlayer> travellers, boolean aboardVehicle) {
        for (ServerPlayer player : travellers) {
            GATE_TRAVEL.get().trigger(player, aboardVehicle);
        }
    }

    /**
     * A Rift Fissure has been emptied and has sealed over.
     *
     * <p>Everyone near enough to have watched it happen, which is the only sensible answer: a siphon
     * has no owner to credit, and standing there as the tear shrinks and goes is the moment worth
     * marking.
     *
     * @param essence what the fissure held when it was found
     */
    public static void fissureClosed(ServerLevel level, BlockPos where, int essence) {
        for (ServerPlayer player : level.getPlayers(player ->
                player.blockPosition().closerThan(where, WITNESS_RANGE))) {
            FISSURE_CLOSED.get().trigger(player, essence);
        }
    }

    /** Every crew of a flotilla fold, once its last ship is out. See {@link FlotillaFoldTrigger}. */
    public static void flotillaFolded(List<ServerPlayer> crews, int ships, boolean full, boolean clean) {
        for (ServerPlayer player : crews) {
            FLOTILLA_FOLD.get().trigger(player, ships, full, clean);
        }
    }

    /** Everyone near a Collapse Cradle that has just finished a fold, and how many crafts it folded. */
    public static void foldCompleted(ServerLevel level, BlockPos where, int count) {
        for (ServerPlayer player : level.getPlayers(player ->
                player.blockPosition().closerThan(where, WITNESS_RANGE))) {
            FOLD_COMPLETED.get().trigger(player, count);
        }
    }

    /**
     * A Collapse Cradle's ritual has ended. Everyone near enough to have watched, as with a fissure sealing -
     * nothing records who lit a cradle, and being there is the part worth marking.
     *
     * @param outcome {@code settled}, {@code flung} or {@code spat}
     */
    public static void collapseFinished(ServerLevel level, BlockPos where, String outcome) {
        for (ServerPlayer player : level.getPlayers(player ->
                player.blockPosition().closerThan(where, WITNESS_RANGE))) {
            COLLAPSE_FINISHED.get().trigger(player, outcome);
        }
    }

    /**
     * Something a Rift Plotter carried has come out of its far rift. Everyone near the plotter - nothing records who
     * set the plot, and being at the gun when it fires is the part worth marking - and whoever went through.
     */
    public static void plotStruck(ServerLevel level, BlockPos plotter, List<ServerPlayer> through) {
        for (ServerPlayer player : level.getPlayers(player ->
                player.blockPosition().closerThan(plotter, WITNESS_RANGE))) {
            PLOT_STRUCK.get().trigger(player);
        }
        for (ServerPlayer player : through) {
            PLOT_STRUCK.get().trigger(player);
        }
    }

    /** A Rift Ward has turned a rift aside: everyone standing by the machine saw it hold. */
    public static void wardHeld(ServerLevel level, BlockPos ward) {
        for (ServerPlayer player : level.getPlayers(player -> player.blockPosition().closerThan(ward, WITNESS_RANGE))) {
            WARD_HELD.get().trigger(player);
        }
    }

    /** A ship has been turned aside by a Rift Ward: everyone aboard was thrown out at its rim. */
    public static void turnedAside(java.util.Collection<ServerPlayer> crew) {
        for (ServerPlayer player : crew) {
            TURNED_ASIDE.get().trigger(player);
        }
    }

    /**
     * Something happened at a Rift Wellhead - see {@link WellheadTrigger} - for everyone standing within reach of
     * it. Nobody in particular forms a frame or melts one down, so whoever was there to see it is who it is for.
     */
    public static void wellhead(ServerLevel level, BlockPos where, String event, double reach) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.blockPosition().distSqr(where) <= reach * reach) {
                WELLHEAD.get().trigger(player, event);
            }
        }
    }

    /** Something happened at a Rift Sunderer - see {@link SundererTrigger} - for everyone standing within reach of it. */
    public static void sunderer(ServerLevel level, BlockPos where, String event, double reach) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.blockPosition().distSqr(where) <= reach * reach) {
                SUNDERER.get().trigger(player, event);
            }
        }
    }

    /** A player has broken a set Stormglass and called a storm. */
    /** Everybody riding a train that has just come out of a Rift Gate. */
    public static void trainThroughGate(List<ServerPlayer> riders) {
        for (ServerPlayer player : riders) {
            TRAIN_THROUGH_GATE.get().trigger(player);
        }
    }

    public static void stormCalled(ServerPlayer caller) {
        STORM_CALLED.get().trigger(caller);
    }

    /** A player's beacon has called its bound airship down. */
    public static void shipSummoned(ServerPlayer commander) {
        SHIP_SUMMONED.get().trigger(commander);
    }
}
