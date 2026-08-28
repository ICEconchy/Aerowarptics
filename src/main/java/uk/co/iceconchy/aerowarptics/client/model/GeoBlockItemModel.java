package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.registry.GeoBlockItem;

/**
 * GeckoLib model binding for every block item drawn from its block's own model.
 *
 * <p>One class for all of them, because the item is the thing that knows which model it is - the
 * five drive tiers alone would otherwise be five near-identical files differing by one texture path.
 * The block-side bindings beside this one are per machine because they read state off a block entity
 * to decide what to show; an item in a slot has no state to read.
 */
@OnlyIn(Dist.CLIENT)
public class GeoBlockItemModel extends GeoModel<GeoBlockItem> {

    @Override
    public ResourceLocation getModelResource(GeoBlockItem item) {
        return item.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(GeoBlockItem item) {
        return item.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(GeoBlockItem item) {
        return item.animationResource();
    }

    @Override
    public RenderType getRenderType(GeoBlockItem item, ResourceLocation texture) {
        // The same type the block renderers use. Every one of these machines has glass, a glowing
        // core or a cut-out grille in it, and drawn opaque the cut-outs come back as black.
        return RenderType.entityTranslucent(texture);
    }
}
