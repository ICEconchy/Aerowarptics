package uk.co.iceconchy.aerowarptics.cooling;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.weather.RiftStormRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What vents and ice do to a drive's cooldown.
 *
 * <p>Read against the shipped defaults - a vent halves, ice takes a tenth, four coolers at most - and the
 * tiers' own cooldowns, so the tests say what a player on an untouched config actually gets.
 */
class DriveCoolingTest {

    /** What a fresh config file starts with - the same object {@code AWConfig} takes its defaults from. */
    private static final DriveCooling.Settings DEFAULTS = DriveCooling.DEFAULTS;

    /** Simulated's Portable Engine: 64 su per RPM at 32 RPM. What an airship actually carries. */
    private static final int ENGINE_RPM = 32;
    private static final double PORTABLE_ENGINE = 64.0D * ENGINE_RPM;

    private static final int MK_I = RiftDriveTier.MK_I.defaults().cooldownTicks();

    private static int cooled(int base, int vents, int ice) {
        return DriveCooling.cooldownTicks(base, new DriveCooling.Fit(vents, ice), DEFAULTS);
    }

    /**
     * The request this was built to: a vent halves the cooling time, the effect stacks up to four times, and
     * ice takes ten per cent off. If a default moves, it should be because somebody meant it to.
     */
    @Test
    void theShippedDefaultsAreAHalfATenthAndFour() {
        assertEquals(0.5D, DEFAULTS.ventShare());
        assertEquals(0.9D, DEFAULTS.iceShare());
        assertEquals(4, DEFAULTS.most());
    }

    // ------------------------------------------------------------------ power

    @Test
    void aVentRunsStraightOffAnEngineWithNoGearingAtAll() {
        assertEquals(ENGINE_RPM, DriveCooling.DEFAULT_VENT_MINIMUM_RPM);
    }

    /**
     * Nothing about a vent that asks for too much throws - it sits there reading "overstressed" and the
     * drive takes its full time to cool. Four on a shaft of their own, the most that count, have to fit
     * beside the drive and the propellers on the one engine a small ship carries.
     */
    @Test
    void aFullSetOfVentsOnTheirOwnShaftTakesNoMoreOfAnEngineThanAMkIDoes() {
        double four = DEFAULTS.most() * DriveCooling.DEFAULT_VENT_STRESS * DriveCooling.DEFAULT_VENT_MINIMUM_RPM;
        RiftDriveTier.Defaults mkI = RiftDriveTier.MK_I.defaults();
        assertTrue(four <= mkI.stressImpact() * mkI.optimalRpm(), "four vents draw " + four);
        assertTrue(four <= PORTABLE_ENGINE / 4, "four vents draw " + four + " of an engine's " + PORTABLE_ENGINE);
    }

