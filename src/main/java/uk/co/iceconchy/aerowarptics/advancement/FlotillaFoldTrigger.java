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
 * Fires for everyone aboard every ship of a flotilla fold, once the last of them is out.
 *
 * <p>Aboard, for the warp trigger's reason - a fold is something every crew in the fleet goes through
 * together, and the escorts' crews have as much claim to it as the flagship's. Fired once the fold is
 * finished rather than as each ship arrives, because two of the three things it reports are only known
 * then: whether anybody was shaken loose, and whether anybody was lost.
 *
 * <p>What makes a fold remarkable is left to the advancements: how many ships went, whether the flagship
 * led as many as its drive can, and whether every one of them came out where it was meant to with nobody
 * left behind.
 */
public class FlotillaFoldTrigger extends SimpleCriterionTrigger<FlotillaFoldTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /**
     * @param ships every ship that set off, the flagship among them
     * @param full  whether the flagship led as many escorts as its drive allows, and all of them came out
     * @param clean whether nobody was shaken loose, left behind, or lost
     */
    public void trigger(ServerPlayer player, int ships, boolean full, boolean clean) {
        trigger(player, instance -> instance.matches(ships, full, clean));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  MinMaxBounds.Ints ships,
                                  Optional<Boolean> full,
                                  Optional<Boolean> clean) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("ships", MinMaxBounds.Ints.ANY)
                        .forGetter(TriggerInstance::ships),
                Codec.BOOL.optionalFieldOf("full").forGetter(TriggerInstance::full),
                Codec.BOOL.optionalFieldOf("clean").forGetter(TriggerInstance::clean)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(int count, boolean wasFull, boolean wasClean) {
            return ships.matches(count)
                    && (full.isEmpty() || full.get() == wasFull)
                    && (clean.isEmpty() || clean.get() == wasClean);
        }
    }
}
