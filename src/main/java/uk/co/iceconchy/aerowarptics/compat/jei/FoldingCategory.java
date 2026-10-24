package uk.co.iceconchy.aerowarptics.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import uk.co.iceconchy.aerowarptics.fold.FoldingRecipe;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * Folding in JEI: what goes in a Collapse Cradle's arms and cup, the essence each craft burns, and what comes
 * out. A whole stack of up to sixteen folds in one run, which the page says, because it is the difference
 * between one fold and sixteen.
 */
public class FoldingCategory extends CreateRecipeCategory<FoldingRecipe> {

    private static final int TEXT = 0x4A3A66;

    public FoldingCategory(Info<FoldingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, FoldingRecipe recipe, IFocusGroup focuses) {
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 12 + (i % 2) * 19, 8 + (i / 2) * 19)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addIngredients(ingredients.get(i));
        }
        List<SizedFluidIngredient> fluids = recipe.getFluidIngredients();
        if (!fluids.isEmpty()) {
            addFluidSlot(builder, 12, 29, fluids.get(0));
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 80, 17)
                .addItemStack(new ItemStack(AWItems.COLLAPSE_CRADLE.get()));
        List<ProcessingOutput> results = recipe.getRollableResults();
        for (int i = 0; i < results.size(); i++) {
            ProcessingOutput output = results.get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 140 + (i % 2) * 19, 8 + (i / 2) * 19)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    protected void draw(FoldingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 52, 21);
        AllGuiTextures.JEI_ARROW.render(graphics, 102, 21);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, AWLang.translate("recipe.folding.essence", AWLang.count(recipe.essence())).component(),
                4, 54, TEXT, false);
        graphics.drawString(font, AWLang.translate("recipe.folding.stack").component(), 4, 64, TEXT, false);
    }
}
