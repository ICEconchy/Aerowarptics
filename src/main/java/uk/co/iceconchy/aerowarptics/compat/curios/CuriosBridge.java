package uk.co.iceconchy.aerowarptics.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * The one door to Curios, an optional dependency: with it, Rift Goggles can be worn in a Curios
 * head slot as well as the helmet slot, as Create's own goggles can.
 *
 * <p>This class names nothing of Curios, so anything may call it whether Curios is installed or not.
 * The classes beside it ({@link CuriosSlots}, {@link CuriosClient}) do, and are only reached once
 * {@link #loaded()} has said yes - so without Curios their imports are never resolved.
 *
 * <p>Letting the goggles <em>into</em> the slot needs no code at all: it is the
 * {@code curios:head} item tag and the slot assignment under {@code data/aerowarptics/curios}.
 */
public final class CuriosBridge {

    public static final String MOD_ID = "curios";

    /** {@code null} until the mod list exists to ask; then fixed for the rest of the game. */
    private static Boolean loaded;

    private CuriosBridge() {
    }

    /** Whether Curios is installed. False before the mod list exists, as in unit tests. */
    public static boolean loaded() {
        Boolean known = loaded;
        if (known == null) {
            ModList mods = ModList.get();
            if (mods == null) {
                return false;
            }
            known = mods.isLoaded(MOD_ID);
            loaded = known;
        }
        return known;
    }

    /**
     * Whether {@code entity} has {@code item} in one of its Curios slots. Cosmetic slots do not
     * count: an item there is shown, not worn. Always false without Curios.
     */
    public static boolean wears(LivingEntity entity, Item item) {
        return entity != null && loaded() && CuriosSlots.wears(entity, item);
    }
}
