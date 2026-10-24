package uk.co.iceconchy.aerowarptics.flotilla;

import java.util.Collection;
import java.util.Locale;

/**
 * The rules for renaming a ship from its coupler.
 *
 * <p>A ship's name is Sable's, not this mod's - it is the sub-level's own name, shown wherever Sable or
 * anything else shows one, and usually blank. The coupler can set it because a Fleet Board full of
 * "Unnamed vessel" is no way to tell escorts apart. Two rules come with that: a name is cleaned of
 * anything that would render oddly, and no two ships in one flotilla may share one, since a roster that
 * lists "Tern" twice is a roster nobody can cast off from.
 *
 * <p>Pure, so both rules are tested directly.
 */
public final class FleetNames {

    /** Longest name a coupler will set. A Display Board row is not much wider. */
    public static final int MAX_LENGTH = 32;

    private FleetNames() {
    }

    /**
     * A name as it will be set: formatting codes and control characters removed, runs of spaces
     * collapsed, trimmed, and cut to {@link #MAX_LENGTH}. Empty means "no name".
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                // A section sign starts a formatting code; the code is the next character, and both go.
                i++;
                continue;
            }
            if (Character.isISOControl(c)) {
                continue;
            }
            if (Character.isWhitespace(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        String cleaned = out.toString();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH).trim() : cleaned;
    }

    /** How two names are compared: ignoring case and the spacing {@link #clean} evens out. */
    public static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a name is already taken by another ship in the flotilla.
     *
     * @param others every other ship's name in the flotilla; blanks never clash, being no name at all
     */
    public static boolean taken(String name, Collection<String> others) {
        String wanted = key(name);
        if (wanted.isEmpty()) {
            return false;
        }
        for (String other : others) {
            if (wanted.equals(key(other))) {
                return true;
            }
        }
        return false;
    }
}
