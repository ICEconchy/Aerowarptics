package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;

import java.util.List;

/**
 * The airship's journey through the rift, from the moment it starts its run to the moment it comes to
 * rest at the far end.
 *
 * <p>A warp is flown through the rift, not teleported. The drive opens a rift ahead of the bow, the server takes the helm
 * and drives the hull at it, the hull goes through the threshold and keeps going down the aperture's
 * throat for a few seconds, and then a second aperture puts it down at the destination still moving,
 * so it coasts out under its own momentum.
 *
 * <p>The hull holds one speed from the moment it reaches the aperture until it is clear at the far
 * end. The approach eases down to that speed before the bow touches the plane, the corridor run
 * continues at it, the single teleport carries it across, and only once the ship is back in open air
 * does it bleed away to nothing. There is no boundary at which the ship's motion changes.
 *
 * <pre>
 *      APPROACH        TRANSIT + CORRIDOR              BREACH        EMERGE
 *   ┌────┐            ((entry))                     ((exit))
 *   │ship│──▶──▶──▶ ──┃═══ ship ══▶ ══════════┓  ┏━━━━━┃ship┃──▶──▶ ┌────┐
 *   └────┘             the throat, out of sight ┘  └ the throat     │rest│
 *                                    │                              └────┘
 *                             one teleport, in here
 * </pre>
 *
 * <p>The apertures are passed through rather than touched. The hull flies at the entry rift until its
 * bow reaches the plane, then keeps going straight down the throat behind it - the passage, and then
 * the corridor run, both happening inside a tube that hides it completely. The single teleport happens
 * deep inside that tube and lands the hull deep inside the far one, still moving at the same speed in
 * the same direction, so the only thing that changes is the scenery. Then it flies out nose first.
 *
 * <p>Motion is commanded as velocity rather than as a teleport per tick, so Sable moves the hull the
 * way it moves any airship and everything standing on the deck comes along without special handling.
 * The two threshold crossings are the only teleports, and both go through
 * {@link Airship#relocate} so passengers are carried across correctly.
 */
public final class WarpFlight {

    /** Where the airship is in its journey. */
    public enum Stage {
        /** Running at the entry rift under server control. */
        APPROACH,
        /** Bow through the entry aperture, being drawn the rest of the way in. */
        TRANSIT,
        /** In the corridor, high above the world. */
        CORRIDOR,
        /** Behind the exit aperture, coming back out of it nose first. */
        BREACH,
        /** Clear of the exit rift and coasting to its resting place. */
        EMERGE
    }

    /**
     * A rift, as both the client needs to draw it and the server needs to fly at it.
     *
     * @param centre world position of the aperture's centre
     * @param normal unit vector the airship passes through the aperture along
     * @param radius aperture radius, scaled to the hull that has to fit through it - across it, for one
     *               that is not round
     * @param aspect how much taller than wide it is. One for the circle every drive tears for itself; a
     *               flotilla's one rift is an ellipse round the whole formation - see {@link FleetPassage}
     */
    public record Rift(Vector3d centre, Vector3d normal, double radius, double aspect) {

        public Rift(Vector3d centre, Vector3d normal, double radius) {
            this(centre, normal, radius, 1.0D);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("cx", centre.x);
            tag.putDouble("cy", centre.y);
            tag.putDouble("cz", centre.z);
            tag.putDouble("nx", normal.x);
            tag.putDouble("ny", normal.y);
            tag.putDouble("nz", normal.z);
            tag.putDouble("r", radius);
            if (aspect != 1.0D) {
                tag.putDouble("a", aspect);
            }
            return tag;
        }

        public static Rift load(CompoundTag tag) {
            return new Rift(
                    new Vector3d(tag.getDouble("cx"), tag.getDouble("cy"), tag.getDouble("cz")),
                    new Vector3d(tag.getDouble("nx"), tag.getDouble("ny"), tag.getDouble("nz")),
                    tag.getDouble("r"),
                    // Absent on every rift saved before an aperture could be anything but round.
                    tag.contains("a") ? tag.getDouble("a") : 1.0D);
        }
    }

    private final Rift entry;
    private final Rift exit;
    private final Vector3d arrivalOrigin;
    private final Quaterniond arrivalOrientation;
    /**
     * The attitude the hull flies its run at the rift at, which is the one it had at its mooring. The same as
     * {@link #arrivalOrientation} for every flight but one into a Berth, which turns the hull in the fold.
     */
    private final Quaterniond departureOrientation;
    private final Vector3d emergeStart;
    /** How far the hull must travel for the aperture to swallow it whole, bow to stern. */
    private final double transitRun;
    /** The hull's own extent along the bearing, which sets when the bow reaches the plane. */
    private final double hullSpan;
    /** The hull's radius the apertures were scaled from, kept so the aperture margin can be re-derived. */
    private final double hullRadius;
    /** The hull's horizontal extent across the bearing, which sets how wide a run the tickets hold. */
    private final double crossSpan;
    /** How far the corridor run carries the hull, on top of the passage. Zero holds it still. */
    private final double corridorDrift;
    private final int transitTicks;
    private final int corridorTicks;
    private final int emergeTicks;
    private final int approachLimitTicks;

    /**
     * For a flight keeping step with a formation through one rift - see {@link FleetPassage} - the ticks it
     * holds still before setting off, how far it runs at the rift, and how far it has run so far. A run of
     * zero is a flight on its own, which measures its way to its plane instead.
     */
    private int stepWait;
    private double stepRun;
    private double stepFlown;

