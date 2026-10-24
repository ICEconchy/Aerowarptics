package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;

/** GeckoLib model binding for the Rift Coupler. All three files are the artist's. */
@OnlyIn(Dist.CLIENT)
public class RiftCouplerModel extends GeoModel<RiftCouplerBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_coupler.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/rift_coupler.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_coupler.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftCouplerBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftCouplerBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftCouplerBlockEntity animatable) {
        return ANIMATION;
    }
}
