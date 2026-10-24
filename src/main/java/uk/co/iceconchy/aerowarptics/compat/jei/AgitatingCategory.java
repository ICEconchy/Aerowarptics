package uk.co.iceconchy.aerowarptics.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.wellhead.AgitatingRecipe;

/**
 * What a Rift Wellhead digests: a block in, the essence it comes to out, and - the half a player has to read -
 * how rough it is on the tear.
 *
 * <p>The essence is not made as the block goes in; the well digests it over time, at the rate it is running at.
 * So the page says what a block is worth and how it strains, and leaves how fast to the machine.
 */
public class AgitatingCategory extends CreateRecipeCategory<AgitatingRecipe> {

    private static final int TEXT = 0x4A3A66;

    public AgitatingCategory(Info<AgitatingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, AgitatingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 22, 12)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));
        builder.addSlot(RecipeIngredientRole.CATALYST, 80, 12)
                .addItemStack(new ItemStack(AWItems.RIFT_COLLAR.get()));
        if (!recipe.getFluidResults().isEmpty()) {
            FluidStack essence = recipe.getFluidResults().get(0);
            addFluidSlot(builder, 138, 12, essence);
        }
    }

    @Override
    protected void draw(AgitatingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 44, 16);
        AllGuiTextures.JEI_ARROW.render(graphics, 102, 16);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, AWLang.translate("recipe.agitating.essence", AWLang.count(recipe.essence())).component(),
                4, 40, TEXT, false);
        graphics.drawString(font, AWLang.translate(roughness(recipe.strain())).component(), 4, 50, TEXT, false);
    }

    /** Three words rather than a number: a player choosing a feed wants to know which is safer, not by how much. */
    static String roughness(double strain) {
        if (strain >= 0.9D) {
            return "recipe.agitating.rough";
        }
        return strain >= 0.55D ? "recipe.agitating.fair" : "recipe.agitating.clean";
    }
}
