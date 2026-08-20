package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;

/** GeckoLib model binding for the Spatial Siphon. */
@OnlyIn(Dist.CLIENT)
public class SpatialSiphonModel extends GeoModel<SpatialSiphonBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/spatial_siphon.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/spatial_siphon.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/spatial_siphon.animation.json");

    @Override
    public ResourceLocation getModelResource(SpatialSiphonBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SpatialSiphonBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SpatialSiphonBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(SpatialSiphonBlockEntity animatable, ResourceLocation texture) {
        // The glass vessel and the essence inside it are both translucent.
        return RenderType.entityTranslucent(texture);
    }
}