    private Stage stage = Stage.APPROACH;
    private int stageTicks;
    /** The velocity commanded on the most recent tick, kept so the trace can compare it to reality. */
    private final Vector3d lastCommanded = new Vector3d();

    private WarpFlight(Rift entry, Rift exit,
                       Vector3d arrivalOrigin, Quaterniond arrivalOrientation, Quaterniond departureOrientation,
                       Vector3d emergeStart,
                       double transitRun, double hullSpan, double hullRadius, double crossSpan,
                       double corridorDrift, int transitTicks,
                       int corridorTicks, int emergeTicks, int approachLimitTicks) {
        this.entry = entry;
        this.exit = exit;
        this.arrivalOrigin = arrivalOrigin;
        this.arrivalOrientation = arrivalOrientation;
        this.departureOrientation = departureOrientation;
        this.emergeStart = emergeStart;
        this.transitRun = transitRun;
        this.hullSpan = hullSpan;
        this.hullRadius = hullRadius;
        this.crossSpan = crossSpan;
        this.corridorDrift = corridorDrift;
        this.transitTicks = transitTicks;
        this.corridorTicks = corridorTicks;
        this.emergeTicks = emergeTicks;
        this.approachLimitTicks = approachLimitTicks;
    }

    // ------------------------------------------------------------------ plan

    /**
     * Works out the whole journey before any of it happens, so a warp that cannot land is refused
     * while the airship is still safely at its mooring rather than mid-corridor.
     *
     * @param airship     the airship about to leave
     * @param destination level the anchor lives in
     * @param anchorPos   the anchor
     * @param bowDirection  world-space unit vector the airship will fly along, already resolved
     * @param corridorTicks how long this drive tier holds the corridor open
     * @param emergeTicks   how long this drive tier takes to set the hull down at the far end
     * @param arrivalHeight the destination's arrival height, already resolved - see {@link ArrivalHeight}
     * @param searchArrival whether to look for clear ground at the far end. {@code false} when the drive's
     *                      clearance checks are switched off: the hull is then set down exactly at the
     *                      target and height, whatever stands there - see {@link SafeArrival#straightIn}
     * @return the planned flight, or the reason there is none
     */
    public static Plan plan(Airship airship, ServerLevel destination, BlockPos anchorPos,
                            Vector3dc bowDirection, int corridorTicks, int emergeTicks,
                            int arrivalHeight, boolean searchArrival) {
        return plan(airship, destination, anchorPos, bowDirection, corridorTicks, emergeTicks, arrivalHeight,
                searchArrival, List.of());
    }

    /**
     * The same plan, for a flagship bringing a formation: the arrival search proves every companion's slot
     * along with the flagship's own - see {@link SafeArrival.Companion}. With the clearance checks off the
     * formation comes in straight over the target, like any unchecked warp.
     */
    public static Plan plan(Airship airship, ServerLevel destination, BlockPos anchorPos,
                            Vector3dc bowDirection, int corridorTicks, int emergeTicks,
                            int arrivalHeight, boolean searchArrival, List<SafeArrival.Companion> companions) {
        return plan(airship, destination, anchorPos, bowDirection, corridorTicks, emergeTicks, arrivalHeight,
                searchArrival, companions, null);
    }

    /**
     * The same plan, for a flagship taking its formation through one rift: flown on the fleet's run rather
     * than its own hull's, so the search proves the whole fleet's longer run out - see {@link FleetPassage}.
     * The flight keeps step once the flagship's coupler has settled when the fleet sets off; see
     * {@link #keepStep}.
     *
     * @param passage the passage, or {@code null} for a flight on its own run
     */
    public static Plan plan(Airship airship, ServerLevel destination, BlockPos anchorPos,
                            Vector3dc bowDirection, int corridorTicks, int emergeTicks,
                            int arrivalHeight, boolean searchArrival, List<SafeArrival.Companion> companions,
                            @Nullable FleetPassage passage) {
        Quaterniond orientation = new Quaterniond(airship.orientation());
        Vector3d heading = new Vector3d(bowDirection);
        if (passage != null) {
            corridorTicks = passage.corridorTicks();
            emergeTicks = passage.emergeTicks();
        }
        // Corridor length, aperture width and run-out all come off the hull as it actually sits,
        // measured along and across the bearing - see FlightGeometry for why not off its world box.
        // Not off the whole assembly either: a bearing contraption's box encloses the blades' full
        // rotation, and scaling the aperture by that made the corridor grow with a ship's propellers
        // rather than with the ship.
        FlightGeometry geometry = geometry(airship, heading, emergeTicks, passage);
        if (geometry == null) {
            return new Plan(null, WarpFailure.AIRSHIP_LOST);
        }
        double emergeDistance = geometry.emergeDistance();

        SafeArrival.Result arrival;
        if (searchArrival) {
            SafeArrival.Search search = SafeArrival.find(airship, destination, anchorPos, orientation,
                    heading, emergeDistance, arrivalHeight, companions);
            arrival = search.result();
            if (arrival == null) {
                // "Nowhere to land" and "could not tell" are different things to a pilot: the first wants
                // a different anchor, the second is about the size of the ship and not the ground at all.
                return new Plan(null, search.verdict() == SafeArrival.Clearance.UNPROVEN
                        ? WarpFailure.ARRIVAL_UNPROVEN : WarpFailure.NO_SAFE_ARRIVAL);
            }
        } else {
            arrival = SafeArrival.straightIn(airship, anchorPos, orientation, arrivalHeight);
            if (arrival == null) {
                return new Plan(null, WarpFailure.AIRSHIP_LOST);
            }
        }

        return build(airship, geometry, arrival, heading, heading, orientation, corridorTicks, emergeTicks);
    }

