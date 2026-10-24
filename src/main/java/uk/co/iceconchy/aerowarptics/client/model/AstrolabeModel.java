package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure;

/**
 * GeckoLib model binding for the Astrolabe Cartography Table.
 *
 * <p>The artist drew the table at each of the sizes it can be built at, one model apiece, each centred on its
 * own footprint: {@code astrolabe_1x}, {@code astrolabe_2x}, and {@code astrolabe} for the full three by three,
 * whose geometry runs from -24 to +24. Only the cell holding the table's state draws it.
 */
@OnlyIn(Dist.CLIENT)
public class AstrolabeModel extends GeoModel<AstrolabeBlockEntity> {

    private static final ResourceLocation SMALL = AeroWarptics.id("geo/astrolabe_1x.geo.json");
    private static final ResourceLocation MIDDLE = AeroWarptics.id("geo/astrolabe_2x.geo.json");
    private static final ResourceLocation FULL = AeroWarptics.id("geo/astrolabe.geo.json");
    private static final ResourceLocation TEXTURE = AeroWarptics.id("textures/block/astrolabe.png");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/astrolabe.animation.json");

    @Override
    public ResourceLocation getModelResource(AstrolabeBlockEntity animatable) {
        int size = animatable == null ? AstrolabeStructure.MAX_SIZE : animatable.size();
        return size <= 1 ? SMALL : size == 2 ? MIDDLE : FULL;
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
        // The chart's grid lines are drawn on a clear pane, which wants alpha.
        return RenderType.entityTranslucent(texture);
    }
}
