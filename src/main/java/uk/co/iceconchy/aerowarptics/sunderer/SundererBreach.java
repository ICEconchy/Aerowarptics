package uk.co.iceconchy.aerowarptics.sunderer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.advancement.SundererTrigger;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateFrameBlock;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWDamageTypes;
import uk.co.iceconchy.aerowarptics.rifting.RiftHop;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.wellhead.CraterPlan;
import uk.co.iceconchy.aerowarptics.wellhead.ScarZones;
import uk.co.iceconchy.aerowarptics.wellhead.WellheadMeltdown;

import java.util.ArrayList;
import java.util.List;

/**
 * What a sundering does to the world: the tear it leaves, the block a hand takes, and the ground a rupture
 * takes. Everything else about a sundering is drawn.
 *
 * <p><strong>This is the one place a fissure is made.</strong> A fissure is otherwise found - the world leaves
 * them at rift scars - and the Sunderer is the single exception the owner allowed (2026-10-16): a tear made here
 * is <em>conjured</em> ({@link RiftFissureBlockEntity#conjure}), which seals with no heart and gutters unless a
 * wellhead holds it. {@code SundererWiringTest} fails if any other file sets one.
 *
 * <p>Every block taken is offered to land protection first ({@link WellheadMeltdown#permitted}), as the
 * meltdown's are, and nothing here touches plot space or reads an unloaded position.
 */
public final class SundererBreach {

    /** How far, in blocks, a rupture reaches the living. */
    public static final double RUPTURE_REACH = 10.0D;
    /** The harm a rupture does at its middle, in half-hearts. */
    public static final double RUPTURE_HARM = 14.0D;
    /** Ticks a newborn tear is drawn for everyone, before it is a fissure like any other. */
    public static final int REVEALED_FOR = 200;
    /** How far, in blocks, those near are told of a tear for its advancement. */
    public static final double WITNESS = 32.0D;

    private SundererBreach() {
    }

    /**
     * The ring comes down and the fissure is set: the Sunderer goes, and a conjured tear hangs where the opening was.
     * The burst third of the frame is gone already - it left the world as the Sunderer fired, and was swallowed.
     *
     * @return whether a fissure was set. A cell somebody filled in since the edge has nowhere for one to hang.
     */
    public static boolean tear(ServerLevel level, BlockPos sunderer, BlockPos centre) {
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, sunderer, Block.getId(level.getBlockState(sunderer)));
        level.removeBlock(sunderer, false);

