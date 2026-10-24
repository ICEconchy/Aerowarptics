package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlock;
import uk.co.iceconchy.aerowarptics.registry.AWDamageTypes;

/**
 * What a Rift Wellhead's meltdown may take and whom it hurts - the rules {@link Meltdowns} plays out.
 *
 * <p>A meltdown leaves no Scar Heart. A heart is what a wound leaves when it closes.
 */
public final class WellheadMeltdown {

    /** Blocks a meltdown leaves standing, for a pack to add to. */
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK, AeroWarptics.id("meltdown_immune"));

    private WellheadMeltdown() {
    }

    /** A well has gone over: the scene begins, and nothing stops it. */
    public static void begin(ServerLevel level, BlockPos centre, WellheadTier tier, int frameRadius) {
        Meltdowns.begin(level, centre, tier, frameRadius, false);
    }

    /**
     * Whether a meltdown may take a block. Never one that nothing can break - which is what keeps the wound it
     * leaves, and another tear caught in the crater - and never one a pack has marked as beyond it.
     */
    public static boolean mayTake(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) >= 0.0F
                && !(state.getBlock() instanceof RiftFissureBlock)
                && !state.is(IMMUNE);
    }

    /**
     * Of the blocks something wants to take, the ones a land-protection mod lets it: they are offered to
     * {@code ExplosionEvent.Detonate} as an explosion's, which is all the hearing such a mod gets. The meltdown's
     * crater asks this, and so does anything else in the mod that takes ground.
     */
    public static java.util.List<BlockPos> permitted(ServerLevel level, BlockPos centre, float radius,
                                                     java.util.List<BlockPos> wanted) {
        if (wanted.isEmpty()) {
            return wanted;
        }
        net.minecraft.world.level.Explosion explosion = new net.minecraft.world.level.Explosion(level, null,
                centre.getX() + 0.5D, centre.getY() + 0.5D, centre.getZ() + 0.5D, radius, false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().addAll(wanted);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ExplosionEvent.Detonate(level, explosion, new java.util.ArrayList<>()));
        return java.util.List.copyOf(explosion.getToBlow());
    }

    /**
     * The ring of air passing: throws back and hurts whatever living stands in the band it crossed this tick,
     * harder the nearer the middle that band is. Never an armour stand, and never a block or an item - the
     * ground is the crater's affair. A band rather than a ball, so each creature is reached when the ring
     * reaches it and once only.
     *
     * @param from  how far the ring had run before this tick
     * @param to    how far it has run now
     * @param range how far it runs in all, which is where its force comes to nothing
     * @return how many were hurt
     */
    static int blast(ServerLevel level, BlockPos centre, double from, double to, double range, WellheadTier tier) {
        Vec3 point = Vec3.atCenterOf(centre);
        double most = AWConfig.WELLHEAD_HARM.get() * 12.0D * (tier.ordinal() + 1);
        int hurt = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre).inflate(to + 2.0D))) {
            if (living instanceof ArmorStand || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            Vec3 away = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).subtract(point);
            double distance = away.length();
            if (distance < from || distance >= to || distance > range) {
                continue;
            }
            double near = 1.0D - distance / range;
            Vec3 push = (distance < 1.0e-3D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize()).scale(3.5D * near);
            living.push(push.x, 0.5D + push.y * 0.5D, push.z);
            living.hurtMarked = true;
            if (living.hurt(AWDamageTypes.meltdown(level, point), (float) (most * near * near))) {
                hurt++;
            }
        }
        return hurt;
    }
}
