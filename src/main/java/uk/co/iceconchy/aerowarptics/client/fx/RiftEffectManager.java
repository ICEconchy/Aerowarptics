package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    /** Ribs apart, a rib is lit as a gate the hull punches through rather than as another ring. */
    private static final int GATE_EVERY = 4;
    /** How much brighter a gate is than the bore around it. */
    private static final float GATE_GLOW = 0.55F;
    /**
     * Ticks a held aperture survives without being renewed.
     *
     * <p>Comfortably more than a tick so a dropped frame does not flicker it, and comfortably less
     * than a second so a gate that closes is dark before anybody drives at it.
     */
    private static final int HOLD_TICKS = 6;

    /** How far through a close the sealing flash begins. The last fifth of it. */
    private static final float SEAL_SPARK = 0.8F;

    /**
     * How long a held aperture stands if nothing ever closes it, in ticks. Five days.
     *
     * <p>Not {@code Integer.MAX_VALUE}: ages are interpolated as floats, and past sixteen million a
     * float cannot tell one tick from the next. Anything a gate does would still be exact after the
     * collapse cuts the hold short, but a number nothing can count in is a trap for the next person.
     */
    private static final int HELD_FOREVER = 10_000_000;

    /** Ticks a gate's aperture takes to seal. Matches the block entity's own closing state. */
    private static final int GATE_CLOSE_TICKS = 20;

    /** How far down the bore the light at the end of it sits. */
    private static final float FAR_LIGHT = 0.985F;

    /** Half-width of a crack where it leaves the impact, as a fraction of the aperture. */
    private static final float CRACK_ROOT = 0.030F;
    /** Half-width of a crack at its running tip. A fracture narrows as it travels. */
    private static final float CRACK_TIP = 0.006F;

    private static final List<ActiveRift> ACTIVE = new ArrayList<>();

    private RiftEffectManager() {
    }

    /** One rift, from the moment it tears open to the moment it collapses. */
    private static final class ActiveRift {
        final Vec3 centre;
        final Vector3f normal;
        final Vector3f right;
        final Vector3f up;
        double radius;
        final int colour;
        final int openTicks;
        /** Not final: a collapse cuts the hold short at the moment it happens. See {@link #collapse}. */
        int holdTicks;
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
        /** The fracture this aperture breaks along, worked out from where it is. */
        final int seed;
        final RiftShatter.Shard[] shards;
        /**
         * The rim's wander frozen at the moment the pane breaks.
         *
         * <p>Shards keep the silhouette they were cut from. Letting the rim carry on wandering under
         * them would make every piece of glass breathe in step with the hole it left, which is not a
         * thing broken glass does.
         */
        final float rimTime;
        /** Whether the pane has gone yet, so the break is announced exactly once. */
        boolean broken;
        /** What is loose inside the bore, if this aperture ever grows one. */
        final RiftDebris.Mote[] motes;

        /**
         * How much taller than wide the aperture is.
         *
         * <p>A drive tears a circle because nothing constrains its shape. A gate's aperture is the
         * hole a player built, so it has to be able to be a rectangle's worth of ellipse instead.
         */
        float aspect = 1.0F;

        /**
         * Ticks left before a held aperture gives up waiting to be renewed, or {@code 0} for one
         * running on its own clock.
         *
         * <p>Gates are open for as long as they are open, which is not a length of time anything can
         * know in advance. Rather than an explicit close - which can be missed by a chunk unload, a
         * disconnect or a gate somebody else broke - a held aperture has to be told it still exists.
         * Something that must be renewed cannot leak; it can only stop.
         */
        int keepAlive;
        /** Identity of the block holding this aperture open, or {@code 0} for a warp's own rift. */
        long holder;

        ActiveRift(Vec3 centre, Vec3 normal, double radius, int colour, float throat,
                   int openTicks, int holdTicks, int closeTicks) {
            this.centre = centre;
            this.radius = radius;
            this.colour = colour;
            this.throat = throat;
            this.openTicks = openTicks;
            this.holdTicks = holdTicks;
            this.closeTicks = closeTicks;
            this.seed = RiftShatter.seedFor(centre.x, centre.y, centre.z);
            this.shards = RiftShatter.fracture(seed);
            this.motes = RiftDebris.field(seed);
            this.rimTime = openTicks * RiftShatter.CRACK_PHASE * 0.12F;

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
            // An aperture called off while it was still cracking never broke, and must not be heard
            // breaking: cutting the hold below would otherwise take it past the moment that fires.
            broken = true;
            if (age >= openTicks + holdTicks) {
                return; // already letting go
            }
            // The hold is ended here rather than the clock being wound forward to the end of it.
            // A held aperture holds for days, and an age out at ten million has less float precision
            // than the close is long - the whole twenty ticks of it would land inside one
            // representable step, and the hole would snap shut instead of closing.
            holdTicks = Math.max(0, age - openTicks);
            // Land the previous age on the new one too, or the next frame interpolates across the
            // change and the aperture appears to flinch before it collapses.
            lastAge = age;
        }

        /**
         * 0..1 aperture size: nothing at all while the pane is only cracking, then a hole.
         *
         * <p>The opening half of this is {@link RiftShatter#hole}, which holds at zero for the whole
         * crack phase. Everything that draws the aperture itself - face, throat, fire, haze - is gated
         * on this, so during the cracking there is simply nothing there but the fracture and the world
         * still visible behind it.
         */
        float aperture(float partialTick) {
            float time = Mth.lerp(partialTick, lastAge, age);
            if (time < openTicks) {
                return RiftShatter.hole(openProgress(partialTick));
            }
            if (time < openTicks + holdTicks) {
                return 1.0F;
            }
            float t = (time - openTicks - holdTicks) / Math.max(1.0F, closeTicks);
            return Math.max(0.0F, 1.0F - t * t); // collapses faster than it opened
        }

        /** 0..1 progress through the opening, whatever the opening is worth in ticks. */
        float openProgress(float partialTick) {
            if (openTicks <= 0) {
                return 1.0F;
            }
            return Mth.clamp(Mth.lerp(partialTick, lastAge, age) / openTicks, 0.0F, 1.0F);
        }

        /** Ticks since the pane began to break. Negative while it is still only cracking. */
        float sinceBreak(float partialTick) {
            return Mth.lerp(partialTick, lastAge, age) - openTicks * RiftShatter.CRACK_PHASE;
        }

        /** 0..1 through the close, or {@code 0} while the aperture is still standing. */
        float sealProgress(float partialTick) {
            float time = Mth.lerp(partialTick, lastAge, age);
            float elapsed = time - openTicks - holdTicks;
            return elapsed <= 0.0F ? 0.0F : Math.min(1.0F, elapsed / Math.max(1.0F, closeTicks));
        }

        /** Whether the aperture has been told to let go, however far through that it is. */
        boolean isCollapsing() {
            return age >= openTicks + holdTicks;
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
     * Keeps a gate's aperture up for another moment.
     *
     * <p>Called from the gate's own client tick rather than pushed by the server, so a player walking
     * up to an aperture that opened before they arrived sees it. Creates one if there is not one
     * already, and otherwise brings its size and shape up to date - a gate whose ring is rebuilt while
     * it stands open changes shape under the player rather than needing to be closed and reopened.
     */
    public static void hold(long holder, Vec3 centre, Vec3 normal, double halfWidth, double halfHeight,
                            int colour, int openTicks) {
        if (!AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
        for (ActiveRift rift : ACTIVE) {
            if (rift.holder == holder) {
                rift.keepAlive = HOLD_TICKS;
                rift.radius = halfWidth;
                rift.aspect = (float) (halfHeight / Math.max(1.0e-3D, halfWidth));
                return;
            }
        }
        ActiveRift rift = new ActiveRift(centre, normal, halfWidth, colour, 0.0F,
                Math.max(1, openTicks), HELD_FOREVER, GATE_CLOSE_TICKS);
        rift.aspect = (float) (halfHeight / Math.max(1.0e-3D, halfWidth));
        rift.holder = holder;
        rift.keepAlive = HOLD_TICKS;
        ACTIVE.add(rift);
    }

    /**
     * Starts a held aperture closing, and keeps it alive long enough to be seen doing it.
     *
     * <p>Separate from {@link #release} because a gate spends a second in its closing state before it
     * is idle, and that second is the whole of the animation. Releasing it only at the end meant the
     * aperture stood at full size for the entire close and then shrank afterwards, which reads as a
     * gate that shuts a beat late.
     */
    public static void seal(long holder) {
        for (ActiveRift rift : ACTIVE) {
            if (rift.holder == holder) {
                rift.keepAlive = HOLD_TICKS;
                if (!rift.isCollapsing()) {
                    rift.collapse();
                }
            }
        }
    }

    /** Lets a held aperture go, without waiting for it to notice it has been forgotten. */
    public static void release(long holder) {
        for (ActiveRift rift : ACTIVE) {
            if (rift.holder == holder) {
                rift.keepAlive = 0;
                rift.collapse();
            }
        }
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
        // A hull has arrived, so the aperture stops animating and is simply there. Hiding a ship is
        // the face's job and it cannot do it half open - the same reasoning as the throat below. This
        // is what stops a short run at the rift, or a brisk one, from catching the shatter mid-break
        // and flying a visible hull through a hole that has not finished appearing.
        if (rift.age < rift.openTicks) {
            rift.age = rift.openTicks;
            rift.lastAge = rift.age;
        }
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
            if (!rift.broken && rift.age >= rift.openTicks * RiftShatter.CRACK_PHASE) {
                // The pane goes. Announced once, from the client that is watching it, because the
                // fracture is worked out from the rift's position and needs nothing from the server.
                rift.broken = true;
                // Volume over one is range rather than loudness in Minecraft, so a big aperture is
                // heard breaking from further off rather than more sharply from close up.
                level.playLocalSound(rift.centre.x, rift.centre.y, rift.centre.z,
                        SoundEvents.GLASS_BREAK, SoundSource.BLOCKS,
                        (float) Math.min(4.0D, 1.6D + rift.radius * 0.10D), 0.45F, true);
            }
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
            // A held aperture that has stopped being renewed is one whose gate has closed, been
            // broken, or gone out of range. Either way it is nobody's any more, so it collapses.
            if (rift.holder != 0L && rift.keepAlive > 0 && --rift.keepAlive == 0) {
                rift.collapse();
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
        Matrix4f matrix = poseStack.last().pose();

        if (membranePass) {
            // The face goes down with the world's blocks so it can occlude them. Throat first, then
            // the face over its mouth: the face is what a viewer looking straight down the aperture
            // sees, and it must win.
            VertexConsumer consumer = buffers.getBuffer(AWRenderTypes.RIFT_MEMBRANE);
            for (ActiveRift rift : ACTIVE) {
                drawThroat(consumer, matrix, rift, partialTick);
                drawFace(consumer, matrix, rift, partialTick);
            }
            buffers.endBatch(AWRenderTypes.RIFT_MEMBRANE);
        } else {
            // Additive light over everything, including the face it is burning around.
            VertexConsumer fire = buffers.getBuffer(RenderType.lightning());
            for (ActiveRift rift : ACTIVE) {
                drawHaze(fire, matrix, rift, partialTick);
                drawFarLight(fire, matrix, rift, partialTick);
                drawFire(fire, matrix, rift, partialTick, eye);
                drawCracks(fire, matrix, rift, partialTick, eye);
                drawSpark(fire, matrix, rift, partialTick, eye);
            }
            buffers.endBatch(RenderType.lightning());

            // Glass last, and translucent rather than additive: a shard passing in front of a burning
            // rim should darken it, not add to it, or every piece disappears into the light it came
            // off.
            VertexConsumer glass = buffers.getBuffer(AWRenderTypes.RIFT_SHARD);
            for (ActiveRift rift : ACTIVE) {
                drawShards(glass, matrix, rift, partialTick, eye);
                drawSeal(glass, matrix, rift, partialTick, eye);
                drawMotes(glass, matrix, rift, partialTick, eye);
            }
            buffers.endBatch(AWRenderTypes.RIFT_SHARD);
        }

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

        float band = bandPosition(rift, partialTick);

        for (int ring = 0; ring < THROAT_RINGS.length - 1; ring++) {
            float nearT = THROAT_RINGS[ring];
            float farT = THROAT_RINGS[ring + 1];
            // Every few ribs is a gate: a bright ring rather than another stripe. A hull passing
            // through one of these is the clearest speed cue the corridor has, because it is a
            // discrete event at a known distance rather than a gradient sliding by.
            float nearGlow = Math.min(1.0F, throatGlow(nearT, band)
                    + (ring % GATE_EVERY == 0 ? GATE_GLOW : 0.0F));
            float farGlow = Math.min(1.0F, throatGlow(farT, band)
                    + ((ring + 1) % GATE_EVERY == 0 ? GATE_GLOW : 0.0F));
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

    /**
     * The fracture, before anything has broken loose.
     *
     * <p>A hard point of impact and cracks racing out from it over an intact view - the hole does not
     * exist yet, and the world behind the aperture is still there to see. This is the whole of what an
     * opening rift looks like for its first half second, and it is the reason the hole arriving lands
     * as an event rather than as a shape growing.
     */
    private static void drawCracks(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   float partialTick, Vec3 eye) {
        float progress = rift.openProgress(partialTick);
        if (progress >= 1.0F) {
            return;
        }
        float crackProgress = progress / RiftShatter.CRACK_PHASE;
        // Cracks outlive the break by a moment and are then gone. Past that they are shard edges, and
        // drawing them as well would leave a wheel of spokes hanging in an empty hole.
        float alpha = crackProgress <= 1.0F ? 1.0F : 1.0F - (crackProgress - 1.0F) * 4.0F;
        if (alpha <= 0.0F) {
            return;
        }
        float reach = RiftShatter.crackReach(crackProgress);

        double towardsEye = (eye.x - rift.centre.x) * rift.normal.x
                + (eye.y - rift.centre.y) * rift.normal.y
                + (eye.z - rift.centre.z) * rift.normal.z;
        float bias = towardsEye >= 0.0D ? FIRE_BIAS : -FIRE_BIAS;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        // Full radius, not the aperture's: the pane that is breaking is already the size of the hole
        // it is about to become. The hole then opens outwards to meet the pieces leaving it.
        double cover = rift.radius;

        for (float angle : RiftShatter.crackAngles(rift.seed)) {
            double along = Math.cos(angle);
            double across = Math.sin(angle);
            double sideU = -across;
            double sideV = along;
            double tip = cover * RiftTear.rim(angle, rift.rimTime) * reach;
            double root = cover * CRACK_ROOT;
            double point = cover * CRACK_TIP;

            // White at the impact and cooling to the rift's own colour as it runs, so the eye reads
            // the direction the fracture travelled rather than a static star.
            localVertex(consumer, matrix, rift, sideU * root, sideV * root, bias,
                    1.0F, 1.0F, 1.0F, alpha * 0.9F);
            localVertex(consumer, matrix, rift, -sideU * root, -sideV * root, bias,
                    1.0F, 1.0F, 1.0F, alpha * 0.9F);
            localVertex(consumer, matrix, rift, along * tip - sideU * point, across * tip - sideV * point,
                    bias, red, green, blue, alpha * 0.15F);
            localVertex(consumer, matrix, rift, along * tip + sideU * point, across * tip + sideV * point,
                    bias, red, green, blue, alpha * 0.15F);
        }

        // The strike itself: brightest at the instant of impact and gone by the time the cracks have
        // run, which is what makes the middle read as where all this started.
        float flash = Math.max(0.0F, 1.0F - crackProgress) * alpha;
        if (flash > 0.0F) {
            double size = cover * (0.06D + 0.10D * flash);
            localVertex(consumer, matrix, rift, 0.0D, size, bias, 1.0F, 1.0F, 1.0F, flash);
            localVertex(consumer, matrix, rift, size, 0.0D, bias, 1.0F, 1.0F, 1.0F, flash * 0.55F);
            localVertex(consumer, matrix, rift, 0.0D, -size, bias, 1.0F, 1.0F, 1.0F, flash);
            localVertex(consumer, matrix, rift, -size, 0.0D, bias, 1.0F, 1.0F, 1.0F, flash * 0.55F);
        }
    }

    /**
     * The pane falling away.
     *
     * <p>Every piece is the cell it was cut from, thrown outwards and tumbling. The tumble is what
     * does the work here: a shard catches the light when it turns face-on and all but disappears
     * edge-on, so a field of them glitters as it drifts instead of hanging there as a cloud of
     * coloured quads.
     */
    private static void drawShards(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   float partialTick, Vec3 eye) {
        float since = rift.sinceBreak(partialTick);
        if (since <= 0.0F || since > RiftShatter.SHARD_LIFE + 8.0F) {
            return;
        }

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;
        double cover = rift.radius;

        for (RiftShatter.Shard shard : rift.shards) {
            float elapsed = since - shard.delay();
            float fade = RiftShatter.fade(elapsed / RiftShatter.SHARD_LIFE);
            if (fade <= 0.0F) {
                continue;
            }
            float travel = RiftShatter.travel(elapsed / RiftShatter.SHARD_LIFE);

            double rim0 = RiftTear.rim(shard.angle0(), rift.rimTime);
            double rim1 = RiftTear.rim(shard.angle1(), rift.rimTime);
            double cos0 = Math.cos(shard.angle0());
            double sin0 = Math.sin(shard.angle0());
            double cos1 = Math.cos(shard.angle1());
            double sin1 = Math.sin(shard.angle1());

            double near0 = cover * rim0 * shard.innerT();
            double far0 = cover * rim0 * shard.outerT();
            double near1 = cover * rim1 * shard.innerT();
            double far1 = cover * rim1 * shard.outerT();

            double u0 = cos0 * near0;
            double v0 = sin0 * near0;
            double u1 = cos1 * near1;
            double v1 = sin1 * near1;
            double u2 = cos1 * far1;
            double v2 = sin1 * far1;
            double u3 = cos0 * far0;
            double v3 = sin0 * far0;

            double centreU = (u0 + u1 + u2 + u3) * 0.25D;
            double centreV = (v0 + v1 + v2 + v3) * 0.25D;

            // The piece keeps its own frame and turns in it, so the corners stay a rigid shape rather
            // than shearing the way rotating each corner about the centre would.
            float turn = shard.spin() * Math.max(0.0F, elapsed);
            float axisU = Mth.cos(shard.axis());
            float axisV = Mth.sin(shard.axis());
            Vector3f alongU = new Vector3f(1.0F, 0.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
            Vector3f alongV = new Vector3f(0.0F, 1.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
            Vector3f facing = new Vector3f(0.0F, 0.0F, 1.0F).rotateAxis(turn, axisU, axisV, 0.0F);

            double outward = shard.outward() * cover * travel;
            double baseU = centreU + Math.cos(shard.midAngle()) * outward;
            double baseV = centreV + Math.sin(shard.midAngle()) * outward;
            // Signed, so a pane bursts both ways rather than all of it coming at the viewer.
            double baseW = shard.push() * cover * 0.45D * travel;

            float sheen = glint(rift, eye, facing, baseU, baseV, baseW);
            float lit = 0.45F + 0.55F * sheen;
            float white = sheen * sheen;
            float alpha = fade * (0.22F + 0.78F * sheen) * 0.85F;
            float shardRed = Mth.lerp(white, red, 1.0F) * lit;
            float shardGreen = Mth.lerp(white, green, 1.0F) * lit;
            float shardBlue = Mth.lerp(white, blue, 1.0F) * lit;

            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u0 - centreU, v0 - centreV, shardRed, shardGreen, shardBlue, alpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u1 - centreU, v1 - centreV, shardRed, shardGreen, shardBlue, alpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u2 - centreU, v2 - centreV, shardRed, shardGreen, shardBlue, alpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u3 - centreU, v3 - centreV, shardRed, shardGreen, shardBlue, alpha);
        }
    }

    /**
     * Space closing over the hole.
     *
     * <p>The same fracture the aperture broke along, run the other way: every piece comes back out of
     * the dark, turning as it falls, and lands where it was cut from. Not the opening played in
     * reverse, which reads as a rewind - the pieces arrive from outside rather than retracing the
     * paths they left by, and they come home from the rim inwards so the hole shuts down to a point.
     */
    private static void drawSeal(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                 float partialTick, Vec3 eye) {
        float progress = rift.sealProgress(partialTick);
        if (progress <= 0.0F || progress >= 1.0F) {
            return;
        }

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;
        // Sized off the aperture as it was, not as it is. The hole is shrinking under the glass, and
        // shards that shrank with it would look like a picture of a rift rather than pieces of one.
        double cover = rift.radius;

        for (RiftShatter.Shard shard : rift.shards) {
            float life = RiftShatter.sealLife(progress, shard.midRadius());
            float fade = RiftShatter.sealFade(life);
            if (fade <= 0.0F) {
                continue;
            }
            // One at the rim, nothing at home: the piece falls inwards as its life runs out.
            float out = 1.0F - RiftShatter.travel(life);

            double rim0 = RiftTear.rim(shard.angle0(), rift.rimTime);
            double rim1 = RiftTear.rim(shard.angle1(), rift.rimTime);
            double cos0 = Math.cos(shard.angle0());
            double sin0 = Math.sin(shard.angle0());
            double cos1 = Math.cos(shard.angle1());
            double sin1 = Math.sin(shard.angle1());

            double near0 = cover * rim0 * shard.innerT();
            double far0 = cover * rim0 * shard.outerT();
            double near1 = cover * rim1 * shard.innerT();
            double far1 = cover * rim1 * shard.outerT();

            double u0 = cos0 * near0;
            double v0 = sin0 * near0;
            double u1 = cos1 * near1;
            double v1 = sin1 * near1;
            double u2 = cos1 * far1;
            double v2 = sin1 * far1;
            double u3 = cos0 * far0;
            double v3 = sin0 * far0;

            double centreU = (u0 + u1 + u2 + u3) * 0.25D;
            double centreV = (v0 + v1 + v2 + v3) * 0.25D;

            // Still turning as it comes in, and square by the time it lands.
            float turn = shard.spin() * out * RiftShatter.BREAK_SPREAD * 3.0F;
            float axisU = Mth.cos(shard.axis());
            float axisV = Mth.sin(shard.axis());
            Vector3f alongU = new Vector3f(1.0F, 0.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
            Vector3f alongV = new Vector3f(0.0F, 1.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
            Vector3f facing = new Vector3f(0.0F, 0.0F, 1.0F).rotateAxis(turn, axisU, axisV, 0.0F);

            double outward = shard.outward() * cover * out;
            double baseU = centreU + Math.cos(shard.midAngle()) * outward;
            double baseV = centreV + Math.sin(shard.midAngle()) * outward;
            double baseW = shard.push() * cover * 0.45D * out;

            float sheen = glint(rift, eye, facing, baseU, baseV, baseW);
            float white = sheen * sheen;
            float lit = 0.45F + 0.55F * sheen;
            float alpha = fade * (0.22F + 0.78F * sheen) * 0.9F;
            float shardRed = Mth.lerp(white, red, 1.0F) * lit;
            float shardGreen = Mth.lerp(white, green, 1.0F) * lit;
            float shardBlue = Mth.lerp(white, blue, 1.0F) * lit;

            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u0 - centreU, v0 - centreV, shardRed, shardGreen, shardBlue, alpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u1 - centreU, v1 - centreV, shardRed, shardGreen, shardBlue, alpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u2 - centreU, v2 - centreV, shardRed, shardGreen, shardBlue, alpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    u3 - centreU, v3 - centreV, shardRed, shardGreen, shardBlue, alpha);
        }
    }

    /**
     * The moment it seals.
     *
     * <p>A hole that shrinks to nothing has no ending - it is simply smaller and smaller until it is
     * not there, and the eye cannot tell the last frame from the one before. A flash on the last of
     * the close gives the closing somewhere to arrive, the way the impact gives the opening somewhere
     * to start.
     */
    private static void drawSpark(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                  float partialTick, Vec3 eye) {
        float progress = rift.sealProgress(partialTick);
        if (progress <= SEAL_SPARK) {
            return;
        }
        float flash = Mth.clamp((progress - SEAL_SPARK) / (1.0F - SEAL_SPARK), 0.0F, 1.0F);
        // Brightest at the instant of sealing and gone immediately after, rather than a glow that
        // lingers on a hole which no longer exists.
        float alpha = Mth.sin(flash * (float) Math.PI);
        if (alpha <= 0.001F) {
            return;
        }

        double towardsEye = (eye.x - rift.centre.x) * rift.normal.x
                + (eye.y - rift.centre.y) * rift.normal.y
                + (eye.z - rift.centre.z) * rift.normal.z;
        float bias = towardsEye >= 0.0D ? FIRE_BIAS : -FIRE_BIAS;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;
        double size = rift.radius * (0.05D + 0.30D * flash);

        localVertex(consumer, matrix, rift, 0.0D, size, bias, 1.0F, 1.0F, 1.0F, alpha);
        localVertex(consumer, matrix, rift, size, 0.0D, bias, red, green, blue, alpha * 0.5F);
        localVertex(consumer, matrix, rift, 0.0D, -size, bias, 1.0F, 1.0F, 1.0F, alpha);
        localVertex(consumer, matrix, rift, -size, 0.0D, bias, red, green, blue, alpha * 0.5F);
    }

    /**
     * How square-on a tumbling shard is to the viewer, 0..1.
     *
     * <p>This is the whole of what makes the pieces read as glass. Brightness keyed to the angle a
     * fragment happens to be turned through means the field flashes as it drifts, and a flash is the
     * one thing that says "hard reflective surface" without a texture to say it with.
     */
    private static float glint(ActiveRift rift, Vec3 eye, Vector3f facing,
                               double u, double v, double w) {
        v *= rift.aspect;
        double x = rift.centre.x + rift.right.x * u + rift.up.x * v + rift.normal.x * w;
        double y = rift.centre.y + rift.right.y * u + rift.up.y * v + rift.normal.y * w;
        double z = rift.centre.z + rift.right.z * u + rift.up.z * v + rift.normal.z * w;
        double toEyeX = eye.x - x;
        double toEyeY = eye.y - y;
        double toEyeZ = eye.z - z;
        double length = Math.sqrt(toEyeX * toEyeX + toEyeY * toEyeY + toEyeZ * toEyeZ);
        if (length < 1.0e-6D) {
            return 1.0F;
        }
        double normalX = rift.right.x * facing.x + rift.up.x * facing.y + rift.normal.x * facing.z;
        double normalY = rift.right.y * facing.x + rift.up.y * facing.y + rift.normal.y * facing.z;
        double normalZ = rift.right.z * facing.x + rift.up.z * facing.y + rift.normal.z * facing.z;
        double dot = (normalX * toEyeX + normalY * toEyeY + normalZ * toEyeZ) / length;
        return (float) Math.min(1.0D, Math.abs(dot));
    }

    /** One corner of a shard, placed in the piece's own turned frame. */
    private static void corner(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                               double baseU, double baseV, double baseW,
                               Vector3f alongU, Vector3f alongV, double offsetU, double offsetV,
                               float red, float green, float blue, float alpha) {
        localVertex(consumer, matrix, rift,
                baseU + alongU.x * offsetU + alongV.x * offsetV,
                baseV + alongU.y * offsetU + alongV.y * offsetV,
                baseW + alongU.z * offsetU + alongV.z * offsetV,
                red, green, blue, alpha);
    }

    /** A vertex at {@code (u, v)} in the aperture's plane, {@code w} along its normal. */
    private static void localVertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                    double u, double v, double w,
                                    float red, float green, float blue, float alpha) {
        // The up axis carries the aperture's aspect, so the glass, the debris and the cracks are all
        // squashed to the same shape as the hole they belong to rather than sitting circular inside a
        // rectangular one.
        v *= rift.aspect;
        float x = (float) (rift.centre.x + rift.right.x * u + rift.up.x * v + rift.normal.x * w);
        float y = (float) (rift.centre.y + rift.right.y * u + rift.up.y * v + rift.normal.y * w);
        float z = (float) (rift.centre.z + rift.right.z * u + rift.up.z * v + rift.normal.z * w);
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, Math.min(1.0F, alpha));
    }

    /**
     * Where the pulse of light running the bore has got to, 0..1.
     *
     * <p>It travels away from the mouth on an aperture a ship goes into and towards it on one a ship
     * comes out of, so the tunnel always shows the direction of travel rather than merely being lit.
     * Shared, because the debris in the bore has to be lit by the same pulse that lights its walls -
     * a pulse that swept over the wall and left the things floating in front of it unchanged would
     * give the whole effect away as paint.
     */
    private static float bandPosition(ActiveRift rift, float partialTick) {
        float band = (rift.lastAge + partialTick) * 0.022F % 1.0F;
        return rift.throat < 0.0F ? 1.0F - band : band;
    }

    /**
     * The light at the end of the tunnel.
     *
     * <p>The bore closes on a cosine and fades out before it does, which stops it reading as a bag -
     * but "not obviously ending" is not the same as going somewhere. A light at the far end gives the
     * corridor a destination, and gives the crew something that grows as they close on it, which is
     * the only progress cue available in a place with no landmarks.
     */
    private static void drawFarLight(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                     float partialTick) {
        float open = Mth.lerp(partialTick, rift.throatOpenLast, rift.throatOpen);
        if (open <= 0.001F || rift.throat == 0.0F) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        double cover = rift.radius * aperture;
        double along = rift.throat * open * FAR_LIGHT;
        // Sized off the bore where it actually sits. The throat has nearly closed by this depth, and
        // a light scaled off the mouth would hang well outside the cone - visible from the world as a
        // glowing ring around a tube that is supposed to be tapering quietly shut.
        double bore = cover * throatWidth(FAR_LIGHT);
        // A slow breath, so the far end is alive rather than a decal pasted on the end of a pipe.
        float pulse = 0.85F + 0.15F * Mth.sin((rift.lastAge + partialTick) * 0.09F);
        float alpha = aperture * open * pulse;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        // A white core inside a halo of the rift's own colour: the core is what you aim at and the
        // halo is what makes it look like light rather than a disc.
        double core = bore * 0.55D * pulse;
        disc(consumer, matrix, rift, 0.0D, core, along,
                1.0F, 1.0F, 1.0F, alpha, 1.0F, 1.0F, 1.0F, alpha * 0.8F);
        disc(consumer, matrix, rift, core, bore * 1.7D, along,
                1.0F, 1.0F, 1.0F, alpha * 0.8F, red, green, blue, 0.0F);
    }

    /** A flat annulus across the bore, for the light at the far end of it. */
    private static void disc(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                             double inner, double outer, double along,
                             float innerRed, float innerGreen, float innerBlue, float innerAlpha,
                             float outerRed, float outerGreen, float outerBlue, float outerAlpha) {
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D;
            double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D;
            localVertex(consumer, matrix, rift, Math.cos(a0) * inner, Math.sin(a0) * inner, along,
                    innerRed, innerGreen, innerBlue, innerAlpha);
            localVertex(consumer, matrix, rift, Math.cos(a1) * inner, Math.sin(a1) * inner, along,
                    innerRed, innerGreen, innerBlue, innerAlpha);
            localVertex(consumer, matrix, rift, Math.cos(a1) * outer, Math.sin(a1) * outer, along,
                    outerRed, outerGreen, outerBlue, outerAlpha);
            localVertex(consumer, matrix, rift, Math.cos(a0) * outer, Math.sin(a0) * outer, along,
                    outerRed, outerGreen, outerBlue, outerAlpha);
        }
    }

    /**
     * What is loose in the bore, passing the hull.
     *
     * <p>Almost all of it is fixed in the tunnel and lets the ship supply the motion - see
     * {@link RiftDebris} for why that is the only way it can be seen at all. Glass glints as it turns;
     * wreckage is drawn dark and silhouettes against the lit wall behind it, which is what keeps the
     * two kinds of thing telling apart at a glance.
     */
    private static void drawMotes(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                  float partialTick, Vec3 eye) {
        float open = Mth.lerp(partialTick, rift.throatOpenLast, rift.throatOpen);
        if (open <= 0.001F || rift.throat == 0.0F) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        float time = (rift.lastAge + partialTick) * 0.12F;
        float clock = Mth.lerp(partialTick, rift.lastAge, rift.age);
        float band = bandPosition(rift, partialTick);
        double cover = rift.radius * aperture;
        double depth = rift.throat * open;

        float red = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float green = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float blue = (rift.colour & 0xFF) / 255.0F;

        for (RiftDebris.Mote mote : rift.motes) {
            float at = RiftDebris.along(mote, clock);
            float width = throatWidth(at);
            if (width <= 0.02F) {
                continue; // inside the cone where the bore has already closed
            }

            double bore = cover * RiftTear.rim(mote.angle(), time) * width;
            double u = Math.cos(mote.angle()) * bore * mote.radius();
            double v = Math.sin(mote.angle()) * bore * mote.radius();
            double w = depth * at;
            // Lit by the bore it is in, running band included, so a piece brightens as the pulse
            // reaches it instead of being evenly lit in a tunnel that plainly is not.
            float lit = throatGlow(at, band);

            if (mote.streak()) {
                streak(consumer, matrix, rift, mote, u, v, w, depth, cover, lit * open, eye,
                        red, green, blue);
                continue;
            }

            float turn = mote.spin() * clock;
            float axisU = Mth.cos(mote.axis());
            float axisV = Mth.sin(mote.axis());
            Vector3f alongU = new Vector3f(1.0F, 0.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
            Vector3f alongV = new Vector3f(0.0F, 1.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
            Vector3f facing = new Vector3f(0.0F, 0.0F, 1.0F).rotateAxis(turn, axisU, axisV, 0.0F);

            float sheen = glint(rift, eye, facing, u, v, w);
            double half = cover * mote.size();

            float moteRed;
            float moteGreen;
            float moteBlue;
            float alpha;
            if (mote.glass()) {
                float white = sheen * sheen;
                float shine = 0.35F + 0.65F * white;
                moteRed = Mth.lerp(white, red, 1.0F) * shine;
                moteGreen = Mth.lerp(white, green, 1.0F) * shine;
                moteBlue = Mth.lerp(white, blue, 1.0F) * shine;
                alpha = open * (0.16F + 0.84F * white);
            } else {
                // Wreckage is a lump, not a mirror. Dark and nearly solid, so it reads as a shape
                // crossing the light rather than as another glowing thing among many.
                moteRed = red * 0.22F;
                moteGreen = green * 0.22F;
                moteBlue = blue * 0.22F;
                alpha = open * 0.78F;
            }
            // Fade with the bore, or a dark piece at the unlit far end is a black hole in a black
            // tunnel, and a bright one is a light with nothing around it.
            alpha *= 0.25F + 0.75F * lit;

            corner(consumer, matrix, rift, u, v, w, alongU, alongV, -half, -half,
                    moteRed, moteGreen, moteBlue, alpha);
            corner(consumer, matrix, rift, u, v, w, alongU, alongV, half, -half,
                    moteRed, moteGreen, moteBlue, alpha);
            corner(consumer, matrix, rift, u, v, w, alongU, alongV, half, half,
                    moteRed, moteGreen, moteBlue, alpha);
            corner(consumer, matrix, rift, u, v, w, alongU, alongV, -half, half,
                    moteRed, moteGreen, moteBlue, alpha);
        }
    }

    /**
     * One of the few pieces with real speed, drawn as the line it would leave.
     *
     * <p>Turned to face the viewer, because a flat ribbon seen edge-on is nothing at all and this one
     * is only on screen for a moment. It fades along its length: the head is where the thing is and
     * the tail is where it was.
     */
    private static void streak(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                               RiftDebris.Mote mote, double u, double v, double w,
                               double depth, double cover, float lit, Vec3 eye,
                               float red, float green, float blue) {
        // The trail lies behind the direction of travel, whichever way that is down this bore.
        double tail = w - depth * RiftDebris.streakLength(mote) * Math.signum(mote.drift());

        double headX = rift.centre.x + rift.right.x * u + rift.up.x * v + rift.normal.x * w;
        double headY = rift.centre.y + rift.right.y * u + rift.up.y * v + rift.normal.y * w;
        double headZ = rift.centre.z + rift.right.z * u + rift.up.z * v + rift.normal.z * w;
        double tailX = rift.centre.x + rift.right.x * u + rift.up.x * v + rift.normal.x * tail;
        double tailY = rift.centre.y + rift.right.y * u + rift.up.y * v + rift.normal.y * tail;
        double tailZ = rift.centre.z + rift.right.z * u + rift.up.z * v + rift.normal.z * tail;

        double toEyeX = eye.x - headX;
        double toEyeY = eye.y - headY;
        double toEyeZ = eye.z - headZ;
        // Across both the bore and the line of sight, which is the one direction that gives a ribbon
        // its full width however the viewer is standing.
        double sideX = rift.normal.y * toEyeZ - rift.normal.z * toEyeY;
        double sideY = rift.normal.z * toEyeX - rift.normal.x * toEyeZ;
        double sideZ = rift.normal.x * toEyeY - rift.normal.y * toEyeX;
        double length = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (length < 1.0e-6D) {
            return; // looking straight down the bore: the ribbon has no width to show
        }
        double half = cover * mote.size() / length;
        sideX *= half;
        sideY *= half;
        sideZ *= half;

        float alpha = (0.35F + 0.65F * lit) * 0.9F;
        worldVertex(consumer, matrix, headX + sideX, headY + sideY, headZ + sideZ, 1.0F, 1.0F, 1.0F, alpha);
        worldVertex(consumer, matrix, headX - sideX, headY - sideY, headZ - sideZ, 1.0F, 1.0F, 1.0F, alpha);
        worldVertex(consumer, matrix, tailX - sideX, tailY - sideY, tailZ - sideZ, red, green, blue, 0.0F);
        worldVertex(consumer, matrix, tailX + sideX, tailY + sideY, tailZ + sideZ, red, green, blue, 0.0F);
    }

    private static void worldVertex(VertexConsumer consumer, Matrix4f matrix,
                                    double x, double y, double z,
                                    float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, Math.min(1.0F, alpha));
    }

    private static void throatVertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                     double angle, double radius, double along,
                                     float red, float green, float blue, float glow) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius * rift.aspect;
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
        double sin = Math.sin(angle) * radius * rift.aspect;
        float x = (float) (rift.centre.x + rift.right.x * cos + rift.up.x * sin) + rift.normal.x * bias;
        float y = (float) (rift.centre.y + rift.right.y * cos + rift.up.y * sin) + rift.normal.y * bias;
        float z = (float) (rift.centre.z + rift.right.z * cos + rift.up.z * sin) + rift.normal.z * bias;
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }
}
