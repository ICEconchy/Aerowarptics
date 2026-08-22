package uk.co.iceconchy.aerowarptics.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.Optional;

/**
 * Fires for everyone aboard when an airship finishes a warp.
 *
 * <p>Aboard, rather than whoever pressed the button: a warp is something a crew goes through
 * together, and a passenger who has just been folded across four thousand blocks has as much claim
 * to the advancement as the person at the console.
 *
 * <p>The trigger reports the distance actually crossed and the tier of drive that did it, so an
 * advancement can ask for a long haul, or for one done on a particular drive, without any of that
 * being decided here.
 */
public class WarpCompletedTrigger extends SimpleCriterionTrigger<WarpCompletedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, double distance, RiftDriveTier tier, boolean toFix) {
        trigger(player, instance -> instance.matches(distance, tier, toFix));
    }

    /**
     * @param distance blocks between where the hull started and where it arrived
     * @param tier     serialized name of a drive tier, when the advancement cares which made the jump
     * @param fix      when set, demands the destination was (or was not) a Rift Probe's fix rather
     *                 than an anchor - which is the difference between going somewhere and being the
     *                 first to go there
     */
    public record TriggerInstance(Optional<ContextAwarePredicate> player,
                                  MinMaxBounds.Doubles distance,
                                  Optional<String> tier,
                                  Optional<Boolean> fix) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance", MinMaxBounds.Doubles.ANY)
                        .forGetter(TriggerInstance::distance),
                Codec.STRING.optionalFieldOf("tier").forGetter(TriggerInstance::tier),
                Codec.BOOL.optionalFieldOf("fix").forGetter(TriggerInstance::fix)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(double travelled, RiftDriveTier drive, boolean toFix) {
            if (!distance.matches(travelled)) {
                return false;
            }
            if (tier.isPresent() && !tier.get().equals(drive.getSerializedName())) {
                return false;
            }
            return fix.isEmpty() || fix.get() == toFix;
        }
    }
}
