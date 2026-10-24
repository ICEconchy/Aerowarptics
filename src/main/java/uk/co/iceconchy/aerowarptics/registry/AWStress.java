package uk.co.iceconchy.aerowarptics.registry;

import com.simibubi.create.api.stress.BlockStressValues;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/**
 * Hooks this mod's kinetic machines into Create's stress system.
 *
 * <p>Registering with {@link BlockStressValues#IMPACTS} is what makes the drives appear in goggle
 * tooltips, in JEI's stress listings and in a network's total stress budget &mdash; exactly like
 * any first-party Create machine. The impact per tier is read from this mod's config, so a pack
 * can rebalance a drive without touching Create.
 *
 * <p>Display Link sources live in {@code compat.display.AWDisplaySources}, not here.
 */
public final class AWStress {

    private AWStress() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (RiftDriveTier tier : RiftDriveTier.values()) {
                BlockStressValues.IMPACTS.register(AWBlocks.RIFT_DRIVES.get(tier).get(), tier::stressImpact);
            }
            // A gate's real impact depends on how big its opening is and whether it is holding one,
            // so what is registered here is the floor &mdash; enough for the block to appear in goggle
            // and JEI stress listings at all, which it would not if nothing were registered.
            BlockStressValues.IMPACTS.register(AWBlocks.RIFT_GATE.get(), AWConfig.GATE_STRESS::get);
            // The cradle draws the Singularity's own stress: if you cannot turn it, you cannot fly what it makes.
            BlockStressValues.IMPACTS.register(AWBlocks.COLLAPSE_CRADLE.get(), RiftDriveTier.SINGULARITY::stressImpact);
            // A vent is a consumer like any other. Ice, which cools a drive without a shaft, is not here at all.
            BlockStressValues.IMPACTS.register(AWBlocks.COOLING_VENT.get(), AWConfig.COOLING_VENT_STRESS::get);
            // The plotter holds a tear open across the world: its impact is its own, and in the server config.
            BlockStressValues.IMPACTS.register(AWBlocks.RIFT_PLOTTER.get(), AWConfig.PLOTTER_STRESS::get);
            // The ward holds a field for as long as it is switched on: its impact is its own too.
            BlockStressValues.IMPACTS.register(AWBlocks.RIFT_WARD.get(), AWConfig.WARD_STRESS::get);
            // A Sunderer is turned only to tear, and draws its own impact while it does.
            BlockStressValues.IMPACTS.register(AWBlocks.RIFT_SUNDERER.get(), AWConfig.SUNDERER_STRESS::get);
            // A collar's real impact is its frame's, and nothing until it has one: what is registered here is the
            // smallest frame's, so the block shows in goggle and JEI stress listings at all - the gate's reason.
            BlockStressValues.IMPACTS.register(AWBlocks.RIFT_COLLAR.get(),
                    AWConfig.WELLHEAD_STRESS.get(uk.co.iceconchy.aerowarptics.wellhead.WellheadTier.I)::get);
        });
    }
}
