package uk.co.iceconchy.aerowarptics.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a Sketch rift's pencil is, and what it is doing, at any moment.
 *
 * <p>The drawing itself is over in a second - see {@link SketchPlan} - and a rift stands for ten. So
 * almost all of a pencil's life is <em>acting</em>: hanging about admiring its work, twirling, tapping
 * the rim, flipping end over end, getting out of the way of a ship and peering after it, and finally
 * scribbling the whole thing out, crumpling it up and lobbing it away.
 *
 * <h2>Why this is one pure function of the clock</h2>
 * A warp gives no warning. A hull can reach the rift a tick after it opened or ten seconds later, and
 * the rift can be told to let go at any point in either case - including halfway through a twirl. Rather
 * than run a state machine that has to be unwound from wherever it got to, everything here is a pure
 * function of the time and the handful of moments the rift knows about ({@link Events}), so any moment
 * can be asked for directly and the pose before an interruption is simply the same function evaluated
 * a tick earlier. That is what makes the blends seamless, and what lets {@code SketchPencilTest} prove
 * the pencil never jumps - including when a ship arrives mid-flourish.
 *
 * <p>Everything is in the aperture's plane, in fractions of its rim, and every time is in ticks since
 * the rift appeared. Free of Minecraft, like {@link SketchPlan}.
 */
public final class SketchPencil {

    /** No such moment: this rift has not been told about a hull, or to let go. */
    public static final float NEVER = -1.0F;

    /**
     * The moments a rift knows about.
     *
     * @param openTicks    how long the rift takes to open, which is how long the drawing has
     * @param transitFrom  when a hull reached the aperture, or {@link #NEVER}
     * @param transitUntil when it is expected to be done with it
     * @param closeFrom    when the rift was told to let go, or {@link #NEVER}
     * @param closeTicks   how long it takes to do it
     */
    public record Events(float openTicks, float transitFrom, float transitUntil, float closeFrom,
                         float closeTicks, int seed) {

        public boolean ducking() {
            return transitFrom >= 0.0F;
        }

        public boolean closing() {
            return closeFrom >= 0.0F;
        }

        /** How far through the close a moment is, or 0 before it starts. */
        public float closeProgress(float time) {
            return closing() ? Math.max(0.0F, (time - closeFrom) / Math.max(1.0F, closeTicks)) : 0.0F;
        }
    }

    /**
     * Where the pencil is and how it is held.
     *
     * @param u       where its point is across the aperture, in rim fractions
     * @param lift    how far its point is off the page
     * @param tilt    how far off square to the page it leans; past a quarter turn it is upside down,
     *                which is how it flips
     * @param lean    which way round the page it leans - always away from the middle, so the barrel is
     *                never lying across the drawing
     * @param roll    how far it has turned about its own axis, which is what a twirl is
     * @param stretch how much longer than itself it is, for the squash and stretch of a cartoon
     * @param scale   how much of it there is: nothing before it arrives, nothing again once it has gone
     */
    public record Pose(float u, float v, float lift, float tilt, float lean, float roll, float stretch,
                       float scale) {
    }

    /** The crumpled-up drawing, once there is one. */
    public record Ball(float u, float v, float lift, float size, float spin) {
    }

    /** Something worth hearing. */
    public enum Cue {
        /** The pencil touching down on a line. */
        DRAW,
        /** Its point tapping the page. */
        TAP,
        /** It whirling about. */
        SWISH,
        /** A pass of scribbling out. */
        SCRIBBLE,
        /** The drawing being crumpled up. */
        CRUMPLE,
        /** The ball being lobbed away. */
        TOSS
    }

    // ------------------------------------------------------------------ poses

