package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;
import uk.co.iceconchy.aerowarptics.fissure.RiftGogglesItem;
import uk.co.iceconchy.aerowarptics.network.ClientboundMeltdownCuePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundScarZonePacket;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;
import uk.co.iceconchy.aerowarptics.wellhead.MeltdownChoreography;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A Rift Wellhead's meltdown as a set piece, the unsettled ground it leaves, and the wound that stands after.
 *
 * <p>Where things are and when is {@link MeltdownChoreography}'s; this draws it, from the camera rather than
 * from a block entity - the machine it happens to is the first thing it destroys, and it is meant to be seen
 * from a long way off. The server says once a second how far through the scene is; between, each client runs
 * it on its own clock.
 *
 * <h2>Dark first</h2>
 * The first version of this was nearly all additive light and a third the size, and at noon there was almost
 * nothing to see (the owner, 2026-10-09). Additive light cannot draw dark and barely shows against a bright
 * sky. So everything that has to be <em>found</em> here is drawn solid, on the membrane's pass, where it hides
 * what is behind it - and the light is laid over it afterwards as trim:
 * <ul>
 *   <li><b>The tear</b>: a black ball, swelling to four tenths of the crater, its limb burning violet to white.</li>
 *   <li><b>The funnel</b>: broken dark rings climbing out of it into the sky and turning, wider as they rise.</li>
 *   <li><b>The heartbeat</b>: a dark ring thrown out along the ground at each beat, quickening.</li>
 *   <li><b>The front</b>: at the detonation, a ball of dark running out from the point with the ground gone
 *       behind it, then drawing back off the crater from the rim inwards.</li>
 * </ul>
 * Over those: the halo, the point in the hush, the burst, the ring of air - and, for anyone near, a white over
 * the whole view for the flash's first second.
 *
 * <p>The ground coming loose and flying is the server's, and real. A player near a swelling tear is drawn
 * towards it by their own client, from the arithmetic the server draws everything else in by.
 *
 * <p>Answers to {@code wellheadSpectacle}, and under it to {@code riftDistortion}, {@code riftLensing},
 * {@code screenShake}, {@code particleDensity} and vanilla's Hide Lightning Flashes. With the trace on it says
 * what it was told and what it drew, so "nothing drew" and "it drew and could not be seen" can be told apart.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WellheadSpectacle {

    private static final int VIOLET = 0x8B4FE0;
    private static final int WHITE = 0xFFFFFF;
    private static final int GLOW_LIGHT = 240;
    private static final int SPHERE_BANDS = 16;
    private static final int SPHERE_SEGMENTS = 32;
    private static final int RING_SEGMENTS = 72;

    /** The funnel: how many rings, how many arcs each is broken into, and how much of the circle they cover. */
    private static final int FUNNEL_RINGS = 12;
    private static final int FUNNEL_ARCS = 3;
    private static final double FUNNEL_COVER = 0.7D;

    /** Ticks a heartbeat's ring takes to run out and fade. */
    private static final int PULSE_TICKS = 26;

    /** Ticks a zone, or a wound, is kept after it was last heard of. */
    private static final int FORGET = 60;

    private static final Map<Long, Show> SHOWS = new HashMap<>();
    private static final Map<Long, Zone> ZONES = new HashMap<>();
    private static final Map<Long, int[]> WOUNDS = new HashMap<>();

    /** The white over the whole view, this tick and last, for the overlay. */
    private static float whiteout;
    private static float lastWhiteout;
    private static boolean toldOff;

    private WellheadSpectacle() {
    }

    public static void accept(ClientboundMeltdownCuePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        long start = level.getGameTime() - packet.elapsed();
        Show show = SHOWS.get(packet.centre().asLong());
        if (show == null) {
            SHOWS.put(packet.centre().asLong(), new Show(packet.centre(), packet.crater(), packet.length(),
                    packet.range(), start, packet.dry()));
            boolean drawn = AWConfig.WELLHEAD_SPECTACLE.get() && AWConfig.RIFT_DISTORTION.get();
            ClientTrace.log(ClientTrace.CUE, "meltdown at {} begun for this client: crater {}, {} ticks, {} in, range {},"
                            + " dry={}, wellheadSpectacle={}, riftDistortion={}", packet.centre(), packet.crater(),
                    packet.length(), packet.elapsed(), packet.range(), packet.dry(), AWConfig.WELLHEAD_SPECTACLE.get(),
                    AWConfig.RIFT_DISTORTION.get());
            // A preview nobody can see is a puzzle: say which switch is off, once, to whoever asked for it.
            if (packet.dry() && !drawn && !toldOff && minecraft.player != null) {
                toldOff = true;
                minecraft.player.displayClientMessage(Component.literal(
                        "AeroWarptics: the meltdown is not being drawn - turn on wellheadSpectacle and riftDistortion"
                                + " in aerowarptics-client.toml"), false);
            }
        } else {
            // Put right by the server's count, without a jump anyone would see: a tick or two either way.
            show.start = start;
        }
    }

    public static void accept(ClientboundScarZonePacket packet) {
        ZONES.put(packet.centre().asLong(), new Zone(Vec3.atCenterOf(packet.centre()), packet.radius(), packet.strength()));
    }

    /**
     * A Rift Wound is standing here: keep what is drawn of it alive for another moment, and let it be wrong at
     * whoever is near. Called from the wound's own client tick.
     *
     * <p>What it does to the air is the opposite of what air does: smoke sinks into it, embers are drawn in
     * rather than thrown off, and now and then something under the ground answers. None of it is the mod's own
     * violet - see {@link #drawWound}.
     */
    public static void tickWound(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        WOUNDS.computeIfAbsent(pos.asLong(), key -> new int[1])[0] = 0;
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        double distance = player.position().distanceTo(centre);
        if (distance > 48.0D) {
            return;
        }
        RandomSource random = level.random;
        double density = AWConfig.PARTICLE_DENSITY.get();
        if (random.nextDouble() < 0.6D * density) {
            // Smoke that falls, from above it, into it.
            level.addParticle(ParticleTypes.LARGE_SMOKE, centre.x + (random.nextDouble() - 0.5D) * 0.8D,
                    centre.y + 2.2D + random.nextDouble() * 1.5D, centre.z + (random.nextDouble() - 0.5D) * 0.8D,
                    0.0D, -0.07D, 0.0D);
        }
        if (random.nextDouble() < 0.5D * density) {
            // Embers drawn in from round about, not thrown off.
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double out = 2.0D + random.nextDouble() * 3.0D;
            double x = centre.x + Math.cos(angle) * out;
            double z = centre.z + Math.sin(angle) * out;
            double y = centre.y - 1.0D + random.nextDouble() * 3.0D;
            level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y, z, (centre.x - x) * 0.06D, (centre.y - y) * 0.06D,
                    (centre.z - z) * 0.06D);
        }
        if (random.nextInt(90) == 0) {
            level.addParticle(ParticleTypes.SCULK_SOUL, centre.x, centre.y - 1.0D, centre.z, 0.0D, 0.04D, 0.0D);
        }
        if (distance < 14.0D) {
            // The day goes a little out of it close to.
            RiftStormSky.lend((float) (0.35D * (1.0D - distance / 14.0D)));
        }
        if (distance < 24.0D && random.nextInt(260) == 0) {
            level.playLocalSound(centre.x, centre.y, centre.z, random.nextBoolean() ? SoundEvents.WARDEN_HEARTBEAT
                    : SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.5F, 0.45F + random.nextFloat() * 0.1F, false);
        }
    }

    public static void clear() {
        SHOWS.clear();
        ZONES.clear();
        WOUNDS.clear();
        whiteout = 0.0F;
        lastWhiteout = 0.0F;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        lastWhiteout = whiteout;
        whiteout = 0.0F;
        for (Iterator<Show> each = SHOWS.values().iterator(); each.hasNext(); ) {
            if (each.next().tick(level, minecraft)) {
                each.remove();
            }
        }
        for (Iterator<Zone> each = ZONES.values().iterator(); each.hasNext(); ) {
            Zone zone = each.next();
            if (++zone.silent > FORGET) {
                each.remove();
            } else {
                zone.tick(level, minecraft);
            }
        }
        WOUNDS.values().removeIf(silent -> ++silent[0] > FORGET);
    }

    /** The white-out: the flash's first second, over everything, for those near enough to have been looking at it. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        float alpha = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false), lastWhiteout, whiteout);
        if (alpha <= 0.004F) {
            return;
        }
        int white = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | 0xFFFFFF;
        event.getGuiGraphics().fill(0, 0, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight(), white);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (SHOWS.isEmpty() && WOUNDS.isEmpty()) {
            return;
        }
        // A wound is drawn whatever the switches say: it is a thing standing in the world, not a show.
        boolean shows = !SHOWS.isEmpty() && AWConfig.WELLHEAD_SPECTACLE.get() && AWConfig.RIFT_DISTORTION.get();
        boolean membranePass = event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        boolean firePass = event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (!membranePass && !firePass) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // Camera-relative throughout: a world coordinate cast to a float is a wobble at the edge of a big world.
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();

        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();

        if (membranePass) {
            if (shows) {
                // Bend first, from a copy of the frame as it stands, then draw the dark over the middle of the bending.
                List<GravityLens.Bend> bends = new ArrayList<>();
                for (Show show : SHOWS.values()) {
                    double radius = show.tear(partialTick);
                    if (radius > 0.05D) {
                        bends.add(new GravityLens.Bend(show.centre, radius,
                                (float) (0.4D + 0.6D * MeltdownChoreography.pull(show.progress(partialTick)))));
                    }
                }
                GravityLens.bend(bends, buffers, camera);
            }
            VertexConsumer solid = buffers.getBuffer(AWRenderTypes.RIFT_MEMBRANE);
            if (shows) {
                for (Show show : SHOWS.values()) {
                    show.drawDark(solid, matrix, eye, partialTick);
                    show.drawn = true;
                }
            }
            for (Long wound : WOUNDS.keySet()) {
                drawWound(solid, matrix, eye, Vec3.atCenterOf(BlockPos.of(wound)), wound, time, true);
            }
            buffers.endBatch(AWRenderTypes.RIFT_MEMBRANE);
            return;
        }

        boolean flashes = !minecraft.options.hideLightningFlash().get();
        VertexConsumer fire = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
        if (shows) {
            for (Show show : SHOWS.values()) {
                show.drawLight(fire, matrix, eye, partialTick, flashes);
            }
        }
        for (Long wound : WOUNDS.keySet()) {
            drawWound(fire, matrix, eye, Vec3.atCenterOf(BlockPos.of(wound)), wound, time, false);
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);
    }

    // ------------------------------------------------------------------ the wound

    /** How many points a wound's crack is drawn through, how many things reach out of it, and how tall its thread is. */
    private static final int WOUND_POINTS = 11;
    private static final int WOUND_TENDRILS = 7;
    private static final double WOUND_THREAD = 110.0D;

    /** Ticks a wound holds one shape before it is suddenly another. It never moves between them. */
    private static final int WOUND_STUTTER = 3;

    /** The colour of what bleeds off it: an arterial red, which nothing else in the mod is. */
    private static final int WOUND_BLEED = 0xC2102A;

    /** A number from -1 to 1 that is always the same for the same three things, and looks like nothing. */
    private static double wrong(long seed, int a, int b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFFFL) / (double) 0xFFFFFL) * 2.0D - 1.0D;
    }

    /**
     * A Rift Wound: something that should not be there, drawn so that it looks it.
     *
     * <p>Every other rift in the mod is a clean shape in the mod's violet that opens, stands and closes smoothly.
     * A wound is none of those things, on purpose (the owner, 2026-10-09: "corrupted, evil and not meant to
     * be"). It is drawn on its own rather than as a held aperture, because an aperture is exactly the tidy thing
     * it must not look like:
     * <ul>
     *   <li><b>A crack, not a hole</b>: a crooked black split taller than the block it stands in, solid, so it is
     *       a strip of nothing by day and by night.</li>
     *   <li><b>It stutters.</b> Its shape is held for a few ticks and is then a different shape, with no motion
     *       between - and every so often it is half as long again for one beat. Nothing in a world moves like that.</li>
     *   <li><b>It reaches.</b> Thin black threads crook out of it into the air and are somewhere else next beat.</li>
     *   <li><b>It bleeds the wrong colour</b>: where its last shape was a moment ago there is an arterial red
     *       ghost, a little to one side, as if the picture had slipped.</li>
     *   <li><b>A thread of dark</b> hangs from the sky down to it, where a landmark would have a beam of light.</li>
     * </ul>
     * Turned to the viewer, so it is never edge-on and gone. Drawn whatever {@code wellheadSpectacle} says: it is
     * a thing standing in the world, not a show.
     *
     * @param dark whether this is the solid pass (the crack, the threads) or the light one (what bleeds off it)
     */
    private static void drawWound(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, long seed, long time,
                                  boolean dark) {
        Vec3 from = centre.subtract(eye);
        double across = Math.sqrt(from.x * from.x + from.z * from.z);
        // Square to the viewer, about the upright: its right is the viewer's right.
        double rx = across < 1.0e-4D ? 1.0D : -from.z / across;
        double rz = across < 1.0e-4D ? 0.0D : from.x / across;
        int beat = (int) (time / WOUND_STUTTER);
        // One beat in eleven it is suddenly longer, and one in seven thinner. Never eased into.
        double spasm = wrong(seed, beat, 901) > 0.82D ? 1.5D : 1.0D;
        double pinch = wrong(seed, beat, 902) > 0.7D ? 0.45D : 1.0D;
        double low = -1.5D * spasm;
        double high = 2.3D * spasm;
        if (dark) {
            crack(consumer, matrix, from, rx, rz, seed, beat, low, high, pinch, 0.0D, 0.0D, true, 0);
            for (int tendril = 0; tendril < WOUND_TENDRILS; tendril++) {
                // Each on its own slower beat, so they are not all somewhere new at once.
                int reach = (int) (time / (WOUND_STUTTER * 2L + tendril));
                double x = 0.0D;
                double y = low + (high - low) * (0.15D + 0.7D * (wrong(seed, tendril, 77) * 0.5D + 0.5D));
                double angle = wrong(seed, tendril * 31 + reach, 5) * Math.PI;
                for (int piece = 0; piece < 5; piece++) {
                    angle += wrong(seed, tendril * 131 + reach, piece) * 1.1D;
                    double length = 0.35D + 0.35D * (wrong(seed, tendril * 17 + reach, piece + 40) * 0.5D + 0.5D);
                    double nx = x + Math.cos(angle) * length;
                    double ny = y + Math.sin(angle) * length;
                    double thick = 0.05D * (1.0D - piece / 6.0D);
                    strip(consumer, matrix, from, rx, rz, x, y, nx, ny, thick, thick * 0.6D, true, 0, 0.0F);
                    x = nx;
                    y = ny;
                }
            }
            // The thread of dark, sky to wound: crooked, and crooked differently each beat.
            double x = 0.0D;
            double y = high;
            for (int piece = 0; piece < 14; piece++) {
                double nx = wrong(seed, beat / 2, 300 + piece) * 0.22D;
                double ny = high + WOUND_THREAD * (piece + 1) / 14.0D;
                strip(consumer, matrix, from, rx, rz, x, y, nx, ny, 0.07D, 0.07D, true, 0, 0.0F);
                x = nx;
                y = ny;
            }
            return;
        }
        // What bleeds off it: the shape it was a beat ago, slipped to one side, in the wrong colour; and a hem of the
        // same round the shape it is now, so that at night there is an edge to the black.
        double slip = wrong(seed, beat, 903) * 0.16D;
        crack(consumer, matrix, from, rx, rz, seed, beat - 1, low, high, 1.0D, slip, 0.05D, false, WOUND_BLEED);
        crack(consumer, matrix, from, rx, rz, seed, beat, low, high, pinch, 0.0D, 0.09D, false, WOUND_BLEED);
        if (wrong(seed, beat, 904) > 0.55D) {
            // The thread, caught for a beat in the same red.
            strip(consumer, matrix, from, rx, rz, 0.0D, high, 0.0D, high + WOUND_THREAD, 0.16D, 0.05D, false, WOUND_BLEED, 0.35F);
        }
    }

    /** The crack itself: a crooked strip from its foot to its head, widest a little above the middle. */
    private static void crack(VertexConsumer consumer, Matrix4f matrix, Vec3 from, double rx, double rz, long seed, int beat,
                              double low, double high, double pinch, double slip, double hem, boolean dark, int colour) {
        double lastX = 0.0D;
        double lastY = 0.0D;
        double lastWide = 0.0D;
        for (int point = 0; point < WOUND_POINTS; point++) {
            double share = point / (double) (WOUND_POINTS - 1);
            double y = low + (high - low) * share;
            double x = slip + wrong(seed, beat, point) * 0.3D * Math.sin(Math.PI * share);
            double wide = hem + pinch * (0.05D + 0.3D * Math.pow(Math.sin(Math.PI * Math.pow(share, 0.8D)), 1.5D)
                    * (0.6D + 0.4D * wrong(seed, beat, point + 50)));
            if (point > 0) {
                strip(consumer, matrix, from, rx, rz, lastX, lastY, x, y, lastWide, Math.max(0.0D, wide), dark, colour, 0.55F);
            }
            lastX = x;
            lastY = y;
            lastWide = Math.max(0.0D, wide);
        }
    }

    /**
     * One piece of a strip in the wound's own plane, from one point to the next, each with its own half-width.
     * Solid and all but black on the dark pass; on the light pass, a colour at an alpha.
     */
    private static void strip(VertexConsumer consumer, Matrix4f matrix, Vec3 from, double rx, double rz, double x0,
                              double y0, double x1, double y1, double wide0, double wide1, boolean dark, int colour,
                              float alpha) {
        double[][] corners = {{x0 - wide0, y0}, {x0 + wide0, y0}, {x1 + wide1, y1}, {x1 - wide1, y1}};
        for (double[] corner : corners) {
            double x = from.x + rx * corner[0];
            double y = from.y + corner[1];
            double z = from.z + rz * corner[0];
            if (dark) {
                solid(consumer, matrix, x, y, z, 0.012F, 0.0F, 0.008F);
            } else {
                vertex(consumer, matrix, x, y, z, colour, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    /** A ball, opaque and writing depth: dark face-on, and catching a colour where its surface turns edge-on. */
    private static void ball(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, double radius, Vec3 eye, int limb,
                             float brightness) {
        float red = ((limb >> 16) & 0xFF) / 255.0F * brightness;
        float green = ((limb >> 8) & 0xFF) / 255.0F * brightness;
        float blue = (limb & 0xFF) / 255.0F * brightness;
        for (int band = 0; band < SPHERE_BANDS; band++) {
            double lat0 = -Math.PI / 2.0D + Math.PI * band / SPHERE_BANDS;
            double lat1 = -Math.PI / 2.0D + Math.PI * (band + 1) / SPHERE_BANDS;
            for (int segment = 0; segment < SPHERE_SEGMENTS; segment++) {
                double lon0 = Math.PI * 2.0D * segment / SPHERE_SEGMENTS;
                double lon1 = Math.PI * 2.0D * (segment + 1) / SPHERE_SEGMENTS;
                ballVertex(consumer, matrix, centre, radius, lat0, lon0, eye, red, green, blue);
                ballVertex(consumer, matrix, centre, radius, lat0, lon1, eye, red, green, blue);
                ballVertex(consumer, matrix, centre, radius, lat1, lon1, eye, red, green, blue);
                ballVertex(consumer, matrix, centre, radius, lat1, lon0, eye, red, green, blue);
            }
        }
    }

    private static void ballVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, double radius, double latitude,
                                   double longitude, Vec3 eye, float red, float green, float blue) {
        double nx = Math.cos(latitude) * Math.cos(longitude);
        double ny = Math.sin(latitude);
        double nz = Math.cos(latitude) * Math.sin(longitude);
        double x = centre.x + nx * radius;
        double y = centre.y + ny * radius;
        double z = centre.z + nz * radius;
        double vx = eye.x - x;
        double vy = eye.y - y;
        double vz = eye.z - z;
        double length = Math.max(1.0e-6D, Math.sqrt(vx * vx + vy * vy + vz * vz));
        // Edge-on to the eye, the limb glows; face-on, nothing comes back out.
        float edge = (float) Math.pow(1.0D - Math.abs((nx * vx + ny * vy + nz * vz) / length), 2.5D);
        solid(consumer, matrix, x - eye.x, y - eye.y, z - eye.z, 0.02F + red * edge, green * edge, 0.035F + blue * edge);
    }

    /** The one place a solid vertex is written: plain position and colour, the membrane's format. */
    private static void solid(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, float red,
                              float green, float blue) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(red, green, blue, 1.0F);
    }

    /** A flat band in the horizontal plane, solid, between two angles: a ring, or a piece of one. */
    private static void solidArc(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, double x, double y, double z,
                                 double inner, double outer, double from, double to, float red, float green, float blue) {
        int steps = Math.max(2, (int) Math.ceil(RING_SEGMENTS * (to - from) / (Math.PI * 2.0D)));
        for (int step = 0; step < steps; step++) {
            double a0 = from + (to - from) * step / steps;
            double a1 = from + (to - from) * (step + 1) / steps;
            solid(consumer, matrix, x + Math.cos(a0) * inner - eye.x, y - eye.y, z + Math.sin(a0) * inner - eye.z, red, green, blue);
            solid(consumer, matrix, x + Math.cos(a1) * inner - eye.x, y - eye.y, z + Math.sin(a1) * inner - eye.z, red, green, blue);
            solid(consumer, matrix, x + Math.cos(a1) * outer - eye.x, y - eye.y, z + Math.sin(a1) * outer - eye.z, red, green, blue);
            solid(consumer, matrix, x + Math.cos(a0) * outer - eye.x, y - eye.y, z + Math.sin(a0) * outer - eye.z, red, green, blue);
        }
    }

    /** The same band in light, its alpha running from the inner edge to the outer. */
    private static void lightArc(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, double x, double y, double z,
                                 double inner, double outer, double from, double to, int colour, float innerAlpha,
                                 float outerAlpha) {
        if (innerAlpha <= 0.001F && outerAlpha <= 0.001F) {
            return;
        }
        int steps = Math.max(2, (int) Math.ceil(RING_SEGMENTS * (to - from) / (Math.PI * 2.0D)));
        for (int step = 0; step < steps; step++) {
            double a0 = from + (to - from) * step / steps;
            double a1 = from + (to - from) * (step + 1) / steps;
            vertex(consumer, matrix, x + Math.cos(a0) * inner - eye.x, y - eye.y, z + Math.sin(a0) * inner - eye.z, colour, innerAlpha);
            vertex(consumer, matrix, x + Math.cos(a1) * inner - eye.x, y - eye.y, z + Math.sin(a1) * inner - eye.z, colour, innerAlpha);
            vertex(consumer, matrix, x + Math.cos(a1) * outer - eye.x, y - eye.y, z + Math.sin(a1) * outer - eye.z, colour, outerAlpha);
            vertex(consumer, matrix, x + Math.cos(a0) * outer - eye.x, y - eye.y, z + Math.sin(a0) * outer - eye.z, colour, outerAlpha);
        }
    }

    /** An annulus square to the line of sight, round a point - a glow seen the same from anywhere. */
    private static void facingAnnulus(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, double inner,
                                      double outer, int colour, float innerAlpha, float outerAlpha) {
        if (innerAlpha <= 0.001F && outerAlpha <= 0.001F) {
            return;
        }
        Vec3 from = centre.subtract(eye);
        if (from.lengthSqr() < 1.0e-9D) {
            return;
        }
        Vec3 forward = from.normalize();
        Vec3 right = Math.abs(forward.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 up = right.cross(forward);
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double a0 = Math.PI * 2.0D * segment / RING_SEGMENTS;
            double a1 = Math.PI * 2.0D * (segment + 1) / RING_SEGMENTS;
            facingVertex(consumer, matrix, from, right, up, a0, inner, colour, innerAlpha);
            facingVertex(consumer, matrix, from, right, up, a1, inner, colour, innerAlpha);
            facingVertex(consumer, matrix, from, right, up, a1, outer, colour, outerAlpha);
            facingVertex(consumer, matrix, from, right, up, a0, outer, colour, outerAlpha);
        }
    }

    private static void facingVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 right, Vec3 up,
                                     double angle, double radius, int colour, float alpha) {
        double c = Math.cos(angle) * radius;
        double s = Math.sin(angle) * radius;
        vertex(consumer, matrix, from.x + right.x * c + up.x * s, from.y + right.y * c + up.y * s,
                from.z + right.z * c + up.z * s, colour, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, int colour,
                               float alpha) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor((Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (colour & 0xFFFFFF))
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static int mix(int from, int to, float t) {
        int red = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return (red << 16) | (green << 8) | blue;
    }

    // ------------------------------------------------------------------ one meltdown

    private static final class Show {

        private final Vec3 centre;
        private final int crater;
        private final int length;
        private final int range;
        private final boolean dry;
        private long start;
        private double progress;
        private double lastProgress;
        private int beats;
        private MeltdownChoreography.Beat beat;
        /** The age in ticks of each heartbeat's ring still running out. */
        private final List<int[]> pulses = new ArrayList<>();
        private int clock;
        /** Whether a frame of this has been drawn, for the trace. */
        private boolean drawn;

        private Show(BlockPos centre, int crater, int length, int range, long start, boolean dry) {
            this.centre = Vec3.atCenterOf(centre);
            this.crater = crater;
            this.length = length;
            this.range = range;
            this.start = start;
            this.dry = dry;
        }

        private double progress(float partialTick) {
            return Mth.lerp(partialTick, lastProgress, progress);
        }

        private double tear(float partialTick) {
            return MeltdownChoreography.tear(progress(partialTick), crater);
        }

        private double funnelHeight() {
            return Math.min(170.0D, crater * 5.0D + 40.0D);
        }

        /** @return whether it is over */
        private boolean tick(ClientLevel level, Minecraft minecraft) {
            clock++;
            lastProgress = progress;
            progress = Math.max(0.0D, (level.getGameTime() - start) / (double) length);
            if (progress >= 1.0D) {
                ClientTrace.log(ClientTrace.CUE, "meltdown at {} over for this client (a frame was drawn: {})", centre, drawn);
                return true;
            }
            MeltdownChoreography.Beat now = MeltdownChoreography.Beat.at(progress);
            LocalPlayer player = minecraft.player;
            double distance = player == null ? 1.0e9D : player.position().distanceTo(centre);
            double felt = MeltdownChoreography.felt(distance, range);
            if (now != beat) {
                beat = now;
                ClientTrace.log(ClientTrace.CUE, "meltdown at {} is now {}: {} blocks off, felt {}, tear {} blocks, dark {},"
                                + " a frame drawn so far: {}", centre, now, (int) distance, felt,
                        MeltdownChoreography.tear(progress, crater), MeltdownChoreography.darkness(progress), drawn);
            }
            pulses.removeIf(pulse -> ++pulse[0] > PULSE_TICKS);
            if (!AWConfig.WELLHEAD_SPECTACLE.get() || felt <= 0.0D) {
                return false;
            }
            RiftStormSky.lend((float) (MeltdownChoreography.darkness(progress) * Math.min(1.0D, felt * 2.0D)));
            double near = 1.0D - CollapseChoreography.smooth(CollapseChoreography.span(distance, crater, crater * 6.0D + 32.0D));
            // Added every tick, and a shake decays by a seventh a tick, so what this settles at is seven times
            // what is added: a fifth of the shake's range at the height of the swell, where the first figure here
            // pinned it to the top for twenty seconds on end (the owner, 2026-10-09: "too disorientating").
            double shake = AWConfig.WELLHEAD_SHAKE.get();
            WarpScreenShake.add((float) (0.02D * MeltdownChoreography.shake(progress) * near * shake));
            whiteout = Math.max(whiteout, (float) (MeltdownChoreography.whiteout(progress) * near
                    * (minecraft.options.hideLightningFlash().get() ? 0.25D : 1.0D)));

            int counted = (int) Math.floor(MeltdownChoreography.heartbeats(progress, length));
            if (counted > beats && now == MeltdownChoreography.Beat.SWELL) {
                pulses.add(new int[1]);
                WarpScreenShake.add((float) (0.1D * near * shake));
                level.playLocalSound(centre.x, centre.y, centre.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS,
                        (float) (6.0D * felt), 0.6F, false);
            }
            beats = counted;
            if (lastProgress < MeltdownChoreography.HUSH_END && progress >= MeltdownChoreography.HUSH_END) {
                detonation(level, distance);
            }
            particles(level, level.random, now);
            if (!dry) {
                pull(player);
            }
            return false;
        }

        private void detonation(ClientLevel level, double distance) {
            if (distance < range) {
                // Vanilla's own lightning flash, so Hide Lightning Flashes turns it off without any help.
                level.setSkyFlashTime(6);
            }
            // The jolt: once, and gone inside a second.
            WarpScreenShake.add((float) (0.6D * AWConfig.WELLHEAD_SHAKE.get()
                    * (1.0D - CollapseChoreography.span(distance, crater, crater * 8.0D + 64.0D))));
            level.addParticle(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
            RandomSource random = level.random;
            double density = AWConfig.PARTICLE_DENSITY.get();
            for (int i = 0; i < (int) (8 * density); i++) {
                Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                        .scale(crater * 0.5D * random.nextDouble());
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, centre.x + way.x, centre.y + way.y, centre.z + way.z, 0.0D, 0.0D, 0.0D);
            }
            for (int i = 0; i < (int) ((300 + crater * 12) * density); i++) {
                Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
                double speed = 0.8D + random.nextDouble() * (1.0D + crater * 0.06D);
                level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : AWParticles.RIFT_SPARK.get(), centre.x, centre.y,
                        centre.z, way.x * speed, way.y * speed, way.z * speed);
            }
        }

        /** Dust: bursting off the frame, streaming up and in through the swell, drifting down through the fallout. */
        private void particles(ClientLevel level, RandomSource random, MeltdownChoreography.Beat now) {
            double density = AWConfig.PARTICLE_DENSITY.get();
            if (now == MeltdownChoreography.Beat.BREACH) {
                for (int i = 0; i < (int) (14 * density); i++) {
                    Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
                    level.addParticle(ParticleTypes.CLOUD, centre.x + way.x * 2.0D, centre.y + way.y * 2.0D,
                            centre.z + way.z * 2.0D, way.x * 0.5D, way.y * 0.5D, way.z * 0.5D);
                    level.addParticle(AWParticles.RIFT_SPARK.get(), centre.x, centre.y, centre.z, way.x * 0.9D, way.y * 0.9D,
                            way.z * 0.9D);
                }
            } else if (now == MeltdownChoreography.Beat.SWELL) {
                double pull = MeltdownChoreography.pull(progress);
                double reach = crater * (0.8D + 1.6D * pull);
                for (int i = 0; i < (int) ((14 + crater) * density); i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double out = reach * (0.3D + 0.7D * random.nextDouble());
                    double x = centre.x + Math.cos(angle) * out;
                    double z = centre.z + Math.sin(angle) * out;
                    double y = centre.y - crater * 0.4D + random.nextDouble() * crater * 0.8D;
                    // In, round and up: the spiral the server's loose ground flies.
                    double speed = 0.2D + 0.5D * pull;
                    level.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : i % 3 == 1 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.ASH,
                            x, y, z, (centre.x - x) / out * speed - Math.sin(angle) * 0.2D, 0.1D + random.nextDouble() * 0.15D,
                            (centre.z - z) / out * speed + Math.cos(angle) * 0.2D);
                }
            } else if (now == MeltdownChoreography.Beat.FALLOUT) {
                for (int i = 0; i < (int) ((8 + crater / 2) * density); i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double out = crater * 1.4D * Math.sqrt(random.nextDouble());
                    level.addParticle(i % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.SMOKE, centre.x + Math.cos(angle) * out,
                            centre.y - crater * 0.3D + random.nextDouble() * crater * 1.3D, centre.z + Math.sin(angle) * out,
                            0.0D, -0.05D, 0.0D);
                }
            }
        }

        /** The player's own client draws them in - a player's movement is theirs, and the server pulls the rest. */
        private void pull(LocalPlayer player) {
            if (player == null || player.isSpectator() || player.isPassenger() || player.getAbilities().flying) {
                return;
            }
            double strength = MeltdownChoreography.pull(progress);
            double toX = centre.x - player.getX();
            double toZ = centre.z - player.getZ();
            double across = Math.sqrt(toX * toX + toZ * toZ);
            double reach = 1.0D - CollapseChoreography.span(across, MeltdownChoreography.tear(progress, crater), crater * 3.0D);
            if (strength <= 0.0D || reach <= 0.0D) {
                return;
            }
            Vec3 velocity = player.getDeltaMovement();
            double[] now = CollapseChoreography.pulled(velocity.x, velocity.z, toX, toZ, 0.14D * strength * reach, 0.9D);
            player.setDeltaMovement(now[0], velocity.y, now[1]);
        }

        /** Everything that is dark, and so can be seen by day: the tear, the funnel, the heartbeat's rings, the front. */
        private void drawDark(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, float partialTick) {
            double now = progress(partialTick);
            int limb = mix(VIOLET, WHITE, (float) MeltdownChoreography.whiteness(now));
            double radius = tear(partialTick);
            if (radius > 0.05D) {
                ball(consumer, matrix, centre, radius, eye, limb, 0.75F);
            }
            double climbed = MeltdownChoreography.funnel(now);
            if (climbed > 0.01D && radius > 0.05D) {
                double height = funnelHeight();
                double turn = (clock + partialTick) * 0.035D;
                for (int ring = 0; ring < FUNNEL_RINGS; ring++) {
                    double share = (ring + 0.5D) / FUNNEL_RINGS;
                    if (share > climbed) {
                        break;
                    }
                    double wide = radius * (0.8D + 2.2D * share);
                    // Each ring its own way round and its own pace, so the funnel is seen to turn rather than slide.
                    double spin = turn * (ring % 2 == 0 ? 1.0D : -1.4D) * (1.0D + ring * 0.12D) + ring * 1.7D;
                    for (int arc = 0; arc < FUNNEL_ARCS; arc++) {
                        double from = spin + Math.PI * 2.0D * arc / FUNNEL_ARCS;
                        solidArc(consumer, matrix, eye, centre.x, centre.y + radius + share * height, centre.z, wide * 0.86D,
                                wide, from, from + Math.PI * 2.0D / FUNNEL_ARCS * FUNNEL_COVER, 0.13F, 0.04F, 0.24F);
                    }
                }
            }
            for (int[] pulse : pulses) {
                double out = (pulse[0] + partialTick) / PULSE_TICKS;
                double reach = Math.max(radius, 1.0D) + out * crater * 2.2D;
                solidArc(consumer, matrix, eye, centre.x, centre.y - 0.5D, centre.z, reach - 0.2D - crater * 0.03D * (1.0D - out),
                        reach, 0.0D, Math.PI * 2.0D, 0.16F, 0.05F, 0.3F);
            }
            double front = MeltdownChoreography.front(now) * crater;
            if (front > 0.3D) {
                // The unmaking itself: a ball of dark with a white edge, and inside it nothing at all.
                ball(consumer, matrix, centre, front, eye, WHITE, 1.0F);
            }
        }

        /** Everything that is light, laid over the dark: halo, funnel trim, point, burst, the front's edge, the ring of air. */
        private void drawLight(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, float partialTick, boolean flashes) {
            double now = progress(partialTick);
            int colour = mix(VIOLET, WHITE, (float) MeltdownChoreography.whiteness(now));
            double radius = tear(partialTick);
            if (radius > 0.05D) {
                float halo = (float) (0.45D + 0.45D * MeltdownChoreography.pull(now));
                facingAnnulus(consumer, matrix, eye, centre, radius * 0.97D, radius * 1.1D, colour, halo, halo * 0.7F);
                facingAnnulus(consumer, matrix, eye, centre, radius * 1.1D, radius * 2.6D, colour, halo * 0.7F, 0.0F);
                // The disc: what it has swallowed so far, turning round it flat.
                lightArc(consumer, matrix, eye, centre.x, centre.y, centre.z, radius * 1.15D, radius * 2.4D, 0.0D,
                        Math.PI * 2.0D, colour, halo * 0.8F, 0.0F);
                double climbed = MeltdownChoreography.funnel(now);
                double height = funnelHeight();
                double turn = (clock + partialTick) * 0.035D;
                for (int ring = 0; ring < FUNNEL_RINGS && (ring + 0.5D) / FUNNEL_RINGS <= climbed; ring++) {
                    double share = (ring + 0.5D) / FUNNEL_RINGS;
                    double wide = radius * (0.8D + 2.2D * share);
                    double spin = turn * (ring % 2 == 0 ? 1.0D : -1.4D) * (1.0D + ring * 0.12D) + ring * 1.7D;
                    for (int arc = 0; arc < FUNNEL_ARCS; arc++) {
                        double from = spin + Math.PI * 2.0D * arc / FUNNEL_ARCS;
                        lightArc(consumer, matrix, eye, centre.x, centre.y + radius + share * height, centre.z, wide,
                                wide * 1.08D, from, from + Math.PI * 2.0D / FUNNEL_ARCS * FUNNEL_COVER, colour,
                                (float) (0.8D * (1.0D - share * 0.6D)), 0.0F);
                    }
                }
            }
            for (int[] pulse : pulses) {
                double out = (pulse[0] + partialTick) / PULSE_TICKS;
                double reach = Math.max(radius, 1.0D) + out * crater * 2.2D;
                lightArc(consumer, matrix, eye, centre.x, centre.y - 0.45D, centre.z, reach, reach + 1.0D + crater * 0.05D, 0.0D,
                        Math.PI * 2.0D, colour, (float) (0.9D * (1.0D - out)), 0.0F);
            }
            if (MeltdownChoreography.hangs(now)) {
                // The hush: everything drawn down to one white point, brighter as the tear round it goes.
                float point = (float) CollapseChoreography.span(now, MeltdownChoreography.SWELL_END, MeltdownChoreography.HUSH_END);
                double size = 0.5D + crater * 0.05D * point;
                facingAnnulus(consumer, matrix, eye, centre, 0.0D, size, WHITE, point, point * 0.6F);
                facingAnnulus(consumer, matrix, eye, centre, size, size * 8.0D, WHITE, point * 0.6F, 0.0F);
            }
            float flash = (float) MeltdownChoreography.flash(now) * (flashes ? 1.0F : 0.3F);
            if (flash > 0.001F) {
                double burst = crater * (0.6D + 1.8D * MeltdownChoreography.carve(now));
                facingAnnulus(consumer, matrix, eye, centre, 0.0D, burst * 0.6D, WHITE, flash, flash * 0.8F);
                facingAnnulus(consumer, matrix, eye, centre, burst * 0.6D, burst * 1.8D, VIOLET, flash * 0.8F, 0.0F);
            }
            double front = MeltdownChoreography.front(now) * crater;
            if (front > 0.3D) {
                facingAnnulus(consumer, matrix, eye, centre, front * 0.96D, front * 1.04D, WHITE, 1.0F, 1.0F);
                facingAnnulus(consumer, matrix, eye, centre, front * 1.04D, front * 1.3D, VIOLET, 0.8F, 0.0F);
            }
            float ring = (float) MeltdownChoreography.ringStrength(now);
            if (ring > 0.001F) {
                double out = Math.max(0.5D, MeltdownChoreography.ring(now) * crater);
                double width = 2.0D + crater * 0.3D * ring;
                lightArc(consumer, matrix, eye, centre.x, centre.y, centre.z, Math.max(0.0D, out - width), out, 0.0D,
                        Math.PI * 2.0D, WHITE, 0.0F, ring);
                lightArc(consumer, matrix, eye, centre.x, centre.y, centre.z, out, out + width * 0.5D, 0.0D, Math.PI * 2.0D,
                        VIOLET, ring, 0.0F);
            }
        }
    }

    // ------------------------------------------------------------------ unsettled ground

    /**
     * A scar zone, as this client sees it. Without Rift Goggles all that shows is that the air is wrong: motes
     * rising, a dim sky, a ticking that quickens towards the middle. With them, sparks mark where the edge is.
     * No line and no ring either way - it is ground that has not settled, not a boundary somebody drew.
     */
    private static final class Zone {

        private final Vec3 centre;
        private final int radius;
        private final float strength;
        private int silent;

        private Zone(Vec3 centre, int radius, float strength) {
            this.centre = centre;
            this.radius = radius;
            this.strength = strength;
        }

        private void tick(ClientLevel level, Minecraft minecraft) {
            LocalPlayer player = minecraft.player;
            if (player == null || strength <= 0.0F || !AWConfig.WELLHEAD_SPECTACLE.get()) {
                return;
            }
            double distance = player.position().distanceTo(centre);
            double depth = Math.max(0.0D, 1.0D - distance / radius);
            RandomSource random = level.random;
            double density = AWConfig.PARTICLE_DENSITY.get();
            if (distance < radius + 32.0D) {
                for (int i = 0; i < (int) Math.ceil((3 + radius / 8.0D) * density * strength); i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double out = radius * Math.sqrt(random.nextDouble());
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, centre.x + Math.cos(angle) * out,
                            centre.y - radius * 0.5D + random.nextDouble() * radius, centre.z + Math.sin(angle) * out,
                            0.0D, 0.03D + random.nextDouble() * 0.04D, 0.0D);
                }
                if (RiftGogglesItem.isWorn(player)) {
                    for (int i = 0; i < (int) Math.ceil(6 * density); i++) {
                        double angle = random.nextDouble() * Math.PI * 2.0D;
                        level.addParticle(AWParticles.RIFT_SPARK.get(), centre.x + Math.cos(angle) * radius,
                                player.getY() + random.nextDouble() * 3.0D, centre.z + Math.sin(angle) * radius, 0.0D, 0.02D, 0.0D);
                    }
                }
            }
            if (depth <= 0.0D) {
                return;
            }
            RiftStormSky.lend((float) (0.45D * strength * (0.3D + 0.7D * depth)));
            // A counter's tick, quicker the deeper in: one in twenty ticks at the rim, most ticks at the middle.
            if (random.nextDouble() < strength * (0.05D + 0.6D * depth * depth)) {
                level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.AMBIENT,
                        0.35F, 1.4F + random.nextFloat() * 0.5F, false);
            }
        }
    }
}
