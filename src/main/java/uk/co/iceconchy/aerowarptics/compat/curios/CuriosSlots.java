package uk.co.iceconchy.aerowarptics.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/** Reads Curios slots. Compiled against Curios; reached only through {@link CuriosBridge}. */
final class CuriosSlots {

    private CuriosSlots() {
    }

    static boolean wears(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.isEquipped(item))
                .orElse(false);
    }
}
