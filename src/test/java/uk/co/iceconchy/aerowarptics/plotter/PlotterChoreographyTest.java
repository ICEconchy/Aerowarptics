package uk.co.iceconchy.aerowarptics.plotter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's strike, beat by beat: it builds with the charge, strains while the far end is waited on, bursts
 * at the tear, holds the rim while open - and never jumps where one state hands to the next.
 */
class PlotterChoreographyTest {

    private static final double[] CHARGES = {0.0D, 0.1D, 0.35D, 0.6D, 0.9D, 1.0D};

    @Test
    void atRestThereIsNothing() {
        for (double ticks = 0.0D; ticks < 200.0D; ticks += 7.0D) {
            assertEquals(0.0D, PlotterChoreography.beam(PlotterState.IDLE, 1.0D, ticks));
            assertEquals(0.0D, PlotterChoreography.star(PlotterState.IDLE, 1.0D, ticks));
            assertEquals(0.0D, PlotterChoreography.gather(PlotterState.IDLE, 1.0D, ticks));
            assertEquals(0.0D, PlotterChoreography.cracks(PlotterState.IDLE, ticks));
            assertEquals(0.0D, PlotterChoreography.flash(PlotterState.IDLE, ticks));
            assertEquals(0.0D, PlotterChoreography.shock(PlotterState.IDLE, ticks));
            assertEquals(0.0D, PlotterChoreography.tethers(PlotterState.IDLE, ticks));
            assertEquals(0.0D, PlotterChoreography.arcChance(PlotterState.IDLE, 1.0D));
        }
    }

    /** The fuller the charge, the heavier the beam, the bigger the star and the more often the arcs. */
    @Test
    void itBuildsWithTheCharge() {
        double beam = -1.0D;
        double star = -1.0D;
        double arcs = -1.0D;
        double period = Double.MAX_VALUE;
        for (double charged : CHARGES) {
            double nowBeam = PlotterChoreography.beam(PlotterState.CHARGING, charged, 100.0D);
            double nowStar = PlotterChoreography.star(PlotterState.CHARGING, charged, 100.0D);
            double nowArcs = PlotterChoreography.arcChance(PlotterState.CHARGING, charged);
            double nowPeriod = PlotterChoreography.gatherPeriod(charged);
            assertTrue(nowBeam > beam && nowStar > star && nowArcs > arcs && nowPeriod < period, "at " + charged);
            beam = nowBeam;
            star = nowStar;
            arcs = nowArcs;
            period = nowPeriod;
        }
        assertTrue(PlotterChoreography.gatherPeriod(1.0D) > 5.0D, "rings closing faster than the eye can follow");
    }

    /** A charge begins from nothing rather than snapping on. */
    @Test
    void aChargeComesInRatherThanSnappingOn() {
        assertEquals(0.0D, PlotterChoreography.beam(PlotterState.CHARGING, 0.5D, 0.0D), 1.0e-9D);
        assertEquals(0.0D, PlotterChoreography.star(PlotterState.CHARGING, 0.5D, 0.0D), 1.0e-9D);
        assertEquals(0.0D, PlotterChoreography.gather(PlotterState.CHARGING, 0.5D, 0.0D), 1.0e-9D);
    }

    /** Charged to the full, the machine hands to the wait on the far end and the wait to the tear with no jump. */
    @Test
    void theBeatsJoin() {
        double charged = 1.0D;
        double late = 200.0D;
        assertEquals(PlotterChoreography.beam(PlotterState.CHARGING, charged, late),
                PlotterChoreography.beam(PlotterState.REACHING, charged, 0.0D), 1.0e-9D);
        assertEquals(PlotterChoreography.gather(PlotterState.CHARGING, charged, late),
                PlotterChoreography.gather(PlotterState.REACHING, charged, 0.0D), 1.0e-9D);
        assertEquals(PlotterChoreography.starGlow(PlotterState.CHARGING, charged, late),
                PlotterChoreography.starGlow(PlotterState.REACHING, charged, 0.0D), 1.0e-9D);
        double strain = 0.12D * PlotterChoreography.STAR + 1.0e-9D;
        for (double ticks = 0.0D; ticks < 120.0D; ticks += 3.0D) {
            assertEquals(PlotterChoreography.STAR, PlotterChoreography.star(PlotterState.REACHING, charged, ticks), strain,
                    "the straining star wanders from full at " + ticks);
            assertEquals(PlotterChoreography.star(PlotterState.OPEN, charged, 0.0D),
                    PlotterChoreography.star(PlotterState.REACHING, charged, ticks), strain, "the burst jumps at " + ticks);
        }
        assertEquals(PlotterChoreography.beam(PlotterState.REACHING, charged, late),
                PlotterChoreography.beam(PlotterState.OPEN, charged, 0.0D), 1.0e-9D);
        assertEquals(PlotterChoreography.tethers(PlotterState.OPEN, late),
                PlotterChoreography.tethers(PlotterState.CLOSING, 0.0D), 1.0e-9D);
    }

