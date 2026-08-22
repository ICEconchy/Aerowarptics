package uk.co.iceconchy.aerowarptics.advancement;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.List;

/**
 * The two things this mod can tell an advancement about.
 *
 * <p>Everything else worth rewarding is already expressible in vanilla terms - holding an item, or
 * standing somewhere - so only the two events with no vanilla equivalent get a trigger of their own:
 * finishing a warp, and coming out of a Rift Gate.
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
}
