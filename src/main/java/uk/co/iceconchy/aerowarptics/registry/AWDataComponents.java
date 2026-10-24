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

    /**
     * The ship a Ship's Compass was folded aboard. The beacon's record, for the beacon's reason: a ship is
     * found by its drive's plot position, which a warp does not change - see {@link RiftBeaconBinding}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RiftBeaconBinding>> SHIP_COMPASS =
            COMPONENTS.register("ship_compass", () -> DataComponentType.<RiftBeaconBinding>builder()
                    .persistent(RiftBeaconBinding.CODEC)
                    .networkSynchronized(RiftBeaconBinding.STREAM_CODEC)
                    .build());

    /**
     * Where a Ship's Compass last knew its ship to be. Worked out on the server, which knows where ships are,
     * and read by the needle on the client, which does not.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.GlobalPos>> SHIP_COMPASS_TARGET =
            COMPONENTS.register("ship_compass_target", () -> DataComponentType.<net.minecraft.core.GlobalPos>builder()
                    .persistent(net.minecraft.core.GlobalPos.CODEC)
                    .networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC)
                    .build());

    /**
     * The bond a stack of Rift Bonds was last tuned to, or absent on one that will start a bond of its own
     * when placed.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> BOND =
            COMPONENTS.register("bond", () -> DataComponentType.<java.util.UUID>builder()
                    .persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                    .build());

    /** Ticks an Unset Stormglass has spent out in a storm so far. Kept on the stack, so a glass set in stages still sets. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORMGLASS_PROGRESS =
            COMPONENTS.register("stormglass_progress", () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());

    private AWDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
