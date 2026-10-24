package uk.co.iceconchy.aerowarptics.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.iceconchy.aerowarptics.rail.RailCorridor;

/**
 * Keeps the Rift Corridor from marking every world "experimental".
 *
 * <p>Vanilla calls a world's dimensions stable only while there are exactly as many as vanilla has, and each
 * dimension it does not recognise as one of its own is stamped experimental as well; a world that is not stable
 * greets its player with "Worlds using Experimental Settings are not supported" every time it is opened. The
 * corridor is a dimension of this mod's own, generated as void and known to be stable, so it is neither counted nor
 * stamped. Any other extra dimension still is both: this changes nothing about anybody else's.
 *
 * <p>Nothing else lives here, and nothing may: classes in a mixin package cannot be loaded as ordinary classes.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {

    @ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int aerowarptics$leaveTheCorridorUncounted(int dimensions,
                                                        @Local(argsOnly = true) Registry<LevelStem> stems) {
        // Either list: a new world's comes from the data packs, a saved one's from its level.dat as well.
        boolean present = stems.containsKey(RailCorridor.STEM)
                || ((WorldDimensions) (Object) this).dimensions().containsKey(RailCorridor.STEM);
        return RailCorridor.countForStability(dimensions, present);
    }

    @ModifyReturnValue(method = "checkStability", at = @At("RETURN"))
    private static Lifecycle aerowarptics$theCorridorIsStable(Lifecycle lifecycle,
                                                               @Local(argsOnly = true) ResourceKey<LevelStem> key) {
        return RailCorridor.STEM.equals(key) ? Lifecycle.stable() : lifecycle;
    }
}
