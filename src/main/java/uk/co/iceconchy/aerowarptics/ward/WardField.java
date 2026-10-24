package uk.co.iceconchy.aerowarptics.ward;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rift Ward's field, as arithmetic: how wide it stands, and where a rift aimed into it comes out instead.
 *
 * <p>A field is a circle on the map and the whole height of the world. Every arrival in this mod is placed
 * sideways first and given a height after, as a scattered exit is, so a field with a top to it would only be
 * something to warp over.
 *
 * <p>Three radii, and they are not the same number. The <em>set</em> radius is what the owner chose, and what
 * upkeep is paid on. The <em>standing</em> radius is that thinned by a Rift Storm, and is what a Rift Plotter and
 * a probe meet. The radius a drive <em>meets</em> is the standing one less that drive's push into it - so a
 * Singularity aimed at the outer part of a field lands where it aimed.
 *
 * <p>Free of Minecraft, like {@code ScatterOffset}: plain numbers in, plain numbers out, so every corner here
 * can be walked in a test.
 */
public final class WardField {

    /** How many times a point is turned aside by overlapping fields before it is walked out of them instead. */
    public static final int MOST_TURNS = 8;

    /** How far past a rim a turned point is set, so that arithmetic alone never leaves it a hair inside. */
    private static final double EDGE = 1.0e-6D;

    private WardField() {
    }

    /** A place on the map. */
    public record Point(double x, double z) {

        public double distanceTo(Point other) {
            return Math.hypot(x - other.x, z - other.z);
        }
    }

    /** One field as something meets it: where its ward stands, and how far out it turns that thing aside. */
    public record Circle(double x, double z, double radius) {

        public boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return radius > 0.0D && dx * dx + dz * dz < radius * radius;
        }

