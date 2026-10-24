package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.PlotterPreview;
import uk.co.iceconchy.aerowarptics.client.screen.RiftPlotterScreen;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;
import uk.co.iceconchy.aerowarptics.plotter.PlotterChoreography;
import uk.co.iceconchy.aerowarptics.plotter.PlotterState;
import uk.co.iceconchy.aerowarptics.plotter.PlotterWorks;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a Rift Plotter does in the world, beyond its own model: the strike as a set piece, and its guides.
 *
 * <ul>
 *   <li><b>The strike</b> - {@link PlotterSpectacle}, timed by {@code PlotterChoreography}: the beam gathering from
 *   the ball, the star swelling where the entry will tear, rings closing on it and arcs jumping to it; cracks while the
 *   far end is waited on; the burst, flash and shockwave of the tear, heard as an inhale and a boom; and tethers
 *   holding the rim while it stands. A hum climbs with the charge underneath it all.</li>
 *   <li><b>The guides</b> - {@link PlotterGuides}: the entry as it is being set, and the dock of any plotter whose
 *   helper is on, for a pilot to find.</li>
 * </ul>
 *
 * <p>The entry is a held aperture ({@link RiftEffectManager#hold}), kept alive from the plotter's own client tick,
 * so a player who walks up while it stands sees it. The exit is the server's to announce, to whoever is near it, and
 * only while something is on its way through.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class PlotterEffects {

    /** Ticks a plotter may go untold before it is taken to have gone. */
    private static final int SEEN_GRACE_TICKS = 2;

    /** Which plotters are live, when each last ticked, and what they are doing. */
    private static final Map<RiftPlotterBlockEntity, Watch> LIVE = new WeakHashMap<>();

    private static final class Watch {
        long seenAt;
        PlotterState state = PlotterState.IDLE;
        boolean held;
        final List<PlotterSpectacle.Arc> arcs = new ArrayList<>();
        Hum hum;
    }

    private PlotterEffects() {
    }

    /** From the plotter's client tick. */
    public static void tick(RiftPlotterBlockEntity plotter) {
        if (!(plotter.getLevel() instanceof ClientLevel level)) {
            return;
        }
        Watch watch = LIVE.computeIfAbsent(plotter, key -> new Watch());
        watch.seenAt = level.getGameTime();
        PlotterState state = plotter.state();
        if (state != watch.state) {
            changed(plotter, level, watch.state, state);
            watch.state = state;
        }
        long holder = holder(plotter.getBlockPos());
        PlotGeometry.Frame entry = plotter.entryFrame();
        if (state == PlotterState.OPEN) {
            RiftEffectManager.hold(holder, entry.centre(), entry.normal(), plotter.plot().radius(), plotter.plot().radius(),
                    plotter.riftColour(), plotter.riftAccent(), plotter.riftTheme(), PlotterWorks.OPEN_TICKS, true);
            watch.held = true;
        } else if (watch.held) {
            RiftEffectManager.seal(holder);
            watch.held = false;
        }
        if (state == PlotterState.CHARGING || state == PlotterState.REACHING) {
            charging(plotter, level, entry, watch);
        }
        watch.arcs.removeIf(arc -> level.getGameTime() - arc.born() > PlotterSpectacle.ARC_LIFE);
        hum(plotter, watch);
    }

    /** Motes drawn in to where the tear will open, the arcs, and the crackle over the machine. */
    private static void charging(RiftPlotterBlockEntity plotter, ClientLevel level, PlotGeometry.Frame entry, Watch watch) {
        RandomSource random = level.random;
        double density = AWConfig.PARTICLE_DENSITY.get();
        double charged = plotter.state() == PlotterState.REACHING ? 1.0D : plotter.charged();
        int motes = (int) Math.round((1 + 6 * charged) * density);
        double radius = plotter.plot().radius();
        for (int i = 0; i < motes; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 from = entry.world(Math.cos(angle) * radius * 1.6D, Math.sin(angle) * radius * 1.6D,
                    (random.nextDouble() - 0.5D) * 2.0D);
            Vec3 in = entry.centre().subtract(from).scale(0.08D);
            level.addParticle(AWParticles.RIFT_SPARK.get(), from.x, from.y, from.z, in.x, in.y, in.z);
        }
        if (AWConfig.RIFT_LIGHTNING.get()
                && random.nextDouble() < PlotterChoreography.arcChance(plotter.state(), charged)) {
            double star = PlotterChoreography.star(plotter.state(), charged, plotter.stateTicks()) * radius;
            Vec3 to = entry.world((random.nextDouble() - 0.5D) * star * 1.6D, (random.nextDouble() - 0.5D) * star * 1.6D, 0.0D);
            watch.arcs.add(new PlotterSpectacle.Arc(random.nextInt(), to, level.getGameTime()));
            if (random.nextFloat() < 0.5F) {
                level.playLocalSound(to.x, to.y, to.z, AWSounds.COLLAPSE_ARC.get(), SoundSource.BLOCKS,
                        0.3F + 0.4F * (float) charged, 0.9F + 0.6F * (float) charged, false);
            }
        }
    }

    /** The moments: the charge beginning, the inhale before the tear, the tear itself. */
    private static void changed(RiftPlotterBlockEntity plotter, ClientLevel level, PlotterState from, PlotterState to) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        PlotGeometry.Frame entry = plotter.entryFrame();
        Vec3 point = entry.centre();
        double near = minecraft.player.position().distanceTo(point);
        BlockPos pos = plotter.getBlockPos();
        if (to == PlotterState.CHARGING && from == PlotterState.IDLE) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, AWSounds.COLLAPSE_SPIN_UP.get(),
                    SoundSource.BLOCKS, 1.2F, 1.1F, false);
        }
        if (to == PlotterState.REACHING) {
            level.playLocalSound(point.x, point.y, point.z, AWSounds.COLLAPSE_INHALE.get(), SoundSource.BLOCKS, 1.4F, 1.35F,
                    false);
        }
        if (to == PlotterState.OPEN) {
            level.playLocalSound(point.x, point.y, point.z, AWSounds.COLLAPSE_BOOM.get(), SoundSource.BLOCKS, 1.6F, 1.45F,
                    false);
            if (near < 96.0D) {
                WarpScreenShake.add((float) (1.0D - near / 96.0D) * 1.6F);
            }
            burst(level, entry, plotter.plot().radius());
        }
    }

    /** The tear's burst: sparks flung out across the rift's plane from its rim, and motes of light thrown wide. */
    private static void burst(ClientLevel level, PlotGeometry.Frame entry, double radius) {
        RandomSource random = level.random;
        double density = AWConfig.PARTICLE_DENSITY.get();
        int sparks = (int) Math.round(140 * density);
        for (int i = 0; i < sparks; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.35D + random.nextDouble() * 0.6D;
            Vec3 from = entry.world(Math.cos(angle) * radius * 0.9D, Math.sin(angle) * radius * 0.9D, 0.0D);
            Vec3 out = entry.world(Math.cos(angle) * speed, Math.sin(angle) * speed, (random.nextDouble() - 0.5D) * 0.2D)
                    .subtract(entry.centre());
            level.addParticle(AWParticles.RIFT_SPARK.get(), from.x, from.y, from.z, out.x, out.y, out.z);
        }
        int motes = (int) Math.round(30 * density);
        for (int i = 0; i < motes; i++) {
            Vec3 at = entry.centre();
            level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, (random.nextDouble() - 0.5D) * 0.6D,
                    (random.nextDouble() - 0.5D) * 0.6D, (random.nextDouble() - 0.5D) * 0.6D);
        }
    }

    /** Distinct from the chute's and the cradle's holders, which are their own block positions. */
    private static long holder(BlockPos pos) {
        return pos.asLong() ^ 0x5A5A_5A5A_0000_0000L;
    }

    // ------------------------------------------------------------------ the hum

    /** Starts the charging hum when a charge begins; it follows the plotter and stops itself when the charge is done. */
    private static void hum(RiftPlotterBlockEntity plotter, Watch watch) {
        if (watch.hum != null && watch.hum.isStopped()) {
            watch.hum = null;
        }
        PlotterState state = plotter.state();
        if (watch.hum == null && (state == PlotterState.CHARGING || state == PlotterState.REACHING)) {
            watch.hum = new Hum(plotter);
            Minecraft.getInstance().getSoundManager().play(watch.hum);
        }
    }

    /** A drone under the charge, climbing in pitch as it fills, and straining while the far end is waited on. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final RiftPlotterBlockEntity plotter;

        private Hum(RiftPlotterBlockEntity plotter) {
            super(AWSounds.COLLAPSE_DRONE.get(), SoundSource.BLOCKS, RandomSource.create());
            this.plotter = plotter;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.01F;
            Vec3 lens = PlotGeometry.lens(plotter.getBlockPos());
            x = lens.x;
            y = lens.y;
            z = lens.z;
            pitch = 0.6F;
        }

        @Override
        public void tick() {
            PlotterState state = plotter.state();
            if (plotter.isRemoved() || state != PlotterState.CHARGING && state != PlotterState.REACHING) {
                stop();
                return;
            }
            double charged = state == PlotterState.REACHING ? 1.0D : plotter.charged();
            double strain = state == PlotterState.REACHING ? 0.06D * Math.sin(plotter.stateTicks() * 0.7D) : 0.0D;
            pitch = Mth.clamp((float) (0.6D + 0.9D * charged + strain), 0.5F, 2.0F);
            float wanted = (float) (0.35D + 0.65D * charged) * TrimmedSound.level();
            volume += (wanted - volume) * 0.1F;
        }
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
            LIVE.clear();
            return;
        }
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = gameTime + partialTick;
        BlockPos previewing = PlotterPreview.plotter();

        List<RiftPlotterBlockEntity> plotters = new ArrayList<>();
        Iterator<Map.Entry<RiftPlotterBlockEntity, Watch>> entries = LIVE.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<RiftPlotterBlockEntity, Watch> entry = entries.next();
            RiftPlotterBlockEntity plotter = entry.getKey();
            if (plotter.isRemoved() || gameTime - entry.getValue().seenAt > SEEN_GRACE_TICKS) {
                if (entry.getValue().hum != null) {
                    minecraft.getSoundManager().stop(entry.getValue().hum);
                }
                entries.remove();
                continue;
            }
            plotters.add(plotter);
        }
        if (previewing == null && plotters.isEmpty()) {
            return;
        }
        boolean guides = AWConfig.PLOTTER_DOCK_GUIDE.get();
        boolean spectacle = AWConfig.RIFT_DISTORTION.get();
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 eye = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        for (LightStrokes.Layer layer : LightStrokes.Layer.values()) {
            LightStrokes.Pen pen = LightStrokes.open(buffers, layer, matrix, eye);
            if (previewing != null) {
                PlotterGuides.entry(pen, previewing, PlotterPreview.plot(), PlotterPreview.firing(), dockReach(), time);
            }
            for (RiftPlotterBlockEntity plotter : plotters) {
                // The one being edited draws its draft instead, above - two docks a few degrees apart would be noise.
                if (guides && plotter.firing().dock() && !plotter.getBlockPos().equals(previewing)) {
                    PlotterGuides.dock(pen, plotter.entryFrame(), plotter.plot().radius(), plotter.dockRadius(),
                            plotter.dockDepth(), plotter.dockHolding(), plotter.dockLinedUp(),
                            plotter.state() == PlotterState.OPEN, time);
                }
                if (spectacle) {
                    PlotterSpectacle.draw(pen, plotter, plotter.stateTicks() + partialTick, time,
                            plotter.spin().angle(partialTick), LIVE.get(plotter).arcs);
                }
            }
            LightStrokes.close(buffers, pen);
        }
    }

    /** How deep a dock nobody has sized reaches, as the table was last told - a client never reads the server's config. */
    private static double dockReach() {
        return Minecraft.getInstance().screen instanceof RiftPlotterScreen table ? table.dockReach() : 32.0D;
    }
}
