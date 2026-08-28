package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.List;

/**
 * Every screen's layout, worked out once and in one place.
 *
 * <p>Screens read their rectangles from here rather than computing them inline, and
 * {@code ScreenLayoutTest} reads the same rectangles and checks they fit. That is the whole reason
 * this class exists: before it, each screen carried a handful of magic offsets, and the only way to
 * find out that two panels overlapped or that one hung a pixel off the edge was to open the game and
 * look. Two of them did.
 *
 * <p>Pure arithmetic, no Minecraft. The sizes are chosen so that the widest thing each panel has to
 * hold - a destination name, a distance in metres, a requirement line - has room for it at the
 * default font.
 */
public final class AWLayouts {

    /**
     * Side of the terrain preview, in pixels: one per sampled block.
     *
     * <p>Written out rather than derived, so this class stays free of Minecraft types. The test
     * checks it against {@code DestinationSurvey}'s own arithmetic, so the two cannot drift.
     */
    public static final int PREVIEW = 129;

    /** The preview with its inset frame around it. */
    public static final int PREVIEW_FRAMED = PREVIEW + AWLayout.INSET * 2;

    private AWLayouts() {
    }

    // --------------------------------------------------------------- console

    /** A captioned bar: one line of label, then the bar under it. */
    public static final int BAR_BAND = 16;

    /** Height of the bar inside a {@link #BAR_BAND}. */
    public static final int BAR = 6;

    public static final int CONSOLE_WIDTH = 300;
    // One bar band, not two. There used to be a second reserved for the spin-up bar, which only means
    // anything while a drive is stabilising - so for all but a few seconds of a drive's life the
    // console carried thirty-two pixels of nothing between its charge bar and its buttons. The two
    // bars now share the one band, which they can because they are never both worth showing: a drive
    // is fully charged before it begins to stabilise, so the charge bar it replaces is reading 100%.
    //
    // The height is what the requirements panel actually needs rather than what was left over. That
    // panel holds a title, a rule, six checked conditions, a second rule and the name of the course
    // under it - ninety-two pixels - and the two reserved bar bands had squeezed it to eighty-six, so
    // the course line was drawn below the panel's own bottom edge. Reclaiming the dead band pays for
    // the six it was short by and still leaves the console shorter than it was.
    public static final int CONSOLE_HEIGHT = 206;

    /**
     * The Rift Drive's console.
     *
     * @param readouts     the numbers: speed, stress, range, mass, charge, cooldown
     * @param requirements the checklist of what a jump still needs
     * @param bar          the charge bar, or the spin-up bar while the drive is winding up
     */
    public record Console(Rect header, Rect readouts, Rect requirements, Rect bar,
                          Rect cancel, Rect heading) {
    }

