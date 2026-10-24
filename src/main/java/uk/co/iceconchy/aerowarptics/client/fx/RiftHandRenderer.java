package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.sunderer.RiftHandPose;

import java.util.List;

/**
 * Draws the hand that sometimes comes through a straining Rift Sunderer, from {@link RiftHandPose}.
 *
 * <p>Built from code, a tapering four-sided tube for each bone, because it is posed by code: it reaches for
 * whichever block of whichever ring it is given, and turns to whoever is standing there. Dark first, for the
 * meltdown's reason - a hand made of light is a hologram, and at noon it is nothing - so the hand is solid, on the
 * membrane's pass, pale against the black it comes out of, and the violet is a faint rim laid over it on the fire
 * pass, with a glint at each knuckle. Whatever of it is still on the far side of the plane is not drawn: a bone that crosses the plane is
 * cut where it does, so the arm is seen to come out of the membrane rather than be laid against it.
 *
 * <p>Its frame is the plane's: {@code u} across the opening, {@code v} up, {@code n} out of it - see
 * {@link Frame}. Positions handed to the vertex helpers are camera-relative, as every effect in the mod's are.
 */
public final class RiftHandRenderer {

    /**
     * Pale and ashen, with the knuckles darker. It was matte black first, which against a black membrane was
     * nothing at all (seen in game, 2026-10-16): the membrane is what the hand is seen against, so the hand is the
     * one thing in the scene that is light, and still solid.
     */
    private static final float[] SKIN = {0.63F, 0.58F, 0.67F};
    private static final float[] KNUCKLE = {0.42F, 0.37F, 0.48F};
    private static final int RIM = 0x8B4FE0;
    private static final int GLINT = 0xE8DAFF;
    private static final int GLOW_LIGHT = 240;

    /** Thicknesses, in blocks: the forearm at the elbow and at the wrist, the palm, a finger at its root and tip. */
    private static final double FOREARM_ROOT = 0.34D;
    private static final double FOREARM_WRIST = 0.24D;
    private static final double PALM_THICK = 0.13D;
    private static final double FINGER_ROOT = 0.1D;
    private static final double FINGER_TIP = 0.045D;

    private RiftHandRenderer() {
    }

    /** Where the hand's frame is in the world: the slit, and the three directions of {@code u}, {@code v}, {@code n}. */
    public record Frame(Vec3 slit, Vec3 across, Vec3 up, Vec3 out) {

        public Vec3 world(RiftHandPose.Point point) {
            return slit.add(across.scale(point.u())).add(up.scale(point.v())).add(out.scale(point.n()));
        }

        public RiftHandPose.Point local(Vec3 world) {
            Vec3 from = world.subtract(slit);
            return new RiftHandPose.Point(from.dot(across), from.dot(up), from.dot(out));
        }
    }

    /** The hand's body, on the solid pass. */
    public static void drawSolid(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Frame frame, RiftHandPose.Pose pose) {
        bone(consumer, matrix, eye, frame, pose.elbow(), pose.wrist(), FOREARM_ROOT, FOREARM_WRIST, SKIN, true, 0, 0.0F);
        RiftHandPose.Point knuckles = palmEnd(pose);
        bone(consumer, matrix, eye, frame, pose.wrist(), knuckles, PALM_THICK * 2.4D, PALM_THICK * 3.4D, SKIN, true, 0, 0.0F);
        for (List<RiftHandPose.Point> finger : pose.fingers()) {
            for (int joint = 0; joint + 1 < finger.size(); joint++) {
                double t0 = joint / (double) (finger.size() - 1);
                double t1 = (joint + 1) / (double) (finger.size() - 1);
                bone(consumer, matrix, eye, frame, finger.get(joint), finger.get(joint + 1),
                        Mth.lerp(t0, FINGER_ROOT, FINGER_TIP), Mth.lerp(t1, FINGER_ROOT, FINGER_TIP),
                        joint % 2 == 0 ? SKIN : KNUCKLE, true, 0, 0.0F);
            }
        }
    }

    /** The faint rim round it, and a glint at each knuckle, on the fire pass. */
    public static void drawLight(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Frame frame, RiftHandPose.Pose pose,
                                 float strength) {
        if (strength <= 0.001F) {
            return;
        }
        bone(consumer, matrix, eye, frame, pose.elbow(), pose.wrist(), FOREARM_ROOT * 1.5D, FOREARM_WRIST * 1.5D, null,
                false, RIM, 0.12F * strength);
        for (List<RiftHandPose.Point> finger : pose.fingers()) {
            for (int joint = 0; joint + 1 < finger.size(); joint++) {
                bone(consumer, matrix, eye, frame, finger.get(joint), finger.get(joint + 1), FINGER_ROOT * 2.2D,
                        FINGER_TIP * 2.2D, null, false, RIM, 0.16F * strength);
                RiftHandPose.Point at = finger.get(joint + 1);
                if (at.n() > 0.0D) {
                    glint(consumer, matrix, eye, frame.world(at), 0.06D, 0.35F * strength);
                }
            }
        }
    }

