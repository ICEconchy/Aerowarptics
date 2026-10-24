package uk.co.iceconchy.aerowarptics.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.rifting.RiftingRecipe;

/**
 * Rifting in JEI, drawn the way Create draws haunting: the fan, the item blown through, and the catalyst
 * standing in the stream.
 *
 * <p>The catalyst drawn is a pane of Rift Portal, because it is the one standing rift with a real baked
 * model: a fissure draws nothing at all, and a brazier is drawn by GeckoLib, which a GUI element cannot
 * show. The info page names all three.
 */
public class FanRiftingCategory extends ProcessingViaFanCategory.MultiOutput<RiftingRecipe> {

    public FanRiftingCategory(CreateRecipeCategory.Info<RiftingRecipe> info) {
        super(info);
    }

    @Override
    protected AllGuiTextures getBlockShadow() {
        return AllGuiTextures.JEI_LIGHT;
    }

    @Override
    protected void renderAttachedBlock(GuiGraphics graphics) {
        GuiGameElement.of(AWBlocks.RIFT_PORTAL.get().defaultBlockState())
                .scale(SCALE)
                .atLocal(0.0D, 0.0D, 2.0D)
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .render(graphics);
    }
}
