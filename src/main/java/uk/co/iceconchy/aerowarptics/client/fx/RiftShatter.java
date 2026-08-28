package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;

/**
 * How a rift breaks space open.
 *
 * <p>An aperture does not iris open. It arrives the way a stone arrives through a window: a hard point
 * of impact, radial cracks racing out from it, concentric fracture rings crossing them, and then the
 * panes between falling away. That is the actual fracture pattern of struck glass, and it is what
 * {@link Pattern#GLASS} produces - {@link #CRACKS} radial fractures crossed by {@link #RINGS} rings,
 * giving one shard per cell.
 *
 * <p>A Rift Modulator can ask for a different pattern instead - {@link Pattern#GEARS}, an even ring of
 * teeth that click open one after another, or {@link Pattern#RUNES}, a precise grid of ring bands that
 * light in place rather than flying anywhere. All three share the same {@link Shard} shape and the same
 * timing curves below ({@link #hole}, {@link #crackReach}, {@link #travel}, {@link #fade}, {@link
 * #sealLife}, {@link #sealFade}) - a pattern only changes <em>where the cuts run and how a piece
 * leaves</em>, never how fast the hole itself opens or closes. {@code RiftEffectManager} draws every
 * pattern through the exact same shard-fling and shard-return code; the shape handed to it is the only
 * thing that differs.
 *
 * <p>Plain arithmetic and a hash, for the same reason {@link RiftTear} is: every viewer works the same
 * fracture out from the rift's own position, so two players watching one aperture see the same break
 * without a byte being sent about it.
 *
 * <h2>What this must not do</h2>
 * The face of an aperture is the thing that hides an airship going through it, and {@link RiftTear}
 * guarantees the rim never cuts inside the circle doing the hiding. Nothing here is allowed to weaken
 * that. The shatter is the <em>presentation</em> of an aperture opening, not the mechanism: it runs
 * entirely within the opening budget, which the flight planner floors at twenty ticks of approach, and
 * the hole is fully open long before a bow reaches the plane. Shards are drawn outside the occluding
 * surface and hide nothing - true of every pattern, since none of them touch {@link #hole}.
 */
public final class RiftShatter {

    /** Radial fractures running out from the impact, for the default {@link Pattern#GLASS}. */
    public static final int CRACKS = 13;

    /** Concentric fracture rings crossing them, for the default {@link Pattern#GLASS}. */
    public static final int RINGS = 4;

    /** One shard per cell of the fracture, for the default {@link Pattern#GLASS}. */
    public static final int SHARDS = CRACKS * RINGS;

    /** Ticks a {@link Sequencing#RATCHET} pattern spends letting go all the way round. */
    private static final float SEQUENTIAL_SPREAD = 8.0F;

    /**
     * The order pieces let go in.
     *
     * <p>This is most of what tells one opening from another before anything has moved: a pane struck
     * in the middle comes apart from the middle, a mechanism releases one tooth after the next, and a
     * circle that is being <em>unmade</em> goes from its edge inwards.
     */
    public enum Sequencing {
        /** Outwards from the impact: the middle of the pane goes first. Struck glass, and spreading fire. */
        RADIAL,
        /** One after another around the rim, like a ratchet turning. */
        RATCHET,
        /** Rim first, working in towards the centre. */
        INWARD,
        /** No order at all. Stars do not go out in sequence. */
        SCATTER
    }

    /**
     * How a piece moves once it is loose - and the whole reason a theme reads as its own thing.
     *
     * <p>The fracture shape alone is nearly invisible: cells this small, seen for two seconds, all
     * look much the same however they were cut. What the eye actually reads is the <em>motion</em>, so
     * this is the field that matters. A gear whose teeth tumble away face-over-edge is a pane of glass
     * that happens to have been cut into ten pieces, which is exactly the wrong answer.
     */
    public enum Motion {
        /**
         * Thrown outward, tumbling about an axis lying in the aperture's plane, and pushed through
         * the plane. The piece flips face-over-edge, catching the light as it goes - what makes a
         * field of fragments glitter, and what makes them read as glass.
         */
        TUMBLE,
        /**
         * Swept round the aperture's own centre, staying flat in its plane the whole way - an iris
         * blade retracting rather than a fragment thrown. Nothing tumbles and nothing leaves the
         * plane, because a mechanism's parts stay in the mechanism.
         */
        SWING,
        /**
         * Never moves at all. The piece ignites where it stands, burns white, and goes out. What is
         * being watched is a circle being consumed, not a surface coming apart.
         */
        DISSOLVE,
        /**
         * Catches, curls, lifts, and darkens - burning paper rather than breaking glass. A piece
         * shrinks as it chars and rises rather than being thrown, so the pane is consumed from where
         * the fire caught rather than knocked out of its frame.
         */
        CHAR,
        /**
         * Drifts outward on a slow spiral, shrinking to a point and twinkling on the way. Nothing is
         * thrown and nothing tumbles; the pane comes apart the way a cloud does.
         */
        DRIFT
    }

