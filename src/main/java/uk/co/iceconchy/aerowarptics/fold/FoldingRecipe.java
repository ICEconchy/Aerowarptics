package uk.co.iceconchy.aerowarptics.fold;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Collapse Cradle folds: {@code aerowarptics:folding}.
 *
 * <p>The cradle holds what is to be folded in its arms and anything it is folded with in its cup, and a fold
 * takes one of each ingredient per craft from those two, as a basin would, and burns the recipe's Rift Essence
 * per craft from the cradle's tank. A whole stack goes through in one run - see
 * {@code CollapseCradleBlockEntity}.
 *
 * <pre>{@code
 * {"type": "aerowarptics:folding",
 *  "ingredients": [{"item": "aerowarptics:warp_crystal"},
 *                  {"type": "neoforge:single", "amount": 100, "fluid": "aerowarptics:rift_essence"}],
 *  "results": [{"id": "aerowarptics:folded_warp_crystal"}],
 *  "processing_time": 200}
 * }</pre>
 *
 * <p>Folding was once done aboard a ship, in a Fold Crucible, on a jump long enough to count. The crucible is
 * gone; what was a distance is now a price in essence, paid at the cradle.
 */
public class FoldingRecipe extends ProcessingRecipe<FoldingRecipe.Contents, ProcessingRecipeParams> {

    public FoldingRecipe(ProcessingRecipeParams params) {
        super(AWRecipeTypes.FOLDING, params);
    }

    /** Millibuckets of Rift Essence one craft burns, or 0 if it asks for none. */
    public int essence() {
        return fluidIngredients.isEmpty() ? 0 : fluidIngredients.get(0).amount();
    }

    @Override
    public boolean matches(Contents contents, Level level) {
        return take(contents.stacks(), true);
    }

    /**
     * How many crafts these stacks hold, up to {@code most}: one ingredient of each per craft, taken as
     * {@link #take} takes them, from copies - the stacks themselves are not touched.
     */
    public int crafts(List<ItemStack> slots, int most) {
        List<ItemStack> copies = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) {
            copies.add(stack.copy());
        }
        int crafts = 0;
        while (crafts < most && take(copies, false)) {
            crafts++;
        }
        return crafts;
    }

    /**
     * Takes one of each ingredient out of the slots, or checks that it could.
     *
     * <p>Greedy, slot by slot. That is enough for the shapes these recipes have - a thing and what it is folded
     * with - and it is the rule a basin plays by too.
     *
     * @param simulate {@code true} to leave the stacks alone and only answer
     * @return whether every ingredient was found
     */
    public boolean take(List<ItemStack> slots, boolean simulate) {
        int[] used = new int[slots.size()];
        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemStack stack = slots.get(slot);
                if (stack.getCount() - used[slot] > 0 && ingredient.test(stack)) {
                    used[slot]++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        if (!simulate) {
            for (int slot = 0; slot < slots.size(); slot++) {
                if (used[slot] > 0) {
                    slots.get(slot).shrink(used[slot]);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (ingredients.isEmpty()) {
            errors.add("A fold needs something to fold.");
        }
        return errors;
    }

    /** The cradle's arms and its cup: a fold is a thing and, at most, what it is folded with. */
    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    @Override
    protected int getMaxOutputCount() {
        return 4;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    /** The cradle's arms and cup, as a recipe sees them. */
    public record Contents(List<ItemStack> stacks) implements RecipeInput {

        @Override
        public ItemStack getItem(int index) {
            return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return stacks.size();
        }
    }

    public static class Serializer implements RecipeSerializer<FoldingRecipe> {

        private final MapCodec<FoldingRecipe> codec =
                ProcessingRecipe.codec(FoldingRecipe::new, ProcessingRecipeParams.CODEC);
        private final StreamCodec<RegistryFriendlyByteBuf, FoldingRecipe> streamCodec =
                ProcessingRecipe.streamCodec(FoldingRecipe::new, ProcessingRecipeParams.STREAM_CODEC);

        @Override
        public MapCodec<FoldingRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoldingRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
