package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sketch rift's pencil: where it is, what it is doing, and what it can be interrupted in the middle
 * of.
 *
 * <p>A warp gives no warning. A hull can reach the aperture a tick after it opened or ten seconds later,
 * and the rift can be told to let go at any moment in either case - halfway through a twirl, or before
 * the drawing is even finished. So most of what is worth proving here is that <em>any</em> moment can be
 * asked for and the pencil is somewhere sensible: never jumping, never lying across the hole a ship is
 * about to come through, and never still on screen after it has gone.
 */
class SketchPencilTest {

    private static final float OPEN = 20.0F;
    private static final float CLOSE = 40.0F;
    /** How long a pencil is, as a fraction of the rift's radius - what the renderer works out. */
    private static final float LENGTH = 0.9F;
    /** Far above anything the pencil is meant to move at: this is about jumps, not about speed. */
    private static final float NO_FASTER_THAN = 8.0F;

    private static SketchPlan plan() {
        return SketchPlan.of(RiftShatter.seedFor(12.5D, 71.0D, -400.25D), OPEN);
    }

    private static SketchPencil.Events events(float transitFrom, float transitTicks, float closeFrom) {
        return new SketchPencil.Events(OPEN, transitFrom,
                transitFrom < 0.0F ? SketchPencil.NEVER : transitFrom + transitTicks,
                closeFrom, CLOSE, 0x5EED);
    }

    /** Every way a warp can treat one rift. */
    private static List<SketchPencil.Events> everyWay() {
        List<SketchPencil.Events> ways = new ArrayList<>();
        ways.add(events(SketchPencil.NEVER, 0.0F, SketchPencil.NEVER));
        // A hull arriving before the drawing is finished, right after it, and after a long wait.
        for (float transit : new float[]{8.0F, 21.0F, 95.0F}) {
            ways.add(events(transit, 60.0F, SketchPencil.NEVER));
            ways.add(events(transit, 60.0F, transit + 60.0F));
        }
        // And being told to let go at every awkward moment: mid-drawing, mid-idle, mid-duck.
        for (float close : new float[]{5.0F, 12.0F, 24.0F, 40.0F, 57.0F, 96.0F, 140.0F}) {
            ways.add(events(SketchPencil.NEVER, 0.0F, close));
            ways.add(events(50.0F, 60.0F, close));
        }
        return ways;
    }

    /**
     * The pencil never jumps, however it is interrupted. Sampled far finer than a frame, at a bound far
     * above anything it is meant to move at, so this catches a pencil that is here one moment and there
     * the next rather than one that is simply quick.
     */
    @Test
    void thePencilNeverJumps() {
        SketchPlan plan = plan();
        float step = 0.01F;
        for (SketchPencil.Events events : everyWay()) {
            float until = events.closing() ? events.closeFrom() + CLOSE : 260.0F;
            SketchPencil.Pose last = SketchPencil.pose(plan, events, 0.0F, LENGTH);
            for (float time = step; time <= until; time += step) {
                SketchPencil.Pose pose = SketchPencil.pose(plan, events, time, LENGTH);
                double moved = Math.sqrt(sq(pose.u() - last.u()) + sq(pose.v() - last.v())
                        + sq(pose.lift() - last.lift()));
                assertTrue(moved <= NO_FASTER_THAN * step,
                        "the pencil jumped " + moved + " at tick " + time + " of " + describe(events));
                assertTrue(turn(pose, last) <= 0.6D,
                        "the pencil span " + turn(pose, last) + " at tick " + time + " of " + describe(events));
                assertTrue(Math.abs(pose.scale() - last.scale()) <= 0.05F, "the pencil popped out of existence");
                last = pose;
            }
        }
    }

