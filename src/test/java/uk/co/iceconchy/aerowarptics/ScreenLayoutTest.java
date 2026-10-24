package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.client.screen.AWAnim;
import uk.co.iceconchy.aerowarptics.client.screen.AWDropdown;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayouts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That everything on a screen fits inside it, and that nothing sits on top of anything else.
 *
 * <p>This is the test the screens did not have and needed. A panel that overhangs its window, or two
 * that overlap by four pixels, does not throw and does not fail to compile - it draws, slightly
 * wrong, and the only way to find out was to open the game and look at it. Two of the old screens had
 * exactly one pixel of margin left, entirely by accident.
 *
 * <p>{@code AWLayouts} is deliberately free of Minecraft so this can call it directly.
 */
class ScreenLayoutTest {

    /** Every panel on a screen, by name, so a failure says which one is wrong. */
    private static Map<String, Rect> panels(String screen) {
        Map<String, Rect> panels = new LinkedHashMap<>();
        switch (screen) {
            case "console" -> {
                AWLayouts.Console layout = AWLayouts.console();
                panels.put("header", layout.header());
                panels.put("readouts", layout.readouts());
                panels.put("requirements", layout.requirements());
                panels.put("bar", layout.bar());
                panels.put("heading", layout.heading());
                panels.put("mode", layout.mode());
                panels.put("clearance", layout.clearance());
                panels.put("cancel", layout.cancel());
            }
            case "chart" -> {
                AWLayouts.Chart layout = AWLayouts.chart();
                panels.put("header", layout.header());
                panels.put("course", layout.course());
                panels.put("hold", layout.hold());
                panels.put("filter", layout.filter());
                panels.put("list", layout.list());
                panels.put("preview", layout.preview());
                panels.put("scale", layout.scale());
                panels.put("detail", layout.detail());
            }
            case "dial" -> {
                AWLayouts.Dial layout = AWLayouts.dial();
                panels.put("header", layout.header());
                panels.put("name", layout.name());
                panels.put("network", layout.network());
                panels.put("filter", layout.filter());
                panels.put("list", layout.list());
                panels.put("detail", layout.detail());
                panels.put("passage", layout.passage());
                panels.put("dial", layout.dial());
                panels.put("access", layout.access());
                panels.put("members", layout.members());
            }
            case "anchor" -> {
                AWLayouts.Anchor layout = AWLayouts.anchor();
                panels.put("header", layout.header());
                panels.put("name", layout.name());
                panels.put("network", layout.network());
                panels.put("members", layout.members());
                panels.put("height", layout.height());
                panels.put("access", layout.access());
                panels.put("enabled", layout.enabled());
                panels.put("save", layout.save());
            }
            case "network" -> {
                AWLayouts.Network layout = AWLayouts.network();
                panels.put("header", layout.header());
                panels.put("name", layout.name());
                panels.put("list", layout.list());
                panels.put("add", layout.add());
                panels.put("addButton", layout.addButton());
                panels.put("info", layout.info());
                panels.put("remove", layout.remove());
                panels.put("leave", layout.leave());
            }
            case "chute" -> {
                AWLayouts.Chute layout = AWLayouts.chute();
                panels.put("header", layout.header());
                panels.put("name", layout.name());
                panels.put("list", layout.list());
                panels.put("detail", layout.detail());
                panels.put("bind", layout.bind());
                panels.put("access", layout.access());
            }
            case "probe" -> {
                AWLayouts.Probe layout = AWLayouts.probe();
                panels.put("header", layout.header());
                panels.put("compass", layout.compass());
                panels.put("range", layout.range());
                panels.put("height", layout.height());
                panels.put("supply", layout.supply());
                panels.put("reading", layout.reading());
                panels.put("verdict", layout.verdict());
                panels.put("sound", layout.sound());
                panels.put("course", layout.course());
            }
            case "book" -> {
                AWLayouts.Book layout = AWLayouts.book();
                panels.put("tabs", layout.tabs());
                panels.put("left", layout.left());
                panels.put("right", layout.right());
                panels.put("back", layout.back());
                panels.put("forward", layout.forward());
            }
            case "modulator" -> {
                AWLayouts.Modulator layout = AWLayouts.modulator();
                panels.put("header", layout.header());
                panels.put("swatches", layout.swatches());
                panels.put("detail", layout.detail());
                panels.put("intensity", layout.intensity());
                panels.put("theme", layout.theme());
            }
            case "fleet_board" -> {
                AWLayouts.FleetBoard layout = AWLayouts.fleetBoard();
                panels.put("header", layout.header());
                panels.put("formation", layout.formation());
                panels.put("roster", layout.roster());
                panels.put("info", layout.info());
                panels.put("sound", layout.sound());
                panels.put("castOff", layout.castOff());
                panels.put("fold", layout.fold());
                panels.put("role", layout.role());
                panels.put("autoAccept", layout.autoAccept());
                panels.put("beacon", layout.beacon());
                panels.put("sharedRift", layout.sharedRift());
                panels.put("rename", layout.rename());
            }
            case "station" -> {
                AWLayouts.Station layout = AWLayouts.station();
                panels.put("header", layout.header());
                panels.put("box", layout.box());
                panels.put("detail", layout.detail());
                panels.put("info", layout.info());
                panels.put("take", layout.take());
                panels.put("breakOff", layout.breakOff());
                panels.put("role", layout.role());
                panels.put("rename", layout.rename());
            }
            default -> throw new IllegalArgumentException(screen);
        }
        return panels;
    }

