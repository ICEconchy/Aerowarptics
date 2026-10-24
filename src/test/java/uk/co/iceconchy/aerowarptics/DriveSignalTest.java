package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.drive.DriveSignal;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a comparator reads off a Rift Drive.
 *
 * <p>A wrong strength here does not throw and does not look wrong on the machine: a circuit simply
 * launches a tick early, or a lamp row is one lamp short. So the table in {@link DriveSignal} is pinned
 * reading by reading.
 */
class DriveSignalTest {

    @Test
    void anIdleDriveReadsNothing() {
        // Whatever charge it is still holding: it is not being turned, and that is what a circuit asks.
        assertEquals(0, DriveSignal.of(RiftDriveState.IDLE, 0.0F));
        assertEquals(0, DriveSignal.of(RiftDriveState.IDLE, 0.8F));
    }

    @Test
    void aCoolingDriveReadsOne() {
        assertEquals(1, DriveSignal.of(RiftDriveState.COOLDOWN, 0.0F));
        assertEquals(1, DriveSignal.of(RiftDriveState.COOLDOWN, 1.0F));
        // A fault is the same thing to a circuit: it will not go yet, and will again without being touched.
        assertEquals(1, DriveSignal.of(RiftDriveState.ERROR, 0.4F));
    }

    @Test
    void aChargingDriveClimbsFromTwoToThirteen() {
        assertEquals(2, DriveSignal.of(RiftDriveState.CHARGING, 0.0F));
        assertEquals(13, DriveSignal.of(RiftDriveState.CHARGING, 0.999F));
        // Every one of the twelve steps is reached, in order, and none is skipped.
        Set<Integer> seen = new HashSet<>();
        int previous = 2;
        for (int i = 0; i < 1000; i++) {
            int signal = DriveSignal.of(RiftDriveState.CHARGING, i / 1000.0F);
            assertTrue(signal >= previous, "the reading fell from " + previous + " to " + signal + " at " + i);
            assertTrue(signal - previous <= 1, "the reading jumped from " + previous + " to " + signal);
            assertTrue(signal >= DriveSignal.CHARGING_LEAST && signal <= DriveSignal.CHARGING_MOST);
            seen.add(signal);
            previous = signal;
        }
        assertEquals(12, seen.size());
    }

    @Test
    void theStepsAreEven() {
        // Twelve steps of a twelfth each, so a row of lamps fills at a steady pace.
        for (int step = 0; step < 12; step++) {
            assertEquals(2 + step, DriveSignal.of(RiftDriveState.CHARGING, (step + 0.5F) / 12.0F));
        }
    }

    @Test
    void aChargingDriveNeverReadsAsCharged() {
        // Fourteen is what a launch circuit waits for. A drive showing it a tick before the state machine
        // will take a signal would have its lever thrown at a drive that refuses it.
        assertEquals(13, DriveSignal.of(RiftDriveState.CHARGING, 1.0F));
        assertEquals(13, DriveSignal.of(RiftDriveState.CHARGING, 7.0F));
        assertEquals(14, DriveSignal.of(RiftDriveState.CHARGED, 1.0F));
    }

    @Test
    void aChargeThatIsNotANumberReadsAsEmpty() {
        assertEquals(2, DriveSignal.of(RiftDriveState.CHARGING, Float.NaN));
        assertEquals(2, DriveSignal.of(RiftDriveState.CHARGING, -3.0F));
    }

    @Test
    void theWholeWarpReadsFifteen() {
        for (RiftDriveState state : RiftDriveState.values()) {
            if (state.isSequenceRunning()) {
                assertEquals(15, DriveSignal.of(state, 1.0F), state + " is part of a warp");
            }
        }
        assertEquals(15, DriveSignal.of(RiftDriveState.STABILIZING, 0.3F));
    }

    @Test
    void everyReadingIsARedstoneStrength() {
        for (RiftDriveState state : RiftDriveState.values()) {
            for (float charge : new float[]{0.0F, 0.5F, 1.0F}) {
                int signal = DriveSignal.of(state, charge);
                assertTrue(signal >= 0 && signal <= 15, state + " reads " + signal);
            }
        }
    }

    @Test
    void onlyTheStateADriveLaunchesFromReadsFourteen() {
        // The reading and the state machine have to agree about when a signal will be taken.
        for (RiftDriveState state : RiftDriveState.values()) {
            assertEquals(state.acceptsDestination(), DriveSignal.of(state, 1.0F) == DriveSignal.CHARGED,
                    state.toString());
        }
    }
}
