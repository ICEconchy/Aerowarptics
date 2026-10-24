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
 * Draws the Warp Anchor, lighting the bulb in its lantern with the anchor's synced status colour so a
 * pilot can read an anchor's state from the air.
 *
 * <p>The lantern holds two bulbs in one place: a dim one painted in the sheet, and a lit one a hair larger
 * round it. The status clips switch the lit one on and off by its scale - blinking while idle, gone while
 * cooling down - and this lights it full-bright in the status colour whenever it is drawn.
 */
@OnlyIn(Dist.CLIENT)
public class WarpAnchorRenderer extends GeoBlockRenderer<WarpAnchorBlockEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final String LIT_BULB = "bulb_lit";

    public WarpAnchorRenderer() {
        super(new WarpAnchorModel());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, WarpAnchorBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean glowing = LIT_BULB.equals(bone.getName());
        int light = glowing ? FULL_BRIGHT : packedLight;
        int tinted = glowing ? 0xFF000000 | animatable.status().colour() : colour;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, light, glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, tinted);
    }

    /** The anchor's own box. NeoForge asks the renderer, not the block entity - see {@link RiftDriveRenderer#getRenderBoundingBox}. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(WarpAnchorBlockEntity anchor) {
        return anchor.getRenderBoundingBox();
    }
}