    private static int[] size(String screen) {
        return switch (screen) {
            case "console" -> new int[]{AWLayouts.CONSOLE_WIDTH, AWLayouts.CONSOLE_HEIGHT};
            case "chart" -> new int[]{AWLayouts.CHART_WIDTH, AWLayouts.CHART_HEIGHT};
            case "dial" -> new int[]{AWLayouts.DIAL_WIDTH, AWLayouts.DIAL_HEIGHT};
            case "anchor" -> new int[]{AWLayouts.ANCHOR_WIDTH, AWLayouts.ANCHOR_HEIGHT};
            case "network" -> new int[]{AWLayouts.NETWORK_WIDTH, AWLayouts.NETWORK_HEIGHT};
            case "chute" -> new int[]{AWLayouts.CHUTE_WIDTH, AWLayouts.CHUTE_HEIGHT};
            case "probe" -> new int[]{AWLayouts.PROBE_WIDTH, AWLayouts.PROBE_HEIGHT};
            case "book" -> new int[]{AWLayouts.BOOK_WIDTH, AWLayouts.BOOK_HEIGHT};
            case "modulator" -> new int[]{AWLayouts.MODULATOR_WIDTH, AWLayouts.MODULATOR_HEIGHT};
            case "fleet_board", "station" -> new int[]{AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT};
            default -> throw new IllegalArgumentException(screen);
        };
    }

    private static final List<String> SCREENS =
            List.of("console", "chart", "dial", "anchor", "network", "probe", "chute", "book", "modulator",
                    "fleet_board", "station");

    /**
     * The panels drawn with a recessed frame around them.
     *
     * <p>The rest - the header, the bars, the buttons, the text boxes - draw their own edges or none
     * at all, so the clearance a frame needs does not apply to them. Keeping the distinction explicit
     * is what stops this test from either missing real collisions or objecting to a caption sitting
     * directly under a rule, which is exactly where a caption belongs.
     */
    private static final Map<String, List<String>> FRAMED = Map.ofEntries(
            Map.entry("console", List.of("readouts", "requirements")),
            Map.entry("chart", List.of("list", "preview", "detail")),
            Map.entry("dial", List.of("list", "detail")),
            Map.entry("anchor", List.<String>of()),
            Map.entry("network", List.of("list")),
            Map.entry("probe", List.of("compass", "range", "height", "supply", "reading", "verdict")),
            Map.entry("chute", List.of("list", "detail")),
            // The handbook's pages draw their own edge and the shadow they throw into the spine, so
            // they want the same clearance from each other that a recessed panel does.
            Map.entry("book", List.of("left", "right")),
            Map.entry("modulator", List.of("swatches", "detail")),
            Map.entry("fleet_board", List.of("formation", "roster")),
            Map.entry("station", List.of("box", "detail")));

    private static Map<String, Rect> framedPanels(String screen) {
        Map<String, Rect> all = panels(screen);
        Map<String, Rect> framed = new LinkedHashMap<>();
        for (String name : FRAMED.get(screen)) {
            Rect rect = all.get(name);
            assertTrue(rect != null, screen + " has no panel called " + name);
            framed.put(name, rect);
        }
        return framed;
    }

