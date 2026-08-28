package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.beacon.RiftBeaconBinding;

/**
 * Data this mod stores on item stacks.
 *
 * <p>A registered component rather than a raw NBT tag, so the binding survives the round trips a
 * stack actually makes - saved to disk, sent to the client for the tooltip, and compared when two
 * stacks are asked whether they merge. Two beacons bound to different ships are different items and
 * must not silently stack into one.
 */
public final class AWDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AeroWarptics.MODID);

    /** The ship a Rift Beacon calls, or absent on one nobody has bound yet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RiftBeaconBinding>> BEACON_BINDING =
            COMPONENTS.register("beacon_binding", () -> DataComponentType.<RiftBeaconBinding>builder()
                    .persistent(RiftBeaconBinding.CODEC)
                    .networkSynchronized(RiftBeaconBinding.STREAM_CODEC)
                    .build());

    private AWDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
