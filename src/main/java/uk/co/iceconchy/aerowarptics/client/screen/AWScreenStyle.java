package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The look the mod's screens share.
 *
 * <p>Two screens showing the same machine from different angles should not have to agree on brass by
 * copying hex codes at each other, so the palette and every shape they draw live here. The rule for
 * adding to it: if a screen needs a thing twice, or two screens need it once, it belongs in this file.
 *
 * <p>Colour is doing real work here rather than decoration. Purple is the rift and everything that
 * belongs to it; brass is the machine; teal means yes and red means no. A reader should be able to
 * tell whether a screen is happy without reading a word of it.
 */
@OnlyIn(Dist.CLIENT)
public final class AWScreenStyle {

    // ---------------------------------------------------------------- palette

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

    /** The rift's own colour. Anything that is about folded space is drawn in it. */
    public static final int ACCENT = 0xFF_C8_6C_FF;
    public static final int ACCENT_DIM = 0xFF_6B_3A_8C;

    /** The empty part of a bar or a slider. */
    public static final int TRACK = 0xFF_1A_16_12;

    /** A hairline between two blocks of content inside one panel. */
    public static final int RULE = 0x33_7C_6C_57;

    private AWScreenStyle() {
    }

    // ----------------------------------------------------------------- panels

    /** The outer window every screen sits in. */
    public static void window(GuiGraphics graphics, int left, int top, int width, int height) {
        new BoxElement()
                .withBackground(PANEL)
                .gradientBorder(BORDER_TOP, BORDER_BOTTOM)
                .at(left, top)
                .withBounds(width - AWLayout.FRAME, height - AWLayout.FRAME)
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
     * A recessed panel behind a laid-out rectangle.
     *
     * <p>The rectangle is the content area; the frame is drawn around it, which is why every screen
     * that did this by hand had a {@code - 2} in it somewhere.
     */
    public static void panel(GuiGraphics graphics, int originX, int originY, AWLayout.Rect rect) {
        inset(graphics,
                originX + rect.x() - AWLayout.INSET,
                originY + rect.y() - AWLayout.INSET,
                rect.width(),
                rect.height());
    }

    /** A hairline across a panel, for separating a title from what it titles. */
    public static void rule(GuiGraphics graphics, int left, int top, int width) {
        graphics.fill(left, top, left + width, top + 1, RULE);
    }

    // ------------------------------------------------------------------ text

    /**
     * The title block: what this screen is, and what it is looking at.
     *
     * <p>Four corners of information - name and subtitle on the left, subject and status on the right
     * - which is the shape every screen in this mod wanted anyway.
     */
    public static void header(GuiGraphics graphics, Font font, int originX, int originY,
                              AWLayout.Rect rect, Component title, String subtitle,
                              String subject, Component status, int statusColour, int subtitleColour) {
        int left = originX + rect.x();
        int top = originY + rect.y();
        int right = originX + rect.right();
        // A flat "half the header" was the old room given to whichever line has to share space with
        // something right-aligned on it - which starved the subtitle even when the status sharing its
        // row was five letters long, and fed it exactly as much rope when the status was not. Measure
        // what is actually sitting on the other end of each line instead.
        int gap = 6;

        graphics.drawString(font, title, left, top, TITLE, false);
        if (!subject.isEmpty()) {
            int room = Math.max(20, rect.width() - font.width(title) - gap);
            String trimmed = trim(font, subject, room);
            graphics.drawString(font, trimmed, right - font.width(trimmed), top, LABEL, false);
        }
        String statusText = status == null ? "" : status.getString();
        if (!subtitle.isEmpty()) {
            int room = statusText.isEmpty() ? rect.width()
                    : Math.max(20, rect.width() - font.width(statusText) - gap);
            graphics.drawString(font, trim(font, subtitle, room), left, top + 11, subtitleColour, false);
        }
        if (status != null) {
            graphics.drawString(font, statusText, right - font.width(statusText), top + 11,
                    statusColour, false);
        }
        rule(graphics, left, top + 24, rect.width());
    }

    /** A label on the left, a value on the right, of one line inside a panel. */
    public static int readout(GuiGraphics graphics, Font font, int left, int y, int width,
                              Component label, String value, int colour) {
        graphics.drawString(font, label, left, y, LABEL, false);
        int room = width - font.width(label.getString()) - 8;
        String trimmed = trim(font, value, Math.max(8, room));
        graphics.drawString(font, trimmed, left + width - font.width(trimmed) - 2, y, colour, false);
        return y + AWLayout.LINE;
    }

    /** Cuts a string to fit, with an ellipsis, rather than letting it run into its neighbour. */
    public static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(0, width - font.width("...")));
        return cut + "...";
    }

    // ------------------------------------------------------------------ bars

    /**
     * A filled bar.
     *
     * <p>Two pixels of highlight along the top of the fill, which is the whole difference between a
     * bar that looks drawn and a bar that looks like a rectangle.
     */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height,
                           float fraction, int colour) {
        graphics.fill(x, y, x + width, y + height, TRACK);
        int filled = Math.round(width * AWAnim.clamp(fraction));
        if (filled <= 0) {
            return;
        }
        graphics.fill(x, y, x + filled, y + height, colour);
        graphics.fill(x, y, x + filled, y + 1, AWAnim.blend(colour, 0xFF_FF_FF_FF, 0.35F));
    }

    /**
     * A bar with a highlight travelling along it.
     *
     * <p>For something that is working rather than merely part-full: the travelling band says the
     * number is going somewhere, which an unmoving bar at forty percent does not.
     */
    public static void workingBar(GuiGraphics graphics, int x, int y, int width, int height,
                                  float fraction, int colour, float ticks) {
        bar(graphics, x, y, width, height, fraction, colour);
        int filled = Math.round(width * AWAnim.clamp(fraction));
        if (filled <= 2) {
            return;
        }
        int band = Math.max(6, width / 8);
        int head = Math.round(AWAnim.sweep(ticks, 50.0F) * (filled + band)) - band;
        int from = Math.max(x, x + head);
        int to = Math.min(x + filled, x + head + band);
        if (to > from) {
            graphics.fill(from, y, to, y + height, AWAnim.fade(0x40_FF_FF_FF, 1.0F));
        }
    }

    // --------------------------------------------------------------- markers

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

    /** A short coloured strip, for marking the selected row of a list. */
    public static void selectionBar(GuiGraphics graphics, int x, int y, int height, int colour) {
        graphics.fill(x, y, x + 2, y + height, colour);
    }

    /** The scrollbar down the right of a list, drawn only when there is more than fits. */
    public static void scrollbar(GuiGraphics graphics, int right, int top, int height,
                                 int total, int visible, float scroll) {
        if (total <= visible) {
            return;
        }
        int barHeight = Math.max(8, height * visible / total);
        int travel = height - barHeight;
        int y = top + Math.round(travel * AWAnim.clamp(scroll / Math.max(1.0F, total - visible)));
        graphics.fill(right - 2, top, right, top + height, 0x33_00_00_00);
        graphics.fill(right - 2, y, right, y + barHeight, 0x88_D9_C3_92);
    }

    /** A rounded-looking status chip. Used where a word needs to read as a state, not a sentence. */
    public static void pill(GuiGraphics graphics, Font font, int x, int y, String text, int colour) {
        int width = font.width(text) + 8;
        graphics.fill(x + 1, y, x + width - 1, y + 11, AWAnim.fade(colour, 0.18F));
        graphics.fill(x, y + 1, x + 1, y + 10, AWAnim.fade(colour, 0.18F));
        graphics.fill(x + width - 1, y + 1, x + width, y + 10, AWAnim.fade(colour, 0.18F));
        graphics.drawString(font, text, x + 4, y + 2, colour, false);
    }

    public static int pillWidth(Font font, String text) {
        return font.width(text) + 8;
    }
}
