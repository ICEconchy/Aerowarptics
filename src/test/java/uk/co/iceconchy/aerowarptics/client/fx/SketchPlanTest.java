package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Sketch rift is drawn with, and when.
 *
 * <p>The thing that matters here is the clock. A hull can reach the aperture the moment it has finished
 * opening, so anything still being drawn then is a hole with a ship coming through a half-coloured
 * picture of it - and colour that does not reach the rim is a hole with a gap in it. Neither throws.
 */
class SketchPlanTest {

    /** A drive's opening: twenty ticks, whatever else about the warp varies. */
    private static final float OPEN = 20.0F;

    private static SketchPlan plan(int index) {
        return SketchPlan.of(RiftShatter.seedFor(index * 5.3D, 70.0D - index, index * 2.9D), OPEN);
    }

    @Test
    void thePencilDrawsOneStrokeAtATime() {
        for (int index = 0; index < 200; index++) {
            for (List<SketchPlan.Stroke> strokes : List.of(plan(index).opening(), plan(index).closing())) {
                for (int at = 0; at < strokes.size(); at++) {
                    SketchPlan.Stroke stroke = strokes.get(at);
                    assertTrue(stroke.end() > stroke.start(), "a stroke that takes no time");
                    if (at > 0) {
                        assertTrue(stroke.start() >= strokes.get(at - 1).end(),
                                "two strokes at once, and only one pencil");
                    }
                }
            }
        }
    }

    /**
     * The whole drawing is down before the rift has finished opening, which is the whole point of it: a
     * hull can arrive then, and does not wait for an artist.
     */
    @Test
    void everythingIsDrawnInsideTheOpening() {
        for (int index = 0; index < 50; index++) {
            List<SketchPlan.Stroke> opening = plan(index).opening();
            assertEquals(SketchPlan.Kind.OUTLINE, opening.get(0).kind(), "the outline comes first");
            assertTrue(opening.get(0).start() > 0.0F, "the pencil is drawing before it has arrived");
            assertTrue(opening.get(opening.size() - 1).end() <= OPEN + 1.0e-4F,
                    "a sketch was still being drawn when the rift finished opening");
        }
    }

    /** The colouring in is finished well before that, because the face is what it reveals. */
    @Test
    void theHoleIsColouredInBeforeTheOpeningEnds() {
        for (int index = 0; index < 50; index++) {
            SketchPlan plan = plan(index);
            assertTrue(plan.front(0.0F) <= -1.0F, "the hole was coloured in before anything was drawn");
            assertTrue(plan.front(OPEN) >= 1.0F, "the hole was not finished being coloured in");
            float last = -2.0F;
            for (float time = 0.0F; time <= OPEN; time += 0.05F) {
                float front = plan.front(time);
                assertTrue(front >= last - 1.0e-4F, "the colouring went backwards at " + time);
                last = front;
            }
        }
    }

    /**
     * The colour covers the hole: no point of it is further from a pass of the crayon than the crayon is
     * wide. A gap here is a stripe of world showing through the middle of a portal.
     */
    @Test
    void theCrayonCoversTheWholeHole() {
        double reach = SketchPlan.Kind.FILL.width();
        for (int index = 0; index < 20; index++) {
            SketchPlan.Stroke fill = plan(index).opening().get(1);
            for (int sample = 0; sample < 400; sample++) {
                double angle = sample * 2.399963D;
                double radius = Math.sqrt((sample + 0.5D) / 400.0D);
                double u = Math.cos(angle) * radius;
                double v = Math.sin(angle) * radius;
                double nearest = Double.MAX_VALUE;
                for (int point = 0; point + 1 < fill.count(); point++) {
                    nearest = Math.min(nearest, distanceToSegment(u, v, fill.u(point), fill.v(point),
                            fill.u(point + 1), fill.v(point + 1)));
                }
                assertTrue(nearest <= reach, "a patch of the hole at " + u + ", " + v
                        + " was never coloured in: " + nearest + " from the nearest pass");
            }
        }
    }

