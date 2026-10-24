package uk.co.iceconchy.aerowarptics.gate;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gate passage's timing, walked a hundred times a tick.
 *
 * <p>Nothing here can throw. What can go wrong is a tunnel that flickers when the server is a tick late,
 * a flare that fades back into the tunnel it grew out of, or - the one that matters - a traveller shown
 * the far gate while they are still being held at it, unable to move. All of that is in the curves, and
 * the curves are a function of three numbers, so every case is walked: the server on time, late, early,
 * and never heard from at all.
 */
class GatePassageTest {

    private static final double EPSILON = 1.0e-9D;

    /** How finely each passage is walked: a hundred times a tick, as {@code SketchPencilTest} walks a pencil. */
    private static final double STEP = 0.01D;

    /**
     * The most any curve may change in one step. A curve that eases over a single tick - the shortest
     * there is, on a passage too short for its own set piece - moves a hundredth and a half of its range;
     * a jump is tens of times that.
     */
    private static final double SMOOTH = 0.02D;

    private static final double[] FORECASTS = {60.0D, 200.0D, 20.0D, 5.0D, 1.0D};

    /** When the client hears the passage is over, as a function of how long it was told it would last. */
    private interface Word {
        double heardAt(double forecast);
    }

    private static final Map<String, Word> WORDS = new LinkedHashMap<>();

    static {
        WORDS.put("on time", forecast -> forecast);
        WORDS.put("a little late", forecast -> forecast + 3.7D);
        WORDS.put("late", forecast -> forecast + 25.0D);
        WORDS.put("as the flare swells", forecast -> forecast - 0.4D * Math.min(GatePassage.FLARE_TICKS, forecast * 0.5D));
        WORDS.put("early", forecast -> forecast * 0.5D);
        WORDS.put("at once", forecast -> 0.0D);
        WORDS.put("after the client gave up", forecast -> forecast + GatePassage.LOST_TICKS + 9.0D);
        WORDS.put("never", forecast -> Double.POSITIVE_INFINITY);
    }

    private interface Curve {
        double at(double age, double forecast, double arrived);
    }

    private static final double SEED = 1.234D;

    private static final Map<String, Curve> CURVES = new LinkedHashMap<>();

    static {
        CURVES.put("veil", GatePassage::veil);
        CURVES.put("flare", GatePassage::flare);
        CURVES.put("splash", (age, forecast, arrived) -> GatePassage.splash(age, forecast));
        CURVES.put("rush", (age, forecast, arrived) -> GatePassage.rush(age, forecast));
        CURVES.put("depth", (age, forecast, arrived) -> GatePassage.depth(age, forecast));
        CURVES.put("swayX", (age, forecast, arrived) -> GatePassage.swayX(age, forecast, arrived, SEED));
        CURVES.put("swayY", (age, forecast, arrived) -> GatePassage.swayY(age, forecast, arrived, SEED));
        CURVES.put("roll", (age, forecast, arrived) -> GatePassage.roll(age, SEED));
        CURVES.put("chroma", (age, forecast, arrived) -> GatePassage.chroma(age, forecast));
        CURVES.put("fov", GatePassage::fov);
    }

    /** What the client knows at an age: nothing of the end until it has heard, and then when it heard. */
    private static double known(double age, double heardAt) {
        return age >= heardAt ? heardAt : -1.0D;
    }

    /** Long enough to see any passage out, however its end was heard of. */
    private static double span(double forecast) {
        return forecast + GatePassage.LOST_TICKS + GatePassage.EMERGE_TICKS + 40.0D;
    }

    // ------------------------------------------------------------- nothing snaps