    /**
     * A fracture shape: how many cuts, how they are spaced, and how a piece leaves once it is cut
     * loose.
     *
     * <p>{@link #GLASS} is worked out once here and then never touched again - every field below
     * reproduces the numbers the old, pattern-less version of this class used, so a Modulator left on
     * {@link uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme#STANDARD} draws bit-for-bit what
     * it always has. {@code RiftShatterTest} pins that down rather than trusting it.
     *
     * @param cracks          radial cuts
     * @param rings           concentric bands crossing them
     * @param organicJitter   whether cracks and rings wander off an even grid - true for hand-broken
     *                        glass, false for a gear or a rune circle, which are cut precisely
     * @param ringExponent    how bunched the rings are towards the middle; 1.0 is even spacing
     * @param sequencing      the order pieces let go in
     * @param motion          how a piece moves once it is loose
     * @param outwardBase     how far a piece travels outward, as a fraction of the rim, before the
     *                        next two terms are added
     * @param outwardMidScale how much further a piece further from the centre travels
     * @param outwardVariance per-piece random spread added to the above
     * @param pushVariance    how hard a piece is thrown along the aperture's normal, at most - 0 keeps
     *                        every piece flat in the aperture's own plane. {@link Motion#TUMBLE} only
     * @param spinBase        for {@link Motion#TUMBLE}, radians of tumble per tick; for
     *                        {@link Motion#SWING}, the total angle a blade sweeps through
     * @param spinVariance    per-piece random spread added to the above
     * @param fixedAxis       whether every piece turns about the same axis (mechanical, uniform) or
     *                        its own random one (glass, chaotic). {@link Motion#TUMBLE} only
     * @param lifeScale       how much of {@link #SHARD_LIFE} a piece of this pattern actually gets.
     *                        Glass drifts for the full long tail; a blade is swept aside and gone, and
     *                        a rune burns out. A piece that does not travel must not outstay one that
     *                        does, or it sits over the opening it was supposed to have got out of
     */
    public record Pattern(int cracks, int rings, boolean organicJitter, float ringExponent,
                          Sequencing sequencing, Motion motion, float outwardBase, float outwardMidScale,
                          float outwardVariance, float pushVariance, float spinBase, float spinVariance,
                          boolean fixedAxis, float lifeScale) {

        /** Struck glass: irregular cracks and rings, pieces tumbling away every which way. */
        public static final Pattern GLASS = new Pattern(CRACKS, RINGS, true, 1.35F,
                Sequencing.RADIAL, Motion.TUMBLE, 0.30F, 0.85F, 0.35F, 1.0F, 0.0F, 0.55F, false, 1.0F);

        /**
         * An iris: ten blades running the full radius, released one after another around the rim and
         * swept aside about the aperture's centre.
         *
         * <p>One ring, so a blade is a single wedge from the middle to the rim rather than a stack of
         * cells - a shutter has blades, not tiles.
         */
        public static final Pattern GEARS = new Pattern(10, 1, false, 1.0F,
                Sequencing.RATCHET, Motion.SWING, 0.45F, 0.0F, 0.0F, 0.0F, 1.15F, 0.0F, true, 0.5F);

        /** A rune circle: precise bands that ignite where they stand, from the rim inwards. */
        public static final Pattern RUNES = new Pattern(12, 3, false, 1.0F,
                Sequencing.INWARD, Motion.DISSOLVE, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.35F);

        /** Burning paper: an irregular pane that catches in the middle and chars outwards. */
        public static final Pattern EMBERS = new Pattern(11, 3, true, 1.2F,
                Sequencing.RADIAL, Motion.CHAR, 0.05F, 0.10F, 0.08F, 0.0F, 0.0F, 0.25F, true, 0.6F);

        /** A field of stars: irregular, going out in no order, drifting apart on a slow spiral. */
        public static final Pattern MOTES = new Pattern(9, 4, true, 1.0F,
                Sequencing.SCATTER, Motion.DRIFT, 0.25F, 0.35F, 0.30F, 0.0F, 0.35F, 0.15F, true, 0.85F);

        /**
         * Every pattern this mod has, so a test can enumerate them and a new one cannot be added
         * without the invariants below being held to it.
         */
        public static final java.util.List<Pattern> ALL = java.util.List.of(GLASS, GEARS, RUNES, EMBERS, MOTES);
    }

