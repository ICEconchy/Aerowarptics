package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;

/** GeckoLib model binding for the Rift Chute. All three files come out of tools/chute_model.py. */
@OnlyIn(Dist.CLIENT)
public class RiftChuteModel extends GeoModel<RiftChuteBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_chute.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/rift_chute.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_chute.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftChuteBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftChuteBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftChuteBlockEntity animatable) {
        return ANIMATION;
    }
}