    public static Console console() {
        Rect body = AWLayout.body(CONSOLE_WIDTH, CONSOLE_HEIGHT, true);
        // The bar band carries its own caption above the bar, which is what the sixteen is for: ten
        // for the line of text and six for the bar itself. Sized any tighter, the caption is drawn
        // over the bottom of the panel above it.
        List<Rect> bands = AWLayout.rows(body, 0, BAR_BAND);
        List<Rect> panels = AWLayout.columns(bands.get(0), 0, 126);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(CONSOLE_WIDTH, CONSOLE_HEIGHT), 2);
        return new Console(
                AWLayout.header(CONSOLE_WIDTH, CONSOLE_HEIGHT),
                panels.get(0),
                panels.get(1),
                bands.get(1),
                footer.get(0),
                footer.get(1));
    }

    // ----------------------------------------------------------------- chart

    // Minecraft's "Auto" GUI scale never lets the virtual screen shrink past 320x240 - but it also
    // never promises more than that, and a great many ordinary window sizes land on exactly 320
    // wide. A window wider than that is not a rare-GUI-scale problem, it is a Tuesday.
    public static final int CHART_WIDTH = 320;
    public static final int CHART_HEIGHT = 292;

    /**
     * The Astrolabe's chart.
     *
     * @param scale  the strip under the preview carrying the scale and contour interval
     * @param detail the block of numbers about the selected destination
     */
    /**
     * The Astrolabe's chart.
     *
     * @param course where the ship is actually aimed, stated across the full width and above
     *               everything else - because it is the one fact on this screen that the list cannot
     *               show, now that a Rift Probe can aim the same drive at a place no anchor marks
     * @param scale  the strip under the preview carrying the scale and contour interval
     * @param detail the block of numbers about the destination being inspected
     */
    public record Chart(Rect header, Rect course, Rect list, Rect preview, Rect scale, Rect detail) {

        public int visibleRows() {
            return AWLayout.visibleRows(list);
        }
    }

    public static Chart chart() {
        Rect body = AWLayout.body(CHART_WIDTH, CHART_HEIGHT, false);
        List<Rect> bands = AWLayout.rows(body, 14, 0);
        List<Rect> panels = AWLayout.columns(bands.get(1), 0, PREVIEW_FRAMED);
        Rect right = panels.get(1);
        List<Rect> stack = AWLayout.rows(right, PREVIEW_FRAMED, 11, 0);
        return new Chart(
                AWLayout.header(CHART_WIDTH, CHART_HEIGHT),
                bands.get(0),
                panels.get(0),
                stack.get(0),
                stack.get(1),
                stack.get(2));
    }

    // ------------------------------------------------------------------ dial

    public static final int DIAL_WIDTH = 304;
    public static final int DIAL_HEIGHT = 240;

    /**
     * A Rift Gate's dial panel.
     *
     * @param name the gate's own name, which its owner may edit
     */
    public record Dial(Rect header, Rect name, Rect list, Rect detail, Rect dial, Rect access) {

        public int visibleRows() {
            return AWLayout.visibleRows(list);
        }
    }

    public static Dial dial() {
        Rect body = AWLayout.body(DIAL_WIDTH, DIAL_HEIGHT, true);
        List<Rect> bands = AWLayout.rows(body, 16, 0);
        List<Rect> panels = AWLayout.columns(bands.get(1), 0, 128);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(DIAL_WIDTH, DIAL_HEIGHT), 2);
        return new Dial(
                AWLayout.header(DIAL_WIDTH, DIAL_HEIGHT),
                bands.get(0),
                panels.get(0),
                panels.get(1),
                footer.get(0),
                footer.get(1));
    }

    // ---------------------------------------------------------------- anchor

    public static final int ANCHOR_WIDTH = 244;
    public static final int ANCHOR_HEIGHT = 178;

    public record Anchor(Rect header, Rect name, Rect network, Rect access, Rect enabled, Rect save) {
    }

    public static Anchor anchor() {
        Rect body = AWLayout.body(ANCHOR_WIDTH, ANCHOR_HEIGHT, true);
        // Each field is a label line above a box, so the bands are taller than the boxes in them.
        List<Rect> bands = AWLayout.rows(body, 26, 26, AWLayout.BUTTON, 0);
        List<Rect> toggles = AWLayout.columns(bands.get(2), 0, 0);
        return new Anchor(
                AWLayout.header(ANCHOR_WIDTH, ANCHOR_HEIGHT),
                new Rect(bands.get(0).x(), bands.get(0).y() + 10, bands.get(0).width(), 16),
                new Rect(bands.get(1).x(), bands.get(1).y() + 10, bands.get(1).width(), 16),
                toggles.get(0),
                toggles.get(1),
                AWLayout.footer(ANCHOR_WIDTH, ANCHOR_HEIGHT));
    }

    // ----------------------------------------------------------------- chute

    public static final int CHUTE_WIDTH = 304;
    public static final int CHUTE_HEIGHT = 236;

    /**
     * A Rift Chute's panel.
     *
     * @param name    the chute's own name, which its owner may edit
     * @param list    every chute this one could be bound to
     * @param detail  what the chute is doing and what it is holding
     * @param bind    the bind / unbind button
     * @param access  the public/private toggle
     */
    public record Chute(Rect header, Rect name, Rect list, Rect detail, Rect bind, Rect access) {

        public int visibleRows() {
            return AWLayout.visibleRows(list);
        }
    }

    public static Chute chute() {
        Rect body = AWLayout.body(CHUTE_WIDTH, CHUTE_HEIGHT, true);
        List<Rect> bands = AWLayout.rows(body, 16, 0);
        List<Rect> panels = AWLayout.columns(bands.get(1), 0, 128);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(CHUTE_WIDTH, CHUTE_HEIGHT), 2);
        return new Chute(
                AWLayout.header(CHUTE_WIDTH, CHUTE_HEIGHT),
                bands.get(0),
                panels.get(0),
                panels.get(1),
                footer.get(0),
                footer.get(1));
    }

    // ----------------------------------------------------------------- probe

    // Same reasoning as the chart: kept at or under the 320-wide floor Minecraft's Auto GUI scale
    // guarantees, rather than the 344 both screens used to assume was always available.
    public static final int PROBE_WIDTH = 320;
    public static final int PROBE_HEIGHT = 280;

    /**
     * The Rift Probe's panel.
     *
     * @param compass the bearing dial, which is square and drives its own hit testing
     * @param range   the range slider and its readout
     * @param supply  essence held against what a sounding at these settings costs
     * @param reading the survey a completed sounding brought back
     * @param verdict what that survey amounts to
     */
    public record Probe(Rect header, Rect compass, Rect range, Rect supply,
                        Rect reading, Rect verdict, Rect sound, Rect course) {
    }

    // ------------------------------------------------------------------ book

    // The handbook is a book rather than a panel, so the numbers are chosen for reading rather than
    // for readouts: two pages wide enough that a sentence does not wrap every four words, and tall
    // enough for a picture and a dozen lines under it. 320 is the ceiling for the same reason it is
    // everywhere else here - it is what Auto GUI scale actually guarantees.
    //
    // The height is past the 240 that scale floor promises, as the chart's and the probe's already
    // are, and for the same kind of reason: those two have a 129px survey to show and this has a
    // page of prose to hold. GuidebookTest measures the words against the room, so the two cannot
    // drift apart - shortening this is a decision that fails a test rather than one that silently
    // pushes a sentence off the paper.
    public static final int BOOK_WIDTH = 320;
    public static final int BOOK_HEIGHT = 300;

    /** Height of the ribbon tabs along the top edge, one per chapter. */
    public static final int RIBBON = 13;

    /**
     * An open handbook.
     *
     * <p>The two pages sit a {@link AWLayout#GUTTER} apart and that gap is the spine: the shadow
     * either page casts into it is drawn inside those eight pixels, which is why the gap is not
     * closed up to win two more characters a line.
     *
     * @param tabs    the strip of chapter ribbons along the top board
     * @param left    the left-hand page
     * @param right   the right-hand page
     * @param back    the corner arrow that turns back a spread
     * @param forward the corner arrow that turns forward a spread
     */
    public record Book(Rect tabs, Rect left, Rect right, Rect back, Rect forward) {

        /** The spine: everything between the two pages. */
        public Rect spine() {
            return new Rect(left.right(), left.y(), right.x() - left.right(), left.height());
        }
    }

    /** Width of a page-turn arrow. Wide enough to hit without hunting for it. */
    private static final int ARROW = 44;

    // How a page is set. These are here rather than in the screen for the usual reason: the screen
    // lays type out with them and GuidebookTest works out, with the same numbers, whether the words
    // actually fit on the page. A page whose last sentence is drawn two pixels below the paper is
    // not a crash and not a compile error - it is a sentence nobody ever reads.

    /** Room at the top of a page for its heading and the rule under it. */
    public static final int BOOK_HEADING = 19;

    /** Left and right margin inside a page. */
    public static final int BOOK_MARGIN = 5;

    /** Height of one line of text. */
    public static final int BOOK_LINE = 10;

    /** Space between one entry and the next. */
    public static final int BOOK_GAP = 3;

    /** Space under a diagram, before the words start. */
    public static final int BOOK_DIAGRAM_GAP = 5;

    /** The strip at the foot of a page that carries its number. */
    public static final int BOOK_FOLIO = 12;

    /** How far a numbered step's text is indented past its number. */
    public static final int BOOK_STEP_INDENT = 12;

    /** How far an aside is indented past its rule. */
    public static final int BOOK_NOTE_INDENT = 6;

    /** Height of one line of the table of contents. */
    public static final int BOOK_CONTENTS_ROW = 12;

    /**
     * The part of a page that carries words: what is left once the heading, the picture and the
     * page number have taken theirs.
     *
     * @param diagramHeight the picture's height, or zero where the page has none
     */
    public static Rect bookText(Rect page, int diagramHeight) {
        int top = page.y() + BOOK_HEADING
                + (diagramHeight > 0 ? diagramHeight + BOOK_DIAGRAM_GAP : 0);
        int bottom = page.bottom() - BOOK_FOLIO;
        return new Rect(page.x() + BOOK_MARGIN, top,
                page.width() - BOOK_MARGIN * 2, Math.max(0, bottom - top));
    }

    public static Book book() {
        // The whole content area, not AWLayout.body: a book has no title bar, because the one thing
        // a title bar would say is written on the page you are looking at.
        List<Rect> bands = AWLayout.rows(AWLayout.content(BOOK_WIDTH, BOOK_HEIGHT),
                RIBBON, 0, AWLayout.BUTTON);
        List<Rect> pages = AWLayout.columns(bands.get(1), 0, 0);
        Rect footer = bands.get(2);
        return new Book(
                bands.get(0),
                pages.get(0),
                pages.get(1),
                new Rect(footer.x(), footer.y(), ARROW, footer.height()),
                new Rect(footer.right() - ARROW, footer.y(), ARROW, footer.height()));
    }

    // -------------------------------------------------------------- modulator

    // Wide enough that the detail panel can hold its two longest lines - "Rift Essence" against
    // "500 / 500 mB", and "Drive" against a full tier name - each on one line with no ellipsis. At the
    // old 240 the panel had 126px to work with and truncated both, which is not something a narrower
    // window buys anything for: this screen has no list to scroll and no map to show, so the only
    // thing width costs is width.
    public static final int MODULATOR_WIDTH = 280;
    // Taller than the swatches and detail panels strictly need, by exactly one BAR_BAND plus the
    // gutter either side of it - the room the intensity slider below takes, added rather than carved
    // out of them so this screen grew when the feature did instead of the two original panels
    // quietly shrinking to make way for a third.
    public static final int MODULATOR_HEIGHT = 220;

    /** Side of one colour swatch in the Modulator's grid, and the gap between two of them. */
    public static final int SWATCH = 16;
    public static final int SWATCH_GAP = 2;
    /** Swatches per row of the grid - sixteen dye colours, laid out four by four. */
    public static final int SWATCH_COLUMNS = 4;
    /** Side of the small colour chip in the legend under the grid. Half a swatch. */
    public static final int CHIP = 8;

    /**
     * A Rift Modulator's panel.
     *
     * @param swatches  the sixteen dye-colour swatches - left click sets the core colour, right click
     *                  sets the rim, so there is nothing here to lay out beyond the grid itself
     * @param detail    essence held, and what the linked drive is doing
     * @param intensity the captioned bar that sets how strongly this drive's rift reads
     * @param theme     the button that cycles the rift's look and opening animation
     */
    public record Modulator(Rect header, Rect swatches, Rect detail, Rect intensity, Rect theme) {
    }

    public static Modulator modulator() {
        Rect body = AWLayout.body(MODULATOR_WIDTH, MODULATOR_HEIGHT, true);
        List<Rect> bands = AWLayout.rows(body, 0, BAR_BAND);
        int swatchesWidth = SWATCH_COLUMNS * SWATCH + (SWATCH_COLUMNS - 1) * SWATCH_GAP + AWLayout.INSET * 2;
        List<Rect> panels = AWLayout.columns(bands.get(0), swatchesWidth, 0);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(MODULATOR_WIDTH, MODULATOR_HEIGHT), 1);
        return new Modulator(
                AWLayout.header(MODULATOR_WIDTH, MODULATOR_HEIGHT),
                panels.get(0),
                panels.get(1),
                bands.get(1),
                footer.get(0));
    }

    public static Probe probe() {
        Rect body = AWLayout.body(PROBE_WIDTH, PROBE_HEIGHT, true);
        List<Rect> panels = AWLayout.columns(body, 0, PREVIEW_FRAMED);
        List<Rect> left = AWLayout.rows(panels.get(0), 0, 44, 40);
        List<Rect> right = AWLayout.rows(panels.get(1), PREVIEW_FRAMED, 0);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(PROBE_WIDTH, PROBE_HEIGHT), 2);
        return new Probe(
                AWLayout.header(PROBE_WIDTH, PROBE_HEIGHT),
                left.get(0),
                left.get(1),
                left.get(2),
                right.get(0),
                right.get(1),
                footer.get(0),
                footer.get(1));
    }
}
