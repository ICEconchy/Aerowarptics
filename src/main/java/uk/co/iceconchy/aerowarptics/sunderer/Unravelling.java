package uk.co.iceconchy.aerowarptics.sunderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.lerp;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.smooth;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.span;

/**
 * Where each block of the frame that bursts is, as a sundering unravels its ring.
 *
 * <p>The burst blocks are taken out of the world as the Sunderer fires and are drawn from here on every client. So
 * this is what a player sees the ring do: each block shudders where it stood, cracks free in its turn and rises
 * out of the ring's plane tumbling, swings into a tilted ring round the seed, circles it faster and tighter, and is
 * drawn in - stretching towards the seed and shrinking into it - one after another, each as the seed beats.
 *
 * <p>Pure, and grown from the sundering's seed, so every client draws the same unravelling and a latecomer sees the
 * one everybody else is watching. Positions are offsets from the middle of the opening, in blocks, along the world's
 * axes; the ring's own are handed in. {@code UnravellingTest} samples every block a hundred times a tick for a jump,
 * since a block that jumps is a block that teleports on screen.
 */
public final class Unravelling {

    /** When the lifting begins and how long one block takes to rise, as shares of the straining. */
    static final double LIFT_FIRST = 0.02D;
    static final double LIFT_SPREAD = 0.16D;
    static final double LIFT_TAKES = 0.06D;

    /** How far the orbit stands clear of the largest the seed gets, in blocks. */
    static final double CLEARANCE = 0.45D;
    /** How far the orbit's plane leans out of the ring's, in radians, and how far round it precesses. */
    static final double TILT = Math.toRadians(38.0D);
    static final double PRECESSION = 0.6D;
    /** How big a block is drawn once it is in orbit. */
    static final double ORBIT_SCALE = 0.8D;

    /** Ticks a thrown block is drawn for before it breaks up. */
    public static final double THROWN_FOR = 30.0D;

    private Unravelling() {
    }

    /** The ring's own directions: across its opening, and out of it. Up is the world's. Unit vectors. */
    public record Axes(double acrossX, double acrossZ, double normalX, double normalZ) {
    }

    /**
     * One block at one moment.
     *
     * @param x       where it is, from the middle of the opening
     * @param axisX   the axis it tumbles about, a unit vector
     * @param angle   how far it has tumbled, in radians
     * @param scale   how big it is drawn
     * @param stretch how far it is drawn out towards the seed, 1 being not at all
     * @param present whether it is drawn at all
     */
    public record Piece(double x, double y, double z, double axisX, double axisY, double axisZ, double angle,
                        double scale, double stretch, boolean present) {

        public double distance() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    // ------------------------------------------------------------------ when

    public static double liftStart(int index, int count) {
        return LIFT_FIRST + LIFT_SPREAD * index / Math.max(1, count);
    }

    public static double liftEnd(int index, int count) {
        return liftStart(index, count) + LIFT_TAKES;
    }

    /** The share of the consuming each swallow takes. */
    static double beat(int count) {
        return (1.0D - SundererChoreography.ORBIT_END) / Math.max(1, count);
    }

    /** When the block swallowed {@code rank}-th starts to fall in, as a share of the straining. */
    public static double swallowStart(int rank, int count) {
        return SundererChoreography.ORBIT_END + rank * beat(count);
    }

    /** When it is gone: at the height of the seed's beat for it - see {@link SundererChoreography#pulse}. */
    public static double swallowed(int rank, int count) {
        return swallowStart(rank, count) + 0.5D * beat(count);
    }

    /** The order the blocks are swallowed in: {@code ranks()[index]} is that block's place in it. */
    public static int[] ranks(int count, long seed) {
        List<Integer> order = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            order.add(index);
        }
        Collections.shuffle(order, new Random(seed ^ 0x2545F4914F6CDD1DL));
        int[] ranks = new int[count];
        for (int place = 0; place < count; place++) {
            ranks[order.get(place)] = place;
        }
        return ranks;
    }

    // ------------------------------------------------------------------ where