    /** Where the pencil waits between things, and how it is held there. */
    public static final float REST_U = 1.12F;
    public static final float REST_V = 0.78F;
    public static final float REST_LIFT = 0.26F;
    /** How far off square to the page it is held while drawing. */
    public static final float TILT = 0.68F;
    /**
     * Which way round the page the barrel leans: up and to the right, as a right hand holds one.
     *
     * <p>Fixed, rather than worked out from where the point happens to be. A hand does not turn the
     * pencil round as it draws, and one that leaned away from the middle of the hole would spin through
     * half a turn the instant the point crossed it - which is a pencil whirling in somebody's fingers,
     * not a pencil drawing a line.
     */
    public static final float LEAN = 0.72F;

    /** Where it comes in from, and how far off the page. */
    private static final float ARRIVE_U = 2.1F;
    private static final float ARRIVE_V = 1.7F;
    private static final float ARRIVE_LIFT = 1.5F;
    /** How much of the run-up it spends pulling back before it swoops in. */
    private static final float ANTICIPATION = 0.45F;

    /** Where it ducks to while a hull is coming through, and how it hangs there. */
    public static final float DUCK_U = 1.55F;
    public static final float DUCK_V = 1.15F;
    public static final float DUCK_LIFT = 0.95F;
    private static final float DUCK_TILT = 1.25F;
    /** Ticks it takes to get out of the way, and to come back. */
    private static final float DUCK_IN = 6.0F;
    private static final float DUCK_OUT = 8.0F;

    // ---------------------------------------------------------------- the idle

    /** What the pencil does with itself while it waits. */
    private enum Move {
        /** Hanging in the air, bobbing, looking at the drawing. */
        BOB(34.0F),
        /** Spinning about its own axis. */
        TWIRL(26.0F),
        /** Down to the rim, three taps, back. */
        TAP(30.0F),
        /** End over end, once, and caught. */
        FLIP(30.0F),
        /** Leaning back and nodding at its own work. */
        NOD(24.0F);

        private final float ticks;

        Move(float ticks) {
            this.ticks = ticks;
        }
    }

    private static final Move[] MOVES = Move.values();
    /** When in a tap the point actually touches the page. */
    private static final float[] TAPS = {0.36F, 0.55F, 0.74F};
    /** How high a flip is done, so the barrel swings round without going through the page. */
    private static final float FLIP_LIFT = 1.25F;

    // ---------------------------------------------------------------- the close

    /** How far through the close each part of it runs. */
    public static final float RETURN_TO = 0.15F;
    public static final float SCRIBBLE_TO = 0.45F;
    public static final float CRUMPLE_TO = 0.70F;
    public static final float FLICK_TO = 0.78F;
    /** How big the crumpled ball is, as a fraction of the rim. */
    public static final float BALL_SIZE = 0.20F;
    /** How ragged the drawing goes as it is crumpled up. */
    public static final float CRUMPLE_JAG = 0.35F;

    private SketchPencil() {
    }

    // ------------------------------------------------------------------- pose

    /**
     * Where the pencil is at a moment.
     *
     * @param length how long the pencil is, in rim fractions - needed because a flip turns it about its
     *               middle, and its point is what everything else is measured by
     */
    public static Pose pose(SketchPlan plan, Events events, float time, float length) {
        Pose base = ducked(plan, events, time, length);
        if (!events.closing() || time < events.closeFrom()) {
            return base;
        }
        return closing(plan, events, time, length);
    }

    /** The pencil's own business, and getting out of the way of a hull. */
    private static Pose ducked(SketchPlan plan, Events events, float time, float length) {
        Pose base = drawingOrIdling(plan, events, time, length);
        float duck = duck(events, time);
        if (duck <= 0.0F) {
            return base;
        }
        return blend(base, duckPose(events, time), duck);
    }

    /** How far out of the way the pencil is, 0..1. */
    private static float duck(Events events, float time) {
        if (!events.ducking()) {
            return 0.0F;
        }
        float out = ease(SketchPlan.clamp((time - events.transitFrom()) / DUCK_IN));
        float back = ease(SketchPlan.clamp((time - events.transitUntil()) / DUCK_OUT));
        return out * (1.0F - back);
    }

