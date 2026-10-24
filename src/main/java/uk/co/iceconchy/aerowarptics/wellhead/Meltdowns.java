package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.advancement.WellheadTrigger;
import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;
import uk.co.iceconchy.aerowarptics.collapse.CradleRing;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.network.ClientboundMeltdownCuePacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every meltdown running in a level, and the playing out of each.
 *
 * <p>A meltdown cannot live in its Rift Collar: the frame is the first thing it destroys. It is a record in the
 * level's own data, ticked with the level, so it goes on when the player who caused it walks away - it holds its
 * own ground, through {@link Holds} like everything else that loads a chunk, out to the edge of what it will
 * take - and picks up where it was after a restart. One whose ground is unloaded all the same simply waits.
 *
 * <p>When it happens is {@link MeltdownChoreography}'s; what is taken is {@link CraterPlan}'s; this is the part
 * with a world in it. Clients are told once a second where a meltdown has got to, so one who arrives half-way
 * joins it half-way.
 *
 * <h2>What it costs</h2>
 * The largest crater is nearly half a million blocks, so nothing here is done all at once. The ground goes by a
 * budget a tick ({@code wellheadCarveBudget}); what the scene's own pace asks for past that is carried over and
 * finished in the fallout, and the lining and the settling of the wall after it take their turn under the same
 * budget. Blocks are set without telling their neighbours and the wall is told once, at the end.
 *
 * <p>The ground is taken through NeoForge's explosion event, a tick's worth at a time, so a mod that protects
 * land from explosions protects it from this.
 *
 * <h2>A dry run</h2>
 * A meltdown begun {@code dry} is the scene and nothing else: clients are told of it exactly as they are of a
 * real one, and it sets no block, moves and hurts nothing, and leaves neither a zone nor a wound. It is how the
 * scene is looked at - see {@link WellheadCommands}.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public class Meltdowns extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_meltdowns";

    /** How far past the crater, in radii, the swell draws things in and the fallout reaches. */
    private static final double PULL_REACH = 3.0D;
    static final double OUTER_REACH = 1.35D;

    /** Blocks a tick the ring of air and its boom travel. A little over a real one's: it has to be watched arriving. */
    private static final double RING_SPEED = 2.5D;

    private final List<Meltdown> running = new ArrayList<>();

    public static Meltdowns get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Meltdowns::new, Meltdowns::load, null), FILE_ID);
    }

    /**
     * Begins one. The frame has been let go and the fissure still stands: it goes at the detonation.
     *
     * @param dry the scene only - nothing in the world is changed, moved or hurt
     */
    public static void begin(ServerLevel level, BlockPos centre, WellheadTier tier, int frameRadius, boolean dry) {
        Meltdowns meltdowns = get(level);
        for (Meltdown meltdown : meltdowns.running) {
            if (meltdown.centre.equals(centre)) {
                return;
            }
        }
        int crater = Math.min(CraterPlan.MOST - 2, AWConfig.WELLHEAD_CRATER.get(tier).get());
        Meltdown meltdown = new Meltdown(centre.immutable(), tier, frameRadius, crater,
                AWConfig.WELLHEAD_MELTDOWN_TICKS.get(), 0, dry, level.random.nextLong());
        meltdowns.running.add(meltdown);
        meltdowns.setDirty();
        Trace.always(Trace.WELLHEAD, "meltdown begun at {} ({}{}): crater of radius {} ({} blocks) in {} ticks",
                Trace.pos(centre), tier.getSerializedName(), dry ? ", dry" : "", crater, CraterPlan.blocks(crater),
                meltdown.length);
        level.playSound(null, centre, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 8.0F, 0.5F);
        meltdown.tell(level);
    }

    /** Whether a real meltdown is running over this block: nothing forms, and nothing new begins, inside one. */
    public static boolean over(ServerLevel level, BlockPos pos) {
        for (Meltdown meltdown : get(level).running) {
            double reach = meltdown.crater * OUTER_REACH;
            if (!meltdown.dry && meltdown.centre.distSqr(pos) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Meltdowns meltdowns = get(level);
        if (meltdowns.running.isEmpty()) {
            return;
        }
        for (Iterator<Meltdown> each = meltdowns.running.iterator(); each.hasNext(); ) {
            Meltdown meltdown = each.next();
            // A meltdown whose ground is not there waits: it never plays out unseen into unloaded land.
            if (!level.isLoaded(meltdown.centre)) {
                continue;
            }
            if (meltdown.tick(level)) {
                each.remove();
            }
            meltdowns.setDirty();
        }
        if (meltdowns.running.isEmpty()) {
            CraterPlan.forget();
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Meltdown meltdown : running) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Centre", meltdown.centre.asLong());
            entry.putInt("Tier", meltdown.tier.ordinal());
            entry.putInt("Frame", meltdown.frame);
            entry.putInt("Crater", meltdown.crater);
            entry.putInt("Length", meltdown.length);
            entry.putInt("Elapsed", meltdown.elapsed);
            entry.putBoolean("Dry", meltdown.dry);
            entry.putLong("Seed", meltdown.seed);
            entry.putInt("Shell", meltdown.shell);
            entry.putInt("ShellAt", meltdown.shellAt);
            entry.putInt("Lined", meltdown.lined);
            entry.putInt("Settled", meltdown.settled);
            entry.putBoolean("Veined", meltdown.veined);
            list.add(entry);
        }
        tag.put("Running", list);
        return tag;
    }

    private static Meltdowns load(CompoundTag tag, HolderLookup.Provider registries) {
        Meltdowns meltdowns = new Meltdowns();
        ListTag list = tag.getList("Running", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            WellheadTier[] tiers = WellheadTier.values();
            Meltdown meltdown = new Meltdown(BlockPos.of(entry.getLong("Centre")),
                    tiers[Math.max(0, Math.min(tiers.length - 1, entry.getInt("Tier")))],
                    Math.max(1, Math.min(WellheadStructure.MAX_RADIUS, entry.getInt("Frame"))),
                    Math.max(1, Math.min(CraterPlan.MOST - 2, entry.getInt("Crater"))), Math.max(20, entry.getInt("Length")),
                    Math.max(0, entry.getInt("Elapsed")), entry.getBoolean("Dry"), entry.getLong("Seed"));
            meltdown.shell = Math.max(0, entry.getInt("Shell"));
            meltdown.shellAt = Math.max(0, entry.getInt("ShellAt"));
            meltdown.lined = Math.max(0, entry.getInt("Lined"));
            meltdown.settled = Math.max(0, entry.getInt("Settled"));
            meltdown.veined = entry.getBoolean("Veined");
            meltdowns.running.add(meltdown);
        }
        return meltdowns;
    }

    // ------------------------------------------------------------------ one of them

    private static final class Meltdown {

        private final BlockPos centre;
        private final WellheadTier tier;
        /** The radius of the frame that failed, so its blocks can be thrown off first. */
        private final int frame;
        private final int crater;
        private final int length;
        private final boolean dry;
        /** What the hanging ground is rolled from, so a resumed meltdown leaves the blobs it meant to. */
        private final long seed;
        private int elapsed;

        /** How far the ground's going has got: the shell being taken and how far into it; then the wall after it. */
        private int shell;
        private int shellAt;
        private int lined;
        private int settled;
        private boolean veined;

        /** Loose ground in the air, and the boom on its way to each player. Not saved: a moment each. */
        private final List<FallingBlockEntity> debris = new ArrayList<>();
        private final List<Object[]> booms = new ArrayList<>();
        private List<int[]> islands;
        private int[] lining;
        private int strikes;
        private int rained;
        private int thrown;
        private double ringBefore;

        private Meltdown(BlockPos centre, WellheadTier tier, int frame, int crater, int length, int elapsed, boolean dry,
                         long seed) {
            this.centre = centre;
            this.tier = tier;
            this.frame = frame;
            this.crater = crater;
            this.length = length;
            this.elapsed = elapsed;
            this.dry = dry;
            this.seed = seed;
        }

        private double progress() {
            return Math.min(1.0D, elapsed / (double) length);
        }

        private List<int[]> islands() {
            if (islands == null) {
                islands = CraterPlan.islands(seed, crater, 5 + 4 * tier.ordinal() + (tier == WellheadTier.III ? 3 : 0));
            }
            return islands;
        }

        private int mostDebris() {
            return AWConfig.WELLHEAD_DEBRIS.get() * (tier.ordinal() + 1);
        }

        private void tell(ServerLevel level) {
            int range = AWConfig.WELLHEAD_SPECTACLE_RANGE.get();
            ClientboundMeltdownCuePacket cue = new ClientboundMeltdownCuePacket(centre, crater, frame, length, elapsed, range, dry);
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().distSqr(centre) <= (double) range * range) {
                    PacketDistributor.sendToPlayer(player, cue);
                }
            }
        }

        /** @return whether it is over */
        private boolean tick(ServerLevel level) {
            boolean breaks = !dry && AWConfig.WELLHEAD_MELTDOWN_BREAKS_BLOCKS.get() && !Holds.inPlot(level, centre);
            if (!dry && elapsed % Holds.REACH_RENEW_INTERVAL == 0) {
                // Out to the edge of what it takes: a hold of the one chunk the tear stands in left a large crater
                // skipping everything past it.
                Holds.holdGround(level, centre, (int) Math.ceil(crater * OUTER_REACH / 16.0D) + 1);
            }
            MeltdownChoreography.Beat before = MeltdownChoreography.Beat.at(progress());
            elapsed++;
            double progress = progress();
            MeltdownChoreography.Beat beat = MeltdownChoreography.Beat.at(progress);
            if (elapsed % 20 == 0 || beat != before) {
                tell(level);
            }
            RandomSource random = level.random;
            booms(level);
            switch (beat) {
                case BREACH -> {
                    if (breaks) {
                        breach(level, random);
                    }
                    if (!dry) {
                        strike(level);
                    }
                }
                case SWELL -> {
                    if (before == MeltdownChoreography.Beat.BREACH) {
                        level.playSound(null, centre, SoundEvents.WARDEN_ROAR, SoundSource.BLOCKS, 10.0F, 0.5F);
                        if (breaks) {
                            clearFrame(level);
                        }
                    }
                    if (breaks) {
                        peel(level, random, progress);
                    }
                    if (!dry) {
                        draw(level, progress);
                    }
                }
                case HUSH -> hang();
                case DETONATION -> {
                    if (before != MeltdownChoreography.Beat.DETONATION) {
                        detonate(level, breaks);
                    }
                    if (!dry) {
                        wave(level, progress);
                    }
                    if (breaks) {
                        carve(level, (int) Math.floor(MeltdownChoreography.carve(progress) * crater), random);
                    }
                }
                case FALLOUT -> {
                    if (!dry) {
                        wave(level, progress);
                    }
                    if (breaks) {
                        // Whatever the scene's pace outran the budget by, and then the wall: lined, settled, veined.
                        if (carve(level, crater, random) && line(level) && settle(level) && !veined) {
                            veined = true;
                            veins(level, random);
                        }
                        fallout(level, random);
                    }
                }
                case OVER -> {
                    if (breaks) {
                        while (!(carve(level, crater, random) && line(level) && settle(level))) {
                            // Finish what is left before the record goes: a crater is never left half taken.
                        }
                    }
                    if (!dry) {
                        ScarZones.leave(level, centre, (int) Math.ceil(crater * OUTER_REACH), tier);
                    }
                    Trace.always(Trace.WELLHEAD, "meltdown at {} over{}", Trace.pos(centre), dry ? " (dry)" : "");
                    return true;
                }
            }
            return false;
        }

        // ---------------------------------------------------------------- breach

        /** The frame fails, all of it inside a couple of seconds: glass before casing, each block thrown hard. */
        private void breach(ServerLevel level, RandomSource random) {
            List<Vec3i> shell = WellheadStructure.shell(frame);
            int most = Math.max(1, shell.size() / 30);
            int done = 0;
            for (int pass = 0; pass < 2 && done < most; pass++) {
                for (int index = 0; index < shell.size() && done < most; index++) {
                    Vec3i offset = shell.get((index * 7 + elapsed * 13) % shell.size());
                    BlockPos pos = centre.offset(offset);
                    BlockState state = level.getBlockState(pos);
                    boolean glass = state.is(AWBlocks.WARP_GLASS.get());
                    if (pass == 0 ? !glass : !(state.is(AWBlocks.RIFT_CASING.get()) || state.getBlock() instanceof RiftCollarBlock)) {
                        continue;
                    }
                    done++;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.playSound(null, pos, glass ? SoundEvents.GLASS_BREAK : SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                            SoundSource.BLOCKS, 3.0F, 0.5F + random.nextFloat() * 0.5F);
                    level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 1,
                            0.0D, 0.0D, 0.0D, 0.0D);
                    Vec3 out = new Vec3(offset.getX(), offset.getY() + 0.6D, offset.getZ()).normalize()
                            .scale(0.9D + random.nextDouble() * 0.6D);
                    throwPiece(level, pos, state, out, false);
                }
            }
        }

        /** Whatever of the frame the breach did not get to goes as the swell begins: nothing of it outlives the tear. */
        private void clearFrame(ServerLevel level) {
            for (Vec3i offset : WellheadStructure.shell(frame)) {
                BlockPos pos = centre.offset(offset);
                BlockState state = level.getBlockState(pos);
                if (state.is(AWBlocks.WARP_GLASS.get()) || state.is(AWBlocks.RIFT_CASING.get())
                        || state.getBlock() instanceof RiftCollarBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        /** Lightning walks from the tear to each switched-on stabiliser round it, one every few ticks. For show. */
        private void strike(ServerLevel level) {
            if (elapsed % 6 != 0) {
                return;
            }
            int reach = AWConfig.WELLHEAD_STABILISER_REACH.get();
            List<int[]> cage = CradleRing.stabilisers(reach, (dx, dy, dz) ->
                    SpatialStabiliserBlock.switchedOn(level.getBlockState(centre.offset(dx, dy, dz))));
            if (strikes >= cage.size()) {
                return;
            }
            int[] offset = cage.get(strikes++);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(centre.offset(offset[0], offset[1] + 1, offset[2])));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }

        // ---------------------------------------------------------------- swell

        /**
         * The ground strips towards the tear from the surface down: each tick the top block of a handful of
         * columns inside the reach comes loose, most of them near the reach's own edge so it is seen to advance.
         * The top of a column is whatever the sky sees first, so a tree goes leaves, then trunk, then the earth
         * under it.
         */
        private void peel(ServerLevel level, RandomSource random, double progress) {
            double reach = Math.max(frame + 2.0D, MeltdownChoreography.peel(progress) * crater);
            for (int tried = 0; tried < 10 + crater; tried++) {
                // Skewed towards the edge: the square root of a share is nearer one than the share is.
                double out = reach * Math.sqrt(Math.sqrt(random.nextDouble()));
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int x = centre.getX() + (int) Math.round(Math.cos(angle) * out);
                int z = centre.getZ() + (int) Math.round(Math.sin(angle) * out);
                BlockPos column = new BlockPos(x, centre.getY(), z);
                if (!level.isLoaded(column)) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                Vec3i offset = pos.subtract(centre);
                if (pos.equals(centre) || level.isOutsideBuildHeight(pos)
                        || !CraterPlan.within(offset.getX(), offset.getY(), offset.getZ(), crater)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !WellheadMeltdown.mayTake(level, pos, state) || permitted(level, List.of(pos)).isEmpty()) {
                    continue;
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                debris.removeIf(piece -> !piece.isAlive());
                if (debris.size() < mostDebris() && !state.hasBlockEntity() && state.getFluidState().isEmpty()) {
                    throwPiece(level, pos, state, new Vec3(0.0D, 0.35D, 0.0D), true);
                } else {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                            6, 0.3D, 0.3D, 0.3D, 0.02D);
                }
            }
        }

        private FallingBlockEntity throwPiece(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity, boolean kept) {
            if (state.hasBlockEntity() || state.getBlock() instanceof RiftCollarBlock) {
                return null;
            }
            FallingBlockEntity piece = FallingBlockEntity.fall(level, pos, state);
            piece.dropItem = false;
            piece.setHurtsEntities(0.0F, 0);
            piece.setDeltaMovement(velocity);
            piece.hurtMarked = true;
            if (kept) {
                piece.setNoGravity(true);
                debris.add(piece);
            }
            return piece;
        }

        /**
         * Everything loose is drawn in: the debris up and round into the tear on a tightening spiral, creatures
         * along the ground, and anything dropped straight into it and gone.
         */
        private void draw(ServerLevel level, double progress) {
            Vec3 point = Vec3.atCenterOf(centre);
            double strength = MeltdownChoreography.pull(progress);
            double tear = MeltdownChoreography.tear(progress, crater);
            for (Iterator<FallingBlockEntity> each = debris.iterator(); each.hasNext(); ) {
                FallingBlockEntity piece = each.next();
                if (!piece.isAlive()) {
                    each.remove();
                    continue;
                }
                Vec3 to = point.subtract(piece.position());
                double distance = to.length();
                if (distance < Math.max(1.5D, tear * 0.9D)) {
                    piece.discard();
                    each.remove();
                    continue;
                }
                // In, round and - while it is still below the tear - up: a spiral that tightens as it closes.
                Vec3 in = to.scale(1.0D / distance);
                Vec3 round = new Vec3(-in.z, 0.0D, in.x);
                double near = 1.0D - CollapseChoreography.span(distance, tear, crater);
                piece.setDeltaMovement(in.scale(0.18D + 0.45D * strength).add(round.scale(0.25D + 0.35D * near)));
                piece.hurtMarked = true;
            }
            double range = crater * PULL_REACH;
            for (Entity entity : level.getEntities((Entity) null, new AABB(centre).inflate(range),
                    entity -> !(entity instanceof Player) && !(entity instanceof FallingBlockEntity) && entity.isAlive()
                            && !entity.isPassenger())) {
                Vec3 to = point.subtract(entity.position());
                double across = Math.sqrt(to.x * to.x + to.z * to.z);
                if (entity instanceof ItemEntity && to.length() < tear + 1.0D) {
                    entity.discard();
                    continue;
                }
                double reach = 1.0D - CollapseChoreography.span(across, tear, range);
                if (reach <= 0.0D) {
                    continue;
                }
                Vec3 motion = entity.getDeltaMovement();
                double[] now = CollapseChoreography.pulled(motion.x, motion.z, to.x, to.z, 0.14D * strength * reach, 0.9D);
                entity.setDeltaMovement(now[0], motion.y + 0.05D * strength * reach * reach, now[1]);
                entity.hurtMarked = true;
            }
        }

        private void hang() {
            for (FallingBlockEntity piece : debris) {
                if (piece.isAlive()) {
                    piece.setDeltaMovement(Vec3.ZERO);
                    piece.hurtMarked = true;
                }
            }
        }

        // ---------------------------------------------------------------- detonation

        /**
         * The moment: the tear goes, with no heart, and leaves a wound; what was hanging goes with it; the ground
         * that is to be left hanging is marked; and the boom sets out for everybody.
         */
        private void detonate(ServerLevel level, boolean breaks) {
            for (FallingBlockEntity piece : debris) {
                piece.discard();
            }
            debris.clear();
            if (!dry) {
                if (level.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure) {
                    fissure.consume();
                }
                level.setBlock(centre, AWBlocks.RIFT_WOUND.get().defaultBlockState(), Block.UPDATE_ALL);
                SpatialWaveDetectorBlockEntity.announce(level, new AABB(centre).inflate(crater));
                // For whoever saw it and is still standing once the ring has had its say.
                AWCriteria.wellhead(level, centre, WellheadTrigger.MELTDOWN, Math.max(128.0D, crater * 4.0D));
            }
            if (breaks) {
                islands(level);
            }
            // Light first, sound after: the flash is the client's and is seen at once, and the boom reaches each
            // player when it would have got there.
            int range = AWConfig.WELLHEAD_SPECTACLE_RANGE.get();
            for (ServerPlayer player : level.players()) {
                double distance = Math.sqrt(player.blockPosition().distSqr(centre));
                if (distance <= range * 1.5D) {
                    booms.add(new Object[]{player.getUUID(), elapsed + (int) (distance / RING_SPEED)});
                }
            }
            ringBefore = 0.0D;
            Trace.always(Trace.WELLHEAD, "meltdown at {} detonated{}", Trace.pos(centre), dry ? " (dry)" : "");
        }

        /** Plays the boom for each player it has now reached. */
        private void booms(ServerLevel level) {
            for (Iterator<Object[]> each = booms.iterator(); each.hasNext(); ) {
                Object[] boom = each.next();
                if ((int) boom[1] > elapsed) {
                    continue;
                }
                each.remove();
                if (level.getPlayerByUUID((java.util.UUID) boom[0]) instanceof ServerPlayer player) {
                    float near = (float) (1.0D - CollapseChoreography.span(Math.sqrt(player.blockPosition().distSqr(centre)),
                            crater, AWConfig.WELLHEAD_SPECTACLE_RANGE.get() * 1.5D));
                    heard(player, SoundEvents.GENERIC_EXPLODE.value(), 0.4F + 3.6F * near, 0.45F);
                    heard(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F + 3.6F * near, 0.5F);
                    heard(player, SoundEvents.WARDEN_SONIC_BOOM, 3.0F * near, 0.6F);
                }
            }
        }

        private static void heard(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
            if (volume > 0.01F) {
                player.playNotifySound(sound, SoundSource.BLOCKS, volume, pitch);
            }
        }

        /** The ring of air, as it travels: whatever it passed this tick is thrown and hurt now, and not before. */
        private void wave(ServerLevel level, double progress) {
            double now = MeltdownChoreography.ring(progress) * crater;
            if (now > ringBefore) {
                WellheadMeltdown.blast(level, centre, ringBefore, now, crater * MeltdownChoreography.RING_REACH, tier);
                ringBefore = now;
            }
        }

        /** The blobs left hanging: whatever is solid ground in each is turned to Scarred Stone, and passed over. */
        private void islands(ServerLevel level) {
            for (int[] island : islands()) {
                int size = island[3];
                for (int dx = -size; dx <= size; dx++) {
                    for (int dy = -size; dy <= size; dy++) {
                        for (int dz = -size; dz <= size; dz++) {
                            int x = island[0] + dx;
                            int y = island[1] + dy;
                            int z = island[2] + dz;
                            BlockPos pos = centre.offset(x, y, z);
                            if (CraterPlan.inIsland(x, y, z, islands) && ground(level, pos)) {
                                level.setBlock(pos, AWBlocks.SCARRED_STONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
            }
        }

        /** Whether a block is plain, whole ground that may be changed: never a machine, a chest, a plant or a fluid. */
        private boolean ground(ServerLevel level, BlockPos pos) {
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && !state.hasBlockEntity() && state.isCollisionShapeFullBlock(level, pos)
                    && WellheadMeltdown.mayTake(level, pos, state);
        }

        /**
         * Takes the crater out to a shell, as far as this tick's budget goes: swallowed, not broken, so nothing
         * drops. The rim is thrown outward as it goes.
         *
         * @return whether the crater is now wholly taken
         */
        private boolean carve(ServerLevel level, int target, RandomSource random) {
            int[][] shells = CraterPlan.shells(crater);
            int budget = AWConfig.WELLHEAD_CARVE_BUDGET.get();
            List<BlockPos> wanted = new ArrayList<>();
            while (shell <= Math.min(target, crater) && budget > 0) {
                int[] offsets = shells[shell];
                while (shellAt < offsets.length && budget > 0) {
                    int packed = offsets[shellAt++];
                    budget--;
                    int dx = CraterPlan.x(packed);
                    int dy = CraterPlan.y(packed);
                    int dz = CraterPlan.z(packed);
                    BlockPos pos = centre.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || CraterPlan.inIsland(dx, dy, dz, islands())) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && WellheadMeltdown.mayTake(level, pos, state)) {
                        wanted.add(pos);
                    }
                }
                if (shellAt >= offsets.length) {
                    shell++;
                    shellAt = 0;
                }
            }
            boolean rim = shell >= crater - 1;
            for (BlockPos pos : permitted(level, wanted)) {
                if (rim && thrown < mostDebris() && pos.getY() >= centre.getY() - 2 && random.nextInt(10) == 0) {
                    // Ejecta: the lip of the bowl goes outward and up, and comes down where it comes down.
                    BlockState state = level.getBlockState(pos);
                    Vec3 out = Vec3.atCenterOf(pos).subtract(Vec3.atCenterOf(centre)).multiply(1.0D, 0.0D, 1.0D);
                    if (out.lengthSqr() > 1.0D && !state.hasBlockEntity() && state.getFluidState().isEmpty()) {
                        thrown++;
                        throwPiece(level, pos, state, out.normalize().scale(0.6D + random.nextDouble() * 0.7D)
                                .add(0.0D, 0.7D + random.nextDouble() * 0.6D, 0.0D), false);
                        continue;
                    }
                }
                // The neighbours are told once the hole is whole - see settle.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            return shell > crater;
        }

        // ---------------------------------------------------------------- fallout

        /** Lines the bowl: what is solid ground just outside the crater becomes Scarred Stone. */
        private boolean line(ServerLevel level) {
            if (lining == null) {
                lining = CraterPlan.lining(crater);
            }
            int budget = AWConfig.WELLHEAD_CARVE_BUDGET.get();
            while (lined < lining.length && budget-- > 0) {
                int packed = lining[lined++];
                BlockPos pos = centre.offset(CraterPlan.x(packed), CraterPlan.y(packed), CraterPlan.z(packed));
                if (ground(level, pos) && !permitted(level, List.of(pos)).isEmpty()) {
                    level.setBlock(pos, AWBlocks.SCARRED_STONE.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            return lined >= lining.length;
        }

        /** Tells the crater's wall that the ground beside it has gone, so water runs in and sand falls. */
        private boolean settle(ServerLevel level) {
            int[] wall = CraterPlan.shells(crater)[crater];
            int budget = AWConfig.WELLHEAD_CARVE_BUDGET.get() / 2;
            while (settled < wall.length && budget-- > 0) {
                int packed = wall[settled++];
                BlockPos pos = centre.offset(CraterPlan.x(packed), CraterPlan.y(packed), CraterPlan.z(packed));
                if (level.isLoaded(pos) && !level.isOutsideBuildHeight(pos)) {
                    level.blockUpdated(pos, Blocks.AIR);
                }
            }
            return settled >= wall.length;
        }

        /** Cracks of Scarred Stone running outward from the rim, along whatever the surface is. */
        private void veins(ServerLevel level, RandomSource random) {
            for (int vein = 0; vein < 6 + crater / 3; vein++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double x = centre.getX() + 0.5D + Math.cos(angle) * (crater + 1.0D);
                double z = centre.getZ() + 0.5D + Math.sin(angle) * (crater + 1.0D);
                for (int step = 0; step < crater / 2 + random.nextInt(Math.max(1, crater / 3)); step++) {
                    angle += (random.nextDouble() - 0.5D) * 0.7D;
                    x += Math.cos(angle);
                    z += Math.sin(angle);
                    BlockPos column = BlockPos.containing(x, centre.getY(), z);
                    if (!level.isLoaded(column)) {
                        break;
                    }
                    BlockPos pos = new BlockPos(column.getX(),
                            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1, column.getZ());
                    if (ground(level, pos) && !permitted(level, List.of(pos)).isEmpty()) {
                        level.setBlock(pos, AWBlocks.SCARRED_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }

        /** Scarred Stone coming down inside the bowl, and the leaves round its rim stripped off. Nothing on the grass. */
        private void fallout(ServerLevel level, RandomSource random) {
            if (elapsed % 3 == 0 && rained < crater * 2) {
                rained++;
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double out = crater * 0.55D * Math.sqrt(random.nextDouble());
                BlockPos from = BlockPos.containing(centre.getX() + Math.cos(angle) * out,
                        centre.getY() + crater * 0.5D + random.nextInt(8), centre.getZ() + Math.sin(angle) * out);
                if (!level.isOutsideBuildHeight(from) && level.isLoaded(from) && level.getBlockState(from).isAir()) {
                    FallingBlockEntity piece = FallingBlockEntity.fall(level, from, AWBlocks.SCARRED_STONE.get().defaultBlockState());
                    piece.dropItem = false;
                }
            }
            for (int tried = 0; tried < crater * 2; tried++) {
                double distance = crater * (1.0D + (OUTER_REACH - 1.0D) * random.nextDouble());
                Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D + 0.3D, random.nextGaussian());
                if (way.lengthSqr() < 1.0e-6D) {
                    continue;
                }
                way = way.normalize().scale(distance);
                BlockPos pos = BlockPos.containing(centre.getX() + 0.5D + way.x, centre.getY() + 0.5D + way.y,
                        centre.getZ() + 0.5D + way.z);
                if (!level.isOutsideBuildHeight(pos) && level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LEAVES)
                        && !permitted(level, List.of(pos)).isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        /**
         * The blocks of a list that anything listening for explosions lets go. A land-protection mod has no other
         * way to hear of this, and an explosion is the nearest thing it knows to what a meltdown is.
         */
        private List<BlockPos> permitted(ServerLevel level, List<BlockPos> wanted) {
            return WellheadMeltdown.permitted(level, centre, crater, wanted);
        }
    }
}