    /**
     * Which shape a Rift Modulator's theme breaks its aperture along.
     *
     * <p>Lives here rather than on {@link uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme}
     * because that enum is shared with the server - it sits on the block entity and travels in a packet
     * - and a {@link Pattern} is purely a client rendering shape with nothing to say about how a warp
     * runs. Reading the shared enum from this side is free; the dependency must not go the other way.
     *
     * <p>Deliberately exhaustive with no {@code default}: a theme added without a shape to break along
     * fails to compile here rather than silently falling back to glass, which is the mistake that made
     * the first pass at themes look like one animation in five colours.
     */
    public static Pattern patternFor(uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme theme) {
        return switch (theme) {
            case STANDARD -> Pattern.GLASS;
            case CLOCKWORK -> Pattern.GEARS;
            case ARCANE -> Pattern.RUNES;
            case EMBER -> Pattern.EMBERS;
            case STARLIGHT -> Pattern.MOTES;
        };
    }

    /**
     * Fraction of the opening spent cracking before anything breaks free.
     *
     * <p>Everything up to here is a lit fracture over an intact view: the world behind the rift is
     * still there to see. The hole only exists afterwards.
     */
    public static final float CRACK_PHASE = 0.5F;

    /** Ticks a shard drifts before it has faded to nothing. */
    public static final float SHARD_LIFE = 45.0F;

    /** Ticks between the middle of the pane breaking and the rim breaking. */
    public static final float BREAK_SPREAD = 6.0F;

    /**
     * How much of a seal is spent waiting, for the piece that comes home last.
     *
     * <p>Space closes from the rim inwards, which is the opposite order to the way it broke. The
     * outermost pieces are back in place while the middle is still open, so the hole shuts down to a
     * point rather than fading out evenly - and a point is something the eye can watch close.
     */
    public static final float SEAL_STAGGER = 0.35F;

    private RiftShatter() {
    }

    /**
     * One piece of broken space.
     *
     * <p>Angles and radii are the cell it was cut from - radii as fractions of the rim - and the rest
     * is how it leaves. Nothing here is in world units; the renderer scales it to the aperture.
     *
     * @param delay   ticks after the break begins before this piece detaches
     * @param outward how hard it is thrown away from the impact, as a fraction of the rim
     * @param push    how hard it is thrown along the aperture's normal, signed, so a pane bursts both ways
     * @param spin    radians per tick of travel it tumbles through
     * @param axis    which way its tumble axis lies in the aperture's plane
     */
    public record Shard(float angle0, float angle1, float innerT, float outerT,
                        float delay, float outward, float push, float spin, float axis) {

        public float midAngle() {
            return (angle0 + angle1) * 0.5F;
        }

        public float midRadius() {
            return (innerT + outerT) * 0.5F;
        }
    }

    // ------------------------------------------------------------------ seed

    /**
     * A stable seed for the rift at a position.
     *
     * <p>Quantised rather than taken from the raw doubles, so a rift is the same rift to every client
     * even if a coordinate arrives a hair different. Two apertures in the same warp are metres apart,
     * so sixteenths are far finer than they need to be to tell them apart - and the entry and exit
     * rifts of one jump breaking differently is the point.
     */
    public static int seedFor(double x, double y, double z) {
        int xi = Mth.floor(x * 16.0D);
        int yi = Mth.floor(y * 16.0D);
        int zi = Mth.floor(z * 16.0D);
        return xi * 0x9E3779B1 ^ yi * 0x85EBCA77 ^ zi * 0xC2B2AE3D;
    }