    /**
     * Nothing hangs off the edge.
     *
     * <p>Checked against the window's interior rather than its declared size, because Catnip's box
     * draws its border outside the bounds it is given - so the last eight pixels of a window are
     * frame, not floor, and a panel that reaches them is already outside.
     */
    @Test
    void everyPanelIsInsideItsWindow() {
        for (String screen : SCREENS) {
            int[] size = size(screen);
            Rect interior = AWLayout.interior(size[0], size[1]);
            for (Map.Entry<String, Rect> entry : panels(screen).entrySet()) {
                Rect rect = entry.getValue();
                String where = screen + "/" + entry.getKey() + " " + rect;
                assertTrue(rect.x() >= 0, where + " starts left of the window");
                assertTrue(rect.y() >= 0, where + " starts above the window");
                assertTrue(rect.right() <= interior.right(),
                        where + " runs past the right edge (" + interior.right() + ")");
                assertTrue(rect.bottom() <= interior.bottom(),
                        where + " runs past the bottom edge (" + interior.bottom() + ")");
            }
        }
    }

    /**
     * Two panels never share a pixel.
     *
     * <p>The recessed frame each panel draws sits two pixels outside its rectangle, so touching
     * rectangles would still overlap on screen. That is why the gutter is checked separately below
     * rather than allowing rectangles to be flush.
     */
    @Test
    void noTwoPanelsOverlap() {
        for (String screen : SCREENS) {
            List<Map.Entry<String, Rect>> all = List.copyOf(panels(screen).entrySet());
            for (int a = 0; a < all.size(); a++) {
                for (int b = a + 1; b < all.size(); b++) {
                    Rect first = all.get(a).getValue();
                    Rect second = all.get(b).getValue();
                    assertTrue(!first.overlaps(second),
                            screen + ": " + all.get(a).getKey() + " " + first
                                    + " overlaps " + all.get(b).getKey() + " " + second);
                }
            }
        }
    }

    /**
     * Panels that sit side by side leave room for both their frames.
     *
     * <p>Each inset frame is drawn {@link AWLayout#INSET} pixels outside its rectangle, so two
     * neighbours need twice that between them or their borders draw over each other.
     */
    @Test
    void neighbouringPanelsLeaveRoomForTheirFrames() {
        int needed = AWLayout.INSET * 2;
        for (String screen : SCREENS) {
            List<Map.Entry<String, Rect>> all = List.copyOf(framedPanels(screen).entrySet());
            for (int a = 0; a < all.size(); a++) {
                for (int b = a + 1; b < all.size(); b++) {
                    Rect first = all.get(a).getValue();
                    Rect second = all.get(b).getValue();
                    boolean sideBySide = first.y() < second.bottom() && second.y() < first.bottom();
                    boolean stacked = first.x() < second.right() && second.x() < first.right();
                    String where = screen + ": " + all.get(a).getKey() + " and " + all.get(b).getKey();
                    if (sideBySide && first.right() <= second.x()) {
                        assertTrue(second.x() - first.right() >= needed,
                                where + " are " + (second.x() - first.right()) + "px apart, need " + needed);
                    }
                    if (stacked && first.bottom() <= second.y()) {
                        assertTrue(second.y() - first.bottom() >= needed,
                                where + " are " + (second.y() - first.bottom()) + "px apart, need " + needed);
                    }
                }
            }
        }
    }

    /** A panel with no room in it draws as an empty box, which reads as a broken screen. */
    @Test
    void everyPanelHasRoomInIt() {
        for (String screen : SCREENS) {
            for (Map.Entry<String, Rect> entry : framedPanels(screen).entrySet()) {
                Rect rect = entry.getValue();
                assertTrue(rect.width() >= 60,
                        screen + "/" + entry.getKey() + " is only " + rect.width() + "px wide");
                // Two lines and the padding around them: anything less is a box with a word in it.
                assertTrue(rect.height() >= AWLayout.LINE * 2 + 4,
                        screen + "/" + entry.getKey() + " is only " + rect.height() + "px tall");
            }
        }
    }

    /**
     * A captioned bar has room for its caption.
     *
     * <p>The console draws each bar's label inside the bar's own band, above the bar. When the band
     * was six pixels tall the label was drawn ten pixels higher - over the bottom of the panel above
     * it - which is the bug that put this test here.
     */
    @Test
    void barBandsHaveRoomForTheirCaptions() {
        AWLayouts.Console console = AWLayouts.console();
        for (Rect band : List.of(console.bar(), AWLayouts.modulator().intensity(), AWLayouts.anchor().height())) {
            assertTrue(band.height() >= AWLayouts.BAR_BAND,
                    "a bar band is " + band.height() + "px, too short for a caption and a bar");
            assertTrue(AWLayouts.BAR_BAND >= 10 + AWLayouts.BAR,
                    "a bar band cannot hold a line of text plus its bar");
        }
        // And the caption, drawn at the band's top, clears the panel above it.
        assertTrue(console.bar().y() - console.readouts().bottom() >= AWLayout.INSET,
                "the charge caption would be drawn over the readouts panel");
    }

