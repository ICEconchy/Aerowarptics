package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The doodles on the inside of a Sketch rift's tunnel.
 *
 * <p>Everything that can go wrong with them goes wrong quietly. A drawing that reaches outside its own
 * page is drawn over its neighbours on the wall; a stroke of fewer than two points, or an odd number of
 * numbers, is a line the renderer skips without a word; and two entries of the set that are the same
 * drawing leave a tunnel that looks repetitive without anything having failed.
 */
class SketchDoodlesTest {

    @Test
    void everyDrawingIsOnItsPage() {
        for (int which = 0; which < SketchDoodles.COUNT; which++) {
            for (float[] stroke : SketchDoodles.strokes(which)) {
                for (int point = 0; point + 1 < stroke.length; point += 2) {
                    assertTrue(Math.abs(stroke[point]) <= 1.0F && Math.abs(stroke[point + 1]) <= 1.0F,
                            "doodle " + which + " drew off its page at " + stroke[point] + ", "
                                    + stroke[point + 1]);
                }
            }
        }
    }

    @Test
    void everyStrokeHasALineInIt() {
        for (int which = 0; which < SketchDoodles.COUNT; which++) {
            for (float[] stroke : SketchDoodles.strokes(which)) {
                assertEquals(0, stroke.length % 2, "doodle " + which + " has a point with one number");
                assertTrue(stroke.length >= 4, "doodle " + which + " has a stroke with nothing to draw");
                boolean moves = false;
                for (int point = 0; point + 3 < stroke.length && !moves; point += 2) {
                    moves = Math.hypot(stroke[point + 2] - stroke[point], stroke[point + 3] - stroke[point + 1])
                            > 1.0e-4F;
                }
                assertTrue(moves, "doodle " + which + " has a stroke that never leaves its first point");
            }
        }
    }

    /** Each is big enough to be read as it goes past, rather than a speck on the wall. */
    @Test
    void everyDrawingFillsItsPage() {
        for (int which = 0; which < SketchDoodles.COUNT; which++) {
            float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            for (float[] stroke : SketchDoodles.strokes(which)) {
                for (int point = 0; point + 1 < stroke.length; point += 2) {
                    bounds[0] = Math.min(bounds[0], stroke[point]);
                    bounds[1] = Math.max(bounds[1], stroke[point]);
                    bounds[2] = Math.min(bounds[2], stroke[point + 1]);
                    bounds[3] = Math.max(bounds[3], stroke[point + 1]);
                }
            }
            assertTrue(bounds[1] - bounds[0] > 0.8F && bounds[3] - bounds[2] > 0.4F,
                    "doodle " + which + " is too small to make out: " + Arrays.toString(bounds));
        }
    }

    @Test
    void theDrawingsAreAllDifferent() {
        for (int which = 0; which < SketchDoodles.COUNT; which++) {
            for (int other = which + 1; other < SketchDoodles.COUNT; other++) {
                assertTrue(!sameDrawing(SketchDoodles.strokes(which), SketchDoodles.strokes(other)),
                        "doodles " + which + " and " + other + " are the same drawing");
            }
        }
    }

    /**
     * The id wraps, because the tunnel picks one from a seed and a seed of exactly one would otherwise
     * ask for a drawing that does not exist.
     */
    @Test
    void theIdWraps() {
        for (int which = -SketchDoodles.COUNT; which <= SketchDoodles.COUNT * 2; which++) {
            List<float[]> here = SketchDoodles.strokes(which);
            List<float[]> wrapped = SketchDoodles.strokes(Math.floorMod(which, SketchDoodles.COUNT));
            assertTrue(sameDrawing(here, wrapped), "doodle " + which + " did not wrap");
        }
    }

    /** The same drawing every time it is asked for, so a doodle does not change on the way down the tube. */
    @Test
    void aDrawingIsTheSameEveryTime() {
        for (int which = 0; which < SketchDoodles.COUNT; which++) {
            List<float[]> once = SketchDoodles.strokes(which);
            List<float[]> again = SketchDoodles.strokes(which);
            assertEquals(once.size(), again.size(), "doodle " + which + " changed how many strokes it has");
            for (int stroke = 0; stroke < once.size(); stroke++) {
                assertArrayEquals(once.get(stroke), again.get(stroke), 0.0F,
                        "doodle " + which + " changed between two asks");
            }
        }
    }

    private static boolean sameDrawing(List<float[]> one, List<float[]> other) {
        if (one.size() != other.size()) {
            return false;
        }
        for (int stroke = 0; stroke < one.size(); stroke++) {
            if (!Arrays.equals(one.get(stroke), other.get(stroke))) {
                return false;
            }
        }
        return true;
    }
}
