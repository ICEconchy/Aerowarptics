package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.screen.OrbitCamera;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;

/**
 * The camera that leaves the player's head while they set a Rift Plotter's entry, and circles the machine and the rift
 * it will tear - so the entry is set by looking at where it will be, not by reading three numbers.
 *
 * <p>The game draws from whatever entity it is told is the camera. This hands it one of its own: a {@link Marker},
 * created on the client and never added to the world, moved every tick round an {@link OrbitCamera} and turned to look
 * at the point it circles. The game interpolates an entity's position and facing between ticks, so an eased orbit at
 * twenty ticks a second is drawn smooth. Because the camera is not the player, the player is drawn - standing at their
 * plotter - and their hand is not.
 *
 * <p>For as long as it flies, the view is forced to first person (third person would push the camera back off the
 * marker, out of its orbit) and the HUD is hidden; both are put back exactly as they were. Nothing here is saved or
 * sent: leave the table, change world or die, and the camera is simply the player's again.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class PlotterCameraRig {

    /** How much of the way to where it is wanted the point circled moves each tick. */
    private static final double FOLLOW = 0.2D;

    @Nullable
    private static Marker rig;
    @Nullable
    private static OrbitCamera orbit;
    private static Vec3 focus = Vec3.ZERO;
    private static Vec3 wanted = Vec3.ZERO;
    @Nullable
    private static CameraType previousType;
    private static boolean previousHideGui;
    private static float yaw;

    private PlotterCameraRig() {
    }

    public static boolean flying() {
        return rig != null;
    }

    /**
     * Takes the camera and starts circling {@code centre}: round at {@code yaw} (radians, 0 looking north from the
     * south), up at {@code pitch}, {@code distance} out. Already flying, it only turns towards the new point.
     */
    public static void start(Vec3 centre, float yaw, float pitch, float distance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (rig != null) {
            aim(centre);
            return;
        }
        rig = new Marker(EntityType.MARKER, minecraft.level);
        orbit = new OrbitCamera(yaw, pitch, distance);
        focus = centre;
        wanted = centre;
        previousType = minecraft.options.getCameraType();
        previousHideGui = minecraft.options.hideGui;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.options.hideGui = true;
        place();
        place();
        PlotterCameraRig.yaw = rig.getYRot();
        minecraft.setCameraEntity(rig);
    }

    /** Where the camera should circle now: it glides there rather than jumping. */
    public static void aim(Vec3 centre) {
        wanted = centre;
    }

    /** A drag: round and up. */
    public static void turn(float yaw, float pitch) {
        if (orbit != null) {
            orbit.turn(yaw, pitch);
        }
    }

    /** A scroll: under one closer, over one further. */
    public static void zoom(float factor) {
        if (orbit != null) {
            orbit.zoom(factor);
        }
    }

    /** Gives the camera back to the player, as they had it. */
    public static void stop() {
        if (rig == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity player = minecraft.player;
        if (minecraft.getCameraEntity() == rig) {
            minecraft.setCameraEntity(player);
        }
        if (previousType != null) {
            minecraft.options.setCameraType(previousType);
        }
        minecraft.options.hideGui = previousHideGui;
        rig = null;
        orbit = null;
        previousType = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (rig == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || rig.level() != minecraft.level
                || minecraft.getCameraEntity() != rig) {
            // The world changed under it, or something else took the camera: stand down rather than fight for it.
            stop();
            return;
        }
        focus = focus.add(wanted.subtract(focus).scale(FOLLOW));
        place();
    }

    /** Moves the marker to where the orbit puts the eye, facing the point circled, keeping last tick's for the lerp. */
    private static void place() {
        if (rig == null || orbit == null) {
            return;
        }
        orbit.tick();
        rig.setOldPosAndRot();
        // The orbit is worked in a small frame about the point circled, then carried out to it in doubles: world
        // coordinates in floats lose a block of precision a few million out.
        Vector3f offset = orbit.eye(1.0F).sub(orbit.focus(1.0F));
        Vec3 eye = focus.add(offset.x, offset.y, offset.z);
        rig.setPos(eye.x, eye.y, eye.z);
        Vec3 look = focus.subtract(eye);
        // Unwound: a yaw that jumps from 179 to -179 would be interpolated the long way round, a spin in one frame.
        float target = (float) PlotGeometry.yawOf(look);
        float step = ((target - yaw) % 360.0F + 540.0F) % 360.0F - 180.0F;
        yaw += step;
        rig.setYRot(yaw);
        rig.setXRot((float) PlotGeometry.pitchOf(look));
    }
}
