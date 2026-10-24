package uk.co.iceconchy.aerowarptics.collapse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who stands in a Collapse Cradle's ring, and the motes drawn in from them.
 *
 * <p>The cradle, a stabiliser's goggles and every client's motes all ask {@link CradleRing}, so what is held here
 * is that the one answer is the right one: a depot counts only on the cradle's own level, a cradle links to eight
 * and a ninth never takes a link off one of them, and a mote is something a player can watch drift from a block
 * into the machine without it jumping, blinking or running the wrong way.
 */
class CradleRingTest {

    private static final int DEPOT_REACH = 3;
    private static final int STABILISER_REACH = 6;
    private static final double[] CENTRE = {0.0D, CollapseChoreography.CENTRE, 0.0D};

    private static final CradleRing.Sight EVERYWHERE = (dx, dy, dz) -> true;

    private static double distance(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** Whether an offset is one of the cradle's own twenty-seven blocks, where nothing else can stand. */
    private static boolean insideTheMachine(int dx, int dy, int dz) {
        return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= 0 && dy <= 2;
    }

    /** A world of depots: whatever has been set down so far. */
    private static CradleRing.Sight standing(List<int[]> depots) {
        return (dx, dy, dz) -> CradleRing.holds(depots, new int[]{dx, dy, dz});
    }

    // ------------------------------------------------------------------ who is in it

    @Test
    void aDepotCountsOnlyOnTheCradlesOwnLevelAndWithinReach() {
        assertTrue(CradleRing.offers(3, 0, -3, DEPOT_REACH), "a depot at the corner of the reach did not count");
        assertFalse(CradleRing.offers(4, 0, 0, DEPOT_REACH), "a depot a block past the reach counted");
        assertFalse(CradleRing.offers(2, 1, 0, DEPOT_REACH), "a depot a block up counted");
        assertFalse(CradleRing.offers(2, -1, 0, DEPOT_REACH), "a depot a block down counted");
        assertFalse(CradleRing.offers(0, 0, 0, DEPOT_REACH), "the master's own block counted as a depot");
    }

    @Test
    void aStabiliserCountsAnywhereInTheCube() {
        assertTrue(CradleRing.steadies(6, -6, 6, STABILISER_REACH), "a stabiliser at the corner of the cube did not count");
        assertFalse(CradleRing.steadies(7, 0, 0, STABILISER_REACH));
        assertFalse(CradleRing.steadies(0, 7, 0, STABILISER_REACH));
        assertFalse(CradleRing.steadies(0, 0, -7, STABILISER_REACH));
    }

    @Test
    void theWalkAsksOnlyWhereTheRuleAllowsAndFindsAllOfIt() {
        List<int[]> asked = new ArrayList<>();
        List<int[]> cage = CradleRing.stabilisers(STABILISER_REACH, (dx, dy, dz) -> {
            asked.add(new int[]{dx, dy, dz});
            return true;
        });
        int side = 2 * STABILISER_REACH + 1;
        assertEquals(side * side * side, cage.size(), "a stabiliser somewhere in the cube was not counted");
        for (int[] offset : asked) {
            assertTrue(CradleRing.steadies(offset[0], offset[1], offset[2], STABILISER_REACH),
                    "the walk looked past the cradle's reach");
        }

        asked.clear();
        CradleRing.depots(DEPOT_REACH, (dx, dy, dz) -> {
            asked.add(new int[]{dx, dy, dz});
            return false;
        });
        assertEquals((2 * DEPOT_REACH + 1) * (2 * DEPOT_REACH + 1) - 1, asked.size(),
                "the walk for depots did not cover the square, less the master");
        for (int[] offset : asked) {
            assertTrue(CradleRing.offers(offset[0], offset[1], offset[2], DEPOT_REACH),
                    "the walk looked for a depot where one would not count");
        }
    }

    /** A cradle saved before it remembered its depots remembers none, and links as it always did. */
    @Test
    void aCradleThatRemembersNoneLinksToTheNearestEightNearestFirst() {
        List<int[]> ring = CradleRing.depots(DEPOT_REACH, EVERYWHERE);
        assertEquals(CradleRing.MOST_DEPOTS, ring.size());
        // With a depot on every block, the nearest eight are the eight touching the master.
        int last = 0;
        for (int[] offset : ring) {
            assertTrue(Math.abs(offset[0]) <= 1 && Math.abs(offset[2]) <= 1, "a further depot was taken before a nearer");
            int squared = offset[0] * offset[0] + offset[2] * offset[2];
            assertTrue(squared >= last, "the depots are not nearest first");
            last = squared;
        }
        // Fewer than eight, and every one is linked.
        List<int[]> row = CradleRing.depots(DEPOT_REACH, (dx, dy, dz) -> dz == 0 && dx >= 2);
        assertEquals(2, row.size());
        assertArrayEquals(new int[]{2, 0, 0}, row.get(0));
        assertArrayEquals(new int[]{3, 0, 0}, row.get(1));
    }

    /**
     * The report this rule answers. A player set depots down round the machine one after another, and every new
     * one seemed to link, long past the eighth: the cradle took "the nearest eight", so a depot nearer than the
     * furthest took its link, with a flourish, while the one that lost it simply went dark. Sixteen depots, in the
     * order a player walks round the machine setting them down - and after the eighth, nothing changes.
     */
    @Test
    void settingDownMoreDepotsThanEightLinksNoMore() {
        int[][] walk = {
                {2, 0, 2}, {2, 0, 1}, {2, 0, 0}, {2, 0, -1}, {2, 0, -2}, {1, 0, -2}, {0, 0, -2}, {-1, 0, -2},
                {-2, 0, -2}, {-2, 0, -1}, {-2, 0, 0}, {-2, 0, 1}, {-2, 0, 2}, {-1, 0, 2}, {0, 0, 2}, {1, 0, 2}};
        List<int[]> standing = new ArrayList<>();
        List<int[]> linked = new ArrayList<>();
        for (int placed = 0; placed < walk.length; placed++) {
            standing.add(walk[placed]);
            List<int[]> now = CradleRing.linked(linked, DEPOT_REACH, standing(standing));
            if (placed < CradleRing.MOST_DEPOTS) {
                assertEquals(placed + 1, now.size(), "a depot set down with a place free did not link");
                assertArrayEquals(walk[placed], now.get(placed), "a new depot did not take the next place");
            } else {
                assertTrue(CradleRing.same(linked, now), "depot " + (placed + 1) + " took a link, or cost one");
            }
            linked = now;
        }
        for (int i = 0; i < CradleRing.MOST_DEPOTS; i++) {
            assertArrayEquals(walk[i], linked.get(i), "the eight linked are not the first eight set down");
        }
    }

    @Test
    void aLinkedDepotKeepsItsPlaceHoweverNearAnotherIsSetDown() {
        // Eight at the edge of the reach, then one right against the machine.
        List<int[]> far = new ArrayList<>(List.of(new int[]{3, 0, 3}, new int[]{3, 0, -3}, new int[]{-3, 0, 3},
                new int[]{-3, 0, -3}, new int[]{3, 0, 0}, new int[]{-3, 0, 0}, new int[]{0, 0, 3}, new int[]{0, 0, -3}));
        List<int[]> linked = CradleRing.linked(List.of(), DEPOT_REACH, standing(far));
        assertEquals(CradleRing.MOST_DEPOTS, linked.size());
        List<int[]> standing = new ArrayList<>(far);
        standing.add(new int[]{2, 0, 0});
        List<int[]> after = CradleRing.linked(linked, DEPOT_REACH, standing(standing));
        assertTrue(CradleRing.same(linked, after), "a nearer depot took a link off one already linked");
        assertFalse(CradleRing.holds(after, new int[]{2, 0, 0}));
    }

    @Test
    void aPlaceThatComesFreeGoesToTheNearestDepotWithoutOne() {
        List<int[]> standing = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            standing.add(new int[]{dx, 0, 2});
            standing.add(new int[]{dx, 0, -2});
        }
        List<int[]> linked = CradleRing.linked(List.of(), DEPOT_REACH, standing(standing));
        assertEquals(CradleRing.MOST_DEPOTS, linked.size());
        // The two it left out are corners, the furthest. Break a linked one, and the nearer corner takes its place.
        int[] broken = linked.get(0);
        standing.removeIf(offset -> offset[0] == broken[0] && offset[2] == broken[2]);
        List<int[]> after = CradleRing.linked(linked, DEPOT_REACH, standing(standing));
        assertEquals(CradleRing.MOST_DEPOTS, after.size(), "a free place was not given out");
        assertFalse(CradleRing.holds(after, broken), "a depot that is gone is still linked");
        for (int i = 1; i < linked.size(); i++) {
            assertArrayEquals(linked.get(i), after.get(i - 1), "a depot lost its place when another was broken");
        }
        int[] taken = after.get(after.size() - 1);
        assertFalse(CradleRing.holds(linked, taken), "the free place went to a depot that already had one");
        assertEquals(8, taken[0] * taken[0] + taken[2] * taken[2], "the free place did not go to a depot that had none");
    }

