package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Tears in a storm sky: the rift the storm is made of, showing through.
 *
 * <p>A tear starts as a bright seam somewhere in the sky, parts slowly along its length to show the End's
 * starfield beyond it, hangs open a while, and heals back to a seam that fades. Two at most at once, and only
 * once the storm has gathered past half its strength - they are the storm at its worst, not its weather.
 *
 * <p>The inside is the End's own sky, on {@link AWRenderTypes#RIFT_END_SKY}: the same surface an Ender rift's
 * face is painted with, projected from the screen, so a tear looks through to the same place a rift does. That
 * program takes no fog, which is what lets it hang at the far end of the view at all. It is opaque and writes
 * depth, and nothing can make it fade; a tear only ever closes, which is also how one leaves a clearing storm -
 * its opening is scaled by the storm's strength, so the five seconds a storm takes to clear shut every tear.
 * The edges are rift light on {@link AWRenderTypes#RIFT_AURORA}: a violet glow outside each lip and a white
 * core along it, which with the tear shut is the seam.
 *
 * <p>Hung furthest out of everything the storm draws at infinity (see {@link SkyFrame}), so the aurora and the
 * vortex pass in front of a tear rather than behind it, and drawn first of them for the same reason: it writes
 * depth, and what is drawn over it afterwards is nearer.
 */
@OnlyIn(Dist.CLIENT)
final class RiftStormTears {

    private static final int MOST = 2;
    private static final int LEAST_LIFE = 200;
    private static final int MOST_LIFE = 500;
    /** Fewest and most ticks between tears at full strength. */
    private static final int LEAST_GAP = 160;
    private static final int MOST_GAP = 600;
    /** How much storm there must be before the sky tears at all. */
    private static final float TEARING = 0.5F;
    /** Points along a tear. */
    private static final int POINTS = 14;
    private static final float REACH = 0.96F;

    /** Widest a tear opens, as a share of its length. */
    private static final float WIDEST = 0.11F;
    /** Width of the violet glow outside each lip, and of the white core along it, in radians of sky. */
    private static final float HALO = 0.018F;
    private static final float CORE = 0.0035F;

    private static final int CORE_COLOUR = 0xF0E6FF;
    private static final int HALO_COLOUR = 0x8A6BFF;

    private static final List<Tear> TEARS = new ArrayList<>();
    private static int countdown = LEAST_GAP;

    private RiftStormTears() {
    }

    static void clear() {
        TEARS.clear();
        countdown = LEAST_GAP;
    }

    /** Ages every tear, and now and then tears another. Called every tick, storm or none, so old tears finish. */
    static void tick(RandomSource random, float weather) {
        TEARS.removeIf(tear -> ++tear.age >= tear.life);
        if (weather <= 0.0F || --countdown > 0) {
            return;
        }
        int gap = LEAST_GAP + random.nextInt(MOST_GAP - LEAST_GAP + 1);
        countdown = Math.round(gap / Math.max(0.2F, weather));
        if (TEARS.size() < MOST && weather >= TEARING) {
            TEARS.add(roll(random));
        }
    }

    private static Tear roll(RandomSource random) {
        Tear tear = new Tear();
        tear.life = LEAST_LIFE + random.nextInt(MOST_LIFE - LEAST_LIFE + 1);
        tear.bearing = random.nextFloat() * Mth.TWO_PI;
        tear.elevation = (15.0F + random.nextFloat() * 45.0F) * Mth.DEG_TO_RAD;
        tear.length = 0.25F + random.nextFloat() * 0.35F;
        // Leaning, never flat along the horizon nor straight up: a crack, not a line ruled on the sky.
        tear.tilt = (random.nextBoolean() ? 1.0F : -1.0F) * (0.3F + random.nextFloat() * 0.9F);
        float jag = tear.length / POINTS * 0.6F;
        for (int point = 0; point < POINTS; point++) {
            boolean end = point == 0 || point == POINTS - 1;
            tear.jag[point] = end ? 0.0F : (random.nextFloat() - 0.5F) * 2.0F * jag;
            tear.spread[point] = 0.7F + random.nextFloat() * 0.6F;
        }
        return tear;
    }

    static void render(RenderLevelStageEvent event, float partialTick, float storm) {
        if (TEARS.isEmpty() || SkyFrame.hidden(event)) {
            return;
        }
        Matrix4f view = SkyFrame.view(event);
        float radius = SkyFrame.radius(REACH);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float closing = Math.min(1.0F, storm * 1.5F);

        // The inside first, as a batch of its own: the End's surface is its own render type, and asking for the
        // light's afterwards ends it.
        VertexConsumer inside = buffers.getBuffer(AWRenderTypes.RIFT_END_SKY);
        float[] a = new float[3];
        float[] b = new float[3];
        float[] c = new float[3];
        float[] d = new float[3];
        for (Tear tear : TEARS) {
            float progress = (tear.age + partialTick) / tear.life;
            float open = RiftStormShapes.tearOpening(progress) * closing;
            if (open <= 0.0F) {
                continue;
            }
            for (int point = 0; point + 1 < POINTS; point++) {
                float w0 = width(tear, point, open);
                float w1 = width(tear, point + 1, open);
                tear.at(a, point, w0, radius);
                tear.at(b, point + 1, w1, radius);
                tear.at(c, point + 1, -w1, radius);
                tear.at(d, point, -w0, radius);
                endVertex(inside, view, a);
                endVertex(inside, view, b);
                endVertex(inside, view, c);
                endVertex(inside, view, d);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_END_SKY);

        VertexConsumer light = buffers.getBuffer(AWRenderTypes.RIFT_AURORA);
        for (Tear tear : TEARS) {
            float progress = (tear.age + partialTick) / tear.life;
            float lit = RiftStormShapes.tearSeam(progress) * closing;
            if (lit <= 0.001F) {
                continue;
            }
            float open = RiftStormShapes.tearOpening(progress) * closing;
            // Each lip's halo spreads outward from it; with the tear shut the two lips lie on the seam and
            // their cores sit together along it.
            for (float side : new float[] {1.0F, -1.0F}) {
                lip(light, view, tear, radius, open, side, HALO, HALO_COLOUR, lit * 0.55F, a, b, c, d);
                lip(light, view, tear, radius, open, side, CORE, CORE_COLOUR, lit, a, b, c, d);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_AURORA);
    }

    private static float width(Tear tear, int point, float open) {
        float along = point / (float) (POINTS - 1);
        return RiftStormShapes.tearWidth(along) * tear.spread[point] * WIDEST * tear.length * open;
    }

    /** One band of light along one lip, bright at the lip and nothing at its outer edge. */
    private static void lip(VertexConsumer buffer, Matrix4f view, Tear tear, float radius, float open,
                            float side, float spread, int rgb, float alpha,
                            float[] a, float[] b, float[] c, float[] d) {
        int bright = SkyFrame.argb(rgb, alpha);
        int none = SkyFrame.argb(rgb, 0.0F);
        for (int point = 0; point + 1 < POINTS; point++) {
            // The light tapers with the tear, so the ends of a seam fade out rather than stopping square.
            float taper0 = taper(point);
            float taper1 = taper(point + 1);
            float w0 = side * width(tear, point, open);
            float w1 = side * width(tear, point + 1, open);
            tear.at(a, point, w0, radius);
            tear.at(b, point + 1, w1, radius);
            tear.at(c, point + 1, w1 + side * spread * taper1, radius);
            tear.at(d, point, w0 + side * spread * taper0, radius);
            lightVertex(buffer, view, a, taper0 < 0.05F ? none : bright);
            lightVertex(buffer, view, b, taper1 < 0.05F ? none : bright);
            lightVertex(buffer, view, c, none);
            lightVertex(buffer, view, d, none);
        }
    }

    private static float taper(int point) {
        return (float) Math.sqrt(RiftStormShapes.tearWidth(point / (float) (POINTS - 1)));
    }

    /** The End's surface: plain position, the whole of {@code RIFT_END_SKY}'s format. */
    private static void endVertex(VertexConsumer buffer, Matrix4f view, float[] p) {
        buffer.addVertex(view, p[0], p[1], p[2]);
    }

    /** Position and colour: the whole of {@code RIFT_AURORA}'s format. */
    private static void lightVertex(VertexConsumer buffer, Matrix4f view, float[] p, int argb) {
        buffer.addVertex(view, p[0], p[1], p[2]).setColor(argb);
    }

    private static final class Tear {
        private final float[] jag = new float[POINTS];
        private final float[] spread = new float[POINTS];
        private float bearing;
        private float elevation;
        private float length;
        private float tilt;
        private int life;
        private float age;

        /**
         * The point a share of the way along this tear, {@code across} radians to one side of its crack, on the
         * sky {@code radius} out. Worked in the sky's own bearing and elevation, with the bearing widened by the
         * elevation so a tear high overhead is not drawn pinched.
         */
        void at(float[] out, int point, float across, float radius) {
            float along = (point / (float) (POINTS - 1) - 0.5F) * length;
            float off = jag[point] + across;
            float cos = Mth.cos(tilt);
            float sin = Mth.sin(tilt);
            float up = along * sin + off * cos;
            float round = (along * cos - off * sin) / Math.max(0.2F, Mth.cos(elevation));
            SkyFrame.point(out, bearing + round, elevation + up, radius);
        }
    }
}
