package uk.co.iceconchy.aerowarptics;

import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.berth.BerthBox;
import uk.co.iceconchy.aerowarptics.berth.BerthMooring;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flight that turns its hull in the fold: one into a Berth, which is the only one that does.
 *
 * <p>Every flight before it had one bearing and one attitude, and the far end was the near end moved. The
 * failures here would each fly: a rift stood off to one side of the hull it is hiding, a ship spun round
 * at its mooring, an old flight that comes out a hair from where it used to.
 */
class TurnedFlightTest {

    private static final double EPS = 1.0e-9D;

    // ------------------------------------------------------------------ the far end

    /**
     * With no turn the far end is, to the last bit, what it always was - the arithmetic every flight that is
     * not into a berth was planned with, written out here as it stood.
     */
    @Test
    void anUnturnedFlightComesOutExactlyWhereItAlwaysDid() {
        Vector3d arrivalOrigin = new Vector3d(1234.5D, 96.25D, -777.75D);
        Vector3d originToCentre = new Vector3d(3.5D, 4.0D, -11.25D);
        Quaterniond attitude = new Quaterniond().rotateY(0.7D).rotateX(0.05D);
        Vector3d heading = new Vector3d(0.6D, 0.0D, -0.8D);
        double emergeDistance = 57.0D;
        double hullLength = 31.0D;
        double riftRadius = 12.5D;

        Vector3d emergeStart = heading.mul(-emergeDistance, new Vector3d()).add(arrivalOrigin);
        Vector3d emergeCentre = emergeStart.add(originToCentre, new Vector3d());
        Vector3d exitCentre = heading.mul(hullLength * 0.5D + FlightGeometry.TRANSIT_MARGIN * 0.5D, new Vector3d())
                .add(emergeCentre);

        WarpFlight.FarEnd far = WarpFlight.farEnd(arrivalOrigin, originToCentre, attitude, new Quaterniond(attitude),
                heading, emergeDistance, hullLength, riftRadius);
        assertEquals(emergeStart, far.emergeStart());
        assertEquals(exitCentre, far.exit().centre());
        assertEquals(heading, far.exit().normal());
        assertEquals(riftRadius, far.exit().radius());
    }

