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
 * Fires when a Rift Fissure is emptied and seals over.
 *
 * <p>Awarded to whoever is standing near it as it closes rather than to whoever placed the siphon,
 * because nothing in the mod records who placed a siphon and inventing an owner for one would be a
 * new concept introduced for the sake of an advancement. Being there is also the better story: the
 * tear shrinks as it drains and then seals, and that is worth coming back for.
 *
 * <p>{@code essence} is what the fissure held to begin with, so a later advancement can ask for a big
 * one without this having to decide what big means.
 */
public class FissureClosedTrigger extends SimpleCriterionTrigger<FissureClosedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int essence) {
        trigger(player, instance -> instance.matches(essence));
    }

    /**
     * @param essence millibuckets the fissure held when it was found, not what this player collected
     */
    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  MinMaxBounds.Ints essence) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("essence", MinMaxBounds.Ints.ANY)
                        .forGetter(TriggerInstance::essence)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(int held) {
            return essence.matches(held);
        }
    }
}
