package uk.co.iceconchy.aerowarptics.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * Every stroke a Sketch rift is drawn with, and when.
 *
 * <p>A drawing that appears is a picture; a drawing that is <em>drawn</em> needs something drawing it,
 * and one pencil can only be in one place - so the whole of a sketch is a single timeline of strokes,
 * none overlapping another, and {@link SketchPencil} puts the pencil's point wherever this says the
 * line has got to.
 *
 * <h2>Why it all happens in one second</h2>
 * A rift winds up for half a second and is open half a second later, and a hull can reach the plane
 * almost at once - {@code RiftEffectManager.transit} jumps a rift straight to open when one does. So
 * <em>everything</em> that has to exist before a ship arrives is drawn inside the opening: the outline,
 * the colour scribbled in, the inner line and the shine. It is a fast, confident cartoon scribble
 * rather than a careful drawing, which is also what makes it fun to watch. Afterwards the pencil only
 * acts; nothing more is drawn until the rift is scribbled out at the close.
 *
 * <p>The opening's strokes are timed in ticks from the rift appearing, scaled to whatever the opening
 * is worth. The closing's are timed in fractions of the close, which starts whenever the rift is told
 * to let go.
 *
 * <p>Free of Minecraft, and worked out from the rift's own seed, for the reasons {@link RiftShatter}
 * gives: every viewer draws the same sketch, and the ways it goes wrong - a line off the page, two
 * strokes at once, colour that does not reach the rim - do not throw.
 */
public final class SketchPlan {

    /** What a stroke is drawn with. */
    public enum Ink {
        /** The pencil's own graphite: the outline, and the scribbling out. */
        GRAPHITE,
        /** A crayon in the pilot's core colour: what the hole is coloured in with. */
        CORE,
        /** A crayon in the pilot's rim colour: the line inside the outline. */
        ACCENT,
        /** White: the cartoon shine. */
        SHINE
    }

    /** What a stroke is, which decides how thick and how solid it is drawn. */
    public enum Kind {
        /** The bold dark line round the hole. */
        OUTLINE(0.030F, 0.95F),
        /**
         * Colouring in. Wide and waxy - wider than the gap between two of its passes, so the passes run
         * into one another and fill the hole rather than striping it.
         */
        FILL(0.060F, 0.55F),
        /** The pilot's second colour, just inside the outline. */
        INNER(0.018F, 0.85F),
        /** The cartoon shine: one fat white swoosh. */
        SHINE(0.030F, 0.75F),
        /** Scribbling the whole thing out at the close. */
        SCRIBBLE_OUT(0.028F, 0.9F);

        private final float width;
        private final float strength;

        Kind(float width, float strength) {
            this.width = width;
            this.strength = strength;
        }

        /** Half the width a stroke of this kind is drawn at, in rim fractions. */
        public float width() {
            return width;
        }

        /** How solid a stroke of this kind is drawn, 0..1. */
        public float strength() {
            return strength;
        }
    }

    /**
     * One stroke of the pencil.
     *
     * @param points  {@code u, v} pairs along the stroke, in rim fractions
     * @param along   how far along the stroke each point is, 0 at the first and 1 at the last
     * @param start   when the pencil touches down - ticks for an opening stroke, close fractions for a
     *                closing one
     * @param end     when it lifts off
     * @param sounded whether touching down is heard
     */
    public record Stroke(Kind kind, Ink ink, float start, float end, float[] points, float[] along,
                         boolean sounded) {

        /** How much of it is down, 0..1. */
        public float drawn(float time) {
            return clamp((time - start) / (end - start));
        }

        public int count() {
            return points.length / 2;
        }

        public float u(int point) {
            return points[point * 2];
        }

        public float v(int point) {
            return points[point * 2 + 1];
        }

        /** Where the stroke has got to at a fraction of its length. */
        public float headU(float fraction) {
            int at = segment(fraction);
            return lerp(within(at, fraction), u(at), u(at + 1));
        }

        public float headV(float fraction) {
            int at = segment(fraction);
            return lerp(within(at, fraction), v(at), v(at + 1));
        }

        /** The segment a fraction of the length falls in. */
        public int segment(float fraction) {
            int last = count() - 2;
            for (int point = 0; point < last; point++) {
                if (along[point + 1] >= fraction) {
                    return point;
                }
            }
            return last;
        }

        /** How far through its segment a fraction of the length is. */
        public float within(int segment, float fraction) {
            float span = along[segment + 1] - along[segment];
            return span <= 0.0F ? 1.0F : clamp((fraction - along[segment]) / span);
        }
    }