    /**
     * No curve jumps - not at a join between two parts of the passage, and not at the moment the client
     * hears it is over, which is the join nobody chose: it lands wherever the network put it.
     */
    @Test
    void nothingJumpsFromOneMomentToTheNext() {
        for (double forecast : FORECASTS) {
            for (Map.Entry<String, Word> word : WORDS.entrySet()) {
                double heardAt = word.getValue().heardAt(forecast);
                for (Map.Entry<String, Curve> curve : CURVES.entrySet()) {
                    double last = curve.getValue().at(0.0D, forecast, known(0.0D, heardAt));
                    for (double age = STEP; age <= span(forecast); age += STEP) {
                        double now = curve.getValue().at(age, forecast, known(age, heardAt));
                        assertTrue(Math.abs(now - last) <= SMOOTH, curve.getKey() + " jumped from " + last + " to "
                                + now + " at " + age + "t of a " + forecast + "t passage, the end heard " + word.getKey());
                        last = now;
                    }
                }
            }
        }
    }

    /** And the moment of hearing itself, exactly: the same either side of it, for every curve. */
    @Test
    void hearingThatItIsOverChangesNothingAtThatMoment() {
        for (double forecast : FORECASTS) {
            for (double heardAt = 0.0D; heardAt <= span(forecast); heardAt += 0.37D) {
                for (Map.Entry<String, Curve> curve : CURVES.entrySet()) {
                    assertEquals(curve.getValue().at(heardAt, forecast, -1.0D), curve.getValue().at(heardAt, forecast, heardAt),
                            1.0e-6D, curve.getKey() + " changed on hearing, at " + heardAt + "t of " + forecast + "t");
                }
            }
        }
    }

    // ------------------------------------------------------------ the far side

    /**
     * The whole point of the veil. From the first tick until the flare is whole and the passage is over,
     * nothing of the world shows - and what is over the view when it does begin to show is white.
     */
    @Test
    void theFarSideIsNeverSeenBeforeTheFlareIsWhole() {
        for (double forecast : FORECASTS) {
            for (Map.Entry<String, Word> word : WORDS.entrySet()) {
                double heardAt = word.getValue().heardAt(forecast);
                for (double age = 0.0D; age <= span(forecast); age += STEP) {
                    double arrived = known(age, heardAt);
                    String when = " at " + age + "t of " + forecast + "t, the end heard " + word.getKey();
                    double veil = GatePassage.veil(age, forecast, arrived);
                    if (veil < 1.0D - EPSILON) {
                        assertEquals(1.0D, GatePassage.flare(age, forecast, arrived), EPSILON,
                                "the far side showed through an unfinished flare" + when);
                    }
                    // Either side of the moment the far side begins to show, and not on it: a sample that
                    // lands within a rounding error of that moment may fairly be called either.
                    double emerges = GatePassage.emergesAt(forecast, arrived);
                    if (age < emerges - 1.0e-6D) {
                        assertEquals(1.0D, veil, EPSILON, "the far side showed early" + when);
                        assertTrue(GatePassage.held(age, forecast, arrived), "a traveller who cannot see was let walk" + when);
                    } else if (age > emerges + 1.0e-6D) {
                        assertFalse(GatePassage.held(age, forecast, arrived),
                                "a traveller who can see the far side was still held" + when);
                    }
                }
            }
        }
    }

    /** The world does not show before the server has said the traveller is through - unless it never says. */
    @Test
    void theFarSideWaitsForTheServersWord() {
        for (double forecast : FORECASTS) {
            for (double age = 0.0D; age < forecast + GatePassage.LOST_TICKS; age += STEP) {
                assertEquals(1.0D, GatePassage.veil(age, forecast, -1.0D), EPSILON,
                        "the far side showed at " + age + "t with the server still to say the passage was over");
            }
        }
    }

    // ---------------------------------------------------------------- the cases

    @Test
    void aPassageThatEndsOnTimeIsWhiteAsItEndsAndClearAMomentLater() {
        double forecast = 60.0D;
        assertEquals(0.0D, GatePassage.flare(forecast - GatePassage.FLARE_TICKS, forecast, -1.0D), EPSILON);
        assertEquals(1.0D, GatePassage.flare(forecast, forecast, forecast), EPSILON);
        assertEquals(1.0D, GatePassage.veil(forecast, forecast, forecast), EPSILON);
        assertEquals(forecast, GatePassage.emergesAt(forecast, forecast), EPSILON);
        assertEquals(0.0D, GatePassage.veil(forecast + GatePassage.EMERGE_TICKS, forecast, forecast), EPSILON);
        assertFalse(GatePassage.over(forecast + GatePassage.EMERGE_TICKS - STEP, forecast, forecast));
        assertTrue(GatePassage.over(forecast + GatePassage.EMERGE_TICKS, forecast, forecast));
    }