    /** Hanging well outside the rim and high off the page, peering at what is going through it. */
    private static Pose duckPose(Events events, float time) {
        float peek = (float) Math.sin(time * 0.12D + events.seed() * 0.001D);
        // Leaning out over the rim rather than in, so the barrel is outside the hole as well as the point.
        return new Pose(DUCK_U, DUCK_V, DUCK_LIFT + 0.04F * peek, DUCK_TILT + 0.08F * peek,
                (float) Math.atan2(DUCK_V, DUCK_U), 0.0F, 1.0F, 1.0F);
    }

    /** Arriving, drawing, hopping between strokes, or idling once the drawing is done. */
    private static Pose drawingOrIdling(SketchPlan plan, Events events, float time, float length) {
        List<SketchPlan.Stroke> strokes = plan.opening();
        SketchPlan.Stroke first = strokes.get(0);
        if (time < first.start()) {
            return arriving(first, time);
        }
        for (int index = 0; index < strokes.size(); index++) {
            SketchPlan.Stroke stroke = strokes.get(index);
            if (time <= stroke.end()) {
                float drawn = stroke.drawn(time);
                return onPage(stroke.headU(drawn), stroke.headV(drawn), 1.0F);
            }
            SketchPlan.Stroke next = index + 1 < strokes.size() ? strokes.get(index + 1) : null;
            if (next != null && time < next.start()) {
                return hop(stroke, next, (time - stroke.end()) / (next.start() - stroke.end()));
            }
        }
        SketchPlan.Stroke last = strokes.get(strokes.size() - 1);
        float idleFrom = last.end() + hopTicks(events);
        if (time < idleFrom) {
            // The flourish off the last stroke: up, away, and into the rest pose.
            float k = ease((time - last.end()) / Math.max(1.0e-4F, idleFrom - last.end()));
            float fromU = last.u(last.count() - 1);
            float fromV = last.v(last.count() - 1);
            return lifted(SketchPlan.lerp(k, fromU, REST_U), SketchPlan.lerp(k, fromV, REST_V),
                    SketchPlan.lerp(k, 0.0F, REST_LIFT) + arc(k) * 0.25F, 1.0F + 0.15F * arc(k));
        }
        return idle(events, time - idleFrom, length);
    }

    /** How long the pencil takes to sweep off the last line and settle. */
    private static float hopTicks(Events events) {
        return Math.max(4.0F, events.openTicks() * 0.25F);
    }

    /** Coming in from off the page: a pull back, then a swoop, growing as it comes. */
    private static Pose arriving(SketchPlan.Stroke first, float time) {
        float k = SketchPlan.clamp(time / Math.max(1.0e-4F, first.start()));
        float scale = SketchPlan.clamp(k / ANTICIPATION);
        if (k < ANTICIPATION) {
            // Anticipation: it drifts back the way it came before it comes in at all.
            float pull = ease(k / ANTICIPATION) * 0.12F;
            return held(ARRIVE_U + pull, ARRIVE_V + pull, ARRIVE_LIFT + pull, TILT * 0.7F, 0.0F, 0.9F, scale);
        }
        float swoop = ease((k - ANTICIPATION) / (1.0F - ANTICIPATION));
        return lifted(SketchPlan.lerp(swoop, ARRIVE_U + 0.12F, first.u(0)),
                SketchPlan.lerp(swoop, ARRIVE_V + 0.12F, first.v(0)),
                SketchPlan.lerp(swoop, ARRIVE_LIFT + 0.12F, 0.0F),
                1.0F + 0.2F * (1.0F - swoop));
    }

