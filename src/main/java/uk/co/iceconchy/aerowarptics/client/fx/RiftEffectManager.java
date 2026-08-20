package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the apertures an airship flies through.
 *
 * <p>A rift is a hole torn in the world, and it is drawn as one. The face of it is <em>opaque and
 * writes depth</em>, drawn after the world's blocks, so anything past its plane is painted over and
 * genuinely disappears. That is what makes a hull vanish as it goes through: not a fade, not a trick
 * of particles, but the aperture actually covering it. The hull is flown all the way in before the
 * server moves it, so by the time it teleports there is nothing left on screen to jump.
 *
 * <p>The rim is torn rather than cut. Its radius wanders with angle and time, but only ever
 * <em>outwards</em> from a circle wide enough to cover the hull - a tear that bit inwards would open
 * a gap and show the ship through the hole it is supposed to be swallowed by.
 *
 * <p>An aperture also has <em>depth</em>. A flat face only hides what is directly behind it, so a hull
 * halfway through one is still in plain view to anyone standing off to the side. While a ship is
 * actually passing through, the aperture grows a closed throat behind its mouth - a tube long enough
 * to contain the whole hull, tapering shut at the far end - so there is no angle left to see it from.
 * The throat runs whichever way the hull is hidden: forwards on the aperture a ship flies into,
 * backwards on the one it comes out of.
 *
 * <p>It is drawn in world space at the position the server sent, so the people standing at the
 * destination watch a rift open in front of them seconds before a ship comes out of it - the two ends
 * of the journey are visible to two different sets of players, which is the whole appeal of long-range
 * travel.
 *
 * <p>Geometry is plain quads on two passes - the opaque face, then additive fire over the top - so
 * there is no texture to load, nothing to bind, and no shader dependency.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class RiftEffectManager {

    /** Concentric bands making up the churn on the face. More reads finer and costs more quads. */
    private static final int BANDS = 5;
    /** Segments around the ring. The rim is ragged, so it needs more of them than a circle would. */
    private static final int SEGMENTS = 48;
    /** Rings across the opaque face. Three is enough for a gradient from a dark throat to a hot rim. */
    private static final float[] FACE_RINGS = {0.0F, 0.5F, 0.82F, 1.0F};
/**
     * Rings down the throat, as fractions of its depth.
     *
     * <p>Bunched towards the mouth, because that is where they are worth having: perspective already
     * compresses the far end, and a rib you cannot resolve is a rib you paid for and cannot see.
     *
     * <p>There are a lot of them now. Ribs are the whole reason a bore reads as receding rather than
     * as a shape - they give the eye something to measure depth against, and something for motion to
     * pass over. The previous five bands were invisible past the first third because everything back
     * there was painted the same flat black.
     */
    private static final float[] THROAT_RINGS = buildRings(16);

    private static float[] buildRings(int count) {
        float[] rings = new float[count + 1];
        for (int ring = 0; ring <= count; ring++) {
            rings[ring] = (float) Math.pow(ring / (double) count, 1.45D);
        }
        return rings;
    }

    /** How far a rib stands proud of the bore, as a fraction of its radius. */
    private static final float RIB_RELIEF = 0.055F;
    /** How wide the travelling light band is, as a fraction of the throat's length. */
    private static final float BAND_WIDTH = 0.11F;
    /** How much of the bore's length the outer haze covers before it has faded out. */
    private static final float HAZE_REACH = 0.55F;
    /** Ticks the throat takes to open behind the mouth, and to close again afterwards. */
    private static final float THROAT_FADE = 6.0F;
    /**
     * How far in front of the face the fire is drawn, in blocks.
     *
     * <p>Both surfaces occupy the same plane, and the face writes depth, so without a nudge towards
     * the viewer the fire would z-fight with the thing it is burning around. Always towards whichever
     * side the camera is on, since a rift gets watched from both.
     */
    private static final float FIRE_BIAS = 0.06F;
    private static final List<ActiveRift> ACTIVE = new ArrayList<>();

    private RiftEffectManager() {
    }

    /** One rift, from the moment it tears open to the moment it collapses. */
    private static final class ActiveRift {
        final Vec3 centre;
        final Vector3f normal;
        final Vector3f right;
        final Vector3f up;
        final double radius;
        final int colour;
        final int openTicks;
        final int holdTicks;
        final int closeTicks;
        int age;
        int lastAge;
        /** Ticks left of a hull passing through, which is when the tear gets angry. */
        int transitTicks;
        /** Depth of the throat, signed along the normal. Zero for an aperture with no hull in it. */
        final float throat;
        /** 0..1 how far the throat has opened, so it grows and closes rather than appearing. */
        float throatOpen;
        float throatOpenLast;

        ActiveRift(Vec3 centre, Vec3 normal, double radius, int colour, float throat,
                   int openTicks, int holdTicks, int closeTicks) {
            this.centre = centre;
            this.radius = radius;
            this.colour = colour;
            this.throat = throat;
            this.openTicks = openTicks;
            this.holdTicks = holdTicks;
            this.closeTicks = closeTicks;

            Vector3f forward = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
            if (forward.lengthSquared() < 1.0e-6F) {
                forward.set(0.0F, 0.0F, 1.0F);
            }
            forward.normalize();
            this.normal = forward;
            // Any vector not parallel to the normal gives a stable basis for the aperture's plane.
            Vector3f seed = Math.abs(forward.y) > 0.9F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            this.right = new Vector3f(forward).cross(seed).normalize();
            this.up = new Vector3f(forward).cross(right).normalize();
        }

        int lifetime() {
            return openTicks + holdTicks + closeTicks;
        }

        boolean expired() {
            return age >= lifetime();
        }

        /** Sends the aperture into its collapse now, whatever it had left to run. */
        void collapse() {
            age = Math.max(age, openTicks + holdTicks);
            // Land the previous age on the new one too, or the next frame interpolates across the
            // jump and the aperture appears to flinch before it collapses.
            lastAge = age;
        }

        /** 0..1 aperture size, easing open and snapping shut. */
        float aperture(float partialTick) {
            float time = Mth.lerp(partialTick, lastAge, age);
            if (time < openTicks) {
                float t = time / openTicks;
                return t * t * (3.0F - 2.0F * t); // smoothstep: tears open, then steadies
            }
            if (time < openTicks + holdTicks) {
                return 1.0F;
            }
            float t = (time - openTicks - holdTicks) / Math.max(1.0F, closeTicks);
            return Math.max(0.0F, 1.0F - t * t); // collapses faster than it opened
        }

    }

    // ------------------------------------------------------------------ feed

    /** Opens a rift from a server cue. */
    public static void open(ClientboundWarpEffectPacket packet, int colour,
                            int openTicks, int holdTicks, int closeTicks) {
        if (!packet.hasRift() || !AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
        ACTIVE.add(new ActiveRift(packet.centre(), packet.normal(), packet.radius(), colour,
                packet.throat(), openTicks, holdTicks, closeTicks));
    }

    /**
     * Tells the aperture at this position that a hull is coming through it.
     *
     * <p>The server sends this once, at the moment the bow touches the plane, and the rift runs the
     * fire off its own clock from there. Streaming a cue every tick for three seconds would say the
     * same thing far more expensively.
     */
    public static void transit(Vec3 centre, int ticks) {
        ActiveRift rift = nearest(centre);
        if (rift == null) {
            return;
        }
        rift.transitTicks = Math.max(rift.transitTicks, ticks);
        if (rift.throat < 0.0F) {
            // An aperture a hull comes *out* of already has the hull inside it the moment this
            // arrives, so its throat has to be there immediately. Growing it over a few ticks would
            // leave the ship briefly visible from the side, which is the whole thing being fixed.
            rift.throatOpen = 1.0F;
            rift.throatOpenLast = 1.0F;
        }
    }

    /** Collapses the aperture at this position, rather than waiting for it to time out. */
    public static void collapse(Vec3 centre) {
        ActiveRift rift = nearest(centre);
        if (rift != null) {
            rift.collapse();
        }
    }

    /**
     * The rift closest to a position, if one is close enough to be the one meant.
     *
     * <p>Matched by position because that is the only thing both ends of a cue agree on: the server
     * plans an aperture at a point and every later cue about it carries that same point.
     */
    private static ActiveRift nearest(Vec3 centre) {
        ActiveRift best = null;
        double bestDistance = 4.0D * 4.0D;
        for (ActiveRift rift : ACTIVE) {
            double distance = rift.centre.distanceToSqr(centre);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = rift;
            }
        }
        return best;
    }

    /** Drops every rift, e.g. on disconnect or dimension change. */
    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean hasActiveRifts() {
        return !ACTIVE.isEmpty();
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        for (Iterator<ActiveRift> iterator = ACTIVE.iterator(); iterator.hasNext(); ) {
            ActiveRift rift = iterator.next();
            rift.lastAge = rift.age;
            rift.age++;
            if (rift.transitTicks > 0) {
                rift.transitTicks--;
                WarpEffects.tearFire(level, rift.centre, rift.right, rift.up, rift.radius, rift.colour);
            }
            // The throat is only there while something is inside it. An aperture sitting open with a
            // tube hanging off the back of it would be a large dark object in the sky for no reason.
            rift.throatOpenLast = rift.throatOpen;
            float target = rift.transitTicks > 0 ? 1.0F : 0.0F;
            rift.throatOpen += (target - rift.throatOpen) / THROAT_FADE;
            if (rift.throatOpen < 0.004F) {
                rift.throatOpen = 0.0F;
            }
            if (rift.expired()) {
                iterator.remove();
            }
        }
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        boolean membranePass = event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        boolean firePass = event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (!membranePass && !firePass) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-eye.x, -eye.y, -eye.z);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // The face goes down with the world's blocks so it can occlude them; the fire goes over
        // everything afterwards, including the face it is burning around.
        RenderType type = membranePass ? AWRenderTypes.RIFT_MEMBRANE : RenderType.lightning();
        VertexConsumer consumer = buffers.getBuffer(type);
        Matrix4f matrix = poseStack.last().pose();

        for (ActiveRift rift : ACTIVE) {
            if (membranePass) {
                // Throat first, then the face over its mouth: the face is what a viewer looking
                // straight down the aperture sees, and it must win.
                drawThroat(consumer, matrix, rift, partialTick);
                drawFace(consumer, matrix, rift, partialTick);
            } else {
                drawHaze(consumer, matrix, rift, partialTick);
                drawFire(consumer, matrix, rift, partialTick, eye);
            }
        }

        buffers.endBatch(type);
        poseStack.popPose();
    }

    /**
     * The opaque face: a dark throat brightening to a hot torn rim.
     *
     * <p>Rings of quads sharing their vertices exactly, so the surface is watertight. A seam here
     * would be a pinhole straight through to the hull behind it.
     */
    private static void drawFace(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float partialTick) {
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }
        float time = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        for (int ring = 0; ring < FACE_RINGS.length - 1; ring++) {
            float innerT = FACE_RINGS[ring];
            float outerT = FACE_RINGS[ring + 1];
            // Nothing in the middle, a glow towards the edge, and the rim itself burning.
            float innerGlow = faceGlow(innerT);
            float outerGlow = faceGlow(outerT);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D;
                double rim0 = cover * RiftTear.rim(a0, time);
                double rim1 = cover * RiftTear.rim(a1, time);

                face(consumer, matrix, rift, a0, rim0 * innerT, red, green, blue, innerGlow);
                face(consumer, matrix, rift, a1, rim1 * innerT, red, green, blue, innerGlow);
                face(consumer, matrix, rift, a1, rim1 * outerT, red, green, blue, outerGlow);
                face(consumer, matrix, rift, a0, rim0 * outerT, red, green, blue, outerGlow);
            }
        }
    }

    /**
     * The throat: a closed tube running back from the mouth, hiding whatever is inside it.
     *
     * <p>This is the part that works from an angle. The face hides a hull from anyone looking through
     * the aperture; the throat hides it from everyone else. It shares the mouth's torn rim exactly, so
     * the two meet on a common edge with no seam and nothing to z-fight against.
     */
    private static void drawThroat(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float partialTick) {
        float open = Mth.lerp(partialTick, rift.throatOpenLast, rift.throatOpen);
        if (open <= 0.001F || rift.throat == 0.0F) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        float time = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;
        // Signed, so the tube runs behind whichever face of the aperture the hull is hidden on.
        double depth = rift.throat * open;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        // A pulse of light running the length of the bore. It travels away from the mouth on an
        // aperture a ship goes into and towards it on one a ship comes out of, so the tunnel always
        // shows the direction of travel rather than just being lit.
        float band = (rift.lastAge + partialTick) * 0.022F % 1.0F;
        if (rift.throat < 0.0F) {
            band = 1.0F - band;
        }

        for (int ring = 0; ring < THROAT_RINGS.length - 1; ring++) {
            float nearT = THROAT_RINGS[ring];
            float farT = THROAT_RINGS[ring + 1];
            float nearGlow = throatGlow(nearT, band);
            float farGlow = throatGlow(farT, band);
            // Alternate rings stand slightly proud, so the bore has actual relief to catch the light
            // instead of being a smooth pipe with stripes painted on it.
            float nearWidth = throatWidth(nearT) * (ring % 2 == 0 ? 1.0F : 1.0F + RIB_RELIEF);
            float farWidth = throatWidth(farT) * (ring % 2 == 0 ? 1.0F + RIB_RELIEF : 1.0F);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D;
                double rim0 = cover * RiftTear.rim(a0, time);
                double rim1 = cover * RiftTear.rim(a1, time);

                throatVertex(consumer, matrix, rift, a0, rim0 * nearWidth, depth * nearT, red, green, blue, nearGlow);
                throatVertex(consumer, matrix, rift, a1, rim1 * nearWidth, depth * nearT, red, green, blue, nearGlow);
                throatVertex(consumer, matrix, rift, a1, rim1 * farWidth, depth * farT, red, green, blue, farGlow);
                throatVertex(consumer, matrix, rift, a0, rim0 * farWidth, depth * farT, red, green, blue, farGlow);
            }
        }
    }

    /**
     * A soft sleeve of light around the outside of the bore.
     *
     * <p>This is what stops the tunnel reading as a hole cut in the screen. An opaque shape against
     * the sky has a hard silhouette, and a hard silhouette on something with no surface detail is
     * exactly what the eye files as "a blob". Fading the edge out over a few blocks gives it an
     * atmosphere to sit in, and costs one additive pass.
     */
    private static void drawHaze(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                 float partialTick) {
        float open = Mth.lerp(partialTick, rift.throatOpenLast, rift.throatOpen);
        if (open <= 0.001F || rift.throat == 0.0F) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        float time = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;
        double depth = rift.throat * open;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        int steps = 6;
        for (int step = 0; step < steps; step++) {
            float nearT = HAZE_REACH * step / steps;
            float farT = HAZE_REACH * (step + 1) / steps;
            float nearAlpha = aperture * 0.30F * (1.0F - nearT / HAZE_REACH);
            float farAlpha = aperture * 0.30F * (1.0F - farT / HAZE_REACH);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D;
                double rim0 = cover * RiftTear.rim(a0, time);
                double rim1 = cover * RiftTear.rim(a1, time);

                // Flares outward as it goes back, so the sleeve reads as vapour coming off the tear
                // rather than as a second, larger tube.
                double nearFlare = 1.04D + 0.30D * nearT;
                double farFlare = 1.04D + 0.30D * farT;

                throatVertex(consumer, matrix, rift, a0, rim0 * nearFlare, depth * nearT, red, green, blue, nearAlpha);
                throatVertex(consumer, matrix, rift, a1, rim1 * nearFlare, depth * nearT, red, green, blue, nearAlpha);
                throatVertex(consumer, matrix, rift, a1, rim1 * farFlare, depth * farT, red, green, blue, farAlpha);
                throatVertex(consumer, matrix, rift, a0, rim0 * farFlare, depth * farT, red, green, blue, farAlpha);
            }
        }
    }

    /**
     * Full width down the length that has to hold a hull, closing over the last stretch.
     *
     * <p>The closure is on a cosine rather than a straight line, which matters less for the shape than
     * for the fact that by the time it happens there is no light left on it. A bore that visibly ends
     * in a cone is a bag; one that fades out before it closes is a tunnel going somewhere.
     */
    private static float throatWidth(float t) {
        if (t <= 0.82F) {
            return 1.0F;
        }
        float closing = (t - 0.82F) / 0.18F;
        return Math.max(0.0F, (float) Math.cos(closing * Math.PI * 0.5D));
    }

    /**
     * How lit the bore is at a given fraction of its depth.
     *
     * <p>Three things at once: a long falloff from the mouth so distance is legible all the way down
     * rather than bottoming out in the first third; a floor tinted with the rift's own colour instead
     * of pure black, so the far end reads as low-intensity energy rather than missing pixels; and the
     * travelling band, which is the only thing in here that says which way the tunnel flows.
     */
    private static float throatGlow(float t, float band) {
        float distance = 0.015F + 0.62F * (float) Math.pow(1.0F - t, 2.2D);
        float offset = (t - band) / BAND_WIDTH;
        float pulse = (float) Math.exp(-offset * offset) * 0.55F;
        return Math.min(1.0F, distance + pulse);
    }

    /** How lit the face is at a given fraction of the way out: black throat, burning edge. */
    private static float faceGlow(float t) {
        return 0.04F + 0.96F * (float) Math.pow(t, 3.5D);
    }

    /**
     * The additive fire: churn across the face and flames licking past the torn rim.
     *
     * <p>Drawn after the face, so it burns on top of it rather than being swallowed by it.
     */
    private static void drawFire(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                 float partialTick, Vec3 eye) {
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        double towardsEye = (eye.x - rift.centre.x) * rift.normal.x
                + (eye.y - rift.centre.y) * rift.normal.y
                + (eye.z - rift.centre.z) * rift.normal.z;
        float bias = towardsEye >= 0.0D ? FIRE_BIAS : -FIRE_BIAS;

        float time = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;
        // A hull going through drags the tear wider and brighter.
        float agitation = rift.transitTicks > 0 ? 1.6F : 1.0F;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        for (int band = 0; band < BANDS; band++) {
            float innerT = band / (float) BANDS;
            float outerT = (band + 1) / (float) BANDS;
            float edge = (float) Math.pow(outerT, 1.6D);
            float alpha = aperture * (0.15F + 0.85F * edge) * agitation;
            // Each band counter-rotates against its neighbour so the aperture churns.
            float spin = time * (band % 2 == 0 ? 1.0F : -1.4F) + band * 0.7F;

            float bandRed = Mth.lerp(edge, red * 0.15F, 1.0F);
            float bandGreen = Mth.lerp(edge, green * 0.15F, green);
            float bandBlue = Mth.lerp(edge, blue * 0.3F, blue);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D + spin;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D + spin;
                double rim0 = cover * RiftTear.rim(a0, time);
                double rim1 = cover * RiftTear.rim(a1, time);

                // A gap chases around each band, so the rim shimmers rather than sitting flat.
                float flicker = 0.65F + 0.35F * Mth.sin((float) (a0 * 3.0D + time * 4.0F));
                float segmentAlpha = alpha * flicker;

                double inner0 = rim0 * (0.35D + 0.55D * innerT);
                double inner1 = rim1 * (0.35D + 0.55D * innerT);
                double outer0 = rim0 * (0.35D + 0.55D * outerT);
                double outer1 = rim1 * (0.35D + 0.55D * outerT);

                vertex(consumer, matrix, rift, bias, a0, inner0, bandRed, bandGreen, bandBlue, segmentAlpha);
                vertex(consumer, matrix, rift, bias, a1, inner1, bandRed, bandGreen, bandBlue, segmentAlpha);
                vertex(consumer, matrix, rift, bias, a1, outer1, bandRed, bandGreen, bandBlue, segmentAlpha * 0.4F);
                vertex(consumer, matrix, rift, bias, a0, outer0, bandRed, bandGreen, bandBlue, segmentAlpha * 0.4F);
            }
        }

        // Flames past the rim: each segment reaches its own distance, so the edge is torn rather
        // than merely bumpy, and none of it is load-bearing for hiding the hull.
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D;
            double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D;
            double rim0 = cover * RiftTear.rim(a0, time);
            double rim1 = cover * RiftTear.rim(a1, time);

            float reach0 = 0.35F + 0.65F * RiftTear.lick(a0, time);
            float reach1 = 0.35F + 0.65F * RiftTear.lick(a1, time);
            float alpha = aperture * 0.55F * agitation;

            vertex(consumer, matrix, rift, bias, a0, rim0, 1.0F, green, blue, alpha);
            vertex(consumer, matrix, rift, bias, a1, rim1, 1.0F, green, blue, alpha);
            vertex(consumer, matrix, rift, bias, a1, rim1 * (1.0D + RiftTear.FLAME * reach1), red, green, blue, 0.0F);
            vertex(consumer, matrix, rift, bias, a0, rim0 * (1.0D + RiftTear.FLAME * reach0), red, green, blue, 0.0F);
        }
    }

    private static void throatVertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                     double angle, double radius, double along,
                                     float red, float green, float blue, float glow) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        float x = (float) (rift.centre.x + rift.right.x * cos + rift.up.x * sin + rift.normal.x * along);
        float y = (float) (rift.centre.y + rift.right.y * cos + rift.up.y * sin + rift.normal.y * along);
        float z = (float) (rift.centre.z + rift.right.z * cos + rift.up.z * sin + rift.normal.z * along);
        consumer.addVertex(matrix, x, y, z).setColor(red * glow, green * glow, blue * glow, 1.0F);
    }

    private static void face(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                             double angle, double radius, float red, float green, float blue, float glow) {
        // Opaque: alpha is ignored by this render type, so brightness has to live in the colour.
        emit(consumer, matrix, rift, 0.0F, angle, radius, red * glow, green * glow, blue * glow, 1.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                               double angle, double radius, float red, float green, float blue, float alpha) {
        emit(consumer, matrix, rift, bias, angle, radius, red, green, blue, Math.min(1.0F, alpha));
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                             double angle, double radius, float red, float green, float blue, float alpha) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        float x = (float) (rift.centre.x + rift.right.x * cos + rift.up.x * sin) + rift.normal.x * bias;
        float y = (float) (rift.centre.y + rift.right.y * cos + rift.up.y * sin) + rift.normal.y * bias;
        float z = (float) (rift.centre.z + rift.right.z * cos + rift.up.z * sin) + rift.normal.z * bias;
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }
}
