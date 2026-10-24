package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
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
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.collapse.CradleRing;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Collapse Cradle's pull on its ring, made visible: motes drawn slowly into the machine from every Depot it is
 * linked to and every switched-on Spatial Stabiliser steadying it.
 *
 * <p>Nothing else says what the cradle counts. A depot a block too far out, a ninth depot and a stabiliser nobody
 * switched on look exactly like ones that are doing their job, until a collapse is flung for want of them.
 *
 * <p><b>It is a force, not a display.</b> The cradle is something old that was found and got running again, and what
 * joins it to its ring should look like that: no line is drawn, no ring, nothing with an edge. All there is to see
 * is what the pull moves - a few motes of the light its carvings glow with, rising out of each block, wandering in
 * along a line nobody can see as dust does in a draught, and gone as they reach the rim of the rings. A depot's
 * drift in along the bow an offering will fly; a stabiliser's come straight. The first version of this drew threads
 * of light with pulses and rings, and read as a hologram laid over the machine - which is the Rift Plotter's
 * manner, not this machine's.
 *
 * <p>Where each mote is comes from {@link CradleRing}. The depots are the ones the cradle says it is linked to -
 * it remembers which eight, and a client could not work that out - and the stabilisers are found by the walk the
 * server takes, by the reach the cradle was sent. The motes fade out while a collapse runs: the offerings have left
 * their depots by then, and the collapse's own lightning is what reaches for the stabilisers. A link that is new
 * throws a few sparks off the block that took it, once.
 *
 * <p>{@code cradleLinks} in the client config turns the lot off.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class CradleLinks {

    /** Ticks between looks for stabilisers: the pull is there within a second of one being switched on. */
    private static final int SURVEY_INTERVAL = 20;
    /** Ticks a cradle may go untold before it is taken to have gone. */
    private static final int SEEN_GRACE_TICKS = 2;
    /** Further off than this a mote is too small to be worth drawing. */
    private static final double DRAWN_WITHIN = 48.0D;

    /** The light the cradle's carvings glow with, and that light where it burns hottest - its own sheet's two. */
    private static final int GLYPH = 0xA060F0;
    private static final int GLYPH_HOT = 0xE8D8FF;

    /** Pixels across a mote's head, and the glow round it; how long a wisp it trails, in blocks. */
    private static final double MOTE = 2.2D;
    private static final double HALO = 6.0D;
    private static final double WISP = 0.32D;
    /** How much of a mote is there at its strongest: something seen when looked for, not something that asks to be. */
    private static final double BODY_ALPHA = 0.5D;
    private static final double HALO_ALPHA = 0.22D;
    /** A depot's lines are numbered after any stabiliser's, so the two never drift in step. */
    private static final int DEPOT_LINES = 64;

    /** Which cradles are live, when each last ticked, and what stands in its ring. */
    private static final Map<CollapseCradleBlockEntity, Watch> LIVE = new WeakHashMap<>();

    private static final class Watch {
        long seenAt;
        int surveyIn;
        /** Whether the ring has been looked at once, so what was already there is not taken for new. */
        boolean surveyed;
        List<int[]> stabilisers = List.of();
        List<int[]> depots = List.of();
        double shown;
        double lastShown;
    }

    /** A cradle and its watch, held for the length of a frame. */
    private record Drawn(CollapseCradleBlockEntity cradle, Watch watch) {
    }

    private CradleLinks() {
    }

    /** From the cradle's client tick. */
    public static void tick(CollapseCradleBlockEntity cradle) {
        if (!(cradle.getLevel() instanceof ClientLevel level)) {
            return;
        }
        if (!AWConfig.CRADLE_LINKS.get()) {
            LIVE.remove(cradle);
            return;
        }
        Watch watch = LIVE.computeIfAbsent(cradle, key -> new Watch());
        watch.seenAt = level.getGameTime();
        Vec3 base = Vec3.atBottomCenterOf(cradle.getBlockPos());
        boolean greet = watch.surveyed && !cradle.running();

        // The depots are the cradle's to say: it remembers which eight, and tells every client.
        List<int[]> depots = cradle.linkedDepots();
        if (!CradleRing.same(depots, watch.depots)) {
            for (int[] offset : depots) {
                if (greet && !CradleRing.holds(watch.depots, offset)) {
                    joined(level, world(base, CradleRing.depotEnd(offset)));
                }
            }
            watch.depots = List.copyOf(depots);
        }
        if (--watch.surveyIn <= 0) {
            watch.surveyIn = SURVEY_INTERVAL;
            BlockPos pos = cradle.getBlockPos();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            List<int[]> stabilisers = CradleRing.stabilisers(cradle.stabiliserReach(), (dx, dy, dz) ->
                    SpatialStabiliserBlock.switchedOn(level.getBlockState(at.setWithOffset(pos, dx, dy, dz))));
            for (int[] offset : stabilisers) {
                if (greet && !CradleRing.holds(watch.stabilisers, offset)) {
                    joined(level, world(base, CradleRing.stabiliserEnd(offset)));
                }
            }
            watch.stabilisers = stabilisers;
            watch.surveyed = true;
        }
        watch.lastShown = watch.shown;
        watch.shown = CradleRing.shown(watch.shown, !cradle.running());
    }

    /** A few sparks off a block the cradle has just taken into its ring. */
    private static void joined(ClientLevel level, Vec3 at) {
        RandomSource random = level.random;
        int sparks = (int) Math.round(4.0D * AWConfig.PARTICLE_DENSITY.get());
        for (int spark = 0; spark < sparks; spark++) {
            level.addParticle(AWParticles.RIFT_SPARK.get(), at.x, at.y, at.z, (random.nextDouble() - 0.5D) * 0.08D,
                    0.03D + random.nextDouble() * 0.05D, (random.nextDouble() - 0.5D) * 0.08D);
        }
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            LIVE.clear();
            return;
        }
        long gameTime = level.getGameTime();
        Vec3 eye = event.getCamera().getPosition();
        List<Drawn> drawn = new ArrayList<>();
        Iterator<Map.Entry<CollapseCradleBlockEntity, Watch>> entries = LIVE.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<CollapseCradleBlockEntity, Watch> entry = entries.next();
            CollapseCradleBlockEntity cradle = entry.getKey();
            Watch watch = entry.getValue();
            // Another level's clock may run behind this one's, so a cradle left in the last world is told by its level.
            if (cradle == null || cradle.isRemoved() || cradle.getLevel() != level
                    || Math.abs(gameTime - watch.seenAt) > SEEN_GRACE_TICKS) {
                entries.remove();
                continue;
            }
            boolean anything = !watch.stabilisers.isEmpty() || !watch.depots.isEmpty();
            if (anything && (watch.shown > 0.0D || watch.lastShown > 0.0D)
                    && Vec3.atCenterOf(cradle.getBlockPos()).distanceToSqr(eye) < DRAWN_WITHIN * DRAWN_WITHIN) {
                drawn.add(new Drawn(cradle, watch));
            }
        }
        if (drawn.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = gameTime + partialTick;
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        // The motes themselves, blended so they read against a bright floor, and a little of their light over them.
        // No dark edging and no ghost through terrain: either is the outline of a display, and this is not one.
        for (LightStrokes.Layer layer : List.of(LightStrokes.Layer.BODY, LightStrokes.Layer.GLOW)) {
            LightStrokes.Pen pen = LightStrokes.open(buffers, layer, matrix, eye);
            for (Drawn each : drawn) {
                Watch watch = each.watch();
                double shown = Mth.lerp(partialTick, watch.lastShown, watch.shown);
                Vec3 base = Vec3.atBottomCenterOf(each.cradle().getBlockPos());
                for (int i = 0; i < watch.stabilisers.size(); i++) {
                    pull(pen, base, CradleRing.stabiliserEnd(watch.stabilisers.get(i)), 0.0D, i, shown, time);
                }
                for (int i = 0; i < watch.depots.size(); i++) {
                    pull(pen, base, CradleRing.depotEnd(watch.depots.get(i)), CradleRing.DEPOT_BOW, DEPOT_LINES + i,
                            shown, time);
                }
            }
            LightStrokes.close(buffers, pen);
        }
    }

    /**
     * The motes on one line: each a short wisp, its head where {@link CradleRing#mote} puts it and its tail a
     * little way back along the way it came, thinning to nothing.
     */
    private static void pull(LightStrokes.Pen pen, Vec3 base, double[] from, double bow, int line, double shown,
                             double time) {
        double length = CradleRing.length(from);
        int motes = CradleRing.motes(length);
        boolean glow = pen.layer == LightStrokes.Layer.GLOW;
        for (int mote = 0; mote < motes; mote++) {
            double t = CradleRing.drift(time, length, line, mote, motes);
            double strength = CradleRing.drawn(t) * shown;
            if (strength <= 0.004D) {
                continue;
            }
            double turn = CradleRing.turn(line, mote);
            // The glow is a shorter, wider smudge about the head; the body is the wisp.
            double back = Math.max(0.0D, t - (glow ? WISP * 0.4D : WISP) / Math.max(0.1D, length));
            Vec3 head = world(base, CradleRing.mote(from, t, bow, turn));
            Vec3 tail = world(base, CradleRing.mote(from, back, bow, turn));
            double across = LightStrokes.width(pen, head, glow ? HALO : MOTE);
            if (glow) {
                LightStrokes.stroke(pen, tail, head, across * 0.5D, across, LightStrokes.argb(GLYPH, 0.0D),
                        LightStrokes.argb(GLYPH, HALO_ALPHA * strength));
            } else {
                int colour = LightStrokes.mix(GLYPH, GLYPH_HOT, 0.55D);
                LightStrokes.stroke(pen, tail, head, across * 0.25D, across, LightStrokes.argb(colour, 0.0D),
                        LightStrokes.argb(colour, BODY_ALPHA * strength));
            }
        }
    }

    /** A point given from the middle of the master's base, in the world. */
    private static Vec3 world(Vec3 base, double[] point) {
        return base.add(point[0], point[1], point[2]);
    }
}
