package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything on the Rift Ward's panel goes, as arithmetic - free of Minecraft, for {@code WardLayoutTest}.
 *
 * <p>The panel is a strip down the right of the screen with the world in view beside it, because what it sets is a
 * circle on the ground too wide to judge from where the player stands: the camera leaves their head and looks down
 * on the field while it is open. It is the Rift Plotter's sidebar in everything but what is on it - the same width,
 * the same six rows, the same foot - so the two relics' panels are one family.
 *
 * <p>Laid out against the screen it is on, at the screen's own scale: Minecraft's Auto GUI scale promises no less
 * than 320 by 240, and six rows between the tabs and the foot is exactly what 240 holds.
 */
public final class WardLayout {

    /** The strip's width, and a row's height: the plotter sidebar's own. */
    public static final int WIDTH = PlotterLayout.SIDEBAR_WIDTH;
    public static final int ROW_HEIGHT = PlotterLayout.ROW_HEIGHT;

    /** Rows on a tab. */
    public static final int ROWS = 6;

    /** The tabs: the field itself, and whom it turns. */
    public static final int TABS = 2;

    /** The height of one name in the list of networks, and how many show at once. */
    public static final int NAME_HEIGHT = 12;
    public static final int NAMES_SHOWN = 5;

    /** Width of the Add button beside the box a network's name is typed into. */
    private static final int ADD_WIDTH = 34;

    private WardLayout() {
    }

    /**
     * The strip.
     *
     * @param close  the button that closes the panel without saving
     * @param tabs   Field, and Who
     * @param rows   six rows, which each tab fills its own way
     * @param status two lines above the Save button: what the field is doing
     * @param save   the button that sends the settings and closes the panel
     */
    public record Sidebar(Rect frame, Rect header, Rect close, List<Rect> tabs, List<Rect> rows, Rect status, Rect save) {

        /** The box a network's name is typed into, on the Who tab's second row. */
        public Rect nameBox() {
            Rect row = rows.get(1);
            return new Rect(row.x(), row.y() + 2, row.width() - ADD_WIDTH - 4, 16);
        }

        /** The button beside it. */
        public Rect add() {
            Rect row = rows.get(1);
            return new Rect(row.right() - ADD_WIDTH, row.y() + 2, ADD_WIDTH, 16);
        }

        /** Where the named networks are listed: the Who tab's third, fourth and fifth rows, as one panel. */
        public Rect names() {
            Rect first = rows.get(2);
            Rect last = rows.get(4);
            return new Rect(first.x(), first.y(), first.width(), last.bottom() - first.y());
        }

        /** One name's line in the list, counted from the top of what is showing. */
        public Rect name(int shown) {
            Rect list = names();
            return new Rect(list.x() + 1, list.y() + 2 + shown * NAME_HEIGHT, list.width() - 2, NAME_HEIGHT);
        }

        /** The mark at the end of a name's line that strikes it off. */
        public Rect strike(int shown) {
            Rect line = name(shown);
            return new Rect(line.right() - NAME_HEIGHT, line.y(), NAME_HEIGHT, NAME_HEIGHT);
        }

        /** Every piece that must stand clear of every other, for the test. */
        public List<Rect> pieces() {
            List<Rect> all = new ArrayList<>();
            all.add(header);
            all.add(close);
            all.addAll(tabs);
            all.addAll(rows);
            all.add(status);
            all.add(save);
            return all;
        }
    }

    public static Sidebar sidebar(int screenWidth, int screenHeight) {
        return sidebar(screenWidth, screenHeight, TABS);
    }

    /** The same strip with another number of tabs across it: the Berth's panel, which is this one's sibling. */
    static Sidebar sidebar(int screenWidth, int screenHeight, int tabCount) {
        int width = Math.min(WIDTH, screenWidth);
        int left = screenWidth - width;
        Rect frame = new Rect(left, 0, width, screenHeight);
        int inner = width - 16;
        Rect close = new Rect(left + width - 8 - 46, 6, 46, 14);
        Rect header = new Rect(left + 8, 8, inner - close.width() - 4, 12);
        List<Rect> tabs = new ArrayList<>();
        int each = inner / tabCount;
        for (int i = 0; i < tabCount; i++) {
            // The last takes whatever the division left over, so the strip ends where the rows do.
            int tabWidth = i == tabCount - 1 ? inner - each * (tabCount - 1) : each;
            tabs.add(new Rect(left + 8 + each * i, 26, tabWidth, 14));
        }
        List<Rect> rows = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            rows.add(new Rect(left + 8, 46 + i * (ROW_HEIGHT + 2), inner, ROW_HEIGHT));
        }
        Rect save = new Rect(left + 8, screenHeight - 8 - 20, inner, 20);
        Rect status = new Rect(left + 8, save.y() - 4 - 30, inner, 30);
        return new Sidebar(frame, header, close, List.copyOf(tabs), List.copyOf(rows), status, save);
    }

    /**
     * How far off the camera stands to take in a field of this radius, looking down on it: far enough that the far
     * rim is in view, and never further than the camera goes.
     */
    public static float cameraDistance(double radius) {
        return (float) Math.max(24.0D, Math.min(OrbitCamera.MAX_DISTANCE, radius * 2.4D + 10.0D));
    }

    /** How far down a list of names may be scrolled. */
    public static int clampScroll(int scroll, int names) {
        return Math.max(0, Math.min(scroll, Math.max(0, names - NAMES_SHOWN)));
    }

    /** The bar inside a slider's row: under its caption, a knob's reach in from either end. */
    public static Rect sliderBar(Rect row) {
        return new Rect(row.x() + 3, row.y() + 12, row.width() - 6, 5);
    }
}