    /** While a line is going down, the point is on the page at the end of it. */
    @Test
    void thePencilIsAtTheHeadOfWhateverItIsDrawing() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(SketchPencil.NEVER, 0.0F, SketchPencil.NEVER);
        for (SketchPlan.Stroke stroke : plan.opening()) {
            for (float k = 0.05F; k < 1.0F; k += 0.1F) {
                float time = stroke.start() + (stroke.end() - stroke.start()) * k;
                SketchPencil.Pose pose = SketchPencil.pose(plan, events, time, LENGTH);
                assertEquals(0.0F, pose.lift(), 1.0e-6F, "the pencil is off the page mid-stroke");
                float drawn = stroke.drawn(time);
                assertEquals(stroke.headU(drawn), pose.u(), 1.0e-4F);
                assertEquals(stroke.headV(drawn), pose.v(), 1.0e-4F);
            }
        }
    }

    /**
     * Once it is out of the way, the whole pencil - point, barrel and all - is outside the hole, so
     * nothing coming through can fly into it.
     */
    @Test
    void theDuckedPencilIsClearOfAnythingComingThrough() {
        SketchPlan plan = plan();
        for (float transit : new float[]{8.0F, 21.0F, 95.0F}) {
            SketchPencil.Events events = events(transit, 60.0F, SketchPencil.NEVER);
            for (float time = transit + 7.0F; time < transit + 58.0F; time += 0.5F) {
                SketchPencil.Pose pose = SketchPencil.pose(plan, events, time, LENGTH);
                assertTrue(radius(pose, 0.0F) > 1.05D, "the ducked pencil's point is over the hole at " + time);
                assertTrue(radius(pose, LENGTH) > 1.05D, "the ducked pencil's barrel is over the hole at " + time);
                assertTrue(pose.lift() > 0.4F, "the ducked pencil is still down on the page at " + time);
            }
        }
    }

    /** It comes back once the ship is through, so it is there to scribble the drawing out. */
    @Test
    void thePencilComesBackAfterwards() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(30.0F, 60.0F, SketchPencil.NEVER);
        SketchPencil.Pose pose = SketchPencil.pose(plan, events, 110.0F, LENGTH);
        assertTrue(pose.lift() < 0.5F, "the pencil never came back down");
        assertTrue(radius(pose, 0.0F) < 1.45D, "the pencil never came back in");
    }

    /**
     * Every idle move starts and ends at the same rest pose, which is what lets them be chained end to
     * end - and cut off wherever a ship happens to arrive.
     */
    @Test
    void everyIdleMoveStartsAndEndsAtRest() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(SketchPencil.NEVER, 0.0F, SketchPencil.NEVER);
        float idleFrom = plan.opening().get(plan.opening().size() - 1).end() + OPEN * 0.25F;
        int found = 0;
        for (float time = idleFrom; time < idleFrom + 300.0F; time += 0.05F) {
            SketchPencil.Pose pose = SketchPencil.pose(plan, events, time, LENGTH);
            if (Math.abs(pose.u() - SketchPencil.REST_U) < 1.0e-4F
                    && Math.abs(pose.v() - SketchPencil.REST_V) < 1.0e-4F) {
                found++;
            }
        }
        assertTrue(found > 8, "the pencil never passes through its rest pose: " + found);
    }

    /** Nothing it does takes it through the page, flips included. */
    @Test
    void thePencilStaysOnItsOwnSideOfThePage() {
        SketchPlan plan = plan();
        for (SketchPencil.Events events : everyWay()) {
            float until = events.closing() ? events.closeFrom() + CLOSE : 260.0F;
            for (float time = 0.0F; time <= until; time += 0.05F) {
                SketchPencil.Pose pose = SketchPencil.pose(plan, events, time, LENGTH);
                assertTrue(pose.lift() >= -0.02F, "the pencil's point went through the page at " + time);
                float far = pose.lift() + (float) Math.cos(pose.tilt()) * LENGTH;
                assertTrue(far >= -0.35F, "the pencil's barrel swung through the page at " + time);
            }
        }
    }

    /**
     * The barrel is held at one angle to the page throughout, the way a hand holds a pencil - and turned
     * outward only when it gets out of the way, where the point of it is to be clear of the hole.
     */
    @Test
    void theBarrelIsHeldAtOneAngleAndOnlyTurnsOutToDuck() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(SketchPencil.NEVER, 0.0F, SketchPencil.NEVER);
        for (float time = 0.0F; time < 200.0F; time += 0.05F) {
            assertEquals(SketchPencil.LEAN, SketchPencil.pose(plan, events, time, LENGTH).lean(), 1.0e-4F,
                    "the pencil turned in the hand at " + time);
        }
        // Out of the way, it leans outward from the middle instead, so the barrel clears the hole too.
        SketchPencil.Events ducking = events(40.0F, 60.0F, SketchPencil.NEVER);
        float ducked = SketchPencil.pose(plan, ducking, 70.0F, LENGTH).lean();
        assertEquals(Math.atan2(SketchPencil.DUCK_V, SketchPencil.DUCK_U), ducked, 1.0e-4F,
                "the ducked pencil is not leaning out over the rim");
        assertTrue(Math.abs(ducked - SketchPencil.LEAN) > 0.05F, "the pencil did not change its lean to duck");
    }

    /** And by the end of the close there is no pencil left at all. */
    @Test
    void thePencilIsGoneByTheEnd() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(SketchPencil.NEVER, 0.0F, 60.0F);
        assertEquals(0.0F, SketchPencil.pose(plan, events, 60.0F + CLOSE, LENGTH).scale(), 1.0e-4F);
    }

    /** The drawing is whole until it is scribbled out, then screwed up into nothing. */
    @Test
    void theDrawingIsOnlyCrumpledOnceItHasBeenScribbledOut() {
        assertEquals(1.0F, SketchPencil.crumple(0.0F));
        assertEquals(1.0F, SketchPencil.crumple(SketchPencil.SCRIBBLE_TO));
        assertEquals(0.0F, SketchPencil.crumple(SketchPencil.CRUMPLE_TO), 1.0e-4F);
        assertEquals(0.0F, SketchPencil.jag(0.2F), 1.0e-6F);
        assertTrue(SketchPencil.jag(SketchPencil.CRUMPLE_TO) > 0.2F, "the drawing crumpled without creasing");
        float last = 1.0F;
        for (float s = 0.0F; s <= 1.0F; s += 0.01F) {
            float now = SketchPencil.crumple(s);
            assertTrue(now <= last + 1.0e-4F, "the drawing un-crumpled at " + s);
            last = now;
        }
    }

    /** The ball is there once the drawing screws up, is thrown, and is gone by the end. */
    @Test
    void theBallGrowsIsThrownAndGoes() {
        assertEquals(null, SketchPencil.ball(SketchPencil.SCRIBBLE_TO));
        SketchPencil.Ball whole = SketchPencil.ball(SketchPencil.CRUMPLE_TO);
        assertEquals(SketchPencil.BALL_SIZE, whole.size(), 1.0e-4F);
        assertEquals(0.0F, whole.u(), 1.0e-6F);
        SketchPencil.Ball thrown = SketchPencil.ball(0.9F);
        assertTrue(Math.hypot(thrown.u(), thrown.v()) > 0.5D, "the ball was not thrown anywhere");
        assertEquals(0.0F, SketchPencil.ball(1.0F).size(), 1.0e-4F);
        assertTrue(SketchPencil.ball(0.9F).spin() != SketchPencil.ball(0.8F).spin(), "the ball does not tumble");
    }

    /** Everything worth hearing is heard once, and in the order it happens. */
    @Test
    void everythingIsHeardOnceAndInOrder() {
        SketchPlan plan = plan();
        // A long wait, so the pencil gets through enough idle moves to have done each of them.
        SketchPencil.Events events = events(SketchPencil.NEVER, 0.0F, 400.0F);
        List<SketchPencil.Cue> heard = new ArrayList<>();
        for (int tick = 1; tick <= 400 + (int) CLOSE; tick++) {
            heard.addAll(SketchPencil.cues(plan, events, tick - 1, tick));
        }
        assertEquals(3, count(heard, SketchPencil.Cue.DRAW), "the pencil should be heard on three strokes");
        assertEquals(2, count(heard, SketchPencil.Cue.SCRIBBLE), "two passes of scribbling out");
        assertEquals(1, count(heard, SketchPencil.Cue.CRUMPLE));
        assertEquals(1, count(heard, SketchPencil.Cue.TOSS));
        assertTrue(count(heard, SketchPencil.Cue.TAP) > 0, "an idle pencil never tapped anything");
        assertTrue(count(heard, SketchPencil.Cue.SWISH) > 0, "an idle pencil never twirled");
        assertTrue(heard.indexOf(SketchPencil.Cue.CRUMPLE) < heard.indexOf(SketchPencil.Cue.TOSS),
                "it was thrown before it was screwed up");
        assertTrue(heard.indexOf(SketchPencil.Cue.SCRIBBLE) < heard.indexOf(SketchPencil.Cue.CRUMPLE));
    }

    /** A pencil that has got out of the way keeps quiet, because it has stopped fidgeting. */
    @Test
    void aDuckedPencilIsSilent() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(40.0F, 90.0F, SketchPencil.NEVER);
        for (int tick = 50; tick < 125; tick++) {
            assertTrue(SketchPencil.cues(plan, events, tick - 1, tick).isEmpty(),
                    "the pencil was heard fidgeting while a ship went through, at " + tick);
        }
    }

    /** What the lead is coloured: whatever it is drawing with, and graphite once it is rubbing out. */
    @Test
    void theLeadIsWhateverItIsDrawingWith() {
        SketchPlan plan = plan();
        SketchPencil.Events events = events(SketchPencil.NEVER, 0.0F, 90.0F);
        assertEquals(SketchPlan.Ink.GRAPHITE, SketchPencil.ink(plan, events, 0.0F));
        assertEquals(SketchPlan.Ink.CORE, SketchPencil.ink(plan, events,
                plan.opening().get(1).start() + 0.5F));
        assertEquals(SketchPlan.Ink.ACCENT, SketchPencil.ink(plan, events,
                plan.opening().get(2).start() + 0.5F));
        assertEquals(SketchPlan.Ink.SHINE, SketchPencil.ink(plan, events, OPEN));
        assertEquals(SketchPlan.Ink.GRAPHITE, SketchPencil.ink(plan, events, 100.0F));
    }

    private static int count(List<SketchPencil.Cue> heard, SketchPencil.Cue cue) {
        int found = 0;
        for (SketchPencil.Cue each : heard) {
            if (each == cue) {
                found++;
            }
        }
        return found;
    }

    /** How far from the middle a point of the pencil is, at a fraction of its length up the barrel. */
    private static double radius(SketchPencil.Pose pose, float along) {
        double u = pose.u() + Math.sin(pose.tilt()) * Math.cos(pose.lean()) * along;
        double v = pose.v() + Math.sin(pose.tilt()) * Math.sin(pose.lean()) * along;
        return Math.hypot(u, v);
    }

    /** How far the barrel swung between two moments, as an angle. */
    private static double turn(SketchPencil.Pose now, SketchPencil.Pose before) {
        double dot = Math.sin(now.tilt()) * Math.cos(now.lean()) * Math.sin(before.tilt()) * Math.cos(before.lean())
                + Math.sin(now.tilt()) * Math.sin(now.lean()) * Math.sin(before.tilt()) * Math.sin(before.lean())
                + Math.cos(now.tilt()) * Math.cos(before.tilt());
        return Math.acos(Math.max(-1.0D, Math.min(1.0D, dot)));
    }

    private static String describe(SketchPencil.Events events) {
        return "transit " + events.transitFrom() + " close " + events.closeFrom();
    }

    private static double sq(double value) {
        return value * value;
    }
}
