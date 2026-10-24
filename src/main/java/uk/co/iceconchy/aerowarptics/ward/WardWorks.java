package uk.co.iceconchy.aerowarptics.ward;

/**
 * How the Rift Ward's mechanism stands, as arithmetic the renderer poses its bones from.
 *
 * <p>The ward is a stone totem in a machine with no roof: a carved pillar of rift-stone cracked across into four
 * pieces, clamped and chained by the player's brass. The pieces hang on a thread of void, and what they are doing
 * says what the machine is doing:
 *
 * <ul>
 *   <li><b>switched off</b> - sat down on one another, still, dark;</li>
 *   <li><b>switched on</b> - lifted apart ({@link #lift}); and while the field <b>stands</b>, each turning against
 *       its neighbours ({@link #turn}) with the field's light in the gaps. A ward that is on with its field down
 *       hangs apart and does not turn, its light guttering - which is how a ward out of charge is told from one
 *       that is working;</li>
 *   <li><b>turning a rift aside</b> - the upper pieces dragged sideways towards the tear, against the clamp
 *       ({@link #shearX}, {@link #shearZ}), and ringing back when it breaks.</li>
 * </ul>
 *
 * <p>The foot is bolted to the deck and does none of it: the pieces numbered here are the three above it, from the
 * one over the foot (0) to the head (2). The model is the artist's; {@code WardRigTest} holds it to these numbers.
 *
 * <p>Posed, never animated, and free of Minecraft.
 */
public final class WardWorks {

    /** The pieces above the foot. */
    public static final int PIECES = 3;

    /** How far, in pixels of the model, each piece lifts off the one below when the machine is on. */
    public static final double GAP = 1.5D;

    /** How far each piece is dragged towards a tear at the worst, in pixels: the head furthest. */
    private static final double[] SHEAR = {0.6D, 1.2D, 2.0D};

    /** How fast each piece turns beside the others, and which way: no two the same, so they never line up for long. */
    private static final double[] RATES = {1.0D, -1.3D, 0.7D};

    /** Radians a tick the slowest-geared piece turns while the field stands: once round in a little under half a minute. */
    public static final double TURN_RATE = 0.012D;

    /**
     * How the two rings round the totem turn with the field, as shares of its turning: the outer ring on the deck
     * slowly one way, and the middle ring it carries faster the other, so the two are never still against each other.
     */
    public static final double OUTER_RING_RATE = 0.5D;
    public static final double MIDDLE_RING_RATE = -1.6D;

    private WardWorks() {
    }

    /** How far piece {@code piece} is dragged at the worst, in pixels. */
    public static double shear(int piece) {
        return SHEAR[piece];
    }

    /** How high a piece hangs above where it sits, in pixels, for a machine {@code spread} of the way open. */
    public static double lift(int piece, double spread) {
        return GAP * (piece + 1) * clamp01(spread);
    }

    /** A piece's turn about the totem's axis, in radians, for the angle the machine has turned through. */
    public static double turn(int piece, double angle) {
        return angle * RATES[piece];
    }

    /**
     * A piece's offset along the model's x, for a drag of {@code drag} (-1..1) towards the unit direction
     * ({@code ux}, {@code uz}) on the map.
     *
     * <p>Negated: GeckoLib mirrors a model's x as it bakes it, so a bone moved by +x goes west. Given the sign as
     * it stood the totem would lean away from the rift it was catching along one axis and towards it along the
     * other - right only for a tear due north or south. The model script's
     * {@code check_the_totem_goes_at_its_pull} holds the sign.
     */
    public static double shearX(int piece, double ux, double drag) {
        return -ux * SHEAR[piece] * clamp(drag);
    }

    /** The same along z, which is not mirrored. */
    public static double shearZ(int piece, double uz, double drag) {
        return uz * SHEAR[piece] * clamp(drag);
    }

    /**
     * How brightly the carvings and the void are lit, 0..1: nothing at all while the machine is switched off, low
     * and slow while the field stands and brighter the fuller the ward is, guttering while the field is down, and
     * flaring as the totem takes the strain of a rift.
     *
     * @param fraction how full the ward's charge is, 0..1
     * @param strain   how hard a tear is being held, 0..1
     * @param time     the game clock, in ticks
     */
    public static double glow(boolean enabled, boolean standing, double fraction, double strain, double time) {
        if (!enabled) {
            return 0.0D;
        }
        double base = standing
                ? 0.30D + 0.28D * clamp01(fraction) + 0.05D * Math.sin(time / 53.0D)
                : 0.07D + 0.05D * Math.sin(time / 5.0D) * Math.sin(time / 1.7D);
        return clamp01(base + 0.6D * clamp01(strain));
    }

    /**
     * The totem's own slow motion, eased tick by tick on the client: how far open it has come, how fast it is
     * turning, and the angle it has turned through - so a ward switched on is seen to open and gather way, and one
     * that runs dry is seen to wind down, rather than either being cut to.
     */
    public static final class Motion {

        private double spread;
        private double lastSpread;
        private double speed;
        private double angle;
        private double lastAngle;

        public void tick(boolean enabled, boolean standing) {
            lastSpread = spread;
            lastAngle = angle;
            spread += ((enabled ? 1.0D : 0.0D) - spread) * 0.06D;
            speed += ((enabled && standing ? 1.0D : 0.0D) - speed) * 0.04D;
            angle += TURN_RATE * speed;
        }

        /** How far open the totem is, 0..1. */
        public double spread(float partialTick) {
            return lastSpread + (spread - lastSpread) * partialTick;
        }

        /** The angle the machine has turned through, in radians. */
        public double angle(float partialTick) {
            return lastAngle + (angle - lastAngle) * partialTick;
        }
    }

    private static double clamp(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