    /**
     * A flight to a placement somebody else has already proven: an escort's slot in a fold, which the
     * flagship's search cleared as part of the formation. Nothing is searched and nothing is read.
     *
     * @param centre where the hull's centre comes to rest
     */
    public static Plan planTo(Airship airship, Vector3dc centre, Vector3dc bowDirection, int corridorTicks,
                              int emergeTicks) {
        return planTo(airship, centre, bowDirection, corridorTicks, emergeTicks, null);
    }

    /**
     * The same, for an escort going through its flotilla's one rift: along the flagship's bearing, on the
     * fleet's run - see {@link FleetPassage}.
     *
     * @param passage the passage, or {@code null} for a flight on its own run
     */
    public static Plan planTo(Airship airship, Vector3dc centre, Vector3dc bowDirection, int corridorTicks,
                              int emergeTicks, @Nullable FleetPassage passage) {
        Vector3d heading = new Vector3d(bowDirection);
        if (passage != null) {
            corridorTicks = passage.corridorTicks();
            emergeTicks = passage.emergeTicks();
        }
        FlightGeometry geometry = geometry(airship, heading, emergeTicks, passage);
        SafeArrival.Result arrival = SafeArrival.exactly(airship, centre);
        if (geometry == null || arrival == null) {
            return new Plan(null, WarpFailure.AIRSHIP_LOST);
        }
        return build(airship, geometry, arrival, heading, heading, new Quaterniond(airship.orientation()),
                corridorTicks, emergeTicks);
    }

    /**
     * A flight into a place somebody else has chosen, <em>turned</em>: a Berth's slip, where the hull comes
     * to rest at an attitude that is not the one it left at, having flown out of its exit rift along a bearing
     * that is not the one it flew in on.
     *
     * <p>Every other flight has one bearing and one attitude, and the far end is the near end moved. This
     * one's far end is planned on the slip's: the exit rift stands in the slip's lane and faces down it, and
     * the hull is turned at the crossing, inside the throat, where nobody sees it done. Nothing is searched
     * and nothing is read - whoever chose the place proves it, as an escort's slot is proved by its flagship.
     *
     * @param centre       where the vessel's centre comes to rest
     * @param orientation  the attitude it rests at
     * @param approach     the level unit vector it travels along as it comes out, which for a ship backing in
     *                     is not the way its bow points
     * @param bowDirection the bearing it flies its run at the entry rift on, as for any flight
     */
    public static Plan planInto(Airship airship, Vector3dc centre, Quaterniondc orientation, Vector3dc approach,
                                Vector3dc bowDirection, int corridorTicks, int emergeTicks) {
        Vector3d heading = new Vector3d(bowDirection);
        FlightGeometry geometry = geometry(airship, heading, emergeTicks, null);
        SafeArrival.Result arrival = SafeArrival.exactly(airship, centre, orientation);
        if (geometry == null || arrival == null) {
            return new Plan(null, WarpFailure.AIRSHIP_LOST);
        }
        return build(airship, geometry, arrival, heading, new Vector3d(approach), new Quaterniond(orientation),
                corridorTicks, emergeTicks);
    }

    /** Everything a flight is once the far end is settled: the apertures, the run out, the timings. */
    private static Plan build(Airship airship, FlightGeometry geometry, SafeArrival.Result arrival,
                              Vector3d heading, Vector3d exitHeading, Quaterniond orientation, int corridorTicks,
                              int emergeTicks) {
        double hullLength = geometry.hullSpan();
        double riftRadius = geometry.riftRadius();
        double transitRun = geometry.transitRun();
        int transitTicks = geometry.transitTicks();
        double emergeDistance = geometry.emergeDistance();

        Vector3d arrivalOrigin = new Vector3d(arrival.origin());
        Quaterniond departure = new Quaterniond(airship.orientation());

        // The entry rift stands off the bow far enough that the hull has room to build up speed. Placed
        // against the middle of the whole vessel, which for a ship of one sub-level is its world box's
        // centre: where the ship is. For one built in sections it is the middle of all of them, so the
        // aperture stands ahead of the foremost section and not merely ahead of the drive's.
        Vector3d hullCentre = airship.vesselCentre(new Vector3d());
        double lead = hullLength * 0.5D + AWConfig.RIFT_LEAD_DISTANCE.get();
        Rift entry = new Rift(heading.mul(lead, new Vector3d()).add(hullCentre), new Vector3d(heading), riftRadius);

        // An aperture is placed against the hull's centre, but a flight is planned in pose origins,
        // and on most builds those are not the same point. Carry the offset across so the far rift
        // ends up where the hull actually will be rather than where its origin is.
        Vector3d originToCentre = hullCentre.sub(airship.position(), new Vector3d());
        FarEnd far = farEnd(arrivalOrigin, originToCentre, departure, orientation, exitHeading, emergeDistance,
                hullLength, riftRadius);

        return new Plan(new WarpFlight(entry, far.exit(), arrivalOrigin, orientation, departure,
                far.emergeStart(), transitRun, hullLength, geometry.hullRadius(), geometry.crossSpan(),
                corridorDrift(), transitTicks, Math.max(1, corridorTicks), Math.max(1, emergeTicks),
                Math.max(20, AWConfig.APPROACH_LIMIT_TICKS.get())), WarpFailure.NONE);
    }

