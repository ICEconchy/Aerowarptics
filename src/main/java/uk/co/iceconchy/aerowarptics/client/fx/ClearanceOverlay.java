package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundClearancePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a drive's departure clearance as a world-space wireframe, for a few seconds after
 * {@code /aerowarptics warp clearance} is run.
 *
 * <p>Three things are outlined:
 * <ul>
 *   <li>the <em>padded</em> volume - the sweep widened to the aperture's reach - in teal, for
 *       reference only;
 *   <li>the <em>bare</em> hull sweep the ship physically flies through, in amber, sitting inside it -
 *       the volume the clearance check actually tests;
 *   <li>every block found fouling the amber sweep, in red.
 * </ul>
 *
 * <p>The teal used to be what was tested, and this overlay is how that was shown to be the problem:
 * launches refused for terrain that touched the teal and never the amber. It is still drawn so a pilot
 * can see where the aperture will reach, but terrain inside it and outside the amber stops nothing.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class ClearanceOverlay {

    private static List<AABB> core = List.of();
    private static List<AABB> padded = List.of();
    private static List<AABB> hits = List.of();
    private static long endMillis;

    private ClearanceOverlay() {
    }

    /** Latch the volumes the server measured, or clear the overlay when the toggle goes off. */
    public static void show(ClientboundClearancePacket packet) {
        if (!packet.active()) {
            core = List.of();
            padded = List.of();
            hits = List.of();
            endMillis = 0L;
            return;
        }
        core = packet.core();
        padded = packet.padded();
        List<AABB> boxes = new ArrayList<>(packet.hits().size());
        for (BlockPos pos : packet.hits()) {
            boxes.add(new AABB(pos));
        }
        hits = boxes;
        endMillis = Util.getMillis() + packet.duration() * 50L;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (padded.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (Util.getMillis() > endMillis) {
            core = List.of();
            padded = List.of();
            hits = List.of();
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        // The aperture's reach (teal), then the tested hull sweep inside it (amber). One box per
        // segment rather than one around the lot: on a diagonal bearing the enclosing box is far
        // wider than anything the check reads, and an overlay that overstates the corridor is worse
        // than none at all.
        for (AABB segment : padded) {
            LevelRenderer.renderLineBox(pose, lines, segment, 0.30F, 0.85F, 0.80F, 0.85F);
        }
        for (AABB segment : core) {
            LevelRenderer.renderLineBox(pose, lines, segment, 1.00F, 0.80F, 0.25F, 0.90F);
        }
        // Every block the departure would fly into (red).
        for (AABB hit : hits) {
            LevelRenderer.renderLineBox(pose, lines, hit, 1.00F, 0.25F, 0.25F, 1.00F);
        }

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
