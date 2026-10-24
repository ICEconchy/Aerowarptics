package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * The cloud a Mostly Dangerous rift arrives in: an ink-black nebula that boils out of nothing, churns
 * while the rift stands, and collapses in on itself the moment the ship is clear of it.
 *
 * <p>The look is a capital ship dropping out of hyperspace - no pane, no tear, just a dark cloud that
 * was not there a second ago, crawling with lightning, with a hull boring out of the middle of it. So
 * nothing here breaks: the hole opens in the heart of a cloud that is already black, and what the eye
 * reads is the cloud itself - how fast it appears, how it turns, and how violently it goes.
 *
 * <h2>Puffs, not a disc</h2>
 * The cloud is drawn as soft puffs of darkness, each fading to nothing at its edge, laid round the hole
 * in rings. Overlapping, they make a core dark enough to hide the sky behind it and an edge that billows
 * rather than being cut. Each ring turns at its own rate - the inner ones faster, the way anything
 * swirling round a centre does - but every puff in a ring turns together, so the spacing within a ring
 * never opens up and the cloud cannot tear itself a gap as it churns. {@code NebulaTest} holds that
 * against the real falloff, because a ring of daylight between the black hole and the black cloud would
 * turn the whole thing into a doughnut, and nothing about that would throw.
 *
 * <h2>Everything is a function of the clock</h2>
 * Where a puff is, how gathered the cloud is, how far through its collapse, when it flashes inside and
 * when the flashes light the hull coming out - all of it is worked out from the rift's own seed and the
 * moments the rift already knows about, like {@link SketchPencil}. Every viewer sees the same cloud, and
 * a rift jumped straight to open by a hull arriving early simply lands later on the same curves.
 *
 * <p>Free of Minecraft, for {@code NebulaTest}.
 */
public final class Nebula {

    // ---------------------------------------------------------------- the rings

    /** The rings of cloud round the hole, as fractions of the rim - the first just inside it. */
    private static final float[] RING_RADII = {0.75F, 1.08F, 1.40F, 1.72F, 2.05F, 2.40F};
    /** Puffs in each ring. More further out, where there is more ring to cover. */
    private static final int[] RING_PUFFS = {10, 13, 15, 17, 18, 19};
    /**
     * How big a puff in each ring is, as a fraction of the rim - its radius, out to where it has faded
     * to nothing. Smaller further out, so the edge breaks up into billows instead of ending in a wall.
     */
    private static final float[] RING_SIZES = {0.60F, 0.60F, 0.58F, 0.54F, 0.48F, 0.42F};
    /** How much bigger than its ring's size a puff may be rolled. */
    private static final float SIZE_SPREAD = 0.10F;
    /** How far a puff may sit off the even spacing of its ring, as a fraction of that spacing. */
    private static final float JOSTLE = 0.15F;
    /** How far a puff may sit in or out of its ring, as a fraction of the rim. */
    private static final float SCATTER = 0.06F;
    /** How thick the cloud is along the rift's axis at its middle, as a fraction of the rim. */
    private static final float THICKNESS = 0.55F;

    /** Puffs in the whole cloud. */
    public static final int PUFFS = count();

    /**
     * How fast the cloud turns, in radians a tick at the rim. A ring further out turns slower, in
     * proportion, which is what makes it read as a vortex rather than as a plate being rotated.
     */
    private static final float SWIRL = 0.028F;
    /** How far a puff wanders in and out as it churns, as a fraction of the rim. */
    private static final float WANDER = 0.05F;
    /** How much a puff swells and shrinks as it churns, as a fraction of its size. */
    private static final float BREATH = 0.12F;

    /**
     * How dark one puff is at its heart.
     *
     * <p>Not solid on its own - two or three of them overlapping are what make the core black. A
     * single puff that hid the sky would make the edge a hard shape again.
     */
    public static final float PUFF_ALPHA = 0.66F;
    /** Where a puff's darkness starts to fall off, as a fraction of its size, and how dark it is there. */
    public static final float PUFF_MID = 0.55F;
    public static final float PUFF_MID_ALPHA = 0.85F;

    /**
     * The furthest any of it reaches, as a fraction of the rim: the outermost puff at its furthest
     * wander, at its biggest, at the far edge of its fade. Worked out rather than chosen, so the lens
     * and anything else sized off the cloud can trust it.
     */
    public static final float REACH = reach();

    // ------------------------------------------------------------ gather, collapse

    /**
     * How far into the opening the cloud has finished gathering.
     *
     * <p>Before the hole has started to open - {@link RiftShatter#CRACK_PHASE} - so the face comes into
     * being inside a cloud that is already there, rather than as a black disc growing out past it.
     */
    public static final float GATHER_BY = 0.6F;