    /**
     * The far end of a flight: where the hull's origin is when it reappears, and the rift it flies out of.
     */
    public record FarEnd(Vector3d emergeStart, Rift exit) {
    }

    /**
     * Works out the far end of a flight from where the hull comes to rest.
     *
     * <p>The hull reappears {@code emergeDistance} back along the bearing it comes out on, and the exit rift
     * stands ahead of where it appears, so the hull arrives wholly behind it and has to fly out through it -
     * the mirror of the way it went in.
     *
     * <p>For a flight that turns the hull, the offset from its origin to its centre turns with it: it is a
     * vector in the world, measured at the mooring, and at the far end the hull lies another way. Carried
     * across unturned, a turned hull's rift stands off to one side of it by however far its origin is from
     * its middle. With no turn, nothing here is turned at all, so every flight that is not into a berth comes
     * out to the last bit as it always did.
     *
     * @param originToCentre from the hull's pose origin to its centre, in the world, as it lies at its mooring
     * @param exitHeading    the unit vector the hull travels along as it comes out
     */
    public static FarEnd farEnd(Vector3dc arrivalOrigin, Vector3dc originToCentre, Quaterniondc departure,
                                Quaterniondc arrival, Vector3dc exitHeading, double emergeDistance,
                                double hullLength, double riftRadius) {
        Vector3d emergeStart = exitHeading.mul(-emergeDistance, new Vector3d()).add(arrivalOrigin);
        Vector3d offset = new Vector3d(originToCentre);
        if (!departure.equals(arrival)) {
            new Quaterniond(arrival).mul(new Quaterniond(departure).conjugate()).normalize().transform(offset);
        }
        Vector3d emergeCentre = emergeStart.add(offset, new Vector3d());
        Vector3d exitCentre = exitHeading
                .mul(hullLength * 0.5D + TRANSIT_MARGIN * 0.5D, new Vector3d())
                .add(emergeCentre);
        return new FarEnd(emergeStart, new Rift(exitCentre, new Vector3d(exitHeading), riftRadius));
    }

    /**
     * A planned flight, or why there could not be one.
     *
     * @param flight  the flight, or {@code null} when refused
     * @param refusal {@link WarpFailure#NONE} with a flight; otherwise the reason to abort with
     */
    public record Plan(@Nullable WarpFlight flight, WarpFailure refusal) {
    }

    /**
     * A hull's flight geometry along a bearing, measured from its plot and orientation and the live
     * config.
     *
     * <p>The one bridge from a live airship to {@link FlightGeometry}. {@link #plan} and
     * {@link #departureShape} both go through here, so a clearance drawn with no course set is sized
     * exactly as the flight that will be flown.
     *
     * @return the geometry, or {@code null} when the hull has no plot to measure
     */
    @Nullable
    public static FlightGeometry geometry(Airship airship, Vector3dc bow, int emergeTicks) {
        // The whole vessel, in the hull's own frame: the hull's plot and every part a warp carries with it.
        // A ship built in sections used to be given a rift the size of the section its drive was in.
        BoundingBox3d vessel = airship.vesselShipBounds();
        if (vessel == null) {
            return null;
        }
        return FlightGeometry.of(
                vessel.maxX() - vessel.minX(),
                vessel.maxY() - vessel.minY(),
                vessel.maxZ() - vessel.minZ(),
                airship.orientation(), bow,
                AWConfig.RIFT_RADIUS_FACTOR.get(),
                AWConfig.RIFT_TRANSIT_TICKS.get(),
                emergeTicks,
                AWConfig.EMERGE_DISTANCE_FACTOR.get());
    }

    /** A hull's geometry, flown on a formation's run when it is going through one rift with one. */
    @Nullable
    private static FlightGeometry geometry(Airship airship, Vector3dc bow, int emergeTicks,
                                           @Nullable FleetPassage passage) {
        FlightGeometry own = geometry(airship, bow, emergeTicks);
        return own == null || passage == null ? own : passage.flownBy(own);
    }

    /**
     * The two departure-corridor dimensions the clearance check needs, worked out without an arrival.
     *
     * @param corridorReach how far ahead of the bow the hull actually flies, from the plane on
     * @param apertureMargin how far past the hull the entry aperture reaches on every side
     */
    public record DepartureShape(double corridorReach, double apertureMargin) {
    }

    /**
     * The departure corridor's length and width for a hull flying along a bearing, with no destination
     * in hand.
     *
     * <p>The same {@link FlightGeometry} {@link #plan} builds, so the same hull span, aperture radius
     * and corridor length a planned flight would produce. A clearance can therefore be measured - and
     * drawn - for a drive that has no course set, which is exactly the case a pilot wants to check
     * before committing to one.
     *
     * @return the shape, or {@code null} when the hull has no plot to measure
     */
    @Nullable
    public static DepartureShape departureShape(Airship airship, Vector3dc bow) {
        // The emerge ticks only shape the far end, which a departure never reaches.
        FlightGeometry geometry = geometry(airship, bow, 1);
        if (geometry == null) {
            return null;
        }
        // Mirrors corridorReach(): the passage, plus whatever drift is configured.
        return new DepartureShape(geometry.transitRun() + corridorDrift(), geometry.apertureMargin());
    }

