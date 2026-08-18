package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Custom particle types for the rift.
 *
 * <p>Only the two effects that carry the look of a warp get bespoke types; the supporting sparks,
 * smoke and glow reuse vanilla particles so the effect stays cheap.
 */
public final class AWParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AeroWarptics.MODID);

    /** Short-lived arc thrown off the drive while it charges and while a rift is forming. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_SPARK =
            PARTICLES.register("rift_spark", () -> new SimpleParticleType(false));

    /** Elongated streak drawn along the warp corridor. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WARP_STREAK =
            PARTICLES.register("warp_streak", () -> new SimpleParticleType(false));

    private AWParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
