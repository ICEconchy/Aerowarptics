package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;

import java.util.List;

/**
 * A block fed to a Rift Wellhead: {@code aerowarptics:agitating}.
 *
 * <p>One ingredient in, and one fluid out - the Rift Essence the well will digest it into, over however long
 * that takes at the strain it is running at. Nothing comes out the moment it goes in: the recipe says what a
 * block is <em>worth</em>, and {@link WellheadReactor} is what makes it so. The {@code strain} beside it is how
 * rough the feed is - see {@link AgitatingRecipeParams}.
 */
public class AgitatingRecipe extends ProcessingRecipe<SingleRecipeInput, AgitatingRecipeParams> {

    public AgitatingRecipe(AgitatingRecipeParams params) {
        super(AWRecipeTypes.AGITATING, params);
    }

    /** Millibuckets of essence one of the ingredient comes to. */
    public int essence() {
        return fluidResults.isEmpty() ? 0 : fluidResults.get(0).getAmount();
    }

    /** How hard each of those millibuckets strains the tear. */
    public double strain() {
        return params.strain();
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && ingredients.get(0).test(input.getItem(0));
    }

    @Override
    public List<String> validate() {
        List<String> errors = super.validate();
        if (essence() <= 0) {
            errors.add("An agitating recipe has to yield some Rift Essence.");
        }
        return errors;
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }

    public static class Serializer implements RecipeSerializer<AgitatingRecipe> {

        private final MapCodec<AgitatingRecipe> codec =
                ProcessingRecipe.codec(AgitatingRecipe::new, AgitatingRecipeParams.CODEC);
        private final StreamCodec<RegistryFriendlyByteBuf, AgitatingRecipe> streamCodec =
                ProcessingRecipe.streamCodec(AgitatingRecipe::new, AgitatingRecipeParams.STREAM_CODEC);

        @Override
        public MapCodec<AgitatingRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AgitatingRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
