package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires when a player comes out of the far side of a Rift Gate.
 *
 * <p>{@code vehicle} separates the two ways through, because they are different achievements. Walking
 * in needs a dialled gate and nothing else; taking a vehicle through needs one wide enough at
 * <em>both</em> ends, which is the part players get wrong. An advancement that left the two together
 * would be handed out for the easy one.
 */
public class GateTravelTrigger extends SimpleCriterionTrigger<GateTravelTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /**
     * @param aboardVehicle whether the player crossed as cargo of a vehicle rather than on foot
     */
    public void trigger(ServerPlayer player, boolean aboardVehicle) {
        trigger(player, instance -> instance.matches(aboardVehicle));
    }

    /**
     * @param vehicle when present, demands that the crossing was (or was not) made in a vehicle
     */
    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  Optional<Boolean> vehicle) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.BOOL.optionalFieldOf("vehicle").forGetter(TriggerInstance::vehicle)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(boolean aboardVehicle) {
            return vehicle.isEmpty() || vehicle.get() == aboardVehicle;
        }
    }
}
