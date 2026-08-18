package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;

/** GeckoLib model binding for the Warp Anchor. */
@OnlyIn(Dist.CLIENT)
public class WarpAnchorModel extends GeoModel<WarpAnchorBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/warp_anchor.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/warp_anchor.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/warp_anchor.animation.json");

    @Override
    public ResourceLocation getModelResource(WarpAnchorBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WarpAnchorBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WarpAnchorBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(WarpAnchorBlockEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
