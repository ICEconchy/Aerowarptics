package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * How a rift breaks space open.
 *
 * <p>An aperture does not iris open. It arrives the way a stone arrives through a window: a hard point
 * of impact crushed white, radial cracks racing out from it and kinking as they go, ring cracks
 * zipping across between them from the middle outwards, and then the panes between falling away.
 * That is the actual fracture pattern of struck glass, and it is what {@link Pattern#GLASS} produces -
 * {@link #CRACKS} radial fractures crossed by {@link #RINGS} rings, laid out as a {@link Web}, giving
 * one shard per cell of it.
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
    public static final int CRACKS = 14;

    /**
     * Concentric fracture rings crossing them, for the default {@link Pattern#GLASS}.
     *
     * <p>Five, where the old wheel of wedges had four: a struck pane's rings are what make it read as
     * a web rather than a star, and with the inner ones bunched towards the impact four left the
     * middle looking like spokes.
     */
    public static final int RINGS = 5;

    /** One shard per cell of the fracture, for the default {@link Pattern#GLASS}. */
    public static final int SHARDS = CRACKS * RINGS;

    /** Ticks a {@link Sequencing#RATCHET} pattern spends letting go all the way round. */
    private static final float SEQUENTIAL_SPREAD = 8.0F;

    private static final float TWO_PI = (float) (Math.PI * 2.0D);

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
        SCATTER,
        /**
         * No stagger at all: every piece goes on the same tick.
         *
         * <p>The odd one out, and deliberately so. Every other sequencing spends part of the shatter
         * budget deciding who goes first, which is what makes an opening read as something coming
         * apart. A jump to lightspeed is not something coming apart - it happens to the whole pane at
         * once, and any stagger at all would give it away as a lot of small events rather than one
         * large one.
         */
        SIMULTANEOUS
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
        DRIFT,
        /**
         * Never detaches. The piece stays anchored at its inner edge while its outer edge races away,
         * so a cell stops being a tile and becomes a radial line - the pane is not broken, it is
         * smeared. What sells a jump to lightspeed is that the shape of the aperture survives the
         * motion; a field of stretched streaks is still recognisably the circle it started as.
         */
        STREAK,
        /**
         * Dragged round the aperture's centre by an amount that falls off with radius: the middle
         * whips round, the rim barely moves. Nothing is thrown and nothing is consumed - the pane is
         * still all there, wound into a vortex. Differential rotation is the only motion in this list
         * that reads as the <em>space</em> bending rather than as an object breaking.
         */
        SHEAR,
        /**
         * Torn into the middle and stretched thin on the way - gravity rather than fracture. Each
         * piece's inner edge runs in faster than its outer one, so cells draw out along the radius
         * towards the hole, and they shudder as they go. Distinct from {@link #SHEAR}, which turns the
         * pane without taking any of it anywhere, and from {@link #DRIFT}, which spirals outward.
         */
        IMPLODE,
        /**
         * Scaled along one fixed axis of the aperture rather than moved: the circle draws out into a
         * longer and longer ellipse, then releases. Nothing rotates, nothing travels outward radially,
         * and every piece stretches by the same factor, so the pane deforms as one object.
         */
        ELONGATE,
        /**
         * Pieces snap onto a crossing grid - alternate cells slide along the aperture's two axes, so
         * what was a radial fracture resolves into bands running over and under one another. The joke
         * only lands if the bands are crisp, so nothing here wanders or spins.
         */
        PLAID,
        /**
         * Travels in hard steps rather than continuously, fading out and back in at each one. The
         * piece is never between two places; it is at one of five, and the eye reads the gaps as the
         * thing failing to arrive properly rather than as something moving quickly.
         */
        STUTTER,
        /**
         * Flickers where it was cut, is gone, and is somewhere else - once. An enderman does not walk
         * anywhere, and neither does this: the piece is at home or at the one place it went, never
         * between. Distinct from {@link #STUTTER}, which is a piece failing to travel in steps; this is
         * a piece not travelling at all.
         */
        BLINK,
        /**
         * Nothing breaks, and there are no pieces to draw. The pane is not broken open at all: the hole
         * arrives some other way - for a sketch, inside a circle a pencil has just drawn round it, and for
         * a capital ship, in the middle of a black cloud - so there is nothing to throw, nothing to return
         * on the seal, and nothing here to draw either way.
         */
        UNBROKEN,
        /**
         * Picks one of the others per piece, from the piece's own seed, and picks again for the next
         * rift. The only motion here that is not a description of a movement but of a refusal to
         * settle on one - which is the whole of the joke, and the reason it must be last.
         */
        IMPROBABLE
    }

    /**
     * A fracture shape: how many cuts, how they are spaced, and how a piece leaves once it is cut
     * loose.
     *
     * <p>{@link #GLASS} is what every pattern-less overload in this class means - it is the break a
     * Modulator left on {@link uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme#STANDARD}
     * draws, and the one gates, chutes and fissures always draw. {@code RiftShatterTest} pins that down
     * rather than trusting it.
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
     * @param wander          how far the cuts stray from a clean polar grid, 0 to 1 - each crack kinks
     *                        where it crosses a ring, and each ring comes out lumpy rather than round.
     *                        See {@link Web}. Only glass has any: a mechanism or a figure is cut true,
     *                        and every planar motion sweeps its pieces as polar wedges, which a
     *                        kinked cell is not
     */
    public record Pattern(int cracks, int rings, boolean organicJitter, float ringExponent,
                          Sequencing sequencing, Motion motion, float outwardBase, float outwardMidScale,
                          float outwardVariance, float pushVariance, float spinBase, float spinVariance,
                          boolean fixedAxis, float lifeScale, float wander) {

        /**
         * Struck glass: a web of kinked cracks and lumpy rings, pieces bursting out along the way the
         * hull travels, tumbling as they go.
         */
        public static final Pattern GLASS = new Pattern(CRACKS, RINGS, true, 1.35F,
                Sequencing.RADIAL, Motion.TUMBLE, 0.30F, 0.85F, 0.35F, 1.0F, 0.0F, 0.55F, false, 1.0F, 1.0F);

        /**
         * An iris: ten blades running the full radius, released one after another around the rim and
         * swept aside about the aperture's centre.
         *
         * <p>One ring, so a blade is a single wedge from the middle to the rim rather than a stack of
         * cells - a shutter has blades, not tiles.
         */
        public static final Pattern GEARS = new Pattern(10, 1, false, 1.0F,
                Sequencing.RATCHET, Motion.SWING, 0.45F, 0.0F, 0.0F, 0.0F, 1.15F, 0.0F, true, 0.5F, 0.0F);

        /** A rune circle: precise bands that ignite where they stand, from the rim inwards. */
        public static final Pattern RUNES = new Pattern(12, 3, false, 1.0F,
                Sequencing.INWARD, Motion.DISSOLVE, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.35F, 0.0F);

        /** Burning paper: an irregular pane that catches in the middle and chars outwards. */
        public static final Pattern EMBERS = new Pattern(11, 3, true, 1.2F,
                Sequencing.RADIAL, Motion.CHAR, 0.05F, 0.10F, 0.08F, 0.0F, 0.0F, 0.25F, true, 0.6F, 0.0F);

        /** A field of stars: irregular, going out in no order, drifting apart on a slow spiral. */
        public static final Pattern MOTES = new Pattern(9, 4, true, 1.0F,
                Sequencing.SCATTER, Motion.DRIFT, 0.25F, 0.35F, 0.30F, 0.0F, 0.35F, 0.15F, true, 0.85F, 0.0F);

        /**
         * Hyperspace: many fine cuts, cut precisely, every one of them letting go on the same tick
         * and stretching rather than detaching.
         *
         * <p>Twenty-eight cracks and only two rings, the opposite shape to everything above it. A
         * streak needs to be long and thin: plenty of cells round the rim so the smear reads as a
         * bundle of lines, and hardly any across the radius so each line runs most of the way out
         * rather than being chopped into a stack of dashes.
         */
        public static final Pattern STREAKS = new Pattern(28, 2, false, 1.0F,
                Sequencing.SIMULTANEOUS, Motion.STREAK, 1.6F, 1.2F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.45F, 0.0F);

        /**
         * A fold: precise cells, many rings, wound round the middle and left there.
         *
         * <p>Five rings where most patterns have three or four, because this is the one pattern whose
         * whole effect is a <em>difference</em> across the radius. Too few rings and the shear has
         * nothing to interpolate between, so the pane turns as one plate and the bend disappears.
         */
        public static final Pattern FOLD = new Pattern(14, 5, false, 0.85F,
                Sequencing.RADIAL, Motion.SHEAR, 0.0F, 0.0F, 0.0F, 0.0F, 2.6F, 0.0F, true, 0.7F, 0.0F);

        /** A clean lens: even cells that stretch as one body along a single axis and let go. */
        public static final Pattern LENS = new Pattern(12, 3, false, 1.0F,
                Sequencing.RADIAL, Motion.ELONGATE, 0.15F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.5F, 0.0F);

        /** A weave: an even grid, snapping into crossing bands all at once. */
        public static final Pattern TARTAN = new Pattern(16, 4, false, 1.0F,
                Sequencing.SIMULTANEOUS, Motion.PLAID, 0.9F, 0.6F, 0.2F, 0.0F, 0.0F, 0.0F, true, 0.6F, 0.0F);

        /**
         * A gravity well: jagged, uneven cells, bunched towards the rim, torn in towards the middle
         * from the edge first.
         *
         * <p>Its sequencing and life are exactly those of the liquid pane it replaced - only its shape
         * and where its pieces go changed - so the opening takes as long as it always did.
         */
        public static final Pattern GRAVITY = new Pattern(13, 4, true, 1.3F,
                Sequencing.INWARD, Motion.IMPLODE, 0.55F, 0.45F, 0.25F, 0.0F, 0.30F, 0.40F, false, 0.9F, 0.0F);

        /** A rift arriving badly: eight even teeth, stepping round, none of them quite committing. */
        public static final Pattern STAGGER = new Pattern(8, 3, false, 1.0F,
                Sequencing.RATCHET, Motion.STUTTER, 0.35F, 0.40F, 0.15F, 0.0F, 0.20F, 0.10F, true, 0.75F, 0.0F);

        /** No pattern worth the name: wide spreads on every knob, and a motion that rerolls per piece. */
        public static final Pattern CHAOS = new Pattern(9, 3, true, 1.0F,
                Sequencing.SCATTER, Motion.IMPROBABLE, 0.30F, 0.50F, 0.60F, 0.0F, 0.50F, 0.90F, false, 0.8F, 0.0F);

        /**
         * Torn space, teleporting: uneven cells that each vanish on their own tick and turn up once,
         * further out and some way round.
         *
         * <p>The spin here is not a turn a piece makes but how far round the rim it lands - a wide
         * spread, so neighbours come apart rather than jumping out together as a ring.
         */
        public static final Pattern BLINKS = new Pattern(12, 4, true, 1.1F,
                Sequencing.SCATTER, Motion.BLINK, 0.55F, 0.45F, 0.45F, 0.0F, 0.0F, 2.4F, false, 0.7F, 0.0F);

        /**
         * A page: a pane that is never broken, so every knob that says how a piece moves is zero.
         *
         * <p>It is still cut - one cell per quarter - because every pattern is held to the same tiling
         * and timing as the rest, and a pattern with no cells would be a special case in all of them for
         * the sake of drawing nothing.
         */
        public static final Pattern PAGE = new Pattern(4, 1, false, 1.0F,
                Sequencing.SIMULTANEOUS, Motion.UNBROKEN, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.2F, 0.0F);

        /**
         * A veil of cloud, which is not glass either: unbroken, like the page, and cut only because every
         * pattern is held to the same tiling as the rest.
         *
         * <p>Six cells where the page has four, and nothing else to tell them apart - which is enough, and
         * needed: a theme's pattern is how the renderer tells one from another, and two equal ones would be
         * one pattern as far as anything could tell.
         */
        public static final Pattern VEIL = new Pattern(6, 1, false, 1.0F,
                Sequencing.SIMULTANEOUS, Motion.UNBROKEN, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true, 0.2F, 0.0F);

        /**
         * Every pattern this mod has, so a test can enumerate them and a new one cannot be added
         * without the invariants below being held to it.
         */
        public static final java.util.List<Pattern> ALL = java.util.List.of(GLASS, GEARS, RUNES, EMBERS, MOTES,
                STREAKS, FOLD, LENS, TARTAN, GRAVITY, STAGGER, CHAOS, BLINKS, PAGE, VEIL);
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
            case STARBLOCKS -> Pattern.STREAKS;
            case BEDROCK -> Pattern.FOLD;
            case BOLDLY_GONE -> Pattern.LENS;
            case LUDICROUS -> Pattern.TARTAN;
            case EVENTFUL_HORIZON -> Pattern.GRAVITY;
            case VWORP -> Pattern.STAGGER;
            case IMPROBABILITY -> Pattern.CHAOS;
            case ENDER -> Pattern.BLINKS;
            case SKETCH -> Pattern.PAGE;
            case MOSTLY_DANGEROUS -> Pattern.VEIL;
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
     * <p>The four leading fields are the <em>nominal</em> cell: the polar wedge between two crack
     * headings and two ring radii, which is what every planar motion sweeps about and what the tiling
     * is proven on. {@link #outline} is the shape actually cut, which only differs from it on a pattern
     * with some {@link Pattern#wander()}.
     *
     * @param delay   ticks after the break begins before this piece detaches
     * @param outward how hard it is thrown away from the impact, as a fraction of the rim
     * @param push    how hard it is thrown along the aperture's normal, signed: mostly the way a hull
     *                travels through the aperture, like a pane punched from one side, with a few pieces
     *                kicked back the other way
     * @param spin    radians per tick of travel it tumbles through
     * @param axis    which way its tumble axis lies in the aperture's plane
     * @param outline the corners it was really cut at
     */
    public record Shard(float angle0, float angle1, float innerT, float outerT,
                        float delay, float outward, float push, float spin, float axis, Outline outline) {

        public float midAngle() {
            return (angle0 + angle1) * 0.5F;
        }

        public float midRadius() {
            return (innerT + outerT) * 0.5F;
        }
    }

    /**
     * The shape a piece was really cut to: four corners, each an angle and a radius as a fraction of
     * the rim.
     *
     * <p>Wound the way a quad is drawn - along the inner edge from the piece's first crack to its
     * second, then back along the outer edge - so corner 0 is inner-first, 1 inner-second, 2
     * outer-second and 3 outer-first. On a pattern with no {@link Pattern#wander()} it is exactly the
     * polar cell the shard's own angles and radii describe. On glass it is that cell with every corner
     * moved to where the {@link Web}'s cracks actually cross, which is what makes the pieces the same
     * shape as the cracks drawn over them a moment earlier.
     *
     * <p>A piece in the innermost ring has its two inner corners on the impact itself, so it is a
     * triangle drawn as a quad with one edge of no length - which is also what a sliver from the
     * middle of a struck pane is.
     */
    public record Outline(float angle0, float radius0, float angle1, float radius1,
                          float angle2, float radius2, float angle3, float radius3) {

        public float angle(int corner) {
            return switch (corner) {
                case 0 -> angle0;
                case 1 -> angle1;
                case 2 -> angle2;
                default -> angle3;
            };
        }

        public float radius(int corner) {
            return switch (corner) {
                case 0 -> radius0;
                case 1 -> radius1;
                case 2 -> radius2;
                default -> radius3;
            };
        }

        /** How far out the piece sits, taken over its corners rather than its nominal cell. */
        public float meanRadius() {
            return (radius0 + radius1 + radius2 + radius3) * 0.25F;
        }

        public double u(int corner) {
            return Math.cos(angle(corner)) * radius(corner);
        }

        public double v(int corner) {
            return Math.sin(angle(corner)) * radius(corner);
        }

        /**
         * Which corner to start drawing from, 0 or 1, so the piece is split along a diagonal that lies
         * inside it.
         *
         * <p>A quad is drawn as two triangles either side of the diagonal from its first corner. For a
         * convex piece either diagonal will do. But where a crack bends hard, one corner of a piece can
         * be pushed in past the others, and then only the diagonal from that corner is inside - split
         * along the other and one triangle comes out inside out, folded over the outside of the piece.
         */
        public int firstCorner() {
            double whole = triangle(0, 1, 2) + triangle(0, 2, 3);
            return triangle(0, 1, 2) * whole >= 0.0D && triangle(0, 2, 3) * whole >= 0.0D ? 0 : 1;
        }

        /** Twice the signed area of the triangle across three corners, wrapping past the last. */
        public double triangle(int a, int b, int c) {
            a %= 4;
            b %= 4;
            c %= 4;
            return (u(b) - u(a)) * (v(c) - v(a)) - (u(c) - u(a)) * (v(b) - v(a));
        }
    }

    // ------------------------------------------------------------------- web

    /** How far a crack strays sideways where it crosses a ring, as a fraction of the run it just made. */
    private static final float CRACK_STRAY = 0.30F;

    /**
     * Most a crack may turn off its heading at a crossing, as a fraction of the gap to its nearer
     * neighbour.
     *
     * <p>Under a half, which is the whole of why two cracks can never cross: each of a neighbouring
     * pair may use up less than half of the gap between them, so both together cannot close it.
     */
    private static final float CRACK_STRAY_LIMIT = 0.45F;

    /**
     * How far a ring strays in or out at each crack, as a fraction of the nearer gap to the ring beside
     * it. Well under a half, for the reason above, and also because the break runs outward by how far
     * out a piece really sits - rings that overlapped would let go out of order.
     */
    private static final float RING_STRAY = 0.30F;

    /**
     * Sideways kink at each bend inside one run of a crack, as a fraction of the run's length.
     *
     * <p>Small. A glass fracture runs straight and turns sharply where it meets another; a crack that
     * wiggles all along its length is lightning, which a rift already has plenty of.
     */
    private static final float KINK = 0.035F;

    /** Sideways wander at the middle of a ring crack, as a fraction of its length. */
    private static final float CHORD_STRAY = 0.08F;

    /** Chance any one run of a radial throws off a short branch that goes nowhere. */
    private static final float BRANCH_CHANCE = 0.45F;

    /** Most of the room beside a crack a branch may cross before it would have become a crack itself. */
    private static final float BRANCH_ROOM = 0.4F;

    /** How close to the rim a branch may reach. The pane ends at the rim, and so does its glass. */
    private static final float BRANCH_LIMIT = 0.97F;

    /** Spikes in the white crush where the blow landed. */
    public static final int CRUSH_SPIKES = 9;

    /** Most a cell of the pane tilts off true as the sheet sags, in radians. */
    private static final float FACET_TILT = 0.32F;

    /*
     * The clock the cracks run to, all as fractions of the crack phase. Radials first, each at its
     * own pace; ring cracks after, from the impact outwards; branches as their parent passes them.
     * Every one of them is finished comfortably before the pane goes - see RiftShatterTest.
     */

    /** Latest a radial may set off after the blow. */
    private static final float RADIAL_LEAD = 0.06F;
    /** Earliest a radial may reach the rim. */
    private static final float RADIAL_FIRST = 0.32F;
    /** How much later than that the slowest may. */
    private static final float RADIAL_SPREAD = 0.20F;
    /** When the innermost ring cracks are due. */
    private static final float CHORD_FIRST = 0.30F;
    /** How much later the outermost ring cracks are due than the innermost. */
    private static final float CHORD_SPREAD = 0.40F;
    /** How late any one ring crack may be on its ring. */
    private static final float CHORD_JITTER = 0.07F;
    /** Shortest time a ring crack takes to cross from one radial to the next. */
    private static final float CHORD_RUN = 0.08F;
    /** How much longer the slowest takes. */
    private static final float CHORD_RUN_SPREAD = 0.06F;
    /** How long after both its radials have passed a ring crack may start. */
    private static final float CHORD_LAG = 0.02F;
    /** How long after its parent has passed a branch may start. */
    private static final float BRANCH_LAG = 0.02F;
    /** How long a branch takes to grow. */
    private static final float BRANCH_RUN = 0.10F;

    /** Where the web's draws from {@link #noise} start, well clear of the shards' own. */
    private static final int WEB_NOISE = 20_000;

    /** The web a pattern cuts its pane along, for the rift at a seed. */
    public static Web web(int seed, Pattern pattern) {
        return new Web(seed, pattern);
    }

    /**
     * The crack pattern a pane shows before it breaks, and the corners its pieces are cut at.
     *
     * <p>Struck glass is not cut along a polar grid. Its radial cracks run roughly straight but kink
     * wherever a ring crack meets them; its rings are not circles but a loop of short straight cracks,
     * each bridging one radial to the next and never quite the same distance out twice; and here and
     * there a crack throws off a short branch that runs a little way and stops. This is all of that,
     * worked out once per aperture from its seed.
     *
     * <p>The nodes - where a radial crosses a ring - are the load-bearing part. They are the corners
     * every piece is cut at (see {@link Shard#outline()}), so the pane that breaks is exactly the pane
     * the eye has just watched cracking, rather than a tidy wheel of wedges swapped in for it at the
     * last moment. Everything else - the kinks inside a run, the wander of a ring crack, the branches -
     * is drawn and not cut, and is kept small enough that the difference cannot be seen in the instant
     * before the pieces move.
     *
     * <p>Also the clock the cracks run to, as fractions of {@link #CRACK_PHASE}: see the constants
     * above it. A ring crack never starts until both radials it bridges have got that far, because a
     * crack cannot run across glass from a crack that is not there yet.
     *
     * <p>A pattern with no {@link Pattern#wander()} gets its plain grid back from this: nodes on the
     * nominal cracks and rings, straight runs, and no branches.
     */
    public static final class Web {

        /** Straight runs a radial is drawn in between one ring and the next. */
        public static final int STEPS = 3;

        private final int seed;
        private final Pattern pattern;
        private final int cracks;
        private final int rings;
        private final float[] headings;
        private final float[] radii;
        /** Where each crack crosses each ring, indexed by {@link #node}. */
        private final float[] nodeAngle;
        private final float[] nodeRadius;
        /** Each radial as a path from the impact to the rim, as (u, v) pairs in rim fractions. */
        private final float[][] paths;
        /** How far along its path each point is, as a fraction of the path's whole length. */
        private final float[][] along;
        private final float[] radialStart;
        private final float[] radialEnd;
        private final float[] weight;
        /** Per ring crack, indexed crack * rings + ring; ring 0 is the impact and has none. */
        private final float[] chordStart;
        private final float[] chordEnd;
        private final boolean[] chordForward;
        private final float[] chordStray;
        private final List<Branch> branches;
        private final float[] crushAngle;
        private final float[] crushLength;
        /** Per cell, indexed ring * cracks + crack, like the shards. */
        private final float[] tiltU;
        private final float[] tiltV;

        private Web(int seed, Pattern pattern) {
            this.seed = seed;
            this.pattern = pattern;
            this.cracks = pattern.cracks();
            this.rings = pattern.rings();
            this.headings = crackAngles(seed, pattern);
            this.radii = ringRadii(seed, pattern);
            float wander = pattern.wander();

            nodeAngle = new float[cracks * (rings + 1)];
            nodeRadius = new float[cracks * (rings + 1)];
            for (int crack = 0; crack < cracks; crack++) {
                float room = Math.min(headings[crack] - heading(crack - 1), heading(crack + 1) - headings[crack]);
                float limit = CRACK_STRAY_LIMIT * room;
                for (int ring = 0; ring <= rings; ring++) {
                    int n = WEB_NOISE + node(crack, ring) * 2;
                    float angle = headings[crack];
                    float radius = radii[ring];
                    // Every crack starts at the impact and every one ends on the rim, however far it
                    // wandered in between - so only the rings inside the pane move in and out.
                    if (wander > 0.0F && ring > 0) {
                        float run = radii[ring] - radii[ring - 1];
                        // Sideways by a share of the run just made, turned into an angle at this
                        // radius - so a crack strays as much near the impact as near the rim, rather
                        // than barely at all in the middle and wildly at the edge.
                        float stray = (noise(seed, n) - 0.5F) * 2.0F * CRACK_STRAY * wander * run / radius;
                        angle += Mth.clamp(stray, -limit, limit);
                        if (ring < rings) {
                            float gap = Math.min(run, radii[ring + 1] - radii[ring]);
                            radius += (noise(seed, n + 1) - 0.5F) * 2.0F * RING_STRAY * wander * gap;
                        }
                    }
                    nodeAngle[node(crack, ring)] = angle;
                    nodeRadius[node(crack, ring)] = radius;
                }
            }

            paths = new float[cracks][];
            along = new float[cracks][];
            for (int crack = 0; crack < cracks; crack++) {
                float[] path = new float[pathPoints() * 2];
                for (int ring = 0; ring < rings; ring++) {
                    float x0 = nodeU(crack, ring);
                    float y0 = nodeV(crack, ring);
                    float dx = nodeU(crack, ring + 1) - x0;
                    float dy = nodeV(crack, ring + 1) - y0;
                    for (int step = 0; step < STEPS; step++) {
                        float t = step / (float) STEPS;
                        // Across the run and in proportion to it, so a bend is the same share of a
                        // short run near the impact as of a long one near the rim - and barely any in
                        // the innermost run, where the neighbouring cracks are close enough to touch.
                        float kink = step == 0 || wander <= 0.0F ? 0.0F
                                : (noise(seed, WEB_NOISE + 5_000 + (crack * rings + ring) * STEPS + step) - 0.5F)
                                        * 2.0F * KINK * wander * (ring == 0 ? 0.3F : 1.0F);
                        int at = (ring * STEPS + step) * 2;
                        path[at] = x0 + dx * t - dy * kink;
                        path[at + 1] = y0 + dy * t + dx * kink;
                    }
                }
                int last = rings * STEPS * 2;
                path[last] = nodeU(crack, rings);
                path[last + 1] = nodeV(crack, rings);
                paths[crack] = path;

                float[] run = new float[pathPoints()];
                float total = 0.0F;
                for (int point = 1; point < run.length; point++) {
                    total += (float) Math.hypot(path[point * 2] - path[point * 2 - 2],
                            path[point * 2 + 1] - path[point * 2 - 1]);
                    run[point] = total;
                }
                for (int point = 1; point < run.length; point++) {
                    run[point] /= total;
                }
                run[run.length - 1] = 1.0F; // exactly, whatever the rounding made of it
                along[crack] = run;
            }

            radialStart = new float[cracks];
            radialEnd = new float[cracks];
            weight = new float[cracks];
            for (int crack = 0; crack < cracks; crack++) {
                int n = WEB_NOISE + 10_000 + crack * 3;
                radialStart[crack] = RADIAL_LEAD * noise(seed, n);
                radialEnd[crack] = RADIAL_FIRST + RADIAL_SPREAD * noise(seed, n + 1);
                weight[crack] = 0.65F + 0.35F * noise(seed, n + 2);
            }

            chordStart = new float[cracks * rings];
            chordEnd = new float[cracks * rings];
            chordForward = new boolean[cracks * rings];
            chordStray = new float[cracks * rings];
            float ringStep = CHORD_SPREAD / Math.max(1, rings - 2);
            for (int crack = 0; crack < cracks; crack++) {
                for (int ring = 1; ring < rings; ring++) {
                    int chord = crack * rings + ring;
                    int n = WEB_NOISE + 12_000 + chord * 4;
                    float ready = Math.max(radialTimeAt(crack, nodeAlong(crack, ring)),
                            radialTimeAt(crack + 1, nodeAlong(crack + 1, ring))) + CHORD_LAG;
                    float due = CHORD_FIRST + ringStep * (ring - 1) + CHORD_JITTER * noise(seed, n);
                    chordStart[chord] = Math.max(ready, due);
                    chordEnd[chord] = chordStart[chord] + CHORD_RUN + CHORD_RUN_SPREAD * noise(seed, n + 1);
                    chordForward[chord] = noise(seed, n + 2) < 0.5F;
                    chordStray[chord] = (noise(seed, n + 3) - 0.5F) * 2.0F * CHORD_STRAY * wander;
                }
            }

            List<Branch> found = new ArrayList<>();
            if (wander > 0.0F) {
                for (int crack = 0; crack < cracks; crack++) {
                    for (int ring = 1; ring < rings; ring++) {
                        Branch branch = branch(crack, ring);
                        if (branch != null) {
                            found.add(branch);
                        }
                    }
                }
            }
            branches = List.copyOf(found);

            crushAngle = new float[CRUSH_SPIKES];
            crushLength = new float[CRUSH_SPIKES];
            for (int spike = 0; spike < CRUSH_SPIKES; spike++) {
                int n = WEB_NOISE + 18_000 + spike * 2;
                crushAngle[spike] = (spike + 0.8F * noise(seed, n)) * TWO_PI / CRUSH_SPIKES;
                crushLength[spike] = 0.022F + 0.045F * noise(seed, n + 1);
            }

            tiltU = new float[cracks * rings];
            tiltV = new float[cracks * rings];
            for (int cell = 0; cell < tiltU.length; cell++) {
                int n = WEB_NOISE + 19_000 + cell * 2;
                tiltU[cell] = (noise(seed, n) - 0.5F) * 2.0F * FACET_TILT;
                tiltV[cell] = (noise(seed, n + 1) - 0.5F) * 2.0F * FACET_TILT;
            }
        }

        /**
         * A short crack forking off one run of a radial, or nothing if this run throws none.
         *
         * <p>It starts on a bend of its parent's own path, so it visibly leaves the crack rather than
         * floating beside it, and it is kept well inside the cell it runs into: one that reached the
         * next crack would be a crack in its own right, and the pane would have to break along it.
         */
        private Branch branch(int crack, int ring) {
            int n = WEB_NOISE + 16_000 + (crack * rings + ring) * 5;
            if (noise(seed, n) >= BRANCH_CHANCE) {
                return null;
            }
            int base = ring * STEPS + (noise(seed, n + 1) < 0.5F ? 1 : 2);
            float bx = pathU(crack, base);
            float by = pathV(crack, base);
            float dx = nodeU(crack, ring + 1) - nodeU(crack, ring);
            float dy = nodeV(crack, ring + 1) - nodeV(crack, ring);
            float run = (float) Math.hypot(dx, dy);
            float out = (float) Math.hypot(bx, by);
            if (run < 1.0e-5F || out > BRANCH_LIMIT) {
                return null;
            }
            // Positive turns anticlockwise, which is towards the next crack round.
            boolean ahead = noise(seed, n + 2) < 0.5F;
            float turn = (0.28F + 0.35F * noise(seed, n + 3)) * (ahead ? 1.0F : -1.0F);
            int beside = ahead ? crack + 1 : crack - 1;
            float room = Math.min(Math.abs(angle(beside, ring) - angle(crack, ring)),
                    Math.abs(angle(beside, ring + 1) - angle(crack, ring + 1)));
            float length = Math.min((0.22F + 0.26F * noise(seed, n + 4)) * run,
                    BRANCH_ROOM * room * out / (float) Math.sin(Math.abs(turn)));
            if (length < 0.01F) {
                return null;
            }
            float cos = (float) Math.cos(turn);
            float sin = (float) Math.sin(turn);
            float tx = bx + (dx * cos - dy * sin) / run * length;
            float ty = by + (dx * sin + dy * cos) / run * length;
            if (Math.hypot(tx, ty) > BRANCH_LIMIT) {
                return null;
            }
            float from = pathAlong(crack, base);
            float start = radialTimeAt(crack, from) + BRANCH_LAG;
            return new Branch(crack, from, bx, by, tx, ty, start, start + BRANCH_RUN);
        }

        private int node(int crack, int ring) {
            return crack * (rings + 1) + ring;
        }

        private int wrap(int crack) {
            return Math.floorMod(crack, cracks);
        }

        public int seed() {
            return seed;
        }

        public Pattern pattern() {
            return pattern;
        }

        public int cracks() {
            return cracks;
        }

        public int rings() {
            return rings;
        }

        /**
         * A crack's mean heading. Counts on round the circle past either end, so crack {@code -1} is
         * the last one a turn back and crack {@code cracks} is the first a turn on - neighbours are
         * always a positive gap apart.
         */
        public float heading(int crack) {
            return headings[wrap(crack)] + TWO_PI * Math.floorDiv(crack, cracks);
        }

        /** A nominal ring's radius, as a fraction of the rim: 0 at the impact, 1 at the rim. */
        public float ring(int ring) {
            return radii[ring];
        }

        /** Where a crack crosses a ring, as an angle - counting on round the circle like {@link #heading}. */
        public float angle(int crack, int ring) {
            return nodeAngle[node(wrap(crack), ring)] + TWO_PI * Math.floorDiv(crack, cracks);
        }

        /** How far out a crack crosses a ring, as a fraction of the rim. */
        public float radius(int crack, int ring) {
            return nodeRadius[node(wrap(crack), ring)];
        }

        public float nodeU(int crack, int ring) {
            return (float) Math.cos(angle(crack, ring)) * radius(crack, ring);
        }

        public float nodeV(int crack, int ring) {
            return (float) Math.sin(angle(crack, ring)) * radius(crack, ring);
        }

        /** Points on every radial's path, the impact and the rim included. */
        public int pathPoints() {
            return rings * STEPS + 1;
        }

        public float pathU(int crack, int point) {
            return paths[wrap(crack)][point * 2];
        }

        public float pathV(int crack, int point) {
            return paths[wrap(crack)][point * 2 + 1];
        }

        /** How far along its radial a path point is, 0 at the impact and 1 on the rim. */
        public float pathAlong(int crack, int point) {
            return along[wrap(crack)][point];
        }

        /** How far along its radial a crack's crossing of a ring is. */
        public float nodeAlong(int crack, int ring) {
            return pathAlong(crack, ring * STEPS);
        }

        /**
         * How much of a radial has been drawn, as a fraction of its length, given progress through
         * the crack phase.
         *
         * <p>Eased out, like {@link #crackReach}: a fracture bursts from the impact and slows as the
         * energy of the blow spreads round the growing web.
         */
        public float radialReach(int crack, float crackProgress) {
            int at = wrap(crack);
            return crackReach((crackProgress - radialStart[at]) / (radialEnd[at] - radialStart[at]));
        }

        /** When a radial's tip passes a given fraction of its length - the inverse of {@link #radialReach}. */
        public float radialTimeAt(int crack, float fraction) {
            int at = wrap(crack);
            float eased = 1.0F - (float) Math.cbrt(1.0F - Mth.clamp(fraction, 0.0F, 1.0F));
            return radialStart[at] + (radialEnd[at] - radialStart[at]) * eased;
        }

        /** How heavy a radial is, 0.65 to 1: not every crack in a pane is as wide or as bright. */
        public float weight(int crack) {
            return weight[wrap(crack)];
        }

        /**
         * How much of the ring crack bridging a radial to the next one round has been drawn, 0..1.
         *
         * @param ring which ring, from 1 to {@code rings - 1}: ring 0 is the impact and the last is
         *             the rim, and neither is a crack
         */
        public float chordReach(int crack, int ring, float crackProgress) {
            int chord = wrap(crack) * rings + ring;
            return crackReach((crackProgress - chordStart[chord]) / (chordEnd[chord] - chordStart[chord]));
        }

        /** When a ring crack starts across, as a fraction of the crack phase. */
        public float chordStart(int crack, int ring) {
            return chordStart[wrap(crack) * rings + ring];
        }

        /** Whether a ring crack runs from this radial to the next one round, or the other way. */
        public boolean chordForward(int crack, int ring) {
            return chordForward[wrap(crack) * rings + ring];
        }

        /** The bend in the middle of a ring crack, sideways off the straight line between its ends. */
        public float chordMidU(int crack, int ring) {
            float dy = nodeV(crack + 1, ring) - nodeV(crack, ring);
            return (nodeU(crack, ring) + nodeU(crack + 1, ring)) * 0.5F
                    - dy * chordStray[wrap(crack) * rings + ring];
        }

        public float chordMidV(int crack, int ring) {
            float dx = nodeU(crack + 1, ring) - nodeU(crack, ring);
            return (nodeV(crack, ring) + nodeV(crack + 1, ring)) * 0.5F
                    + dx * chordStray[wrap(crack) * rings + ring];
        }

        /**
         * How far a cell of the pane has been cut free of the glass round it, 0..1: both its radials
         * past its outer ring, and the ring crack along its outer edge drawn. A cell on the rim has
         * the rim for that edge.
         */
        public float cellFormed(int crack, int ring, float crackProgress) {
            float sides = Math.min(
                    Mth.clamp(radialReach(crack, crackProgress) / nodeAlong(crack, ring + 1), 0.0F, 1.0F),
                    Mth.clamp(radialReach(crack + 1, crackProgress) / nodeAlong(crack + 1, ring + 1), 0.0F, 1.0F));
            float edge = ring + 1 < rings ? chordReach(crack, ring + 1, crackProgress) : 1.0F;
            return Math.min(sides, edge);
        }

        public List<Branch> branches() {
            return branches;
        }

        public float crushAngle(int spike) {
            return crushAngle[spike];
        }

        /** How far a spike of the crush reaches, as a fraction of the rim. */
        public float crushLength(int spike) {
            return crushLength[spike];
        }

        /** How far a cell tilts about the aperture's two axes once the sheet has fully sagged. */
        public float tiltU(int crack, int ring) {
            return tiltU[ring * cracks + wrap(crack)];
        }

        public float tiltV(int crack, int ring) {
            return tiltV[ring * cracks + wrap(crack)];
        }
    }

    /**
     * A short crack that forks off a radial and stops - drawn, never cut along.
     *
     * @param crack which radial it forks from
     * @param along how far along that radial it leaves it, as a fraction of the radial's length
     * @param u0    where it leaves, in rim fractions - a point on the radial's own path
     * @param u1    where it stops
     * @param start when it starts to grow, as a fraction of the crack phase - never before its parent
     *              has got there
     * @param end   when it has finished growing
     */
    public record Branch(int crack, float along, float u0, float v0, float u1, float v1, float start, float end) {

        /** How much of it has been drawn, 0..1. */
        public float reach(float crackProgress) {
            return crackReach((crackProgress - start) / (end - start));
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
     * out from the pattern's own knobs, so each pattern only had to describe how it differs from the
     * others rather than bring a fracture of its own.
     */
    public static Shard[] fracture(int seed, Pattern pattern) {
        return fracture(web(seed, pattern));
    }

    /**
     * The fracture cut along a web already worked out - for a caller that draws the web as well, and
     * so has one in hand.
     */
    public static Shard[] fracture(Web web) {
        Pattern pattern = web.pattern();
        int seed = web.seed();
        int cracks = web.cracks();
        int rings = web.rings();
        Shard[] shards = new Shard[cracks * rings];

        int index = 0;
        for (int ring = 0; ring < rings; ring++) {
            for (int crack = 0; crack < cracks; crack++) {
                // The last cell closes the loop onto the first crack, one turn on - which heading()
                // and angle() both count for.
                float angle0 = web.heading(crack);
                float angle1 = web.heading(crack + 1);
                float inner = web.ring(ring);
                float outer = web.ring(ring + 1);
                float mid = (inner + outer) * 0.5F;
                Outline outline = new Outline(
                        web.angle(crack, ring), web.radius(crack, ring),
                        web.angle(crack + 1, ring), web.radius(crack + 1, ring),
                        web.angle(crack + 1, ring + 1), web.radius(crack + 1, ring + 1),
                        web.angle(crack, ring + 1), web.radius(crack, ring + 1));
                // Ordered by where the piece really sits rather than by its nominal ring, so a pane
                // whose rings came out lumpy lets go lumpily too instead of in perfect circles.
                float sits = outline.meanRadius();

                int n = 1_000 + index * 7;
                float delay = switch (pattern.sequencing()) {
                    case RATCHET -> (crack / (float) cracks) * SEQUENTIAL_SPREAD;
                    case INWARD -> (1.0F - sits) * BREAK_SPREAD;
                    case SCATTER -> noise(seed, n + 4) * BREAK_SPREAD;
                    case RADIAL -> sits * BREAK_SPREAD;
                    case SIMULTANEOUS -> 0.0F;
                };
                float outward = pattern.outwardBase() + pattern.outwardMidScale() * mid
                        + pattern.outwardVariance() * noise(seed, n);
                // Mostly one way: a pane punched through goes with the blow, and only a few pieces
                // kick back. That way is the aperture's normal, which is the hull's heading at both
                // ends of a warp - so the glass bursts away from a ship flying in, and ahead of one
                // coming out.
                float push = pattern.pushVariance() <= 0.0F ? 0.0F
                        : (noise(seed, n + 1) * 1.4F - 0.4F) * pattern.pushVariance();
                float spin = pattern.spinBase() + (pattern.spinVariance() <= 0.0F ? 0.0F
                        : (noise(seed, n + 2) - 0.5F) * pattern.spinVariance());
                // A fixed axis is what makes a field of pieces tumble in step, like teeth on one gear
                // rather than a handful of glass thrown in the air.
                float axis = pattern.fixedAxis() ? 0.0F : noise(seed, n + 3) * (float) (Math.PI * 2.0D);

                shards[index++] = new Shard(angle0, angle1, inner, outer, delay, outward, push, spin, axis,
                        outline);
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
