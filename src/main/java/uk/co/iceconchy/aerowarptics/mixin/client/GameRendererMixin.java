package uk.co.iceconchy.aerowarptics.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.iceconchy.aerowarptics.client.fx.AfterShaders;

/**
 * The one moment between a shader pack finishing the world and the game clearing its depth.
 *
 * <p>Under a shader pack the mod draws its rifts over the pack's finished frame rather than inside it - see
 * {@link AfterShaders} for why. Iris composites as {@code LevelRenderer.renderLevel} returns, and the very next
 * thing {@code GameRenderer.renderLevel} does is clear the depth buffer for the hand, after which nothing can be
 * hidden behind a hill again. NeoForge announces nothing in between - its last stage is still inside
 * {@code LevelRenderer.renderLevel} - so this is a mixin, straight after that call.
 *
 * <p>With no shader pack there is nothing held and this does nothing.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;"
                    + "ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
                    + "Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void aerowarptics$drawOverTheShaderPack(DeltaTracker deltaTracker, CallbackInfo callback) {
        AfterShaders.replay();
    }
}
