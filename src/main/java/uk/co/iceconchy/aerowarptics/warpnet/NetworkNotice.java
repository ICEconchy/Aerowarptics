package uk.co.iceconchy.aerowarptics.warpnet;

import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a network has to tell the player who just asked something of it - a name somebody else has, a
 * player who is not online, a member added.
 *
 * <p>Said on the screen they asked from, as a Rift Coupler's notices are and for the same reason: a
 * panel that stays open dims the action bar behind it, so a refusal said there is never seen.
 *
 * @param key     its translation key, without the mod's prefix; blank for nothing to say
 * @param arg     the name the notice is about - a network's or a player's - raw
 * @param refused whether this is a refusal, so the order it answers was not carried out
 */
public record NetworkNotice(String key, String arg, boolean refused) {

    public static final NetworkNotice NONE = new NetworkNotice("", "", false);

    /** A name that is empty, too long, or holds something that cannot be drawn. */
    public static final String NAME_INVALID = "network.notice.name_invalid";
    /** A name another network already has, which it names. */
    public static final String NAME_TAKEN = "network.notice.name_taken";
    /** A network the player is not in, which it names. */
    public static final String NOT_MEMBER = "network.notice.not_member";
    /** Something only the network's owner may do. */
    public static final String NOT_OWNER = "network.notice.not_owner";
    /** One network more than a player may own on this server. */
    public static final String TOO_MANY = "network.notice.too_many";
    /** One member more than a network may have on this server. */
    public static final String FULL = "network.notice.full";
    /** A player who is not online to be added, whom it names. */
    public static final String NO_PLAYER = "network.notice.no_player";
    /** A player who is in the network already, whom it names. */
    public static final String ALREADY_MEMBER = "network.notice.already_member";
    /** An owner trying to leave the network they own. */
    public static final String OWNER_STAYS = "network.notice.owner_stays";
    /** A network that has gone since the panel was drawn. */
    public static final String GONE = "network.notice.gone";
    /** Networks switched off in the server's config. */
    public static final String DISABLED = "network.notice.disabled";
    /** A member added, whom it names. */
    public static final String ADDED = "network.notice.added";
    /** A member removed, whom it names. */
    public static final String REMOVED = "network.notice.removed";
    /** A network renamed, to the name it gives. */
    public static final String RENAMED = "network.notice.renamed";

    /** Every key a notice is made from here, for the test that holds each to a translation. */
    public static final List<String> KEYS = List.of(NAME_INVALID, NAME_TAKEN, NOT_MEMBER, NOT_OWNER, TOO_MANY,
            FULL, NO_PLAYER, ALREADY_MEMBER, OWNER_STAYS, GONE, DISABLED, ADDED, REMOVED, RENAMED);

    /** Longest key and longest name the wire carries. Every key is a short one of this mod's own. */
    private static final int KEY_LENGTH = 64;
    private static final int ARG_LENGTH = 64;

    public NetworkNotice {
        key = key == null ? "" : key;
        arg = arg == null ? "" : arg;
    }

    public static NetworkNotice refusal(String key) {
        return new NetworkNotice(key, "", true);
    }

    public static NetworkNotice refusal(String key, @Nullable String arg) {
        return new NetworkNotice(key, arg, true);
    }

    public static NetworkNotice done(String key, @Nullable String arg) {
        return new NetworkNotice(key, arg, false);
    }

    public boolean present() {
        return !key.isEmpty();
    }

    // ----------------------------------------------------------------- wire

    public static void encode(FriendlyByteBuf buf, NetworkNotice notice) {
        buf.writeUtf(notice.key.length() > KEY_LENGTH ? "" : notice.key, KEY_LENGTH);
        buf.writeUtf(notice.arg.length() > ARG_LENGTH ? notice.arg.substring(0, ARG_LENGTH) : notice.arg,
                ARG_LENGTH);
        buf.writeBoolean(notice.refused);
    }

    public static NetworkNotice decode(FriendlyByteBuf buf) {
        return new NetworkNotice(buf.readUtf(KEY_LENGTH), buf.readUtf(ARG_LENGTH), buf.readBoolean());
    }
}
