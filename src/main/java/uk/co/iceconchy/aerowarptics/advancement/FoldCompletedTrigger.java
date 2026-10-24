package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires for everyone near a Collapse Cradle when it finishes a fold.
 *
 * <p>Everyone near rather than whoever pressed Start, for the fissure's reason: a redstone pulse records
 * nobody, and being there is the part worth marking. {@code count} is how many crafts the run folded, so a
 * pack can ask for a whole stack without this deciding what a whole stack is.
 */
public class FoldCompletedTrigger extends SimpleCriterionTrigger<FoldCompletedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int count) {
        trigger(player, instance -> instance.matches(count));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  MinMaxBounds.Ints count) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY)
                        .forGetter(TriggerInstance::count)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(int folded) {
            return count.matches(folded);
        }
    }
}
