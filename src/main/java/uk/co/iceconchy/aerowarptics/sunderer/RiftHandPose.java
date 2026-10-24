package uk.co.iceconchy.aerowarptics.sunderer;

import java.util.ArrayList;
import java.util.List;

/**
 * The hand that sometimes comes through a straining Sunderer before it tears: where every joint of it is, as a
 * curve of how far through its beat it is.
 *
 * <p>Pure, and only drawn - the server needs one thing from it, the moment it lets go of the plane with the
 * block it took ({@link #TAKES}). Everything else is the client's. It is posed by code rather than animated, as
 * the cradle's mechanism is, so that it can reach for whichever block of whichever ring it is given and turn to
 * whoever happens to be standing there.
 *
 * <h2>Its frame</h2>
 * Positions are in blocks, from the point the membrane splits: {@code u} across the plane, {@code v} up it, and
 * {@code n} out of it, towards the side the hand comes out on. Whatever has {@code n} below zero is still on the
 * far side, and is not drawn.
 *
 * <h2>Its beat</h2>
 * <ol>
 *   <li>the membrane bulges, and splits;</li>
 *   <li>fingers first - too long, four joints each - then the palm, then the forearm to the elbow;</li>
 *   <li>the fingers spread and it feels about, slowly;</li>
 *   <li>if anyone is near, it turns its palm to them and is still;</li>
 *   <li>it reaches for a block of the frame and closes on it;</li>
 *   <li>it goes back the way it came, and takes the block with it.</li>
 * </ol>
 */
public final class RiftHandPose {

    public static final double BULGE_END = 0.12D;
    public static final double FINGERS_END = 0.30D;
    public static final double ARM_END = 0.45D;
    public static final double FEEL_END = 0.60D;
    public static final double NOTICE_END = 0.70D;
    public static final double GRIP_END = 0.85D;
    /** The moment it lets go of the plane and starts back: the block it closed on goes with it from here. */
    public static final double TAKES = GRIP_END;

    /** Lengths, in blocks. Long, and the fingers longest of all for what they are. */
    public static final double PALM = 0.7D;
    public static final double PALM_WIDTH = 0.55D;
    public static final double KNUCKLE = 0.33D;
    public static final int JOINTS = 4;
    public static final int FINGERS = 5;
    public static final double FINGER = KNUCKLE * JOINTS;

    /** How far out of the plane the wrist comes at most, and how far it stands off the block it grips. */
    private static final double WRIST_OUT = 1.5D;
    private static final double GRIP_STANDOFF = 1.2D;
    private static final double GRIP_LIFT = 0.8D;
    /** How far behind the plane the elbow sits once the arm is out. */
    private static final double ELBOW_BEHIND = 0.3D;

    /** How far round each joint bends at a full curl, and how wide the fingers go at a full splay. */
    private static final double CURL_PER_JOINT = Math.toRadians(24.0D);
    private static final double SPLAY_LEAST = Math.toRadians(5.0D);
    private static final double SPLAY_MOST = Math.toRadians(22.0D);
    private static final double SWEEP = Math.toRadians(25.0D);

    private RiftHandPose() {
    }

    /** A point in the hand's frame. */
    public record Point(double u, double v, double n) {

        Point plus(Point other) {
            return new Point(u + other.u, v + other.v, n + other.n);
        }

        Point minus(Point other) {
            return new Point(u - other.u, v - other.v, n - other.n);
        }

        Point times(double scale) {
            return new Point(u * scale, v * scale, n * scale);
        }

        double length() {
            return Math.sqrt(u * u + v * v + n * n);
        }

        Point unit() {
            double length = length();
            return length < 1.0e-9D ? new Point(0.0D, 0.0D, 1.0D) : times(1.0D / length);
        }

        double dot(Point other) {
            return u * other.u + v * other.v + n * other.n;
        }

        Point cross(Point other) {
            return new Point(v * other.n - n * other.v, n * other.u - u * other.n, u * other.v - v * other.u);
        }

        public double distance(Point other) {
            return minus(other).length();
        }
    }

    /**
     * The hand at one moment.
     *
     * @param split   how far the membrane has split, 0 to 1: the slit is drawn this open
     * @param bulge   how far the membrane bows out before it splits, in blocks
     * @param elbow   where the forearm leaves the plane
     * @param wrist   where it meets the palm
     * @param fingers five chains of {@link #JOINTS} + 1 points each, knuckle to tip; the first is the thumb
     * @param palmNormal which way the palm faces
     */
    public record Pose(double split, double bulge, Point elbow, Point wrist, List<List<Point>> fingers,
                       Point palmNormal) {

        public List<Point> tips() {
            List<Point> tips = new ArrayList<>(fingers.size());
            for (List<Point> finger : fingers) {
                tips.add(finger.get(finger.size() - 1));
            }
            return tips;
        }

        /** Every joint, elbow to fingertips. */
        public List<Point> joints() {
            List<Point> joints = new ArrayList<>();
            joints.add(elbow);
            joints.add(wrist);
            for (List<Point> finger : fingers) {
                joints.addAll(finger);
            }
            return joints;
        }

        /** Where a block it carries sits: in the curl of the fingers, in front of the palm. */
        public Point carried() {
            Point sum = new Point(0.0D, 0.0D, 0.0D);
            for (Point tip : tips()) {
                sum = sum.plus(tip);
            }
            return sum.times(1.0D / FINGERS);
        }
    }

