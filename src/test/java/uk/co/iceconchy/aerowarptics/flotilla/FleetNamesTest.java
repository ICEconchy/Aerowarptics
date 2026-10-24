package uk.co.iceconchy.aerowarptics.flotilla;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renaming a ship from its coupler: what is set, and what is refused. */
class FleetNamesTest {

    @Test
    void formattingCodesAndControlCharactersAreTakenOut() {
        assertEquals("HMS Tern", FleetNames.clean("§cHMS §lTern"));
        assertEquals("Kittiwake", FleetNames.clean("Kittiwake"));
    }

    @Test
    void spacingIsEvenedOut() {
        assertEquals("Iron Reach", FleetNames.clean("   Iron \t  Reach  "));
        assertEquals("", FleetNames.clean("    "), "all space is no name");
        assertEquals("", FleetNames.clean(null));
    }

    @Test
    void aNameIsCutToTheLongestADisplayCanShow() {
        String cleaned = FleetNames.clean("The Unreasonably Long Name Of A Very Small Boat");
        assertTrue(cleaned.length() <= FleetNames.MAX_LENGTH);
        assertFalse(cleaned.endsWith(" "));
    }

    @Test
    void noTwoShipsInAFlotillaShareAName() {
        List<String> others = List.of("HMS Tern", "Kittiwake", "");
        assertTrue(FleetNames.taken("hms  tern", others), "case and spacing are not a different name");
        assertFalse(FleetNames.taken("Tern", others));
        assertFalse(FleetNames.taken("", others), "any number of ships may have no name");
    }
}
