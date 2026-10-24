package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Something happened at a Rift Sunderer with a player standing by: it tore a fissure, it ruptured, something
 * reached through it, or the tear it left was harnessed by a wellhead.
 *
 * <p>One trigger for all four, told apart by {@code event}, for the wellhead's reason: none of them is done by
 * anybody in particular, so each is awarded to whoever was near enough to have seen it.
 */
public class SundererTrigger extends SimpleCriterionTrigger<SundererTrigger.TriggerInstance> {

    public static final String TORN = "torn";
    public static final String RUPTURED = "ruptured";
    public static final String WAVED = "waved";
    public static final String HELD = "held";

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, instance -> instance.event().map(event::equals).orElse(true));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> event)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("event").forGetter(TriggerInstance::event)
        ).apply(instance, TriggerInstance::new));
    }
}