    /**
     * The scribbling out goes right across the hole, twice, at a good angle to itself.
     *
     * <p>Not that it covers every last patch - it is a scribble, and the gaps in it are the point. What
     * would be wrong is a scribble that only reached part of the drawing, or two passes that lie on top
     * of one another instead of crossing.
     */
    @Test
    void theScribblingOutGoesRightAcrossTheHoleTwice() {
        for (int index = 0; index < 50; index++) {
            List<SketchPlan.Stroke> closing = plan(index).closing();
            assertEquals(2, closing.size(), "one pass is not a scribbling out");
            double[] headings = new double[2];
            for (int pass = 0; pass < 2; pass++) {
                SketchPlan.Stroke stroke = closing.get(pass);
                double low = Double.MAX_VALUE;
                double high = -Double.MAX_VALUE;
                for (int point = 0; point < stroke.count(); point++) {
                    double along = stroke.u(point) * Math.cos(1.0D) + stroke.v(point) * Math.sin(1.0D);
                    low = Math.min(low, along);
                    high = Math.max(high, along);
                    assertTrue(Math.hypot(stroke.u(point), stroke.v(point)) < 1.6D,
                            "the scribbling out went miles off the page");
                }
                assertTrue(high - low > 1.6D, "a pass of scribbling out did not cross the hole");
                headings[pass] = Math.atan2(stroke.v(1) - stroke.v(0), stroke.u(1) - stroke.u(0));
            }
            double between = Math.abs(headings[0] - headings[1]) % Math.PI;
            assertTrue(between > 0.5D && between < Math.PI - 0.5D,
                    "the two passes of scribbling out do not cross: " + between);
        }
    }

    /** Every line is on the page, give or take the bit of colouring outside the lines that is on purpose. */
    @Test
    void everyLineIsOnThePage() {
        for (int index = 0; index < 200; index++) {
            for (SketchPlan.Stroke stroke : plan(index).opening()) {
                for (int point = 0; point < stroke.count(); point++) {
                    double radius = Math.hypot(stroke.u(point), stroke.v(point));
                    assertTrue(radius <= 1.12D, stroke.kind() + " strayed to " + radius);
                }
            }
        }
    }

    /** The closing strokes run inside the close, in its own units. */
    @Test
    void theScribblingOutFitsInsideTheClose() {
        List<SketchPlan.Stroke> closing = plan(3).closing();
        assertEquals(SketchPlan.SCRIBBLE_FROM, closing.get(0).start(), 1.0e-6F);
        assertEquals(SketchPlan.SCRIBBLE_TO, closing.get(closing.size() - 1).end(), 1.0e-6F);
    }

    /** Every viewer draws the same sketch. */
    @Test
    void oneRiftAlwaysDrawsTheSameSketch() {
        List<SketchPlan.Stroke> first = plan(11).opening();
        List<SketchPlan.Stroke> second = plan(11).opening();
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.get(index).start(), second.get(index).start());
            assertTrue(java.util.Arrays.equals(first.get(index).points(), second.get(index).points()));
        }
    }

    /** The pencil is heard touching down, and not on the shine, which is a flourish rather than a stroke. */
    @Test
    void touchingDownIsHeard() {
        SketchPlan plan = plan(2);
        int heard = 0;
        for (SketchPlan.Stroke stroke : plan.opening()) {
            if (stroke.sounded()) {
                heard++;
            }
        }
        assertEquals(3, heard, "the pencil should be heard on the outline, the fill and the inner line");
        SketchPlan.Stroke first = plan.opening().get(0);
        assertTrue(SketchPlan.touchesDown(plan.opening(), first.start() - 0.5F, first.start()));
        assertTrue(!SketchPlan.touchesDown(plan.opening(), first.start(), first.start() + 0.2F));
    }

    private static double distanceToSegment(double u, double v, double u0, double v0, double u1, double v1) {
        double runU = u1 - u0;
        double runV = v1 - v0;
        double lengthSquared = runU * runU + runV * runV;
        double along = lengthSquared <= 0.0D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, ((u - u0) * runU + (v - v0) * runV) / lengthSquared));
        return Math.hypot(u - (u0 + runU * along), v - (v0 + runV * along));
    }
}
