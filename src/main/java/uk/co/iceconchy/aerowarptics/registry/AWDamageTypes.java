package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * The mod's own ways to be hurt. Damage types are data - {@code data/aerowarptics/damage_type} - so this only
 * names them.
 */
public final class AWDamageTypes {

    /**
     * Caught too close to an unsteady collapse. In {@code #minecraft:is_explosion}, so Blast Protection answers
     * it; it breaks no blocks and is never dealt to an item.
     */
    public static final ResourceKey<DamageType> COLLAPSE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AeroWarptics.id("collapse"));

    /** Caught in a Rift Wellhead's meltdown. */
    public static final ResourceKey<DamageType> MELTDOWN =
            ResourceKey.create(Registries.DAMAGE_TYPE, AeroWarptics.id("meltdown"));

    /** Standing in the ground a meltdown left unsettled, or beside a well that is tearing. */
    public static final ResourceKey<DamageType> FALLOUT =
            ResourceKey.create(Registries.DAMAGE_TYPE, AeroWarptics.id("fallout"));

    /** Caught in a Rift Sunderer's tear: standing in the opening as it strains, or near it as it ruptures. */
    public static final ResourceKey<DamageType> SUNDERING =
            ResourceKey.create(Registries.DAMAGE_TYPE, AeroWarptics.id("sundering"));

    private AWDamageTypes() {
    }

    public static DamageSource meltdown(ServerLevel level, Vec3 from) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(MELTDOWN),
                from);
    }

    public static DamageSource fallout(ServerLevel level, Vec3 from) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FALLOUT),
                from);
    }

    /** A collapse's harm, from the point it fell to. */
    public static DamageSource collapse(ServerLevel level, Vec3 from) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(COLLAPSE),
                from);
    }

    /** A sundering's harm, from the opening it was torn in. */
    public static DamageSource sundering(ServerLevel level, Vec3 from) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SUNDERING),
                from);
    }
}
