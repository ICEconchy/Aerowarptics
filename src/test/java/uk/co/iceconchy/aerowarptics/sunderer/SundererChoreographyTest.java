package uk.co.iceconchy.aerowarptics.sunderer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sundering's scene held to what makes it a scene: nothing teleports at a beat, with the hand or without it,
 * the seed is born before the frame lifts and is gone at the end, and the room is given back.
 */
class SundererChoreographyTest {

    private static final int SAMPLES_A_TICK = 100;
    private static final double SIZE = 2.5D;
    private static final int COUNT = 7;

    private static Map<String, BiFunction<Double, Boolean, Double>> curves() {
        Map<String, BiFunction<Double, Boolean, Double>> curves = new LinkedHashMap<>();
        curves.put("straining", SundererChoreography::straining);
        curves.put("orbiting", SundererChoreography::orbiting);
        curves.put("handBeat", SundererChoreography::handBeat);
        curves.put("breach", SundererChoreography::breach);
        curves.put("point", (tick, hand) -> SundererChoreography.point(tick));
        curves.put("search", (tick, hand) -> SundererChoreography.search(tick));
        curves.put("lens", SundererChoreography::lens);
        curves.put("seed", (tick, hand) -> SundererChoreography.seed(tick, hand, SIZE, COUNT));
        curves.put("pulse", (tick, hand) -> SundererChoreography.pulse(tick, hand, COUNT));
        curves.put("disc", SundererChoreography::disc);
        curves.put("orbitAngle", SundererChoreography::orbitAngle);
        curves.put("implosion", SundererChoreography::implosion);
        curves.put("darkness", SundererChoreography::darkness);
        curves.put("light", SundererChoreography::light);
        curves.put("pull", SundererChoreography::pull);
        curves.put("humPitch", SundererChoreography::humPitch);
        curves.put("humVolume", SundererChoreography::humVolume);
        curves.put("flash", SundererChoreography::flash);
        curves.put("dust", SundererChoreography::dust);
        return curves;
    }

    /** A curve that jumps is a piece that teleports on screen. A hundred samples a tick, across the whole scene. */
    @Test
    void noCurveJumpsAnywhere() {
        for (boolean hand : new boolean[]{false, true}) {
            int steps = SundererSequence.length(hand) * SAMPLES_A_TICK;
            for (Map.Entry<String, BiFunction<Double, Boolean, Double>> curve : curves().entrySet()) {
                double largest = 1.0D;
                for (int step = 0; step <= steps; step += SAMPLES_A_TICK) {
                    largest = Math.max(largest, Math.abs(curve.getValue().apply(step / (double) SAMPLES_A_TICK, hand)));
                }
                double last = curve.getValue().apply(0.0D, hand);
                for (int step = 1; step <= steps; step++) {
                    double now = curve.getValue().apply(step / (double) SAMPLES_A_TICK, hand);
                    assertTrue(Math.abs(now - last) <= 0.01D * largest, curve.getKey() + (hand ? " (hand)" : "")
                            + " jumps by " + Math.abs(now - last) + " at tick " + step / (double) SAMPLES_A_TICK);
                    last = now;
                }
            }
        }
    }

    @Test
    void itStartsAndEndsWithNothingThere() {
        for (boolean hand : new boolean[]{false, true}) {
            double end = SundererSequence.length(hand);
            for (String name : new String[]{"point", "search", "lens", "seed", "disc", "flash", "darkness"}) {
                assertEquals(0.0D, curves().get(name).apply(0.0D, hand), 1.0e-9D, name + " at the start");
                assertEquals(0.0D, curves().get(name).apply(end, hand), 1.0e-9D, name + " at the end");
            }
            // A charged Sunderer is lit before it fires; by the end it is gone.
            assertEquals(0.0D, SundererChoreography.light(end, hand), 1.0e-9D, "the Sunderer's light outlived it");
            assertEquals(0, SundererChoreography.lightStage(end, hand));
        }
    }

    /** The point searches and finds nothing, and the seed is born of it before a single block lifts. */
    @Test
    void theSeedIsBornBeforeTheFrameLifts() {
        double reached = SundererSequence.REACHING;
        assertTrue(SundererChoreography.seed(reached, false, SIZE, COUNT) > 0.1D, "no seed when the reaching ends");
        assertEquals(0.0D, SundererChoreography.point(reached), 1.0e-9D, "the point outlived the seed's birth");
        assertEquals(0.0D, SundererChoreography.search(reached), 1.0e-9D, "the search was still reaching");
        assertTrue(SundererChoreography.search((SundererChoreography.SEARCH_TO + SundererChoreography.SEARCH_DIES) / 2) > 0.9D,
                "the search never reached out");
        for (int index = 0; index < COUNT; index++) {
            assertTrue(Unravelling.liftStart(index, COUNT) > 0.0D, "a block lifted in the reaching");
        }
    }

    /** The seed grows as the frame is drawn round it, and never past what the orbit leaves it room for. */
    @Test
    void theSeedGrowsAndStaysClearOfTheOrbit() {
        double grown = SundererChoreography.seed(SundererSequence.breachStart(false) - 1, false, SIZE, COUNT);
        assertTrue(grown > SundererChoreography.SEED_BORN * 2.0D, "the seed never grew");
        for (int tick = 0; tick < SundererSequence.length(false); tick++) {
            assertTrue(SundererChoreography.seed(tick, false, SIZE, COUNT) <= SundererChoreography.largestSeed(SIZE) + 1.0e-9D,
                    "the seed outgrew its room at tick " + tick);
        }
    }

    @Test
    void theOrbitHoldsAllButStillForTheHand() {
        double start = SundererSequence.handStart();
        double before = SundererChoreography.orbitAngle(start, true);
        double after = SundererChoreography.orbitAngle(start + SundererSequence.HAND, true);
        assertEquals(SundererChoreography.HAND_DRIFT, after - before, 1.0e-9D);
        assertTrue(SundererChoreography.orbiting(start, true) > 0.0D, "the hand comes before the orbit has begun");
        assertTrue(SundererChoreography.straining(start, true) < SundererChoreography.ORBIT_END,
                "the hand comes after the swallowing has begun");
    }

    @Test
    void theRingIsWhatLightsTheRoomAtTheHeightOfIt() {
        double height = SundererSequence.breachStart(false) - 1;
        assertEquals(3, SundererChoreography.lightStage(height, false));
        assertTrue(SundererChoreography.darkness(height, false) > 0.5D);
    }

    @Test
    void theHandIsOutOnlyInItsBeat() {
        assertTrue(!SundererChoreography.handOut(SundererSequence.handStart() + 50, false));
        assertTrue(SundererChoreography.handOut(SundererSequence.handStart() + 50, true));
        assertTrue(!SundererChoreography.handOut(SundererSequence.handStart() - 1, true));
        assertTrue(!SundererChoreography.handOut(SundererSequence.handStart() + SundererSequence.HAND + 1, true));
    }

    @Test
    void theFissureIsSetInTheBreachAndNotBefore() {
        for (boolean hand : new boolean[]{false, true}) {
            assertTrue(!SundererChoreography.torn(SundererSequence.breachStart(hand) + 1, hand));
            assertTrue(SundererChoreography.torn(SundererSequence.length(hand), hand));
        }
    }

    /** About 35 s, and about 44 with the hand: the owner's figures (2026-10-17). */
    @Test
    void itRunsAsLongAsTheOwnerAsked() {
        assertEquals(36.0D, SundererSequence.length(false) / 20.0D, 2.0D);
        assertEquals(44.0D, SundererSequence.length(true) / 20.0D, 2.0D);
    }
}
