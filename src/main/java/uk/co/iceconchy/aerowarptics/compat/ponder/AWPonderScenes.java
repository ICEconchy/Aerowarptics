package uk.co.iceconchy.aerowarptics.compat.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.BeaconScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.CraftScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.DriveScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.FissureScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.GateScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.MachineScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.CouplerScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.ModulatorScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.RailScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.DetectorScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.CoolingScenes;
import uk.co.iceconchy.aerowarptics.compat.ponder.scene.StabiliserScenes;
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
 * to keep in step. The third, the rail lesson, does have its own: a track into a gate needs a bed to
 * run along, and that bed would be scenery the first two had to keep hidden.
 */
public final class AWPonderScenes {

    private AWPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> scenes =
                helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));

        // The siphon earns a second scene rather than the fissure getting its own item: there is no
        // fissure to hold, and closing one is as much a lesson about the vessel as about the tear.
        scenes.forComponents(AWItems.SPATIAL_SIPHON.get())
                .addStoryBoard("spatial_siphon", MachineScenes::spatialSiphon)
                .addStoryBoard("rift_fissure", FissureScenes::closing);

        // The goggles get both halves. They are the only way to see a fissure at all, so a player
        // holding a pair is exactly the player who has not yet been told what they are for.
        scenes.forComponents(AWItems.RIFT_GOGGLES.get())
                .addStoryBoard("rift_fissure", FissureScenes::finding)
                .addStoryBoard("rift_fissure", FissureScenes::closing);

        scenes.forComponents(AWItems.RIFT_BEACON.get())
                .addStoryBoard("rift_beacon", BeaconScenes::summoning);

        scenes.forComponents(AWItems.WARP_ANCHOR.get())
                .addStoryBoard("warp_anchor", MachineScenes::warpAnchor);

        scenes.forComponents(AWItems.ASTROLABE.get())
                .addStoryBoard("astrolabe", MachineScenes::astrolabe);

        scenes.forComponents(AWItems.RIFT_PROBE.get())
                .addStoryBoard("rift_probe", MachineScenes::riftProbe);

        scenes.forComponents(AWItems.RIFT_CHUTE.get())
                .addStoryBoard("rift_chute", MachineScenes::riftChute);

        scenes.forComponents(AWItems.RIFT_MODULATOR.get())
                .addStoryBoard("rift_modulator", ModulatorScenes::riftModulator);

        scenes.forComponents(AWItems.RIFT_COUPLER.get())
                .addStoryBoard("rift_coupler", CouplerScenes::riftCoupler);

        scenes.forComponents(AWItems.SPATIAL_STABILISER.get())
                .addStoryBoard("spatial_stabiliser", StabiliserScenes::spatialStabiliser);

        scenes.forComponents(AWItems.COOLING_VENT.get())
                .addStoryBoard("cooling_vent", CoolingScenes::coolingVent);

        scenes.forComponents(AWItems.SPATIAL_WAVE_DETECTOR.get())
                .addStoryBoard("spatial_wave_detector", DetectorScenes::spatialWaveDetector);

        // The crafting layer: each machine's scene goes on what it makes as well, since the player holding an
        // Opened Rift Lens or a Folded Warp Crystal in JEI is the one asking how it is made.
        scenes.forComponents(AWItems.RIFT_BRAZIER.get(), AWItems.OPENED_RIFT_LENS.get(), AWItems.WARP_GLASS.get())
                .addStoryBoard("rift_brazier", CraftScenes::rifting);
        scenes.forComponents(AWItems.RIFT_COLLAR.get())
                .addStoryBoard("rift_wellhead", uk.co.iceconchy.aerowarptics.compat.ponder.scene.WellheadScenes::wellhead);
        scenes.forComponents(AWItems.RIFT_SUNDERER.get())
                .addStoryBoard("rift_sunderer", uk.co.iceconchy.aerowarptics.compat.ponder.scene.SundererScenes::sunderer);
        scenes.forComponents(AWItems.COLLAPSE_CRADLE.get())
                .addStoryBoard("collapse_cradle", CraftScenes::collapse)
                .addStoryBoard("collapse_cradle", CraftScenes::folding);
        scenes.forComponents(AWItems.SINGULARITY_CORE.get())
                .addStoryBoard("collapse_cradle", CraftScenes::collapse);
        scenes.forComponents(AWItems.FOLDED_WARP_CRYSTAL.get(), AWItems.PEARLESCENT_WARP_CRYSTAL.get(),
                        AWItems.SHIP_COMPASS.get())
                .addStoryBoard("collapse_cradle", CraftScenes::folding);
        // The heart is what a sealed fissure leaves, so it plays the sealing - and the collapse it is for.
        scenes.forComponents(AWItems.SCAR_HEART.get())
                .addStoryBoard("rift_fissure", FissureScenes::closing)
                .addStoryBoard("collapse_cradle", CraftScenes::collapse);

        // Every tier is the same machine with different numbers, so they share the scene rather than
        // each getting a near-identical one. The frame scene goes on the drives as well as on the
        // casing: a frame is a thing a drive does, and the player holding the drive is the one who has
        // not yet heard it can.
        scenes.forComponents(drives())
                .addStoryBoard("rift_drive", DriveScenes::riftDrive)
                .addStoryBoard("drive_extender", DriveScenes::driveExtender);

        scenes.forComponents(AWItems.DRIVE_EXTENDER.get())
                .addStoryBoard("drive_extender", DriveScenes::driveExtender);

        // The frame is most of a gate by block count, so it earns the same pair of scenes as the
        // controller - a player holding a stack of frame is exactly the player who needs them.
        scenes.forComponents(AWItems.RIFT_GATE.get(), AWItems.RIFT_GATE_FRAME.get())
                .addStoryBoard("rift_gate", GateScenes::assembly)
                .addStoryBoard("rift_gate", GateScenes::dialling)
                .addStoryBoard("rift_gate_rail", RailScenes::trains);
    }

    private static List<ItemLike> drives() {
        List<ItemLike> drives = new ArrayList<>();
        AWItems.RIFT_DRIVES.values().forEach(drive -> drives.add(drive.get()));
        return drives;
    }
}
