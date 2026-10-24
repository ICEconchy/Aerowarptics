package uk.co.iceconchy.aerowarptics.flotilla;

import org.jetbrains.annotations.Nullable;

/**
 * What a coupler has to tell the player standing at it about something they just asked for - a slot that
 * would not move, a flagship that would not take them, a name somebody else has.
 *
 * <p>Said on the screen they asked from, in its panel, and not in the action bar. A coupler's screen dims
 * the whole game behind it, the action bar included, so a refusal said there was never seen: a slot marker
 * dragged somewhere it may not go simply jumped back, with no word as to why.
 *
 * @param key its translation key, without the mod's prefix
 * @param arg a ship's name, raw: blank for an unnamed one, which the client names in the player's own
 *            language, the way the rest of the panel's names are sent
 */
public record CouplerNotice(String key, String arg) {

    public static final CouplerNotice NONE = new CouplerNotice("", "");

    /** A slot that could not be worked out: a ship out of contact, or a slot that is no place at all. */
    public static final String SLOT_UNREAD = "flotilla.slot_unread";
    /** A slot that would bring its ship within reach of the flagship. */
    public static final String SLOT_NEAR_FLAGSHIP = "flotilla.slot_refused.flagship";
    /** A slot that would bring its ship within reach of another escort, which it names. */
    public static final String SLOT_NEAR_SHIP = "flotilla.slot_refused.ship";
    /** A name another ship in the flotilla already has. */
    public static final String NAME_TAKEN = "flotilla.name_refused";

    /** Every key a notice is made from here, for the test that holds each to a translation. */
    public static final java.util.List<String> KEYS = java.util.List.of(SLOT_UNREAD, SLOT_NEAR_FLAGSHIP,
            SLOT_NEAR_SHIP, NAME_TAKEN);

    public CouplerNotice {
        key = key == null ? "" : key;
        arg = arg == null ? "" : arg;
    }

    public static CouplerNotice of(String key) {
        return new CouplerNotice(key, "");
    }

    public static CouplerNotice of(String key, @Nullable String arg) {
        return new CouplerNotice(key, arg);
    }

    public boolean present() {
        return !key.isEmpty();
    }
}
