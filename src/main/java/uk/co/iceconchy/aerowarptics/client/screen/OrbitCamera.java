package uk.co.iceconchy.aerowarptics.client.screen;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The camera over the plotting table's model: it circles a point, looks down on it from a height, and stands off it
 * at a distance, and every one of those eases towards where it has been asked to be rather than jumping - so a drag
 * turns the model in the hand, and the Engage sweep can simply ask for somewhere else and be flown there.
 *
 * <p>Minecraft-free (JOML is not Minecraft), so the view and the ray it casts back out through the pointer can be
 * checked against each other in a test: a point projected to the screen and cast back must land on the ray.
 */
public final class OrbitCamera {

    /** How steep the camera may look down, and how shallow. Level would put the eye in the ground. */
    public static final float MIN_PITCH = (float) Math.toRadians(8.0D);
    public static final float MAX_PITCH = (float) Math.toRadians(88.0D);

    public static final float MIN_DISTANCE = 12.0F;
    public static final float MAX_DISTANCE = 600.0F;

    /** Field of view, vertical, in radians. */
    public static final float FOV = (float) Math.toRadians(50.0D);

    /** How much of the way to its goal the camera goes each tick. */
    private static final float EASE = 0.22F;

    private final float[] now = new float[6];
    private final float[] last = new float[6];
    private final float[] goal = new float[6];

    // Indices: yaw, pitch, distance, focus x, focus y, focus z.
    private static final int YAW = 0;
    private static final int PITCH = 1;
    private static final int DISTANCE = 2;

    public OrbitCamera(float yaw, float pitch, float distance) {
        set(now, yaw, pitch, distance, 0.0F, 0.0F, 0.0F);
        System.arraycopy(now, 0, last, 0, 6);
        System.arraycopy(now, 0, goal, 0, 6);
    }

    private static void set(float[] into, float yaw, float pitch, float distance, float x, float y, float z) {
        into[YAW] = yaw;
        into[PITCH] = clampPitch(pitch);
        into[DISTANCE] = clampDistance(distance);
        into[3] = x;
        into[4] = y;
        into[5] = z;
    }

    /** Turns by a drag: yaw round the focus, pitch up and down. */
    public void turn(float yaw, float pitch) {
        goal[YAW] += yaw;
        goal[PITCH] = clampPitch(goal[PITCH] + pitch);
    }

    /** Zooms by a factor: under one closer, over one further. */
    public void zoom(float factor) {
        goal[DISTANCE] = clampDistance(goal[DISTANCE] * factor);
    }

    /** Asks the camera to fly somewhere: a new focus and a new stand-off, eased there over the next ticks. */
    public void flyTo(float yaw, float pitch, float distance, float x, float y, float z) {
        // Yaw the short way round from wherever the camera has wound to.
        float wound = goal[YAW];
        float delta = (float) Math.IEEEremainder(yaw - wound, Math.PI * 2.0D);
        set(goal, wound + delta, pitch, distance, x, y, z);
    }

    /** Moves the focus without flying: a pan. */
    public void focus(float x, float y, float z) {
        goal[3] = x;
        goal[4] = y;
        goal[5] = z;
    }

    public void tick() {
        System.arraycopy(now, 0, last, 0, 6);
        for (int i = 0; i < 6; i++) {
            now[i] += (goal[i] - now[i]) * EASE;
        }
    }

    public float yaw(float partialTick) {
        return lerp(YAW, partialTick);
    }

    public float pitch(float partialTick) {
        return lerp(PITCH, partialTick);
    }

    public float distance(float partialTick) {
        return lerp(DISTANCE, partialTick);
    }

    public Vector3f focus(float partialTick) {
        return new Vector3f(lerp(3, partialTick), lerp(4, partialTick), lerp(5, partialTick));
    }

    /** Where the eye is: stood off the focus, up at the pitch, round at the yaw (0 looks north, from the south). */
    public Vector3f eye(float partialTick) {
        float yaw = yaw(partialTick);
        float pitch = pitch(partialTick);
        float distance = distance(partialTick);
        Vector3f focus = focus(partialTick);
        return new Vector3f(
                focus.x + (float) (Math.sin(yaw) * Math.cos(pitch)) * distance,
                focus.y + (float) Math.sin(pitch) * distance,
                focus.z + (float) (Math.cos(yaw) * Math.cos(pitch)) * distance);
    }

    public Matrix4f view(float partialTick) {
        return new Matrix4f().lookAt(eye(partialTick), focus(partialTick), new Vector3f(0.0F, 1.0F, 0.0F));
    }

    public static Matrix4f projection(float aspect, float far) {
        return new Matrix4f().setPerspective(FOV, Math.max(0.1F, aspect), 0.5F, Math.max(64.0F, far));
    }

    /**
     * The ray through a point on the view, as {origin xyz, direction xyz}.
     *
     * @param ndcX -1 at the view's left edge, 1 at its right
     * @param ndcY -1 at its bottom, 1 at its top
     */
    public static double[] ray(Matrix4f view, Matrix4f projection, float ndcX, float ndcY) {
        Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
        Vector4f near = new Vector4f(ndcX, ndcY, -1.0F, 1.0F).mul(inverse);
        Vector4f far = new Vector4f(ndcX, ndcY, 1.0F, 1.0F).mul(inverse);
        near.div(near.w);
        far.div(far.w);
        return new double[]{near.x, near.y, near.z, far.x - near.x, far.y - near.y, far.z - near.z};
    }

    private float lerp(int index, float partialTick) {
        return last[index] + (now[index] - last[index]) * partialTick;
    }

    private static float clampPitch(float pitch) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }

    private static float clampDistance(float distance) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }
}