    /** A server running slow: the flare is whole on time, and stays whole until the word comes. */
    @Test
    void aLateServerIsWaitedForInWhite() {
        double forecast = 60.0D;
        double heard = forecast + 25.0D;
        for (double age = forecast; age <= heard; age += STEP) {
            double arrived = known(age, heard);
            assertEquals(1.0D, GatePassage.flare(age, forecast, arrived), EPSILON);
            assertEquals(1.0D, GatePassage.veil(age, forecast, arrived), EPSILON);
        }
        assertEquals(heard, GatePassage.emergesAt(forecast, heard), EPSILON);
    }

    /** A client running slow hears early: the flare starts on the word, and takes its full time. */
    @Test
    void wordThatComesEarlyStartsTheFlareThereAndThen() {
        double forecast = 60.0D;
        double heard = 20.0D;
        assertEquals(heard, GatePassage.flareBegins(forecast, heard), EPSILON);
        assertEquals(heard + GatePassage.FLARE_TICKS, GatePassage.flareWhole(forecast, heard), EPSILON);
        assertEquals(heard + GatePassage.FLARE_TICKS, GatePassage.emergesAt(forecast, heard), EPSILON,
                "the far side showed before the flare the early word started had finished");
        assertTrue(GatePassage.held(heard + GatePassage.FLARE_TICKS - STEP, forecast, heard));
    }

    /** A lost packet must not leave a player looking at white, with their keys doing nothing, for ever. */
    @Test
    void aPassageWhoseEndIsNeverHeardOfClearsByItself() {
        for (double forecast : FORECASTS) {
            double gaveUp = forecast + GatePassage.LOST_TICKS;
            assertTrue(GatePassage.held(gaveUp - STEP, forecast, -1.0D));
            assertFalse(GatePassage.held(gaveUp, forecast, -1.0D));
            assertTrue(GatePassage.over(gaveUp + GatePassage.EMERGE_TICKS, forecast, -1.0D));
            // And word that turns up after that is too late to cover the view again.
            double late = gaveUp + 9.0D;
            assertEquals(gaveUp, GatePassage.emergesAt(forecast, late), EPSILON);
        }
    }

    // ------------------------------------------------------------ one direction

    /** The flare never falls: if it did, the tunnel would come back for a moment behind the light. */
    @Test
    void theFlareOnlyGrowsAndTheVeilOnlyThins() {
        for (double forecast : FORECASTS) {
            for (Word word : WORDS.values()) {
                double heardAt = word.heardAt(forecast);
                double flare = 0.0D;
                double veil = 1.0D;
                for (double age = 0.0D; age <= span(forecast); age += STEP) {
                    double arrived = known(age, heardAt);
                    double nowFlare = GatePassage.flare(age, forecast, arrived);
                    double nowVeil = GatePassage.veil(age, forecast, arrived);
                    assertTrue(nowFlare >= flare - EPSILON, "the flare fell at " + age + "t of " + forecast + "t");
                    assertTrue(nowVeil <= veil + EPSILON, "the veil came back at " + age + "t of " + forecast + "t");
                    flare = nowFlare;
                    veil = nowVeil;
                }
                assertEquals(0.0D, veil, EPSILON, "a passage of " + forecast + "t never cleared");
            }
        }
    }

    /** The conduit only goes forwards, and as fast as it says it is going. */
    @Test
    void theConduitGoesByAtTheSpeedItSaysItDoes() {
        for (double forecast : FORECASTS) {
            assertEquals(0.0D, GatePassage.depth(0.0D, forecast), EPSILON);
            for (double age = 0.0D; age <= span(forecast); age += 0.1D) {
                double rush = GatePassage.rush(age, forecast);
                assertTrue(rush > 0.0D && rush <= 1.0D + EPSILON, "rush out of range: " + rush);
                // Depth is the integral of rush: its slope, measured, is the rush times the pace.
                double slope = (GatePassage.depth(age + 1.0e-4D, forecast) - GatePassage.depth(age, forecast)) / 1.0e-4D;
                assertEquals(GatePassage.PACE * rush, slope, 1.0e-3D, "the walls and the speed disagree at " + age
                        + "t of " + forecast + "t");
            }
        }
    }

