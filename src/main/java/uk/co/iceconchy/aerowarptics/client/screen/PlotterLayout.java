package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything on the Rift Plotter's table goes. Free of Minecraft, like {@link CradleLayout}, so a test can
 * hold every piece inside the window and off every other piece.
 *
 * <p>The model of the ground takes the left of the slab - it is the thing being aimed into, and it wants to be big.
 * The right is a column of three tabs - the target, the exit, the entry - each a stack of rows, one control a row.
 * Along the foot, what the plot costs and what the machine holds, and the one button that fires it.
 *
 * <p>Wider than 320: the window scales itself down to fit ({@link AWWindowScreen}), as the chart's does, because a
 * model of the ground drawn any smaller is not one anybody can aim into.
 */
public final class PlotterLayout {

    public static final int WIDTH = 396;
    /** Tall enough that the footer's four lines - the far end, the tank bar, the tank, the state - sit inside it. */
    public static final int HEIGHT = 256;

    /** Rows a tab can hold. */
    public static final int ROWS = 7;

    public static final int ROW_HEIGHT = 20;

    private PlotterLayout() {
    }

    public record Panel(Rect header, Rect view, Rect tabs, List<Rect> tabButtons, List<Rect> rows, Rect footer,
                        Rect readouts, Rect engage) {

        /** Every piece, for the overlap checks. The tab buttons sit inside the tab strip and are left out of it. */
        public List<Rect> pieces() {
            List<Rect> all = new ArrayList<>();
            all.add(header);
            all.add(view);
            all.add(tabs);
            all.addAll(rows);
            all.add(readouts);
            all.add(engage);
            return all;
        }
    }

    /**
     * How wide the sidebar is while the entry is being set with the world in view. No wider: on the smallest screen
     * the game promises, 320 across, any more would take over half the view - so every line in it fits 144 pixels.
     */
    public static final int SIDEBAR_WIDTH = 160;

    /** Rows a sidebar tab holds. */
    public static final int SIDEBAR_ROWS = 6;

    /**
     * Tabs across the sidebar's top: where the rift is, which way it faces, its dock, and what fires it. Four is as
     * many as the strip holds with a word on each - 36 pixels apiece - and six rows is as many as the smallest screen
     * holds under them, so a seventh control is a reason for a tab, not a row.
     */
    public static final int SIDEBAR_TABS = 4;

    /**
     * The sidebar the table becomes while the entry is set: a strip down the right of the screen, the world in view
     * beside it. Its back button and its tabs at the top, a stack of rows, and the price, the state and the fire button
     * along its foot. Laid out against the screen it is on, so it runs the screen's full height.
     *
     * @param back    the button back to the table
     * @param tabs    the sidebar's tabs: the rift, its facing, its dock, and how it fires
     * @param status  the lines of price and state above the fire button
     */
    public record Sidebar(Rect frame, Rect header, Rect back, List<Rect> tabs, List<Rect> rows, Rect status, Rect engage) {

        public List<Rect> pieces() {
            List<Rect> all = new ArrayList<>();
            all.add(header);
            all.addAll(tabs);
            all.addAll(rows);
            all.add(status);
            all.add(engage);
            return all;
        }
    }

    public static Sidebar sidebar(int screenWidth, int screenHeight) {
        int width = Math.min(SIDEBAR_WIDTH, screenWidth);
        int left = screenWidth - width;
        Rect frame = new Rect(left, 0, width, screenHeight);
        int inner = width - 16;
        Rect back = new Rect(left + width - 8 - 46, 6, 46, 14);
        Rect header = new Rect(left + 8, 8, inner - back.width() - 4, 12);
        List<Rect> tabs = new ArrayList<>();
        int each = inner / SIDEBAR_TABS;
        for (int i = 0; i < SIDEBAR_TABS; i++) {
            // The last takes whatever the division left over, so the strip ends where the rows do.
            int tabWidth = i == SIDEBAR_TABS - 1 ? inner - each * (SIDEBAR_TABS - 1) : each;
            tabs.add(new Rect(left + 8 + each * i, 26, tabWidth, 14));
        }
        List<Rect> rows = new ArrayList<>();
        for (int i = 0; i < SIDEBAR_ROWS; i++) {
            rows.add(new Rect(left + 8, 46 + i * (ROW_HEIGHT + 2), inner, ROW_HEIGHT));
        }
        Rect engage = new Rect(left + 8, screenHeight - 8 - 20, inner, 20);
        Rect status = new Rect(left + 8, engage.y() - 4 - 30, inner, 30);
        return new Sidebar(frame, header, back, List.copyOf(tabs), List.copyOf(rows), status, engage);
    }

    public static Panel panel() {
        Rect header = new Rect(12, 8, WIDTH - 24, 14);
        Rect view = new Rect(12, 26, 238, 176);
        int columnX = view.right() + 8;
        int columnWidth = WIDTH - 12 - columnX;
        Rect tabs = new Rect(columnX, 26, columnWidth, 14);
        List<Rect> tabButtons = new ArrayList<>();
        int tabWidth = columnWidth / 3;
        for (int i = 0; i < 3; i++) {
            int width = i == 2 ? columnWidth - tabWidth * 2 : tabWidth;
            tabButtons.add(new Rect(columnX + tabWidth * i, 26, width, 14));
        }
        List<Rect> rows = new ArrayList<>();
        int top = tabs.bottom() + 4;
        for (int i = 0; i < ROWS; i++) {
            rows.add(new Rect(columnX, top + i * (ROW_HEIGHT + 2), columnWidth, ROW_HEIGHT));
        }
        Rect footer = new Rect(12, view.bottom() + 6, WIDTH - 24, HEIGHT - view.bottom() - 6 - 10);
        Rect engage = new Rect(footer.right() - 86, footer.y() + 6, 86, 22);
        Rect readouts = new Rect(footer.x(), footer.y(), footer.width() - engage.width() - 8, footer.height());
        return new Panel(header, view, tabs, List.copyOf(tabButtons), List.copyOf(rows), footer, readouts, engage);
    }
}
