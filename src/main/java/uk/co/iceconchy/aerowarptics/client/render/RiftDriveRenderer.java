package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.util.RenderUtil;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.model.RiftDriveModel;
import uk.co.iceconchy.aerowarptics.drive.DriveStructure;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;

/**
 * Draws the Rift Drive.
 *
 * <p>Animation selection happens in the block entity, from state the server sent; this renderer only
 * adds the state-driven colour and the full-bright core so the machine glows harder as it charges.
 *
 * <p>Two things are posed here on top of the artist's clips, for the same reason: a clip loops, and
 * neither of these is a loop. The needle on the lid is a setting - a fixed quarter turn, already eased by
 * the block entity, which the renderer just reads off. And while the drive charges, the rift's size is a
 * reading: the charging clip pulses the core, and the charge so far shrinks that pulse, so a drive at a
 * third full and a drive ready to go look different from across a hangar.
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveRenderer extends GeoBlockRenderer<RiftDriveBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;
    private static final String NEEDLE_BONE = "needle";
    /** The rift at the heart of the drive. The artist's model calls it "cube", and so does its animation file. */
    static final String CORE_BONE = "cube";

    /**
     * How small an empty drive's rift is drawn while it charges, as a share of the size the clip gives it.
     * Full charge draws it at the clip's own size, never larger: the clips already take the core as large as
     * the ring around it allows, which {@code DriveClearanceTest} holds them to.
     */
    static final float EMPTY_CORE_SHARE = 0.6F;

    /** Half a block, in the units GeckoLib's block renderer works in. See {@link #rotateBlock}. */
    private static final double HALF_BLOCK = 0.5D;

    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float intensity;
    private float coreShare = 1.0F;

    public RiftDriveRenderer() {
        super(new RiftDriveModel());
        addRenderLayer(new StormCorruptionLayer(this));
    }

    /**
     * Turns the model to match the block's facing, about the middle of the block rather than the
     * floor of it.
     *
     * <p>GeckoLib rotates about the origin its own {@code preRender} sets up, which is the centre of
     * the block's <em>base</em> &mdash; {@code translate(0.5, 0, 0.5)}. That is right for the four
     * horizontal facings, which only ever spin about the vertical axis through that point, and wrong
     * for the two vertical ones. This model stands on its base plate and reaches sixteen pixels up,
     * so tipping it a quarter turn about the floor swings it clean out of its own block: half sinks
     * below the ground and the rest lands in the neighbour.
     *
     * <p>That is the drive that "sometimes comes out rotated". A {@code DirectionalKineticBlock}
     * takes all six facings, and placing one while looking down at the ground sets {@code FACING} to
     * {@code UP} &mdash; Create's ordinary behaviour for a directional block. Nothing about the
     * placement is wrong; it simply has to draw as a drive lying on its back, and it did not.
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

    /**
     * Draws the drive the size of the frame of Drive Extenders it is built into.
     *
     * <p>One model, scaled evenly and centred on the frame rather than on the drive's own block - a
     * drive sits in the middle of a face, not in the middle of the cube. The scale is GeckoLib's own,
     * which it applies before {@link #rotateBlock}; so that override's half-block lift is half a block
     * of the <em>scaled</em> model, and turns an enlarged drive about the middle of its frame with no
     * change at all.
     *
     * <p>The scale is set on every call rather than once, because one renderer draws every drive in
     * view and the next one may be on its own.
     */
    @Override
    public void render(RiftDriveBlockEntity drive, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        DriveStructure.Frame frame = drive.frame();
        int size = frame.size();
        BlockPos corner = frame.origin().subtract(drive.getBlockPos());
        // GeckoLib adds half a block on x and z to reach the middle of a block's base; this moves that
        // base from the drive's block to the frame's floor, so the half block lands in the middle of it.
        double middle = (size - 1) * 0.5D;
        poseStack.pushPose();
        poseStack.translate(corner.getX() + middle, corner.getY(), corner.getZ() + middle);
        scaleWidth = size;
        scaleHeight = size;
        super.render(drive, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * The box that decides whether the drive is in view: the whole frame, not the drive's own block.
     *
     * <p>Asked of the renderer, not the block entity. NeoForge 21.1 culls a block entity renderer by
     * this method alone, and its default is the one block the drive stands in - the block entity's own
     * {@code getRenderBoundingBox} is Create's hook, which GeckoLib's renderer never reads. Without this a
     * drive built into its frame vanished the moment its own block left the screen, however much of the
     * seven-block model was still in front of the player.
     */
    @Override
    public AABB getRenderBoundingBox(RiftDriveBlockEntity drive) {
        return drive.getRenderBoundingBox();
    }

    /** A seven-block drive seen from across a hangar is still a drive worth drawing. */
    @Override
    public int getViewDistance() {
        return 96;
    }

    /**
     * An extended drive's model reaches into chunk sections its own block is not in, and should not
     * blink out when the section holding the drive is culled while the rest of the frame is plainly in
     * view. Always rather than only when extended: whether a section counts a block entity as off-screen
     * is decided when the section is built, which can be a packet before the frame that would say so.
     *
     * <p>Never aboard a ship, though. Sable culls no section of a hull, so there is nothing to protect
     * against there, and the off-screen list is the one a warp can quietly empty - see {@link DrawnAboard}.
     */
    @Override
    public boolean shouldRenderOffScreen(RiftDriveBlockEntity drive) {
        return DrawnAboard.offScreen(drive);
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
        coreShare = animatable.state() == RiftDriveState.CHARGING
                ? EMPTY_CORE_SHARE + (1.0F - EMPTY_CORE_SHARE) * animatable.chargePartial(partialTick)
                : 1.0F;

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
        // A storm eats the machine, not the rift in it nor the reading on it. Both bones stand alone in the
        // model, so skipping one skips nothing else.
        if (glowing && StormCorruptionLayer.isCorruption(renderType)) {
            return;
        }
        int light = glowing ? FULL_BRIGHT : packedLight;
        // The needle keeps its painted colour. It is a reading, not a mood light, and dimming it with
        // the drive's state would make it unreadable exactly when a pilot most wants to check it.
        int tinted = needle ? colour : glowing ? packColour() : colour;
        // The charge is applied about the core's own pivot rather than written to the bone: the animation
        // pass runs after preRender and sets the core's scale itself, so a scale written there is lost.
        boolean shrunk = CORE_BONE.equals(bone.getName()) && coreShare < 1.0F;
        if (shrunk) {
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, bone);
            poseStack.scale(coreShare, coreShare, coreShare);
            RenderUtil.translateAwayFromPivotPoint(poseStack, bone);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, light, glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, tinted);
        if (shrunk) {
            poseStack.popPose();
        }
    }

    /**
     * Only the core is lit from the state colour. The frame, the housing and the ring are brass and
     * stone, meant to read as metal catching the rift's light, so lighting them too would flatten the
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
