package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.fx.CollapseSpectacle;
import uk.co.iceconchy.aerowarptics.client.model.CollapseCradleModel;
import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.collapse.CradleWorks;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws the Collapse Cradle - the whole three-block machine, from its master - and what is in it.
 *
 * <p>Nothing on it is animated; everything that moves is posed here, every frame, from the ritual's own clock
 * through {@link CradleWorks}, after which GeckoLib's animation pass leaves a bone it was told to turn alone.
 * The sun gear turns with the shaft at Create's own rate and the pistons pump off the same crank; the lock ring
 * follows the ritual's progress; the rings are the one thing a client integrates, in the block entity's
 * {@link CradleWorks.Rings}, because their speed changes as it winds. The model is the artist's.
 *
 * <p>The Rift Core, the Scar Heart and the offerings are drawn as the items themselves - a picture of what is
 * on the altar is the thing a player walking up to it needs to read - and where each is, through a ritual, is
 * {@link CollapseChoreography}'s: the core held in the arms and lifted into the middle of the rings, the heart
 * beating in its cup, each offering lifting off its depot and flying into orbit round the machine, and all of
 * them falling into the point at the end. A settled core comes back down out of the point to hang over the heart.
 *
 * <p>A fold has a smaller scene of its own: the stack rises, a small rift draws it up into the rings and folds it,
 * and what it made comes back down. And a collapse heading for trouble shows it on the machine as well as in the
 * rift - the whole of it shudders, the outer ring rocks on its rollers, the sun gear slips and the pistons misfire
 * ({@link CradleWorks}, "failing").
 *
 * <p>The channels cut into the stone and the bronze light up through {@link CradleGlowLayer}. The hole, the disc
 * and the column round them are not drawn here - see {@link CollapseSpectacle} - nor the fold's rift, the sparks
 * or the smoke: see {@code CradleEffects}.
 */
