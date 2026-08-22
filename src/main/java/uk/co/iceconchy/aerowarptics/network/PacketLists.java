package uk.co.iceconchy.aerowarptics.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How many rows a packet may carry, and which rows survive when there are more than that.
 *
 * <p>Written after finding that both list-bearing packets encoded with a bare {@code list(256)} and
 * were fed from queries with no limit at all. {@code ByteBufCodecs.list} enforces its bound on the
 * <em>write</em> as well as the read, so the failure was not a truncated screen: it was an
 * {@code EncoderException} on the server, thrown every time anybody opened an Astrolabe, on any world
 * that had accumulated 256 visible anchors. {@code maxAnchorsPerPlayer} defaults to unlimited, so
 * nothing upstream was ever going to stop it.
 *
 * <p>The cap is applied by ranking first and cutting second, which matters more than it sounds. The
 * chart draws the list in the order the server sends it, so cutting an unsorted list would drop an
 * arbitrary quarter of somebody's destinations and say nothing - a silent wrong answer in place of a
 * loud crash, which is the worse of the two. Ranking means what survives is what a pilot would have
 * picked anyway, and the packets carry the untruncated total so the screens can admit to it.
 *
 * <p>Rows are only reordered when there are too many of them. Below the limit the order a caller
 * built is left exactly as it was: this is a safety net, not a policy about how lists are sorted.
 */
public final class PacketLists {

    /**
     * Rows one packet may carry.
     *
     * <p>Every codec bound and every truncation in this package reads this, so there is one number to
     * change and no way for a list to be capped at one figure and encoded against another.
     */
    public static final int MAX_ROWS = 256;

    /**
     * The best {@link #MAX_ROWS} rows by the given ranking, or the list untouched if it already fits.
     *
     * @param priority orders rows best-first; only consulted when the list is over the limit
     */
    public static <T> List<T> cap(List<T> rows, Comparator<? super T> priority) {
        if (rows.size() <= MAX_ROWS) {
            return rows;
        }
        List<T> ranked = new ArrayList<>(rows);
        ranked.sort(priority);
        return List.copyOf(ranked.subList(0, MAX_ROWS));
    }

    private PacketLists() {
    }
}
