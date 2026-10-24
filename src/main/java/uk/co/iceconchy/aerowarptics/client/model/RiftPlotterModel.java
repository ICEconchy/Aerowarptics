package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;

/** GeckoLib model binding for the Rift Plotter. The geometry, the sheet and its glow mask are the artist's; the one clip is its item's. */
@OnlyIn(Dist.CLIENT)
public class RiftPlotterModel extends GeoModel<RiftPlotterBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_plotter.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/rift_plotter.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_plotter.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftPlotterBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftPlotterBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftPlotterBlockEntity animatable) {
        return ANIMATION;
    }
}
