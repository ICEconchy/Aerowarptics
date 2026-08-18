package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Rift Drive state machine's transition rules. */
class RiftDriveStateTest {

    @Test
    void theHappyPathIsWalkable() {
        assertTrue(RiftDriveState.IDLE.canTransitionTo(RiftDriveState.CHARGING));
        assertTrue(RiftDriveState.CHARGING.canTransitionTo(RiftDriveState.CHARGED));
        assertTrue(RiftDriveState.CHARGED.canTransitionTo(RiftDriveState.DESTINATION_SELECTED));
        assertTrue(RiftDriveState.DESTINATION_SELECTED.canTransitionTo(RiftDriveState.STABILIZING));
        assertTrue(RiftDriveState.STABILIZING.canTransitionTo(RiftDriveState.WARPING));
        assertTrue(RiftDriveState.WARPING.canTransitionTo(RiftDriveState.ARRIVING));
        assertTrue(RiftDriveState.ARRIVING.canTransitionTo(RiftDriveState.COOLDOWN));
        assertTrue(RiftDriveState.COOLDOWN.canTransitionTo(RiftDriveState.IDLE));
    }

    @Test
    void aWarpCannotBeStartedWhileOneIsRunning() {
        for (RiftDriveState state : RiftDriveState.values()) {
            if (state.isSequenceRunning()) {
                assertFalse(state.acceptsDestination(),
                        state + " must not accept a new destination mid-sequence");
                assertFalse(state.canTransitionTo(RiftDriveState.DESTINATION_SELECTED),
                        state + " must not re-enter DESTINATION_SELECTED");
            }
        }
    }

    @Test
    void onlyAFullyChargedDriveAcceptsADestination() {
        for (RiftDriveState state : RiftDriveState.values()) {
            assertEquals(state == RiftDriveState.CHARGED, state.acceptsDestination(), state.toString());
        }
    }

    @Test
    void chargeOnlyAccumulatesBeforeCommitment() {
        assertTrue(RiftDriveState.IDLE.accumulatesCharge());
        assertTrue(RiftDriveState.CHARGING.accumulatesCharge());
        assertFalse(RiftDriveState.CHARGED.accumulatesCharge());
        assertFalse(RiftDriveState.WARPING.accumulatesCharge());
        assertFalse(RiftDriveState.COOLDOWN.accumulatesCharge(),
                "a cooling drive must not silently refill");
        assertFalse(RiftDriveState.ERROR.accumulatesCharge());
    }

    @Test
    void aChargedDriveStillRunsTheChargeCycleSoItCanBleedWhenThePowerStops() {
        assertTrue(RiftDriveState.CHARGED.participatesInCharging());
        assertFalse(RiftDriveState.CHARGED.accumulatesCharge());
        for (RiftDriveState state : RiftDriveState.values()) {
            if (state.isSequenceRunning() || state == RiftDriveState.COOLDOWN || state == RiftDriveState.ERROR) {
                assertFalse(state.participatesInCharging(),
                        state + " must be left alone by the charge cycle");
            }
        }
    }

    @Test
    void onlyThePreRiftStagesMayBeCancelled() {
        assertTrue(RiftDriveState.DESTINATION_SELECTED.isCancellable());
        assertTrue(RiftDriveState.STABILIZING.isCancellable());
        assertFalse(RiftDriveState.WARPING.isCancellable(),
                "once the rift is open the sequence must run to completion");
        assertFalse(RiftDriveState.ARRIVING.isCancellable());
        assertFalse(RiftDriveState.IDLE.isCancellable());
    }

    @Test
    void everyStateCanFailAndErrorRecoversToIdle() {
        for (RiftDriveState state : RiftDriveState.values()) {
            if (state == RiftDriveState.ERROR) {
                assertFalse(state.canTransitionTo(RiftDriveState.ERROR));
            } else {
                assertTrue(state.canTransitionTo(RiftDriveState.ERROR), state + " must be able to fail");
            }
        }
        assertTrue(RiftDriveState.ERROR.canTransitionTo(RiftDriveState.IDLE));
        assertFalse(RiftDriveState.ERROR.canTransitionTo(RiftDriveState.CHARGING));
    }

    @Test
    void aCooldownCannotBeSkipped() {
        assertFalse(RiftDriveState.ARRIVING.canTransitionTo(RiftDriveState.IDLE));
        assertFalse(RiftDriveState.ARRIVING.canTransitionTo(RiftDriveState.CHARGED));
        assertFalse(RiftDriveState.COOLDOWN.canTransitionTo(RiftDriveState.CHARGED));
        assertFalse(RiftDriveState.COOLDOWN.canTransitionTo(RiftDriveState.DESTINATION_SELECTED));
    }

    @Test
    void aWarpCannotBeStartedWithoutPassingThroughCharged() {
        assertFalse(RiftDriveState.IDLE.canTransitionTo(RiftDriveState.DESTINATION_SELECTED));
        assertFalse(RiftDriveState.CHARGING.canTransitionTo(RiftDriveState.DESTINATION_SELECTED));
        assertFalse(RiftDriveState.CHARGING.canTransitionTo(RiftDriveState.WARPING));
    }

    @Test
    void indexRoundTripIsStableAndBoundsSafe() {
        for (RiftDriveState state : RiftDriveState.values()) {
            assertEquals(state, RiftDriveState.byIndex(state.ordinal()));
        }
        assertEquals(RiftDriveState.IDLE, RiftDriveState.byIndex(-1));
        assertEquals(RiftDriveState.IDLE, RiftDriveState.byIndex(999));
    }

    @Test
    void everyStateHasItsOwnAnimation() {
        long distinct = java.util.Arrays.stream(RiftDriveState.values())
                .map(RiftDriveState::animation)
                .distinct()
                .count();
        assertEquals(RiftDriveState.values().length, distinct);
    }
}
