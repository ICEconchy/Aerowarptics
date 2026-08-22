package uk.co.iceconchy.aerowarptics.compat.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.DriveScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.GateScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.MachineScenes;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Which scene plays for which item.
 *
 * <p>The string passed to {@code addStoryBoard} is the schematic under
 * {@code assets/aerowarptics/ponder/}, which {@code tools/ponder_schematics.py} writes. Two gate
 * scenes share one schematic on purpose: building the ring and using it are separate lessons, but
 * they are lessons about the same object, and a second copy of the ring would only be a second thing
 * to keep in step.
 */
public final class AWPonderScenes {

    private AWPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> scenes =
                helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));

        scenes.forComponents(AWItems.SPATIAL_SIPHON.get())
                .addStoryBoard("spatial_siphon", MachineScenes::spatialSiphon);

        scenes.forComponents(AWItems.WARP_ANCHOR.get())
                .addStoryBoard("warp_anchor", MachineScenes::warpAnchor);

        scenes.forComponents(AWItems.ASTROLABE.get())
                .addStoryBoard("astrolabe", MachineScenes::astrolabe);

        scenes.forComponents(AWItems.RIFT_PROBE.get())
                .addStoryBoard("rift_probe", MachineScenes::riftProbe);

        scenes.forComponents(AWItems.RIFT_CHUTE.get())
                .addStoryBoard("rift_chute", MachineScenes::riftChute);

        // Every tier is the same machine with different numbers, so they share the scene rather than
        // each getting a near-identical one.
        scenes.forComponents(drives())
                .addStoryBoard("rift_drive", DriveScenes::riftDrive);

        // The frame is most of a gate by block count, so it earns the same pair of scenes as the
        // controller - a player holding a stack of frame is exactly the player who needs them.
        scenes.forComponents(AWItems.RIFT_GATE.get(), AWItems.RIFT_GATE_FRAME.get())
                .addStoryBoard("rift_gate", GateScenes::assembly)
                .addStoryBoard("rift_gate", GateScenes::dialling);
    }

    private static List<ItemLike> drives() {
        List<ItemLike> drives = new ArrayList<>();
        AWItems.RIFT_DRIVES.values().forEach(drive -> drives.add(drive.get()));
        return drives;
    }
}
