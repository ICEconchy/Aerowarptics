package uk.co.iceconchy.aerowarptics.ward;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deflection's scene, walked a hundred times a tick.
 *
 * <p>A curve that jumps at a beat is a piece that teleports on screen - a tear that snaps half open, a weight that
 * is suddenly somewhere else - and nothing fails but the eye. So every curve is sampled across every join, at
 * several lengths of scene, and held to moving no faster than a thing can.
 */
class WardChoreographyTest {

    private static final int[] TOTALS = {WardChoreography.SHORTEST, 60, 100, 240, 1_200};

    /** Every curve a client draws from or a server times by, by name. */
    private static Map<String, BiFunction<Double, Integer, Double>> curves() {
        Map<String, BiFunction<Double, Integer, Double>> curves = new LinkedHashMap<>();
        curves.put("reach", WardChoreography::reach);
        curves.put("caught", WardChoreography::caught);
        curves.put("skid", WardChoreography::skid);
        curves.put("burst", WardChoreography::burst);
        curves.put("tear", WardChoreography::tear);
        curves.put("strain", WardChoreography::strain);
        curves.put("dim", WardChoreography::dim);
        curves.put("lean", WardChoreography::lean);
        curves.put("ring", WardChoreography::ring);
        curves.put("bend", WardChoreography::bend);
        curves.put("shake", WardChoreography::shake);
        curves.put("slackLean", WardChoreography::slackLean);
        curves.put("sag", WardChoreography::sag);
        // The tear as it is drawn: how far open, times how much of it is still there.
        curves.put("tear drawn", (age, total) -> WardChoreography.tear(age, total) * WardChoreography.ghost(age, total));
        curves.put("ring drawn", (age, total) -> WardChoreography.ring(age, total) * WardChoreography.ringFade(age, total));
        return curves;
    }

    @Test
    void nothingJumpsAtAnyBeat() {
        for (int total : TOTALS) {
            int end = total + WardChoreography.SETTLE_TICKS + 20;
            for (Map.Entry<String, BiFunction<Double, Integer, Double>> curve : curves().entrySet()) {
                double last = curve.getValue().apply(-5.0D, total);
                for (int step = -499; step <= end * 100; step++) {
                    double age = step / 100.0D;
                    double now = curve.getValue().apply(age, total);
                    // A hundredth of a tick. Nothing here moves its whole range in under two ticks.
                    assertTrue(Math.abs(now - last) <= 0.0075D, curve.getKey() + " jumps by " + (now - last)
                            + " at tick " + age + " of a " + total + "-tick scene");
                    last = now;
                }
            }
        }
    }

    @Test
    void everyCurveStaysInItsRange() {
        for (int total : TOTALS) {
            for (Map.Entry<String, BiFunction<Double, Integer, Double>> curve : curves().entrySet()) {
                double low = curve.getKey().equals("lean") ? -1.0D : 0.0D;
                for (int tick = -10; tick <= total + WardChoreography.SETTLE_TICKS + 10; tick++) {
                    double value = curve.getValue().apply((double) tick, total);
                    assertTrue(value >= low - 1.0e-9D && value <= 1.0D + 1.0e-9D,
                            curve.getKey() + " is " + value + " at tick " + tick);
                }
            }
        }
    }

    @Test
    void nothingHasBegunBeforeTheSceneDoes() {
        for (Map.Entry<String, BiFunction<Double, Integer, Double>> curve : curves().entrySet()) {
            assertEquals(0.0D, curve.getValue().apply(0.0D, 100), 1.0e-9D, curve.getKey() + " has begun at tick nought");
        }
    }

    /** The beats in order: opened and held half open, hauled over, dragged all the way, and burst wide at the rim. */
    @Test
    void theTearIsHeldHalfOpenDraggedToTheRimAndBurstsThere() {
        int total = 200;
        assertEquals(0.5D, WardChoreography.tear(total * 0.3D, total), 1.0e-9D, "caught, it stands half open");
        assertEquals(0.5D, WardChoreography.tear(total - 0.01D, total), 1.0e-3D);
        assertEquals(1.0D, WardChoreography.tear(total + WardChoreography.BURST_TICKS, total), 1.0e-9D);
        assertEquals(0.0D, WardChoreography.skid(total * 0.4D, total), 1.0e-9D, "it is dragged before it is caught");
        assertEquals(1.0D, WardChoreography.skid(total, total), 1.0e-9D, "it bursts before it reaches the rim");
        double last = 0.0D;
        for (int tick = 0; tick <= total; tick++) {
            double skid = WardChoreography.skid(tick, total);
            assertTrue(skid >= last, "the tear is dragged back towards the place it was aimed at");
            last = skid;
        }
        assertEquals(1.0D, WardChoreography.ghost(total, total), 1.0e-9D, "the tear has gone before it breaks");
        assertEquals(0.0D, WardChoreography.ghost(total + WardChoreography.BURST_TICKS + WardChoreography.GHOST_TICKS, total), 1.0e-9D);
    }

