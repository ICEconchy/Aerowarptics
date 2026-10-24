package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether an airship can actually turn each drive.
 *
 * <p>Nothing about a drive that asks for too much throws: it sits on the deck reading "overstressed", and
 * the player concludes the mod is broken. The first defaults did exactly that. Create charges stress per
 * RPM, so 16 per RPM at 128 RPM is 2,048 su - a whole Portable Engine for a Mk I - and the Singularity
 * wanted six. Read against the shipped defaults, so this is what a player on an untouched config gets.
 *
 * <p>The engine figures are the defaults of the versions this mod builds against (Simulated 1.3.0 and
 * Create 6.0.10), read from their jars rather than remembered.
 */
class DrivePowerBudgetTest {

    /** Simulated's Portable Engine: 64 su per RPM at 32 RPM. What an airship actually carries. */
    private static final int ENGINE_RPM = 32;
    private static final double PORTABLE_ENGINE = 64.0D * ENGINE_RPM;

    /** The same engine superheated turns twice as fast for the same capacity per RPM. */
    private static final double SUPERHEATED_ENGINE = 64.0D * ENGINE_RPM * 2;

    /** A Create steam engine at full efficiency: 16,384 su at 64 RPM. */
    private static final double STEAM_ENGINE = 16_384.0D;

    /** Create's default {@code maxRotationSpeed}. Nothing turns faster, whatever the gearing. */
    private static final int CREATE_MAX_RPM = 256;

    private static final RiftDriveTier[] SURVIVAL = {
            RiftDriveTier.MK_I, RiftDriveTier.MK_II, RiftDriveTier.MK_III, RiftDriveTier.SINGULARITY};

    private static double fullRateDraw(RiftDriveTier tier) {
        return tier.defaults().stressImpact() * tier.defaults().optimalRpm();
    }

    /**
     * Cogs only double or halve, so from an engine's 32 RPM the only speeds on offer without a Rotation
     * Speed Controller are 32 doubled. The old Mk II asked for 96 and 192, which in practice meant
     * running it at 256 and paying more than its own figures said.
     */
    private static boolean reachableWithCogs(int rpm) {
        if (rpm < ENGINE_RPM || rpm % ENGINE_RPM != 0) {
            return false;
        }
        int ratio = rpm / ENGINE_RPM;
        return (ratio & (ratio - 1)) == 0;
    }

    @Test
    void everySpeedADriveAsksForCanBeGearedFromAnEngine() {
        for (RiftDriveTier tier : SURVIVAL) {
            RiftDriveTier.Defaults d = tier.defaults();
            assertTrue(reachableWithCogs(d.minimumRpm()), tier + " minimum " + d.minimumRpm());
            assertTrue(reachableWithCogs(d.optimalRpm()), tier + " optimal " + d.optimalRpm());
            assertTrue(d.minimumRpm() <= d.optimalRpm(), tier.name());
            assertTrue(d.optimalRpm() <= CREATE_MAX_RPM, tier + " is faster than Create allows");
        }
    }

    @Test
    void aMkIRunsStraightOffAnEngineWithNoGearingAtAll() {
        assertEquals(ENGINE_RPM, RiftDriveTier.MK_I.defaults().minimumRpm());
    }

    @Test
    void theFirstTwoDrivesLeaveMostOfAnEngineForThePropellers() {
        // A Mk I shares its engine with whatever flies the ship; a Mk II may need one to itself, but only one.
        assertTrue(fullRateDraw(RiftDriveTier.MK_I) <= PORTABLE_ENGINE / 4, "Mk I " + fullRateDraw(RiftDriveTier.MK_I));
        assertTrue(fullRateDraw(RiftDriveTier.MK_II) <= PORTABLE_ENGINE, "Mk II " + fullRateDraw(RiftDriveTier.MK_II));
    }

    @Test
    void theLongHaulDrivesFitOnAFewSuperheatedEnginesOrOneSteamEngine() {
        assertTrue(fullRateDraw(RiftDriveTier.MK_III) <= SUPERHEATED_ENGINE,
                "Mk III " + fullRateDraw(RiftDriveTier.MK_III));
        assertTrue(fullRateDraw(RiftDriveTier.SINGULARITY) <= 2 * SUPERHEATED_ENGINE,
                "Singularity " + fullRateDraw(RiftDriveTier.SINGULARITY));
        assertTrue(fullRateDraw(RiftDriveTier.SINGULARITY) <= STEAM_ENGINE / 2,
                "Singularity " + fullRateDraw(RiftDriveTier.SINGULARITY));
    }

    @Test
    void eachTierCostsMoreToTurnThanTheOneBefore() {
        for (int i = 1; i < SURVIVAL.length; i++) {
            assertTrue(fullRateDraw(SURVIVAL[i]) > fullRateDraw(SURVIVAL[i - 1]),
                    SURVIVAL[i] + " draws no more than " + SURVIVAL[i - 1]);
        }
    }

    @Test
    void theCreativeDriveDrawsNothing() {
        assertEquals(0.0D, fullRateDraw(RiftDriveTier.CREATIVE));
    }
}
