package uk.co.iceconchy.aerowarptics.berth;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the Berth is wired into the things it has to be asked by.
 *
 * <p>None of this throws when it is missing. A drive that does not ask a station puts a ship down loose in
 * the middle of one; one that takes its berth before its flight is certain leaves a slip standing empty and
 * unoffered; one that starts a sequence without casting off flies its run at the rift against a berth still
 * pulling it back. So the hook-up is read off the source, as the wards' is.
 */
class BerthWiringTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static String source(String path) {
        try {
            return Files.readString(SOURCES.resolve(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A method's text, from its signature to the next thing declared at the same depth. */
    private static String body(String source, String signature) {
        int from = source.indexOf(signature);
        assertTrue(from >= 0, "no longer declares: " + signature);
        int to = source.indexOf("\n    }\n", from);
        assertTrue(to > from, "could not find the end of: " + signature);
        return source.substring(from, to);
    }

    private static final String DRIVE = source("drive/RiftDriveBlockEntity.java");

    /** Past the wards, and then into a berth: a rift a ward turned is not put where the station is. */
    @Test
    void aStationIsAskedAfterTheWardsAndOnlyForARiftTheyLetThrough() {
        String past = body(DRIVE, "private WardedPlan planPastWards(");
        int wards = past.indexOf("Wards.turnAside(");
        int station = past.indexOf("stationAt(");
        int berth = past.indexOf("planBerth(");
        int search = past.indexOf("WarpFlight.plan(");
        assertTrue(wards >= 0 && station > wards, "a station is asked before the wards are, or not at all");
        assertTrue(past.contains("turn.turned() ? null : stationAt("), "a rift turned to a rim is still given a berth");
        assertTrue(berth > station && berth < search, "the ordinary arrival search runs before the station is asked");
        // And only where the rift comes out at the anchor itself, which a scattered one does not.
        assertTrue(body(DRIVE, "private WarpAnchor stationAt(").contains("anchor.pos().equals(target)"));
    }

    /** Asking changes nothing: the berth is taken beside the wards' commit, after the last thing that can refuse. */
    @Test
    void aBerthIsTakenOnlyOnceTheFlightIsCertain() {
        String begin = body(DRIVE, "private void beginWarp() {");
        int reserve = begin.indexOf("Berths.reserve(");
        assertTrue(reserve >= 0, "beginWarp never takes the berth it planned into");
        assertTrue(reserve > begin.lastIndexOf("abort("), "a berth is reserved before the last refusal");
        assertTrue(reserve > begin.indexOf("Wards.commit("));
        assertTrue(reserve < begin.indexOf("openRift("), "the rift opens before the berth is taken");

        String plan = body(DRIVE, "private WarpFlight.Plan planBerth(");
        assertFalse(plan.contains("Berths.reserve("), "planning a berth takes it");
        assertFalse(plan.contains("Berths.write("));
        assertTrue(plan.contains("Berths.offers("));
    }

    /** A berth is not a clearance check: the checks' switch skips the proof of the slip, never the berth. */
    @Test
    void theClearanceSwitchSkipsTheProofAndNotTheBerth() {
        String plan = body(DRIVE, "private WarpFlight.Plan planBerth(");
        int offers = plan.indexOf("Berths.offers(");
        int gate = plan.indexOf("if (clearanceChecks) {");
        int proof = plan.indexOf("Berths.proof(");
        assertTrue(gate > offers, "a drive with its checks off is offered no berth");
        assertTrue(proof > gate, "the slip is proved whatever the switch says");
        assertTrue(plan.contains("WarpFlight.planInto("), "a berth is planned as an ordinary, unturned arrival");
    }

    @Test
    void aFlightThatEndsGivesItsBerthBackOrIsMooredInIt() {
        assertTrue(body(DRIVE, "public void abort(WarpFailure reason) {").contains("releaseBerth();"),
                "an aborted flight keeps its berth reserved");
        assertTrue(body(DRIVE, "private void releaseBerth() {").contains("Berths.unreserve("));
        String complete = body(DRIVE, "private void completeWarp() {");
        assertTrue(complete.contains("Berths.moored("), "a ship that arrives in its berth is not moored");
        assertTrue(complete.indexOf("Berths.moored(") > complete.indexOf("AttitudeHold.release("),
                "the berth takes the ship before the flight has let go of it");
    }

    /** A berth makes no joint and never refuses a launch, so every way a sequence starts has to cast off. */
    @Test
    void everyWayADriveStartsCastsOff() {
        for (String start : new String[] {
                "private WarpFailure startWarp(WarpCourse course, @Nullable ServerPlayer player) {",
                "public WarpFailure followFold(FoldOrder order) {",
                "private WarpFailure startCruise(@Nullable ServerPlayer player) {"}) {
            String method = body(DRIVE, start);
            int castOff = method.indexOf("castOff(serverLevel, airship);");
            assertTrue(castOff >= 0, "does not cast off: " + start);
            assertTrue(castOff < method.indexOf("transition(RiftDriveState.DESTINATION_SELECTED)"));
            assertTrue(castOff > method.lastIndexOf("return reject("), "casts off a ship whose launch is then refused");
        }
        assertFalse(source("airship/Fastenings.java").contains("Berth"), "a berth is being read as a fastening");
    }

    /**
     * A fleet is berthed whole, as one decision taken at one moment: every escort's slip is reserved with the
     * flagship's, after the last refusal, and each escort flies into its own, turned.
     */
    @Test
    void aFleetIsBerthedAsOneDecision() {
        String plan = body(DRIVE, "private WarpFlight.Plan planBerth(");
        assertTrue(plan.indexOf("if (passage != null) {") < plan.indexOf("planFleetBerths("),
                "a fleet going through one rift is offered berths");
        String fleet = body(DRIVE, "private WarpFlight.Plan planFleetBerths(");
        assertTrue(fleet.contains("Berths.fleetOffers("));
        assertFalse(fleet.contains("Berths.reserve("), "planning a fleet's berths takes them");
        assertTrue(fleet.contains("struck.add("), "a fouled slip is not struck off, so the same one is offered again");

        String begin = body(DRIVE, "private void beginWarp() {");
        assertTrue(begin.indexOf("muster().berthed(fleetBerths)") < begin.indexOf("muster().flagshipPlanned("),
                "the escorts are told where they come out before the muster knows their berths");
        assertEquals(2, begin.split("Berths[.]reserve[(]", -1).length - 1, "the flagship's berth and its escorts'");
        assertTrue(begin.indexOf("fleetBerths.get(companion.ship().uuid())") > begin.lastIndexOf("abort("));

        String escort = body(DRIVE, "private void beginEscortWarp() {");
        assertTrue(escort.contains("foldBerth != null && passage == null"));
        assertTrue(escort.contains("WarpFlight.planInto(airship, foldBerth.centre(), foldBerth.attitude()"),
                "an escort bound for a berth is put down unturned");
        assertTrue(body(DRIVE, "public void berthFold(").contains("berthId = berth.berth().id();"),
                "an escort does not know which slip to give back or be moored in");
    }

    /** A crossing rebuilds the hull as it left; a flight that turns it has to turn it afterwards, crew and all. */
    @Test
    void aCrossingTurnsTheRebuiltHull() {
        String finish = body(DRIVE, "private boolean finishCrossing() {");
        int turn = finish.indexOf("current.turns() && !airship.relocate(current.emergenceOrigin(), current.arrivalOrientation()");
        assertTrue(turn >= 0, "a hull rebuilt in another dimension is never turned to its berth");
        assertTrue(turn < finish.indexOf("current.leftCorridor();"));
    }

    /** A ship given no berth is kept out of every slip. */
    @Test
    void theArrivalSearchKeepsOutOfSlips() {
        String find = source("warp/SafeArrival.java");
        assertTrue(find.contains("Berths.slips(destination)"));
        assertTrue(find.contains("Berths.inASlip(slips, worldBounds)"));
    }

    @Test
    void aStationsRefusalsCostThePilotNothing() {
        for (WarpFailure failure : new WarpFailure[] {WarpFailure.NO_FREE_BERTH, WarpFailure.BERTH_TOO_SMALL,
                WarpFailure.BERTH_FORBIDDEN, WarpFailure.BERTH_OBSTRUCTED}) {
            assertFalse(failure.penalised(), failure + " is refused before anything moves, and should be free");
        }
    }

    /** A moored ship is held from the record on the server's tick; the block's own tick holds nothing. */
    @Test
    void theBlockHoldsNothingItself() {
        String block = source("berth/BerthBlockEntity.java");
        assertFalse(block.contains("steerVelocity") || block.contains("holdShip") || block.contains("AttitudeHold"),
                "the berth's block holds a ship: unloaded, it would drop it");
    }
}
