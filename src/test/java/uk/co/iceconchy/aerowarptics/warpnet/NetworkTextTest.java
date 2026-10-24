package uk.co.iceconchy.aerowarptics.warpnet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
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
 * That everything a network says on a screen is translated, and fits where it is said.
 *
 * <p>{@code CouplerTextTest}'s two reasons, for networks. A notice is keyed on the server and shown by a
 * client that translates whatever it is sent, so a missing one is a raw key on the info line and the
 * source scan that finds the rest of the mod's missing keys cannot see it. And a button's label that is
 * a word too long does not look broken in a screenshot - vanilla scrolls it back and forth in the game.
 * The widths are the default font's real advances, the table that test uses.
 */
class NetworkTextTest {

    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    private static final Map<Character, Integer> ADVANCE = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry('"', 4), Map.entry('\'', 2), Map.entry('(', 4),
            Map.entry(')', 4), Map.entry('*', 4), Map.entry(',', 2), Map.entry('.', 2), Map.entry(':', 2),
            Map.entry(';', 2), Map.entry('<', 5), Map.entry('>', 5), Map.entry('@', 7), Map.entry('I', 4),
            Map.entry('[', 4), Map.entry(']', 4), Map.entry('`', 3), Map.entry('f', 5), Map.entry('i', 2),
            Map.entry('k', 5), Map.entry('l', 3), Map.entry('t', 4), Map.entry('{', 4), Map.entry('|', 2),
            Map.entry('}', 4), Map.entry('~', 7));

    /** The widest a player's name gets: sixteen characters, none of them narrow. */
    private static final String PLAYER = "W".repeat(16);

    /** The widest a network's name gets. */
    private static final String NETWORK = "W".repeat(NetworkRules.NAME_MAX);

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
        String full = "aerowarptics." + key;
        assertTrue(lang.has(full), "en_us.json has no " + full);
        return String.format(lang.get(full).getAsString(), args);
    }

    private static void fits(List<String> problems, String label, Rect button) {
        int room = button.width() - AWLayouts.BUTTON_TEXT_INSET * 2;
        if (width(label) > room) {
            problems.add("\"" + label + "\" is " + width(label) + "px on a " + room + "px button");
        }
    }

    @Test
    void everyNoticeANetworkCanGiveIsTranslated() {
        JsonObject lang = lang();
        for (String key : NetworkNotice.KEYS) {
            assertTrue(lang.has("aerowarptics." + key), "en_us.json has no aerowarptics." + key);
        }
    }

    /**
     * The members panel says what came of an order on one line, cut short if it will not fit - and a
     * refusal cut short is a refusal nobody can read. Each is measured with the longest name it can carry.
     */
    @Test
    void everyNoticeTheMembersPanelShowsFitsItsLine() {
        JsonObject lang = lang();
        int room = AWLayouts.network().info().width();
        Map<String, String> worst = Map.ofEntries(
                Map.entry(NetworkNotice.NAME_INVALID, ""), Map.entry(NetworkNotice.NAME_TAKEN, NETWORK),
                Map.entry(NetworkNotice.NOT_OWNER, ""), Map.entry(NetworkNotice.FULL, ""),
                Map.entry(NetworkNotice.NO_PLAYER, PLAYER), Map.entry(NetworkNotice.ALREADY_MEMBER, PLAYER),
                Map.entry(NetworkNotice.OWNER_STAYS, ""), Map.entry(NetworkNotice.GONE, ""),
                Map.entry(NetworkNotice.ADDED, PLAYER), Map.entry(NetworkNotice.REMOVED, PLAYER),
                Map.entry(NetworkNotice.RENAMED, NETWORK));
        List<String> problems = new ArrayList<>();
        worst.forEach((key, name) -> {
            String said = text(lang, key, name);
            if (width(said) > room) {
                problems.add("\"" + said + "\" (" + key + ") is " + width(said) + "px on a " + room + "px line");
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyButtonLabelFitsItsButton() {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();

        AWLayouts.Dial dial = AWLayouts.dial();
        fits(problems, text(lang, "gui.rift_gate.dial"), dial.dial());
        fits(problems, text(lang, "gui.rift_gate.hang_up"), dial.dial());
        // The longest thing on the dial's footer: a third mode is a longer word than either of the first two.
        for (WarpAnchorAccess access : WarpAnchorAccess.values()) {
            fits(problems, text(lang, "gui.rift_gate.access", text(lang, access.translationKey())), dial.access());
        }
        fits(problems, text(lang, "gui.network.members"), dial.members());

        AWLayouts.Anchor anchor = AWLayouts.anchor();
        fits(problems, text(lang, "gui.network.members"), anchor.members());
        for (WarpAnchorAccess access : WarpAnchorAccess.values()) {
            fits(problems, text(lang, access.translationKey()), anchor.access());
        }

        AWLayouts.Network panel = AWLayouts.network();
        fits(problems, text(lang, "gui.network.add"), panel.addButton());
        fits(problems, text(lang, "gui.network.remove"), panel.remove());
        for (String key : List.of("leave", "disband", "confirm_leave", "confirm_disband")) {
            fits(problems, text(lang, "gui.network." + key), panel.leave());
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The filter strip is drawn between two arrows, eight pixels each side, on the narrower of two lists. */
    @Test
    void theFilterStripHoldsItsOwnWords() {
        JsonObject lang = lang();
        int room = Math.min(AWLayouts.chart().filter().width(), AWLayouts.dial().filter().width()) - 16;
        String all = text(lang, "gui.network.filter_all");
        assertTrue(width(all) <= room, "\"" + all + "\" is " + width(all) + "px on a " + room + "px strip");
    }
}
