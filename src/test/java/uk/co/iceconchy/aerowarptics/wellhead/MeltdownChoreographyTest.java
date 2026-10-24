package uk.co.iceconchy.aerowarptics.wellhead;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.WellheadLayout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A meltdown's scene held to what makes it a scene: nothing teleports at a beat, the ground goes inside the
 * detonation and nowhere else, and it ends with everything put away. And the two smaller things that hang off
 * it - how the ground it leaves fades, and where its machine's panel goes on the smallest screen.
 */
class MeltdownChoreographyTest {

    /** The scene's default length, and how finely each tick of it is looked at. */
    private static final int TICKS = 900;
    private static final int SAMPLES_A_TICK = 100;

    private static Map<String, DoubleUnaryOperator> curves() {
        Map<String, DoubleUnaryOperator> curves = new LinkedHashMap<>();
        curves.put("tear", p -> MeltdownChoreography.tear(p, 48.0D));
        curves.put("peel", MeltdownChoreography::peel);
        curves.put("pull", MeltdownChoreography::pull);
        curves.put("carve", MeltdownChoreography::carve);
        curves.put("front", MeltdownChoreography::front);
        curves.put("darkness", MeltdownChoreography::darkness);
        curves.put("whiteness", MeltdownChoreography::whiteness);
        curves.put("flash", MeltdownChoreography::flash);
        curves.put("whiteout", MeltdownChoreography::whiteout);
        curves.put("shake", MeltdownChoreography::shake);
        curves.put("funnel", MeltdownChoreography::funnel);
        curves.put("ring", MeltdownChoreography::ring);
        curves.put("ringStrength", MeltdownChoreography::ringStrength);
        return curves;
    }

    /** A curve that jumps is a piece that teleports on screen. A hundred samples a tick, across the whole scene. */
    @Test
    void noCurveJumpsAnywhere() {
        int steps = TICKS * SAMPLES_A_TICK;
        for (Map.Entry<String, DoubleUnaryOperator> curve : curves().entrySet()) {
            double largest = Math.max(1.0D, Math.abs(curve.getValue().applyAsDouble(0.5D)));
            double last = curve.getValue().applyAsDouble(0.0D);
            for (int step = 1; step <= steps; step++) {
                double now = curve.getValue().applyAsDouble(step / (double) steps);
                largest = Math.max(largest, Math.abs(now));
                // The flash is the fastest thing in it and comes in three or four ticks: nothing moves by more
                // than a hundredth of its own range in a hundredth of a tick.
                assertTrue(Math.abs(now - last) <= 0.01D * largest,
                        curve.getKey() + " jumps by " + Math.abs(now - last) + " at " + step / (double) steps);
                last = now;
            }
        }
    }

    @Test
    void itBeginsAndEndsWithEverythingPutAway() {
        for (Map.Entry<String, DoubleUnaryOperator> curve : curves().entrySet()) {
            if (curve.getKey().equals("tear")) {
                assertEquals(1.0D, curve.getValue().applyAsDouble(0.0D), 1.0e-9D, "the tear starts as it hung");
            } else {
                assertEquals(0.0D, curve.getValue().applyAsDouble(0.0D), 1.0e-9D, curve.getKey() + " at the start");
            }
        }
        for (String name : new String[]{"tear", "pull", "front", "darkness", "flash", "whiteout", "shake", "funnel",
                "ringStrength"}) {
            assertEquals(0.0D, curves().get(name).applyAsDouble(1.0D), 1.0e-9D, name + " at the end");
        }
        assertEquals(1.0D, MeltdownChoreography.carve(1.0D), 1.0e-9D);
    }

    @Test
    void theGroundGoesInsideTheDetonationAndNowhereElse() {
        assertEquals(0.0D, MeltdownChoreography.carve(MeltdownChoreography.HUSH_END));
        assertEquals(1.0D, MeltdownChoreography.carve(MeltdownChoreography.BLAST_END));
        double last = 0.0D;
        for (int step = 0; step <= 1000; step++) {
            double now = MeltdownChoreography.carve(step / 1000.0D);
            assertTrue(now >= last, "ground came back at " + step);
            last = now;
        }
        // The tear has gone by the time the ground starts to: there is nothing left to be seen eating it.
        assertEquals(0.0D, MeltdownChoreography.tear(MeltdownChoreography.HUSH_END, 48.0D), 1.0e-9D);
        assertEquals(1.0D, MeltdownChoreography.flash(MeltdownChoreography.HUSH_END + 0.004D), 1.0e-9D);
    }

