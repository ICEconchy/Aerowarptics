package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;

/** GeckoLib model binding for the Rift Modulator. All three files come out of tools/rift_modulator_model.py. */
@OnlyIn(Dist.CLIENT)
public class RiftModulatorModel extends GeoModel<RiftModulatorBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_modulator.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/rift_modulator.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_modulator.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftModulatorBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftModulatorBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftModulatorBlockEntity animatable) {
        return ANIMATION;
    }
}
