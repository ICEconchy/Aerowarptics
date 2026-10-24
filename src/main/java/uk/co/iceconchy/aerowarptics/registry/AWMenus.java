package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleMenu;

/**
 * Container menus. The mod's other screens are panels fed by packets, with nothing in them a hand can pick up;
 * the Collapse Cradle's holds its slots, so it is a real container, opened by the server with the cradle's
 * position.
 */
public final class AWMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AeroWarptics.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CollapseCradleMenu>> COLLAPSE_CRADLE =
            MENUS.register("collapse_cradle", () -> IMenuTypeExtension.create(CollapseCradleMenu::client));

    private AWMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