    @Test
    void aDepotOutOfReachLosesItsPlaceAndADamagedMemoryIsMended() {
        // The server's reach was turned down: what is past it now is let go.
        List<int[]> wide = List.of(new int[]{3, 0, 0}, new int[]{2, 0, 0});
        List<int[]> narrowed = CradleRing.linked(wide, 2, EVERYWHERE);
        assertFalse(CradleRing.holds(narrowed, new int[]{3, 0, 0}), "a depot past the reach kept its link");
        assertArrayEquals(new int[]{2, 0, 0}, narrowed.get(0), "a depot in reach lost its place to the narrowing");

        // Doubles, a depot on another level, the master itself, and far too many: none of it gets through.
        List<int[]> damaged = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            damaged.add(new int[]{2, 0, 0});
            damaged.add(new int[]{2, 1, 0});
            damaged.add(new int[]{0, 0, 0});
            damaged.add(new int[]{-3 + i % 7, 0, 3});
        }
        List<int[]> mended = CradleRing.linked(damaged, DEPOT_REACH, EVERYWHERE);
        assertEquals(CradleRing.MOST_DEPOTS, mended.size());
        for (int i = 0; i < mended.size(); i++) {
            int[] offset = mended.get(i);
            assertTrue(CradleRing.offers(offset[0], offset[1], offset[2], DEPOT_REACH), "a depot that cannot count is linked");
            assertFalse(CradleRing.holds(mended.subList(0, i), offset), "one depot holds two places");
        }
    }

    // ------------------------------------------------------------------ the pull

    @Test
    void aLineRunsFromItsBlockToTheRimOfTheRings() {
        for (int dx = -STABILISER_REACH; dx <= STABILISER_REACH; dx++) {
            for (int dy = -STABILISER_REACH; dy <= STABILISER_REACH; dy++) {
                for (int dz = -STABILISER_REACH; dz <= STABILISER_REACH; dz++) {
                    if (insideTheMachine(dx, dy, dz)) {
                        continue;
                    }
                    assertLine(CradleRing.stabiliserEnd(new int[]{dx, dy, dz}), 0.0D);
                    if (CradleRing.offers(dx, dy, dz, DEPOT_REACH)) {
                        assertLine(CradleRing.depotEnd(new int[]{dx, dy, dz}), CradleRing.DEPOT_BOW);
                    }
                }
            }
        }
    }

    private static void assertLine(double[] from, double bow) {
        assertArrayEquals(from, CradleRing.thread(from, 0.0D, bow), 1.0e-9D, "a line does not start at its block");
        double[] end = CradleRing.thread(from, 1.0D, bow);
        assertEquals(CradleRing.HOLD, distance(end, CENTRE), 1.0e-9D, "a line does not end on the rim of the rings");
        // The block is outside the rim, and the line meets the rim on the block's own side of it: nothing is drawn
        // through the armillary to the far side, nor backwards out of the machine.
        assertTrue(distance(from, CENTRE) > CradleRing.HOLD, "a block in the ring is inside the rings");
        assertEquals(distance(from, CENTRE) - CradleRing.HOLD, distance(from, end), 1.0e-9D,
                "a line does not meet the machine on the side its block is on");
        assertEquals(distance(from, end), CradleRing.length(from), 1.0e-9D);
        for (int step = 0; step <= 20; step++) {
            double t = step / 20.0D;
            double[] straight = CradleRing.thread(from, t, 0.0D);
            double[] bowed = CradleRing.thread(from, t, bow);
            assertTrue(bowed[1] >= straight[1] - 1.0e-9D, "a bowed line sags");
            // A mote leaves the middle of its block, is never far from its line, and arrives on the rim.
            for (double turn = 0.0D; turn < 1.0D; turn += 0.25D) {
                double[] mote = CradleRing.mote(from, t, bow, turn);
                assertTrue(distance(mote, bowed) <= CradleRing.SWIRL + 1.0e-9D, "a mote strayed from its line");
                if (step == 0 || step == 20) {
                    assertArrayEquals(bowed, mote, 1.0e-9D, "a mote does not start at its block, or end on the rim");
                }
            }
        }
    }

    @Test
    void aBlockAtTheVeryMiddleStillHasSomewhereToBeHeld() {
        double[] end = CradleRing.hold(CENTRE);
        for (double value : end) {
            assertTrue(Double.isFinite(value));
        }
        assertEquals(CradleRing.HOLD, distance(end, CENTRE), 1.0e-9D);
    }

    @Test
    void motesDriftInAtOnePaceHoweverLongTheLine() {
        assertEquals(1, CradleRing.motes(0.4D), "a short line carries no mote at all");
        assertEquals(3, CradleRing.motes(3.0D * CradleRing.SPACING));
        for (double length : new double[]{1.2D, 3.0D, 7.5D}) {
            double moved = CradleRing.drift(101.0D, length, 4, 0, 1) - CradleRing.drift(100.0D, length, 4, 0, 1);
            moved -= Math.floor(moved);
            assertEquals(CradleRing.DRIFT, moved * length, 1.0e-9D, "a mote on a line of " + length + " drifts at its own pace");
        }
        // The motes of one line are strung evenly along it, and no two lines keep step.
        int motes = CradleRing.motes(6.4D);
        for (int mote = 0; mote + 1 < motes; mote++) {
            double apart = CradleRing.drift(0.0D, 6.4D, 2, mote + 1, motes) - CradleRing.drift(0.0D, 6.4D, 2, mote, motes);
            assertEquals(1.0D / motes, apart - Math.floor(apart), 1.0e-9D);
        }
        for (int line = 0; line < 80; line++) {
            double apart = Math.abs(CradleRing.drift(0.0D, 4.0D, line, 0, 1) - CradleRing.drift(0.0D, 4.0D, line + 1, 0, 1));
            assertTrue(Math.min(apart, 1.0D - apart) > 0.2D, "two neighbouring lines drift in step");
        }
    }

    /**
     * A hundred samples a tick, on a short bowed line and a long straight one: a mote only ever goes in towards the
     * machine, never moves further in a hundredth of a tick than a thing drifting could, and where it goes round
     * again - the one place it is somewhere else all at once - there is nothing of it to see.
     */
    @Test
    void aMoteNeverJumpsWhereItCanBeSeen() {
        double[][] ends = {CradleRing.depotEnd(new int[]{2, 0, 0}), CradleRing.stabiliserEnd(new int[]{-5, 3, 6})};
        double[] bows = {CradleRing.DEPOT_BOW, 0.0D};
        for (int which = 0; which < ends.length; which++) {
            double[] from = ends[which];
            double length = CradleRing.length(from);
            double turn = CradleRing.turn(which, 0);
            double lastT = CradleRing.drift(0.0D, length, which, 0, 1);
            double[] last = CradleRing.mote(from, lastT, bows[which], turn);
            boolean seen = false;
            for (double time = 0.01D; time < 3.0D * length / CradleRing.DRIFT; time += 0.01D) {
                double t = CradleRing.drift(time, length, which, 0, 1);
                double[] now = CradleRing.mote(from, t, bows[which], turn);
                double drawn = CradleRing.drawn(t);
                assertTrue(drawn >= 0.0D && drawn <= 1.0D);
                if (t < lastT) {
                    assertTrue(lastT > 0.99D && t < 0.01D, "a mote ran back towards its block");
                    assertTrue(CradleRing.drawn(lastT) < 0.01D && drawn < 0.01D, "a mote went round again in plain sight");
                } else {
                    assertTrue(distance(last, now) < 0.005D, "a mote jumped at " + time);
                    assertTrue(Math.abs(drawn - CradleRing.drawn(lastT)) < 0.01D, "a mote blinked at " + time);
                }
                seen |= drawn == 1.0D;
                last = now;
                lastT = t;
            }
            assertTrue(seen, "a mote is never whole");
        }
        assertEquals(0.0D, CradleRing.drawn(0.0D), 0.0D);
        assertEquals(0.0D, CradleRing.drawn(1.0D), 0.0D);
    }

    @Test
    void theMotesFadeRatherThanCut() {
        double shown = 0.0D;
        int ticks = 0;
        while (shown < 1.0D) {
            double next = CradleRing.shown(shown, true);
            assertTrue(next > shown && next - shown <= CradleRing.FADE + 1.0e-9D, "the motes cut in");
            shown = next;
            assertTrue(++ticks <= 20, "the motes never came");
        }
        assertEquals(1.0D, CradleRing.shown(shown, true), 0.0D, "the motes are drawn past whole");
        ticks = 0;
        while (shown > 0.0D) {
            double next = CradleRing.shown(shown, false);
            assertTrue(next < shown && shown - next <= CradleRing.FADE + 1.0e-9D, "the motes cut out");
            shown = next;
            assertTrue(++ticks <= 20, "the motes never went");
        }
        assertEquals(0.0D, CradleRing.shown(shown, false), 0.0D, "the motes are drawn at less than nothing");
    }
}
