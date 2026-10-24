package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkFilter;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOption;

import java.util.List;
import java.util.UUID;

/**
 * The strip above a destination list that says which network it is filtered to, and steps to the next.
 *
 * <p>Shared by the Astrolabe's chart and a gate's dial, which filter the same way and should look it.
 * Which network follows which is {@link NetworkFilter}'s; this only draws the strip and says which half
 * of it a click landed on.
 */
@OnlyIn(Dist.CLIENT)
final class NetworkFilterStrip {

    private NetworkFilterStrip() {
    }

    /**
     * Draws the strip: the network's name between two arrows, or "All networks" with no filter.
     *
     * <p>With no networks to filter by the arrows are left off and the words dimmed, so the strip does not
     * look like a control that has stopped working.
     *
     * @param mouseX in screen pixels, like the origin
     */
    static void draw(GuiGraphics graphics, Font font, int originX, int originY, Rect strip,
                     List<NetworkOption> options, @Nullable UUID filter, double mouseX, double mouseY) {
        int left = originX + strip.x();
        int top = originY + strip.y();
        boolean live = !options.isEmpty();
        if (live) {
            int side = side(strip, mouseX - originX, mouseY - originY);
            arrow(graphics, left + 1, top + 3, false, side < 0 ? AWScreenStyle.VALUE : AWScreenStyle.LABEL);
            arrow(graphics, left + strip.width() - 4, top + 3, true,
                    side > 0 ? AWScreenStyle.VALUE : AWScreenStyle.LABEL);
        }
        String label = filter == null
                ? AWLang.translate("gui.network.filter_all").string()
                : NetworkFilter.nameOf(options, filter);
        String shown = AWScreenStyle.trim(font, label, strip.width() - 16);
        int colour = !live ? AWAnim.fade(AWScreenStyle.LABEL, 0.5F)
                : filter == null ? AWScreenStyle.LABEL : AWScreenStyle.ACCENT;
        graphics.drawString(font, shown, left + (strip.width() - font.width(shown)) / 2, top + 2, colour, false);
    }

    /**
     * Which way a click on the strip steps the filter.
     *
     * @param x window-relative, like the rectangle
     * @return {@code -1} for its left half, {@code 1} for its right, {@code 0} for a point not on it
     */
    static int side(Rect strip, double x, double y) {
        if (!strip.contains(x, y)) {
            return 0;
        }
        return x < strip.centreX() ? -1 : 1;
    }

    /** A three-pixel arrowhead. Drawn rather than written, for the reason {@link AWScreenStyle#chevron} is. */
    private static void arrow(GuiGraphics graphics, int x, int y, boolean right, int colour) {
        for (int column = 0; column < 3; column++) {
            int inset = right ? column : 2 - column;
            graphics.fill(x + column, y + inset, x + column + 1, y + 5 - inset, colour);
        }
    }
}
