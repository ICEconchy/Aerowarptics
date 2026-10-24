package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Function;

/**
 * What feeding a block to a Rift Wellhead comes to, on top of Create's usual ingredient and result.
 *
 * <p>{@code strain} is how hard each millibucket of the essence it yields strains the tear: 1 for the roughest
 * feed there is, less for a cleaner one. It is the whole difference between a block that is cheap to feed and
 * one that is safe to.
 */
public class AgitatingRecipeParams extends ProcessingRecipeParams {

    /** The roughest a feed may be declared, so a data pack cannot write a block that melts a well in one bite. */
    public static final double MOST_STRAIN = 4.0D;

    /**
     * The part of the file this mod reads, on its own - held in a class of its own so a test can decode it
     * without loading Create's params, whose codecs need a game to resolve ingredients against.
     */
    public static final class Fields {

        public static final MapCodec<Double> STRAIN =
                Codec.doubleRange(0.0D, MOST_STRAIN).optionalFieldOf("strain", 1.0D);

        private Fields() {
        }
    }

    public static final MapCodec<AgitatingRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            codec(AgitatingRecipeParams::new).forGetter(Function.identity()),
            Fields.STRAIN.forGetter(AgitatingRecipeParams::strain)
    ).apply(instance, (params, strain) -> {
        params.strain = strain;
        return params;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, AgitatingRecipeParams> STREAM_CODEC =
            streamCodec(AgitatingRecipeParams::new);

    private double strain = 1.0D;

    protected final double strain() {
        return strain;
    }

    @Override
    protected void encode(RegistryFriendlyByteBuf buffer) {
        super.encode(buffer);
        ByteBufCodecs.DOUBLE.encode(buffer, strain);
    }

    @Override
    protected void decode(RegistryFriendlyByteBuf buffer) {
        super.decode(buffer);
        strain = ByteBufCodecs.DOUBLE.decode(buffer);
    }
}
