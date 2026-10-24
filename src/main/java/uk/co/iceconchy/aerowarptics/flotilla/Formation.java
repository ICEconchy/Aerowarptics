package uk.co.iceconchy.aerowarptics.flotilla;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The geometry of a flotilla: turning slots into ship space and back, spacing them, and ordering them.
 *
 * <p>Free of Minecraft on purpose, like {@code WarpCost} and {@code FlightGeometry}, so the answers
 * that decide whether two ships can be told to sit in the same piece of sky are checked by tests rather
 * than by flying them into each other.
 *
 * <h2>The frame</h2>
 * A slot is measured along the flagship's bow ({@link FormationSlot}). The bow is a horizontal axis of
 * the flagship's own ship space - a drive's heading is always one of the four compass points of its
 * hull - given here as the two whole-number steps {@code bowX, bowZ}. Starboard is the bow turned a
 * quarter clockwise seen from above, which with Minecraft's axes (x east, z south) is {@code (-bowZ,
 * bowX)}: a bow to the north has starboard to the east.
 */
public final class Formation {

    private Formation() {
    }

    /**
     * How much room a ship takes up in a formation.
     *
     * <p>A cylinder rather than the hull's box. Escorts sit at whatever heading their own pilots hold,
     * so a box would have to be turned for each of them and would still only be right until one came
     * about; a cylinder about the hull's centre wide enough for its whole footprint is right at every
     * heading. It wastes a little room beside a long, thin hull and never lets two of them touch.
     *
     * @param radius     horizontal distance from the hull's centre to its furthest corner, in blocks
     * @param halfHeight half the hull's height, in blocks
     */
    public record Footprint(double radius, double halfHeight) {

        public Footprint {
            radius = Double.isFinite(radius) ? Math.max(0.0D, radius) : 0.0D;
            halfHeight = Double.isFinite(halfHeight) ? Math.max(0.0D, halfHeight) : 0.0D;
        }

        /** The footprint of a hull whose world box measures this much along each axis. */
        public static Footprint ofBox(double width, double height, double length) {
            return new Footprint(0.5D * Math.sqrt(width * width + length * length), 0.5D * height);
        }
    }

    /**
     * A formation's extent in its own frame: starboard, up and ahead, each from the flagship's centre.
     * What the Fleet Board prints as the size of the wake and what the clearance overlay draws.
     */
    public record Extent(double minRight, double maxRight, double minUp, double maxUp,
                         double minAhead, double maxAhead) {

        public double width() {
            return maxRight - minRight;
        }

        public double height() {
            return maxUp - minUp;
        }

        public double length() {
            return maxAhead - minAhead;
        }
    }

    // ------------------------------------------------------------------ frames

    /**
     * A position given as a step in the flagship's ship space, from its centre, as a slot.
     *
     * @return the slot that step describes
     */
    public static FormationSlot toSlot(double dx, double dy, double dz, int bowX, int bowZ) {
        double ahead = dx * bowX + dz * bowZ;
        double right = -dx * bowZ + dz * bowX;
        return new FormationSlot(right, dy, ahead);
    }

    /**
     * A slot as a step in the flagship's ship space, from its centre: the inverse of {@link #toSlot}.
     *
     * @return {@code {dx, dy, dz}}
     */
    public static double[] toShip(FormationSlot slot, int bowX, int bowZ) {
        double dx = slot.ahead() * bowX - slot.right() * bowZ;
        double dz = slot.ahead() * bowZ + slot.right() * bowX;
        return new double[]{dx, slot.up(), dz};
    }

    /** How far a ship at {@code actual} is from the slot it has been given, in blocks. */
    public static double drift(FormationSlot actual, FormationSlot assigned) {
        double dr = actual.right() - assigned.right();
        double du = actual.up() - assigned.up();
        double da = actual.ahead() - assigned.ahead();
        return Math.sqrt(dr * dr + du * du + da * da);
    }

    /** Whether a ship this far from its slot is keeping station. Exactly at the tolerance still counts. */
    public static boolean inStation(double drift, double tolerance) {
        return drift <= tolerance;
    }

    // ----------------------------------------------------------------- spacing

