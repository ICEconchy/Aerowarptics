package uk.co.iceconchy.aerowarptics.warpnet;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A destination list's network filter: what it offers, what it settles on, and what follows what. */
class NetworkFilterTest {

    private static final UUID HARBOUR = UUID.randomUUID();
    private static final UUID NORTH = UUID.randomUUID();
    private static final UUID ZEPHYR = UUID.randomUUID();
    private static final UUID GONE = UUID.randomUUID();

    private static final Map<UUID, String> NAMES = Map.of(HARBOUR, "Harbour", NORTH, "north", ZEPHYR, "Zephyr");

    private static List<NetworkOption> offered() {
        return NetworkFilter.options(Arrays.asList(ZEPHYR, null, HARBOUR, NORTH, HARBOUR, null, GONE), NAMES::get);
    }

    @Test
    void theNetworksOnOfferAreEachOneOnceByName() {
        assertEquals(List.of("Harbour", "north", "Zephyr"), offered().stream().map(NetworkOption::name).toList(),
                "repeats, destinations on no network and a network that has gone are all left out");
    }

    @Test
    void aListOfDestinationsOnNoNetworkOffersNothing() {
        assertEquals(List.of(), NetworkFilter.options(Arrays.asList(null, null), NAMES::get));
        assertEquals(List.of(), NetworkFilter.options(List.of(), NAMES::get));
    }

    /** The button goes round: everything, each network by name, and everything again - both ways. */
    @Test
    void theButtonStepsThroughEverythingAndEachNetworkAndBackRound() {
        List<NetworkOption> options = offered();
        List<UUID> forwards = new ArrayList<>();
        UUID filter = null;
        for (int press = 0; press < 4; press++) {
            filter = NetworkFilter.next(options, filter);
            forwards.add(filter);
        }
        assertEquals(Arrays.asList(HARBOUR, NORTH, ZEPHYR, null), forwards);

        List<UUID> backwards = new ArrayList<>();
        for (int press = 0; press < 4; press++) {
            filter = NetworkFilter.previous(options, filter);
            backwards.add(filter);
        }
        assertEquals(Arrays.asList(ZEPHYR, NORTH, HARBOUR, null), backwards);
    }

    @Test
    void withNothingOnOfferTheButtonStaysOnEverything() {
        assertNull(NetworkFilter.next(List.of(), null));
        assertNull(NetworkFilter.previous(List.of(), null));
    }

    /**
     * A network can be disbanded while somebody has a list filtered by it. Kept, the filter would show
     * them an empty list and a button with nothing on it to say why.
     */
    @Test
    void aFilterNoLongerOnOfferFallsBackToEverything() {
        List<NetworkOption> options = offered();
        assertEquals(NORTH, NetworkFilter.applied(options, NORTH));
        assertNull(NetworkFilter.applied(options, GONE));
        assertNull(NetworkFilter.applied(options, null));
        assertNull(NetworkFilter.applied(List.of(), NORTH));
        // And stepping on from one that has gone starts again from the first, not from nowhere.
        assertEquals(HARBOUR, NetworkFilter.next(options, GONE));
    }

    @Test
    void aNetworkIsNamedAsTheListOffersIt() {
        List<NetworkOption> options = offered();
        assertEquals("north", NetworkFilter.nameOf(options, NORTH));
        assertEquals("", NetworkFilter.nameOf(options, GONE));
        assertEquals("", NetworkFilter.nameOf(options, null));
    }
}
