package uk.co.iceconchy.aerowarptics.compat.curios;

import com.simibubi.create.compat.curios.GogglesCurioRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * Draws Rift Goggles on a player's head when they are worn in a Curios slot.
 *
 * <p>Borrows Create's own renderer for its goggles rather than keeping a copy. It draws the item in
 * its head pose and tilts it up out of the way of a helmet, the same as Create's pair; and Create
 * registers the model layer it bakes whenever Curios is installed, which is the only time this runs.
 */
public final class CuriosClient {

    private CuriosClient() {
    }

    /** Called during client setup, and only when {@link CuriosBridge#loaded()}. */
    public static void registerRenderers() {
        try {
            // Read here, not inside the supplier, so a missing class fails now rather than mid-frame.
            ModelLayerLocation layer = GogglesCurioRenderer.LAYER;
            CuriosRendererRegistry.register(AWItems.RIFT_GOGGLES.get(),
                    () -> new GogglesCurioRenderer(Minecraft.getInstance().getEntityModels().bakeLayer(layer)));
        } catch (LinkageError e) {
            // A Create that has moved or renamed its Curios renderer: the goggles still work in the
            // slot, they just are not drawn there.
            AeroWarptics.LOGGER.warn("[curios] could not borrow Create's goggles renderer; Rift Goggles "
                    + "worn in a Curios slot will not be drawn", e);
        }
    }
}
