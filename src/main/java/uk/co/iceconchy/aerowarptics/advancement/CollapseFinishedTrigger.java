package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires for everyone who watched a Collapse Cradle's ritual end, with how it ended: {@code settled} (the
 * core on the cradle), {@code flung} (the core, thrown clear) or {@code spat} (nothing made; the core and
 * offerings thrown back out whole).
 */
public class CollapseFinishedTrigger extends SimpleCriterionTrigger<CollapseFinishedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String outcome) {
        trigger(player, instance -> instance.matches(outcome));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  Optional<String> outcome) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("outcome").forGetter(TriggerInstance::outcome)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(String ended) {
            return outcome.map(ended::equals).orElse(true);
        }
    }
}