    /**
     * The hand at {@code beat}, 0 to 1 through its share of the sundering.
     *
     * @param grip   the middle of the block of frame it reaches for, in the hand's frame
     * @param viewer whoever it turns to, in the hand's frame, or null when nobody is near
     */
    public static Pose at(double beat, Point grip, Point viewer) {
        double h = clamp01(beat);

        double split = smooth(span(h, BULGE_END * 0.6D, BULGE_END)) * (1.0D - smooth(span(h, 0.95D, 1.0D)));
        double bulge = 0.5D * smooth(span(h, 0.0D, BULGE_END)) * (1.0D - smooth(span(h, BULGE_END, FINGERS_END)));

        // How far out it has come, as a length of hand from the fingertips back: nothing, then the fingers, then
        // the palm and the forearm. It goes back the same way at the end.
        double outward = FINGER * smooth(span(h, BULGE_END, FINGERS_END))
                + (PALM + WRIST_OUT) * smooth(span(h, FINGERS_END, ARM_END));
        outward *= 1.0D - smooth(span(h, GRIP_END, 0.98D));

        double reach = smooth(span(h, NOTICE_END, GRIP_END)) * (1.0D - smooth(span(h, GRIP_END, 0.95D)));
        // Half closed as it comes, opening as it feels about, closing on the block, and closed going back.
        double curl = lerp(smooth(span(h, ARM_END, FEEL_END)), 0.35D, 0.1D)
                + 0.9D * smooth(span(h, NOTICE_END, GRIP_END));
        double splay = smooth(span(h, ARM_END, FEEL_END)) * (1.0D - smooth(span(h, NOTICE_END, GRIP_END)));
        double turn = viewer == null ? 0.0D
                : smooth(span(h, FEEL_END, FEEL_END + 0.04D)) * (1.0D - smooth(span(h, NOTICE_END, NOTICE_END + 0.04D)));
        // It feels about while it is spread, and is still while it looks.
        double sweep = SWEEP * Math.sin(span(h, ARM_END, NOTICE_END) * Math.PI * 2.0D)
                * smooth(span(h, ARM_END, FEEL_END)) * (1.0D - turn) * (1.0D - reach);

        // Its straight-out line: the elbow on the plane, the wrist out in front of it.
        Point out = new Point(0.0D, 0.0D, 1.0D);
        Point restWrist = new Point(Math.sin(sweep) * 0.6D, 0.0D, WRIST_OUT);
        Point flat = new Point(grip.u(), grip.v(), 0.0D);
        double gripDistance = flat.length();
        Point toGrip = flat.unit();
        Point gripWrist = toGrip.times(Math.max(0.0D, gripDistance - GRIP_STANDOFF)).plus(out.times(GRIP_LIFT));
        Point wristHeld = lerpPoint(reach, restWrist, gripWrist);

        Point restForward = new Point(Math.sin(sweep), 0.0D, Math.cos(sweep));
        Point gripForward = grip.plus(out.times(0.3D)).minus(gripWrist).unit();
        Point forward = lerpPoint(reach, restForward, gripForward).unit();

        // The elbow stays in the slit and the forearm stretches out of it to wherever the wrist is - it is not
        // shaped like anything with bones. Coming and going, the whole of it slides straight through the plane,
        // so only `outward` of its length is ever past it.
        double handLength = WRIST_OUT + PALM + FINGER;
        Point slide = out.times(-Math.max(0.0D, handLength - outward));
        Point elbow = new Point(0.0D, 0.0D, -ELBOW_BEHIND).plus(slide);
        Point wrist = wristHeld.plus(slide);

        // Which way the palm faces. Facing down while the arm points straight out, and carried round with the arm
        // by the shortest turn from there - never worked out from "up", which flips the moment the arm passes
        // upright and turns the hand over in a tick. It is then twisted about the arm: by as much as it takes to
        // face the block as it reaches, and to face whoever is there while it looks.
        Point palmNormal = carried(PALM_AT_REST, forward);
        if (reach > 0.0D) {
            Point gripPalm = carried(PALM_AT_REST, gripForward);
            Point wanted = out.times(-1.0D).minus(gripForward.times(-gripForward.n()));
            palmNormal = rotateAbout(palmNormal, forward, reach * twist(gripPalm, wanted, gripForward));
        }
        if (viewer != null && turn > 0.0D) {
            Point look = viewer.minus(wrist);
            Point facing = look.minus(forward.times(look.dot(forward)));
            if (facing.length() > 1.0e-6D) {
                palmNormal = rotateAbout(palmNormal, forward, turn * twist(palmNormal, facing, forward));
            }
        }
        palmNormal = palmNormal.minus(forward.times(palmNormal.dot(forward))).unit();
        Point side = palmNormal.cross(forward).unit();

        Point knuckles = wrist.plus(forward.times(PALM));
        List<List<Point>> fingers = new ArrayList<>(FINGERS);
        for (int finger = 0; finger < FINGERS; finger++) {
            double place = finger / (double) (FINGERS - 1) - 0.5D;
            double spread = place * 2.0D * lerp(splay, SPLAY_LEAST, SPLAY_MOST) * 2.0D;
            Point base = knuckles.plus(side.times(place * PALM_WIDTH));
            if (finger == 0) {
                // The thumb sits back down the palm and out to the side.
                base = wrist.plus(forward.times(PALM * 0.45D)).plus(side.times(-0.5D * PALM_WIDTH - 0.08D));
                spread -= Math.toRadians(30.0D);
            }
            // Spread in the palm's own plane, then bent at each joint towards the side the palm faces.
            Point direction = rotateAbout(forward, palmNormal, spread).unit();
            Point bend = palmNormal.cross(direction).unit();
            List<Point> chain = new ArrayList<>(JOINTS + 1);
            chain.add(base);
            Point at = base;
            for (int joint = 0; joint < JOINTS; joint++) {
                direction = rotateAbout(direction, bend, -curl * CURL_PER_JOINT).unit();
                at = at.plus(direction.times(KNUCKLE));
                chain.add(at);
            }
            fingers.add(chain);
        }
        return new Pose(split, bulge, elbow, wrist, fingers, palmNormal);
    }

