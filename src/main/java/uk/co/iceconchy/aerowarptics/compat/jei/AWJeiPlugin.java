package uk.co.iceconchy.aerowarptics.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Description pages in JEI.
 *
 * <p>This mod adds no recipe types of its own - everything is crafted on a bench or mixed in a
 * basin, and JEI finds those without help. What it cannot find is the part that matters: that a
 * drive has to be built into an airship, that a gate needs a partner, that essence comes out of
 * travelling rather than out of the ground. Those go on the item's own information page, which is
 * the first place a player looks after "how do I make it".
 *
 * <p>Deliberately short. The long version is the Ponder scene, and anything written twice is
 * something that will eventually disagree with itself - so these say what the item is and what
 * commonly goes wrong with it, and leave the walkthrough to Ponder.
 *
 * <p>The class is only ever loaded by JEI itself, so the mod runs unchanged without it.
 */
@JeiPlugin
public class AWJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = AeroWarptics.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The book first, because it is the answer to the question somebody browsing JEI is asking.
        info(registration, AWItems.HANDBOOK.get(), "handbook");

        for (RiftDriveTier tier : RiftDriveTier.values()) {
            info(registration, AWItems.RIFT_DRIVES.get(tier).get(), "rift_drive");
        }

        info(registration, AWItems.WARP_ANCHOR.get(), "warp_anchor");
        info(registration, AWItems.ASTROLABE.get(), "astrolabe");
        info(registration, AWItems.SPATIAL_SIPHON.get(), "spatial_siphon");
        info(registration, AWItems.RIFT_PROBE.get(), "rift_probe");
        info(registration, AWItems.RIFT_CHUTE.get(), "rift_chute");
        info(registration, AWItems.RIFT_GATE.get(), "rift_gate");
        info(registration, AWItems.RIFT_GATE_FRAME.get(), "rift_gate_frame");
        info(registration, AWItems.RIFT_CORE.get(), "rift_core");
        info(registration, AWItems.RIFT_LENS.get(), "rift_lens");
        info(registration, AWItems.STABILISER_RING.get(), "stabiliser_ring");
        info(registration, AWItems.SINGULARITY_CORE.get(), "singularity_core");

        // The bucket and the fluid are the same substance and want the same page, but JEI keeps them
        // in separate ingredient types, so a player looking at one would otherwise find nothing.
        info(registration, AWItems.RIFT_ESSENCE_BUCKET.get(), "rift_essence");
        registration.addIngredientInfo(
                new FluidStack(AWFluids.RIFT_ESSENCE.get(), AWFluids.BUCKET),
                NeoForgeTypes.FLUID_STACK,
                text("rift_essence"));
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        registration.addIngredientInfo(item, text(key));
    }

    private static Component text(String key) {
        return AWLang.component("jei.info." + key);
    }
}
