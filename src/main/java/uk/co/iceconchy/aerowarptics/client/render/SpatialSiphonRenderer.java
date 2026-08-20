package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.client.model.SpatialSiphonModel;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;

/**
 * Draws the Spatial Siphon, with as much essence in it as it is actually holding.
 *
 * <p>The fill is posed rather than animated, for the same reason the drive's needle is: it is a
 * reading, not a movement. Scaling the column from its own base means an empty vessel is empty and a
 * full one is full, with everything in between landing where a player would expect.
 */
@OnlyIn(Dist.CLIENT)
public class SpatialSiphonRenderer extends GeoBlockRenderer<SpatialSiphonBlockEntity> {

    private static final String FLUID_BONE = "essence";
    private static final int FULL_BRIGHT = 0xF0_00F0;

    /** Never quite zero: a hairline of essence still reads better than a bone that vanishes. */
    private static final float MINIMUM_VISIBLE = 0.04F;

    public SpatialSiphonRenderer() {
        super(new SpatialSiphonModel());
    }

    @Override
    public void preRender(PoseStack poseStack, SpatialSiphonBlockEntity siphon, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        float fill = siphon.fillLevel();
        model.getBone(FLUID_BONE).ifPresent(bone -> {
            bone.setHidden(fill <= 0.001F);
            bone.setScaleY(Math.max(MINIMUM_VISIBLE, fill));
        });
        super.preRender(poseStack, siphon, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, SpatialSiphonBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean glowing = FLUID_BONE.equals(bone.getName());
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, glowing ? FULL_BRIGHT : packedLight,
                glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, colour);
    }
}
