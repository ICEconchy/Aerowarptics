package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
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

    /** Sky and block light written on every glow vertex. A rift lights itself. */
    private static final int GLOW_LIGHT = 240;

    /** Render-thread scratch for {@link #place}, so a few thousand vertices are not a few thousand objects. */
    private static final Vector3f POINT = new Vector3f();

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

    /** How far a charring piece rises as it burns, as a fraction of the aperture's cover. */
    private static final float CHAR_LIFT = 0.55F;

    /** How far an improbable piece that decides to drip sags, as a fraction of the aperture's cover. */
    private static final double IMPROBABLE_DRIP = 0.42D;

    /**
     * How far the long axis of an elongating aperture draws out, as a multiple of the rim.
     *
     * <p>Well over one on purpose: the lens has to leave its own circle convincingly, or it reads as
     * a rift that wobbled rather than one that stretched.
     */
    private static final float LENS_REACH = 2.2F;

    /**
     * Steps a stuttering piece travels in.
     *
     * <p>Five is the count that reads as failing to arrive. Fewer looks like a slideshow, and by about
     * eight the gaps are shorter than the eye resolves and the whole thing goes back to being smooth
     * motion with an odd flicker on it.
     */
    private static final int STUTTER_STEPS = 5;

    /** How many ways an improbable piece can choose to leave. */
    private static final int IMPROBABLE_WAYS = 5;

    /** Half-width of a crack where it leaves the impact, as a fraction of the aperture. */
    private static final float CRACK_ROOT = 0.030F;
    /** Half-width of a crack at its running tip. A fracture narrows as it travels. */
    private static final float CRACK_TIP = 0.006F;


    /** How much further out than the rim a bolt off the aperture reaches, as a fraction of it. */
    private static final float LIGHTNING_REACH = 0.55F;
    /** Half-width of a bolt at its root, as a fraction of the aperture's cover. */
    private static final float LIGHTNING_ROOT = 0.026F;
    /** Half-width of a bolt at its tip. A discharge narrows as it runs, the same as a crack does. */
    private static final float LIGHTNING_TIP = 0.005F;
    /** Maximum sideways kick a segment of a bolt off the rim takes, in radians. */
    private static final float LIGHTNING_APERTURE_JITTER = 0.11F;
    /**
     * How far a corridor bolt dips towards the axis at the middle of its arc, as a fraction of the
     * bore radius it starts and ends at. Never so far that it reaches the axis - see
     * {@link RiftDebris#INNER} for the same floor kept for the same reason: a bolt through the middle
     * of the bore would be a bolt through the hull passing along it.
     */
    private static final float LIGHTNING_CORRIDOR_DIP = 0.55F;
    /** Maximum sideways kick a segment of a corridor bolt takes off its own arc, in radians. */
    private static final float LIGHTNING_CORRIDOR_JITTER = 0.16F;
    /** How wide an arc a corridor bolt jumps, in radians - narrowest and widest it may be. */
    private static final float LIGHTNING_SPREAD_MIN = 0.7F;
    private static final float LIGHTNING_SPREAD_MAX = 1.7F;
    /**
     * Where down the corridor a bolt may sit, as a fraction of its depth. Kept short of where the
     * bore has visibly started to close, the same reasoning {@link #FAR_LIGHT} follows: a bolt drawn
     * at the true far end would be a bright arc hanging past the point the tube has already narrowed
     * to nothing around it.
     */
    private static final float LIGHTNING_DEPTH_MAX = 0.78F;

    /** How far a ground strike reaches down looking for something to hit, in blocks. */
    private static final int GROUND_STRIKE_RANGE = 48;
    /** How far out from the rift's own centre a strike starts, as a fraction of its radius. */
    private static final float GROUND_STRIKE_SPREAD = 0.4F;
    /** Sideways jitter along a ground strike, as a fraction of the strike's own length. */
    private static final float GROUND_JITTER_FRACTION = 0.05F;
    /** Half-width of a ground strike at its widest, as a fraction of its own length. */
    private static final float GROUND_WIDTH_FRACTION = 0.014F;

    private static final List<ActiveRift> ACTIVE = new ArrayList<>();

    private RiftEffectManager() {
    }

    /**
     * One rift, from the moment it tears open to the moment it collapses.
     *
     * <p>Package-private rather than private because {@link RiftFurniture} draws the themed geometry
     * standing around and across this aperture, and needs its basis, its rim and its colours to do it.
     * The two classes are one subsystem split for size, not two things with an API between them.
     */
    static final class ActiveRift {
        final Vec3 centre;
        final Vector3f normal;
        final Vector3f right;
        final Vector3f up;
        double radius;
        final int colour;
        /**
         * The rim's colour. Equal to {@link #colour} for every aperture except a Rift Drive's own,
         * where either a Modulator with a second swatch chosen or a borrowed theme's own palette makes
         * the two differ - see {@link #drawFace}, {@link #drawFire} and the theme furniture.
         */
        final int accentColour;
        /**
         * Whether {@link #colour} and {@link #accentColour} are the theme's own rather than the
         * pilot's - see {@link ThemeLook}. A few passes read the rim colour where they would otherwise
         * burn white-hot, because a blue rift with a pink-white edge is not a blue rift.
         */
        final boolean canonical;
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
        /** How this aperture is dressed: what stands around it, and how it comes apart. */
        final RiftModulatorTheme theme;
        /** Which fracture shape that theme cuts along - see {@link RiftShatter#patternFor}. */
        final RiftShatter.Pattern pattern;
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

        /** Lightning discharging off the torn rim. See {@link RiftLightning}. */
        final RiftLightning.Emitter[] apertureBolts;
        /** Lightning arcing across the corridor. See {@link RiftLightning}. */
        final RiftLightning.Emitter[] corridorBolts;
        /** Lightning reaching for the ground below, if there is any within range. */
        final RiftLightning.Emitter[] groundBolts;

        /**
         * How far the opening reaches at each angle, or {@code null} for a plain ellipse.
         *
         * <p>What lets an aperture bend to the ring it is standing in. A gate's opening is flood
         * filled and is very often not a rectangle, so an ellipse fitted to its bounding box bulges
         * straight through the frame. Every part of a rift is drawn radially - the face, the torn
         * rim, the fire, the cracks, the glass - so scaling the reach per angle bends all of it at
         * once, rather than teaching each of them about the shape separately.
         */
        float[] profile;

        /**
         * The rim's radius at an angle: its wander, scaled to whatever the opening allows there.
         *
         * <p>Everything that used to call {@code RiftTear.rim} directly goes through here instead,
         * which is what keeps the tear and the shape from having to know about each other.
         *
         * <h2>Why the tear is turned inside out for a shaped aperture</h2>
         * {@link RiftTear#rim} returns {@code [1, 1 + RAG]} - it is always at least the full radius,
         * because a rift is <em>meant</em> to overshoot its opening a little so the ragged edge bleeds
         * onto the frame around it. That is right for a rectangle, where the frame is what it spills
         * onto, and quite wrong for a flood-filled ring, where the same overshoot spills through the
         * gap into open sky.
         *
         * <p>So where there is a profile the wander is remapped from {@code [1, 1+RAG]} down to
         * {@code [1-RAG, 1]} by subtracting {@code RAG}. The edge still crawls by the same amount; it
         * simply crawls inwards from the opening rather than outwards past it, and the aperture can
         * never cross the frame however the wander lands.
         */
        double reach(double angle, float time) {
            float torn = RiftTear.rim(angle, time);
            if (profile == null || profile.length == 0) {
                return torn;
            }
            return profileAt(angle) * (torn - RiftTear.RAG);
        }

        /**
         * The profile sampled at an angle - the <em>smaller</em> of its two nearest entries.
         *
         * <p>Not interpolated, deliberately. Linear interpolation between a long ray down an arm and
         * a short one into a notch draws a straight diagonal between them, and that diagonal cuts
         * outside the inner corner it is supposed to be following. Taking the lesser of the two can
         * never exceed either, so the aperture stays inside the opening between samples as well as
         * at them - at the cost of the corner reading a touch tight, which is the right way round to
         * be wrong.
         */
        private double profileAt(double angle) {
            double turns = angle / (Math.PI * 2.0D);
            double at = (turns - Math.floor(turns)) * profile.length;
            int low = ((int) at) % profile.length;
            int high = (low + 1) % profile.length;
            return Math.min(profile[low], profile[high]);
        }

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
        /**
         * Whether this rift throws a bolt down at the ground and marks whatever it hits.
         *
         * <p>True for the two rifts that are a hole hanging in open space with a world beneath them - a
         * warp's own aperture, and a fissure's old wound - and false for a chute's. A chute is a rift
         * <em>encompassed</em> by its own housing: the only thing a bolt straight down from it could
         * ever strike is the block it is caged in, so it lights the rim and the bore like any other but
         * never reaches for a ground it is already sitting on. The rim and corridor bolts are unaffected
         * either way; this gates only {@link #drawGroundLightning}.
         */
        boolean groundStrikes = true;

        ActiveRift(Vec3 centre, Vec3 normal, double radius, int colour, int accentColour, float throat,
                   RiftModulatorTheme theme, int openTicks, int holdTicks, int closeTicks) {
            this.centre = centre;
            this.radius = radius;
            // The seven borrowed themes wear their own colours rather than the pilot's - see ThemeLook.
            // Settled here, ahead of everything else, because every pass reads colour and they all have
            // to agree: a hyperspace tunnel with a pilot-pink bore is not a hyperspace tunnel. The seed
            // is worked out first because Improbability rolls its palette from it.
            int where = RiftShatter.seedFor(centre.x, centre.y, centre.z);
            ThemeLook.Palette palette = ThemeLook.palette(theme, where);
            this.canonical = palette != null;
            this.colour = palette != null ? palette.core() : colour;
            this.accentColour = palette != null ? palette.rim() : accentColour;
            this.throat = throat;
            this.openTicks = openTicks;
            this.holdTicks = holdTicks;
            this.closeTicks = closeTicks;
            this.seed = where;
            this.theme = theme;
            this.pattern = RiftShatter.patternFor(theme);
            this.shards = RiftShatter.fracture(seed, pattern);
            this.motes = RiftDebris.field(seed);
            this.apertureBolts = RiftLightning.apertureField(seed);
            this.corridorBolts = RiftLightning.corridorField(seed);
            this.groundBolts = RiftLightning.groundField(seed);
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

    /** What the moment of release sounds like, for whichever way this aperture comes apart. */
    private static SoundEvent breakSound(RiftModulatorTheme theme) {
        return switch (theme) {
            // Pitched right down by the caller, which turns a door into heavy machinery under load.
            case CLOCKWORK -> SoundEvents.IRON_DOOR_OPEN;
            case ARCANE -> SoundEvents.AMETHYST_BLOCK_CHIME; // a circle ringing, not a pane shattering
            case EMBER -> SoundEvents.FIRE_EXTINGUISH; // a deep whoomph at this pitch, not a hiss
            case STARLIGHT -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            case STANDARD -> SoundEvents.GLASS_BREAK;
            // Everything below is pitched right down by the caller too, which is what turns each of
            // these from the household noise it normally is into something the size of an aperture.
            case STARBLOCKS -> SoundEvents.FIREWORK_ROCKET_LAUNCH; // a whoosh going away from you
            case BEDROCK -> SoundEvents.WARDEN_SONIC_BOOM; // the deepest thud vanilla has, for a fold
            case BOLDLY_GONE -> SoundEvents.BEACON_ACTIVATE; // a drive powering up into engagement
            case LUDICROUS -> SoundEvents.SLIME_BLOCK_PLACE; // a comic squelch, because it is a joke
            case EVENTFUL_HORIZON -> SoundEvents.ENDER_DRAGON_GROWL; // a roar, at this pitch - it is violent
            case VWORP -> SoundEvents.SHULKER_BOX_OPEN; // a groaning grind at this pitch
            case IMPROBABILITY -> SoundEvents.ILLUSIONER_MIRROR_MOVE; // warbly and not quite real
        };
    }

    /** Opens a rift from a server cue. */
    public static void open(ClientboundWarpEffectPacket packet, int colour, int accentColour,
                            RiftModulatorTheme theme, int openTicks, int holdTicks, int closeTicks) {
        if (!packet.hasRift() || !AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
        ACTIVE.add(new ActiveRift(packet.centre(), packet.normal(), packet.radius(), colour, accentColour,
                packet.throat(), theme, openTicks, holdTicks, closeTicks));
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
                            int colour, int openTicks, boolean groundStrikes) {
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
        // Flat colour and the standard break: gates and chutes have no Modulator to dress them.
        ActiveRift rift = new ActiveRift(centre, normal, halfWidth, colour, colour, 0.0F,
                RiftModulatorTheme.STANDARD, Math.max(1, openTicks), HELD_FOREVER, GATE_CLOSE_TICKS);
        rift.aspect = (float) (halfHeight / Math.max(1.0e-3D, halfWidth));
        rift.holder = holder;
        rift.keepAlive = HOLD_TICKS;
        // A fissure hangs over open ground and strikes it; a chute is a rift boxed in its own cage and
        // has nothing below it but that cage, so its caller passes false. See ActiveRift.groundStrikes.
        rift.groundStrikes = groundStrikes;
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
    /**
     * Re-aims a held aperture so it faces a given direction.
     *
     * <p>Only a Rift Chute needs this. Its housing is open on all four sides, so a rift with a fixed
     * normal would be edge-on and effectively invisible from half the angles a player can stand at.
     * Turning it to face the camera each frame is what makes one small aperture readable from
     * anywhere - the same trick the corridor's streaks already use, applied to the whole pane.
     *
     * <p>The fracture turns with the plane, which is correct: the glass was cut in this frame, so it
     * stays the same break seen from a different side rather than becoming a different break.
     */
    public static void aim(long holder, Vec3 direction) {
        for (ActiveRift rift : ACTIVE) {
            if (rift.holder != holder) {
                continue;
            }
            Vector3f forward = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
            if (forward.lengthSquared() < 1.0e-6F) {
                return;
            }
            forward.normalize();
            rift.normal.set(forward);
            Vector3f seed = Math.abs(forward.y) > 0.9F
                    ? new Vector3f(1.0F, 0.0F, 0.0F)
                    : new Vector3f(0.0F, 1.0F, 0.0F);
            rift.right.set(new Vector3f(forward).cross(seed).normalize());
            rift.up.set(new Vector3f(forward).cross(rift.right).normalize());
            return;
        }
    }

    /**
     * Gives a held aperture the shape of the opening it stands in.
     *
     * <p>Separate from {@link #hold} for the same reason {@link #aim} is: the profile is worked out
     * from the opening's own mask and only its owner knows it, while everything else about an
     * aperture is the same whoever tore it. Passing {@code null} restores the plain ellipse.
     *
     * <p><strong>Nothing calls this at the moment.</strong> Rift Gates did, and they are the reason
     * every part of the drawing is radial; their opening is a pane of Rift Portal blocks now, which
     * is the shape of the ring by construction and needs no profile. Kept because it is the only
     * thing here that can make an aperture anything other than an ellipse, and because deleting it
     * would take the reasoning above with it.
     */
    public static void shapeTo(long holder, float[] profile) {
        for (ActiveRift rift : ACTIVE) {
            if (rift.holder == holder) {
                rift.profile = profile;
                return;
            }
        }
    }

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
                        breakSound(rift.theme), SoundSource.BLOCKS,
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
            // Additive light over everything, including the face it is burning around. Two-sided:
            // an aperture is a hole people stand on both sides of, and a culled glow means half of
            // them are looking at a bare occluder.
            VertexConsumer fire = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
            for (ActiveRift rift : ACTIVE) {
                drawHaze(fire, matrix, rift, partialTick);
                // What the crew see from inside the bore, for the themes that borrow their corridor from
                // somewhere - hyperspace, a warp bubble, the time vortex. Drawn just inside the tube's own
                // wall, so the solid tube hides every stroke of it from anyone outside.
                RiftFurniture.corridor(fire, matrix, rift, partialTick);
                drawFarLight(fire, matrix, rift, partialTick);
                drawFire(fire, matrix, rift, partialTick, eye);
                drawOpening(fire, matrix, rift, partialTick, eye);
                // What a theme stands around its aperture, for as long as the aperture stands. Scaled
                // by the hole's own size inside, so it grows in behind the wind-up and leaves with the
                // seal rather than needing a clock of its own.
                RiftFurniture.standing(fire, matrix, rift, partialTick, eye);
                drawSpark(fire, matrix, rift, partialTick, eye);
                // Only for themes whose source crackles - see ThemeLook.crackles.
                if (AWConfig.RIFT_LIGHTNING.get() && ThemeLook.crackles(rift.theme)) {
                    drawApertureLightning(fire, matrix, rift, partialTick, eye);
                    drawCorridorLightning(fire, matrix, rift, partialTick);
                    // Only a rift with open ground beneath it reaches for it - not a chute, which is
                    // caged around its own rift and would only ever strike the block it sits in.
                    if (rift.groundStrikes) {
                        drawGroundLightning(fire, matrix, rift, partialTick);
                    }
                }
            }
            buffers.endBatch(AWRenderTypes.RIFT_FIRE);

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
     *
     * <p>Coloured core to rim rather than one flat tint: each ring's colour is the core colour blended
     * towards the accent colour by how far out it sits, {@link #faceGlow}'s own {@code t}. A Rift
     * Drive with no Modulator, or one with no second swatch chosen, has an accent colour identical to
     * its core one - see {@code RiftDriveBlockEntity.effectiveAccentColour} - so the lerp is between a
     * colour and itself and the face draws exactly as it always has.
     */
    private static void drawFace(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float partialTick) {
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }
        float time = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;

        float coreRed = ((rift.colour >> 16) & 0xFF) / 255.0F;
        float coreGreen = ((rift.colour >> 8) & 0xFF) / 255.0F;
        float coreBlue = (rift.colour & 0xFF) / 255.0F;
        float rimRed = ((rift.accentColour >> 16) & 0xFF) / 255.0F;
        float rimGreen = ((rift.accentColour >> 8) & 0xFF) / 255.0F;
        float rimBlue = (rift.accentColour & 0xFF) / 255.0F;

        for (int ring = 0; ring < FACE_RINGS.length - 1; ring++) {
            float innerT = FACE_RINGS[ring];
            float outerT = FACE_RINGS[ring + 1];
            // Nothing in the middle, a glow towards the edge, and the rim itself burning.
            float innerGlow = faceGlow(innerT);
            float outerGlow = faceGlow(outerT);
            float innerRed = Mth.lerp(innerT, coreRed, rimRed);
            float innerGreen = Mth.lerp(innerT, coreGreen, rimGreen);
            float innerBlue = Mth.lerp(innerT, coreBlue, rimBlue);
            float outerRed = Mth.lerp(outerT, coreRed, rimRed);
            float outerGreen = Mth.lerp(outerT, coreGreen, rimGreen);
            float outerBlue = Mth.lerp(outerT, coreBlue, rimBlue);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D;
                double rim0 = cover * rift.reach(a0, time);
                double rim1 = cover * rift.reach(a1, time);

                face(consumer, matrix, rift, a0, rim0 * innerT, innerRed, innerGreen, innerBlue, innerGlow);
                face(consumer, matrix, rift, a1, rim1 * innerT, innerRed, innerGreen, innerBlue, innerGlow);
                face(consumer, matrix, rift, a1, rim1 * outerT, outerRed, outerGreen, outerBlue, outerGlow);
                face(consumer, matrix, rift, a0, rim0 * outerT, outerRed, outerGreen, outerBlue, outerGlow);
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
                double rim0 = cover * rift.reach(a0, time);
                double rim1 = cover * rift.reach(a1, time);

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
                double rim0 = cover * rift.reach(a0, time);
                double rim1 = cover * rift.reach(a1, time);

                // Flares outward as it goes back, so the sleeve reads as vapour coming off the tear
                // rather than as a second, larger tube.
                double nearFlare = 1.04D + 0.30D * nearT;
                double farFlare = 1.04D + 0.30D * farT;

                hazeVertex(consumer, matrix, rift, a0, rim0 * nearFlare, depth * nearT, red, green, blue, nearAlpha);
                hazeVertex(consumer, matrix, rift, a1, rim1 * nearFlare, depth * nearT, red, green, blue, nearAlpha);
                hazeVertex(consumer, matrix, rift, a1, rim1 * farFlare, depth * farT, red, green, blue, farAlpha);
                hazeVertex(consumer, matrix, rift, a0, rim0 * farFlare, depth * farT, red, green, blue, farAlpha);
            }
        }
    }

    /**
     * Full width down the length that has to hold a hull, closing over the last stretch.
     *
     * <p>The closure is on a cosine rather than a straight line, which matters less for the shape than
     * for the fact that by the time it happens there is no light left on it. A bore that visibly ends
     * in a cone is a bag; one that fades out before it closes is a tunnel going somewhere.
     *
     * <p>Package-private because {@code RiftFurniture} dresses the inside of this same tube for the
     * borrowed themes, and has to follow its shape exactly to stay inside it.
     */
    static float throatWidth(float t) {
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
        // The fire cools into the accent colour rather than the core one, so it agrees with the face
        // it is burning around - see drawFace. Equal to the core channels whenever nothing has chosen
        // a second swatch, so this changes nothing for an undecorated drive.
        float accentGreen = ((rift.accentColour >> 8) & 0xFF) / 255.0F;
        float accentBlue = (rift.accentColour & 0xFF) / 255.0F;
        // White-hot at the edge for a pilot's colours, as it always was. A borrowed palette's rim is its
        // own colour all the way out, or every blue rift burns with a pink edge.
        float edgeRed = rift.canonical ? ((rift.accentColour >> 16) & 0xFF) / 255.0F : 1.0F;

        for (int band = 0; band < BANDS; band++) {
            float innerT = band / (float) BANDS;
            float outerT = (band + 1) / (float) BANDS;
            float edge = (float) Math.pow(outerT, 1.6D);
            float alpha = aperture * (0.15F + 0.85F * edge) * agitation;
            // Each band counter-rotates against its neighbour so the aperture churns.
            float spin = time * (band % 2 == 0 ? 1.0F : -1.4F) + band * 0.7F;

            float bandRed = Mth.lerp(edge, red * 0.15F, edgeRed);
            float bandGreen = Mth.lerp(edge, green * 0.15F, accentGreen);
            float bandBlue = Mth.lerp(edge, blue * 0.3F, accentBlue);

            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = (segment / (double) SEGMENTS) * Math.PI * 2.0D + spin;
                double a1 = ((segment + 1) / (double) SEGMENTS) * Math.PI * 2.0D + spin;
                double rim0 = cover * rift.reach(a0, time);
                double rim1 = cover * rift.reach(a1, time);

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
            double rim0 = cover * rift.reach(a0, time);
            double rim1 = cover * rift.reach(a1, time);

            float reach0 = 0.35F + 0.65F * RiftTear.lick(a0, time);
            float reach1 = 0.35F + 0.65F * RiftTear.lick(a1, time);
            float alpha = aperture * 0.55F * agitation;

            vertex(consumer, matrix, rift, bias, a0, rim0, edgeRed, accentGreen, accentBlue, alpha);
            vertex(consumer, matrix, rift, bias, a1, rim1, edgeRed, accentGreen, accentBlue, alpha);
            vertex(consumer, matrix, rift, bias, a1, rim1 * (1.0D + RiftTear.FLAME * reach1), red, green, blue, 0.0F);
            vertex(consumer, matrix, rift, bias, a0, rim0 * (1.0D + RiftTear.FLAME * reach0), red, green, blue, 0.0F);
        }
    }

    /**
     * What is drawn over the intact view while the aperture is winding up to open.
     *
     * <p>The hole does not exist yet - the world behind is still there to see - so this is the whole
     * of what an opening rift looks like for its first half second, and it is where a theme has to do
     * most of its talking. Struck glass cracks; a mechanism turns; a circle is written and charges.
     * Sending all three through the same radial-fracture drawing is what made them look like one
     * animation wearing three colours.
     */
    private static void drawOpening(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                    float partialTick, Vec3 eye) {
        if (rift.theme == RiftModulatorTheme.STANDARD) {
            drawCracks(consumer, matrix, rift, partialTick, eye);
        } else {
            RiftFurniture.opening(consumer, matrix, rift, partialTick, eye);
        }
    }

    /**
     * The fracture, before anything has broken loose.
     *
     * <p>A hard point of impact and cracks racing out from it over an intact view - the hole does not
     * exist yet, and the world behind the aperture is still there to see. This is the whole of what an
     * opening rift looks like for its first half second under the standard theme, and it is the reason
     * the hole arriving lands as an event rather than as a shape growing.
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

        for (float angle : RiftShatter.crackAngles(rift.seed, rift.pattern)) {
            double along = Math.cos(angle);
            double across = Math.sin(angle);
            double sideU = -across;
            double sideV = along;
            double tip = cover * rift.reach(angle, rift.rimTime) * reach;
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
     * The pane coming apart, however this aperture's theme comes apart.
     *
     * <p>Every motion is a different <em>answer to where a piece goes</em>, and that is what the eye
     * actually reads - far more than which cells the pane was cut into. Glass is thrown out and
     * tumbles face-over-edge, catching the light as it turns, so a field of it glitters. An iris blade
     * never leaves the plane and never tumbles: it sweeps round the aperture's own centre and slides
     * clear, because a mechanism's parts stay in the mechanism. A rune does not go anywhere at all -
     * it ignites where it stands and burns out, and the wave of that running rim-to-centre is the
     * whole animation.
     *
     * <p>The arms below stay short because each one is only ever a curve and a colour handed to
     * {@link #planarShard}; anything that needed more than that got a method of its own further down,
     * which is where the borrowed themes live.
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
        float span = RiftShatter.SHARD_LIFE * rift.pattern.lifeScale();

        for (RiftShatter.Shard shard : rift.shards) {
            float elapsed = since - shard.delay();
            float life = elapsed / span;
            float fade = RiftShatter.fade(life);
            if (fade <= 0.0F) {
                continue;
            }
            float travel = RiftShatter.travel(life);

            switch (rift.pattern.motion()) {
                case TUMBLE -> tumblingShard(consumer, matrix, rift, shard, eye, cover, elapsed,
                        travel, fade, red, green, blue);
                case SWING -> {
                    // Picked up from where the wind-up left the assembly, not from zero - see
                    // RiftFurniture.GEAR_REST.
                    float swing = RiftFurniture.GEAR_REST + shard.spin() * travel;
                    // A blade catches the light as it comes round, so the ring reads as turning metal
                    // rather than as a wheel of flat colour sliding sideways.
                    float sheen = Math.abs(Mth.cos(shard.midAngle() + swing));
                    float lit = 0.55F + 0.45F * sheen;
                    float white = sheen * sheen * 0.6F;
                    planarShard(consumer, matrix, rift, shard, cover, swing,
                            shard.outward() * cover * travel, 0.0D, 1.0D,
                            Mth.lerp(white, red, 1.0F) * lit, Mth.lerp(white, green, 1.0F) * lit,
                            Mth.lerp(white, blue, 1.0F) * lit, fade * 0.8F);
                }
                case DISSOLVE -> {
                    // White-hot as it catches, cooling to the rift's own colour as it burns out. Held
                    // at the angle the charging circle turned to, so the bands line up with the figure
                    // that was just drawn rather than with where it started.
                    float white = Math.max(0.0F, 1.0F - life * 2.5F);
                    planarShard(consumer, matrix, rift, shard, cover, RiftFurniture.RUNE_REST, 0.0D,
                            0.0D, 1.0D,
                            Mth.lerp(white, red, 1.0F), Mth.lerp(white, green, 1.0F),
                            Mth.lerp(white, blue, 1.0F), fade * 0.75F);
                }
                case CHAR -> {
                    // Catches white, chars through the rift's own colour, and is a dark curl by the
                    // end. Lifts as it goes and shrinks as it curls, which is what burning paper does
                    // and what being thrown is not.
                    float white = Math.max(0.0F, 1.0F - life * 4.0F);
                    float darken = 1.0F - 0.75F * Mth.clamp(life * 1.4F, 0.0F, 1.0F);
                    planarShard(consumer, matrix, rift, shard, cover,
                            shard.spin() * travel, shard.outward() * cover * travel,
                            CHAR_LIFT * cover * travel, 1.0D - 0.45D * travel,
                            Mth.lerp(white, red, 1.0F) * darken, Mth.lerp(white, green, 1.0F) * darken,
                            Mth.lerp(white, blue, 1.0F) * darken, fade * 0.85F);
                }
                case DRIFT -> {
                    // A slow spiral outward, shrinking to a point. The twinkle is the tell: each mote
                    // is on its own clock, so the field sparkles as it disperses instead of dimming
                    // together the way a single fading sheet would.
                    float twinkle = 0.55F + 0.45F * Mth.sin(
                            (since + shard.axis() * 12.0F) * 0.55F + shard.midAngle() * 3.0F);
                    planarShard(consumer, matrix, rift, shard, cover,
                            shard.spin() * travel, shard.outward() * cover * travel,
                            0.0D, 1.0D - 0.55D * travel,
                            red, green, blue, fade * twinkle * 0.9F);
                }
                case STREAK -> streakShard(consumer, matrix, rift, shard, cover, travel, fade,
                        red, green, blue);
                case SHEAR -> shearShard(consumer, matrix, rift, shard, cover, travel, fade,
                        red, green, blue);
                case IMPLODE -> implodeShard(consumer, matrix, rift, shard, cover, since, travel, fade,
                        red, green, blue);
                case ELONGATE -> elongateShard(consumer, matrix, rift, shard, cover, travel, fade,
                        red, green, blue);
                case PLAID -> plaidShard(consumer, matrix, rift, shard, cover, travel, fade);
                case STUTTER -> stutterShard(consumer, matrix, rift, shard, cover, travel, fade,
                        red, green, blue);
                case IMPROBABLE -> improbableShard(consumer, matrix, rift, shard, cover, since, travel,
                        fade, life);
            }
        }
    }

    // ------------------------------------------------------- the borrowed motions

    /**
     * A piece that never detaches: pinned at its inner edge while its outer edge races away.
     *
     * <p>The one motion here that does not move a shape so much as change one - which is why it needs
     * the two-slide overload of {@link #planarShard} rather than the ordinary one. Sliding both edges
     * would translate the cell outward and leave a hole at the hub; moving only the outer edge draws
     * the cell out into a line that still starts where it always did, and a ring of those is a
     * starfield going to streaks.
     */
    private static void streakShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                    RiftShatter.Shard shard, double cover, float travel, float fade,
                                    float red, float green, float blue) {
        // Blowing out towards white as it draws out, because a streak that keeps its colour reads as a
        // painted line rather than as something moving too fast to have a colour any more.
        float white = travel * travel;
        // Narrowing as it lengthens: a streak is thin, and a full-width wedge stretched outward is a
        // slice of pie, not a star going past.
        double shrink = 1.0D - 0.55D * travel;
        planarShard(consumer, matrix, rift, shard, cover, 0.0F,
                0.0D, shard.outward() * cover * travel, 0.0D, shrink,
                Mth.lerp(white, red, 1.0F), Mth.lerp(white, green, 1.0F), Mth.lerp(white, blue, 1.0F),
                fade * (0.55F + 0.45F * travel));
    }

    /**
     * Space wound round a dark middle: every piece dragged by an amount that falls away with radius.
     *
     * <p>The falloff is the whole effect. A uniform turn is a plate spinning, which says nothing about
     * space; a turn that is violent at the hub and nearly nothing at the rim is the one shape the eye
     * reads as the <em>frame</em> being bent rather than an object moving inside it.
     */
    private static void shearShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   RiftShatter.Shard shard, double cover, float travel, float fade,
                                   float red, float green, float blue) {
        float fromRim = 1.0F - shard.midRadius();
        float swing = shard.spin() * travel * fromRim * fromRim;
        // Lit gold at the edge and black in the middle: the rim of the pane is the event horizon, lit by
        // everything bending round it, and the middle is falling into something no light leaves. The
        // glass pass is translucent rather than additive, so a black piece really does darken what is
        // behind it - which makes this the one place in a rift a singularity can be drawn at all.
        float[] horizon = RiftFurniture.channels(rift.accentColour);
        float lit = shard.midRadius() * (1.0F - 0.6F * travel);
        planarShard(consumer, matrix, rift, shard, cover, swing, 0.0D, 0.0D, 1.0D,
                Mth.lerp(lit, red, horizon[0]), Mth.lerp(lit, green, horizon[1]),
                Mth.lerp(lit, blue, horizon[2]), fade * 0.9F);
    }

    /**
     * A piece torn into the middle and pulled thin on the way - what gravity does to a thing, rather
     * than what breaking does.
     *
     * <p>Its inner edge runs in faster than its outer one, so each cell stretches along its own radius
     * towards the hole instead of travelling as a tile: spaghettified rather than thrown. Both edges stop
     * just short of the centre, because an edge carried through it comes out drawn on the far side and
     * reads as a fold, not a fall. And it shudders - a small wobble round the centre whose phase jumps
     * every tick rather than gliding, because what separates violent from merely fast is that it is
     * discontinuous.
     */
    private static void implodeShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                     RiftShatter.Shard shard, double cover, float since, float travel,
                                     float fade, float red, float green, float blue) {
        double pull = shard.outward() * cover * travel;
        double inner = -Math.min(pull, cover * shard.innerT() * 0.98D);
        double outer = -Math.min(pull * 0.55D, cover * shard.outerT() * 0.98D);
        float jolt = Mth.sin(Mth.floor(since) * 2.7F + shard.axis() * 5.0F);
        float swing = shard.spin() * jolt * 0.25F * travel;
        // Cold arc-light as it tears loose, the drive's red as it falls, near black by the time it is
        // gone. The glass pass is translucent, so that last stage genuinely darkens the view behind it.
        float[] arc = RiftFurniture.channels(rift.accentColour);
        float[] glow = RiftFurniture.channels(ThemeLook.GRAVITY_GLOW);
        float r;
        float g;
        float b;
        if (travel < 0.5F) {
            float k = travel * 2.0F;
            r = Mth.lerp(k, arc[0], glow[0]);
            g = Mth.lerp(k, arc[1], glow[1]);
            b = Mth.lerp(k, arc[2], glow[2]);
        } else {
            float k = (travel - 0.5F) * 2.0F;
            r = Mth.lerp(k, glow[0], red);
            g = Mth.lerp(k, glow[1], green);
            b = Mth.lerp(k, glow[2], blue);
        }
        planarShard(consumer, matrix, rift, shard, cover, swing, inner, outer, 0.0D,
                1.0D - 0.4D * travel, r, g, b, fade * 0.95F);
    }

    /**
     * The aperture drawn out along one axis until it lets go.
     *
     * <p>Done per piece rather than by scaling the whole figure, because {@link #planarShard} moves in
     * polar terms: a piece travels outward by how much of it lies along the stretch axis, which is
     * {@code |cos|} of where it sits. Pieces at the ends run right out, pieces at the sides barely
     * move, and the ring between them is an ellipse - the same result, reached the way the rest of
     * this class already works.
     */
    private static void elongateShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                      RiftShatter.Shard shard, double cover, float travel, float fade,
                                      float red, float green, float blue) {
        double alongAxis = Math.abs(Math.cos(shard.midAngle()));
        double slide = shard.outward() * cover * travel * LENS_REACH * alongAxis;
        // A fringe that runs round the rim: the ends of the lens push one way off the pilot's colour
        // and the sides the other, which is the whole of what makes this read as refracted rather
        // than merely tinted.
        float fringe = (float) Math.cos(shard.midAngle() * 2.0D) * 0.35F * travel;
        // The engagement flash: every piece white the instant the drive engages, cooling to warp blue as
        // it draws out, and gone within the first third of its travel so it reads as a single flash.
        float flash = Math.max(0.0F, 1.0F - travel * 3.0F);
        planarShard(consumer, matrix, rift, shard, cover, 0.0F, slide, 0.0D, 1.0D,
                Mth.lerp(flash, Mth.clamp(red + fringe, 0.0F, 1.0F), 1.0F), Mth.lerp(flash, green, 1.0F),
                Mth.lerp(flash, Mth.clamp(blue - fringe, 0.0F, 1.0F), 1.0F), fade * 0.85F);
    }

    /**
     * Warp and weft: bands crossing at right angles, in the sett's own colours, snapping apart together.
     *
     * <p>The motion is almost nothing - alternate bands slide at different rates so the weave opens -
     * because a tartan is a <em>colour</em> pattern and putting the effect anywhere else would lose
     * it. The two band indices are recovered from the cell's own middle rather than passed in, which
     * keeps this a function of the shard like every other motion here.
     */
    private static void plaidShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   RiftShatter.Shard shard, double cover, float travel, float fade) {
        RiftShatter.Pattern pattern = rift.pattern;
        int ringBand = (int) (shard.midRadius() * pattern.rings());
        int crackBand = (int) (shard.midAngle() / (Math.PI * 2.0D) * pattern.cracks());
        // A thread of the sett, chosen so neighbouring cells are neighbouring threads - warp round the
        // rings and weft across the cracks - which is what lets the pieces reassemble as cloth.
        float[] woven = RiftFurniture.channels(ThemeLook.settColour(ringBand * 3 + crackBand));
        // Warp and weft come apart at different rates, so the weave separates into its two directions
        // rather than expanding as one sheet.
        double rate = Math.floorMod(crackBand, 2) == 0 ? 1.0D : 0.55D;
        planarShard(consumer, matrix, rift, shard, cover, 0.0F,
                shard.outward() * cover * travel * rate, 0.0D, 1.0D,
                woven[0], woven[1], woven[2], fade * 0.9F);
    }

    /**
     * A piece that will not arrive properly: five hard steps with a gap between each.
     *
     * <p>Position is quantised and opacity is not, which is the arrangement that reads as stuttering.
     * The reverse - smooth travel with a flickering alpha - is a piece moving normally behind a bad
     * light, and the difference between the two is the whole effect.
     */
    private static void stutterShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                     RiftShatter.Shard shard, double cover, float travel, float fade,
                                     float red, float green, float blue) {
        float scaled = travel * STUTTER_STEPS;
        float step = Mth.floor(scaled);
        float within = scaled - step;
        float stepped = step / STUTTER_STEPS;
        // Solid in the middle of a step and gone at either end of it, so the piece is only ever seen
        // at one of five places and never on the way between two of them.
        float presence = Mth.sin(within * (float) Math.PI);
        if (presence <= 0.02F) {
            return;
        }
        // Alternate teeth in the rift's two colours - the vortex's blue and orange - so the stepping
        // reads round the rim as well as out from it.
        int tooth = (int) (shard.midAngle() / (Math.PI * 2.0D) * rift.pattern.cracks());
        float[] own = (tooth & 1) == 1 ? RiftFurniture.channels(rift.accentColour) : new float[]{red, green, blue};
        // Brightest as it lands, which gives each step an edge rather than letting them blur together.
        float flare = presence * presence;
        planarShard(consumer, matrix, rift, shard, cover, shard.spin() * stepped,
                shard.outward() * cover * stepped, 0.0D, 1.0D,
                Mth.lerp(flare * 0.5F, own[0], 1.0F), Mth.lerp(flare * 0.5F, own[1], 1.0F),
                Mth.lerp(flare * 0.5F, own[2], 1.0F), fade * presence * 0.9F);
    }

    /**
     * A piece that cannot decide, and settles it by rolling.
     *
     * <p>The roll is taken from the shard's own {@code axis}, which the fracture already filled with
     * per-piece noise, so the choice is stable for as long as this rift stands and different for the
     * next one - the joke is that it is never the same twice, not that it flickers.
     *
     * <p>Every branch is one of the motions above with its numbers pulled about, rather than anything
     * new. That is the point: what makes this theme read is a <em>field</em> of pieces disagreeing
     * with one another about how a rift opens.
     */
    private static void improbableShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                        RiftShatter.Shard shard, double cover, float since,
                                        float travel, float fade, float life) {
        int roll = Math.floorMod((int) (shard.axis() * 1_024.0F), IMPROBABLE_WAYS);
        // A colour of its own per piece, cycling as it goes, and nothing to do with the pilot's - the
        // one theme where the Modulator's swatch is deliberately overruled.
        float[] hue = rainbow(shard.midAngle() * 0.5F + since * 0.06F + roll);
        double slide = shard.outward() * cover * travel;

        switch (roll) {
            case 0 -> planarShard(consumer, matrix, rift, shard, cover, shard.spin() * travel,
                    slide, 0.0D, 1.0D - 0.5D * travel, hue[0], hue[1], hue[2], fade * 0.9F);
            case 1 -> planarShard(consumer, matrix, rift, shard, cover, shard.spin() * travel * 3.0F,
                    slide * 0.3D, CHAR_LIFT * cover * travel, 1.0D - 0.3D * travel,
                    hue[0], hue[1], hue[2], fade * 0.9F);
            case 2 -> planarShard(consumer, matrix, rift, shard, cover,
                    RiftFurniture.GEAR_REST + shard.spin() * travel, slide * 0.6D, 0.0D, 1.0D,
                    hue[0], hue[1], hue[2], fade * 0.85F);
            case 3 -> planarShard(consumer, matrix, rift, shard, cover, 0.0F, -slide * 0.4D,
                    -IMPROBABLE_DRIP * cover * travel, 1.0D, hue[0], hue[1], hue[2], fade * 0.9F);
            // Motionless, and simply burning out where it stands - the improbable case where nothing
            // improbable happens at all.
            default -> planarShard(consumer, matrix, rift, shard, cover, 0.0F, 0.0D, 0.0D,
                    1.0D - life * 0.6D, hue[0], hue[1], hue[2], fade * 0.8F);
        }
    }

    /**
     * A colour going round the wheel, for the one theme that supplies its own.
     *
     * <p>Three sines a third of a turn apart. Not a real HSV conversion, which would be more code for
     * a result nobody could tell apart at the size and speed these are seen at.
     */
    private static float[] rainbow(float phase) {
        float turn = phase * (float) (Math.PI * 2.0D);
        float third = (float) (Math.PI * 2.0D / 3.0D);
        return new float[]{
                0.5F + 0.5F * Mth.sin(turn),
                0.5F + 0.5F * Mth.sin(turn + third),
                0.5F + 0.5F * Mth.sin(turn + third * 2.0F)};
    }


    /**
     * One piece of glass, thrown clear and tumbling.
     *
     * <p>The piece keeps its own frame and turns in it, so the corners stay a rigid shape rather than
     * shearing the way rotating each corner about the centre would.
     */
    private static void tumblingShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                      RiftShatter.Shard shard, Vec3 eye, double cover, float elapsed,
                                      float travel, float fade, float red, float green, float blue) {
        double rim0 = rift.reach(shard.angle0(), rift.rimTime);
        double rim1 = rift.reach(shard.angle1(), rift.rimTime);
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

    /**
     * One piece that never leaves the aperture's plane: swept round its centre and slid outwards.
     *
     * <p>Moved in <em>polar</em> terms rather than as a rigid body - the corners are taken round by an
     * angle and out by a distance. That is not a cheat around the rigid-body transform {@link
     * #tumblingShard} needs; it is the correct motion for this shape. An iris blade is a polar object,
     * and sweeping it round its own arc is exactly what a shutter does. Doing it rigidly would lift the
     * wedge off the circle it belongs to and leave a gap at the hub.
     *
     * @param swing radians round the aperture's centre
     * @param slide how far out along its own radius, in blocks
     * @param lift  how far along the aperture's own up axis, in blocks - what lets a charring piece
     *              rise rather than merely spread
     * @param shrink what is left of the piece's own size, 1 for none. A piece that curls or dwindles
     *              scales about its own middle, so it stays where it was rather than crawling inwards
     */
    private static void planarShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                    RiftShatter.Shard shard, double cover, float swing, double slide,
                                    double lift, double shrink,
                                    float red, float green, float blue, float alpha) {
        planarShard(consumer, matrix, rift, shard, cover, swing, slide, slide, lift, shrink,
                red, green, blue, alpha);
    }

    /**
     * The same piece, with its two radial edges moved by different amounts.
     *
     * <p>What {@link RiftShatter.Motion#STREAK} needs and nothing else does. Moving both edges
     * together - the overload above, which every other motion uses - slides a cell outward with its
     * shape intact. Moving only the outer one draws the cell out into a line anchored where it
     * started, which is a different thing entirely and the only way to smear a pane without tearing a
     * hole at the hub.
     *
     * @param innerSlide how far out the edge nearest the middle goes, in blocks
     * @param outerSlide how far out the edge nearest the rim goes, in blocks
     */
    private static void planarShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                    RiftShatter.Shard shard, double cover, float swing,
                                    double innerSlide, double outerSlide,
                                    double lift, double shrink,
                                    float red, float green, float blue, float alpha) {
        double a0 = shard.angle0() + swing;
        double a1 = shard.angle1() + swing;
        double rim0 = rift.reach(a0, rift.rimTime);
        double rim1 = rift.reach(a1, rift.rimTime);
        double cos0 = Math.cos(a0);
        double sin0 = Math.sin(a0);
        double cos1 = Math.cos(a1);
        double sin1 = Math.sin(a1);

        double near0 = cover * rim0 * shard.innerT() + innerSlide;
        double far0 = cover * rim0 * shard.outerT() + outerSlide;
        double near1 = cover * rim1 * shard.innerT() + innerSlide;
        double far1 = cover * rim1 * shard.outerT() + outerSlide;

        double u0 = cos0 * near0;
        double v0 = sin0 * near0;
        double u1 = cos1 * near1;
        double v1 = sin1 * near1;
        double u2 = cos1 * far1;
        double v2 = sin1 * far1;
        double u3 = cos0 * far0;
        double v3 = sin0 * far0;

        if (shrink < 1.0D) {
            double midU = (u0 + u1 + u2 + u3) * 0.25D;
            double midV = (v0 + v1 + v2 + v3) * 0.25D;
            u0 = midU + (u0 - midU) * shrink;
            v0 = midV + (v0 - midV) * shrink;
            u1 = midU + (u1 - midU) * shrink;
            v1 = midV + (v1 - midV) * shrink;
            u2 = midU + (u2 - midU) * shrink;
            v2 = midV + (v2 - midV) * shrink;
            u3 = midU + (u3 - midU) * shrink;
            v3 = midV + (v3 - midV) * shrink;
        }

        localVertex(consumer, matrix, rift, u0, v0 + lift, 0.0D, red, green, blue, alpha);
        localVertex(consumer, matrix, rift, u1, v1 + lift, 0.0D, red, green, blue, alpha);
        localVertex(consumer, matrix, rift, u2, v2 + lift, 0.0D, red, green, blue, alpha);
        localVertex(consumer, matrix, rift, u3, v3 + lift, 0.0D, red, green, blue, alpha);
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

            // Whatever a theme's pieces did on the way out, they undo on the way back - a shutter that
            // swung open has to swing closed, and a circle that burned away has to be written again
            // where it stood. Only glass has anywhere to tumble back from.
            if (rift.pattern.motion() != RiftShatter.Motion.TUMBLE) {
                // Whatever a theme's pieces did on the way out they undo coming back, from the same
                // rest angles the opening uses - so a shutter closes onto where it opened from, and a
                // circle is rewritten over its own figure rather than over a fresh one.
                float fromRim = 1.0F - shard.midRadius();
                float swing = switch (rift.pattern.motion()) {
                    case SWING -> RiftFurniture.GEAR_REST + shard.spin() * out;
                    case DISSOLVE -> RiftFurniture.RUNE_REST;
                    // Unwinding: the same falloff the fold wound in with, run backwards, so the middle
                    // untwists last rather than the whole plate turning back together.
                    case SHEAR -> shard.spin() * out * fromRim * fromRim;
                    // A streak has no rotation to undo, and a lens and a weave were never turned.
                    case STREAK, ELONGATE, PLAID -> 0.0F;
                    default -> shard.spin() * out;
                };
                double lift = switch (rift.pattern.motion()) {
                    case CHAR -> CHAR_LIFT * cover * out;
                    default -> 0.0D;
                };
                // How far out each edge still is. Only a streak's and an implosion's two edges differ:
                // a streak comes home by its outer edge retracting onto its inner one, and a piece that
                // was pulled thin into the middle un-stretches back out of it.
                double slide = shard.outward() * cover * out;
                double innerSlide = switch (rift.pattern.motion()) {
                    case STREAK -> 0.0D;
                    // Pulled into the middle on the way out, so it comes back out of it, inner edge last.
                    case IMPLODE -> -Math.min(slide, cover * shard.innerT() * 0.98D);
                    case ELONGATE -> slide * LENS_REACH * Math.abs(Math.cos(shard.midAngle()));
                    default -> slide;
                };
                double outerSlide = switch (rift.pattern.motion()) {
                    case STREAK -> slide;
                    case IMPLODE -> -Math.min(slide * 0.55D, cover * shard.outerT() * 0.98D);
                    default -> innerSlide;
                };
                float white = Math.max(0.0F, 1.0F - (1.0F - out) * 2.5F);
                planarShard(consumer, matrix, rift, shard, cover, swing,
                        innerSlide, outerSlide, lift, 1.0D - 0.45D * out,
                        Mth.lerp(white, red, 1.0F), Mth.lerp(white, green, 1.0F),
                        Mth.lerp(white, blue, 1.0F), fade * 0.8F);
                continue;
            }

            double rim0 = rift.reach(shard.angle0(), rift.rimTime);
            double rim1 = rift.reach(shard.angle1(), rift.rimTime);
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
     * Lightning discharging off the torn rim, jumping outward from it into open air.
     *
     * <p>One flash at a time per emitter - see {@link RiftLightning} for why a small fixed field of
     * them, each on its own clock, is what "random sparking" actually is here. A bolt jags outward
     * from a point on the rim the same way {@link #drawFire}'s flames lick past it, and narrows to a
     * point the same way a crack does, for the same reason: a real discharge runs and thins as it
     * goes. Free at one end and anchored to the rim at the other, unlike a corridor bolt - see
     * {@link #drawCorridorLightning}.
     */
    private static void drawApertureLightning(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                               float partialTick, Vec3 eye) {
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }
        float clock = Mth.lerp(partialTick, rift.lastAge, rift.age);
        float shimmerTime = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;

        double towardsEye = (eye.x - rift.centre.x) * rift.normal.x
                + (eye.y - rift.centre.y) * rift.normal.y
                + (eye.z - rift.centre.z) * rift.normal.z;
        float bias = towardsEye >= 0.0D ? FIRE_BIAS : -FIRE_BIAS;

        // A borrowed palette's bolts are its rim colour - cold arc-light, vortex orange - not its core,
        // which for a gravity drive is a red too dark to crackle with.
        int bolt = rift.canonical ? rift.accentColour : rift.colour;
        float red = ((bolt >> 16) & 0xFF) / 255.0F;
        float green = ((bolt >> 8) & 0xFF) / 255.0F;
        float blue = (bolt & 0xFF) / 255.0F;

        for (int index = 0; index < rift.apertureBolts.length; index++) {
            RiftLightning.Emitter emitter = rift.apertureBolts[index];
            float progress = RiftLightning.flash(emitter, clock);
            if (progress < 0.0F) {
                continue;
            }
            float glow = RiftLightning.brightness(progress);
            int cycleNumber = RiftLightning.cycle(emitter, clock);
            double rimRadius = cover * rift.reach(emitter.anchor(), shimmerTime);

            double lastAngle = emitter.anchor();
            double lastRadius = rimRadius;
            for (int point = 1; point <= RiftLightning.SEGMENTS; point++) {
                float along = point / (float) RiftLightning.SEGMENTS;
                float previousAlong = (point - 1) / (float) RiftLightning.SEGMENTS;
                // Free at the tip, so the jag is wildest leaving the rim and settles as it runs out.
                float kick = RiftLightning.jitter(rift.seed, index, cycleNumber, point)
                        * LIGHTNING_APERTURE_JITTER * (1.0F - along);
                double angle = emitter.anchor() + kick;
                double radius = rimRadius * (1.0D + LIGHTNING_REACH * along);

                double halfWidthNear = cover * Mth.lerp(previousAlong, LIGHTNING_ROOT, LIGHTNING_TIP);
                double halfWidthFar = cover * Mth.lerp(along, LIGHTNING_ROOT, LIGHTNING_TIP);
                double dThetaNear = halfWidthNear / Math.max(0.01D, lastRadius);
                double dThetaFar = halfWidthFar / Math.max(0.01D, radius);

                float segmentAlpha = glow * (1.0F - along * 0.4F);
                // White where the discharge is freshest, cooling to the rift's own colour as it runs -
                // the same read {@link #drawCracks} gives its own fractures for the same reason.
                float white = 1.0F - previousAlong * 0.6F;
                float nearRed = Mth.lerp(white, red, 1.0F);
                float nearGreen = Mth.lerp(white, green, 1.0F);
                float nearBlue = Mth.lerp(white, blue, 1.0F);

                vertex(consumer, matrix, rift, bias, lastAngle - dThetaNear, lastRadius,
                        nearRed, nearGreen, nearBlue, segmentAlpha);
                vertex(consumer, matrix, rift, bias, angle - dThetaFar, radius,
                        red, green, blue, segmentAlpha * 0.6F);
                vertex(consumer, matrix, rift, bias, angle + dThetaFar, radius,
                        red, green, blue, segmentAlpha * 0.6F);
                vertex(consumer, matrix, rift, bias, lastAngle + dThetaNear, lastRadius,
                        nearRed, nearGreen, nearBlue, segmentAlpha);

                lastAngle = angle;
                lastRadius = radius;
            }
        }
    }

    /**
     * Lightning arcing across the corridor, jumping between two points of the bore's own wall.
     *
     * <p>Anchored at both ends rather than free at one, unlike a bolt off the rim - see
     * {@link #drawApertureLightning} - so it is widest at the middle of its own arc and pinches to
     * nothing at either wall, the shape an arc actually jumping a gap traces rather than one merely
     * discharging into open air. Dips towards the axis at its middle so it reads as crossing the open
     * bore instead of merely running along the wall it starts and ends on.
     */
    private static void drawCorridorLightning(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                              float partialTick) {
        float open = Mth.lerp(partialTick, rift.throatOpenLast, rift.throatOpen);
        if (open <= 0.001F || rift.throat == 0.0F) {
            return;
        }
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }

        float clock = Mth.lerp(partialTick, rift.lastAge, rift.age);
        float shimmerTime = (rift.lastAge + partialTick) * 0.12F;
        double cover = rift.radius * aperture;
        double depth = rift.throat * open;

        // A borrowed palette's bolts are its rim colour - cold arc-light, vortex orange - not its core,
        // which for a gravity drive is a red too dark to crackle with.
        int bolt = rift.canonical ? rift.accentColour : rift.colour;
        float red = ((bolt >> 16) & 0xFF) / 255.0F;
        float green = ((bolt >> 8) & 0xFF) / 255.0F;
        float blue = (bolt & 0xFF) / 255.0F;

        for (int index = 0; index < rift.corridorBolts.length; index++) {
            RiftLightning.Emitter emitter = rift.corridorBolts[index];
            float progress = RiftLightning.flash(emitter, clock);
            if (progress < 0.0F) {
                continue;
            }
            float glow = RiftLightning.brightness(progress);
            int cycleNumber = RiftLightning.cycle(emitter, clock);
            // Bright and mostly white throughout, with only a wash of the rift's own colour - an arc
            // has no root to be freshest at the way a bolt off the rim does, so there is nothing here
            // to gradient between.
            float boltRed = Mth.lerp(0.75F, red, 1.0F);
            float boltGreen = Mth.lerp(0.75F, green, 1.0F);
            float boltBlue = Mth.lerp(0.75F, blue, 1.0F);

            // Fixed for the whole bolt, and drawn from the seed rather than carried on the emitter:
            // where down the corridor it sits and how wide an arc it jumps are a fact about this
            // emitter, not about which flash it is currently on.
            float depthFraction = LIGHTNING_DEPTH_MAX * RiftShatter.noise(rift.seed, 70_000 + index * 5);
            float spread = LIGHTNING_SPREAD_MIN + (LIGHTNING_SPREAD_MAX - LIGHTNING_SPREAD_MIN)
                    * RiftShatter.noise(rift.seed, 70_000 + index * 5 + 1);
            float direction = RiftShatter.noise(rift.seed, 70_000 + index * 5 + 2) < 0.5F ? -1.0F : 1.0F;

            double along = depth * depthFraction;
            double wallRadius = cover * rift.reach(emitter.anchor(), shimmerTime)
                    * throatWidth(depthFraction);
            if (wallRadius <= 0.01D) {
                continue; // inside the cone where the bore has already closed
            }
            double startAngle = emitter.anchor();
            double endAngle = startAngle + spread * direction;

            double lastAngle = startAngle;
            double lastRadius = 0.0D;
            for (int point = 0; point <= RiftLightning.SEGMENTS; point++) {
                float t = point / (float) RiftLightning.SEGMENTS;
                // Pinched to nothing at both ends, widest at the middle - an arc anchored on both
                // sides rather than a discharge free at one, unlike the aperture's own bolts.
                float span = Mth.sin(t * (float) Math.PI);
                float kick = RiftLightning.jitter(rift.seed, index, cycleNumber, point)
                        * LIGHTNING_CORRIDOR_JITTER * span;
                double angle = Mth.lerp(t, startAngle, endAngle) + kick;
                double radiusFraction = 1.0D - LIGHTNING_CORRIDOR_DIP * Math.sin(t * Math.PI);
                double radius = wallRadius * radiusFraction;
                double halfWidth = cover * LIGHTNING_ROOT * span;
                double dTheta = halfWidth / Math.max(0.01D, radius);

                float segmentAlpha = glow * 0.9F;

                if (point > 0) {
                    hazeVertex(consumer, matrix, rift, lastAngle - dTheta, lastRadius, along,
                            boltRed, boltGreen, boltBlue, segmentAlpha);
                    hazeVertex(consumer, matrix, rift, angle - dTheta, radius, along,
                            boltRed, boltGreen, boltBlue, segmentAlpha);
                    hazeVertex(consumer, matrix, rift, angle + dTheta, radius, along,
                            boltRed, boltGreen, boltBlue, segmentAlpha);
                    hazeVertex(consumer, matrix, rift, lastAngle + dTheta, lastRadius, along,
                            boltRed, boltGreen, boltBlue, segmentAlpha);
                }
                lastAngle = angle;
                lastRadius = radius;
            }
        }
    }

    /**
     * Lightning reaching down from the rift for the ground, and marking whatever it hits.
     *
     * <p>Not against the rift's own plane at all, unlike {@link #drawApertureLightning} and
     * {@link #drawCorridorLightning} - a strike starts near the rift and runs straight down through
     * open world space to wherever the ground actually is, so this is the one bolt drawn as raw world
     * points rather than against the rift's local angle-and-radius frame. Pinned at both ends the same
     * way a corridor bolt is, because a strike is anchored at the ground exactly as much as it is at
     * the rift - a jag that wandered off its own impact point would light the world beside the block
     * it is supposed to be marking.
     *
     * <p>A flash that finds nothing within reach - open sky under a drive riding high, or a rift with
     * solid rock a block below it either way - simply does not happen that cycle. There is nothing
     * dishonest about a bolt not being drawn when there is nothing for it to strike.
     */
    private static void drawGroundLightning(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                            float partialTick) {
        float aperture = rift.aperture(partialTick);
        if (aperture <= 0.001F) {
            return;
        }
        if (!(Minecraft.getInstance().level instanceof ClientLevel level)) {
            return;
        }
        float clock = Mth.lerp(partialTick, rift.lastAge, rift.age);

        // A borrowed palette's bolts are its rim colour - cold arc-light, vortex orange - not its core,
        // which for a gravity drive is a red too dark to crackle with.
        int bolt = rift.canonical ? rift.accentColour : rift.colour;
        float red = ((bolt >> 16) & 0xFF) / 255.0F;
        float green = ((bolt >> 8) & 0xFF) / 255.0F;
        float blue = (bolt & 0xFF) / 255.0F;

        for (int index = 0; index < rift.groundBolts.length; index++) {
            RiftLightning.Emitter emitter = rift.groundBolts[index];
            float progress = RiftLightning.flash(emitter, clock);
            if (progress < 0.0F) {
                continue;
            }
            float glow = RiftLightning.brightness(progress);
            int cycleNumber = RiftLightning.cycle(emitter, clock);

            Vec3 start = new Vec3(
                    rift.centre.x + Math.cos(emitter.anchor()) * rift.radius * GROUND_STRIKE_SPREAD,
                    rift.centre.y,
                    rift.centre.z + Math.sin(emitter.anchor()) * rift.radius * GROUND_STRIKE_SPREAD);
            Vec3 probe = start.subtract(0.0D, GROUND_STRIKE_RANGE, 0.0D);
            BlockHitResult hit = level.clip(new ClipContext(start, probe,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue; // nothing within reach this flash
            }
            RiftShimmer.strike(hit.getBlockPos(), rift.colour);

            Vec3 end = hit.getLocation();
            double length = start.distanceTo(end);
            if (length < 0.05D) {
                continue; // struck right at the rift's own feet - nothing to draw a bolt along
            }
            Vector3f direction = new Vector3f(
                    (float) ((end.x - start.x) / length),
                    (float) ((end.y - start.y) / length),
                    (float) ((end.z - start.z) / length));
            // Any vector not parallel to the strike gives a stable perpendicular pair to jitter and
            // widen it in, the same construction the rift's own basis uses in the constructor.
            Vector3f seedVector = Math.abs(direction.y) > 0.9F
                    ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f perpA = new Vector3f(direction).cross(seedVector).normalize();
            Vector3f perpB = new Vector3f(direction).cross(perpA).normalize();

            double jitterMagnitude = length * GROUND_JITTER_FRACTION;
            double halfWidth = length * GROUND_WIDTH_FRACTION;

            double lastX = start.x;
            double lastY = start.y;
            double lastZ = start.z;
            double lastWidth = 0.0D;
            for (int point = 1; point <= RiftLightning.SEGMENTS; point++) {
                float t = point / (float) RiftLightning.SEGMENTS;
                // Pinched to nothing at both ends: this bolt is anchored at the rift and anchored at
                // its own impact, not free at either.
                float span = Mth.sin(t * (float) Math.PI);
                float kickA = RiftLightning.jitter(rift.seed, index, cycleNumber, point * 2) * span;
                float kickB = RiftLightning.jitter(rift.seed, index, cycleNumber, point * 2 + 1) * span;

                double baseX = Mth.lerp(t, start.x, end.x);
                double baseY = Mth.lerp(t, start.y, end.y);
                double baseZ = Mth.lerp(t, start.z, end.z);
                double x = baseX + (perpA.x * kickA + perpB.x * kickB) * jitterMagnitude;
                double y = baseY + (perpA.y * kickA + perpB.y * kickB) * jitterMagnitude;
                double z = baseZ + (perpA.z * kickA + perpB.z * kickB) * jitterMagnitude;
                double width = halfWidth * span;

                float segmentAlpha = glow * 0.85F;
                // White nearer the rift, cooling towards the rift's own colour as it runs - the same
                // read every other bolt in this class gives a fresh discharge.
                float white = 1.0F - t * 0.5F;
                float boltRed = Mth.lerp(white, red, 1.0F);
                float boltGreen = Mth.lerp(white, green, 1.0F);
                float boltBlue = Mth.lerp(white, blue, 1.0F);

                glowVertex(consumer, matrix,
                        (float) (lastX + perpA.x * lastWidth), (float) (lastY + perpA.y * lastWidth),
                        (float) (lastZ + perpA.z * lastWidth), boltRed, boltGreen, boltBlue, segmentAlpha);
                glowVertex(consumer, matrix,
                        (float) (x + perpA.x * width), (float) (y + perpA.y * width),
                        (float) (z + perpA.z * width), boltRed, boltGreen, boltBlue, segmentAlpha);
                glowVertex(consumer, matrix,
                        (float) (x - perpA.x * width), (float) (y - perpA.y * width),
                        (float) (z - perpA.z * width), boltRed, boltGreen, boltBlue, segmentAlpha);
                glowVertex(consumer, matrix,
                        (float) (lastX - perpA.x * lastWidth), (float) (lastY - perpA.y * lastWidth),
                        (float) (lastZ - perpA.z * lastWidth), boltRed, boltGreen, boltBlue, segmentAlpha);

                lastX = x;
                lastY = y;
                lastZ = z;
                lastWidth = width;
            }
        }
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

    /**
     * A vertex at {@code (u, v)} in the aperture's plane, {@code w} along its normal.
     *
     * <p>Every caller draws glow - cracks, sparks, the far light, the corners of every shard, and all
     * of {@link RiftFurniture} - so this goes down the glow path. See {@link #glowVertex}.
     *
     * <p>Package-private because it is the single primitive {@link RiftFurniture} is built out of:
     * every gear tooth, orbit, glyph and fang in that file is quads of these, which is what keeps the
     * themed geometry honest about living in the aperture's own frame.
     */
    static void localVertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                            double u, double v, double w,
                            float red, float green, float blue, float alpha) {
        // The up axis carries the aperture's aspect, so the glass, the debris and the cracks are all
        // squashed to the same shape as the hole they belong to rather than sitting circular inside a
        // rectangular one.
        v *= rift.aspect;
        POINT.set((float) (rift.centre.x + rift.right.x * u + rift.up.x * v + rift.normal.x * w),
                (float) (rift.centre.y + rift.right.y * u + rift.up.y * v + rift.normal.y * w),
                (float) (rift.centre.z + rift.right.z * u + rift.up.z * v + rift.normal.z * w));
        glowVertex(consumer, matrix, POINT.x, POINT.y, POINT.z,
                red, green, blue, Math.min(1.0F, alpha));
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

            double bore = cover * rift.reach(mote.angle(), time) * width;
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
        glowVertex(consumer, matrix, (float) x, (float) y, (float) z,
                red, green, blue, Math.min(1.0F, alpha));
    }

    /**
     * One vertex of a glow pass.
     *
     * <p>The glow render types carry the beacon beam's vertex format, so every vertex owes a texture
     * coordinate, a light level and a normal on top of its colour. All three are constants: the middle
     * of a white sprite, full brightness, and straight up. None of them says anything - they are there
     * because the format demands them, and the format is what routes a rift to a shader pack's
     * emissive program instead of its lit one. See {@code AWRenderTypes.RIFT_FIRE}.
     *
     * <p>This and {@link #solidVertex} are the only two methods in this class that touch a buffer, and
     * that is deliberate rather than tidy. The two passes now take different vertex formats, and a
     * helper that writes the wrong set of attributes does not draw badly - it throws
     * {@code Missing elements in vertex} and takes the game down mid-frame. Funnelling every write
     * through one method per format is what makes that mistake impossible to make quietly, and
     * {@code RiftVertexFormatTest} is what stops a third writer appearing later.
     */
    private static void glowVertex(VertexConsumer consumer, Matrix4f matrix,
                                   float x, float y, float z,
                                   float red, float green, float blue, float alpha) {
        // Clamped, because a channel above one is not brighter - it wraps. The buffer stores each
        // channel as a byte, so 1.6 of red is stored as 408 cast to a byte, which is a dim red, and a
        // ring meant to blaze comes out murky. Several passes push lit past one on purpose for a hot
        // core; every one of them meant "as bright as it goes".
        consumer.addVertex(matrix, x, y, z)
                .setColor(Math.min(1.0F, red), Math.min(1.0F, green), Math.min(1.0F, blue), alpha)
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /**
     * A point on the bore behind the mouth.
     *
     * <p>Only the position. The bore is drawn twice from two different passes - once as the solid
     * tube that hides the hull, and once as the haze burning around it - and those go into buffers
     * with different vertex formats, so the shared part stops here.
     */
    private static Vector3f bore(ActiveRift rift, double angle, double radius, double along) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius * rift.aspect;
        return POINT.set(
                (float) (rift.centre.x + rift.right.x * cos + rift.up.x * sin + rift.normal.x * along),
                (float) (rift.centre.y + rift.right.y * cos + rift.up.y * sin + rift.normal.y * along),
                (float) (rift.centre.z + rift.right.z * cos + rift.up.z * sin + rift.normal.z * along));
    }

    /** The solid tube, on the same path as the face it belongs to. */
    private static void throatVertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                     double angle, double radius, double along,
                                     float red, float green, float blue, float glow) {
        Vector3f point = bore(rift, angle, radius, along);
        solidVertex(consumer, matrix, point.x, point.y, point.z,
                red * glow, green * glow, blue * glow, 1.0F);
    }

    /** The haze burning around that tube, which is glow and goes the other way. */
    private static void hazeVertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   double angle, double radius, double along,
                                   float red, float green, float blue, float alpha) {
        Vector3f point = bore(rift, angle, radius, along);
        glowVertex(consumer, matrix, point.x, point.y, point.z,
                red, green, blue, Math.min(1.0F, alpha));
    }

    private static void face(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                             double angle, double radius, float red, float green, float blue, float glow) {
        // Opaque: alpha is ignored by this render type, so brightness has to live in the colour.
        emit(consumer, matrix, rift, 0.0F, angle, radius, red * glow, green * glow, blue * glow, 1.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                               double angle, double radius, float red, float green, float blue, float alpha) {
        Vector3f point = place(rift, bias, angle, radius);
        glowVertex(consumer, matrix, point.x, point.y, point.z,
                red, green, blue, Math.min(1.0F, alpha));
    }

    /**
     * A vertex of the opaque face.
     *
     * <p>Kept on the plain position-colour format the membrane has always used, deliberately. The
     * face's whole job is to write depth and paint over what is behind it, and that is a job for a
     * surface the pipeline treats as solid - unlike the glow around it, which had to be moved onto an
     * emissive path to stop shader packs lighting it.
     */
    private static void emit(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                             double angle, double radius, float red, float green, float blue, float alpha) {
        Vector3f point = place(rift, bias, angle, radius);
        solidVertex(consumer, matrix, point.x, point.y, point.z, red, green, blue, alpha);
    }

    /**
     * One vertex of the opaque face or the bore behind it.
     *
     * <p>Plain position and colour, which is the format the membrane has always used and must keep.
     * Its whole job is to write depth and paint over what is behind it, and that is a job for a
     * surface the pipeline treats as solid - unlike the glow around it, which had to be moved onto an
     * emissive path to stop shader packs lighting it.
     *
     * <p>One of exactly two methods in this class that touch a buffer; see {@link #glowVertex} for
     * the other, and for why that split is worth enforcing.
     */
    private static void solidVertex(VertexConsumer consumer, Matrix4f matrix,
                                    float x, float y, float z,
                                    float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }

    /**
     * Where a point on the aperture's disc lands in world space.
     *
     * <p>Written into a scratch vector rather than a fresh one because this runs a few thousand times
     * a frame and only ever on the render thread.
     */
    private static Vector3f place(ActiveRift rift, float bias, double angle, double radius) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius * rift.aspect;
        return POINT.set(
                (float) (rift.centre.x + rift.right.x * cos + rift.up.x * sin) + rift.normal.x * bias,
                (float) (rift.centre.y + rift.right.y * cos + rift.up.y * sin) + rift.normal.y * bias,
                (float) (rift.centre.z + rift.right.z * cos + rift.up.z * sin) + rift.normal.z * bias);
    }
}
