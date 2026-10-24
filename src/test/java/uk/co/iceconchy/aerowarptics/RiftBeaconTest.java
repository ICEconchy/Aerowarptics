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
        assertFalse(WarpFailure.SHIP_OUT_OF_REACH.penalised(),
                "a ship that could not be brought back to be asked has not flown anywhere to pay for");
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
        // simply no longer the last ones. The most recent is a destination under a Rift Ward with no room at
        // its rim. Before it a ship tied down by something the warp would leave behind, and before that the
        // beacon's own again: a ship it reached for and could not bring back. Before that the two flotilla
        // reasons, with the Rift Coupler; before them the two dimension reasons, with the crossing that
        // rebuilds a hull.
        // And the four a station refuses with came after all of those, with the Berth; then a rope that
        // cannot cross into another dimension.
        assertEquals(WarpFailure.DIMENSION_ROPED, all[all.length - 1]);
        assertEquals(WarpFailure.BERTH_OBSTRUCTED, all[all.length - 2]);
        assertEquals(WarpFailure.BERTH_FORBIDDEN, all[all.length - 3]);
        assertEquals(WarpFailure.BERTH_TOO_SMALL, all[all.length - 4]);
        assertEquals(WarpFailure.NO_FREE_BERTH, all[all.length - 5]);
        assertEquals(WarpFailure.DESTINATION_WARDED, all[all.length - 6]);
        assertEquals(WarpFailure.SHIP_FASTENED, all[all.length - 7]);
        assertEquals(WarpFailure.SHIP_OUT_OF_REACH, all[all.length - 8]);
        assertEquals(WarpFailure.FLOTILLA_NOT_READY, all[all.length - 9]);
        assertEquals(WarpFailure.FOLD_CALLED_OFF, all[all.length - 10]);
        assertEquals(WarpFailure.DIMENSION_TOO_TALL, all[all.length - 11]);
        assertEquals(WarpFailure.DIMENSION_NO_ROOM, all[all.length - 12]);
        assertEquals(WarpFailure.ARRIVAL_UNPROVEN, all[all.length - 13]);
        assertEquals(WarpFailure.LAUNCH_UNPROVEN, all[all.length - 14]);
        assertEquals(WarpFailure.ARRIVAL_NOT_LOADED, all[all.length - 15]);
        assertEquals(WarpFailure.NO_CLEAR_LAUNCH, all[all.length - 16]);
        assertEquals(WarpFailure.BEACON_DRIVE_MISSING, all[all.length - 17]);
        assertEquals(WarpFailure.BEACON_UNBOUND, all[all.length - 18]);
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

    /**
     * That a summon the drive refuses is said to whoever summoned.
     *
     * <p>A drive's refusals are otherwise read off its console, which a summoner is never at. From 1.3.0
     * the beacon assumed {@code startWarp} told them, and it never did: a summon to somewhere too close,
     * or with the drive short of charge, did nothing at all, and read as a broken item. A tied-down ship
     * is the one refusal the drive announces itself, with what ties it and where, so saying it again
     * would only write over that.
     */
    @Test
    void aRefusedSummonIsToldToTheSummoner() {
        String item = read(SOURCE.resolve("beacon").resolve("RiftBeaconItem.java"));
        int complete = item.indexOf("static boolean complete(");
        assertTrue(complete > 0, "complete has been renamed or removed");
        String body = item.substring(complete, item.indexOf("\n    }", complete));
        int refused = body.indexOf("if (result.isFailure())");
        assertTrue(refused > 0, "the summon no longer looks at the drive's answer");
        String branch = body.substring(refused, body.indexOf("return true;", refused));
        assertTrue(branch.contains("new ClientboundWarpFeedbackPacket(result)"),
                "a summon the drive refuses is silent to the player who made it");
        assertTrue(branch.contains("result != WarpFailure.SHIP_FASTENED"),
                "a tied-down ship's notice, which says where it is tied, is written over by a bare one");
    }

    /**
     * That a beacon aimed at a ship's deck summons to where the deck is, not to its plot.
     *
     * <p>Sable's ray hits a ship's blocks and answers in the ship's plot grid, so a beacon pointed at a deck
     * asked for a spot twenty million blocks out and was refused as too far. Only the wiring can be checked
     * here - the conversion itself is a Sable pose, which needs a running game.
     */
    @Test
    void aShipsDeckIsAimedAtWhereItIsOnTheMap() {
        String item = read(SOURCE.resolve("beacon").resolve("RiftBeaconItem.java"));
        int aim = item.indexOf("private InteractionResult aim(");
        assertTrue(aim > 0, "aim has been renamed or removed");
        assertTrue(item.substring(aim, item.indexOf("\n    }", aim)).contains("onTheMap("),
                "the aimed-at block goes to the drive without being taken out of a ship's plot");
        int map = item.indexOf("private static BlockPos onTheMap(");
        assertTrue(map > 0, "onTheMap has been renamed or removed");
        String conversion = item.substring(map, item.indexOf("\n    }", map));
        assertTrue(conversion.contains("Airship.containing(") && conversion.contains(".toWorld("),
                "onTheMap no longer asks whether the block is a ship's and converts it through its pose");
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