    // ------------------------------------------------------------------ timing

    /** When each stroke of the opening starts and ends, as fractions of the opening. */
    public static final float OUTLINE_FROM = 0.08F;
    public static final float OUTLINE_TO = 0.33F;
    public static final float FILL_FROM = 0.40F;
    public static final float FILL_TO = 0.76F;
    public static final float INNER_FROM = 0.80F;
    public static final float INNER_TO = 0.92F;
    public static final float SHINE_FROM = 0.94F;
    public static final float SHINE_TO = 1.0F;

    /**
     * The gaps between them are not spare time. They are what the pencil crosses to get from the end of
     * one stroke to the start of the next, and a gap too short for the distance is a pencil that
     * teleports - which is what {@code SketchPencilTest} watches for.
     */
    public static final float HOP = FILL_FROM - OUTLINE_TO;

    /** When the scribbling out runs, as fractions of the close. */
    public static final float SCRIBBLE_FROM = 0.15F;
    public static final float SCRIBBLE_TO = 0.45F;
    /** When the first pass ends and the second begins: the gap is the pencil hopping between them. */
    public static final float SCRIBBLE_FIRST_TO = 0.28F;
    public static final float SCRIBBLE_SECOND_FROM = 0.31F;

    // ------------------------------------------------------------------ shapes

    /** How far past its own start the outline runs before the pencil lifts, in radians. */
    private static final float OVERSHOOT = 0.55F;
    private static final int OUTLINE_POINTS = 72;
    /** Which way the colouring-in runs across the hole. */
    private static final float FILL_HEADING = 0.55F;
    /**
     * Passes of the crayon across the hole. Odd, so it finishes on the far side from where it began, and
     * close enough together that each is inside the one beside it - see {@code SketchPlanTest}.
     */
    private static final int FILL_PASSES = 21;
    /**
     * How far past the rim each pass of colour runs, as a fraction of the chord it is filling.
     *
     * <p>Outside the lines, deliberately. A fill that stopped dead on the outline would read as printed;
     * colouring past the edge is the single clearest sign that something drew it in a hurry.
     */
    private static final float FILL_OVERSHOOT = 1.06F;
    /** Where the inner line sits, as a fraction of the rim. */
    private static final float INNER_RING = 0.87F;
    /** The shine: where it sits, how far round it sweeps, and where round the hole it is. */
    private static final float SHINE_RING = 0.63F;
    private static final float SHINE_SWEEP = 0.85F;
    private static final float SHINE_AT = 2.45F;
    /** Zig-zags in each of the two scribbling-out passes, and how far apart the two passes' headings are. */
    private static final int SCRIBBLE_ZIGS = 11;
    private static final float SCRIBBLE_CROSS = 1.35F;

    private final List<Stroke> opening;
    private final List<Stroke> closing;
    private final float fillHeading;

    private SketchPlan(List<Stroke> opening, List<Stroke> closing, float fillHeading) {
        this.opening = opening;
        this.closing = closing;
        this.fillHeading = fillHeading;
    }