    /** Backed onto its drive a vent turns at the drive's speed, so the comparison is impact against impact. */
    @Test
    void aVentBackedOntoItsDriveCostsLessThanTheDriveItCools() {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            if (!tier.creative()) {
                assertTrue(DriveCooling.DEFAULT_VENT_STRESS < tier.defaults().stressImpact(), tier.name());
            }
        }
    }

    // --------------------------------------------------------------- cooldown

    @Test
    void anUncooledDriveKeepsItsCooldown() {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            int base = tier.defaults().cooldownTicks();
            assertEquals(base, DriveCooling.cooldownTicks(base, DriveCooling.Fit.NONE, DEFAULTS), tier.name());
        }
    }

    @Test
    void oneVentHalvesTheCooldown() {
        assertEquals(MK_I / 2, cooled(MK_I, 1, 0));
    }

    @Test
    void ventsStackEachHalvingWhatTheLastLeft() {
        assertEquals(MK_I / 4, cooled(MK_I, 2, 0));
        assertEquals(MK_I / 8, cooled(MK_I, 3, 0));
        assertEquals(MK_I / 16, cooled(MK_I, 4, 0));
    }

    @Test
    void aFifthVentDoesNothing() {
        assertEquals(cooled(MK_I, 4, 0), cooled(MK_I, 5, 0));
        assertEquals(cooled(MK_I, 4, 0), cooled(MK_I, 40, 0));
        assertTrue(DriveCooling.ventCounts(3, DEFAULTS.most()), "the fourth vent counts");
        assertFalse(DriveCooling.ventCounts(4, DEFAULTS.most()), "the fifth does not, and its goggles say so");
        assertFalse(DriveCooling.ventCounts(-1, DEFAULTS.most()), "a vent the drive never listed does not count");
    }

    @Test
    void iceTakesATenthOff() {
        assertEquals((int) Math.round(MK_I * 0.9D), cooled(MK_I, 0, 1));
    }

    @Test
    void iceStacksToTheSameLimitAsVents() {
        assertEquals((int) Math.round(MK_I * Math.pow(0.9D, 4)), cooled(MK_I, 0, 4));
        assertEquals(cooled(MK_I, 0, 4), cooled(MK_I, 0, 26), "a drive walled in with ice is not cooled further");
    }

    @Test
    void ventsAreCountedBeforeIce() {
        // Two vents and a wall of ice: the vents count, and the ice gets the two places they left.
        assertEquals(new DriveCooling.Fit(2, 2), DriveCooling.fit(2, 26, 4));
        assertEquals((int) Math.round(MK_I * 0.25D * 0.81D), cooled(MK_I, 2, 26));
        // Four vents leave the ice nothing to do.
        assertEquals(new DriveCooling.Fit(4, 0), DriveCooling.fit(4, 26, 4));
        assertEquals(cooled(MK_I, 4, 0), cooled(MK_I, 4, 26));
    }

    /**
     * The reason vents are counted first. Were the limit filled in the order things were found, a pilot who
     * bolted a vent on beside four blocks of ice would find it surplus.
     */
    @Test
    void swappingIceForAVentNeverMakesADriveCoolSlower() {
        for (int vents = 0; vents <= 6; vents++) {
            for (int ice = 1; ice <= 6; ice++) {
                assertTrue(cooled(MK_I, vents + 1, ice - 1) <= cooled(MK_I, vents, ice),
                        vents + " vents and " + ice + " ice cooled faster than with one of the ice made a vent");
            }
        }
    }

    @Test
    void addingACoolerNeverMakesADriveCoolSlower() {
        for (int vents = 0; vents <= 6; vents++) {
            for (int ice = 0; ice <= 6; ice++) {
                int now = cooled(MK_I, vents, ice);
                assertTrue(cooled(MK_I, vents + 1, ice) <= now, "a vent more than " + vents + "+" + ice);
                assertTrue(cooled(MK_I, vents, ice + 1) <= now, "an ice more than " + vents + "+" + ice);
            }
        }
    }

    @Test
    void aStormAndAVentBothTakeTheirShare() {
        // The drive asks the storm first and cools what is left: half, and half again.
        int stormed = RiftStormRules.cooldownTicks(MK_I, true, 0.5D);
        assertEquals(MK_I / 4, cooled(stormed, 1, 0));
    }

    @Test
    void aCreativeDriveHasNothingToCool() {
        assertEquals(0, cooled(RiftDriveTier.CREATIVE.defaults().cooldownTicks(), 4, 4));
        assertEquals(0, cooled(-5, 1, 0), "a cooldown is never negative");
    }

    @Test
    void theCooledCooldownRoundsRatherThanTruncates() {
        assertEquals(2, cooled(3, 1, 0));
    }

    @Test
    void aConfigOutOfRangeIsReadAsTheNearestSensibleThing() {
        DriveCooling.Settings wild = new DriveCooling.Settings(-3, 7.0D, -2.0D);
        assertEquals(0, wild.most(), "fewer than no coolers is none");
        assertEquals(1.0D, wild.ventShare(), "a share past one would lengthen the cooldown");
        assertEquals(0.0D, wild.iceShare());
        assertEquals(MK_I, DriveCooling.cooldownTicks(MK_I, new DriveCooling.Fit(4, 4), wild),
                "with no coolers counted, nothing is cooled");
        assertEquals(1.0D, new DriveCooling.Settings(4, Double.NaN, 0.9D).ventShare(),
                "a share that is not a number leaves the cooldown alone rather than removing it");
    }

    @Test
    void switchingCoolingOffLeavesEveryCooldownAlone() {
        DriveCooling.Settings off = new DriveCooling.Settings(0, 0.5D, 0.9D);
        assertEquals(DriveCooling.Fit.NONE, DriveCooling.fit(9, 9, off.most()));
        assertEquals(MK_I, DriveCooling.cooldownTicks(MK_I, new DriveCooling.Fit(9, 9), off));
    }

    @Test
    void theShareIsWhatTheGogglesAreTold() {
        assertEquals(1.0D, DriveCooling.share(DriveCooling.Fit.NONE, DEFAULTS));
        assertEquals(0.0625D, DriveCooling.share(new DriveCooling.Fit(4, 0), DEFAULTS), 1.0e-9D);
        assertEquals(0.45D, DriveCooling.share(new DriveCooling.Fit(1, 1), DEFAULTS), 1.0e-9D);
        // Handed more than count, it counts them itself rather than trusting the caller to have.
        assertEquals(0.0625D, DriveCooling.share(new DriveCooling.Fit(9, 9), DEFAULTS), 1.0e-9D);
    }
}
