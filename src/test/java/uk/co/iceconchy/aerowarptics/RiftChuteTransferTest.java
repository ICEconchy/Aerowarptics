package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.chute.ChuteTransfer;
import uk.co.iceconchy.aerowarptics.chute.ChuteTransfer.Plan;
import uk.co.iceconchy.aerowarptics.chute.ChuteTransfer.Reason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a Rift Chute never invents or loses an item.
 *
 * <p>This is the failure the block has to be proved not to have. Items crossing between two
 * sub-levels are the sort of thing that goes wrong quietly - nobody notices two spare ingots, and
 * nobody can prove where a missing one went - so the arithmetic that decides how many move is kept
 * free of Minecraft and swept here across every combination that matters, rather than being watched
 * once in a running game and declared fine.
 */
class RiftChuteTransferTest {

    private static final int COST = 2;
    private static final int BATCH = 16;

    private static Plan plan(int available, int room, int essence) {
        return ChuteTransfer.plan(available, room, essence, COST, BATCH, true, true, false);
    }

    /**
     * A transfer never moves more than the sender has, the receiver will take, or the tank can pay
     * for - swept across the whole space rather than spot-checked.
     */
    @Test
    void aTransferNeverExceedsAnyOfItsThreeLimits() {
        for (int available = 0; available <= 64; available++) {
            for (int room = 0; room <= 64; room += 3) {
                for (int essence = 0; essence <= 80; essence += 7) {
                    Plan plan = plan(available, room, essence);
                    String where = "available=" + available + " room=" + room + " essence=" + essence;
                    assertTrue(plan.count() >= 0, where + " moved a negative count");
                    assertTrue(plan.count() <= available, where + " moved more than it had");
                    assertTrue(plan.count() <= room, where + " moved more than there was room for");
                    assertTrue(plan.count() <= BATCH, where + " exceeded the batch limit");
                    assertTrue(plan.essence() <= essence, where + " spent essence it did not have");
                }
            }
        }
    }

    /** Essence is charged per item, exactly - never rounded in the player's favour or the server's. */
    @Test
    void essenceIsChargedPerItemMoved() {
        for (int available = 1; available <= 40; available++) {
            Plan plan = plan(available, 64, 1_000);
            assertEquals(plan.count() * COST, plan.essence(),
                    "charged the wrong amount for " + plan.count() + " items");
        }
    }

    /** A transfer that moves nothing costs nothing. An idle chute must not drain its own tank. */
    @Test
    void movingNothingCostsNothing() {
        assertEquals(0, plan(0, 64, 1_000).essence(), "an empty chute was charged");
        assertEquals(0, plan(10, 0, 1_000).essence(), "a blocked chute was charged");
        assertEquals(0, plan(10, 64, 0).essence(), "a dry chute was charged");
        assertFalse(plan(0, 64, 1_000).moves());
    }

    /** The tank bounds the batch: eight items' worth of essence sends eight items, not nine. */
    @Test
    void theTankBoundsHowManyCross() {
        Plan plan = ChuteTransfer.plan(64, 64, 8 * COST, COST, BATCH, true, true, false);
        assertEquals(8, plan.count(), "spent more essence than it held");
        assertEquals(8 * COST, plan.essence());
    }

    /** With the cost configured to zero, chutes run free rather than refusing to run at all. */
    @Test
    void aZeroCostChuteStillMoves() {
        Plan plan = ChuteTransfer.plan(64, 64, 0, 0, BATCH, true, true, false);
        assertTrue(plan.moves(), "a free chute refused to move anything");
        assertEquals(0, plan.essence());
        assertTrue(ChuteTransfer.riftOpen(0, 0), "a free chute's rift was collapsed");
    }

    /**
     * Every refusal names the right cause.
     *
     * <p>The panel shows this reason and nothing else, so a wrong one sends a player to fix the wrong
     * thing - which is worse than no message at all.
     */
    @Test
    void eachRefusalNamesItsOwnCause() {
        assertEquals(Reason.EMPTY, plan(0, 64, 100).reason());
        assertEquals(Reason.UNBOUND,
                ChuteTransfer.plan(10, 64, 100, COST, BATCH, false, true, false).reason());
        assertEquals(Reason.WARPING,
                ChuteTransfer.plan(10, 64, 100, COST, BATCH, true, true, true).reason());
        assertEquals(Reason.PARTNER_ABSENT,
                ChuteTransfer.plan(10, 64, 100, COST, BATCH, true, false, false).reason());
        assertEquals(Reason.PARTNER_FULL, plan(10, 0, 100).reason());
        assertEquals(Reason.NO_ESSENCE, plan(10, 64, COST - 1).reason());
        assertEquals(Reason.READY, plan(10, 64, 100).reason());
    }

    /**
     * An empty chute reports itself idle rather than faulty, whatever else is wrong with it.
     *
     * <p>Order of checks, tested deliberately: a chute with nothing in it and no partner is not
     * broken, and a player told "pick a chute to send to" by a chute that has nothing to send would
     * go and configure something that was already fine.
     */
    @Test
    void anEmptyChuteIsIdleRatherThanFaulty() {
        assertEquals(Reason.EMPTY,
                ChuteTransfer.plan(0, 0, 0, COST, BATCH, false, false, true).reason());
    }

    /** A warping ship holds cargo rather than dropping it, even when everything else is ready. */
    @Test
    void aWarpingShipHoldsItsCargo() {
        Plan plan = ChuteTransfer.plan(64, 64, 1_000, COST, BATCH, true, true, true);
        assertFalse(plan.moves(), "items crossed while the ship was inside the fold");
        assertEquals(0, plan.essence(), "essence was spent on a transfer that did not happen");
    }

    /** The rift stands open on a chute that could pay for one more item, and collapses when it cannot. */
    @Test
    void theRiftReflectsWhetherOneMoreItemCouldCross() {
        assertFalse(ChuteTransfer.riftOpen(COST - 1, COST), "the rift held open on an empty tank");
        assertTrue(ChuteTransfer.riftOpen(COST, COST), "the rift collapsed with essence to spend");
    }

    /**
     * A batch limit of one still moves one.
     *
     * <p>The clamp uses {@code Math.max(1, batchLimit)}, so a misconfigured zero cannot silently
     * stop every chute on the server.
     */
    @Test
    void aDegenerateBatchLimitStillMovesSomething() {
        assertEquals(1, ChuteTransfer.plan(64, 64, 1_000, COST, 1, true, true, false).count());
        assertEquals(1, ChuteTransfer.plan(64, 64, 1_000, COST, 0, true, true, false).count());
    }

    /**
     * A chute that cannot pay can still receive.
     *
     * <p>This is what makes an unattended drop-off work: essence is charged to whichever end sends,
     * so the far end of a line needs no supply piped to it. The sending side is the only one whose
     * tank is ever consulted, which this pins by giving the sender plenty and asking nothing of the
     * receiver at all.
     */
    @Test
    void onlyTheSendingEndNeedsEssence() {
        Plan plan = ChuteTransfer.plan(16, 64, 1_000, COST, BATCH, true, true, false);
        assertTrue(plan.moves(), "a funded sender refused to send");
        assertEquals(16 * COST, plan.essence(), "the sender was not charged for what it sent");
    }
}
