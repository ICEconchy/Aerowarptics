package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a gate's own opening puts somebody who steps through it, and the clock they are given.
 *
 * <p>A corridor is sealed - no way in but on a train - which is right until a train stops in one, when the only
 * person who could drive it out is the only person who cannot reach it. The door is the answer, and both halves of
 * it are arithmetic worth holding: the end they arrive at, and the countdown they read.
 */
class CorridorDoorTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.withDefaultNamespace("overworld"));

    private static RailLane lane(int slot, int length) {
        return RailLane.laid(UUID.randomUUID(), OVERWORLD, new BlockPos(-1, 64, 0), new BlockPos(0, 64, 0),
                Direction.EAST, slot, length, ResourceLocation.parse("create:track"), UUID.randomUUID());
    }

    /** In at the gate a train goes in at, out beside the track at that same end of the corridor. */
    @Test
    void steppingInAtAGateLandsAtThatGatesOwnEndOfTheCorridor() {
        RailLane lane = lane(0, 160);
        assertEquals(RailWorks.westTrack(0), CorridorDoor.landing(lane, true));
        assertEquals(RailWorks.eastTrack(0, 160), CorridorDoor.landing(lane, false));
    }

    /** The two ends are the two ends: a corridor nobody could tell the ends of would be a door to nowhere. */
    @Test
    void theTwoEndsAreNotTheSamePlace() {
        RailLane lane = lane(3, 160);
        BlockPos near = CorridorDoor.landing(lane, true);
        BlockPos far = CorridorDoor.landing(lane, false);
        assertNotEquals(near, far);
        assertEquals(near.getY(), far.getY(), "both stand on the corridor's own track level");
        assertEquals(CorridorSlots.z(3), near.getZ(), "and on its own slot's line");
        assertEquals(CorridorSlots.z(3), far.getZ());
        assertTrue(near.getX() < far.getX(), "west end first, as a corridor is laid");
    }

    /** Each lane's door opens on its own corridor, never into somebody else's. */
    @Test
    void everyLanesDoorOpensOnItsOwnCorridor() {
        for (int slot = 0; slot < 8; slot++) {
            BlockPos landing = CorridorDoor.landing(lane(slot, 160), true);
            assertEquals(slot, CorridorSlots.slotAt(landing.getZ() + 0.5D));
        }
    }

    /** The clock rounds up, so it never reads zero while there is still time to use. */
    @Test
    void theClockRoundsUpAndPadsItsSeconds() {
        assertEquals("3:00", AWLang.clock(3_600));
        assertEquals("1:00", AWLang.clock(1_200));
        assertEquals("0:59", AWLang.clock(1_180));
        assertEquals("0:10", AWLang.clock(200));
        assertEquals("0:01", AWLang.clock(1), "a single tick left is still a second shown");
        assertEquals("0:00", AWLang.clock(0));
        assertEquals("0:00", AWLang.clock(-40), "nothing goes backwards");
    }

    /** Minutes keep counting past an hour rather than growing a third field nobody can read at a glance. */
    @Test
    void theClockStaysInMinutes() {
        assertEquals("60:00", AWLang.clock(72_000));
        assertEquals("75:00", AWLang.clock(90_000));
    }
}