    /** The outer shells are the great ones, so the edge slows as it reaches them: fast at the middle, slow at the rim. */
    @Test
    void theEdgeOfTheGroundsGoingSlowsAsItGrows() {
        double span = MeltdownChoreography.BLAST_END - MeltdownChoreography.HUSH_END;
        double early = MeltdownChoreography.carve(MeltdownChoreography.HUSH_END + span * 0.1D);
        double late = 1.0D - MeltdownChoreography.carve(MeltdownChoreography.BLAST_END - span * 0.1D);
        assertTrue(early > late * 5.0D, "the first tenth took " + early + " and the last " + late);
    }

    /** The dark is the edge of the ground's going while it goes, never ahead of it, and draws back afterwards. */
    @Test
    void theDarkFollowsTheGroundOutAndThenDrawsBack() {
        for (int step = 0; step <= 1000; step++) {
            double progress = step / 1000.0D;
            assertTrue(MeltdownChoreography.front(progress) <= MeltdownChoreography.carve(progress) + 1.0e-9D,
                    "the dark ran ahead of the ground at " + progress);
            if (progress >= MeltdownChoreography.HUSH_END && progress <= MeltdownChoreography.BLAST_END) {
                assertEquals(MeltdownChoreography.carve(progress), MeltdownChoreography.front(progress), 1.0e-9D);
            }
        }
        assertEquals(1.0D, MeltdownChoreography.front(MeltdownChoreography.BLAST_END), 1.0e-9D);
        assertEquals(0.0D, MeltdownChoreography.front(MeltdownChoreography.BLAST_END + 0.04D), 1.0e-9D);
        // And the white over the view is the flash's first second, gone well inside the detonation.
        assertEquals(0.0D, MeltdownChoreography.whiteout(MeltdownChoreography.HUSH_END + 0.04D), 1.0e-9D);
        assertTrue(MeltdownChoreography.HUSH_END + 0.04D < MeltdownChoreography.BLAST_END);
    }

    @Test
    void theHushIsStill() {
        double middle = (MeltdownChoreography.SWELL_END + MeltdownChoreography.HUSH_END) / 2.0D;
        assertTrue(MeltdownChoreography.hangs(middle));
        assertFalse(MeltdownChoreography.hangs(MeltdownChoreography.SWELL_END - 0.001D));
        assertFalse(MeltdownChoreography.hangs(MeltdownChoreography.HUSH_END));
        assertEquals(0.0D, MeltdownChoreography.pull(middle), 1.0e-9D, "something is still drawn in during the hush");
        assertEquals(0.0D, MeltdownChoreography.shake(middle), 1.0e-9D, "the camera still shakes during the hush");
        assertEquals(1.0D, MeltdownChoreography.darkness(middle), 1.0e-9D);
    }

    /** The heartbeat only ever quickens, runs only through the swell, and comes to a few dozen beats. */
    @Test
    void theHeartbeatOnlyEverQuickens() {
        double lastInterval = Double.MAX_VALUE;
        double lastCount = 0.0D;
        for (int tick = 0; tick <= TICKS; tick++) {
            double progress = tick / (double) TICKS;
            double interval = MeltdownChoreography.heartbeatInterval(progress);
            assertTrue(interval <= lastInterval + 1.0e-9D, "the heartbeat slowed at tick " + tick);
            lastInterval = interval;
            double count = MeltdownChoreography.heartbeats(progress, TICKS);
            assertTrue(count >= lastCount - 1.0e-9D, "a heartbeat was taken back at tick " + tick);
            // Never two beats in one tick: each is a ring, a thump and a kick of the camera.
            assertTrue(count - lastCount < 1.0D, "two heartbeats fell in tick " + tick);
            lastCount = count;
        }
        assertEquals(0.0D, MeltdownChoreography.heartbeats(MeltdownChoreography.BREACH_END, TICKS), 1.0e-9D);
        assertEquals(MeltdownChoreography.heartbeats(MeltdownChoreography.SWELL_END, TICKS),
                MeltdownChoreography.heartbeats(1.0D, TICKS), 1.0e-9D, "it beats on after the swell");
        assertTrue(lastCount > 12.0D && lastCount < 40.0D, lastCount + " heartbeats in a swell");
        assertEquals(MeltdownChoreography.HEARTBEAT_SLOW, MeltdownChoreography.heartbeatInterval(0.0D), 1.0e-9D);
        assertEquals(MeltdownChoreography.HEARTBEAT_FAST, MeltdownChoreography.heartbeatInterval(1.0D), 1.0e-9D);
    }