    /** A repeatable value in {@code [0, 1)} for a seed and an index. */
    public static float noise(int seed, int index) {
        int h = seed * 0x27D4EB2F + index * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        h *= 0x27D4EB2F;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
    }

    // -------------------------------------------------------------- fracture

    /**
     * Where the radial cracks run, in ascending order, for the default {@link Pattern#GLASS}.
     *
     * <p>Jitter is bounded well inside half a gap on purpose. Two cracks that swapped places would
     * make a shard of negative width, which draws as a fold rather than a piece of glass - so the
     * bound is what keeps the ordering true without a sort.
     */
    public static float[] crackAngles(int seed) {
        return crackAngles(seed, Pattern.GLASS);
    }

    /**
     * Where a pattern's radial cracks run, in ascending order.
     *
     * <p>{@link Pattern#organicJitter()} is what keeps this safe from the same crossed-crack problem
     * {@link #crackAngles(int)} documents: a precise pattern has no jitter to bound in the first place.
     */
    public static float[] crackAngles(int seed, Pattern pattern) {
        int cracks = pattern.cracks();
        float[] angles = new float[cracks];
        float gap = (float) (Math.PI * 2.0D / cracks);
        float jitter = pattern.organicJitter() ? gap * 0.7F : 0.0F;
        for (int crack = 0; crack < cracks; crack++) {
            angles[crack] = crack * gap + (noise(seed, crack) - 0.5F) * jitter;
        }
        return angles;
    }

    /**
     * Where the concentric fracture rings sit, as fractions of the rim, from the impact outwards, for
     * the default {@link Pattern#GLASS}.
     *
     * <p>Bunched towards the middle, which is where struck glass actually rings closest together.
     */
    public static float[] ringRadii(int seed) {
        return ringRadii(seed, Pattern.GLASS);
    }

    /** Where a pattern's concentric rings sit, as fractions of the rim, from the impact outwards. */
    public static float[] ringRadii(int seed, Pattern pattern) {
        int rings = pattern.rings();
        float[] radii = new float[rings + 1];
        radii[0] = 0.0F;
        radii[rings] = 1.0F;
        for (int ring = 1; ring < rings; ring++) {
            float base = spacing(ring, rings, pattern.ringExponent());
            if (pattern.organicJitter()) {
                float gap = base - spacing(ring - 1, rings, pattern.ringExponent());
                radii[ring] = base + (noise(seed, 101 + ring) - 0.5F) * gap * 0.5F;
            } else {
                radii[ring] = base;
            }
        }
        return radii;
    }

    private static float spacing(int ring, int rings, float exponent) {
        return (float) Math.pow(ring / (double) rings, exponent);
    }

    /** The whole fracture: one shard per cell, ordered from the impact outwards, for {@link Pattern#GLASS}. */
    public static Shard[] fracture(int seed) {
        return fracture(seed, Pattern.GLASS);
    }