    /** Lifting off one stroke and landing on the next. */
    private static Pose hop(SketchPlan.Stroke from, SketchPlan.Stroke to, float k) {
        float fromU = from.u(from.count() - 1);
        float fromV = from.v(from.count() - 1);
        float eased = ease(k);
        float u = SketchPlan.lerp(eased, fromU, to.u(0));
        float v = SketchPlan.lerp(eased, fromV, to.v(0));
        float reach = (float) Math.hypot(to.u(0) - fromU, to.v(0) - fromV);
        return lifted(u, v, arc(k) * Math.min(0.3F, 0.08F + reach * 0.15F), 1.0F + 0.1F * arc(k));
    }

    // ------------------------------------------------------------------- idle

    /** Which move is running, and how far through it, at a time after the drawing is done. */
    private static Pose idle(Events events, float since, float length) {
        float cursor = 0.0F;
        for (int index = 0; index < 512; index++) {
            Move move = move(events, index);
            if (since < cursor + move.ticks) {
                return perform(move, (since - cursor) / move.ticks, events, index, length);
            }
            cursor += move.ticks;
        }
        return rest();
    }

    /** Which move comes nth. Rolled from the seed, so two rifts do not fidget in step. */
    private static Move move(Events events, int index) {
        return MOVES[(int) (RiftShatter.noise(events.seed(), 18_000 + index) * MOVES.length) % MOVES.length];
    }

    /**
     * One move, as a departure from the rest pose that starts and ends exactly at it - which is what
     * lets them be chained end to end, and cut off at any moment by a ship arriving.
     */
    private static Pose perform(Move move, float k, Events events, int index, float length) {
        float settle = (float) Math.sin(k * Math.PI); // nothing at either end of the move
        switch (move) {
            case BOB -> {
                return lifted(REST_U + 0.05F * settle * (float) Math.sin(k * Math.PI * 2.0D),
                        REST_V + 0.07F * settle,
                        REST_LIFT + 0.06F * settle * (float) Math.sin(k * Math.PI * 3.0D), 1.0F);
            }
            case TWIRL -> {
                // Two full turns about its own axis, quick in the middle and easing at both ends.
                float spin = ease(k) * (float) (Math.PI * 4.0D);
                return held(REST_U, REST_V, REST_LIFT + 0.12F * settle, TILT - 0.25F * settle, spin,
                        1.0F + 0.08F * settle, 1.0F);
            }
            case TAP -> {
                return tap(k, events, index, settle);
            }
            case FLIP -> {
                // End over end about its own middle, held high so the barrel never swings through the page.
                // The pose is the pencil's *point*, so the point has to be worked out from where the middle
                // is - which is also what keeps this move starting and ending exactly at the rest pose,
                // since at no turn at all the two agree.
                float turn = ease(k) * (float) (Math.PI * 2.0D);
                float lean = leanAt(REST_U, REST_V);
                float half = length * 0.5F;
                float midU = REST_U + (float) (Math.sin(TILT) * Math.cos(lean)) * half;
                float midV = REST_V + (float) (Math.sin(TILT) * Math.sin(lean)) * half;
                float midLift = REST_LIFT + (float) Math.cos(TILT) * half + FLIP_LIFT * settle;
                float tilt = TILT + turn;
                return new Pose(midU - (float) (Math.sin(tilt) * Math.cos(lean)) * half,
                        midV - (float) (Math.sin(tilt) * Math.sin(lean)) * half,
                        midLift - (float) Math.cos(tilt) * half, tilt, lean, 0.0F, 1.0F, 1.0F);
            }
            default -> {
                // A nod at its own work: leaning back and dipping twice.
                float nod = settle * (float) Math.sin(k * Math.PI * 4.0D);
                return held(REST_U, REST_V, REST_LIFT + 0.03F * settle, TILT + 0.28F * settle + 0.12F * nod,
                        0.0F, 1.0F, 1.0F);
            }
        }
    }

