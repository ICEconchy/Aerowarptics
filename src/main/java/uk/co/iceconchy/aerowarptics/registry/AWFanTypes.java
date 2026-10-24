package uk.co.iceconchy.aerowarptics.registry;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.rifting.RiftingType;

/**
 * Fan processing types this mod adds to Create's registry.
 *
 * <p>Create sorts its types by priority when that registry is baked, after every mod has registered into
 * it, so a type added here takes its place beside washing and haunting without asking Create for anything.
 */
public final class AWFanTypes {

    public static final DeferredRegister<FanProcessingType> TYPES =
            DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, AeroWarptics.MODID);

    public static final DeferredHolder<FanProcessingType, RiftingType> RIFTING =
            TYPES.register("rifting", RiftingType::new);

    private AWFanTypes() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
