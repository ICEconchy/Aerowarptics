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
 * there is no texture to load, nothing to bind, and no shader dependency. With one exception, by
 * request: an Ender rift paints its face and bore with vanilla's own End portal program (see
 * {@code AWRenderTypes.RIFT_END_SKY}), which is still an opaque occluder and hides a hull the same way.
 * And one variation on the passes: a Sketch rift is drawn in ink, which is dark, so its furniture goes
 * on the translucent glass pass rather than the additive one - see {@link ThemeLook#inked}. A Mostly
 * Dangerous rift's cloud is dark for the same reason and goes on that pass too, ahead of the light that
 * plays over it; and that theme is the one thing that bends the view, which is {@link GravityLens}.
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

    /**
     * Ticks an aperture stands past the end of a passage if nothing closes it. The same spare the open cue
     * gives it, and for the same reason: only a backstop, against the cue that ends it going missing.
     */
    static final int PASSAGE_SPARE_TICKS = 40;

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

    /** Patches of the End an Ender rift's face bleeds through in before the hole exists. */
    private static final int BLEED_BLOTCHES = 6;
    /**
     * How far out those patches may reach, as a fraction of the rim. Inside it, because a patch past the
     * tear would be a square of starfield hanging in the sky beside the aperture.
     */
    private static final float BLEED_REACH = 0.95F;
    /** Where the bleed's blotches start in the rift's noise, clear of every other draw from it. */
    private static final int BLEED_SALT = 40_000;
    /** How far into the wind-up the End starts to show through. */
    private static final float BLEED_FROM = 0.25F;
    /** Segments round one patch. It is lumpy, not round, so it does not need many. */
    private static final int BLEED_SEGMENTS = 20;
    private static final Blotches.Blotch[] NO_BLOTCHES = {};
    private static final Nebula.Puff[] NO_PUFFS = {};
    private static final Nebula.Trail[] NO_TRAILS = {};
    private static final RiftLightning.Emitter[] NO_EMITTERS = {};

    /**
     * How near a rift someone has to be for the flashes lighting a hull coming out of it to light their
     * world too, as a multiple of its radius - and never less than {@link #FLASH_NEAREST} blocks.
     *
     * <p>The flash is vanilla's own lightning flash, which lights everything the sky lights. Close to, it
     * is the ship lit up by the storm it is coming out of; from the far side of a map it would be a
     * flicker with no cause anyone can see.
     */
    private static final double FLASH_REACH = 12.0D;
    private static final double FLASH_NEAREST = 64.0D;

    /**
     * How much of an Ender piece's life it spends at home, flickering, before it is somewhere else.
     *
     * <p>Under a third: the flicker is the warning and the hop is the event, and a warning that goes on
     * longer than what it warns of reads as the piece being stuck.
     */
    private static final float BLINK_HOP = 0.28F;

    /** Rings and segments across a Sketch's paper face - finer than the lit one, because it is coloured
     * in a piece at a time and the pieces are what the reveal is made of. */
    private static final int PAPER_RINGS = 6;
    private static final int PAPER_SEGMENTS = 48;
    /** How far the fill is lightened towards paper: crayon on a page is never the pure colour. */
    private static final float PAPER_TINT = 0.22F;

    /**
     * Half-width of a crack's bright core where it leaves the impact, as a fraction of the aperture.
     *
     * <p>Thin. The old spokes were wedges four times this wide at the root, which is a star, not a
     * crack - what a fracture in a window actually shows is a hairline of caught light.
     */
    private static final float CRACK_ROOT = 0.0075F;
    /** Half-width of that core at the rim. A fracture is widest where the glass took the blow. */
    private static final float CRACK_TIP = 0.0038F;
    /** Half-width of the soft light either side of a crack's core, as a fraction of the aperture. */
    private static final float CRACK_HALO = 0.022F;
    /**
     * Least half-width of a crack's core, per block of distance to the aperture: about half a pixel at
     * an ordinary field of view.
     *
     * <p>A line thinner than a pixel does not draw thinner, it draws intermittently, and a distant
     * pane's cracks would shimmer in and out as they fell between rows.
     */
    private static final double HAIRLINE = 0.0006D;
    /** How far through the crack phase the pane starts to sag, and its cells to catch the light. */
    private static final float SAG_FROM = 0.15F;
    /**
     * Ticks a piece of the web takes to fade once the glass round it has let go - about as long as a
     * loose piece takes to fade in, so the one hands over to the other.
     */
    private static final float FALL_FADE = 2.0F;
    /** How far through the crack phase the strike's flash has faded to the glow of the crush. */
    private static final float FLASH_SPAN = 0.35F;
    /** Wedges in the soft glow of the strike. */
    private static final int FLASH_WEDGES = 10;
    /** How thick the cut edge of a loose piece of glass is, as a fraction of the aperture. */
    private static final float GLASS_EDGE = 0.016F;
    /** Most of a piece's size its cut edge may take up. Past this a small piece is all edge anyway. */
    private static final float GLASS_EDGE_MAX = 0.42F;
    /** How fast a loose piece of glass falls, as a fraction of the aperture per tick, per tick. */
    private static final float GLASS_FALL = 0.0009F;
    /** Render-thread scratch for {@link #glassPiece}: its corners, and the same corners inset by its edge. */
    private static final double[] PIECE_U = new double[4];
    private static final double[] PIECE_V = new double[4];
    private static final double[] INSET_U = new double[4];
    private static final double[] INSET_V = new double[4];


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
        /** What the face and bore are made of - see {@link ThemeLook.Surface}. */
        final ThemeLook.Surface surface;
        /** When the wind-up makes a sound, as fractions of it - see {@link ThemeLook#beats}. */
        final float[] beats;
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
        /** The cracks the pane shows before it goes, and the corners its pieces are cut at. */
        final RiftShatter.Web web;
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
         * The blobs this aperture's face is covered by as it arrives, if its theme has any: the End
         * bleeding through an Ender rift, or the patches a Sketch colours in. Empty for every other theme.
         */
        final Blotches.Blotch[] blotches;
        /** What a Sketch's pencil draws and when, or {@code null} for a rift nobody is drawing. */
        final SketchPlan sketch;
        /** A Mostly Dangerous rift's cloud, or empty for every other theme - see {@link Nebula}. */
        final Nebula.Puff[] puffs;
        /** The lightning crawling over that cloud, and the flashes deep inside it. */
        final RiftLightning.Emitter[] cloudArcs;
        final RiftLightning.Emitter[] cloudFlashes;
        /** Where the trails hang once a ship has come out of it. */
        final Nebula.Trail[] trails;
        /**
         * When a hull reached this aperture and when it should be done with it, in ticks since the rift
         * appeared, or {@link SketchPencil#NEVER}.
         *
         * <p>Only a Sketch's pencil reads them, to get out of the way and to come back. Kept as the two
         * moments rather than as a smoothed number so that everything the pencil does stays a pure
         * function of the clock - see {@link SketchPencil}.
         */
        float transitFrom = SketchPencil.NEVER;
        float transitUntil = SketchPencil.NEVER;

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
            if (surface == ThemeLook.Surface.PAPER) {
                // A drawn circle is not torn. Its edge is the line somebody drew round it, so the face,
                // the bore and every stroke share one clean rim rather than a crawling one.
                return 1.0D;
            }
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

        /**
         * Whether this rift hangs behind glass, and so has its light drawn before the glass rather than after
         * the particles - see {@link RiftEffectManager#drawLight}. A fissure, and nothing else.
         */
        boolean underGlass;

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
            this.surface = ThemeLook.surface(theme);
            this.beats = ThemeLook.beats(theme);
            this.throat = throat;
            this.openTicks = openTicks;
            this.holdTicks = holdTicks;
            this.closeTicks = closeTicks;
            this.seed = where;
            this.theme = theme;
            this.pattern = RiftShatter.patternFor(theme);
            this.web = RiftShatter.web(seed, pattern);
            this.shards = RiftShatter.fracture(web);
            this.motes = RiftDebris.field(seed);
            this.blotches = surface == ThemeLook.Surface.END_SKY
                    ? Blotches.scatter(where, BLEED_BLOTCHES, BLEED_REACH, BLEED_SALT)
                    : NO_BLOTCHES;
            this.sketch = surface == ThemeLook.Surface.PAPER
                    ? SketchPlan.of(where, Math.max(1.0F, openTicks))
                    : null;
            boolean cloud = surface == ThemeLook.Surface.NEBULA;
            this.puffs = cloud ? Nebula.puffs(where) : NO_PUFFS;
            this.cloudArcs = cloud ? Nebula.arcField(where) : NO_EMITTERS;
            this.cloudFlashes = cloud ? Nebula.flashField(where) : NO_EMITTERS;
            this.trails = cloud ? Nebula.trails(where) : NO_TRAILS;
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

        /**
         * The handful of moments a Sketch's pencil acts on.
         *
         * <p>Built fresh rather than kept, because two of them move: the close is wherever the hold
         * currently ends, which {@link #collapse} brings forward the instant the rift is told to let go.
         */
        SketchPencil.Events sketchEvents() {
            return new SketchPencil.Events(openTicks, transitFrom, transitUntil,
                    openTicks + holdTicks, closeTicks, seed);
        }

        boolean expired() {
            return age >= lifetime();
        }

        /**
         * A hull is going through: jump to open, grow the throat, and stand for as long as it takes.
         *
         * @param ticks how long the hull will be inside, by the server's reckoning
         */
        void passage(int ticks) {
            transitTicks = Math.max(transitTicks, ticks);
            // A hull has arrived, so the aperture stops animating and is simply there. Hiding a ship is
            // the face's job and it cannot do it half open - the same reasoning as the throat below. This
            // is what stops a short run at the rift, or a brisk one, from catching the shatter mid-break
            // and flying a visible hull through a hole that has not finished appearing.
            if (age < openTicks) {
                age = openTicks;
                lastAge = age;
            }
            // Held for the whole passage and a margin, whatever it was opened with. That hold was the
            // server's forecast of when the hull would come, made seconds ago, and a server that fell
            // behind since - a large hull's crossing, the far side's ground loading - would otherwise see
            // the aperture close on schedule around a hull that is still inside it. An explicit close
            // still ends it the moment the hull is out; this only moves the backstop.
            holdTicks = Math.max(holdTicks, age - openTicks + ticks + PASSAGE_SPARE_TICKS);
            // When the pencil has to get out of the way, and when it may come back. Taken after the jump
            // above, so a hull that arrives before the drawing was finished ducks it from where the jump
            // left it rather than from where it would have been.
            if (transitFrom < 0.0F) {
                transitFrom = age;
            }
            transitUntil = Math.max(transitUntil, age + ticks);
            if (throat < 0.0F) {
                // An aperture a hull comes *out* of already has the hull inside it the moment this
                // arrives, so its throat has to be there immediately. Growing it over a few ticks would
                // leave the ship briefly visible from the side, which is the whole thing being fixed.
                throatOpen = 1.0F;
                throatOpenLast = 1.0F;
            }
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
            if (surface == ThemeLook.Surface.PAPER) {
                // A drawing does not grow: it is coloured in, and the face appears where the crayon has
                // been - see drawPaperFace. So the hole is full size from the first stroke, and the only
                // thing that ever shrinks it is being screwed up at the end.
                return SketchPencil.crumple(sealProgress(partialTick));
            }
            if (time < openTicks) {
                return RiftShatter.hole(openProgress(partialTick));
            }
            if (time < openTicks + holdTicks) {
                return 1.0F;
            }
            if (surface == ThemeLook.Surface.NEBULA) {
                // Not a hole shrinking but a cloud falling in on itself, and the hole goes with it on the
                // same curve - so the cloud's inner edge is over the rim the whole way down.
                return Nebula.shrink(Nebula.collapse(sealProgress(partialTick)));
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
            return sealAt(Mth.lerp(partialTick, lastAge, age));
        }

        /** 0..1 through the close at a given age - for asking where a tick started and where it ended. */
        float sealAt(float time) {
            float elapsed = time - openTicks - holdTicks;
            return elapsed <= 0.0F ? 0.0F : Math.min(1.0F, elapsed / Math.max(1.0F, closeTicks));
        }

        /** Whether the aperture has been told to let go, however far through that it is. */
        boolean isCollapsing() {
            return age >= openTicks + holdTicks;
        }

    }

    // ------------------------------------------------------------------ feed

    /**
     * What the moment of release sounds like, for whichever way this aperture comes apart - or
     * {@code null} for one that makes its own noises from its own timeline instead.
     */
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
            case ENDER -> SoundEvents.ENDERMAN_TELEPORT; // the pane does exactly what an enderman does
            // A drawing is never heard breaking, because it never breaks: its pencil is heard instead,
            // stroke by stroke - see playSketch.
            case SKETCH -> null;
            // Nothing breaks here either, but a hole opening in the middle of a thundercloud is heard.
            case MOSTLY_DANGEROUS -> SoundEvents.LIGHTNING_BOLT_THUNDER;
        };
    }

    /**
     * What a theme's wind-up sounds like at each of its {@link ThemeLook#beats}, or {@code null} for a
     * theme whose wind-up is silent.
     */
    private static SoundEvent beatSound(RiftModulatorTheme theme) {
        return switch (theme) {
            // Heard the instant the eyes start to open, which is the instant it would be in the End.
            case ENDER -> SoundEvents.ENDERMAN_STARE;
            // A charge building out of nothing, which is what the cloud is doing.
            case MOSTLY_DANGEROUS -> SoundEvents.WARDEN_SONIC_CHARGE;
            // A sketch's pencil is heard stroke by stroke from its own plan, well past the wind-up - see
            // onClientTick - rather than at moments inside it.
            case SKETCH, STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK, STARBLOCKS, BEDROCK, BOLDLY_GONE,
                    LUDICROUS, EVENTFUL_HORIZON, VWORP, IMPROBABILITY -> null;
        };
    }

    /** Opens a rift from a server cue. */
    public static void open(ClientboundWarpEffectPacket packet, int colour, int accentColour,
                            RiftModulatorTheme theme, int openTicks, int holdTicks, int closeTicks) {
        if (!packet.hasRift() || !AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
        ActiveRift rift = new ActiveRift(packet.centre(), packet.normal(), packet.radius(), colour, accentColour,
                packet.throat(), theme, openTicks, holdTicks, closeTicks);
        // Round for any drive's own rift. A flotilla going through one together tears an ellipse round the
        // whole formation, which is usually far wider than it is tall - see FleetPassage. Bounded, so a cue
        // from a confused server cannot draw a rift a hair tall or a mile high.
        float aspect = packet.aspect();
        rift.aspect = Float.isFinite(aspect) ? Mth.clamp(aspect, 0.02F, 50.0F) : 1.0F;
        ACTIVE.add(rift);
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
        hold(holder, centre, normal, halfWidth, halfHeight, colour, colour, RiftModulatorTheme.STANDARD, openTicks,
                groundStrikes);
    }

    /**
     * {@link #hold}, dressed: a held aperture in a Modulator's colours and theme. Only a Collapse Cradle's
     * ritual asks for one, with the look of the Modulator standing beside it; the theme is fixed when the
     * aperture is first opened, as a drive's is.
     */
    public static void hold(long holder, Vec3 centre, Vec3 normal, double halfWidth, double halfHeight,
                            int colour, int accentColour, RiftModulatorTheme theme, int openTicks,
                            boolean groundStrikes) {
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
        // Flat colour and the standard break for gates and chutes, which have no Modulator to dress them.
        ActiveRift rift = new ActiveRift(centre, normal, halfWidth, colour, accentColour, 0.0F,
                theme, Math.max(1, openTicks), HELD_FOREVER, GATE_CLOSE_TICKS);
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
     * Says a held rift hangs behind glass, so its light is drawn before the glass - see {@link #drawLight}.
     * Asked each tick it is held, as its size is, so it survives the aperture being opened anew.
     */
    public static void underGlass(long holder) {
        for (ActiveRift rift : ACTIVE) {
            if (rift.holder == holder) {
                rift.underGlass = true;
                return;
            }
        }
    }

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
        if (rift != null) {
            rift.passage(ticks);
        }
    }

    /**
     * Keeps the aperture at this position up for at least this many ticks more.
     *
     * @return whether there was one standing to keep up
     */
    public static boolean renew(Vec3 centre, int ticks) {
        ActiveRift rift = nearest(centre);
        if (rift == null) {
            return false;
        }
        rift.holdTicks = Math.max(rift.holdTicks, rift.age - rift.openTicks + ticks);
        return true;
    }

    /**
     * Whether this client has a standing aperture at this position.
     *
     * <p>Asked before a transit cue, because a client can be told a hull is going through an aperture it
     * never saw open - see {@code WarpEffects.onStage}.
     */
    public static boolean isOpen(Vec3 centre) {
        return nearest(centre) != null;
    }

    /** Collapses the aperture at this position, rather than waiting for it to time out. */
    public static void collapse(Vec3 centre) {
        ActiveRift rift = nearest(centre);
        if (rift != null) {
            rift.collapse();
        }
    }

    /**
     * The standing rift closest to a position, if one is close enough to be the one meant.
     *
     * <p>Matched by position because that is the only thing both ends of a cue agree on: the server
     * plans an aperture at a point and every later cue about it carries that same point.
     *
     * <p>One already closing is never the one meant. It cannot hide a hull, and it is already doing the
     * only thing a close could ask of it - so a transit cue that finds only that opens a fresh aperture in
     * front of it, and a close goes to the fresh one rather than being spent on the one already going.
     */
    private static ActiveRift nearest(Vec3 centre) {
        ActiveRift best = null;
        double bestDistance = 4.0D * 4.0D;
        for (ActiveRift rift : ACTIVE) {
            if (rift.isCollapsing()) {
                continue;
            }
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
                // A theme with nothing to break may have nothing to say about it either: a Sketch is
                // drawn open, and makes its own noises from its own timeline - see playSketch.
                SoundEvent release = breakSound(rift.theme);
                if (release != null) {
                    // Volume over one is range rather than loudness in Minecraft, so a big aperture is
                    // heard breaking from further off rather than more sharply from close up.
                    level.playLocalSound(rift.centre.x, rift.centre.y, rift.centre.z,
                            release, SoundSource.BLOCKS,
                            (float) Math.min(4.0D, 1.6D + rift.radius * 0.10D), 0.45F, true);
                }
            }
            playBeats(level, rift);
            playSketch(level, rift);
            playNebula(level, rift);
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

    /**
     * The sounds a theme's wind-up makes, at the moments its drawing reaches them.
     *
     * <p>One at most per tick. A rift jumped straight to open by a hull arriving has skipped its wind-up
     * and plays none of them - the break it lands on is the only thing worth hearing by then.
     */
    private static void playBeats(ClientLevel level, ActiveRift rift) {
        if (rift.beats.length == 0 || rift.lastAge >= rift.openTicks) {
            return;
        }
        SoundEvent sound = beatSound(rift.theme);
        if (sound == null) {
            return;
        }
        float windUp = Math.max(1.0F, rift.openTicks * RiftShatter.CRACK_PHASE);
        float before = rift.lastAge / windUp;
        float after = rift.age / windUp;
        for (int beat = 0; beat < rift.beats.length; beat++) {
            if (before < rift.beats[beat] && after >= rift.beats[beat]) {
                level.playLocalSound(rift.centre.x, rift.centre.y, rift.centre.z, sound, SoundSource.BLOCKS,
                        (float) Math.min(2.0D, 0.8D + rift.radius * 0.05D),
                        0.9F + 0.2F * RiftShatter.noise(rift.seed, 31 + beat), true);
                return;
            }
        }
    }

    /**
     * A Sketch's pencil, heard: the scratch of it touching down on a line, its point tapping the page,
     * the swish of it whirling about, and the scribbling out, crumpling and lobbing at the end.
     *
     * <p>All of it comes from the same timeline the pencil is drawn from, so nothing can be heard that
     * is not also being watched.
     */
    private static void playSketch(ClientLevel level, ActiveRift rift) {
        if (rift.sketch == null) {
            return;
        }
        float volume = (float) Math.min(2.0D, 0.7D + rift.radius * 0.05D);
        for (SketchPencil.Cue cue : SketchPencil.cues(rift.sketch, rift.sketchEvents(), rift.lastAge, rift.age)) {
            float pitch = 0.85F + 0.3F * RiftShatter.noise(rift.seed, 33 + rift.age);
            switch (cue) {
                case DRAW -> play(level, rift, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, volume, pitch);
                // Faster and higher than the drawing: the same pencil, used in a hurry.
                case SCRIBBLE -> play(level, rift, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                        volume, pitch + 0.45F);
                // Quiet, because an idle pencil is background: it should be noticed, not listened to.
                case TAP -> play(level, rift, SoundEvents.WOODEN_BUTTON_CLICK_ON, volume * 0.35F, pitch + 0.6F);
                case SWISH -> play(level, rift, SoundEvents.PLAYER_ATTACK_SWEEP, volume * 0.3F, pitch + 0.5F);
                case CRUMPLE -> play(level, rift, SoundEvents.BUNDLE_INSERT, volume, 0.7F);
                case TOSS -> play(level, rift, SoundEvents.SNOWBALL_THROW, volume, 0.8F);
            }
        }
    }

    /**
     * A Mostly Dangerous rift's moments: the flashes that light a hull coming out, and the cloud falling
     * in on itself - heard as it starts to go, and as the last of it arrives in the middle, which is also
     * when the smoke and the wreckage are thrown out.
     *
     * <p>The flashes light the world only for whoever is near enough to see what caused them, and only at
     * the far end of a jump, where a ship is coming out: they are vanilla's lightning flash, so they also
     * respect its Hide Lightning Flashes setting without any help from here.
     */
    private static void playNebula(ClientLevel level, ActiveRift rift) {
        if (rift.surface != ThemeLook.Surface.NEBULA) {
            return;
        }
        float volume = (float) Math.min(3.0D, 1.0D + rift.radius * 0.08D);
        if (rift.throat < 0.0F) {
            int flashes = Nebula.emergenceStarts(rift.lastAge, rift.age, rift.transitFrom);
            if (flashes > 0) {
                Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                double near = Math.max(FLASH_NEAREST, rift.radius * FLASH_REACH);
                if (eye.distanceToSqr(rift.centre) < near * near) {
                    level.setSkyFlashTime(2);
                }
                play(level, rift, SoundEvents.LIGHTNING_BOLT_IMPACT, volume * 0.6F,
                        0.7F + 0.4F * RiftShatter.noise(rift.seed, 35 + rift.age));
            }
        }
        float before = rift.sealAt(rift.lastAge);
        float after = rift.sealAt(rift.age);
        if (before <= 0.0F && after > 0.0F) {
            // The in-breath: everything about to be dragged into the middle.
            play(level, rift, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), volume, 0.5F);
        }
        if (before < Nebula.COLLAPSE_TO && after >= Nebula.COLLAPSE_TO) {
            play(level, rift, SoundEvents.GENERIC_EXPLODE.value(), volume, 0.55F);
            WarpEffects.dispersal(level, rift.centre, rift.right, rift.up, rift.normal, rift.radius);
        }
    }

    private static void play(ClientLevel level, ActiveRift rift, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(rift.centre.x, rift.centre.y, rift.centre.z, sound, SoundSource.BLOCKS,
                volume, pitch, true);
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
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

        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        Matrix4f matrix = poseStack.last().pose();

        if (membranePass) {
            // The face goes down with the world's blocks so it can occlude them. Throat first, then
            // the face over its mouth: the face is what a viewer looking straight down the aperture
            // sees, and it must win.
            VertexConsumer consumer = buffers.getBuffer(AWRenderTypes.RIFT_MEMBRANE);
            boolean endSky = false;
            for (ActiveRift rift : ACTIVE) {
                if (rift.surface == ThemeLook.Surface.END_SKY) {
                    endSky = true;
                    continue;
                }
                drawThroat(consumer, matrix, rift, partialTick);
                drawFace(consumer, matrix, rift, partialTick);
                // The pencil a sketch is drawn with, and the ball it screws the drawing up into. Solid,
                // so they go where solid things go: they hide what is behind them, and the lines drawn
                // afterwards are hidden where they cover them.
                RiftFurniture.pencil(consumer, matrix, rift, partialTick, eye);
                RiftFurniture.paperBall(consumer, matrix, rift, partialTick, eye);
            }
            buffers.endBatch(AWRenderTypes.RIFT_MEMBRANE);

            // The End's rifts, as a batch of their own after the membrane's is finished: both are
            // custom types sharing one buffer, and asking for the second would end the first under the
            // loop still writing into it. Same order, same geometry - only the shader differs - plus the
            // sky bleeding through ahead of the hole.
            if (endSky) {
                VertexConsumer sky = buffers.getBuffer(AWRenderTypes.RIFT_END_SKY);
                for (ActiveRift rift : ACTIVE) {
                    if (rift.surface != ThemeLook.Surface.END_SKY) {
                        continue;
                    }
                    drawThroat(sky, matrix, rift, partialTick);
                    drawBleed(sky, matrix, rift, partialTick);
                    drawFace(sky, matrix, rift, partialTick);
                }
                buffers.endBatch(AWRenderTypes.RIFT_END_SKY);
            }

            // Bent space, last: the lens redraws the view round a rift out of a copy of the frame, and the
            // copy has to be taken now, once every face and throat is down. A copy taken before them would
            // hold the very hull they are there to hide, and the lens would draw it bent round the rift for
            // everyone to see. Taken after, it can only move about what was already on screen. See GravityLens.
            GravityLens.draw(ACTIVE, buffers, camera, partialTick);

            // And the light of any rift that hangs behind glass, now rather than after the particles - see
            // drawLight for why.
            for (ActiveRift rift : ACTIVE) {
                if (rift.underGlass) {
                    drawLight(buffers, matrix, partialTick, eye, true);
                    break;
                }
            }
        } else {
            drawLight(buffers, matrix, partialTick, eye, false);
        }

        poseStack.popPose();
    }

    /**
     * Everything of a rift that is light, ink or glass rather than a solid face: the haze, the fire, what stands
     * round it, its lightning, and its shards.
     *
     * <p>Drawn twice a frame, for two kinds of rift. Most are drawn after the particles, over everything. A rift
     * that hangs behind glass - a fissure, which is what a Rift Wellhead's frame is built round - is drawn
     * {@code early}, straight after its own face and before the game's tripwire layer, which is where Warp Glass
     * is. Glass writes depth, so light drawn after it is thrown away wherever the glass stands in front: the
     * face showed through and its rings did not (seen in game, 2026-10-09). Drawn before, the glass blends over
     * the lot. Only fissures, because moving every rift's light ahead of the particles would change how every
     * rift in the mod looks for the sake of the one that is ever boxed in.
     *
     * @param early whether this is the pass for rifts behind glass
     */
    private static void drawLight(MultiBufferSource.BufferSource buffers, Matrix4f matrix, float partialTick, Vec3 eye,
                                  boolean early) {
        // A cloud is dark, and additive light cannot draw dark - so it goes on the glass pass, the way
        // ink does, and it goes first, as a batch of its own: the lightning crawling over it and the
        // flashes inside it are light, and have to land on top of it rather than be dimmed by it.
        boolean clouded = false;
        for (ActiveRift rift : ACTIVE) {
            if (rift.underGlass != early) {
                continue;
            }
            clouded |= rift.surface == ThemeLook.Surface.NEBULA;
        }
        if (clouded) {
            VertexConsumer cloud = buffers.getBuffer(AWRenderTypes.RIFT_SHARD);
            for (ActiveRift rift : ACTIVE) {
                if (rift.underGlass != early) {
                    continue;
                }
                RiftFurniture.cloud(cloud, matrix, rift, partialTick, eye);
            }
            buffers.endBatch(AWRenderTypes.RIFT_SHARD);
        }

        // Additive light over everything, including the face it is burning around. Two-sided:
        // an aperture is a hole people stand on both sides of, and a culled glow means half of
        // them are looking at a bare occluder.
        VertexConsumer fire = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
        for (ActiveRift rift : ACTIVE) {
            if (rift.underGlass != early) {
                continue;
            }
            // A rift drawn in ink has no light in it at all - no haze, no fire, no far light, no
            // flash. What it has instead goes on the glass pass below, because ink is dark and this
            // pass can only add.
            if (rift.surface == ThemeLook.Surface.PAPER) {
                continue;
            }
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
            if (rift.underGlass != early) {
                continue;
            }
            drawShards(glass, matrix, rift, partialTick, eye);
            drawSeal(glass, matrix, rift, partialTick, eye);
            drawMotes(glass, matrix, rift, partialTick, eye);
            // A drawn rift's pencil lines and crayon: its wind-up, what stands round it, and its bore.
            RiftFurniture.inked(glass, matrix, rift, partialTick, eye);
        }
        buffers.endBatch(AWRenderTypes.RIFT_SHARD);
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
        if (rift.surface == ThemeLook.Surface.PAPER) {
            drawPaperFace(consumer, matrix, rift, partialTick, aperture);
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
            // Nothing in the middle, a glow towards the edge, and the rim itself burning - unless the face
            // is paper, which is simply paper all the way across.
            float innerGlow = faceGlow(rift, innerT);
            float outerGlow = faceGlow(rift, outerT);
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
     * A Sketch's face: the hole coloured in.
     *
     * <p>Revealed by the crayon rather than grown as a circle. A piece of the face is there once the
     * colouring has reached it - {@code SketchPlan.front} says how far that is, taken from where the
     * pencil's point actually is - so the hole fills in behind the crayon, band by band, and is complete
     * the moment the fill stroke is. A hull that arrives early jumps the rift to fully open, which lands
     * here as all of it, so the face is never caught half coloured with a ship coming at it.
     *
     * <p>Finer than the lit face, because here the pieces are what the reveal is made of: a coarse one
     * would colour itself in in four visible steps.
     *
     * <p>At the close it is crumpled instead - shrinking towards the middle and going ragged at its edge
     * as it screws up, which is {@link SketchPencil#crumple} and {@link SketchPencil#jag}.
     */
    private static void drawPaperFace(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                      float partialTick, float crumple) {
        SketchPlan plan = rift.sketch;
        if (plan == null) {
            return;
        }
        float time = Mth.lerp(partialTick, rift.lastAge, rift.age);
        float front = plan.front(time);
        if (front <= -1.0F) {
            return; // nothing coloured in yet: the world is still there to see inside the outline
        }
        float jag = SketchPencil.jag(rift.sealProgress(partialTick));
        // The pilot's own colour, lightened towards paper: crayon lets the page through, and a flat fill
        // of the pure colour reads as a painted disc rather than as something coloured in.
        float red = Mth.lerp(PAPER_TINT, ((rift.colour >> 16) & 0xFF) / 255.0F,
                ((ThemeLook.PAPER >> 16) & 0xFF) / 255.0F);
        float green = Mth.lerp(PAPER_TINT, ((rift.colour >> 8) & 0xFF) / 255.0F,
                ((ThemeLook.PAPER >> 8) & 0xFF) / 255.0F);
        float blue = Mth.lerp(PAPER_TINT, (rift.colour & 0xFF) / 255.0F, (ThemeLook.PAPER & 0xFF) / 255.0F);
        double cover = rift.radius * crumple;

        for (int ring = 0; ring < PAPER_RINGS; ring++) {
            double inner = ring / (double) PAPER_RINGS;
            double outer = (ring + 1) / (double) PAPER_RINGS;
            for (int segment = 0; segment < PAPER_SEGMENTS; segment++) {
                double a0 = segment * (Math.PI * 2.0D / PAPER_SEGMENTS);
                double a1 = (segment + 1) * (Math.PI * 2.0D / PAPER_SEGMENTS);
                double midAngle = (a0 + a1) * 0.5D;
                double midRadius = (inner + outer) * 0.5D;
                // Has the crayon reached this piece? Measured across the fill's own heading, which is the
                // line the colouring sweeps along.
                if (plan.across((float) (Math.cos(midAngle) * midRadius),
                        (float) (Math.sin(midAngle) * midRadius)) > front) {
                    continue;
                }
                double fold0 = crease(rift, segment, jag);
                double fold1 = crease(rift, segment + 1, jag);
                solidLocal(consumer, matrix, rift, Math.cos(a0) * inner * cover * fold0,
                        Math.sin(a0) * inner * cover * fold0, red, green, blue);
                solidLocal(consumer, matrix, rift, Math.cos(a1) * inner * cover * fold1,
                        Math.sin(a1) * inner * cover * fold1, red, green, blue);
                solidLocal(consumer, matrix, rift, Math.cos(a1) * outer * cover * fold1,
                        Math.sin(a1) * outer * cover * fold1, red, green, blue);
                solidLocal(consumer, matrix, rift, Math.cos(a0) * outer * cover * fold0,
                        Math.sin(a0) * outer * cover * fold0, red, green, blue);
            }
        }
    }

    /**
     * How far in the page is folded at one of its segments as it crumples - shared by the face and by
     * every line drawn on it, so the paper and its drawing screw up together.
     */
    static double crease(ActiveRift rift, int segment, float jag) {
        return jag <= 0.0F ? 1.0D
                : 1.0D - jag * RiftShatter.noise(rift.seed, 23_000 + Math.floorMod(segment, PAPER_SEGMENTS));
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

        // A paper tube is paper, whatever colour the hole in front of it was coloured in.
        boolean paper = rift.surface == ThemeLook.Surface.PAPER;
        int tube = paper ? ThemeLook.PAPER : rift.colour;
        float red = ((tube >> 16) & 0xFF) / 255.0F;
        float green = ((tube >> 8) & 0xFF) / 255.0F;
        float blue = (tube & 0xFF) / 255.0F;

        float band = bandPosition(rift, partialTick);

        for (int ring = 0; ring < THROAT_RINGS.length - 1; ring++) {
            float nearT = THROAT_RINGS[ring];
            float farT = THROAT_RINGS[ring + 1];
            // Every few ribs is a gate: a bright ring rather than another stripe. A hull passing
            // through one of these is the clearest speed cue the corridor has, because it is a
            // discrete event at a known distance rather than a gradient sliding by.
            float nearGlow = paper ? paperGlow(nearT) : Math.min(1.0F, throatGlow(nearT, band)
                    + (ring % GATE_EVERY == 0 ? GATE_GLOW : 0.0F));
            float farGlow = paper ? paperGlow(farT) : Math.min(1.0F, throatGlow(farT, band)
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
     * The End showing through an Ender rift before the rift is there.
     *
     * <p>The one theme whose occluder arrives early, and deliberately: the stare is meant to be
     * something looking out of the End at you, which it cannot do from in front of an intact sky. So
     * from a quarter of the way into the wind-up, lumpy patches of the End's surface spread across the
     * face - each growing from its own moment until they are all full size by the break - and the face
     * proper then grows in over them. The shader projects from the screen, so where a patch and the face
     * overlap there is nothing to see but one sky.
     *
     * <p>This only ever adds occluder, never removes any, so it cannot weaken the face's hiding of a
     * hull - and no hull is near the plane while a rift is still winding up anyway; one that arrives
     * early jumps the rift straight to open, which ends this.
     */
    private static void drawBleed(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float partialTick) {
        float progress = rift.openProgress(partialTick);
        if (progress >= 1.0F || rift.blotches.length == 0) {
            return;
        }
        float charge = progress / RiftShatter.CRACK_PHASE;
        float spread = (charge - BLEED_FROM) / (1.0F - BLEED_FROM);
        if (spread <= 0.0F) {
            return;
        }
        float creep = (rift.lastAge + partialTick) * 0.1F;
        for (Blotches.Blotch blotch : rift.blotches) {
            float grown = Blotches.grown(blotch, spread);
            if (grown <= 0.001F) {
                continue;
            }
            double size = rift.radius * blotch.radius() * grown;
            double centreU = rift.radius * blotch.u();
            double centreV = rift.radius * blotch.v();
            for (int segment = 0; segment < BLEED_SEGMENTS; segment++) {
                double a0 = segment * (Math.PI * 2.0D / BLEED_SEGMENTS);
                double a1 = (segment + 1) * (Math.PI * 2.0D / BLEED_SEGMENTS);
                double r0 = size * Blotches.edge(blotch, a0, creep);
                double r1 = size * Blotches.edge(blotch, a1, creep);
                // A fan, as quads with the middle twice - the same trick the fangs use for a triangle.
                solidLocal(consumer, matrix, rift, centreU, centreV, 1.0F, 1.0F, 1.0F);
                solidLocal(consumer, matrix, rift, centreU + Math.cos(a0) * r0, centreV + Math.sin(a0) * r0,
                        1.0F, 1.0F, 1.0F);
                solidLocal(consumer, matrix, rift, centreU + Math.cos(a1) * r1, centreV + Math.sin(a1) * r1,
                        1.0F, 1.0F, 1.0F);
                solidLocal(consumer, matrix, rift, centreU, centreV, 1.0F, 1.0F, 1.0F);
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
        if (rift.surface == ThemeLook.Surface.NEBULA) {
            return; // the cloud is its own atmosphere, and a sleeve of black light adds nothing
        }
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

    /**
     * How lit the face is at a given fraction of the way out: black throat, burning edge.
     *
     * <p>Paper is the exception, and is flat: the shading on a drawn hole is the pencil's job, and a
     * face that went dark in the middle would be a hole drawn in light with pencil lines over it.
     */
    private static float faceGlow(ActiveRift rift, float t) {
        if (rift.surface == ThemeLook.Surface.PAPER) {
            return 1.0F;
        }
        if (rift.surface == ThemeLook.Surface.NEBULA) {
            // Black to the edge, bar the faintest trace of the arc-light at the very rim: the heart of a
            // thundercloud, not a tear burning.
            return 0.03F + 0.12F * t * t;
        }
        return 0.04F + 0.96F * (float) Math.pow(t, 3.5D);
    }

    /**
     * How light a paper bore is at a given fraction of its depth: nearly as pale as the face at the
     * mouth, and a little greyer further down, the way paper looks rolled into a tube. Nothing travels
     * along it and no rib flares - the hatching drawn over it is what gives it depth.
     */
    private static float paperGlow(float t) {
        return 1.0F - 0.28F * t;
    }

    /**
     * The additive fire: churn across the face and flames licking past the torn rim.
     *
     * <p>Drawn after the face, so it burns on top of it rather than being swallowed by it.
     */
    private static void drawFire(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                 float partialTick, Vec3 eye) {
        if (rift.surface == ThemeLook.Surface.NEBULA) {
            return; // a cloud does not burn: its light is its lightning, drawn by RiftFurniture
        }
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
     * The pane cracking, before anything has broken loose.
     *
     * <p>Drawn the way a struck window actually goes, over an intact view - the hole does not exist
     * yet, and the world behind the aperture is still there to see. A white crush of pulverised glass
     * where the blow landed; radial cracks racing out of it at their own paces, kinking wherever they
     * cross a ring; ring cracks zipping across between them from the middle outwards, each only once
     * both the cracks it bridges have got that far; and the odd short branch that forks off and runs
     * out. The cells between them start catching the light at slightly different angles as the sheet
     * sags, and just before it goes every crack flares as it opens up. This is the whole of what an
     * opening rift looks like for its first half second under the standard theme, and it is the reason
     * the hole arriving lands as an event rather than as a shape growing.
     *
     * <p>Every crack between two rings is a line the pieces are cut along - see {@link RiftShatter.Web}
     * - so when the pane goes, each shard that leaves is a cell the eye has just watched being drawn.
     */
    private static void drawCracks(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   float partialTick, Vec3 eye) {
        float progress = rift.openProgress(partialTick);
        float since = rift.sinceBreak(partialTick);
        // The web outlives the break for exactly as long as there is glass left standing for it to be
        // in, and goes with that glass - see standing().
        if (progress >= 1.0F || since > RiftShatter.BREAK_SPREAD + FALL_FADE) {
            return;
        }
        // Held at its end once the pane has gone: nothing cracks any further, it only falls.
        float crackProgress = Math.min(1.0F, progress / RiftShatter.CRACK_PHASE);

        double towardsEye = (eye.x - rift.centre.x) * rift.normal.x
                + (eye.y - rift.centre.y) * rift.normal.y
                + (eye.z - rift.centre.z) * rift.normal.z;
        float bias = towardsEye >= 0.0D ? FIRE_BIAS : -FIRE_BIAS;
        float[] tint = RiftFurniture.channels(rift.colour);

        // Full radius, not the aperture's: the pane that is breaking is already the size of the hole
        // it is about to become. The hole then opens outwards to meet the pieces leaving it.
        double cover = rift.radius;
        double hairline = eye.distanceTo(rift.centre) * HAIRLINE;
        double halo = Math.max(cover * CRACK_HALO, hairline * 3.5D);
        RiftShatter.Web web = rift.web;
        // Every crack opens up and flares in the moment before the pane goes, which is what tells the
        // eye the break is coming instead of letting it arrive out of a still picture.
        float flare = 1.0F + 0.6F * ease(0.8F, 1.0F, crackProgress);

        drawSag(consumer, matrix, rift, partialTick, eye, bias, crackProgress, since, tint);

        for (int crack = 0; crack < web.cracks(); crack++) {
            float reach = web.radialReach(crack, crackProgress);
            if (reach <= 0.0F) {
                continue;
            }
            float weight = web.weight(crack);
            for (int point = 1; point < web.pathPoints(); point++) {
                float from = web.pathAlong(crack, point - 1);
                if (from >= reach) {
                    break;
                }
                // A run lies between two pieces of the same ring, and goes when they do.
                int span = (point - 1) / RiftShatter.Web.STEPS;
                float lit = standing(since, (web.ring(span) + web.ring(span + 1)) * 0.5F)
                        * flare * (0.55F + 0.45F * weight);
                if (lit <= 0.0F) {
                    continue;
                }
                float to = web.pathAlong(crack, point);
                float cut = to <= reach ? 1.0F : (reach - from) / (to - from);
                float x0 = web.pathU(crack, point - 1);
                float y0 = web.pathV(crack, point - 1);
                float x1 = Mth.lerp(cut, x0, web.pathU(crack, point));
                float y1 = Mth.lerp(cut, y0, web.pathV(crack, point));
                crackRun(consumer, matrix, rift, bias, cover, x0, y0, x1, y1,
                        crackCore(cover, hairline, from, weight),
                        crackCore(cover, hairline, Mth.lerp(cut, from, to), weight),
                        halo * (0.6D + 0.4D * weight), tint, lit);
                if (cut < 1.0F) {
                    // The running front: brightest while the fracture is still fast, and gone by the
                    // time it arrives. The ease that slows the crack is what dims its spark.
                    crackSpark(consumer, matrix, rift, bias, cover, x1, y1, hairline,
                            lit * (float) Math.pow(1.0F - reach, 2.0D / 3.0D));
                }
            }
        }

        for (int crack = 0; crack < web.cracks(); crack++) {
            for (int ring = 1; ring < web.rings(); ring++) {
                float zip = web.chordReach(crack, ring, crackProgress);
                if (zip <= 0.0F) {
                    continue;
                }
                // Finer than the radials either side of it, as the secondary fracture it is.
                float weight = Math.min(web.weight(crack), web.weight(crack + 1)) * 0.85F;
                // The inner edge of a piece of the ring outside it, which it stays with - once the
                // piece inside has gone it is the broken edge of what is left.
                float lit = standing(since, (web.ring(ring) + web.ring(ring + 1)) * 0.5F)
                        * flare * (0.5F + 0.45F * weight);
                if (lit <= 0.0F) {
                    continue;
                }
                double core = crackCore(cover, hairline, web.ring(ring), weight) * 0.8D;
                double glow = halo * (0.45D + 0.4D * weight);
                int first = web.chordForward(crack, ring) ? crack : crack + 1;
                int last = web.chordForward(crack, ring) ? crack + 1 : crack;
                float ax = web.nodeU(first, ring);
                float ay = web.nodeV(first, ring);
                float mx = web.chordMidU(crack, ring);
                float my = web.chordMidV(crack, ring);
                float bx = web.nodeU(last, ring);
                float by = web.nodeV(last, ring);
                // Across from one radial to the other through the bend in the middle, half the run
                // each side of it.
                float half = Math.min(1.0F, zip * 2.0F);
                float tipX = Mth.lerp(half, ax, mx);
                float tipY = Mth.lerp(half, ay, my);
                crackRun(consumer, matrix, rift, bias, cover, ax, ay, tipX, tipY, core, core, glow, tint, lit);
                if (zip > 0.5F) {
                    float rest = (zip - 0.5F) * 2.0F;
                    tipX = Mth.lerp(rest, mx, bx);
                    tipY = Mth.lerp(rest, my, by);
                    crackRun(consumer, matrix, rift, bias, cover, mx, my, tipX, tipY, core, core, glow, tint, lit);
                }
                if (zip < 1.0F) {
                    crackSpark(consumer, matrix, rift, bias, cover, tipX, tipY, hairline, lit * (1.0F - zip));
                }
            }
        }

        for (RiftShatter.Branch branch : web.branches()) {
            float grown = branch.reach(crackProgress);
            if (grown <= 0.0F) {
                continue;
            }
            float out = (float) Math.hypot(branch.u0(), branch.v0());
            float lit = standing(since, out) * flare * 0.7F;
            if (lit <= 0.0F) {
                continue;
            }
            float weight = web.weight(branch.crack()) * 0.7F;
            double root = crackCore(cover, hairline, out, weight) * 0.8D;
            // Narrowing to nothing: a branch is a crack that ran out of energy before it got anywhere.
            crackRun(consumer, matrix, rift, bias, cover, branch.u0(), branch.v0(),
                    Mth.lerp(grown, branch.u0(), branch.u1()), Mth.lerp(grown, branch.v0(), branch.v1()),
                    root, Math.max(hairline, root * (1.0D - 0.7D * grown)), halo * 0.4D, tint, lit);
        }

        drawImpact(consumer, matrix, rift, bias, cover, hairline, crackProgress, standing(since, 0.0F), tint);
    }

    /**
     * How much of the pane is still standing at a distance out from the impact, 0..1, given the ticks
     * since it broke.
     *
     * <p>Glass lets go from the impact outwards over {@link RiftShatter#BREAK_SPREAD} ticks, so the web
     * has to go the same way. Wiped all at once at the break, as it used to be, the outer pane vanished
     * a good few ticks before any of its pieces fell, and for that moment there was neither crack nor
     * glass where a window should have been. Kept any longer, a crack would hang in the air where the
     * glass either side of it had already gone.
     *
     * @param out how far out, as a fraction of the rim
     */
    private static float standing(float since, float out) {
        return 1.0F - Mth.clamp((since - out * RiftShatter.BREAK_SPREAD) / FALL_FADE, 0.0F, 1.0F);
    }

    /** Half-width of a crack's core at a point this far out, never finer than a hairline. */
    private static double crackCore(double cover, double hairline, float out, float weight) {
        return Math.max(hairline, cover * Mth.lerp(Mth.clamp(out, 0.0F, 1.0F), CRACK_ROOT, CRACK_TIP)
                * (0.6F + 0.4F * weight));
    }

    /**
     * How far a point of the web really sits from the middle, per unit of rim fraction.
     *
     * <p>The web is worked out on a unit circle; the pane it cracks is the torn one, frozen at the
     * moment it breaks. Scaling each point by the rim's reach at its own angle bends the whole web to
     * that shape, the same way the pieces that come out of it are bent.
     */
    private static double tornScale(ActiveRift rift, double cover, double x, double y) {
        return cover * rift.reach(Math.atan2(y, x), rift.rimTime);
    }

    /**
     * One straight run of a crack: a bright hairline core, and a soft glow either side of it.
     *
     * <p>The core overruns both its ends by its own width, so two runs meeting at a kink overlap
     * rather than leaving a notch in the outside of the bend - and, the pass being additive, the
     * overlap lights the joint the way a junction in real cracked glass catches the light. The glow
     * does not overrun, or every joint would bloom.
     *
     * @param x0    where the run starts, in rim fractions - see {@link #tornScale}
     * @param core0 half-width of the core at the start, in blocks
     * @param halo  how far the glow reaches either side, in blocks
     */
    private static void crackRun(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                                 double cover, float x0, float y0, float x1, float y1,
                                 double core0, double core1, double halo, float[] tint, float alpha) {
        double scale0 = tornScale(rift, cover, x0, y0);
        double scale1 = tornScale(rift, cover, x1, y1);
        double u0 = x0 * scale0;
        double v0 = y0 * scale0;
        double u1 = x1 * scale1;
        double v1 = y1 * scale1;
        double du = u1 - u0;
        double dv = v1 - v0;
        double length = Math.sqrt(du * du + dv * dv);
        if (length < 1.0e-6D) {
            return;
        }
        double alongU = du / length;
        double alongV = dv / length;
        double sideU = -alongV;
        double sideV = alongU;

        // Nearly white: a crack is light caught on a fracture face, not a line of the rift's colour.
        float coreRed = Mth.lerp(0.2F, 1.0F, tint[0]);
        float coreGreen = Mth.lerp(0.2F, 1.0F, tint[1]);
        float coreBlue = Mth.lerp(0.2F, 1.0F, tint[2]);
        double startU = u0 - alongU * core0;
        double startV = v0 - alongV * core0;
        double endU = u1 + alongU * core1;
        double endV = v1 + alongV * core1;
        localVertex(consumer, matrix, rift, startU + sideU * core0, startV + sideV * core0, bias,
                coreRed, coreGreen, coreBlue, alpha);
        localVertex(consumer, matrix, rift, startU - sideU * core0, startV - sideV * core0, bias,
                coreRed, coreGreen, coreBlue, alpha);
        localVertex(consumer, matrix, rift, endU - sideU * core1, endV - sideV * core1, bias,
                coreRed, coreGreen, coreBlue, alpha);
        localVertex(consumer, matrix, rift, endU + sideU * core1, endV + sideV * core1, bias,
                coreRed, coreGreen, coreBlue, alpha);

        // Lit along the crack and gone at the edge of the glow, one side at a time, so it is a soft
        // bloom rather than a wider bar.
        float glow = Math.min(1.0F, alpha) * 0.4F;
        for (int side = -1; side <= 1; side += 2) {
            double reachU = sideU * halo * side;
            double reachV = sideV * halo * side;
            localVertex(consumer, matrix, rift, u0, v0, bias, tint[0], tint[1], tint[2], glow);
            localVertex(consumer, matrix, rift, u0 + reachU, v0 + reachV, bias, tint[0], tint[1], tint[2], 0.0F);
            localVertex(consumer, matrix, rift, u1 + reachU, v1 + reachV, bias, tint[0], tint[1], tint[2], 0.0F);
            localVertex(consumer, matrix, rift, u1, v1, bias, tint[0], tint[1], tint[2], glow);
        }
    }

    /** The front of a running crack: a point of white light, at a point given in rim fractions. */
    private static void crackSpark(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                                   double cover, float x, float y, double hairline, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        double scale = tornScale(rift, cover, x, y);
        double u = x * scale;
        double v = y * scale;
        double size = Math.max(cover * 0.02D, hairline * 4.0D);
        float lit = Math.min(1.0F, alpha);
        localVertex(consumer, matrix, rift, u, v + size, bias, 1.0F, 1.0F, 1.0F, lit);
        localVertex(consumer, matrix, rift, u + size, v, bias, 1.0F, 1.0F, 1.0F, lit);
        localVertex(consumer, matrix, rift, u, v - size, bias, 1.0F, 1.0F, 1.0F, lit);
        localVertex(consumer, matrix, rift, u - size, v, bias, 1.0F, 1.0F, 1.0F, lit);
    }

    /**
     * Where the blow landed: a star of glass crushed white, and the flash of the strike itself.
     *
     * <p>The crush is there from the first frame to the break. Pulverised glass is white rather than
     * clear, which is why a real impact point reads as a bright spot with the cracks leaving it rather
     * than as the place the lines happen to meet. The flash is brightest at the instant of the blow and
     * settles to a small glow on the crush by the time the cracks have run.
     */
    private static void drawImpact(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float bias,
                                   double cover, double hairline, float crackProgress, float alpha,
                                   float[] tint) {
        if (alpha <= 0.0F) {
            return;
        }
        RiftShatter.Web web = rift.web;
        double root = Math.max(hairline, cover * 0.010D);
        for (int spike = 0; spike < RiftShatter.CRUSH_SPIKES; spike++) {
            double along = Math.cos(web.crushAngle(spike));
            double across = Math.sin(web.crushAngle(spike));
            double length = cover * web.crushLength(spike);
            // A sliver: wide where it leaves the middle, a point at its end.
            localVertex(consumer, matrix, rift, -across * root, along * root, bias,
                    1.0F, 1.0F, 1.0F, alpha * 0.85F);
            localVertex(consumer, matrix, rift, across * root, -along * root, bias,
                    1.0F, 1.0F, 1.0F, alpha * 0.85F);
            localVertex(consumer, matrix, rift, along * length, across * length, bias,
                    tint[0], tint[1], tint[2], alpha * 0.2F);
            localVertex(consumer, matrix, rift, along * length, across * length, bias,
                    tint[0], tint[1], tint[2], alpha * 0.2F);
        }

        float flash = Math.max(0.0F, 1.0F - crackProgress / FLASH_SPAN);
        float glow = alpha * (0.35F + 0.65F * flash);
        double size = cover * (0.035D + 0.16D * flash * flash);
        for (int wedge = 0; wedge < FLASH_WEDGES; wedge++) {
            double a0 = wedge / (double) FLASH_WEDGES * Math.PI * 2.0D;
            double a1 = (wedge + 1) / (double) FLASH_WEDGES * Math.PI * 2.0D;
            // A triangle drawn as a quad with its first two corners on the same point.
            localVertex(consumer, matrix, rift, 0.0D, 0.0D, bias, 1.0F, 1.0F, 1.0F, glow);
            localVertex(consumer, matrix, rift, 0.0D, 0.0D, bias, 1.0F, 1.0F, 1.0F, glow);
            localVertex(consumer, matrix, rift, Math.cos(a0) * size, Math.sin(a0) * size, bias,
                    tint[0], tint[1], tint[2], 0.0F);
            localVertex(consumer, matrix, rift, Math.cos(a1) * size, Math.sin(a1) * size, bias,
                    tint[0], tint[1], tint[2], 0.0F);
        }
    }

    /**
     * The cells between the cracks catching the light, each at its own angle.
     *
     * <p>What says "glass" more than any line does. An intact pane mirrors the sky as one sheet; a
     * cracked one sags as it gives, every cell a degree or two its own way, so neighbouring cells
     * mirror different things and the web shows up as a patchwork of brighter and darker panes. A cell
     * only shows once the cracks round it have been drawn - a pane with no crack to have made it would
     * be glass tilting for no reason - and the whole sheet shivers in the moment before it goes.
     */
    private static void drawSag(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, float partialTick,
                                Vec3 eye, float bias, float crackProgress, float since, float[] tint) {
        float sag = ease(SAG_FROM, 1.0F, crackProgress);
        if (sag <= 0.0F) {
            return;
        }
        RiftShatter.Web web = rift.web;
        double cover = rift.radius;
        float clock = Mth.lerp(partialTick, rift.lastAge, rift.age);
        float shiver = ease(0.8F, 1.0F, crackProgress);
        // Mostly white, like the sky it is mirroring, with a little of the rift in it.
        float red = Mth.lerp(0.35F, 1.0F, tint[0]);
        float green = Mth.lerp(0.35F, 1.0F, tint[1]);
        float blue = Mth.lerp(0.35F, 1.0F, tint[2]);

        // The cells are the pieces about to fall, so they are drawn from the pieces' own outlines -
        // which are the web's crossings, laid out in the same order.
        for (int ring = 0; ring < web.rings(); ring++) {
            for (int crack = 0; crack < web.cracks(); crack++) {
                float formed = web.cellFormed(crack, ring, crackProgress);
                if (formed <= 0.0F) {
                    continue;
                }
                RiftShatter.Shard shard = rift.shards[ring * web.cracks() + crack];
                // Gone as its piece goes, which is fading in as this fades out.
                float standing = 1.0F - Mth.clamp((since - shard.delay()) / FALL_FADE, 0.0F, 1.0F);
                if (standing <= 0.0F) {
                    continue;
                }
                RiftShatter.Outline outline = shard.outline();
                double centreU = 0.0D;
                double centreV = 0.0D;
                for (int corner = 0; corner < 4; corner++) {
                    double angle = outline.angle(corner);
                    double out = cover * rift.reach(angle, rift.rimTime) * outline.radius(corner);
                    PIECE_U[corner] = Math.cos(angle) * out;
                    PIECE_V[corner] = Math.sin(angle) * out;
                    centreU += PIECE_U[corner] * 0.25D;
                    centreV += PIECE_V[corner] * 0.25D;
                }
                float wobble = 1.0F + shiver * 0.5F * Mth.sin(clock * 2.9F + (ring * 31 + crack) * 1.7F);
                float tilt = sag * wobble;
                float sheen = skyGlint(rift, eye, centreU, centreV,
                        web.tiltU(crack, ring) * tilt, web.tiltV(crack, ring) * tilt);
                float glow = standing * formed * sag * (0.025F + 0.17F * sheen);
                int first = outline.firstCorner();
                for (int turnRound = 0; turnRound < 4; turnRound++) {
                    int corner = (first + turnRound) % 4;
                    localVertex(consumer, matrix, rift, PIECE_U[corner], PIECE_V[corner], bias,
                            red, green, blue, glow);
                }
            }
        }
    }

    /**
     * How much bright sky a slightly tilted piece of pane mirrors towards the viewer, 0..1.
     *
     * <p>Not {@link #glint}, which asks how square-on a piece is and so lights every cell of a flat
     * pane the same when it is seen face-on. A cracked window reads as cracked because its cells
     * reflect <em>different things</em>, so this reflects the line of sight off the tilted cell and
     * asks how far up it ends up pointing. "Up is bright" is all the sky there is to it, and it is
     * enough to tell one cell from its neighbour.
     *
     * @param tiltU how far the cell has turned towards the aperture's right axis, in radians-ish
     * @param tiltV how far towards its up axis
     */
    private static float skyGlint(ActiveRift rift, Vec3 eye, double u, double v, float tiltU, float tiltV) {
        v *= rift.aspect;
        double normalX = rift.normal.x + rift.right.x * tiltU + rift.up.x * tiltV;
        double normalY = rift.normal.y + rift.right.y * tiltU + rift.up.y * tiltV;
        double normalZ = rift.normal.z + rift.right.z * tiltU + rift.up.z * tiltV;
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        double toEyeX = eye.x - (rift.centre.x + rift.right.x * u + rift.up.x * v);
        double toEyeY = eye.y - (rift.centre.y + rift.right.y * u + rift.up.y * v);
        double toEyeZ = eye.z - (rift.centre.z + rift.right.z * u + rift.up.z * v);
        double eyeLength = Math.sqrt(toEyeX * toEyeX + toEyeY * toEyeY + toEyeZ * toEyeZ);
        if (eyeLength < 1.0e-6D || normalLength < 1.0e-6D) {
            return 1.0F;
        }
        double facing = (normalX * toEyeX + normalY * toEyeY + normalZ * toEyeZ) / (normalLength * eyeLength);
        // The line of sight reflected off the cell, 2(n.e)n - e - which comes out the same whichever
        // face the viewer is looking at, since flipping n flips n.e with it. Only its height matters.
        double reflectedY = 2.0D * facing * normalY / normalLength - toEyeY / eyeLength;
        float sky = (float) Mth.clamp((reflectedY + 0.35D) / 1.1D, 0.0D, 1.0D);
        return sky * sky;
    }

    /** 0 below {@code from}, 1 above {@code to}, and a smooth S between. */
    private static float ease(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
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
        if (since <= 0.0F || since > RiftShatter.SHARD_LIFE + 8.0F
                || rift.pattern.motion() == RiftShatter.Motion.UNBROKEN) {
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
                case BLINK -> blinkShard(consumer, matrix, rift, shard, cover, since, life, fade,
                        red, green, blue);
                case UNBROKEN -> {
                    // Unreachable: returned above. Nothing broke, so there is nothing to draw.
                }
                case IMPROBABLE -> improbableShard(consumer, matrix, rift, shard, cover, since, travel,
                        fade, life);
            }
        }
    }

    // ------------------------------------------------------------ the last two

    /**
     * A piece that teleports: flickering where it was cut, then gone, then somewhere else.
     *
     * <p>Only ever in one of two places. At home it stutters on and off on its own clock, faster as the
     * hop nears, and warms from the End's teal towards an eye's magenta; then it is simply elsewhere -
     * turned some way round the rim and thrown out past it - arriving bright and cooling back into the
     * void as it dwindles. Nothing is drawn between the two, which is the whole difference between a
     * teleport and a very fast move.
     */
    private static void blinkShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   RiftShatter.Shard shard, double cover, float since, float life,
                                   float fade, float red, float green, float blue) {
        float[] eye = RiftFurniture.channels(rift.accentColour);
        if (life < BLINK_HOP) {
            float charge = life / BLINK_HOP;
            // Its own phase from the piece's own noise, so the field flickers rather than strobing.
            boolean on = Mth.sin(since * (1.4F + 4.5F * charge) + shard.axis() * 7.0F) > -0.25F;
            planarShard(consumer, matrix, rift, shard, cover, 0.0F, 0.0D, 0.0D, 1.0D,
                    Mth.lerp(charge, red, eye[0]), Mth.lerp(charge, green, eye[1]), Mth.lerp(charge, blue, eye[2]),
                    fade * (on ? 0.85F : 0.2F));
            return;
        }
        float away = (life - BLINK_HOP) / (1.0F - BLINK_HOP);
        float[] hot = RiftFurniture.channels(ThemeLook.ENDER_EYE_HOT);
        // White-pink the instant it lands, the eye's colour a moment later, the void by the end.
        float arrive = Math.max(0.0F, 1.0F - away * 5.0F);
        float cool = Mth.clamp((away - 0.2F) * 1.25F, 0.0F, 1.0F);
        float r = Mth.lerp(cool, Mth.lerp(arrive, eye[0], hot[0]), red);
        float g = Mth.lerp(cool, Mth.lerp(arrive, eye[1], hot[1]), green);
        float b = Mth.lerp(cool, Mth.lerp(arrive, eye[2], hot[2]), blue);
        planarShard(consumer, matrix, rift, shard, cover, shard.spin(), shard.outward() * cover, 0.0D,
                0.8D - 0.5D * away, r, g, b, fade * 0.9F);
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
     * One piece of glass, thrown clear, tumbling, and falling.
     *
     * <p>Everything about the throw eases off, but gravity does not, so a piece bursts clear, coasts,
     * and is dropping by the time it fades - the arc that tells a pane coming out of its frame from a
     * burst of light.
     */
    private static void tumblingShard(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                      RiftShatter.Shard shard, Vec3 eye, double cover, float elapsed,
                                      float travel, float fade, float red, float green, float blue) {
        float since = Math.max(0.0F, elapsed);
        glassPiece(consumer, matrix, rift, shard, eye, cover, shard.spin() * since,
                shard.outward() * cover * travel, shard.push() * cover * 0.45D * travel,
                GLASS_FALL * cover * since * since, fade * 0.85F, red, green, blue);
    }

    /**
     * One piece of the pane, drawn as glass: a faint body inside a bright cut edge.
     *
     * <p>The edge is what makes it read. A flat translucent tile is a tile; the same tile with its
     * rim picked out is a piece of something with thickness, which is the whole difference between a
     * pane of glass and a sheet of coloured light. The edge is a set width in from each corner rather
     * than a share of the piece, because a pane is as thick as it is however it broke - so the little
     * slivers from round the impact come out nearly all edge, and glitter.
     *
     * <p>Cut to the piece's {@link RiftShatter.Outline}, so it is the cell the cracks outlined. The
     * piece keeps its own frame and turns in it, so the corners stay a rigid shape rather than
     * shearing the way rotating each corner about the centre would.
     *
     * @param turn     radians it has tumbled through
     * @param outward  how far it has been thrown from the impact, in blocks
     * @param push     how far it has been thrown along the aperture's normal, in blocks
     * @param drop     how far it has fallen, in blocks
     * @param strength how solid it is at its brightest, which is the fade and the pass's own ceiling
     */
    private static void glassPiece(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift,
                                   RiftShatter.Shard shard, Vec3 eye, double cover, float turn,
                                   double outward, double push, double drop, float strength,
                                   float red, float green, float blue) {
        RiftShatter.Outline outline = shard.outline();
        double centreU = 0.0D;
        double centreV = 0.0D;
        for (int corner = 0; corner < 4; corner++) {
            double angle = outline.angle(corner);
            double out = cover * rift.reach(angle, rift.rimTime) * outline.radius(corner);
            PIECE_U[corner] = Math.cos(angle) * out;
            PIECE_V[corner] = Math.sin(angle) * out;
            centreU += PIECE_U[corner] * 0.25D;
            centreV += PIECE_V[corner] * 0.25D;
        }
        double edge = cover * GLASS_EDGE;
        for (int corner = 0; corner < 4; corner++) {
            PIECE_U[corner] -= centreU;
            PIECE_V[corner] -= centreV;
            double length = Math.sqrt(PIECE_U[corner] * PIECE_U[corner] + PIECE_V[corner] * PIECE_V[corner]);
            double keep = length < 1.0e-6D ? 0.0D : 1.0D - Math.min(GLASS_EDGE_MAX, edge / length);
            INSET_U[corner] = PIECE_U[corner] * keep;
            INSET_V[corner] = PIECE_V[corner] * keep;
        }

        float axisU = Mth.cos(shard.axis());
        float axisV = Mth.sin(shard.axis());
        Vector3f alongU = new Vector3f(1.0F, 0.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
        Vector3f alongV = new Vector3f(0.0F, 1.0F, 0.0F).rotateAxis(turn, axisU, axisV, 0.0F);
        Vector3f facing = new Vector3f(0.0F, 0.0F, 1.0F).rotateAxis(turn, axisU, axisV, 0.0F);

        // Down is the world's, not the aperture's, so it is taken into the aperture's frame - with the
        // up axis divided by the aspect, which localVertex multiplies back in.
        double baseU = centreU + Math.cos(shard.midAngle()) * outward - rift.right.y * drop;
        double baseV = centreV + Math.sin(shard.midAngle()) * outward - rift.up.y * drop / rift.aspect;
        double baseW = push - rift.normal.y * drop;

        float sheen = glint(rift, eye, facing, baseU, baseV, baseW);
        float lit = 0.45F + 0.55F * sheen;
        float white = sheen * sheen;
        float bodyAlpha = strength * (0.14F + 0.62F * sheen);
        float bodyRed = Mth.lerp(white, red, 1.0F) * lit;
        float bodyGreen = Mth.lerp(white, green, 1.0F) * lit;
        float bodyBlue = Mth.lerp(white, blue, 1.0F) * lit;
        // The cut edge is nearly white and never faint: it is light caught end-on in the glass, which
        // it catches whichever way the piece has turned.
        float edgeAlpha = strength * (0.55F + 0.45F * sheen);
        float edgeWhite = 0.55F + 0.45F * white;
        float edgeRed = Mth.lerp(edgeWhite, red, 1.0F);
        float edgeGreen = Mth.lerp(edgeWhite, green, 1.0F);
        float edgeBlue = Mth.lerp(edgeWhite, blue, 1.0F);

        int first = outline.firstCorner();
        for (int turnRound = 0; turnRound < 4; turnRound++) {
            int corner = (first + turnRound) % 4;
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    INSET_U[corner], INSET_V[corner], bodyRed, bodyGreen, bodyBlue, bodyAlpha);
        }
        for (int corner = 0; corner < 4; corner++) {
            int next = (corner + 1) % 4;
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    PIECE_U[corner], PIECE_V[corner], edgeRed, edgeGreen, edgeBlue, edgeAlpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    PIECE_U[next], PIECE_V[next], edgeRed, edgeGreen, edgeBlue, edgeAlpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    INSET_U[next], INSET_V[next], edgeRed, edgeGreen, edgeBlue, edgeAlpha);
            corner(consumer, matrix, rift, baseU, baseV, baseW, alongU, alongV,
                    INSET_U[corner], INSET_V[corner], edgeRed, edgeGreen, edgeBlue, edgeAlpha);
        }
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
        // A pane that never broke has nothing to come back.
        if (progress <= 0.0F || progress >= 1.0F || rift.pattern.motion() == RiftShatter.Motion.UNBROKEN) {
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

            // A teleported piece comes home the way it left: at the one place it went, and then home,
            // flickering in - never anywhere between.
            if (rift.pattern.motion() == RiftShatter.Motion.BLINK) {
                if (out > 0.5F) {
                    float[] lit = RiftFurniture.channels(rift.accentColour);
                    planarShard(consumer, matrix, rift, shard, cover, shard.spin(), shard.outward() * cover,
                            0.0D, 0.6D, lit[0], lit[1], lit[2], fade * 0.85F);
                } else {
                    boolean on = Mth.sin(progress * 90.0F + shard.axis() * 7.0F) > -0.25F;
                    planarShard(consumer, matrix, rift, shard, cover, 0.0F, 0.0D, 0.0D, 1.0D,
                            red, green, blue, fade * (on ? 0.8F : 0.2F));
                }
                continue;
            }

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

            // Still turning as it comes in, and square by the time it lands. Not falling: a piece
            // that fell on the way out and rose on the way back would read as the opening rewound.
            glassPiece(consumer, matrix, rift, shard, eye, cover,
                    shard.spin() * out * RiftShatter.BREAK_SPREAD * 3.0F,
                    shard.outward() * cover * out, shard.push() * cover * 0.45D * out, 0.0D,
                    fade * 0.9F, red, green, blue);
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
        if (rift.surface == ThemeLook.Surface.NEBULA) {
            return; // it seals on its own flash, as the collapse arrives - see Nebula.pop
        }
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
     * An opaque vertex at {@code (u, v)} in the aperture's own plane - the solid counterpart of
     * {@link #localVertex}, for the one opaque surface that is not laid out round the middle.
     *
     * <p>Only the End's bleed uses it, and its format has no colour, so the white here is never seen.
     */
    private static void solidLocal(VertexConsumer consumer, Matrix4f matrix, ActiveRift rift, double u, double v,
                                   float red, float green, float blue) {
        double up = v * rift.aspect;
        solidVertex(consumer, matrix,
                (float) (rift.centre.x + rift.right.x * u + rift.up.x * up),
                (float) (rift.centre.y + rift.right.y * u + rift.up.y * up),
                (float) (rift.centre.z + rift.right.z * u + rift.up.z * up),
                red, green, blue, 1.0F);
    }

    /**
     * An opaque vertex already placed in the world - for the one solid thing {@link RiftFurniture}
     * builds, a Sketch's pencil, which it lays out in three dimensions itself.
     *
     * <p>Package-private so that the pencil still goes through {@link #solidVertex}, and so through the
     * one writer the membrane's format has.
     */
    static void solidWorld(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                           float red, float green, float blue) {
        solidVertex(consumer, matrix, (float) x, (float) y, (float) z, red, green, blue, 1.0F);
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