    /**
     * The sketch for one rift.
     *
     * @param openTicks how long the rift takes to open, in ticks: the whole drawing fits inside it
     */
    public static SketchPlan of(int seed, float openTicks) {
        float heading = FILL_HEADING + (RiftShatter.noise(seed, 17_400) - 0.5F) * 0.5F;
        List<Stroke> opening = new ArrayList<>();
        opening.add(timed(Kind.OUTLINE, Ink.GRAPHITE, outline(seed, 1.0F, OVERSHOOT, OUTLINE_POINTS),
                OUTLINE_FROM * openTicks, OUTLINE_TO * openTicks, true));
        opening.add(timed(Kind.FILL, Ink.CORE, fill(heading),
                FILL_FROM * openTicks, FILL_TO * openTicks, true));
        opening.add(timed(Kind.INNER, Ink.ACCENT, outline(seed + 977, INNER_RING, 0.3F, 40),
                INNER_FROM * openTicks, INNER_TO * openTicks, true));
        opening.add(timed(Kind.SHINE, Ink.SHINE, shine(),
                SHINE_FROM * openTicks, SHINE_TO * openTicks, false));

        List<Stroke> closing = new ArrayList<>();
        closing.add(timed(Kind.SCRIBBLE_OUT, Ink.GRAPHITE, scribbleOut(seed, 0.0F),
                SCRIBBLE_FROM, SCRIBBLE_FIRST_TO, true));
        closing.add(timed(Kind.SCRIBBLE_OUT, Ink.GRAPHITE, scribbleOut(seed + 31, SCRIBBLE_CROSS),
                SCRIBBLE_SECOND_FROM, SCRIBBLE_TO, true));
        return new SketchPlan(List.copyOf(opening), List.copyOf(closing), heading);
    }

    /** The strokes drawn while the rift opens, in ticks from the rift appearing. */
    public List<Stroke> opening() {
        return opening;
    }

    /** The strokes that scribble it out, in fractions of the close. */
    public List<Stroke> closing() {
        return closing;
    }

    /**
     * How far the colour has got across the hole, as a distance along the fill's own heading from
     * {@code -1} at the first pass to {@code 1} past the last.
     *
     * <p>What the opaque face is revealed by: a piece of it is there once the colour has reached it, so
     * the hole fills in behind the crayon instead of growing as a circle. Past one - so, complete -
     * from the moment the fill is finished, which is well before the opening is over. A hull that
     * arrives early jumps the rift to fully open, and that lands here as "all of it".
     */
    public float front(float time) {
        Stroke fill = opening.get(1);
        float drawn = fill.drawn(time);
        if (drawn >= 1.0F) {
            return 2.0F;
        }
        if (drawn <= 0.0F) {
            return -2.0F;
        }
        // Taken from where the crayon actually is rather than from how much of the stroke is down: the
        // passes are chords, so they are not the same length, and a fill that filled evenly in time
        // would run ahead of the pencil at the ends of the hole and behind it across the middle.
        return across(fill.headU(drawn), fill.headV(drawn));
    }

    /** How far along the fill's own heading a point on the face lies. */
    public float across(float u, float v) {
        return (float) (-Math.sin(fillHeading) * u + Math.cos(fillHeading) * v);
    }

    /** Which way the colouring-in runs, in radians. The face is revealed along the same line. */
    public float fillHeading() {
        return fillHeading;
    }

