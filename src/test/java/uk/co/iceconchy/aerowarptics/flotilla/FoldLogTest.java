package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a flotilla counts as assembled, when a fold is over, and what it earns.
 *
 * <p>The bell and the three advancements hang off these rules, and every one of them is a rule about
 * ships that have not arrived yet - which is exactly the kind of thing that works on the test server with
 * one escort and goes quietly wrong with a straggler.
 */
class FoldLogTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));

    private static FoldLog.Arrival arrival(UUID coupler, boolean echo) {
        return new FoldLog.Arrival(coupler, OVERWORLD, BlockPos.ZERO, List.of(), echo);
    }

    @Test
    void theFleetIsAssembledWhenTheLastShipInFormationIsOut() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        log.begin(fold, flagship, List.of(a, b), 0, false, 0L);

        assertNull(log.arrived(fold, arrival(flagship, false)).assembledNow());
        assertNull(log.arrived(fold, arrival(a, false)).assembledNow());
        FoldLog.Outcome last = log.arrived(fold, arrival(b, false));
        assertNotNull(last.assembledNow(), "the bell rings when the last one is out");
        assertNotNull(last.finishedNow());
        assertEquals(0, log.open(), "and the fold is forgotten");
    }

    @Test
    void theFleetDoesNotWaitForAStraggler() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID steady = UUID.randomUUID();
        UUID loose = UUID.randomUUID();
        log.begin(fold, flagship, List.of(steady, loose), 0, false, 0L);
        log.shaken(fold, loose);

        log.arrived(fold, arrival(flagship, false));
        FoldLog.Outcome out = log.arrived(fold, arrival(steady, false));
        assertNotNull(out.assembledNow(), "assembled without the ship coming the long way round");
        assertTrue(out.assembledNow().stragglersDue());
        assertNull(out.finishedNow(), "but the fold is not over until it is out");

        FoldLog.Outcome late = log.arrived(fold, arrival(loose, true));
        assertNull(late.assembledNow(), "the bell does not ring twice");
        assertNotNull(late.straggler());
        assertNotNull(late.finishedNow());
    }

    @Test
    void aShipThatNeverWentDoesNotHoldTheBellBack() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID lost = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        log.begin(fold, flagship, List.of(lost, fine), 0, false, 0L);
        log.failed(fold, lost);
        log.arrived(fold, arrival(fine, false));
        FoldLog.Outcome out = log.arrived(fold, arrival(flagship, false));
        assertNotNull(out.assembledNow());
        assertFalse(out.finishedNow().nobodyLeftBehind());
        assertFalse(out.finishedNow().fleetInBeing());
    }

    @Test
    void theFleetIsNeverAssembledWithoutItsFlagship() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID escort = UUID.randomUUID();
        log.begin(fold, flagship, List.of(escort), 0, false, 0L);
        assertNull(log.arrived(fold, arrival(escort, false)).assembledNow());
        FoldLog.Outcome out = log.failed(fold, flagship);
        assertNull(out.assembledNow());
        assertNotNull(out.finishedNow(), "a fold whose flagship failed is still over");
    }

    @Test
    void nobodyLeftBehindWantsThreeShipsAllInTheirSlots() {
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        FoldLog.Fold clean = fly(flagship, List.of(a, b), 0, false, List.of());
        assertTrue(clean.nobodyLeftBehind());

        assertFalse(fly(flagship, List.of(a), 0, false, List.of()).nobodyLeftBehind(), "two ships is not a flotilla of three");
        assertFalse(fly(flagship, List.of(a, b), 1, false, List.of()).nobodyLeftBehind(), "a forced fold left someone");
        assertFalse(fly(flagship, List.of(a, b), 0, false, List.of(b)).nobodyLeftBehind(), "someone was shaken loose");
    }

    @Test
    void fleetInBeingWantsAFullFlotillaAllOut() {
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        assertTrue(fly(flagship, List.of(a), 0, true, List.of()).fleetInBeing());
        assertTrue(fly(flagship, List.of(a), 0, true, List.of(a)).fleetInBeing(), "a straggler still arrived");
        assertFalse(fly(flagship, List.of(a), 0, false, List.of()).fleetInBeing());
    }

    @Test
    void anUnknownFoldOrShipChangesNothing() {
        FoldLog log = new FoldLog();
        assertSame(FoldLog.Outcome.NOTHING, log.arrived(UUID.randomUUID(), arrival(UUID.randomUUID(), false)));
        UUID fold = UUID.randomUUID();
        log.begin(fold, UUID.randomUUID(), List.of(), 0, false, 0L);
        assertSame(FoldLog.Outcome.NOTHING, log.arrived(fold, arrival(UUID.randomUUID(), false)),
                "a ship that was not in the fold is not counted");
    }

    @Test
    void aFoldNobodyHearsFromIsForgotten() {
        FoldLog log = new FoldLog();
        log.begin(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), 0, false, 100L);
        log.expire(100L + FoldLog.FORGET_TICKS);
        assertEquals(1, log.open());
        log.expire(101L + FoldLog.FORGET_TICKS);
        assertEquals(0, log.open());
    }

    // ------------------------------------------------------------ one rift

    @Test
    void theFleetsOneRiftClosesBehindTheLastShipNotTheFirst() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        log.begin(fold, flagship, List.of(a, b), 0, false, 0L);
        log.share(fold, List.of(flagship, a, b));
        assertTrue(log.fold(fold).shared());

        // Closed by the first through, the entry would collapse round two hulls still inside its throat.
        assertFalse(log.throughEntry(fold, flagship));
        assertFalse(log.throughEntry(fold, b));
        assertTrue(log.throughEntry(fold, a), "the last one through closes it, whichever order they tick in");

        assertFalse(log.outOfExit(fold, a));
        assertFalse(log.outOfExit(fold, flagship));
        assertTrue(log.outOfExit(fold, b));
    }

    @Test
    void aCueTheFleetSharesIsSentOnce() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        log.begin(fold, flagship, List.of(a), 0, false, 0L);
        log.share(fold, List.of(flagship, a));

        // Every ship reaches these on the same tick. Told by each, a client stands one aperture on another.
        assertTrue(log.firstTo(fold, FoldLog.SharedCue.ENTRY_TRANSIT));
        assertFalse(log.firstTo(fold, FoldLog.SharedCue.ENTRY_TRANSIT));
        assertTrue(log.firstTo(fold, FoldLog.SharedCue.EXIT_OPEN), "each cue is its own");
        assertFalse(log.firstTo(fold, FoldLog.SharedCue.EXIT_OPEN));
        assertTrue(log.firstTo(UUID.randomUUID(), FoldLog.SharedCue.EXIT_TRANSIT),
                "a fold nobody remembers sends its cue rather than none at all");
    }

    @Test
    void aShipThatStopsShortIsNoLongerWaitedFor() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID lost = UUID.randomUUID();
        UUID going = UUID.randomUUID();
        log.begin(fold, flagship, List.of(lost, going), 0, false, 0L);
        log.share(fold, List.of(flagship, lost, going));

        assertFalse(log.throughEntry(fold, flagship));
        assertFalse(log.throughEntry(fold, going));
        // The entry was waiting only for this one: it closes as the ship gives up, not on its own clock.
        assertEquals(new FoldLog.Closing(true, false), log.leave(fold, lost));
        assertFalse(log.outOfExit(fold, flagship));
        assertTrue(log.outOfExit(fold, going), "and the exit no longer waits for it either");
    }

    @Test
    void aShipLostAfterTheCrossingOnlyLetsGoOfTheExit() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        log.begin(fold, flagship, List.of(a), 0, false, 0L);
        log.share(fold, List.of(flagship, a));

        assertFalse(log.throughEntry(fold, a));
        assertTrue(log.throughEntry(fold, flagship));
        assertFalse(log.outOfExit(fold, flagship));
        assertEquals(new FoldLog.Closing(false, true), log.leave(fold, a),
                "through the entry already, it has only the exit left to let go of");
    }

    @Test
    void aFailureAlsoLetsTheRiftGo() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID refused = UUID.randomUUID();
        log.begin(fold, flagship, List.of(refused), 0, false, 0L);
        log.share(fold, List.of(flagship, refused));
        log.failed(fold, refused);
        assertTrue(log.throughEntry(fold, flagship), "a ship that failed is not waited for");
    }

    @Test
    void aStragglerTearsItsOwnAndIsNotPartOfTheOneRift() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID loose = UUID.randomUUID();
        log.begin(fold, flagship, List.of(loose), 0, false, 0L);
        log.shaken(fold, loose);
        log.share(fold, List.of(flagship));

        assertEquals(FoldLog.Closing.NOTHING, log.leave(fold, loose));
        assertFalse(log.throughEntry(fold, loose), "its own rift is not the fleet's to close");
        assertTrue(log.throughEntry(fold, flagship));
    }

    @Test
    void aFoldOfARiftEachHasNothingSharedToClose() {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        UUID flagship = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        log.begin(fold, flagship, List.of(a), 0, false, 0L);
        assertFalse(log.fold(fold).shared());
        assertEquals(FoldLog.Closing.NOTHING, log.leave(fold, a));
    }

    /** Flies a whole fold to its end: every ship out, the shaken ones last. */
    private static FoldLog.Fold fly(UUID flagship, List<UUID> escorts, int left, boolean full, List<UUID> loose) {
        FoldLog log = new FoldLog();
        UUID fold = UUID.randomUUID();
        log.begin(fold, flagship, escorts, left, full, 0L);
        for (UUID escort : loose) {
            log.shaken(fold, escort);
        }
        FoldLog.Fold finished = log.arrived(fold, arrival(flagship, false)).finishedNow();
        for (UUID escort : escorts) {
            FoldLog.Outcome out = log.arrived(fold, arrival(escort, loose.contains(escort)));
            if (out.finishedNow() != null) {
                finished = out.finishedNow();
            }
        }
        assertNotNull(finished);
        return finished;
    }
}
