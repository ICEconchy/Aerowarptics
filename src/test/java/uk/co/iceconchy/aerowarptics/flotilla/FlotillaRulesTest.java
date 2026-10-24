package uk.co.iceconchy.aerowarptics.flotilla;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How many a flagship may lead, and what a fold costs each drive in it. */
class FlotillaRulesTest {

    /** The cost formula at the server's defaults for a Mk II, minus its range, for the curve's sake. */
    private static final WarpCost.Formula FORMULA =
            new WarpCost.Formula(0.2D, 0.00005D, 0.1D, 10_000.0D, 1.6D, 0.0D, 1.0e9D);

    @Test
    void theEscortCapDoublesUpTheTiers() {
        assertEquals(1, FlotillaRules.defaultEscorts(0), "Mk I");
        assertEquals(2, FlotillaRules.defaultEscorts(1), "Mk II");
        assertEquals(4, FlotillaRules.defaultEscorts(2), "Mk III");
        assertEquals(8, FlotillaRules.defaultEscorts(3), "Singularity");
        assertEquals(-1, FlotillaRules.defaultEscorts(4), "Creative is unlimited");
        assertEquals(Integer.MAX_VALUE, FlotillaRules.cap(-1));
    }

    @Test
    void aFlagshipAtItsCapTakesNoMore() {
        assertTrue(FlotillaRules.hasRoom(0, 1));
        assertFalse(FlotillaRules.hasRoom(1, 1));
        assertFalse(FlotillaRules.hasRoom(3, 0), "a cap of nought is no flotilla at all");
        assertTrue(FlotillaRules.hasRoom(10_000, -1));
    }

    @Test
    void aShipOnItsOwnPaysExactlyWhatItAlwaysDid() {
        assertEquals(5_000.0D, FlotillaRules.chargedMass(5_000, 5_000, 5_000, 1.5D), 1.0e-9D);
        assertEquals(FORMULA.cost(3_000, 5_000),
                FlotillaRules.foldCost(FORMULA, 3_000, 5_000, 5_000, 5_000, 1.5D), 1.0e-12D);
    }

    @Test
    void anExponentOfOneIsNoDearerThanFlyingAlone() {
        assertEquals(2_000.0D, FlotillaRules.chargedMass(2_000, 30_000, 10_000, 1.0D), 1.0e-9D);
    }

    @Test
    void equalShipsPayMoreTheMoreOfThemThereAre() {
        double one = 4_000.0D;
        assertEquals(Math.sqrt(2.0D), FlotillaRules.chargedMass(one, 2 * one, one, 1.5D) / one, 1.0e-9D);
        assertEquals(2.0D, FlotillaRules.chargedMass(one, 4 * one, one, 1.5D) / one, 1.0e-9D);
        assertEquals(Math.sqrt(8.0D), FlotillaRules.chargedMass(one, 8 * one, one, 1.5D) / one, 1.0e-9D);
    }

    @Test
    void aFleetCostsMoreThanTheSameTonnageFlyingAsOneShip() {
        // Four ships of 5,000 against one of 20,000: the fleet's drives pay more between them.
        double fleet = 4 * FlotillaRules.chargedMass(5_000, 20_000, 5_000, 1.5D);
        double single = FlotillaRules.chargedMass(20_000, 20_000, 20_000, 1.5D);
        assertTrue(fleet > single, "fleet " + fleet + " should outweigh " + single);
    }

    @Test
    void theChargedMassNeverFallsAsTheFleetGrows() {
        double previous = 0.0D;
        for (int ships = 1; ships <= 16; ships++) {
            double charged = FlotillaRules.chargedMass(1_000, ships * 1_000.0D, 1_000, 1.5D);
            assertTrue(charged >= previous);
            previous = charged;
        }
    }

    @Test
    void nonsenseIsTreatedAsNothingRatherThanPoisoningTheCost() {
        assertEquals(0.0D, FlotillaRules.chargedMass(Double.NaN, 1_000, 1_000, 1.5D), 1.0e-9D);
        assertEquals(1_000.0D, FlotillaRules.chargedMass(1_000, Double.NaN, Double.NaN, 1.5D), 1.0e-9D);
        assertEquals(1_000.0D, FlotillaRules.chargedMass(1_000, 4_000, 1_000, 0.5D), 1.0e-9D,
                "an exponent below one is one");
    }

    @Test
    void aFoldAsksForItsWholeEssenceUpFront() {
        assertEquals(0, FlotillaRules.essenceForFold(0, 4, 600, 20), "no escorts, no essence");
        assertEquals(0, FlotillaRules.essenceForFold(3, 0, 600, 20), "a free coupler asks for nothing");
        assertEquals(3 * 4 * 30, FlotillaRules.essenceForFold(3, 4, 600, 20));
        assertEquals(3 * 4 * 31, FlotillaRules.essenceForFold(3, 4, 601, 20), "a part interval is a whole one");
    }
}
