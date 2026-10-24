package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayouts;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.drive.DriveMode;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.GatePassageStyle;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every word the Rift Drive's console draws fits where it is drawn.
 *
 * <p>{@link ScreenLayoutTest} proves the console's panels fit the window and each other, and says
 * nothing about what is inside them - which is how the console came to trim two of its six requirement
 * lines with an ellipsis on every drive, and to scroll half its button labels back and forth once a
 * third button squeezed the footer. Neither throws, and neither looks broken in a code review: the text
 * is there, just not readable.
 *
 * <p>The widths are the default font's real glyph advances, read off the game's {@code ascii.png} -
 * not the rounded-up table {@code GuidebookTest} uses - because a console line has a pixel or two of
 * slack, not a paragraph's worth, and an over-estimate would fail lines that fit. The rooms are
 * {@link AWLayouts.Console}'s, which the screen lays its text out with.
 */
class ConsoleTextTest {

    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    /** Every printable ASCII glyph whose advance is not six, from the 1.21.1 font sheet. */
    private static final Map<Character, Integer> ADVANCE = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry('"', 4), Map.entry('\'', 2), Map.entry('(', 4),
            Map.entry(')', 4), Map.entry('*', 4), Map.entry(',', 2), Map.entry('.', 2), Map.entry(':', 2),
            Map.entry(';', 2), Map.entry('<', 5), Map.entry('>', 5), Map.entry('@', 7), Map.entry('I', 4),
            Map.entry('[', 4), Map.entry(']', 4), Map.entry('`', 3), Map.entry('f', 5), Map.entry('i', 2),
            Map.entry('k', 5), Map.entry('l', 3), Map.entry('t', 4), Map.entry('{', 4), Map.entry('|', 2),
            Map.entry('}', 4), Map.entry('~', 7));

    /** What {@code Font.width} returns for plain text in the default font. */
    private static int width(String text) {
        int total = 0;
        for (char letter : text.toCharArray()) {
            // Outside printable ASCII the glyph comes from another sheet this table does not know, so
            // refuse to guess rather than pass a line on a made-up width.
            assertTrue(letter >= ' ' && letter <= '~',
                    "\"" + text + "\" has a character this test cannot measure: U+"
                            + Integer.toHexString(letter));
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
    private static String text(JsonObject lang, String key, Object... args) {
        String full = AeroWarptics.MODID + "." + key;
        assertTrue(lang.has(full), "en_us.json has no " + full);
        return String.format(lang.get(full).getAsString(), args);
    }

    /** The widest string a formatter produces across every order of magnitude it will be handed. */
    private static String widest(java.util.function.DoubleFunction<String> format, double ceiling) {
        String widest = "";
        for (double value = 1.0D; value <= ceiling; value *= 10.0D) {
            // The nines are the wide end of each decade, and the ones just past a unit change are the
            // other: "999.9 km" and "1,000 km" are not the same width.
            for (double sample : new double[]{value, value * 9.999D, value * 9.9999D}) {
                String shown = format.apply(sample);
                if (width(shown) > width(widest)) {
                    widest = shown;
                }
            }
        }
        return widest;
    }

    private static void fits(List<String> problems, String where, String text, int room) {
        if (width(text) > room) {
            problems.add(where + ": \"" + text + "\" is " + width(text) + "px in " + room + "px");
        }
    }

    private static void assertNoProblems(List<String> problems) {
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static final String DISTANCE = widest(AWLang::distance, 1.0e8D);
    private static final String COUNT = widest(AWLang::count, 1.0e8D);

    /** No button label is wide enough that vanilla scrolls it instead of drawing it still. */
    @Test
    void everyButtonLabelFitsItsButton() {
        JsonObject lang = lang();
        AWLayouts.Console console = AWLayouts.console();
        int room = console.buttonRoom();
        List<String> problems = new ArrayList<>();

        for (String key : List.of("gui.rift_navigation.abort", "gui.rift_navigation.close", "gui.rift_drive.disengage",
                "gui.rift_drive.clearance_on", "gui.rift_drive.clearance_off")) {
            fits(problems, key, text(lang, key), room);
        }
        for (DriveMode mode : DriveMode.values()) {
            fits(problems, "mode " + mode, text(lang, mode.translationKey()), room);
        }
        // The bow button names the setting and the compass letter it resolves to, or a dash off a ship.
        for (DriveHeading heading : DriveHeading.values()) {
            for (String letter : List.of("N", "E", "S", "W", "-")) {
                fits(problems, "bow " + heading,
                        text(lang, "gui.rift_navigation.bow", text(lang, heading.translationKey()), letter), room);
            }
        }
        assertNoProblems(problems);
    }

    /** Every checked condition is read whole, in either list - never cut off with an ellipsis. */
    @Test
    void everyRequirementFitsItsLine() {
        JsonObject lang = lang();
        AWLayouts.Console console = AWLayouts.console();
        List<String> problems = new ArrayList<>();
        for (String key : List.of("vessel", "fastened", "rotation", "charge", "course", "reachable", "signal",
                "signal_disabled", "cruise_reach", "cruise_signal")) {
            fits(problems, "req." + key, text(lang, "gui.rift_drive.req." + key), console.requirementRoom());
        }
        // And the line under them. A course's own name is whatever a player called an anchor, so it is
        // trimmed when it must be; the lines this mod writes itself are not allowed to be.
        int room = console.courseRoom();
        fits(problems, "no course", text(lang, "gui.astrolabe.no_course"), room);
        // What ties a ship down takes that line while it does, whichever of them it is.
        for (Fastening.Kind kind : Fastening.Kind.values()) {
            fits(problems, "held by " + kind.getSerializedName(),
                    text(lang, "gui.rift_drive.held_by", text(lang, kind.translationKey())), room);
        }
        fits(problems, "cruise reach", text(lang, "gui.rift_drive.cruise_reach", DISTANCE), room);
        fits(problems, "cruised", text(lang, "gui.rift_drive.cruised", DISTANCE, DISTANCE), room);
        // A creative drive's, which have no second distance to print.
        fits(problems, "cruise reach infinite", text(lang, "gui.rift_drive.cruise_reach_infinite"), room);
        fits(problems, "cruised infinite", text(lang, "gui.rift_drive.cruised_infinite", DISTANCE), room);
        assertNoProblems(problems);
    }

    /**
     * Each readout's label and its widest figure share the line with the gap
     * {@code AWScreenStyle.readout} keeps between them.
     */
    @Test
    void everyReadoutFitsItsLine() {
        JsonObject lang = lang();
        int room = AWLayouts.console().readoutRoom();
        int gap = 8;
        List<String> problems = new ArrayList<>();
        Map<String, String> readouts = Map.of(
                "gui.rift_drive.speed", "256 / 256",
                "gui.rift_drive.stress", "9999 su",
                "gui.rift_navigation.range", DISTANCE,
                "gui.rift_drive.ship_range", DISTANCE,
                "gui.rift_drive.mass", COUNT,
                "gui.rift_navigation.charge", "100%",
                "gui.rift_navigation.cooldown_short", "999s");
        for (Map.Entry<String, String> readout : readouts.entrySet()) {
            String label = text(lang, readout.getKey());
            fits(problems, readout.getKey(), label + " ".repeat(gap / 4) + readout.getValue(), room);
        }
        // A creative drive reads "Infinite" for its range, its ship's range and its charge.
        for (String key : List.of("gui.rift_navigation.range", "gui.rift_drive.ship_range",
                "gui.rift_navigation.charge")) {
            fits(problems, key + " (creative)",
                    text(lang, key) + " ".repeat(gap / 4) + text(lang, "gui.infinite"), room);
        }
        // And a ship too heavy for its drive to throw at all says so in place of a distance.
        fits(problems, "gui.rift_drive.ship_range (too heavy)", text(lang, "gui.rift_drive.ship_range")
                + " ".repeat(gap / 4) + text(lang, "gui.rift_drive.ship_range_none"), room);
        assertNoProblems(problems);
    }

    /**
     * The header's subtitle - the tier - has room beside the longest status that can share its line, and
     * every caption over the bar band fits across it.
     */
    @Test
    void theHeaderAndTheBarCaptionsFit() {
        JsonObject lang = lang();
        AWLayouts.Console console = AWLayouts.console();
        List<String> problems = new ArrayList<>();

        int gap = 6;
        List<String> statuses = new ArrayList<>();
        for (RiftDriveState state : RiftDriveState.values()) {
            statuses.add(text(lang, state.translationKey()));
        }
        statuses.add(text(lang, "drive.state.cruising"));
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            for (String status : statuses) {
                int room = console.header().width() - width(status) - gap;
                fits(problems, "subtitle beside " + status, text(lang, tier.translationKey()), room);
            }
        }
        fits(problems, "the unnamed ship beside the title", text(lang, "gui.rift_navigation.unnamed_ship"),
                console.header().width() - width(text(lang, "gui.rift_drive.title")) - gap);

        int bar = console.bar().width();
        for (String key : List.of("gui.rift_navigation.charge", "gui.rift_drive.spin", "gui.rift_drive.dropping_out",
                "gui.rift_drive.terrain_ahead", "gui.rift_drive.loading_ahead")) {
            fits(problems, key, text(lang, key), bar);
        }
        fits(problems, "cruise speed", text(lang, "gui.rift_drive.cruise_speed", COUNT), bar);
        assertNoProblems(problems);
    }

    /**
     * The console fits the virtual screen Auto GUI scale guarantees without being scaled at all, so the
     * scaling {@code AWWindowScreen} does is only ever for a GUI scale set larger than Auto would pick -
     * and there it brings every edge back on.
     */
    @Test
    void theConsoleFitsTheAutoScaleFloorAndScalesOntoAnythingSmaller() {
        int windowWidth = AWLayouts.CONSOLE_WIDTH;
        int windowHeight = AWLayouts.CONSOLE_HEIGHT;
        assertTrue(AWLayout.fitScale(320, 240, windowWidth, windowHeight) == 1.0F,
                "the console needs scaling even at the 320x240 Auto guarantees");
        // A 1280x720 window at GUI scale 4, and a 1366x768 laptop at the same.
        for (int[] screen : new int[][]{{320, 180}, {341, 192}}) {
            float scale = AWLayout.fitScale(screen[0], screen[1], windowWidth, windowHeight);
            assertTrue(Math.round(windowHeight * scale) <= screen[1] && Math.round(windowWidth * scale) <= screen[0],
                    "the console still overhangs a " + screen[0] + "x" + screen[1] + " screen at scale " + scale);
        }
    }

    // ------------------------------------------------------- the gate's dial panel
    // Not the console's, but measured with the same ruler: the dial gained a button under its readings, and
    // the readings' panel gave up the room for it.

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

    /** Every passage style has a name and a line about it, and "Passage: name" sits still on its button. */
    @Test
    void theDialsPassageButtonHoldsEveryStylesName() {
        JsonObject lang = lang();
        int room = AWLayouts.dial().passageRoom();
        List<String> problems = new ArrayList<>();
        for (GatePassageStyle style : GatePassageStyle.values()) {
            String name = text(lang, style.translationKey());
            fits(problems, "passage " + style, text(lang, "gui.rift_gate.passage", name), room);
            // The line about it is the button's tooltip, which vanilla wraps: it only has to be there.
            assertTrue(!text(lang, style.aboutKey()).isBlank(), style + " has nothing to say for itself");
        }
        assertTrue(!text(lang, "gui.rift_gate.passage.hint").isBlank());
        assertNoProblems(problems);
    }

    /**
     * The dial's readings panel still holds the longest thing it can be asked to say.
     *
     * <p>The numbers are {@code RiftGateDialScreen.renderDetails}'s: a two-pixel inset, the cost and the
     * tank under it, the speed, a rule - fifty-three pixels in all, and a line more since the rails reading went in
     * under the speed (2026-10-18) - and then the status, wrapped four pixels short of the panel's own text width,
     * ten pixels a line. A refusal is the one thing on the panel a player has to read in full, and it is a sentence.
     */
    @Test
    void theDialsReadingsPanelHoldsItsLongestRefusal() {
        JsonObject lang = lang();
        AWLayouts.Dial dial = AWLayouts.dial();
        int room = dial.detail().width() - 4 - 4;
        int head = AWLayout.INSET + 53 + AWLayout.LINE;
        List<String> said = new ArrayList<>();
        for (GateFailure failure : GateFailure.values()) {
            if (failure.isFailure()) {
                said.add(text(lang, failure.translationKey()));
            }
        }
        for (String status : List.of("gui.rift_gate.ready", "gui.rift_gate.pick", "gui.rift_gate.standing_by")) {
            said.add(text(lang, status));
        }
        // What a gate's network says back, about the longest name a network or a player may have.
        String longName = "W".repeat(24);
        for (String notice : List.of(NetworkNotice.NAME_INVALID, NetworkNotice.NAME_TAKEN, NetworkNotice.NOT_MEMBER,
                NetworkNotice.NOT_OWNER, NetworkNotice.TOO_MANY, NetworkNotice.FULL, NetworkNotice.GONE,
                NetworkNotice.DISABLED)) {
            said.add(text(lang, notice, longName));
        }
        List<String> problems = new ArrayList<>();
        for (String status : said) {
            int needed = head + lines(status, room) * 10;
            if (needed > dial.detail().height()) {
                problems.add("\"" + status + "\" needs " + needed + "px of a " + dial.detail().height() + "px panel");
            }
        }
        assertNoProblems(problems);
    }
}
