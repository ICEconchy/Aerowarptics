package uk.co.iceconchy.aerowarptics.compat.jei;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;
import uk.co.iceconchy.aerowarptics.rifting.RiftingRecipe;
import net.neoforged.neoforge.fluids.FluidStack;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Description pages in JEI, and the categories for this mod's own processes.
 *
 * <p>Most of what the mod makes is crafted on a bench or run through a stock Create machine, and JEI
 * finds those without help. The three processes the crafting layer adds - rifting, folding and the
 * collapse - are recipe types of their own, and each gets a category built the way Create builds its
 * own, so a player reads them in the same visual language as washing or mixing.
 *
 * <p>What JEI cannot find is the part that matters: that a
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

    /** Built once per registration pass, then asked for its recipes and catalysts, as Create's plugin does. */
    private final java.util.List<CreateRecipeCategory<?>> categories = new java.util.ArrayList<>();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        categories.clear();
        categories.add(new CreateRecipeCategory.Builder<>(RiftingRecipe.class)
                .addTypedRecipes(AWRecipeTypes.RIFTING)
                .catalystStack(AWJeiPlugin::riftingFan)
                .catalyst(() -> AWItems.RIFT_BRAZIER.get())
                .doubleItemIcon(AllItems.PROPELLER.get(), AWItems.OPENED_RIFT_LENS.get())
                .emptyBackground(178, 72)
                .build(AeroWarptics.id("rifting"), FanRiftingCategory::new));
        categories.add(new CreateRecipeCategory.Builder<>(uk.co.iceconchy.aerowarptics.fold.FoldingRecipe.class)
                .addTypedRecipes(AWRecipeTypes.FOLDING)
                .catalyst(() -> AWItems.COLLAPSE_CRADLE.get())
                .doubleItemIcon(AWItems.COLLAPSE_CRADLE.get(), AWItems.FOLDED_WARP_CRYSTAL.get())
                .emptyBackground(178, 96)
                .build(AeroWarptics.id("folding"), FoldingCategory::new));
        categories.add(new CreateRecipeCategory.Builder<>(uk.co.iceconchy.aerowarptics.collapse.CollapseRecipe.class)
                .addTypedRecipes(AWRecipeTypes.COLLAPSE)
                .catalyst(() -> AWItems.COLLAPSE_CRADLE.get())
                .catalyst(() -> com.simibubi.create.AllBlocks.DEPOT.get())
                .itemIcon(AWItems.COLLAPSE_CRADLE.get())
                .emptyBackground(178, 100)
                .build(AeroWarptics.id("collapse"), CollapseCategory::new));
        categories.add(new CreateRecipeCategory.Builder<>(uk.co.iceconchy.aerowarptics.wellhead.AgitatingRecipe.class)
                .addTypedRecipes(AWRecipeTypes.AGITATING)
                .catalyst(() -> AWItems.RIFT_COLLAR.get())
                .doubleItemIcon(AWItems.RIFT_COLLAR.get(), AWItems.RIFT_ESSENCE_BUCKET.get())
                .emptyBackground(178, 64)
                .build(AeroWarptics.id("agitating"), AgitatingCategory::new));
        registration.addRecipeCategories(categories.toArray(IRecipeCategory[]::new));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        categories.forEach(category -> category.registerCatalysts(registration));
    }

    /** An encased fan named for what it is doing, as Create names its own for washing and haunting. */
    private static ItemStack riftingFan() {
        ItemStack fan = AllBlocks.ENCASED_FAN.asStack();
        fan.set(DataComponents.CUSTOM_NAME, AWLang.translate("recipe.rifting.fan").component()
                .copy().withStyle(style -> style.withItalic(false)));
        return fan;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        categories.forEach(category -> category.registerRecipes(registration));

        // The book first, because it is the answer to the question somebody browsing JEI is asking.
        info(registration, AWItems.HANDBOOK.get(), "handbook");

        for (RiftDriveTier tier : RiftDriveTier.values()) {
            info(registration, AWItems.RIFT_DRIVES.get(tier).get(), "rift_drive");
        }
        info(registration, AWItems.DRIVE_EXTENDER.get(), "drive_extender");

        info(registration, AWItems.WARP_ANCHOR.get(), "warp_anchor");
        info(registration, AWItems.ASTROLABE.get(), "astrolabe");
        info(registration, AWItems.SPATIAL_SIPHON.get(), "spatial_siphon");
        info(registration, AWItems.RIFT_PROBE.get(), "rift_probe");
        info(registration, AWItems.RIFT_GOGGLES.get(), "rift_goggles");
        info(registration, AWItems.RIFT_BEACON.get(), "rift_beacon");
        info(registration, AWItems.RIFT_CHUTE.get(), "rift_chute");
        info(registration, AWItems.RIFT_MODULATOR.get(), "rift_modulator");
        info(registration, AWItems.RIFT_COUPLER.get(), "rift_coupler");
        info(registration, AWItems.SPATIAL_STABILISER.get(), "spatial_stabiliser");
        info(registration, AWItems.COOLING_VENT.get(), "cooling_vent");
        info(registration, AWItems.RIFT_BOND.get(), "rift_bond");
        info(registration, AWItems.BERTH.get(), "berth");
        info(registration, AWItems.SPATIAL_WAVE_DETECTOR.get(), "spatial_wave_detector");
        info(registration, AWItems.RIFT_GATE.get(), "rift_gate");
        info(registration, AWItems.RIFT_GATE_FRAME.get(), "rift_gate_frame");
        info(registration, AWItems.RIFT_CORE.get(), "rift_core");
        info(registration, AWItems.RIFT_LENS.get(), "rift_lens");
        info(registration, AWItems.STABILISER_RING.get(), "stabiliser_ring");
        info(registration, AWItems.SINGULARITY_CORE.get(), "singularity_core");
        info(registration, AWItems.RIFT_CASING.get(), "rift_casing");
        info(registration, AWItems.RIFT_PEARL.get(), "rift_pearl");
        info(registration, AWItems.RIFT_BRAZIER.get(), "rift_brazier");
        info(registration, AWItems.OPENED_RIFT_LENS.get(), "opened_rift_lens");
        info(registration, AWItems.WARP_GLASS.get(), "warp_glass");
        info(registration, AWItems.SCAR_HEART.get(), "scar_heart");
        info(registration, AWItems.RIFT_COLLAR.get(), "rift_collar");
        info(registration, AWItems.RIFT_SUNDERER.get(), "rift_sunderer");
        info(registration, AWItems.SCARRED_STONE.get(), "scarred_stone");
        info(registration, AWItems.RELIC_ARMILLARY.get(), "relic_armillary");
        info(registration, AWItems.SCAR_CHART.get(), "scar_chart");
        info(registration, AWItems.FOLDED_WARP_CRYSTAL.get(), "folded_warp_crystal");
        info(registration, AWItems.SHIP_COMPASS.get(), "ship_compass");
        info(registration, AWItems.COLLAPSE_CRADLE.get(), "collapse_cradle");
        info(registration, AWItems.RIFT_PLOTTER.get(), "rift_plotter");
        info(registration, AWItems.RIFT_WARD.get(), "rift_ward");
        info(registration, AWItems.UNSET_STORMGLASS.get(), "unset_stormglass");
        info(registration, AWItems.STORMGLASS.get(), "stormglass");

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
