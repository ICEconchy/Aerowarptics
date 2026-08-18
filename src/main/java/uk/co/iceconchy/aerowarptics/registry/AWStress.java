package uk.co.iceconchy.aerowarptics.registry;

import com.simibubi.create.api.stress.BlockStressValues;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/**
 * Hooks the Rift Drives into Create's stress system.
 *
 * <p>Registering with {@link BlockStressValues#IMPACTS} is what makes the drives appear in goggle
 * tooltips, in JEI's stress listings and in a network's total stress budget - exactly like any
 * first-party Create machine. The impact per tier is read from this mod's config, so a pack can
 * rebalance a drive without touching Create.
 */
public final class AWStress {

    private AWStress() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (RiftDriveTier tier : RiftDriveTier.values()) {
                BlockStressValues.IMPACTS.register(AWBlocks.RIFT_DRIVES.get(tier).get(), tier::stressImpact);
            }
        });
    }
}