    /**
     * The console's two panels have room for everything they draw.
     *
     * <p>Every other check here is about panels against each other and against the window; none of
     * them can see what a panel puts <em>inside</em> itself, which is how the requirements panel came
     * to be six pixels short of its own contents and drew the course name below its bottom edge. The
     * numbers below are the same ones {@code RiftDriveConsoleScreen} lays out with, so a line added to
     * either panel has to be accounted for here before it can overflow in the game.
     */
    @Test
    void theConsolePanelsHoldWhatTheyDraw() {
        AWLayouts.Console console = AWLayouts.console();

        // readouts: seven label-and-value lines, from a two-pixel inset.
        int readouts = AWLayout.INSET + 7 * AWLayout.LINE;
        assertTrue(console.readouts().height() >= readouts,
                "the readouts panel is " + console.readouts().height() + "px for " + readouts + "px of lines");

        // requirements: a title, a rule, six checked conditions, a rule, and the course under it.
        int requirements = AWLayout.INSET + 10 + 5 + 6 * 10 + 2 + 5 + 8;
        assertTrue(console.requirements().height() >= requirements,
                "the requirements panel is " + console.requirements().height() + "px for "
                        + requirements + "px of content - the course line would be drawn below it");
    }

    /**
     * The chart's detail panel has room for everything it draws.
     *
     * <p>Same blind spot as the console's panels: nothing else here can see what a panel puts inside
     * itself, which is how this one came to be 52px for 69px of content and drew the course-status pill
     * below its own bottom edge - hanging off the foot of the screen. The numbers are the ones
     * {@code AstrolabeChartScreen.renderDetails} lays out with: a two-pixel inset, a title and the rule
     * under it, four label-and-value readouts - distance, cost, ground and network - and the pill a gutter
     * below them.
     */
    @Test
    void theChartDetailPanelHoldsWhatItDraws() {
        // inset, title-and-rule, four readouts, then the pill two pixels below with its own height.
        int content = AWLayout.INSET + 15 + 4 * AWLayout.LINE + 2 + 11;
        assertTrue(AWLayouts.chart().detail().height() >= content,
                "the chart's detail panel is " + AWLayouts.chart().detail().height() + "px for "
                        + content + "px of content - the course-status pill would be drawn below it");
    }

    /**
     * The probe's verdict panel has room for everything it draws, now that it says where the ship
     * will arrive as well as what the ground is.
     *
     * <p>The numbers are {@code RiftProbeScreen.renderVerdict}'s: a two-pixel inset, the verdict pill
     * and the line it heads, three label-and-value readouts - ground, mapped, and the lowest arrival -
     * and the course-status pill two pixels under them.
     */
    @Test
    void theProbeVerdictPanelHoldsWhatItDraws() {
        int content = AWLayout.INSET + 16 + 3 * AWLayout.LINE + 2 + 11;
        assertTrue(AWLayouts.probe().verdict().height() >= content,
                "the probe's verdict panel is " + AWLayouts.probe().verdict().height() + "px for "
                        + content + "px of content - the course-status pill would be drawn below it");
    }

    /**
     * The two probe sliders are built the same way, so the height slider is not a cramped copy of the
     * range one - and neither has left the dial too small to hit a point on.
     */
    @Test
    void theProbeSlidersMatchAndTheDialKeepsItsSize() {
        AWLayouts.Probe probe = AWLayouts.probe();
        assertEquals(probe.range().width(), probe.height().width());
        assertEquals(probe.range().height(), probe.height().height());
        // RiftProbeScreen's ring radius: eight labels round a ring any smaller than this overlap.
        int radius = Math.min(probe.compass().width(), probe.compass().height() - 12) / 2 - 12;
        assertTrue(radius >= 30, "the bearing dial's ring is only " + radius + "px across its radius");
    }

