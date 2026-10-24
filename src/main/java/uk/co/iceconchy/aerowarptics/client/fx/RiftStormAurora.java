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
 * Curtains of rift light hanging in a storm sky.
 *
 * <p>Three of them, each a ribbon of columns strung along an arc of the sky, bright at the hem and
 * fading to nothing above: rift-fire cyan at the bottom through violet to magenta. The hem ripples, the
 * curtains drift slowly round the sky in opposite directions, and each column shimmers on its own beat.
 * Additive, so by day against a bright sky there is little to see - a trace of colour in a storm that
 * has already darkened it - and by night it is the brightest thing up there. That is how an aurora
 * behaves, and it means the storm needs no rule of its own about when to show one.
 *
 * <h2>Why it is drawn where it is</h2>
 * At a fixed distance from the camera in the sky's own frame - turned with the view, never moved with the
 * player. That is what puts it at infinity: walk a thousand blocks and it has not come any closer, exactly
 * like the stars. The distance is just inside the far plane, beyond anything the world draws, so a depth
 * test lets it through only where the sky shows and every hill, tree and hull stands in front of it.
 *
 * <p>It used to be drawn first, straight after the sky, where there was nothing to stand in front of it and
 * no test was needed. But the clouds are drawn after everything solid, and painted over it there just as they
 * paint over the stars - so in a storm, which is when there is an aurora, it was mostly behind cloud. Now it
 * is drawn after the clouds, tested against the depth the world had before them ({@link CloudlessDepth}),
 * so it hangs in front of the clouds and behind everything else. Under Fabulous graphics, which keeps the
 * clouds in a layer of its own and has no such depth to swap in, it is still drawn straight after the sky,
 * and the clouds still cover it.
 *
 * <p>Deterministic in the render clock, not rolled: every frame computes the same curtains from the same
 * time, so there is nothing to store and nothing to drift between frames.
 */
@OnlyIn(Dist.CLIENT)
final class RiftStormAurora {

    private static final int BANDS = 3;
    /** Columns per curtain. Enough that a ripple reads as a curve rather than a zigzag. */
    private static final int COLUMNS = 28;
    /**
     * How far out the curtains hang, as a share of the distance to the far plane. Past every block the
     * world draws - terrain stops a little beyond the render distance, and the far plane is four times
     * that - and still inside the plane, which would cut them off. In front of the storm's tears and its
     * vortex, behind its streaks - see {@link SkyFrame}.
     */
    private static final float REACH = 0.9F;
    /** Brightest the hem gets, at full storm, before shimmer. */
    private static final float PEAK_ALPHA = 0.55F;

    /** Where each curtain starts, round the sky, in radians - spread so no two overlap at first. */
    private static final float[] BEARING = {0.3F, 2.4F, 4.3F};
    /** How wide each curtain spans, in radians. */
    private static final float[] SPAN = {1.25F, 0.95F, 1.1F};
    /** How high each curtain's hem hangs, in radians above the horizon. */
    private static final float[] HEM = {0.40F, 0.52F, 0.46F};

    private static final int HEM_COLOUR = 0x4AD9C7;
    private static final int MID_COLOUR = 0x8A6BFF;
    private static final int CROWN_COLOUR = 0xC060FF;

    private RiftStormAurora() {
    }

    /**
     * Draws the curtains, at the stage {@link RiftStormSky} calls this from: {@code AFTER_WEATHER} with the
     * cloudless depth swapped in, or {@code AFTER_SKY} under Fabulous graphics.
     */
    static void render(RenderLevelStageEvent event, float partialTick, float storm) {
        if (SkyFrame.hidden(event)) {
            return;
        }

        float time = event.getRenderTick() + partialTick;
        float radius = SkyFrame.radius(REACH);
        Matrix4f view = SkyFrame.view(event);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_AURORA);
        float[] hem = new float[3];
        float[] mid = new float[3];
        float[] crown = new float[3];
        float[] lastHem = new float[3];
        float[] lastMid = new float[3];
        float[] lastCrown = new float[3];

        for (int band = 0; band < BANDS; band++) {
            // Neighbouring curtains drift in opposite directions, about a full turn an in-game day.
            float drift = time * 0.00026F * (band % 2 == 0 ? 1.0F : -1.0F);
            float height = 0.34F + 0.06F * Mth.sin(time * 0.004F + band * 1.7F);
            float lastAlpha = 0.0F;

            for (int column = 0; column <= COLUMNS; column++) {
                float t = column / (float) COLUMNS;
                float bearing = BEARING[band] + drift + SPAN[band] * (t - 0.5F)
                        + 0.05F * Mth.sin(time * 0.02F + t * 9.0F + band * 3.0F);
                float elevation = HEM[band] + 0.045F * Mth.sin(time * 0.015F + t * 6.5F + band);
                // Faded to nothing at both ends, so a curtain has edges rather than being cut off.
                float envelope = Mth.sin(t * Mth.PI);
                float shimmer = 0.55F + 0.45F * Mth.sin(time * 0.09F + t * 23.0F + band * 5.0F);
                float alpha = PEAK_ALPHA * storm * envelope * shimmer;

                SkyFrame.point(hem, bearing, elevation, radius);
                SkyFrame.point(mid, bearing, elevation + height * 0.35F, radius);
                SkyFrame.point(crown, bearing, elevation + height, radius);

                if (column > 0) {
                    // Hem to middle: cyan into violet, at the hem's brightness.
                    quad(buffer, view, lastHem, hem, mid, lastMid,
                            argb(HEM_COLOUR, lastAlpha), argb(HEM_COLOUR, alpha),
                            argb(MID_COLOUR, alpha * 0.7F), argb(MID_COLOUR, lastAlpha * 0.7F));
                    // Middle to crown: violet into magenta, thinning away to nothing.
                    quad(buffer, view, lastMid, mid, crown, lastCrown,
                            argb(MID_COLOUR, lastAlpha * 0.7F), argb(MID_COLOUR, alpha * 0.7F),
                            argb(CROWN_COLOUR, 0.0F), argb(CROWN_COLOUR, 0.0F));
                }
                System.arraycopy(hem, 0, lastHem, 0, 3);
                System.arraycopy(mid, 0, lastMid, 0, 3);
                System.arraycopy(crown, 0, lastCrown, 0, 3);
                lastAlpha = alpha;
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_AURORA);
    }

    private static void quad(VertexConsumer buffer, Matrix4f view,
                             float[] a, float[] b, float[] c, float[] d,
                             int colourA, int colourB, int colourC, int colourD) {
        vertex(buffer, view, a, colourA);
        vertex(buffer, view, b, colourB);
        vertex(buffer, view, c, colourC);
        vertex(buffer, view, d, colourD);
    }

    /** Position and colour: the whole of this render type's format. */
    private static void vertex(VertexConsumer buffer, Matrix4f view, float[] p, int argb) {
        buffer.addVertex(view, p[0], p[1], p[2]).setColor(argb);
    }

    private static int argb(int rgb, float alpha) {
        return SkyFrame.argb(rgb, alpha);
    }
}
