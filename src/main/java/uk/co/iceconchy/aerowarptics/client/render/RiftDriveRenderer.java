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
import uk.co.iceconchy.aerowarptics.client.model.RiftDriveModel;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;

/**
 * Draws the Rift Drive.
 *
 * <p>Animation selection happens in the block entity, from state the server sent; this renderer only
 * adds the state-driven colour and the full-bright core so the machine glows harder as it charges.
 *
 * <p>It also aims the bow needle. That is done here rather than in an animation because the setting
 * is a fixed quarter turn rather than a movement, and because the block entity already eases the
 * angle for the swing - the renderer just reads it off.
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveRenderer extends GeoBlockRenderer<RiftDriveBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;
    private static final String NEEDLE_BONE = "needle";

    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float intensity;

    public RiftDriveRenderer() {
        super(new RiftDriveModel());
    }

    @Override
    public void preRender(PoseStack poseStack, RiftDriveBlockEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        RiftDriveState state = animatable.state();
        int tint = RiftDriveModel.coreColour(state);
        red = ((tint >> 16) & 0xFF) / 255.0F;
        green = ((tint >> 8) & 0xFF) / 255.0F;
        blue = (tint & 0xFF) / 255.0F;
        // The needle is aimed after the animation pass has written the bone's rotation, so it is
        // not overwritten. GeckoLib turns bones anticlockwise, which the heading already accounts for.
        model.getBone(NEEDLE_BONE).ifPresent(bone -> bone.setRotY(animatable.needleAngle(partialTick)));

        intensity = switch (state) {
            case IDLE -> 0.25F;
            case CHARGING -> 0.35F + animatable.chargePartial(partialTick) * 0.6F;
            case CHARGED -> 1.0F;
            case DESTINATION_SELECTED, STABILIZING -> 0.85F + animatable.sequenceProgress() * 0.15F;
            case WARPING -> 1.0F;
            case ARRIVING -> 1.0F - animatable.sequenceProgress() * 0.4F;
            case COOLDOWN -> 0.5F - animatable.sequenceProgress() * 0.4F;
            case ERROR -> 0.4F;
        };
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, RiftDriveBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean needle = NEEDLE_BONE.equals(bone.getName());
        boolean glowing = needle || isGlowingBone(bone.getName());
        int light = glowing ? FULL_BRIGHT : packedLight;
        // The needle keeps its painted colour. It is a reading, not a mood light, and dimming it with
        // the drive's state would make it unreadable exactly when a pilot most wants to check it.
        int tinted = needle ? colour : glowing ? packColour() : colour;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, light, glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, tinted);
    }

    /**
     * Only the core is lit from the state colour. The gimbal rings are machined brass and are meant
     * to read as metal catching the core's light, so lighting them too would flatten the machine into
     * a lamp. The needle is lit separately, at a fixed brightness.
     */
    private static boolean isGlowingBone(String name) {
        return "core".equals(name);
    }

    private int packColour() {
        int a = 0xFF;
        int r = clamp(red * (0.4F + intensity * 0.6F));
        int g = clamp(green * (0.4F + intensity * 0.6F));
        int b = clamp(blue * (0.4F + intensity * 0.6F));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}
