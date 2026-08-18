package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Sound events for every stage of the warp sequence.
 *
 * <p>All of them are positional (played through {@code level.playSound} at the drive's position), so
 * the sequence is audible to everyone nearby rather than only the pilot. The definitions in
 * {@code sounds.json} are built out of existing game audio rather than shipping new files.
 */
public final class AWSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AeroWarptics.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_CHARGING = register("drive_charging");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_CHARGED = register("drive_charged");
    public static final DeferredHolder<SoundEvent, SoundEvent> STABILIZING = register("stabilizing");
    public static final DeferredHolder<SoundEvent, SoundEvent> DESTINATION_LOCK = register("destination_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = register("rift_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_TRAVEL = register("warp_travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_EXIT = register("warp_exit");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_FAILED = register("warp_failed");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_COOLDOWN = register("drive_cooldown");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_CONFIGURED = register("anchor_configured");

    private AWSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AeroWarptics.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