    /** Down to a point on the rim, three taps with a squash on each, and back to rest. */
    private static Pose tap(float k, Events events, int index, float settle) {
        double angle = RiftShatter.noise(events.seed(), 18_500 + index) * Math.PI * 2.0D;
        float atU = (float) Math.cos(angle) * 1.04F;
        float atV = (float) Math.sin(angle) * 1.04F;
        float travel = ease(SketchPlan.clamp(k / TAPS[0]));
        float back = ease(SketchPlan.clamp((k - TAPS[TAPS.length - 1]) / (1.0F - TAPS[TAPS.length - 1])));
        float u = SketchPlan.lerp(travel, REST_U, atU);
        float v = SketchPlan.lerp(travel, REST_V, atV);
        u = SketchPlan.lerp(back, u, REST_U);
        v = SketchPlan.lerp(back, v, REST_V);
        // Off the page between taps and on it at each, so the taps are events rather than a hover.
        float lift = SketchPlan.lerp(travel, REST_LIFT, 0.0F);
        lift = SketchPlan.lerp(back, lift, REST_LIFT);
        float squash = 1.0F;
        for (float at : TAPS) {
            float near = 1.0F - SketchPlan.clamp(Math.abs(k - at) / 0.07F);
            lift += 0.16F * (1.0F - near) * settle * Math.min(travel, 1.0F - back);
            squash -= 0.1F * near;
        }
        return new Pose(u, v, lift, TILT, leanAt(u, v), 0.0F, squash, 1.0F);
    }

    // ------------------------------------------------------------------ close

    /** Coming back, scribbling it all out, crumpling it up, and lobbing it away. */
    private static Pose closing(SketchPlan plan, Events events, float time, float length) {
        float s = events.closeProgress(time);
        SketchPlan.Stroke first = plan.closing().get(0);
        if (s < RETURN_TO) {
            // Back from wherever it was - which is this same function, one instant before the close.
            Pose from = ducked(plan, events, events.closeFrom(), length);
            Pose to = onPage(first.u(0), first.v(0), 1.0F);
            float k = ease(s / RETURN_TO);
            return blend(from, to, k, arc(k) * 0.35F);
        }
        if (s < SCRIBBLE_TO) {
            List<SketchPlan.Stroke> strokes = plan.closing();
            for (int index = 0; index < strokes.size(); index++) {
                SketchPlan.Stroke stroke = strokes.get(index);
                if (s < stroke.start() && index > 0) {
                    // Between the two passes: the pencil still has to get from the end of one to the
                    // start of the other, which is across the whole drawing.
                    SketchPlan.Stroke previous = strokes.get(index - 1);
                    return hop(previous, stroke, (s - previous.end()) / (stroke.start() - previous.end()));
                }
                if (s <= stroke.end()) {
                    float drawn = stroke.drawn(s);
                    // A hand scribbling something out does not hold a steady line.
                    float shake = 0.02F * (float) Math.sin(time * 4.3D + events.seed() * 0.01D);
                    return onPage(stroke.headU(drawn) + shake, stroke.headV(drawn) - shake, 1.0F);
                }
            }
        }
        SketchPlan.Stroke last = plan.closing().get(plan.closing().size() - 1);
        float fromU = last.u(last.count() - 1);
        float fromV = last.v(last.count() - 1);
        if (s < CRUMPLE_TO) {
            // Up and back to watch its own drawing screw itself up.
            float k = ease((s - SCRIBBLE_TO) / (CRUMPLE_TO - SCRIBBLE_TO));
            return lifted(SketchPlan.lerp(k, fromU, 0.55F), SketchPlan.lerp(k, fromV, 0.62F),
                    SketchPlan.lerp(k, 0.0F, 0.55F), 1.0F);
        }
        if (s < FLICK_TO) {
            // The flick: a quick dip at the ball.
            float k = (s - CRUMPLE_TO) / (FLICK_TO - CRUMPLE_TO);
            float dip = (float) Math.sin(k * Math.PI);
            return held(0.55F - 0.35F * dip, 0.62F - 0.4F * dip, 0.55F - 0.35F * dip,
                    TILT + 0.5F * dip, 0.0F, 1.0F + 0.12F * dip, 1.0F);
        }
        // And away: off the way it came in, shrinking to nothing.
        float k = ease(SketchPlan.clamp((s - FLICK_TO) / (1.0F - FLICK_TO)));
        return held(SketchPlan.lerp(k, 0.55F, ARRIVE_U), SketchPlan.lerp(k, 0.62F, ARRIVE_V),
                SketchPlan.lerp(k, 0.55F, ARRIVE_LIFT), TILT, (float) (Math.PI * 2.0D) * k, 1.0F + 0.2F * k,
                1.0F - k);
    }

