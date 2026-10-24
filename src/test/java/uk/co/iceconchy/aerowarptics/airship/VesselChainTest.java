package uk.co.iceconchy.aerowarptics.airship;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as one vessel, by following what each part says it is joined to.
 *
 * <p>The failure this guards refuses a warp with nothing in the way: a part left out of the chain is
 * "another ship", and it is standing inside the hull's own box, so every bearing reads as obstructed.
 * A part is a name here, since the walk never asks what it is walking.
 */
class VesselChainTest {

    /** A stand-in for a sub-level: compared by identity, as the real ones are. */
    private static final class Part {
        private final String name;

        private Part(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final Map<Part, List<Part>> joints = new HashMap<>();

    private void join(Part from, Part... to) {
        joints.computeIfAbsent(from, part -> new ArrayList<>()).addAll(Arrays.asList(to));
    }

    private List<Part> chain(Part seed) {
        return VesselChain.from(seed, joints::get);
    }

    @Test
    void aHullOnItsOwnIsTheWholeVessel() {
        Part hull = new Part("hull");
        assertEquals(List.of(hull), chain(hull));
    }

    @Test
    void aTurretOnASwivelBearingIsPartOfTheShip() {
        // The bearing aboard the hull names its plate's sub-level.
        Part hull = new Part("hull");
        Part turret = new Part("turret");
        join(hull, turret);
        assertEquals(List.of(hull, turret), chain(hull));
    }

    @Test
    void theChainIsFoundFromEitherEnd() {
        // The plate names its bearing's sub-level, so a drive on the turret finds the hull under it.
        Part hull = new Part("hull");
        Part turret = new Part("turret");
        join(hull, turret);
        join(turret, hull);
        assertEquals(List.of(turret, hull), chain(turret));
    }

    @Test
    void aBearingOnABearingIsFollowedAllTheWayOut() {
        Part hull = new Part("hull");
        Part turret = new Part("turret");
        Part barrel = new Part("barrel");
        Part tender = new Part("tender");
        join(hull, turret, tender);
        join(turret, barrel);
        assertEquals(List.of(hull, turret, tender, barrel), chain(hull));
    }

    @Test
    void aRingOfJointsIsWalkedOnce() {
        Part a = new Part("a");
        Part b = new Part("b");
        Part c = new Part("c");
        join(a, b);
        join(b, c);
        join(c, a, b);
        assertEquals(List.of(a, b, c), chain(a));
    }

    @Test
    void aPartNamedTwiceIsCountedOnce() {
        // Sable's walk and the plot's own machines both name the turret; it is still one turret.
        Part hull = new Part("hull");
        Part turret = new Part("turret");
        join(hull, turret, turret, hull, turret);
        assertEquals(List.of(hull, turret), chain(hull));
    }

    @Test
    void aJointToNothingIsSkipped() {
        // A bearing whose plate is not loaded answers null, or a list with a hole in it.
        Part hull = new Part("hull");
        Part turret = new Part("turret");
        join(hull, null, turret, null);
        assertEquals(List.of(hull, turret), chain(hull));
        assertEquals(List.of(turret), chain(turret), "a part with nothing to say is still itself");
    }

    @Test
    void partsAreToldApartByIdentityNotByLookingAlike() {
        // Two different ships that would compare equal must not be taken for one another.
        Part hull = new Part("ship");
        Part stranger = new Part("ship");
        List<Part> chain = chain(hull);
        assertEquals(1, chain.size());
        assertSame(hull, chain.getFirst());
        assertTrue(chain.stream().noneMatch(part -> part == stranger));
    }

    @Test
    void aShipMooredAlongsideIsNotPartOfTheVessel() {
        // Nothing joins them, so the clearance checks are right to call it another ship.
        Part hull = new Part("hull");
        Part turret = new Part("turret");
        Part neighbour = new Part("neighbour");
        Part neighboursTurret = new Part("neighbour's turret");
        join(hull, turret);
        join(neighbour, neighboursTurret);
        assertEquals(List.of(hull, turret), chain(hull));
    }
}
