package uk.co.iceconchy.aerowarptics.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.iceconchy.aerowarptics.client.fx.RiftStormSky;

/**
 * Two places a Rift Storm changes what vanilla thinks the rain is, without changing the rain.
 *
 * <p>Vanilla reads the level's rain level for more than rain. {@code renderSky} fades the sun, moon
 * and stars out by it; {@code renderSnowAndRain} and {@code tickRain} draw, splash and play the rain by
 * it. Changing the level's actual rain level would do both - and also make the client believe it was
 * raining, dripping water off leaves in a desert. So each read is changed only where it is made:
 * <ul>
 *   <li>in {@code renderSky}, raised, so a storm veils the sun the way cloud cover does;
 *   <li>in {@code renderSnowAndRain} and {@code tickRain}, lowered, so vanilla's pale rain and snow fade
 *       out as the storm's own rain in {@code RiftStormRain} fades in, rather than both falling at once.
 * </ul>
 *
 * <p>Each method reads it exactly once. {@code MixinTargetTest} proves that against the game's own
 * bytecode, because an injection that matches nothing is a mixin failure at launch, and one that
 * matches the wrong call would do something nobody meant.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float aerowarptics$riftStormVeilsTheSun(float rainLevel) {
        return RiftStormSky.shroud(rainLevel);
    }

    @ModifyExpressionValue(method = {"renderSnowAndRain", "tickRain"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float aerowarptics$riftStormReplacesTheRain(float rainLevel) {
        return RiftStormSky.vanillaRain(rainLevel);
    }
}
