package uk.co.iceconchy.aerowarptics.collapse;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;

import java.util.List;

/**
 * The Collapse Cradle's ritual: {@code aerowarptics:collapse}.
 *
 * <p>Ingredients are read <em>by position</em>, which is the one departure from a Create recipe's shape
 * and the reason it is worth a sentence: slot 0 is what sits in the cradle's core slot, slot 1 what sits in
 * its heart slot, and everything after that is an offering, taken from the depots in the ring. Keeping the
 * heart at a fixed index is what lets a test see which recipes need one - a Scar Heart is the stake, and a
 * recipe that quietly moved it among the offerings would be one a failed ritual refunds.
 *
 * <pre>{@code
 * {"type": "aerowarptics:collapse",
 *  "ingredients": [{"item": "aerowarptics:rift_core"}, {"item": "aerowarptics:scar_heart"},
 *                  {"item": "aerowarptics:pearlescent_warp_crystal"}, ...,
 *                  {"type": "neoforge:single", "amount": 4000, "fluid": "aerowarptics:rift_essence"}],
 *  "results": [{"id": "aerowarptics:singularity_core"}],
 *  "processing_time": 1200, "drive_tier": "singularity"}
 * }</pre>
 */
public class CollapseRecipe extends ProcessingRecipe<CollapseRecipe.Altar, CollapseRecipeParams> {

    public CollapseRecipe(CollapseRecipeParams params) {
        super(AWRecipeTypes.COLLAPSE, params);
    }

    public Ingredient core() {
        return ingredients.get(0);
    }

    public Ingredient heart() {
        return ingredients.get(1);
    }

    /** Everything taken from the ring of depots, one item per entry. */
    public List<Ingredient> offerings() {
        return ingredients.subList(2, ingredients.size());
    }

    /** Millibuckets of Rift Essence the whole ritual burns, or 0 if it asks for none. */
    public int essence() {
        return fluidIngredients.isEmpty() ? 0 : fluidIngredients.get(0).amount();
    }

    public RiftDriveTier driveTier() {
        return params.driveTier();
    }

    @Override
    public boolean matches(Altar altar, Level level) {
        return core().test(altar.core()) && heart().test(altar.heart()) && takeOfferings(altar.offerings(), true);
    }

    /**
     * Takes one item per offering out of what the depots hold, or checks that it could.
     *
     * <p>Items beyond what the recipe asks for are left where they stand: a ring holding more than the
     * ritual needs is a ring somebody over-filled, not one that should refuse.
     */
    public boolean takeOfferings(List<ItemStack> depots, boolean simulate) {
        int[] used = new int[depots.size()];
        for (Ingredient offering : offerings()) {
            boolean found = false;
            for (int depot = 0; depot < depots.size(); depot++) {
                ItemStack stack = depots.get(depot);
                if (stack.getCount() - used[depot] > 0 && offering.test(stack)) {
                    used[depot]++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        if (!simulate) {
            for (int depot = 0; depot < depots.size(); depot++) {
                if (used[depot] > 0) {
                    depots.get(depot).shrink(used[depot]);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (ingredients.size() < 2) {
            errors.add("A collapse needs at least a core (slot 0) and a heart (slot 1).");
        }
        return errors;
    }

    @Override
    protected int getMaxInputCount() {
        return 10;
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

    /**
     * What stands on the altar: the cradle's two slots and the depots round it.
     *
     * @param offerings the depots' held stacks, in no particular order
     */
    public record Altar(ItemStack core, ItemStack heart, List<ItemStack> offerings) implements RecipeInput {

        @Override
        public ItemStack getItem(int index) {
            if (index == 0) {
                return core;
            }
            if (index == 1) {
                return heart;
            }
            int offering = index - 2;
            return offering >= 0 && offering < offerings.size() ? offerings.get(offering) : ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 2 + offerings.size();
        }
    }

    public static class Serializer implements RecipeSerializer<CollapseRecipe> {

        private final MapCodec<CollapseRecipe> codec =
                ProcessingRecipe.codec(CollapseRecipe::new, CollapseRecipeParams.CODEC);
        private final StreamCodec<RegistryFriendlyByteBuf, CollapseRecipe> streamCodec =
                ProcessingRecipe.streamCodec(CollapseRecipe::new, CollapseRecipeParams.STREAM_CODEC);

        @Override
        public MapCodec<CollapseRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CollapseRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
