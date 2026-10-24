package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.pearl.RiftPearlEntity;

/** Entity types. Only the thrown Rift Pearl, which is a pearl in flight and nothing more. */
public final class AWEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AeroWarptics.MODID);

    /** Sized and tracked like vanilla's thrown ender pearl, so it flies and draws the same way. */
    public static final DeferredHolder<EntityType<?>, EntityType<RiftPearlEntity>> RIFT_PEARL =
            ENTITIES.register("rift_pearl", () -> EntityType.Builder.<RiftPearlEntity>of(RiftPearlEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(AeroWarptics.id("rift_pearl").toString()));

    private AWEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
