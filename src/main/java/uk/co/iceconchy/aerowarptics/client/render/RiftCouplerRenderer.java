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
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.model.RiftCouplerModel;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;

/**
 * Draws the Rift Coupler.
 *
 * <p>Only the gem between the forks (the artist's {@code gem} bone) is tinted: the ship's tether colour, the same one its
 * harmonic tether is drawn in, so a coupler says which ship is which before anyone reads a screen. It is
 * lit only while the coupler is part of a muster - a crystal glowing on every idle deck would say nothing.
 * Without a drive beside it the crystal is unlit grey glass, the Modulator's own answer to "is this doing
 * anything".
 */
@OnlyIn(Dist.CLIENT)
public class RiftCouplerRenderer extends GeoBlockRenderer<RiftCouplerBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;
    private static final String CRYSTAL_BONE = "gem";
    private static final int IDLE_TINT = 0xFF_6E_6A_78;
    private static final double HALF_BLOCK = 0.5D;

    private int tint = IDLE_TINT;
    private boolean glowing;

    public RiftCouplerRenderer() {
        super(new RiftCouplerModel());
    }

    /** The Modulator's fix, for the Modulator's reason: all six facings, standing on its own base. */
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

    /** Only in the world, like the Modulator - aboard a ship it is the list a warp can empty. See {@link DrawnAboard}. */
    @Override
    public boolean shouldRenderOffScreen(RiftCouplerBlockEntity coupler) {
        return DrawnAboard.offScreen(coupler);
    }

    @Override
    public void preRender(PoseStack poseStack, RiftCouplerBlockEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        RiftDriveBlockEntity drive = animatable.linkedDrive();
        glowing = drive != null && animatable.mustering();
        tint = drive == null ? IDLE_TINT : 0xFF_00_00_00 | drive.tetherColour();
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, RiftCouplerBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean crystal = CRYSTAL_BONE.equals(bone.getName());
        int light = crystal && glowing ? FULL_BRIGHT : packedLight;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, light, crystal && glowing ? OverlayTexture.NO_OVERLAY : packedOverlay,
                crystal ? tint : colour);
    }
}
