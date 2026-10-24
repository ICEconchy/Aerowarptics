package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.fx.AWRenderTypes;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShatter;
import uk.co.iceconchy.aerowarptics.client.model.RiftBrazierModel;
import uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity;
import uk.co.iceconchy.aerowarptics.rifting.RiftFlame;

/**
 * Draws the Rift Brazier - a basin of essence in an open brass cage - and the rift fire burning up out of it
 * while it is lit.
 *
 * <p>The bowl is the artist's model, drawn as it is: it has nothing that moves or reads out, so all this adds
 * is the fire. Whether the brazier is burning is the fire itself; how much essence is in it is on the Display
 * Link and in the tooltip.
 *
 * <h2>The fire</h2>
 * Drawn the way a rift's face is (see {@link RiftFlame} for the shapes): each tongue a void-dark core on the
 * glass pass, then - additive, over it - a glow creeping in from the edge, a hot torn rim, and a violet haze
 * outside, with motes of light drifting up through the dark as debris drifts in a rift's throat. Crossed
 * upright panes, as vanilla's fire is, drawn here in the brazier's own frame: they burn on a ship's deck as
 * well as on the ground, which a rift held open in the world never could.
 */
@OnlyIn(Dist.CLIENT)
public class RiftBrazierRenderer extends GeoBlockRenderer<RiftBrazierBlockEntity> {

    /** The chute's violet, as the brazier's rift always was; its rim burning nearly white; its haze deeper. */
    private static final int FIRE = 0xB070FF;
    private static final int RIM = 0xF0E6FF;
    private static final int HAZE = 0x7A3CD8;
    /** The dark in a flame's heart, as in a rift's. */
    private static final int VOID = 0x0E0418;
    private static final float VOID_ALPHA = 0.92F;

    /** Rows up each tongue: enough for the rim's tear to ripple, few enough for a deck of braziers. */
    private static final int ROWS = 12;
    private static final int MOTES = 2;
    private static final float MOTE_SIZE = 0.022F;
    private static final int GLOW_LIGHT = 240;

    public RiftBrazierRenderer() {
        super(new RiftBrazierModel());
    }

    @Override
    public boolean shouldRenderOffScreen(RiftBrazierBlockEntity brazier) {
        return DrawnAboard.offScreen(brazier);
    }

    /** The block entity's box, which reaches up over the flames - see {@code RenderBoundsTest}. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RiftBrazierBlockEntity brazier) {
        return brazier.getRenderBoundingBox();
    }

    @Override
    public void render(RiftBrazierBlockEntity brazier, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        super.render(brazier, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        float caught = brazier.flame(partialTick);
        if (caught <= 0.01F) {
            return;
        }
        double time = (brazier.getLevel() == null ? 0L : brazier.getLevel().getGameTime()) + partialTick;
        Matrix4f pose = poseStack.last().pose();
        double[] heights = new double[RiftFlame.TONGUES.size()];
        for (int index = 0; index < heights.length; index++) {
            heights[index] = RiftFlame.height(RiftFlame.TONGUES.get(index), time, caught);
        }
        // The dark first, on the glass pass, then the light over it: additive light cannot draw dark, and the
        // fire has to land on top of the void rather than be dimmed by it - a rift's face and fire, in order.
        VertexConsumer glass = bufferSource.getBuffer(AWRenderTypes.RIFT_SHARD);
        for (int index = 0; index < heights.length; index++) {
            for (int pane = 0; pane < 2; pane++) {
                drawCore(glass, pose, RiftFlame.TONGUES.get(index), pane, time, caught, heights[index]);
            }
        }
        VertexConsumer fire = bufferSource.getBuffer(AWRenderTypes.RIFT_FIRE);
        for (int index = 0; index < heights.length; index++) {
            RiftFlame.Tongue tongue = RiftFlame.TONGUES.get(index);
            double height = heights[index];
            for (int pane = 0; pane < 2; pane++) {
                drawBand(fire, pose, tongue, pane, time, caught, height, RiftFlame.GLOW_FROM, RiftFlame.CORE,
                        FIRE, 0.0F, FIRE, 0.55F);
                drawBand(fire, pose, tongue, pane, time, caught, height, RiftFlame.CORE, 1.0D, FIRE, 0.55F, RIM, 0.9F);
                drawBand(fire, pose, tongue, pane, time, caught, height, 1.0D, RiftFlame.HAZE, HAZE, 0.45F, HAZE, 0.0F);
            }
            drawMotes(fire, pose, tongue, index, time, caught, height);
        }
    }

    /** A tongue's void-dark heart, thinning to nothing over its top quarter so it ends in light. */
    private static void drawCore(VertexConsumer consumer, Matrix4f pose, RiftFlame.Tongue tongue, int pane,
                                 double time, float caught, double height) {
        float alpha = VOID_ALPHA * Math.min(1.0F, caught * 1.5F);
        for (int row = 0; row < ROWS; row++) {
            double s0 = row / (double) ROWS;
            double s1 = (row + 1) / (double) ROWS;
            double w0 = RiftFlame.halfWidth(tongue, s0, time, caught) * RiftFlame.CORE;
            double w1 = RiftFlame.halfWidth(tongue, s1, time, caught) * RiftFlame.CORE;
            float a0 = alpha * (float) RiftFlame.core(s0);
            float a1 = alpha * (float) RiftFlame.core(s1);
            strip(consumer, pose, tongue, pane, time, height, s0, s1, -w0, w0, -w1, w1, VOID, a0, VOID, a0, VOID, a1, VOID, a1);
        }
    }

