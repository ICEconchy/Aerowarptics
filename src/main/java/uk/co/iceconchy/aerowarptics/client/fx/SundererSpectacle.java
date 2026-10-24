package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.network.ClientboundSundererCuePacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.sunderer.RiftHandPose;
import uk.co.iceconchy.aerowarptics.sunderer.SundererChoreography;
import uk.co.iceconchy.aerowarptics.sunderer.SundererSequence;
import uk.co.iceconchy.aerowarptics.sunderer.Unravelling;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A Rift Sunderer's tear as a set piece: the ring unravelling round a seed of nothing.
 *
 * <p>When is {@link SundererChoreography}'s and where each burst block of the frame is, is {@link Unravelling}'s,
 * both grown from the seed the server sends, so every client draws the same scene and a latecomer joins it
 * half-way. The server says once a second how far through it is; between, each client runs it on its own clock,
 * and plays the end of the breach by itself once the Sunderer that told it is gone.
 *
 * <h2>No surface (the owner, 2026-10-17)</h2>
 * The first version drew a flat membrane across the opening with cracks on it, and the owner sent it back as
 * "just a 2d plane". This draws nothing flat. The opening stays open, and what is in it is solid and round:
 * <ul>
 *   <li>a <b>point</b> that searches along the ring's axis with starlines for a far side and finds none;</li>
 *   <li>the <b>seed</b> it becomes - a black ball with a violet limb, on the membrane's solid pass, so it reads at
 *       noon and hides what goes into it - and a <b>lens</b> round it that bends the world behind, the standing
 *       frame included ({@link GravityLens}; a dark halo stands in under a shader pack, which has no lens);</li>
 *   <li>the <b>burst third of the frame</b>, real block models taken out of the world as it fired, shuddering,
 *       cracking free, orbiting over a disc of dust, grinding, and swallowed one a beat;</li>
 *   <li>the <b>hand</b>, when it comes, reaching out of the seed's edge for a block of the frame still standing.</li>
 * </ul>
 * The violet is trim on the fire pass; the dust is on the shard pass, which is translucent and so reads by day.
 *
 * <p>Two things are each client's own. The hand comes out on the side the player is on, and turns to that player
 * if they are near: everybody watching is looked at. And every sound is played here, on the scene's clock.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class SundererSpectacle {

    private static final float SEED_RED = 0.012F;
    private static final float SEED_GREEN = 0.004F;
    private static final float SEED_BLUE = 0.022F;
    private static final float[] LIMB = {0.55F, 0.25F, 0.95F};
    private static final int GLOW = 0x8B4FE0;
    private static final int WHITE = 0xF4EEFF;
    /** Dark grit, not pale: a pale disc was lost against the sky by day (seen in game, 2026-10-17). */
    private static final int DUST = 0x3E3352;
    private static final int SHADOW = 0x0E0716;
    private static final int GLOW_LIGHT = 240;

    private static final int SPHERE_BANDS = 16;
    private static final int SPHERE_SEGMENTS = 32;
    private static final int DISC_RINGS = 6;
    private static final int DISC_SEGMENTS = 64;
    private static final int STARLINES = 48;

    /** Ticks a misfire's point and lens take to fade, and a rupture's thrown blocks are shown for. */
    private static final int CLOSING = 24;

    private static final Map<Long, Show> SHOWS = new HashMap<>();

    private SundererSpectacle() {
    }

    public static void accept(ClientboundSundererCuePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        long key = packet.origin().asLong();
        Show show = SHOWS.get(key);
        long start = level.getGameTime() - packet.elapsed();
        if (show == null) {
            if (packet.outcome() != ClientboundSundererCuePacket.RUNNING) {
                return;
            }
            show = new Show(packet, start);
            SHOWS.put(key, show);
            ClientTrace.log(ClientTrace.CUE, "sundering at {} begun for this client: {} in, {} blocks burst, hand {},"
                            + " dry={}, riftDistortion={}, lens {}", packet.origin(), packet.elapsed(),
                    packet.lifted().size(), packet.hand(), packet.dry(), AWConfig.RIFT_DISTORTION.get(),
                    GravityLens.status());
            if (packet.elapsed() < 10) {
                show.play(level, AWSounds.SUNDERER_REACH.get(), 1.4F, 1.0F);
            }
            show.startHum(minecraft);
        } else if (packet.outcome() == ClientboundSundererCuePacket.RUNNING) {
            // Put right by the server's count; a tick or two either way is nothing anyone would see.
            show.start = start;
        } else {
            show.end(level, packet.outcome());
        }
    }

    public static void clear() {
        for (Show show : SHOWS.values()) {
            show.stopHum();
        }
        SHOWS.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || SHOWS.isEmpty()) {
            return;
        }
        for (Iterator<Show> each = SHOWS.values().iterator(); each.hasNext(); ) {
            Show show = each.next();
            if (show.tick(level, minecraft)) {
                show.stopHum();
                each.remove();
            }
        }
    }

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
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f matrix = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        if (membranePass) {
            // Bend first, from the frame as it stands, then draw the seed over the middle of the bending - so the
            // seed's edge stays crisp and what is behind the ring is what is pulled about. The lifted blocks are
            // drawn after the bending too: they stand in front of it.
            List<GravityLens.Bend> bends = new ArrayList<>();
            for (Show show : SHOWS.values()) {
                show.bend(bends, level, partialTick);
            }
            GravityLens.bend(bends, buffers, camera);
            VertexConsumer solid = buffers.getBuffer(AWRenderTypes.RIFT_MEMBRANE);
            for (Show show : SHOWS.values()) {
                show.drawDark(solid, matrix, eye, partialTick, minecraft);
            }
            buffers.endBatch(AWRenderTypes.RIFT_MEMBRANE);
            for (Show show : SHOWS.values()) {
                show.drawBlocks(event.getPoseStack(), buffers, eye, partialTick, minecraft);
            }
            buffers.endBatch();
            return;
        }
        boolean flashes = !minecraft.options.hideLightningFlash().get();
        VertexConsumer fire = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
        for (Show show : SHOWS.values()) {
            show.drawLight(fire, matrix, eye, partialTick, flashes, minecraft);
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);
        VertexConsumer shard = buffers.getBuffer(AWRenderTypes.RIFT_SHARD);
        for (Show show : SHOWS.values()) {
            show.drawDust(shard, matrix, eye, partialTick, minecraft);
        }
        buffers.endBatch(AWRenderTypes.RIFT_SHARD);
    }

    // ------------------------------------------------------------------ one tear

    private static final class Show {

        private final BlockPos origin;
        private final RiftGateShape shape;
        private final boolean hand;
        private final boolean dry;
        private final int range;
        private final long seed;
        private final int length;
        private final List<BlockPos> standing;

        /** The opening's frame in the world: its middle is the middle of the cell the fissure is set in. */
        private final Vec3 middle;
        private final Vec3 across;
        private final Vec3 out;
        private final Unravelling.Axes axes;
        private final double size;

        /** The burst third: where each stood, from the middle, and its place in the swallowing. */
        private final List<BlockPos> lifted;
        private final double[][] homes;
        private final int[] ranks;
        private final BlockState frameState;
        /** Where each was when a rupture flung it, so it flies on from there. */
        @Nullable
        private Unravelling.Piece[] flungFrom;

        /** The hand's: the block it takes, and what that block looks like. */
        private final BlockPos gripAt;
        @Nullable
        private final BlockState carried;

        private long start;
        private double last = -1.0D;
        /** -1 while it runs; the tick it ended at, once misfired or ruptured. */
        private long ended = -1L;
        private int outcome = ClientboundSundererCuePacket.RUNNING;
        private float endedLens;
        private float endedSeed;
        @Nullable
        private Hum hum;
        private boolean drawn;

        Show(ClientboundSundererCuePacket packet, long start) {
            this.origin = packet.origin();
            this.shape = packet.shape();
            this.hand = packet.hand();
            this.dry = packet.dry();
            this.range = packet.range();
            this.seed = packet.seed();
            this.start = start;
            this.length = SundererSequence.length(hand);
            boolean acrossX = shape.span() == Direction.Axis.X;
            this.across = acrossX ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
            Vec3 normal = acrossX ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            Vec3 facing = Vec3.atLowerCornerOf(packet.facing().getNormal());
            this.out = facing.dot(normal) < 0.0D ? normal.scale(-1.0D) : normal;
            this.axes = new Unravelling.Axes(across.x, across.z, out.x, out.z);
            this.middle = Vec3.atCenterOf(packet.centre());
            this.size = Math.min(shape.halfWidth(), shape.halfHeight());
            this.lifted = packet.lifted();
            this.homes = new double[lifted.size()][];
            for (int index = 0; index < lifted.size(); index++) {
                Vec3 home = Vec3.atCenterOf(lifted.get(index)).subtract(middle);
                homes[index] = new double[]{home.x, home.y, home.z};
            }
            this.ranks = Unravelling.ranks(lifted.size(), seed);
            this.frameState = AWBlocks.RIFT_GATE_FRAME.get().defaultBlockState();
            this.standing = frameOf(shape, new HashSet<>(lifted));
            this.gripAt = packet.grip();
            Minecraft minecraft = Minecraft.getInstance();
            this.carried = minecraft.level == null ? null : minecraft.level.getBlockState(gripAt);
        }

        /** Every block of frame round the opening that did not burst: what touches an open cell and is not one. */
        private static List<BlockPos> frameOf(RiftGateShape shape, Set<BlockPos> lifted) {
            Set<BlockPos> open = new HashSet<>(shape.cells());
            Set<BlockPos> found = new HashSet<>();
            boolean acrossX = shape.span() == Direction.Axis.X;
            for (BlockPos cell : open) {
                for (int a = -1; a <= 1; a++) {
                    for (int u = -1; u <= 1; u++) {
                        BlockPos next = acrossX ? cell.offset(a, u, 0) : cell.offset(0, u, a);
                        if (!open.contains(next) && !lifted.contains(next)) {
                            found.add(next);
                        }
                    }
                }
            }
            return new ArrayList<>(found);
        }

        // ---------------------------------------------------------- when

        private double tick(float partialTick, ClientLevel level) {
            return level.getGameTime() - start + partialTick;
        }

        /** How far into a misfire's or a rupture's ending it is, 0 to 1; 0 while it runs. */
        private double ending(ClientLevel level, float partialTick) {
            return ended < 0L ? 0.0D : Math.min(1.0D, (level.getGameTime() - ended + partialTick) / CLOSING);
        }

        private double seedRadius(double t, ClientLevel level, float partialTick) {
            if (ended >= 0L) {
                return endedSeed * (1.0D - CollapseChoreography.smooth(ending(level, partialTick) * 3.0D));
            }
            return SundererChoreography.seed(t, hand, size, lifted.size());
        }

        private double lens(double t, ClientLevel level, float partialTick) {
            if (ended >= 0L) {
                return endedLens * (1.0D - CollapseChoreography.smooth(ending(level, partialTick)));
            }
            return SundererChoreography.lens(t, hand);
        }

        private double felt(Minecraft minecraft) {
            LocalPlayer player = minecraft.player;
            double distance = player == null ? 1.0e9D : player.position().distanceTo(middle);
            return 1.0D - CollapseChoreography.smooth(CollapseChoreography.span(distance, 16.0D, range));
        }

        private Unravelling.Piece piece(int index, double t) {
            double[] home = homes[index];
            return Unravelling.at(index, homes.length, ranks[index], home[0], home[1], home[2], axes, size, seed, t, hand);
        }

        void end(ClientLevel level, int how) {
            if (ended >= 0L) {
                return;
            }
            double t = level.getGameTime() - start;
            endedLens = (float) SundererChoreography.lens(t, hand);
            endedSeed = (float) SundererChoreography.seed(t, hand, size, lifted.size());
            ended = level.getGameTime();
            outcome = how;
            stopHum();
            if (how == ClientboundSundererCuePacket.MISFIRED) {
                play(level, AWSounds.SUNDERER_MISFIRE.get(), 1.4F, 1.0F);
            } else {
                flungFrom = new Unravelling.Piece[homes.length];
                for (int index = 0; index < homes.length; index++) {
                    flungFrom[index] = piece(index, t);
                }
                play(level, AWSounds.SUNDERER_RUPTURE.get(), 3.0F, 1.0F);
                if (!Minecraft.getInstance().options.hideLightningFlash().get()) {
                    level.setSkyFlashTime(4);
                }
                burst(level, middle, 220, 1.6D);
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, middle.x, middle.y, middle.z, 0.0D, 0.0D, 0.0D);
                WarpScreenShake.add((float) (0.6D * felt(Minecraft.getInstance())));
            }
            ClientTrace.log(ClientTrace.CUE, "sundering at {} ended for this client: {}", origin,
                    how == ClientboundSundererCuePacket.MISFIRED ? "misfired" : "ruptured");
        }

        /** @return whether it is over */
        boolean tick(ClientLevel level, Minecraft minecraft) {
            RandomSource random = level.random;
            double density = AWConfig.PARTICLE_DENSITY.get();
            if (ended >= 0L) {
                double since = level.getGameTime() - ended;
                if (flungFrom != null) {
                    for (int index = 0; index < flungFrom.length; index++) {
                        Unravelling.Piece now = Unravelling.thrown(flungFrom[index], index, seed, since);
                        Unravelling.Piece next = Unravelling.thrown(flungFrom[index], index, seed, since + 1.0D);
                        if (now.present() && !next.present()) {
                            Vec3 at = middle.add(now.x(), now.y(), now.z());
                            for (int i = 0; i < (int) (16 * density); i++) {
                                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, frameState), at.x, at.y, at.z,
                                        random.nextGaussian() * 0.1D, random.nextDouble() * 0.15D, random.nextGaussian() * 0.1D);
                            }
                        }
                    }
                }
                return since > Unravelling.THROWN_FOR + 2.0D;
            }
            double t = level.getGameTime() - start;
            if (t > length + 5) {
                return true;
            }
            double felt = felt(minecraft);
            RiftStormSky.lend((float) (SundererChoreography.darkness(t, hand) * felt));
            if (hum != null) {
                hum.voice = new Hum.Voice(middle, (float) SundererChoreography.humPitch(t, hand),
                        (float) (SundererChoreography.humVolume(t, hand) * felt));
            }
            double before = last < 0.0D ? t : last;

            // Motes drawn in from the frame to the point and then the seed, the whole time it is open.
            double drawing = Math.max(SundererChoreography.point(t), SundererChoreography.pull(t, hand));
            if (drawing > 0.0D && random.nextDouble() < 0.8D * density * drawing) {
                Vec3 from = Vec3.atCenterOf(standing.isEmpty() ? origin : standing.get(random.nextInt(standing.size())))
                        .subtract(middle);
                // Vanilla's portal particle runs from where it is sent towards where it was spawned: in, to the seed.
                level.addParticle(ParticleTypes.PORTAL, middle.x, middle.y, middle.z, from.x, from.y, from.z);
            }

            // Each burst block: a crack as it breaks free, grit as it grinds past another, and a swallow at the end.
            double sBefore = SundererChoreography.straining(before, hand);
            double sNow = SundererChoreography.straining(t, hand);
            boolean grinding = false;
            for (int index = 0; index < homes.length; index++) {
                double lifts = Unravelling.liftStart(index, homes.length);
                if (sBefore < lifts && sNow >= lifts) {
                    Vec3 at = Vec3.atCenterOf(lifted.get(index));
                    play(level, at, AWSounds.SUNDERER_CRACK.get(), 1.2F, 0.7F + random.nextFloat() * 0.3F);
                    for (int i = 0; i < (int) (12 * density); i++) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, frameState), at.x, at.y, at.z,
                                random.nextGaussian() * 0.08D, random.nextDouble() * 0.12D, random.nextGaussian() * 0.08D);
                    }
                }
                double gone = Unravelling.swallowed(ranks[index], homes.length);
                if (sBefore < gone && sNow >= gone) {
                    play(level, AWSounds.SUNDERER_SWALLOW.get(), 1.2F, 0.8F + random.nextFloat() * 0.4F);
                    burst(level, middle, 24, 0.3D);
                    WarpScreenShake.add((float) (0.08D * felt));
                }
                Unravelling.Piece piece = piece(index, t);
                if (!piece.present() || sNow < Unravelling.liftEnd(index, homes.length)) {
                    continue;
                }
                Vec3 at = middle.add(piece.x(), piece.y(), piece.z());
                for (int other = index + 1; other < homes.length; other++) {
                    Unravelling.Piece near = piece(other, t);
                    if (near.present() && at.distanceTo(middle.add(near.x(), near.y(), near.z())) < 1.0D) {
                        grinding = true;
                        if (random.nextDouble() < 0.5D * density) {
                            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, frameState), at.x, at.y, at.z,
                                    random.nextGaussian() * 0.05D, 0.05D, random.nextGaussian() * 0.05D);
                        }
                    }
                }
            }
            if (grinding && random.nextDouble() < 0.12D) {
                play(level, AWSounds.SUNDERER_GRIND.get(), 0.9F, 0.7F + random.nextFloat() * 0.3F);
            }

            // The standing frame sheds grit while it strains.
            if (sNow > 0.0D && sNow < 1.0D && !standing.isEmpty() && random.nextDouble() < 0.3D * density) {
                BlockPos pos = standing.get(random.nextInt(standing.size()));
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + random.nextDouble(),
                            pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0D, 0.0D, 0.0D);
                }
            }

            if (hand) {
                handSounds(level, t);
            }

            // The breach: the seed lets go.
            if (last >= 0.0D && !SundererChoreography.torn(last, hand) && SundererChoreography.torn(t, hand)) {
                play(level, AWSounds.SUNDERER_BREACH.get(), 3.0F, 1.0F);
                if (!minecraft.options.hideLightningFlash().get()) {
                    level.setSkyFlashTime(3);
                }
                level.addParticle(ParticleTypes.FLASH, middle.x, middle.y, middle.z, 0.0D, 0.0D, 0.0D);
                burst(level, middle, 140, 1.0D);
                WarpScreenShake.add((float) (0.5D * felt));
            }
            double dust = SundererChoreography.dust(t, hand);
            if (dust > 0.5D && dust < SundererChoreography.DUST_REACH - 0.2D) {
                double ground = shape.minY() - 0.5D;
                int count = (int) Math.max(4, 28 * density);
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level.addParticle(ParticleTypes.POOF, middle.x + Math.cos(angle) * dust, ground + 0.6D,
                            middle.z + Math.sin(angle) * dust, Math.cos(angle) * 0.08D, 0.02D, Math.sin(angle) * 0.08D);
                }
            }
            if (!drawn && t > 40 && AWConfig.RIFT_DISTORTION.get()) {
                ClientTrace.log(ClientTrace.CUE, "sundering at {} has drawn no frame by tick {}", origin, (int) t);
                drawn = true;
            }
            last = t;
            return false;
        }

        private void handSounds(ClientLevel level, double t) {
            double beat = SundererChoreography.handBeat(t, true);
            double was = last < 0.0D ? 0.0D : SundererChoreography.handBeat(last, true);
            if (was < 0.02D && beat >= 0.02D) {
                play(level, AWSounds.SUNDERER_HAND_CREAK.get(), 2.0F, 1.0F);
            }
            if (was < RiftHandPose.FINGERS_END && beat >= RiftHandPose.FINGERS_END) {
                play(level, AWSounds.SUNDERER_HAND_BREATH.get(), 1.6F, 1.0F);
            }
            if (was < RiftHandPose.FEEL_END && beat >= RiftHandPose.FEEL_END) {
                play(level, AWSounds.SUNDERER_HAND_BREATH.get(), 1.2F, 0.85F);
            }
            if (was < 0.96D && beat >= 0.96D) {
                play(level, AWSounds.SUNDERER_CRACK.get(), 2.0F, 0.5F);
            }
        }

        private void burst(ClientLevel level, Vec3 from, int count, double speed) {
            RandomSource random = level.random;
            int many = (int) (count * AWConfig.PARTICLE_DENSITY.get());
            for (int i = 0; i < many; i++) {
                Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
                        .scale(0.2D + random.nextDouble() * speed);
                level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : AWParticles.RIFT_SPARK.get(), from.x, from.y,
                        from.z, way.x, way.y, way.z);
            }
        }

        void play(ClientLevel level, SoundEvent sound, float volume, float pitch) {
            play(level, middle, sound, volume, pitch);
        }

        void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
            level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
        }

        void startHum(Minecraft minecraft) {
            hum = new Hum(new Hum.Voice(middle, 1.0F, 0.0F));
            minecraft.getSoundManager().play(hum);
        }

        void stopHum() {
            if (hum != null) {
                hum.voice = null;
                hum = null;
            }
        }

        // ---------------------------------------------------------- the hand

        /**
         * The hand's frame: its slit on the seed's edge in the ring's plane, on the side towards the block it takes,
         * its arm coming out on the side the player is on. Whatever of the arm is still inside the seed, the seed's
         * own depth hides.
         */
        private RiftHandRenderer.Frame handFrame(Minecraft minecraft, double seedRadius) {
            Vec3 toGrip = Vec3.atCenterOf(gripAt).subtract(middle);
            Vec3 flat = across.scale(toGrip.dot(across)).add(0.0D, toGrip.y, 0.0D);
            Vec3 way = flat.lengthSqr() < 1.0e-6D ? new Vec3(0.0D, 1.0D, 0.0D) : flat.normalize();
            Vec3 slit = middle.add(way.scale(seedRadius * 0.9D));
            Vec3 outward = out;
            LocalPlayer player = minecraft.player;
            if (player != null && player.getEyePosition().subtract(slit).dot(out) < 0.0D) {
                outward = out.scale(-1.0D);
            }
            Vec3 acrossHere = outward.equals(out) ? across : across.scale(-1.0D);
            return new RiftHandRenderer.Frame(slit, acrossHere, new Vec3(0.0D, 1.0D, 0.0D), outward);
        }

        @Nullable
        private RiftHandPose.Pose pose(double t, Minecraft minecraft, RiftHandRenderer.Frame frame) {
            if (!hand || ended >= 0L || !SundererChoreography.handOut(t, true)) {
                return null;
            }
            RiftHandPose.Point gripHere = frame.local(Vec3.atCenterOf(gripAt));
            RiftHandPose.Point viewer = null;
            LocalPlayer player = minecraft.player;
            if (player != null && player.getEyePosition().distanceTo(frame.slit()) < 8.0D) {
                viewer = frame.local(player.getEyePosition());
            }
            return RiftHandPose.at(SundererChoreography.handBeat(t, true), gripHere, viewer);
        }

        // ---------------------------------------------------------- drawing

        /** The lens: round the seed, wide enough to bend the standing frame. */
        void bend(List<GravityLens.Bend> bends, ClientLevel level, float partialTick) {
            double t = tick(partialTick, level);
            float strength = (float) lens(t, level, partialTick);
            if (strength > 0.001F && GravityLens.available()) {
                bends.add(new GravityLens.Bend(middle, 1.5D * Math.max(0.6D, size * 0.8D) / Nebula.REACH, strength));
            }
        }

        void drawDark(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, float partialTick, Minecraft minecraft) {
            ClientLevel level = minecraft.level;
            if (level == null) {
                return;
            }
            // The pass reached it, which is what the trace asks. Not the seed: that is not born until SEED_FROM, and
            // the point before it is on the light pass.
            drawn = true;
            double t = tick(partialTick, level);
            double radius = seedRadius(t, level, partialTick);
            if (radius > 0.01D) {
                ball(consumer, matrix, eye, middle, radius);
            }
            RiftHandRenderer.Frame frame = handFrame(minecraft, radius);
            RiftHandPose.Pose pose = pose(t, minecraft, frame);
            if (pose != null) {
                RiftHandRenderer.drawSolid(consumer, matrix, eye, frame, pose);
            }
        }

        /**
         * The burst third of the frame, as real block models: tumbling, stretched towards the seed as it falls in,
         * and lit by the light where each one is. After a misfire the real blocks are back, so none is drawn.
         */
        void drawBlocks(PoseStack poses, MultiBufferSource buffers, Vec3 eye, float partialTick, Minecraft minecraft) {
            ClientLevel level = minecraft.level;
            if (level == null || outcome == ClientboundSundererCuePacket.MISFIRED) {
                return;
            }
            double t = tick(partialTick, level);
            for (int index = 0; index < homes.length; index++) {
                Unravelling.Piece piece = piece(index, t);
                if (ended >= 0L && flungFrom != null) {
                    piece = Unravelling.thrown(flungFrom[index], index, seed, level.getGameTime() - ended + partialTick);
                }
                if (!piece.present() || piece.scale() <= 0.01D) {
                    continue;
                }
                drawBlock(poses, buffers, eye, minecraft, level, frameState, middle.add(piece.x(), piece.y(), piece.z()),
                        piece);
            }
            drawCarried(poses, buffers, eye, partialTick, minecraft, level, t);
        }

        private void drawBlock(PoseStack poses, MultiBufferSource buffers, Vec3 eye, Minecraft minecraft, ClientLevel level,
                               BlockState state, Vec3 at, Unravelling.Piece piece) {
            poses.pushPose();
            poses.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
            if (piece.stretch() > 1.001D) {
                // Drawn out along the way to the seed: turned so that way is z, stretched along z, turned back.
                Vec3 toSeed = middle.subtract(at);
                if (toSeed.lengthSqr() > 1.0e-6D) {
                    Vector3f way = toSeed.normalize().toVector3f();
                    Quaternionf align = new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), way);
                    poses.mulPose(align);
                    poses.scale(1.0F, 1.0F, (float) piece.stretch());
                    poses.mulPose(new Quaternionf(align).conjugate());
                }
            }
            poses.mulPose(new Quaternionf().rotationAxis((float) piece.angle(), (float) piece.axisX(),
                    (float) piece.axisY(), (float) piece.axisZ()));
            float scale = (float) piece.scale();
            poses.scale(scale, scale, scale);
            poses.translate(-0.5D, -0.5D, -0.5D);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
            minecraft.getBlockRenderer().renderSingleBlock(state, poses, buffers, light, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }

        /** The block a hand took, in the curl of its fingers as it goes back. */
        private void drawCarried(PoseStack poses, MultiBufferSource buffers, Vec3 eye, float partialTick,
                                 Minecraft minecraft, ClientLevel level, double t) {
            if (carried == null || carried.isAir() || !hand || dry) {
                return;
            }
            double beat = SundererChoreography.handBeat(t, true);
            if (beat < RiftHandPose.TAKES || beat >= 1.0D) {
                return;
            }
            RiftHandRenderer.Frame frame = handFrame(minecraft, seedRadius(t, level, partialTick));
            RiftHandPose.Pose pose = pose(t, minecraft, frame);
            if (pose == null || pose.carried().n() < -0.4D) {
                return;
            }
            Vec3 at = frame.world(pose.carried());
            poses.pushPose();
            poses.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
            poses.scale(0.8F, 0.8F, 0.8F);
            poses.translate(-0.5D, -0.5D, -0.5D);
            minecraft.getBlockRenderer().renderSingleBlock(carried, poses, buffers, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }

        void drawLight(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, float partialTick, boolean flashes,
                       Minecraft minecraft) {
            ClientLevel level = minecraft.level;
            if (level == null) {
                return;
            }
            double t = tick(partialTick, level);
            double fade = 1.0D - ending(level, partialTick);
            double point = ended >= 0L ? 0.0D : SundererChoreography.point(t);
            if (point > 0.001D) {
                glow(consumer, matrix, eye, middle, 0.0D, 0.12D + 0.3D * point, WHITE, (float) point, 0.0F);
                glow(consumer, matrix, eye, middle, 0.0D, 0.6D + 1.2D * point, GLOW, (float) (0.6D * point), 0.0F);
            }
            double search = ended >= 0L ? 0.0D : SundererChoreography.search(t);
            if (search > 0.001D) {
                starlines(consumer, matrix, eye, search, t);
            }
            double radius = seedRadius(t, level, partialTick);
            if (radius > 0.01D) {
                // The seed's limb, burning round its edge.
                glow(consumer, matrix, eye, middle, radius * 0.92D, radius * 1.7D, GLOW, (float) (0.55D * fade), 0.0F);
            }
            // A glint behind each orbiting block, along the way it came.
            if (ended < 0L) {
                for (int index = 0; index < homes.length; index++) {
                    Unravelling.Piece now = piece(index, t);
                    if (!now.present() || SundererChoreography.straining(t, hand) < Unravelling.liftEnd(index, homes.length)) {
                        continue;
                    }
                    Unravelling.Piece back = piece(index, Math.max(0.0D, t - 4.0D));
                    Vec3 a = middle.add(back.x(), back.y(), back.z());
                    Vec3 b = middle.add(now.x(), now.y(), now.z());
                    Vec3 along = b.subtract(a);
                    if (along.lengthSqr() > 1.0e-4D) {
                        Vec3 side = along.normalize().cross(eye.subtract(b)).normalize();
                        line(consumer, matrix, eye, a, b, side, 0.35D * now.scale(), GLOW, 0.35F);
                    }
                }
            }
            double flash = ended >= 0L ? 0.0D : SundererChoreography.flash(t, hand);
            if (flash > 0.001D) {
                float alpha = (float) (flash * (flashes ? 1.0D : 0.35D));
                glow(consumer, matrix, eye, middle, 0.0D, 1.5D + 5.0D * flash, WHITE, alpha, 0.0F);
                glow(consumer, matrix, eye, middle, 0.0D, 4.0D + 9.0D * flash, GLOW, alpha * 0.5F, 0.0F);
            }
            RiftHandRenderer.Frame frame = handFrame(minecraft, radius);
            RiftHandPose.Pose pose = pose(t, minecraft, frame);
            if (pose != null) {
                RiftHandRenderer.drawLight(consumer, matrix, eye, frame, pose, 1.0F);
            }
        }

        /**
         * The starlines: a tube of streaks shooting out of the point both ways along the ring's axis, reaching for a
         * far side, and thinning to nothing when there is none.
         */
        private void starlines(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, double search, double t) {
            Random random = new Random(seed ^ 0x51A7L);
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            for (int line = 0; line < STARLINES; line++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double reach = 0.3D + random.nextDouble() * Math.max(0.6D, size * 0.8D);
                double speed = 0.6D + random.nextDouble() * 0.8D;
                double phase = random.nextDouble();
                Vec3 radial = across.scale(Math.cos(angle) * reach).add(up.scale(Math.sin(angle) * reach));
                for (double way : new double[]{1.0D, -1.0D}) {
                    // Each streak runs outward along the axis and starts again, so the tube is seen to rush.
                    double run = (phase + t * 0.03D * speed) % 1.0D;
                    double near = run * 26.0D * search;
                    double far = near + (3.0D + 5.0D * speed) * search;
                    Vec3 a = middle.add(radial.scale(0.6D + 0.4D * run)).add(out.scale(way * near));
                    Vec3 b = middle.add(radial).add(out.scale(way * far));
                    Vec3 side = b.subtract(a).normalize().cross(eye.subtract(b)).normalize();
                    float alpha = (float) (search * (1.0D - run) * 0.8D);
                    line(consumer, matrix, eye, a, b, side, 0.06D, line % 3 == 0 ? WHITE : GLOW, alpha);
                }
            }
        }

        /**
         * The dust disc, on the shard pass so it reads by day: a turning band of grit in the orbit's plane, spiral
         * arms wound by the orbit; and, under a shader pack where there is no lens, a dark halo round the seed in its
         * place.
         */
        void drawDust(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, float partialTick, Minecraft minecraft) {
            ClientLevel level = minecraft.level;
            if (level == null) {
                return;
            }
            double t = tick(partialTick, level);
            double radius = seedRadius(t, level, partialTick);
            if (!GravityLens.available()) {
                float shade = (float) (0.6D * lens(t, level, partialTick));
                if (shade > 0.001F && radius > 0.01D) {
                    glow(consumer, matrix, eye, middle, radius, radius + Math.max(0.8D, size * 0.7D), SHADOW, shade, 0.0F);
                }
            }
            double whole = ended >= 0L ? 0.0D : SundererChoreography.disc(t, hand);
            if (whole <= 0.001D || radius <= 0.01D) {
                return;
            }
            double[] plane = Unravelling.orbitPlane(axes, t, hand);
            Vec3 a = new Vec3(plane[0], plane[1], plane[2]);
            Vec3 b = new Vec3(plane[3], plane[4], plane[5]);
            double inner = radius * 1.25D;
            double outer = Math.max(inner + 0.6D, SundererChoreography.largestSeed(size) + 1.4D);
            double spin = SundererChoreography.orbitAngle(t, hand) * 1.3D;
            for (int ring = 0; ring < DISC_RINGS; ring++) {
                double t0 = ring / (double) DISC_RINGS;
                double t1 = (ring + 1) / (double) DISC_RINGS;
                double r0 = Mth.lerp(t0, inner, outer);
                double r1 = Mth.lerp(t1, inner, outer);
                for (int segment = 0; segment < DISC_SEGMENTS; segment++) {
                    double a0 = Math.PI * 2.0D * segment / DISC_SEGMENTS;
                    double a1 = Math.PI * 2.0D * (segment + 1) / DISC_SEGMENTS;
                    discVertex(consumer, matrix, eye, a, b, r0, a0, t0, inner, spin, whole);
                    discVertex(consumer, matrix, eye, a, b, r0, a1, t0, inner, spin, whole);
                    discVertex(consumer, matrix, eye, a, b, r1, a1, t1, inner, spin, whole);
                    discVertex(consumer, matrix, eye, a, b, r1, a0, t1, inner, spin, whole);
                }
            }
        }

        private void discVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 a, Vec3 b, double radius,
                                double angle, double t, double inner, double spin, double whole) {
            Vec3 at = middle.add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius));
            double profile = CollapseChoreography.smooth(t / 0.2D) * Math.pow(1.0D - t, 1.3D);
            double arms = 0.5D + 0.5D * Math.cos(3.0D * angle - 4.0D * Math.log(radius / inner + 1.0D) - spin);
            fire(consumer, matrix, eye, at, DUST, (float) Math.min(0.85D, 1.6D * whole * profile * (0.35D + 0.65D * arms)));
        }

        // ---------------------------------------------------------- the seed

        /** A black ball with a violet limb, edge-on to the eye glowing and face-on nothing: the seed. */
        private static void ball(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, double radius) {
            for (int band = 0; band < SPHERE_BANDS; band++) {
                double lat0 = -Math.PI / 2.0D + Math.PI * band / SPHERE_BANDS;
                double lat1 = -Math.PI / 2.0D + Math.PI * (band + 1) / SPHERE_BANDS;
                for (int segment = 0; segment < SPHERE_SEGMENTS; segment++) {
                    double lon0 = Math.PI * 2.0D * segment / SPHERE_SEGMENTS;
                    double lon1 = Math.PI * 2.0D * (segment + 1) / SPHERE_SEGMENTS;
                    ballVertex(consumer, matrix, eye, centre, radius, lat0, lon0);
                    ballVertex(consumer, matrix, eye, centre, radius, lat0, lon1);
                    ballVertex(consumer, matrix, eye, centre, radius, lat1, lon1);
                    ballVertex(consumer, matrix, eye, centre, radius, lat1, lon0);
                }
            }
        }

        private static void ballVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, double radius,
                                       double latitude, double longitude) {
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
            float edge = (float) Math.pow(1.0D - Math.abs((nx * vx + ny * vy + nz * vz) / length), 2.5D);
            consumer.addVertex(matrix, (float) (x - eye.x), (float) (y - eye.y), (float) (z - eye.z))
                    .setColor(SEED_RED + LIMB[0] * edge, SEED_GREEN + LIMB[1] * edge, SEED_BLUE + LIMB[2] * edge, 1.0F);
        }

        // ---------------------------------------------------------- light helpers

        private static void line(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 a, Vec3 b, Vec3 side,
                                 double width, int colour, float alpha) {
            Vec3 half = side.scale(width * 0.5D);
            fire(consumer, matrix, eye, a.add(half), colour, 0.0F);
            fire(consumer, matrix, eye, b.add(half), colour, 0.0F);
            fire(consumer, matrix, eye, b, colour, alpha);
            fire(consumer, matrix, eye, a, colour, alpha);
            fire(consumer, matrix, eye, a, colour, alpha);
            fire(consumer, matrix, eye, b, colour, alpha);
            fire(consumer, matrix, eye, b.subtract(half), colour, 0.0F);
            fire(consumer, matrix, eye, a.subtract(half), colour, 0.0F);
        }

        /** A ring of light square to the line of sight, its alpha running from inner to outer. */
        private static void glow(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 centre, double inner,
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
            int segments = 32;
            for (int segment = 0; segment < segments; segment++) {
                double a0 = Math.PI * 2.0D * segment / segments;
                double a1 = Math.PI * 2.0D * (segment + 1) / segments;
                fire(consumer, matrix, eye, centre.add(right.scale(Math.cos(a0) * inner)).add(up.scale(Math.sin(a0) * inner)), colour, innerAlpha);
                fire(consumer, matrix, eye, centre.add(right.scale(Math.cos(a1) * inner)).add(up.scale(Math.sin(a1) * inner)), colour, innerAlpha);
                fire(consumer, matrix, eye, centre.add(right.scale(Math.cos(a1) * outer)).add(up.scale(Math.sin(a1) * outer)), colour, outerAlpha);
                fire(consumer, matrix, eye, centre.add(right.scale(Math.cos(a0) * outer)).add(up.scale(Math.sin(a0) * outer)), colour, outerAlpha);
            }
        }

        private static void fire(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 at, int colour, float alpha) {
            consumer.addVertex(matrix, (float) (at.x - eye.x), (float) (at.y - eye.y), (float) (at.z - eye.z))
                    .setColor((Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (colour & 0xFFFFFF))
                    .setUv(0.5F, 0.5F)
                    .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                    .setNormal(0.0F, 1.0F, 0.0F);
        }
    }

    // ------------------------------------------------------------------ the groan

    /**
     * The groan of a straining ring: a loop that follows the show's voice and climbs with it. A tickable loop is
     * left unwrapped by the sound filter, so it takes its own share of the mix ({@link TrimmedSound#level()}).
     */
    private static final class Hum extends AbstractTickableSoundInstance {

        record Voice(Vec3 at, float pitch, float volume) {
        }

        @Nullable
        private volatile Voice voice;

        Hum(Voice first) {
            super(AWSounds.SUNDERER_STRAIN.get(), SoundSource.BLOCKS, RandomSource.create());
            this.voice = first;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.01F;
            this.x = first.at().x;
            this.y = first.at().y;
            this.z = first.at().z;
        }

        @Override
        public void tick() {
            Voice now = voice;
            if (now == null) {
                stop();
                return;
            }
            x = now.at().x;
            y = now.at().y;
            z = now.at().z;
            pitch = Math.max(0.5F, Math.min(2.0F, now.pitch()));
            volume += (Math.max(0.01F, now.volume() * 1.6F * TrimmedSound.level()) - volume) * 0.15F;
        }
    }
}
