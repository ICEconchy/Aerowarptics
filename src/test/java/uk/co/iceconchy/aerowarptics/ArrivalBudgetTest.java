package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the block-check budget is tied to the ship rather than to a flat number.
 *
 * <p>The budget is a safety valve: run out and the volume could not be <em>proved</em> clear, and an
 * unproven volume is refused. As a fixed count that quietly became a size limit on ships. A large
 * hull legitimately has to prove a large volume, so past a certain size the check ran out every
 * single time and the biggest vessels were refused on every attempt - with nothing in the way, and
 * no block to name, because there was no block. Scaling the allowance with the hull is what stops
 * the valve behaving like a rule.
 */
class ArrivalBudgetTest {

    private static final double SCALE = 8.0D;         // arrivalBlockCheckScale default
    private static final long CEILING = 4_000_000L;   // maxArrivalBlockChecks default
    private static final long FLOOR = 65_536L;

    private static long budget(double hullVolume) {
        return SafeArrival.budgetFor(hullVolume, SCALE, CEILING);
    }

    /** A bigger ship gets a bigger allowance - the whole point. */
    @Test
    void theAllowanceGrowsWithTheHull() {
        assertTrue(budget(200_000.0D) > budget(20_000.0D),
                "a ten-times-larger hull was given no more allowance");
    }

    /** And it grows in proportion, not in some arbitrary step. */
    @Test
    void theAllowanceIsProportionalBetweenTheFloorAndTheCeiling() {
        // Both ends chosen to sit clear of the floor and the ceiling, where scaling actually applies.
        assertEquals(20_000.0D * SCALE, budget(20_000.0D));
        assertEquals(40_000.0D * SCALE, budget(40_000.0D));
    }

    /**
     * The ceiling still binds, so a nonsense hull cannot ask the server for unbounded work.
     *
     * <p>Scaling the budget without a cap would trade a wrong refusal for a main-thread stall, which
     * is the worse of the two.
     */
    @Test
    void theCeilingStillCapsAbsurdHulls() {
        assertEquals(CEILING, budget(1.0e12D));
        assertEquals(CEILING, budget(Double.MAX_VALUE));
    }

    /** A tiny hull still gets a workable floor rather than an allowance of nearly nothing. */
    @Test
    void aTinyHullStillGetsTheFloor() {
        assertEquals(FLOOR, budget(1.0D));
        assertEquals(FLOOR, budget(0.0D));
    }

    /** Nonsense volumes fall back to the floor rather than producing a negative or wrapped budget. */
    @Test
    void nonsenseVolumesFallBackToTheFloor() {
        assertEquals(FLOOR, budget(-1.0D));
        assertEquals(FLOOR, budget(Double.NaN));
        assertTrue(budget(Double.POSITIVE_INFINITY) <= CEILING);
    }

    /** The budget is never negative or zero, whatever it is handed. */
    @Test
    void theBudgetIsAlwaysPositive() {
        for (double volume : new double[]{-5.0D, 0.0D, 1.0D, 1_000.0D, 1.0e9D, Double.NaN}) {
            assertTrue(budget(volume) > 0L, "a budget of " + budget(volume) + " for volume " + volume);
        }
    }

    /** A ceiling set below the floor is still honoured - the cap is the operator's last word. */
    @Test
    void aCeilingBelowTheFloorStillWins() {
        assertEquals(4_096L, SafeArrival.budgetFor(1.0D, SCALE, 4_096L));
        assertEquals(4_096L, SafeArrival.budgetFor(1.0e9D, SCALE, 4_096L));
    }
}
