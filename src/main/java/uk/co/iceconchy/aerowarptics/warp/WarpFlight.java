package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
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

/**
 * The airship's journey through the rift, from the moment it starts its run to the moment it comes to
 * rest at the far end.
 *
 * <p>A warp is flown, not cut to. The drive opens a rift ahead of the bow, the server takes the helm
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
     * @param radius aperture radius, scaled to the hull that has to fit through it
     */
    public record Rift(Vector3d centre, Vector3d normal, double radius) {

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("cx", centre.x);
            tag.putDouble("cy", centre.y);
            tag.putDouble("cz", centre.z);
            tag.putDouble("nx", normal.x);
            tag.putDouble("ny", normal.y);
            tag.putDouble("nz", normal.z);
            tag.putDouble("r", radius);
            return tag;
        }

        public static Rift load(CompoundTag tag) {
            return new Rift(
                    new Vector3d(tag.getDouble("cx"), tag.getDouble("cy"), tag.getDouble("cz")),
                    new Vector3d(tag.getDouble("nx"), tag.getDouble("ny"), tag.getDouble("nz")),
                    tag.getDouble("r"));
        }
    }

    private final Rift entry;
    private final Rift exit;
    private final Vector3d arrivalOrigin;
    private final Quaterniond arrivalOrientation;
    private final Vector3d emergeStart;
    /** How far the hull must travel for the aperture to swallow it whole, bow to stern. */
    private final double transitRun;
    /** The hull's own extent along the bearing, which sets when the bow reaches the plane. */
    private final double hullSpan;
    private final int transitTicks;
    private final int corridorTicks;
    private final int emergeTicks;
    private final int approachLimitTicks;

    private Stage stage = Stage.APPROACH;
    private int stageTicks;

    private WarpFlight(Rift entry, Rift exit,
                       Vector3d arrivalOrigin, Quaterniond arrivalOrientation, Vector3d emergeStart,
                       double transitRun, double hullSpan, int transitTicks,
                       int corridorTicks, int emergeTicks, int approachLimitTicks) {
        this.entry = entry;
        this.exit = exit;
        this.arrivalOrigin = arrivalOrigin;
        this.arrivalOrientation = arrivalOrientation;
        this.emergeStart = emergeStart;
        this.transitRun = transitRun;
        this.hullSpan = hullSpan;
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
     * @return the planned flight, or {@code null} when no safe arrival exists
     */
    @Nullable
    public static WarpFlight plan(Airship airship, ServerLevel destination, BlockPos anchorPos,
                                  Vector3dc bowDirection, int corridorTicks, int emergeTicks) {
        Quaterniond orientation = new Quaterniond(airship.orientation());
        BoundingBox3dc hull = airship.worldBounds();

        double hullLength = Math.max(hull.width(), hull.length());
        double hullRadius = Math.max(4.0D, Math.sqrt(hull.width() * hull.width() + hull.height() * hull.height()) * 0.5D);
        double riftRadius = hullRadius * AWConfig.RIFT_RADIUS_FACTOR.get();

        Vector3d heading = new Vector3d(bowDirection);

        // Bow to stern, plus enough that the stern is properly inside rather than grazing the plane.
        double transitRun = hullLength + TRANSIT_MARGIN;
        int transitTicks = Math.max(5, AWConfig.RIFT_TRANSIT_TICKS.get());
        int emergeRun = Math.max(1, emergeTicks);
        double passage = transitRun / transitTicks;

        // The run out has to fly the hull clear of the aperture and then bleed that same speed away
        // to nothing. Sizing it from the passage speed is what lets the ship leave the throat and
        // come to rest without ever changing pace: a shorter run would need a sudden brake, a longer
        // one a sudden shove. The configured multiple is a floor under that, not the whole story.
        double settle = passage * emergeRun * 0.5D;
        double emergeDistance = Math.max(8.0D,
                Math.max(hullLength * AWConfig.EMERGE_DISTANCE_FACTOR.get(), transitRun + settle));
        SafeArrival.Result arrival = SafeArrival.find(airship, destination, anchorPos, orientation,
                heading, emergeDistance);
        if (arrival == null) {
            return null;
        }

        Vector3d arrivalOrigin = new Vector3d(arrival.origin());
        Vector3d emergeStart = heading.mul(-emergeDistance, new Vector3d()).add(arrivalOrigin);

        // The entry rift stands off the bow far enough that the hull has room to build up speed.
        Vector3d hullCentre = hull.center(new Vector3d());
        double lead = hullLength * 0.5D + AWConfig.RIFT_LEAD_DISTANCE.get();
        Rift entry = new Rift(heading.mul(lead, new Vector3d()).add(hullCentre), new Vector3d(heading), riftRadius);

        // An aperture is placed against the hull's centre, but a flight is planned in pose origins,
        // and on most builds those are not the same point. Carry the offset across so the far rift
        // ends up where the hull actually will be rather than where its origin is.
        Vector3d originToCentre = hullCentre.sub(airship.position(), new Vector3d());
        Vector3d emergeCentre = emergeStart.add(originToCentre, new Vector3d());

        // The exit rift stands ahead of where the hull appears, so the hull arrives wholly behind it
        // and has to fly out through it - the mirror of the way it went in.
        Vector3d exitCentre = heading
                .mul(hullLength * 0.5D + TRANSIT_MARGIN * 0.5D, new Vector3d())
                .add(emergeCentre);
        Rift exit = new Rift(exitCentre, new Vector3d(heading), riftRadius);

        return new WarpFlight(entry, exit, arrivalOrigin, orientation,
                emergeStart, transitRun, hullLength, transitTicks, Math.max(1, corridorTicks), emergeRun,
                Math.max(20, AWConfig.APPROACH_LIMIT_TICKS.get()));
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
    private static final double TRANSIT_MARGIN = 4.0D;

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

    /** How far the hull travels to be swallowed whole, bow to stern. */
    public double transitRun() {
        return transitRun;
    }

    /** Blocks per tick the hull travels from the moment it reaches an aperture until it is clear. */
    public double transitSpeed() {
        return transitRun / transitTicks;
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
        return throatFor(transitRun + corridorTicks * transitSpeed());
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

    private Step tickApproach(Airship airship) {
        Vector3d centre = airship.worldBounds().center(new Vector3d());
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
        airship.driveVelocity(entry.normal().mul(target * ramp, new Vector3d()));
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
        airship.driveVelocity(entry.normal().mul(transitRun / transitTicks, new Vector3d()));
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
        airship.driveVelocity(entry.normal().mul(transitSpeed(), new Vector3d()));
        return Step.CONTINUE;
    }

    /** The hull coming back out of the exit aperture, at the pace it went into the first one. */
    private Step tickBreach(Airship airship) {
        if (stageTicks >= transitTicks) {
            return Step.EMERGED;
        }
        airship.driveVelocity(exit.normal().mul(transitRun / transitTicks, new Vector3d()));
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
        airship.driveVelocity(remaining.mul(emergeFraction(emergeTicks - stageTicks)));
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
        tag.putDouble("TransitRun", transitRun);
        tag.putDouble("HullSpan", hullSpan);
        tag.putInt("TransitTicks", transitTicks);
        tag.putInt("CorridorTicks", corridorTicks);
        tag.putInt("EmergeTicks", emergeTicks);
        tag.putInt("ApproachLimit", approachLimitTicks);
        return tag;
    }

    @Nullable
    public static WarpFlight load(CompoundTag tag) {
        if (!tag.contains("Entry")) {
            return null;
        }
        WarpFlight flight = new WarpFlight(
                Rift.load(tag.getCompound("Entry")),
                Rift.load(tag.getCompound("Exit")),
                readVec(tag, "Arrival"),
                new Quaterniond(tag.getDouble("OrientX"), tag.getDouble("OrientY"),
                        tag.getDouble("OrientZ"), tag.getDouble("OrientW")),
                readVec(tag, "EmergeStart"),
                tag.getDouble("TransitRun"),
                tag.getDouble("HullSpan"),
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
