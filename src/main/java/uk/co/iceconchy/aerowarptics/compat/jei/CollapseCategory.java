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
import uk.co.iceconchy.aerowarptics.collapse.CollapseRecipe;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * The Collapse in JEI: the core and the heart on the cradle, the offerings on the ring of depots round it,
 * the essence it burns, and the core it makes.
 *
 * <p>The page says the part a slot cannot: the heart is the stake. A ritual that goes wrong hands back the
 * core and every offering, and keeps only the heart and the essence.
 */
public class CollapseCategory extends CreateRecipeCategory<CollapseRecipe> {

    private static final int TEXT = 0x4A3A66;

    public CollapseCategory(Info<CollapseRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, CollapseRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 80, 6)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.core());
        builder.addSlot(RecipeIngredientRole.INPUT, 80, 44)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.heart());
        builder.addSlot(RecipeIngredientRole.CATALYST, 80, 25)
                .addItemStack(new ItemStack(AWItems.COLLAPSE_CRADLE.get()));
        List<Ingredient> offerings = recipe.offerings();
        for (int i = 0; i < offerings.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4 + (i % 4) * 18, 6 + (i / 4) * 19)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addIngredients(offerings.get(i));
        }
        List<SizedFluidIngredient> fluids = recipe.getFluidIngredients();
        if (!fluids.isEmpty()) {
            addFluidSlot(builder, 4, 44, fluids.get(0));
        }
        List<ProcessingOutput> results = recipe.getRollableResults();
        for (int i = 0; i < results.size(); i++) {
            ProcessingOutput output = results.get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 150, 25 + i * 19)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    protected void draw(CollapseRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 110, 29);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, AWLang.translate("recipe.collapse.speed",
                AWLang.translate(recipe.driveTier().translationKey()).component()).component(), 4, 68, TEXT, false);
        graphics.drawString(font, AWLang.translate("recipe.collapse.time",
                AWLang.count(Math.max(1, recipe.getProcessingDuration() / 20))).component(), 4, 78, TEXT, false);
        graphics.drawString(font, AWLang.translate("recipe.collapse.stake").component(), 4, 88, TEXT, false);
    }
}