    /** The tear: the star bursts past the rim and is gone, the flash is brief, the shockwave rolls out and fades. */
    @Test
    void theTearBurstsFlashesAndRollsOut() {
        assertTrue(PlotterChoreography.star(PlotterState.OPEN, 1.0D, PlotterChoreography.BURST_TICKS) > 1.0D,
                "the star never reaches the rim");
        assertEquals(0.0D, PlotterChoreography.starGlow(PlotterState.OPEN, 1.0D, PlotterChoreography.BURST_TICKS), 1.0e-9D);
        assertEquals(1.0D, PlotterChoreography.flash(PlotterState.OPEN, 0.0D), 1.0e-9D);
        assertEquals(0.0D, PlotterChoreography.flash(PlotterState.OPEN, PlotterChoreography.FLASH_TICKS), 1.0e-9D);
        double last = 0.0D;
        double glow = 2.0D;
        for (double ticks = 0.0D; ticks < PlotterChoreography.SHOCK_TICKS; ticks += 1.0D) {
            double reach = PlotterChoreography.shock(PlotterState.OPEN, ticks);
            double bright = PlotterChoreography.shockGlow(PlotterState.OPEN, ticks);
            assertTrue(reach >= last && reach <= PlotterChoreography.SHOCK_REACH, "the wave rolls back at " + ticks);
            assertTrue(bright < glow, "the wave brightens at " + ticks);
            last = reach;
            glow = bright;
        }
        assertEquals(0.0D, PlotterChoreography.shock(PlotterState.OPEN, PlotterChoreography.SHOCK_TICKS));
        assertEquals(0.0D, PlotterChoreography.shockGlow(PlotterState.OPEN, PlotterChoreography.SHOCK_TICKS), 1.0e-9D);
    }

    /** Open, the tethers take hold after the burst and keep hold; closing, they let go before the rift has healed. */
    @Test
    void theTethersHoldTheRimWhileItIsOpen() {
        assertEquals(0.0D, PlotterChoreography.tethers(PlotterState.OPEN, 0.0D), 1.0e-9D);
        assertEquals(1.0D, PlotterChoreography.tethers(PlotterState.OPEN, 400.0D), 1.0e-9D);
        assertEquals(0.0D, PlotterChoreography.tethers(PlotterState.CLOSING, PlotterWorks.CLOSE_TICKS), 1.0e-9D);
    }

    /** Waiting on the far end, the cracks run out to the rim; they do not before, and are gone once it tears. */
    @Test
    void theCracksRunOutWhileTheFarEndIsWaitedOn() {
        assertEquals(0.0D, PlotterChoreography.cracks(PlotterState.CHARGING, 500.0D));
        assertEquals(0.0D, PlotterChoreography.cracks(PlotterState.REACHING, 0.0D), 1.0e-9D);
        assertEquals(1.0D, PlotterChoreography.cracks(PlotterState.REACHING, PlotterChoreography.CRACK_TICKS), 1.0e-9D);
        assertEquals(0.0D, PlotterChoreography.crackGlow(PlotterState.OPEN, PlotterChoreography.BURST_TICKS), 1.0e-9D);
    }

    /** Every brightness is a fraction, whatever it is asked. */
    @Test
    void everyBrightnessIsAFraction() {
        for (PlotterState state : PlotterState.values()) {
            for (double charged : CHARGES) {
                for (double ticks = 0.0D; ticks < 300.0D; ticks += 2.5D) {
                    for (double value : new double[]{PlotterChoreography.beam(state, charged, ticks),
                            PlotterChoreography.starGlow(state, charged, ticks), PlotterChoreography.gather(state, charged, ticks),
                            PlotterChoreography.cracks(state, ticks), PlotterChoreography.crackGlow(state, ticks),
                            PlotterChoreography.flash(state, ticks), PlotterChoreography.shockGlow(state, ticks),
                            PlotterChoreography.tethers(state, ticks), PlotterChoreography.arcChance(state, charged)}) {
                        assertTrue(value >= 0.0D && value <= 1.0D, state + " at " + ticks + ": " + value);
                    }
                }
            }
        }
    }
}
