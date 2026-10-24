package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.collapse.CollapseRules;
import uk.co.iceconchy.aerowarptics.collapse.CradleRing;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Collapse as a set piece: everything a Collapse Cradle's ritual looks, sounds and feels like round it.
 *
 * <p>Where things are and when is {@link CollapseChoreography}'s; this draws it. A ritual is a landmark, so
 * most of it is drawn here, in the world, from the camera, rather than by the cradle's renderer - which a
 * block entity only gets within a few dozen blocks, and only while its box is on screen:
 * <ul>
 *   <li><b>The rift</b> it opens, while it stands, is a held aperture in {@link RiftEffectManager}, dressed by
 *       a Rift Modulator beside the cradle - its shatter, its theme and its seal are all the aperture's own.</li>
 *   <li><b>The hole</b> it folds into is a ball of darkness, opaque and writing depth, so the disc of light
 *       turning round it passes behind it and the offerings in orbit vanish into it. Space bends round it
 *       through {@link GravityLens}, and a thin ring of light hugs its edge so it reads against a night sky.</li>
 *   <li><b>The column</b> climbing out of it into the sky is what is seen from far off, with rings of light
 *       running down it into the hole. The day dims round the site through {@link RiftStormSky#lend}.</li>
 *   <li><b>How unsteady it is</b> shows everywhere at once, from where its instability is heading rather than
 *       where it has got to, so a bad cage shows from the start: the hole wobbles, the disc flickers and sags
 *       towards the side the stabilisers do not stand on, the heart beats faster, lightning strikes the
 *       ground, and the camera trembles.</li>
 *   <li><b>The hush and the moment</b>: the loops cut out, streaks of light fall into the point, and then the
 *       sky flashes, a ring of air runs out along the ground and the camera jolts. The boom itself is the
 *       server's, so it carries further than any client draws.</li>
 * </ul>
 *
 * <p>The offerings, the core and the heart are drawn by {@code CollapseCradleRenderer}, since they are items
 * and that is where the item renderer is to hand; it asks this for the heart's beat, the orbit's tilt and a
 * settled core's descent.
 *
 * <p>A player standing near a running ritual is drawn in by their own client, from the same arithmetic the
 * server draws everything else in by - a player's movement is theirs.
 *
 * <p>Answers to the same settings the rest of the mod's effects do: {@code riftDistortion} for everything drawn
 * in the world, {@code riftLightning} for the lightning, {@code riftLensing} for the bending, {@code screenShake}
 * and {@code effectVolume}, and vanilla's Hide Lightning Flashes for the flash.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class CollapseSpectacle {

    /** Undressed, a ritual burns the Singularity's own deep violet. */
    private static final int CRADLE_COLOUR = 0x7A2BD8;
    private static final int CRADLE_OPEN_TICKS = 40;

    /** How long a show outlives its collapse: the flash, the ring of air, and a settled core coming down. */
    private static final int AFTERMATH = 70;
    /** How long a settled core takes to come down out of the point onto its cradle. */
    public static final int SETTLE_TICKS = uk.co.iceconchy.aerowarptics.collapse.CradleWorks.RETURN_TICKS;
    /** Ticks a show survives unwatched - its cradle broken, its chunk gone - before it is forgotten. */
    private static final int FORGET_AFTER = 5;
    private static final int SURVEY_INTERVAL = 20;

    /** How far away the flash still lights a viewer's sky, and the jolt and the dimming are worked out from. */
    private static final double FLASH_REACH = 96.0D;
    /** How long the ring of air takes to run out, and the flash to fade. */
    private static final double RING_TICKS = 18.0D;
    private static final double BURST_TICKS = 7.0D;
    /** How far out along the ground the ring of air runs, as a share of the blast's range, and at least. */
    private static final double RING_OVER_BLAST = 1.6D;
    private static final double RING_LEAST = 6.0D;

    /** How far the orbit's plane sags away from a lopsided cage, and wobbles when unsteady - see {@link CollapseChoreography#orbitPoint}. */
    private static final double SAG = 0.35D;
    private static final double WOBBLE = 0.22D;

    /** Light written on every glowing vertex: a ritual lights itself. */
    private static final int GLOW_LIGHT = 240;

    private static final int SPHERE_BANDS = 12;
    private static final int SPHERE_SEGMENTS = 24;
    private static final int RING_SEGMENTS = 48;
    private static final int DISC_RINGS = 6;
    private static final int DISC_SEGMENTS = 64;
    private static final int BOLT_POINTS = 12;
    private static final int HUSH_STREAKS = 24;

    /** The hole's own dark, and how much of the ritual's colour its limb catches. */
    private static final float HOLE_RED = 0.02F;
    private static final float HOLE_GREEN = 0.0F;
    private static final float HOLE_BLUE = 0.035F;
    private static final float LIMB = 0.45F;

    private static final Map<Long, Show> SHOWS = new HashMap<>();

    private CollapseSpectacle() {
    }

    // ------------------------------------------------------------------ asked by the cradle

    /** Called from a cradle's own client tick: follows its ritual, starting a show when one begins. */
    public static void watch(CollapseCradleBlockEntity cradle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            return;
        }
        long key = cradle.getBlockPos().asLong();
        Show show = SHOWS.get(key);
        if (show == null) {
            if (!cradle.running()) {
                return;
            }
            show = new Show(cradle, level);
            SHOWS.put(key, show);
        }
        show.unwatched = 0;
        show.dress(cradle.modulator());
        if (cradle.running()) {
            show.follow(cradle);
        } else if (show.climaxAge < 0) {
            // It stopped without collapsing - the cradle was broken, or the server let it go.
            show.dispose();
            SHOWS.remove(key);
            return;
        }
        show.tick(level, minecraft);
    }

    /** The cradle's collapse count moved while this client watched: the moment itself. */
    public static void collapsed(CollapseCradleBlockEntity cradle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            return;
        }
        long key = cradle.getBlockPos().asLong();
        Show show = SHOWS.computeIfAbsent(key, ignored -> new Show(cradle, level));
        show.dress(cradle.modulator());
        show.climax(level, minecraft, outcome(cradle.lastOutcome()));
    }

    /** How hard the Scar Heart is beating, 0..1, for the renderer: a thump that dies away. */
    public static float beat(BlockPos cradle, float partialTick) {
        Show show = SHOWS.get(cradle.asLong());
        if (show == null) {
            return 0.0F;
        }
        float since = show.sinceBeat + partialTick;
        return (float) Math.exp(-since / 3.0D);
    }

    /** How the orbit's plane is tipped, {slope along x, slope along z}, for the renderer. */
    public static double[] tilt(BlockPos cradle, float partialTick) {
        Show show = SHOWS.get(cradle.asLong());
        if (show == null) {
            return new double[]{0.0D, 0.0D};
        }
        return new double[]{Mth.lerp(partialTick, show.lastTiltX, show.tiltX),
                Mth.lerp(partialTick, show.lastTiltZ, show.tiltZ)};
    }

    /**
     * How far a settled core has come down out of the point onto its cradle, 0..1, for the renderer - or
     * less than 0 if none is coming down.
     */
    public static float settling(BlockPos cradle, float partialTick) {
        Show show = SHOWS.get(cradle.asLong());
        if (show == null || show.climaxAge < 0 || show.outcome != CollapseRules.Outcome.SETTLED) {
            return -1.0F;
        }
        return Math.min(1.0F, (show.climaxAge + partialTick) / SETTLE_TICKS);
    }

    public static void clear() {
        for (Show show : SHOWS.values()) {
            show.dispose();
        }
        SHOWS.clear();
    }

    private static CollapseRules.Outcome outcome(String id) {
        for (CollapseRules.Outcome outcome : CollapseRules.Outcome.values()) {
            if (outcome.id().equals(id)) {
                return outcome;
            }
        }
        return CollapseRules.Outcome.SETTLED;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (SHOWS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        Iterator<Show> shows = SHOWS.values().iterator();
        while (shows.hasNext()) {
            Show show = shows.next();
            if (show.level != minecraft.level || ++show.unwatched > FORGET_AFTER
                    || (show.climaxAge >= 0 && show.climaxAge > AFTERMATH)) {
                show.dispose();
                shows.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (SHOWS.isEmpty() || !AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
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

        if (membranePass) {
            // Bend first, from a copy of the frame as it stands, then draw the hole over the middle of the
            // bending - so the hole's edge stays crisp and the sky round it is what is pulled about.
            List<GravityLens.Bend> bends = new ArrayList<>();
            for (Show show : SHOWS.values()) {
                double radius = show.holeRadius(partialTick);
                float strength = (float) CollapseChoreography.lens(show.progress(partialTick));
                if (radius > 0.01D && strength > 0.001F) {
                    bends.add(new GravityLens.Bend(show.holeCentre(partialTick), 1.5D * radius / Nebula.REACH, strength));
                }
            }
            GravityLens.bend(bends, buffers, camera);

            VertexConsumer solid = buffers.getBuffer(AWRenderTypes.RIFT_MEMBRANE);
            for (Show show : SHOWS.values()) {
                drawHole(solid, matrix, show, eye, partialTick);
            }
            buffers.endBatch(AWRenderTypes.RIFT_MEMBRANE);
            return;
        }

        VertexConsumer fire = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
        boolean lightning = AWConfig.RIFT_LIGHTNING.get();
        boolean flashes = !minecraft.options.hideLightningFlash().get();
        for (Show show : SHOWS.values()) {
            drawColumn(fire, matrix, show, eye, partialTick);
            drawDisc(fire, matrix, show, eye, partialTick);
            drawPhotonRing(fire, matrix, show, eye, partialTick);
            if (lightning) {
                drawBolts(fire, matrix, show, eye, partialTick);
            }
            drawHushStreaks(fire, matrix, show, eye, partialTick);
            drawAirRing(fire, matrix, show, eye, partialTick);
            drawBurst(fire, matrix, show, eye, partialTick, flashes);
            drawSettleGlow(fire, matrix, show, eye, partialTick);
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);
    }

    /**
     * The hole: a ball of darkness, its limb catching a little of the ritual's colour so it has a shape
     * rather than being a cut-out. Opaque, and it writes depth, so the half of the disc behind it and any
     * offering that falls into it are gone.
     */
    private static void drawHole(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        double radius = show.holeRadius(partialTick);
        if (radius <= 0.01D) {
            return;
        }
        Vec3 centre = show.holeCentre(partialTick);
        float limbRed = ((show.colour >> 16) & 0xFF) / 255.0F * LIMB;
        float limbGreen = ((show.colour >> 8) & 0xFF) / 255.0F * LIMB;
        float limbBlue = (show.colour & 0xFF) / 255.0F * LIMB;
        for (int band = 0; band < SPHERE_BANDS; band++) {
            double lat0 = -Math.PI / 2.0D + Math.PI * band / SPHERE_BANDS;
            double lat1 = -Math.PI / 2.0D + Math.PI * (band + 1) / SPHERE_BANDS;
            for (int segment = 0; segment < SPHERE_SEGMENTS; segment++) {
                double lon0 = Math.PI * 2.0D * segment / SPHERE_SEGMENTS;
                double lon1 = Math.PI * 2.0D * (segment + 1) / SPHERE_SEGMENTS;
                holeVertex(consumer, matrix, centre, radius, lat0, lon0, eye, limbRed, limbGreen, limbBlue);
                holeVertex(consumer, matrix, centre, radius, lat0, lon1, eye, limbRed, limbGreen, limbBlue);
                holeVertex(consumer, matrix, centre, radius, lat1, lon1, eye, limbRed, limbGreen, limbBlue);
                holeVertex(consumer, matrix, centre, radius, lat1, lon0, eye, limbRed, limbGreen, limbBlue);
            }
        }
    }

    private static void holeVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, double radius,
                                   double latitude, double longitude, Vec3 eye, float limbRed, float limbGreen,
                                   float limbBlue) {
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
        double facing = Math.abs((nx * vx + ny * vy + nz * vz) / length);
        float edge = (float) Math.pow(1.0D - facing, 2.5D);
        solid(consumer, matrix, x - eye.x, y - eye.y, z - eye.z,
                HOLE_RED + limbRed * edge, HOLE_GREEN + limbGreen * edge, HOLE_BLUE + limbBlue * edge);
    }

    /**
     * The column: a beam out of the top of the hole into the sky, seen for miles, with rings of light running
     * down it into the hole. Crossed rather than turned to face the viewer, as a summoning beam is, because it
     * is a landmark people walk round.
     */
    private static void drawColumn(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        double progress = show.progress(partialTick);
        double standing = CollapseChoreography.column(progress);
        if (standing <= 0.001D) {
            return;
        }
        Vec3 centre = show.holeCentre(partialTick);
        double foot = centre.y + show.holeRadius(partialTick);
        double height = CollapseChoreography.COLUMN_HEIGHT * standing;
        double time = show.clock + partialTick;
        float flicker = 1.0F - show.unrest * 0.3F * (0.5F + 0.5F * (float) Math.sin(time * 1.3D));
        double core = 0.08D + 0.2D * progress;
        float alpha = (float) Math.min(1.0D, standing * 3.0D) * flicker;
        pane(consumer, matrix, centre, foot, height, core, show.accent, alpha * 0.9F, eye);
        pane(consumer, matrix, centre, foot, height, core * 3.5D, show.colour, alpha * 0.18F, eye);
        for (int ring = 0; ring < 6; ring++) {
            double travel = (time * 2.5D + ring * (height / 6.0D)) % Math.max(1.0D, height);
            double y = foot + height - travel;
            float fade = (float) (travel / Math.max(1.0D, height));
            flatRing(consumer, matrix, centre.x, y, centre.z, core * 2.0D, core * 6.0D * (1.0D - fade * 0.6D),
                    show.accent, alpha * 0.35F * fade, eye);
        }
    }

    private static void pane(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, double foot, double height,
                             double half, int colour, float alpha, Vec3 eye) {
        double top = foot + height;
        quad(consumer, matrix, eye, colour, alpha,
                centre.x - half, foot, centre.z, centre.x + half, foot, centre.z,
                centre.x + half, top, centre.z, centre.x - half, top, centre.z);
        quad(consumer, matrix, eye, colour, alpha,
                centre.x, foot, centre.z - half, centre.x, foot, centre.z + half,
                centre.x, top, centre.z + half, centre.x, top, centre.z - half);
    }

    /**
     * The disc of light turning round the hole: brightest at its inner edge, laid in spiral arms that wind as
     * the orbit quickens, brighter on the side turning towards the viewer, and flickering when unsteady. It
     * lies in the orbit's own plane, so it sags with it, and the half behind the hole is hidden by it.
     */
    private static void drawDisc(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        double progress = show.progress(partialTick);
        double whole = CollapseChoreography.disc(progress);
        double hole = show.holeRadius(partialTick);
        if (whole <= 0.001D || hole <= 0.01D) {
            return;
        }
        Vec3 centre = show.holeCentre(partialTick);
        double inner = hole * 1.2D;
        double outer = Math.max(inner + 0.5D, CollapseChoreography.orbitRadius(progress) + 0.9D);
        double tiltX = Mth.lerp(partialTick, show.lastTiltX, show.tiltX);
        double tiltZ = Mth.lerp(partialTick, show.lastTiltZ, show.tiltZ);
        double time = show.clock + partialTick;
        double spin = Math.PI * 2.0D * CollapseChoreography.orbitTurns(progress) * 1.3D + time * 0.1D;
        double hushBright = 1.0D + 0.6D * CollapseChoreography.hush(progress);
        float flicker = 1.0F - show.unrest * 0.35F * (float) Math.abs(Math.sin(time * 0.9D + Math.sin(time * 2.7D)));
        for (int ring = 0; ring < DISC_RINGS; ring++) {
            double t0 = ring / (double) DISC_RINGS;
            double t1 = (ring + 1) / (double) DISC_RINGS;
            double r0 = Mth.lerp(t0, inner, outer);
            double r1 = Mth.lerp(t1, inner, outer);
            for (int segment = 0; segment < DISC_SEGMENTS; segment++) {
                double a0 = Math.PI * 2.0D * segment / DISC_SEGMENTS;
                double a1 = Math.PI * 2.0D * (segment + 1) / DISC_SEGMENTS;
                discVertex(consumer, matrix, show, centre, eye, r0, a0, t0, inner, spin, tiltX, tiltZ, whole * hushBright, flicker);
                discVertex(consumer, matrix, show, centre, eye, r0, a1, t0, inner, spin, tiltX, tiltZ, whole * hushBright, flicker);
                discVertex(consumer, matrix, show, centre, eye, r1, a1, t1, inner, spin, tiltX, tiltZ, whole * hushBright, flicker);
                discVertex(consumer, matrix, show, centre, eye, r1, a0, t1, inner, spin, tiltX, tiltZ, whole * hushBright, flicker);
            }
        }
    }

    private static void discVertex(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 centre, Vec3 eye,
                                   double radius, double angle, double t, double inner, double spin,
                                   double tiltX, double tiltZ, double strength, float flicker) {
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;
        double x = centre.x + dx;
        double y = centre.y - (tiltX * dx + tiltZ * dz);
        double z = centre.z + dz;
        // Bright at the inner edge, gone at the outer: t is 0 at the hole and 1 at the rim.
        double profile = CollapseChoreography.smooth(t / 0.15D) * Math.pow(1.0D - t, 1.5D);
        double arms = 0.55D + 0.45D * Math.cos(3.0D * angle - 5.0D * Math.log(radius / inner + 1.0D) - spin);
        // The side turning towards the viewer is the brighter, as light off anything that fast is.
        double vx = eye.x - x;
        double vy = eye.y - y;
        double vz = eye.z - z;
        double length = Math.max(1.0e-6D, Math.sqrt(vx * vx + vy * vy + vz * vz));
        double towards = (-Math.sin(angle) * vx + Math.cos(angle) * vz) / length;
        double doppler = 1.0D + 0.5D * towards;
        float alpha = (float) Mth.clamp(strength * profile * arms * doppler * flicker * 0.9D, 0.0D, 1.0D);
        int colour = mix(show.accent, show.colour, (float) t);
        vertex(consumer, matrix, (float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z), colour, alpha);
    }

    /** A thin ring of light hugging the hole's edge, square to the viewer: what gives a ball of dark its outline. */
    private static void drawPhotonRing(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        double hole = show.holeRadius(partialTick);
        if (hole <= 0.01D) {
            return;
        }
        float strength = (float) (0.4D + 0.6D * CollapseChoreography.disc(show.progress(partialTick)));
        Vec3 centre = show.holeCentre(partialTick);
        facingAnnulus(consumer, matrix, eye, centre, hole, hole * 1.07D, show.accent, 0.0F, 0.9F * strength);
        facingAnnulus(consumer, matrix, eye, centre, hole * 1.07D, hole * 1.5D, show.accent, 0.9F * strength, 0.0F);
    }

    /** Lightning between the hole and each stabiliser holding it, and down to the ground when it gets away. */
    private static void drawBolts(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        if (show.bolts.isEmpty()) {
            return;
        }
        Vec3 centre = show.holeCentre(partialTick);
        double hole = show.holeRadius(partialTick);
        for (Bolt bolt : show.bolts) {
            Vec3 toward = bolt.target.subtract(centre);
            double length = toward.length();
            if (length < 0.2D) {
                continue;
            }
            Vec3 from = centre.add(toward.scale(Math.min(hole, length) / length));
            float fade = Math.max(0.0F, (bolt.life + 1.0F - partialTick) / (bolt.lifetime + 1.0F));
            ribbon(consumer, matrix, eye, bolt, from, bolt.target, 0.07D, show.accent, 0.85F * fade);
            ribbon(consumer, matrix, eye, bolt, from, bolt.target, 0.025D, 0xFFFFFF, 0.9F * fade);
        }
    }

    /** In the hush, streaks of light falling into the point from every side. */
    private static void drawHushStreaks(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        double hush = CollapseChoreography.hush(show.progress(partialTick));
        if (hush <= 0.0D || show.climaxAge >= 0) {
            return;
        }
        Vec3 centre = show.holeCentre(partialTick);
        double time = show.clock + partialTick;
        for (int streak = 0; streak < HUSH_STREAKS; streak++) {
            double yaw = RiftShatter.noise(show.seed, 900 + streak) * Math.PI * 2.0D;
            double pitch = (RiftShatter.noise(show.seed, 950 + streak) - 0.5D) * Math.PI * 0.8D;
            double dx = Math.cos(pitch) * Math.cos(yaw);
            double dy = Math.sin(pitch);
            double dz = Math.cos(pitch) * Math.sin(yaw);
            double phase = (time * 0.07D + streak * 0.618D) % 1.0D;
            double near = 0.3D + 6.5D * (1.0D - phase);
            double far = near + 1.6D;
            Vec3 a = centre.add(dx * far, dy * far, dz * far);
            Vec3 b = centre.add(dx * near, dy * near, dz * near);
            streak(consumer, matrix, eye, a, b, 0.035D, show.accent, (float) (phase * 0.8D * Math.min(1.0D, hush * 3.0D)));
        }
    }

    /** The ring of air thrown out along the ground at the moment, and a second, fainter, square to the viewer. */
    private static void drawAirRing(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        if (show.climaxAge < 0) {
            return;
        }
        double age = show.climaxAge + partialTick;
        double t = age / RING_TICKS;
        if (t >= 1.0D) {
            return;
        }
        double out = 1.0D - (1.0D - t) * (1.0D - t);
        double reach = Math.max(RING_LEAST, serverInt(AWConfig.COLLAPSE_BLAST_RANGE, 8) * RING_OVER_BLAST)
                * CollapseChoreography.blastForce(show.outcome) / CollapseChoreography.blastForce(CollapseRules.Outcome.FLUNG);
        double radius = 0.5D + out * reach;
        double width = 0.4D + 1.6D * (1.0D - t);
        float alpha = (float) Math.pow(1.0D - t, 1.5D);
        double y = show.base.y + 0.06D;
        flatAnnulus(consumer, matrix, eye, show.base.x, y, show.base.z, Math.max(0.0D, radius - width), radius,
                show.accent, 0.0F, alpha);
        flatAnnulus(consumer, matrix, eye, show.base.x, y, show.base.z, radius, radius + width * 0.5D,
                show.accent, alpha, 0.0F);
        double sky = age / (RING_TICKS * 0.6D);
        if (sky < 1.0D) {
            double shell = 0.5D + (1.0D - (1.0D - sky) * (1.0D - sky)) * 7.0D;
            float shellAlpha = (float) (0.5D * (1.0D - sky));
            facingAnnulus(consumer, matrix, eye, show.centre, shell * 0.9D, shell, show.colour, 0.0F, shellAlpha);
            facingAnnulus(consumer, matrix, eye, show.centre, shell, shell * 1.06D, show.colour, shellAlpha, 0.0F);
        }
    }

    /** The flash: a ball of light at the point, swelling and gone in a few ticks - dimmed for Hide Lightning Flashes. */
    private static void drawBurst(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick,
                                  boolean flashes) {
        if (show.climaxAge < 0) {
            return;
        }
        double t = (show.climaxAge + partialTick) / BURST_TICKS;
        if (t >= 1.0D) {
            return;
        }
        double radius = 0.6D + 4.5D * (1.0D - (1.0D - t) * (1.0D - t));
        float alpha = (float) ((1.0D - t) * (1.0D - t)) * (flashes ? 1.0F : 0.35F);
        facingAnnulus(consumer, matrix, eye, show.centre, 0.0D, radius * 0.5D, 0xFFFFFF, alpha, alpha * 0.8F);
        facingAnnulus(consumer, matrix, eye, show.centre, radius * 0.5D, radius, show.accent, alpha * 0.8F, 0.0F);
    }

    /** A soft glow round a settled core as it comes down onto the cradle. */
    private static void drawSettleGlow(VertexConsumer consumer, Matrix4f matrix, Show show, Vec3 eye, float partialTick) {
        if (show.climaxAge < 0 || show.outcome != CollapseRules.Outcome.SETTLED) {
            return;
        }
        double age = show.climaxAge + partialTick;
        double fade = 1.0D - age / (SETTLE_TICKS + 20.0D);
        if (fade <= 0.0D) {
            return;
        }
        double down = CollapseChoreography.smooth(Math.min(1.0D, age / SETTLE_TICKS));
        double height = Mth.lerp(down, CollapseChoreography.CENTRE, CollapseChoreography.HOVER);
        Vec3 at = show.base.add(0.0D, height, 0.0D);
        facingAnnulus(consumer, matrix, eye, at, 0.0D, 0.6D, show.accent, (float) (0.6D * fade), 0.0F);
    }

    // ------------------------------------------------------------------ shapes

    /** A flat ring lying across the column. */
    private static void flatRing(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z, double inner,
                                 double outer, int colour, float alpha, Vec3 eye) {
        flatAnnulus(consumer, matrix, eye, x, y, z, inner, outer, colour, alpha, 0.0F);
    }

    /** A flat annulus in the horizontal plane, its alpha running from the inner edge to the outer. */
    private static void flatAnnulus(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, double x, double y, double z,
                                    double inner, double outer, int colour, float innerAlpha, float outerAlpha) {
        if (innerAlpha <= 0.001F && outerAlpha <= 0.001F) {
            return;
        }
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double a0 = Math.PI * 2.0D * segment / RING_SEGMENTS;
            double a1 = Math.PI * 2.0D * (segment + 1) / RING_SEGMENTS;
            vertex(consumer, matrix, (float) (x + Math.cos(a0) * inner - eye.x), (float) (y - eye.y),
                    (float) (z + Math.sin(a0) * inner - eye.z), colour, innerAlpha);
            vertex(consumer, matrix, (float) (x + Math.cos(a1) * inner - eye.x), (float) (y - eye.y),
                    (float) (z + Math.sin(a1) * inner - eye.z), colour, innerAlpha);
            vertex(consumer, matrix, (float) (x + Math.cos(a1) * outer - eye.x), (float) (y - eye.y),
                    (float) (z + Math.sin(a1) * outer - eye.z), colour, outerAlpha);
            vertex(consumer, matrix, (float) (x + Math.cos(a0) * outer - eye.x), (float) (y - eye.y),
                    (float) (z + Math.sin(a0) * outer - eye.z), colour, outerAlpha);
        }
    }

    /** An annulus square to the line of sight, round a point - a glow seen the same from anywhere. */
    private static void facingAnnulus(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, double inner,
                                      double outer, int colour, float innerAlpha, float outerAlpha) {
        if (innerAlpha <= 0.001F && outerAlpha <= 0.001F) {
            return;
        }
        double fx = centre.x - eye.x;
        double fy = centre.y - eye.y;
        double fz = centre.z - eye.z;
        double distance = Math.max(1.0e-6D, Math.sqrt(fx * fx + fy * fy + fz * fz));
        fx /= distance;
        fy /= distance;
        fz /= distance;
        double rx;
        double rz;
        if (Math.abs(fy) > 0.99D) {
            rx = 1.0D;
            rz = 0.0D;
        } else {
            double across = Math.sqrt(fx * fx + fz * fz);
            rx = -fz / across;
            rz = fx / across;
        }
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double a0 = Math.PI * 2.0D * segment / RING_SEGMENTS;
            double a1 = Math.PI * 2.0D * (segment + 1) / RING_SEGMENTS;
            facingVertex(consumer, matrix, eye, centre, rx, rz, ux, uy, uz, a0, inner, colour, innerAlpha);
            facingVertex(consumer, matrix, eye, centre, rx, rz, ux, uy, uz, a1, inner, colour, innerAlpha);
            facingVertex(consumer, matrix, eye, centre, rx, rz, ux, uy, uz, a1, outer, colour, outerAlpha);
            facingVertex(consumer, matrix, eye, centre, rx, rz, ux, uy, uz, a0, outer, colour, outerAlpha);
        }
    }

    private static void facingVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, double rx,
                                     double rz, double ux, double uy, double uz, double angle, double radius,
                                     int colour, float alpha) {
        double c = Math.cos(angle) * radius;
        double s = Math.sin(angle) * radius;
        vertex(consumer, matrix, (float) (centre.x + rx * c + ux * s - eye.x), (float) (centre.y + uy * s - eye.y),
                (float) (centre.z + rz * c + uz * s - eye.z), colour, alpha);
    }

    /** A jagged bolt between two points, pinned at both ends, as a ribbon turned to the viewer. */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Bolt bolt, Vec3 from, Vec3 to,
                               double width, int colour, float alpha) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        Vec3 side = along.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0e-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 lift = along.cross(side).normalize();
        double jag = Math.min(0.6D, length * 0.08D);
        Vec3 previous = from;
        for (int point = 1; point <= BOLT_POINTS; point++) {
            double t = point / (double) BOLT_POINTS;
            double pinned = Math.sin(Math.PI * t);
            double a = (RiftShatter.noise(bolt.seed, point * 2) - 0.5D) * 2.0D * jag * pinned;
            double b = (RiftShatter.noise(bolt.seed, point * 2 + 1) - 0.5D) * 2.0D * jag * pinned;
            Vec3 next = from.add(along.scale(t)).add(side.scale(a)).add(lift.scale(b));
            streak(consumer, matrix, eye, previous, next, width, colour, alpha);
            previous = next;
        }
    }

    /** A straight stroke of light between two points, turned to the viewer. */
    private static void streak(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 a, Vec3 b, double width,
                               int colour, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }
        Vec3 along = b.subtract(a);
        Vec3 toEye = eye.subtract(a.add(b).scale(0.5D));
        Vec3 side = along.cross(toEye);
        if (side.lengthSqr() < 1.0e-9D) {
            return;
        }
        side = side.normalize().scale(width);
        quad(consumer, matrix, eye, colour, alpha,
                a.x - side.x, a.y - side.y, a.z - side.z, a.x + side.x, a.y + side.y, a.z + side.z,
                b.x + side.x, b.y + side.y, b.z + side.z, b.x - side.x, b.y - side.y, b.z - side.z);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, int colour, float alpha,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3) {
        vertex(consumer, matrix, (float) (x0 - eye.x), (float) (y0 - eye.y), (float) (z0 - eye.z), colour, alpha);
        vertex(consumer, matrix, (float) (x1 - eye.x), (float) (y1 - eye.y), (float) (z1 - eye.z), colour, alpha);
        vertex(consumer, matrix, (float) (x2 - eye.x), (float) (y2 - eye.y), (float) (z2 - eye.z), colour, alpha);
        vertex(consumer, matrix, (float) (x3 - eye.x), (float) (y3 - eye.y), (float) (z3 - eye.z), colour, alpha);
    }

    /**
     * The one place a glowing vertex is written. {@code RIFT_FIRE} is the beacon beam's format, so a texture
     * coordinate, a light level and a normal go with every colour - constants that say nothing, and without
     * which the buffer throws mid-frame. See {@code RiftVertexFormatTest}.
     */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int colour,
                               float alpha) {
        consumer.addVertex(matrix, x, y, z)
                .setColor((Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (colour & 0xFFFFFF))
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** The one place the hole's opaque vertex is written: plain position and colour, the membrane's format. */
    private static void solid(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                              float red, float green, float blue) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z).setColor(red, green, blue, 1.0F);
    }

    private static int mix(int from, int to, float t) {
        int red = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return (red << 16) | (green << 8) | blue;
    }

    /**
     * A server setting, read on the client - where it arrives with the server's config. Before it has, the
     * shipped default: nothing drawn from it decides anything.
     */
    private static int serverInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (RuntimeException notYetLoaded) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ one ritual

    /** Lightning, alive for a few ticks. */
    private static final class Bolt {
        private final Vec3 target;
        private final int seed;
        private final int lifetime;
        private int life;

        private Bolt(Vec3 target, int seed, int lifetime) {
            this.target = target;
            this.seed = seed;
            this.lifetime = lifetime;
            this.life = lifetime;
        }
    }

    /** One ritual as this client is showing it. */
    private static final class Show {
        private final long key;
        private final BlockPos pos;
        private final ClientLevel level;
        /** The middle of the cradle's base, and of the hole over it. */
        private final Vec3 base;
        private final Vec3 centre;
        private final int seed;

        private int colour = CRADLE_COLOUR;
        private int accent = CRADLE_COLOUR;
        private RiftModulatorTheme theme = RiftModulatorTheme.STANDARD;

        private float progress;
        private float lastProgress;
        private boolean seen;
        private float unrest;
        private boolean held;

        private int unwatched;
        private long clock;
        private boolean apertureOpen;

        private final List<Vec3> stabilisers = new ArrayList<>();
        /** How far the cradle looks for a stabiliser, as the server told it. */
        private final int reach;
        private int surveyIn;
        private double leanX;
        private double leanZ;
        private double tiltX;
        private double tiltZ;
        private double lastTiltX;
        private double lastTiltZ;

        private int beatIn;
        private int sinceBeat = 99;

        private final List<Bolt> bolts = new ArrayList<>();
        private int boltSeed;

        private Loop drone;
        private Loop howl;

        private int climaxAge = -1;
        private CollapseRules.Outcome outcome = CollapseRules.Outcome.SETTLED;

        private Show(CollapseCradleBlockEntity cradle, ClientLevel level) {
            this.pos = cradle.getBlockPos().immutable();
            this.key = pos.asLong();
            this.level = level;
            this.base = Vec3.atBottomCenterOf(pos);
            this.centre = base.add(0.0D, CollapseChoreography.CENTRE, 0.0D);
            this.seed = Long.hashCode(pos.asLong() * 31L + 7L);
            this.boltSeed = seed;
            this.reach = cradle.stabiliserReach();
        }

        private float progress(float partialTick) {
            return Mth.lerp(partialTick, lastProgress, progress);
        }

        private double holeRadius(float partialTick) {
            return climaxAge >= 0 ? 0.0D : CollapseChoreography.holeRadius(progress(partialTick));
        }

        /** The hole's middle, shaken about by how unsteady it is - and harder in the hush. */
        private Vec3 holeCentre(float partialTick) {
            double time = clock + partialTick;
            // Small: the hole hangs inside the Relic Armillary's innermost ring, and a hole that shook as far as it
            // once did would shake into it.
            double shake = 0.015D + 0.05D * unrest + 0.03D * CollapseChoreography.hush(progress(partialTick));
            return centre.add(shake * Math.sin(time * 1.7D + seed % 7), shake * Math.sin(time * 2.3D),
                    shake * Math.cos(time * 1.9D + seed % 5));
        }

        private void dress(RiftModulatorBlockEntity modulator) {
            if (modulator != null) {
                colour = modulator.colour() & 0xFFFFFF;
                accent = modulator.accentColour() & 0xFFFFFF;
                theme = modulator.theme();
            } else {
                colour = CRADLE_COLOUR;
                accent = CRADLE_COLOUR;
                theme = RiftModulatorTheme.STANDARD;
            }
        }

        private void follow(CollapseCradleBlockEntity cradle) {
            lastProgress = progress;
            progress = cradle.progress(1.0F);
            if (!seen) {
                // Arriving mid-ritual: nothing it already passed is played again.
                lastProgress = progress;
                seen = true;
            }
            unrest = cradle.unrest();
            held = cradle.held();
        }

        private boolean crossed(double beat) {
            return lastProgress < beat && progress >= beat;
        }

        private void tick(ClientLevel level, Minecraft minecraft) {
            clock++;
            lastTiltX = tiltX;
            lastTiltZ = tiltZ;
            if (climaxAge >= 0) {
                aftermath(level);
                return;
            }
            RandomSource random = level.random;
            LocalPlayer player = minecraft.player;
            double distance = player == null ? 1.0e9D : player.position().distanceTo(centre);

            aperture(minecraft);
            cues(level, distance);
            loops(minecraft);
            heart(level);
            survey(level);
            lean();
            lightning(level, random);
            particles(level, random);

            RiftStormSky.lend((float) (CollapseChoreography.darkness(progress)
                    * CollapseChoreography.darknessReach(distance)));
            double near = 1.0D - CollapseChoreography.smooth(CollapseChoreography.span(distance, 4.0D, 32.0D));
            if (progress >= CollapseChoreography.HUSH) {
                WarpScreenShake.add((float) (0.045D * near));
            } else if (progress >= CollapseChoreography.WINDING_FROM) {
                WarpScreenShake.add((float) (0.012D * unrest * near));
            }
            if (!held) {
                pull(player);
            }
        }

        /** The rift, while it stands: torn at the end of the spin-up, sealed onto the hole at the fold. */
        private void aperture(Minecraft minecraft) {
            double radius = CollapseChoreography.apertureRadius(progress);
            if (radius <= 0.0D) {
                if (apertureOpen) {
                    RiftEffectManager.seal(key);
                    apertureOpen = false;
                }
                return;
            }
            Vec3 toViewer = minecraft.gameRenderer.getMainCamera().getPosition().subtract(centre);
            if (toViewer.lengthSqr() < 1.0e-6D) {
                toViewer = new Vec3(0.0D, 0.0D, 1.0D);
            }
            RiftEffectManager.hold(key, centre, toViewer.normalize(), radius, radius, colour, accent, theme,
                    CRADLE_OPEN_TICKS, false);
            RiftEffectManager.aim(key, toViewer.normalize());
            apertureOpen = true;
        }

        /** The beats heard as they pass: the tear, the fold, the in-breath. */
        private void cues(ClientLevel level, double distance) {
            if (crossed(CollapseRules.SPIN_UP_END)) {
                play(level, AWSounds.COLLAPSE_TEAR.get(), 3.0F, 1.0F);
                play(level, AWSounds.RIFT_OPEN.get(), 1.5F, 0.6F);
                WarpScreenShake.add((float) (0.3D * (1.0D - CollapseChoreography.span(distance, 4.0D, 32.0D))));
            }
            if (crossed(CollapseChoreography.FOLD)) {
                play(level, AWSounds.COLLAPSE_FOLD.get(), 2.0F, 0.8F);
            }
            if (crossed(CollapseChoreography.HUSH)) {
                play(level, AWSounds.COLLAPSE_INHALE.get(), 3.0F, 1.0F);
                play(level, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 2.0F, 0.5F);
            }
        }

        /** The drone and the howl, rising with the ritual - and cut dead at the hush, which is the point of it. */
        private void loops(Minecraft minecraft) {
            if (drone != null && drone.isStopped()) {
                drone = null;
            }
            if (howl != null && howl.isStopped()) {
                howl = null;
            }
            if (drone == null && droneVoice() != null) {
                drone = new Loop(AWSounds.COLLAPSE_DRONE.get(), this::droneVoice);
                minecraft.getSoundManager().play(drone);
            }
            if (howl == null && howlVoice() != null) {
                howl = new Loop(AWSounds.COLLAPSE_HOWL.get(), this::howlVoice);
                minecraft.getSoundManager().play(howl);
            }
        }

        private Voice droneVoice() {
            if (climaxAge >= 0 || progress < CollapseRules.SPIN_UP_END || progress >= CollapseChoreography.HUSH
                    || SHOWS.get(key) != this) {
                return null;
            }
            float pitch = 0.5F + 0.6F * progress + unrest * 0.08F * (float) Math.sin(clock * 0.3D);
            float volume = 0.5F + 0.5F * (float) CollapseChoreography.span(progress, CollapseRules.SPIN_UP_END,
                    CollapseChoreography.FOLD);
            // Stalled, it sags - a machine winding down under load.
            return new Voice(centre, held ? pitch * 0.7F : pitch, volume);
        }

        private Voice howlVoice() {
            if (climaxAge >= 0 || progress < CollapseChoreography.WINDING_FROM || progress >= CollapseChoreography.HUSH
                    || SHOWS.get(key) != this) {
                return null;
            }
            float rise = (float) CollapseChoreography.span(progress, CollapseChoreography.WINDING_FROM,
                    CollapseChoreography.HUSH);
            return new Voice(centre, 0.5F + 0.5F * progress, (0.15F + 0.6F * rise) * (0.6F + 0.4F * unrest));
        }

        /** The Scar Heart beating in its cup, quicker as the ritual winds and when it is unsteady. */
        private void heart(ClientLevel level) {
            sinceBeat++;
            if (progress >= CollapseChoreography.HUSH) {
                return;
            }
            if (--beatIn <= 0) {
                beatIn = CollapseChoreography.heartbeat(progress, unrest);
                sinceBeat = 0;
                playAt(level, base.add(0.0D, CollapseChoreography.CUP, 0.0D), AWSounds.COLLAPSE_HEARTBEAT.get(), 1.5F,
                        0.8F + 0.3F * progress);
            }
        }

        /**
         * Which stabilisers stand switched on round the cradle, and which way the cage leans - by
         * {@link CradleRing}'s walk, so the lightning reaches for the ones the server is counting.
         */
        private void survey(ClientLevel level) {
            if (--surveyIn > 0) {
                return;
            }
            surveyIn = SURVEY_INTERVAL;
            stabilisers.clear();
            List<double[]> offsets = new ArrayList<>();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int[] offset : CradleRing.stabilisers(reach, (dx, dy, dz) ->
                    SpatialStabiliserBlock.switchedOn(level.getBlockState(at.setWithOffset(pos, dx, dy, dz))))) {
                stabilisers.add(Vec3.atCenterOf(pos.offset(offset[0], offset[1], offset[2])).add(0.0D, 0.3D, 0.0D));
                offsets.add(new double[]{offset[0], offset[1], offset[2]});
            }
            leanX = 0.0D;
            leanZ = 0.0D;
            if (!offsets.isEmpty()) {
                for (double[] offset : offsets) {
                    leanX += offset[0];
                    leanZ += offset[2];
                }
                leanX /= offsets.size() * (double) reach;
                leanZ /= offsets.size() * (double) reach;
            }
            // The lean's length is the very imbalance the server damps by (CollapseInstability.imbalance), bar
            // the height: a cage that sags here is one that is winding up there.
        }

        /**
         * The orbit's plane sags away from the side the stabilisers stand on, and wobbles round as the ritual
         * grows unsteady - the plainest way to see a lopsided cage from across the site.
         */
        private void lean() {
            double wobble = WOBBLE * unrest;
            double wantX = -leanX * SAG + wobble * Math.cos(clock * 0.07D);
            double wantZ = -leanZ * SAG + wobble * Math.sin(clock * 0.07D);
            tiltX += (wantX - tiltX) * 0.1D;
            tiltZ += (wantZ - tiltZ) * 0.1D;
        }

        /** Lightning from the hole to each stabiliser in turn, and - when it is getting away - to the ground. */
        private void lightning(ClientLevel level, RandomSource random) {
            bolts.removeIf(bolt -> --bolt.life < 0);
            if (!AWConfig.RIFT_LIGHTNING.get() || CollapseChoreography.holeRadius(progress) <= 0.0D) {
                return;
            }
            boolean hush = progress >= CollapseChoreography.HUSH;
            int period = CollapseChoreography.arcPeriod(progress, unrest);
            for (int i = 0; i < stabilisers.size(); i++) {
                Vec3 stabiliser = stabilisers.get(i);
                int phase = Math.floorMod((int) (RiftShatter.noise(seed, 400 + i) * 997.0F), period);
                if (hush ? clock % 2 == 0 : (clock + phase) % period == 0) {
                    bolts.add(new Bolt(stabiliser, ++boltSeed, CollapseChoreography.ARC_LIFE));
                    if (!hush) {
                        playAt(level, stabiliser, AWSounds.COLLAPSE_ARC.get(), 0.6F, 1.2F + random.nextFloat() * 0.6F);
                    }
                }
            }
            if (!hush && unrest > 0.3F && random.nextFloat() < (unrest - 0.3F) * 0.12F) {
                strike(level, random);
            }
        }

        /** A bolt to open ground near the cradle, leaving its mark on the block it hits. */
        private void strike(ClientLevel level, RandomSource random) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double reach = 2.5D + random.nextDouble() * 4.5D;
            int x = Mth.floor(base.x + Math.cos(angle) * reach);
            int z = Mth.floor(base.z + Math.sin(angle) * reach);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(top - base.y) > 8.0D) {
                return;
            }
            Vec3 target = new Vec3(x + 0.5D, top, z + 0.5D);
            bolts.add(new Bolt(target, ++boltSeed, CollapseChoreography.ARC_LIFE + 1));
            RiftShimmer.strike(new BlockPos(x, top - 1, z), accent);
            playAt(level, target, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.3F);
            for (int spark = 0; spark < 6; spark++) {
                level.addParticle(AWParticles.RIFT_SPARK.get(), target.x, target.y + 0.1D, target.z,
                        (random.nextDouble() - 0.5D) * 0.3D, random.nextDouble() * 0.25D, (random.nextDouble() - 0.5D) * 0.3D);
            }
        }

        private void particles(ClientLevel level, RandomSource random) {
            double density = AWConfig.PARTICLE_DENSITY.get();
            // The stabilisers waking as it spins up.
            if (progress < CollapseChoreography.WINDING_FROM && clock % 3 == 0) {
                for (Vec3 stabiliser : stabilisers) {
                    level.addParticle(AWParticles.RIFT_SPARK.get(), stabiliser.x, stabiliser.y + 0.3D, stabiliser.z,
                            (random.nextDouble() - 0.5D) * 0.02D, 0.08D, (random.nextDouble() - 0.5D) * 0.02D);
                }
            }
            // Everything round it falling in: vanilla's portal motes run from where they start to where they
            // were told, which is exactly the shape of it.
            if (progress >= CollapseChoreography.WINDING_FROM) {
                boolean hush = progress >= CollapseChoreography.HUSH;
                double wanted = (hush ? 6.0D : 1.0D + 2.0D * progress + 2.0D * unrest) * density;
                int count = (int) wanted + (random.nextDouble() < wanted % 1.0D ? 1 : 0);
                for (int mote = 0; mote < count; mote++) {
                    double yaw = random.nextDouble() * Math.PI * 2.0D;
                    double pitch = (random.nextDouble() - 0.3D) * Math.PI * 0.6D;
                    double far = 3.0D + random.nextDouble() * 6.0D;
                    level.addParticle(ParticleTypes.PORTAL, centre.x, centre.y, centre.z,
                            Math.cos(pitch) * Math.cos(yaw) * far, Math.sin(pitch) * far,
                            Math.cos(pitch) * Math.sin(yaw) * far);
                }
            }
            // Unsteady, it throws sparks off the edge of the disc.
            if (progress >= CollapseChoreography.DISC_FROM && unrest > 0.25F && random.nextFloat() < unrest * 0.6F * density) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double out = CollapseChoreography.orbitRadius(progress) + 0.5D;
                level.addParticle(AWParticles.RIFT_SPARK.get(), centre.x + Math.cos(angle) * out, centre.y,
                        centre.z + Math.sin(angle) * out, Math.cos(angle) * 0.25D, 0.05D, Math.sin(angle) * 0.25D);
            }
        }

        /** The local player drawn in, by the same arithmetic the server draws everything else in by. */
        private void pull(LocalPlayer player) {
            if (player == null || player.isSpectator() || player.isPassenger() || player.getAbilities().flying
                    || Math.abs(player.getY() - base.y) > 6.0D) {
                return;
            }
            double strength = CollapseChoreography.pull(progress);
            int range = serverInt(AWConfig.COLLAPSE_PULL_RANGE, 12);
            double toX = base.x - player.getX();
            double toZ = base.z - player.getZ();
            double reach = CollapseChoreography.pullReach(Math.sqrt(toX * toX + toZ * toZ), range);
            if (strength <= 0.0D || reach <= 0.0D) {
                return;
            }
            Vec3 velocity = player.getDeltaMovement();
            double[] now = CollapseChoreography.pulled(velocity.x, velocity.z, toX, toZ, strength * reach,
                    CollapseChoreography.pullCap(progress));
            player.setDeltaMovement(now[0], velocity.y, now[1]);
        }

        /** The moment: the flash, the jolt, and what is thrown out - once, as the count moves. */
        private void climax(ClientLevel level, Minecraft minecraft, CollapseRules.Outcome ended) {
            climaxAge = 0;
            outcome = ended;
            progress = 1.0F;
            lastProgress = 1.0F;
            if (apertureOpen) {
                RiftEffectManager.seal(key);
                apertureOpen = false;
            }
            stopLoops();
            bolts.clear();
            LocalPlayer player = minecraft.player;
            double distance = player == null ? 1.0e9D : player.position().distanceTo(centre);
            if (distance < FLASH_REACH) {
                // Vanilla's own lightning flash, so Hide Lightning Flashes turns it off without any help.
                level.setSkyFlashTime(3);
            }
            WarpScreenShake.add((float) CollapseChoreography.shake(distance, outcome));
            RandomSource random = level.random;
            double density = AWConfig.PARTICLE_DENSITY.get();
            level.addParticle(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
            level.addParticle(ParticleTypes.SONIC_BOOM, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
            // The ring along the ground, thrown as sparks under the drawn one.
            int ring = (int) (128 * density);
            for (int i = 0; i < ring; i++) {
                double angle = i / (double) Math.max(1, ring) * Math.PI * 2.0D;
                double speed = 0.7D + random.nextDouble() * 0.3D;
                level.addParticle(AWParticles.RIFT_SPARK.get(), base.x + Math.cos(angle) * 0.8D, base.y + 0.15D,
                        base.z + Math.sin(angle) * 0.8D, Math.cos(angle) * speed, 0.02D + random.nextDouble() * 0.03D,
                        Math.sin(angle) * speed);
            }
            for (int i = 0; i < (int) (32 * density); i++) {
                double yaw = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(AWParticles.WARP_STREAK.get(), centre.x, centre.y, centre.z,
                        Math.cos(yaw) * 0.2D, 0.3D + random.nextDouble() * 0.4D, Math.sin(yaw) * 0.2D);
            }
            switch (outcome) {
                case SETTLED -> {
                    for (int i = 0; i < (int) (40 * density); i++) {
                        level.addParticle(ParticleTypes.END_ROD, centre.x, centre.y, centre.z,
                                (random.nextDouble() - 0.5D) * 0.25D, (random.nextDouble() - 0.5D) * 0.25D,
                                (random.nextDouble() - 0.5D) * 0.25D);
                    }
                }
                case FLUNG -> {
                    for (int i = 0; i < (int) (30 * density); i++) {
                        level.addParticle(ParticleTypes.FIREWORK, centre.x, centre.y, centre.z,
                                (random.nextDouble() - 0.5D) * 0.6D, (random.nextDouble() - 0.3D) * 0.6D,
                                (random.nextDouble() - 0.5D) * 0.6D);
                    }
                }
                case SPAT -> {
                    level.addParticle(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
                    for (int i = 0; i < (int) (24 * density); i++) {
                        level.addParticle(ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z,
                                (random.nextDouble() - 0.5D) * 0.4D, random.nextDouble() * 0.2D,
                                (random.nextDouble() - 0.5D) * 0.4D);
                    }
                }
            }
        }

        /** After the moment: a settled core coming down trails light onto the cradle. */
        private void aftermath(ClientLevel level) {
            climaxAge++;
            if (outcome == CollapseRules.Outcome.SETTLED && climaxAge < SETTLE_TICKS && climaxAge % 2 == 0) {
                double down = CollapseChoreography.smooth(climaxAge / (double) SETTLE_TICKS);
                double height = Mth.lerp(down, CollapseChoreography.CENTRE, CollapseChoreography.HOVER);
                level.addParticle(ParticleTypes.END_ROD, base.x, base.y + height + 0.2D, base.z,
                        (level.random.nextDouble() - 0.5D) * 0.04D, 0.02D, (level.random.nextDouble() - 0.5D) * 0.04D);
            }
        }

        private void stopLoops() {
            if (drone != null) {
                drone.end();
                drone = null;
            }
            if (howl != null) {
                howl.end();
                howl = null;
            }
        }

        private void dispose() {
            stopLoops();
            if (apertureOpen) {
                RiftEffectManager.release(key);
                apertureOpen = false;
            }
        }

        private void play(ClientLevel level, SoundEvent sound, float volume, float pitch) {
            playAt(level, centre, sound, volume, pitch);
        }

        private void playAt(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
            // The mod's own are trimmed on their way into the sound engine; a borrowed one only here.
            boolean own = SoundMix.covers(sound.getLocation().getNamespace());
            level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS,
                    own ? volume : volume * (float) (double) AWConfig.EFFECT_VOLUME.get(), pitch, false);
        }
    }

    /** Where a loop is and how it sounds this tick; {@code null} to stop it. */
    private record Voice(Vec3 at, float pitch, float volume) {
    }

    /** One of a ritual's loops, following what its show says each tick and stopping itself when told. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final Supplier<Voice> voice;

        private Loop(SoundEvent sound, Supplier<Voice> voice) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.voice = voice;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.01F;
            Voice first = voice.get();
            if (first != null) {
                x = first.at().x;
                y = first.at().y;
                z = first.at().z;
                pitch = first.pitch();
            }
        }

        @Override
        public void tick() {
            Voice now = voice.get();
            if (now == null) {
                stop();
                return;
            }
            x = now.at().x;
            y = now.at().y;
            z = now.at().z;
            pitch = Mth.clamp(now.pitch(), 0.5F, 2.0F);
            volume += (now.volume() * TrimmedSound.level() - volume) * 0.1F;
        }

        private void end() {
            stop();
        }
    }
}
