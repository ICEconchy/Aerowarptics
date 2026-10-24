package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 * The rain a Rift Storm brings: vanilla's drops in violet, a violet splash where they land, and the sound of rain.
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
 * <p>"The first thing in the way" may be a ship, and the level's heightmap has never heard of one. Vanilla's
 * rain stops on a deck only because Sable wraps the heightmap reads inside vanilla's own two methods; built
 * the way vanilla's is but not by them, this fell through every hull until it asked for itself. Every read of
 * the heightmap here is paired with a read of the ships over it - see {@link RainCover}.
 *
 * <p>One way it deliberately differs: vanilla's rain is tested against the clouds' depth, and this is not.
 * Nobody stands at cloud height in vanilla; on an airship everybody does, and the rain round the deck vanished
 * into every cloud the ship was passing through. See {@link CloudlessDepth}.
 *
 * <p>The drops are vanilla's own, from vanilla's own texture, recoloured as it loads - see
 * {@link RiftRainTexture}. They used to be drawn in the rift's colours by a script of this mod's: brighter,
 * thicker, and lit from within so they glowed at night. In the middle of a storm that was the one thing on
 * screen a player could not stop looking at. They are now shaded exactly as vanilla's are, drawn as densely
 * as vanilla's are, and fizz rather less where they land; what says "rift" is the colour, and the sky.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class RiftStormRain {

    /** Texture tiles per block of fall: vanilla's, whose rain texture is four blocks of drops tall. */
    private static final float TILES_PER_BLOCK = 0.25F;
    /** Splash attempts a tick at full strength. Vanilla tries a hundred, but its splash is a speck; this is a puff. */
    private static final float SPLASHES = 40.0F;
    /** One splash in this many throws a spark rather than a puff. */
    private static final int SPARK_ONE_IN = 40;
    /** How far from the camera a splash can land, in blocks: vanilla's. */
    private static final int SPLASH_REACH = 10;

    private static final DustParticleOptions SPLASH =
            new DustParticleOptions(new Vector3f(0.72F, 0.42F, 1.0F), 0.5F);

    private static int ticks;
    private static int soundTimer;

    private RiftStormRain() {
    }

    // ------------------------------------------------------------------ tick

    /**
     * Splashes and sound, as vanilla's {@code tickRain}: a number of tries a tick at full strength, halved
     * on Fast graphics and again for decreased particles, each landing on a random column within ten
     * blocks of the camera - and the last one that landed is where the sound comes from. Fewer tries than
     * vanilla's hundred: a violet puff is a good deal more to look at than vanilla's grey speck.
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
        int tries = Math.round(SPLASHES * storm * storm * density) / (particles == ParticleStatus.DECREASED ? 2 : 1);
        // The heightmap is the ground's. A deck is not in it, so every read of it below asks the ships too.
        RainCovers ships = RainCovers.ticked(level, eye.getX(), eye.getZ(), SPLASH_REACH);

        for (int attempt = 0; attempt < tries; attempt++) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    eye.offset(random.nextInt(2 * SPLASH_REACH + 1) - SPLASH_REACH, 0,
                            random.nextInt(2 * SPLASH_REACH + 1) - SPLASH_REACH));
            double deck = ships.over(top.getX(), top.getZ());
            boolean aboard = deck > top.getY();
            double height = aboard ? deck : top.getY();
            if (height <= level.getMinBuildHeight() || Math.abs(height - eye.getY()) > 10) {
                continue;
            }
            landed = aboard ? new BlockPos(top.getX(), Mth.ceil(deck) - 1, top.getZ()) : top.below();
            if (particles == ParticleStatus.MINIMAL) {
                break;
            }
            double fx = random.nextDouble();
            double fz = random.nextDouble();
            double x = landed.getX() + fx;
            double y;
            if (aboard) {
                // A deck's blocks are in its plot, not here: this block of the world is air, with no shape
                // to ask how tall it is. The deck's own height is already exact.
                y = deck;
            } else {
                double surface = Math.max(
                        level.getBlockState(landed).getCollisionShape(level, landed).max(Direction.Axis.Y, fx, fz),
                        level.getFluidState(landed).getHeight(level, landed));
                y = landed.getY() + Math.max(0.0D, surface);
            }
            double z = landed.getZ() + fz;
            // Mostly a violet puff, once in a while a spark thrown up off the surface: rift rain does not
            // so much splash as fizz.
            if (random.nextInt(SPARK_ONE_IN) == 0) {
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
            double roof = Math.max(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, eye).getY(),
                    ships.over(eye.getX(), eye.getZ()));
            boolean sheltered = landed.getY() > eye.getY() + 1 && roof > eye.getY();
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

    /**
     * Draws the ring of falling columns. Called by {@link RiftStormSky} at the weather stage, after the
     * clouds, with the depth they left behind swapped out - see {@link CloudlessDepth}.
     */
    static void render(RenderLevelStageEvent event, float partialTick, float storm) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 eye = event.getCamera().getPosition();
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        int ex = Mth.floor(eye.x);
        int ey = Mth.floor(eye.y);
        int ez = Mth.floor(eye.z);
        int clock = event.getRenderTick() & 131071;

        RiftRainTexture.ensureRegistered();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = Minecraft.useShaderTransparency() ? AWRenderTypes.RIFT_RAIN_LAYERED : AWRenderTypes.RIFT_RAIN;
        VertexConsumer buffer = buffers.getBuffer(type);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        RainCovers ships = RainCovers.drawn(level, ex, ez, radius);

        for (int z = ez - radius; z <= ez + radius; z++) {
            for (int x = ex - radius; x <= ex + radius; x++) {
                // The first thing in the way is the ground or a ship, whichever is higher - and a ship is
                // not in the heightmap, and rides at any height, so from here on the heights are not whole.
                double ground = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), ships.over(x, z));
                double bottom = Math.max(ey - radius, ground);
                double top = Math.max(ey + radius, ground);
                if (bottom >= top) {
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
                // scroll in lockstep and read as a texture moving rather than rain falling. Vanilla's rates.
                int hash = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761;
                float speed = 3.0F + ((hash >>> 8) & 0xFF) / 255.0F;
                float scroll = (-((clock + (hash & 0xFF)) + partialTick) / 32.0F * speed) % 32.0F;

                cursor.set(x, Math.max(Mth.ceil(ground), ey), z);
                int light = LevelRenderer.getLightColor(level, cursor);
                int argb = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | 0xFFFFFF;

                float cx = (float) (x + 0.5D - eye.x);
                float cz = (float) (z + 0.5D - eye.z);
                float yTop = (float) (top - eye.y);
                float yBottom = (float) (bottom - eye.y);
                float vTop = (float) (bottom * TILES_PER_BLOCK) + scroll;
                float vBottom = (float) (top * TILES_PER_BLOCK) + scroll;
                vertex(buffer, cx - sx, yTop, cz - sz, 0.0F, vTop, argb, light);
                vertex(buffer, cx + sx, yTop, cz + sz, 1.0F, vTop, argb, light);
                vertex(buffer, cx + sx, yBottom, cz + sz, 1.0F, vBottom, argb, light);
                vertex(buffer, cx - sx, yBottom, cz - sz, 0.0F, vBottom, argb, light);
            }
        }
        buffers.endBatch(type);
    }

    /** The particle format: position, texture, colour and light, all four or the buffer throws. */
    private static void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int argb, int light) {
        buffer.addVertex(x, y, z)
                .setUv(u, v)
                .setColor(argb)
                .setLight(light);
    }
}
