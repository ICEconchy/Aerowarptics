package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires when a Rift Beacon successfully calls its bound airship down.
 *
 * <p>Raised at the moment the summon is accepted rather than when the hull arrives, and the
 * difference is worth stating. By then the drive has already agreed to everything it agrees to for
 * any warp - it is turning fast enough, it is charged, the destination is in range and affordable,
 * and the arrival search has proved a clear volume for the hull. What is left is the flight, which the
 * crew's own {@link WarpCompletedTrigger} covers.
 *
 * <p>So this marks the act the item exists for: commanding a ship you are not standing on. Nothing
 * else in the mod can do that, which is why it earns a trigger rather than being folded into the warp
 * one - a summoner is by definition not aboard, and would never appear in that trigger's crew.
 */
public class ShipSummonedTrigger extends SimpleCriterionTrigger<ShipSummonedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
        ).apply(instance, TriggerInstance::new));
    }
}