    /**
     * The bow, as a direction in the airship's own frame.
     *
     * <p>The pilot's setting is a quarter turn from the drive's front face, and the drive's block
     * facing is read out of the sub-level, so this whole calculation stays in ship space. It is only
     * turned into a world bearing at the moment a flight is planned, which is what stops a warp from
     * firing sideways after the vessel has come about.
     *
     * <p>A drive mounted on a vertical shaft has no front face to speak of, so the ship's own north
     * stands in as the reference. The needle on the machine reads from the same rule, so what it
     * shows is still what the drive will do.
     *
     * @param heading     the pilot's setting
     * @param driveFacing the drive's block facing, in ship space
     */
    public static Direction shipSpaceBow(DriveHeading heading, Direction driveFacing) {
        Direction reference = driveFacing.getAxis().isHorizontal() ? driveFacing : Direction.NORTH;
        return heading.apply(reference);
    }

    /**
     * That ship-space bow as a world-space bearing: horizontal, and unit length.
     *
     * @param airship the vessel, whose pose supplies the ship-to-world rotation
     * @param shipBow the bow in ship space, from {@link #shipSpaceBow}
     */
    public static Vector3d worldBow(Airship airship, Direction shipBow) {
        Vector3d world = airship.toWorldDirection(facingVector(shipBow));
        world.y = 0.0D;
        if (world.lengthSquared() < 1.0e-6D) {
            // The hull is stood on end, so its bow points at the sky. Fall back to the unrotated
            // axis: arbitrary, but bounded, and a vessel in that attitude has larger problems.
            return new Vector3d(shipBow.getStepX(), 0.0D, shipBow.getStepZ());
        }
        return world.normalize();
    }

    /** Blocks of overshoot on a passage, so the stern ends up inside the aperture rather than in it. */
    private static final double TRANSIT_MARGIN = FlightGeometry.TRANSIT_MARGIN;

    /** Blocks over which the run at an aperture eases down to the speed it will pass through at. */
    private static final double BRAKE_DISTANCE = 12.0D;

    /**
     * Fraction of a throat that is full width before it starts closing.
     *
     * <p>Must match the renderer's taper. It is here rather than there because the depth has to be
     * chosen against it, and the server is what decides the depth.
     */
    public static final double THROAT_FULL_WIDTH = 0.82D;

    public static Vector3d facingVector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    // ----------------------------------------------------------------- flight

    public Stage stage() {
        return stage;
    }

    public int stageTicks() {
        return stageTicks;
    }

    public Rift entryRift() {
        return entry;
    }

    public Rift exitRift() {
        return exit;
    }

    public Vector3dc arrivalOrigin() {
        return arrivalOrigin;
    }

    /** The hull's extent along the bearing, which is what the apertures are sized and timed against. */
    public double hullSpan() {
        return hullSpan;
    }

    /** The hull's horizontal extent across the bearing. */
    public double crossSpan() {
        return crossSpan;
    }

    /**
     * Where the hull's centre is when it reappears behind the exit aperture.
     *
     * <p>Worked back from the exit rift, which {@link #plan} stood exactly half a hull and half the
     * passage margin ahead of that centre. The chunk claims want centres rather than pose origins: on a
     * large build the two can be far enough apart to put the hull in a different chunk from its origin.
     */
    public Vector3d emergenceCentre() {
        return exit.normal().mul(-(hullSpan * 0.5D + TRANSIT_MARGIN * 0.5D), new Vector3d()).add(exit.centre());
    }

    /** Where the hull's centre comes to rest, the same offset from its origin as at the reappearance. */
    public Vector3d arrivalCentre() {
        return emergenceCentre().add(arrivalOrigin).sub(emergeStart);
    }

    /** How far the hull travels to be swallowed whole, bow to stern. */
    public double transitRun() {
        return transitRun;
    }

    /** Blocks per tick the hull travels from the moment it reaches an aperture until it is clear. */
    public double transitSpeed() {
        return transitRun / transitTicks;
    }

    /**
     * How far ahead of its current pose the hull actually flies in real world space before the single
     * teleport carries it across - the run in, the passage, and the corridor run, which is flown
     * inside the entry throat rather than anywhere else. This is the length of the departure path that
     * must be proven clear (phase 1) and kept chunk-resident (phase 3).
     */
    public double corridorReach() {
        return transitRun + corridorDrift;
    }

    /**
     * The configured corridor drift: how far the corridor run carries the hull once the aperture has
     * swallowed it, zero by default.
     *
     * <p>The corridor used to run for its whole duration at passage speed, so the reach grew as
     * {@code hullLength x (1 + corridorTicks / transitTicks)} - two and a half ship-lengths of real
     * world space on a long hull, every block of which had to be proven clear, kept chunk-resident
     * and hidden behind a deeper throat. None of it was observable: the hull is inside the throat for
     * the entire phase. Holding still costs nothing and the corridor still lasts exactly as long.
     */
    public static double corridorDrift() {
        try {
            return Math.max(0.0D, AWConfig.CORRIDOR_DRIFT.get());
        } catch (IllegalStateException e) {
            return 0.0D; // config not loaded - unit tests
        }
    }

    /** Blocks per tick during the corridor run: the drift spread evenly over the phase. */
    public double corridorSpeed() {
        return corridorDrift / Math.max(1, corridorTicks);
    }

    /** How far the hull coasts from the exit aperture to its resting place, along the bearing. */
    public double emergeRunOut() {
        return arrivalOrigin.distance(emergeStart);
    }

    /**
     * How far past the hull's own footprint the apertures reach, so the departure and arrival
     * clearance checks prove the aperture opening and throat clear, not just the hull's box.
     */
    public double apertureMargin() {
        return SafeArrival.apertureMargin(entry.radius(), hullRadius);
    }

