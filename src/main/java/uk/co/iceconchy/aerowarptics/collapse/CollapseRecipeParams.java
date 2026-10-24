package uk.co.iceconchy.aerowarptics.collapse;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.function.Function;

/**
 * What a collapse asks of the cradle, on top of Create's usual ingredients, fluid and results.
 *
 * <p>{@code drive_tier} is the tier whose rotation the cradle has to be turned at - its minimum RPM to
 * start at all, its optimal RPM to run clean - and whose stress it draws. The Singularity's own figures by
 * default: if you cannot turn the cradle, you cannot fly the drive it makes.
 */
public class CollapseRecipeParams extends ProcessingRecipeParams {

    /**
     * The part of the file this mod reads, on its own - held in a class of its own so a test can decode it
     * without loading Create's params, whose codecs need a game to resolve ingredients against.
     */
    public static final class Fields {

        public static final MapCodec<RiftDriveTier> DRIVE_TIER =
                RiftDriveTier.CODEC.optionalFieldOf("drive_tier", RiftDriveTier.SINGULARITY);

        private Fields() {
        }
    }

    public static final MapCodec<CollapseRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            codec(CollapseRecipeParams::new).forGetter(Function.identity()),
            Fields.DRIVE_TIER.forGetter(CollapseRecipeParams::driveTier)
    ).apply(instance, (params, tier) -> {
        params.driveTier = tier;
        return params;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, CollapseRecipeParams> STREAM_CODEC =
            streamCodec(CollapseRecipeParams::new);

    private RiftDriveTier driveTier = RiftDriveTier.SINGULARITY;

    protected final RiftDriveTier driveTier() {
        return driveTier;
    }

    @Override
    protected void encode(RegistryFriendlyByteBuf buffer) {
        super.encode(buffer);
        ByteBufCodecs.VAR_INT.encode(buffer, driveTier.index());
    }

    @Override
    protected void decode(RegistryFriendlyByteBuf buffer) {
        super.decode(buffer);
        driveTier = RiftDriveTier.byIndex(ByteBufCodecs.VAR_INT.decode(buffer));
    }
}
