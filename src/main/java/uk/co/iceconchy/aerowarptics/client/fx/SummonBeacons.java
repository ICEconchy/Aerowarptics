package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundRiftBeaconPacket;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

import java.util.ArrayList;
import java.util.List;

/**
 * The pillar of light standing where a ship has been called.
 *
 * <p>Two jobs, and they are separate on purpose. The <em>shockwave</em> fires once, on the tick the
 * summon lands, and is thrown as particles - it is a moment, and particles are what this mod uses
 * for moments. The <em>beam</em> stands afterwards and is drawn geometry, because a column that has
 * to be the same shape and the same height for every second it is up is a thing to draw rather than
 * a cloud to keep topping up. Building a straight pillar out of particles gives a fuzzy
 * approximation of one, and costs more the longer it stands.
 *
 * <p>Drawn in world space from the camera, the same way the apertures are, so nothing about a beam's
 * position depends on a pose stack somebody else has already transformed.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class SummonBeacons {

    /** How far up the beam reaches, in blocks. Tall enough to clear terrain and be seen over it. */
    private static final float HEIGHT = 96.0F;

    /** Half-width of the core column. The glow around it is drawn wider. */
    private static final float CORE = 0.32F;

    /** How much wider the outer sheath is than the core. */
    private static final float SHEATH = 3.2F;

    /** Ticks the beam spends growing to full height, and fading out again at the end. */
    private static final int RISE = 8;
    private static final int FADE = 30;

    /** Rings that run up the column, and how fast they climb, in blocks per tick. */
    private static final int RINGS = 4;
    private static final float RING_SPEED = 1.6F;

    /** Sky and block light written on every vertex. A beacon lights itself. */
    private static final int GLOW_LIGHT = 240;

    private static final List<Beam> ACTIVE = new ArrayList<>();

    private SummonBeacons() {
    }

    /** Lights a beam and throws the shockwave, from the packet the server sent on the summon. */
    public static void mark(ClientboundRiftBeaconPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            return;
        }
        BlockPos target = packet.target();
        Vec3 centre = Vec3.atCenterOf(target);
        // A second summon onto the same spot replaces the first rather than stacking a second beam
        // inside it, which would read as one brighter beam and then flicker as they expired apart.
        ACTIVE.removeIf(beam -> beam.at.equals(target));
        ACTIVE.add(new Beam(target, centre, Math.max(1, packet.ticks()), tint(packet.tier())));
        shockwave(level, centre);
    }

    /**
     * The outward slap of air when a ship is called.
     *
     * <p>Thrown flat along the ground rather than as a sphere, because it is announcing a place on
     * the floor rather than an explosion in the air, and a ring that hugs the terrain tells a
     * bystander where to move away from.
     */
    private static void shockwave(ClientLevel level, Vec3 centre) {
        RandomSource random = level.random;
        for (int i = 0; i < 96; i++) {
            double angle = i / 96.0D * Math.PI * 2.0D;
            double speed = 0.55D + random.nextDouble() * 0.25D;
            double x = Math.cos(angle);
            double z = Math.sin(angle);
            level.addParticle(AWParticles.RIFT_SPARK.get(),
                    centre.x + x * 0.6D, centre.y + 0.1D, centre.z + z * 0.6D,
                    x * speed, 0.02D + random.nextDouble() * 0.03D, z * speed);
        }
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            level.addParticle(AWParticles.WARP_STREAK.get(),
                    centre.x, centre.y + random.nextDouble() * 2.0D, centre.z,
                    Math.cos(angle) * 0.15D, 0.35D + random.nextDouble() * 0.2D,
                    Math.sin(angle) * 0.15D);
        }
        level.addParticle(ParticleTypes.FLASH, centre.x, centre.y + 0.5D, centre.z, 0.0D, 0.0D, 0.0D);
        level.playLocalSound(centre.x, centre.y, centre.z, AWSounds.RIFT_OPEN.get(),
                SoundSource.BLOCKS, 1.4F, 0.7F, false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        if (!(Minecraft.getInstance().level instanceof ClientLevel)) {
            // Left behind by a disconnect or a dimension change. Beams belong to a level, and one
            // carried into the next would stand in a place that no longer means anything.
            ACTIVE.clear();
            return;
        }
        ACTIVE.removeIf(beam -> ++beam.age >= beam.life);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Additive and writing no depth: a beam of light should brighten what is behind it rather
        // than cut a hole in it.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
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
        for (Beam beam : ACTIVE) {
            draw(buffer, matrix, beam, partialTick);
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);

        poseStack.popPose();
    }

    private static void draw(VertexConsumer buffer, Matrix4f matrix, Beam beam, float partialTick) {
        float age = beam.age + partialTick;
        float rise = Mth.clamp(age / RISE, 0.0F, 1.0F);
        float remaining = beam.life - age;
        float alpha = Mth.clamp(remaining / FADE, 0.0F, 1.0F) * rise;
        if (alpha <= 0.0F) {
            return;
        }

        float height = HEIGHT * rise;
        Vec3 foot = beam.centre;
        float baseY = (float) foot.y - 0.5F;

        // Two nested columns, each a pair of crossed panes. Crossed rather than billboarded because
        // a beam is a landmark people walk around, and one that turns to face every viewer stops
        // reading as a fixed thing standing in a fixed place.
        column(buffer, matrix, foot, baseY, height, CORE, beam.colour, alpha * 0.85F);
        column(buffer, matrix, foot, baseY, height, CORE * SHEATH, beam.colour, alpha * 0.22F);

        // Rings climbing the column, so it reads as something being drawn upward rather than a
        // static stick of light. Evenly spaced and wrapped, so there is always one low down where
        // somebody standing next to it can see it move.
        for (int i = 0; i < RINGS; i++) {
            float travel = (age * RING_SPEED + i * (HEIGHT / RINGS)) % HEIGHT;
            if (travel > height) {
                continue;
            }
            float fade = 1.0F - travel / HEIGHT;
            ring(buffer, matrix, foot, baseY + travel, CORE * SHEATH * 1.4F, beam.colour,
                    alpha * fade * 0.5F);
        }
    }

    /** Two crossed vertical panes, visible from both sides because the render type does not cull. */
    private static void column(VertexConsumer buffer, Matrix4f matrix, Vec3 foot, float baseY,
                               float height, float half, int colour, float alpha) {
        int argb = withAlpha(colour, alpha);
        float x = (float) foot.x;
        float z = (float) foot.z;
        float top = baseY + height;
        // Pane running along X.
        vertex(buffer, matrix, x - half, baseY, z, argb);
        vertex(buffer, matrix, x + half, baseY, z, argb);
        vertex(buffer, matrix, x + half, top, z, argb);
        vertex(buffer, matrix, x - half, top, z, argb);
        // Pane running along Z.
        vertex(buffer, matrix, x, baseY, z - half, argb);
        vertex(buffer, matrix, x, baseY, z + half, argb);
        vertex(buffer, matrix, x, top, z + half, argb);
        vertex(buffer, matrix, x, top, z - half, argb);
    }

    /** A flat disc lying across the column, marking how far up something has climbed. */
    private static void ring(VertexConsumer buffer, Matrix4f matrix, Vec3 foot, float y, float radius,
                             int colour, float alpha) {
        int argb = withAlpha(colour, alpha);
        float x = (float) foot.x;
        float z = (float) foot.z;
        vertex(buffer, matrix, x - radius, y, z - radius, argb);
        vertex(buffer, matrix, x - radius, y, z + radius, argb);
        vertex(buffer, matrix, x + radius, y, z + radius, argb);
        vertex(buffer, matrix, x + radius, y, z - radius, argb);
    }

    /**
     * The one place this class puts a vertex into a buffer.
     *
     * <p>It shares {@code RIFT_FIRE} with the apertures, which means it shares their vertex format:
     * the beacon beam's, carrying a texture coordinate, a light level and a normal on top of the
     * colour. All three are constants that say nothing - they are there because the format demands
     * them, and the format is what keeps a shader pack from lighting a beam of light. Leaving any of
     * them off does not draw badly; it throws {@code Missing elements in vertex} mid-frame.
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

    /** The same ladder of colours a drive's own rift is drawn in, so a beam names the tier calling. */
    private static int tint(int tier) {
        int[] colours = {0x49D9C4, 0x49B7D9, 0x7A6BFF, 0xC86BFF, 0xFFD36B};
        return colours[Mth.clamp(tier, 0, colours.length - 1)];
    }

    private static final class Beam {
        private final BlockPos at;
        private final Vec3 centre;
        private final int life;
        private final int colour;
        private int age;

        private Beam(BlockPos at, Vec3 centre, int life, int colour) {
            this.at = at;
            this.centre = centre;
            this.life = life;
            this.colour = colour;
        }
    }
}