    /**
     * How deep the entry aperture's throat has to run to keep a hull out of sight inside it.
     *
     * <p>A flat aperture only hides what is directly behind it, so a hull halfway through one is
     * plainly visible to anyone standing off to the side. The throat is the aperture given depth: a
     * closed tube behind the mouth, with room past the hull's deepest point for the far end to taper
     * shut rather than ending in a visible disc.
     *
     * <p>This one also has to hold the hull for the corridor run, which happens inside it rather than
     * a thousand blocks up, so it is as long as the passage plus everything flown after it.
     */
    public double entryThroatDepth() {
        return throatFor(corridorReach());
    }

    /** The far aperture's throat, which only has to hide the hull while it flies back out. */
    public double exitThroatDepth() {
        return throatFor(transitRun);
    }

    /**
     * A throat deep enough that its far end tapers shut well beyond anything it is hiding.
     *
     * <p>The margin is on the <em>whole</em> reach, not just the passage. Scaling only the passage
     * left the spare fixed while the corridor run grew, and the hull's bow ended up inside the closing
     * cone at the end of a long corridor - a gap in the one surface whose entire job is not to have
     * one. Sizing it this way keeps the taper clear of the hull whatever the corridor is set to.
     *
     * @param reach how far the deepest part of the hull gets from the mouth
     */
    public static double throatFor(double reach) {
        return reach / THROAT_FULL_WIDTH * 1.05D;
    }

    public int corridorTicks() {
        return corridorTicks;
    }

    public int emergeTicks() {
        return emergeTicks;
    }

    /** The velocity the flight commanded on its most recent tick, in blocks per tick. */
    public Vector3dc lastCommanded() {
        return lastCommanded;
    }

    /**
     * Commands the hull's velocity and remembers what was asked for.
     *
     * <p>Every stage goes through here rather than calling {@link Airship#driveVelocity} directly, so
     * the commanded figure is captured for the trace that catches a yeet - the reported velocity
     * diverging from the commanded one is the clearest sign the solver ejected the hull.
     */
    private void command(Airship airship, Vector3dc blocksPerTick) {
        lastCommanded.set(blocksPerTick);
        airship.driveVelocity(blocksPerTick);
    }

    /** 0..1 progress through the current stage, for effects. */
    public float stageProgress() {
        int total = switch (stage) {
            case APPROACH -> approachLimitTicks;
            case TRANSIT, BREACH -> transitTicks;
            case CORRIDOR -> corridorTicks;
            case EMERGE -> emergeTicks;
        };
        return Math.min(1.0F, stageTicks / (float) Math.max(1, total));
    }

    /** What a tick of the flight asks the caller to do next. */
    public enum Step {
        /** Still flying this stage. */
        CONTINUE,
        /** The bow has reached the entry aperture and the hull is starting to go through. */
        BEGIN_TRANSIT,
        /** The aperture has swallowed the hull whole; move it into the corridor. */
        ENTER_CORRIDOR,
        /** The corridor run finished; move the hull behind the exit aperture. */
        EXIT_CORRIDOR,
        /** The hull is clear of the exit aperture and back in the world. */
        EMERGED,
        /** The hull has come to rest at the destination. */
        ARRIVED
    }

    /**
     * Advances the flight by one tick, commanding the airship's velocity.
     *
     * @return what the caller should do about the result
     */
    public Step tick(Airship airship) {
        stageTicks++;
        return switch (stage) {
            case APPROACH -> tickApproach(airship);
            case TRANSIT -> tickTransit(airship);
            case CORRIDOR -> tickCorridor(airship);
            case BREACH -> tickBreach(airship);
            case EMERGE -> tickEmerge(airship);
        };
    }

    /**
     * Flies this flight's run at the rift in step with the rest of its formation: held still until the
     * fleet sets off, then run the passage's distance by reckoning rather than to a plane - see
     * {@link FleetPassage} for why both.
     *
     * @param now the game time the flight was planned at
     */
    public void keepStep(FleetPassage passage, long now) {
        stepWait = (int) Math.max(0L, Math.min(Integer.MAX_VALUE / 2, passage.startTick() - now));
        stepRun = Math.max(0.0D, passage.approach());
        stepFlown = 0.0D;
    }

    /** Ticks this flight holds still before its run at the rift, keeping step with a formation. */
    public int stepWait() {
        return stepWait;
    }

    private Step tickApproach(Airship airship) {
        if (stepRun > 0.0D) {
            return tickApproachInStep(airship);
        }
        // The vessel's centre, as the rift was placed against it - see build().
        Vector3d centre = airship.vesselCentre(new Vector3d());
        Vector3d toRift = entry.centre().sub(centre, new Vector3d());
        double alongNormal = toRift.dot(entry.normal());

        // The run ends when the *bow* touches the plane, not the centre. Ending it at the centre is
        // what made a ship look like it vanished on contact: half of it never reached the aperture.
        double toPlane = alongNormal - hullSpan * 0.5D;
        if (toPlane <= 0.0D || stageTicks >= approachLimitTicks) {
            return Step.BEGIN_TRANSIT;
        }

        double target = approachSpeed(toPlane, AWConfig.APPROACH_SPEED.get(), transitSpeed());
        // Ease off the mooring too, so the departure is not a jolt either.
        double ramp = Math.min(1.0D, stageTicks / 10.0D);
        command(airship, entry.normal().mul(target * ramp, new Vector3d()));
        return Step.CONTINUE;
    }

