package uk.co.iceconchy.aerowarptics.rifting;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/**
 * A chorus fruit's hop, for whatever the rift moves: a creature standing in a rifting fan's stream, and one a
 * thrown Rift Pearl hits.
 *
 * <p>Through the chorus fruit's own teleport event, so a protection mod that stops a chorus fruit stops these
 * too, and landing where a chorus fruit would: somewhere safe within range, tried a few times, or nowhere.
 */
public final class RiftHop {

    /** How far a chorus fruit carries whoever eats it, in blocks each way - and so how far a Rift Pearl does. */
    public static final int CHORUS_RANGE = 8;

    private static final int ATTEMPTS = 16;

    private RiftHop() {
    }

    /**
     * Whether the rift may move this creature at all. Never one a ship or a contraption is carrying - a hop
     * out of a deck's frame drops somebody off the side of a hull in flight, and one riding a contraption
     * would be pulled out of the seat it is bolted into - never a boss, and never anything a pack has said
     * cannot be teleported.
     */
    public static boolean mayHop(LivingEntity living) {
        if (living.isSpectator() || (living instanceof Player player && player.isCreative() && player.getAbilities().flying)) {
            return false;
        }
        if (living.getType().is(Tags.EntityTypes.BOSSES) || living.getType().is(Tags.EntityTypes.TELEPORTING_NOT_SUPPORTED)) {
            return false;
        }
        if (living.getVehicle() instanceof AbstractContraptionEntity) {
            return false;
        }
        return Sable.HELPER.getTrackingOrVehicleSubLevel(living) == null;
    }

    /** Hops a creature somewhere within {@code range}, as a chorus fruit would. Whether it went anywhere. */
    public static boolean hop(ServerLevel level, LivingEntity living, int range) {
        RandomSource random = living.getRandom();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double x = living.getX() + (random.nextDouble() - 0.5D) * range * 2.0D;
            double y = Mth.clamp(living.getY() + (random.nextInt(range * 2) - range),
                    level.getMinBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight() - 1);
            double z = living.getZ() + (random.nextDouble() - 0.5D) * range * 2.0D;
            if (living.isPassenger()) {
                living.stopRiding();
            }
            Vec3 from = living.position();
            EntityTeleportEvent.ChorusFruit event = EventHooks.onChorusFruitTeleport(living, x, y, z);
            if (event.isCanceled()) {
                return false;
            }
            if (living.randomTeleport(event.getTargetX(), event.getTargetY(), event.getTargetZ(), true)) {
                level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(living));
                level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                        SoundSource.NEUTRAL, 0.8F, 1.3F);
                level.sendParticles(AWParticles.RIFT_SPARK.get(), from.x, from.y + 1.0D, from.z, 12, 0.3D, 0.6D, 0.3D, 0.02D);
                living.resetFallDistance();
                return true;
            }
        }
        return false;
    }
}
