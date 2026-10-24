package uk.co.iceconchy.aerowarptics.bond;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.VesselChain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who stands in a bond, and what a vessel is once bonds are followed.
 *
 * <p>The failure this guards is silent in both directions: a section missing from its bond is left behind
 * at a warp with nothing said, and a block still listed after it has gone is a section of somebody else's
 * ship carried off with this one.
 */
class BondLedgerTest {

    /** A stand-in for a bond block: compared by identity, as block entities are. */
    private record Block(String section) {
    }

    private final BondLedger<Block> ledger = new BondLedger<>();
    private final UUID red = UUID.randomUUID();
    private final UUID blue = UUID.randomUUID();

    @Test
    void everybodyInABondIsFoundAndNobodyFromAnother() {
        Block bow = new Block("bow");
        Block stern = new Block("stern");
        Block stranger = new Block("stranger");
        ledger.join(red, bow);
        ledger.join(red, stern);
        ledger.join(blue, stranger);

        assertEquals(2, ledger.members(red).size());
        assertTrue(ledger.members(red).contains(bow));
        assertTrue(ledger.members(red).contains(stern));
        assertFalse(ledger.members(red).contains(stranger));
    }

    @Test
    void joiningTwiceIsJoiningOnce() {
        Block bow = new Block("bow");
        ledger.join(red, bow);
        ledger.join(red, bow);
        assertEquals(1, ledger.members(red).size());
    }

    @Test
    void twoBlocksThatLookAlikeAreStillTwo() {
        // Records are equal by value; a ledger keyed by equals() would fold a reloaded block into the one
        // it replaced and then lose both when the old one left.
        Block old = new Block("bow");
        Block reloaded = new Block("bow");
        ledger.join(red, old);
        ledger.join(red, reloaded);
        ledger.leave(red, old);
        assertEquals(1, ledger.members(red).size());
        assertTrue(ledger.members(red).get(0) == reloaded);
    }

    @Test
    void aBondNobodyIsLeftInIsForgotten() {
        Block bow = new Block("bow");
        ledger.join(red, bow);
        ledger.leave(red, bow);
        assertEquals(0, ledger.bonds());
        assertTrue(ledger.members(red).isEmpty());
    }

    @Test
    void leavingABondNeverJoinedIsNothing() {
        ledger.leave(red, new Block("bow"));
        assertEquals(0, ledger.bonds());
    }

    @Test
    void membersMayLeaveWhileTheListIsWalked() {
        Block bow = new Block("bow");
        Block stern = new Block("stern");
        ledger.join(red, bow);
        ledger.join(red, stern);
        for (Block block : ledger.members(red)) {
            ledger.leave(red, block);
        }
        assertEquals(0, ledger.bonds());
    }

    @Test
    void aVesselIsEverySectionReachedThroughItsBonds() {
        // Three sections in a line: the middle one carries a bond of each colour, so the bow reaches the
        // stern through it although they share nothing themselves.
        Block bowRed = new Block("bow");
        Block midRed = new Block("mid");
        Block midBlue = new Block("mid");
        Block sternBlue = new Block("stern");
        Block ashore = new Block("pier");
        ledger.join(red, bowRed);
        ledger.join(red, midRed);
        ledger.join(blue, midBlue);
        ledger.join(blue, sternBlue);
        ledger.join(UUID.randomUUID(), ashore);

        List<String> vessel = VesselChain.<String>from("bow", section -> {
            List<String> joined = new ArrayList<>();
            for (UUID bond : List.of(red, blue)) {
                boolean aboard = ledger.members(bond).stream().anyMatch(b -> b.section().equals(section));
                if (aboard) {
                    ledger.members(bond).forEach(b -> joined.add(b.section().intern()));
                }
            }
            return joined;
        });
        assertEquals(3, vessel.size());
        assertTrue(vessel.containsAll(List.of("bow", "mid", "stern")));
        assertFalse(vessel.contains("pier"));
    }

    @Test
    void aBondHoldsOnlyWithinItsReach() {
        assertTrue(RiftBonds.within(0.0D, 256.0D));
        assertTrue(RiftBonds.within(256.0D, 256.0D));
        assertFalse(RiftBonds.within(256.5D, 256.0D));
        // A section whose pose cannot be read is not a section that is near.
        assertFalse(RiftBonds.within(Double.NaN, 256.0D));
        assertFalse(RiftBonds.within(Double.POSITIVE_INFINITY, 256.0D));
    }
}