    /**
     * The same run, in step: the same speeds on the same ticks as every other ship in the formation, off
     * the distance flown by reckoning rather than the distance left to a plane.
     */
    private Step tickApproachInStep(Airship airship) {
        if (stageTicks <= stepWait) {
            // Held rather than let go, so the formation that sets off is the one the passage was planned
            // from, and not one a pilot has nudged in the meantime.
            command(airship, new Vector3d());
            return Step.CONTINUE;
        }
        int running = stageTicks - stepWait;
        double toPlane = stepRun - stepFlown;
        if (toPlane <= 0.0D || running >= approachLimitTicks) {
            return Step.BEGIN_TRANSIT;
        }
        double speed = approachSpeed(toPlane, AWConfig.APPROACH_SPEED.get(), transitSpeed())
                * Math.min(1.0D, running / 10.0D);
        stepFlown += speed;
        command(airship, entry.normal().mul(speed, new Vector3d()));
        return Step.CONTINUE;
    }

    /**
     * How fast to run at an aperture from a given distance out.
     *
     * <p>Cruising speed until the last stretch, then easing down so the bow reaches the plane doing
     * exactly the speed it will pass through at. Running at a rift and dropping to a crawl the instant
     * it touches is a lurch you feel from the deck, and it is the one speed change in the whole warp
     * that nothing is hiding.
     *
     * @param toPlane how far the bow still is from the aperture, in blocks
     * @param cruise  the configured approach speed, in blocks per tick
     * @param passage the speed the hull travels at once it is inside, in blocks per tick
     */
    public static double approachSpeed(double toPlane, double cruise, double passage) {
        double brake = Mth.clamp(toPlane / BRAKE_DISTANCE, 0.0D, 1.0D);
        return passage + (cruise - passage) * brake;
    }

    /**
     * The hull being drawn through the entry aperture.
     *
     * <p>Timed rather than measured. A position test here would stall the whole warp if the hull
     * fouled something on its way in; on a clock the aperture closes over it on schedule either way,
     * and the speed comes from the hull's own length so a large ship takes no longer than a small one.
     */
    private Step tickTransit(Airship airship) {
        if (stageTicks >= transitTicks) {
            return Step.ENTER_CORRIDOR;
        }
        command(airship, entry.normal().mul(transitRun / transitTicks, new Vector3d()));
        return Step.CONTINUE;
    }

    /**
     * The corridor run, flown down the inside of the entry aperture's throat.
     *
     * <p>The hull does not go anywhere in particular - a fold in space is not a distance - so it
     * simply keeps going the way it was already going, at the speed it was already doing. What sells
     * the journey is what the crew sees, and that is a screen effect. Flying it here rather than a
     * thousand blocks up means no second teleport, no second region of the world to stream in, and no
     * moment where the ship's motion changes.
     */
    private Step tickCorridor(Airship airship) {
        if (stageTicks >= corridorTicks) {
            return Step.EXIT_CORRIDOR;
        }
        // At the default drift of zero this commands a standstill, which is still a command: the
        // velocity is re-set every tick, so gravity, lift and the ship's own thrust stay overridden
        // exactly as they are in every other stage.
        command(airship, entry.normal().mul(corridorSpeed(), new Vector3d()));
        return Step.CONTINUE;
    }

    /** The hull coming back out of the exit aperture, at the pace it went into the first one. */
    private Step tickBreach(Airship airship) {
        if (stageTicks >= transitTicks) {
            return Step.EMERGED;
        }
        command(airship, exit.normal().mul(transitRun / transitTicks, new Vector3d()));
        return Step.CONTINUE;
    }

    private Step tickEmerge(Airship airship) {
        if (stageTicks >= emergeTicks) {
            // No parting command to stop: the profile below has already bled the speed away, and
            // ordering a halt on the last tick is exactly the dead stop this is meant to avoid.
            return Step.ARRIVED;
        }
        // Aiming at the hull's actual position rather than a precomputed run keeps this honest if
        // physics nudged the ship on its way out of the aperture.
        Vector3d remaining = arrivalOrigin.sub(airship.position(), new Vector3d());
        command(airship, remaining.mul(emergeFraction(emergeTicks - stageTicks)));
        return Step.CONTINUE;
    }

    /**
     * What fraction of the remaining distance to cover on a tick of the run out.
     *
     * <p>Twice its even share, which makes every step shorter than the last and still lands exactly on
     * the mark - on the final tick the fraction reaches one and takes whatever is left. A flat
     * {@code 1 / ticksLeft} would hold a constant speed and then stop dead, which is the thing being
     * avoided: a ship should settle, not halt.
     *
     * <p>Sizing the run out at {@code passage x ticks / 2} back in {@link #plan} is what makes the
     * first of these steps come out at passage speed, so the ship leaves the aperture at exactly the
     * speed it was doing inside it.
     */
    public static double emergeFraction(int ticksLeft) {
        return 2.0D / (Math.max(1, ticksLeft) + 1);
    }

    /** Called by the drive when the bow reaches the entry aperture. */
    public void beganTransit() {
        stage = Stage.TRANSIT;
        stageTicks = 0;
    }

    /** Called by the drive once it has moved the hull into the corridor. */
    public void enteredCorridor() {
        stage = Stage.CORRIDOR;
        stageTicks = 0;
    }

    /** Called by the drive once it has moved the hull behind the exit rift. */
    public void leftCorridor() {
        stage = Stage.BREACH;
        stageTicks = 0;
    }

    /** Called by the drive once the hull is clear of the exit aperture. */
    public void breached() {
        stage = Stage.EMERGE;
        stageTicks = 0;
    }

    /** Ticks a hull spends inside an aperture at either end, for phase timing. */
    public int transitTicks() {
        return transitTicks;
    }