    /**
     * What the pencil is drawing with at a moment - which is what its lead is coloured, so that a pilot
     * can see it pick up each crayon.
     *
     * <p>The stroke it is on, or the last one it finished: a pencil that went back to graphite the
     * instant it lifted off would flicker through three colours in the second it takes to draw a rift.
     */
    public static SketchPlan.Ink ink(SketchPlan plan, Events events, float time) {
        if (events.closing() && time >= events.closeFrom()) {
            return SketchPlan.Ink.GRAPHITE; // scribbling out is done in pencil, whatever it was coloured in
        }
        SketchPlan.Ink ink = SketchPlan.Ink.GRAPHITE;
        for (SketchPlan.Stroke stroke : plan.opening()) {
            if (time < stroke.start()) {
                break;
            }
            ink = stroke.ink();
        }
        return ink;
    }

    /**
     * How much of the drawing is left as it is crumpled up: all of it until the scribbling is done, then
     * squeezing down to the ball it becomes.
     */
    public static float crumple(float closeProgress) {
        if (closeProgress <= SCRIBBLE_TO) {
            return 1.0F;
        }
        float k = SketchPlan.clamp((closeProgress - SCRIBBLE_TO) / (CRUMPLE_TO - SCRIBBLE_TO));
        return SketchPlan.lerp(ease(k), 1.0F, 0.0F);
    }

    /** How ragged its edges have gone by then. */
    public static float jag(float closeProgress) {
        return CRUMPLE_JAG * (1.0F - crumple(closeProgress));
    }

    /**
     * The crumpled ball, once the drawing has begun to screw up - growing as the drawing shrinks into
     * it, then lobbed away on an arc, spinning, and gone.
     */
    public static Ball ball(float closeProgress) {
        if (closeProgress <= SCRIBBLE_TO) {
            return null;
        }
        float growing = SketchPlan.clamp((closeProgress - SCRIBBLE_TO - 0.06F) / (CRUMPLE_TO - SCRIBBLE_TO - 0.06F));
        float size = BALL_SIZE * ease(growing);
        if (closeProgress <= FLICK_TO) {
            return new Ball(0.0F, 0.0F, 0.0F, size, closeProgress * 8.0F);
        }
        float k = SketchPlan.clamp((closeProgress - FLICK_TO) / (1.0F - FLICK_TO));
        // Tossed: out and up, and falling away by the end of it, the way anything thrown at a bin goes.
        return new Ball(-2.4F * k, 1.6F * k - 2.6F * k * k, 0.7F * k, size * (1.0F - k * k),
                closeProgress * 8.0F + k * 14.0F);
    }

    // ------------------------------------------------------------------- cues

    /** Everything worth hearing that falls after {@code from} and by {@code to}. */
    public static List<Cue> cues(SketchPlan plan, Events events, float from, float to) {
        List<Cue> cues = new ArrayList<>(2);
        if (events.closing() && to >= events.closeFrom()) {
            float was = events.closeProgress(from);
            float now = events.closeProgress(to);
            for (SketchPlan.Stroke stroke : plan.closing()) {
                if (stroke.start() > was && stroke.start() <= now) {
                    cues.add(Cue.SCRIBBLE);
                }
            }
            if (crossed(was, now, SCRIBBLE_TO)) {
                cues.add(Cue.CRUMPLE);
            }
            if (crossed(was, now, CRUMPLE_TO)) {
                cues.add(Cue.TOSS);
            }
            return cues;
        }
        if (SketchPlan.touchesDown(plan.opening(), from, to)) {
            cues.add(Cue.DRAW);
        }
        idleCues(plan, events, from, to, cues);
        return cues;
    }

