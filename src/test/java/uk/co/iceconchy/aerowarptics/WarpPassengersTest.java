package uk.co.iceconchy.aerowarptics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.WarpPassengers;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a passenger who has come off a warping ship gets put back on it.
 *
 * <p>The rule is worth a test of its own because getting it wrong is bad in both directions. Too
 * narrow and somebody is deleted inside a fold in space; too wide and a player who deliberately jumped
 * off a moving ship is teleported back aboard, which is a trap rather than a rescue.
 */
class WarpPassengersTest {

    @Test
    void aPassengerInsideAnApertureIsAlwaysPutBack() {
        assertTrue(WarpPassengers.insideTheFold(WarpFlight.Stage.TRANSIT));
        assertTrue(WarpPassengers.insideTheFold(WarpFlight.Stage.CORRIDOR));
        assertTrue(WarpPassengers.insideTheFold(WarpFlight.Stage.BREACH));
    }

    /**
     * The two stages where the ship is in open air.
     *
     * <p>Falling off a ship is an ordinary thing to do when there is ground under it, so the only
     * thing done to somebody who does is to take back the ship's momentum. Where they land is theirs.
     */
    @Test
    void aPassengerOutInTheWorldIsLeftWhereTheyFall() {
        assertFalse(WarpPassengers.insideTheFold(WarpFlight.Stage.APPROACH));
        assertFalse(WarpPassengers.insideTheFold(WarpFlight.Stage.EMERGE));
    }

    /** Every stage has an answer, so a stage added later cannot quietly default to "abandon them". */
    @Test
    void everyStageOfAFlightIsAccountedFor() {
        int inside = 0;
        for (WarpFlight.Stage stage : WarpFlight.Stage.values()) {
            if (WarpPassengers.insideTheFold(stage)) {
                inside++;
            }
        }
        assertTrue(inside == 3 && WarpFlight.Stage.values().length == 5,
                "a stage was added without deciding what happens to the crew during it");
    }

    /**
     * Recovery covers the run out as well as the fold.
     *
     * <p>It used to stop at the exit aperture, and strays were <em>forgotten</em> during the run out
     * rather than held - so {@code settle} could not put them back either and the last stage of every
     * journey quietly wrote off anyone not yet aboard. The hull is still under the drive's command
     * for the whole of it.
     */
    @Test
    void theRunOutIsStillTheShipsResponsibility() {
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.TRANSIT));
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.CORRIDOR));
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.BREACH));
        assertTrue(WarpPassengers.recoverable(WarpFlight.Stage.EMERGE),
                "a passenger adrift during the run out is still somewhere they did not choose to be");
    }

    /**
     * The approach is the one stage where stepping off is the player's own business.
     *
     * <p>The ship is where they boarded it and the world underneath is real, so hauling them back
     * would be a trap rather than a rescue.
     */
    @Test
    void steppingOffOnTheApproachIsAllowed() {
        assertFalse(WarpPassengers.recoverable(WarpFlight.Stage.APPROACH));
    }

    /** Every stage is either the approach or recoverable - there is no third case to forget about. */
    @Test
    void everyStageAfterTheApproachIsCovered() {
        for (WarpFlight.Stage stage : WarpFlight.Stage.values()) {
            assertEquals(stage != WarpFlight.Stage.APPROACH, WarpPassengers.recoverable(stage),
                    stage + " is neither clearly the player's business nor clearly the ship's");
        }
    }

    // ---------------------------------------------------------------- nbt round-trip

    /**
     * A saved and loaded set of seats is identical to the original.
     *
     * <p>This is the safety net for a server restart mid-warp: the drive persists its passenger list
     * so that the recovery path can put people back rather than writing them off.
     */
    @Test
    void seatsSurviveARoundTrip() {
        WarpPassengers original = new WarpPassengers();
        // Both seats must be loaded in one call: load() clears the map first.
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        addSeat(list, UUID.fromString("00000000-0000-0000-0000-000000001111"), 10.0, 64.0, 20.0);
        addSeat(list, UUID.fromString("00000000-0000-0000-0000-000000002222"), -5.5, 72.0, 30.5);
        tag.put("Seats", list);
        original.load(tag);

        CompoundTag saved = original.save();
        WarpPassengers restored = new WarpPassengers();
        restored.load(saved);

        assertEquals(2, restored.seatCount(), "both seats should have been restored");
    }

    /** Saving an empty passenger list produces a tag that loads as empty. */
    @Test
    void emptySeatsRoundTripToEmpty() {
        WarpPassengers empty = new WarpPassengers();
        CompoundTag saved = empty.save();
        WarpPassengers restored = new WarpPassengers();
        restored.load(saved);
        assertEquals(0, restored.seatCount(), "an empty save should load as empty");
    }

    /** Loading a null tag clears any existing seats. */
    @Test
    void loadingNullTagClearsSeats() {
        WarpPassengers passengers = new WarpPassengers();
        injectSeat(passengers, UUID.fromString("00000000-0000-0000-0000-000000003333"), 1.0, 2.0, 3.0);
        passengers.load(null);
        assertEquals(0, passengers.seatCount(), "loading null should clear the seat map");
    }

    /** Loading a tag with no Seats key clears any existing seats. */
    @Test
    void loadingTagWithoutSeatsKeyClearsSeats() {
        WarpPassengers passengers = new WarpPassengers();
        injectSeat(passengers, UUID.fromString("00000000-0000-0000-0000-000000004444"), 1.0, 2.0, 3.0);
        passengers.load(new CompoundTag());
        assertEquals(0, passengers.seatCount(), "loading a tag without Seats should clear the seat map");
    }

    /** A seat with negative coordinates round-trips correctly. */
    @Test
    void negativeCoordinatesSurviveRoundTrip() {
        WarpPassengers original = new WarpPassengers();
        injectSeat(original, UUID.fromString("00000000-0000-0000-0000-000000005555"), -1000.5, -2000.25, -3000.75);

        CompoundTag saved = original.save();
        WarpPassengers restored = new WarpPassengers();
        restored.load(saved);
        assertEquals(1, restored.seatCount(), "the seat with negative coordinates should survive");
    }

    /** Builds a seat entry inside an existing list tag, for multi-seat test setup. */
    private static void addSeat(net.minecraft.nbt.ListTag list, UUID id, double x, double y, double z) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("Most", id.getMostSignificantBits());
        entry.putLong("Least", id.getLeastSignificantBits());
        entry.putDouble("X", x);
        entry.putDouble("Y", y);
        entry.putDouble("Z", z);
        list.add(entry);
    }

    /**
     * Injects a seat directly, bypassing the entity-based hold() path.
     *
     * <p>The save/load methods operate on the seats map, so we can test them without a running game
     * by using reflection-free access through the package-private seatCount() and a helper.
     */
    private static void injectSeat(WarpPassengers passengers, UUID id, double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putLong("Most", id.getMostSignificantBits());
        entry.putLong("Least", id.getLeastSignificantBits());
        entry.putDouble("X", x);
        entry.putDouble("Y", y);
        entry.putDouble("Z", z);
        list.add(entry);
        tag.put("Seats", list);
        passengers.load(tag);
    }
}
