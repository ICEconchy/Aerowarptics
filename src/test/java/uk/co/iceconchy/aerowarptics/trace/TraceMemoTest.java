package uk.co.iceconchy.aerowarptics.trace;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trace's memory of what it last said, and the header's reach table.
 *
 * <p>The memory is what keeps a chart that refreshes every two seconds from writing the same line every
 * two seconds. Get it wrong one way and the log floods; the other way, and the one refusal that mattered
 * is swallowed because it looked like one from an hour ago.
 */
class TraceMemoTest {

    @Test
    void theFirstReportOnASubjectIsAlwaysNews() {
        TraceMemo memo = new TraceMemo(8);
        assertTrue(memo.changed("drive", "charging"));
    }

    @Test
    void sayingTheSameThingAgainIsNot() {
        TraceMemo memo = new TraceMemo(8);
        memo.changed("drive", "charging");
        assertFalse(memo.changed("drive", "charging"));
        assertFalse(memo.changed("drive", "charging"));
    }

    @Test
    void aChangeIsNewsAndSoIsChangingBack() {
        TraceMemo memo = new TraceMemo(8);
        memo.changed("access", "refused");
        assertTrue(memo.changed("access", "ok"));
        // The case settled() exists for: a refusal matching one from before is still news, because the
        // subject was fine in between.
        assertTrue(memo.changed("access", "refused"));
    }

    @Test
    void subjectsAreRememberedSeparately() {
        TraceMemo memo = new TraceMemo(8);
        memo.changed("a", "same");
        assertTrue(memo.changed("b", "same"), "the same words about a different subject are news");
        assertFalse(memo.changed("a", "same"));
    }

    @Test
    void theLeastRecentlyTouchedSubjectIsForgottenFirst() {
        TraceMemo memo = new TraceMemo(2);
        memo.changed("old", "x");
        memo.changed("kept", "y");
        memo.changed("kept", "y");
        memo.changed("new", "z");
        assertEquals(2, memo.size());
        assertTrue(memo.changed("old", "x"), "forgotten, so heard in full again");
        assertFalse(memo.changed("new", "z"));
    }

    @Test
    void clearingMakesEverythingNewsAgain() {
        TraceMemo memo = new TraceMemo(8);
        memo.changed("drive", "charging");
        memo.clear();
        assertTrue(memo.changed("drive", "charging"));
    }

    @Test
    void theReachTableShowsWhereAHeavyHullFallsShortOfTheRange() {
        WarpCost.Formula mkI = new WarpCost.Formula(0.20D, 0.00004D, 0.15D, 40_000.0D, 1.0D, 128.0D, 4_000.0D);
        String line = TraceReport.reachLine("mk_i", mkI);
        assertTrue(line.startsWith("tier mk_i: range 128-4000"), line);
        assertTrue(line.contains(" 0=4000"), line);
        assertTrue(line.contains(" 100k=4000"), line);
        assertTrue(line.contains(" 200k=1250"), "the reach falls short of the range: " + line);
        assertTrue(line.contains(" 400k=NONE"), "too heavy to jump at all: " + line);
    }

    @Test
    void anUnlimitedRangeReadsAsUnlimited() {
        WarpCost.Formula free = new WarpCost.Formula(0.0D, 0.0D, 0.0D, 40_000.0D, 100.0D, 0.0D,
                Double.POSITIVE_INFINITY);
        assertTrue(TraceReport.reachLine("creative", free).contains("800k=unlimited"));
    }
}
