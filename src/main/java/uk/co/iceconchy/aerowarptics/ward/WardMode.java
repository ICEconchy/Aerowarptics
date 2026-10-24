package uk.co.iceconchy.aerowarptics.ward;

import java.util.Locale;

/**
 * Whom a Rift Ward turns aside. Three settings, and the setting is the whole of the rule - see {@link WardAccess}.
 *
 * <p>Goes over the wire by ordinal and into a save by name: append, never insert, and never rename.
 */
public enum WardMode {

    /** Everyone. A ward just set down is this: it holds its ground against whoever comes. */
    BLOCK_ALL,
    /** Everyone but the members of the networks it names. */
    ALLOW_NETWORKS,
    /** Nobody but the members of the networks it names - a door shut on particular people. */
    BLOCK_NETWORKS;

    /** The next mode round, for the panel's button. */
    public WardMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "rift_ward.mode." + name().toLowerCase(Locale.ROOT);
    }

    /** Whether the networks a ward names mean anything in this mode. */
    public boolean usesNetworks() {
        return this != BLOCK_ALL;
    }

    public static WardMode byName(String name) {
        for (WardMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return BLOCK_ALL;
    }

    public static WardMode byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : BLOCK_ALL;
    }
}
