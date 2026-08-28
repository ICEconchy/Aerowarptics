package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
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
 * <p>Two things are posed here rather than animated, for the same reason: a clip loops, and neither
 * of these is a loop. The bow needle is a setting - a fixed quarter turn, already eased by the block
 * entity, which the renderer just reads off. The rift's size is a reading: it grows with the charge,
 * so a drive at a third full and a drive ready to go look different from across a hangar, which no
 * amount of playing the charging clip faster can say.
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveRenderer extends GeoBlockRenderer<RiftDriveBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;
    private static final String NEEDLE_BONE = "needle";
    private static final String CORE_BONE = "core";

    /**
     * How large the rift is ever drawn, as a multiple of the modelled knot.
     *
     * <p>Shared with {@code tools/rift_drive_model.py}, which allows the core this much room when it
     * checks that nothing in the model passes through anything else. Raising it here without raising
     * it there puts the rift through the gear that is supposed to be turning around it.
     */
    private static final float MAX_CORE_SCALE = 1.5F;

    /** Half a block, in the units GeckoLib's block renderer works in. See {@link #rotateBlock}. */
    private static final double HALF_BLOCK = 0.5D;

    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float intensity;

    public RiftDriveRenderer() {
        super(new RiftDriveModel());
    }

    /**
     * Turns the model to match the block's facing, about the middle of the block rather than the
     * floor of it.
     *
     * <p>GeckoLib rotates about the origin its own {@code preRender} sets up, which is the centre of
     * the block's <em>base</em> &mdash; {@code translate(0.5, 0, 0.5)}. That is right for the four
     * horizontal facings, which only ever spin about the vertical axis through that point, and wrong
     * for the two vertical ones. This model stands on its base plate and reaches fifteen pixels up,
     * so tipping it a quarter turn about the floor swings it clean out of its own block: half sinks
     * below the ground and the rest lands in the neighbour.
     *
     * <p>That is the drive that "sometimes comes out rotated". A {@code DirectionalKineticBlock}
     * takes all six facings, and placing one while looking down at the ground sets {@code FACING} to
     * {@code UP} &mdash; Create's ordinary behaviour, and correct, because the shaft enters along the
     * axis the drive faces. Nothing about the placement is wrong; it simply has to draw as a drive
     * lying on its back, and it did not.
     *
     * <p>Lifting the origin half a block, turning, and dropping it back puts the pivot at the block's
     * centre, where a quarter turn keeps every part of the model inside the block it belongs to.
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

        float coreScale = coreScale(animatable, partialTick);
        model.getBone(CORE_BONE).ifPresent(bone -> {
            bone.setScaleX(coreScale);
            bone.setScaleY(coreScale);
            bone.setScaleZ(coreScale);
        });

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
     * How big the rift in the middle of the tesseract is drawn, this frame.
     *
     * <p>A drive with no charge in it still has a rift - a seed of one, the machine idling - and a
     * charged drive has one straining against the bars of the inner cage. Everything between is the
     * charge itself, which is the one number a pilot wants to read off the machine rather than out of
     * a screen.
     */
    private static float coreScale(RiftDriveBlockEntity drive, float partialTick) {
        float scale = switch (drive.state()) {
            case IDLE -> 0.3F;
            case CHARGING -> 0.3F + drive.chargePartial(partialTick) * 0.9F;
            case CHARGED -> 1.3F;
            case DESTINATION_SELECTED, STABILIZING -> 1.3F + drive.sequenceProgress() * 0.2F;
            case WARPING -> MAX_CORE_SCALE;
            case ARRIVING -> MAX_CORE_SCALE - drive.sequenceProgress() * 0.7F;
            case COOLDOWN -> 0.8F - drive.sequenceProgress() * 0.5F;
            case ERROR -> 0.35F;
        };
        return Math.min(scale, MAX_CORE_SCALE);
    }

    /**
     * Only the core is lit from the state colour. The gears and the two cages are machined metal and
     * are meant to read as metal catching the rift's light, so lighting them too would flatten the
     * machine into a lamp. The needle is lit separately, at a fixed brightness.
     */
    private static boolean isGlowingBone(String name) {
        return CORE_BONE.equals(name);
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