    /**
     * Where one burst block is.
     *
     * @param index which block, in the order the server lifted them out
     * @param count how many burst
     * @param rank  its place in the swallowing - {@link #ranks}
     * @param homeX where it stood, from the middle of the opening
     * @param size  the opening's smaller half-size
     */
    public static Piece at(int index, int count, int rank, double homeX, double homeY, double homeZ, Axes axes,
                           double size, long seed, double tick, boolean hand) {
        Random random = new Random(seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL);
        double[] axis = unit(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        double spin = (0.05D + random.nextDouble() * 0.1D) * (random.nextBoolean() ? 1.0D : -1.0D);
        double side = index % 2 == 0 ? 1.0D : -1.0D;
        double offset = Math.PI * 2.0D * index / Math.max(1, count) + (random.nextDouble() - 0.5D) * 0.4D;
        double phase = random.nextDouble() * Math.PI * 2.0D;

        double s = SundererChoreography.straining(tick, hand);
        double from = liftStart(index, count);
        double to = liftEnd(index, count);
        double lift = smooth(span(s, from, to));

        // Shuddering where it stands: barely as the ring reaches, harder as its turn comes, gone once it is free.
        double shake = (0.015D * smooth(span(tick, 0.0D, SundererSequence.REACHING))
                + 0.05D * smooth(span(s, Math.max(0.0D, from - 0.04D), from))) * (1.0D - lift);
        double jx = shake * Math.sin(tick * 2.3D + phase);
        double jy = shake * Math.sin(tick * 3.1D + phase * 1.7D);
        double jz = shake * Math.sin(tick * 2.7D + phase * 0.6D);

        double[] orbit = orbitPoint(offset, axes, size, s, tick, hand);
        double bob = 0.12D * Math.sin(tick * 0.15D + phase) * lift;
        // Rising out of the ring's plane on the way: alternate blocks to either side of it.
        double arc = Math.sin(Math.PI * lift);
        double x = lerp(lift, homeX + jx, orbit[0]) + axes.normalX() * side * 0.9D * arc;
        double y = lerp(lift, homeY + jy, orbit[1]) + 0.35D * arc + bob;
        double z = lerp(lift, homeZ + jz, orbit[2]) + axes.normalZ() * side * 0.9D * arc;

        // Tumbling from the moment it is free, at its own pace; barely while the hand holds everything still.
        double since = Math.max(0.0D, s - from) * SundererSequence.STRAINING;
        double angle = spin * since * smooth(span(s, from, from + 0.02D)) + 0.6D * spin * 10.0D
                * SundererChoreography.handBeat(tick, hand);
        double scale = lerp(lift, 1.0D, ORBIT_SCALE);

        // Falling in, in its turn: drawn towards the middle, out along the way, and down to nothing.
        double fall = smooth(span(s, swallowStart(rank, count), swallowed(rank, count)));
        x *= 1.0D - fall;
        y = lerp(fall, y, 0.0D);
        z *= 1.0D - fall;
        scale *= 1.0D - 0.9D * fall;
        double stretch = 1.0D + 2.5D * fall;
        boolean present = s < swallowed(rank, count);
        return new Piece(x, y, z, axis[0], axis[1], axis[2], angle, scale, stretch, present);
    }

    /**
     * A point on the orbit: a ring round the middle of the opening leaning out of the ring's plane and slowly
     * precessing, its radius closing from wide of the frame's inside towards the seed.
     */
    static double[] orbitPoint(double offset, Axes axes, double size, double s, double tick, boolean hand) {
        double tight = SundererChoreography.largestSeed(size) + CLEARANCE;
        double wide = Math.max(0.7D * size, tight);
        double radius = lerp(smooth(span(s, SundererChoreography.LIFT_END, SundererChoreography.ORBIT_END)), wide, tight);
        double angle = offset + SundererChoreography.orbitAngle(tick, hand);
        double precess = PRECESSION * SundererChoreography.orbiting(tick, hand) * Math.PI * 2.0D;
        // The ring's plane is across and up; lean it out of itself about its across line, then turn that about the
        // ring's normal as it goes.
        double cu = Math.cos(angle) * radius;
        double cv = Math.sin(angle) * radius * Math.cos(TILT);
        double cn = Math.sin(angle) * radius * Math.sin(TILT);
        double pu = cu * Math.cos(precess) - cv * Math.sin(precess);
        double pv = cu * Math.sin(precess) + cv * Math.cos(precess);
        return new double[]{
                axes.acrossX() * pu + axes.normalX() * cn,
                pv,
                axes.acrossZ() * pu + axes.normalZ() * cn};
    }

    /**
     * The orbit's plane at a moment, as two unit vectors {@code {ax, ay, az, bx, by, bz}}: a point on the orbit is
     * {@code a cos(angle) + b sin(angle)} times its radius. What the dust disc is laid in, so it turns with the frame.
     */
    public static double[] orbitPlane(Axes axes, double tick, boolean hand) {
        double precess = PRECESSION * SundererChoreography.orbiting(tick, hand) * Math.PI * 2.0D;
        double[] first = planeVector(axes, Math.cos(precess), Math.sin(precess), 0.0D);
        double[] second = planeVector(axes, -Math.sin(precess) * Math.cos(TILT), Math.cos(precess) * Math.cos(TILT),
                Math.sin(TILT));
        return new double[]{first[0], first[1], first[2], second[0], second[1], second[2]};
    }

    /** A vector given across, up and out of the ring, in the world's axes. */
    private static double[] planeVector(Axes axes, double across, double up, double out) {
        return new double[]{axes.acrossX() * across + axes.normalX() * out, up, axes.acrossZ() * across + axes.normalZ() * out};
    }

    /** The radius of the orbit at a share of the straining, for a test to hold the seed clear of it. */
    static double orbitRadius(double size, double s) {
        double tight = SundererChoreography.largestSeed(size) + CLEARANCE;
        double wide = Math.max(0.7D * size, tight);
        return lerp(smooth(span(s, SundererChoreography.LIFT_END, SundererChoreography.ORBIT_END)), wide, tight);
    }

    /**
     * A block a rupture flings: outward from wherever it was, up, then falling, tumbling hard, for
     * {@link #THROWN_FOR} ticks.
     */
    public static Piece thrown(Piece from, int index, long seed, double ticks) {
        Random random = new Random(seed * 31L + index);
        double[] out = from.distance() < 1.0e-6D ? new double[]{0.0D, 1.0D, 0.0D}
                : unit(from.x(), from.y(), from.z());
        double speed = 0.3D + random.nextDouble() * 0.2D;
        double t = Math.max(0.0D, ticks);
        return new Piece(from.x() + out[0] * speed * t,
                from.y() + (out[1] * speed + 0.3D) * t - 0.02D * t * t,
                from.z() + out[2] * speed * t,
                from.axisX(), from.axisY(), from.axisZ(), from.angle() + 0.3D * t, from.scale(), 1.0D,
                from.present() && t < THROWN_FOR);
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-9D) {
            return new double[]{0.0D, 1.0D, 0.0D};
        }
        return new double[]{x / length, y / length, z / length};
    }
}
