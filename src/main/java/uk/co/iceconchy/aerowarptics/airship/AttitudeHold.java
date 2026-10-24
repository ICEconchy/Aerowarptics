package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a hull at the attitude it launched at while a drive is flying it.
 *
 * <h2>Why it tilted</h2>
 * The drive flies a warp by commanding the hull's velocity every tick, and zeroes its spin while it is
 * at it - which reads as though nothing can turn the hull. But Sable calls the drive once a tick and
 * then runs two physics substeps, and in each of them every sail and other lift block on the hull
 * pushes on it with a force proportional to how fast it is going, at that block's own position (see
 * {@code BlockSubLevelLiftProvider.sable$contributeLiftAndDrag}). A warp flies a hull at thirty to a
 * hundred and forty blocks a second, far faster than it ever flies itself, so any lift block that is not
 * balanced by another about the centre of mass puts a real twist on it. Zeroing the spin next tick stops
 * it spinning faster; it does nothing about the angle it has already turned through. So the hull
 * leaned a little further every tick of the run in, was put back level at the crossing - the flight
 * restores its planned attitude there - and then leaned again coming out of the far rift. That is the
 * ship "not keeping its angle".
 *
 * <h2>What this does</h2>
 * After every substep, it puts a held hull back to its attitude, where it rotates about its own centre of
 * mass, and takes away the spin. Its position, and the motion the drive gave it, are left alone. Done
 * after each substep rather than once a tick, the lean never builds up across one, so no pose the server
 * keeps or sends ever has it.
 *
 * <p>A hold is renewed by the flight every tick and lapses a few ticks after the last renewal, so a drive
 * that dies mid-flight cannot pin a hull's attitude for ever - the same principle as the crew manifest.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class AttitudeHold {

    /** How many ticks a hold outlives the last flight tick that renewed it. */
    static final int GRACE_TICKS = 5;

    /**
     * An attitude error smaller than this, in radians, is left alone - about a hundredth of a degree.
     * Correcting it would be a teleport to move a hull by less than it moves in a rounding error.
     */
    private static final double TOLERANCE = 2.0E-4D;

    private static final class Hold {
        private final ResourceKey<Level> dimension;
        private final Quaterniond attitude;
        private int ticksLeft = GRACE_TICKS;

        private Hold(ResourceKey<Level> dimension, Quaterniondc attitude) {
            this.dimension = dimension;
            this.attitude = new Quaterniond(attitude).normalize();
        }
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private AttitudeHold() {
    }

    /** Holds a hull at an attitude for this tick and a few after, or renews the hold if it is already held. */
    public static void hold(Airship airship, Quaterniondc attitude) {
        hold(airship.uuid(), airship.level().dimension(), attitude);
    }

    static void hold(UUID ship, ResourceKey<Level> dimension, Quaterniondc attitude) {
        if (!enabled()) {
            HOLDS.remove(ship);
            return;
        }
        Hold held = HOLDS.get(ship);
        if (held != null && held.dimension.equals(dimension)) {
            held.attitude.set(attitude).normalize();
            held.ticksLeft = GRACE_TICKS;
        } else {
            HOLDS.put(ship, new Hold(dimension, attitude));
        }
    }

    /** Hands the hull's attitude back to its own physics - the flight is over, however it ended. */
    public static void release(UUID ship) {
        HOLDS.remove(ship);
    }

    public static int held() {
        return HOLDS.size();
    }

    /** Counts every hold down a tick, dropping the ones nothing has renewed. Shared with the test. */
    static void expire() {
        HOLDS.values().removeIf(hold -> --hold.ticksLeft <= 0);
    }

    /**
     * The angle, in radians, between two attitudes: how far a hull at {@code current} would have to turn
     * to be at {@code target}. A quaternion and its negation are the same attitude, which is why this is
     * taken from the magnitude of the real part.
     */
    public static double error(Quaterniondc current, Quaterniondc target) {
        Quaterniond difference = new Quaterniond(target).mul(new Quaterniond(current).conjugate()).normalize();
        return 2.0D * Math.acos(Math.min(1.0D, Math.abs(difference.w())));
    }

    private static boolean enabled() {
        try {
            return AWConfig.HOLD_ATTITUDE.get();
        } catch (IllegalStateException e) {
            return true; // config not loaded - unit tests
        }
    }

    @SubscribeEvent
    public static void afterPhysicsStep(ForgeSablePostPhysicsTickEvent event) {
        if (HOLDS.isEmpty()) {
            return;
        }
        SubLevelPhysicsSystem system = event.getPhysicsSystem();
        ServerLevel level = system.getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        for (Map.Entry<UUID, Hold> entry : HOLDS.entrySet()) {
            Hold hold = entry.getValue();
            if (hold.dimension.equals(dimension)
                    && container.getSubLevel(entry.getKey()) instanceof ServerSubLevel ship && !ship.isRemoved()) {
                restore(system, ship, hold.attitude);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!HOLDS.isEmpty()) {
            expire();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HOLDS.clear();
    }

    /**
     * Puts one hull back to its attitude and stops it turning.
     *
     * <p>The linear velocity is read before the teleport and put back after it, whatever the teleport did
     * to it: the drive's commanded motion has to survive into the next substep, or a held hull would fly
     * half as far as it was told to.
     */
    private static void restore(SubLevelPhysicsSystem system, ServerSubLevel ship, Quaterniond attitude) {
        PhysicsPipeline pipeline = system.getPipeline();
        Pose3d pose = ship.logicalPose();
        Vector3d linear = pipeline.getLinearVelocity(ship, new Vector3d());
        if (error(pose.orientation(), attitude) >= TOLERANCE) {
            // About its own centre of mass: a pose's position is where its rotation point is in the world.
            pipeline.teleport(ship, new Vector3d(pose.position()), attitude);
        }
        Vector3d nowLinear = pipeline.getLinearVelocity(ship, new Vector3d());
        Vector3d nowAngular = pipeline.getAngularVelocity(ship, new Vector3d());
        pipeline.addLinearAndAngularVelocity(ship, linear.sub(nowLinear), nowAngular.negate());
        // Brings the pose, and the velocity Sable reports to clients from it, into line with the body.
        system.updatePose(ship);
    }
}
