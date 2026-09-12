package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.weather.RiftStorm;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Rift Storm looks like from underneath it, and the one place that knows how much storm there is.
 *
 * <p>This class keeps the storm's strength on the client - gathering over five seconds, clearing over
 * five - and everything else a storm draws reads it from here:
 * <ul>
 *   <li>the sky, clouds and daylight, darkened toward violet through {@link RiftStormPalette}. Vanilla
 *       has no event for the sky's colour, so {@code ClientLevelMixin} and {@code LevelRendererMixin}
 *       ask the hooks below; the fog colour does have an event, and is changed here;
 *   <li>the rain, in {@link RiftStormRain}, which also fades vanilla's own rain out;
 *   <li>the aurora, in {@link RiftStormAurora};
 *   <li>bolts of rift fire splitting the sky at a distance, each lighting the sky and the ground violet
 *       for an instant and followed by a low, slowed-down roll of thunder, and a few sparks of the
 *       same fire drifting down through open air - both drawn here.
 * </ul>
 *
 * <p>The darkening is the one thing in this mod that tints anything on purpose, and it tints the way
 * vanilla's own rain does: the sky, the clouds, the fog's colour and the daylight, never the fog's
 * distance and never a wash over the screen. {@link WarpCorridorOverlay} records why the latter two were
 * taken out of the warp corridor - they dyed the deck the player stood on - and none of that is
 * reintroduced here. A storm making the day dimmer is a storm; a storm making the planks purple is paint.
 *
 * <p>Nothing here is synchronised between players. A storm is weather, not an event at a place: two
 * players under it should both see a torn sky, not the same bolt in the same spot. The server says only
 * whether a storm is raging; each client decides for itself where the fire falls.
 *
 * <p>The bolts obey the same {@code riftLightning} setting the apertures' own lightning does, and the
 * sparks the same {@code particleDensity}, so a player who has turned those down for performance does
 * not have them turned back up by the weather. The darkened sky, rain and aurora answer to
 * {@code riftStormWeather}; the flashes also answer to vanilla's own "Hide Lightning Flashes".
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class RiftStormSky {

    /** Ticks for a storm to gather to full strength, or to clear. Weather does not arrive at once. */
    private static final float FADE_TICKS = 100.0F;
    /** Ticks one bolt lasts from strike to dark. */
    private static final float BOLT_LIFE = 10.0F;
    /** Fewest and most ticks between bolts at full strength. */
    private static final int BOLT_LEAST_GAP = 30;
    private static final int BOLT_MOST_GAP = 140;
    /** Points along a bolt's main channel, top to bottom. */
    private static final int SEGMENTS = 10;
    /** Sparks a tick at full strength and a particle density of one. Embers now; the rain is the rain. */
    private static final float SPARKS_PER_TICK = 1.0F;
    /** How much of a flash is lost each tick. A strike lights the sky for about four ticks. */
    private static final float FLASH_DECAY = 0.25F;
    /** How far round the player sparks fall, horizontally. */
    private static final double SPARK_REACH = 14.0D;

    /** A white-violet core inside a violet halo: the palette of the drive's own vented rift. */
    private static final int CORE = 0xF0E6FF;
    private static final int HALO = 0x8A6BFF;
    private static final float CORE_WIDTH = 0.45F;
    private static final float HALO_WIDTH = 2.4F;
    private static final int GLOW_LIGHT = 240;

    private static final List<Bolt> BOLTS = new ArrayList<>();

    private static boolean raging;
    /** How much storm there is over this player, {@code 0..1}. Drives the bolts and sparks. */
    private static float strength;
    private static float lastStrength;
    /** The same, but zero while {@code riftStormWeather} is off. Drives the sky, rain and aurora. */
    private static float weather;
    private static float lastWeather;
    /** How lit the sky is by the last bolt, {@code 0..1}. */
    private static float flash;
    private static float lastFlash;
    private static int boltCountdown = BOLT_LEAST_GAP;

    private RiftStormSky() {
    }

    /** What the server last said. The sky gathers or clears toward it rather than snapping. */
    public static void set(boolean active) {
        raging = active;
    }

    /** Forget the storm entirely, for a client leaving the server it was raging on. */
    public static void clear() {
        raging = false;
        calm();
    }

    private static void calm() {
        strength = 0.0F;
        lastStrength = 0.0F;
        weather = 0.0F;
        lastWeather = 0.0F;
        flash = 0.0F;
        lastFlash = 0.0F;
        BOLTS.clear();
    }

    // ----------------------------------------------------------------- hooks
    //
    // Called by the mixins, on the render thread, many times a frame. Each returns what it was handed
    // untouched unless a storm is actually drawing, so an ordinary sky costs one float comparison.

    /** How much storm weather to draw, between ticks. */
    public static float weather(float partialTick) {
        return Mth.lerp(partialTick, lastWeather, weather);
    }

    private static float skyFlash(float partialTick) {
        return Mth.lerp(partialTick, lastFlash, flash);
    }

    /** {@code ClientLevel.getSkyColor}. */
    public static Vec3 skyColour(Vec3 original, float partialTick) {
        float storm = weather(partialTick);
        if (storm <= 0.0F) {
            return original;
        }
        float[] c = RiftStormPalette.sky((float) original.x, (float) original.y, (float) original.z,
                storm, skyFlash(partialTick));
        return new Vec3(c[0], c[1], c[2]);
    }

    /** {@code ClientLevel.getCloudColor}. */
    public static Vec3 cloudColour(Vec3 original, float partialTick) {
        float storm = weather(partialTick);
        if (storm <= 0.0F) {
            return original;
        }
        float[] c = RiftStormPalette.clouds((float) original.x, (float) original.y, (float) original.z,
                storm, skyFlash(partialTick));
        return new Vec3(c[0], c[1], c[2]);
    }

    /** {@code ClientLevel.getSkyDarken}: the daylight the lightmap is built from. */
    public static float skyLight(float original, float partialTick) {
        float storm = weather(partialTick);
        return storm <= 0.0F ? original : RiftStormPalette.skyLight(original, storm, skyFlash(partialTick));
    }

    /** The rain level {@code LevelRenderer.renderSky} fades the sun, moon and stars by. */
    public static float shroud(float rainLevel) {
        return weather <= 0.0F ? rainLevel : RiftStormPalette.shroud(rainLevel, weather);
    }

    /** The rain level vanilla's own rain and snow are drawn, splashed and heard at. */
    public static float vanillaRain(float rainLevel) {
        return weather <= 0.0F ? rainLevel : RiftStormPalette.vanillaRain(rainLevel, weather);
    }

    /**
     * The fog's colour, which is also the colour the sky meets the ground in.
     *
     * <p>Left alone when the camera is inside anything - water, lava, powder snow - because those set a
     * fog colour of their own that says where the player is, and a storm overhead has no business
     * changing what the inside of a lake looks like.
     */
    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float partialTick = (float) event.getPartialTick();
        float storm = weather(partialTick);
        if (storm <= 0.0F || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float[] c = RiftStormPalette.fog(event.getRed(), event.getGreen(), event.getBlue(),
                storm, skyFlash(partialTick));
        event.setRed(c[0]);
        event.setGreen(c[1]);
        event.setBlue(c[2]);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            calm();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        BOLTS.removeIf(bolt -> ++bolt.age >= BOLT_LIFE);
        lastFlash = flash;
        flash = Math.max(0.0F, flash - FLASH_DECAY);

        // Asked of the level the player is in now, every tick, rather than sent on every change of
        // dimension: walking through a Nether portal mid-storm clears the sky on its own.
        float target = raging && RiftStorm.underOpenSky(level) ? 1.0F : 0.0F;
        lastStrength = strength;
        strength = target > strength
                ? Math.min(target, strength + 1.0F / FADE_TICKS)
                : Math.max(target, strength - 1.0F / FADE_TICKS);
        // Read once a tick here rather than by each hook, which run many times a frame - and before a
        // config has loaded, reading one throws.
        lastWeather = weather;
        weather = AWConfig.RIFT_STORM_WEATHER.get() ? strength : 0.0F;
        if (strength <= 0.0F) {
            return;
        }

        RandomSource random = level.getRandom();
        fallSparks(level, player, random);

        if (AWConfig.RIFT_LIGHTNING.get() && --boltCountdown <= 0) {
            // A gathering storm strikes rarely; only a full one strikes at the full rate.
            int gap = BOLT_LEAST_GAP + random.nextInt(BOLT_MOST_GAP - BOLT_LEAST_GAP + 1);
            boltCountdown = Math.round(gap / Math.max(0.2F, strength));
            strike(minecraft, level, random);
        }
    }

    private static void fallSparks(ClientLevel level, Player player, RandomSource random) {
        float wanted = SPARKS_PER_TICK * strength * (float) (double) AWConfig.PARTICLE_DENSITY.get();
        int count = (int) wanted + (random.nextFloat() < wanted - (int) wanted ? 1 : 0);
        for (int spark = 0; spark < count; spark++) {
            double x = player.getX() + (random.nextDouble() - 0.5D) * 2.0D * SPARK_REACH;
            double y = player.getY() + 1.0D + random.nextDouble() * 10.0D;
            double z = player.getZ() + (random.nextDouble() - 0.5D) * 2.0D * SPARK_REACH;
            if (!level.canSeeSky(BlockPos.containing(x, y, z))) {
                continue;
            }
            level.addParticle(AWParticles.RIFT_SPARK.get(), x, y, z,
                    (random.nextDouble() - 0.5D) * 0.03D,
                    -0.08D - random.nextDouble() * 0.12D,
                    (random.nextDouble() - 0.5D) * 0.03D);
        }
    }

    /**
     * Throws one bolt somewhere in the sky round the player, and the thunder after it.
     *
     * <p>Placed as a fraction of the player's render distance rather than at a fixed range, because
     * geometry is fogged by distance: a bolt a hundred and fifty blocks out is a fine sight at twelve
     * chunks and a faint smear, or nothing, at four.
     */
    private static void strike(Minecraft minecraft, ClientLevel level, RandomSource random) {
        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        double reach = Mth.clamp(minecraft.gameRenderer.getRenderDistance()
                * (0.35D + random.nextDouble() * 0.35D), 32.0D, 180.0D);
        double bearing = random.nextDouble() * Math.PI * 2.0D;
        double x = eye.x + Math.cos(bearing) * reach;
        double z = eye.z + Math.sin(bearing) * reach;
        double top = eye.y + 45.0D + random.nextDouble() * 45.0D;
        double length = 30.0D + random.nextDouble() * 50.0D;

        Bolt bolt = new Bolt();
        bolt.channels.add(channel(random, x, top, z, length, SEGMENTS, 1.0F));
        // Sometimes a fork, split off partway down and thinner than the channel it left.
        if (random.nextBoolean()) {
            double[] main = bolt.channels.get(0).points;
            int from = 2 + random.nextInt(SEGMENTS / 2);
            bolt.channels.add(channel(random, main[from * 3], main[from * 3 + 1], main[from * 3 + 2],
                    length * 0.4D, SEGMENTS / 2, 0.55F));
        }
        BOLTS.add(bolt);

        // The sky and the ground flash with it. Vanilla's accessibility option for lightning flashes is
        // honoured the way vanilla honours it for its own: the bolt is still drawn, the flash is not.
        if (!minecraft.options.hideLightningFlash().get()) {
            flash = 1.0F;
        }

        // Thunder from just beside the listener, off towards the bolt, rather than from the bolt itself:
        // sound fades out entirely within sixteen blocks at any volume up to one, so a roll placed where
        // the bolt is would never be heard. Slowed down, because a distant roll is lower than a crack.
        float volume = 0.6F * strength * (float) (double) AWConfig.EFFECT_VOLUME.get();
        if (volume > 0.0F) {
            level.playLocalSound(eye.x + Math.cos(bearing) * 4.0D, eye.y + 2.0D, eye.z + Math.sin(bearing) * 4.0D,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, volume,
                    0.5F + random.nextFloat() * 0.2F, false);
        }
    }

    /** One jagged line down through the sky, leaning as it goes. */
    private static Channel channel(RandomSource random, double x, double y, double z,
                                   double length, int segments, float width) {
        double leanX = (random.nextDouble() - 0.5D) * length * 0.6D;
        double leanZ = (random.nextDouble() - 0.5D) * length * 0.6D;
        double jag = length / segments * 0.7D;
        double[] points = new double[(segments + 1) * 3];
        for (int point = 0; point <= segments; point++) {
            double t = point / (double) segments;
            // The first point stays where the bolt was struck from, so a fork stays joined to its channel.
            double jitter = point == 0 ? 0.0D : jag;
            points[point * 3] = x + leanX * t + (random.nextDouble() - 0.5D) * 2.0D * jitter;
            points[point * 3 + 1] = y - length * t;
            points[point * 3 + 2] = z + leanZ * t + (random.nextDouble() - 0.5D) * 2.0D * jitter;
        }
        return new Channel(points, width);
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (BOLTS.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eye = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partialTick, lastStrength, strength);
        if (fade <= 0.0F) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        // Positions are written relative to the eye rather than translated there, so a bolt far from
        // the world origin does not lose its shape to float rounding.
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
        for (Bolt bolt : BOLTS) {
            float lit = flash((bolt.age + partialTick) / BOLT_LIFE) * fade;
            if (lit <= 0.001F) {
                continue;
            }
            for (Channel channel : bolt.channels) {
                ribbon(buffer, matrix, eye, channel, HALO_WIDTH * channel.width, HALO, lit * 0.35F);
                ribbon(buffer, matrix, eye, channel, CORE_WIDTH * channel.width, CORE, lit);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);
    }

    /**
     * A strike's brightness through its life: a snap to full, a fast fade, and a second, weaker flicker
     * a few ticks in - the re-strike down the same channel that makes real lightning stutter.
     */
    private static float flash(float progress) {
        float first = RiftLightning.brightness(progress);
        float restrike = 0.7F * RiftLightning.brightness(Mth.clamp((progress - 0.35F) / 0.4F, -1.0F, 1.0F));
        return Math.max(first, progress >= 0.35F ? restrike : 0.0F);
    }

    /** Draws a channel as a chain of flat strips, each turned to face the camera. */
    private static void ribbon(VertexConsumer buffer, Matrix4f matrix, Vec3 eye, Channel channel,
                               float halfWidth, int rgb, float alpha) {
        int argb = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
        double[] p = channel.points;
        Vector3f side = new Vector3f();
        for (int i = 0; i + 1 < p.length / 3; i++) {
            float ax = (float) (p[i * 3] - eye.x);
            float ay = (float) (p[i * 3 + 1] - eye.y);
            float az = (float) (p[i * 3 + 2] - eye.z);
            float bx = (float) (p[(i + 1) * 3] - eye.x);
            float by = (float) (p[(i + 1) * 3 + 1] - eye.y);
            float bz = (float) (p[(i + 1) * 3 + 2] - eye.z);
            // Across the strip is perpendicular both to the strip and to the line of sight to it.
            side.set(bx - ax, by - ay, bz - az).cross((ax + bx) * 0.5F, (ay + by) * 0.5F, (az + bz) * 0.5F);
            if (side.lengthSquared() < 1.0e-6F) {
                continue;
            }
            side.normalize(halfWidth);
            vertex(buffer, matrix, ax - side.x, ay - side.y, az - side.z, argb);
            vertex(buffer, matrix, ax + side.x, ay + side.y, az + side.z, argb);
            vertex(buffer, matrix, bx + side.x, by + side.y, bz + side.z, argb);
            vertex(buffer, matrix, bx - side.x, by - side.y, bz - side.z, argb);
        }
    }

    /**
     * {@code RIFT_FIRE}'s vertex format, which is the beacon beam's: the texture coordinate, light and
     * normal are constants that say nothing, and leaving any of them off throws mid-frame. See
     * {@link RiftShimmer} and {@code RiftVertexFormatTest}.
     */
    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, int argb) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(argb)
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private record Channel(double[] points, float width) {
    }

    private static final class Bolt {
        private final List<Channel> channels = new ArrayList<>(2);
        private float age;
    }
}
