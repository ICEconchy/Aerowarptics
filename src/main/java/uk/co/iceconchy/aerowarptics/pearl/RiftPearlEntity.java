package uk.co.iceconchy.aerowarptics.pearl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.registry.AWEntities;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.rifting.RiftHop;

/**
 * A Rift Pearl in flight. Whatever living thing it hits is hopped somewhere near, as if it had eaten a chorus
 * fruit; wherever it lands, it bursts in a small rift cue - a spray of rift sparks and the sound of an aperture
 * opening.
 *
 * <p>It moves what it <em>hits</em>, never the one who threw it: an ender pearl that carried its thrower
 * would have to fire the ender pearl's own teleport event, which a claim mod listens for by that type, or
 * skip it and walk through every protected border on a server. The hop goes through the chorus fruit's event
 * instead ({@link RiftHop}), so whatever stops a chorus fruit stops this, and it never moves a boss, anything
 * a ship or a contraption is carrying, or anything a pack has tagged as not to be teleported. The hit itself
 * does no harm, as an ender pearl's does none - but the creature knows who threw it.
 */
public class RiftPearlEntity extends ThrowableItemProjectile {

    public RiftPearlEntity(EntityType<? extends RiftPearlEntity> type, Level level) {
        super(type, level);
    }

    public RiftPearlEntity(Level level, LivingEntity thrower) {
        super(AWEntities.RIFT_PEARL.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return AWItems.RIFT_PEARL.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity hit = result.getEntity();
        hit.hurt(damageSources().thrown(this, getOwner()), 0.0F);
        if (level() instanceof ServerLevel server && hit instanceof LivingEntity living
                && AWConfig.RIFT_PEARL_HOPS.get() && RiftHop.mayHop(living)) {
            RiftHop.hop(server, living, RiftHop.CHORUS_RANGE);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server && !isRemoved()) {
            server.sendParticles(AWParticles.RIFT_SPARK.get(), getX(), getY(), getZ(), 36, 0.35D, 0.35D, 0.35D, 0.08D);
            server.playSound(null, getX(), getY(), getZ(), AWSounds.RIFT_OPEN.get(), SoundSource.NEUTRAL, 0.7F, 1.6F);
            discard();
        }
    }
}
