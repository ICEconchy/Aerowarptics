package uk.co.iceconchy.aerowarptics.client.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Where things go on a screen, as arithmetic.
 *
 * <p>Deliberately free of Minecraft: no {@code GuiGraphics}, no {@code Font}, nothing that needs a
 * game running. That is what lets {@link uk.co.iceconchy.aerowarptics} test the layouts directly -
 * and a screen layout is exactly the kind of thing worth testing, because the failure mode is a panel
 * quietly hanging off the edge of its window on someone else's GUI scale, which nobody notices until
 * they see a screenshot of it.
 *
 * <h2>The window model</h2>
 * Catnip's {@code BoxElement} is placed at the window's top-left with bounds of {@code width - 8},
 * and draws its border outside that. So the usable interior of a window {@code width} across is
 * {@code width - 8} pixels starting at the window origin, and {@link #PADDING} is inset from there.
 * Everything in this class is relative to the window origin, which is what {@code guiLeft}/{@code
 * guiTop} hold at render time.
 */
public final class AWLayout {

    /** Border allowance Catnip's box takes out of the declared window size. */
    public static final int FRAME = 8;

    /** Breathing room between the frame and any content. */
    public static final int PADDING = 10;

    /** Space between two panels sitting side by side or stacked. */
    public static final int GUTTER = 8;

    /** Height of the title block at the top of every screen. */
    public static final int HEADER = 30;

    /** Height of a row of buttons at the bottom. */
    public static final int BUTTON = 18;

    /** Space a footer of buttons occupies, including the gap above it. */
    public static final int FOOTER = BUTTON + GUTTER;

    /** Height of one label-and-value line inside a panel. */
    public static final int LINE = 13;

    /** Height of one row in a scrolling list. */
    public static final int ROW = 13;

    /** Inset panels are drawn two pixels out from their content, matching the old screens. */
    public static final int INSET = 2;

    private AWLayout() {
    }

    /**
     * A rectangle in window-relative pixels.
     *
     * <p>{@code x}/{@code y} are offsets from the window origin, so a screen adds {@code guiLeft} and
     * {@code guiTop} at draw time and a test does not have to.
     */
    public record Rect(int x, int y, int width, int height) {

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centreX() {
            return x + width / 2;
        }

        public int centreY() {
            return y + height / 2;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        /** Whether two rectangles share any pixel. Touching edges do not count as overlapping. */
        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right()
                    && y < other.bottom() && other.y < bottom();
        }

        public Rect shrink(int by) {
            return new Rect(x + by, y + by, Math.max(0, width - by * 2), Math.max(0, height - by * 2));
        }

        public Rect grow(int by) {
            return shrink(-by);
        }

        public Rect withHeight(int newHeight) {
            return new Rect(x, y, width, newHeight);
        }

        public Rect moved(int dx, int dy) {
            return new Rect(x + dx, y + dy, width, height);
        }
    }

    /**
     * Where a centred window should actually sit, once it might not fit.
     *
     * <p>Catnip centres a window by splitting the leftover space evenly on both sides - {@code
     * (screenSize - windowSize) / 2} - which goes negative the instant the window is wider or taller
     * than the screen. That is not a hypothetical: Minecraft's own "Auto" GUI scale is only guaranteed
     * to keep the screen at least 320 by 240 virtual pixels, never more, so a screen exactly at that
     * floor is completely ordinary. A centred window split negative on both edges loses the same strip
     * of content whichever side you look from - both the close button and the far edge of a list can
     * go missing at once.
     *
     * <p>Anchoring to the top-left instead loses only the far edge. The header, and whatever controls
     * a screen puts first, stay reachable even when the whole window cannot fit.
     *
     * @param screenSize the game's own virtual width or height
     * @param windowSize this window's width or height
     * @param centred    what Catnip already computed - kept rather than recomputed, so this stays a
     *                   pure adjustment of one already-known value
     */
    public static int anchor(int screenSize, int windowSize, int centred) {
        if (windowSize >= screenSize) {
            return 0;
        }
        return Math.max(0, Math.min(centred, screenSize - windowSize));
    }

    /** The interior of a window of this size: everything the frame does not take. */
    public static Rect interior(int windowWidth, int windowHeight) {
        return new Rect(0, 0, windowWidth - FRAME, windowHeight - FRAME);
    }

    /** The area content may occupy: the interior, inset by {@link #PADDING}. */
    public static Rect content(int windowWidth, int windowHeight) {
        return interior(windowWidth, windowHeight).shrink(PADDING);
    }

    /** The title block across the top of the content area. */
    public static Rect header(int windowWidth, int windowHeight) {
        Rect content = content(windowWidth, windowHeight);
        return content.withHeight(HEADER);
    }

    /** Everything between the header and the footer. */
    public static Rect body(int windowWidth, int windowHeight, boolean hasFooter) {
        Rect content = content(windowWidth, windowHeight);
        int top = content.y() + HEADER;
        int bottom = content.bottom() - (hasFooter ? FOOTER : 0);
        return new Rect(content.x(), top, content.width(), Math.max(0, bottom - top));
    }

    /** The row of buttons along the bottom of the content area. */
    public static Rect footer(int windowWidth, int windowHeight) {
        Rect content = content(windowWidth, windowHeight);
        return new Rect(content.x(), content.bottom() - BUTTON, content.width(), BUTTON);
    }

    /**
     * Splits an area into vertical columns.
     *
     * <p>Fixed widths are honoured exactly; a width of {@code 0} means "take what is left", shared
     * between however many columns asked for it. Rounding goes to the last flexible column, so the
     * columns always add up to the area exactly rather than leaving a stray pixel.
     */
    public static List<Rect> columns(Rect area, int... widths) {
        int fixed = 0;
        int flexible = 0;
        for (int width : widths) {
            if (width > 0) {
                fixed += width;
            } else {
                flexible++;
            }
        }
        int gutters = GUTTER * Math.max(0, widths.length - 1);
        int spare = Math.max(0, area.width() - fixed - gutters);
        int each = flexible == 0 ? 0 : spare / flexible;

        List<Rect> result = new ArrayList<>(widths.length);
        int x = area.x();
        int flexibleSeen = 0;
        for (int index = 0; index < widths.length; index++) {
            int width;
            if (widths[index] > 0) {
                width = widths[index];
            } else {
                flexibleSeen++;
                // The last flexible column mops up the rounding, so nothing is left unassigned.
                width = flexibleSeen == flexible ? spare - each * (flexible - 1) : each;
            }
            result.add(new Rect(x, area.y(), width, area.height()));
            x += width + GUTTER;
        }
        return result;
    }

    /** Splits an area into horizontal bands, with the same fixed-or-flexible rule as columns. */
    public static List<Rect> rows(Rect area, int... heights) {
        int fixed = 0;
        int flexible = 0;
        for (int height : heights) {
            if (height > 0) {
                fixed += height;
            } else {
                flexible++;
            }
        }
        int gutters = GUTTER * Math.max(0, heights.length - 1);
        int spare = Math.max(0, area.height() - fixed - gutters);
        int each = flexible == 0 ? 0 : spare / flexible;

        List<Rect> result = new ArrayList<>(heights.length);
        int y = area.y();
        int flexibleSeen = 0;
        for (int index = 0; index < heights.length; index++) {
            int height;
            if (heights[index] > 0) {
                height = heights[index];
            } else {
                flexibleSeen++;
                height = flexibleSeen == flexible ? spare - each * (flexible - 1) : each;
            }
            result.add(new Rect(area.x(), y, area.width(), height));
            y += height + GUTTER;
        }
        return result;
    }

    /** Splits a footer into evenly sized buttons. */
    public static List<Rect> buttons(Rect footer, int count) {
        int gutters = GUTTER * Math.max(0, count - 1);
        int each = (footer.width() - gutters) / Math.max(1, count);
        List<Rect> result = new ArrayList<>(count);
        int x = footer.x();
        for (int index = 0; index < count; index++) {
            // The last button reaches the right edge exactly, so a row of three is not one pixel short.
            int width = index == count - 1 ? footer.right() - x : each;
            result.add(new Rect(x, footer.y(), width, footer.height()));
            x += width + GUTTER;
        }
        return result;
    }

    /** How many whole list rows fit in a panel of this height. */
    public static int visibleRows(Rect panel) {
        return Math.max(0, (panel.height() - INSET * 2) / ROW);
    }
}
