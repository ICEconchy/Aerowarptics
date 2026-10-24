package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Everything a Rift Plotter has been told: where the far point is, where round it the exit opens and which way
 * it faces, and where near the machine the entry is torn, which way that faces, and how wide.
 *
 * <p>The far end is described the way a gunner describes a fall of shot - from the target, not from the gun. A
 * target is typed in as coordinates, and the exit is set <em>round</em> it: so far out, on such a bearing, so
 * high. That is what makes the plot a weapon rather than a teleporter. A player moving the exit about the target
 * keeps it pointed at the target, because "come in from the north-east, sixty out, forty up, diving" is the
 * decision they are making, and a raw pair of exit coordinates would make them do the trigonometry.
 *
 * <p>The entry is described from the machine, in two parts that used to be one. <em>Where</em> it is torn is a
 * bearing, an elevation and a reach from the lens. <em>Which way it faces</em> was once the same line - the rift sat
 * square to the machine like a plate held out at arm's length - and still is until a player turns it: a dock beside
 * a runway wants its rift across the runway, not across the line to wherever the machine happened to be built.
 * {@link #inputTurned} is that switch, and off is what every plot saved before it loads as.
 *
 * <p>The target's height is the ground's there, and the server's to say - it reads the surface when the far end
 * has loaded, so a player cannot plot an exit under the ground by typing a Y. {@link #targetY} is only what the
 * last survey showed, kept so a panel can draw the plot before the server has looked again.
 *
 * <p>Every value is clamped here, once, on the way in - from a packet, from a save, from a test - so nothing
 * downstream ever has to wonder whether a bearing is in degrees or a radius is negative.
 *
 * @param targetX      the far point, in world blocks
 * @param targetY      the ground there, as last surveyed; the server re-reads it before it opens anything
 * @param targetZ      the far point, in world blocks
 * @param distance     how far from the target the exit opens, across the ground, in blocks
 * @param bearing      which way from the target the exit lies, in compass degrees: 0 north, 90 east
 * @param height       how far above the target's ground the exit's middle is, in blocks
 * @param aimed        whether the exit faces straight at the target - {@link #aimYaw} and {@link #aimPitch} then
 *                     say nothing, and the exit follows the target wherever it is moved
 * @param aimYaw       with {@code aimed} off: degrees the exit is turned off the line to the target, clockwise
 *                     seen from above
 * @param aimPitch     with {@code aimed} off: degrees the exit tips down, Minecraft's sign - positive dives
 * @param inputYaw     which way from the lens the entry lies, as a Minecraft yaw: 0 south, 90 west
 * @param inputPitch   how far above or below the lens it lies, as a Minecraft pitch: -90 is straight up, 90
 *                     straight down
 * @param inputReach   how far from the plotter's lens the entry is torn, in blocks
 * @param inputTurned  whether the entry faces a way of its own - off, {@link #faceYaw} and {@link #facePitch} say
 *                     nothing, and it faces straight out from the lens wherever it is thrown
 * @param faceYaw      with {@code inputTurned}: which way the entry faces, as a Minecraft yaw
 * @param facePitch    with {@code inputTurned}: how far it tips, as a Minecraft pitch - -90 faces straight up
 * @param radius       the rift's radius, entry and exit alike, in blocks
 * @param set          whether a target has ever been set - a new plotter has nowhere to send anything
 */
public record Plot(int targetX, int targetY, int targetZ, double distance, double bearing, double height,
                   boolean aimed, double aimYaw, double aimPitch, double inputYaw, double inputPitch,
                   double inputReach, boolean inputTurned, double faceYaw, double facePitch, double radius,
                   boolean set) {

    /** The smallest rift the lens can hold: wide enough for somebody to walk through, and no smaller. */
    public static final double MIN_RADIUS = 1.5D;

    /** The furthest the exit may be put from its target. The survey only draws so far, and nor should the aim. */
    public static final double MAX_DISTANCE = 256.0D;

    /** How high over the target the exit may hang. Above this nothing reads the ground it is aiming at. */
    public static final double MAX_HEIGHT = 256.0D;

    /** The closest and furthest the lens can throw the entry. Closer, and it would be torn through the machine. */
    public static final double MIN_REACH = 4.0D;
    public static final double MAX_REACH = 48.0D;

    /** Steepest an exit may be tipped off level, so it never faces straight down and loses its bearing. */
    public static final double MAX_AIM_PITCH = 85.0D;

    /** A plotter nobody has set yet: nowhere, with a sensible rift in front of the machine to start from. */
    public static final Plot NONE = new Plot(0, 64, 0, 48.0D, 0.0D, 24.0D, true, 0.0D, 0.0D, 0.0D, 0.0D,
            12.0D, false, 0.0D, 0.0D, 6.0D, false);

    public static final StreamCodec<RegistryFriendlyByteBuf, Plot> STREAM_CODEC =
            StreamCodec.of(Plot::encode, Plot::decode);

    /** The same plot with every value inside its limits, and a radius no wider than {@code maxRadius}. */
    public Plot clamped(double maxRadius) {
        double top = Math.max(MIN_RADIUS, maxRadius);
        return new Plot(targetX, Mth.clamp(targetY, -2048, 4096), targetZ,
                clamp(distance, 0.0D, MAX_DISTANCE, NONE.distance),
                wrapBearing(bearing),
                clamp(height, 0.0D, MAX_HEIGHT, NONE.height),
                aimed,
                Mth.wrapDegrees(finite(aimYaw, 0.0D)),
                clamp(aimPitch, -MAX_AIM_PITCH, MAX_AIM_PITCH, 0.0D),
                Mth.wrapDegrees(finite(inputYaw, 0.0D)),
                clamp(inputPitch, -90.0D, 90.0D, 0.0D),
                clamp(inputReach, MIN_REACH, MAX_REACH, NONE.inputReach),
                inputTurned,
                Mth.wrapDegrees(finite(faceYaw, 0.0D)),
                clamp(facePitch, -90.0D, 90.0D, 0.0D),
                clamp(radius, MIN_RADIUS, top, Math.min(top, NONE.radius)),
                set);
    }

    // ------------------------------------------------------------------ the entry's facing

    /** Which way the entry faces, as a Minecraft yaw: its own way if it has been turned, straight out if not. */
    public double facingYaw() {
        return inputTurned ? faceYaw : inputYaw;
    }

    /** How far the entry tips, as a Minecraft pitch: its own way if it has been turned, straight out if not. */
    public double facingPitch() {
        return inputTurned ? facePitch : inputPitch;
    }

    // ------------------------------------------------------------------ changes

    public Plot withTarget(int x, int y, int z) {
        return new Plot(x, y, z, distance, bearing, height, aimed, aimYaw, aimPitch, inputYaw, inputPitch,
                inputReach, inputTurned, faceYaw, facePitch, radius, true);
    }

    public Plot withTargetY(int y) {
        return new Plot(targetX, y, targetZ, distance, bearing, height, aimed, aimYaw, aimPitch, inputYaw,
                inputPitch, inputReach, inputTurned, faceYaw, facePitch, radius, set);
    }

    public Plot withExit(double newDistance, double newBearing, double newHeight) {
        return new Plot(targetX, targetY, targetZ, newDistance, newBearing, newHeight, aimed, aimYaw, aimPitch,
                inputYaw, inputPitch, inputReach, inputTurned, faceYaw, facePitch, radius, set);
    }

    public Plot withAim(boolean newAimed, double newYaw, double newPitch) {
        return new Plot(targetX, targetY, targetZ, distance, bearing, height, newAimed, newYaw, newPitch,
                inputYaw, inputPitch, inputReach, inputTurned, faceYaw, facePitch, radius, set);
    }

    /** Where the entry is torn, and how wide. Which way it faces is {@link #withFacing}'s. */
    public Plot withInput(double yaw, double pitch, double reach, double newRadius) {
        return new Plot(targetX, targetY, targetZ, distance, bearing, height, aimed, aimYaw, aimPitch, yaw, pitch,
                reach, inputTurned, faceYaw, facePitch, newRadius, set);
    }

    /** Which way the entry faces: turned to a yaw and pitch of its own, or - not turned - straight out from the lens. */
    public Plot withFacing(boolean turned, double yaw, double pitch) {
        return new Plot(targetX, targetY, targetZ, distance, bearing, height, aimed, aimYaw, aimPitch, inputYaw,
                inputPitch, inputReach, turned, yaw, pitch, radius, set);
    }

    // ------------------------------------------------------------------ helpers

    /** A bearing folded into 0..360, whatever it was given - a dial dragged round twice is still a bearing. */
    public static double wrapBearing(double degrees) {
        double finite = finite(degrees, 0.0D);
        double wrapped = finite % 360.0D;
        return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Mth.clamp(finite(value, fallback), min, max);
    }

    /** A packet can carry a NaN as easily as a number; a NaN in a plot would put a rift nowhere at all. */
    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TargetX", targetX);
        tag.putInt("TargetY", targetY);
        tag.putInt("TargetZ", targetZ);
        tag.putDouble("Distance", distance);
        tag.putDouble("Bearing", bearing);
        tag.putDouble("Height", height);
        tag.putBoolean("Aimed", aimed);
        tag.putDouble("AimYaw", aimYaw);
        tag.putDouble("AimPitch", aimPitch);
        tag.putDouble("InputYaw", inputYaw);
        tag.putDouble("InputPitch", inputPitch);
        tag.putDouble("InputReach", inputReach);
        tag.putBoolean("InputTurned", inputTurned);
        tag.putDouble("FaceYaw", faceYaw);
        tag.putDouble("FacePitch", facePitch);
        tag.putDouble("Radius", radius);
        tag.putBoolean("Set", set);
        return tag;
    }

    /**
     * A saved plot, or {@link #NONE} where nothing was saved. Clamped by the caller, which knows the radius. A plot
     * saved before an entry could be turned has no facing in it, and reads as not turned - which is how it stood.
     */
    public static Plot load(CompoundTag tag) {
        if (!tag.contains("Set")) {
            return NONE;
        }
        return new Plot(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"),
                tag.getDouble("Distance"), tag.getDouble("Bearing"), tag.getDouble("Height"),
                tag.getBoolean("Aimed"), tag.getDouble("AimYaw"), tag.getDouble("AimPitch"),
                tag.getDouble("InputYaw"), tag.getDouble("InputPitch"), tag.getDouble("InputReach"),
                tag.getBoolean("InputTurned"), tag.getDouble("FaceYaw"), tag.getDouble("FacePitch"),
                tag.getDouble("Radius"), tag.getBoolean("Set"));
    }

    private static void encode(RegistryFriendlyByteBuf buf, Plot plot) {
        buf.writeInt(plot.targetX);
        buf.writeInt(plot.targetY);
        buf.writeInt(plot.targetZ);
        buf.writeDouble(plot.distance);
        buf.writeDouble(plot.bearing);
        buf.writeDouble(plot.height);
        buf.writeBoolean(plot.aimed);
        buf.writeDouble(plot.aimYaw);
        buf.writeDouble(plot.aimPitch);
        buf.writeDouble(plot.inputYaw);
        buf.writeDouble(plot.inputPitch);
        buf.writeDouble(plot.inputReach);
        buf.writeBoolean(plot.inputTurned);
        buf.writeDouble(plot.faceYaw);
        buf.writeDouble(plot.facePitch);
        buf.writeDouble(plot.radius);
        buf.writeBoolean(plot.set);
    }

    private static Plot decode(RegistryFriendlyByteBuf buf) {
        return new Plot(buf.readInt(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readBoolean());
    }
}
