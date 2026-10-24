package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;

/** GeckoLib model binding for the Rift Ward. The geometry, the sheet and its glow mask are the artist's; the one clip is its item's. */
@OnlyIn(Dist.CLIENT)
public class RiftWardModel extends GeoModel<RiftWardBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_ward.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/rift_ward.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_ward.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftWardBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftWardBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftWardBlockEntity animatable) {
        return ANIMATION;
    }
}