    /** The middle of the palm's far edge, where the four fingers start. */
    private static RiftHandPose.Point palmEnd(RiftHandPose.Pose pose) {
        double u = 0.0D;
        double v = 0.0D;
        double n = 0.0D;
        for (int finger = 1; finger < pose.fingers().size(); finger++) {
            RiftHandPose.Point root = pose.fingers().get(finger).get(0);
            u += root.u();
            v += root.v();
            n += root.n();
        }
        int count = Math.max(1, pose.fingers().size() - 1);
        return new RiftHandPose.Point(u / count, v / count, n / count);
    }

    /**
     * One bone: a four-sided tube from {@code from} to {@code to}, cut at the plane. Solid, in a colour, or light, in
     * a colour and an alpha.
     */
    private static void bone(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Frame frame, RiftHandPose.Point from,
                             RiftHandPose.Point to, double thickFrom, double thickTo, float[] colour, boolean solid,
                             int light, float alpha) {
        if (from.n() <= 0.0D && to.n() <= 0.0D) {
            return;
        }
        RiftHandPose.Point a = from;
        RiftHandPose.Point b = to;
        if (a.n() < 0.0D) {
            double t = a.n() / (a.n() - b.n());
            a = new RiftHandPose.Point(Mth.lerp(t, a.u(), b.u()), Mth.lerp(t, a.v(), b.v()), 0.0D);
        } else if (b.n() < 0.0D) {
            double t = b.n() / (b.n() - a.n());
            b = new RiftHandPose.Point(Mth.lerp(t, b.u(), a.u()), Mth.lerp(t, b.v(), a.v()), 0.0D);
        }
        Vec3 p0 = frame.world(a);
        Vec3 p1 = frame.world(b);
        Vec3 axis = p1.subtract(p0);
        if (axis.lengthSqr() < 1.0e-8D) {
            return;
        }
        Vec3 along = axis.normalize();
        Vec3 side = Math.abs(along.y) > 0.95D ? along.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize()
                : along.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 other = along.cross(side).normalize();
        Vec3[] ring0 = square(p0, side, other, thickFrom * 0.5D);
        Vec3[] ring1 = square(p1, side, other, thickTo * 0.5D);
        for (int face = 0; face < 4; face++) {
            int next = (face + 1) % 4;
            corner(consumer, matrix, eye, ring0[face], colour, solid, light, alpha);
            corner(consumer, matrix, eye, ring0[next], colour, solid, light, alpha);
            corner(consumer, matrix, eye, ring1[next], colour, solid, light, alpha);
            corner(consumer, matrix, eye, ring1[face], colour, solid, light, alpha);
        }
        // The tip is capped, so a finger is not seen to be hollow.
        corner(consumer, matrix, eye, ring1[0], colour, solid, light, alpha);
        corner(consumer, matrix, eye, ring1[1], colour, solid, light, alpha);
        corner(consumer, matrix, eye, ring1[2], colour, solid, light, alpha);
        corner(consumer, matrix, eye, ring1[3], colour, solid, light, alpha);
    }

    private static Vec3[] square(Vec3 at, Vec3 side, Vec3 other, double half) {
        return new Vec3[]{
                at.add(side.scale(half)).add(other.scale(half)),
                at.add(side.scale(-half)).add(other.scale(half)),
                at.add(side.scale(-half)).add(other.scale(-half)),
                at.add(side.scale(half)).add(other.scale(-half))};
    }

    private static void corner(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 at, float[] colour, boolean solid,
                               int light, float alpha) {
        float x = (float) (at.x - eye.x);
        float y = (float) (at.y - eye.y);
        float z = (float) (at.z - eye.z);
        if (solid) {
            consumer.addVertex(matrix, x, y, z).setColor(colour[0], colour[1], colour[2], 1.0F);
        } else {
            consumer.addVertex(matrix, x, y, z)
                    .setColor((Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (light & 0xFFFFFF))
                    .setUv(0.5F, 0.5F)
                    .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                    .setNormal(0.0F, 1.0F, 0.0F);
        }
    }

    /** A small glow square to the line of sight. */
    private static void glint(VertexConsumer consumer, Matrix4f matrix, Vec3 eye, Vec3 at, double radius, float alpha) {
        Vec3 from = at.subtract(eye);
        if (from.lengthSqr() < 1.0e-6D) {
            return;
        }
        Vec3 forward = from.normalize();
        Vec3 right = Math.abs(forward.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 up = right.cross(forward);
        Vec3[] corners = {right.add(up), right.scale(-1.0D).add(up), right.add(up).scale(-1.0D), right.subtract(up)};
        for (Vec3 corner : corners) {
            corner(consumer, matrix, eye, at.add(corner.scale(radius)), null, false, GLINT, alpha);
        }
    }
}
