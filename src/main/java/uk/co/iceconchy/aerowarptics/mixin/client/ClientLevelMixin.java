package uk.co.iceconchy.aerowarptics.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.iceconchy.aerowarptics.client.fx.RiftStormSky;

/**
 * A Rift Storm darkens the sky, the clouds and the daylight.
 *
 * <p>A mixin because there is nothing else to hook: NeoForge fires an event for the fog's colour, which
 * {@link RiftStormSky} uses, but none for the sky's, the clouds' or the daylight's. Each of these only
 * adjusts what vanilla already worked out, at the very end, so rain, thunder, a sunset and anybody
 * else's changes are all still in the colour a storm is applied to. With no storm drawing, each hands
 * back exactly what it was given.
 *
 * <p>Nothing else lives here, and nothing may: classes in a mixin package cannot be loaded as ordinary
 * classes. The arithmetic is in {@code RiftStormPalette}, the state in {@link RiftStormSky}.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 aerowarptics$riftStormSky(Vec3 original, @Local(argsOnly = true) float partialTick) {
        return RiftStormSky.skyColour(original, partialTick);
    }

    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 aerowarptics$riftStormClouds(Vec3 original, @Local(argsOnly = true) float partialTick) {
        return RiftStormSky.cloudColour(original, partialTick);
    }

    /** The daylight the lightmap is built from - so a storm dims the world the way vanilla's thunder does. */
    @ModifyReturnValue(method = "getSkyDarken", at = @At("RETURN"))
    private float aerowarptics$riftStormDaylight(float original, @Local(argsOnly = true) float partialTick) {
        return RiftStormSky.skyLight(original, partialTick);
    }
}