    /**
     * A band of light either side of a tongue, from one share of its half-width to another, its colour and
     * alpha running from the inner edge to the outer. The rim's tear is in the half-width, so every band
     * wanders with it.
     */
    private static void drawBand(VertexConsumer consumer, Matrix4f pose, RiftFlame.Tongue tongue, int pane,
                                 double time, float caught, double height, double from, double to,
                                 int innerColour, float innerAlpha, int outerColour, float outerAlpha) {
        float inner = innerAlpha * caught;
        float outer = outerAlpha * caught;
        for (int row = 0; row < ROWS; row++) {
            double s0 = row / (double) ROWS;
            double s1 = (row + 1) / (double) ROWS;
            double w0 = RiftFlame.halfWidth(tongue, s0, time, caught);
            double w1 = RiftFlame.halfWidth(tongue, s1, time, caught);
            for (int side = -1; side <= 1; side += 2) {
                strip(consumer, pose, tongue, pane, time, height, s0, s1,
                        side * w0 * from, side * w0 * to, side * w1 * from, side * w1 * to,
                        innerColour, inner, outerColour, outer, innerColour, inner, outerColour, outer);
            }
        }
    }

    /**
     * Motes of light drifting up through a tongue's dark, as broken glass drifts in a rift's throat - each on
     * its own slow climb, brightest halfway, on the tongue's first pane.
     */
    private static void drawMotes(VertexConsumer consumer, Matrix4f pose, RiftFlame.Tongue tongue, int index,
                                  double time, float caught, double height) {
        for (int mote = 0; mote < MOTES; mote++) {
            double climb = (time * 0.012D + RiftShatter.noise(index, 70 + mote) + mote * 0.5D) % 1.0D;
            double s = climb * 0.85D;
            double across = (RiftShatter.noise(index * 7 + mote, (int) (time * 0.012D + mote * 0.5D)) - 0.5D)
                    * RiftFlame.halfWidth(tongue, s, time, caught) * RiftFlame.CORE;
            double lateral = across + RiftFlame.sway(s, time, tongue.phase());
            float x = (float) (tongue.x() + lateral);
            float y = (float) (RiftFlame.FLOOR + s * height);
            float z = (float) tongue.z();
            float alpha = (float) Math.sin(Math.PI * climb) * 0.9F * caught;
            vertex(consumer, pose, x, y - MOTE_SIZE, z, RIM, alpha);
            vertex(consumer, pose, x + MOTE_SIZE, y, z, RIM, alpha);
            vertex(consumer, pose, x, y + MOTE_SIZE, z, RIM, alpha);
            vertex(consumer, pose, x - MOTE_SIZE, y, z, RIM, alpha);
        }
    }

    /**
     * One quad of a tongue on one of its two panes, between two heights and two pairs of offsets across it,
     * each corner coloured on its own. Pane 0 runs along x, pane 1 along z; the sway leans both.
     */
    private static void strip(VertexConsumer consumer, Matrix4f pose, RiftFlame.Tongue tongue, int pane, double time,
                              double height, double s0, double s1, double a0, double b0, double a1, double b1,
                              int colourA0, float alphaA0, int colourB0, float alphaB0,
                              int colourA1, float alphaA1, int colourB1, float alphaB1) {
        double sway0 = RiftFlame.sway(s0, time, tongue.phase());
        double sway1 = RiftFlame.sway(s1, time, tongue.phase());
        float y0 = (float) (RiftFlame.FLOOR + s0 * height);
        float y1 = (float) (RiftFlame.FLOOR + s1 * height);
        corner(consumer, pose, tongue, pane, sway0 + a0, y0, colourA0, alphaA0);
        corner(consumer, pose, tongue, pane, sway0 + b0, y0, colourB0, alphaB0);
        corner(consumer, pose, tongue, pane, sway1 + b1, y1, colourB1, alphaB1);
        corner(consumer, pose, tongue, pane, sway1 + a1, y1, colourA1, alphaA1);
    }

    private static void corner(VertexConsumer consumer, Matrix4f pose, RiftFlame.Tongue tongue, int pane,
                               double across, float y, int colour, float alpha) {
        float x = (float) (tongue.x() + (pane == 0 ? across : 0.0D));
        float z = (float) (tongue.z() + (pane == 1 ? across : 0.0D));
        vertex(consumer, pose, x, y, z, colour, alpha);
    }

    /**
     * The one place a flame's vertex is written. Both the glass and the fire are the beacon beam's format, so a
     * texture coordinate, a light level and a normal go with every colour - constants that say nothing, and
     * without which the buffer throws mid-frame. See {@code RiftVertexFormatTest}.
     */
    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int colour,
                               float alpha) {
        consumer.addVertex(pose, x, y, z)
                .setColor((Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (colour & 0xFFFFFF))
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
