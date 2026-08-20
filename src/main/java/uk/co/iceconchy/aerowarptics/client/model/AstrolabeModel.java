package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;

/**
 * GeckoLib model binding for the Astrolabe Cartography Table.
 *
 * <p>One model spans all nine blocks. Only the centre cell draws it, which is why the geometry runs
 * from -24 to +24 rather than staying inside a single block.
 */
@OnlyIn(Dist.CLIENT)
public class AstrolabeModel extends GeoModel<AstrolabeBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/astrolabe.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/astrolabe.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/astrolabe.animation.json");

    @Override
    public ResourceLocation getModelResource(AstrolabeBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AstrolabeBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AstrolabeBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(AstrolabeBlockEntity animatable, ResourceLocation texture) {
        // The chart glass and the ring inlays both want alpha.
        return RenderType.entityTranslucent(texture);
    }
}
