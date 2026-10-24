package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.client.screen.CradleLayout;
import uk.co.iceconchy.aerowarptics.client.screen.PlotterLayout;
import uk.co.iceconchy.aerowarptics.collapse.CollapseFailure;
import uk.co.iceconchy.aerowarptics.collapse.CollapseRules;
import uk.co.iceconchy.aerowarptics.plotter.PlotFailure;
import uk.co.iceconchy.aerowarptics.plotter.PlotterState;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every word the two relic machines' screens draw fits where it is drawn - the Rift Plotter's table and the
 * sidebar it folds into, and the Collapse Cradle's panel - and that the cradle's tooltips wrap.
 *
 * <p>The plotter shipped with refusals up to 364 pixels long for a status line of 144, cut to an ellipsis a third of
 * the way in, and an exit-speed label and value drawn over each other; the cradle cut its status lines off mid-word
 * and handed vanilla tooltips of 900 pixels on one line, which run off the screen. None of it throws. Each check here
 * is the pixel arithmetic the screen does, against the default font's real glyph advances - the table
 * {@link ConsoleTextTest} reads off {@code ascii.png} - and the rooms the layouts give.
 */
class RelicTextTest {

    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    /** Every printable ASCII glyph whose advance is not six, from the 1.21.1 font sheet - see ConsoleTextTest. */
    private static final Map<Character, Integer> ADVANCE = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry('"', 4), Map.entry('\'', 2), Map.entry('(', 4),
            Map.entry(')', 4), Map.entry('*', 4), Map.entry(',', 2), Map.entry('.', 2), Map.entry(':', 2),
            Map.entry(';', 2), Map.entry('<', 5), Map.entry('>', 5), Map.entry('@', 7), Map.entry('I', 4),
            Map.entry('[', 4), Map.entry(']', 4), Map.entry('`', 3), Map.entry('f', 5), Map.entry('i', 2),
            Map.entry('k', 5), Map.entry('l', 3), Map.entry('t', 4), Map.entry('{', 4), Map.entry('|', 2),
            Map.entry('}', 4), Map.entry('~', 7),
            // Two from further down the same sheet, taken at a full cell so neither can pass a line that does not fit.
            Map.entry('°', 6), Map.entry('·', 6));

    private static final JsonObject LANG_FILE = lang();

    /** The widest figures each formatter prints. */
    private static final String DISTANCE = widest(AWLang::distance, 1.0e8D);
    private static final String COUNT = widest(AWLang::count, 1.0e8D);
    private static final String MB = "99,999,999";

    private static int width(String text) {
        int total = 0;
        for (char letter : text.toCharArray()) {
            assertTrue(letter >= ' ' && letter <= '~' || ADVANCE.containsKey(letter),
                    "\"" + text + "\" has a character this test cannot measure: U+" + Integer.toHexString(letter));
            total += ADVANCE.getOrDefault(letter, 6);
        }
        return total;
    }

    private static JsonObject lang() {
        try (Reader reader = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A key relative to the mod's namespace, formatted the way {@code AWLang.translate} formats it. */
    private static String text(String key, Object... args) {
        String full = AeroWarptics.MODID + "." + key;
        assertTrue(LANG_FILE.has(full), "en_us.json has no " + full);
        return String.format(LANG_FILE.get(full).getAsString(), args);
    }

    private static String widest(java.util.function.DoubleFunction<String> format, double ceiling) {
        String widest = "";
        for (double value = 1.0D; value <= ceiling; value *= 10.0D) {
            for (double sample : new double[]{value, value * 9.999D, value * 9.9999D}) {
                String shown = format.apply(sample);
                if (width(shown) > width(widest)) {
                    widest = shown;
                }
            }
        }
        return widest;
    }

    /** How many lines {@code Font.split} breaks a text into at a width: greedily, at spaces. */
    private static int lines(String text, int room) {
        int lines = 1;
        int line = 0;
        for (String word : text.split(" ")) {
            int needed = line == 0 ? width(word) : line + 4 + width(word);
            if (needed <= room) {
                line = needed;
            } else {
                lines++;
                line = width(word);
            }
        }
        return lines;
    }

    private static void fits(List<String> problems, String where, String text, int room) {
        if (width(text) > room) {
            problems.add(where + ": \"" + text + "\" is " + width(text) + "px in " + room + "px");
        }
    }

    /** A label and its value on one line, with the gap the screen keeps between them. */
    private static void pair(List<String> problems, String where, String label, String value, int gap, int room) {
        fits(problems, where, label + " ".repeat(gap / 4) + value, room);
    }

    private static void assertNoProblems(List<String> problems) {
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static List<String> refusals() {
        List<String> all = new ArrayList<>();
        for (PlotFailure failure : PlotFailure.values()) {
            if (failure.isFailure()) {
                all.add(text(failure.translationKey()));
            }
        }
        return all;
    }

    /** Every state line the plotter can show, at its widest. */
    private static List<String> plotterStates(boolean sidebar) {
        List<String> all = new ArrayList<>(List.of(text("gui.rift_plotter.reaching"),
                text("gui.rift_plotter.open", "600", "999"), text("gui.rift_plotter.lane_open", "999"),
                text("gui.rift_plotter.closing"), text("gui.rift_plotter.sending"), text("gui.rift_plotter.ready")));
        if (sidebar) {
            all.add(text("gui.rift_plotter.side.charging", "100%"));
            all.addAll(refusals());
        } else {
            all.add(text("gui.rift_plotter.charging", "100%", MB, MB));
            for (String refusal : refusals()) {
                all.add(refusal);
                all.add(text("gui.rift_plotter.last", refusal));
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ the plotter's table

    @Test
    void everySliderOnTheTableHasRoomForItsLabelAndItsValue() {
        int room = PlotterLayout.panel().rows().get(0).width();
        List<String> problems = new ArrayList<>();
        Map<String, List<String>> sliders = Map.of(
                "gui.rift_plotter.distance", List.of("256 m"),
                "gui.rift_plotter.bearing", List.of("359° NW"),
                "gui.rift_plotter.height", List.of("160 m"),
                "gui.rift_plotter.aim_yaw", List.of("-90°"),
                "gui.rift_plotter.aim_pitch", List.of(text("gui.rift_plotter.dive", 85), text("gui.rift_plotter.climb", 85),
                        text("gui.rift_plotter.level")),
                "gui.rift_plotter.exit_speed", List.of(text("gui.rift_plotter.keep_speed"), text("gui.rift_plotter.at_rest"),
                        "1000 m/s"));
        for (Map.Entry<String, List<String>> slider : sliders.entrySet()) {
            for (String value : slider.getValue()) {
                pair(problems, slider.getKey(), text(slider.getKey()), value, 6, room);
            }
        }
        assertNoProblems(problems);
    }

    @Test
    void everythingElseOnTheTableFits() {
        PlotterLayout.Panel panel = PlotterLayout.panel();
        int column = panel.rows().get(0).width();
        List<String> problems = new ArrayList<>();
        for (String tab : List.of("target", "exit", "entry")) {
            fits(problems, "tab " + tab, text("gui.rift_plotter.tab." + tab), panel.tabButtons().get(0).width() - 4);
        }
        for (String key : List.of("gui.rift_plotter.survey", "gui.rift_plotter.surveying_short")) {
            fits(problems, key, text(key), 56 - 4);
        }
        fits(problems, "coverage", text("gui.rift_plotter.coverage", "100%"), column - 56 - 6);
        fits(problems, "aimed", text("gui.rift_plotter.aimed"), column - 13);
        pair(problems, "ground", text("gui.rift_plotter.ground"), "y -64", 8, column);
        pair(problems, "away", text("gui.rift_plotter.away"), DISTANCE + " NW", 8, column);
        for (String key : List.of("gui.rift_plotter.engage", "gui.rift_plotter.stand_down")) {
            fits(problems, key, text(key), panel.engage().width() - 4);
        }
        int footer = panel.readouts().width();
        fits(problems, "far end", text("gui.rift_plotter.far_end", DISTANCE, "NW", MB), footer);
        fits(problems, "tank", text("gui.rift_plotter.tank", "16,000", "16,000", MB), footer);
        fits(problems, "no target", text("gui.rift_plotter.no_target_short"), footer);
        for (String state : plotterStates(false)) {
            fits(problems, "footer state", state, footer);
        }
        int header = panel.header().width() - 16 - width(text("gui.rift_plotter.title"));
        for (PlotterState state : PlotterState.values()) {
            fits(problems, "header " + state, text(state.translationKey()), header);
        }
        assertNoProblems(problems);
    }

    /** The table's footer has a line for each of its four readings, inside it. */
    @Test
    void theFooterHoldsItsFourLines() {
        Rect footer = PlotterLayout.panel().footer();
        assertTrue(30 + 8 <= footer.height(), "the state line runs off the foot of the footer: " + footer);
    }

    // ------------------------------------------------------------------ the sidebar

    @Test
    void everythingOnTheSidebarFitsTheNarrowestScreen() {
        PlotterLayout.Sidebar side = PlotterLayout.sidebar(320, 240);
        int room = side.rows().get(0).width();
        List<String> problems = new ArrayList<>();
        Map<String, List<String>> sliders = Map.of(
                "gui.rift_plotter.heading", List.of("359° NW"),
                "gui.rift_plotter.tilt", List.of(text("gui.rift_plotter.skyward"), text("gui.rift_plotter.groundward"), "-80°"),
                "gui.rift_plotter.reach", List.of("48 m"),
                "gui.rift_plotter.radius", List.of("64.0 m"),
                "gui.rift_plotter.face_yaw", List.of("359° NW"),
                "gui.rift_plotter.face_pitch", List.of(text("gui.rift_plotter.dive", 90), text("gui.rift_plotter.climb", 90),
                        text("gui.rift_plotter.level")),
                "gui.rift_plotter.dock_radius", List.of(text("gui.rift_plotter.dock_auto", 256), "256 m"),
                "gui.rift_plotter.dock_depth", List.of(text("gui.rift_plotter.dock_auto", 256), "256 m"),
                "gui.rift_plotter.dock_delay", List.of("30 s"),
                "gui.rift_plotter.window", List.of("600 s"));
        for (Map.Entry<String, List<String>> slider : sliders.entrySet()) {
            for (String value : slider.getValue()) {
                pair(problems, slider.getKey(), text(slider.getKey()), value, 6, room);
            }
        }
        for (String toggle : List.of("face_out", "dock", "fire_on_dock", "fire_on_redstone", "lane")) {
            fits(problems, toggle, text("gui.rift_plotter." + toggle), room - 14);
        }
        pair(problems, "fits", text("gui.rift_plotter.fits"), text("gui.rift_plotter.fits_value", "90.5"), 8, room);
        for (String carries : List.of("carries_all", "carries_vehicles")) {
            pair(problems, carries, text("gui.rift_plotter.carries"), text("gui.rift_plotter." + carries), 8, room);
        }
        int status = side.status().width();
        fits(problems, "price", text("gui.rift_plotter.side.cost", COUNT), status);
        fits(problems, "lane price", text("gui.rift_plotter.side.lane_cost", COUNT, COUNT), status);
        for (String dock : List.of("dock_empty", "dock_holding", "dock_lined_up")) {
            fits(problems, dock, text("gui.rift_plotter." + dock), status);
        }
        for (String state : plotterStates(true)) {
            fits(problems, "sidebar state", state, status);
        }
        fits(problems, "title", text("gui.rift_plotter.entry_title"), side.header().width());
        fits(problems, "back", text("gui.rift_plotter.back"), side.back().width() - 4);
        assertEquals(PlotterLayout.SIDEBAR_TABS, side.tabs().size());
        for (String tab : List.of("rift", "facing", "dock", "fire")) {
            fits(problems, "tab " + tab, text("gui.rift_plotter.side." + tab), side.tabs().get(0).width() - 4);
        }
        assertNoProblems(problems);
    }

    // ------------------------------------------------------------------ the goggles

    /** A goggle line long enough to push the overlay across the crosshair is one too long. */
    @Test
    void everyGoggleLineIsShort() {
        int room = 200;
        List<String> problems = new ArrayList<>();
        fits(problems, "charge", text("gui.goggles.rift_plotter.charge", MB, MB), room);
        fits(problems, "essence", text("gui.goggles.rift_plotter.essence", "16000", "16000"), room);
        fits(problems, "held", text("gui.goggles.rift_plotter.held", MB), room);
        fits(problems, "lane", text("gui.goggles.rift_plotter.lane", "999999"), room);
        fits(problems, "target", text("gui.goggles.rift_plotter.target", "-29999999", "-29999999"), room);
        for (String key : List.of("docking", "lined_up", "exit_open", "exit_waiting")) {
            fits(problems, key, text("gui.goggles.rift_plotter." + key), room);
        }
        for (String refusal : refusals()) {
            fits(problems, "refusal", refusal, room);
        }
        assertNoProblems(problems);
    }

    // ------------------------------------------------------------------ the ward

    /** The Rift Ward's sidebar: every string against the room its row gives it, at the worst each can be. */
    @Test
    void theWardsSidebarFits() {
        uk.co.iceconchy.aerowarptics.client.screen.WardLayout.Sidebar side =
                uk.co.iceconchy.aerowarptics.client.screen.WardLayout.sidebar(320, 240);
        int room = side.rows().get(0).width();
        List<String> problems = new ArrayList<>();
        fits(problems, "title", text("gui.rift_ward.title"), side.header().width());
        fits(problems, "close", text("gui.rift_ward.close"), side.close().width() - 4);
        for (String tab : List.of("field", "who")) {
            fits(problems, "tab " + tab, text("gui.rift_ward.tab." + tab), side.tabs().get(0).width() - 4);
        }
        pair(problems, "charge", text("gui.rift_ward.charge"), AWLang.essence(9_999_999.0D), 8, room);
        pair(problems, "tank", text("gui.rift_ward.tank"), AWLang.essence(16_000.0D, 16_000.0D), 8, room);
        pair(problems, "upkeep", text("gui.rift_ward.upkeep"), text("gui.rift_ward.per_minute", "999,999"), 8, room);
        pair(problems, "radius", text("gui.rift_ward.radius"), AWLang.distance(512.0D), 8, room);
        for (String key : List.of("gui.rift_ward.on", "gui.rift_ward.off", "gui.rift_ward.self_test")) {
            fits(problems, key, text(key), room - 14);
        }
        for (String key : List.of("gui.rift_ward.hint.0", "gui.rift_ward.hint.1", "gui.rift_ward.everyone")) {
            fits(problems, key, text(key), room);
        }
        for (String mode : List.of("block_all", "allow_networks", "block_networks")) {
            fits(problems, mode, text("rift_ward.mode." + mode), room - 4);
        }
        fits(problems, "add", text("gui.rift_ward.add"), side.add().width() - 4);
        fits(problems, "none named", text("gui.rift_ward.none_named"), side.names().width() - 8);
        for (String state : List.of("rift_ward.state.standing", "rift_ward.state.down", "rift_ward.state.off")) {
            fits(problems, state, text(state), side.status().width());
        }
        fits(problems, "turned", text("gui.rift_ward.turned", "99,999"), side.status().width());
        fits(problems, "save", text("gui.rift_ward.save"), side.save().width() - 4);
        assertNoProblems(problems);
    }

    // ------------------------------------------------------------------ the berth

    /**
     * The Berth's sidebar, the ward's with three tabs: every string against the room its row gives it, at the
     * worst each can be - the longest slip a server may allow, and a ship with no name, since one with a name is
     * trimmed by the screen.
     */
    @Test
    void theBerthsSidebarFits() {
        uk.co.iceconchy.aerowarptics.client.screen.WardLayout.Sidebar side =
                uk.co.iceconchy.aerowarptics.client.screen.BerthLayout.sidebar(320, 240);
        int room = side.rows().get(0).width();
        List<String> problems = new ArrayList<>();
        fits(problems, "title", text("gui.berth.title"), side.header().width());
        fits(problems, "close", text("gui.berth.close"), side.close().width() - 4);
        for (String tab : List.of("slip", "place", "who")) {
            fits(problems, "tab " + tab, text("gui.berth.tab." + tab), side.tabs().get(0).width() - 4);
        }
        for (String slider : List.of("length", "width", "height", "east", "up", "south")) {
            pair(problems, slider, text("gui.berth." + slider), AWLang.distance(-512.0D), 8, room);
        }
        pair(problems, "facing", text("gui.berth.facing"), text("gui.berth.degrees", 355), 8, room);
        pair(problems, "priority", text("gui.berth.priority"), "99", 8, room);
        for (String button : List.of("bow_first", "stern_first", "cast_off", "relink", "reserve.anyone",
                "reserve.network", "reserve.owner")) {
            fits(problems, button, text("gui.berth." + button), room - 4);
        }
        for (String toggle : List.of("on", "off", "tractor")) {
            fits(problems, toggle, text("gui.berth." + toggle), room - 14);
        }
        for (String line : List.of("reserve.anyone.about", "reserve.owner.about", "name", "state.empty",
                "state.cast_off")) {
            fits(problems, line, text("gui.berth." + line), room);
        }
        pair(problems, "anchor", text("gui.berth.anchor"), text("gui.berth.no_anchor"), 8, room);
        for (String state : List.of("reserved", "drawing", "moored")) {
            fits(problems, state, text("gui.berth.state." + state, text("gui.berth.a_ship")), side.status().width());
        }
        fits(problems, "takes", text("gui.berth.takes", 508, 508, 508), side.status().width());
        fits(problems, "save", text("gui.berth.save"), side.save().width() - 4);
        assertNoProblems(problems);
    }

    @Test
    void everyWardGoggleLineIsShort() {
        int room = 200;
        List<String> problems = new ArrayList<>();
        fits(problems, "radius", text("gui.goggles.rift_ward.radius", "512"), room);
        fits(problems, "charge", text("gui.goggles.rift_ward.charge", "10000000", "10000000"), room);
        fits(problems, "essence", text("gui.goggles.rift_ward.essence", "16000", "16000"), room);
        fits(problems, "self test", text("gui.goggles.rift_ward.self_test"), room);
        for (String mode : List.of("block_all", "allow_networks", "block_networks")) {
            fits(problems, mode, text("rift_ward.mode." + mode), room);
        }
        fits(problems, "turned", text("gui.goggles.rift_ward.turned", "999999"), room);
        for (String state : List.of("rift_ward.state.standing", "rift_ward.state.down", "rift_ward.state.off")) {
            fits(problems, state, text(state), room);
        }
        assertNoProblems(problems);
    }

    // ------------------------------------------------------------------ the cradle's panel

    /** The status box has three lines, and everything it can be asked to say at once wraps into them. */
    @Test
    void theCradlesStatusBoxHoldsEverythingItSays() {
        Rect box = CradleLayout.panel().status();
        int room = box.width() - 6;
        int most = (box.height() - 3 - 9) / 11 + 1;
        List<List<String>> sayings = new ArrayList<>();
        sayings.add(List.of(text("gui.collapse_cradle.panel.reading")));
        for (CollapseRules.Phase phase : CollapseRules.Phase.values()) {
            sayings.add(List.of(text("gui.collapse_cradle.panel.phase." + phase.name().toLowerCase(Locale.ROOT)),
                    text("gui.collapse_cradle.panel.heading", "100%"), text("gui.collapse_cradle.panel.unstable")));
        }
        sayings.add(List.of(text("gui.collapse_cradle.panel.folding_what"),
                text("gui.collapse_cradle.panel.makes", "16", "Folded Warp Crystals")));
        List<String> endings = new ArrayList<>();
        for (String key : LANG_FILE.keySet()) {
            if (key.startsWith(AeroWarptics.MODID + ".gui.collapse_cradle.outcome.")) {
                endings.add(LANG_FILE.get(key).getAsString());
            }
        }
        for (CollapseFailure failure : CollapseFailure.values()) {
            if (failure.isFailure()) {
                endings.add(text(failure.translationKey()));
            }
        }
        for (String next : List.of("next_fold", "next_collapse", "nothing")) {
            for (String ending : endings) {
                sayings.add(List.of(text("gui.collapse_cradle.panel." + next), ending));
            }
        }
        List<String> problems = new ArrayList<>();
        for (List<String> saying : sayings) {
            int total = 0;
            for (String line : saying) {
                total += lines(line, room);
            }
            if (total > most) {
                problems.add(saying + " takes " + total + " lines of " + most);
            }
        }
        assertNoProblems(problems);
    }

    @Test
    void everythingElseOnTheCradlesPanelFits() {
        CradleLayout.Panel panel = CradleLayout.panel();
        int preview = panel.preview().width();
        List<String> problems = new ArrayList<>();
        for (CollapseFailure failure : CollapseFailure.values()) {
            if (failure.isFailure() && lines(text(failure.translationKey()), preview) > 2) {
                problems.add(failure + " takes more than the two lines under Start");
            }
        }
        fits(problems, "needs speed", text("gui.collapse_cradle.panel.needs_speed", "1,024", "1,024"), preview);
        fits(problems, "cost", text("gui.collapse_cradle.panel.cost", COUNT), preview - 20);
        for (String key : List.of("begin_collapse", "begin_fold", "at_work", "nothing")) {
            fits(problems, key, text("gui.collapse_cradle.panel." + key), panel.start().width() - 4);
        }
        int header = CradleLayout.WIDTH - 28 - 8 - width(LANG_FILE.get("block.aerowarptics.collapse_cradle").getAsString());
        for (String state : List.of(text("gui.collapse_cradle.panel.held"), text("gui.collapse_cradle.panel.collapsing", "100%"),
                text("gui.collapse_cradle.panel.folding", "100%"), text("gui.collapse_cradle.panel.idle"),
                text("gui.collapse_cradle.panel.reading"))) {
            fits(problems, "header state", state, header);
        }
        // The figures under the gauges, which the panel cuts to their room rather than wrap: seen in game as
        // "No sta", "8,000 m" and "128 rp" when they stood in the thirty-six pixels beside the gauges.
        int figures = panel.figures().width();
        fits(problems, "no stabilisers", text("gui.collapse_cradle.panel.no_stabilisers"), figures);
        fits(problems, "stabilisers", text("gui.collapse_cradle.panel.stabilisers", "64"), figures);
        fits(problems, "essence", COUNT + " mB", figures);
        fits(problems, "speed", "1,024 rpm", figures);
        // Each gauge's name stands over it, two pixels left of its edge, and the two must not run together.
        int tank = panel.essence().x() - 2 + width(text("gui.collapse_cradle.panel.essence_short"));
        if (tank + 2 > panel.speed().x() - 2) {
            problems.add("the tank's name runs into the shaft's: it ends at " + tank + " and that starts at "
                    + (panel.speed().x() - 2));
        }
        fits(problems, "the shaft's name", text("gui.collapse_cradle.panel.speed_short"),
                panel.readouts().right() - (panel.speed().x() - 2));
        assertNoProblems(problems);
    }

    /** No tooltip, wrapped as the panel wraps them, is taller than a small screen. */
    @Test
    void theCradlesTooltipsWrapToASensibleHeight() {
        List<String> problems = new ArrayList<>();
        List<String> tips = List.of(text("gui.collapse_cradle.panel.essence_tip", COUNT, COUNT, COUNT),
                text("gui.collapse_cradle.panel.speed_tip", "1,024", "1,024", "1,024"),
                text("gui.collapse_cradle.panel.lean_tip"), text("gui.collapse_cradle.panel.dial_tip"),
                text("gui.collapse_cradle.panel.arms_tip"), text("gui.collapse_cradle.panel.cup_tip"),
                text("gui.collapse_cradle.panel.tray_tip"));
        for (String tip : tips) {
            if (lines(tip, 180) > 12) {
                problems.add("\"" + tip + "\" wraps to " + lines(tip, 180) + " lines");
            }
            for (String word : tip.split(" ")) {
                fits(problems, "a word of a tip", word, 180);
            }
        }
        assertNoProblems(problems);
    }
}
