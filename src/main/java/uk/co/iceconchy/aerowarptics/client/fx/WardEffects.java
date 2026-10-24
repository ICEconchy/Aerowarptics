package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.screen.RiftWardScreen;
import uk.co.iceconchy.aerowarptics.network.ClientboundWardCuePacket;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;
import uk.co.iceconchy.aerowarptics.ward.WardChoreography;
import uk.co.iceconchy.aerowarptics.ward.WardStructure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Everything a Rift Ward shows of itself that is not the machine: a deflection played across the sky, the few
 * motes that settle at a standing field's rim, and - only while its panel is open - the rim itself.
 *
 * <h2>The scene</h2>
 * The server says once that a rift is being turned aside, where from and where to, and how long until it breaks
 * ({@link ClientboundWardCuePacket}). From there this client runs {@link WardChoreography} on its own clock: the
 * tear opening over the place aimed at, caught half open, dragged across the sky to the rim, and bursting there
 * as the hull comes out. The ward's renderer asks {@link #pull} which way its totem is being dragged. Those aboard
 * the ship are in its corridor and see none of the sky, so they are given the wrench instead - the deck shaken,
 * the view rolled over, the toll muffled.
 *
 * <h2>The idle field</h2>
 * <b>A force, not a display</b>, as the cradle's pull is: no dome, no ring, nothing with an edge. And a wrong one -
 * this is a machine that holds a place shut by leaning on what the place is made of, and it should look as though
 * it ought not to be done. So what a standing ward moves, it moves the wrong way: a few slow motes in the
 * carvings' own colours that <em>rise</em> off the ground where the field ends, and out of the machine's open top;
 * dust at the foot of the plinth creeping inward to it; a dark stain wavering in the air over the totem. All of it
 * only near whoever is looking, and none of it drawn as a line. With it goes a low drone ({@link Hum}), which with
 * the totem's light and its turning is how a ward says it is on. The one bright line is an instruction rather than
 * the relic's own manner: while the panel is open the rim is drawn, so a radius can be set by eye.
 *
 * <p>{@code wardSpectacle} and {@code wardMotes} in the client config turn the sky and the motes off; the weight
 * and the toll are the machine, and stay.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WardEffects {

    /** Which way a ward's totem is being dragged, and how hard. */
    public record Pull(double ux, double uz, double lean, double strain, double sag) {

        static final Pull NONE = new Pull(1.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** One deflection, being played. */
    private static final class Scene {
        final ClientboundWardCuePacket cue;
        int age;

        Scene(ClientboundWardCuePacket cue) {
            this.cue = cue;
        }

        boolean sky() {
            return cue.kind() != ClientboundWardCuePacket.Kind.GAVE_WAY;
        }

        /** How big the tear is drawn: a ship's, or a plotter's. */
        double size() {
            return cue.kind() == ClientboundWardCuePacket.Kind.PLOTTED ? 3.5D : 8.0D;
        }

        Vec3 tearAt(double age) {
            double skid = sky() ? WardChoreography.skid(age, cue.total()) : 0.0D;
            return cue.aimed().lerp(cue.went(), skid);
        }
    }

    /** The carvings' light, and that light where it burns hottest - the relics' own two. */
    private static final int GLYPH = 0xA060F0;
    private static final int GLYPH_HOT = 0xE8D8FF;
    /** The rim's guide: the plotter's "lined up" teal, since both are instructions. */
    private static final int GUIDE = 0x49D9C4;
    private static final int WHITE = 0xFFFFFF;

    /** How far off a scene still dims the day and shakes the ground, in blocks. */
    private static final double FELT_WITHIN = 256.0D;
    /** How far the view is rolled over for those aboard, at the worst of it, in degrees. */
    private static final float MOST_ROLL = 6.0F;

    /** Motes round a field's rim near the viewer, how far apart they settle, and how near one has to be to be drawn. */
    private static final int MOTES = 9;
    private static final double MOTE_SPACING = 4.5D;
    private static final double MOTES_WITHIN = 40.0D;
    /** Ticks a mote takes to rise out of the ground and go, and how far it climbs doing it. */
    private static final double MOTE_RISE_TICKS = 150.0D;
    private static final double MOTE_RISE = 3.5D;
    /** How near the machine a viewer has to be for what it sheds to be drawn, and how far out its dust is drawn in from. */
    private static final double WRONG_WITHIN = 48.0D;
    private static final double DUST_FROM = 5.5D;
    private static final double DUST_TO = 1.7D;
    /** The colour of the stain over the totem: the void's. */
    private static final int STAIN = 0x05020B;
    /** Ticks a ward may go untold before it is taken to have gone. */
    private static final int SEEN_GRACE_TICKS = 2;

    private static final List<Scene> SCENES = new ArrayList<>();
    private static final Map<RiftWardBlockEntity, Long> LIVE = new WeakHashMap<>();
    private static final Map<RiftWardBlockEntity, Hum> HUMS = new WeakHashMap<>();

    /** How far the view is rolled, eased tick by tick. */
    private static float roll;
    private static float lastRoll;

    private WardEffects() {
    }

    // ------------------------------------------------------------------ told

    /** The server's word that a ward is turning something aside. */
    public static void accept(ClientboundWardCuePacket cue) {
        SCENES.add(new Scene(cue));
        ClientLevel level = Minecraft.getInstance().level;
        if (cue.aboard() && level != null && Minecraft.getInstance().player != null) {
            // Those aboard hear it start: there is nothing for them to see until the corridor lurches.
            Vec3 here = Minecraft.getInstance().player.position();
            level.playLocalSound(here.x, here.y, here.z, AWSounds.WARD_WRENCH.get(), SoundSource.BLOCKS, 0.5F, 0.7F, false);
        }
    }

    /** From a ward's client tick: it is here, and its motes may be drawn. */
    public static void tick(RiftWardBlockEntity ward) {
        if (ward.getLevel() instanceof ClientLevel level) {
            LIVE.put(ward, level.getGameTime());
            hum(ward);
        }
    }

    /** Starts a standing ward's drone if it has none; the drone follows the ward and stops itself. */
    private static void hum(RiftWardBlockEntity ward) {
        Hum hum = HUMS.get(ward);
        if (hum != null && hum.isStopped()) {
            HUMS.remove(ward);
            hum = null;
        }
        if (hum == null && ward.enabled() && ward.standing()) {
            hum = new Hum(ward);
            HUMS.put(ward, hum);
            Minecraft.getInstance().getSoundManager().play(hum);
        }
    }

    /**
     * The drone of a ward whose field stands: low, close - it carries a dozen blocks or so - and a little higher the
     * fuller the ward is. It fades out rather than stopping when the field drops, and is gone with the machine.
     */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final RiftWardBlockEntity ward;

        private Hum(RiftWardBlockEntity ward) {
            super(AWSounds.WARD_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
            this.ward = ward;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.01F;
            this.pitch = 0.5F;
            x = ward.getBlockPos().getX() + 0.5D;
            y = ward.getBlockPos().getY() + WardStructure.CORE_HEIGHT;
            z = ward.getBlockPos().getZ() + 0.5D;
        }

        @Override
        public void tick() {
            boolean on = !ward.isRemoved() && ward.enabled() && ward.standing()
                    && ward.getLevel() == Minecraft.getInstance().level;
            float wanted = on ? 0.45F * TrimmedSound.level() : 0.0F;
            volume += (wanted - volume) * 0.08F;
            pitch = (float) (0.5D + 0.12D * ward.charged());
            if (!on && volume < 0.02F) {
                stop();
            }
        }
    }

    public static void clear() {
        SCENES.clear();
        LIVE.clear();
        HUMS.clear();
        roll = 0.0F;
        lastRoll = 0.0F;
    }

    /** Which way a ward's totem is dragged this frame: towards the tear of the scene it is in, or not at all. */
    public static Pull pull(BlockPos ward, float partialTick) {
        Scene scene = null;
        for (Scene each : SCENES) {
            if (each.cue.ward().equals(ward)) {
                scene = each;
            }
        }
        if (scene == null) {
            return Pull.NONE;
        }
        double age = scene.age + partialTick;
        int total = scene.cue.total();
        Vec3 tear = scene.tearAt(age);
        double dx = tear.x - (ward.getX() + 0.5D);
        double dz = tear.z - (ward.getZ() + 0.5D);
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6D) {
            return Pull.NONE;
        }
        boolean held = scene.sky();
        return new Pull(dx / length, dz / length,
                held ? WardChoreography.lean(age, total) : WardChoreography.slackLean(age, total),
                held ? WardChoreography.strain(age, total) : 0.0D,
                held ? 0.0D : WardChoreography.sag(age, total));
    }

    // ------------------------------------------------------------------ the clock

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        lastRoll = roll;
        float wantedRoll = 0.0F;
        Vec3 here = minecraft.player.position();
        Iterator<Scene> scenes = SCENES.iterator();
        while (scenes.hasNext()) {
            Scene scene = scenes.next();
            ClientboundWardCuePacket cue = scene.cue;
            int total = cue.total();
            scene.age++;
            if (WardChoreography.over(scene.age, total)) {
                scenes.remove();
                continue;
            }
            Vec3 ward = Vec3.atBottomCenterOf(cue.ward()).add(0.0D, WardStructure.CORE_HEIGHT, 0.0D);
            float reach = Math.max(1.0F, cue.tollRange() / 16.0F);
            if (scene.age == WardChoreography.tollAt(total)) {
                if (cue.aboard()) {
                    level.playLocalSound(here.x, here.y, here.z, AWSounds.WARD_TOLL.get(), SoundSource.BLOCKS, 0.8F, 0.55F, false);
                } else {
                    play(level, ward, scene.sky() ? AWSounds.WARD_TOLL.get() : AWSounds.WARD_GIVE_WAY.get(), reach,
                            scene.sky() ? 0.6F : 0.7F);
                }
            }
            if (scene.age == total && scene.sky() && !cue.aboard()) {
                play(level, cue.went(), AWSounds.WARD_BREAK.get(), reach, 0.7F);
                burst(level, cue.went(), scene.size());
            }
            if (!scene.sky()) {
                continue;
            }
            double shake = WardChoreography.shake(scene.age, total);
            if (cue.aboard()) {
                WarpScreenShake.add((float) (0.22D * shake));
                // Rolled over towards the side the rift is being dragged to, and let go of at the break.
                float side = cue.went().x - cue.aimed().x + cue.went().z - cue.aimed().z >= 0.0D ? 1.0F : -1.0F;
                wantedRoll += side * MOST_ROLL * (float) WardChoreography.bend(scene.age, total);
                continue;
            }
            Vec3 tear = scene.tearAt(scene.age);
            double felt = 1.0D - Mth.clamp(Math.hypot(tear.x - here.x, tear.z - here.z) / FELT_WITHIN, 0.0D, 1.0D);
            if (felt <= 0.0D) {
                continue;
            }
            if (cue.kind() == ClientboundWardCuePacket.Kind.DEFLECTED && AWConfig.WARD_SPECTACLE.get()) {
                RiftStormSky.lend((float) (0.55D * WardChoreography.dim(scene.age, total) * felt));
                WarpScreenShake.add((float) (0.10D * shake * felt * felt));
            }
            if (AWConfig.WARD_SPECTACLE.get()) {
                shed(level, tear, scene.size(), WardChoreography.strain(scene.age, total));
            }
        }
        roll += (wantedRoll - roll) * 0.25F;
        if (Math.abs(roll) < 0.01F && wantedRoll == 0.0F) {
            roll = 0.0F;
        }
    }

    /** Sparks shed off a tear being held against the field. */
    private static void shed(ClientLevel level, Vec3 tear, double size, double strain) {
        double density = AWConfig.PARTICLE_DENSITY.get();
        if (strain <= 0.0D || level.random.nextDouble() > 0.6D * strain * density) {
            return;
        }
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double out = size * 0.5D * (0.6D + 0.4D * level.random.nextDouble());
        level.addParticle(AWParticles.RIFT_SPARK.get(), tear.x + Math.cos(angle) * out,
                tear.y + (level.random.nextDouble() - 0.5D) * size, tear.z + Math.sin(angle) * out,
                (level.random.nextDouble() - 0.5D) * 0.2D, -0.05D - level.random.nextDouble() * 0.1D,
                (level.random.nextDouble() - 0.5D) * 0.2D);
    }

    /** The break: a scatter of sparks out of the tear as it bursts. */
    private static void burst(ClientLevel level, Vec3 at, double size) {
        int sparks = (int) Math.round(40.0D * AWConfig.PARTICLE_DENSITY.get());
        for (int spark = 0; spark < sparks; spark++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double lift = (level.random.nextDouble() - 0.5D) * 0.6D;
            double speed = 0.25D + level.random.nextDouble() * 0.45D;
            level.addParticle(AWParticles.RIFT_SPARK.get(), at.x, at.y + (level.random.nextDouble() - 0.5D) * size,
                    at.z, Math.cos(angle) * speed, lift, Math.sin(angle) * speed);
        }
    }

    private static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, true);
    }

    // ------------------------------------------------------------------ aboard

    /** The corridor wrenched over, for those aboard: a moving lens, so it answers to {@code warpFov}. */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (roll == 0.0F && lastRoll == 0.0F) {
            return;
        }
        if (!AWConfig.WARP_FOV.get() || !AWConfig.WARD_SPECTACLE.get()) {
            return;
        }
        event.setRoll(event.getRoll() + Mth.lerp((float) event.getPartialTick(), lastRoll, roll));
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            clear();
            return;
        }
        long gameTime = level.getGameTime();
        Iterator<Map.Entry<RiftWardBlockEntity, Long>> entries = LIVE.entrySet().iterator();
        List<RiftWardBlockEntity> wards = new ArrayList<>();
        while (entries.hasNext()) {
            Map.Entry<RiftWardBlockEntity, Long> entry = entries.next();
            RiftWardBlockEntity ward = entry.getKey();
            if (ward == null || ward.isRemoved() || ward.getLevel() != level
                    || Math.abs(gameTime - entry.getValue()) > SEEN_GRACE_TICKS) {
                entries.remove();
                continue;
            }
            wards.add(ward);
        }
        RiftWardScreen panel = minecraft.screen instanceof RiftWardScreen open ? open : null;
        boolean sky = AWConfig.WARD_SPECTACLE.get() && !SCENES.isEmpty();
        boolean motes = AWConfig.WARD_MOTES.get() && !wards.isEmpty();
        if (!sky && !motes && panel == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = gameTime + partialTick;
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 eye = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        for (LightStrokes.Layer layer : LightStrokes.Layer.values()) {
            // No shade under any of it: an edging is the outline of a display - see the class note.
            if (layer == LightStrokes.Layer.SHADE) {
                continue;
            }
            LightStrokes.Pen pen = LightStrokes.open(buffers, layer, matrix, eye);
            if (layer == LightStrokes.Layer.THROUGH) {
                // Only the guide is drawn through terrain: it is the one thing here a player has to find.
                if (panel != null) {
                    rim(pen, level, panel.wardPos(), panel.draftRadius(), 0.35D);
                }
                LightStrokes.close(buffers, pen);
                continue;
            }
            if (panel != null && layer == LightStrokes.Layer.BODY) {
                rim(pen, level, panel.wardPos(), panel.draftRadius(), 0.9D);
            }
            if (motes) {
                for (RiftWardBlockEntity ward : wards) {
                    motes(pen, level, ward, time);
                    wrongness(pen, ward, time);
                }
            }
            if (sky) {
                for (Scene scene : SCENES) {
                    if (scene.sky() && !scene.cue.aboard()) {
                        scene(pen, scene, scene.age + partialTick, time);
                    }
                }
            }
            LightStrokes.close(buffers, pen);
        }
    }

    /** One deflection, in one layer: the tear, its strain, the streak it leaves, and the ring of air at the break. */
    private static void scene(LightStrokes.Pen pen, Scene scene, double age, double time) {
        ClientboundWardCuePacket cue = scene.cue;
        int total = cue.total();
        double open = WardChoreography.tear(age, total);
        double there = WardChoreography.ghost(age, total);
        double strain = WardChoreography.strain(age, total);
        double size = scene.size();
        Vec3 tear = scene.tearAt(age);
        int colour = cue.colour() & 0xFFFFFF;
        int hot = LightStrokes.mix(colour, WHITE, 0.6D);
        boolean glow = pen.layer == LightStrokes.Layer.GLOW;
        double radius = size * open;

        if (open > 0.0D && there > 0.0D) {
            // The streak: where the tear has been, thinning away behind it.
            double skid = WardChoreography.skid(age, total);
            if (skid > 0.0D && strain > 0.0D) {
                Vec3 back = cue.aimed().lerp(cue.went(), Math.max(0.0D, skid - 0.22D));
                LightStrokes.stroke(pen, back, tear, 0.0D, radius * (glow ? 1.2D : 0.7D),
                        LightStrokes.argb(colour, 0.0D), LightStrokes.argb(glow ? hot : colour, (glow ? 0.55D : 0.35D) * strain));
            }
            if (glow) {
                LightStrokes.disc(pen, tear, radius * 0.85D, radius * 1.9D, LightStrokes.argb(hot, 0.8D * there),
                        LightStrokes.argb(colour, 0.0D));
                // Held against the field it throws out cracks, re-drawn every few ticks.
                int crackle = (int) (time / 3.0D);
                int cracks = 7;
                for (int crack = 0; crack < cracks && strain > 0.0D; crack++) {
                    int seed = RiftShatter.seedFor(cue.aimed().x, cue.aimed().y, cue.aimed().z) + crack * 7919;
                    double angle = Math.PI * 2.0D * (crack + RiftShatter.noise(seed + crackle, 0)) / cracks;
                    double lift = (RiftShatter.noise(seed + crackle, 1) - 0.5D) * 1.2D;
                    double length = radius * (1.4D + 1.2D * strain * RiftShatter.noise(seed + crackle, 2));
                    Vec3 end = tear.add(Math.cos(angle) * length, lift * length, Math.sin(angle) * length);
                    LightStrokes.bolt(pen, tear, end, seed + crackle * 31, length * 0.12D, 3.0D,
                            LightStrokes.argb(hot, 0.85D * strain * there));
                }
            } else {
                // The hole itself: dark, with a rim of the rift's own colour. Blended, since light cannot draw dark.
                LightStrokes.disc(pen, tear, 0.0D, radius, LightStrokes.argb(0x08020F, 0.92D * there),
                        LightStrokes.argb(0x08020F, 0.92D * there));
                LightStrokes.disc(pen, tear, radius * 0.82D, radius * 1.08D, LightStrokes.argb(colour, 0.9D * there),
                        LightStrokes.argb(hot, 0.9D * there));
            }
        }
        double fade = WardChoreography.ringFade(age, total);
        if (fade > 0.0D) {
            double ring = size * (1.0D + 5.0D * WardChoreography.ring(age, total));
            circle(pen, cue.went(), ring, glow ? 5.0D : 3.0D, LightStrokes.argb(glow ? hot : colour, (glow ? 0.7D : 0.4D) * fade));
            if (glow) {
                LightStrokes.disc(pen, cue.went(), 0.0D, size * 3.0D,
                        LightStrokes.argb(WHITE, WardChoreography.burst(age, total) * fade * fade),
                        LightStrokes.argb(colour, 0.0D));
            }
        }
    }

    /** A level circle about a point, as strokes turned to the viewer. */
    private static void circle(LightStrokes.Pen pen, Vec3 centre, double radius, double pixels, int argb) {
        int segments = 48;
        Vec3 last = null;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.PI * 2.0D * segment / segments;
            Vec3 point = centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            if (last != null) {
                LightStrokes.stroke(pen, last, point, pixels, argb);
            }
            last = point;
        }
    }

    /**
     * What a standing ward does to the air round the machine itself: motes climbing out of its open top, dust at
     * the foot of the plinth creeping inward along the ground, and a dark stain wavering over the totem.
     */
    private static void wrongness(LightStrokes.Pen pen, RiftWardBlockEntity ward, double time) {
        if (!ward.enabled() || !ward.standing()) {
            return;
        }
        Vec3 foot = Vec3.atBottomCenterOf(ward.getBlockPos());
        if (foot.distanceToSqr(pen.eye) > WRONG_WITHIN * WRONG_WITHIN) {
            return;
        }
        boolean glow = pen.layer == LightStrokes.Layer.GLOW;
        int seedBase = ward.getBlockPos().hashCode();
        int colour = glow ? GLYPH : LightStrokes.mix(GLYPH, GLYPH_HOT, 0.55D);
        // Out of the open top, and up: each on its own slow spiral, gone a few blocks over the machine.
        for (int mote = 0; mote < 6; mote++) {
            int seed = seedBase + mote * 7919;
            double t = (time / (110.0D + 60.0D * RiftShatter.noise(seed, 0)) + RiftShatter.noise(seed, 1)) % 1.0D;
            double strength = Math.sin(Math.PI * t);
            double angle = RiftShatter.noise(seed, 2) * Math.PI * 2.0D + t * 2.2D;
            double out = 0.35D + 0.8D * RiftShatter.noise(seed, 3);
            Vec3 head = foot.add(Math.cos(angle) * out, WardStructure.TOP_HEIGHT - 0.6D + 4.5D * t, Math.sin(angle) * out);
            wisp(pen, head, head.add(0.0D, glow ? -0.12D : -0.3D, 0.0D), colour, strength, glow);
        }
        // Along the ground, and in: dust drawn to the foot of the plinth from a few blocks off.
        for (int mote = 0; mote < 8; mote++) {
            int seed = seedBase + 104_729 + mote * 7919;
            double t = (time / (170.0D + 80.0D * RiftShatter.noise(seed, 0)) + RiftShatter.noise(seed, 1)) % 1.0D;
            double strength = Math.sin(Math.PI * t) * 0.8D;
            double angle = (mote + RiftShatter.noise(seed, 2)) * Math.PI * 2.0D / 8.0D;
            double out = DUST_FROM + (DUST_TO - DUST_FROM) * t;
            double back = out + (glow ? 0.12D : 0.3D);
            Vec3 head = foot.add(Math.cos(angle) * out, 0.12D, Math.sin(angle) * out);
            Vec3 tail = foot.add(Math.cos(angle) * back, 0.12D, Math.sin(angle) * back);
            wisp(pen, head, tail, colour, strength, glow);
        }
        // The stain: nothing with an edge, a darkness in the air that will not hold still. Blended, since light
        // cannot draw dark.
        if (!glow) {
            double waver = 0.75D + 0.25D * Math.sin(time / 9.0D) * Math.sin(time / 23.0D);
            Vec3 over = foot.add(Math.sin(time / 31.0D) * 0.15D,
                    WardStructure.TOP_HEIGHT + 0.7D + Math.sin(time / 47.0D) * 0.2D, Math.cos(time / 37.0D) * 0.15D);
            LightStrokes.disc(pen, over, 0.0D, 1.3D + 0.25D * waver, LightStrokes.argb(STAIN, 0.3D * waver),
                    LightStrokes.argb(STAIN, 0.0D));
        }
    }

    /** One mote: a short wisp, its head where it is and its tail a little way back along the way it came. */
    private static void wisp(LightStrokes.Pen pen, Vec3 head, Vec3 tail, int colour, double strength, boolean glow) {
        if (strength <= 0.01D) {
            return;
        }
        double across = LightStrokes.width(pen, head, glow ? 6.0D : 2.2D);
        LightStrokes.stroke(pen, tail, head, across * (glow ? 0.5D : 0.25D), across, LightStrokes.argb(colour, 0.0D),
                LightStrokes.argb(colour, (glow ? 0.2D : 0.45D) * strength));
    }

    /**
     * The few motes rising where a field ends. They are worked out round whoever is looking - a handful along the
     * nearest stretch of rim - so there are some to find wherever the rim is met, and none drawn anywhere else.
     */
    private static void motes(LightStrokes.Pen pen, ClientLevel level, RiftWardBlockEntity ward, double time) {
        double radius = ward.standingRadius();
        if (radius <= 0.0D) {
            return;
        }
        double cx = ward.getBlockPos().getX() + 0.5D;
        double cz = ward.getBlockPos().getZ() + 0.5D;
        double out = Math.hypot(pen.eye.x - cx, pen.eye.z - cz);
        if (Math.abs(out - radius) > MOTES_WITHIN) {
            return;
        }
        boolean glow = pen.layer == LightStrokes.Layer.GLOW;
        // Motes keep their places on the rim, so they do not slide round it with the viewer: the nearest few of a
        // fixed set, spaced along the rim.
        double step = MOTE_SPACING / radius;
        long nearest = Math.round(Math.atan2(pen.eye.z - cz, pen.eye.x - cx) / step);
        for (int offset = -MOTES / 2; offset <= MOTES / 2; offset++) {
            long index = nearest + offset;
            int seed = (int) (index * 7919L) ^ ward.getBlockPos().hashCode();
            double angle = (index + (RiftShatter.noise(seed, 0) - 0.5D) * 0.6D) * step;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double rise = (time / (MOTE_RISE_TICKS * (0.8D + 0.4D * RiftShatter.noise(seed, 1)))
                    + RiftShatter.noise(seed, 2)) % 1.0D;
            // Out of the ground, up slowly, and gone: the wrong way for anything to fall.
            double strength = Math.sin(Math.PI * rise);
            if (strength <= 0.01D) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double sway = Math.sin(time / 37.0D + seed) * 0.25D;
            Vec3 head = new Vec3(x + sway, ground + 0.3D + MOTE_RISE * rise, z + Math.cos(time / 41.0D + seed) * 0.25D);
            if (head.distanceToSqr(pen.eye) > MOTES_WITHIN * MOTES_WITHIN) {
                continue;
            }
            wisp(pen, head, head.add(0.0D, glow ? -0.12D : -0.3D, 0.0D),
                    glow ? GLYPH : LightStrokes.mix(GLYPH, GLYPH_HOT, 0.55D), strength, glow);
        }
    }

    /** The rim as its owner is setting it, laid over the ground: drawn only while the panel is open. */
    private static void rim(LightStrokes.Pen pen, ClientLevel level, BlockPos ward, double radius, double alpha) {
        double cx = ward.getX() + 0.5D;
        double cz = ward.getZ() + 0.5D;
        int segments = Mth.clamp((int) (radius * 1.2D), 48, 192);
        Vec3 last = null;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.PI * 2.0D * segment / segments;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            Vec3 point = new Vec3(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.4D, z);
            if (last != null) {
                LightStrokes.stroke(pen, last, point, 3.0D, LightStrokes.argb(GUIDE, alpha));
            }
            last = point;
        }
    }
}