    /** Whether a stroke worth hearing touches down after {@code from} and by {@code to}. */
    public static boolean touchesDown(List<Stroke> strokes, float from, float to) {
        for (Stroke stroke : strokes) {
            if (stroke.sounded() && stroke.start() > from && stroke.start() <= to) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ build

    /**
     * A loose loop round the hole: wobbling off true, and running on past where it began, which is what
     * a circle drawn in one stroke actually does.
     */
    private static float[] outline(int seed, float ring, float overshoot, int steps) {
        double start = RiftShatter.noise(seed, 17_000) * Math.PI * 2.0D;
        double phase1 = RiftShatter.noise(seed, 17_100) * Math.PI * 2.0D;
        double phase2 = RiftShatter.noise(seed, 17_200) * Math.PI * 2.0D;
        float[] points = new float[(steps + 1) * 2];
        for (int point = 0; point <= steps; point++) {
            double angle = start + (Math.PI * 2.0D + overshoot) * point / steps;
            double radius = ring * (1.0D + 0.028D * Math.sin(angle * 2.0D + phase1)
                    + 0.016D * Math.sin(angle * 5.0D + phase2));
            points[point * 2] = (float) (Math.cos(angle) * radius);
            points[point * 2 + 1] = (float) (Math.sin(angle) * radius);
        }
        return points;
    }

    /**
     * The colouring-in: passes back and forth across the whole hole, joined end to end into one stroke
     * the way a crayon goes without lifting, each running a little past the edge.
     */
    private static float[] fill(float heading) {
        float alongU = (float) Math.cos(heading);
        float alongV = (float) Math.sin(heading);
        float[] points = new float[FILL_PASSES * 4];
        int at = 0;
        float gap = 2.0F / FILL_PASSES;
        for (int pass = 0; pass < FILL_PASSES; pass++) {
            float offset = -1.0F + (pass + 0.5F) * gap;
            // Cut to the chord at the near edge of the band this pass is filling rather than at its
            // middle: at the top and bottom of the hole a chord shortens fast, and a pass cut to its own
            // line leaves the corners of its band bare.
            float edge = Math.max(0.0F, Math.abs(offset) - gap * 0.5F);
            float half = (float) Math.sqrt(Math.max(0.0F, 1.0F - edge * edge)) * FILL_OVERSHOOT;
            float direction = pass % 2 == 0 ? 1.0F : -1.0F;
            float baseU = -alongV * offset;
            float baseV = alongU * offset;
            points[at++] = baseU - alongU * half * direction;
            points[at++] = baseV - alongV * half * direction;
            points[at++] = baseU + alongU * half * direction;
            points[at++] = baseV + alongV * half * direction;
        }
        return points;
    }

    /** The shine: a short fat arc up and to one side, the way anything shiny is drawn in a cartoon. */
    private static float[] shine() {
        int steps = 10;
        float[] points = new float[(steps + 1) * 2];
        for (int point = 0; point <= steps; point++) {
            double angle = SHINE_AT - SHINE_SWEEP * 0.5D + SHINE_SWEEP * point / steps;
            points[point * 2] = (float) (Math.cos(angle) * SHINE_RING);
            points[point * 2 + 1] = (float) (Math.sin(angle) * SHINE_RING);
        }
        return points;
    }

    /** One frantic pass of scribbling out: a zig-zag across the whole hole, and a little past it. */
    private static float[] scribbleOut(int seed, float turn) {
        float heading = FILL_HEADING + turn;
        float alongU = (float) Math.cos(heading);
        float alongV = (float) Math.sin(heading);
        float[] points = new float[(SCRIBBLE_ZIGS + 1) * 2];
        for (int zig = 0; zig <= SCRIBBLE_ZIGS; zig++) {
            float offset = -1.0F + 2.0F * zig / SCRIBBLE_ZIGS;
            // Longer than the hole is wide and never the same length twice: a scribble is not tidy.
            float reach = (zig % 2 == 0 ? 1.0F : -1.0F)
                    * (0.75F + 0.35F * RiftShatter.noise(seed, 17_600 + zig));
            points[zig * 2] = -alongV * offset + alongU * reach;
            points[zig * 2 + 1] = alongU * offset + alongV * reach;
        }
        return points;
    }

    private static Stroke timed(Kind kind, Ink ink, float[] points, float start, float end, boolean sounded) {
        float[] along = new float[points.length / 2];
        float total = length(points);
        float run = 0.0F;
        for (int point = 1; point < along.length; point++) {
            run += (float) Math.hypot(points[point * 2] - points[point * 2 - 2],
                    points[point * 2 + 1] - points[point * 2 - 1]);
            along[point] = total <= 0.0F ? 1.0F : run / total;
        }
        along[along.length - 1] = 1.0F;
        return new Stroke(kind, ink, start, end, points, along, sounded);
    }

    private static float length(float[] points) {
        float total = 0.0F;
        for (int point = 2; point + 1 < points.length; point += 2) {
            total += (float) Math.hypot(points[point] - points[point - 2], points[point + 1] - points[point - 1]);
        }
        return total;
    }

    static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }

    static float clamp(float t) {
        return Math.max(0.0F, Math.min(1.0F, t));
    }
}