    /** Where the hull should be placed when it enters the corridor. */
    /** Where the hull should be placed when it leaves the corridor. */
    public Vector3dc emergenceOrigin() {
        return emergeStart;
    }

    public Quaterniondc arrivalOrientation() {
        return arrivalOrientation;
    }

    public Quaterniondc departureOrientation() {
        return departureOrientation;
    }

    /** Whether this flight turns the hull in the fold - only one into a Berth does. */
    public boolean turns() {
        return !departureOrientation.equals(arrivalOrientation);
    }

    /**
     * The attitude the hull is held at just now: the one it left at until the crossing, the one it will rest
     * at after. Ask this, never {@link #arrivalOrientation}, for anything that holds a hull's attitude through
     * a flight - held at its arrival attitude from the start, a turned flight spins the ship round at its mooring.
     */
    public Quaterniondc attitude() {
        return attitude(stage, departureOrientation, arrivalOrientation);
    }

    /** {@link #attitude()}, as a rule: the crossing is the step from the corridor to the breach. */
    public static Quaterniondc attitude(Stage stage, Quaterniondc departure, Quaterniondc arrival) {
        return switch (stage) {
            case APPROACH, TRANSIT, CORRIDOR -> departure;
            case BREACH, EMERGE -> arrival;
        };
    }

    // -------------------------------------------------------------------- nbt

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Stage", stage.name());
        tag.putInt("StageTicks", stageTicks);
        tag.put("Entry", entry.save());
        tag.put("Exit", exit.save());
        putVec(tag, "Arrival", arrivalOrigin);
        putVec(tag, "EmergeStart", emergeStart);
        tag.putDouble("OrientX", arrivalOrientation.x);
        tag.putDouble("OrientY", arrivalOrientation.y);
        tag.putDouble("OrientZ", arrivalOrientation.z);
        tag.putDouble("OrientW", arrivalOrientation.w);
        if (turns()) {
            tag.putDouble("DepartX", departureOrientation.x);
            tag.putDouble("DepartY", departureOrientation.y);
            tag.putDouble("DepartZ", departureOrientation.z);
            tag.putDouble("DepartW", departureOrientation.w);
        }
        tag.putDouble("TransitRun", transitRun);
        tag.putDouble("HullSpan", hullSpan);
        tag.putDouble("HullRadius", hullRadius);
        tag.putDouble("CrossSpan", crossSpan);
        tag.putDouble("CorridorDrift", corridorDrift);
        tag.putInt("TransitTicks", transitTicks);
        tag.putInt("CorridorTicks", corridorTicks);
        tag.putInt("EmergeTicks", emergeTicks);
        tag.putInt("ApproachLimit", approachLimitTicks);
        if (stepRun > 0.0D) {
            tag.putInt("StepWait", stepWait);
            tag.putDouble("StepRun", stepRun);
            tag.putDouble("StepFlown", stepFlown);
        }
        return tag;
    }

    @Nullable
    public static WarpFlight load(CompoundTag tag) {
        if (!tag.contains("Entry")) {
            return null;
        }
        Quaterniond arrival = new Quaterniond(tag.getDouble("OrientX"), tag.getDouble("OrientY"),
                tag.getDouble("OrientZ"), tag.getDouble("OrientW"));
        // Absent on every flight that turns nothing, which is every flight saved before one could: it left
        // at the attitude it arrives at.
        Quaterniond departure = tag.contains("DepartW")
                ? new Quaterniond(tag.getDouble("DepartX"), tag.getDouble("DepartY"), tag.getDouble("DepartZ"),
                        tag.getDouble("DepartW"))
                : new Quaterniond(arrival);
        WarpFlight flight = new WarpFlight(
                Rift.load(tag.getCompound("Entry")),
                Rift.load(tag.getCompound("Exit")),
                readVec(tag, "Arrival"),
                arrival,
                departure,
                readVec(tag, "EmergeStart"),
                tag.getDouble("TransitRun"),
                tag.getDouble("HullSpan"),
                tag.getDouble("HullRadius"),
                // A flight saved before the cross-section was kept: the aperture's own diameter is a
                // bound on it, generous rather than short.
                tag.contains("CrossSpan") ? tag.getDouble("CrossSpan") : tag.getDouble("HullRadius") * 2.0D,
                tag.getDouble("CorridorDrift"),
                Math.max(1, tag.getInt("TransitTicks")),
                tag.getInt("CorridorTicks"),
                tag.getInt("EmergeTicks"),
                tag.getInt("ApproachLimit"));
        try {
            flight.stage = Stage.valueOf(tag.getString("Stage"));
        } catch (IllegalArgumentException e) {
            flight.stage = Stage.APPROACH;
        }
        flight.stageTicks = tag.getInt("StageTicks");
        flight.stepWait = tag.getInt("StepWait");
        flight.stepRun = tag.getDouble("StepRun");
        flight.stepFlown = tag.getDouble("StepFlown");
        return flight;
    }

    private static void putVec(CompoundTag tag, String key, Vector3dc vec) {
        CompoundTag inner = new CompoundTag();
        inner.putDouble("x", vec.x());
        inner.putDouble("y", vec.y());
        inner.putDouble("z", vec.z());
        tag.put(key, inner);
    }

    private static Vector3d readVec(CompoundTag tag, String key) {
        CompoundTag inner = tag.getCompound(key);
        return new Vector3d(inner.getDouble("x"), inner.getDouble("y"), inner.getDouble("z"));
    }
}
