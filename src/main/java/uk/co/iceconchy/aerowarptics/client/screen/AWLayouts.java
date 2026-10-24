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

    // The full 320 Auto GUI scale guarantees, where it used to be 300. Those twenty pixels are what let
    // every requirement line be read whole: the list ran 126px wide, and "Awaiting redstone throttle"
    // and "Charge enough to set off" were both drawn cut off with an ellipsis on every console.
    public static final int CONSOLE_WIDTH = 320;
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
    //
    // Then a second row of buttons and its gutter on top of that, when the drive gained a fourth
    // control. Four across had 62px each, and "Bow: Forward (N)" is 84 - vanilla scrolls a label that
    // does not fit, so half the footer read as text sliding back and forth. Two rows of two give each
    // button 142px, which holds every label with room for a translation to run longer. Still inside the
    // 240 Auto scale guarantees; AWWindowScreen scales it down on a smaller screen than that.
    public static final int CONSOLE_HEIGHT = 232;

    /**
     * How wide the console's readouts panel is: its widest line, "Max Range" against "100,000 km", with the
     * eight-pixel gap {@code AWScreenStyle.readout} keeps between them. That was a creative drive's range;
     * it reads "Infinite" now, and any range long enough to print wider than this does too. "Ship Range",
     * the line under it, is two pixels wider and still inside, which is why it is not called anything longer. The
     * requirements panel beside it takes the rest, because its lines are sentences - the longest of them
     * "Flown 100,000 km of 100,000 km" under the list, which is what the last few pixels here went to.
     */
    public static final int CONSOLE_READOUTS_WIDTH = 120;

    /**
     * How far text sits inside a console panel's rectangle, on each side. The panel's frame is drawn
     * {@link AWLayout#INSET} outside the rectangle, so this is the gap between that frame and the words.
     */
    public static final int PANEL_TEXT_PAD = 2;

    /** How far a requirement's words start past its marker - six pixels of box and four of gap. */
    public static final int REQUIREMENT_INDENT = 10;

    /**
     * How far a vanilla button keeps its label from each of its edges. {@code AbstractButton.renderString}
     * scrolls the label rather than trimming it once it is wider than the button less this on both sides,
     * which is how to tell a label that does not fit: it moves.
     */
    public static final int BUTTON_TEXT_INSET = 2;

    /**
     * The Rift Drive's console.
     *
     * @param readouts     the numbers: speed, stress, the tier's range, this ship's, mass, charge, cooldown
     * @param requirements the checklist of what a jump still needs
     * @param bar          the charge bar, or the spin-up bar while the drive is winding up, or the
     *                     odometer while it cruises
     * @param heading      the bow setting
     * @param mode         waypoint or free cruise
     * @param clearance    whether the drive checks for terrain before it warps
     * @param cancel       abort before the rift opens, disengage a cruise, or close - bottom right,
     *                     where a screen is closed from
     */
    public record Console(Rect header, Rect readouts, Rect requirements, Rect bar,
                          Rect heading, Rect mode, Rect clearance, Rect cancel) {

        /** How wide a readout line may run: label on the left, figure on the right. */
        public int readoutRoom() {
            return readouts.width() - PANEL_TEXT_PAD * 2;
        }

        /** How wide a requirement's words may run, once the marker in front of them has its room. */
        public int requirementRoom() {
            return requirements.width() - PANEL_TEXT_PAD * 2 - REQUIREMENT_INDENT - PANEL_TEXT_PAD;
        }

        /** How wide the line under the requirements may run - the course, or a cruise's reach. */
        public int courseRoom() {
            return requirements.width() - PANEL_TEXT_PAD * 3;
        }

        /** How wide a label on any of the console's buttons may be before vanilla starts scrolling it. */
        public int buttonRoom() {
            return Math.min(Math.min(heading.width(), mode.width()), Math.min(clearance.width(), cancel.width()))
                    - BUTTON_TEXT_INSET * 2;
        }
    }

    public static Console console() {
        Rect body = AWLayout.body(CONSOLE_WIDTH, CONSOLE_HEIGHT, false);
        // The bar band carries its own caption above the bar, which is what the sixteen is for: ten
        // for the line of text and six for the bar itself. Sized any tighter, the caption is drawn
        // over the bottom of the panel above it.
        List<Rect> bands = AWLayout.rows(body, 0, BAR_BAND, AWLayout.BUTTON, AWLayout.BUTTON);
        List<Rect> panels = AWLayout.columns(bands.get(0), CONSOLE_READOUTS_WIDTH, 0);
        // Settings on the upper row, and the one control that ends something on the lower, beside the
        // clearance switch - so the button that closes the screen stays in the corner it always was.
        List<Rect> settings = AWLayout.buttons(bands.get(2), 2);
        List<Rect> actions = AWLayout.buttons(bands.get(3), 2);
        return new Console(
                AWLayout.header(CONSOLE_WIDTH, CONSOLE_HEIGHT),
                panels.get(0),
                panels.get(1),
                bands.get(1),
                settings.get(0),
                settings.get(1),
                actions.get(0),
                actions.get(1));
    }

    // ----------------------------------------------------------------- chart

    // Minecraft's "Auto" GUI scale never lets the virtual screen shrink past 320x240 - but it also
    // never promises more than that, and a great many ordinary window sizes land on exactly 320
    // wide. A window wider than that is not a rare-GUI-scale problem, it is a Tuesday.
    public static final int CHART_WIDTH = 320;
    // Tall enough that the detail panel - the flexible row at the foot of the right-hand column - holds
    // everything it draws: a title, a rule, three readouts and the course-status pill under them. At
    // the old 292 that panel was 52px for 69px of content, so the pill was drawn hanging below its own
    // box, off the bottom of the screen. The survey is a fixed 129px square whatever the table size, so
    // the height a chart needs does not vary - this is the one number that makes the pill fit, and
    // ScreenLayoutTest measures the panel against that content so it cannot drift short again. Past the
    // 240 floor Auto GUI scale guarantees, as the chart already was; AWWindowScreen scales it to fit.
    // One LINE taller than that again, for the fourth readout - the destination's network - which also
    // gives the list back most of what the filter strip above it takes.
    public static final int CHART_HEIGHT = 325;

    /**
     * Height of the strip above a destination list that says which network it is filtered to: one row of
     * the list it sits over, so it reads as that list's heading.
     */
    public static final int FILTER_STRIP = AWLayout.ROW;

    /**
     * How wide the chart's hold-altitude toggle is: a six-pixel box, a gap, and "Hold altitude" in the
     * default font with a few pixels to spare. Fixed rather than measured, because this class has no
     * font to measure with - the screen trims the label to it if a translation runs longer.
     */
    public static final int HOLD_TOGGLE_WIDTH = 80;

    /**
     * The Astrolabe's chart.
     *
     * @param course where the ship is actually aimed, stated across the width and above everything
     *               else - because it is the one fact on this screen that the list cannot show, now
     *               that a Rift Probe can aim the same drive at a place no anchor marks
     * @param hold   the hold-altitude toggle, at the end of the course's own line because it is a
     *               property of the course and not of whichever anchor is being inspected below
     * @param filter the network the list is filtered to, directly above the list it filters
     * @param scale  the strip under the preview carrying the scale and contour interval
     * @param detail the block of numbers about the destination being inspected
     */
    public record Chart(Rect header, Rect course, Rect hold, Rect filter, Rect list, Rect preview, Rect scale,
                        Rect detail) {

        public int visibleRows() {
            return AWLayout.visibleRows(list);
        }
    }

    public static Chart chart() {
        Rect body = AWLayout.body(CHART_WIDTH, CHART_HEIGHT, false);
        List<Rect> bands = AWLayout.rows(body, 14, 0);
        List<Rect> courseLine = AWLayout.columns(bands.get(0), 0, HOLD_TOGGLE_WIDTH);
        List<Rect> panels = AWLayout.columns(bands.get(1), 0, PREVIEW_FRAMED);
        List<Rect> left = AWLayout.rows(panels.get(0), FILTER_STRIP, 0);
        Rect right = panels.get(1);
        List<Rect> stack = AWLayout.rows(right, PREVIEW_FRAMED, 11, 0);
        return new Chart(
                AWLayout.header(CHART_WIDTH, CHART_HEIGHT),
                courseLine.get(0),
                courseLine.get(1),
                left.get(0),
                left.get(1),
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
     * @param name    the gate's own name, which its owner may edit
     * @param network the network the gate is on, beside its name and edited the same way
     * @param filter  the network the list is filtered to, directly above the list
     * @param passage the button that steps through how a gate passage looks to this player, under the
     *                gate's readings rather than in the footer: the footer is what the gate does, and this
     *                is the one thing on the panel that is the player's and not the gate's
     * @param members the button that opens the gate's network's members panel
     */
    public record Dial(Rect header, Rect name, Rect network, Rect filter, Rect list, Rect detail, Rect passage,
                       Rect dial, Rect access, Rect members) {

        public int visibleRows() {
            return AWLayout.visibleRows(list);
        }

        /** How wide the passage button's label may be before vanilla starts scrolling it. */
        public int passageRoom() {
            return passage.width() - BUTTON_TEXT_INSET * 2;
        }
    }

    public static Dial dial() {
        Rect body = AWLayout.body(DIAL_WIDTH, DIAL_HEIGHT, true);
        List<Rect> bands = AWLayout.rows(body, 16, 0);
        List<Rect> boxes = AWLayout.columns(bands.get(0), 0, 0);
        List<Rect> panels = AWLayout.columns(bands.get(1), 0, 128);
        List<Rect> left = AWLayout.rows(panels.get(0), FILTER_STRIP, 0);
        List<Rect> right = AWLayout.rows(panels.get(1), 0, AWLayout.BUTTON);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(DIAL_WIDTH, DIAL_HEIGHT), 3);
        return new Dial(
                AWLayout.header(DIAL_WIDTH, DIAL_HEIGHT),
                boxes.get(0),
                boxes.get(1),
                left.get(0),
                left.get(1),
                right.get(0),
                right.get(1),
                footer.get(0),
                footer.get(1),
                footer.get(2));
    }

    // ---------------------------------------------------------------- anchor

    public static final int ANCHOR_WIDTH = 244;
    // One BAR_BAND and its gutter taller than the form was before it had an arrival height, added
    // rather than carved out of the fields above it - the same way the Modulator grew its slider.
    public static final int ANCHOR_HEIGHT = 202;

    /** Width of the Members button beside a network box: the word, and a button's own edges. */
    public static final int MEMBERS_BUTTON = 64;

    /**
     * A Warp Anchor's configuration panel.
     *
     * @param network the network's name. The list of the player's own networks hangs under it while it
     *                has the keyboard, over the controls beneath - see {@link AWDropdown}
     * @param members the button beside it that opens that network's members panel
     * @param height  the captioned slider that sets how far above the anchor a ship comes in
     */
    public record Anchor(Rect header, Rect name, Rect network, Rect members, Rect height, Rect access,
                         Rect enabled, Rect save) {
    }

    public static Anchor anchor() {
        Rect body = AWLayout.body(ANCHOR_WIDTH, ANCHOR_HEIGHT, true);
        // Each field is a label line above a box, so the bands are taller than the boxes in them.
        List<Rect> bands = AWLayout.rows(body, 26, 26, BAR_BAND, AWLayout.BUTTON, 0);
        List<Rect> toggles = AWLayout.columns(bands.get(3), 0, 0);
        List<Rect> networkLine = AWLayout.columns(
                new Rect(bands.get(1).x(), bands.get(1).y() + 10, bands.get(1).width(), 16), 0, MEMBERS_BUTTON);
        return new Anchor(
                AWLayout.header(ANCHOR_WIDTH, ANCHOR_HEIGHT),
                new Rect(bands.get(0).x(), bands.get(0).y() + 10, bands.get(0).width(), 16),
                networkLine.get(0),
                networkLine.get(1),
                bands.get(2),
                toggles.get(0),
                toggles.get(1),
                AWLayout.footer(ANCHOR_WIDTH, ANCHOR_HEIGHT));
    }

    // --------------------------------------------------------------- network

    public static final int NETWORK_WIDTH = 244;
    // Inside the 240 Auto GUI scale guarantees, so this one is never scaled: it has two text boxes in it,
    // and a box drawn at four fifths of its size is a box whose caret is somewhere other than its letters.
    public static final int NETWORK_HEIGHT = 236;

    /**
     * A network's members panel.
     *
     * @param name      the network's name, which its owner may edit
     * @param list      everybody in it, the owner first
     * @param add       the box a player's name is typed into
     * @param addButton the button that adds them
     * @param info      one line for what the network has to say about the last thing asked of it
     * @param remove    takes the selected member out
     * @param leave     leave the network, or - for its owner - disband it
     */
    public record Network(Rect header, Rect name, Rect list, Rect add, Rect addButton, Rect info, Rect remove,
                          Rect leave) {

        public int visibleRows() {
            return AWLayout.visibleRows(list);
        }
    }

    public static Network network() {
        Rect body = AWLayout.body(NETWORK_WIDTH, NETWORK_HEIGHT, true);
        List<Rect> bands = AWLayout.rows(body, 16, 0, 16, 11);
        List<Rect> addLine = AWLayout.columns(bands.get(2), 0, MEMBERS_BUTTON);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(NETWORK_WIDTH, NETWORK_HEIGHT), 2);
        return new Network(
                AWLayout.header(NETWORK_WIDTH, NETWORK_HEIGHT),
                bands.get(0),
                bands.get(1),
                addLine.get(0),
                addLine.get(1),
                bands.get(3),
                footer.get(0),
                footer.get(1));
    }

    /**
     * The draggable bar inside a {@link #BAR_BAND}: the band's full width less a knob's reach either
     * side, sitting at the band's foot under its caption.
     *
     * <p>Shared so the captioned sliders - the Modulator's intensity and an anchor's arrival height -
     * put their bar in the same place, and a test can see where that is.
     */
    public static Rect sliderBar(Rect band) {
        return new Rect(band.x() + 4, band.y() + BAR_BAND - BAR - 2, band.width() - 8, BAR);
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
    // A third control band - the arrival height, the same size as the range above it - and its gutter
    // taller than it was, so the dial kept its size rather than shrinking to make room. The verdict
    // panel on the other side grows by the same amount, and spends it on where the ship will arrive.
    public static final int PROBE_HEIGHT = 332;

    /** Width of the probe's dimension box: room for "Twilight Forest" and the mark that says it opens. */
    public static final int PROBE_DIMENSION_WIDTH = 100;

    /**
     * The Rift Probe's panel.
     *
     * @param dimension the box naming the dimension being scanned, which opens into a list. It sits in
     *                  the header's own top-right corner - the "subject" every other screen's header
     *                  fills and this one left empty - so the panel grew no taller to take it: the dial
     *                  below has not a pixel to give, and the window is already scaled to fit most
     *                  screens. Its list hangs over the panels beneath; see {@link AWDropdown}
     * @param compass the bearing dial, which is square and drives its own hit testing
     * @param range   the range slider and its readout
     * @param height  the arrival-height slider, built like the range one
     * @param supply  essence held against what a sounding at these settings costs
     * @param reading the survey a completed sounding brought back
     * @param verdict what that survey amounts to
     */
    public record Probe(Rect header, Rect dimension, Rect compass, Rect range, Rect height, Rect supply,
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
    /** Space between the two lines of that legend, below the first chip. */
    public static final int CHIP_GAP = 5;
    /**
     * Lines of text that may stand in the legend's place when the theme brings its own colours and the
     * grid is greyed out. The note is wrapped to the grid's width and cut at this many.
     */
    public static final int SWATCH_NOTE_LINES = 3;
    /** Height of one wrapped line of that note. */
    public static final int NOTE_LINE = 10;

    /** Rows the Modulator's swatch grid occupies for a given number of swatches. */
    public static int swatchRows(int swatches) {
        return (swatches + SWATCH_COLUMNS - 1) / SWATCH_COLUMNS;
    }

    /**
     * How far down the swatch panel the legend starts - or, when the grid is greyed out, the note that
     * stands in for it. Shared by the screen and the test that proves both fit.
     */
    public static int swatchLegendTop(int swatches) {
        return swatchRows(swatches) * (SWATCH + SWATCH_GAP) + AWLayout.GUTTER;
    }

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
        List<Rect> left = AWLayout.rows(panels.get(0), 0, 44, 44, 40);
        List<Rect> right = AWLayout.rows(panels.get(1), PREVIEW_FRAMED, 0);
        List<Rect> footer = AWLayout.buttons(AWLayout.footer(PROBE_WIDTH, PROBE_HEIGHT), 2);
        Rect header = AWLayout.header(PROBE_WIDTH, PROBE_HEIGHT);
        // On the title's line and two pixels proud of it either way, which is what a box round a line
        // of text needs; its foot stops short of the status line below.
        Rect dimension = new Rect(header.right() - PROBE_DIMENSION_WIDTH, header.y() - 2,
                PROBE_DIMENSION_WIDTH, AWDropdown.ROW);
        return new Probe(
                header,
                dimension,
                left.get(0),
                left.get(1),
                left.get(2),
                left.get(3),
                right.get(0),
                right.get(1),
                footer.get(0),
                footer.get(1));
    }

    // ----------------------------------------------------------- rift coupler

    // Both of the coupler's screens are the full 320 across, for the console's reason: the Fleet Board
    // shows a formation beside a roster, and neither is any use squeezed. 236 tall, under the 240 Auto
    // scale guarantees, with AWWindowScreen shrinking it onto anything smaller.
    public static final int COUPLER_WIDTH = 320;
    public static final int COUPLER_HEIGHT = 236;

    /** Width of the Fleet Board's formation grid: a square view of the sky round the flagship. */
    public static final int FORMATION_WIDTH = 104;

    /** Height of the one line under the Fleet Board's panels: wake clearance and essence. */
    public static final int COUPLER_INFO = 10;

    /**
     * Widths of the Fleet Board's settings buttons, left to right - role, auto-accept, beacon, rift - with
     * Rename taking whatever is left.
     *
     * <p>Five buttons do not share the row evenly: an even fifth leaves "Be Escort" and "Call: Fleet" a
     * couple of pixels short, and vanilla scrolls a label that does not fit rather than cutting it. So each
     * is as wide as its longest label needs and Rename, the shortest, has the rest. {@code CouplerTextTest}
     * holds every label to its button.
     */
    private static final int[] FLEET_SETTINGS = {56, 50, 55, 53, 0};

    /**
     * The flagship's Fleet Board.
     *
     * @param formation the top-down grid, bow up, the escorts' markers dragged about it
     * @param roster    one row per escort, then the open slots, then the ships asking to join
     * @param info      the wake's clearance and the coupler's essence, on one line - and the rename box,
     *                  while a rename is being typed
     * @param sound     Sound Stations
     * @param castOff   Cast Off, acting on the selected row
     * @param fold      Fold, greyed until every row is ready, held to force
     * @param role      switches the coupler to escort
     * @param autoAccept whether the owner's own ships are taken without asking
     * @param beacon    whether a Rift Beacon calls the whole flotilla
     * @param sharedRift whether the flotilla folds through one rift or a rift each
     * @param rename    starts renaming the flagship
     */
    public record FleetBoard(Rect header, Rect formation, Rect roster, Rect info, Rect sound, Rect castOff,
                             Rect fold, Rect role, Rect autoAccept, Rect beacon, Rect sharedRift, Rect rename) {

        /** How many rows the roster panel shows before it scrolls. */
        public int rosterRows() {
            return AWLayout.visibleRows(roster);
        }
    }

    public static FleetBoard fleetBoard() {
        Rect body = AWLayout.body(COUPLER_WIDTH, COUPLER_HEIGHT, false);
        List<Rect> bands = AWLayout.rows(body, 0, COUPLER_INFO, AWLayout.BUTTON, AWLayout.BUTTON);
        List<Rect> panels = AWLayout.columns(bands.get(0), FORMATION_WIDTH, 0);
        List<Rect> orders = AWLayout.buttons(bands.get(2), 3);
        List<Rect> settings = AWLayout.columns(bands.get(3), FLEET_SETTINGS);
        return new FleetBoard(AWLayout.header(COUPLER_WIDTH, COUPLER_HEIGHT), panels.get(0), panels.get(1),
                bands.get(1), orders.get(0), orders.get(1), orders.get(2),
                settings.get(0), settings.get(1), settings.get(2), settings.get(3), settings.get(4));
    }

    /**
     * An escort's Station screen.
     *
     * @param box     the station box: where the ship is against its slot, a steering aid
     * @param detail  drift, tolerance, charge and harmony beside it
     * @param info    one line for the rename box
     * @param take    Take Station, on the flagship picked in the list
     * @param breakOff Break Off, or take back a request
     * @param role    switches the coupler to flagship
     * @param rename  starts renaming this ship
     */
    public record Station(Rect header, Rect box, Rect detail, Rect info, Rect take, Rect breakOff, Rect role,
                          Rect rename) {

        /**
         * Where the list of flagships to join is drawn while the ship is in no flotilla: both panels'
         * room, since the box and its readouts have nothing to show until then.
         */
        public Rect list() {
            return new Rect(box.x(), box.y(), detail.right() - box.x(), box.height());
        }
    }

    public static Station station() {
        Rect body = AWLayout.body(COUPLER_WIDTH, COUPLER_HEIGHT, false);
        List<Rect> bands = AWLayout.rows(body, 0, COUPLER_INFO, AWLayout.BUTTON, AWLayout.BUTTON);
        // Square, as tall as the band allows, so a block of drift is the same length both ways on screen.
        int side = bands.get(0).height();
        List<Rect> panels = AWLayout.columns(bands.get(0), side, 0);
        List<Rect> orders = AWLayout.buttons(bands.get(2), 2);
        List<Rect> settings = AWLayout.buttons(bands.get(3), 2);
        return new Station(AWLayout.header(COUPLER_WIDTH, COUPLER_HEIGHT), panels.get(0), panels.get(1),
                bands.get(1), orders.get(0), orders.get(1), settings.get(0), settings.get(1));
    }
}