    /** The taps and swishes of whichever moves the pencil was in the middle of. */
    private static void idleCues(SketchPlan plan, Events events, float from, float to, List<Cue> cues) {
        List<SketchPlan.Stroke> strokes = plan.opening();
        float idleFrom = strokes.get(strokes.size() - 1).end() + hopTicks(events);
        if (to <= idleFrom || duck(events, to) > 0.5F) {
            return; // still drawing, or out of the way and keeping quiet
        }
        float cursor = idleFrom;
        for (int index = 0; index < 512 && cursor <= to; index++) {
            Move move = move(events, index);
            float end = cursor + move.ticks;
            if (end > from) {
                if (move == Move.TAP) {
                    for (float at : TAPS) {
                        float when = cursor + move.ticks * at;
                        if (when > from && when <= to) {
                            cues.add(Cue.TAP);
                        }
                    }
                } else if ((move == Move.TWIRL || move == Move.FLIP) && cursor > from && cursor <= to) {
                    cues.add(Cue.SWISH);
                }
            }
            cursor = end;
        }
    }

    private static boolean crossed(float was, float now, float at) {
        return was < at && now >= at;
    }

    // ------------------------------------------------------------------ poses

    private static Pose rest() {
        return lifted(REST_U, REST_V, REST_LIFT, 1.0F);
    }

    /** The point on the page, held as it is held while drawing. */
    private static Pose onPage(float u, float v, float stretch) {
        return new Pose(u, v, 0.0F, TILT, leanAt(u, v), 0.0F, stretch, 1.0F);
    }

    private static Pose lifted(float u, float v, float lift, float stretch) {
        return new Pose(u, v, lift, TILT, leanAt(u, v), 0.0F, stretch, 1.0F);
    }

    private static Pose held(float u, float v, float lift, float tilt, float roll, float stretch, float scale) {
        return new Pose(u, v, lift, tilt, leanAt(u, v), roll, stretch, scale);
    }

    /**
     * Which way the pencil leans. The hand's own angle everywhere but out of the way of a ship, where it
     * leans outward instead so that the whole barrel is clear of the hole - see {@link #duckPose}.
     */
    private static float leanAt(float u, float v) {
        return LEAN;
    }

    private static Pose blend(Pose from, Pose to, float t) {
        return blend(from, to, t, 0.0F);
    }

    /** One pose into another, with an optional lift over the middle of the change. */
    private static Pose blend(Pose from, Pose to, float t, float over) {
        float k = SketchPlan.clamp(t);
        float u = SketchPlan.lerp(k, from.u(), to.u());
        float v = SketchPlan.lerp(k, from.v(), to.v());
        return new Pose(u, v, SketchPlan.lerp(k, from.lift(), to.lift()) + over,
                SketchPlan.lerp(k, from.tilt(), to.tilt()),
                SketchPlan.lerp(k, from.lean(), to.lean()),
                SketchPlan.lerp(k, from.roll(), to.roll()), SketchPlan.lerp(k, from.stretch(), to.stretch()),
                SketchPlan.lerp(k, from.scale(), to.scale()));
    }

    /** Nothing at either end, one in the middle: the shape of a hop, a dip or a flourish. */
    private static float arc(float k) {
        return (float) Math.sin(SketchPlan.clamp(k) * Math.PI);
    }

    private static float ease(float t) {
        float k = SketchPlan.clamp(t);
        return k * k * (3.0F - 2.0F * k);
    }
}