    @Test
    void theBeatsComeInOrderAndTheTearSwellsWithTheCrater() {
        MeltdownChoreography.Beat last = MeltdownChoreography.Beat.BREACH;
        for (int step = 0; step <= 1000; step++) {
            MeltdownChoreography.Beat now = MeltdownChoreography.Beat.at(step / 1000.0D);
            assertTrue(now.ordinal() >= last.ordinal());
            last = now;
        }
        assertEquals(MeltdownChoreography.Beat.OVER, last);
        double before = MeltdownChoreography.SWELL_END - 1.0e-6D;
        assertTrue(MeltdownChoreography.tear(before, 48.0D) > MeltdownChoreography.tear(before, 16.0D));
        assertEquals(48.0D * MeltdownChoreography.TEAR_SHARE, MeltdownChoreography.tear(before, 48.0D), 0.01D);
        assertEquals(1.0D, MeltdownChoreography.felt(10.0D, 384.0D), 1.0e-9D);
        assertEquals(0.0D, MeltdownChoreography.felt(384.0D, 384.0D), 1.0e-9D);
    }

    // ------------------------------------------------------------------ the ground it leaves

    @Test
    void unsettledGroundFadesToNothingAndIsGone() {
        ScarZone zone = new ScarZone(0L, 24, 1_000L, 25_000L);
        assertEquals(1.0D, zone.strength(1_000L), 1.0e-9D);
        assertEquals(0.5D, zone.strength(13_000L), 1.0e-9D);
        assertEquals(0.0D, zone.strength(25_000L), 1.0e-9D);
        assertFalse(zone.over(24_999L));
        assertTrue(zone.over(25_000L));
        double last = 1.0D;
        for (long now = 1_000L; now <= 26_000L; now += 100L) {
            assertTrue(zone.strength(now) <= last, "a zone grew stronger");
            last = zone.strength(now);
        }
        assertEquals(0.0D, new ScarZone(0L, 24, 5L, 5L).strength(5L), "a zone with no time in it bears on something");
    }

    @Test
    void itBearsHardestInTheMiddleAndNotAtAllPastItsEdge() {
        ScarZone zone = new ScarZone(0L, 24, 0L, 24_000L);
        assertEquals(1.0D, zone.bears(0.0D, 0L), 1.0e-9D);
        assertTrue(zone.bears(23.9D, 0L) > 0.25D, "the rim of a fresh zone is safe ground");
        assertEquals(0.0D, zone.bears(24.0D, 0L), 1.0e-9D);
        assertEquals(0.0D, zone.bears(40.0D, 0L), 1.0e-9D);
        assertTrue(zone.bears(6.0D, 0L) > zone.bears(18.0D, 0L));
        assertEquals(zone.bears(6.0D, 0L) * 0.5D, zone.bears(6.0D, 12_000L), 1.0e-9D);
        assertEquals(0.0D, zone.bears(0.0D, 24_000L), 1.0e-9D);
    }

    // ------------------------------------------------------------------ the panel

    /** Minecraft's Auto GUI scale promises 320 by 240 and nothing more. */
    @Test
    void thePanelFitsTheSmallestScreenAndNothingOnItOverlaps() {
        for (int[] screen : new int[][]{{320, 240}, {427, 240}, {640, 360}, {1920, 1080}}) {
            WellheadLayout layout = WellheadLayout.in(screen[0], screen[1]);
            int[] panel = layout.panel();
            assertTrue(panel[0] >= 0 && panel[1] >= 0 && panel[0] + panel[2] <= screen[0] && panel[1] + panel[3] <= screen[1],
                    "the panel leaves a " + screen[0] + " by " + screen[1] + " screen");
            int[] gauge = layout.gauge();
            int[] scram = layout.scramButton();
            int[] hatch = layout.hatchButton();
            for (int[] box : new int[][]{gauge, scram, hatch}) {
                assertTrue(box[0] >= panel[0] && box[1] >= panel[1] && box[0] + box[2] <= panel[0] + panel[2]
                        && box[1] + box[3] <= panel[1] + panel[3], "something hangs off the panel");
            }
            assertTrue(layout.titleY() + 9 <= gauge[1] - 2, "the title runs into the gauge");
            assertTrue(gauge[1] + gauge[3] + 2 <= layout.rowY(0), "the gauge runs into the readings");
            assertTrue(layout.rowY(WellheadLayout.ROWS - 1) + 9 <= scram[1], "the readings run into the buttons");
            assertTrue(scram[0] + scram[2] <= hatch[0], "the buttons overlap");
            assertEquals(gauge[0], layout.gaugeAt(0.0D));
            assertEquals(gauge[0] + gauge[2] - 1, layout.gaugeAt(1.0D));
            assertEquals(layout.gaugeAt(1.0D), layout.gaugeAt(7.0D), "a mark runs off the end of the gauge");
        }
        // And where a screen is smaller still, it is pinned to the corner rather than clipped on both edges.
        assertEquals(0, WellheadLayout.in(200, 100).panel()[0]);
        assertEquals(0, WellheadLayout.in(200, 100).panel()[1]);
    }
}