    /**
     * How far into the close the cloud has finished collapsing.
     *
     * <p>A third: the collapse is the violent part and wants to be quick, and what is left of the close
     * is the aftermath - the smoke, and the trails hanging where the ship came out.
     */
    public static final float COLLAPSE_TO = 0.3F;

    /** Radians of extra turn the cloud winds through as it collapses: it spins up as it falls in. */
    private static final float SPIN_UP = 3.0F;
    /**
     * How much faster the cloud is drawn in than it shrinks.
     *
     * <p>Over one, so puffs are pulled into the middle ahead of their own size going, which is what
     * reads as collapsing <em>inwards</em> rather than simply shrinking. The hole shrinks on the same
     * curve as the pull, so the cloud's inner edge stays over it the whole way down.
     */
    private static final float PULL_IN = 1.6F;

    // ----------------------------------------------------------- flashes, trails

    /**
     * When the flashes that light the hull coming out go off, in ticks after it starts to come out.
     *
     * <p>Uneven, so they read as lightning inside the cloud rather than as something blinking; spaced
     * further apart than a flash lasts, so each one is a flash of its own; and all well inside the time
     * a hull takes to come through, which is sixty ticks unless a server has changed it.
     */
    private static final float[] EMERGENCE = {0.0F, 5.0F, 13.0F, 24.0F, 38.0F};
    /** Ticks one of those flashes lasts. */
    public static final float EMERGENCE_FLASH = 4.0F;

    /** Glowing trails left hanging where the ship came out. */
    public static final int TRAILS = 5;

    /** How long a trail takes to come up at the start of the close, as a fraction of it. */
    private static final float TRAIL_RISE = 0.04F;

    /** How long either side of the end of the collapse its flash lasts, as fractions of the close. */
    private static final float POP_BEFORE = 0.03F;
    private static final float POP_AFTER = 0.15F;

    /**
     * Arcs crawling over the cloud, and how often each fires, in ticks. Far more often than lightning off
     * a rim - the cloud's never stops - so at any moment three or four of them are alight.
     */
    public static final int ARCS = 12;
    private static final float ARC_PERIOD_MIN = 9.0F;
    private static final float ARC_PERIOD_MAX = 24.0F;
    /** Flashes deep inside the cloud, lighting it from within, and how often each goes off. */
    public static final int FLASHES = 6;
    private static final float FLASH_PERIOD_MIN = 16.0F;
    private static final float FLASH_PERIOD_MAX = 44.0F;

    /** Where the cloud's noise starts in the rift's own, clear of every other draw from it. */
    private static final int SALT = 90_000;

    private Nebula() {
    }

    // ------------------------------------------------------------------ records

    /**
     * One puff of cloud, where it sits when the cloud is fully gathered and at rest.
     *
     * @param angle  round the hole, in radians
     * @param radius from the middle, as a fraction of the rim
     * @param depth  along the rift's axis, as a fraction of the rim - the cloud has thickness
     * @param size   its radius out to where it has faded to nothing, as a fraction of the rim
     * @param spin   radians a tick its ring turns at
     * @param phase  where it is in its own churn
     */
    public record Puff(float angle, float radius, float depth, float size, float spin, float phase) {
    }

    /**
     * A puff where it is at one moment, in the rift's own frame and as fractions of the rim.
     *
     * @param alpha how dark it is at its heart
     */
    public record Placed(float u, float v, float w, float size, float alpha) {
    }

    /**
     * A trail left where the ship came out.
     *
     * @param u      across the hole, as a fraction of the rim
     * @param v      up the hole, likewise
     * @param length how far it hangs out along the way the ship went, as a fraction of the rim
     */
    public record Trail(float u, float v, float length) {
    }

    // ------------------------------------------------------------------- puffs

    /** The cloud a rift's own seed gathers into. */
    public static Puff[] puffs(int seed) {
        Puff[] puffs = new Puff[PUFFS];
        int index = 0;
        for (int ring = 0; ring < RING_RADII.length; ring++) {
            int count = RING_PUFFS[ring];
            float spacing = (float) (Math.PI * 2.0D / count);
            float offset = RiftShatter.noise(seed, SALT + ring) * spacing;
            float spin = SWIRL / RING_RADII[ring];
            // Thickest in the middle and thinning to the edge, the way a cloud seen side on is.
            float thickness = THICKNESS * (1.0F - 0.6F * ring / (RING_RADII.length - 1.0F));
            for (int within = 0; within < count; within++) {
                int n = SALT + 100 + index * 6;
                float angle = offset + spacing * (within + JOSTLE * (RiftShatter.noise(seed, n) - 0.5F));
                float radius = RING_RADII[ring] + SCATTER * (RiftShatter.noise(seed, n + 1) - 0.5F) * 2.0F;
                float depth = thickness * (RiftShatter.noise(seed, n + 2) - 0.5F);
                float size = RING_SIZES[ring] + SIZE_SPREAD * RiftShatter.noise(seed, n + 3);
                float phase = RiftShatter.noise(seed, n + 4) * (float) (Math.PI * 2.0D);
                puffs[index++] = new Puff(angle, radius, depth, size, spin, phase);
            }
        }
        return puffs;
    }