    /**
     * The whole fracture for a given pattern: one shard per cell, ordered from the impact outwards.
     *
     * <p>Every field on {@link Shard} beyond the cut itself - {@link Shard#delay()}, {@link
     * Shard#outward()}, {@link Shard#push()}, {@link Shard#spin()}, {@link Shard#axis()} - is worked
     * out from the pattern's own knobs, so {@link Pattern#GLASS} reproduces this method's old,
     * pattern-less numbers exactly and {@link Pattern#GEARS}/{@link Pattern#RUNES} only had to describe
     * how they differ from it.
     */
    public static Shard[] fracture(int seed, Pattern pattern) {
        float[] angles = crackAngles(seed, pattern);
        float[] radii = ringRadii(seed, pattern);
        int cracks = pattern.cracks();
        int rings = pattern.rings();
        Shard[] shards = new Shard[cracks * rings];

        int index = 0;
        for (int ring = 0; ring < rings; ring++) {
            for (int crack = 0; crack < cracks; crack++) {
                float angle0 = angles[crack];
                // The last cell closes the loop onto the first crack, one turn on.
                float angle1 = crack + 1 < cracks
                        ? angles[crack + 1]
                        : angles[0] + (float) (Math.PI * 2.0D);
                float inner = radii[ring];
                float outer = radii[ring + 1];
                float mid = (inner + outer) * 0.5F;

                int n = 1_000 + index * 7;
                float delay = switch (pattern.sequencing()) {
                    case RATCHET -> (crack / (float) cracks) * SEQUENTIAL_SPREAD;
                    case INWARD -> (1.0F - mid) * BREAK_SPREAD;
                    case SCATTER -> noise(seed, n + 4) * BREAK_SPREAD;
                    case RADIAL -> mid * BREAK_SPREAD;
                };
                float outward = pattern.outwardBase() + pattern.outwardMidScale() * mid
                        + pattern.outwardVariance() * noise(seed, n);
                float push = pattern.pushVariance() <= 0.0F ? 0.0F
                        : (noise(seed, n + 1) - 0.5F) * 2.0F * pattern.pushVariance();
                float spin = pattern.spinBase() + (pattern.spinVariance() <= 0.0F ? 0.0F
                        : (noise(seed, n + 2) - 0.5F) * pattern.spinVariance());
                // A fixed axis is what makes a field of pieces tumble in step, like teeth on one gear
                // rather than a handful of glass thrown in the air.
                float axis = pattern.fixedAxis() ? 0.0F : noise(seed, n + 3) * (float) (Math.PI * 2.0D);

                shards[index++] = new Shard(angle0, angle1, inner, outer, delay, outward, push, spin, axis);
            }
        }
        return shards;
    }

    // ----------------------------------------------------------------- shape

    /**
     * How open the hole is, given progress through the whole opening.
     *
     * <p>Nothing for the whole crack phase - the fracture is lit but the world behind it is intact -
     * and then the hole arrives fast. Space does not ease open.
     */
    public static float hole(float openProgress) {
        if (openProgress <= CRACK_PHASE) {
            return 0.0F;
        }
        float t = Mth.clamp((openProgress - CRACK_PHASE) / (1.0F - CRACK_PHASE), 0.0F, 1.0F);
        float remaining = 1.0F - t;
        return 1.0F - remaining * remaining * remaining;
    }

    /** How far the cracks have run, 0..1, given progress through the crack phase. */
    public static float crackReach(float crackProgress) {
        float t = Mth.clamp(crackProgress, 0.0F, 1.0F);
        float remaining = 1.0F - t;
        return 1.0F - remaining * remaining * remaining;
    }

    /**
     * How far along its arc a shard has travelled, 0..1.
     *
     * <p>Eased out, so a piece bursts away and then coasts. There is nothing in here to slow it down,
     * but a fragment that kept accelerating would read as being blown rather than as having been let
     * go of.
     */
    public static float travel(float life) {
        float t = Mth.clamp(life, 0.0F, 1.0F);
        float remaining = 1.0F - t;
        return 1.0F - remaining * remaining;
    }

    /**
     * How far through its own return a piece is, given how far through the seal the aperture is.
     *
     * <p>Negative before this piece has started coming back, past one once it is home.
     *
     * @param midRadius where the piece sits, from the middle of the pane out to the rim
     */
    public static float sealLife(float sealProgress, float midRadius) {
        float waited = (1.0F - midRadius) * SEAL_STAGGER;
        return (sealProgress - waited) / (1.0F - SEAL_STAGGER);
    }

    /**
     * How visible a returning shard is, 0..1.
     *
     * <p>The mirror of {@link #fade}: it arrives out of nothing, is brightest on the way in, and is
     * gone by the time it lands. A piece still visible when it gets home would be a shard sitting in a
     * hole that has just closed over it.
     */
    public static float sealFade(float life) {
        if (life <= 0.0F || life >= 1.0F) {
            return 0.0F;
        }
        float rise = Mth.clamp(life * 5.0F, 0.0F, 1.0F);
        float fall = 1.0F - life * life * life;
        return rise * fall;
    }

    /**
     * How visible a shard is, 0..1, given how far through its life it is.
     *
     * <p>Snaps in as it detaches and falls away over the rest, so the break is an event and the drift
     * afterwards is a long quiet tail.
     */
    public static float fade(float life) {
        if (life <= 0.0F || life >= 1.0F) {
            return 0.0F;
        }
        float rise = Mth.clamp(life * 14.0F, 0.0F, 1.0F);
        return rise * (1.0F - life * life);
    }
}
