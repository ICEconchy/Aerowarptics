package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity;

/** GeckoLib model binding for the Rift Brazier. The geometry and the sheet are the artist's; only its fire moves. */
@OnlyIn(Dist.CLIENT)
public class RiftBrazierModel extends GeoModel<RiftBrazierBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_brazier.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/rift_brazier.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_brazier.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftBrazierBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftBrazierBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftBrazierBlockEntity animatable) {
        return ANIMATION;
    }
}