    /**
     * Turned into a slip, the hull's centre reappears on the slip's own axis, the exit rift stands ahead of it
     * on that axis facing down it, and the hull comes to rest with its centre on the mark - whichever way it
     * left, however far its origin is from its middle, bow first or stern first.
     */
    @Test
    void aTurnedHullComesOutDownItsSlipsLaneOntoItsMark() {
        Vector3d originToCentreShip = new Vector3d(6.0D, 3.0D, -14.0D); // a drive well off the middle
        double emergeDistance = 48.0D;
        double hullLength = 30.0D;
        for (int heading = 0; heading < 360; heading += 40) {
            Quaterniond departure = new Quaterniond().rotateY(Math.toRadians(heading));
            Vector3d bow = departure.transform(new Vector3d(0, 0, 1));
            Vector3d originToCentre = departure.transform(new Vector3d(originToCentreShip));
            for (int yaw = 0; yaw < 360; yaw += 55) {
                for (boolean sternFirst : new boolean[] {false, true}) {
                    BerthBox box = new BerthBox(400.5D, 80.0D, -120.5D, 40.0D, 14.0D, 14.0D, yaw);
                    Quaterniond arrival = BerthMooring.attitude(departure, bow, box);
                    Vector3d approach = box.approach(sternFirst);
                    Vector3d mark = box.mark(8.0D, 1.0D);
                    // The origin that puts the turned hull's centre on the mark.
                    Vector3d arrivalOrigin = new Vector3d(mark).sub(arrival.transform(new Vector3d(originToCentreShip)));

                    WarpFlight.FarEnd far = WarpFlight.farEnd(arrivalOrigin, originToCentre, departure, arrival,
                            approach, emergeDistance, hullLength, 10.0D);
                    String at = "heading " + heading + " into yaw " + yaw + (sternFirst ? " stern first" : "");

                    // Where the centre reappears: straight back up the lane from the mark.
                    Vector3d emergeCentre = new Vector3d(far.emergeStart())
                            .add(arrival.transform(new Vector3d(originToCentreShip)));
                    Vector3d back = new Vector3d(mark).sub(emergeCentre);
                    assertEquals(emergeDistance, back.dot(approach), 1.0e-6D, at);
                    assertEquals(emergeDistance, back.length(), 1.0e-6D, "off the slip's axis, " + at);

                    // The rift: on that axis, ahead of the hull, facing the way it travels.
                    Vector3d toRift = new Vector3d(far.exit().centre()).sub(emergeCentre);
                    assertEquals(hullLength * 0.5D + FlightGeometry.TRANSIT_MARGIN * 0.5D, toRift.dot(approach), 1.0e-6D, at);
                    assertEquals(toRift.length(), toRift.dot(approach), 1.0e-6D, "the rift stands beside the hull, " + at);
                    assertEquals(1.0D, far.exit().normal().dot(approach), EPS, at);

                    // And the bow ends along the facing either way: backing in changes the travel, not the lie.
                    assertEquals(1.0D, arrival.transform(new Vector3d(0, 0, 1)).dot(box.facing()), 1.0e-9D, at);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the attitude held

    /** The hull keeps the attitude it left at until the crossing. Held at its arrival one, it spins at its mooring. */
    @Test
    void theHullIsHeldAsItLeftUntilTheCrossingAndAsItArrivesAfter() {
        Quaterniond departure = new Quaterniond().rotateY(0.3D);
        Quaterniond arrival = new Quaterniond().rotateY(2.1D);
        for (WarpFlight.Stage stage : WarpFlight.Stage.values()) {
            boolean crossed = stage == WarpFlight.Stage.BREACH || stage == WarpFlight.Stage.EMERGE;
            assertSame(crossed ? arrival : departure, WarpFlight.attitude(stage, departure, arrival), stage.name());
        }
    }

    /** The drive holds what the flight says it holds at this stage - read off the source, as the wiring tests are. */
    @Test
    void theDriveAsksTheFlightWhichAttitudeItHolds() throws Exception {
        String drive = Files.readString(Path.of(
                "src/main/java/uk/co/iceconchy/aerowarptics/drive/RiftDriveBlockEntity.java"));
        assertTrue(drive.contains("AttitudeHold.hold(airship, current.attitude())"),
                "the drive no longer holds the flight's attitude for its stage");
        assertFalse(drive.contains("AttitudeHold.hold(airship, current.arrivalOrientation())"),
                "the drive holds a hull at its arrival attitude from the start: a turned flight spins it at its mooring");
    }

    // ------------------------------------------------------------------ saved

    private static CompoundTag rift(Vector3d centre, Vector3d normal) {
        return new WarpFlight.Rift(centre, normal, 9.0D).save();
    }

    private static CompoundTag vec(double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        return tag;
    }

    /** A flight saved before one could turn - which is every flight in every world there is - turns nothing. */
    @Test
    void aFlightSavedBeforeTurnsLeftAsItArrives() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Stage", "CORRIDOR");
        tag.put("Entry", rift(new Vector3d(0, 80, 0), new Vector3d(0, 0, 1)));
        tag.put("Exit", rift(new Vector3d(500, 80, 0), new Vector3d(0, 0, 1)));
        tag.put("Arrival", vec(500, 80, 60));
        tag.put("EmergeStart", vec(500, 80, 10));
        Quaterniond attitude = new Quaterniond().rotateY(1.1D);
        tag.putDouble("OrientX", attitude.x);
        tag.putDouble("OrientY", attitude.y);
        tag.putDouble("OrientZ", attitude.z);
        tag.putDouble("OrientW", attitude.w);
        tag.putDouble("TransitRun", 30.0D);
        tag.putDouble("HullSpan", 26.0D);
        tag.putDouble("HullRadius", 8.0D);
        tag.putInt("TransitTicks", 20);
        tag.putInt("CorridorTicks", 60);
        tag.putInt("EmergeTicks", 40);
        tag.putInt("ApproachLimit", 200);

        WarpFlight old = WarpFlight.load(tag);
        assertNotNull(old);
        assertFalse(old.turns());
        assertEquals(old.arrivalOrientation(), old.departureOrientation());
        assertEquals(old.arrivalOrientation(), old.attitude());
        assertFalse(old.save(null).contains("DepartW"), "an unturned flight grew a departure attitude on saving");

        // And one that does turn comes back turning, mid-corridor, still held as it left.
        Quaterniond departure = new Quaterniond().rotateY(-0.4D);
        tag.putDouble("DepartX", departure.x);
        tag.putDouble("DepartY", departure.y);
        tag.putDouble("DepartZ", departure.z);
        tag.putDouble("DepartW", departure.w);
        WarpFlight turned = WarpFlight.load(WarpFlight.load(tag).save(null));
        assertTrue(turned.turns());
        assertEquals(departure, turned.departureOrientation());
        assertEquals(departure, turned.attitude());
        assertEquals(attitude, turned.arrivalOrientation());
    }
}
