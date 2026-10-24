package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * A flotilla going through one rift: the flagship tears a single aperture in front of the whole
 * formation, every ship flies into it together, and they come out of one at the far end in their slots.
 *
 * <p>Each ship still flies its own flight on its own drive and its own charge. What they share is the run.
 * Every flight in the passage is planned as though its hull were as long as the whole formation, along the
 * flagship's bearing and on the flagship's clock: the same run at the rift, the same passage, the same
 * corridor, the same run out. So the formation moves as one rigid thing from the moment it sets off to the
 * moment it comes to rest, and that is the only way several hulls can share one throat. Planned each on
 * its own, the ship at the head of a column slows to passage speed at the plane and stops inside the throat
 * for the corridor, while the ship behind it - running at the same plane - flies straight into it. Moving in
 * step, nobody closes on anybody, because nobody was close to anybody to begin with: the slots were kept
 * apart when they were set.
 *
 * <p>Three things have to be the same for every ship for that to hold, and each is settled here rather than
 * left to the drives:
 * <ul>
 *   <li><b>When it sets off.</b> The escorts' rifts open a tick or two after the flagship's, as the word
 *   reaches each drive, so every flight holds still until {@link #startTick} and they all go together.
 *   <li><b>How far it runs.</b> The run at the rift is flown by dead reckoning, the same {@link #approach}
 *   for everybody, rather than measured to a plane. Measured, each hull would stop running when its own bow
 *   met the plane, a tick apart from its neighbours depending on how the physics nudged it.
 *   <li><b>The passage and the run out.</b> {@link #transitRun} is long enough to swallow the whole
 *   formation front to back, and {@link #emergeDistance} is sized from it by the rule a single hull's is.
 *   Both ends therefore see the formation cross the plane in order, front first, and all of it clear.
 * </ul>
 *
 * <p>The apertures themselves are drawn only - the physics of a flight still runs off its own planes - and
 * are sized to hold every ship's own aperture, which is what hides each hull as it goes in. An upright
 * ellipse rather than a circle, because a flotilla is usually far wider than it is tall, and a circle round
 * three ships abreast stands a hundred blocks into the ground.
 *
 * <p>Pure apart from its NBT, like {@link FlightGeometry}, so the geometry is tested directly.
 *
 * @param heading        the flagship's bearing, horizontal and unit length, which every ship flies along
 * @param span           the formation's length along it, with room for every escort to have been anywhere
 *                       in its station
 * @param transitRun     how far every ship travels from the passage starting to the formation being inside
 * @param transitTicks   how long that takes
 * @param corridorTicks  how long every ship holds the corridor - the flagship's drive's figure
 * @param emergeTicks    how long every ship takes to settle at the far end - the flagship's too
 * @param emergeDistance how far back from its slot every ship reappears
 * @param approach       how far every ship runs before the passage starts: the lead, from the front bow
 * @param entry          the one aperture the formation flies into
 * @param exit           the one it comes out of, or {@code null} until the flagship's search has put the
 *                       formation down somewhere
 * @param startTick      the game time the formation sets off, or 0 until the exit is known
 */
public record FleetPassage(Vector3d heading, double span, double transitRun, int transitTicks, int corridorTicks,
                           int emergeTicks, double emergeDistance, double approach, WarpFlight.Rift entry,
                           @Nullable WarpFlight.Rift exit, long startTick) {

    /**
     * Ticks between the flagship's rift opening and the formation setting off.
     *
     * <p>Long enough for the word to reach every escort's drive and for each to open its flight - which takes
     * the coupler's tick and then the drive's, so one or two - with room to spare. A flight that opened late
     * would set off late and run a step behind the rest. Half a second of the fleet holding still in front
     * of a rift that has just torn open is also no bad thing to look at.
     */
    public static final int START_DELAY_TICKS = 10;

    /** Samples round each ship's aperture when fitting the fleet's round them all. */
    private static final int RIM_SAMPLES = 72;

    /**
     * Blocks, and a fraction of the radius, added to each ship's aperture before it is sampled. A polygon
     * through the samples is a little smaller than the circle it was taken from; padded this much first, it
     * is never smaller than the circle it stands for.
     */
    private static final double FIT_PAD = 1.0D;
    private static final double FIT_PAD_FRACTION = 0.002D;

    /** How flat, or tall, the fleet's aperture may be: from a thirtieth as tall as wide to thirty times. */
    private static final double MIN_ASPECT_EXPONENT = -1.5D;
    private static final double MAX_ASPECT_EXPONENT = 1.5D;
    private static final int COARSE_STEPS = 60;
    private static final int FINE_STEPS = 40;

    /**
     * One ship in the passage.
     *
     * @param centre     its hull's centre - where it is at the start, or where it comes to rest at the end
     * @param span       its own length along the heading
     * @param riftRadius the radius of the aperture it would have torn for itself
     */
    public record Hull(Vector3dc centre, double span, double riftRadius) {

        /** The same hull somewhere else. */
        public Hull at(Vector3dc elsewhere) {
            return new Hull(new Vector3d(elsewhere), span, riftRadius);
        }
    }

    /**
     * The passage for a formation as it stands now, with the entry torn in front of it and the far end still
     * to be found.
     *
     * @param bow                  the flagship's bearing
     * @param departing            every ship going through, where it is now; never empty
     * @param lead                 blocks from the front bow to the entry - {@code flight.riftLeadDistance}
     * @param tolerance            how far any escort may be from its slot - {@code stationTolerance}
     * @param transitTicks         {@code flight.riftTransitTicks}
     * @param corridorTicks        the flagship's drive's corridor ticks
     * @param emergeTicks          the flagship's drive's arrive ticks
     * @param emergeDistanceFactor {@code flight.emergeDistanceFactor}
     */
    public static FleetPassage of(Vector3dc bow, List<Hull> departing, double lead, double tolerance,
                                  int transitTicks, int corridorTicks, int emergeTicks,
                                  double emergeDistanceFactor) {
        if (departing.isEmpty()) {
            throw new IllegalArgumentException("a passage with nobody in it");
        }
        Vector3d heading = FlightGeometry.horizontal(bow);
        // Measured where the ships are now, and widened by a station's width either way: the formation that
        // comes out is the slots, and each ship may be up to that far from its slot when it goes in. The
        // passage has to swallow whichever of the two is longer.
        double span = front(heading, departing) - back(heading, departing) + 2.0D * Math.max(0.0D, tolerance);
        double transitRun = span + FlightGeometry.TRANSIT_MARGIN;
        int ticks = FlightGeometry.transitTicks(transitTicks);
        double emergeDistance = FlightGeometry.emergeDistance(span, transitRun, ticks, emergeTicks,
                emergeDistanceFactor);
        WarpFlight.Rift entry = aperture(heading, entryPlane(heading, departing, lead), departing);
        return new FleetPassage(heading, span, transitRun, ticks, Math.max(1, corridorTicks),
                Math.max(1, emergeTicks), emergeDistance, lead, entry, null, 0L);
    }

    /**
     * The same passage with its far end: the exit, stood in front of where the formation reappears, and the
     * moment it sets off.
     *
     * @param resting every ship going through, at the centre it comes to rest on
     */
    public FleetPassage arriving(List<Hull> resting, long startTick) {
        if (resting.isEmpty()) {
            throw new IllegalArgumentException("a passage with nobody in it");
        }
        return new FleetPassage(heading, span, transitRun, transitTicks, corridorTicks, emergeTicks,
                emergeDistance, approach, entry, aperture(heading, exitPlane(resting), resting), startTick);
    }

    /**
     * Where the entry stands, as a distance along the heading: the lead in front of the foremost bow.
     *
     * <p>Every ship runs {@link #approach} - the lead - before the passage starts, so this is where the
     * foremost bow is when it does. Everything behind it goes in during the passage.
     */
    public static double entryPlane(Vector3dc heading, List<Hull> departing, double lead) {
        return front(heading, departing) + lead;
    }

    /**
     * Where the exit stands, as a distance along the heading: half the passage margin in front of the
     * foremost bow as the formation reappears, the mirror of the way one hull comes out of its own.
     */
    public double exitPlane(List<Hull> resting) {
        return front(heading, resting) - emergeDistance + FlightGeometry.TRANSIT_MARGIN * 0.5D;
    }

    /** Blocks per tick every ship travels through the passage. */
    public double passageSpeed() {
        return transitRun / transitTicks;
    }

    /** A ship's own geometry, flown on this passage's run. */
    public FlightGeometry flownBy(FlightGeometry own) {
        return own.flownWith(transitRun, transitTicks, emergeDistance);
    }

    /** How far along the heading the foremost bow reaches. */
    static double front(Vector3dc heading, List<Hull> hulls) {
        double front = Double.NEGATIVE_INFINITY;
        for (Hull hull : hulls) {
            front = Math.max(front, hull.centre().dot(heading) + hull.span() * 0.5D);
        }
        return front;
    }

    /** How far along the heading the hindmost stern reaches. */
    static double back(Vector3dc heading, List<Hull> hulls) {
        double back = Double.POSITIVE_INFINITY;
        for (Hull hull : hulls) {
            back = Math.min(back, hull.centre().dot(heading) - hull.span() * 0.5D);
        }
        return back;
    }

    // ---------------------------------------------------------------- aperture

    /**
     * The smallest upright ellipse, standing across the heading at {@code plane}, that every ship's own
     * aperture fits inside.
     *
     * <p>Centred on the middle of the ships' apertures, then every shape from very flat to very tall is
     * tried and the one covering least area kept. Each ship's aperture is its own radius round its own
     * centre, which is what hides that hull in a rift of its own - so a hull inside the fleet's is hidden by
     * the fleet's just as well.
     *
     * @param plane where the aperture stands, as a distance along the heading
     */
    static WarpFlight.Rift aperture(Vector3dc heading, double plane, List<Hull> hulls) {
        // The plane's two axes: across the bearing, and straight up. Unit length and square to the heading,
        // so any world point is its distance along each of the three added up.
        Vector3d across = new Vector3d(-heading.z(), 0.0D, heading.x());
        int count = hulls.size();
        double[] u = new double[count];
        double[] v = new double[count];
        double[] r = new double[count];
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            Hull hull = hulls.get(i);
            u[i] = hull.centre().dot(across);
            v[i] = hull.centre().y();
            r[i] = hull.riftRadius() * (1.0D + FIT_PAD_FRACTION) + FIT_PAD;
            minU = Math.min(minU, u[i] - r[i]);
            maxU = Math.max(maxU, u[i] + r[i]);
            minV = Math.min(minV, v[i] - r[i]);
            maxV = Math.max(maxV, v[i] + r[i]);
        }
        double midU = (minU + maxU) * 0.5D;
        double midV = (minV + maxV) * 0.5D;

        // The area of an ellipse is its width times its height, give or take pi; how wide one of a given
        // shape has to be is the farthest any sample reaches in that shape's own measure. Coarse over the
        // whole range, then fine either side of the best.
        double bestExponent = 0.0D;
        double bestArea = Double.POSITIVE_INFINITY;
        double step = (MAX_ASPECT_EXPONENT - MIN_ASPECT_EXPONENT) / COARSE_STEPS;
        for (int i = 0; i <= COARSE_STEPS; i++) {
            double exponent = MIN_ASPECT_EXPONENT + step * i;
            double area = area(exponent, midU, midV, u, v, r);
            if (area < bestArea) {
                bestArea = area;
                bestExponent = exponent;
            }
        }
        double from = bestExponent - step;
        double fine = 2.0D * step / FINE_STEPS;
        for (int i = 0; i <= FINE_STEPS; i++) {
            double exponent = Math.max(MIN_ASPECT_EXPONENT, Math.min(MAX_ASPECT_EXPONENT, from + fine * i));
            double area = area(exponent, midU, midV, u, v, r);
            if (area < bestArea) {
                bestArea = area;
                bestExponent = exponent;
            }
        }
        double aspect = Math.pow(10.0D, bestExponent);
        double width = widthFor(aspect, midU, midV, u, v, r);

        Vector3d centre = new Vector3d(across).mul(midU)
                .add(0.0D, midV, 0.0D)
                .add(new Vector3d(heading).mul(plane));
        return new WarpFlight.Rift(centre, new Vector3d(heading), width, aspect);
    }

    private static double area(double exponent, double midU, double midV, double[] u, double[] v, double[] r) {
        double aspect = Math.pow(10.0D, exponent);
        double width = widthFor(aspect, midU, midV, u, v, r);
        return width * width * aspect;
    }

    /** How wide an ellipse of this shape, round this middle, has to be to hold every sample of every rim. */
    private static double widthFor(double aspect, double midU, double midV, double[] u, double[] v, double[] r) {
        double width = 0.0D;
        for (int i = 0; i < u.length; i++) {
            for (int k = 0; k < RIM_SAMPLES; k++) {
                double angle = k * (Math.PI * 2.0D / RIM_SAMPLES);
                double du = u[i] + r[i] * Math.cos(angle) - midU;
                double dv = (v[i] + r[i] * Math.sin(angle) - midV) / aspect;
                width = Math.max(width, Math.hypot(du, dv));
            }
        }
        return width;
    }

    /**
     * Whether a point in the plane of an aperture lies inside it - for the tests, and for anything that
     * wants to ask of a rift that is not round.
     */
    public static boolean inside(WarpFlight.Rift rift, Vector3dc point) {
        Vector3d heading = FlightGeometry.horizontal(rift.normal());
        Vector3d across = new Vector3d(-heading.z, 0.0D, heading.x);
        Vector3d offset = new Vector3d(point).sub(rift.centre());
        double du = offset.dot(across) / rift.radius();
        double dv = offset.y / (rift.radius() * rift.aspect());
        return du * du + dv * dv <= 1.0D;
    }

    // --------------------------------------------------------------------- nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("HeadingX", heading.x);
        tag.putDouble("HeadingZ", heading.z);
        tag.putDouble("Span", span);
        tag.putDouble("TransitRun", transitRun);
        tag.putInt("TransitTicks", transitTicks);
        tag.putInt("CorridorTicks", corridorTicks);
        tag.putInt("EmergeTicks", emergeTicks);
        tag.putDouble("EmergeDistance", emergeDistance);
        tag.putDouble("Approach", approach);
        tag.put("Entry", entry.save());
        if (exit != null) {
            tag.put("Exit", exit.save());
        }
        tag.putLong("Start", startTick);
        return tag;
    }

    @Nullable
    public static FleetPassage load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Entry")) {
            return null;
        }
        return new FleetPassage(FlightGeometry.horizontal(new Vector3d(tag.getDouble("HeadingX"), 0.0D,
                tag.getDouble("HeadingZ"))), tag.getDouble("Span"), tag.getDouble("TransitRun"),
                FlightGeometry.transitTicks(tag.getInt("TransitTicks")), Math.max(1, tag.getInt("CorridorTicks")),
                Math.max(1, tag.getInt("EmergeTicks")), tag.getDouble("EmergeDistance"), tag.getDouble("Approach"),
                WarpFlight.Rift.load(tag.getCompound("Entry")),
                tag.contains("Exit") ? WarpFlight.Rift.load(tag.getCompound("Exit")) : null, tag.getLong("Start"));
    }
}
