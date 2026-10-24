package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;

/** GeckoLib model binding for the Collapse Cradle. The geometry, the sheet and its glow mask are the artist's; the one clip is its item's. */
@OnlyIn(Dist.CLIENT)
public class CollapseCradleModel extends GeoModel<CollapseCradleBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/collapse_cradle.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/collapse_cradle.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/collapse_cradle.animation.json");

    @Override
    public ResourceLocation getModelResource(CollapseCradleBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CollapseCradleBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CollapseCradleBlockEntity animatable) {
        return ANIMATION;
    }
}
