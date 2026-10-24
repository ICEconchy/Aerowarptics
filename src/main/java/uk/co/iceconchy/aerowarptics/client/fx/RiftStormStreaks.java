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
 * Shards of rift fire streaking across a storm sky, each dragging a fading trail behind it.
 *
 * <p>Something like a shooting star, and meant to be read as one at a glance - but violet, or now and then the
 * aurora's cyan, and falling from a torn sky rather than out of space. Now and then two or three come together,
 * a moment apart, on nearly the same line: a fragment breaking up as it falls.
 *
 * <p>Hung at infinity on {@link AWRenderTypes#RIFT_AURORA}, nearest of the storm's sky (see {@link SkyFrame}),
 * so they cross in front of the aurora and the vortex. Rolled on the client, like the bolts: two players under
 * one storm each see streaks of their own.
 */
@OnlyIn(Dist.CLIENT)
final class RiftStormStreaks {

    private static final int LEAST_GAP = 60;
    private static final int MOST_GAP = 200;
    private static final int LEAST_LIFE = 20;
    private static final int MOST_LIFE = 40;
    /** Points along a trail, head to tail. */
    private static final int SAMPLES = 10;
    private static final float REACH = 0.88F;
    /** Half the width of a streak's head, in radians of sky. */
    private static final float HEAD_WIDTH = 0.004F;

    private static final int CORE_COLOUR = 0xF0E6FF;
    private static final int[] TAIL_COLOURS = {0x8A6BFF, 0xC060FF, 0x4AD9C7};

    private static final List<Streak> STREAKS = new ArrayList<>();
    private static int countdown = LEAST_GAP;

    private RiftStormStreaks() {
    }

    static void clear() {
        STREAKS.clear();
        countdown = LEAST_GAP;
    }

    static void tick(RandomSource random, float weather) {
        STREAKS.removeIf(streak -> ++streak.age >= streak.life);
        if (weather <= 0.0F || --countdown > 0) {
            return;
        }
        int gap = LEAST_GAP + random.nextInt(MOST_GAP - LEAST_GAP + 1);
        countdown = Math.round(gap / Math.max(0.2F, weather));

        Streak first = roll(random);
        STREAKS.add(first);
        // One time in four, the shard has broken up: its pieces follow it down, a moment behind.
        if (random.nextInt(4) == 0) {
            int pieces = 1 + random.nextInt(2);
            for (int piece = 1; piece <= pieces; piece++) {
                Streak follower = first.copy();
                follower.age = -(piece * (2 + random.nextInt(4)));
                follower.bearing += (random.nextFloat() - 0.5F) * 0.04F;
                follower.elevation += (random.nextFloat() - 0.5F) * 0.03F;
                follower.width *= 0.6F;
                STREAKS.add(follower);
            }
        }
    }

    private static Streak roll(RandomSource random) {
        Streak streak = new Streak();
        streak.life = LEAST_LIFE + random.nextInt(MOST_LIFE - LEAST_LIFE + 1);
        streak.bearing = random.nextFloat() * Mth.TWO_PI;
        streak.elevation = (30.0F + random.nextFloat() * 40.0F) * Mth.DEG_TO_RAD;
        // Mostly across and downward, the way things fall.
        float heading = (random.nextBoolean() ? 0.0F : Mth.PI) + (0.2F + random.nextFloat() * 0.5F)
                * (random.nextBoolean() ? 1.0F : -1.0F);
        float speed = 0.012F + random.nextFloat() * 0.012F;
        streak.across = Mth.cos(heading) * speed;
        streak.down = -Math.abs(Mth.sin(heading)) * speed - 0.004F;
        streak.trail = 0.12F + random.nextFloat() * 0.18F;
        streak.width = HEAD_WIDTH * (0.8F + random.nextFloat() * 0.5F);
        streak.colour = TAIL_COLOURS[random.nextInt(4) == 0 ? 2 : random.nextInt(2)];
        return streak;
    }

    static void render(RenderLevelStageEvent event, float partialTick, float storm) {
        if (STREAKS.isEmpty() || SkyFrame.hidden(event)) {
            return;
        }
        Matrix4f view = SkyFrame.view(event);
        float radius = SkyFrame.radius(REACH);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_AURORA);
        float[] a = new float[3];
        float[] b = new float[3];
        float[] c = new float[3];
        float[] d = new float[3];
        for (Streak streak : STREAKS) {
            float age = streak.age + partialTick;
            if (age <= 0.0F) {
                continue;
            }
            float lit = RiftStormShapes.streakLife(age / streak.life) * storm;
            if (lit <= 0.001F) {
                continue;
            }
            // How far it has come, in radians of sky - and so how much trail it can have left behind it yet.
            float travelled = age * streak.speed();
            float trail = Math.min(streak.trail, travelled);
            for (int sample = 0; sample < SAMPLES; sample++) {
                float s0 = sample / (float) SAMPLES;
                float s1 = (sample + 1) / (float) SAMPLES;
                float w0 = streak.width * (1.0F - s0);
                float w1 = streak.width * (1.0F - s1);
                streak.at(a, age, s0 * trail, w0, radius);
                streak.at(b, age, s1 * trail, w1, radius);
                streak.at(c, age, s1 * trail, -w1, radius);
                streak.at(d, age, s0 * trail, -w0, radius);
                int near = colour(streak, s0, lit);
                int far = colour(streak, s1, lit);
                vertex(buffer, view, a, near);
                vertex(buffer, view, b, far);
                vertex(buffer, view, c, far);
                vertex(buffer, view, d, near);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_AURORA);
    }

    /** White-hot at the head, cooling into the streak's own colour along the trail, and gone at its end. */
    private static int colour(Streak streak, float behind, float lit) {
        float heat = RiftStormShapes.clamp01(1.0F - behind * 3.0F);
        int r = lerp(heat, (streak.colour >> 16) & 0xFF, (CORE_COLOUR >> 16) & 0xFF);
        int g = lerp(heat, (streak.colour >> 8) & 0xFF, (CORE_COLOUR >> 8) & 0xFF);
        int b = lerp(heat, streak.colour & 0xFF, CORE_COLOUR & 0xFF);
        return SkyFrame.argb(r << 16 | g << 8 | b, RiftStormShapes.streakTrail(behind) * lit);
    }

    private static int lerp(float t, int from, int to) {
        return Math.round(from + (to - from) * t);
    }

    /** Position and colour: the whole of {@code RIFT_AURORA}'s format. */
    private static void vertex(VertexConsumer buffer, Matrix4f view, float[] p, int argb) {
        buffer.addVertex(view, p[0], p[1], p[2]).setColor(argb);
    }

    private static final class Streak {
        private float bearing;
        private float elevation;
        /** Radians a tick round the sky, and down it. */
        private float across;
        private float down;
        /** Longest its trail grows, in radians of sky. */
        private float trail;
        private float width;
        private int colour;
        private int life;
        private float age;

        float speed() {
            return (float) Math.sqrt(across * across + down * down);
        }

        /**
         * The point {@code behind} radians back along the path from where the head is at {@code age}, and
         * {@code side} radians across it.
         */
        void at(float[] out, float age, float behind, float side, float radius) {
            float speed = Math.max(1.0e-5F, speed());
            float ux = across / speed;
            float uy = down / speed;
            float headX = across * age - ux * behind;
            float headY = down * age - uy * behind;
            float x = headX - uy * side;
            float y = headY + ux * side;
            float up = elevation + y;
            SkyFrame.point(out, bearing + x / Math.max(0.2F, Mth.cos(up)), up, radius);
        }

        Streak copy() {
            Streak copy = new Streak();
            copy.bearing = bearing;
            copy.elevation = elevation;
            copy.across = across;
            copy.down = down;
            copy.trail = trail;
            copy.width = width;
            copy.colour = colour;
            copy.life = life;
            copy.age = age;
            return copy;
        }
    }
}