    /**
     * Whether two ships, given these slots, would share any sky.
     *
     * <p>A slot is somewhere a ship is going to be put down at the end of a fold, so this is the check
     * that stands between a formation and two hulls materialising inside one another. The margin is the
     * room the pilots are allowed to wander: two ships each keeping station within tolerance of slots
     * that are further apart than their footprints and both tolerances can never touch.
     */
    public static boolean clashes(FormationSlot a, Footprint fa, FormationSlot b, Footprint fb, double margin) {
        double dr = a.right() - b.right();
        double da = a.ahead() - b.ahead();
        double horizontal = Math.sqrt(dr * dr + da * da);
        double vertical = Math.abs(a.up() - b.up());
        return horizontal < fa.radius() + fb.radius() + margin
                && vertical < fa.halfHeight() + fb.halfHeight() + margin;
    }

    /**
     * The first ship a slot would clash with, or {@code -1} when it is clear of all of them.
     *
     * @param others    every other slot in the formation, the flagship's own ({@link FormationSlot#FLAGSHIP})
     *                  included
     * @param footprints their footprints, in the same order
     */
    public static int firstClash(FormationSlot slot, Footprint footprint,
                                 List<FormationSlot> others, List<Footprint> footprints, double margin) {
        for (int i = 0; i < others.size(); i++) {
            if (clashes(slot, footprint, others.get(i), footprints.get(i), margin)) {
                return i;
            }
        }
        return -1;
    }

    /** A slot rounded to the nearest {@code step} blocks on every axis, so dragged markers line up. */
    public static FormationSlot snap(FormationSlot slot, double step) {
        if (!(step > 0.0D)) {
            return slot;
        }
        return new FormationSlot(Math.round(slot.right() / step) * step,
                Math.round(slot.up() / step) * step,
                Math.round(slot.ahead() / step) * step);
    }

    /** A slot pulled back along its own line until it is no further than {@code reach} from the flagship. */
    public static FormationSlot clampReach(FormationSlot slot, double reach) {
        double length = slot.reach();
        if (length <= reach || length <= 0.0D) {
            return slot;
        }
        double scale = reach / length;
        return new FormationSlot(slot.right() * scale, slot.up() * scale, slot.ahead() * scale);
    }

    // ---------------------------------------------------------------- ordering

    /**
     * Which order the escorts tear through in: front to back, so the fleet goes like a broadside.
     *
     * <p>Furthest ahead first. Level with each other, the one nearer the flagship's line goes first, so
     * a line abreast ripples outwards from the middle rather than from one arbitrary end. Anything
     * still tied keeps the order it was given, so the same formation always ripples the same way.
     *
     * @return indices into {@code slots}
     */
    public static List<Integer> rippleOrder(List<FormationSlot> slots) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingDouble(i -> -slots.get(i).ahead())
                .thenComparingDouble(i -> Math.abs(slots.get(i).right()))
                .thenComparingInt(i -> i));
        return order;
    }

    /**
     * The box a whole formation fills, the flagship's footprint included.
     *
     * @param slots      every slot, the flagship's among them
     * @param footprints their footprints, in the same order
     */
    public static Extent extent(List<FormationSlot> slots, List<Footprint> footprints) {
        double minR = 0, maxR = 0, minU = 0, maxU = 0, minA = 0, maxA = 0;
        boolean first = true;
        for (int i = 0; i < slots.size(); i++) {
            FormationSlot slot = slots.get(i);
            Footprint footprint = footprints.get(i);
            double r = footprint.radius();
            double h = footprint.halfHeight();
            if (first) {
                minR = slot.right() - r;
                maxR = slot.right() + r;
                minU = slot.up() - h;
                maxU = slot.up() + h;
                minA = slot.ahead() - r;
                maxA = slot.ahead() + r;
                first = false;
                continue;
            }
            minR = Math.min(minR, slot.right() - r);
            maxR = Math.max(maxR, slot.right() + r);
            minU = Math.min(minU, slot.up() - h);
            maxU = Math.max(maxU, slot.up() + h);
            minA = Math.min(minA, slot.ahead() - r);
            maxA = Math.max(maxA, slot.ahead() + r);
        }
        return new Extent(minR, maxR, minU, maxU, minA, maxA);
    }
}
