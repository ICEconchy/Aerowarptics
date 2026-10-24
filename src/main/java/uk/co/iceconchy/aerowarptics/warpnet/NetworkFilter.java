package uk.co.iceconchy.aerowarptics.warpnet;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A destination list's network filter, as arithmetic: what it offers, what is chosen, what comes next.
 *
 * <p>The filter is one of the networks on offer or nothing, and nothing means every destination. It is
 * applied on the server, before a list is cut to what one packet can carry, so that on a server with more
 * destinations than that a filtered list can reach the ones the cut would otherwise hide.
 *
 * <p>Free of Minecraft, and shared: the server uses it to decide what a list is filtered by and the
 * screens use it to step the button, so the two cannot disagree about what follows what.
 */
public final class NetworkFilter {

    /** Orders the networks a list offers: by name, whatever the case, and then by id so the order is total. */
    public static final Comparator<NetworkOption> BY_NAME =
            Comparator.comparing(NetworkOption::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(option -> option.id().toString());

    private NetworkFilter() {
    }

    /**
     * The networks a list of destinations can be filtered by: each one a destination is on, once, by name.
     *
     * @param networks the network of every destination in the list, nulls and repeats included
     * @param names    a network's name by its id; one with no name is one that has gone, and is left out
     */
    public static List<NetworkOption> options(Collection<UUID> networks, Function<UUID, String> names) {
        Set<UUID> distinct = new LinkedHashSet<>(networks);
        distinct.remove(null);
        List<NetworkOption> options = new ArrayList<>();
        for (UUID network : distinct) {
            String name = names.apply(network);
            if (name != null && !name.isBlank()) {
                options.add(new NetworkOption(network, name));
            }
        }
        options.sort(BY_NAME);
        return options;
    }

    /**
     * The filter a list is really under: the one asked for if it is still on offer, and none if it is not.
     *
     * <p>A network can be disbanded, or its last visible destination broken, while somebody has a list
     * filtered by it. Kept, the filter would show them an empty list with nothing on the button to say why.
     */
    @Nullable
    public static UUID applied(List<NetworkOption> options, @Nullable UUID wanted) {
        return indexOf(options, wanted) < 0 ? null : wanted;
    }

    /** The filter after this one: every destination, then each network in turn, then round again. */
    @Nullable
    public static UUID next(List<NetworkOption> options, @Nullable UUID current) {
        return step(options, current, 1);
    }

    /** The filter before this one. */
    @Nullable
    public static UUID previous(List<NetworkOption> options, @Nullable UUID current) {
        return step(options, current, -1);
    }

    /** A network's name as the list offers it, or blank for one it does not offer. */
    public static String nameOf(List<NetworkOption> options, @Nullable UUID network) {
        int index = indexOf(options, network);
        return index < 0 ? "" : options.get(index).name();
    }

    private static UUID step(List<NetworkOption> options, @Nullable UUID current, int by) {
        // One more stop than there are networks: "all of them" sits before the first.
        int stops = options.size() + 1;
        int stop = Math.floorMod(indexOf(options, current) + 1 + by, stops);
        return stop == 0 ? null : options.get(stop - 1).id();
    }

    private static int indexOf(List<NetworkOption> options, @Nullable UUID network) {
        if (network == null) {
            return -1;
        }
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id().equals(network)) {
                return index;
            }
        }
        return -1;
    }
}
