package uk.co.iceconchy.aerowarptics.registry;

import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseRecipe;
import uk.co.iceconchy.aerowarptics.fold.FoldingRecipe;
import uk.co.iceconchy.aerowarptics.rifting.RiftingRecipe;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The mod's own recipe types, in the shape of Create's {@code AllRecipeTypes}.
 *
 * <p>Every process the crafting layer adds is a datapack recipe type, so a pack maker can hang their own
 * progression on rifting, folding or a collapse - see {@code docs/crafting-plan.md}, rule 7. Each one is a
 * serializer and a type registered under the same name, and the enum is what the machines and JEI ask.
 *
 * <p>Built on Create's processing recipes rather than beside them: the JSON reads like any Create recipe,
 * with the same {@code ingredients} and {@code results} (chances included), and the three types carry only
 * what a Create recipe has no word for - how long a fold has to be, or which drive a collapse is for.
 */
public enum AWRecipeTypes implements IRecipeTypeInfo {

    /** Items blown through a standing rift by an encased fan. */
    RIFTING(() -> new StandardProcessingRecipe.Serializer<>(RiftingRecipe::new)),

    /** A stack folded at a Collapse Cradle, for Rift Essence. */
    FOLDING(FoldingRecipe.Serializer::new),

    /** The Collapse Cradle's ritual. */
    COLLAPSE(CollapseRecipe.Serializer::new),

    /** A block fed to a Rift Wellhead, and the essence and the strain that come of it. */
    AGITATING(uk.co.iceconchy.aerowarptics.wellhead.AgitatingRecipe.Serializer::new);

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializer;
    private final DeferredHolder<RecipeType<?>, RecipeType<?>> type;

    AWRecipeTypes(Supplier<RecipeSerializer<?>> serializer) {
        String name = name().toLowerCase(Locale.ROOT);
        ResourceLocation key = AeroWarptics.id(name);
        this.id = key;
        this.serializer = Registers.SERIALIZERS.register(name, serializer);
        this.type = Registers.TYPES.register(name, () -> RecipeType.simple(key));
    }

    public static void register(IEventBus modEventBus) {
        Registers.SERIALIZERS.register(modEventBus);
        Registers.TYPES.register(modEventBus);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializer.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type.get();
    }

    public <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> find(I input, Level level) {
        return level.getRecipeManager().getRecipeFor(this.<I, R>getType(), input, level);
    }

    /** Held apart so the registers exist before the first constant is built, as in Create's own enum. */
    private static final class Registers {
        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, AeroWarptics.MODID);
        private static final DeferredRegister<RecipeType<?>> TYPES =
                DeferredRegister.create(Registries.RECIPE_TYPE, AeroWarptics.MODID);
    }
}
