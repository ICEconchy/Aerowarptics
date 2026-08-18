package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the apertures an airship flies through.
 *
 * <p>A rift is a ring of torn space: a dark aperture with bands of energy racing around its rim,
 * widening as it tears open and collapsing once the hull is through. It is drawn in world space at the
 * position the server sent, so the people standing at the destination watch a rift open in front of
 * them seconds before a ship comes out of it - the two ends of the journey are visible to two
 * different sets of players, which is the whole appeal of long-range travel.
 *
 * <p>Geometry is plain triangle-fanned quads on the additive lightning pass, so there is no texture to
 * load, nothing to bind, and no shader dependency.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class RiftEffectManager {

    /** Concentric bands making up the rim. More reads as a finer aperture and costs more quads. */
    private static final int BANDS = 5;
    /** Segments around the ring. 32 is smooth at any size a hull needs. */
    private static final int SEGMENTS = 32;

    private static final List<ActiveRift> ACTIVE = new ArrayList<>();

    private RiftEffectManager() {
    }

    /** One rift, from the moment it tears open to the moment it collapses. */
    private static final class ActiveRift {
        final Vec3 centre;
        final Vector3f right;
        final Vector3f up;
        final double radius;
        final int colour;
        final int openTicks;
        final int holdTicks;
        final int closeTicks;
        int age;
        int lastAge;

        ActiveRift(Vec3 centre, Vec3 normal, double radius, int colour,
                   int openTicks, int holdTicks, int closeTicks) {
            this.centre = centre;
            this.radius = radius;
            this.colour = colour;
            this.openTicks = openTicks;
            this.holdTicks = holdTicks;
            this.closeTicks = closeTicks;

            Vector3f forward = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
            if (forward.lengthSquared() < 1.0e-6F) {
                forward.set(0.0F, 0.0F, 1.0F);
            }
            forward.normalize();
            // Any vector not parallel to the normal gives a stable basis for the aperture's plane.
            Vector3f seed = Math.abs(forward.y) > 0.9F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            this.right = new Vector3f(forward).cross(seed).normalize();
            this.up = new Vector3f(forward).cross(right).normalize();
        }

        int lifetime() {
            return openTicks + holdTicks + closeTicks;
        }

        boolean expired() {
            return age >= lifetime();
        }

        /** 0..1 aperture size, easing open and snapping shut. */
        float aperture(float partialTick) {
            float time = Mth.lerp(partialTick, lastAge, age);
            if (time < openTicks) {
                float t = time / openTicks;
                return t * t * (3.0F - 2.0F * t); // smoothstep: tears open, then steadies
            }
            if (time < openTicks + holdTicks) {
                return 1.0F;
            }
            float t = (time - openTicks - holdTicks) / Math.max(1.0F, closeTicks);
            return Math.max(0.0F, 1.0F - t * t); // collapses faster than it opened
        }
    }

    // ------------------------------------------------------------------ feed

    /** Opens a rift from a server cue. */
    public static void open(ClientboundWarpEffectPacket packet, int colour,
                            int openTicks, int holdTicks, int closeTicks) {
        if (!packet.hasRift() || !AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
        ACTIVE.add(new ActiveRift(packet.centre(), packet.normal(), packet.radius(), colour,
                openTicks, holdTicks, closeTicks));
    }

    /** Drops every rift, e.g. on disconnect or dimension change. */
    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean hasActiveRifts() {
        return !ACTIVE.isEmpty();
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
        for (Iterator<ActiveRift> iterator = ACTIVE.iterator(); iterator.hasNext(); ) {
            ActiveRift rift = iterator.next();
            rift.lastAge = rift.age;
            rift.age++;
            if (rift.expired()) {
                iterator.remove();
            }
        }
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-eye.x, -eye.y, -eye.z);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        for (ActiveRift rift : ACTIVE) {
            drawRift(consumer, matrix, rift, partialTick);
        }

        buffers.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void drawRift(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float partialTick) {
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        float time = (rift.lastAge + partialTick) * 0.12F;
        double outer = rift.radius * aperture;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        for (int band = 0; band < BANDS; band++) {
            // Bands run from the dark throat outwards, brightening and thinning towards the rim.
            float innerT = band / (float) BANDS;
            float outerT = (band + 1) / (float) BANDS;
            double innerRadius = outer * (0.35D + 0.65D * innerT);
            double outerRadius = outer * (0.35D + 0.65D * outerT);

            float edge = (float) Math.pow(outerT, 1.6D);
            float alpha = aperture * (0.15F + 0.85F * edge);
            // Each band counter-rotates against its neighbour so the aperture churns.
            float spin = time * (band % 2 == 0 ? 1.0F : -1.4F) + band * 0.7F;

            float bandRed = Mth.lerp(edge, red * 0.15F, 1.0F);
            float bandGreen = Mth.lerp(edge, green * 0.15F, green);
            float bandBlue = Mth.lerp(edge, blue * 0.3F, blue);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D + spin;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D + spin;

                // A gap chases around each band, so the rim shimmers rather than sitting flat.
                float flicker = 0.65F + 0.35F * Mth.sin((float) (a0 * 3.0D + time * 4.0F));
                float segmentAlpha = alpha * flicker;

                vertex(consumer, matrix, rift, a0, innerRadius, bandRed, bandGreen, bandBlue, segmentAlpha);
                vertex(consumer, matrix, rift, a1, innerRadius, bandRed, bandGreen, bandBlue, segmentAlpha);
                vertex(consumer, matrix, rift, a1, outerRadius, bandRed, bandGreen, bandBlue, segmentAlpha * 0.4F);
                vertex(consumer, matrix, rift, a0, outerRadius, bandRed, bandGreen, bandBlue, segmentAlpha * 0.4F);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                               double angle, double radius, float red, float green, float blue, float alpha) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        float x = (float) (rift.centre.x + rift.right.x * cos + rift.up.x * sin);
        float y = (float) (rift.centre.y + rift.right.y * cos + rift.up.y * sin);
        float z = (float) (rift.centre.z + rift.right.z * cos + rift.up.z * sin);
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, Math.min(1.0F, alpha));
    }
}
