package uk.co.iceconchy.aerowarptics.rifting;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;

/**
 * An item blown through a standing rift by an encased fan: {@code aerowarptics:rifting}.
 *
 * <p>Shaped exactly like Create's haunting recipe, one ingredient in and a list of chanced results out,
 * because it is the same kind of process with a different catalyst. What makes a rift "standing" is
 * {@link RiftingType#isValidAt}'s business, not the recipe's.
 */
public class RiftingRecipe extends StandardProcessingRecipe<SingleRecipeInput> {

    public RiftingRecipe(ProcessingRecipeParams params) {
        super(AWRecipeTypes.RIFTING, params);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && ingredients.get(0).test(input.getItem(0));
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 12;
    }
}