        BlockState there = level.getBlockState(centre);
        if (!there.isAir() && !there.canBeReplaced()) {
            Trace.always(Trace.RIFTING, "sunderer at {} tore, but its centre {} was filled in: no fissure",
                    Trace.pos(sunderer), Trace.pos(centre));
            return false;
        }
        level.setBlock(centre, AWBlocks.RIFT_FISSURE.get().defaultBlockState(), Block.UPDATE_ALL);
        // In the same tick as the block: a fissure that reaches its first tick with no reservoir rolls one.
        if (level.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure) {
            fissure.conjure(AWConfig.SUNDERER_FISSURE_ESSENCE.get(), AWConfig.SUNDERER_UNSETTLED_MINUTES.get() * 1_200L);
            // Born where everybody can see it, for a while: the payoff of the scene is not for goggles alone.
            fissure.reveal(REVEALED_FOR);
        }
        level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.5F);
        AWCriteria.sunderer(level, centre, SundererTrigger.TORN, WITNESS);
        Trace.always(Trace.RIFTING, "sunderer at {} tore a fissure at {}", Trace.pos(sunderer), Trace.pos(centre));
        return true;
    }

    /** A hand goes back the way it came, and the block of frame it closed on goes with it. */
    public static void take(ServerLevel level, BlockPos grip, BlockPos centre) {
        if (!level.isLoaded(grip) || Holds.inPlot(level, grip)
                || !(level.getBlockState(grip).getBlock() instanceof RiftGateFrameBlock)) {
            return;
        }
        if (WellheadMeltdown.permitted(level, centre, 1.0F, List.of(grip)).isEmpty()) {
            return;
        }
        level.setBlock(grip, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Trace.always(Trace.RIFTING, "something reached through the sundering at {} and took {}", Trace.pos(centre),
                Trace.pos(grip));
    }

    /**
     * A committed sundering failed: the membrane blows out. The Sunderer and its whole ring go, a small crater is
     * taken and lined with Scarred Stone, the ground is left unsettled, and what stands near is hurt and thrown.
     */
    public static void rupture(ServerLevel level, BlockPos sunderer, BlockPos centre, List<BlockPos> frame) {
        int crater = AWConfig.SUNDERER_RUPTURE_CRATER.get();
        List<BlockPos> ring = new ArrayList<>(frame);
        ring.add(sunderer);
        burst(level, centre, ring);

        if (AWConfig.SUNDERER_RUPTURE_BREAKS_BLOCKS.get() && !Holds.inPlot(level, centre)) {
            carve(level, centre, crater);
        }
        ScarZones.leave(level, centre, crater + 2, AWConfig.SUNDERER_RUPTURE_ZONE_MINUTES.get() * 1_200L, "sundering");
        throwBack(level, centre);
        level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.4F);
        level.playSound(null, centre, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 2.0F, 0.5F);
        AWCriteria.sunderer(level, centre, SundererTrigger.RUPTURED, WITNESS);
        Trace.always(Trace.RIFTING, "sunderer at {} ruptured: crater {} at {}", Trace.pos(sunderer), crater,
                Trace.pos(centre));
    }

    /** Takes blocks that are still there, with their breaking shown and nothing dropped. */
    private static void burst(ServerLevel level, BlockPos centre, List<BlockPos> blocks) {
        List<BlockPos> wanted = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (level.isLoaded(pos) && !Holds.inPlot(level, pos) && !level.getBlockState(pos).isAir()) {
                wanted.add(pos);
            }
        }
        for (BlockPos pos : WellheadMeltdown.permitted(level, centre, 4.0F, wanted)) {
            BlockState state = level.getBlockState(pos);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The crater: a few hundred blocks, small enough to take in the tick it happens, told to their neighbours once
     * the hole is whole - the meltdown's rule, kept even at this size.
     */
    private static void carve(ServerLevel level, BlockPos centre, int crater) {
        List<BlockPos> wanted = new ArrayList<>();
        for (int[] shell : CraterPlan.shells(crater)) {
            for (int packed : shell) {
                BlockPos pos = centre.offset(CraterPlan.x(packed), CraterPlan.y(packed), CraterPlan.z(packed));
                if (!level.isLoaded(pos) || Holds.inPlot(level, pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && WellheadMeltdown.mayTake(level, pos, state) && level.getBlockEntity(pos) == null) {
                    wanted.add(pos);
                }
            }
        }
        for (BlockPos pos : WellheadMeltdown.permitted(level, centre, crater, wanted)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        List<BlockPos> wall = new ArrayList<>();
        for (int packed : CraterPlan.lining(crater)) {
            BlockPos pos = centre.offset(CraterPlan.x(packed), CraterPlan.y(packed), CraterPlan.z(packed));
            if (!level.isLoaded(pos) || Holds.inPlot(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isSolidRender(level, pos) && WellheadMeltdown.mayTake(level, pos, state)
                    && level.getBlockEntity(pos) == null) {
                wall.add(pos);
            }
        }
        for (BlockPos pos : WellheadMeltdown.permitted(level, centre, crater + 2.0F, wall)) {
            level.setBlock(pos, AWBlocks.SCARRED_STONE.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (BlockPos pos : wall) {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }

    /** Hurts and throws what stands near, harder nearer the middle, and hops what it can. */
    private static void throwBack(ServerLevel level, BlockPos centre) {
        Vec3 middle = Vec3.atCenterOf(centre);
        AABB reach = new AABB(middle, middle).inflate(RUPTURE_REACH);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, reach,
                entity -> entity.isAlive() && !(entity instanceof ArmorStand))) {
            double distance = living.position().distanceTo(middle);
            if (distance >= RUPTURE_REACH) {
                continue;
            }
            double near = 1.0D - distance / RUPTURE_REACH;
            living.hurt(AWDamageTypes.sundering(level, middle), (float) (RUPTURE_HARM * near * near));
            Vec3 away = living.position().subtract(middle);
            Vec3 push = away.lengthSqr() < 1.0e-6D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize();
            living.push(push.x * 1.4D * near, 0.4D + 0.4D * near, push.z * 1.4D * near);
            living.hurtMarked = true;
            if (near > 0.5D && RiftHop.mayHop(living)) {
                RiftHop.hop(level, living, RiftHop.CHORUS_RANGE);
            }
        }
    }

    /**
     * What a straining sundering does to what stands near it, once a tick: draws the living towards the opening,
     * swallows loose items that reach it, and hurts and hops anything standing in it once a second.
     *
     * @param pull how hard it draws, 0 to 1, from {@link SundererChoreography#pull}
     */
    public static void strain(ServerLevel level, SundererRing.Ring ring, double pull, long tick) {
        if (pull <= 0.0D) {
            return;
        }
        Vec3 middle = ring.shape().centre();
        double reach = 6.0D + Math.max(ring.shape().halfWidth(), ring.shape().halfHeight());
        AABB around = new AABB(middle, middle).inflate(reach);
        for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null, around,
                entity -> entity.isAlive() && !(entity instanceof ArmorStand) && !entity.isSpectator())) {
            Vec3 to = middle.subtract(entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D));
            double distance = to.length();
            if (distance > reach || distance < 1.0e-3D) {
                continue;
            }
            boolean inOpening = ring.shape().contains(entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D))
                    && Math.abs(ring.shape().distanceToPlane(entity.position())) < 0.8D;
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
                if (inOpening || distance < 1.0D) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                            item.getX(), item.getY(), item.getZ(), 6, 0.1D, 0.1D, 0.1D, 0.02D);
                    item.discard();
                    continue;
                }
            }
            double strength = 0.04D * pull * (1.0D - distance / reach);
            entity.push(to.x / distance * strength, to.y / distance * strength * 0.5D, to.z / distance * strength);
            entity.hurtMarked = true;
            if (inOpening && tick % 20L == 0L && entity instanceof LivingEntity living) {
                living.hurt(AWDamageTypes.sundering(level, middle), 2.0F);
                if (RiftHop.mayHop(living)) {
                    RiftHop.hop(level, living, 4);
                }
            }
        }
    }
}
