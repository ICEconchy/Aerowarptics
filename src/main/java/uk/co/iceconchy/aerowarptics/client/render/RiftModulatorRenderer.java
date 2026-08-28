package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.client.model.RiftModulatorModel;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;

/**
 * Draws the Rift Modulator.
 *
 * <p>Only the {@code lens} bone is tinted, to whatever colour the block entity is holding - the plinth
 * and the pillar underneath it are machined brass and stay machined brass whatever the rift ends up
 * looking like. An unlinked or unfuelled Modulator draws its lens in a flat, unlit grey rather than the
 * chosen colour, so a glance at the block already answers "is this actually doing anything" without
 * needing the goggles.
 */
@OnlyIn(Dist.CLIENT)
public class RiftModulatorRenderer extends GeoBlockRenderer<RiftModulatorBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;
    private static final String LENS_BONE = "lens";
    /** What the lens draws as while it has nothing to show - unlit glass, not a colour choice. */
    private static final int IDLE_TINT = 0xFF_6E_6A_78;

    private static final double HALF_BLOCK = 0.5D;

    private int tint = IDLE_TINT;
    private boolean glowing;

    public RiftModulatorRenderer() {
        super(new RiftModulatorModel());
    }

    /**
     * Same fix as {@link RiftDriveRenderer#rotateBlock}, for the same reason: this model also takes
     * all six facings and also stands on its own base rather than being symmetric about the floor, so
     * a quarter turn about the block's corner rather than its middle sends it out of its own space the
     * moment it is mounted on a ceiling or the underside of a hull.
     */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        if (facing.getAxis().isHorizontal()) {
            super.rotateBlock(facing, poseStack);
            return;
        }
        poseStack.translate(0.0D, HALF_BLOCK, 0.0D);
        poseStack.mulPose(facing == Direction.UP
                ? Axis.XP.rotationDegrees(90.0F)
                : Axis.XN.rotationDegrees(90.0F));
        poseStack.translate(0.0D, -HALF_BLOCK, 0.0D);
    }

    @Override
    public void preRender(PoseStack poseStack, RiftModulatorBlockEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        glowing = animatable.linkedDrive() != null && animatable.active();
        tint = glowing ? (0xFF_00_00_00 | (animatable.colour() & 0xFF_FF_FF)) : IDLE_TINT;
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, RiftModulatorBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean lens = LENS_BONE.equals(bone.getName());
        int light = lens && glowing ? FULL_BRIGHT : packedLight;
        int tinted = lens ? tint : colour;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, light, lens && glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, tinted);
    }
}
