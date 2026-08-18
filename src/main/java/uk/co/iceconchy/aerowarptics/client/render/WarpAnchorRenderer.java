package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.client.model.WarpAnchorModel;

/**
 * Draws the Warp Anchor, tinting its beacon bone with the anchor's synced status colour so a pilot
 * can read an anchor's state from the air.
 */
@OnlyIn(Dist.CLIENT)
public class WarpAnchorRenderer extends GeoBlockRenderer<WarpAnchorBlockEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;

    public WarpAnchorRenderer() {
        super(new WarpAnchorModel());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, WarpAnchorBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean glowing = "beacon".equals(bone.getName()) || "lens".equals(bone.getName());
        int light = glowing ? FULL_BRIGHT : packedLight;
        int tinted = glowing ? 0xFF000000 | animatable.status().colour() : colour;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, light, glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, tinted);
    }
}
