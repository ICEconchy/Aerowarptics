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
        });
    }
}
