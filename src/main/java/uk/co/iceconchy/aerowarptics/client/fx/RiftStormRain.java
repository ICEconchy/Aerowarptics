package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/**
 * The rain a Rift Storm brings: violet drops, a violet splash where they land, and the sound of rain.
 *
 * <p>Built the way vanilla builds its own rain in {@code LevelRenderer.renderSnowAndRain} and
 * {@code tickRain}, deliberately, so it behaves like rain in every way a player has learned to expect:
 * a ring of falling columns round the camera, ten wide on Fancy and five on Fast, each stopped at the
 * first thing in the way; shade from the light where it falls; splashes on whatever is below; and the
 * muffled version of the sound when there is a roof overhead. It falls in every biome, deserts and
 * snowfields included - it is not water, and nothing about a desert keeps a torn sky out.
 *
 * <p>Vanilla's own rain is faded out as this fades in, by {@code LevelRendererMixin}: pale blue and
 * violet drops falling through the same air read as two weathers at once.
 *
 * <p>The drops carry their colour in the texture, not in the vertices - a tint can only take colour
 * away, and there is no way to multiply vanilla's pale blue rain into violet. They are also never quite
 * dark: a floor on the block light they are shaded by lets them glow faintly at night, which is the
 * difference between rift rain and rain that happens to be purple.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class RiftStormRain {

    /** Block light the drops are never shaded below. Enough to glimmer in the dark; not enough to light anything. */
    private static final int GLOW_FLOOR = 9;
    /** Texture tiles per block of fall. The drops were drawn at thirty-two pixels a block. */
    private static final float TILES_PER_BLOCK = 0.5F;

    private static final DustParticleOptions SPLASH =
            new DustParticleOptions(new Vector3f(0.72F, 0.42F, 1.0F), 0.7F);

    private static int ticks;
    private static int soundTimer;

    private RiftStormRain() {
    }

    // ------------------------------------------------------------------ tick

    /**
     * Splashes and sound, as vanilla's {@code tickRain}: a hundred tries a tick at full strength, halved
     * on Fast graphics and again for decreased particles, each landing on a random column within ten
     * blocks of the camera - and the last one that landed is where the sound comes from.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        ticks++;
        float storm = RiftStormSky.weather(1.0F) / (Minecraft.useFancyGraphics() ? 1.0F : 2.0F);
        if (storm <= 0.0F) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        ParticleStatus particles = minecraft.options.particles().get();
        RandomSource random = RandomSource.create(ticks * 312987231L);
        BlockPos eye = BlockPos.containing(camera.getPosition());
        BlockPos landed = null;
        float density = (float) (double) AWConfig.PARTICLE_DENSITY.get();
        int tries = Math.round(100.0F * storm * storm * density) / (particles == ParticleStatus.DECREASED ? 2 : 1);

        for (int attempt = 0; attempt < tries; attempt++) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    eye.offset(random.nextInt(21) - 10, 0, random.nextInt(21) - 10));
            if (top.getY() <= level.getMinBuildHeight() || Math.abs(top.getY() - eye.getY()) > 10) {
                continue;
            }
            landed = top.below();
            if (particles == ParticleStatus.MINIMAL) {
                break;
            }
            double fx = random.nextDouble();
            double fz = random.nextDouble();
            double surface = Math.max(
                    level.getBlockState(landed).getCollisionShape(level, landed).max(Direction.Axis.Y, fx, fz),
                    level.getFluidState(landed).getHeight(level, landed));
            double x = landed.getX() + fx;
            double y = landed.getY() + Math.max(0.0D, surface);
            double z = landed.getZ() + fz;
            // Mostly a violet puff, now and then a spark thrown up off the surface: rift rain does not
            // so much splash as fizz.
            if (random.nextInt(10) == 0) {
                level.addParticle(AWParticles.RIFT_SPARK.get(), x, y + 0.05D, z,
                        (random.nextDouble() - 0.5D) * 0.04D, 0.04D + random.nextDouble() * 0.04D,
                        (random.nextDouble() - 0.5D) * 0.04D);
            } else {
                level.addParticle(SPLASH, x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
            }
        }

        if (landed != null && random.nextInt(3) < soundTimer++) {
            soundTimer = 0;
            float volume = storm * (float) (double) AWConfig.EFFECT_VOLUME.get();
            // Pitched a little under vanilla's, so it is recognisably rain and recognisably not quite.
            boolean sheltered = landed.getY() > eye.getY() + 1
                    && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, eye).getY() > eye.getY();
            if (sheltered) {
                level.playLocalSound(landed, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER,
                        0.1F * volume, 0.4F, false);
            } else {
                level.playLocalSound(landed, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER,
                        0.2F * volume, 0.8F, false);
            }
        }
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float storm = RiftStormSky.weather(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (storm <= 0.0F || level == null) {
            return;
        }

        Vec3 eye = event.getCamera().getPosition();
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        int ex = Mth.floor(eye.x);
        int ey = Mth.floor(eye.y);
        int ez = Mth.floor(eye.z);
        int clock = event.getRenderTick() & 131071;

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_RAIN);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int z = ez - radius; z <= ez + radius; z++) {
            for (int x = ex - radius; x <= ex + radius; x++) {
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(ey - radius, ground);
                int top = Math.max(ey + radius, ground);
                if (bottom == top) {
                    continue;
                }

                double dx = x + 0.5D - eye.x;
                double dz = z + 0.5D - eye.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                float fade = (float) (distance / radius);
                float alpha = ((1.0F - fade * fade) * 0.5F + 0.5F) * storm;
                if (alpha <= 0.0F) {
                    continue;
                }

                // Turned to face the camera, as vanilla's columns are, so a column is never seen edge-on.
                double across = Math.max(distance, 1.0e-3D);
                float sx = (float) (-dz / across * 0.5D);
                float sz = (float) (dx / across * 0.5D);

                // Each column falls at its own speed from its own starting point, or the whole ring would
                // scroll in lockstep and read as a texture moving rather than rain falling.
                int hash = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761;
                float speed = 3.0F + ((hash >>> 8) & 0xFF) / 255.0F;
                float scroll = (-((clock + (hash & 0xFF)) + partialTick) / 16.0F * speed) % 32.0F;

                cursor.set(x, Math.max(ground, ey), z);
                int light = LevelRenderer.getLightColor(level, cursor);
                light = LightTexture.pack(Math.max(LightTexture.block(light), GLOW_FLOOR), LightTexture.sky(light));
                int argb = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | 0xFFFFFF;

                float cx = (float) (x + 0.5D - eye.x);
                float cz = (float) (z + 0.5D - eye.z);
                float yTop = (float) (top - eye.y);
                float yBottom = (float) (bottom - eye.y);
                float vTop = bottom * TILES_PER_BLOCK + scroll;
                float vBottom = top * TILES_PER_BLOCK + scroll;
                vertex(buffer, cx - sx, yTop, cz - sz, 0.0F, vTop, argb, light);
                vertex(buffer, cx + sx, yTop, cz + sz, 1.0F, vTop, argb, light);
                vertex(buffer, cx + sx, yBottom, cz + sz, 1.0F, vBottom, argb, light);
                vertex(buffer, cx - sx, yBottom, cz - sz, 0.0F, vBottom, argb, light);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_RAIN);
    }

    /** The particle format: position, texture, colour and light, all four or the buffer throws. */
    private static void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int argb, int light) {
        buffer.addVertex(x, y, z)
                .setUv(u, v)
                .setColor(argb)
                .setLight(light);
    }
}
