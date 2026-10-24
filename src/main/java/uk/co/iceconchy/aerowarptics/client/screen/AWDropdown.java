package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

/**
 * A drop-down list, as arithmetic.
 *
 * <p>A box that shows one choice and, clicked, hangs a list of the others under itself. This is the part
 * of it that can be wrong without anybody seeing: where the list goes, which option is under the pointer,
 * and what happens when there are more options than rows. Free of Minecraft for the reason
 * {@link AWLayout} is, so {@code ScreenLayoutTest} can hold it to the window it opens in.
 *
 * <p>The list is drawn over whatever it hangs across rather than pushing it out of the way, which is the
 * whole point of a drop-down on a screen with no room to spare: closed, it costs one line.
 */
public final class AWDropdown {

    /** Height of one option. A line of text and its breathing room, a little tighter than a list row. */
    public static final int ROW = 12;

    /** Options shown at once. More than this and the list scrolls rather than growing down the window. */
    public static final int MAX_ROWS = 6;

    private AWDropdown() {
    }

    /** How many rows the open list has: one each, up to {@link #MAX_ROWS}. */
    public static int visibleRows(int options) {
        return Math.max(0, Math.min(MAX_ROWS, options));
    }

    /** The open list: hanging off the foot of its box, a pixel clear of it, and exactly as wide. */
    public static Rect list(Rect box, int options) {
        return new Rect(box.x(), box.bottom() + 1, box.width(),
                visibleRows(options) * ROW + AWLayout.INSET * 2);
    }

    /** A scroll position brought back inside the list, so the last row is never left blank. */
    public static int clampScroll(int scroll, int options) {
        return Math.max(0, Math.min(Math.max(0, options - MAX_ROWS), scroll));
    }

    /**
     * Where a list should open so the current choice is on it.
     *
     * <p>Without this a list of twenty opens on its first six whatever is chosen, and the one row a
     * player expects to find highlighted is somewhere below the bottom.
     */
    public static int scrollToShow(int selected, int options) {
        return clampScroll(selected - MAX_ROWS + 1, options);
    }

    /**
     * The option under a point, or {@code -1} for none.
     *
     * @param px     window-relative, like the rectangle
     * @param scroll options scrolled off the top
     */
    public static int optionAt(Rect list, double px, double py, int scroll, int options) {
        if (!list.contains(px, py)) {
            return -1;
        }
        int row = (int) Math.floor((py - list.y() - AWLayout.INSET) / ROW);
        if (row < 0 || row >= visibleRows(options)) {
            return -1;
        }
        int option = row + clampScroll(scroll, options);
        return option < options ? option : -1;
    }
}