@OnlyIn(Dist.CLIENT)
public class CollapseCradleRenderer extends GeoBlockRenderer<CollapseCradleBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;
    /** The core is drawn {@link CradleWorks#CORE_HALF} wide each way, in blocks: inside the inner ring's hole. */
    private static final float CORE_SCALE = (float) (CradleWorks.CORE_HALF * 2.0D / 16.0D);
    /** Small enough that the heart beating in its cup never reaches the middle ring's sweep. */
    private static final float HEART_SCALE = 0.32F;
    /** The shaft's angle is kept to ten turns, where the sun gear and the crank both come round to the start. */
    private static final long SHAFT_CYCLE = 12_000L;

    public CollapseCradleRenderer() {
        super(new CollapseCradleModel());
        addRenderLayer(new CradleGlowLayer(this));
    }

    @Override
    public boolean shouldRenderOffScreen(CollapseCradleBlockEntity cradle) {
        return DrawnAboard.offScreen(cradle);
    }

    /** The block entity's box: the whole machine, and the orbit while a ritual runs - see {@code RenderBoundsTest}. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(CollapseCradleBlockEntity cradle) {
        return cradle.getRenderBoundingBox();
    }

    @Override
    public void preRender(PoseStack poseStack, CollapseCradleBlockEntity cradle, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        pose(model, cradle, partialTick);
        if (!isReRender) {
            // The whole machine shudders as it fails. Not on a re-render: the glow layer draws over a pose stack
            // already moved, and moving it twice would slide the light off the carvings it lights.
            double[] shudder = CradleWorks.shudder(strain(cradle), tick(cradle));
            poseStack.translate(shudder[0] / 16.0D, shudder[1] / 16.0D, shudder[2] / 16.0D);
        }
        super.preRender(poseStack, cradle, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** How badly the collapse under way is failing, from where its instability is heading. */
    static double strain(CollapseCradleBlockEntity cradle) {
        return cradle.running() ? CradleWorks.strain(cradle.unrest()) : 0.0D;
    }

    private static long tick(CollapseCradleBlockEntity cradle) {
        return cradle.getLevel() == null ? 0L : cradle.getLevel().getGameTime();
    }

    /** Every moving part, where the ritual - or the fold - has it now, and how it fails. */
    private static void pose(BakedGeoModel model, CollapseCradleBlockEntity cradle, float partialTick) {
        boolean running = cradle.running();
        boolean folding = cradle.folding();
        double progress = cradle.progress(partialTick);
        double fold = folding ? cradle.foldProgress(partialTick) : -1.0D;
        float after = cradle.after(partialTick);
        long tick = tick(cradle);
        double time = tick + partialTick;
        double strain = strain(cradle);
        double shaft = shaftAngle(cradle, partialTick) + CradleWorks.slip(strain, time);

        bone(model, "sun", bone -> bone.setRotY((float) (shaft * CradleWorks.SUN_RATIO)));
        double work = folding ? 0.6D : CradleWorks.work(progress, running);
        for (int i = 0; i < 4; i++) {
            int index = i;
            double pumping = Math.min(1.0D, work + CradleWorks.misfire(strain, tick, i));
            bone(model, "piston_" + i, bone -> bone.setPosY((float) CradleWorks.piston(index, shaft, pumping)));
        }

        CradleWorks.Rings rings = cradle.rings();
        double[] rock = CradleWorks.rock(strain, time);
        bone(model, "ring_outer", bone -> {
            bone.setRotY((float) rings.yaw(partialTick));
            bone.setRotX((float) rock[0]);
            bone.setRotZ((float) rock[1]);
        });
        bone(model, "ring_middle", bone -> bone.setRotX((float) rings.pitch(partialTick)));
        bone(model, "ring_inner", bone -> bone.setRotZ((float) rings.roll(partialTick)));

        double rattle = strain * Math.toRadians(3.0D) * Math.sin(time * 3.1D);
        bone(model, "lock", bone -> bone.setRotY((float) (CradleWorks.lock(progress, running, after) + rattle)));
    }

    private static void bone(BakedGeoModel model, String name, Consumer<GeoBone> pose) {
        model.getBone(name).ifPresent(pose);
    }

    @Override
    public void render(CollapseCradleBlockEntity cradle, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        super.render(cradle, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        float time = (cradle.getLevel() == null ? 0 : cradle.getLevel().getGameTime()) + partialTick;
        poseStack.pushPose();
        double[] shudder = CradleWorks.shudder(strain(cradle), tick(cradle));
        poseStack.translate(shudder[0] / 16.0D, shudder[1] / 16.0D, shudder[2] / 16.0D);
        drawItems(cradle, partialTick, time, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    /** What is on the altar: in a ritual, in a fold, coming down after one, or at rest. */
    private static void drawItems(CollapseCradleBlockEntity cradle, float partialTick, float time, PoseStack poseStack,
                                  MultiBufferSource bufferSource, int packedLight) {
        if (cradle.running()) {
            drawRitual(cradle, partialTick, time, poseStack, bufferSource, packedLight);
            return;
        }
        if (cradle.folding()) {
            drawFold(cradle, partialTick, time, poseStack, bufferSource, packedLight);
            return;
        }
        drawItem(cradle, cradle.heart(), poseStack, bufferSource, packedLight, 0.0D, CollapseChoreography.CUP, 0.0D,
                HEART_SCALE, time * 1.5F);
        float after = cradle.after(partialTick);
        if (cradle.catching()) {
            // Coming down out of the point it was made in, growing into itself as it goes, into waiting hands.
            double height = CradleWorks.settledCore(after) / 16.0D;
            drawItem(cradle, cradle.presented(), poseStack, bufferSource, FULL_BRIGHT, 0.0D, height, 0.0D,
                    CORE_SCALE * Math.min(1.0F, after * 6.0F), time * 12.0F * (1.0F - after) + time);
            return;
        }
        drawItem(cradle, cradle.presented(), poseStack, bufferSource, packedLight, 0.0D, CollapseChoreography.HOVER, 0.0D,
                CORE_SCALE, time);
    }

    /**
     * A fold: the stack lifted and drawn up into the rift, shrinking into it, and what it made growing out and
     * coming back down. Lit by the rift while it is in it. The cup keeps what it holds until the fold is done.
     */
    private static void drawFold(CollapseCradleBlockEntity cradle, float partialTick, float time, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight) {
        double f = cradle.foldProgress(partialTick);
        drawItem(cradle, cradle.heart(), poseStack, bufferSource, packedLight, 0.0D, CollapseChoreography.CUP, 0.0D,
                HEART_SCALE, time * 1.5F);
        ItemStack shown = CradleWorks.folded(f) ? cradle.foldResult() : cradle.core();
        boolean inRift = f > CradleWorks.FOLD_LIFTED && f < CradleWorks.FOLD_DOWN;
        float spin = inRift ? time * (float) (6.0D + 30.0D * Math.sin(Math.PI * f)) : time;
        drawItem(cradle, shown, poseStack, bufferSource, inRift ? FULL_BRIGHT : packedLight, 0.0D,
                CradleWorks.foldHeight(f) / 16.0D, 0.0D, (float) (CORE_SCALE * CradleWorks.foldScale(f)), spin);
    }

    /** The altar mid-ritual: the heart beating, the core lifted into the hole, and the offerings in orbit. */
    private static void drawRitual(CollapseCradleBlockEntity cradle, float partialTick, float time, PoseStack poseStack,
                                   MultiBufferSource bufferSource, int packedLight) {
        double progress = cradle.progress(partialTick);
        float beat = CollapseSpectacle.beat(cradle.getBlockPos(), partialTick);
        drawItem(cradle, cradle.heart(), poseStack, bufferSource, beat > 0.3F ? FULL_BRIGHT : packedLight, 0.0D,
                CollapseChoreography.heartHeight(progress), 0.0D,
                (float) (HEART_SCALE * CollapseChoreography.heartScale(progress)) * (1.0F + 0.25F * beat), time * 1.5F);
        // Turning slowly while the arms hold it, then faster and faster once the rift has it.
        float spin = progress < CradleWorks.RELEASED ? time
                : time * (float) CollapseChoreography.lerp(progress, 9.0D, 30.0D);
        drawItem(cradle, cradle.core(), poseStack, bufferSource, FULL_BRIGHT, 0.0D,
                CollapseChoreography.coreHeight(progress), 0.0D,
                (float) (CORE_SCALE * CollapseChoreography.coreScale(progress)), spin);

        List<ItemStack> offerings = cradle.offerings();
        List<net.minecraft.core.BlockPos> depots = cradle.offeringDepots();
        int count = Math.min(offerings.size(), depots.size());
        if (count == 0) {
            return;
        }
        double[][] from = new double[count][];
        for (int i = 0; i < count; i++) {
            from[i] = new double[]{depots.get(i).getX() - cradle.getBlockPos().getX(),
                    depots.get(i).getZ() - cradle.getBlockPos().getZ()};
        }
        double[] slots = CollapseChoreography.slotAngles(from);
        double[] tilt = CollapseSpectacle.tilt(cradle.getBlockPos(), partialTick);
        float scale = (float) (0.5D * CollapseChoreography.offeringScale(progress));
        for (int i = 0; i < count; i++) {
            double[] at = CollapseChoreography.offering(progress, from[i], slots[i], tilt[0], tilt[1]);
            // Lit by nothing but the rift once it is off its depot: it glows its way into the orbit.
            int light = progress < 0.02D ? packedLight : FULL_BRIGHT;
            drawItem(cradle, offerings.get(i), poseStack, bufferSource, light, at[0], at[1], at[2], scale,
                    time * 6.0F + i * 45.0F);
        }
    }

    /** One item, at a place relative to the middle of the master's floor, turning about its own upright. */
    private static void drawItem(CollapseCradleBlockEntity cradle, ItemStack stack, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int light, double x, double height, double z,
                                 float scale, float spin) {
        if (stack.isEmpty() || scale <= 0.005F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D + x, height, 0.5D + z);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin % 360.0F));
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, cradle.getLevel(), (int) cradle.getBlockPos().asLong());
        poseStack.popPose();
    }

    /**
     * Radians the shaft has turned, from the cradle's own speed - an RPM is a turn a minute, 1200 ticks - as
     * Create turns its own shafts. Counted in doubles over a ten-turn cycle of the game's clock: a float of a
     * long game's ticks cannot tell one frame from the next, and ten turns is where the gears and the pistons'
     * crank all come round to where they started, so the cycle's wrap is never seen.
     */
    static double shaftAngle(CollapseCradleBlockEntity cradle, float partialTick) {
        if (cradle.getLevel() == null) {
            return 0.0D;
        }
        double ticks = Math.floorMod(cradle.getLevel().getGameTime(), SHAFT_CYCLE) + (double) partialTick;
        return ticks * cradle.getSpeed() / 1200.0D * Math.PI * 2.0D;
    }
}
