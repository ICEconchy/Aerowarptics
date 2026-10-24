package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Something happened at a Rift Wellhead with a player standing by: its frame formed, it was scrammed at the
 * brink, or it melted down.
 *
 * <p>One trigger for the three, told apart by {@code event}, because none of them is done by anybody in
 * particular - a frame forms a second after the last block goes in, a scram is as likely a comparator's as a
 * hand's, and a meltdown is the well's own - so each is awarded to whoever was near enough to have seen it.
 */
public class WellheadTrigger extends SimpleCriterionTrigger<WellheadTrigger.TriggerInstance> {

    public static final String FORMED = "formed";
    public static final String SCRAMMED = "scrammed";
    public static final String MELTDOWN = "meltdown";

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
