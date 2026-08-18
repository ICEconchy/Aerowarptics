package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * and drives the hull at it, the hull crosses the threshold and comes out in the warp corridor high
 * above the world, it runs the corridor for a few seconds, and then a second rift puts it down at the
 * destination still moving, so it coasts out under its own momentum.
 *
 * <pre>
 *          APPROACH                 CORRIDOR                    EMERGE
 *   ┌────┐                      ╔══════════════╗                        ┌────┐
 *   │ship│──▶──▶──▶ ((entry))   ║ ▶▶▶ ship ▶▶▶ ║   ((exit)) ──▶──▶ ship │ rest│
 *   └────┘         at the bow   ╚══════════════╝   at the far end       └────┘
 *                                  y = corridorAltitude
 * </pre>
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
        /** In the corridor, high above the world. */
        CORRIDOR,
        /** Coasting out of the exit rift towards its resting place. */
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
    private final Vector3d corridorStart;
    private final Vector3d corridorEnd;
    private final Vector3d arrivalOrigin;
    private final Quaterniond arrivalOrientation;
    private final Vector3d emergeStart;
    private final int corridorTicks;
    private final int emergeTicks;
    private final int approachLimitTicks;

    private Stage stage = Stage.APPROACH;
    private int stageTicks;

    private WarpFlight(Rift entry, Rift exit, Vector3d corridorStart, Vector3d corridorEnd,
                       Vector3d arrivalOrigin, Quaterniond arrivalOrientation, Vector3d emergeStart,
                       int corridorTicks, int emergeTicks, int approachLimitTicks) {
        this.entry = entry;
        this.exit = exit;
        this.corridorStart = corridorStart;
        this.corridorEnd = corridorEnd;
        this.arrivalOrigin = arrivalOrigin;
        this.arrivalOrientation = arrivalOrientation;
        this.emergeStart = emergeStart;
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

        // The exit run: the hull comes out of the far rift already moving and coasts to a stop.
        double emergeDistance = Math.max(8.0D, hullLength * AWConfig.EMERGE_DISTANCE_FACTOR.get());
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

        // The exit rift sits where the hull first appears, facing the way it will travel.
        Rift exit = new Rift(new Vector3d(emergeStart), new Vector3d(heading), riftRadius);

        // The corridor runs from above the departure point along the bearing to the destination.
        double altitude = corridorAltitude(destination);
        int corridorRun = Math.max(1, corridorTicks);
        double corridorSpeed = AWConfig.CORRIDOR_SPEED.get();
        Vector3d bearing = new Vector3d(
                arrivalOrigin.x - hullCentre.x, 0.0D, arrivalOrigin.z - hullCentre.z);
        if (bearing.lengthSquared() < 1.0e-6D) {
            bearing.set(heading.x, 0.0D, heading.z);
        }
        if (bearing.lengthSquared() < 1.0e-6D) {
            bearing.set(0.0D, 0.0D, 1.0D);
        }
        bearing.normalize();

        Vector3d corridorStart = new Vector3d(hullCentre.x, altitude, hullCentre.z);
        Vector3d corridorEnd = bearing.mul(corridorSpeed * corridorRun, new Vector3d()).add(corridorStart);

        return new WarpFlight(entry, exit, corridorStart, corridorEnd, arrivalOrigin, orientation,
                emergeStart, corridorRun, Math.max(1, emergeTicks),
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

    /** A lane well above anything built, but comfortably inside the range Sable keeps sub-levels in. */
    private static double corridorAltitude(ServerLevel level) {
        double configured = AWConfig.CORRIDOR_ALTITUDE.get();
        return Math.max(level.getMaxBuildHeight() + 128.0D, configured);
    }

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

    public Vector3dc corridorStart() {
        return corridorStart;
    }

    /** 0..1 progress through the current stage, for effects. */
    public float stageProgress() {
        int total = switch (stage) {
            case APPROACH -> approachLimitTicks;
            case CORRIDOR -> corridorTicks;
            case EMERGE -> emergeTicks;
        };
        return Math.min(1.0F, stageTicks / (float) Math.max(1, total));
    }

    /** What a tick of the flight asks the caller to do next. */
    public enum Step {
        /** Still flying this stage. */
        CONTINUE,
        /** The hull reached the entry rift; move it into the corridor. */
        ENTER_CORRIDOR,
        /** The corridor run finished; move the hull to the exit rift. */
        EXIT_CORRIDOR,
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
            case CORRIDOR -> tickCorridor(airship);
            case EMERGE -> tickEmerge(airship);
        };
    }

    private Step tickApproach(Airship airship) {
        Vector3d centre = airship.worldBounds().center(new Vector3d());
        Vector3d toRift = entry.centre().sub(centre, new Vector3d());
        double alongNormal = toRift.dot(entry.normal());

        // The hull is through once its centre passes the plane of the aperture.
        if (alongNormal <= 0.0D || stageTicks >= approachLimitTicks) {
            return Step.ENTER_CORRIDOR;
        }

        double speed = AWConfig.APPROACH_SPEED.get();
        // Ease in over the first second so the ship leans into the run rather than snapping to speed.
        double ramp = Math.min(1.0D, stageTicks / 20.0D);
        airship.driveVelocity(entry.normal().mul(speed * ramp, new Vector3d()));
        return Step.CONTINUE;
    }

    private Step tickCorridor(Airship airship) {
        if (stageTicks >= corridorTicks) {
            return Step.EXIT_CORRIDOR;
        }
        Vector3d along = corridorEnd.sub(corridorStart, new Vector3d());
        double length = along.length();
        if (length < 1.0e-6D) {
            return Step.EXIT_CORRIDOR;
        }
        along.div(length);
        airship.driveVelocity(along.mul(length / corridorTicks, new Vector3d()));
        return Step.CONTINUE;
    }

    private Step tickEmerge(Airship airship) {
        if (stageTicks >= emergeTicks) {
            airship.driveVelocity(new Vector3d());
            return Step.ARRIVED;
        }
        // Bleed the exit speed away smoothly so the hull settles rather than stopping dead.
        Vector3d travel = arrivalOrigin.sub(emergeStart, new Vector3d());
        double remaining = 1.0D - stageTicks / (double) emergeTicks;
        airship.driveVelocity(travel.mul(remaining * 2.0D / emergeTicks, new Vector3d()));
        return Step.CONTINUE;
    }

    /** Called by the drive once it has moved the hull into the corridor. */
    public void enteredCorridor() {
        stage = Stage.CORRIDOR;
        stageTicks = 0;
    }

    /** Called by the drive once it has moved the hull to the exit rift. */
    public void leftCorridor() {
        stage = Stage.EMERGE;
        stageTicks = 0;
    }

    /** Where the hull should be placed when it enters the corridor. */
    public Vector3dc corridorEntryOrigin(Airship airship) {
        // Keep the hull's centre on the lane, allowing for the pose origin not being the centre.
        Vector3d centre = airship.worldBounds().center(new Vector3d());
        Vector3d offset = new Vector3d(airship.position()).sub(centre);
        return corridorStart.add(offset, new Vector3d());
    }

    /** Where the hull should be placed when it leaves the corridor. */
    public Vector3dc emergenceOrigin() {
        return emergeStart;
    }

    public Quaterniondc arrivalOrientation() {
        return arrivalOrientation;
    }

    /** The volume the hull will sweep through in the corridor, for a pre-flight clearance test. */
    public BoundingBox3dc corridorVolume(Airship airship) {
        BoundingBox3dc hull = airship.worldBounds();
        Vector3d size = hull.size(new Vector3d());
        BoundingBox3d volume = new BoundingBox3d(
                Math.min(corridorStart.x, corridorEnd.x) - size.x,
                corridorStart.y - size.y,
                Math.min(corridorStart.z, corridorEnd.z) - size.z,
                Math.max(corridorStart.x, corridorEnd.x) + size.x,
                corridorStart.y + size.y,
                Math.max(corridorStart.z, corridorEnd.z) + size.z);
        return volume;
    }

    // -------------------------------------------------------------------- nbt

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Stage", stage.name());
        tag.putInt("StageTicks", stageTicks);
        tag.put("Entry", entry.save());
        tag.put("Exit", exit.save());
        putVec(tag, "CorridorStart", corridorStart);
        putVec(tag, "CorridorEnd", corridorEnd);
        putVec(tag, "Arrival", arrivalOrigin);
        putVec(tag, "EmergeStart", emergeStart);
        tag.putDouble("OrientX", arrivalOrientation.x);
        tag.putDouble("OrientY", arrivalOrientation.y);
        tag.putDouble("OrientZ", arrivalOrientation.z);
        tag.putDouble("OrientW", arrivalOrientation.w);
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
                readVec(tag, "CorridorStart"),
                readVec(tag, "CorridorEnd"),
                readVec(tag, "Arrival"),
                new Quaterniond(tag.getDouble("OrientX"), tag.getDouble("OrientY"),
                        tag.getDouble("OrientZ"), tag.getDouble("OrientW")),
                readVec(tag, "EmergeStart"),
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