    /**
     * The probe's dimension box sits in the corner of the header nothing else was using.
     *
     * <p>It is not in the panel map above because it is inside the header on purpose. What it must not
     * do is reach the header's second line, where the status is drawn right-aligned under it, or crowd
     * the title on its own line.
     */
    @Test
    void theProbeDimensionBoxSitsInTheHeadersEmptyCorner() {
        AWLayouts.Probe probe = AWLayouts.probe();
        Rect box = probe.dimension();
        Rect interior = AWLayout.interior(AWLayouts.PROBE_WIDTH, AWLayouts.PROBE_HEIGHT);
        assertTrue(box.x() >= 0 && box.y() >= 0 && box.right() <= interior.right(), box + " is off the window");
        assertEquals(probe.header().right(), box.right(), "the box is not flush with the header's right edge");
        // AWScreenStyle.header draws the subtitle and the status eleven pixels under the header's top.
        assertTrue(box.bottom() <= probe.header().y() + 11,
                box + " reaches the status line at y " + (probe.header().y() + 11));
        // And the title on the box's own line. "Rift Probe" is under sixty pixels in the game's font.
        assertTrue(box.x() - probe.header().x() >= 80, "only " + (box.x() - probe.header().x())
                + "px left of the dimension box for the title");
        // A line of text is eight pixels tall and is drawn two below the box's top.
        assertTrue(box.height() >= 2 + 8 + 2, box + " is too short for a line of text");
    }

    /** However many dimensions a server has, the open list stays on the window and under its box. */
    @Test
    void theProbeDimensionListStaysInsideTheWindow() {
        Rect box = AWLayouts.probe().dimension();
        Rect interior = AWLayout.interior(AWLayouts.PROBE_WIDTH, AWLayouts.PROBE_HEIGHT);
        for (int options = 1; options <= 64; options++) {
            Rect list = AWDropdown.list(box, options);
            assertEquals(box.x(), list.x());
            assertEquals(box.width(), list.width());
            assertTrue(list.y() >= box.bottom(), "the list of " + options + " opens over its own box");
            assertTrue(list.bottom() <= interior.bottom() && list.right() <= interior.right(),
                    "a list of " + options + " runs off the window: " + list);
            assertTrue(list.height() <= AWDropdown.MAX_ROWS * AWDropdown.ROW + AWLayout.INSET * 2,
                    "a list of " + options + " grew past its rows");
        }
    }

    /**
     * The option a click lands on is the one drawn under it, scrolled or not.
     *
     * <p>Off by one here aims the probe at the dimension next to the one the player chose, which they
     * would find out about after paying for the scan.
     */
    @Test
    void aDropdownRowIsTheOptionDrawnThere() {
        int row = AWDropdown.ROW;
        int most = AWDropdown.MAX_ROWS;
        Rect box = AWLayouts.probe().dimension();
        for (int options : new int[]{1, 3, most, most + 4}) {
            Rect list = AWDropdown.list(box, options);
            int rows = Math.min(most, options);
            for (int scroll = 0; scroll <= Math.max(0, options - most); scroll++) {
                for (int line = 0; line < rows; line++) {
                    // The top and bottom pixel of each row, as RiftProbeScreen draws them.
                    int top = list.y() + AWLayout.INSET + line * row;
                    for (int y : new int[]{top, top + row - 1}) {
                        assertEquals(line + scroll, AWDropdown.optionAt(list, list.x() + 4, y, scroll, options),
                                options + " options scrolled " + scroll + ", row " + line + " at y " + y);
                    }
                }
            }
            // The frame round the rows is not a row, and nor is anywhere off the list.
            assertEquals(-1, AWDropdown.optionAt(list, list.x() + 4, list.y(), 0, options));
            assertEquals(-1, AWDropdown.optionAt(list, list.x() + 4, list.bottom() - 1, 0, options));
            assertEquals(-1, AWDropdown.optionAt(list, list.x() - 1, list.y() + AWLayout.INSET + 1, 0, options));
        }
    }

    /** A list opens on the row that is chosen, and can never be scrolled past its last option. */
    @Test
    void aDropdownOpensOnItsChoiceAndStopsAtItsEnds() {
        int most = AWDropdown.MAX_ROWS;
        for (int options = 1; options <= most * 3; options++) {
            for (int selected = 0; selected < options; selected++) {
                int scroll = AWDropdown.scrollToShow(selected, options);
                assertTrue(scroll <= selected && selected < scroll + most,
                        "option " + selected + " of " + options + " is off a list opened at " + scroll);
                assertTrue(scroll >= 0 && scroll <= Math.max(0, options - most));
            }
            assertEquals(0, AWDropdown.clampScroll(-3, options));
            assertEquals(Math.max(0, options - most), AWDropdown.clampScroll(999, options));
        }
    }

