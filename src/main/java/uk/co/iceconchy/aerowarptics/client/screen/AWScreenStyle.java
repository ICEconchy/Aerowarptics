package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The look the mod's screens share.
 *
 * <p>Two screens showing the same machine from different angles should not have to agree on brass by
 * copying hex codes at each other, so the palette and the two panel shapes live here.
 */
@OnlyIn(Dist.CLIENT)
public final class AWScreenStyle {

    public static final Color PANEL = new Color(0xDD_16_12_1B, true);
    public static final Color BORDER_TOP = new Color(0xFF_5C_4A_36, true);
    public static final Color BORDER_BOTTOM = new Color(0xFF_2E_25_1B, true);
    public static final Color INSET = new Color(0xCC_0D_0B_12, true);
    public static final Color INSET_BORDER = new Color(0x66_3B_31_24, true);

    public static final int TITLE = 0xFF_D9_C3_92;
    public static final int LABEL = 0xFF_7C_6C_57;
    public static final int VALUE = 0xFF_E6_E1_D6;
    public static final int OK = 0xFF_49_D9_C4;
    public static final int BAD = 0xFF_D9_5C_4A;
    public static final int WARN = 0xFF_E0_B0_4A;

    private AWScreenStyle() {
    }

    /** The outer window every screen sits in. */
    public static void window(GuiGraphics graphics, int left, int top, int width, int height) {
        new BoxElement()
                .withBackground(PANEL)
                .gradientBorder(BORDER_TOP, BORDER_BOTTOM)
                .at(left, top)
                .withBounds(width - 8, height - 8)
                .render(graphics);
    }

    /** A recessed area inside the window: a list, a readout block, a chart. */
    public static void inset(GuiGraphics graphics, int left, int top, int width, int height) {
        new BoxElement()
                .withBackground(INSET)
                .flatBorder(INSET_BORDER)
                .at(left, top)
                .withBounds(width, height)
                .render(graphics);
    }

    /**
     * A requirement marker: filled when met, hollow when not.
     *
     * <p>Drawn rather than written, because a tick character is not something every font Minecraft
     * might be running has, and a requirement list that renders as boxes is worse than useless.
     */
    public static void marker(GuiGraphics graphics, int x, int y, boolean met) {
        int colour = met ? OK : BAD;
        graphics.fill(x, y, x + 6, y + 1, colour);
        graphics.fill(x, y + 5, x + 6, y + 6, colour);
        graphics.fill(x, y + 1, x + 1, y + 5, colour);
        graphics.fill(x + 5, y + 1, x + 6, y + 5, colour);
        if (met) {
            graphics.fill(x + 2, y + 2, x + 4, y + 4, colour);
        }
    }
}
