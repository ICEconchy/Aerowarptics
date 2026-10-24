package uk.co.iceconchy.aerowarptics.warpnet;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * What a network may be called, and who may do what to one.
 *
 * <p>Free of Minecraft, so every rule here can be tested as it stands.
 */
public final class NetworkRules {

    /** Longest name a network may have. The panels' boxes and the lists' tags are sized to it. */
    public static final int NAME_MAX = 24;

    private NetworkRules() {
    }

    /**
     * A typed name as it will be kept: trimmed, with each run of spaces inside it brought down to one.
     *
     * <p>Two names that differ only in how many spaces somebody leant on are the same name to anyone
     * reading a list, so they are made the same name before anything is compared.
     */
    public static String clean(@Nullable String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** Whether a cleaned name can be a network's: something, not too long, and nothing unprintable. */
    public static boolean validName(String cleaned) {
        if (cleaned.isEmpty() || cleaned.length() > NAME_MAX) {
            return false;
        }
        for (int index = 0; index < cleaned.length(); index++) {
            char letter = cleaned.charAt(index);
            if (letter < ' ' || letter == 127 || letter == '§') {
                return false;
            }
        }
        return true;
    }

    /** What two names are compared by: cleaned, and regardless of case. */
    public static String key(@Nullable String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    /** Whether somebody may rename a network, change who is in it, or disband it: its owner, or an operator. */
    public static boolean mayManage(WarpNetwork network, @Nullable UUID actor, boolean operator) {
        return operator || network.isOwnedBy(actor);
    }

    /** Whether somebody may put a machine of theirs on a network: anybody in it, or an operator. */
    public static boolean mayAttach(WarpNetwork network, @Nullable UUID actor, boolean operator) {
        return operator || network.has(actor);
    }

    /**
     * Whether a machine may stay on the network it says it is on: the network is still there, and the
     * machine's owner is still in it. An unowned machine only needs the network to exist.
     *
     * <p>Asked whenever a machine re-asserts itself, because a block remembers its network across an unload
     * and the network may have been disbanded, or its owner put out of it, in the meantime.
     *
     * @param network the network as the registry has it now, or {@code null} if it has gone
     */
    public static boolean mayStay(@Nullable WarpNetwork network, @Nullable UUID machineOwner) {
        return network != null && (machineOwner == null || network.has(machineOwner));
    }

    /** Whether a count has reached a cap, where a cap of nought or less is no cap. */
    public static boolean atCap(int count, int cap) {
        return cap > 0 && count >= cap;
    }
}