    /**
     * Where a puff is at a moment.
     *
     * @param gathered how far the cloud has come together - see {@link #gathered}
     * @param collapse how far it has fallen in on itself - see {@link #collapse}
     */
    public static Placed place(Puff puff, float time, float gathered, float collapse) {
        float shrink = shrink(collapse);
        float spread = gathered * shrink;
        float wander = WANDER * (float) Math.sin(time * 0.041F + puff.phase());
        float radius = (puff.radius() + wander) * spread;
        float angle = puff.angle() + puff.spin() * time + SPIN_UP * collapse * collapse * collapse;
        float breath = 1.0F + BREATH * (float) Math.sin(time * 0.057F + puff.phase() * 1.7F);
        // Shrinks slower than it is pulled in, so a collapsing puff is always bigger than the hole it is
        // being pulled over - see PULL_IN.
        float size = puff.size() * breath * (0.35F + 0.65F * gathered) * (1.0F - collapse);
        float alpha = PUFF_ALPHA * Math.min(1.0F, gathered * 2.0F);
        return new Placed((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius,
                puff.depth() * spread, size, alpha);
    }

    /**
     * How dark a puff is at a fraction of the way out to its edge, as a fraction of its heart.
     *
     * <p>The same falloff the renderer draws - a heart, a ring at {@link #PUFF_MID} a little lighter,
     * and nothing at the edge - so a coverage worked out from this is the coverage on screen.
     */
    public static float falloff(float out) {
        if (out <= 0.0F) {
            return 1.0F;
        }
        if (out <= PUFF_MID) {
            return 1.0F - (1.0F - PUFF_MID_ALPHA) * out / PUFF_MID;
        }
        if (out < 1.0F) {
            return PUFF_MID_ALPHA * (1.0F - (out - PUFF_MID) / (1.0F - PUFF_MID));
        }
        return 0.0F;
    }

    /**
     * How much of the sky the whole cloud hides at a point on the rift's face, looked at square on.
     *
     * <p>Puffs laid over one another the way translucent layers are, each letting through what the one
     * behind it let through less its own share. What the renderer draws, reduced to the question a test
     * can ask of it.
     */
    public static float opacity(Puff[] puffs, float u, float v, float time, float gathered, float collapse) {
        float through = 1.0F;
        for (Puff puff : puffs) {
            Placed placed = place(puff, time, gathered, collapse);
            if (placed.size() <= 0.0F) {
                continue;
            }
            float du = u - placed.u();
            float dv = v - placed.v();
            float out = (float) Math.sqrt(du * du + dv * dv) / placed.size();
            through *= 1.0F - placed.alpha() * falloff(out);
        }
        return 1.0F - through;
    }

    // --------------------------------------------------------- gather, collapse

    /**
     * How far the cloud has come together, 0 to 1, at a fraction of the way through the opening.
     *
     * <p>Out of nothing, fast, and settling: an ease out, so most of it arrives in the first few ticks.
     */
    public static float gathered(float openProgress) {
        float x = clamp(openProgress / GATHER_BY);
        float left = 1.0F - x;
        return 1.0F - left * left * left;
    }

    /**
     * How far the cloud has fallen in on itself, 0 to 1, at a fraction of the way through the close.
     *
     * <p>An ease in: it barely moves, then goes all at once - which is what violent looks like.
     */
    public static float collapse(float sealProgress) {
        float x = clamp(sealProgress / COLLAPSE_TO);
        return x * x;
    }

    /**
     * How far in a collapse has pulled everything - the puffs and the hole alike, 1 at rest and 0 when
     * it has all gone into the middle.
     */
    public static float shrink(float collapse) {
        return (float) Math.pow(1.0F - clamp(collapse), PULL_IN);
    }

    // ------------------------------------------------------------------ flashes

    /**
     * How bright the flashes lighting a hull coming out are, 0 to 1.
     *
     * <p>A hard snap and a fast fade each, the same shape a bolt of lightning has.
     *
     * @param transitFrom when the hull started coming out, or negative if it has not
     */
    public static float emergence(float time, float transitFrom) {
        if (transitFrom < 0.0F) {
            return 0.0F;
        }
        float brightest = 0.0F;
        for (float at : EMERGENCE) {
            float into = (time - transitFrom - at) / EMERGENCE_FLASH;
            if (into >= 0.0F && into < 1.0F) {
                float fade = 1.0F - into;
                brightest = Math.max(brightest, fade * fade);
            }
        }
        return brightest;
    }

    /**
     * How many of those flashes go off between two moments, the first excluded and the second included -
     * one tick's worth, for whoever sounds them and lights the world with them.
     */
    public static int emergenceStarts(float from, float to, float transitFrom) {
        if (transitFrom < 0.0F) {
            return 0;
        }
        int starts = 0;
        for (float at : EMERGENCE) {
            float moment = transitFrom + at;
            if (moment > from && moment <= to) {
                starts++;
            }
        }
        return starts;
    }

    /** The moments of those flashes, after the hull starts coming out - for tests and for nothing else. */
    static float[] emergenceMoments() {
        return EMERGENCE.clone();
    }

    /**
     * How bright the flash is as the cloud finishes collapsing, 0 to 1, at a fraction of the close.
     *
     * <p>Brightest exactly as the collapse completes - the moment everything arrives in the middle at
     * once - and gone quickly after.
     */
    public static float pop(float sealProgress) {
        float from = COLLAPSE_TO - POP_BEFORE;
        float to = COLLAPSE_TO + POP_AFTER;
        if (sealProgress <= from || sealProgress >= to) {
            return 0.0F;
        }
        if (sealProgress <= COLLAPSE_TO) {
            return (sealProgress - from) / POP_BEFORE;
        }
        float fade = 1.0F - (sealProgress - COLLAPSE_TO) / POP_AFTER;
        return fade * fade;
    }

    // ------------------------------------------------------------------- trails

    /** The trails a rift's own seed leaves: where across the hole each hangs, and how far it reaches. */
    public static Trail[] trails(int seed) {
        Trail[] trails = new Trail[TRAILS];
        for (int trail = 0; trail < TRAILS; trail++) {
            int n = SALT + 5_000 + trail * 4;
            // Clustered towards the middle, where a ship's engines are rather than out at its edges.
            double angle = RiftShatter.noise(seed, n) * Math.PI * 2.0D;
            double out = 0.15D + 0.4D * RiftShatter.noise(seed, n + 1);
            float length = 1.8F + 1.2F * RiftShatter.noise(seed, n + 2);
            trails[trail] = new Trail((float) (Math.cos(angle) * out), (float) (Math.sin(angle) * out), length);
        }
        return trails;
    }

    /**
     * How bright the trails are, 0 to 1, at a fraction of the way through the close.
     *
     * <p>They come up as the cloud starts to go, hold while it falls in, and fade through the rest of
     * the close - nothing at either end, so they neither appear nor vanish.
     */
    public static float afterglow(float sealProgress) {
        if (sealProgress <= 0.0F || sealProgress >= 1.0F) {
            return 0.0F;
        }
        if (sealProgress < TRAIL_RISE) {
            return sealProgress / TRAIL_RISE;
        }
        if (sealProgress <= COLLAPSE_TO) {
            return 1.0F;
        }
        float fade = 1.0F - (sealProgress - COLLAPSE_TO) / (1.0F - COLLAPSE_TO);
        return fade * fade;
    }

    // -------------------------------------------------------------------- arcs

    /** The arcs a rift's own seed crawls with - see {@link RiftLightning} for how a field of them keeps time. */
    public static RiftLightning.Emitter[] arcField(int seed) {
        return RiftLightning.field(seed, ARCS, SALT + 8_000, ARC_PERIOD_MIN, ARC_PERIOD_MAX);
    }

    /** The flashes deep inside it. */
    public static RiftLightning.Emitter[] flashField(int seed) {
        return RiftLightning.field(seed, FLASHES, SALT + 9_000, FLASH_PERIOD_MIN, FLASH_PERIOD_MAX);
    }

    /** Which of the three colours an arc is drawn in on one of its flashes - white, violet or blue. */
    public static int arcColour(int seed, int emitter, int cycle) {
        return Math.min(2, (int) (RiftShatter.noise(seed ^ (cycle * 0x2545F491), SALT + 7_000 + emitter) * 3.0F));
    }

    // ------------------------------------------------------------------ helpers

    private static int count() {
        int total = 0;
        for (int puffs : RING_PUFFS) {
            total += puffs;
        }
        return total;
    }

    private static float reach() {
        float furthest = 0.0F;
        for (int ring = 0; ring < RING_RADII.length; ring++) {
            float edge = RING_RADII[ring] + SCATTER + WANDER
                    + (RING_SIZES[ring] + SIZE_SPREAD) * (1.0F + BREATH);
            furthest = Math.max(furthest, edge);
        }
        return furthest;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