    /** An anchor's height slider sits inside its own band, under its caption. */
    @Test
    void theAnchorHeightSliderSitsUnderItsCaption() {
        Rect band = AWLayouts.anchor().height();
        Rect bar = AWLayouts.sliderBar(band);
        assertTrue(bar.x() >= band.x() && bar.right() <= band.right(), "the bar runs out of its band sideways");
        assertTrue(bar.bottom() <= band.bottom(), "the bar hangs below its band");
        assertTrue(bar.y() - band.y() >= 8, "the bar would be drawn over its caption");
    }

    /**
     * The preview panel is exactly the size of the picture that goes in it.
     *
     * <p>{@code AWLayouts} writes the number out so it can stay free of Minecraft; this is what stops
     * the two drifting apart. A survey one pixel wider than its panel would be silently cropped.
     */
    @Test
    void thePreviewPanelMatchesTheSurveyItHolds() {
        int surveySize = 2 * DestinationSurvey.RADIUS / DestinationSurvey.STEP + 1;
        assertEquals(surveySize, AWLayouts.PREVIEW,
                "AWLayouts.PREVIEW has drifted from DestinationSurvey's grid");
        assertEquals(AWLayouts.PREVIEW, AWLayouts.chart().preview().width() - AWLayout.INSET * 2);
        assertEquals(AWLayouts.PREVIEW, AWLayouts.probe().reading().width() - AWLayout.INSET * 2);
        assertTrue(AWLayouts.chart().preview().height() >= AWLayouts.PREVIEW,
                "the chart's preview panel is shorter than the survey it draws");
        assertTrue(AWLayouts.probe().reading().height() >= AWLayouts.PREVIEW,
                "the probe's reading panel is shorter than the survey it draws");
    }

    /**
     * A list's filter strip sits directly over the list it filters, and is exactly as wide - so it reads as
     * that list's heading, and a click on either half of it is a click over the list.
     */
    @Test
    void aFilterStripIsTheHeadingOfItsList() {
        Rect[][] pairs = {
                {AWLayouts.chart().filter(), AWLayouts.chart().list()},
                {AWLayouts.dial().filter(), AWLayouts.dial().list()}};
        for (Rect[] pair : pairs) {
            Rect strip = pair[0];
            Rect list = pair[1];
            assertEquals(list.x(), strip.x());
            assertEquals(list.width(), strip.width());
            assertEquals(AWLayouts.FILTER_STRIP, strip.height());
            assertTrue(strip.bottom() + AWLayout.INSET <= list.y(), "the strip is drawn into the list's frame");
            assertTrue(list.y() - strip.bottom() <= AWLayout.GUTTER, "the strip has drifted away from its list");
        }
    }

    /**
     * The list of a player's networks hangs under the anchor panel's network box, over the controls below.
     * Open at its fullest it has still to be inside the window, or its last rows cannot be clicked.
     */
    @Test
    void theAnchorsNetworkListOpensInsideTheWindow() {
        Rect interior = AWLayout.interior(AWLayouts.ANCHOR_WIDTH, AWLayouts.ANCHOR_HEIGHT);
        Rect list = uk.co.iceconchy.aerowarptics.client.screen.AWDropdown.list(AWLayouts.anchor().network(),
                AWDropdown.MAX_ROWS + 10);
        assertTrue(list.bottom() <= interior.bottom(),
                "the network list runs to " + list.bottom() + ", past the window's " + interior.bottom());
        assertTrue(list.right() <= interior.right());
    }

    /** The members panel has two text boxes, so it is one of the screens that must never be scaled down. */
    @Test
    void theMembersPanelFitsTheAutoScaleFloorUnscaled() {
        assertEquals(1.0F, AWLayout.fitScale(320, 240, AWLayouts.NETWORK_WIDTH, AWLayouts.NETWORK_HEIGHT));
        assertTrue(AWLayouts.network().visibleRows() >= 5,
                "the members panel shows only " + AWLayouts.network().visibleRows() + " members");
    }

    /** A list that shows three rows is not a list. */
    @Test
    void everyListShowsEnoughRowsToBeWorthScrolling() {
        assertTrue(AWLayouts.chart().visibleRows() >= 12,
                "the chart shows only " + AWLayouts.chart().visibleRows() + " destinations");
        assertTrue(AWLayouts.dial().visibleRows() >= 8,
                "the dial shows only " + AWLayouts.dial().visibleRows() + " gates");
        assertTrue(AWLayouts.chute().visibleRows() >= 8,
                "the chute shows only " + AWLayouts.chute().visibleRows() + " chutes");
    }

