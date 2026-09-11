package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.beacon.RiftBeaconRules;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Rift Beacon refuses, and in what order.
 *
 * <p>The order is the substance of this class. Every one of these refusals sends the holder off to
 * do something different - bind the thing, go home, rebuild the ship - so a beacon that reports the
 * second-most-relevant problem is worse than one that reports nothing.
 */
class RiftBeaconTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "uk", "co", "iceconchy",
            "aerowarptics");

    @Test
    void aBoundBeaconWithItsShipStillAfloatMaySummon() {
        assertEquals(WarpFailure.NONE, RiftBeaconRules.check(true, true, true, true));
    }

    @Test
    void anUnboundBeaconSaysSoRatherThanHuntingForADrive() {
        assertEquals(WarpFailure.BEACON_UNBOUND, RiftBeaconRules.check(false, true, true, true));
    }

    @Test
    void beingUnboundIsReportedEvenWhenEverythingElseIsAlsoWrong() {
        // A fresh beacon carried into the Nether: the only useful thing to say is "bind it".
        assertEquals(WarpFailure.BEACON_UNBOUND, RiftBeaconRules.check(false, false, false, false));
    }

    @Test
    void aBeaconBoundInAnotherDimensionIsOutOfReachRatherThanBroken() {
        // Deliberately with driveFound false, which is what the lookup returns from the wrong
        // dimension. Reporting a missing drive here would send somebody home to look for one that
        // is exactly where they left it.
        assertEquals(WarpFailure.DIMENSION_UNSUPPORTED,
                RiftBeaconRules.check(true, false, false, false));
    }

    @Test
    void aDriveThatIsNoLongerThereIsNamedAsSuch() {
        assertEquals(WarpFailure.BEACON_DRIVE_MISSING, RiftBeaconRules.check(true, true, false, false));
    }

    @Test
    void aDriveNoLongerPartOfAShipReportsTheShipRatherThanTheDrive() {
        assertEquals(WarpFailure.NO_AIRSHIP, RiftBeaconRules.check(true, true, true, false));
    }

    @Test
    void noRefusalFromABeaconCostsThePilotCharge() {
        // A beacon never starts a warp when it refuses, so none of its reasons may be the kind that
        // burns charge and a cooldown on the way out.
        assertFalse(WarpFailure.BEACON_UNBOUND.penalised());
        assertFalse(WarpFailure.BEACON_DRIVE_MISSING.penalised());
    }

    /**
     * That nothing was inserted into the middle of the failure list.
     *
     * <p>{@code WarpFailure} goes over the wire as an ordinal. Slipping a new constant in above an
     * existing one renames every reason below it on any client that has not updated in step, which
     * shows up as a warp failing for a plausible but completely wrong reason - the hardest kind of
     * bug to believe a report of.
     */
    @Test
    void theBeaconsReasonsWereAppendedRatherThanInserted() {
        WarpFailure[] all = WarpFailure.values();
        // Each wave of new reasons was appended at the very end - the only safe place to add a
        // wire-ordinal constant. Everything before them kept its ordinal; the older entries are
        // simply no longer the last ones. LAUNCH_UNPROVEN is the most recent arrival, added when
        // "could not prove the corridor clear" was split away from "the corridor is blocked".
        assertEquals(WarpFailure.LAUNCH_UNPROVEN, all[all.length - 1]);
        assertEquals(WarpFailure.ARRIVAL_NOT_LOADED, all[all.length - 2]);
        assertEquals(WarpFailure.NO_CLEAR_LAUNCH, all[all.length - 3]);
        assertEquals(WarpFailure.BEACON_DRIVE_MISSING, all[all.length - 4]);
        assertEquals(WarpFailure.BEACON_UNBOUND, all[all.length - 5]);
        assertEquals(0, WarpFailure.NONE.ordinal(), "NONE must stay first");
    }

    /**
     * That a summon goes through the drive rather than around it.
     *
     * <p>The point of the beacon is to relax exactly one rule - standing on the ship - and the way
     * that stays true is that the summon path calls {@code startWarp} like everything else. A future
     * change that reaches past it to place a hull directly would take the range check, the cost, the
     * one-warp-per-hull lock and the search for somewhere it fits with it, silently.
     */
    @Test
    void summoningIsAnOrdinaryWarpAndNotAShortcutAroundOne() {
        String drive = read(SOURCE.resolve("drive").resolve("RiftDriveBlockEntity.java"));
        int summon = drive.indexOf("public WarpFailure summonTo(");
        assertTrue(summon > 0, "summonTo has been renamed or removed");
        String body = drive.substring(summon, drive.indexOf("\n    }", summon));
        assertTrue(body.contains("startWarp("),
                "summonTo no longer fires the warp through startWarp, so it no longer inherits its checks");
    }

    /**
     * That binding is still the moment permission is checked.
     *
     * <p>This is the load-bearing half of the design. If the bind path ever stops asking
     * {@code validatePlayer}, the beacon becomes a way to move any ship you can walk up to.
     */
    @Test
    void bindingAsksTheSamePermissionQuestionEverythingElseAsks() {
        String item = read(SOURCE.resolve("beacon").resolve("RiftBeaconItem.java"));
        assertTrue(item.contains("WarpValidator.validatePlayer("),
                "binding no longer checks whether the player may command the drive");
    }

    /** No packet in this feature may bound a list on its own; see {@code PacketListCapTest}. */
    @Test
    void theBeaconAddedNoNewUnboundedLists() {
        try (Stream<Path> files = Files.walk(SOURCE.resolve("network"))) {
            List<String> offenders = new ArrayList<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : read(file).split("\n")) {
                    if (line.contains("ByteBufCodecs.list(") && !line.contains("MAX_ROWS")
                            && !line.contains("MAX_LISTED")) {
                        offenders.add(file.getFileName() + ": " + line.strip());
                    }
                }
            }
            assertTrue(offenders.isEmpty(), offenders.toString());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
