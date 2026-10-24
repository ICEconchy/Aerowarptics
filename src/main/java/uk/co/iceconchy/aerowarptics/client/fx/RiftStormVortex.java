package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The storm turning overhead: a slow spiral of violet-dark cloud round the zenith, with a faint light in its eye.
 *
 * <p>Wherever the player goes it is straight above them, hung at infinity like the aurora - see
 * {@link SkyFrame}. That is the point of it: a storm with an eye that followed a ship across the world is a
 * storm the ship is inside, not one it flew under.
 *
 * <p>Darkening, not light, so it goes on {@link AWRenderTypes#RIFT_SKY_SHADE}, blended by alpha; additive light
 * cannot draw dark. The eye is the one bright thing in it, and is drawn additively on
 * {@link AWRenderTypes#RIFT_AURORA} after the cloud. It gathers more slowly than the rest of the storm -
 * weighted by the square of the storm's strength - so it is the last thing to arrive and the first to go.
 *
 * <p>Deterministic in the render clock, as the aurora is: every frame computes the same vortex from the same
 * time, so there is nothing to store and nothing to drift between frames.
 */
@OnlyIn(Dist.CLIENT)
final class RiftStormVortex {

    private static final int RINGS = 16;
    private static final int SECTORS = 64;
    private static final float REACH = 0.94F;
    /** How far from the zenith the vortex reaches at its rim, in radians - about thirty-five degrees. */
    private static final float SPAN = 0.62F;
    /** Radians it turns a tick: a full turn about every two minutes. Anticlockwise seen from below. */
    private static final float TURN_RATE = Mth.TWO_PI / 2400.0F;
    /** How dark the thickest of its cloud is. */
    private static final float PEAK_ALPHA = 0.6F;
    /** The eye's radius, as a share of the vortex's, and how bright its light is. */
    private static final float EYE = 0.12F;
    private static final float EYE_ALPHA = 0.45F;

    private static final int SHADE_COLOUR = 0x1A0B2E;
    private static final int EYE_COLOUR = 0x8A6BFF;

    private RiftStormVortex() {
    }

    static void render(RenderLevelStageEvent event, float partialTick, float storm) {
        if (SkyFrame.hidden(event)) {
            return;
        }
        float gathered = storm * storm;
        if (gathered <= 0.001F) {
            return;
        }
        float time = event.getRenderTick() + partialTick;
        float turn = time * TURN_RATE;
        Matrix4f view = SkyFrame.view(event);
        float radius = SkyFrame.radius(REACH);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

        VertexConsumer shade = buffers.getBuffer(AWRenderTypes.RIFT_SKY_SHADE);
        float[] a = new float[3];
        float[] b = new float[3];
        float[] c = new float[3];
        float[] d = new float[3];
        for (int ring = 0; ring < RINGS; ring++) {
            float inner = ring / (float) RINGS;
            float outer = (ring + 1) / (float) RINGS;
            for (int sector = 0; sector < SECTORS; sector++) {
                float from = sector * Mth.TWO_PI / SECTORS;
                float to = (sector + 1) * Mth.TWO_PI / SECTORS;
                at(a, inner, from, radius);
                at(b, inner, to, radius);
                at(c, outer, to, radius);
                at(d, outer, from, radius);
                vertex(shade, view, a, shade(inner, from, turn, gathered));
                vertex(shade, view, b, shade(inner, to, turn, gathered));
                vertex(shade, view, c, shade(outer, to, turn, gathered));
                vertex(shade, view, d, shade(outer, from, turn, gathered));
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_SKY_SHADE);

        // The eye breathes, slowly and not quite regularly.
        float breath = 0.75F + 0.15F * Mth.sin(time * 0.05F) + 0.1F * Mth.sin(time * 0.013F + 1.3F);
        VertexConsumer light = buffers.getBuffer(AWRenderTypes.RIFT_AURORA);
        int rings = 4;
        for (int ring = 0; ring < rings; ring++) {
            float inner = ring / (float) rings;
            float outer = (ring + 1) / (float) rings;
            int innerColour = SkyFrame.argb(EYE_COLOUR, RiftStormShapes.vortexEye(inner) * EYE_ALPHA * breath * gathered);
            int outerColour = SkyFrame.argb(EYE_COLOUR, RiftStormShapes.vortexEye(outer) * EYE_ALPHA * breath * gathered);
            for (int sector = 0; sector < 24; sector++) {
                float from = sector * Mth.TWO_PI / 24.0F;
                float to = (sector + 1) * Mth.TWO_PI / 24.0F;
                at(a, inner * EYE, from, radius);
                at(b, inner * EYE, to, radius);
                at(c, outer * EYE, to, radius);
                at(d, outer * EYE, from, radius);
                vertex(light, view, a, innerColour);
                vertex(light, view, b, innerColour);
                vertex(light, view, c, outerColour);
                vertex(light, view, d, outerColour);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_AURORA);
    }

    /** A point on the vortex, a share of its radius out from the zenith and at an angle round it. */
    private static void at(float[] out, float share, float angle, float radius) {
        SkyFrame.point(out, angle, Mth.HALF_PI - share * SPAN, radius);
    }

    private static int shade(float share, float angle, float turn, float gathered) {
        return SkyFrame.argb(SHADE_COLOUR, RiftStormShapes.vortexShade(share, angle, turn) * PEAK_ALPHA * gathered);
    }

    /** Position and colour: the whole of both formats this draws into. */
    private static void vertex(VertexConsumer buffer, Matrix4f view, float[] p, int argb) {
        buffer.addVertex(view, p[0], p[1], p[2]).setColor(argb);
    }
}