    /** The furthest from the slit, along the plane, the block it takes is ever left. */
    public static final double SLIT_REACH = 2.5D;

    /**
     * Where the membrane splits: on the line from the middle of the opening to the block it will take, as near
     * the middle as still leaves that block in reach. Both in the plane's own coordinates; the answer too.
     */
    public static double[] slit(double centreAcross, double centreUp, double gripAcross, double gripUp) {
        double du = gripAcross - centreAcross;
        double dv = gripUp - centreUp;
        double distance = Math.sqrt(du * du + dv * dv);
        if (distance <= SLIT_REACH || distance < 1.0e-9D) {
            return new double[]{centreAcross, centreUp};
        }
        double move = (distance - SLIT_REACH) / distance;
        return new double[]{centreAcross + du * move, centreUp + dv * move};
    }

    /** The arm's line straight out of the plane, and which way the palm faces along it. */
    private static final Point OUT = new Point(0.0D, 0.0D, 1.0D);
    private static final Point PALM_AT_REST = new Point(0.0D, -1.0D, 0.0D);

    /**
     * A palm that faced {@code palm} with the arm straight out, carried round by the shortest turn that points
     * the arm along {@code forward} instead. Never undefined: the arm never points straight back into the plane.
     */
    static Point carried(Point palm, Point forward) {
        Point axis = OUT.cross(forward);
        double sin = axis.length();
        if (sin < 1.0e-9D) {
            return palm;
        }
        return rotateAbout(palm, axis, Math.atan2(sin, OUT.dot(forward)));
    }

    /** The turn about {@code axis} that takes {@code from} round to face {@code to}, in radians, -pi to pi. */
    static double twist(Point from, Point to, Point axis) {
        Point a = axis.unit();
        Point f = from.minus(a.times(from.dot(a)));
        Point t = to.minus(a.times(to.dot(a)));
        if (f.length() < 1.0e-9D || t.length() < 1.0e-9D) {
            return 0.0D;
        }
        return Math.atan2(a.dot(f.cross(t)), f.dot(t));
    }

    /** Rodrigues' rotation of a vector about a unit axis, by an angle in radians. */
    static Point rotateAbout(Point vector, Point axis, double angle) {
        Point k = axis.unit();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return vector.times(cos)
                .plus(k.cross(vector).times(sin))
                .plus(k.times(k.dot(vector) * (1.0D - cos)));
    }

    private static Point lerpPoint(double t, Point from, Point to) {
        return from.plus(to.minus(from).times(t));
    }

    static double span(double value, double from, double to) {
        return to <= from ? (value >= to ? 1.0D : 0.0D) : clamp01((value - from) / (to - from));
    }

    static double smooth(double t) {
        double c = clamp01(t);
        return c * c * (3.0D - 2.0D * c);
    }

    static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
