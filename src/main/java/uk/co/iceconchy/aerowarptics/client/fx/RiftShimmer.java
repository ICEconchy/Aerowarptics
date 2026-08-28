package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.List;

/**
 * The mark a lightning strike leaves on whatever it hit.
 *
 * <p>A rift is not the only thing that reacts when a bolt off it reaches the ground - the block on the
 * other end does too. Drawn the same way {@link SummonBeacons}'s pillar is: geometry standing at a
 * fixed place for as long as it stands, rather than particles trying to approximate a shape that has
 * to hold still and be seen from every side.
 *
 * <p>Entirely independent of {@link RiftEffectManager}, on purpose. A strike belongs to whichever rift
 * threw it for exactly as long as it takes to land; the mark it leaves belongs to the block, lives as
 * long as the block cares to keep it, and has no further business with the rift that caused it. Two
 * strikes landing seconds apart from two different rifts on the same block should look like one block
 * that has been struck twice, not like two rifts arguing over whose mark wins.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class RiftShimmer {

    /** Ticks a mark stands before it has faded away completely. */
    private static final float LIFE = 70.0F;
    /** Ticks it takes to reach full strength. Fast: a strike is a sudden thing to arrive after. */
    private static final float FADE_IN = 3.0F;
    /** Ticks of tail spent fading back out, once it starts. */
    private static final float FADE_OUT = 40.0F;
    /** How far proud of the block's own faces the glow sits. */
    private static final float OUTSET = 0.02F;
    /** Sky and block light written on every vertex. A struck block does not wait to be lit. */
    private static final int GLOW_LIGHT = 240;

    private static final List<Struck> ACTIVE = new ArrayList<>();

    private RiftShimmer() {
    }

    /**
     * Marks a block as struck, refreshing rather than stacking a mark already standing there.
     *
     * <p>A second strike on a block still shimmering from the first reads as one strike renewing
     * itself, not as a brighter block - stacking alpha would make a block hit twice in quick
     * succession flash out of proportion with everything around it.
     */
    public static void strike(BlockPos pos, int colour) {
        ACTIVE.removeIf(struck -> struck.pos.equals(pos));
        ACTIVE.add(new Struck(pos, colour));
    }

    public static void clear() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        ACTIVE.removeIf(struck -> ++struck.age >= LIFE);
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eye = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-eye.x, -eye.y, -eye.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
        for (Struck struck : ACTIVE) {
            draw(buffer, matrix, struck, partialTick);
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);

        poseStack.popPose();
    }

    /**
     * A box just proud of the block's own bounds, glowing and breathing.
     *
     * <p>The block's full unit cube rather than its real shape - a stair or a slab gets the same box a
     * full block would, which is a plainer read than chasing every block's own outline would be worth
     * for a mark that is gone inside a few seconds.
     */
    private static void draw(VertexConsumer buffer, Matrix4f matrix, Struck struck, float partialTick) {
        float age = struck.age + partialTick;
        float fadeIn = Mth.clamp(age / FADE_IN, 0.0F, 1.0F);
        float remaining = LIFE - age;
        float fadeOut = Mth.clamp(remaining / FADE_OUT, 0.0F, 1.0F);
        // A shimmer rather than a steady glow: the whole point of the word this is named for.
        float pulse = 0.7F + 0.3F * Mth.sin(age * 0.5F);
        float alpha = fadeIn * fadeOut * pulse * 0.5F;
        if (alpha <= 0.001F) {
            return;
        }

        int argb = withAlpha(struck.colour, alpha);
        float x0 = struck.pos.getX() - OUTSET;
        float y0 = struck.pos.getY() - OUTSET;
        float z0 = struck.pos.getZ() - OUTSET;
        float x1 = struck.pos.getX() + 1.0F + OUTSET;
        float y1 = struck.pos.getY() + 1.0F + OUTSET;
        float z1 = struck.pos.getZ() + 1.0F + OUTSET;

        // Six faces of a box. Winding does not matter - the render type is two-sided - so this is
        // just the six rectangles a cube is made of, in no particular order.
        face(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, argb); // south
        face(buffer, matrix, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, argb); // north
        face(buffer, matrix, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, argb); // west
        face(buffer, matrix, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, argb); // east
        face(buffer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, argb); // top
        face(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, argb); // bottom
    }

    private static void face(VertexConsumer buffer, Matrix4f matrix,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
        vertex(buffer, matrix, x0, y0, z0, argb);
        vertex(buffer, matrix, x1, y1, z1, argb);
        vertex(buffer, matrix, x2, y2, z2, argb);
        vertex(buffer, matrix, x3, y3, z3, argb);
    }

    /**
     * The one place this class puts a vertex into a buffer.
     *
     * <p>It shares {@code RIFT_FIRE} with the apertures and the summon beam, which means it shares
     * their vertex format: the beacon beam's, carrying a texture coordinate, a light level and a
     * normal on top of the colour. All three are constants that say nothing - they are there because
     * the format demands them, and the format is what keeps a shader pack from lighting a glow that
     * has no business being lit. Leaving any of them off does not draw badly; it throws
     * {@code Missing elements in vertex} mid-frame. See {@code RiftVertexFormatTest}.
     */
    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, int argb) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(argb)
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static int withAlpha(int rgb, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static final class Struck {
        private final BlockPos pos;
        private final int colour;
        private float age;

        private Struck(BlockPos pos, int colour) {
            this.pos = pos.immutable();
            this.colour = colour;
        }
    }
}