    /** Pulled over as the tear is caught, held against the chain all the way to the rim, then swinging itself still. */
    @Test
    void theWeightIsHauledOverHeldAndThenSwingsItselfStill() {
        int total = 200;
        assertEquals(0.0D, WardChoreography.lean(total * 0.25D, total), 1.0e-9D);
        for (int tick = (int) (total * 0.4D); tick <= total; tick++) {
            assertEquals(1.0D, WardChoreography.lean(tick, total), 1.0e-9D, "the chain went slack mid-skid at " + tick);
        }
        boolean swungThrough = false;
        double worst = 0.0D;
        for (int tick = total; tick <= total + WardChoreography.SETTLE_TICKS; tick++) {
            double lean = WardChoreography.lean(tick, total);
            swungThrough |= lean < -0.2D;
            if (tick > total + WardChoreography.SETTLE_TICKS - 20) {
                worst = Math.max(worst, Math.abs(lean));
            }
        }
        assertTrue(swungThrough, "let go, the weight never swung past hanging straight");
        assertTrue(worst < 0.05D, "the weight is still swinging by " + worst + " as the scene ends");
        assertFalse(WardChoreography.over(total + WardChoreography.SETTLE_TICKS - 1, total));
        assertTrue(WardChoreography.over(total + WardChoreography.SETTLE_TICKS, total));
    }

    @Test
    void theDayDimsForTheSkidAndLiftsAfterTheBreak() {
        int total = 200;
        assertEquals(1.0D, WardChoreography.dim(total * 0.7D, total), 1.0e-9D);
        assertEquals(0.0D, WardChoreography.dim(total + WardChoreography.LIFT_TICKS, total), 1.0e-9D);
    }

    /** A ward that cannot pay starts for the tear, and is let drop: no lean left, the chain gone slack. */
    @Test
    void aWardGivingWayLetsTheWeightDrop() {
        int total = 200;
        double most = 0.0D;
        for (int tick = 0; tick <= total; tick++) {
            most = Math.max(most, WardChoreography.slackLean(tick, total));
        }
        assertTrue(most > 0.3D && most <= 0.5D, "the weight never started for the tear: " + most);
        assertEquals(0.0D, WardChoreography.slackLean(total * 0.4D, total), 1.0e-9D);
        assertEquals(0.0D, WardChoreography.slackLean(total, total), 1.0e-9D);
        assertEquals(1.0D, WardChoreography.sag(total * 0.4D, total), 1.0e-9D);
    }

    @Test
    void aShortHopStillGetsAScene() {
        assertEquals(WardChoreography.SHORTEST, WardChoreography.runUp(5));
        assertEquals(600, WardChoreography.runUp(600));
        assertTrue(WardChoreography.tollAt(WardChoreography.SHORTEST) >= 5, "the toll comes before there is a tear to catch");
        assertTrue(WardChoreography.tollAt(600) < 600);
    }

    /** Those aboard are shaken as the tear is caught and again, harder, as it breaks - and bent over only in between. */
    @Test
    void theCrewAreShakenAtTheCatchAndHarderAtTheBreak() {
        int total = 200;
        double atCatch = 0.0D;
        double atBreak = 0.0D;
        for (int tick = 0; tick <= total + 40; tick++) {
            double shake = WardChoreography.shake(tick, total);
            if (tick < total) {
                atCatch = Math.max(atCatch, shake);
            } else {
                atBreak = Math.max(atBreak, shake);
            }
        }
        assertTrue(atCatch > 0.3D, "no jolt as the tear is caught");
        assertTrue(atBreak > atCatch, "the break shakes no harder than the catch");
        assertEquals(0.0D, WardChoreography.bend(total + WardChoreography.BURST_TICKS, total), 1.0e-9D);
    }
}
