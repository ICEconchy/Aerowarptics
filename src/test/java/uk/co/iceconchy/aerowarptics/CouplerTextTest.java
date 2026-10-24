package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayouts;

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
 * That every label on the Rift Coupler's two screens fits the button it is drawn on.
 *
 * <p>{@code ConsoleTextTest}'s reason, for the coupler: vanilla scrolls a button label that does not
 * fit rather than cutting it, so a label one word too long does not look broken in a screenshot - it
 * slides back and forth in the game. The widths are the default font's real advances, the same table
 * that test uses; the rooms are {@link AWLayouts}'s.
 */
class CouplerTextTest {

    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    private static final Map<Character, Integer> ADVANCE = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry('"', 4), Map.entry('\'', 2), Map.entry('(', 4),
            Map.entry(')', 4), Map.entry('*', 4), Map.entry(',', 2), Map.entry('.', 2), Map.entry(':', 2),
            Map.entry(';', 2), Map.entry('<', 5), Map.entry('>', 5), Map.entry('@', 7), Map.entry('I', 4),
            Map.entry('[', 4), Map.entry(']', 4), Map.entry('`', 3), Map.entry('f', 5), Map.entry('i', 2),
            Map.entry('k', 5), Map.entry('l', 3), Map.entry('t', 4), Map.entry('{', 4), Map.entry('|', 2),
            Map.entry('}', 4), Map.entry('~', 7));

    private static int width(String text) {
        int total = 0;
        for (char letter : text.toCharArray()) {
            assertTrue(letter >= ' ' && letter <= '~', "\"" + text + "\" has a character this test cannot measure");
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

    private static String text(JsonObject lang, String key, Object... args) {
        String full = "aerowarptics.gui.rift_coupler." + key;
        assertTrue(lang.has(full), "en_us.json has no " + full);
        return String.format(lang.get(full).getAsString(), args);
    }

    /** The room a button leaves its label before vanilla starts scrolling it. */
    private static int room(Rect button) {
        return button.width() - AWLayouts.BUTTON_TEXT_INSET * 2;
    }

    private static void fits(List<String> problems, String label, Rect button) {
        if (width(label) > room(button)) {
            problems.add("\"" + label + "\" is " + width(label) + "px on a " + room(button) + "px button");
        }
    }

    @Test
    void everyFleetBoardLabelFitsItsButton() {
        JsonObject lang = lang();
        AWLayouts.FleetBoard board = AWLayouts.fleetBoard();
        List<String> problems = new ArrayList<>();
        fits(problems, text(lang, "sound_stations"), board.sound());
        fits(problems, text(lang, "stand_down"), board.sound());
        fits(problems, text(lang, "cast_off"), board.castOff());
        fits(problems, text(lang, "fold"), board.fold());
        fits(problems, text(lang, "hold_to_fold"), board.fold());
        // Held, the Fold button says how many it will leave; the most a Singularity can lead is eight.
        fits(problems, text(lang, "leaving", 8), board.fold());
        fits(problems, text(lang, "be_escort"), board.role());
        fits(problems, text(lang, "disband"), board.role());
        fits(problems, text(lang, "auto_on"), board.autoAccept());
        fits(problems, text(lang, "auto_off"), board.autoAccept());
        fits(problems, text(lang, "beacon_fleet"), board.beacon());
        fits(problems, text(lang, "beacon_ship"), board.beacon());
        fits(problems, text(lang, "rift_one"), board.sharedRift());
        fits(problems, text(lang, "rift_each"), board.sharedRift());
        fits(problems, text(lang, "rename"), board.rename());
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyStationLabelFitsItsButton() {
        JsonObject lang = lang();
        AWLayouts.Station station = AWLayouts.station();
        List<String> problems = new ArrayList<>();
        fits(problems, text(lang, "take_station"), station.take());
        fits(problems, text(lang, "break_off"), station.breakOff());
        fits(problems, text(lang, "withdraw"), station.breakOff());
        fits(problems, text(lang, "be_flagship"), station.role());
        fits(problems, text(lang, "rename"), station.rename());
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every notice a coupler can say on its screen has a translation. They are keyed on the server and shown
     * by a client that translates whatever it is sent, so a missing one is a raw key on the info line - and
     * the source scan that catches the rest of the mod's missing keys cannot see them.
     */
    @Test
    void everyNoticeACouplerCanGiveIsTranslated() {
        JsonObject lang = lang();
        List<String> keys = new ArrayList<>(uk.co.iceconchy.aerowarptics.flotilla.CouplerNotice.KEYS);
        for (uk.co.iceconchy.aerowarptics.flotilla.JoinResult result
                : uk.co.iceconchy.aerowarptics.flotilla.JoinResult.values()) {
            keys.add(result.translationKey());
        }
        for (String key : keys) {
            assertTrue(lang.has("aerowarptics." + key), "en_us.json has no aerowarptics." + key);
        }
    }

    /**
     * A course a drive will not fly is said to be the course's doing. Both screens draw slots and a tolerance
     * round them, so a bare "Out of range" was read as the range a pilot could see - their slot's - and they
     * sat dead in the middle of it wondering what was wrong.
     */
    @Test
    void aCourseOutOfReachSaysItIsTheCourse() {
        JsonObject lang = lang();
        for (String key : List.of("flotilla.status.too_close", "flotilla.status.too_far",
                "flotilla.flagship.too_close", "flotilla.flagship.too_far")) {
            assertTrue(lang.has("aerowarptics." + key), "en_us.json has no aerowarptics." + key);
            String said = lang.get("aerowarptics." + key).getAsString();
            assertTrue(said.toLowerCase(java.util.Locale.ROOT).contains("course"),
                    "\"" + said + "\" (" + key + ") does not say it is the course that is out of reach");
        }
    }

    @Test
    void theInfoLineHoldsTheWakeAndTheEssenceSideBySide() {
        JsonObject lang = lang();
        Rect info = AWLayouts.fleetBoard().info();
        // A big formation and a full tank: the longest this line gets.
        int wake = width(text(lang, "wake", 999, 256, 999)) + 10;
        int essence = width(text(lang, "essence_line", "1,000 / 1,000 mB"));
        assertTrue(wake + essence + 8 <= info.width(), "the wake and the essence run into each other");
    }
}
