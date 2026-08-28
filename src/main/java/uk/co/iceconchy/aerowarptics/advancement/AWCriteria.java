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
 * The four things this mod can tell an advancement about.
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

    /** A player's beacon has called its bound airship down. */
    public static void shipSummoned(ServerPlayer commander) {
        SHIP_SUMMONED.get().trigger(commander);
    }
}
