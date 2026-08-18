package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.AirshipWarpData;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ship-attached warp claim.
 *
 * <p>These run against a detached user-data tag, which is exactly the structure Sable persists with
 * a sub-level - so what is asserted here is what survives a save.
 */
class AirshipWarpDataTest {

    private static final BlockPos DRIVE_A = new BlockPos(10, 64, 10);
    private static final BlockPos DRIVE_B = new BlockPos(-4, 70, 22);
    private static final Vec3 TARGET = new Vec3(1250.5D, 110.5D, -8399.5D);

    @Test
    void aFreshAirshipIsNotWarping() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        assertFalse(data.isWarping());
        assertNull(data.owningDrive());
        assertNull(data.destinationAnchor());
        assertNull(data.target());
    }

    @Test
    void claimingRecordsTheOwningDriveAndDestination() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        UUID anchor = UUID.randomUUID();

        assertTrue(data.claim(DRIVE_A, anchor, TARGET, 200, 1000L));

        assertTrue(data.isWarping());
        assertEquals(DRIVE_A, data.owningDrive());
        assertTrue(data.isOwnedBy(DRIVE_A));
        assertFalse(data.isOwnedBy(DRIVE_B));
        assertEquals(anchor, data.destinationAnchor());
        assertEquals(TARGET, data.target());
        assertEquals(200, data.durationTicks());
        assertEquals(1000L, data.startedAt());
    }

    @Test
    void aSecondDriveCannotStealAnAirshipMidWarp() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        assertTrue(data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L));

        assertFalse(data.claim(DRIVE_B, UUID.randomUUID(), TARGET, 200, 5L),
                "two drives on one airship must not both warp it");
        assertEquals(DRIVE_A, data.owningDrive(), "the original claim is untouched by the refusal");
    }

    @Test
    void theOwningDriveMayRefreshItsOwnClaim() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(data.claim(DRIVE_A, first, TARGET, 200, 0L));
        assertTrue(data.claim(DRIVE_A, second, TARGET, 300, 40L));
        assertEquals(second, data.destinationAnchor());
        assertEquals(300, data.durationTicks());
    }

    @Test
    void releasingFreesTheAirshipForAnotherDrive() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.release(false, 900L);

        assertFalse(data.isWarping());
        assertNull(data.owningDrive());
        assertNull(data.destinationAnchor());
        assertEquals(900L, data.cooldownUntil());
        assertFalse(data.wasCancelled());
        assertTrue(data.claim(DRIVE_B, UUID.randomUUID(), TARGET, 200, 1000L));
    }

    @Test
    void cancellationIsRecordedDistinctlyFromCompletion() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.release(true, 100L);
        assertTrue(data.wasCancelled());
    }

    @Test
    void progressIsOnlyRecordedWhileAWarpIsActive() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.advance("warping", 42);
        assertEquals(0, data.progressTicks(), "a released airship must not accumulate phantom progress");

        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.advance("warping", 42);
        assertEquals(42, data.progressTicks());
        assertEquals("warping", data.phase());
    }

    @Test
    void aStaleClaimFromAnInterruptedSessionIsReleased() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 100L);

        data.releaseIfStale(300L, 1000);
        assertTrue(data.isWarping(), "a claim that is merely in progress is left alone");

        data.releaseIfStale(5_000L, 1000);
        assertFalse(data.isWarping(), "a claim older than the grace period is cleaned up");
        assertTrue(data.wasCancelled());
    }

    @Test
    void aClaimFromTheFutureIsAlsoTreatedAsStale() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 10_000L);
        // Game time can move backwards if a world is restored from a backup.
        data.releaseIfStale(50L, 1000);
        assertFalse(data.isWarping());
    }

    @Test
    void whereTheAirshipSetOffFromIsRememberedForTheWholeWarp() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.rememberOrigin(new Vector3d(120.5D, 78.0D, -340.25D),
                new Quaterniond(0.0D, 0.3826834D, 0.0D, 0.9238795D));

        var origin = data.origin();
        assertNotNull(origin, "a hull in transit must always know where home is");
        assertEquals(120.5D, origin.position().x(), 1.0e-9D);
        assertEquals(78.0D, origin.position().y(), 1.0e-9D);
        assertEquals(-340.25D, origin.position().z(), 1.0e-9D);
        assertEquals(0.9238795D, origin.orientation().w(), 1.0e-7D);
    }

    @Test
    void releasingTheWarpForgetsWhereItSetOffFrom() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.rememberOrigin(new Vector3d(1.0D, 2.0D, 3.0D), new Quaterniond());
        assertNotNull(data.origin());

        data.release(false, 0L);
        assertNull(data.origin(), "a completed warp leaves no recovery point behind");
    }

    @Test
    void anAirshipThatNeverLeftHasNoRecoveryPoint() {
        AirshipWarpData data = AirshipWarpData.of(new CompoundTag());
        assertNull(data.origin());
    }

    @Test
    void theRecoveryPointSurvivesSerialisationWithTheAirship() {
        CompoundTag userData = new CompoundTag();
        AirshipWarpData data = AirshipWarpData.of(userData);
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.rememberOrigin(new Vector3d(-500.0D, 96.0D, 2400.0D), new Quaterniond());

        var restored = AirshipWarpData.of(userData.copy()).origin();
        assertNotNull(restored);
        assertEquals(2400.0D, restored.position().z(), 1.0e-9D);
    }

    @Test
    void theModOnlyEverTouchesItsOwnSectionOfTheAirshipTag() {
        CompoundTag userData = new CompoundTag();
        CompoundTag foreign = new CompoundTag();
        foreign.putString("owner", "some other addon");
        userData.put("someothermod", foreign);

        AirshipWarpData data = AirshipWarpData.of(userData);
        data.claim(DRIVE_A, UUID.randomUUID(), TARGET, 200, 0L);
        data.release(false, 0L);
        data.clear();

        assertTrue(userData.contains("someothermod"), "another mod's data must survive intact");
        assertEquals("some other addon", userData.getCompound("someothermod").getString("owner"));
        assertFalse(userData.contains(AirshipWarpData.ROOT_KEY), "our own section is fully removed");
    }

    @Test
    void stateSurvivesBeingSerialisedWithTheAirship() {
        CompoundTag userData = new CompoundTag();
        UUID anchor = UUID.randomUUID();
        AirshipWarpData.of(userData).claim(DRIVE_A, anchor, TARGET, 250, 777L);

        // Sable writes this tag out with the sub-level and hands the same structure back on load.
        CompoundTag reloaded = userData.copy();
        AirshipWarpData restored = AirshipWarpData.of(reloaded);

        assertTrue(restored.isWarping());
        assertEquals(DRIVE_A, restored.owningDrive());
        assertEquals(anchor, restored.destinationAnchor());
        assertEquals(TARGET, restored.target());
        assertEquals(777L, restored.startedAt());
    }
}