        public boolean contains(Point point) {
            return contains(point.x(), point.z());
        }
    }

    /**
     * Where something ended up, and which fields turned it.
     *
     * @param at      where it comes out
     * @param turnedBy the fields that turned it, by their place in the list asked of, in the order they did
     * @param settled whether {@code at} is clear of every field asked of, which it always should be
     */
    public record Turned(Point at, List<Integer> turnedBy, boolean settled) {

        public boolean moved() {
            return !turnedBy.isEmpty();
        }
    }

    // ------------------------------------------------------------------ how wide

    /** The set radius, thinned while a storm rages over it. */
    public static double standing(double set, boolean storm, double thinning) {
        return Math.max(0.0D, set) * (storm ? clamp01(thinning) : 1.0D);
    }

    /** The radius a drive meets: the standing one, less the share of it that drive pushes through. */
    public static double met(double standing, double penetration) {
        return Math.max(0.0D, standing) * (1.0D - clamp01(penetration));
    }

    // ------------------------------------------------------------------ where it goes

    /**
     * Turns a point aside from one field.
     *
     * <p>Along the line from where the traveller is to where it aimed, the first place that line meets the
     * field, pushed out by {@code clearance} so the whole of what arrives is outside. A traveller with no line
     * to speak of - one starting inside the field, or in another dimension, or directly over its own target -
     * is pushed straight out along the radius through its target instead.
     *
     * @param from      where the traveller is, or {@code null} where that is not a place in this world
     * @param clearance how far outside the field the point must be: half the length of what is arriving
     * @return the target itself when it is not inside the field
     */
    public static Point deflect(Circle field, Point from, Point target, double clearance) {
        if (!field.contains(target)) {
            return target;
        }
        double reach = field.radius() + Math.max(0.0D, clearance);
        if (from != null) {
            double fx = from.x() - field.x();
            double fz = from.z() - field.z();
            double dx = target.x() - from.x();
            double dz = target.z() - from.z();
            double a = dx * dx + dz * dz;
            boolean outside = fx * fx + fz * fz > reach * reach;
            if (outside && a > 1.0e-9D) {
                // |from + t d - centre|^2 = reach^2, and the smaller root is the side the traveller comes in by.
                double b = 2.0D * (fx * dx + fz * dz);
                double c = fx * fx + fz * fz - reach * reach;
                double discriminant = b * b - 4.0D * a * c;
                if (discriminant >= 0.0D) {
                    double t = (-b - Math.sqrt(discriminant)) / (2.0D * a);
                    if (t >= 0.0D && t <= 1.0D) {
                        return new Point(from.x() + dx * t, from.z() + dz * t);
                    }
                }
            }
        }
        return radial(field, target, from, reach);
    }

    /** Straight out from the middle through the target; a target on the middle itself goes out towards the traveller. */
    private static Point radial(Circle field, Point target, Point from, double reach) {
        double dx = target.x() - field.x();
        double dz = target.z() - field.z();
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6D && from != null) {
            dx = from.x() - field.x();
            dz = from.z() - field.z();
            length = Math.hypot(dx, dz);
        }
        if (length < 1.0e-6D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        return new Point(field.x() + dx / length * reach, field.z() + dz / length * reach);
    }

    /**
     * Turns a point aside from every field it is in, until it is clear of them all.
     *
     * <p>Only a rift aimed <em>into</em> a field is turned at all. One that is, is then kept its clearance outside
     * every field asked of, not only the one it was aimed into: the nearest field's middle decides first, and the
     * answer is asked of all of them again, since being turned out of one field into its neighbour's is being
     * inside a field still. Where fields overlap so thickly that turning from each in turn only goes round in
     * circles, it is walked straight out from the middle of the ones that turned it until it is clear.
     */
    public static Turned settle(List<Circle> fields, Point from, Point target, double clearance) {
        if (nearestHolding(fields, target) < 0) {
            return new Turned(target, List.of(), true);
        }
        double margin = Math.max(0.0D, clearance);
        List<Circle> grown = new ArrayList<>(fields.size());
        for (Circle field : fields) {
            // A field with no radius turns nothing, and must not be given one by the clearance.
            grown.add(field.radius() > 0.0D ? new Circle(field.x(), field.z(), field.radius() + margin) : field);
        }
        Point at = target;
        List<Integer> turnedBy = new ArrayList<>();
        for (int turn = 0; turn < MOST_TURNS; turn++) {
            int nearest = nearestHolding(grown, at);
            if (nearest < 0) {
                return new Turned(at, List.copyOf(turnedBy), true);
            }
            at = deflect(grown.get(nearest), from, at, EDGE);
            if (!turnedBy.contains(nearest)) {
                turnedBy.add(nearest);
            }
        }
        if (nearestHolding(grown, at) < 0) {
            return new Turned(at, List.copyOf(turnedBy), true);
        }
        // A thicket. Out from the middle of the fields that turned it, a block at a time, until it is clear of all.
        double mx = 0.0D;
        double mz = 0.0D;
        for (int index : turnedBy) {
            mx += fields.get(index).x() / turnedBy.size();
            mz += fields.get(index).z() / turnedBy.size();
        }
        double dx = at.x() - mx;
        double dz = at.z() - mz;
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }
        double furthest = 0.0D;
        for (Circle field : grown) {
            furthest = Math.max(furthest, Math.hypot(field.x() - mx, field.z() - mz) + field.radius());
        }
        for (double out = 0.0D; out <= furthest + 1.0D; out += 1.0D) {
            Point walked = new Point(mx + dx / length * out, mz + dz / length * out);
            if (nearestHolding(grown, walked) < 0) {
                return new Turned(walked, List.copyOf(turnedBy), true);
            }
        }
        return new Turned(new Point(mx + dx / length * (furthest + 1.0D), mz + dz / length * (furthest + 1.0D)),
                List.copyOf(turnedBy), false);
    }

    /** The field a point is inside whose middle is nearest it, by its place in the list, or -1 for none. */
    public static int nearestHolding(List<Circle> fields, Point at) {
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < fields.size(); index++) {
            Circle field = fields.get(index);
            if (!field.contains(at)) {
                continue;
            }
            double distance = Math.hypot(at.x() - field.x(), at.z() - field.z());
            if (distance < best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    /** Whether a point is inside any of some fields. */
    public static boolean inside(List<Circle> fields, double x, double z) {
        for (Circle field : fields) {
            if (field.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Other places round a field's rim to come out at, for when the first has nowhere to land: the same distance
     * from the middle, stepping round either way by turns, nearest first.
     *
     * @param first       where the rift was turned to
     * @param stepDegrees how far round each step is
     * @param steps       how many steps to take each way
     */
    public static List<Point> rimWalk(Circle field, Point first, double stepDegrees, int steps) {
        double dx = first.x() - field.x();
        double dz = first.z() - field.z();
        double reach = Math.hypot(dx, dz);
        double angle = Math.atan2(dz, dx);
        List<Point> out = new ArrayList<>(steps * 2);
        for (int step = 1; step <= steps; step++) {
            for (int side = -1; side <= 1; side += 2) {
                double at = angle + side * Math.toRadians(stepDegrees) * step;
                out.add(new Point(field.x() + Math.cos(at) * reach, field.z() + Math.sin(at) * reach));
            }
        }
        return out;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