    /** Buttons sit in a row with a gap between them, and the row fills the width exactly. */
    @Test
    void footerButtonsFillTheirRow() {
        for (String screen : List.of("console", "dial", "network", "probe", "chute")) {
            int[] size = size(screen);
            Rect footer = AWLayout.footer(size[0], size[1]);
            // The dial has a third: the way into its gate's network.
            List<Rect> buttons = AWLayout.buttons(footer, screen.equals("dial") ? 3 : 2);
            assertEquals(footer.x(), buttons.getFirst().x(), screen + ": first button is not flush left");
            assertEquals(footer.right(), buttons.getLast().right(),
                    screen + ": last button does not reach the right edge");
            assertEquals(AWLayout.GUTTER, buttons.get(1).x() - buttons.get(0).right(),
                    screen + ": buttons are not one gutter apart");
            for (Rect button : buttons) {
                assertTrue(button.width() >= 80,
                        screen + ": a " + button.width() + "px button will not hold its label");
            }
        }
    }

    /** Columns and rows account for every pixel they were given, with no stray remainder. */
    @Test
    void splittingAnAreaLosesNothing() {
        Rect area = new Rect(0, 0, 301, 197);
        List<Rect> columns = AWLayout.columns(area, 0, 0, 0);
        assertEquals(area.x(), columns.getFirst().x());
        assertEquals(area.right(), columns.getLast().right(), "columns did not fill the area");

        List<Rect> rows = AWLayout.rows(area, 0, 40, 0);
        assertEquals(area.y(), rows.getFirst().y());
        assertEquals(area.bottom(), rows.getLast().bottom(), "rows did not fill the area");
        assertEquals(40, rows.get(1).height(), "a fixed row was not given its height");
    }

    /** Fixed columns get exactly what they asked for, whatever is left over. */
    @Test
    void fixedColumnsKeepTheirWidth() {
        Rect area = new Rect(10, 10, 300, 100);
        List<Rect> columns = AWLayout.columns(area, 0, AWLayouts.PREVIEW_FRAMED);
        assertEquals(AWLayouts.PREVIEW_FRAMED, columns.get(1).width());
        assertEquals(area.right(), columns.get(1).right());
        assertEquals(AWLayout.GUTTER, columns.get(1).x() - columns.get(0).right());
    }

    /** Screens have to fit on a modest window at a sensible GUI scale. */
    @Test
    void everyScreenFitsAThousandByEightHundred() {
        for (String screen : SCREENS) {
            int[] size = size(screen);
            // 1000x800 is a 1920x1080 desktop at GUI scale 2 with room to spare, and a 2560x1440 one
            // at scale 3. A screen larger than this cannot be shown without the game shrinking itself.
            assertTrue(size[0] <= 1000, screen + " is " + size[0] + "px wide");
            assertTrue(size[1] <= 800, screen + " is " + size[1] + "px tall");
        }
    }

    /**
     * No screen is wider than the smallest virtual screen Minecraft's own "Auto" GUI scale ever
     * produces.
     *
     * <p>1000x800 above is a sanity check against something absurd; this is the number that actually
     * matters. "Auto" picks the largest integer scale that still leaves the virtual screen at least
     * 320 by 240, and never promises more than that floor - a great many ordinary window sizes land
     * on exactly 320 wide, not as a rare GUI-scale choice but as the routine result of that
     * calculation. A window wider than 320 is not "fits on tiny windows", it is "fits on Tuesday".
     *
     * <p>The floor is only asserted on width. Holding every screen's height to 240 as well would mean
     * the chart and the probe could no longer show the 129px survey they exist to display - see
     * {@link #everyPanelIsInsideItsWindow()}'s sibling checks for what actually bounds their height.
     * {@link AWLayout#anchor} is what keeps a screen usable on the rarer window where even width
     * does not fit.
     */
    @Test
    void noScreenIsWiderThanTheGuaranteedAutoScaleFloor() {
        for (String screen : SCREENS) {
            int[] size = size(screen);
            assertTrue(size[0] <= 320, screen + " is " + size[0] + "px wide, wider than Auto scale guarantees");
        }
    }

    /**
     * The chart and the probe are taller than the 240px floor Auto scale guarantees, so they scale to
     * fit rather than hang their bottom edge - buttons and all - off the screen the way anchoring
     * alone would. This is the check that they actually come back on once scaled.
     */
    @Test
    void tallScreensScaleOntoTheAutoScaleFloor() {
        for (String screen : List.of("chart", "probe", "book")) {
            int[] size = size(screen);
            float scale = AWLayout.fitScale(320, 240, size[0], size[1]);
            assertTrue(scale < 1.0F, screen + " is not tall enough to need scaling at 320x240");
            assertTrue(Math.round(size[1] * scale) <= 240,
                    screen + " still overhangs 240px after scaling to " + scale);
            assertTrue(Math.round(size[0] * scale) <= 320,
                    screen + " still overhangs 320px after scaling to " + scale);
        }
    }