    // ------------------------------------------------------------------ the rest

    /** Straight in, and straight out: the far end is dead ahead at both ends of the passage. */
    @Test
    void theFarEndIsDeadAheadGoingInAndComingOut() {
        double forecast = 60.0D;
        assertEquals(0.0D, GatePassage.swayX(0.0D, forecast, -1.0D, SEED), EPSILON);
        assertEquals(0.0D, GatePassage.swayY(0.0D, forecast, -1.0D, SEED), EPSILON);
        assertEquals(0.0D, GatePassage.swayX(forecast, forecast, forecast, SEED), EPSILON);
        assertEquals(0.0D, GatePassage.swayY(forecast, forecast, forecast, SEED), EPSILON);
        // And off it in between, or there is no twist to the conduit at all.
        double furthest = 0.0D;
        for (double age = 0.0D; age < forecast; age += STEP) {
            furthest = Math.max(furthest, Math.abs(GatePassage.swayX(age, forecast, -1.0D, SEED)));
        }
        assertTrue(furthest > 0.1D, "the conduit never bends: " + furthest);
    }

    /** The pane's light is all there is as the passage begins, and is gone well before it ends. */
    @Test
    void thePanesLightOpensThePassageAndClears() {
        assertEquals(1.0D, GatePassage.splash(0.0D, 60.0D), EPSILON);
        assertEquals(0.0D, GatePassage.splash(GatePassage.PLUNGE_TICKS, 60.0D), EPSILON);
        // On a passage too short for both, each takes half, and the plunge is over as the flare begins. (Two
        // ticks is the shortest that can be halved: neither is ever shorter than one.)
        for (double forecast : List.of(2.0D, 5.0D, 20.0D)) {
            assertEquals(0.0D, GatePassage.splash(GatePassage.flareBegins(forecast, -1.0D), forecast), EPSILON,
                    "the pane's light and the flare overlap on a " + forecast + "t passage");
        }
    }

    /** Wide where nobody can see it, so that it is already wide as the far side appears. */
    @Test
    void theLensIsWideBeforeTheFarSideShowsAndEasesBack() {
        double forecast = 60.0D;
        assertEquals(GatePassage.FOV_WIDEN, GatePassage.fov(0.0D, forecast, -1.0D), EPSILON);
        assertEquals(GatePassage.FOV_WIDEN, GatePassage.fov(forecast, forecast, forecast), EPSILON);
        assertEquals(0.0D, GatePassage.fov(forecast + GatePassage.EMERGE_TICKS, forecast, forecast), EPSILON);
        assertEquals(0.0D, GatePassage.fov(-1.0D, forecast, -1.0D), EPSILON);
    }

    /** Every curve stays a number, and in its range, whatever the forecast - a server may send nought. */
    @Test
    void everyCurveIsFiniteWhateverItIsHanded() {
        for (double forecast : new double[] {0.0D, 0.5D, 1.0D, 2.0D, 1200.0D}) {
            for (double arrived : new double[] {-1.0D, 0.0D, 0.25D, forecast, forecast + 500.0D}) {
                for (double age = -2.0D; age <= span(forecast); age += 0.25D) {
                    for (Map.Entry<String, Curve> curve : CURVES.entrySet()) {
                        double value = curve.getValue().at(age, forecast, arrived);
                        assertTrue(Double.isFinite(value), curve.getKey() + " is " + value + " at " + age + "t of "
                                + forecast + "t");
                    }
                    for (String unit : List.of("veil", "flare", "splash", "chroma")) {
                        double value = CURVES.get(unit).at(age, forecast, arrived);
                        assertTrue(value >= -EPSILON && value <= 1.0D + EPSILON, unit + " is " + value + " at " + age
                                + "t of " + forecast + "t");
                    }
                }
            }
        }
    }
}