    /**
     * A window that already fits is never touched, so the scaled render path stays off for every
     * ordinary case; one taller than the screen shrinks by exactly the height ratio; nothing is ever
     * scaled up.
     */
    @Test
    void fitScaleShrinksOnlyWhatOverhangs() {
        assertEquals(1.0F, AWLayout.fitScale(480, 270, 320, 236),
                "a window that fits was scaled anyway");
        assertEquals(1.0F, AWLayout.fitScale(320, 240, 320, 240),
                "a window exactly filling the screen was scaled");
        assertEquals(240.0F / 292.0F, AWLayout.fitScale(480, 240, 320, 292), 1.0e-6F);
        assertEquals(1.0F, AWLayout.fitScale(1000, 800, 320, 292),
                "a window with room to spare was scaled up");
    }

    /**
     * A centred window that fits stays centred; one that does not fit is anchored to the corner
     * instead of split evenly negative on both edges.
     */
    @Test
    void anchorKeepsAWindowOnScreenWhenItCan() {
        // Plenty of room: the centred position Catnip already computed is left alone.
        assertEquals(50, AWLayout.anchor(400, 300, 50));

        // Exactly enough room: the window fills the screen, so top-left is correct either way.
        assertEquals(0, AWLayout.anchor(300, 300, 0));

        // The window is wider than the screen - centred would be negative. Anchor to the corner.
        assertEquals(0, AWLayout.anchor(300, 320, -10));

        // The window fits, but Catnip's centring (perhaps from a stale size) would have pushed it
        // partly off the right or bottom edge - clamp back onto the screen rather than trust it blindly.
        assertEquals(80, AWLayout.anchor(400, 320, 200));

        // Never produces a negative origin, whatever comes in.
        assertTrue(AWLayout.anchor(200, 320, -400) >= 0);
    }

    /**
     * Under the Modulator's swatch grid there is room for whichever of its two legends is showing: the
     * two chips that say what a click does, or - with the grid greyed out - the note that stands in
     * for them, at its longest.
     */
    @Test
    void theSwatchLegendAndTheNoteInItsPlaceBothFit() {
        Rect panel = AWLayouts.modulator().swatches();
        int swatches = 16;
        int top = AWLayouts.swatchLegendTop(swatches);
        int grid = AWLayouts.swatchRows(swatches) * (AWLayouts.SWATCH + AWLayouts.SWATCH_GAP) - AWLayouts.SWATCH_GAP;
        assertTrue(top > grid, "the legend starts inside the grid");
        int chips = AWLayouts.CHIP * 2 + AWLayouts.CHIP_GAP;
        assertTrue(top + chips <= panel.height(), "the legend runs out of the swatch panel");
        int note = AWLayouts.SWATCH_NOTE_LINES * AWLayouts.NOTE_LINE;
        assertTrue(top + note <= panel.height(),
                "the greyed-out note runs out of the swatch panel: " + (top + note) + " > " + panel.height());
    }

    /**
     * A greyed-out colour is a grey, keeps its opacity, and sits in a band narrow enough that the
     * brightest and darkest swatches no longer stand out - which is the whole of what makes a switched-off
     * grid look switched off rather than merely dim.
     */
    @Test
    void greyingOutLeavesOnlyAQuietGrey() {
        int darkest = 255;
        int brightest = 0;
        for (int colour : new int[]{0xFF_000000, 0xFF_FFFFFF, 0xFF_FF0000, 0xFF_00FF00, 0xFF_1D1D21,
                0xFF_F9FFFE, 0x80_3C44AA}) {
            int grey = AWAnim.greyed(colour);
            int r = (grey >> 16) & 0xFF;
            assertEquals(r, (grey >> 8) & 0xFF, "a greyed colour still has colour in it");
            assertEquals(r, grey & 0xFF, "a greyed colour still has colour in it");
            assertEquals(colour >>> 24, grey >>> 24, "greying changed how opaque a colour is");
            darkest = Math.min(darkest, r);
            brightest = Math.max(brightest, r);
        }
        assertTrue(brightest - darkest <= 110, "greyed-out swatches still range from " + darkest + " to " + brightest);
        assertTrue(darkest >= 0x20, "a greyed-out swatch is black, which reads as a hole rather than as off");
    }
}
